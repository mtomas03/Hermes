package it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.repository;

import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.entity.ConversationByUser;
import org.springframework.data.cassandra.core.mapping.MapId;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Cassandra repository accessing the {@code conversations_by_user} table.
 */
@Repository
public interface ConversationByUserRepository extends CassandraRepository<ConversationByUser, MapId> {
}
