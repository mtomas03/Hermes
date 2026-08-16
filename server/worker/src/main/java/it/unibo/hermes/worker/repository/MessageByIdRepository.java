package it.unibo.hermes.worker.repository;

import it.unibo.hermes.worker.entity.MessageByIdEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the {@code message_by_id} table.
 */
@Repository
public interface MessageByIdRepository extends CassandraRepository<MessageByIdEntity, UUID> {

    /**
     * Updates only the delivery status for a specific message.
     */
    @Query("UPDATE message_by_id SET delivery_status = ?0 WHERE message_id = ?1")
    void updateDeliveryStatus(String deliveryStatus, UUID messageId);
}