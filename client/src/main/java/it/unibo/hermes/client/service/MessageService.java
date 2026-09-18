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
 * Service responsible for sending and receiving messages, managing Lamport clocks,
 * and persisting messages to local storage.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final WebSocketService wsService;
    private final LocalPersistenceService persistenceService;
    private final ConcurrentMap<String, AtomicLong> lamportClocks = new ConcurrentHashMap<>();

    public MessageService(WebSocketService wsService, LocalPersistenceService persistenceService) {
        this.wsService = wsService;
        this.persistenceService = persistenceService;
    }

    /**
     * Sends a message to {@code recipientUsername} in {@code conversationId}.
     *
     * @param senderUsername        the username of the sender (local user)
     * @param conversationId        the unique identifier of the conversation
     * @param recipientUsername     the username of the recipient
     * @param content               the content of the message
     * @return the message with status updated to SENT, if successfully sent
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

        persistenceService.saveMessage(local);
        log.debug("Saved outbound message locally: {}", messageId);

        OutboundMessageDto dto = new OutboundMessageDto(
                messageId, conversationId, senderUsername,
                recipientUsername, content,
                currentClock);

        boolean sent = wsService.sendMessage(dto);
        if (sent) {
            persistenceService.updateMessageStatus(messageId, MessageStatus.SENT);
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
     *
     * @param dto   the inbound message DTO
     * @return the persisted Message entity
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

        persistenceService.saveMessage(msg);
        return msg;
    }

    /**
     * Acknowledges the delivery of a message by updating its status to SENT.
     *
     * @param messageId the unique identifier of the message to acknowledge
     */
    public void acknowledgeDelivery(String messageId) {
        persistenceService.updateMessageStatus(messageId, MessageStatus.SENT);
    }

    /**
     * Synchronises the Lamport clock for a conversation
     * with the maximum clock value from the server.
     *
     * @param conversationId    the unique identifier of the conversation
     * @param remoteMaxClock    the maximum clock value from the server
     */
    public void syncConversationClock(String conversationId, long remoteMaxClock) {
        getConversationClock(conversationId)
                .updateAndGet(current -> Math.max(current, remoteMaxClock));
    }

    private AtomicLong getConversationClock(String conversationId) {
        return lamportClocks.computeIfAbsent(conversationId, cid -> {
            long lastKnownClock = persistenceService.getLastLogicalTimestamp(cid);
            return new AtomicLong(lastKnownClock);
        });
    }
}
