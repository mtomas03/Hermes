package it.unibo.hermes.worker.entity;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Cassandra entity representing a record in the {@code message_by_id} table.
 *
 * <p>This table acts as a secondary view denormalised by {@code message_id},
 * allowing O(1) single-partition lookups and updates without needing the conversation context.
 */
@Table("message_by_id")
public class MessageByIdEntity {

    @PrimaryKey("message_id")
    private UUID messageId;

    @Column("conversation_id")
    private UUID conversationId;

    @Column("sender_id")
    private String senderId;

    @Column("recipient_id")
    private String recipientId;

    @Column("content")
    private String content;

    @Column("logical_timestamp")
    private long logicalTimestamp;

    @Column("physical_timestamp")
    private Instant physicalTimestamp;

    @Column("delivery_status")
    private String deliveryStatus;

    /**
     * Constructs a fully initialised message-by-id entity.
     *
     * @param messageId         the unique identifier of the message
     * @param conversationId    the unique identifier of the conversation
     * @param senderId          the identifier of the sender
     * @param recipientId       the identifier of the recipient
     * @param content           the textual content of the message
     * @param logicalTimestamp  the Lamport logical timestamp of the message
     * @param physicalTimestamp the server creation physical timestamp
     * @param deliveryStatus    the current delivery status name
     */
    public MessageByIdEntity(UUID messageId, UUID conversationId, String senderId, String recipientId, String content, long logicalTimestamp, Instant physicalTimestamp, String deliveryStatus) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.logicalTimestamp = logicalTimestamp;
        this.physicalTimestamp = physicalTimestamp;
        this.deliveryStatus = deliveryStatus;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public void setLogicalTimestamp(long logicalTimestamp) {
        this.logicalTimestamp = logicalTimestamp;
    }

    public Instant getPhysicalTimestamp() {
        return physicalTimestamp;
    }

    public void setPhysicalTimestamp(Instant physicalTimestamp) {
        this.physicalTimestamp = physicalTimestamp;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }
}