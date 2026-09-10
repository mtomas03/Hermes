package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.event.MessageAcknowledgedEvent;
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
    private PersistenceService persistenceService;

    private AcknowledgementService ackService;

    @BeforeEach
    void setUp() {
        ackService = new AcknowledgementService(persistenceService);
    }

    @Test
    void shouldMarkMessageAsAcknowledged() {
        String messageId = UUID.randomUUID().toString();
        MessageAcknowledgedEvent event = new MessageAcknowledgedEvent(
                messageId, "alice-bob", "bob", System.currentTimeMillis());

        ackService.processAcknowledgement(event);

        verify(persistenceService).updateDeliveryStatus(messageId, DeliveryStatus.ACKNOWLEDGED);
    }
}
