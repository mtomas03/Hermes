package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service keeping each connected user's presence TTL alive in Redis.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final WebSocketSessionRegistry registry;
    private final PresenceService presenceService;

    /**
     * Creates the heartbeat service.
     *
     * @param registry        the registry tracking users currently connected on this instance
     * @param presenceService the service managing user presence state updates
     */
    public HeartbeatService(WebSocketSessionRegistry registry,
                            PresenceService presenceService) {
        this.registry = registry;
        this.presenceService = presenceService;
    }

    /**
     * Refreshes the Redis presence TTL of every user currently connected to this Gateway instance.
     */
    @Scheduled(fixedDelayString = "${hermes.gateway.heartbeat-check-ms:30000}")
    public void refreshPresenceTtl() {
        for (String username : registry.allUsernames()) {
            try {
                presenceService.refreshTtl(username);
            } catch (Exception e) {
                log.warn("Failed to refresh presence TTL for '{}': {}", username, e.getMessage());
            }
        }
    }
}
