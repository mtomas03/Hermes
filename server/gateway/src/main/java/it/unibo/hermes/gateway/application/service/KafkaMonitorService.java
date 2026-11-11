package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.BackboneHealthPort;
import it.unibo.hermes.gateway.application.port.secondary.ClientNotifierPort;
import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled service monitoring the operational health of Kafka and coordinating recovery actions.
 */
@Service
public class KafkaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(KafkaMonitorService.class);

    private final BackboneHealthPort kafkaHealthProbe;
    private final ConnectedClientsPort registry;
    private final ClientNotifierPort clientNotifier;

    /**
     * Shared atomic status flag indicating Kafka reachability.
     */
    private final AtomicBoolean backboneAvailable = new AtomicBoolean(true);

    /**
     * Creates the Kafka monitoring service.
     *
     * @param kafkaHealthProbe the probe executing broker connectivity health checks
     * @param registry         the registry tracking users currently connected on this instance
     * @param clientNotifier   the port used to push control requests to connected users
     */
    public KafkaMonitorService(BackboneHealthPort kafkaHealthProbe,
                               ConnectedClientsPort registry,
                               ClientNotifierPort clientNotifier) {
        this.kafkaHealthProbe = kafkaHealthProbe;
        this.registry = registry;
        this.clientNotifier = clientNotifier;
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
     * Periodically verifies Kafka broker health and
     * triggers client reconnection procedures upon service recovery.
     */
    @Scheduled(fixedDelayString = "${hermes.gateway.heartbeat-check-ms:30000}")
    public void checkBackbone() {
        boolean wasAvailable = backboneAvailable.get();
        boolean nowAvailable = kafkaHealthProbe.isHealthy();

        backboneAvailable.set(nowAvailable);

        if (!wasAvailable && nowAvailable) {
            log.info("Kafka backbone recovered - forcing client reconnection for consistent sync");
            forceReconnectAll();
        } else if (wasAvailable && !nowAvailable) {
            log.warn("Kafka backbone is unavailable - switching to Cassandra fallback path");
        }
    }

    /**
     * Broadcasts a forced reconnection message to every user connected to this Gateway instance.
     *
     * <p> Forces connected clients to reconnect and perform a pull-based synchronisation to fetch
     * messages stored directly in Cassandra during the Kafka outage. This ensures client local history
     * remains consistent with server state before real-time streaming resumes.
     */
    private void forceReconnectAll() {
        for (String username : registry.allUsernames()) {
            try {
                clientNotifier.requestReconnect(username, "BACKBONE_RECOVERED");
            } catch (Exception e) {
                log.warn("Could not send FORCE_RECONNECT to '{}': {}", username, e.getMessage());
            }
        }
    }
}
