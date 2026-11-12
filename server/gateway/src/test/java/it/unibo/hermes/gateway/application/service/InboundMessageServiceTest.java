package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.model.SendMessageCommand;
import it.unibo.hermes.gateway.application.port.secondary.MessagePublisherPort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboundMessageServiceTest {

    @Mock
    private MessagePublisherPort messagePublisher;

    @Mock
    private MessageStorePort messageStore;

    @Mock
    private UserAccountPort userAccounts;

    @Mock
    private KafkaMonitorService backboneMonitor;

    private InboundMessageService service;

    @BeforeEach
    void setUp() {
        service = new InboundMessageService(messagePublisher, messageStore, userAccounts, backboneMonitor);
        lenient().when(backboneMonitor.isMessageBackboneAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(service, "publishBudgetMs", 2_000L);
        lenient().when(userAccounts.existsByUsername("bob")).thenReturn(true);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(service, "backoffMultiplier", 1.0);
    }

    private SendMessageCommand command() {
        return new SendMessageCommand(
                UUID.randomUUID().toString(), "alice-bob",
                "alice", "bob",
                "hi", 1L);
    }

    private SendMessageCommand command(String recipient, String content, String conversationId) {
        return new SendMessageCommand(
                UUID.randomUUID().toString(), conversationId,
                "alice", recipient,
                content, 1L);
    }

    @Test
    void shouldPublishToKafkaOnFirstAttempt() {
        doNothing().when(messagePublisher).publish(any(Message.class), any(Duration.class));

        Message accepted = service.publish(command());

        assertThat(accepted).isNotNull();
        assertThat(accepted.senderUsername()).isEqualTo("alice");
        assertThat(accepted.recipientUsername()).isEqualTo("bob");
        assertThat(accepted.content()).isEqualTo("hi");
        verify(messagePublisher, times(1)).publish(any(Message.class), any(Duration.class));
        verifyNoInteractions(messageStore);
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        doThrow(new BackboneUnavailableException("Kafka glitch", new RuntimeException()))
                .doNothing()
                .when(messagePublisher).publish(any(Message.class), any(Duration.class));

        Message accepted = service.publish(command());

        assertThat(accepted).isNotNull();
        verify(messagePublisher, times(2)).publish(any(Message.class), any(Duration.class));
        verifyNoInteractions(messageStore);
    }

    @Test
    void shouldFallBackToStoreWhenKafkaFailsAllAttempts() {
        doThrow(new BackboneUnavailableException("Kafka down", new RuntimeException()))
                .when(messagePublisher).publish(any(Message.class), any(Duration.class));

        Message accepted = service.publish(command());

        assertThat(accepted).isNotNull();
        verify(messagePublisher, times(2)).publish(any(Message.class), any(Duration.class));
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageStore).saveFallback(captor.capture());
        Message fallback = captor.getValue();
        assertThat(fallback.senderUsername()).isEqualTo("alice");
        assertThat(fallback.recipientUsername()).isEqualTo("bob");
        assertThat(fallback.status()).isEqualTo(MessageStatus.STORED);
    }

    @Test
    void shouldRejectMessageWhenBothKafkaAndStoreAreUnavailable() {
        doThrow(new BackboneUnavailableException("Kafka down", new RuntimeException()))
                .when(messagePublisher).publish(any(Message.class), any(Duration.class));
        doThrow(new RuntimeException("Cassandra unreachable"))
                .when(messageStore).saveFallback(any(Message.class));

        SendMessageCommand cmd = command();
        assertThatThrownBy(() -> service.publish(cmd))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Both Kafka and Cassandra are unavailable");

        verify(messagePublisher, times(2)).publish(any(Message.class), any(Duration.class));
        verify(messageStore, times(1)).saveFallback(any(Message.class));
    }

    @Test
    void shouldRejectMissingRecipient() {
        assertThatThrownBy(() -> service.publish(command(" ", "hi", "alice-bob")))
                .isInstanceOfSatisfying(InvalidMessageException.class, e -> {
                    assertThat(e.code()).isEqualTo("MISSING_RECIPIENT");
                    assertThat(e.getMessage()).isEqualTo("Field 'recipientUsername' is required");
                });
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldRejectMissingContent() {
        assertThatThrownBy(() -> service.publish(command("bob", "  ", "alice-bob")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("MISSING_CONTENT"));
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldRejectSelfSend() {
        assertThatThrownBy(() -> service.publish(command("alice", "hi", "alice-alice")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("SELF_SEND"));
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldRejectNonCanonicalConversationId() {
        assertThatThrownBy(() -> service.publish(command("bob", "hi", "bob-alice")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_CONVERSATION"));
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldRejectNullConversationId() {
        assertThatThrownBy(() -> service.publish(command("bob", "hi", null)))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_CONVERSATION"));
    }

    @Test
    void shouldNormaliseRecipientCaseAndWhitespaceAndStoreTheDerivedConversationId() {
        doNothing().when(messagePublisher).publish(any(Message.class), any(Duration.class));

        Message accepted = service.publish(command(" Bob ", "hi", "alice-bob"));

        assertThat(accepted.recipientUsername()).isEqualTo("bob");
        assertThat(accepted.conversationId()).isEqualTo("alice-bob");
    }

    @Test
    void shouldRejectRecipientContainingTheConversationSeparator() {
        assertThatThrownBy(() -> service.publish(command("b-c", "hi", "alice-b-c")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_RECIPIENT"));
        verifyNoInteractions(messagePublisher, messageStore, userAccounts);
    }

    @Test
    void shouldRejectRecipientOutsideTheCanonicalCharset() {
        assertThatThrownBy(() -> service.publish(command("bob!", "hi", "alice-bob!")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_RECIPIENT"));
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldTreatCaseVariantOfOwnUsernameAsSelfSend() {
        assertThatThrownBy(() -> service.publish(command("ALICE", "hi", "alice-alice")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("SELF_SEND"));
    }

    @Test
    void shouldRejectMessageToNonexistentRecipient() {
        when(userAccounts.existsByUsername("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.publish(command("ghost", "hi", "alice-ghost")))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code()).isEqualTo("UNKNOWN_RECIPIENT"));
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldAcceptMessageWhenAccountStoreIsUnavailable() {
        when(userAccounts.existsByUsername("charlie")).thenThrow(new RuntimeException("postgres down"));
        doNothing().when(messagePublisher).publish(any(Message.class), any(Duration.class));

        Message accepted = service.publish(command("charlie", "hi", "alice-charlie"));

        assertThat(accepted.recipientUsername()).isEqualTo("charlie");
    }

    @Test
    void shouldNotQueryTheAccountStoreAgainForARecipientAlreadySeenToExist() {
        doNothing().when(messagePublisher).publish(any(Message.class), any(Duration.class));

        service.publish(command());
        service.publish(command());

        verify(userAccounts, times(1)).existsByUsername("bob");
    }

    @Test
    void shouldKeepPreRefactoringBehaviourForMalformedMessageId() {
        SendMessageCommand bad = new SendMessageCommand("not-a-uuid", "alice-bob", "alice", "bob", "hi", 1L);

        assertThatThrownBy(() -> service.publish(bad)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher, messageStore);
    }

    @Test
    void shouldGoStraightToFallbackWithoutTouchingKafkaWhenTheBackboneIsKnownDown() {
        when(backboneMonitor.isMessageBackboneAvailable()).thenReturn(false);

        Message accepted = service.publish(command());

        assertThat(accepted.status()).isEqualTo(MessageStatus.STORED);
        verifyNoInteractions(messagePublisher);
        verify(messageStore).saveFallback(any(Message.class));
    }

    @Test
    void shouldMarkTheBackboneDownAfterFailedPublishSoLaterMessagesSkipIt() {
        doThrow(new BackboneUnavailableException("down", new RuntimeException()))
                .when(messagePublisher).publish(any(Message.class), any(Duration.class));

        service.publish(command());

        verify(backboneMonitor).reportBackboneFailure();
    }

    @Test
    void shouldNotRetryATimeoutBecauseTheRecordMayAlreadyBePublished() {
        doThrow(new BackboneUnavailableException("timeout", new RuntimeException(), true))
                .when(messagePublisher).publish(any(Message.class), any(Duration.class));

        Message accepted = service.publish(command());

        verify(messagePublisher, times(1)).publish(any(Message.class), any(Duration.class));
        // idempotent fallback with the very same message id
        ArgumentCaptor<Message> stored = ArgumentCaptor.forClass(Message.class);
        verify(messageStore).saveFallback(stored.capture());
        assertThat(stored.getValue().messageId()).isEqualTo(accepted.messageId());
        verify(backboneMonitor).reportBackboneFailure();
    }

    @Test
    void shouldStillRetryConfirmedFailures() {
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        doThrow(new BackboneUnavailableException("rejected", new RuntimeException(), false))
                .when(messagePublisher).publish(any(Message.class), any(Duration.class));

        service.publish(command());

        verify(messagePublisher, times(3)).publish(any(Message.class), any(Duration.class));
    }

    @Test
    void shouldBoundTheTotalPublishingTimeUnderAKafkaOutage() {
        ReflectionTestUtils.setField(service, "maxAttempts", 50);
        ReflectionTestUtils.setField(service, "initialBackoffMs", 10L);
        ReflectionTestUtils.setField(service, "publishBudgetMs", 150L);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(inv -> {
            attempts.incrementAndGet();
            Thread.sleep(40);
            throw new BackboneUnavailableException("slow failure", new RuntimeException());
        }).when(messagePublisher).publish(any(Message.class), any(Duration.class));

        long start = System.nanoTime();
        service.publish(command());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1_000);
        assertThat(attempts.get()).isLessThan(50);
        verify(messageStore).saveFallback(any(Message.class));
    }

    @Test
    void shouldNeverGiveAnAttemptMoreTimeThanTheRemainingBudget() {
        ReflectionTestUtils.setField(service, "publishBudgetMs", 500L);
        ArgumentCaptor<Duration> timeout = ArgumentCaptor.forClass(Duration.class);

        service.publish(command());

        verify(messagePublisher).publish(any(Message.class), timeout.capture());
        assertThat(timeout.getValue()).isLessThanOrEqualTo(Duration.ofMillis(500));
    }

    @Test
    void shouldReportAnIdConflictFromTheFallbackAsAClientErrorNotAsAStorageOutage() {
        when(backboneMonitor.isMessageBackboneAvailable()).thenReturn(false);
        doThrow(new InvalidMessageException("MESSAGE_ID_CONFLICT", "already used"))
                .when(messageStore).saveFallback(any(Message.class));

        assertThatThrownBy(() -> service.publish(command()))
                .isInstanceOfSatisfying(InvalidMessageException.class,
                        e -> assertThat(e.code())
                                .isEqualTo("MESSAGE_ID_CONFLICT"));
    }
}
