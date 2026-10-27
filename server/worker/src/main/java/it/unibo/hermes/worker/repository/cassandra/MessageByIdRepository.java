package it.unibo.hermes.worker.repository.cassandra;

import it.unibo.hermes.worker.entity.cassandra.MessageById;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the {@code message_by_id} table.
 */
@Repository
public interface MessageByIdRepository extends CassandraRepository<MessageById, UUID> {}