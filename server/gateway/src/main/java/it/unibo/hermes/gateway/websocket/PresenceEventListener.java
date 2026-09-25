package it.unibo.hermes.gateway.websocket;

import it.unibo.hermes.gateway.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Drives user presence tracking from Spring's STOMP session lifecycle events.
 *
 * <p> {@link SessionConnectedEvent} fires once the STOMP CONNECT handshake completes
 * (the session is fully established) and {@link SessionDisconnectEvent} fires when
 * the session terminates for any reason.
 */
@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final WebSocketSessionRegistry registry;
    private final PresenceService presenceService;

    @Value("${hermes.gateway.instance-id:gateway-1}")
    private String gatewayInstanceId;

    /**
     * Creates the presence event listener.
     *
     * @param registry        the registry tracking currently connected usernames on this instance
     * @param presenceService the service updating user online presence states
     */
    public PresenceEventListener(WebSocketSessionRegistry registry, PresenceService presenceService) {
        this.registry = registry;
        this.presenceService = presenceService;
    }

    /**
     * Registers the user and marks their presence online once the STOMP session is established.
     *
     * @param event the session-connected event, carrying the authenticated principal
     */
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        String username = usernameOf(event.getUser());
        if (username == null) {
            log.warn("SessionConnectedEvent with no authenticated principal - ignoring");
            return;
        }
        registry.register(username);
        presenceService.setOnline(username, gatewayInstanceId);
        log.info("STOMP session established for user '{}'", username);
    }

    /**
     * Unregisters the user and marks their presence offline once the STOMP session terminates.
     *
     * @param event the session-disconnect event, carrying the authenticated principal
     */
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String username = usernameOf(event.getUser());
        if (username == null) {
            return;
        }
        registry.unregister(username);
        presenceService.setOffline(username, gatewayInstanceId);
        log.info("STOMP session closed for user '{}' - status: {}", username, event.getCloseStatus());
    }

    private String usernameOf(Principal user) {
        return user != null ? user.getName() : null;
    }
}
