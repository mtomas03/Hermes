package it.unibo.hermes.client.service;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.OutboundMessageDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles the lifecycle of a single message.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final WebSocketService wsService;
    private final LocalPersistenceService persistence;
    private final ConcurrentMap<String, AtomicLong> lamportClocks = new ConcurrentHashMap<>();

    public MessageService(WebSocketService wsService, LocalPersistenceService persistence) {
        this.wsService = wsService;
        this.persistence = persistence;
    }

    /**
     * Sends a message to {@code recipientUsername} in {@code conversationId}.
     */
    public Message send(String senderUsername,
                        String conversationId,
                        String recipientUsername,
                        String content) {
        AtomicLong clock = getConversationClock(conversationId);
        long currentClock = clock.incrementAndGet();

        String messageId = UUID.randomUUID().toString();
        Message local = new Message(
                messageId,
                conversationId,
                senderUsername,
                recipientUsername,
                content,
                currentClock,
                MessageStatus.PENDING);

        persistence.saveMessage(local);
        log.debug("Saved outbound message locally: {}", messageId);

        OutboundMessageDto dto = new OutboundMessageDto(
                messageId, conversationId, senderUsername,
                recipientUsername, content,
                currentClock);

        boolean sent = wsService.sendMessage(dto);
        if (sent) {
            persistence.updateMessageStatus(messageId, MessageStatus.SENT);
            local.setStatus(MessageStatus.SENT);
            log.info("Message submitted via WebSocket: {}", messageId);
        } else {
            log.warn("Message queued locally (WebSocket unavailable): {}", messageId);
        }
        return local;
    }

    /**
     * Converts an inbound DTO received from the server into a domain Message
     * and persists it (idempotent – safe during reconnect replays).
     */
    public Message receiveAndPersist(InboundMessageDto dto) {
        AtomicLong clock = getConversationClock(dto.conversationId());
        long msgClock = dto.logicalTimestamp() != null ? dto.logicalTimestamp() : 0L;
        clock.updateAndGet(current -> Math.max(current, msgClock) + 1);

        Message msg = new Message(
                dto.messageId(),
                dto.conversationId(),
                dto.senderUsername(),
                dto.recipientUsername(),
                dto.content(),
                dto.logicalTimestamp(),
                MessageStatus.SENT);

        persistence.saveMessage(msg);
        return msg;
    }

    public void acknowledgeDelivery(String messageId) {
        persistence.updateMessageStatus(messageId, MessageStatus.SENT);
    }

    public void syncConversationClock(String conversationId, long remoteMaxClock) {
        getConversationClock(conversationId)
                .updateAndGet(current -> Math.max(current, remoteMaxClock));
    }

    private AtomicLong getConversationClock(String conversationId) {
        return lamportClocks.computeIfAbsent(conversationId, cid -> {
            long lastKnownClock = persistence.getLastLogicalTimestamp(cid);
            return new AtomicLong(lastKnownClock);
        });
    }
}
