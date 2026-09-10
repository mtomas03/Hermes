package it.unibo.hermes.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.adapter.KafkaPublisherAdapter;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaMonitorServiceTest {

    @Mock
    private KafkaPublisherAdapter kafkaAdapter;
    @Mock
    private WebSocketSessionRegistry registry;
    @Mock
    private ObjectMapper objectMapper;

    private KafkaMonitorService monitorService;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        monitorService = new KafkaMonitorService(kafkaAdapter, registry, objectMapper);
        lenient().when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"type\":\"FORCE_RECONNECT\"}");
    }

    @Test
    void shouldStartAsAvailableByDefault() {
        assertThat(monitorService.isBackboneAvailable()).isTrue();
    }

    @Test
    void shouldNotBroadcastWhenBackboneStaysAvailable() {
        when(kafkaAdapter.isHealthy()).thenReturn(true);

        monitorService.checkBackbone();

        assertThat(monitorService.isBackboneAvailable()).isTrue();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldFlipToUnavailableWithoutBroadcastingWhenBackboneGoesDown() {
        when(kafkaAdapter.isHealthy()).thenReturn(false);

        monitorService.checkBackbone();

        assertThat(monitorService.isBackboneAvailable()).isFalse();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldBroadcastForceReconnectWhenBackboneRecovers() throws Exception {
        // First probe goes down, second probe recovers
        when(kafkaAdapter.isHealthy()).thenReturn(false, true);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(registry.allEntries()).thenReturn(
                List.of(new WebSocketSessionRegistry.Entry("alice", session, Instant.now())));

        monitorService.checkBackbone(); // true -> false
        monitorService.checkBackbone(); // false -> true, should broadcast

        assertThat(monitorService.isBackboneAvailable()).isTrue();
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldSkipClosedSessionsWhenBroadcasting() throws Exception {
        when(kafkaAdapter.isHealthy()).thenReturn(false, true);
        WebSocketSession closedSession = mock(WebSocketSession.class);
        when(closedSession.isOpen()).thenReturn(false);
        when(registry.allEntries()).thenReturn(
                List.of(new WebSocketSessionRegistry.Entry("bob", closedSession, Instant.now())));

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        verify(closedSession, never()).sendMessage(any());
    }

    @Test
    void shouldContinueBroadcastingToOtherSessionsWhenOneSendFails() throws Exception {
        when(kafkaAdapter.isHealthy()).thenReturn(false, true);
        WebSocketSession failing = mock(WebSocketSession.class);
        WebSocketSession healthy = mock(WebSocketSession.class);
        when(failing.isOpen()).thenReturn(true);
        when(healthy.isOpen()).thenReturn(true);
        doThrow(new RuntimeException("broken pipe")).when(failing).sendMessage(any());
        when(registry.allEntries()).thenReturn(List.of(
                new WebSocketSessionRegistry.Entry("failing-user", failing, Instant.now()),
                new WebSocketSessionRegistry.Entry("healthy-user", healthy, Instant.now())));

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        // The second session still receives its message despite the first one failing
        verify(healthy).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldNotBroadcastWhenSerialisationFails() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {
                });
        when(kafkaAdapter.isHealthy()).thenReturn(false, true);

        monitorService.checkBackbone();
        monitorService.checkBackbone();

        verifyNoInteractions(registry);
    }
}
