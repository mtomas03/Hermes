package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.AckPublisherPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AcknowledgementServiceTest {

    @Mock
    private AckPublisherPort ackPublisher;

    @Test
    void shouldForwardAcknowledgementToPublisher() {
        new AcknowledgementService(ackPublisher).acknowledge("m-1", "bob");

        verify(ackPublisher).publishAck("m-1", "bob");
    }
}
