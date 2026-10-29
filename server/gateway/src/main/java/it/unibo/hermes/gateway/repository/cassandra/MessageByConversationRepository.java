package it.unibo.hermes.gateway.repository.cassandra;

import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversationPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Cassandra repository accessing the {@code messages_by_conversation} table.
 */
@Repository
public interface MessageByConversationRepository
        extends CassandraRepository<MessageByConversation, MessageByConversationPrimaryKey> {

    /**
     * Retrieves the full message history belonging to a conversation, ordered by logical timestamp ascending.
     *
     * @param conversationId the conversation identifier
     * @return all messages belonging to the conversation
     */
    @Query("SELECT * FROM messages_by_conversation " +
            "WHERE conversation_id = ?0 " +
            "ORDER BY logical_timestamp ASC")
    List<MessageByConversation> findAllByConversationId(String conversationId);
}