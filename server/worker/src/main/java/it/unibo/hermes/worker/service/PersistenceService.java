package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.entity.ConversationMessageEntity;
import it.unibo.hermes.worker.entity.ConversationMessageKey;
import it.unibo.hermes.worker.entity.MessageByIdEntity;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.repository.ConversationMessageRepository;
import it.unibo.hermes.worker.repository.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for managing persistent storage and lifecycle status updates for messages in Cassandra.
 *
 * <p> Data is written across two Cassandra tables to support distinct access patterns. The {@code message_by_id}
 * table is optimised for single-partition lookups and status updates by message ID, while the {@code messages} table
 * is partitioned by conversation ID and ordered chronologically by logical timestamp for chat history retrieval.
 */
@Service
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final MessageByIdRepository messageByIdRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final CassandraOperations cassandraOperations;

    /**
     * Constructs a new {@code PersistenceService}.
     *
     * @param messageByIdRepository         repository for single-message lookup operations
     * @param conversationMessageRepository repository for conversation-ordered message operations
     * @param cassandraOperations           Spring Data Cassandra template for low-level CQL queries
     */
    public PersistenceService(
            MessageByIdRepository messageByIdRepository,
            ConversationMessageRepository conversationMessageRepository,
            CassandraOperations cassandraOperations) {
        this.messageByIdRepository = messageByIdRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.cassandraOperations = cassandraOperations;
    }

    /**
     * Persists a newly created message with an initial status of {@link DeliveryStatus#PENDING}
     * across both Cassandra tables.
     *
     * <p> If a record with the given {@code messageId} already exists in the database,
     * the insertion is skipped.
     *
     * @param event the creation event carrying message payload, identifiers, and timestamps
     * @throws PersistenceUnavailableException if Cassandra is unreachable or if the write operation fails
     */
    public void persistMessage(MessageCreatedEvent event) {
        UUID messageId = UUID.fromString(event.messageId());
        UUID conversationId = UUID.fromString(event.conversationId());
        Instant physical = Instant.ofEpochMilli(event.physicalTimestamp());

        try {
            Optional<MessageByIdEntity> existing = messageByIdRepository.findById(messageId);
            if (existing.isPresent()) {
                log.debug("Message {} already exists with status {}, skipping insert",
                        messageId, existing.get().getDeliveryStatus());
                return;
            }

            // 1. Insert into message_by_id table
            MessageByIdEntity byId = new MessageByIdEntity(
                    messageId,
                    conversationId,
                    event.senderUsername(),
                    event.recipientUsername(),
                    event.content(),
                    event.logicalTimestamp(),
                    physical,
                    DeliveryStatus.PENDING.name()
            );
            messageByIdRepository.save(byId);

            // 2. Insert into messages table (conversation-ordered)
            ConversationMessageKey key = new ConversationMessageKey(
                    conversationId,
                    event.logicalTimestamp(),
                    messageId
            );
            ConversationMessageEntity byConversation = new ConversationMessageEntity(
                    key,
                    event.senderUsername(),
                    event.recipientUsername(),
                    event.content(),
                    DeliveryStatus.PENDING.name(),
                    physical
            );
            conversationMessageRepository.save(byConversation);

            log.debug("Message {} persisted as PENDING", messageId);

        } catch (Exception e) {
            log.error("Failed to persist message {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot persist message " + messageId, e);
        }
    }

    /**
     * Updates the message's delivery status in its lifecycle across both Cassandra tables.
     *
     * @param messageId the unique identifier of the message to update (UUID string)
     * @param newStatus the new delivery status to apply
     * @throws PersistenceUnavailableException if Cassandra is unreachable or if the update query fails
     */
    public void updateDeliveryStatus(String messageId, DeliveryStatus newStatus) {
        UUID id = UUID.fromString(messageId);

        try {
            Optional<MessageByIdEntity> entityOpt = messageByIdRepository.findById(id);
            if (entityOpt.isEmpty()) {
                log.warn("Cannot update status for unknown message {}", messageId);
                return;
            }
            MessageByIdEntity entity = entityOpt.get();

            if (!isForwardTransition(entity.getDeliveryStatus(), newStatus)) {
                log.warn("Ignoring backward status transition for message {}: {} -> {}",
                        messageId, entity.getDeliveryStatus(), newStatus);
                return;
            }

            // Update message_by_id table
            messageByIdRepository.updateDeliveryStatus(newStatus.name(), id);

            // Update messages table (conversation-ordered)
            cassandraOperations.getCqlOperations().execute(
                    "UPDATE messages SET delivery_status = ? " +
                            "WHERE conversation_id = ? AND logical_timestamp = ? AND message_id = ?",
                    newStatus.name(),
                    entity.getConversationId(),
                    entity.getLogicalTimestamp(),
                    id);

            log.debug("Message {} status updated: {} -> {}", messageId, entity.getDeliveryStatus(), newStatus);

        } catch (PersistenceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update delivery status for message {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot update status for message " + messageId, e);
        }
    }

    /**
     * Checks if a message has already progressed beyond the initial {@link DeliveryStatus#PENDING} status.
     *
     * @param messageId the unique identifier of the message to check (UUID string)
     * @return {@code true} if the message exists and its status is not {@code PENDING}; {@code false} otherwise
     */
    public boolean isAlreadyProcessed(String messageId) {
        UUID id = UUID.fromString(messageId);
        return messageByIdRepository.findById(id)
                .map(e -> !DeliveryStatus.PENDING.name().equals(e.getDeliveryStatus()))
                .orElse(false);
    }

    /**
     * Verifies if advancing from {@code currentStatusName} to {@code newStatus} represents a valid forward transition.
     *
     * @param currentStatusName the name of the current status stored in the database
     * @param newStatus         the candidate new status
     * @return {@code true} if the transition is forward or if the current status is unrecognised; {@code false} otherwise
     */
    private boolean isForwardTransition(String currentStatusName, DeliveryStatus newStatus) {
        if (currentStatusName == null) return true;
        try {
            DeliveryStatus current = DeliveryStatus.valueOf(currentStatusName);
            return newStatus.ordinal() > current.ordinal();
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognised current delivery status '{}', allowing transition", currentStatusName);
            return true;
        }
    }
}