package it.unibo.hermes.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.adapter.KafkaPublisherAdapter;
import it.unibo.hermes.gateway.dto.WsMessage;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled service monitoring the operational health of Kafka and coordinating recovery actions.
 */
@Service
public class KafkaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(KafkaMonitorService.class);

    private final KafkaPublisherAdapter kafkaAdapter;
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * Shared atomic status flag indicating Kafka reachability.
     */
    private final AtomicBoolean backboneAvailable = new AtomicBoolean(true);

    /**
     * Creates the Kafka monitoring service.
     *
     * @param kafkaAdapter the adapter executing broker connectivity health probes
     * @param registry     the registry tracking active local WebSocket sessions
     * @param objectMapper the object mapper used to serialize client notification frames
     */
    public KafkaMonitorService(KafkaPublisherAdapter kafkaAdapter,
                               WebSocketSessionRegistry registry,
                               ObjectMapper objectMapper) {
        this.kafkaAdapter = kafkaAdapter;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * Tells whether Kafka is currently operational and reachable.
     *
     * @return {@code true} if the backbone is reachable, {@code false} otherwise
     */
    public boolean isBackboneAvailable() {
        return backboneAvailable.get();
    }

    /**
     * Periodically verifies Kafka broker health and triggers client reconnection procedures upon service recovery.
     *
     * <p> Updates the internal availability flag based on broker probe results and broadcasts reconnection requests
     * whenever the system transitions from an unavailable state back to healthy.
     */
    @Scheduled(fixedDelayString = "${hermes.gateway.heartbeat-check-ms:30000}")
    public void checkBackbone() {
        boolean wasAvailable = backboneAvailable.get();
        boolean nowAvailable = kafkaAdapter.isHealthy();

        backboneAvailable.set(nowAvailable);

        if (!wasAvailable && nowAvailable) {
            log.info("Kafka backbone recovered - forcing client reconnection for consistent sync");
            forceReconnectAll();
        } else if (wasAvailable && !nowAvailable) {
            log.warn("Kafka backbone is unavailable - switching to Cassandra fallback path");
        }
    }

    /**
     * Broadcasts a forced reconnection message to all active WebSocket connections.
     *
     * <p> Forces connected clients to reconnect and perform a pull-based synchronisation to fetch
     * messages stored directly in Cassandra during the Kafka outage. This ensures client local history
     * remains consistent with server state before real-time streaming resumes.
     */
    private void forceReconnectAll() {
        WsMessage msg = WsMessage.forceReconnect("BACKBONE_RECOVERED");
        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise FORCE_RECONNECT message", e);
            return;
        }

        for (WebSocketSessionRegistry.Entry entry : registry.allEntries()) {
            WebSocketSession session = entry.session();
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(payload));
                    }
                }
            } catch (Exception e) {
                log.warn("Could not send FORCE_RECONNECT to '{}': {}", entry.username(), e.getMessage());
            }
        }
    }
}