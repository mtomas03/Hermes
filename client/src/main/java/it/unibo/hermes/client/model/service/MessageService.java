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
    private final AtomicBoolean flushAgain = new AtomicBoolean(false);

    public MessageService(WebSocketService wsService, LocalPersistenceService persistenceService) {
        this.wsService = wsService;
        this.persistenceService = persistenceService;
    }

    /**
     * Sends a message to {@code recipientUsername} in {@code conversationId}.
     *
     * <p> The message is first stored in the local outbox, with status {@link MessageStatus#PENDING},
     * and then submitted on the WebSocket by triggering an outbox flush.
     * This guarantees ordering and prevents race conditions with background reconnect flushes.
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

        flushOutbox(senderUsername);
        return local;
    }

    /**
     * Retransmits the messages of {@code senderUsername} that the server has not
     * confirmed: needed to be called when the STOMP session is ready to send.
     *
     * <p> Uses a coalescing "flush again" flag: if a flush is already active, it registers
     * the request and returns immediately. The active flush will loop back and re-flush,
     * ensuring no reconnect triggers are dropped.
     *
     * @param senderUsername the local user whose outbox is flushed
     * @return the number of messages submitted
     */
    public int flushOutbox(String senderUsername) {
        flushAgain.set(true);
        if (!isFlushing.compareAndSet(false, true)) {
            log.debug("Outbox flush already in progress, coalesced requested pass");
            return 0;
        }

        int totalSubmitted = 0;
        try {
            boolean keepFlushing;
            do {
                flushAgain.set(false);
                int passSubmitted = 0;

                for (Message pending : persistenceService.loadPendingOutbound(senderUsername)) {
                    if (!transmit(pending)) {
                        log.warn("Outbox flush interrupted at message {}: {} submitted this pass",
                                pending.getMessageId(), passSubmitted);
                        flushAgain.set(false);
                        isFlushing.set(false);
                        return totalSubmitted + passSubmitted;
                    }
                    passSubmitted++;
                }
                totalSubmitted += passSubmitted;

                isFlushing.set(false);
                keepFlushing = flushAgain.get() && isFlushing.compareAndSet(false, true);
            } while (keepFlushing);

            if (totalSubmitted > 0) {
                log.info("Outbox flushed: {} messages submitted, awaiting server ACK", totalSubmitted);
            }
            return totalSubmitted;
        } catch (Exception e) {
            isFlushing.set(false);
            throw e;
        }
    }

    /**
     * Converts an inbound message DTO, received from the server,
     * into a domain message and persists it.
     *
     * @param dto the inbound message DTO
     * @return the persisted domain message entity
     * @throws MessagePersistenceException if the message could not be persisted
     */
    public Message receiveAndPersist(InboundMessageDto dto) {
        AtomicLong clock = getConversationClock(dto.conversationId());
        long msgClock = dto.logicalTimestamp() != null ? dto.logicalTimestamp() : 0L;
        clock.updateAndGet(current -> Math.max(current, msgClock) + 1);

        MessageStatus initialStatus = MessageStatus
                .fromServerStatus(dto.messageStatus())
                .orElse(MessageStatus.STORED);

        Message msg = new Message(
                dto.messageId(),
                dto.conversationId(),
                dto.senderUsername(),
                dto.recipientUsername(),
                dto.content(),
                dto.logicalTimestamp(),
                initialStatus);

        boolean persisted = persistenceService.saveMessage(msg);
        if (!persisted) {
            throw new MessagePersistenceException(dto.messageId());
        }
        return msg;
    }

    /**
     * Updates the delivery status of an outbound message based on the status reported
     * by the server in {@code AckDto}, defaulting to {@link MessageStatus#SENT} if omitted.
     *
     * @param messageId    the unique identifier of the message to acknowledge
     * @param serverStatus the status string reported by the gateway, may be {@code null}
     */
    public void acknowledgeDelivery(String messageId, String serverStatus) {
        MessageStatus target = MessageStatus
                .fromServerStatus(serverStatus)
                .filter(s -> s != MessageStatus.PENDING)
                .orElse(MessageStatus.SENT);
        if (target == MessageStatus.FAILED) {
            markRejected(messageId);
            return;
        }
        persistenceService.updateMessageStatus(messageId, target);
    }

    /**
     * Acknowledges the delivery of a message by updating its status to {@link MessageStatus#SENT}.
     *
     * @param messageId the unique identifier of the message to acknowledge
     */
    public void acknowledgeDelivery(String messageId) {
        acknowledgeDelivery(messageId, null);
    }

    /**
     * Marks an inbound message as acknowledged
     * by updating its status to {@link MessageStatus#ACKNOWLEDGED}.
     *
     * @param messageId the unique identifier of the acknowledged message
     */
    public void markAcknowledged(String messageId) {
        persistenceService.updateMessageStatus(messageId, MessageStatus.ACKNOWLEDGED);
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

    /**
     * Resets all conversation Lamport clocks.
     */
    public void resetClocks() {
        lamportClocks.clear();
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
