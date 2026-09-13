package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraAdapter;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.SyncResponse;
import it.unibo.hermes.gateway.entity.MessageByConversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private CassandraAdapter cassandraAdapter;

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(cassandraAdapter);
    }

    @Test
    void shouldRejectSyncForNonParticipant() {
        assertThatThrownBy(() -> syncService
                .syncMissing("carol", "alice-bob", -1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User 'carol' is not a participant");

        verify(cassandraAdapter, never()).findAllMessages(any());
        verify(cassandraAdapter, never()).findMessageAfter(any(), anyLong());
    }

    @Test
    void shouldFetchFullHistoryWhenCursorIsNegative() {
        MessageByConversation msg = new MessageByConversation(
                UUID.randomUUID(), "alice-bob", "alice", "bob",
                "Hello Bob!", 1L, MessageStatus.DELIVERED);
        when(cassandraAdapter.findAllMessages("alice-bob"))
                .thenReturn(List.of(msg));

        SyncResponse response = syncService.syncMissing("alice", "alice-bob", -1);

        assertThat(response.conversationId()).isEqualTo("alice-bob");
        assertThat(response.messages()).hasSize(1);
        verify(cassandraAdapter).findAllMessages("alice-bob");
        verify(cassandraAdapter, never()).findMessageAfter(any(), anyLong());
    }

    @Test
    void shouldFetchOnlyMessagesAfterCursorWhenProvided() {
        when(cassandraAdapter.findMessageAfter("alice-bob", 5L))
                .thenReturn(List.of());

        SyncResponse response = syncService.syncMissing("bob", "alice-bob", 5L);

        assertThat(response.messages()).isEmpty();
        verify(cassandraAdapter).findMessageAfter("alice-bob", 5L);
        verify(cassandraAdapter, never()).findAllMessages(any());
    }

    @Test
    void shouldMapMessageFieldsIntoResponseDto() {
        UUID messageId = UUID.randomUUID();
        MessageByConversation msg = new MessageByConversation(
                messageId,
                "alice-bob", "alice", "bob",
                "hi", 2L, MessageStatus.STORED);

        when(cassandraAdapter.findMessageAfter("alice-bob", 0L)).thenReturn(List.of(msg));

        SyncResponse response = syncService.syncMissing("alice", "alice-bob", 0L);

        assertThat(response.messages()).hasSize(1);
        SyncResponse.MessageDto dto = response.messages().getFirst();
        assertThat(dto.messageId()).isEqualTo(messageId.toString());
        assertThat(dto.conversationId()).isEqualTo("alice-bob");
        assertThat(dto.senderUsername()).isEqualTo("alice");
        assertThat(dto.recipientUsername()).isEqualTo("bob");
        assertThat(dto.content()).isEqualTo("hi");
        assertThat(dto.logicalTimestamp()).isEqualTo(2L);
        assertThat(dto.status()).isEqualTo("STORED");
    }
}
