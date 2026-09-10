package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.domain.PresenceInfo;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.producer.DeliveryEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private PersistenceService persistenceService;
    @Mock
    private RedisPresenceService presenceService;
    @Mock
    private DeliveryEventProducer deliveryEventProducer;

    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(persistenceService, presenceService, deliveryEventProducer);
    }

    private MessageCreatedEvent event() {
        return new MessageCreatedEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L, Instant.now());
    }

    @Test
    void shouldSkipAlreadyProcessedDuplicateEvent() {
        MessageCreatedEvent event = event();
        when(persistenceService.isAlreadyProcessed(event.messageId())).thenReturn(true);

        deliveryService.processMessage(event);

        verify(persistenceService, never()).persistMessage(any());
        verifyNoInteractions(presenceService, deliveryEventProducer);
    }

    @Test
    void shouldDeliverImmediatelyWhenRecipientIsOnline() {
        MessageCreatedEvent event = event();
        when(persistenceService.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceService.getPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.online("bob", "gw-1")));

        deliveryService.processMessage(event);

        verify(persistenceService).persistMessage(event);
        verify(deliveryEventProducer).publishDeliveryEvent(event, "gw-1");
        verify(persistenceService).updateDeliveryStatus(event.messageId(), DeliveryStatus.DELIVERING);
    }

    @Test
    void shouldStoreMessageWhenRecipientIsOffline() {
        MessageCreatedEvent event = event();
        when(persistenceService.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceService.getPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.offline("bob")));

        deliveryService.processMessage(event);

        verify(persistenceService).persistMessage(event);
        verify(persistenceService).updateDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        verifyNoInteractions(deliveryEventProducer);
    }

    @Test
    void shouldStoreMessageWhenPresenceInformationIsMissing() {
        MessageCreatedEvent event = event();
        when(persistenceService.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceService.getPresence("bob")).thenReturn(Optional.empty());

        deliveryService.processMessage(event);

        verify(persistenceService).updateDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        verifyNoInteractions(deliveryEventProducer);
    }

    @Test
    void shouldFallBackToStoredWhenPublishingDeliveryEventFails() {
        MessageCreatedEvent event = event();
        when(persistenceService.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceService.getPresence("bob")).thenReturn(Optional.of(PresenceInfo.online("bob", "gw-1")));
        doThrow(new RuntimeException("kafka down"))
                .when(deliveryEventProducer).publishDeliveryEvent(event, "gw-1");

        deliveryService.processMessage(event);

        verify(persistenceService).updateDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        verify(persistenceService, never()).updateDeliveryStatus(event.messageId(), DeliveryStatus.DELIVERING);
    }
}
