package it.unibo.hermes.client.model.domain;

import java.util.Optional;

public enum MessageStatus {

    /**
     * Message is composed locally, not yet sent or saved by the server.
     */
    PENDING(1),

    /**
     * Message is sent to the server and saved in the server message database.
     */
    SENT(2),

    /**
     * Message is stored in the server message database.
     */
    STORED(3),

    /**
     * Message is currently being delivered by the server to the recipient.
     */
    DELIVERING(4),

    /**
     * Message has been delivered to the recipient.
     */
    DELIVERED(5),

    /**
     * Message has been acknowledged by the recipient client.
     */
    ACKNOWLEDGED(6),

    /**
     * Message was permanently rejected or could not be sent.
     */
    FAILED(0);

    private final int rank;

    MessageStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /**
     * Returns {@code true} if the transition from
     * the current status to {@code next}
     * is valid with respect to the monotonic message lifecycle.
     */
    public boolean canTransitionTo(MessageStatus next) {
        if (next == null || this == next) {
            return false;
        }
        if (this == PENDING && next == FAILED) {
            return true;
        }
        return this != FAILED && next != FAILED && next.rank > this.rank;
    }

    /**
     * Maps a raw status string from the server into a {@link MessageStatus}.
     */
    public static Optional<MessageStatus> fromServerStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        return switch (rawStatus.trim().toUpperCase()) {
            case "PENDING" -> Optional.of(PENDING);
            case "SENT", "ACCEPTED" -> Optional.of(SENT);
            case "STORED" -> Optional.of(STORED);
            case "DELIVERING" -> Optional.of(DELIVERING);
            case "DELIVERED" -> Optional.of(DELIVERED);
            case "ACKNOWLEDGED" -> Optional.of(ACKNOWLEDGED);
            case "FAILED", "REJECTED" -> Optional.of(FAILED);
            default -> Optional.empty();
        };
    }
}
