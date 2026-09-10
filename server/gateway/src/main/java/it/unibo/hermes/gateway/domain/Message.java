package it.unibo.hermes.gateway.domain;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single chat message persisted in Cassandra.
 *
 * <p> This entity is written either by the Worker on the normal Kafka path
 * or directly by the Gateway on the fallback path when Kafka is unavailable.
 * It is read by the Gateway during pull-based synchronisation.
 */
@Table("messages_by_conversation")
public class Message {

    @PrimaryKey
    private MessagePrimaryKey key;

    @Column("sender_username")
    private String senderUsername;

    @Column("recipient_username")
    private String recipientUsername;

    @Column("content")
    private String content;

    @Column("physical_timestamp")
    private Instant physicalTimestamp;

    @Column("status")
    private String status;

    protected Message() {
    }

    public Message(
            UUID messageId,
            String conversationId,
            String senderUsername,
            String recipientUsername,
            String content,
            Long logicalTimestamp,
            Instant physicalTimestamp,
            MessageStatus status) {
        this.key = new MessagePrimaryKey(conversationId, logicalTimestamp, messageId);
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.physicalTimestamp = physicalTimestamp;
        this.status = status.name();
    }

    public static String conversationId(String userA, String userB) {
        String username1 = userA.trim().toLowerCase();
        String username2 = userB.trim().toLowerCase();

        return username1.compareTo(username2) <= 0
                ? username1 + "-" + username2
                : username2 + "-" + username1;
    }

    public String getConversationId() {
        return key.getConversationId();
    }

    public long getLogicalTimestamp() {
        return key.getLogicalTimestamp();
    }

    public UUID getMessageId() {
        return key.getMessageId();
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

    public MessageStatus getStatus() {
        return MessageStatus.valueOf(status);
    }

    public void setStatus(MessageStatus s) {
        this.status = s.name();
    }
}