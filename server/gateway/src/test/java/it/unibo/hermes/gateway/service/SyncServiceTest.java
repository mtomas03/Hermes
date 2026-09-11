package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraMessageAdapter;
import it.unibo.hermes.gateway.domain.Message;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.SyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private CassandraMessageAdapter cassandraAdapter;

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(cassandraAdapter);
    }

    @Test
    void shouldRejectSyncForNonParticipant() {
        assertThatThrownBy(() -> syncService.syncMissing("carol", "alice-bob", -1))
                .isInstanceOf(AccessDeniedException.class);
        verify(cassandraAdapter, never())
                .findAll(any());
        verify(cassandraAdapter, never())
                .findAfter(any(), anyLong());
    }

    @Test
    void shouldFetchFullHistoryWhenCursorIsNegative() {
        Message msg = new Message(
                UUID.randomUUID(), "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.DELIVERED);
        when(cassandraAdapter.findAll("alice-bob"))
                .thenReturn(List.of(msg));

        SyncResponse response = syncService.syncMissing(
                "alice", "alice-bob", -1);

        assertThat(response.conversationId()).isEqualTo("alice-bob");
        assertThat(response.messages()).hasSize(1);
        verify(cassandraAdapter).findAll("alice-bob");
    }

    @Test
    void shouldFetchOnlyMessagesAfterCursorWhenProvided() {
        when(cassandraAdapter.findAfter("alice-bob", 5L))
                .thenReturn(List.of());

        SyncResponse response = syncService.syncMissing(
                "bob", "alice-bob", 5L);

        assertThat(response.messages()).isEmpty();
        verify(cassandraAdapter).findAfter("alice-bob", 5L);
        verify(cassandraAdapter, never()).findAll(any());
    }

    @Test
    void shouldMapMessageFieldsIntoResponseDto() {
        Message msg = new Message(
                UUID.randomUUID(),
                "alice-bob", "alice", "bob",
                "yo", 2L, MessageStatus.STORED);
        when(cassandraAdapter.findAfter("alice-bob", 0L)).thenReturn(List.of(msg));

        SyncResponse response = syncService.syncMissing("alice", "alice-bob", 0L);

        SyncResponse.MessageDto dto = response.messages().getFirst();
        assertThat(dto.senderUsername()).isEqualTo("alice");
        assertThat(dto.recipientUsername()).isEqualTo("bob");
        assertThat(dto.content()).isEqualTo("yo");
        assertThat(dto.status()).isEqualTo("STORED");
    }
}
