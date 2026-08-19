package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.domain.Message;
import it.unibo.hermes.gateway.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cassandra adapter for message persistence and retrieval.
 */
@Component
public class CassandraMessageAdapter {

    private static final Logger log = LoggerFactory.getLogger(CassandraMessageAdapter.class);

    private final MessageRepository messageRepository;

    /**
     * Creates the Cassandra adapter with required repository.
     *
     * @param messageRepository the Spring Data repository handling Cassandra queries
     */
    public CassandraMessageAdapter(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Persists a message to the Cassandra database as part of the fallback write pipeline.
     *
     * @param message the message to persist
     */
    public void save(Message message) {
        Message saved = messageRepository.save(message);
        log.debug("Message {} saved to Cassandra (conversationId={})",
                saved.getMessageId(), saved.getConversationId());
    }

    /**
     * Retrieves messages with a logical timestamp greater than {@code afterLogicalTs}, allowing the client
     * to fetch only the new messages received after its last stored message.
     *
     * @param conversationId the conversation identifier
     * @param afterLogicalTs the last logical timestamp stored by the client
     * @return messages received after the given timestamp, in causal order
     */
    public List<Message> findAfter(String conversationId, long afterLogicalTs) {
        return messageRepository.findMessagesAfter(conversationId, afterLogicalTs);
    }

    /**
     * Retrieves all messages belonging to a conversation.
     *
     * @param conversationId the conversation identifier
     * @return all messages in the conversation
     */
    public List<Message> findAll(String conversationId) {
        return messageRepository.findAllByConversationId(conversationId);
    }
}