package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.CassandraAdapter;
import it.unibo.hermes.worker.infrastructure.event.MessageAckEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcknowledgementServiceTest {

    @Mock
    private CassandraAdapter cassandraAdapter;

    private AcknowledgementService ackService;

    @BeforeEach
    void setUp() {
        ackService = new AcknowledgementService(cassandraAdapter);
    }

    @Test
    void shouldMarkMessageAsAcknowledged() {
        String messageId = UUID.randomUUID().toString();
        MessageAckEvent event = new MessageAckEvent(messageId, "bob");
        when(cassandraAdapter.findRecipientUsername(messageId))
                .thenReturn(Optional.of("bob"));

        ackService.processAcknowledgement(event);

        verify(cassandraAdapter).updateMessageDeliveryStatus(messageId, DeliveryStatus.ACKNOWLEDGED);
    }

    @Test
    void shouldIgnoreAckForgedByANonRecipient() {
        String messageId = UUID.randomUUID().toString();
        when(cassandraAdapter.findRecipientUsername(messageId)).thenReturn(Optional.of("bob"));

        ackService.processAcknowledgement(new MessageAckEvent(messageId, "charlie"));

        verify(cassandraAdapter, never()).updateMessageDeliveryStatus(any(), any());
    }

    @Test
    void shouldIgnoreAckForUnknownMessage() {
        String messageId = UUID.randomUUID().toString();
        when(cassandraAdapter.findRecipientUsername(messageId)).thenReturn(Optional.empty());

        ackService.processAcknowledgement(new MessageAckEvent(messageId, "bob"));

        verify(cassandraAdapter, never()).updateMessageDeliveryStatus(any(), any());
    }

    @Test
    void shouldIgnoreAckWithoutRecipient() {
        String messageId = UUID.randomUUID().toString();
        when(cassandraAdapter.findRecipientUsername(messageId)).thenReturn(Optional.of("bob"));

        ackService.processAcknowledgement(new MessageAckEvent(messageId, null));

        verify(cassandraAdapter, never()).updateMessageDeliveryStatus(any(), any());
    }
}
