package it.unibo.hermes.client.service;

import it.unibo.hermes.client.infrastructure.persistance.ConversationRepository;
import it.unibo.hermes.client.infrastructure.persistance.LocalUserRepository;
import it.unibo.hermes.client.infrastructure.persistance.MessageRepository;
import it.unibo.hermes.client.infrastructure.persistance.SyncCursorRepository;
import it.unibo.hermes.client.model.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalPersistenceServiceTest {

    @Mock
    private MessageRepository messageRepo;
    @Mock
    private ConversationRepository conversationRepo;
    @Mock
    private SyncCursorRepository cursorRepo;
    @Mock
    private LocalUserRepository userRepo;

    private LocalPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new LocalPersistenceService(messageRepo, conversationRepo, cursorRepo, userRepo);
    }

    @Test
    void savingAMessageShouldInsertItAndBumpConversationActivity() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);

        service.saveMessage(msg);

        verify(messageRepo).insertIfAbsent(msg);
    }

    @Test
    void loadingMessagesShouldDelegateToMessageRepository() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);
        when(messageRepo.findByConversation("alice-bob")).thenReturn(List.of(msg));

        List<Message> result = service.loadMessages("alice-bob");

        assertEquals(1, result.size());
        verify(messageRepo).findByConversation("alice-bob");
    }

    @Test
    void loadingMessagesForConversationWithNoHistoryShouldReturnEmptyList() {
        when(messageRepo.findByConversation("conv-empty")).thenReturn(List.of());

        List<Message> result = service.loadMessages("conv-empty");

        assertTrue(result.isEmpty());
    }

    @Test
    void updatingMessageStatusShouldDelegateToMessageRepository() {
        service.updateMessageStatus("m1", MessageStatus.SENT);

        verify(messageRepo).updateStatus("m1", MessageStatus.SENT);
    }

    @Test
    void savingConversationShouldDelegateToConversationRepository() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));

        service.saveConversation(conv);

        verify(conversationRepo).upsert(conv);
    }

    @Test
    void loadingAllConversationsShouldDelegateToConversationRepository() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));
        when(conversationRepo.findAll()).thenReturn(List.of(conv));

        List<Conversation> result = service.loadAllConversations();

        assertEquals(1, result.size());
    }

    @Test
    void findingConversationShouldDelegateToConversationRepository() {
        when(conversationRepo.findByConversationId("alice-bob")).thenReturn(Optional.empty());

        Optional<Conversation> result = service.findConversation("alice-bob");

        assertTrue(result.isEmpty());
        verify(conversationRepo).findByConversationId("alice-bob");
    }

    @Test
    void savingCursorShouldDelegateToCursorRepository() {
        SyncCursor cursor = new SyncCursor("alice-bob", "m1", Instant.now());

        service.saveCursor(cursor);

        verify(cursorRepo).upsert(cursor);
    }

    @Test
    void loadingCursorShouldDelegateToCursorRepository() {
        when(cursorRepo.findByConversation("alice-bob")).thenReturn(Optional.empty());

        Optional<SyncCursor> result = service.loadCursor("alice-bob");

        assertTrue(result.isEmpty());
    }

    @Test
    void savingLocalUserShouldDelegateToUserRepository() {
        User user = new User("alice");

        service.saveLocalUser(user);

        verify(userRepo).save(user);
    }

    @Test
    void loadingLocalUserShouldDelegateToUserRepository() {
        when(userRepo.findFirst()).thenReturn(Optional.of(new User("alice")));

        Optional<User> result = service.loadLocalUser();

        assertTrue(result.isPresent());
    }

    @Test
    void clearingLocalUserShouldDelegateToUserRepository() {
        service.clearLocalUser();

        verify(userRepo).clear();
    }
}
