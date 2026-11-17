package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.application.exception.DeliveryPublicationException;
import it.unibo.hermes.worker.application.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.application.port.secondary.MessageDeliveryProducerPort;
import it.unibo.hermes.worker.application.port.secondary.MessageStorePort;
import it.unibo.hermes.worker.application.port.secondary.PresenceLookupPort;
import it.unibo.hermes.worker.domain.exception.MessageConflictException;
import it.unibo.hermes.worker.domain.model.DeliveryStatus;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.domain.model.PresenceInfo;
import it.unibo.hermes.worker.domain.model.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final String GATEWAY_ID = "gateway-1";

    @Mock
    private MessageStorePort messageRepository;

    @Mock
    private PresenceLookupPort presenceLookup;

    @Mock
    private MessageDeliveryProducerPort deliveryEventPublisher;

    private DeliveryService deliveryService;

    private static StoredMessage stored(Message message, DeliveryStatus status) {
        return new StoredMessage(message, status);
    }

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(messageRepository, presenceLookup, deliveryEventPublisher);
    }

    private Message createMessage() {
        return new Message(
                UUID.randomUUID().toString(), "alice-bob",
                "alice", "bob",
                "hi", 1L);
    }

    @Test
    void shouldSkipAlreadyProcessedDuplicateEventButRealignTheProjection() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.of(stored(message, DeliveryStatus.DELIVERING)));

        deliveryService.processMessage(message);

        verify(messageRepository, never()).createIfAbsent(any());
        verify(messageRepository).reconcileProjection(message.messageId());
        verify(messageRepository, never()).advanceStatus(any(), any());
        verifyNoInteractions(presenceLookup, deliveryEventPublisher);
    }

    @Test
    void shouldPersistThenDeliverImmediatelyWhenRecipientIsOnline() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.empty());
        when(messageRepository.createIfAbsent(message))
                .thenReturn(stored(message, DeliveryStatus.PENDING));
        when(presenceLookup.findPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.online("bob", GATEWAY_ID)));

        deliveryService.processMessage(message);

        InOrder inOrder = inOrder(messageRepository, presenceLookup, deliveryEventPublisher);
        inOrder.verify(messageRepository).createIfAbsent(message);
        inOrder.verify(messageRepository).reconcileProjection(message.messageId());
        inOrder.verify(presenceLookup).findPresence("bob");
        inOrder.verify(deliveryEventPublisher).publish(message, GATEWAY_ID);
        inOrder.verify(messageRepository).advanceStatus(message.messageId(), DeliveryStatus.DELIVERING);
        verify(messageRepository, never()).advanceStatus(message.messageId(), DeliveryStatus.STORED);
    }

    @Test
    void shouldFallBackToStoredWhenPublishingTheDeliveryEventFails() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.empty());
        when(messageRepository.createIfAbsent(message))
                .thenReturn(stored(message, DeliveryStatus.PENDING));
        when(presenceLookup.findPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.online("bob", GATEWAY_ID)));
        doThrow(new DeliveryPublicationException("broker down", new RuntimeException("timeout")))
                .when(deliveryEventPublisher).publish(message, GATEWAY_ID);

        deliveryService.processMessage(message);

        verify(messageRepository).advanceStatus(message.messageId(), DeliveryStatus.STORED);
        verify(messageRepository, never()).advanceStatus(message.messageId(), DeliveryStatus.DELIVERING);
    }

    @Test
    void shouldStoreMessageWhenRecipientIsOffline() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.empty());
        when(messageRepository.createIfAbsent(message))
                .thenReturn(stored(message, DeliveryStatus.PENDING));
        when(presenceLookup.findPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.offline("bob")));

        deliveryService.processMessage(message);

        verify(messageRepository).advanceStatus(message.messageId(), DeliveryStatus.STORED);
        verifyNoInteractions(deliveryEventPublisher);
    }

    @Test
    void shouldStoreMessageWhenPresenceInformationIsMissing() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.empty());
        when(messageRepository.createIfAbsent(message))
                .thenReturn(stored(message, DeliveryStatus.PENDING));
        when(presenceLookup.findPresence("bob"))
                .thenReturn(Optional.empty());

        deliveryService.processMessage(message);

        verify(messageRepository).advanceStatus(message.messageId(), DeliveryStatus.STORED);
        verifyNoInteractions(deliveryEventPublisher);
    }

    @Test
    void persistenceFailureIsPropagatedForRetryAndNothingIsRoutedOrAdvanced() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.empty());
        when(messageRepository.createIfAbsent(message))
                .thenThrow(new PersistenceUnavailableException(
                        "down", new RuntimeException("timeout")));

        assertThatThrownBy(() -> deliveryService.processMessage(message))
                .isInstanceOf(PersistenceUnavailableException.class);

        verify(messageRepository, never()).reconcileProjection(any());
        verify(messageRepository, never()).advanceStatus(any(), any());
        verifyNoInteractions(presenceLookup, deliveryEventPublisher);
    }

    @Test
    void redeliveryAfterACrashFollowingPersistenceRoutesTheMessageWithoutCreatingItTwice() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(
                        Optional.of(stored(message, DeliveryStatus.PENDING))); // persisted, crashed before routing
        when(presenceLookup.findPresence("bob"))
                .thenReturn(
                        Optional.of(PresenceInfo.online("bob", GATEWAY_ID)));

        deliveryService.processMessage(message);

        verify(messageRepository, never()).createIfAbsent(any());
        verify(messageRepository).reconcileProjection(message.messageId());
        verify(deliveryEventPublisher).publish(message, GATEWAY_ID);
        verify(messageRepository).advanceStatus(message.messageId(), DeliveryStatus.DELIVERING);
    }

    @Test
    void aLateDuplicateOfAnAcknowledgedMessageNeverRoutesOrChangesItsStatus() {
        Message message = createMessage();
        when(messageRepository.findById(message.messageId()))
                .thenReturn(Optional.of(stored(message, DeliveryStatus.ACKNOWLEDGED)));

        deliveryService.processMessage(message);

        verify(messageRepository, never()).advanceStatus(any(), any());
        verifyNoInteractions(presenceLookup, deliveryEventPublisher);
    }

    @Test
    void reusedIdentifierWithDifferentContentIsRejectedBeforeAnythingIsAlignedOrRouted() {
        Message original = createMessage();
        Message conflicting = new Message(original.messageId(), original.conversationId(),
                original.senderUsername(), original.recipientUsername(), "tampered", 1L);
        when(messageRepository.findById(original.messageId())).thenReturn(Optional.empty());
        when(messageRepository.createIfAbsent(conflicting)).thenReturn(stored(original, DeliveryStatus.PENDING));

        assertThatThrownBy(() -> deliveryService.processMessage(conflicting))
                .isInstanceOf(MessageConflictException.class)
                .hasMessageContaining("content");

        verify(messageRepository, never()).reconcileProjection(any());
        verify(messageRepository, never()).advanceStatus(any(), any());
        verifyNoInteractions(presenceLookup, deliveryEventPublisher);
    }

    @Test
    void pendingMessageAlreadyStoredWithDifferentDataIsAlsoRejected() {
        Message original = createMessage();
        Message conflicting = new Message(original.messageId(), original.conversationId(),
                "charlie", original.recipientUsername(), original.content(), 1L);
        when(messageRepository.findById(original.messageId()))
                .thenReturn(Optional.of(stored(original, DeliveryStatus.PENDING)));

        assertThatThrownBy(() -> deliveryService.processMessage(conflicting))
                .isInstanceOf(MessageConflictException.class)
                .hasMessageContaining("senderUsername");

        verify(messageRepository, never()).reconcileProjection(any());
        verifyNoInteractions(presenceLookup, deliveryEventPublisher);
    }
}
