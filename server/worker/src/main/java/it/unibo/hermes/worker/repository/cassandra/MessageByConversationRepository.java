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
        extends CassandraRepository<MessageByConversation, MessageByConversationPrimaryKey> {}