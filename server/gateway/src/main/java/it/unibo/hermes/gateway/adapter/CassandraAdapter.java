package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.entity.cassandra.ConversationByUser;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.entity.cassandra.MessageById;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByConversationRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

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
     * @return {@code true} if the user is a participant, {@code false} if the user is not
     * @throws PersistenceUnavailableException if Cassandra is unreachable or timed out
     */
    public boolean isParticipant(String username, String conversationId) {
        return readOrUnavailable(
                () -> conversationByUserRepository.existsByUsernameAndConversationId(username, conversationId),
                "participant validation for user '" + username + "' in '" + conversationId + "'");
    }

    /**
     * A message is persisted during the gateway's fallback path (when Kafka is unavailable),
     * so that the same persistent invariants apply as in the worker's path.
     *
     * @param messageByConversation the message entity to be saved
     */
    public void saveFallback(MessageByConversation messageByConversation) {
        String sender = messageByConversation.getSenderUsername();
        String recipient = messageByConversation.getRecipientUsername();
        String conversationId = messageByConversation.getConversationId();

        messageByConversationRepository.save(messageByConversation);
        conversationByUserRepository.save(new ConversationByUser(sender, conversationId, recipient));
        conversationByUserRepository.save(new ConversationByUser(recipient, conversationId, sender));
        messageByIdRepository.save(new MessageById(
                messageByConversation.getMessageId(),
                conversationId,
                sender,
                recipient,
                messageByConversation.getContent(),
                messageByConversation.getLogicalTimestamp(),
                messageByConversation.getDeliveryStatus().name()
        ));

        log.info("Message {} saved to Cassandra via Gateway fallback path",
                messageByConversation.getMessageId());
    }

    /**
     * Retrieves all messages belonging to a conversation.
     *
     * @param conversationId the conversation identifier
     * @return all messages in the conversation
     */
    public List<MessageByConversation> findAllMessages(String conversationId) {
        return readOrUnavailable(
                () -> messageByConversationRepository.findAllByConversationId(conversationId),
                "full message read for conversation '" + conversationId + "'");
    }

    private <T> T readOrUnavailable(Supplier<T> read, String operation) {
        try {
            return read.get();
        } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
            log.warn("Cassandra unavailable during {}: {}", operation, e.getMessage());
            throw new PersistenceUnavailableException("Cassandra unavailable during " + operation, e);
        }
    }
}
