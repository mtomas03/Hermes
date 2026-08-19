package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled service monitoring active WebSocket connections for missing heartbeat signals.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final WebSocketSessionRegistry registry;
    private final PresenceService presenceService;

    @Value("${hermes.gateway.heartbeat-timeout-ms:90000}")
    private long heartbeatTimeoutMs;

    /**
     * Creates the heartbeat monitoring service.
     *
     * @param registry        the registry tracking active local WebSocket sessions
     * @param presenceService the service managing user presence state updates
     */
    public HeartbeatService(WebSocketSessionRegistry registry,
                            PresenceService presenceService) {
        this.registry = registry;
        this.presenceService = presenceService;
    }

    /**
     * Scans registered sessions periodically and closes any connections that have exceeded the heartbeat timeout.
     *
     * <p> Identifies sessions whose last PING timestamp falls outside the acceptable threshold, terminating
     * them with a session-not-reliable status to prompt client reconnection and resource release.
     */
    @Scheduled(fixedDelayString = "${hermes.gateway.heartbeat-check-ms:30000}")
    public void checkHeartbeats() {
        Instant threshold = Instant.now().minusMillis(heartbeatTimeoutMs);

        List<WebSocketSessionRegistry.Entry> stale = registry.entriesOlderThan(threshold);
        for (WebSocketSessionRegistry.Entry entry : stale) {
            String username = entry.username();
            WebSocketSession session = entry.session();
            log.warn("Session for user '{}' timed out – closing", username);

            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }
            } catch (Exception e) {
                log.error("Error closing timed-out session for '{}'", username, e);
            } finally {
                // Guarantees clean-up even if the session is already closed or fails to close cleanly
                registry.unregister(username);
                presenceService.setOffline(username);
            }
        }
    }
}