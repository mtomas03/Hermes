package it.unibo.hermes.worker.repository.cassandra;

import it.unibo.hermes.worker.entity.cassandra.MessageByConversation;
import it.unibo.hermes.worker.entity.cassandra.MessageByConversationPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for accessing the {@code messages_by_conversation} table.
 */
@Repository
public interface MessageByConversationRepository
        extends CassandraRepository<MessageByConversation, MessageByConversationPrimaryKey> {

    /**
     * Updates the delivery status for a message within a specific conversation partition.
     *
     * @param deliveryStatus   the new status to set
     * @param conversationId   the partition key
     * @param logicalTimestamp the clustering key
     * @param messageId        the clustering key
     */
    @Query("UPDATE messages_by_conversation SET delivery_status = ?0 " +
            "WHERE conversation_id = ?1 AND logical_timestamp = ?2 AND message_id = ?3")
    void updateDeliveryStatus(String deliveryStatus, String conversationId, long logicalTimestamp, UUID messageId);
}