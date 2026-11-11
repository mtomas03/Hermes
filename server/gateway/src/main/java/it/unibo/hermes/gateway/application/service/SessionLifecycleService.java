package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.SessionLifecyclePort;
import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service managing the lifecycle of a client session, including connection and disconnection events.
 */
@Service
public class SessionLifecycleService implements SessionLifecyclePort {

    private final ConnectedClientsPort connectedClients;
    private final PresenceService presenceService;
    private final String gatewayInstanceId;

    /**
     * Creates the session lifecycle service.
     *
     * @param connectedClients  the port tracking clients connected to this instance
     * @param presenceService   the service maintaining shared presence
     * @param gatewayInstanceId the identifier of this Gateway instance
     */
    public SessionLifecycleService(
            ConnectedClientsPort connectedClients,
            PresenceService presenceService,
            @Value("${hermes.gateway.instance-id:gateway-1}") String gatewayInstanceId) {
        this.connectedClients = connectedClients;
        this.presenceService = presenceService;
        this.gatewayInstanceId = gatewayInstanceId;
    }

    @Override
    public void clientConnected(String username) {
        connectedClients.register(username);
        presenceService.setOnline(username, gatewayInstanceId);
    }

    @Override
    public void clientDisconnected(String username) {
        connectedClients.unregister(username);
        presenceService.setOffline(username, gatewayInstanceId);
    }
}
