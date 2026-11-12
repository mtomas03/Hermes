package it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence;

import it.unibo.hermes.gateway.application.port.secondary.ConversationStorePort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Conversation;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.ConversationByUser;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.cassandra.MessageByConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Secondary adapter implementing {@link ConversationStorePort} and {@link MessageStorePort} on top of Cassandra
 * by providing persistence and retrieval methods for conversations and messages.
 */
@Component
public class CassandraAdapter implements ConversationStorePort, MessageStorePort {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    private static final String BY_CONVERSATION_INSERT =
            "INSERT INTO messages_by_conversation (conversation_id, logical_timestamp, message_id, "
                    + "sender_username, recipient_username, content, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";
    private static final String BY_ID_INSERT =
            "INSERT INTO message_by_id (message_id, conversation_id, sender_username, recipient_username, "
                    + "content, logical_timestamp, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";
    private static final String BY_ID_SELECT =
            "SELECT conversation_id, sender_username, recipient_username, content, logical_timestamp, "
                    + "delivery_status FROM message_by_id WHERE message_id = ?";

    private final MessageByConversationRepository messageRepository;
    private final ConversationByUserRepository conversationByUserRepository;
    private final CassandraOperations cassandraOperations;

    public CassandraAdapter(
            MessageByConversationRepository messageRepository,
            ConversationByUserRepository conversationByUserRepository,
            CassandraOperations cassandraOperations) {
        this.messageRepository = messageRepository;
        this.conversationByUserRepository = conversationByUserRepository;
        this.cassandraOperations = cassandraOperations;
    }

    private static Message toDomain(MessageByConversation m) {
        return new Message(
                m.getMessageId(),
                m.getConversationId(),
                m.getSenderUsername(),
                m.getRecipientUsername(),
                m.getContent(),
                m.getLogicalTimestamp(),
                m.getDeliveryStatus());
    }

    @Override
    public List<Conversation> findByUsername(String username) {
        return conversationByUserRepository.findByUsername(username).stream()
                .map(c -> new Conversation(c.getConversationId(), username, c.getOtherParticipant()))
                .toList();
    }

    @Override
    public boolean isParticipant(String username, String conversationId) {
        return readOrUnavailable(
                () -> conversationByUserRepository.existsByUsernameAndConversationId(username, conversationId),
                "participant validation for user '" + username + "' in '" + conversationId + "'");
    }

    /**
     * Persists a message directly to Cassandra when Kafka is unavailable.
     *
     * <p> Cassandra offers no multi-table transaction, so atomicity is not claimed.
     * The writes are ordered and idempotent instead:
     * <ol>
     *   <li> {@code message_by_id} first with a conditional insert:
     *         it is the authoritative record and its result is checked;</li>
     *   <li> if the id already exists, its immutable fields must equal the incoming ones
     *        otherwise the id is being reused for a different message
     *        and the request is rejected;</li>
     *   <li> then the {@code messages_by_conversation} projection and the two membership rows,
     *        using the authoritative status.</li>
     * </ol>
     * A failure, in the middle, leaves the authoritative row without some of its projections; the client's
     * retransmission of the same message id re-enters here, finds the row, verifies it and completes the
     * missing writes. No projection can exist without its authoritative row.
     *
     * @param message the message to be persisted
     * @throws InvalidMessageException if the id already exists with different immutable fields
     */
    @Override
    public void saveFallback(Message message) {
        String sender = message.senderUsername();
        String recipient = message.recipientUsername();
        String conversationId = message.conversationId();
        var messageId = message.messageId();
        long logicalTimestamp = message.logicalTimestamp();
        String content = message.content();
        String status = message.status().name();
        var cql = cassandraOperations.getCqlOperations();

        boolean applied = cql.execute(BY_ID_INSERT,
                messageId, conversationId, sender, recipient, content, logicalTimestamp, status);

        String projectionStatus = status;
        if (applied) {
            log.info("Message {} saved to Cassandra via Gateway fallback path", messageId);
        } else {
            projectionStatus = verifyExistingAuthoritativeRowMatches(message);
            log.info("Message {} already exists in Cassandra (status {}): completing its projections",
                    messageId, projectionStatus);
        }

        boolean projected = cql.execute(BY_CONVERSATION_INSERT,
                conversationId, logicalTimestamp, messageId, sender, recipient, content, projectionStatus);
        if (!projected) {
            log.debug("Projection of message {} already present", messageId);
        }
        conversationByUserRepository.save(new ConversationByUser(sender, conversationId, recipient));
        conversationByUserRepository.save(new ConversationByUser(recipient, conversationId, sender));
    }

    private String verifyExistingAuthoritativeRowMatches(Message incoming) {
        List<Map<String, Object>> rows = cassandraOperations.getCqlOperations().queryForList(BY_ID_SELECT,
                incoming.messageId());
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Message " + incoming.messageId() + " was reported as existing but cannot be read back");
        }
        Map<String, Object> stored = rows.getFirst();
        List<String> mismatches = new ArrayList<>();
        if (!Objects.equals(incoming.conversationId(), stored.get("conversation_id"))) {
            mismatches.add("conversationId");
        }
        if (!Objects.equals(incoming.senderUsername(), stored.get("sender_username"))) {
            mismatches.add("senderUsername");
        }
        if (!Objects.equals(incoming.recipientUsername(), stored.get("recipient_username"))) {
            mismatches.add("recipientUsername");
        }
        if (!Objects.equals(incoming.content(), stored.get("content"))) {
            mismatches.add("content");
        }
        if (!Objects.equals(incoming.logicalTimestamp(), stored.get("logical_timestamp"))) {
            mismatches.add("logicalTimestamp");
        }
        if (!mismatches.isEmpty()) {
            log.warn("Message id {} reused with different {}", incoming.messageId(), mismatches);
            throw new InvalidMessageException("MESSAGE_ID_CONFLICT",
                    "Field 'messageId' is already used by a different message");
        }
        return (String) stored.get("delivery_status");
    }

    @Override
    public List<Message> findAllByConversation(String conversationId) {
        return readOrUnavailable(
                () -> messageRepository.findAllByConversationId(conversationId).stream()
                        .map(CassandraAdapter::toDomain)
                        .toList(),
                "full message read for conversation '" + conversationId + "'");
    }

    private <T> T readOrUnavailable(Supplier<T> read, String operation) {
        try {
            return read.get();
        } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
            log.warn("Cassandra unavailable during {}: {}", operation, e.getMessage());
            throw new PersistenceUnavailableException("Cassandra unavailable during " + operation, e);
        }
    }
}
