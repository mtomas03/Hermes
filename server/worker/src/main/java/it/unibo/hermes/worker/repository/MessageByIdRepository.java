package it.unibo.hermes.worker.repository;

import it.unibo.hermes.worker.entity.MessageById;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the {@code message_by_id} table.
 */
@Repository
public interface MessageByIdRepository extends CassandraRepository<MessageById, UUID> {

    /**
     * Updates the delivery status and updated_at timestamp for a specific message.
     *
     * @param deliveryStatus the new status to set
     * @param messageId      the target message ID
     */
    @Query("UPDATE message_by_id SET delivery_status = ?0, updated_at = toTimestamp(now()) WHERE message_id = ?1")
    void updateDeliveryStatus(String deliveryStatus, UUID messageId);
}