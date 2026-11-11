package it.unibo.hermes.gateway.infrastructure.adapter.primary.websocket;

import it.unibo.hermes.gateway.application.port.primary.SessionLifecyclePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Translates Spring's STOMP session lifecycle events into {@link SessionLifecyclePort} calls.
 *
 * <p> {@link SessionConnectedEvent} fires once the STOMP CONNECT handshake completes
 * (the session is fully established) and {@link SessionDisconnectEvent} fires when
 * the session terminates for any reason.
 */
@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final SessionLifecyclePort sessionLifecycle;

    /**
     * Creates the presence event listener.
     *
     * @param sessionLifecycle the primary port tracking client sessions
     */
    public PresenceEventListener(SessionLifecyclePort sessionLifecycle) {
        this.sessionLifecycle = sessionLifecycle;
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
        sessionLifecycle.clientConnected(username);
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
        sessionLifecycle.clientDisconnected(username);
        log.info("STOMP session closed for user '{}' - status: {}", username, event.getCloseStatus());
    }

    private String usernameOf(Principal user) {
        return user != null ? user.getName() : null;
    }
}
