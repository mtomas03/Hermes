package it.unibo.hermes.worker.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Lifecycle states of a message as tracked by the Delivery Worker.
 * State transitions are monotone: a message never moves backward.
 */
public enum DeliveryStatus {

    /**
     * Message has been accepted from Kafka but the delivery decision
     * has not yet been made or completed.
     */
    PENDING,

    /**
     * Recipient is offline; message is durably stored in Cassandra
     * and will be retrieved during the next synchronisation pull.
     */
    STORED,

    /**
     * Delivery event has been forwarded to the target gateway.
     * Waiting for the WebSocket push and subsequent acknowledgement.
     */
    DELIVERING,

    /**
     * Gateway pushed the message to the recipient's WebSocket.
     * Awaiting explicit client acknowledgement.
     */
    DELIVERED,

    /**
     * Recipient has explicitly acknowledged receipt.
     */
    ACKNOWLEDGED;

    /**
     * Returns whether a message in {@code from} may move to {@code to},
     * according to the monotone state machine defined by this enum.
     *
     * @param from the currently stored status
     * @param to   the requested status
     * @return {@code true} if a message in {@code from} may move to {@code to}
     */
    public static boolean canTransition(DeliveryStatus from, DeliveryStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PENDING -> to == STORED || to == DELIVERING || to == DELIVERED || to == ACKNOWLEDGED;
            case STORED -> to == DELIVERING || to == DELIVERED || to == ACKNOWLEDGED;
            case DELIVERING -> to == DELIVERED || to == ACKNOWLEDGED;
            case DELIVERED -> to == ACKNOWLEDGED;
            case ACKNOWLEDGED -> false;
        };
    }

    /**
     * Returns the list of valid predecessor states for the given {@code target} state.
     *
     * @param target the requested status
     * @return the valid predecessors (empty if nothing can precede {@code target})
     */
    public static List<DeliveryStatus> predecessorsOf(DeliveryStatus target) {
        return Arrays.stream(values())
                .filter(candidate -> canTransition(candidate, target))
                .toList();
    }

    /**
     * Returns the {@link DeliveryStatus} corresponding to the given {@code name}, if any.
     *
     * @param name the value of the {@code delivery_status} column
     * @return the status, or empty if {@code name} is null or not a known status
     */
    public static Optional<DeliveryStatus> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equals(name))
                .findFirst();
    }
}
