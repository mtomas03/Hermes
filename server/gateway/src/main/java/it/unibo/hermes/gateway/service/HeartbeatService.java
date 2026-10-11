package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final String gatewayInstanceId;

    /**
     * Creates the heartbeat service.
     *
     * @param registry        the registry tracking users currently connected on this instance
     * @param presenceService the service managing user presence state updates
     * @param gatewayInstanceId the identifier of this Gateway instance, written when a lost entry is restored
     */
    public HeartbeatService(WebSocketSessionRegistry registry,
                            PresenceService presenceService,
                            @Value("${hermes.gateway.instance-id:gateway-1}") String gatewayInstanceId) {
        this.registry = registry;
        this.presenceService = presenceService;
        this.gatewayInstanceId = gatewayInstanceId;
    }

    /**
     * Keeps the Redis presence of every user, currently connected to this Gateway instance, alive.
     * The entry's TTL is refreshed and the entry is recreated if it has been lost.
     */
    @Scheduled(fixedDelayString = "${hermes.gateway.heartbeat-check-ms:30000}")
    public void refreshPresenceTtl() {
        for (String username : registry.allUsernames()) {
            try {
                presenceService.refreshOrRestore(username, gatewayInstanceId);
            } catch (Exception e) {
                log.warn("Failed to refresh presence TTL for '{}': {}", username, e.getMessage());
            }
        }
    }
}
