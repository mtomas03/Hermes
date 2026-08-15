package it.unibo.hermes.worker.entity;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Cassandra entity representing a record in the {@code messages} table.
 *
 * <p> This table is optimised for querying messages belonging to a specific conversation,
 * ordered chronologically by their logical timestamp.
 */
@Table("messages")
public class ConversationMessageEntity {

    @PrimaryKey
    private ConversationMessageKey key;

    @Column("sender_username")
    private String senderUsername;

    @Column("recipient_username")
    private String recipientUsername;

    @Column("content")
    private String content;

    @Column("delivery_status")
    private String deliveryStatus;

    @Column("physical_timestamp")
    private Instant physicalTimestamp;

    /**
     * Constructs a fully initialised conversation message entity.
     *
     * @param key               the composite primary key
     * @param senderUsername    the username of the sender
     * @param recipientUsername the username of the recipient
     * @param content           the content of the message
     * @param deliveryStatus    the current delivery status
     * @param physicalTimestamp the server creation physical timestamp
     */
    public ConversationMessageEntity(ConversationMessageKey key, String senderUsername, String recipientUsername, String content, String deliveryStatus, Instant physicalTimestamp) {
        this.key = key;
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.deliveryStatus = deliveryStatus;
        this.physicalTimestamp = physicalTimestamp;
    }

    public ConversationMessageKey getKey() { return key; }

    public void setKey(ConversationMessageKey key) { this.key = key; }

    public String getSenderUsername() { return senderUsername; }

    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getRecipientUsername() { return recipientUsername; }

    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getDeliveryStatus() { return deliveryStatus; }

    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public Instant getPhysicalTimestamp() { return physicalTimestamp; }

    public void setPhysicalTimestamp(Instant physicalTimestamp) { this.physicalTimestamp = physicalTimestamp; }
}