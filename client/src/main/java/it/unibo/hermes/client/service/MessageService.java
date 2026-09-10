package it.unibo.hermes.client.service;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.OutboundMessageDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles the lifecycle of a single message.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final WebSocketService wsService;
    private final LocalPersistenceService persistence;

    public MessageService(WebSocketService wsService, LocalPersistenceService persistence) {
        this.wsService = wsService;
        this.persistence = persistence;
    }

    /**
     * Sends a message to {@code recipientUsername} in {@code conversationId}.
     *
     * <p>The message is saved locally first (PENDING), then submitted via WebSocket.
     * If the WebSocket is unavailable the message stays PENDING for retry.
     *
     * @return the optimistically-created Message
     */
    public Message send(String senderUsername,
                        String conversationId,
                        String recipientUsername,
                        String content) {

        String clientMessageId = UUID.randomUUID().toString();
        Message local = new Message(
                clientMessageId,
                conversationId,
                senderUsername,
                recipientUsername,
                content,
                null,
                Instant.now(),
                MessageStatus.PENDING);

        persistence.saveMessage(local);
        log.debug("Saved outbound message locally: {}", clientMessageId);

        OutboundMessageDto dto = new OutboundMessageDto(
                conversationId, senderUsername,
                recipientUsername, content,
                null, Instant.now());
        boolean sent = wsService.sendMessage(dto);
        if (sent) {
            persistence.updateMessageStatus(clientMessageId, MessageStatus.SENT);
            local.setStatus(MessageStatus.SENT);
            log.info("Message submitted via WebSocket: {}", clientMessageId);
        } else {
            log.warn("Message queued locally (WebSocket unavailable): {}", clientMessageId);
        }
        return local;
    }

    /**
     * Converts an inbound DTO received from the server into a domain Message
     * and persists it (idempotent – safe during reconnect replays).
     */
    public Message receiveAndPersist(InboundMessageDto dto) {
        Message msg = new Message(
                dto.messageId(),
                dto.conversationId(),
                dto.senderUsername(),
                dto.recipientUsername(),
                dto.content(),
                dto.logicalTimestamp(),
                dto.physicalTimestamp(),
                MessageStatus.SENT);

        persistence.saveMessage(msg);
        return msg;
    }

    public void acknowledgeDelivery(String messageId) {
        persistence.updateMessageStatus(messageId, MessageStatus.SENT);
    }
}
