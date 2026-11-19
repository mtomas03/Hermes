package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.AckPublisherPort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcknowledgementServiceTest {

    @Mock
    private AckPublisherPort ackPublisher;
    @Mock
    private KafkaMonitorService backboneMonitor;

    private AcknowledgementService service;

    @BeforeEach
    void setUp() {
        service = new AcknowledgementService(ackPublisher, backboneMonitor);
        ReflectionTestUtils.setField(service, "ackBudgetMs", 1000L);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(service, "backoffMultiplier", 1.0);
        lenient().when(backboneMonitor.isMessageBackboneAvailable()).thenReturn(true);
    }

    @Test
    void shouldForwardAcknowledgementToPublisherWithinTheBudget() {
        service.acknowledge("m1", "bob");

        verify(ackPublisher).publishAck(eq("m1"), eq("bob"), any(Duration.class));
    }

    @Test
    void shouldRetryATransientFailureAndSucceed() {
        doThrow(new BackboneUnavailableException("down", null))
                .doNothing()
                .when(ackPublisher).publishAck(any(), any(), any());

        service.acknowledge("m1", "bob");

        verify(ackPublisher, times(2))
                .publishAck(eq("m1"), eq("bob"), any(Duration.class));
        verify(backboneMonitor, never()).reportBackboneFailure();
    }

    @Test
    void shouldRetryAfterAnUnknownOutcomeBecauseAcknowledgementsAreIdempotent() {
        doThrow(new BackboneUnavailableException("timeout", null, true))
                .doNothing()
                .when(ackPublisher).publishAck(any(), any(), any());

        service.acknowledge("m1", "bob");

        verify(ackPublisher, times(2))
                .publishAck(eq("m1"), eq("bob"), any(Duration.class));
    }

    @Test
    void shouldReportFailureAfterBoundedRetriesInsteadOfLosingTheAckSilently() {
        doThrow(new BackboneUnavailableException("down", null))
                .when(ackPublisher).publishAck(any(), any(), any());

        assertThatThrownBy(() -> service.acknowledge("m1", "bob"))
                .isInstanceOf(BackboneUnavailableException.class)
                .hasMessageContaining("m1");

        verify(ackPublisher, times(3))
                .publishAck(eq("m1"), eq("bob"), any(Duration.class));
        verify(backboneMonitor).reportBackboneFailure();
    }

    @Test
    void shouldFailFastWithoutBlockingWhenTheBackboneIsKnownDown() {
        when(backboneMonitor.isMessageBackboneAvailable()).thenReturn(false);

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> service.acknowledge("m1", "bob"))
                .isInstanceOf(BackboneUnavailableException.class);

        assertThat((System.currentTimeMillis() - start)).isLessThan(500);
        verifyNoInteractions(ackPublisher);
    }
}
