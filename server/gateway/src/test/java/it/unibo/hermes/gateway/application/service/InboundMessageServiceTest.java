package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.model.SendMessageCommand;
import it.unibo.hermes.gateway.application.port.secondary.MessagePublisherPort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
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

import java.util.UUID;

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

    private InboundMessageService service;

    @BeforeEach
    void setUp() {
        service = new InboundMessageService(messagePublisher, messageStore);
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
        doNothing().when(messagePublisher).publish(any(Message.class));

        Message accepted = service.publish(command());

        assertThat(accepted).isNotNull();
        assertThat(accepted.senderUsername()).isEqualTo("alice");
        assertThat(accepted.recipientUsername()).isEqualTo("bob");
        assertThat(accepted.content()).isEqualTo("hi");
        verify(messagePublisher, times(1)).publish(any(Message.class));
        verifyNoInteractions(messageStore);
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        doThrow(new BackboneUnavailableException("Kafka glitch", new RuntimeException()))
                .doNothing()
                .when(messagePublisher).publish(any(Message.class));

        Message accepted = service.publish(command());

        assertThat(accepted).isNotNull();
        verify(messagePublisher, times(2)).publish(any(Message.class));
        verifyNoInteractions(messageStore);
    }

    @Test
    void shouldFallBackToStoreWhenKafkaFailsAllAttempts() {
        doThrow(new BackboneUnavailableException("Kafka down", new RuntimeException()))
                .when(messagePublisher).publish(any(Message.class));

        Message accepted = service.publish(command());

        assertThat(accepted).isNotNull();
        verify(messagePublisher, times(2)).publish(any(Message.class));
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
                .when(messagePublisher).publish(any(Message.class));
        doThrow(new RuntimeException("Cassandra unreachable"))
                .when(messageStore).saveFallback(any(Message.class));

        SendMessageCommand cmd = command();
        assertThatThrownBy(() -> service.publish(cmd))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Both Kafka and Cassandra are unavailable");

        verify(messagePublisher, times(2)).publish(any(Message.class));
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
    void shouldAcceptConversationIdRegardlessOfRecipientCaseAndWhitespace() {
        doNothing().when(messagePublisher).publish(any(Message.class));

        Message accepted = service.publish(command(" Bob ", "hi", "alice-bob"));

        assertThat(accepted).isNotNull();
    }

    @Test
    void shouldKeepPreRefactoringBehaviourForMalformedMessageId() {
        SendMessageCommand bad = new SendMessageCommand("not-a-uuid", "alice-bob", "alice", "bob", "hi", 1L);

        assertThatThrownBy(() -> service.publish(bad)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher, messageStore);
    }
}
