package it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence;

import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Conversation;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.ConversationByUser;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.cassandra.MessageByConversationRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.cql.CqlOperations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraAdapterTest {

    @Mock
    private MessageByConversationRepository messageByConversationRepository;

    @Mock
    private CassandraOperations cassandraOperations;

    @Mock
    private CqlOperations cqlOperations;

    @Mock
    private ConversationByUserRepository conversationByUserRepository;

    @InjectMocks
    private CassandraAdapter cassandraAdapter;

    @BeforeEach
    void setUp() {
        lenient().when(cassandraOperations.getCqlOperations()).thenReturn(cqlOperations);
    }

    @Test
    void shouldFindConversationsByUsername() {
        ConversationByUser conv = new ConversationByUser("alice", "alice-bob", "bob");
        when(conversationByUserRepository.findByUsername("alice")).thenReturn(List.of(conv));

        List<Conversation> result = cassandraAdapter.findByUsername("alice");

        assertThat(result).containsExactly(new Conversation("alice-bob", "alice", "bob"));
        verify(conversationByUserRepository).findByUsername("alice");
    }

    @Test
    void shouldCheckIfUserIsParticipant() {
        when(conversationByUserRepository.existsByUsernameAndConversationId(
                "alice", "alice-bob")).thenReturn(true);

        boolean isParticipant = cassandraAdapter.isParticipant("alice", "alice-bob");

        assertThat(isParticipant).isTrue();
        verify(conversationByUserRepository).existsByUsernameAndConversationId("alice", "alice-bob");
    }

    @Test
    void fallbackCreatesBothRowsWithConditionalInsertsAndNeverWithAPlainSave() {
        UUID messageId = UUID.randomUUID();
        Message fallbackMessage = new Message(
                messageId, "alice-bob",
                "alice", "bob",
                "Fallback content", 1L, MessageStatus.STORED);
        when(cqlOperations.execute(startsWith("INSERT"), any(Object[].class)))
                .thenReturn(true);

        cassandraAdapter.saveFallback(fallbackMessage);

        verify(cqlOperations).execute(
                argThat((String cql) -> cql.startsWith("INSERT INTO messages_by_conversation")
                        && cql.contains("IF NOT EXISTS")),
                eq("alice-bob"), eq(1L), eq(messageId),
                eq("alice"), eq("bob"), eq("Fallback content"),
                eq("STORED"));
        verify(cqlOperations).execute(
                argThat((String cql) -> cql.startsWith("INSERT INTO message_by_id")
                        && cql.contains("IF NOT EXISTS")),
                eq(messageId), eq("alice-bob"), eq("alice"),
                eq("bob"), eq("Fallback content"), eq(1L),
                eq("STORED"));
        verify(messageByConversationRepository, never()).save(any());
        verify(cqlOperations, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void fallbackWritesTheAuthoritativeRowFirstThenTheProjectionThenTheMembership() {
        Message message = fallbackMessage(UUID.randomUUID());
        when(cqlOperations.execute(startsWith("INSERT"), any(Object[].class))).thenReturn(true);

        cassandraAdapter.saveFallback(message);

        ArgumentCaptor<ConversationByUser> indexes = ArgumentCaptor.forClass(ConversationByUser.class);
        InOrder order = inOrder(cqlOperations, conversationByUserRepository);
        order.verify(cqlOperations).execute(startsWith("INSERT INTO message_by_id"), any(Object[].class));
        order.verify(cqlOperations).execute(startsWith("INSERT INTO messages_by_conversation"),
                any(Object[].class));
        order.verify(conversationByUserRepository, times(2)).save(indexes.capture());

        assertThat(indexes.getAllValues())
                .extracting(ConversationByUser::getUsername,
                        ConversationByUser::getConversationId,
                        ConversationByUser::getOtherParticipant)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("alice", "alice-bob", "bob"),
                        Tuple.tuple("bob", "alice-bob", "alice"));
    }

    @Test
    void failureOfTheAuthoritativeWriteLeavesNoProjectionAndNoMembership() {
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("no node available"));

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(cqlOperations, never()).execute(startsWith("INSERT INTO messages_by_conversation"),
                any(Object[].class));
        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void failureOfTheProjectionAfterTheAuthoritativeWriteIsPropagatedSoTheClientCanRetransmit() {
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenReturn(true);
        when(cqlOperations.execute(startsWith("INSERT INTO messages_by_conversation"), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("timeout"));

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void retransmissionOfAnIdenticalMessageCompletesTheMissingProjectionsWithTheAuthoritativeStatus() {
        UUID id = UUID.randomUUID();
        Message retransmitted = fallbackMessage(id);
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenReturn(false);
        when(cqlOperations.queryForList(startsWith("SELECT"), any(Object[].class)))
                .thenReturn(List.of(storedRow("hi", 1L, "DELIVERED")));
        when(cqlOperations.execute(startsWith("INSERT INTO messages_by_conversation"), any(Object[].class)))
                .thenReturn(true);

        assertThatCode(() -> cassandraAdapter.saveFallback(retransmitted)).doesNotThrowAnyException();

        // the projection has the status already reached by the message
        verify(cqlOperations).execute(startsWith("INSERT INTO messages_by_conversation"),
                eq("alice-bob"), eq(1L), eq(id),
                eq("alice"), eq("bob"),
                eq("hi"), eq("DELIVERED"));
        verify(conversationByUserRepository, times(2)).save(any());
        verify(cqlOperations, never()).execute(startsWith("UPDATE"), any(Object[].class));
        verify(messageByConversationRepository, never()).save(any());
    }

    @Test
    void retransmissionWithAlreadyPresentProjectionDoesNotCreateDuplicates() {
        when(cqlOperations.execute(startsWith("INSERT"), any(Object[].class))).thenReturn(false);
        when(cqlOperations.queryForList(startsWith("SELECT"), any(Object[].class)))
                .thenReturn(List.of(storedRow("hi", 1L, "STORED")));

        assertThatCode(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .doesNotThrowAnyException();

        // conditional inserts only: an existing projection is left untouched
        verify(cqlOperations, times(2)).execute(startsWith("INSERT"), any(Object[].class));
        verify(cqlOperations, never()).execute(startsWith("UPDATE"), any(Object[].class));
    }

    @Test
    void reusingAnIdWithDifferentContentIsRejectedAndWritesNothingElse() {
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenReturn(false);
        when(cqlOperations.queryForList(startsWith("SELECT"), any(Object[].class)))
                .thenReturn(List.of(storedRow("original", 1L, "STORED")));

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("MESSAGE_ID_CONFLICT"));

        verify(cqlOperations, never()).execute(startsWith("INSERT INTO messages_by_conversation"),
                any(Object[].class));
        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void reusingAnIdWithADifferentTimestampIsRejectedInsteadOfCreatingASecondProjectionRow() {
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenReturn(false);
        when(cqlOperations.queryForList(startsWith("SELECT"), any(Object[].class)))
                .thenReturn(List.of(storedRow("hi", 99L, "STORED")));

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("MESSAGE_ID_CONFLICT"));

        verify(cqlOperations, never()).execute(startsWith("INSERT INTO messages_by_conversation"),
                any(Object[].class));
    }

    @Test
    void reusingAnIdForADifferentParticipantPairIsRejected() {
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenReturn(false);
        Map<String, Object> other = Map.of(
                "conversation_id", "alice-charlie",
                "sender_username", "alice",
                "recipient_username", "charlie",
                "content", "hi",
                "logical_timestamp", 1L,
                "delivery_status", "STORED");
        when(cqlOperations.queryForList(startsWith("SELECT"), any(Object[].class))).thenReturn(List.of(other));

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void anExistingRowThatCannotBeReadBackIsAnErrorNotASilentSuccess() {
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenReturn(false);
        when(cqlOperations.queryForList(startsWith("SELECT"), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(fallbackMessage(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(conversationByUserRepository);
    }

    @Test
    void fallbackPropagatesCassandraErrorsInsteadOfTreatingThemAsRejections() {
        Message message = fallbackMessage(UUID.randomUUID());
        when(cqlOperations.execute(startsWith("INSERT INTO message_by_id"), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("no node available"));

        assertThatThrownBy(() -> cassandraAdapter.saveFallback(message))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(cqlOperations, never()).execute(startsWith("INSERT INTO messages_by_conversation"),
                any(Object[].class));
    }

    @Test
    void isParticipantIsFalseOnlyWhenNoMembershipRecordExists() {
        when(conversationByUserRepository.existsByUsernameAndConversationId(
                "charlie", "alice-bob")).thenReturn(false);

        assertThat(cassandraAdapter.isParticipant("charlie", "alice-bob")).isFalse();
    }

    @Test
    void cassandraAvailabilityFailureIsNotReportedAsNotParticipant() {
        when(conversationByUserRepository.existsByUsernameAndConversationId(
                "alice", "alice-bob"))
                .thenThrow(new DataAccessResourceFailureException("no node available"));

        assertThatThrownBy(() -> cassandraAdapter.isParticipant("alice", "alice-bob"))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void cassandraTimeoutOnFullReadIsReportedAsPersistenceUnavailable() {
        when(messageByConversationRepository.findAllByConversationId("alice-bob"))
                .thenThrow(new QueryTimeoutException("read timed out"));

        assertThatThrownBy(() -> cassandraAdapter.findAllByConversation("alice-bob"))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void queryOrMappingErrorsAreNotMaskedAsUnavailability() {
        InvalidDataAccessApiUsageException bug = new InvalidDataAccessApiUsageException("undefined column");
        when(conversationByUserRepository.existsByUsernameAndConversationId(
                "alice", "alice-bob")).thenThrow(bug);

        assertThatThrownBy(() -> cassandraAdapter.isParticipant("alice", "alice-bob"))
                .isSameAs(bug);
    }

    @Test
    void shouldReturnAllMessagesForConversationMappedToDomain() {
        String conversationId = "alice-bob";
        UUID messageId = UUID.randomUUID();
        when(messageByConversationRepository.findAllByConversationId(conversationId))
                .thenReturn(List.of(new MessageByConversation(
                        messageId, conversationId,
                        "alice", "bob",
                        "hi", 1L, MessageStatus.STORED)));

        List<Message> result = cassandraAdapter.findAllByConversation(conversationId);

        assertThat(result).containsExactly(
                new Message(messageId, conversationId,
                        "alice", "bob",
                        "hi", 1L, MessageStatus.STORED));
        verify(messageByConversationRepository).findAllByConversationId(conversationId);
    }

    private Message fallbackMessage(UUID id) {
        return new Message(
                id, "alice-bob",
                "alice", "bob",
                "hi", 1L, MessageStatus.STORED);
    }

    private Map<String, Object> storedRow(String content, long timestamp, String status) {
        return Map.of(
                "conversation_id", "alice-bob",
                "sender_username", "alice",
                "recipient_username", "bob",
                "content", content,
                "logical_timestamp", timestamp,
                "delivery_status", status);
    }
}
