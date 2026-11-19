package it.unibo.hermes.client.model.service;

import it.unibo.hermes.client.infrastructure.exception.SQLitePersistenceException;
import it.unibo.hermes.client.model.domain.*;
import it.unibo.hermes.client.model.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for persisting and retrieving messages, conversations
 * and local user data.
 */
@Service
public class LocalPersistenceService {

    private final SQLiteDatabaseConnection db;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final LocalUserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;

    @Autowired
    public LocalPersistenceService(SQLiteDatabaseConnection db,
                                   MessageRepository messageRepository,
                                   ConversationRepository conversationRepository,
                                   LocalUserRepository localUserRepository,
                                   AuthTokenRepository authTokenRepository) {
        this.db = db;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = localUserRepository;
        this.authTokenRepository = authTokenRepository;
    }

    public LocalPersistenceService(MessageRepository messageRepository,
                                   ConversationRepository conversationRepository,
                                   LocalUserRepository localUserRepository,
                                   AuthTokenRepository authTokenRepository) {
        this(null, messageRepository, conversationRepository, localUserRepository, authTokenRepository);
    }

    /**
     * Persists a message locally.
     *
     * @param msg the message to persist
     * @return {@code true} if the message is now durably stored,
     * {@code false} if persistence failed and the caller must not treat the message
     * as safely received
     */
    public boolean saveMessage(Message msg) {
        return messageRepository.upsertMessage(msg);
    }

    /**
     * Same as {@link #saveMessage(Message)}, but first creates
     * the local conversation if it is missing, in the same transaction with the message.
     *
     * @param msg           the received message
     * @param localUsername the local user or {@code null} if unknown
     * @return {@code true} if the message is now durably stored, {@code false} otherwise
     */
    public boolean saveInboundMessage(Message msg, String localUsername) {
        String peer = peerOf(msg, localUsername);
        if (peer == null) {
            return messageRepository.upsertMessage(msg);
        }

        boolean[] stored = {false};
        Runnable op = () -> {
            conversationRepository.insertIfAbsent(
                    new Conversation(msg.getConversationId(), new User(peer)));
            stored[0] = messageRepository.upsertMessage(msg);
        };
        try {
            if (db != null) {
                db.inTransaction(op);
            } else {
                op.run();
            }
        } catch (SQLitePersistenceException e) {
            return false;
        }
        return stored[0];
    }

    /**
     * Loads all messages of a conversation.
     *
     * @param conversationId the conversation id
     * @return the list of messages in the conversation
     */
    public List<Message> loadMessages(String conversationId) {
        return messageRepository.findByConversation(conversationId);
    }

    /**
     * Loads the outbox of a user: messages composed locally and
     * not yet confirmed by the server.
     *
     * @param senderUsername the local user
     * @return the unconfirmed messages, oldest first
     */
    public List<Message> loadPendingOutbound(String senderUsername) {
        return messageRepository.findPendingBySender(senderUsername);
    }

    /**
     * Updates the status of a message.
     *
     * @param messageId the message id
     * @param status    the new message status
     */
    public void updateMessageStatus(String messageId, MessageStatus status) {
        messageRepository.updateStatus(messageId, status);
    }

    /**
     * Persists a conversation locally.
     *
     * @param conv the conversation to persist
     */
    public void saveConversation(Conversation conv) {
        conversationRepository.upsert(conv);
    }

    /**
     * Persists a list of messages in a single atomic transaction.
     *
     * @param messages the messages to persist
     */
    public void saveMessagesBatch(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (db != null) {
            db.inTransaction(() -> messageRepository.upsertAll(messages));
        } else {
            messageRepository.upsertAll(messages);
        }
    }

    /**
     * Persists a list of conversations in a single atomic transaction.
     *
     * @param conversations the conversations to persist
     */
    public void saveConversationsBatch(List<Conversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        if (db != null) {
            db.inTransaction(() -> conversationRepository.upsertAll(conversations));
        } else {
            conversationRepository.upsertAll(conversations);
        }
    }

    /**
     * Updates the status of a list of messages in a single atomic transaction.
     *
     * @param messageIds the message ids
     * @param status     the new message status
     */
    public void updateMessageStatusesBatch(List<String> messageIds, MessageStatus status) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        if (db != null) {
            db.inTransaction(() -> messageRepository.updateStatusBatch(messageIds, status));
        } else {
            messageRepository.updateStatusBatch(messageIds, status);
        }
    }

    /**
     * Loads all conversations.
     *
     * @return the list of conversations
     */
    public List<Conversation> loadAllConversations() {
        return conversationRepository.findAll();
    }

    /**
     * Loads a conversation by its id.
     *
     * @param conversationId the conversation id
     * @return the conversation, if found
     */
    public Optional<Conversation> findConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId);
    }

    /**
     * Saves a local user.
     *
     * @param user the user to save
     */
    public void saveLocalUser(User user) {
        userRepository.save(user);
    }

    /**
     * Loads the first local user.
     *
     * @return an Optional containing the first user, or empty if none found
     */
    public Optional<User> loadLocalUser() {
        return userRepository.findFirst();
    }

    /**
     * Persists the JWT so it can be reused after a restart, until it expires.
     *
     * @param token the token to persist
     */
    public void saveAuthToken(AuthToken token) {
        authTokenRepository.save(token);
    }

    /**
     * Loads the stored JWT if it is still valid. An expired token is deleted and not returned.
     *
     * @return an Optional containing the token if it is still valid, or empty if none found or expired
     */
    public Optional<AuthToken> loadValidAuthToken() {
        Optional<AuthToken> stored = authTokenRepository.find();
        if (stored.isPresent() && stored.get().isExpired()) {
            authTokenRepository.clear();
            return Optional.empty();
        }
        return stored;
    }

    /**
     * Returns the number of delivery attempts recorded
     * for a {@code PENDING} message.
     *
     * @param messageId the identifier of the message
     * @return the number of attempts
     */
    public int getDeliveryAttempts(String messageId) {
        return messageRepository.getDeliveryAttempts(messageId);
    }

    /**
     * Increments the delivery attempt count
     * for a {@code PENDING} message.
     *
     * @param messageId the identifier of the message
     */
    public void incrementDeliveryAttempts(String messageId) {
        messageRepository.incrementDeliveryAttempts(messageId);
    }

    /**
     * Deletes every persisted token.
     */
    public void clearAuthToken() {
        authTokenRepository.clear();
    }

    /**
     * Clears the local user data.
     */
    public void clearLocalUser() {
        userRepository.clear();
        messageRepository.clear();
        conversationRepository.clear();
    }

    /**
     * Retrieves the last logical timestamp for a given conversation.
     *
     * @param conversationId the conversation id
     * @return the last logical timestamp, or 0 if no messages are found
     */
    public long getLastLogicalTimestamp(String conversationId) {
        return messageRepository.getLastLogicalTimestamp(conversationId);
    }

    private String peerOf(Message msg, String localUsername) {
        if (localUsername == null) {
            return null;
        } else if (localUsername.equalsIgnoreCase(msg.getRecipientUsername())) {
            return msg.getSenderUsername();
        } else if (localUsername.equalsIgnoreCase(msg.getSenderUsername())) {
            return msg.getRecipientUsername();
        } else {
            return null;
        }
    }
}
