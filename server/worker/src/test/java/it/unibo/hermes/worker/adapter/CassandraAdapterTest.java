package it.unibo.hermes.worker.adapter;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.entity.cassandra.MessageById;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.worker.repository.cassandra.MessageByIdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.cql.CqlOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraAdapterTest {

    private static final String BY_ID = "UPDATE message_by_id";
    private static final String BY_CONV = "UPDATE messages_by_conversation";
    private static final String BY_ID_INSERT = "INSERT INTO message_by_id";
    private static final String BY_CONV_INSERT = "INSERT INTO messages_by_conversation";

    @Mock
    private MessageByIdRepository messageByIdRepository;

    @Mock
    private ConversationByUserRepository conversationByUserRepository;

    @Mock
    private CassandraOperations cassandraOperations;

    @Mock
    private CqlOperations cqlOperations;

    private CassandraAdapter cassandraAdapter;

    @BeforeEach
    void setUp() {
        lenient().when(cassandraOperations.getCqlOperations()).thenReturn(cqlOperations);
        cassandraAdapter = new CassandraAdapter(
                messageByIdRepository,
                conversationByUserRepository,
                cassandraOperations);
    }

    private MessageEvent createSampleEvent() {
        return new MessageEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L);
    }

    @Test
    void newMessageIsInitialisedWithAConditionalInsertAndNeverWithAPlainSave() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.PENDING)));
        givenCqlApplied(BY_ID_INSERT, true);
        givenCqlApplied(BY_CONV_INSERT, true);

        cassandraAdapter.persistMessage(event);

        verify(cqlOperations).execute(
                argThat((String cql) -> cql.startsWith(BY_ID_INSERT) && cql.contains("IF NOT EXISTS")
                        && cql.contains("'PENDING'")),
                eq(messageId), eq(event.conversationId()), eq("alice"), eq("bob"), eq("hi"), eq(1L));
        verify(messageByIdRepository, never()).save(any());
    }

    @Test
    void projectionRowIsCreatedConditionallyFromTheAuthoritativeDataAndBothParticipantsAreIndexed() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.PENDING)));
        givenCqlApplied(BY_ID_INSERT, true);
        givenCqlApplied(BY_CONV_INSERT, true);

        cassandraAdapter.persistMessage(event);

        verify(cqlOperations).execute(
                argThat((String cql) -> cql.startsWith(BY_CONV_INSERT) && cql.contains("IF NOT EXISTS")
                        && cql.contains("PENDING")),
                eq(event.conversationId()), eq(1L), eq(messageId), eq("alice"), eq("bob"), eq("hi"));
        verify(conversationByUserRepository).save(argThat(index ->
                index.getUsername().equals("alice")
                        && index.getConversationId().equals(event.conversationId())
                        && index.getOtherParticipant().equals("bob")));
        verify(conversationByUserRepository).save(argThat(index ->
                index.getUsername().equals("bob")
                        && index.getConversationId().equals(event.conversationId())
                        && index.getOtherParticipant().equals("alice")));
    }

    @Test
    void authoritativeRowIsInitialisedBeforeTheProjectionAndTheIndexes() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.PENDING)));
        givenCqlApplied(BY_ID_INSERT, true);

        cassandraAdapter.persistMessage(event);

        InOrder order = inOrder(cqlOperations, conversationByUserRepository);
        order.verify(cqlOperations).execute(startsWith(BY_ID_INSERT), any(Object[].class));
        order.verify(cqlOperations).execute(startsWith(BY_CONV_INSERT), any(Object[].class));
        order.verify(conversationByUserRepository, times(2)).save(any());
    }

    @Test
    void rejectedInsertBecauseAWorkerWonTheRaceKeepsTheAdvancedStatusAndIsNotAnError() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.DELIVERED)));
        givenCqlApplied(BY_ID_INSERT, false);
        givenCqlApplied(BY_CONV_INSERT, false);
        givenCqlApplied(BY_CONV, true);

        assertThatCode(() -> cassandraAdapter.persistMessage(event)).doesNotThrowAnyException();

        verify(messageByIdRepository, never()).save(any());
        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
        // the projection is aligned upwards to DELIVERED, never reset to PENDING
        verify(cqlOperations).execute(startsWith(BY_CONV), eq("DELIVERED"), eq("alice-bob"), eq(1L),
                eq(messageId), eq("PENDING"), eq("STORED"), eq("DELIVERING"));
    }

    @Test
    void rejectedInsertOnAnAcknowledgedMessageNeverRegressesItToPending() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.ACKNOWLEDGED)));
        givenCqlApplied(BY_ID_INSERT, false);
        givenCqlApplied(BY_CONV_INSERT, false);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.persistMessage(event);

        // the only statements on message_by_id are the rejected conditional insert and plain reads
        verify(cqlOperations, times(1)).execute(startsWith(BY_ID_INSERT), any(Object[].class));
        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
        verify(cqlOperations).execute(startsWith(BY_CONV), eq("ACKNOWLEDGED"), any(), any(), any(),
                eq("PENDING"), eq("STORED"), eq("DELIVERING"), eq("DELIVERED"));
        verify(cqlOperations, times(1)).execute(startsWith(BY_CONV), any(Object[].class));
    }

    @Test
    void existingMessageSkipsTheInsertButStillCompletesProjectionAndIndexes() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        givenStored(messageId, DeliveryStatus.PENDING);

        cassandraAdapter.persistMessage(event);

        verify(cqlOperations, never()).execute(startsWith(BY_ID_INSERT), any(Object[].class));
        verify(cqlOperations).execute(startsWith(BY_CONV_INSERT), any(Object[].class));
        verify(conversationByUserRepository, times(2)).save(any());
        verify(messageByIdRepository, never()).save(any());
    }

    @Test
    void duplicateEventOnAnAcknowledgedMessageLeavesTheAuthoritativeStatusUntouched() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        givenStored(messageId, DeliveryStatus.ACKNOWLEDGED);
        givenCqlApplied(BY_CONV_INSERT, false);
        givenCqlApplied(BY_CONV, false);
        givenProjectionRows(List.of(Map.of("delivery_status", "ACKNOWLEDGED")));

        assertThatCode(() -> cassandraAdapter.persistMessage(event)).doesNotThrowAnyException();

        verify(messageByIdRepository, never()).save(any());
        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
        verify(cqlOperations, never()).execute(startsWith(BY_ID_INSERT), any(Object[].class));
    }

    @Test
    void existingProjectionAlreadyAcknowledgedIsNotResetToPending() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        givenStored(messageId, DeliveryStatus.ACKNOWLEDGED);
        givenCqlApplied(BY_CONV_INSERT, false);
        givenCqlApplied(BY_CONV, false);
        givenProjectionRows(List.of(Map.of("delivery_status", "ACKNOWLEDGED")));

        cassandraAdapter.persistMessage(event);

        verify(cqlOperations).execute(startsWith(BY_CONV), eq("ACKNOWLEDGED"), any(), any(), any(),
                eq("PENDING"), eq("STORED"), eq("DELIVERING"), eq("DELIVERED"));
        verify(cqlOperations, times(1)).execute(startsWith(BY_CONV), any(Object[].class));
    }

    @Test
    void projectionCreatedAsPendingWhileTheAuthoritativeStatusAdvancedIsAlignedToIt() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.ACKNOWLEDGED)));
        givenCqlApplied(BY_ID_INSERT, true);
        givenCqlApplied(BY_CONV_INSERT, true);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.persistMessage(event);

        InOrder order = inOrder(cqlOperations);
        order.verify(cqlOperations).execute(startsWith(BY_CONV_INSERT), any(Object[].class));
        order.verify(cqlOperations).execute(startsWith(BY_CONV), eq("ACKNOWLEDGED"), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void immutableFieldMismatchOnAnExistingMessageIsReportedAndNothingIsOverwritten() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId)).thenReturn(Optional.of(new MessageById(
                messageId, event.conversationId(),
                "alice", "bob",
                "a different content", 1L,
                DeliveryStatus.DELIVERED.name())));

        assertThatThrownBy(() -> cassandraAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("content");

        verifyNoInteractions(cqlOperations, conversationByUserRepository);
        verify(messageByIdRepository, never()).save(any());
    }

    @Test
    void immutableFieldMismatchDiscoveredAfterARejectedInsertIsReported() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new MessageById(messageId, event.conversationId(),
                        "charlie", "bob", "hi", 1L, DeliveryStatus.STORED.name())));
        givenCqlApplied(BY_ID_INSERT, false);

        assertThatThrownBy(() -> cassandraAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class)
                .rootCause().hasMessageContaining("senderUsername");

        verify(cqlOperations, never()).execute(startsWith(BY_CONV_INSERT), any(Object[].class));
        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void rejectedInsertWithAnUnreadableRowIsAnErrorToRetryAndNotASilentDuplicate() {
        MessageEvent event = createSampleEvent();
        when(messageByIdRepository.findById(any())).thenReturn(Optional.empty());
        givenCqlApplied(BY_ID_INSERT, false);

        assertThatThrownBy(() -> cassandraAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void cassandraFailureOnTheConditionalInsertIsDistinctFromARejectionAndIsPropagated() {
        MessageEvent event = createSampleEvent();
        when(messageByIdRepository.findById(any())).thenReturn(Optional.empty());
        when(cqlOperations.execute(startsWith(BY_ID_INSERT), any(Object[].class)))
                .thenThrow(new RuntimeException("Cassandra write timeout"));

        assertThatThrownBy(() -> cassandraAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Cannot persist message " + event.messageId())
                .hasRootCauseMessage("Cassandra write timeout");
        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void redeliveryAfterAFailureFollowingTheAuthoritativeInsertCompletesTheMissingRows() {
        MessageEvent event = createSampleEvent();
        UUID messageId = UUID.fromString(event.messageId());
        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.PENDING)));
        givenCqlApplied(BY_ID_INSERT, true);
        givenCqlApplied(BY_CONV_INSERT, true);
        when(conversationByUserRepository.save(any()))
                .thenThrow(new RuntimeException("Cassandra write failed"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> cassandraAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class);

        when(messageByIdRepository.findById(messageId))
                .thenReturn(Optional.of(messageWithStatus(messageId, DeliveryStatus.PENDING)));
        cassandraAdapter.persistMessage(event);

        verify(cqlOperations, times(1)).execute(startsWith(BY_ID_INSERT), any(Object[].class));
        verify(cqlOperations, times(2)).execute(startsWith(BY_CONV_INSERT), any(Object[].class));
        verify(conversationByUserRepository, times(3)).save(any());
        verify(messageByIdRepository, never()).save(any());
    }

    @Test
    void shouldWrapRepositoryFailureAsPersistenceUnavailable() {
        MessageEvent event = createSampleEvent();
        when(messageByIdRepository.findById(any()))
                .thenThrow(new RuntimeException("Cassandra connection error"));

        assertThatThrownBy(() -> cassandraAdapter.persistMessage(event))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Cannot persist message " + event.messageId());
    }

    @Test
    void updatingStatusForUnknownMessageShouldBeANoOp() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verifyNoInteractions(cqlOperations);
    }

    @Test
    void validTransitionUpdatesTheAuthoritativeRowFirstAndThenTheProjection() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.PENDING);
        givenCqlApplied(BY_ID, true);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING);

        InOrder order = inOrder(cqlOperations);
        order.verify(cqlOperations).execute(startsWith(BY_ID),
                eq("DELIVERING"), eq(id), eq("PENDING"), eq("STORED"));
        order.verify(cqlOperations).execute(startsWith(BY_CONV),
                eq("DELIVERING"), eq("alice-bob"), eq(1L), eq(id), eq("PENDING"), eq("STORED"));
    }

    @Test
    void acknowledgedIsAllowedFromEveryEarlierStatus() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.PENDING);
        givenCqlApplied(BY_ID, true);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.ACKNOWLEDGED);

        verify(cqlOperations).execute(startsWith(BY_ID), eq("ACKNOWLEDGED"), eq(id),
                eq("PENDING"), eq("STORED"), eq("DELIVERING"), eq("DELIVERED"));
    }

    @Test
    void lateDeliveringThatLostTheRaceWithTheAckConvergesTheProjectionToAcknowledged() {
        UUID id = UUID.randomUUID();
        // first read: PENDING (both consumers saw it); after the rejected update: ACKNOWLEDGED
        when(messageByIdRepository.findById(id))
                .thenReturn(Optional.of(messageWithStatus(id, DeliveryStatus.PENDING)))
                .thenReturn(Optional.of(messageWithStatus(id, DeliveryStatus.ACKNOWLEDGED)));
        givenCqlApplied(BY_ID, false);   // the database refuses DELIVERING: ACKNOWLEDGED is stored
        givenCqlApplied(BY_CONV, false); // the projection already shows ACKNOWLEDGED
        givenProjectionRows(List.of(Map.of("delivery_status", "ACKNOWLEDGED")));

        assertThatCode(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING))
                .doesNotThrowAnyException();

        // the projection is aligned to what is really stored, never to the stale requested status
        verify(cqlOperations, never()).execute(startsWith(BY_CONV), eq("DELIVERING"),
                any(), any(), any(), any(), any());
        verify(cqlOperations).execute(startsWith(BY_CONV), eq("ACKNOWLEDGED"), eq("alice-bob"), eq(1L), eq(id),
                eq("PENDING"), eq("STORED"), eq("DELIVERING"), eq("DELIVERED"));
    }

    @Test
    void duplicateAckDoesNotTouchTheAuthoritativeRowButStillCompletesTheProjection() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.ACKNOWLEDGED);
        givenCqlApplied(BY_CONV, true); // projection was left behind by an earlier partial update

        cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.ACKNOWLEDGED);

        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
        verify(cqlOperations).execute(startsWith(BY_CONV), eq("ACKNOWLEDGED"), eq("alice-bob"), eq(1L), eq(id),
                eq("PENDING"), eq("STORED"), eq("DELIVERING"), eq("DELIVERED"));
    }

    @Test
    void backwardRequestKeepsTheStoredStatusAndAlignsTheProjectionToIt() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.DELIVERED);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED);

        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
        verify(cqlOperations).execute(startsWith(BY_CONV), eq("DELIVERED"), eq("alice-bob"), eq(1L), eq(id),
                eq("PENDING"), eq("STORED"), eq("DELIVERING"));
    }

    @Test
    void failureOnTheProjectionAfterTheAuthoritativeUpdateIsPropagatedForRetry() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.PENDING);
        givenCqlApplied(BY_ID, true);
        when(cqlOperations.execute(startsWith(BY_CONV), any(Object[].class)))
                .thenThrow(new RuntimeException("write timeout"));

        assertThatThrownBy(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Cannot update status for message " + id);
        verify(cqlOperations).execute(startsWith(BY_ID), any(Object[].class));
    }

    @Test
    void retryAfterAPartialUpdateRepairsTheProjectionEvenThoughTheAuthoritativeRowIsAlreadyUpdated() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id))
                .thenReturn(Optional.of(messageWithStatus(id, DeliveryStatus.PENDING)))
                .thenReturn(Optional.of(messageWithStatus(id, DeliveryStatus.DELIVERING)));
        givenCqlApplied(BY_ID, true);
        when(cqlOperations.execute(startsWith(BY_CONV), any(Object[].class)))
                .thenThrow(new RuntimeException("write timeout"))
                .thenReturn(true);

        assertThatThrownBy(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING))
                .isInstanceOf(PersistenceUnavailableException.class);

        cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING); // the retry

        verify(cqlOperations, times(1)).execute(startsWith(BY_ID), any(Object[].class)); // not applied twice
        verify(cqlOperations, times(2)).execute(startsWith(BY_CONV), eq("DELIVERING"), any(), any(), any(),
                any(), any());
    }

    @Test
    void missingProjectionRowIsReportedAsDivergenceAndNotSilentlyIgnored() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.DELIVERING);
        givenCqlApplied(BY_CONV, false);
        givenProjectionRows(List.of());

        assertThatThrownBy(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void projectionBehindTheAuthoritativeStatusAfterARejectedUpdateIsReportedAsDivergence() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.DELIVERING);
        givenCqlApplied(BY_CONV, false);
        givenProjectionRows(List.of(Map.of("delivery_status", "PENDING")));

        assertThatThrownBy(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.DELIVERING))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void unrecognisedStoredStatusIsNotOverwrittenBlindly() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(
                new MessageById(id, "alice-bob",
                        "alice", "bob",
                        "hi", 1L, "SOMETHING_ELSE")));

        assertThatThrownBy(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.ACKNOWLEDGED))
                .isInstanceOf(PersistenceUnavailableException.class);
        verifyNoInteractions(cqlOperations);
    }

    @Test
    void failureOnTheAuthoritativeUpdateIsWrappedAsPersistenceUnavailable() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.PENDING);
        when(cqlOperations.execute(startsWith(BY_ID), any(Object[].class)))
                .thenThrow(new RuntimeException("Cassandra update timeout"));

        assertThatThrownBy(() -> cassandraAdapter.updateMessageDeliveryStatus(id.toString(), DeliveryStatus.STORED))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Cannot update status for message " + id);
    }

    @Test
    void reconcileAlignsTheProjectionWithoutChangingTheAuthoritativeStatus() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.DELIVERING);
        givenCqlApplied(BY_CONV_INSERT, false);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.reconcileConversationProjection(id.toString());

        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
        verify(cqlOperations).execute(startsWith(BY_CONV), eq("DELIVERING"), eq("alice-bob"), eq(1L), eq(id),
                eq("PENDING"), eq("STORED"));
    }

    @Test
    void reconcileRecreatesAMissingProjectionRowAndIndexesFromTheAuthoritativeData() {
        UUID id = UUID.randomUUID();
        givenStored(id, DeliveryStatus.DELIVERED);
        givenCqlApplied(BY_CONV_INSERT, true);
        givenCqlApplied(BY_CONV, true);

        cassandraAdapter.reconcileConversationProjection(id.toString());

        InOrder order = inOrder(cqlOperations);
        order.verify(cqlOperations).execute(startsWith(BY_CONV_INSERT),
                eq("alice-bob"), eq(1L), eq(id), eq("alice"), eq("bob"), eq("hi"));
        order.verify(cqlOperations).execute(startsWith(BY_CONV), eq("DELIVERED"), any(), any(), any(),
                any(), any(), any());
        verify(conversationByUserRepository, times(2)).save(any());
        verify(cqlOperations, never()).execute(startsWith(BY_ID), any(Object[].class));
    }

    @Test
    void reconcileOfAnUnknownMessageIsANoOp() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        cassandraAdapter.reconcileConversationProjection(id.toString());

        verifyNoInteractions(cqlOperations);
    }

    @Test
    void isAlreadyProcessedShouldBeFalseForUnknownMessage() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(cassandraAdapter.isAlreadyProcessed(id.toString())).isFalse();
    }

    @Test
    void isAlreadyProcessedShouldBeFalseWhenStillPending() {
        UUID id = UUID.randomUUID();
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.PENDING.name())));

        assertThat(cassandraAdapter.isAlreadyProcessed(id.toString())).isFalse();
    }

    @Test
    void isAlreadyProcessedShouldBeTrueWhenPastPending() {
        UUID id = UUID.randomUUID();
        MessageById entity = new MessageById(
                id, "alice-bob", "alice", "bob", "hi",
                1L, DeliveryStatus.STORED.name());
        when(messageByIdRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(cassandraAdapter.isAlreadyProcessed(id.toString())).isTrue();
    }

    private static MessageById messageWithStatus(UUID id, DeliveryStatus status) {
        return new MessageById(id, "alice-bob",
                "alice", "bob",
                "hi", 1L, status.name());
    }

    private void givenStored(UUID id, DeliveryStatus status) {
        when(messageByIdRepository.findById(id))
                .thenReturn(Optional.of(messageWithStatus(id, status)));
    }

    private void givenCqlApplied(String updatePrefix, boolean applied) {
        when(cqlOperations.execute(startsWith(updatePrefix), any(Object[].class)))
                .thenReturn(applied);
    }

    private void givenProjectionRows(List<Map<String, Object>> rows) {
        when(cqlOperations.queryForList(startsWith("SELECT delivery_status FROM messages_by_conversation"),
                any(Object[].class))).thenReturn(rows);
    }
}
