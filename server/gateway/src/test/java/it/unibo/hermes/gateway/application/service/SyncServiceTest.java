package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.ConversationStorePort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
import it.unibo.hermes.gateway.domain.exception.ConversationAccessDeniedException;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private ConversationStorePort conversationStore;
    @Mock
    private MessageStorePort messageStore;

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(conversationStore, messageStore);
    }

    @Test
    void shouldRejectSyncForNonParticipant() {
        assertThatThrownBy(() -> syncService.syncConversation("charlie", "alice-bob"))
                .isInstanceOf(ConversationAccessDeniedException.class)
                .hasMessageContaining("User 'charlie' is not a participant");

        verify(messageStore, never()).findAllByConversation(any());
    }

    @Test
    void shouldFetchFullHistoryForAuthorizedParticipant() {
        when(conversationStore.isParticipant("alice", "alice-bob")).thenReturn(true);

        Message msg1 = new Message(UUID.randomUUID(), "alice-bob", "alice", "bob",
                "Hello Bob!", 1L, MessageStatus.DELIVERED);
        Message msg2 = new Message(UUID.randomUUID(), "alice-bob", "bob", "alice",
                "Hi Alice!", 2L, MessageStatus.DELIVERED);
        when(messageStore.findAllByConversation("alice-bob")).thenReturn(List.of(msg1, msg2));

        List<Message> response = syncService.syncConversation("alice", "alice-bob");

        assertThat(response).containsExactly(msg1, msg2);
        verify(messageStore).findAllByConversation("alice-bob");
    }

    @Test
    void shouldRejectUserNamedInTheIdWhenNoMembershipRowExists() {
        when(conversationStore.isParticipant("alice", "alice-bob")).thenReturn(false);

        assertThatThrownBy(() -> syncService.syncConversation("alice", "alice-bob"))
                .isInstanceOf(ConversationAccessDeniedException.class);
        verify(messageStore, never()).findAllByConversation(any());
    }

    @Test
    void shouldRejectMembershipRowForAConversationThatDoesNotNameTheUser() {
        lenient().when(conversationStore.isParticipant("charlie", "alice-bob")).thenReturn(true);

        assertThatThrownBy(() -> syncService.syncConversation("charlie", "alice-bob"))
                .isInstanceOf(ConversationAccessDeniedException.class);
        verify(messageStore, never()).findAllByConversation(any());
    }

    @Test
    void shouldRejectAmbiguousOrMalformedConversationIds() {
        lenient().when(conversationStore.isParticipant(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> syncService.syncConversation("alice", "alice-b-c"))
                .isInstanceOf(ConversationAccessDeniedException.class);
        assertThatThrownBy(() -> syncService.syncConversation("alice", "alice"))
                .isInstanceOf(ConversationAccessDeniedException.class);
        verify(messageStore, never()).findAllByConversation(any());
    }
}
