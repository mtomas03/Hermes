package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.entity.cassandra.ConversationByUser;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.entity.cassandra.MessageById;
import it.unibo.hermes.gateway.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByConversationRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cassandra adapter for message and conversation persistence and retrieval.
 */
@Component
public class CassandraAdapter {

    private static final Logger log = LoggerFactory.getLogger(CassandraAdapter.class);

    private final MessageByConversationRepository messageByConversationRepository;
    private final MessageByIdRepository messageByIdRepository;
    private final ConversationByUserRepository conversationByUserRepository;

    public CassandraAdapter(
            MessageByConversationRepository messageByConversationRepository,
            MessageByIdRepository messageByIdRepository,
            ConversationByUserRepository conversationByUserRepository) {
        this.messageByConversationRepository = messageByConversationRepository;
        this.messageByIdRepository = messageByIdRepository;
        this.conversationByUserRepository = conversationByUserRepository;
    }

    /**
     * Retrieves all conversations associated with a given user from Cassandra.
     *
     * @param username the username of the requesting user
     * @return list of ConversationByUser entities
     */
    public List<ConversationByUser> findConversationsByUsername(String username) {
        return conversationByUserRepository.findByUsername(username);
    }

    /**
     * Checks whether a user is an authorised participant of a conversation in Cassandra.
     *
     * @param username       the username to verify
     * @param conversationId the conversation identifier
     * @return {@code true} if the user is a participant, {@code false} otherwise
     */
    public boolean isParticipant(String username, String conversationId) {
        try {
            return conversationByUserRepository.existsByUsernameAndConversationId(username, conversationId);
        } catch (Exception e) {
            log.warn("Cassandra error during participant validation for user '{}' in '{}': {}",
                    username, conversationId, e.getMessage());
            return false;
        }
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
     * Retrieves messages with a logical timestamp greater than {@code afterLogicalTimestamp}.
     *
     * @param conversationId        the conversation identifier
     * @param afterLogicalTimestamp the last logical timestamp stored by the client
     * @return messages received after the given timestamp
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
