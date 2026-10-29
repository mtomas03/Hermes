package it.unibo.hermes.worker.adapter;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.entity.cassandra.ConversationByUser;
import it.unibo.hermes.worker.entity.cassandra.MessageById;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.worker.repository.cassandra.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cassandra adapter managing writes across {@code message_by_id},
 * {@code messages_by_conversation}, and {@code conversations_by_user} tables.
 */
@Component
public class CassandraAdapter {

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

    /**
     * Initialises a new message in {@code PENDING} status across Cassandra tables, ensuring idempotency
     * and preventing delivery status regression in concurrent or duplicate execution scenarios.
     *
     * <p>This method is safe against duplicate Kafka events, retries and concurrent workers:
     * <ol>
     *   <li>The authoritative record in {@code message_by_id} is created using a conditional insert.
     *       If rejected, the existing stored record is reloaded without overwriting its current delivery status.</li>
     *   <li>Immutable message fields are verified against the stored record to detect data conflicts.</li>
     *   <li>The projection row in {@code messages_by_conversation} is conditionally created and participant index
     *       records in {@code conversation_by_user} are written.</li>
     *   <li>The projection's delivery status is reconciled to match the authoritative status.</li>
     * </ol>
     *
     * @param event the message event payload to persist
     * @throws PersistenceUnavailableException if Cassandra is unreachable, immutable fields diverge or persistence fails
     */
    public void persistMessage(MessageEvent event) {
        UUID messageId = UUID.fromString(event.messageId());

        try {
            MessageById authoritative = initializeAuthoritative(event, messageId);
            completeFromAuthoritative(messageId.toString(), authoritative);

            log.debug("Message {} persisted and conversation indexed for {} and {}",
                    messageId, event.senderUsername(), event.recipientUsername());

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
     * @param messageId the message identifier
     * @param newStatus the requested status
     * @throws PersistenceUnavailableException if Cassandra fails or the two tables diverge
     */
    public void updateMessageDeliveryStatus(String messageId, DeliveryStatus newStatus) {
        try {
            UUID id = UUID.fromString(messageId);
            Optional<MessageById> entityOpt = messageByIdRepository.findById(id);
            if (entityOpt.isEmpty()) {
                log.warn("Cannot update status for unknown message {}", messageId);
                return;
            }
            MessageById entity = entityOpt.get();
            DeliveryStatus authoritative = storedStatus(messageId, entity);

            if (authoritative == newStatus) {
                log.debug("Message {} is already {} (duplicate request)", messageId, newStatus);
            } else if (DeliveryStatus.canTransition(authoritative, newStatus)) {
                if (conditionalStatusUpdate(BY_ID_UPDATE, newStatus, id)) {
                    log.debug("Message {} status updated: {} -> {}", messageId, authoritative, newStatus);
                    authoritative = newStatus;
                } else {
                    // another writer moved the message first: continue from what is really stored
                    authoritative = reloadStoredStatus(messageId, id);
                    log.info("Message {} transition {} -> {} rejected: concurrently moved to {}",
                            messageId, entity.getDeliveryStatus(), newStatus, authoritative);
                }
            } else {
                log.debug("Message {} transition {} -> {} not allowed, keeping {}",
                        messageId, authoritative, newStatus, authoritative);
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
    public void reconcileConversationProjection(String messageId) {
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

    /**
     * Checks if a message has already been processed.
     *
     * @param messageId the message identifier
     * @return true if the message has already been processed, false otherwise
     */
    public boolean isAlreadyProcessed(String messageId) {
        UUID id = UUID.fromString(messageId);
        return messageByIdRepository.findById(id)
                .map(e -> !DeliveryStatus.PENDING.name().equals(e.getDeliveryStatus()))
                .orElse(false);
    }

    private MessageById initializeAuthoritative(MessageEvent event, UUID messageId) {
        Optional<MessageById> existing = messageByIdRepository.findById(messageId);
        if (existing.isPresent()) {
            log.debug("Message {} already exists with status {}, not re-initialising",
                    messageId, existing.get().getDeliveryStatus());
            verifyImmutableFields(event, existing.get());
            return existing.get();
        }

        long logicalTimestamp = Objects.requireNonNull(event.logicalTimestamp(),
                "logicalTimestamp is required to persist message " + messageId);
        boolean applied = cassandraOperations.getCqlOperations().execute(BY_ID_INSERT,
                messageId, event.conversationId(), event.senderUsername(), event.recipientUsername(),
                event.content(), logicalTimestamp);
        if (applied) {
            log.debug("Message {} initialised as PENDING", messageId);
            return new MessageById(messageId, event.conversationId(), event.senderUsername(),
                    event.recipientUsername(), event.content(), logicalTimestamp, DeliveryStatus.PENDING.name());
        }

        MessageById winner = messageByIdRepository.findById(messageId).orElseThrow(() ->
                new IllegalStateException("Insert of message " + messageId
                        + " was rejected but the row cannot be read back"));
        log.info("Message {} was initialised concurrently, continuing from status {}",
                messageId, winner.getDeliveryStatus());
        verifyImmutableFields(event, winner);
        return winner;
    }

    private static void verifyImmutableFields(MessageEvent event, MessageById stored) {
        List<String> mismatches = new ArrayList<>();
        if (!Objects.equals(event.conversationId(), stored.getConversationId())) {
            mismatches.add("conversationId");
        }
        if (!Objects.equals(event.senderUsername(), stored.getSenderUsername())) {
            mismatches.add("senderUsername");
        }
        if (!Objects.equals(event.recipientUsername(), stored.getRecipientUsername())) {
            mismatches.add("recipientUsername");
        }
        if (!Objects.equals(event.content(), stored.getContent())) {
            mismatches.add("content");
        }
        if (!Objects.equals(event.logicalTimestamp(), stored.getLogicalTimestamp())) {
            mismatches.add("logicalTimestamp");
        }
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException("Message " + stored.getMessageId()
                    + " already exists with different immutable fields " + mismatches);
        }
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
