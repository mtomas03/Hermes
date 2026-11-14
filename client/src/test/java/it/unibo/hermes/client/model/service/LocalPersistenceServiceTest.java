package it.unibo.hermes.client.model.service;

import it.unibo.hermes.client.infrastructure.exception.SQLitePersistenceException;
import it.unibo.hermes.client.model.domain.*;
import it.unibo.hermes.client.model.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalPersistenceServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private LocalUserRepository userRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;

    private LocalPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new LocalPersistenceService(messageRepository, conversationRepository, userRepository, authTokenRepository);
    }

    @Test
    void savingAMessageShouldUpsertIt() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);
        when(messageRepository.upsertMessage(msg)).thenReturn(true);

        boolean result = service.saveMessage(msg);

        assertTrue(result);
        verify(messageRepository).upsertMessage(msg);
    }

    @Test
    void savingAMessageShouldPropagateAPersistenceFailure() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);
        when(messageRepository.upsertMessage(msg)).thenReturn(false);

        boolean result = service.saveMessage(msg);

        assertFalse(result);
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

    @Test
    void validStoredTokenShouldBeReturned() {
        AuthToken token = new AuthToken("jwt", Instant.now().plusSeconds(600));
        when(authTokenRepository.find()).thenReturn(Optional.of(token));

        assertEquals(Optional.of(token), service.loadValidAuthToken());
        Mockito.verify(authTokenRepository, Mockito.never()).clear();
    }

    @Test
    void expiredStoredTokenShouldBeDeletedAndNotReturned() {
        AuthToken expired = new AuthToken("jwt", Instant.now().minusSeconds(1));
        when(authTokenRepository.find()).thenReturn(Optional.of(expired));

        assertTrue(service.loadValidAuthToken().isEmpty());
        verify(authTokenRepository).clear();
    }

    @Test
    void clearingTheTokenShouldNotTouchMessagesOrConversations() {
        service.clearAuthToken();

        verify(authTokenRepository).clear();
        Mockito.verifyNoInteractions(messageRepository, conversationRepository);
    }

    @Test
    void savingMessagesBatchShouldDelegateToMessageRepository() {
        List<Message> batch = List.of(
                new Message("m1", "alice-bob",
                        "alice", "bob",
                        "hi", 1L, MessageStatus.SENT),
                new Message("m2", "alice-bob",
                        "bob", "alice",
                        "hi", 2L, MessageStatus.STORED));

        service.saveMessagesBatch(batch);

        verify(messageRepository).upsertAll(batch);
    }

    @Test
    void savingEmptyMessagesBatchShouldBeANoOp() {
        service.saveMessagesBatch(List.of());

        verifyNoInteractions(messageRepository);
    }

    @Test
    void savingConversationsBatchShouldDelegateToConversationRepository() {
        List<Conversation> batch = List.of(
                new Conversation("alice-bob", new User("bob")),
                new Conversation("alice-charlie", new User("charlie")));

        service.saveConversationsBatch(batch);

        verify(conversationRepository).upsertAll(batch);
    }

    @Test
    void updatingMessageStatusesBatchShouldDelegateToMessageRepository() {
        List<String> ids = List.of("m1", "m2");

        service.updateMessageStatusesBatch(ids, MessageStatus.ACKNOWLEDGED);

        verify(messageRepository).updateStatusBatch(ids, MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void batchOperationsWithDatabaseConnectionShouldExecuteInsideTransaction() {
        SQLiteDatabaseConnection db = Mockito.mock(SQLiteDatabaseConnection.class);
        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(0);
            work.run();
            return null;
        }).when(db).inTransaction(any());

        LocalPersistenceService transactionalService = new LocalPersistenceService(
                db, messageRepository, conversationRepository, userRepository, authTokenRepository);

        List<Message> messages = List.of(
                new Message("m1", "alice-bob",
                        "alice", "bob",
                        "hi", 1L, MessageStatus.SENT));
        List<Conversation> conversations = List.of(
                new Conversation("alice-bob", new User("bob")));
        List<String> ids = List.of("m1");

        transactionalService.saveConversationsBatch(conversations);
        transactionalService.saveMessagesBatch(messages);
        transactionalService.updateMessageStatusesBatch(ids, MessageStatus.ACKNOWLEDGED);

        Mockito.verify(db, Mockito.times(3)).inTransaction(any());
        verify(conversationRepository).upsertAll(conversations);
        verify(messageRepository).upsertAll(messages);
        verify(messageRepository).updateStatusBatch(ids, MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void updatingMessageStatusShouldPropagatePersistenceException() {
        doThrow(new SQLitePersistenceException("db error", new SQLException("locked")))
                .when(messageRepository).updateStatus("m1", MessageStatus.ACKNOWLEDGED);

        assertThrows(SQLitePersistenceException.class,
                () -> service.updateMessageStatus("m1", MessageStatus.ACKNOWLEDGED));
    }
}
