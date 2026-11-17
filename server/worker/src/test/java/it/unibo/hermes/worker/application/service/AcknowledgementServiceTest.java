package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.application.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.application.port.secondary.MessageStorePort;
import it.unibo.hermes.worker.domain.model.Acknowledgement;
import it.unibo.hermes.worker.domain.model.DeliveryStatus;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.domain.model.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcknowledgementServiceTest {

    @Mock
    private MessageStorePort messageStore;

    private AcknowledgementService acknowledgementService;

    @BeforeEach
    void setUp() {
        acknowledgementService = new AcknowledgementService(messageStore);
    }

    private Message createMessage() {
        return new Message(
                UUID.randomUUID().toString(), "alice-bob",
                "alice", "bob",
                "hi", 1L);
    }

    private void givenStored(Message message, DeliveryStatus status) {
        when(messageStore.findById(message.messageId()))
                .thenReturn(Optional.of(new StoredMessage(message, status)));
    }

    @Test
    void shouldMarkMessageAsAcknowledged() {
        Message message = createMessage();
        givenStored(message, DeliveryStatus.DELIVERING);

        acknowledgementService.processAcknowledgement(
                new Acknowledgement(message.messageId(), "bob"));

        verify(messageStore).advanceStatus(message.messageId(), DeliveryStatus.ACKNOWLEDGED);
    }

    @Test
    void duplicateAcknowledgementIsForwardedAsIdempotentStatusAdvance() {
        Message message = createMessage();
        givenStored(message, DeliveryStatus.ACKNOWLEDGED);

        acknowledgementService.processAcknowledgement(
                new Acknowledgement(message.messageId(), "bob"));
        acknowledgementService.processAcknowledgement(
                new Acknowledgement(message.messageId(), "bob"));

        verify(messageStore, times(2))
                .advanceStatus(message.messageId(), DeliveryStatus.ACKNOWLEDGED);
    }

    @Test
    void shouldIgnoreAckForgedByANonRecipient() {
        Message message = createMessage();
        givenStored(message, DeliveryStatus.DELIVERING);

        acknowledgementService.processAcknowledgement(
                new Acknowledgement(message.messageId(), "charlie"));

        verify(messageStore, never()).advanceStatus(any(), any());
    }

    @Test
    void shouldIgnoreAckForUnknownMessage() {
        String unknownId = UUID.randomUUID().toString();
        when(messageStore.findById(unknownId)).thenReturn(Optional.empty());

        acknowledgementService.processAcknowledgement(new Acknowledgement(unknownId, "bob"));

        verify(messageStore, never()).advanceStatus(any(), any());
    }

    @Test
    void persistenceFailureWhileAdvancingIsPropagatedForRetry() {
        Message message = createMessage();
        givenStored(message, DeliveryStatus.DELIVERING);
        doThrow(new PersistenceUnavailableException("down", new RuntimeException("timeout")))
                .when(messageStore).advanceStatus(message.messageId(), DeliveryStatus.ACKNOWLEDGED);

        assertThatThrownBy(() -> acknowledgementService.processAcknowledgement(
                new Acknowledgement(message.messageId(), "bob")))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void persistenceFailureWhileLookingUpTheMessageIsPropagatedForRetry() {
        Message message = createMessage();
        when(messageStore.findById(message.messageId()))
                .thenThrow(new PersistenceUnavailableException(
                        "down", new RuntimeException("timeout")));

        assertThatThrownBy(() -> acknowledgementService.processAcknowledgement(
                new Acknowledgement(message.messageId(), "bob")))
                .isInstanceOf(PersistenceUnavailableException.class);

        verify(messageStore, never()).advanceStatus(any(), any());
    }
}
