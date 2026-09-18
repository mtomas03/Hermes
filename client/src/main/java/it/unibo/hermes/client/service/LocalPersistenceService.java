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

    public void saveMessage(Message msg) {
        messageRepository.insertIfAbsent(msg);
    }

    public List<Message> loadMessages(String conversationId) {
        return messageRepository.findByConversation(conversationId);
    }

    public void updateMessageStatus(String messageId, MessageStatus status) {
        messageRepository.updateStatus(messageId, status);
    }

    public void saveConversation(Conversation conv) {
        conversationRepository.upsert(conv);
    }

    public List<Conversation> loadAllConversations() {
        return conversationRepository.findAll();
    }

    public Optional<Conversation> findConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId);
    }

    public void saveLocalUser(User user) {
        userRepository.save(user);
    }

    public Optional<User> loadLocalUser() {
        return userRepository.findFirst();
    }

    public void clearLocalUser() {
        userRepository.clear();
    }

    public long getLastLogicalTimestamp(String conversationId) {
        return messageRepository.getLastLogicalTimestamp(conversationId);
    }
}
