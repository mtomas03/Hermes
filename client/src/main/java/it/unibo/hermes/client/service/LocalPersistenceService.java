package it.unibo.hermes.client.service;

import it.unibo.hermes.client.infrastructure.persistance.ConversationRepository;
import it.unibo.hermes.client.infrastructure.persistance.LocalUserRepository;
import it.unibo.hermes.client.infrastructure.persistance.MessageRepository;
import it.unibo.hermes.client.infrastructure.persistance.SyncCursorRepository;
import it.unibo.hermes.client.model.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Facade over the SQLite DAOs.
 */
@Service
public class LocalPersistenceService {

    private final MessageRepository messageRepo;
    private final ConversationRepository conversationRepo;
    private final SyncCursorRepository cursorRepo;
    private final LocalUserRepository userRepo;

    public LocalPersistenceService(MessageRepository messageRepository,
                                   ConversationRepository conversationRepository,
                                   SyncCursorRepository syncCursorRepository,
                                   LocalUserRepository localUserRepository) {
        this.messageRepo = messageRepository;
        this.conversationRepo = conversationRepository;
        this.cursorRepo = syncCursorRepository;
        this.userRepo = localUserRepository;
    }

    public void saveMessage(Message msg) {
        messageRepo.insertIfAbsent(msg);
        // Also bump the conversation's last activity
        conversationRepo.updateLastActivity(msg.getConversationId(), msg.getPhysicalTimestamp());
    }

    public List<Message> loadMessages(String conversationId) {
        return messageRepo.findByConversation(conversationId);
    }

    public Optional<Message> findLastMessage(String conversationId) {
        return messageRepo.findLastMessage(conversationId);
    }

    public void updateMessageStatus(String messageId, MessageStatus status) {
        messageRepo.updateStatus(messageId, status);
    }

    public void saveConversation(Conversation conv) {
        conversationRepo.upsert(conv);
    }

    public List<Conversation> loadAllConversations() {
        return conversationRepo.findAll();
    }

    public Optional<Conversation> findConversation(String conversationId) {
        return conversationRepo.findById(conversationId);
    }

    public void saveCursor(SyncCursor cursor) {
        cursorRepo.upsert(cursor);
    }

    public Optional<SyncCursor> loadCursor(String conversationId) {
        return cursorRepo.findByConversation(conversationId);
    }

    public void saveLocalUser(User user) {
        userRepo.save(user);
    }

    public Optional<User> loadLocalUser() {
        return userRepo.findFirst();
    }

    public void clearLocalUser() {
        userRepo.clear();
    }
}
