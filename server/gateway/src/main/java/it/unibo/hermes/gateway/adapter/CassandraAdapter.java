package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.entity.cassandra.ConversationByUser;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Cassandra adapter for message and conversation persistence and retrieval.
 */
@Component
public class CassandraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    private static final String BY_CONVERSATION_INSERT =
            "INSERT INTO messages_by_conversation (conversation_id, logical_timestamp, message_id, "
                    + "sender_username, recipient_username, content, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";
    private static final String BY_ID_INSERT =
            "INSERT INTO message_by_id (message_id, conversation_id, sender_username, recipient_username, "
                    + "content, logical_timestamp, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";

    private final MessageByConversationRepository messageByConversationRepository;
    private final ConversationByUserRepository conversationByUserRepository;
    private final CassandraOperations cassandraOperations;

    public CassandraAdapter(
            MessageByConversationRepository messageByConversationRepository,
            ConversationByUserRepository conversationByUserRepository,
            CassandraOperations cassandraOperations) {
        this.messageByConversationRepository = messageByConversationRepository;
        this.conversationByUserRepository = conversationByUserRepository;
        this.cassandraOperations = cassandraOperations;
    }

    /**
     * Retrieves all conversations associated with a given user from Cassandra.
     *
     * @param username the username of the requesting user
     * @return list of ConversationByUser entities
     */
    public List<ConversationByUser> findConversationsByUsername(String username) {
        return conversationByUserRepository.findByUsername(username);
    }

    /**
     * Checks whether a user is an authorised participant of a conversation in Cassandra.
     *
     * @param username       the username to verify
     * @param conversationId the conversation identifier
     * @return {@code true} if the user is a participant, {@code false} if the user is not
     * @throws PersistenceUnavailableException if Cassandra is unreachable or timed out
     */
    public boolean isParticipant(String username, String conversationId) {
        return readOrUnavailable(
                () -> conversationByUserRepository.existsByUsernameAndConversationId(username, conversationId),
                "participant validation for user '" + username + "' in '" + conversationId + "'");
    }

    /**
     * A message is persisted during the gateway's fallback path (when Kafka is unavailable),
     * so that the same persistent invariants apply as in the worker's path.
     *
     * @param messageByConversation the message entity to be persisted
     */
    public void saveFallback(MessageByConversation messageByConversation) {
        String sender = messageByConversation.getSenderUsername();
        String recipient = messageByConversation.getRecipientUsername();
        String conversationId = messageByConversation.getConversationId();
        var messageId = messageByConversation.getMessageId();
        long logicalTimestamp = messageByConversation.getLogicalTimestamp();
        String content = messageByConversation.getContent();
        String status = messageByConversation.getDeliveryStatus().name();
        var cql = cassandraOperations.getCqlOperations();

        cql.execute(BY_CONVERSATION_INSERT,
                conversationId, logicalTimestamp, messageId, sender, recipient, content, status);
        conversationByUserRepository.save(new ConversationByUser(sender, conversationId, recipient));
        conversationByUserRepository.save(new ConversationByUser(recipient, conversationId, sender));
        boolean applied = cql.execute(BY_ID_INSERT,
                messageId, conversationId, sender, recipient, content, logicalTimestamp, status);

        if (applied) {
            log.info("Message {} saved to Cassandra via Gateway fallback path", messageId);
        } else {
            log.info("Message {} already exists in Cassandra",
                    messageId);
        }
    }

    /**
     * Retrieves all messages belonging to a conversation.
     *
     * @param conversationId the conversation identifier
     * @return all messages in the conversation
     */
    public List<MessageByConversation> findAllMessages(String conversationId) {
        return readOrUnavailable(
                () -> messageByConversationRepository.findAllByConversationId(conversationId),
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
