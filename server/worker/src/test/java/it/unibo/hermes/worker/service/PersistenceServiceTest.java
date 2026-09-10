package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.entity.ConversationMessageEntity;
import it.unibo.hermes.worker.entity.MessageByIdEntity;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.repository.ConversationMessageRepository;
import it.unibo.hermes.worker.repository.MessageByIdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.cql.CqlOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistenceServiceTest {

    @Mock
    private MessageByIdRepository messageByIdRepository;
    @Mock
    private ConversationMessageRepository conversationMessageRepository;
    @Mock
    private CassandraOperations cassandraOperations;
    @Mock
    private CqlOperations cqlOperations;

    private PersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new PersistenceService(
                messageByIdRepository, conversationMessageRepository, cassandraOperations);
    }

    private MessageCreatedEvent event() {
        return new MessageCreatedEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L, System.currentTimeMillis());
    }

    @Test
    void shouldInsertIntoBothTablesForNewMessage() {
        MessageCreatedEvent event = event();
        when(messageByIdRepository.findById(UUID.fromString(event.messageId())))
                .thenReturn(Optional.empty());

        persistenceService.persistMessage(event);

        verify(messageByIdRepository).save(any(MessageByIdEntity.class));
        verify(conversationMessageRepository).save(any(ConversationMessageEntity.class));
    }

    @Test
    void shouldSkipInsertWhenMessageAlreadyExists() {
        MessageCreatedEvent event = event();
        MessageByIdEntity existing = new MessageByIdEntity(
                UUID.fromString(event.messageId()), event.conversationId(),
                "alice", "bob", "hi",
                1L, Instant.now(), DeliveryStatus.PENDING.name());
        when(messageByIdRepository.findById(UUID.fromString(event.messageId()))).thenReturn(Optional.of(existing));

        persistenceService.persistMessage(event);

        verify(messageByIdRepository, never()).save(any());
        verify(conversationMessageRepository, never()).save(any());
    }

    @Test
    void shouldWrapRepositoryFailureAsPersistenceUnavailable() {
        MessageCreatedEvent event = event();
        when(messageByIdRepository.findById(any()))
                .thenThrow(new RuntimeException("cassandra down"));

        assertThatThrownBy(() -> persistenceService.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void updatingStatusForUnknownMessageShouldBeANoOp() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        persistenceService.updateDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(messageByIdRepository, never()).updateDeliveryStatus(any(), any());
        verifyNoInteractions(cassandraOperations);
    }

    @Test
    void shouldApplyForwardStatusTransition() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageByIdEntity(
                id, "alice-bob", "alice", "bob", "hi",
                1L, Instant.now(), DeliveryStatus.PENDING.name())));
        when(cassandraOperations.getCqlOperations()).thenReturn(cqlOperations);

        persistenceService.updateDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(messageByIdRepository).updateDeliveryStatus("STORED", id);
        verify(cqlOperations).execute(any(String.class), any(), any(), any(), any());
    }

    @Test
    void shouldIgnoreBackwardStatusTransition() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageByIdEntity(
                id, "alice-bob", "alice", "bob", "hi",
                1L, Instant.now(), DeliveryStatus.DELIVERED.name())));

        // Attempting to change status from DELIVERED to STORED (backward) must be ignored
        persistenceService.updateDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(messageByIdRepository, never()).updateDeliveryStatus(any(), any());
        verifyNoInteractions(cassandraOperations);
    }

    @Test
    void isAlreadyProcessedShouldBeFalseForUnknownMessage() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(persistenceService.isAlreadyProcessed(id.toString())).isFalse();
    }

    @Test
    void isAlreadyProcessedShouldBeFalseWhenStillPending() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageByIdEntity(
                id, "alice-bob", "alice", "bob", "hi",
                1L, Instant.now(), DeliveryStatus.PENDING.name())));

        assertThat(persistenceService.isAlreadyProcessed(id.toString())).isFalse();
    }

    @Test
    void isAlreadyProcessedShouldBeTrueWhenPastPending() {
        UUID id = UUID.randomUUID();
        MessageByIdEntity entity = new MessageByIdEntity(
                id, "alice-bob", "alice", "bob", "hi",
                1L, Instant.now(), DeliveryStatus.STORED.name());
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(persistenceService.isAlreadyProcessed(id.toString())).isTrue();
    }
}
