package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.entity.cassandra.ConversationByUser;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.entity.cassandra.MessageById;
import it.unibo.hermes.gateway.repository.cassandra.ConversationByUserRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByConversationRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByIdRepository;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CassandraAdapterTest {

    @Mock
    private MessageByConversationRepository messageByConversationRepository;

    @Mock
    private MessageByIdRepository messageByIdRepository;

    @Mock
    private ConversationByUserRepository conversationByUserRepository;

    @InjectMocks
    private CassandraAdapter cassandraAdapter;

    @Test
    void shouldFindConversationsByUsername() {
        ConversationByUser conv = new ConversationByUser("alice", "alice-bob", "bob");
        when(conversationByUserRepository.findByUsername("alice")).thenReturn(List.of(conv));

        List<ConversationByUser> result = cassandraAdapter.findConversationsByUsername("alice");

        assertThat(result).containsExactly(conv);
        verify(conversationByUserRepository).findByUsername("alice");
    }

    @Test
    void shouldCheckIfUserIsParticipant() {
        when(conversationByUserRepository.existsByUsernameAndConversationId("alice", "alice-bob")).thenReturn(true);

        boolean isParticipant = cassandraAdapter.isParticipant("alice", "alice-bob");

        assertThat(isParticipant).isTrue();
        verify(conversationByUserRepository).existsByUsernameAndConversationId("alice", "alice-bob");
    }

    @Test
    void shouldSaveToBothRepositories() {
        UUID messageId = UUID.randomUUID();
        MessageByConversation fallbackMessage = new MessageByConversation(
                messageId,
                "alice-bob",
                "alice",
                "bob",
                "Fallback content",
                1L,
                MessageStatus.STORED
        );

        cassandraAdapter.saveFallback(fallbackMessage);

        verify(messageByConversationRepository).save(fallbackMessage);
        ArgumentCaptor<MessageById> byIdCaptor = ArgumentCaptor.forClass(MessageById.class);
        verify(messageByIdRepository).save(byIdCaptor.capture());
        MessageById savedById = byIdCaptor.getValue();
        assertThat(savedById.getMessageId()).isEqualTo(messageId);
        assertThat(savedById.getConversationId()).isEqualTo("alice-bob");
        assertThat(savedById.getSenderUsername()).isEqualTo("alice");
        assertThat(savedById.getRecipientUsername()).isEqualTo("bob");
        assertThat(savedById.getContent()).isEqualTo("Fallback content");
        assertThat(savedById.getLogicalTimestamp()).isEqualTo(1L);
        assertThat(savedById.getDeliveryStatus()).isEqualTo(MessageStatus.STORED.name());
    }

    @Test
    void fallbackCreatesConversationIndexForBothParticipantsAndWritesMessageByIdLast() {
        MessageByConversation message = new MessageByConversation(
                UUID.randomUUID(),
                "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.STORED);

        cassandraAdapter.saveFallback(message);

        ArgumentCaptor<ConversationByUser> indexes = ArgumentCaptor.forClass(ConversationByUser.class);
        InOrder order = inOrder(messageByConversationRepository, conversationByUserRepository, messageByIdRepository);
        order.verify(messageByConversationRepository).save(message);
        order.verify(conversationByUserRepository, times(2)).save(indexes.capture());
        order.verify(messageByIdRepository).save(any(MessageById.class));

        assertThat(indexes.getAllValues())
                .extracting(ConversationByUser::getUsername,
                        ConversationByUser::getConversationId,
                        ConversationByUser::getOtherParticipant)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("alice", "alice-bob", "bob"),
                        org.assertj.core.groups.Tuple.tuple("bob", "alice-bob", "alice"));
    }

    @Test
    void isParticipantIsFalseOnlyWhenNoMembershipRecordExists() {
        when(conversationByUserRepository.existsByUsernameAndConversationId(
                "carol", "alice-bob")).thenReturn(false);

        assertThat(cassandraAdapter.isParticipant("carol", "alice-bob")).isFalse();
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

        assertThatThrownBy(() -> cassandraAdapter.findAllMessages("alice-bob"))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void queryOrMappingErrorsAreNotMaskedAsUnavailability() {
        InvalidDataAccessApiUsageException bug = new InvalidDataAccessApiUsageException("undefined column");
        when(conversationByUserRepository.existsByUsernameAndConversationId(
                "alice", "alice-bob")).thenThrow(bug);

        assertThatThrownBy(() -> cassandraAdapter.isParticipant("alice", "alice-bob")).isSameAs(bug);
    }

    @Test
    void shouldReturnAllMessagesForConversation() {
        String conversationId = "alice-bob";
        List<MessageByConversation> expected = List.of(
                new MessageByConversation(
                        UUID.randomUUID(),
                        conversationId, "alice", "bob",
                        "hi", 1L, MessageStatus.STORED)
        );

        when(messageByConversationRepository.findAllByConversationId(conversationId))
                .thenReturn(expected);
        List<MessageByConversation> result = cassandraAdapter.findAllMessages(conversationId);

        assertThat(result).isEqualTo(expected).hasSize(1);
        verify(messageByConversationRepository).findAllByConversationId(conversationId);
    }
}
