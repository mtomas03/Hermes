package it.unibo.hermes.gateway.repository.cassandra;

import it.unibo.hermes.gateway.entity.MessageById;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the {@code message_by_id} table.
 */
@Repository
public interface MessageByIdRepository extends CassandraRepository<MessageById, UUID> {
}