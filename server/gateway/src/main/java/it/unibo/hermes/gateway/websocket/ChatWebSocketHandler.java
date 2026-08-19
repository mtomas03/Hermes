package it.unibo.hermes.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import it.unibo.hermes.gateway.dto.WsMessage;
import it.unibo.hermes.gateway.dto.WsMessageType;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.security.WebSocketJwtHandshakeInterceptor;
import it.unibo.hermes.gateway.service.MessagePublisherService;
import it.unibo.hermes.gateway.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * Handles the complete lifecycle and real-time message processing for WebSocket connections.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final WebSocketSessionRegistry registry;
    private final PresenceService presenceService;
    private final MessagePublisherService publisherService;
    private final ObjectMapper objectMapper;

    @Value("${hermes.gateway.instance-id:gateway-1}")
    private String gatewayInstanceId;

    /**
     * Creates the chat WebSocket handler.
     *
     * @param registry         the registry tracking active local WebSocket sessions
     * @param presenceService  the service updating user online presence states
     * @param publisherService the service publishing message events to downstream channels
     * @param objectMapper     the object mapper used for WebSocket message JSON serialization
     */
    public ChatWebSocketHandler(WebSocketSessionRegistry registry,
                                PresenceService presenceService,
                                MessagePublisherService publisherService,
                                ObjectMapper objectMapper) {
        this.registry = registry;
        this.presenceService = presenceService;
        this.publisherService = publisherService;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers the newly established WebSocket session and updates the user's presence to online.
     *
     * @param session the established WebSocket session
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String username = extractUsername(session);
        registry.register(username, session);
        presenceService.setOnline(username, gatewayInstanceId);
        log.info("Session opened for user '{}' (sessionId={})", username, session.getId());
    }

    /**
     * Removes the closed WebSocket session from the local registry and marks the user offline in Redis.
     *
     * @param session the closed WebSocket session
     * @param status  the status indicating why the connection closed
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String username = extractUsername(session);
        registry.unregister(username);
        presenceService.setOffline(username);
        log.info("Session closed for user '{}' - status: {}", username, status);
    }

    /**
     * Logs transport errors encountered on an active WebSocket session.
     *
     * @param session the WebSocket session where the error occurred
     * @param error   the exception representing the transport error
     */
    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable error) {
        String username = extractUsername(session);
        log.error("Transport error for user '{}': {}", username, error.getMessage());
    }

    /**
     * Deserializes and dispatches incoming WebSocket text messages according to their message type.
     *
     * @param session the sending WebSocket session
     * @param raw     the raw text message payload
     * @throws Exception if message processing encounters an unhandled failure
     */
    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage raw) throws Exception {
        String username = extractUsername(session);
        WsMessage msg;
        try {
            msg = objectMapper.readValue(raw.getPayload(), WsMessage.class);
        } catch (Exception e) {
            log.warn("Malformed WS message from user '{}': {}", username, e.getMessage());
            sendError(session, "MALFORMED_MESSAGE", "Could not parse message JSON");
            return;
        }

        if (msg.getType() == null) {
            sendError(session, "MISSING_TYPE", "Message type is required");
            return;
        }

        switch (msg.getType()) {
            case PING -> handlePing(session, username);
            case SEND_MESSAGE -> handleSendMessage(session, username, msg);
            case ACK -> handleAck(session, username, msg);
            default -> sendError(session, "UNKNOWN_TYPE",
                    "Unsupported message type: " + msg.getType());
        }
    }

    /**
     * Refreshes the client's heartbeat timestamp and replies with a PONG response message.
     *
     * @param session  the WebSocket session
     * @param username the username of the pinging client
     * @throws IOException if sending the response message fails
     */
    private void handlePing(WebSocketSession session, String username) throws IOException {
        registry.recordHeartbeat(username);
        presenceService.refreshTtl(username);
        send(session, WsMessage.pong());
        log.trace("PING/PONG for '{}'", username);
    }

    /**
     * Validates and publishes an outgoing message, sending an acceptance response back to the sender.
     *
     * @param session the sending WebSocket session
     * @param sender  the username of the message sender
     * @param msg     the outgoing message payload
     */
    private void handleSendMessage(WebSocketSession session, String sender, WsMessage msg) {
        if (msg.getRecipientUsername() == null || msg.getRecipientUsername().isBlank()) {
            sendError(session, "MISSING_RECIPIENT", "Field 'recipientUsername' is required");
            return;
        }
        if (msg.getContent() == null || msg.getContent().isBlank()) {
            sendError(session, "MISSING_CONTENT", "Field 'content' is required");
            return;
        }
        if (sender.equals(msg.getRecipientUsername())) {
            sendError(session, "SELF_SEND", "Cannot send a message to yourself");
            return;
        }

        try {
            MessageEvent accepted = publisherService.publish(msg, sender);

            WsMessage ack = new WsMessage();
            ack.setType(WsMessageType.MESSAGE_ACCEPTED);
            ack.setMessageId(accepted.getMessageId().toString());
            ack.setClientMessageId(msg.getClientMessageId());
            ack.setLogicalTimestamp(accepted.getLogicalTimestamp());
            send(session, ack);

        } catch (PersistenceUnavailableException e) {
            log.error("Message rejected - persistence unavailable: {}", e.getMessage());
            sendError(session, "DELIVERY_REJECTED",
                    "Message could not be accepted: storage temporarily unavailable");
        } catch (Exception e) {
            log.error("Unexpected error publishing message from '{}': {}", sender, e.getMessage(), e);
            sendError(session, "INTERNAL_ERROR", "An internal error occurred");
        }
    }

    /**
     * Logs recipient message acknowledgements received from the client.
     *
     * @param session  the WebSocket session
     * @param username the username of the acknowledging client
     * @param msg     the acknowledgement message containing the target message ID
     */
    private void handleAck(WebSocketSession session, String username, WsMessage msg) {
        if (msg.getMessageId() == null) {
            log.warn("ACK from user '{}' is missing messageId", username);
            return;
        }
        log.debug("ACK received from user '{}' for message {}", username, msg.getMessageId());
    }

    /**
     * Formats and transmits a WebSocket error message to the client.
     *
     * @param session the target WebSocket session
     * @param code    the application error code
     * @param reason  the descriptive reason for the failure
     */
    private void sendError(WebSocketSession session, String code, String reason) {
        try {
            send(session, WsMessage.error(code, reason));
        } catch (Exception e) {
            log.error("Failed to send error message for session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Serializes and transmits a WebSocket message to the client in a thread-safe manner.
     *
     * @param session the target WebSocket session
     * @param message the message object to serialize and transmit
     * @throws IOException if a transport write error occurs
     */
    private void send(WebSocketSession session, WsMessage message) throws IOException {
        String payload = objectMapper.writeValueAsString(message);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    /**
     * Extracts the authenticated username stored in the session attributes during handshake interception.
     *
     * @param session the WebSocket session
     * @return the authenticated username
     * @throws IllegalStateException if the username attribute is missing
     */
    private String extractUsername(WebSocketSession session) {
        Object attr = session.getAttributes().get(
                WebSocketJwtHandshakeInterceptor.SESSION_ATTR_USERNAME);
        if (attr == null) {
            throw new IllegalStateException(
                    "Username attribute missing from session " + session.getId());
        }
        return attr.toString();
    }
}