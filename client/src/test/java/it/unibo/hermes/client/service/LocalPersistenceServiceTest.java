package it.unibo.hermes.client.service;

import it.unibo.hermes.client.repository.ConversationRepository;
import it.unibo.hermes.client.repository.LocalUserRepository;
import it.unibo.hermes.client.repository.MessageRepository;
import it.unibo.hermes.client.model.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalPersistenceServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private LocalUserRepository userRepository;

    private LocalPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new LocalPersistenceService(messageRepository, conversationRepository, userRepository);
    }

    @Test
    void savingAMessageShouldInsertItAndBumpConversationActivity() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);

        service.saveMessage(msg);

        verify(messageRepository).insertIfAbsent(msg);
    }

    @Test
    void loadingMessagesShouldDelegateToMessageRepository() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);
        when(messageRepository.findByConversation("alice-bob")).thenReturn(List.of(msg));

        List<Message> result = service.loadMessages("alice-bob");

        assertEquals(1, result.size());
        verify(messageRepository).findByConversation("alice-bob");
    }

    @Test
    void loadingMessagesForConversationWithNoHistoryShouldReturnEmptyList() {
        when(messageRepository.findByConversation("conv-empty")).thenReturn(List.of());

        List<Message> result = service.loadMessages("conv-empty");

        assertTrue(result.isEmpty());
    }

    @Test
    void updatingMessageStatusShouldDelegateToMessageRepository() {
        service.updateMessageStatus("m1", MessageStatus.SENT);

        verify(messageRepository).updateStatus("m1", MessageStatus.SENT);
    }

    @Test
    void savingConversationShouldDelegateToConversationRepository() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));

        service.saveConversation(conv);

        verify(conversationRepository).upsert(conv);
    }

    @Test
    void loadingAllConversationsShouldDelegateToConversationRepository() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));
        when(conversationRepository.findAll()).thenReturn(List.of(conv));

        List<Conversation> result = service.loadAllConversations();

        assertEquals(1, result.size());
    }

    @Test
    void findingConversationShouldDelegateToConversationRepository() {
        when(conversationRepository.findByConversationId("alice-bob")).thenReturn(Optional.empty());

        Optional<Conversation> result = service.findConversation("alice-bob");

        assertTrue(result.isEmpty());
        verify(conversationRepository).findByConversationId("alice-bob");
    }

    @Test
    void savingLocalUserShouldDelegateToUserRepository() {
        User user = new User("alice");

        service.saveLocalUser(user);

        verify(userRepository).save(user);
    }

    @Test
    void loadingLocalUserShouldDelegateToUserRepository() {
        when(userRepository.findFirst()).thenReturn(Optional.of(new User("alice")));

        Optional<User> result = service.loadLocalUser();

        assertTrue(result.isPresent());
    }

    @Test
    void clearingLocalUserShouldDelegateToUserRepository() {
        service.clearLocalUser();

        verify(userRepository).clear();
    }
}
