package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.BackboneHealthPort;
import it.unibo.hermes.gateway.application.port.secondary.ClientNotifierPort;
import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaMonitorServiceTest {

    @Mock
    private BackboneHealthPort kafkaHealthProbe;
    @Mock
    private ConnectedClientsPort registry;
    @Mock
    private ClientNotifierPort clientNotifier;

    private KafkaMonitorService monitorService;

    @BeforeEach
    void setUp() {
        monitorService = new KafkaMonitorService(kafkaHealthProbe, registry, clientNotifier);
    }

    @Test
    void shouldStartAsAvailableByDefault() {
        assertThat(monitorService.isMessageBackboneAvailable()).isTrue();
    }

    @Test
    void shouldNotBroadcastWhenBackboneStaysAvailable() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(true);

        monitorService.checkBackbone();

        assertThat(monitorService.isMessageBackboneAvailable()).isTrue();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldFlipToUnavailableWithoutBroadcastingWhenBackboneGoesDown() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false);

        monitorService.checkBackbone();

        assertThat(monitorService.isMessageBackboneAvailable()).isFalse();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldBroadcastForceReconnectWhenBackboneRecovers() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false, true);
        when(registry.allUsernames()).thenReturn(Set.of("alice"));

        monitorService.checkBackbone(); // true -> false
        monitorService.checkBackbone(); // false -> true, should broadcast

        assertThat(monitorService.isMessageBackboneAvailable()).isTrue();
        verify(clientNotifier).requestReconnect("alice", "BACKBONE_RECOVERED");
    }

    @Test
    void shouldNotBroadcastWhenNoUsersAreConnected() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false, true);
        when(registry.allUsernames()).thenReturn(Set.of());

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        verifyNoInteractions(clientNotifier);
    }

    @Test
    void shouldContinueBroadcastingToOtherUsersWhenOneSendFails() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false, true);
        when(registry.allUsernames()).thenReturn(Set.of("failing-user", "healthy-user"));
        doThrow(new RuntimeException("broken pipe"))
                .when(clientNotifier).requestReconnect("failing-user", "BACKBONE_RECOVERED");

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        verify(clientNotifier).requestReconnect("healthy-user", "BACKBONE_RECOVERED");
    }

    @Test
    void reportedPublishFailureShouldImmediatelyMarkTheBackboneUnavailable() {
        assertThat(monitorService.isMessageBackboneAvailable()).isTrue();

        monitorService.reportBackboneFailure();

        assertThat(monitorService.isMessageBackboneAvailable()).isFalse();
    }

    @Test
    void probeShouldRestoreTheBackboneAfterAReportedFailureAndForceReconnect() {
        when(registry.allUsernames()).thenReturn(Set.of("alice"));
        when(kafkaHealthProbe.isHealthy()).thenReturn(true);
        monitorService.reportBackboneFailure();

        monitorService.checkBackbone();

        assertThat(monitorService.isMessageBackboneAvailable()).isTrue();
        verify(clientNotifier).requestReconnect("alice", "BACKBONE_RECOVERED");
    }
}
