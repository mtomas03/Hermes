package it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence;

import it.unibo.hermes.gateway.application.port.secondary.ConversationStorePort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * Secondary adapter implementing {@link ConversationStorePort} and {@link MessageStorePort} on top of Cassandra
 * by providing persistence and retrieval methods for conversations and messages.
 */
@Component
public class CassandraAdapter implements ConversationStorePort, MessageStorePort {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    /**
     * CQL statement to insert a message into the messages_by_conversation table, if it does not already exist.
     */
    private static final String BY_CONVERSATION_INSERT =
            "INSERT INTO messages_by_conversation (conversation_id, logical_timestamp, message_id, "
                    + "sender_username, recipient_username, content, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";

    /**
     * CQL statement to insert a message into the message_by_id table, if it does not already exist.
     */
    private static final String BY_ID_INSERT =
            "INSERT INTO message_by_id (message_id, conversation_id, sender_username, recipient_username, "
                    + "content, logical_timestamp, delivery_status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";

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
