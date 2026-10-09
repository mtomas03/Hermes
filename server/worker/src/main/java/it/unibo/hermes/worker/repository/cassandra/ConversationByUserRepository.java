package it.unibo.hermes.worker.repository.cassandra;

import it.unibo.hermes.worker.entity.cassandra.ConversationByUser;
import org.springframework.data.cassandra.core.mapping.MapId;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Cassandra repository accessing the {@code conversations_by_user} table.
 */
@Repository
public interface ConversationByUserRepository extends CassandraRepository<ConversationByUser, MapId> {
}
