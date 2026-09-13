package it.unibo.hermes.worker.adapter;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.entity.MessageById;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.repository.MessageByConversationRepository;
import it.unibo.hermes.worker.repository.MessageByIdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraMessageAdapterTest {

    @Mock
    private MessageByIdRepository messageByIdRepository;

    @Mock
    private MessageByConversationRepository messageByConversationRepository;

    private CassandraMessageAdapter cassandraMessageAdapter;

    @BeforeEach
    void setUp() {
        cassandraMessageAdapter = new CassandraMessageAdapter(
                messageByIdRepository, messageByConversationRepository);
    }

    private MessageEvent createSampleEvent() {
        return new MessageEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L);
    }

    @Test
    void shouldInsertIntoBothTablesForNewMessage() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId)).thenReturn(Optional.empty());

        cassandraMessageAdapter.persistMessage(event);

        verify(messageByIdRepository).save(argThat(byId ->
                byId.getMessageId().equals(messageId) &&
                        byId.getConversationId().equals(event.conversationId()) &&
                        byId.getSenderUsername().equals("alice") &&
                        byId.getRecipientUsername().equals("bob") &&
                        byId.getContent().equals("hi") &&
                        byId.getLogicalTimestamp() == 1L &&
                        DeliveryStatus.PENDING.name().equals(byId.getDeliveryStatus())
        ));
        verify(messageByConversationRepository).save(argThat(byConv ->
                byConv.getKey().getConversationId().equals(event.conversationId()) &&
                        byConv.getKey().getLogicalTimestamp() == 1L &&
                        byConv.getKey().getMessageId().equals(messageId) &&
                        byConv.getSenderUsername().equals("alice") &&
                        byConv.getRecipientUsername().equals("bob") &&
                        byConv.getContent().equals("hi") &&
                        DeliveryStatus.PENDING.name().equals(byConv.getDeliveryStatus())
        ));
    }

    @Test
    void shouldSkipInsertWhenMessageAlreadyExists() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        MessageById existing = new MessageById(
                messageId, event.conversationId(),
                "alice", "bob", "hi",
                1L, DeliveryStatus.PENDING.name());
        when(messageByIdRepository.findById(messageId)).thenReturn(Optional.of(existing));

        cassandraMessageAdapter.persistMessage(event);

        verify(messageByIdRepository, never()).save(any());
        verifyNoInteractions(messageByConversationRepository);
    }

    @Test
    void shouldWrapRepositoryFailureAsPersistenceUnavailable() {
        MessageEvent event = createSampleEvent();
        when(messageByIdRepository.findById(any()))
                .thenThrow(new RuntimeException("Cassandra connection error"));

        assertThatThrownBy(() -> cassandraMessageAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Cannot persist message " + event.messageId());
    }

    @Test
    void updatingStatusForUnknownMessageShouldBeANoOp() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        cassandraMessageAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(messageByIdRepository, never()).updateDeliveryStatus(any(), any());
        verifyNoInteractions(messageByConversationRepository);
    }

    @Test
    void shouldApplyForwardStatusTransition() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.PENDING.name())));

        cassandraMessageAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(messageByIdRepository).updateDeliveryStatus(DeliveryStatus.STORED.name(), id);
        verify(messageByConversationRepository).updateDeliveryStatus(
                DeliveryStatus.STORED.name(),
                "alice-bob",
                1L,
                id
        );
    }

    @Test
    void shouldIgnoreBackwardStatusTransition() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.DELIVERED.name())));

        cassandraMessageAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(messageByIdRepository, never()).updateDeliveryStatus(any(), any());
        verifyNoInteractions(messageByConversationRepository);
    }

    @Test
    void shouldWrapUpdateFailureAsPersistenceUnavailable() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.PENDING.name())));

        doThrow(new RuntimeException("Cassandra update timeout"))
                .when(messageByIdRepository).updateDeliveryStatus(any(), any());

        assertThatThrownBy(() -> cassandraMessageAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Cannot update status for message " + id);
    }

    @Test
    void isAlreadyProcessedShouldBeFalseForUnknownMessage() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(cassandraMessageAdapter.isAlreadyProcessed(id.toString())).isFalse();
    }

    @Test
    void isAlreadyProcessedShouldBeFalseWhenStillPending() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.PENDING.name())));

        assertThat(cassandraMessageAdapter.isAlreadyProcessed(id.toString())).isFalse();
    }

    @Test
    void isAlreadyProcessedShouldBeTrueWhenPastPending() {
        UUID id = UUID.randomUUID();
        MessageById entity = new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.STORED.name());
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(cassandraMessageAdapter.isAlreadyProcessed(id.toString())).isTrue();
    }
}
