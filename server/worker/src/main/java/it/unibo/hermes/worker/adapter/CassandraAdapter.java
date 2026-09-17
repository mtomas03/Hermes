package it.unibo.hermes.worker.adapter;

import edu.umd.cs.findbugs.annotations.NonNull;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.entity.cassandra.ConversationByUser;
import it.unibo.hermes.worker.entity.cassandra.MessageByConversation;
import it.unibo.hermes.worker.entity.cassandra.MessageByConversationPrimaryKey;
import it.unibo.hermes.worker.entity.cassandra.MessageById;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.worker.repository.cassandra.MessageByConversationRepository;
import it.unibo.hermes.worker.repository.cassandra.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cassandra adapter managing writes across {@code message_by_id},
 * {@code messages_by_conversation}, and {@code conversations_by_user} tables.
 */
@Component
public class CassandraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    private final MessageByIdRepository messageByIdRepository;
    private final MessageByConversationRepository conversationMessageRepository;
    private final ConversationByUserRepository conversationByUserRepository;

    public CassandraAdapter(
            MessageByIdRepository messageByIdRepository,
            MessageByConversationRepository messageByConversationRepository,
            ConversationByUserRepository conversationByUserRepository) {
        this.messageByIdRepository = messageByIdRepository;
        this.conversationMessageRepository = messageByConversationRepository;
        this.conversationByUserRepository = conversationByUserRepository;
    }

    @NonNull
    private static MessageByConversation getByConversation(MessageEvent event, UUID messageId, Instant physicalTimestamp) {
        MessageByConversationPrimaryKey key = new MessageByConversationPrimaryKey(
                event.conversationId(),
                event.logicalTimestamp(),
                messageId
        );
        return new MessageByConversation(
                key,
                event.senderUsername(),
                event.recipientUsername(),
                event.content(),
                DeliveryStatus.PENDING.name(),
                physicalTimestamp
        );
    }

    /**
     * Persists a new message as PENDING across Cassandra tables
     * and updates conversation mappings for both participants.
     */
    public void persistMessage(MessageEvent event) {
        UUID messageId = UUID.fromString(event.messageId());
        Instant physicalTimestamp = Instant.now();

        try {
            Optional<MessageById> existing = messageByIdRepository.findById(messageId);
            if (existing.isPresent()) {
                log.debug("Message {} already exists with status {}, skipping insert",
                        messageId, existing.get().getDeliveryStatus());
                return;
            }

            MessageById byId = new MessageById(
                    messageId,
                    event.conversationId(),
                    event.senderUsername(),
                    event.recipientUsername(),
                    event.content(),
                    event.logicalTimestamp(),
                    DeliveryStatus.PENDING.name()
            );
            messageByIdRepository.save(byId);

            MessageByConversation byConversation = getByConversation(event, messageId, physicalTimestamp);
            conversationMessageRepository.save(byConversation);

            ConversationByUser senderIndex = new ConversationByUser(
                    event.senderUsername(),
                    event.conversationId(),
                    event.recipientUsername()
            );
            conversationByUserRepository.save(senderIndex);

            ConversationByUser recipientIndex = new ConversationByUser(
                    event.recipientUsername(),
                    event.conversationId(),
                    event.senderUsername()
            );
            conversationByUserRepository.save(recipientIndex);

            log.debug("Message {} persisted and conversation indexed for {} and {}",
                    messageId, event.senderUsername(), event.recipientUsername());

        } catch (Exception e) {
            log.error("Failed to persist message {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot persist message " + messageId, e);
        }
    }

    /**
     * Updates message delivery status in both tables ensuring forward-only transitions.
     */
    public void updateMessageDeliveryStatus(String messageId, DeliveryStatus newStatus) {
        UUID id = UUID.fromString(messageId);

        try {
            Optional<MessageById> entityOpt = messageByIdRepository.findById(id);
            if (entityOpt.isEmpty()) {
                log.warn("Cannot update status for unknown message {}", messageId);
                return;
            }
            MessageById entity = entityOpt.get();

            if (!isForwardTransition(entity.getDeliveryStatus(), newStatus)) {
                log.warn("Ignoring backward status transition for message {}: {} -> {}",
                        messageId, entity.getDeliveryStatus(), newStatus);
                return;
            }

            messageByIdRepository.updateDeliveryStatus(newStatus.name(), id);
            conversationMessageRepository.updateDeliveryStatus(
                    newStatus.name(),
                    entity.getConversationId(),
                    entity.getLogicalTimestamp(),
                    id
            );

            log.debug("Message {} status updated: {} -> {}", messageId, entity.getDeliveryStatus(), newStatus);

        } catch (Exception e) {
            log.error("Failed to update message delivery status {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot update status for message " + messageId, e);
        }
    }

    public boolean isAlreadyProcessed(String messageId) {
        UUID id = UUID.fromString(messageId);
        return messageByIdRepository.findById(id)
                .map(e -> !DeliveryStatus.PENDING.name().equals(e.getDeliveryStatus()))
                .orElse(false);
    }

    private boolean isForwardTransition(String currentStatusName, DeliveryStatus newStatus) {
        if (currentStatusName == null) return true;
        try {
            DeliveryStatus current = DeliveryStatus.valueOf(currentStatusName);
            return newStatus.ordinal() > current.ordinal();
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognised delivery status '{}', allowing transition", currentStatusName);
            return true;
        }
    }
}
