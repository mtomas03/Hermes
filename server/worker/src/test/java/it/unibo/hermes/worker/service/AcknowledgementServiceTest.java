package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.adapter.CassandraAdapter;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.event.MessageAckEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

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
        MessageAckEvent event = new MessageAckEvent(
                messageId,
                "alice-bob",
                "alice", "bob",
                1L
        );

        ackService.processAcknowledgement(event);

        verify(cassandraAdapter).updateMessageDeliveryStatus(messageId, DeliveryStatus.ACKNOWLEDGED);
    }
}
