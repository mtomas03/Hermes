package it.unibo.hermes.client.service;

import it.unibo.hermes.client.repository.ConversationRepository;
import it.unibo.hermes.client.repository.LocalUserRepository;
import it.unibo.hermes.client.repository.MessageRepository;
import it.unibo.hermes.client.model.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for persisting and retrieving messages, conversations
 * and local user data.
 */
@Service
public class LocalPersistenceService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final LocalUserRepository userRepository;

    public LocalPersistenceService(MessageRepository messageRepository,
                                   ConversationRepository conversationRepository,
                                   LocalUserRepository localUserRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = localUserRepository;
    }

    /**
     * Persists a message locally.
     *
     * @param msg the message to persist
     * @return {@code true} if the message is now durably stored,
     *         {@code false} if persistence failed and the caller must not treat the message
     *                       as safely received
     */
    public boolean saveMessage(Message msg) {
        return messageRepository.insertIfAbsent(msg);
    }

    /**
     * Loads all messages of a conversation.
     *
     * @param conversationId    the conversation id
     * @return the list of messages in the conversation
     */
    public List<Message> loadMessages(String conversationId) {
        return messageRepository.findByConversation(conversationId);
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
     * @param conversationId    the conversation id
     * @return the conversation, if found
     */
    public Optional<Conversation> findConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId);
    }

    /**
     * Saves a local user.
     *
     * @param user  the user to save
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
     * Clears the local user data.
     */
    public void clearLocalUser() {
        userRepository.clear();
    }

    /**
     * Retrieves the last logical timestamp for a given conversation.
     *
     * @param conversationId    the conversation id
     * @return the last logical timestamp, or 0 if no messages are found
     */
    public long getLastLogicalTimestamp(String conversationId) {
        return messageRepository.getLastLogicalTimestamp(conversationId);
    }
}
