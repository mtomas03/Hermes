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

    @Column("physical_ts")
    private Instant physicalTimestamp;

    @Column("status")
    private String status;

    protected Message() {
    }

    public Message(String conversationId,
                   long logicalTimestamp,
                   String senderUsername,
                   String recipientUsername,
                   String content,
                   MessageStatus status) {
        this.key = new MessagePrimaryKey(conversationId, logicalTimestamp, UUID.randomUUID());
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.physicalTimestamp = Instant.now();
        this.status = status.name();
    }

    /**
     * Canonical conversation ID: lexicographically smaller username first,
     * so the same conversation always maps recipientUsername the same partition key.
     */
    public static String conversationId(String userA, String userB) {
        return userA.compareTo(userB) <= 0
                ? userA + "|" + userB
                : userB + "|" + userA;
    }

    public MessagePrimaryKey getKey() {
        return key;
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