package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.adapter.CassandraMessageAdapter;
import it.unibo.hermes.worker.adapter.RedisPresenceAdapter;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.domain.PresenceInfo;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.producer.MessageDeliveryProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final String GATEWAY_ID = "gateway-1";

    @Mock
    private CassandraMessageAdapter cassandraMessageAdapter;

    @Mock
    private RedisPresenceAdapter presenceAdapter;

    @Mock
    private MessageDeliveryProducer messageDeliveryProducer;

    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(cassandraMessageAdapter, presenceAdapter, messageDeliveryProducer);
    }

    private MessageEvent createSampleEvent() {
        return new MessageEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L);
    }

    @Test
    void shouldSkipAlreadyProcessedDuplicateEvent() {
        MessageEvent event = createSampleEvent();
        when(cassandraMessageAdapter.isAlreadyProcessed(event.messageId())).thenReturn(true);

        deliveryService.processMessage(event);

        verify(cassandraMessageAdapter, never()).persistMessage(any());
        verifyNoInteractions(presenceAdapter, messageDeliveryProducer);
    }

    @Test
    void shouldDeliverImmediatelyWhenRecipientIsOnline() {
        MessageEvent event = createSampleEvent();
        when(cassandraMessageAdapter.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceAdapter.getPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.online("bob", GATEWAY_ID)));

        deliveryService.processMessage(event);

        verify(cassandraMessageAdapter).persistMessage(event);
        verify(messageDeliveryProducer).publishDeliveryEvent(event, GATEWAY_ID);
        verify(cassandraMessageAdapter).updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.DELIVERING);
    }

    @Test
    void shouldFallBackToStoredWhenPublishingDeliveryEventFails() {
        MessageEvent event = createSampleEvent();
        when(cassandraMessageAdapter.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceAdapter.getPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.online("bob", GATEWAY_ID)));
        doThrow(new RuntimeException("Kafka delivery stream timeout"))
                .when(messageDeliveryProducer).publishDeliveryEvent(event, GATEWAY_ID);

        deliveryService.processMessage(event);

        verify(cassandraMessageAdapter).persistMessage(event);
        verify(cassandraMessageAdapter).updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        verify(cassandraMessageAdapter, never()).updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.DELIVERING);
    }

    @Test
    void shouldStoreMessageWhenRecipientIsOffline() {
        MessageEvent event = createSampleEvent();
        when(cassandraMessageAdapter.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceAdapter.getPresence("bob"))
                .thenReturn(Optional.of(PresenceInfo.offline("bob")));

        deliveryService.processMessage(event);

        verify(cassandraMessageAdapter).persistMessage(event);
        verify(cassandraMessageAdapter).updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        verifyNoInteractions(messageDeliveryProducer);
    }

    @Test
    void shouldStoreMessageWhenPresenceInformationIsMissing() {
        MessageEvent event = createSampleEvent();
        when(cassandraMessageAdapter.isAlreadyProcessed(event.messageId())).thenReturn(false);
        when(presenceAdapter.getPresence("bob")).thenReturn(Optional.empty());

        deliveryService.processMessage(event);

        verify(cassandraMessageAdapter).persistMessage(event);
        verify(cassandraMessageAdapter).updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        verifyNoInteractions(messageDeliveryProducer);
    }
}
