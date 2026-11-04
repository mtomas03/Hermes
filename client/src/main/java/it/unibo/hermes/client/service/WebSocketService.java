package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.DeliveryAckDto;
import it.unibo.hermes.client.dto.ErrorDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.OutboundMessageDto;
import it.unibo.hermes.client.dto.SystemMessageDto;
import it.unibo.hermes.client.websocket.StompSessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages the STOMP-over-WebSocket connection lifecycle.
 */
@Service
public class WebSocketService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketService.class);

    private final WebSocketStompClient stompClient;
    private final AppProperties props;
    private final AtomicReference<StompSession> session = new AtomicReference<>();
    private final Set<String> pendingAcks = ConcurrentHashMap.newKeySet();

    // Callbacks injected by ConnectionController
    private volatile Consumer<InboundMessageDto> onMessage = msg -> {
    };
    private volatile Consumer<AckDto> onAck = ack -> {
    };
    private volatile Consumer<ErrorDto> onAppError = err -> {
    };
    private volatile Consumer<SystemMessageDto> onSystemMessage = msg -> {
    };
    private volatile Runnable onConnected = () -> {
    };
    private volatile Consumer<Throwable> onError = e -> {
    };
    private volatile Runnable onDisconnected = () -> {
    };
    private volatile Runnable onReadyToSend = () -> {
    };

    public WebSocketService(WebSocketStompClient stompClient, AppProperties props) {
        this.stompClient = stompClient;
        this.props = props;
    }

    /**
     * Registers the callback to run each time a new message is received from the Gateway.
     *
     * @param callback the callback to run
     */
    public void setOnMessage(Consumer<InboundMessageDto> callback) {
        this.onMessage = callback;
    }

    /**
     * Registers the callback to run each time a new ACK is received from the Gateway.
     *
     * @param callback the callback to run
     */
    public void setOnAck(Consumer<AckDto> callback) {
        this.onAck = callback;
    }

    /**
     * Registers the callback to run each time a new application-level error is received from the Gateway.
     *
     * @param callback the callback to run
     */
    public void setOnAppError(Consumer<ErrorDto> callback) {
        this.onAppError = callback;
    }

    /**
     * Registers the callback to run each time a new system-level message is received from the Gateway.
     *
     * @param callback the callback to run
     */
    public void setOnSystemMessage(Consumer<SystemMessageDto> callback) {
        this.onSystemMessage = callback;
    }

    /**
     * Registers the callback to run each time a new STOMP session is established.
     *
     * @param callback the callback to run
     */
    public void setOnConnected(Runnable callback) {
        this.onConnected = callback;
    }

    /**
     * Registers the callback to run each time a new STOMP session fails to establish or is closed due to an error.
     *
     * @param callback the callback to run
     */
    public void setOnError(Consumer<Throwable> callback) {
        this.onError = callback;
    }

    /**
     * Registers the callback to run each time a STOMP session is closed, either by the Gateway or by the client.
     *
     * @param callback the callback to run
     */
    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }

    /**
     * Registers the callback to run each time a new session is registered:
     * it never runs before the session is ready to send messages.
     *
     * @param callback the callback to run
     */
    public void setOnReadyToSend(Runnable callback) {
        this.onReadyToSend = callback;
    }

    /**
     * Opens the WebSocket connection using the given JWT.
     *
     * @param rawToken  the JWT to use for authentication
     */
    public void connect(String rawToken) {
        String url = props.getWsUrl();
        log.info("Connecting to WebSocket: {}", url);

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        StompHeaders stompConnectHeaders = new StompHeaders();
        stompConnectHeaders.add("Authorization", "Bearer " + rawToken);

        StompSessionHandler handler = new StompSessionHandler(
                props,
                msg -> onMessage.accept(msg),
                ack -> onAck.accept(ack),
                err -> onAppError.accept(err),
                msg -> onSystemMessage.accept(msg),
                () -> onConnected.run(),
                err -> onError.accept(err),
                () -> {
                    session.set(null);
                    onDisconnected.run();
                });

        stompClient.connectAsync(url, httpHeaders, stompConnectHeaders, handler)
                .thenApply(s -> {
                    session.set(s);
                    flushPendingAcks();
                    onReadyToSend.run();
                    return s;
                })
                .exceptionally(e -> {
                    log.error("WebSocket connection failed: {}", e.getMessage());
                    onError.accept(e);
                    return null;
                });
    }

    /**
     * Sends a message via STOMP.
     *
     * @return {@code true} if the message was sent,
     *         {@code false} if the session is not connected or the send failed
     */
    public boolean sendMessage(OutboundMessageDto dto) {
        StompSession s = session.get();
        if (s == null || !s.isConnected()) {
            log.warn("Cannot send: not connected");
            return false;
        }
        try {
            s.send(props.getStompSendDestination(), dto);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not send message {}: {}", dto.messageId(), e.getMessage());
            return false;
        }
    }

    /**
     * Acknowledges a message that is persisted locally.
     *
     * @param messageId the identifier of the message being acknowledged
     */
    public void sendAck(String messageId) {
        pendingAcks.add(messageId);
        flushPendingAcks();
    }

    /**
     * Disconnects the WebSocket connection if it is currently open.
     */
    public void disconnect() {
        pendingAcks.clear();
        StompSession s = session.getAndSet(null);
        if (s != null && s.isConnected()) {
            log.info("Disconnecting WebSocket");
            s.disconnect();
        }
    }

    /**
     * Tells whether the underlying STOMP session is currently open.
     *
     * @return {@code true} if there is an active, connected STOMP session
     */
    public boolean isConnected() {
        StompSession s = session.get();
        return s != null && s.isConnected();
    }

    private void flushPendingAcks() {
        StompSession s = session.get();
        if (s == null || !s.isConnected()) {
            if (!pendingAcks.isEmpty()) {
                log.debug("{} ACK(s) pending until the WebSocket is connected", pendingAcks.size());
            }
            return;
        }
        for (String id : List.copyOf(pendingAcks)) {
            if (!pendingAcks.remove(id)) {
                continue; // transmitted by a concurrent flush
            }
            try {
                s.send(props.getStompSendAckDestination(), new DeliveryAckDto(id));
            } catch (RuntimeException e) {
                log.warn("Could not send ACK for {}, keeping it pending: {}", id, e.getMessage());
                pendingAcks.add(id);
            }
        }
    }
}
