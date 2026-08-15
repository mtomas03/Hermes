package it.unibo.hermes.client.model.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Tracks the last-synced message for a given conversation.
 * Advances only forward: once set, it never moves backwards.
 */
public final class SyncCursor {
    private final String conversationId;
    private volatile String lastSyncedMessageId;
    private volatile Instant lastSyncedAt;

    public SyncCursor(String conversationId, String lastSyncedMessageId, Instant lastSyncedAt) {
        this.conversationId = Objects.requireNonNull(conversationId);
        this.lastSyncedMessageId = lastSyncedMessageId;
        this.lastSyncedAt = lastSyncedAt != null ? lastSyncedAt : Instant.EPOCH;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getLastSyncedMessageId() {
        return lastSyncedMessageId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void advance(String messageId, Instant syncedAt) {
        this.lastSyncedMessageId = messageId;
        this.lastSyncedAt = syncedAt;
    }
}
