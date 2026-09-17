package it.unibo.hermes.worker.entity.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

/**
 * Cassandra entity representing a record in the {@code message_by_id} table.
 */
@Table("message_by_id")
public class MessageById {

    @PrimaryKey("message_id")
    private UUID messageId;

    @Column("conversation_id")
    private String conversationId;

    @Column("sender_username")
    private String senderUsername;

    @Column("recipient_username")
    private String recipientUsername;

    @Column("content")
    private String content;

    @Column("logical_timestamp")
    private long logicalTimestamp;

    @Column("delivery_status")
    private String deliveryStatus;

    /**
     * Constructs a fully initialised message-by-id entity.
     *
     * @param messageId         the unique identifier of the message
     * @param conversationId    the unique identifier of the conversation
     * @param senderUsername    the identifier of the sender
     * @param recipientUsername the identifier of the recipient
     * @param content           the textual content of the message
     * @param logicalTimestamp  the Lamport logical timestamp of the message
     * @param deliveryStatus    the current delivery status name
     */
    public MessageById(UUID messageId, String conversationId,
                       String senderUsername, String recipientUsername,
                       String content, long logicalTimestamp, String deliveryStatus) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.logicalTimestamp = logicalTimestamp;
        this.deliveryStatus = deliveryStatus;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
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

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }
}