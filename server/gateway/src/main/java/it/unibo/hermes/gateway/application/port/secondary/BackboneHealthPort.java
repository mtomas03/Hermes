package it.unibo.hermes.gateway.application.port.secondary;

/**
 * Secondary port interface for checking the health of the message backbone.
 */
public interface BackboneHealthPort {

    /**
     * Checks the health of the message backbone.
     *
     * @return true if the message backbone is healthy, false otherwise
     */
    boolean isHealthy();
}
