package it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.repository;

import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.entity.MessageByConversation;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.entity.MessageByConversationPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for accessing the {@code messages_by_conversation} table.
 */
@Repository
public interface MessageByConversationRepository
        extends CassandraRepository<MessageByConversation, MessageByConversationPrimaryKey> {
}