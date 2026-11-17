package it.unibo.hermes.worker.configuration;

/**
 * Defines the policy for handling failures in Kafka consumers.
 */
public final class ConsumerFailurePolicy {

    private ConsumerFailurePolicy() {
    }

    /**
     * Determines if a failed delivery attempt must be reported at error level as a stuck consumer.
     *
     * @param deliveryAttempt the number of failed delivery attempts
     * @param threshold       the number of failed attempts after which the consumer is considered stuck
     * @return {@code true} if the consumer is considered stuck and the failure must be reported at error level,
     *         {@code false} otherwise
     */
    public static boolean isStuckAlert(int deliveryAttempt, int threshold) {
        return threshold > 0 && deliveryAttempt >= threshold && deliveryAttempt % threshold == 0;
    }
}
