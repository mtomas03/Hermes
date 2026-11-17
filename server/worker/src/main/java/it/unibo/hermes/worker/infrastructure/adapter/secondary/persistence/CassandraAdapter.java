package it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence;

import it.unibo.hermes.worker.application.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.application.port.secondary.MessageStorePort;
import it.unibo.hermes.worker.domain.model.DeliveryStatus;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.domain.model.StoredMessage;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.entity.ConversationByUser;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.entity.MessageById;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.repository.ConversationByUserRepository;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.repository.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Secondary adapter implementing {@link MessageStorePort} on top of Cassandra by managing the
 * {@code message_by_id} table (authoritative), the {@code messages_by_conversation} and
 * {@code conversations_by_user} tables (projections derived from it).
 */
@Component
public class CassandraAdapter implements MessageStorePort {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    private static final String BY_ID_INSERT =
            "INSERT INTO message_by_id (message_id, conversation_id, sender_username, recipient_username, "
                    + "content, logical_timestamp, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING') IF NOT EXISTS";
    private static final String BY_CONVERSATION_INSERT =
            "INSERT INTO messages_by_conversation (conversation_id, logical_timestamp, message_id, "
                    + "sender_username, recipient_username, content, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING') IF NOT EXISTS";
    private static final String BY_ID_UPDATE =
            "UPDATE message_by_id SET delivery_status = ? WHERE message_id = ?";
    private static final String BY_CONVERSATION_UPDATE =
            "UPDATE messages_by_conversation SET delivery_status = ? "
                    + "WHERE conversation_id = ? AND logical_timestamp = ? AND message_id = ?";
    private static final String BY_CONVERSATION_SELECT =
            "SELECT delivery_status FROM messages_by_conversation "
                    + "WHERE conversation_id = ? AND logical_timestamp = ? AND message_id = ?";

    private final MessageByIdRepository messageByIdRepository;
    private final ConversationByUserRepository conversationByUserRepository;
    /**
     * Complements Spring Data Repositories for low-level CQL execution and lightweight transactions (LWT).
     * Required to capture the boolean '[applied]' result of conditional updates (Compare-And-Set) and to construct
     * dynamic 'IF delivery_status IN (...)' queries for state transitions, which Spring Data repositories cannot handle.
     */
    private final CassandraOperations cassandraOperations;

    public CassandraAdapter(
            MessageByIdRepository messageByIdRepository,
            ConversationByUserRepository conversationByUserRepository,
            CassandraOperations cassandraOperations) {
        this.messageByIdRepository = messageByIdRepository;
        this.conversationByUserRepository = conversationByUserRepository;
        this.cassandraOperations = cassandraOperations;
    }

    @Override
    public Optional<StoredMessage> findById(String messageId) {
        UUID id;
        try {
            id = UUID.fromString(messageId);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
        try {
            return messageByIdRepository.findById(id)
                    .map(entity -> toStoredMessage(messageId, entity));
        } catch (Exception e) {
            log.error("Failed to read message {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot read message " + messageId, e);
        }
    }

    /**
     * Creates a new message record if it does not exist yet, or returns the existing one.
     *
     * <p> The record in {@code message_by_id} is created using a conditional insert (lightweight transaction).
     * If it is rejected, because the message exists already, the stored record is reloaded and returned
     * without overwriting its current delivery status.
     *
     * @param message the message to record
     * @return the authoritative record: the one just created or the one that was already stored
     * @throws PersistenceUnavailableException if Cassandra is unreachable or the write fails
     */
    @Override
    public StoredMessage createIfAbsent(Message message) {
        UUID messageId = UUID.fromString(message.messageId());
        try {
            MessageById authoritative = initialiseAuthoritative(message, messageId);
            return toStoredMessage(message.messageId(), authoritative);
        } catch (Exception e) {
            log.error("Failed to persist message {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot persist message " + messageId, e);
        }
    }

    /**
     * Moves the delivery status of a message forward, safely with respect to
     * concurrent writers, duplicated or re-delivered events and partial failures.
     *
     * <p> {@code message_by_id} is the single authoritative record of the status and
     * {@code messages_by_conversation} is a projection of it.
     * <ol>
     *   <li>The authoritative status is changed with a conditional update (lightweight transaction)
     *       that only applies if the stored status is a valid predecessor, as defined by
     *       {@link DeliveryStatus#canTransition}. Two writers that read the same initial status
     *       cannot both succeed and a late event can never undo a later status.</li>
     *   <li>The projection is then aligned to the authoritative status with another conditional
     *       update. This step runs also when the authoritative status was already the requested
     *       one, so a retry or a duplicate event completes an update that stopped halfway.</li>
     * </ol>
     *
     * <p> The two tables are never updated atomically: if the projection step fails, the exception
     * propagates (Kafka retries the event, bounded by the error handler) and the next execution
     * repairs the projection. Until then the sync endpoint may show the previous status
     * (eventual consistency, not atomicity).
     *
     * @param messageId    the message identifier
     * @param targetStatus the requested status
     * @throws PersistenceUnavailableException if Cassandra fails or the two tables diverge
     */
    @Override
    public void advanceStatus(String messageId, DeliveryStatus targetStatus) {
        try {
            UUID id = UUID.fromString(messageId);
            Optional<MessageById> entityOpt = messageByIdRepository.findById(id);
            if (entityOpt.isEmpty()) {
                log.warn("Cannot update status for unknown message {}", messageId);
                return;
            }
            MessageById entity = entityOpt.get();
            DeliveryStatus authoritative = storedStatus(messageId, entity);

            if (authoritative == targetStatus) {
                log.debug("Message {} is already {} (duplicate request)", messageId, targetStatus);
            } else if (DeliveryStatus.canTransition(authoritative, targetStatus)) {
                if (conditionalStatusUpdate(BY_ID_UPDATE, targetStatus, id)) {
                    log.debug("Message {} status updated: {} -> {}", messageId, authoritative, targetStatus);
                    authoritative = targetStatus;
                } else {
                    // another writer moved the message first: continue from what is really stored
                    authoritative = reloadStoredStatus(messageId, id);
                    log.info("Message {} transition {} -> {} rejected: concurrently moved to {}",
                            messageId, entity.getDeliveryStatus(), targetStatus, authoritative);
                }
            } else {
                log.debug("Message {} transition {} -> {} not allowed, keeping {}",
                        messageId, authoritative, targetStatus, authoritative);
            }

            projectToConversation(messageId, entity, authoritative);

        } catch (Exception e) {
            log.error("Failed to update message delivery status {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot update status for message " + messageId, e);
        }
    }

    /**
     * Aligns the projection with the authoritative status, without changing it.
     * Used when an event is recognised as already processed, so that
     * a previous run that stopped between the two writes does not leave the projection behind.
     *
     * <p>A missing projection row or participant index is recreated from the authoritative data.
     *
     * @param messageId the message identifier
     * @throws PersistenceUnavailableException if Cassandra fails or the two tables diverge
     */
    @Override
    public void reconcileProjection(String messageId) {
        try {
            UUID id = UUID.fromString(messageId);
            Optional<MessageById> entityOpt = messageByIdRepository.findById(id);
            if (entityOpt.isEmpty()) {
                return;
            }
            completeFromAuthoritative(messageId, entityOpt.get());
        } catch (Exception e) {
            log.error("Failed to reconcile status projection of message {}: {}", messageId, e.getMessage());
            throw new PersistenceUnavailableException("Cannot reconcile status for message " + messageId, e);
        }
    }

    private MessageById initialiseAuthoritative(Message message, UUID messageId) {
        Optional<MessageById> existing = messageByIdRepository.findById(messageId);
        if (existing.isPresent()) {
            log.debug("Message {} already exists with status {}, not re-initialising",
                    messageId, existing.get().getDeliveryStatus());
            return existing.get();
        }

        long logicalTimestamp = message.logicalTimestamp();
        boolean applied = cassandraOperations.getCqlOperations().execute(BY_ID_INSERT,
                messageId, message.conversationId(), message.senderUsername(), message.recipientUsername(),
                message.content(), logicalTimestamp);
        if (applied) {
            log.debug("Message {} initialised as PENDING", messageId);
            return new MessageById(messageId, message.conversationId(), message.senderUsername(),
                    message.recipientUsername(), message.content(), logicalTimestamp, DeliveryStatus.PENDING.name());
        }

        MessageById winner = messageByIdRepository.findById(messageId).orElseThrow(() ->
                new IllegalStateException("Insert of message " + messageId
                        + " was rejected but the row cannot be read back"));
        log.info("Message {} was initialised concurrently, continuing from status {}",
                messageId, winner.getDeliveryStatus());
        return winner;
    }

    private void completeFromAuthoritative(String messageId, MessageById authoritative) {
        UUID id = authoritative.getMessageId();

        boolean created = cassandraOperations.getCqlOperations().execute(BY_CONVERSATION_INSERT,
                authoritative.getConversationId(), authoritative.getLogicalTimestamp(), id,
                authoritative.getSenderUsername(), authoritative.getRecipientUsername(),
                authoritative.getContent());
        log.debug("Message {} projection row {}", messageId, created ? "created" : "already present");

        conversationByUserRepository.save(new ConversationByUser(
                authoritative.getSenderUsername(),
                authoritative.getConversationId(),
                authoritative.getRecipientUsername()));
        conversationByUserRepository.save(new ConversationByUser(
                authoritative.getRecipientUsername(),
                authoritative.getConversationId(),
                authoritative.getSenderUsername()));

        MessageById current = messageByIdRepository.findById(id).orElseThrow(() ->
                new IllegalStateException("Message " + messageId + " disappeared while completing its rows"));
        projectToConversation(messageId, current, storedStatus(messageId, current));
    }

    private void projectToConversation(String messageId, MessageById entity, DeliveryStatus status) {
        if (DeliveryStatus.predecessorsOf(status).isEmpty()) {
            return; // PENDING: nothing can precede it
        }
        Object[] key = {entity.getConversationId(), entity.getLogicalTimestamp(), UUID.fromString(messageId)};

        if (conditionalStatusUpdate(BY_CONVERSATION_UPDATE, status, key)) {
            log.debug("Message {} projection set to {}", messageId, status);
            return;
        }

        List<Map<String, Object>> rows = cassandraOperations.getCqlOperations()
                .queryForList(BY_CONVERSATION_SELECT, key);
        if (rows.isEmpty()) {
            log.error("Message {} has status {} but no row in messages_by_conversation: tables diverged",
                    messageId, status);
            throw new IllegalStateException("Missing messages_by_conversation row for message " + messageId);
        }
        String projectedName = (String) rows.getFirst().get("delivery_status");
        Optional<DeliveryStatus> projected = DeliveryStatus.fromName(projectedName);
        if (projected.isPresent()
                && (projected.get() == status || DeliveryStatus.canTransition(status, projected.get()))) {
            log.debug("Message {} projection already {} (requested {}), nothing to do",
                    messageId, projectedName, status);
            return;
        }
        log.error("Message {} projection is '{}' but the authoritative status is {}: tables diverged",
                messageId, projectedName, status);
        throw new IllegalStateException("Diverged status for message " + messageId);
    }

    private StoredMessage toStoredMessage(String messageId, MessageById entity) {
        return new StoredMessage(
                new Message(entity.getMessageId().toString(), entity.getConversationId(),
                        entity.getSenderUsername(), entity.getRecipientUsername(),
                        entity.getContent(), entity.getLogicalTimestamp()),
                storedStatus(messageId, entity));
    }

    private DeliveryStatus storedStatus(String messageId, MessageById entity) {
        return DeliveryStatus.fromName(entity.getDeliveryStatus()).orElseThrow(() ->
                new IllegalStateException("Message " + messageId + " has an unrecognised status '"
                        + entity.getDeliveryStatus() + "'"));
    }

    private DeliveryStatus reloadStoredStatus(String messageId, UUID id) {
        MessageById reloaded = messageByIdRepository.findById(id).orElseThrow(() ->
                new IllegalStateException("Message " + messageId + " disappeared while updating its status"));
        return storedStatus(messageId, reloaded);
    }

    private boolean conditionalStatusUpdate(String update, DeliveryStatus newStatus, Object... whereArgs) {
        List<String> predecessors = DeliveryStatus.predecessorsOf(newStatus).stream()
                .map(Enum::name)
                .toList();
        String placeholders = String.join(", ", Collections.nCopies(predecessors.size(), "?"));

        List<Object> args = new ArrayList<>();
        args.add(newStatus.name());
        args.addAll(Arrays.asList(whereArgs));
        args.addAll(predecessors);

        return cassandraOperations.getCqlOperations()
                .execute(update + " IF delivery_status IN (" + placeholders + ")", args.toArray());
    }
}
