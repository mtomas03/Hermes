package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.dto.SystemMessageDto;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaMonitorServiceTest {

    @Mock
    private KafkaHealthProbe kafkaHealthProbe;
    @Mock
    private WebSocketSessionRegistry registry;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private KafkaMonitorService monitorService;

    @BeforeEach
    void setUp() {
        monitorService = new KafkaMonitorService(kafkaHealthProbe, registry, messagingTemplate);
    }

    @Test
    void shouldStartAsAvailableByDefault() {
        assertThat(monitorService.isBackboneAvailable()).isTrue();
    }

    @Test
    void shouldNotBroadcastWhenBackboneStaysAvailable() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(true);

        monitorService.checkBackbone();

        assertThat(monitorService.isBackboneAvailable()).isTrue();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldFlipToUnavailableWithoutBroadcastingWhenBackboneGoesDown() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false);

        monitorService.checkBackbone();

        assertThat(monitorService.isBackboneAvailable()).isFalse();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldBroadcastForceReconnectWhenBackboneRecovers() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false, true);
        when(registry.allUsernames()).thenReturn(Set.of("alice"));

        monitorService.checkBackbone(); // true -> false
        monitorService.checkBackbone(); // false -> true, should broadcast

        assertThat(monitorService.isBackboneAvailable()).isTrue();
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/system"), any(SystemMessageDto.class));
    }

    @Test
    void shouldNotBroadcastWhenNoUsersAreConnected() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false, true);
        when(registry.allUsernames()).thenReturn(Set.of());

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void shouldContinueBroadcastingToOtherUsersWhenOneSendFails() {
        when(kafkaHealthProbe.isHealthy()).thenReturn(false, true);
        when(registry.allUsernames()).thenReturn(Set.of("failing-user", "healthy-user"));
        doThrow(new RuntimeException("broken pipe"))
                .when(messagingTemplate).convertAndSendToUser(eq("failing-user"), eq("/queue/system"), any());

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        verify(messagingTemplate).convertAndSendToUser(eq("healthy-user"), eq("/queue/system"), any(SystemMessageDto.class));
    }
}
