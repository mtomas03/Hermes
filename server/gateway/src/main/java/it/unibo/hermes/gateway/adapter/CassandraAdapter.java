package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.entity.MessageByConversation;
import it.unibo.hermes.gateway.entity.MessageById;
import it.unibo.hermes.gateway.repository.cassandra.MessageByConversationRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cassandra adapter for message persistence and retrieval.
 */
@Component
public class CassandraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    private final MessageByConversationRepository messageByConversationRepository;
    private final MessageByIdRepository messageByIdRepository;

    public CassandraAdapter(
            MessageByConversationRepository messageByConversationRepository,
            MessageByIdRepository messageByIdRepository) {
        this.messageByConversationRepository = messageByConversationRepository;
        this.messageByIdRepository = messageByIdRepository;
    }

    /**
     * Persists a message atomically across message_by_conversation and
     * message_by_id tables during the Gateway fallback pipeline.
     *
     * @param messageByConversation the message entity to be saved
     */
    public void saveFallback(MessageByConversation messageByConversation) {
        MessageById byId = new MessageById(
                messageByConversation.getMessageId(),
                messageByConversation.getConversationId(),
                messageByConversation.getSenderUsername(),
                messageByConversation.getRecipientUsername(),
                messageByConversation.getContent(),
                messageByConversation.getLogicalTimestamp(),
                messageByConversation.getDeliveryStatus().name()
        );
        messageByIdRepository.save(byId);

        messageByConversationRepository.save(messageByConversation);

        log.info("Message {} saved to both Cassandra tables via Gateway fallback path",
                messageByConversation.getMessageId());
    }

    /**
     * Retrieves messages with a logical timestamp greater than {@code afterLogicalTimestamp}, allowing the client
     * to fetch only the new messages received after its last stored message.
     *
     * @param conversationId        the conversation identifier
     * @param afterLogicalTimestamp the last logical timestamp stored by the client
     * @return messages received after the given timestamp, in causal order
     */
    public List<MessageByConversation> findMessageAfter(String conversationId, long afterLogicalTimestamp) {
        return messageByConversationRepository.findMessagesAfter(conversationId, afterLogicalTimestamp);
    }

    /**
     * Retrieves all messages belonging to a conversation.
     *
     * @param conversationId the conversation identifier
     * @return all messages in the conversation
     */
    public List<MessageByConversation> findAllMessages(String conversationId) {
        return messageByConversationRepository.findAllByConversationId(conversationId);
    }
}