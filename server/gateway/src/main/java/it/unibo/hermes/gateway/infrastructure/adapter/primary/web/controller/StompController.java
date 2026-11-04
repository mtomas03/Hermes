package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.InboundMessagePort;
import it.unibo.hermes.gateway.application.service.InboundMessageService;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.AckDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.DeliveryAckDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ErrorDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageToGatewayDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer.MessageAckProducer;
import it.unibo.hermes.gateway.infrastructure.event.MessageAckEvent;
import it.unibo.hermes.gateway.infrastructure.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller handling STOMP messages sent to the Gateway by clients.
 */
@Controller
public class StompController {

    private static final Logger log = LoggerFactory.getLogger(StompController.class);

    private final InboundMessagePort inboundMessagePort;
    private final MessageAckProducer messageAckProducer;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the STOMP controller.
     *
     * @param inboundMessagePort the inbound port publishing accepted messages to Kafka/Cassandra
     * @param messageAckProducer the producer forwarding client ACK events to Kafka
     * @param messagingTemplate  the template used to push acks/errors back to the sending user
     */
    public StompController(InboundMessageService inboundMessagePort,
                           MessageAckProducer messageAckProducer,
                           SimpMessagingTemplate messagingTemplate) {
        this.inboundMessagePort = inboundMessagePort;
        this.messageAckProducer = messageAckProducer;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Checks whether the given conversation ID is canonical
     * for the given sender and recipient.
     *
     * @param conversationId the conversation ID to check
     * @param sender         the sender's username
     * @param recipient      the recipient's username
     * @return true if the conversation ID is canonical, false otherwise
     */
    public boolean isCanonicalConversationId(
            String conversationId, String sender, String recipient) {
        if (conversationId == null) {
            return false;
        }
        String a = sender.trim().toLowerCase();
        String b = recipient.trim().toLowerCase();
        String expected = a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a;
        return expected.equals(conversationId);
    }

    /**
     * Handles a new message submitted by a client to {@code /app/chat.sendMessage}.
     *
     * <p> On success, publishes an {@link AckDto} to the sender's
     * {@code /user/queue/acks}: it confirms the message has been handed off to
     * Kafka or the Cassandra fallback.
     *
     * @param payload   the message submitted by the client
     * @param principal the authenticated sender, resolved from the STOMP session
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload MessageToGatewayDto payload, Principal principal) {
        String sender = principal.getName();

        if (payload.recipientUsername() == null || payload.recipientUsername().isBlank()) {
            sendError(
                    sender, payload.messageId(),
                    "MISSING_RECIPIENT", "Field 'recipientUsername' is required");
            return;
        }
        if (payload.content() == null || payload.content().isBlank()) {
            sendError(
                    sender, payload.messageId(),
                    "MISSING_CONTENT", "Field 'content' is required");
            return;
        }
        if (sender.equals(payload.recipientUsername())) {
            sendError(
                    sender, payload.messageId(),
                    "SELF_SEND", "Cannot send a message to yourself");
            return;
        }

        if (!isCanonicalConversationId(payload.conversationId(), sender, payload.recipientUsername())) {
            log.warn("Rejected message from '{}': conversationId '{}' does not match the participants",
                    sender, payload.conversationId());
            sendError(
                    sender, payload.messageId(),
                    "INVALID_CONVERSATION", "Field 'conversationId' does not match the conversation participants");
            return;
        }

        try {
            MessageEvent accepted = inboundMessagePort.publish(payload, sender);
            messagingTemplate.convertAndSendToUser(sender, "/queue/acks",
                    new AckDto(accepted.messageId().toString(), "ACCEPTED"));
        } catch (PersistenceUnavailableException e) {
            log.error("Message rejected - persistence unavailable: {}", e.getMessage());
            sendError(sender, payload.messageId(), "DELIVERY_REJECTED",
                    "Message could not be accepted: storage temporarily unavailable");
        } catch (Exception e) {
            log.error("Unexpected error publishing message from '{}': {}", sender, e.getMessage(), e);
            sendError(sender, payload.messageId(), "INTERNAL_ERROR", "An internal error occurred");
        }
    }

    /**
     * Handles an ACK submitted by a client to {@code /app/chat.ack}, confirming it has received a message
     * pushed to its {@code /user/queue/messages} and has persisted it locally.
     *
     * <p> The acknowledging identity always comes from the authenticated {@link Principal},
     * never from the payload.
     *
     * @param payload   the ack submitted by the client
     * @param principal the authenticated recipient
     */
    @MessageMapping("/chat.ack")
    public void ack(@Payload DeliveryAckDto payload, Principal principal) {
        String recipient = principal.getName();

        if (payload.messageId() == null || payload.messageId().isBlank()) {
            log.warn("ACK from user '{}' is missing messageId", recipient);
            sendError(
                    recipient, null,
                    "MISSING_MESSAGE_ID", "Field 'messageId' is required");
            return;
        }

        log.debug("Delivery ACK received from recipient '{}' for message {}", recipient, payload.messageId());

        messageAckProducer.publishAck(new MessageAckEvent(payload.messageId(), recipient));
    }

    /**
     * Catches payload conversion failures that occur before a mapped method can run and
     * reports them to the sending user on {@code /user/queue/errors}.
     *
     * @param ex the exception raised while resolving/handling an inbound STOMP message
     * @return an error payload to be sent to the user's error queue
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorDto handleException(Exception ex) {
        log.warn("STOMP message handling error: {}", ex.getMessage());
        return new ErrorDto("MALFORMED_MESSAGE", "Could not process message", null);
    }

    private void sendError(String username, String messageId, String code, String reason) {
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", new ErrorDto(code, reason, messageId));
    }
}
