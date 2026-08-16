package it.unibo.hermes.worker.repository;

import it.unibo.hermes.worker.entity.ConversationMessageEntity;
import it.unibo.hermes.worker.entity.ConversationMessageKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@code messages} table (conversation-ordered view).
 */
@Repository
public interface ConversationMessageRepository
        extends CassandraRepository<ConversationMessageEntity, ConversationMessageKey> {
}