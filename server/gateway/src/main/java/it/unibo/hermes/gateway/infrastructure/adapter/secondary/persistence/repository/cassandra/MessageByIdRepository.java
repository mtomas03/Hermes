package it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.cassandra;

import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.MessageById;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the {@code message_by_id} table.
 */
@Repository
public interface MessageByIdRepository extends CassandraRepository<MessageById, UUID> {
}