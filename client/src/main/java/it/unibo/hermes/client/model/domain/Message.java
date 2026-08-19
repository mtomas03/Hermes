package it.unibo.hermes.client.model.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A single text message belonging to a conversation.
 */
public final class Message {
    private final String messageId;
    private final String conversationId;
    private final String senderUsername;
    private final String recipientUsername;
    private final String content;
    private final Instant physicalTimestamp;
    private final long logicalTimestamp;
    private volatile MessageStatus status;

    public Message(String messageId,
                   String conversationId,
                   String senderUsername,
                   String recipientUsername,
                   String content,
                   Long logicalTimestamp,
                   Instant physicalTimestamp,
                   MessageStatus status) {
        this.messageId = Objects.requireNonNull(messageId);
        this.conversationId = Objects.requireNonNull(conversationId);
        this.senderUsername = Objects.requireNonNull(senderUsername);
        this.recipientUsername = Objects.requireNonNull(recipientUsername);
        this.content = Objects.requireNonNull(content);
        this.logicalTimestamp = logicalTimestamp != null ? logicalTimestamp : 0L;
        this.physicalTimestamp = physicalTimestamp != null ? physicalTimestamp : Instant.now();
        this.status = status != null ? status : MessageStatus.SENT;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public String getContent() {
        return content;
    }

    public Instant getPhysicalTimestamp() {
        return physicalTimestamp;
    }

    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message m)) return false;
        return messageId.equals(m.messageId);
    }

    @Override
    public int hashCode() {
        return messageId.hashCode();
    }

    @Override
    public String toString() {
        return "Message[" + messageId + " from=" + senderUsername + " to=" + recipientUsername + "]";
    }
}
