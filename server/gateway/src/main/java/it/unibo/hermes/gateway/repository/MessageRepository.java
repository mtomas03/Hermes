package it.unibo.hermes.gateway.repository;

import it.unibo.hermes.gateway.domain.Message;
import it.unibo.hermes.gateway.domain.MessagePrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Cassandra repository accessing the {@code messages_by_conversation} table.
 */
@Repository
public interface MessageRepository
        extends CassandraRepository<Message, MessagePrimaryKey> {

    /**
     * Retrieves messages with a logical timestamp strictly greater than {@code afterTimestamp} in causal order.
     *
     * @param conversationId the conversation identifier[cite: 4]
     * @param afterTimestamp the last logical timestamp stored by the client[cite: 4]
     * @return messages received after the given timestamp, ordered by logical timestamp ascending[cite: 4]
     */
    @Query("SELECT * FROM messages_by_conversation " +
            "WHERE conversation_id = ?0 AND logical_timestamp > ?1 " +
            "ORDER BY logical_timestamp ASC")
    List<Message> findMessagesAfter(String conversationId, long afterTimestamp);

    /**
     * Retrieves the full message history belonging to a conversation, ordered by logical timestamp ascending.
     *
     * @param conversationId the conversation identifier
     * @return all messages belonging to the conversation
     */
    @Query("SELECT * FROM messages_by_conversation " +
            "WHERE conversation_id = ?0 " +
            "ORDER BY logical_timestamp ASC")
    List<Message> findAllByConversationId(String conversationId);
}