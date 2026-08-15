package it.unibo.hermes.client.model.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a 1-to-1 conversation between the local user and one other user.
 */
public final class Conversation {
    private final String conversationId;
    private final User otherUser;
    private volatile Instant lastActivity;

    public Conversation(String conversationId, User otherUser, Instant lastActivity) {
        this.conversationId = Objects.requireNonNull(conversationId);
        this.otherUser = Objects.requireNonNull(otherUser);
        this.lastActivity = lastActivity != null ? lastActivity : Instant.EPOCH;
    }

    public String getConversationId() {
        return conversationId;
    }

    public User getOtherUser() {
        return otherUser;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Instant t) {
        this.lastActivity = t;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversation c)) return false;
        return conversationId.equals(c.conversationId);
    }

    @Override
    public int hashCode() {
        return conversationId.hashCode();
    }

    @Override
    public String toString() {
        return "Conversation[" + conversationId + " peer=" + otherUser.username() + "]";
    }
}
