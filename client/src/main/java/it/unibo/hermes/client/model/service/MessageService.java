package it.unibo.hermes.client.model.service;

import it.unibo.hermes.client.infrastructure.exception.MessagePersistenceException;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.dto.InboundMessageDto;
import it.unibo.hermes.client.model.dto.OutboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    public MessageService(WebSocketService wsService, LocalPersistenceService persistenceService) {
        this.wsService = wsService;
        this.persistenceService = persistenceService;
    }

    /**
     * Sends a message to {@code recipientUsername} in {@code conversationId}.
     *
     * <p> The message is first stored in the local outbox (status {@link MessageStatus#PENDING}) and then
     * submitted on the WebSocket. Submitting it does not prove that the Gateway received it,
     * so it stays PENDING until the Gateway answers with its ACK.
     * Until then, it is retransmitted with the same message ID each time the connection is re-established.
     *
     * @param senderUsername    the username of the sender (local user)
     * @param conversationId    the unique identifier of the conversation
     * @param recipientUsername the username of the recipient
     * @param content           the content of the message
     * @return the locally stored message, still PENDING
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

        if (transmit(local)) {
            log.info("Message submitted via WebSocket, awaiting server ACK: {}", messageId);
        } else {
            log.warn("Message kept in the outbox (WebSocket unavailable): {}", messageId);
        }
        return local;
    }

    /**
     * Retransmits the messages of {@code senderUsername} that the server has not
     * confirmed: needed to be called when the STOMP session is ready to send.
     *
     * <p> A message, that was received by the server but whose ACK was lost, is sent again.
     * Messages are sent oldest first and the flush stops at the first failed submission,
     * leaving the remaining ones {@link MessageStatus#PENDING} for the next reconnection.
     *
     * @param senderUsername the local user whose outbox is flushed
     * @return the number of messages submitted
     */
    public int flushOutbox(String senderUsername) {
        if (!isFlushing.compareAndSet(false, true)) {
            log.debug("Outbox flush already in progress");
            return 0;
        }
        try {
            int submitted = 0;
            for (Message pending : persistenceService.loadPendingOutbound(senderUsername)) {
                if (!transmit(pending)) {
                    log.warn("Outbox flush interrupted at message {}: {} submitted, rest kept pending",
                            pending.getMessageId(), submitted);
                    return submitted;
                }
                submitted++;
            }
            if (submitted > 0) {
                log.info("Outbox flushed: {} messages retransmitted, awaiting server ACK", submitted);
            }
            return submitted;
        } finally {
            isFlushing.set(false);
        }
    }

    /**
     * Converts an inbound DTO received from the server into a domain Message and persists it.
     *
     * @param dto the inbound message DTO
     * @return the persisted Message entity
     * @throws MessagePersistenceException if the message could not be persisted
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

        boolean persisted = persistenceService.saveMessage(msg);
        if (!persisted) {
            throw new MessagePersistenceException(dto.messageId());
        }
        return msg;
    }

    /**
     * Acknowledges the delivery of a message by updating its status to {@link MessageStatus#SENT}.
     *
     * @param messageId the unique identifier of the message to acknowledge
     */
    public void acknowledgeDelivery(String messageId) {
        persistenceService.updateMessageStatus(messageId, MessageStatus.SENT);
    }

    /**
     * Marks a message as rejected by updating its status to {@link MessageStatus#FAILED}.
     *
     * @param messageId the unique identifier of the rejected message
     */
    public void markRejected(String messageId) {
        persistenceService.updateMessageStatus(messageId, MessageStatus.FAILED);
    }

    /**
     * Synchronises the Lamport clock for a conversation
     * with the maximum clock value from the server.
     *
     * @param conversationId the unique identifier of the conversation
     * @param remoteMaxClock the maximum clock value from the server
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

    private boolean transmit(Message message) {
        return wsService.sendMessage(new OutboundMessageDto(
                message.getMessageId(), message.getConversationId(), message.getSenderUsername(),
                message.getRecipientUsername(), message.getContent(), message.getLogicalTimestamp()));
    }
}
