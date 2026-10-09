package it.unibo.hermes.worker.entity.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;


/**
 * Cassandra entity representing a record in the {@code messages_by_conversation} table.
 *
 * <p> This table is optimised for querying messages belonging to a specific conversation,
 * ordered chronologically by their logical timestamp.
 */
@Table("messages_by_conversation")
public class MessageByConversation {

    @PrimaryKey
    private MessageByConversationPrimaryKey key;

    @Column("sender_username")
    private String senderUsername;

    @Column("recipient_username")
    private String recipientUsername;

    @Column("content")
    private String content;

    @Column("delivery_status")
    private String deliveryStatus;


    /**
     * Constructs a fully initialised conversation message entity.
     *
     * @param key               the composite primary key
     * @param senderUsername    the username of the sender
     * @param recipientUsername the username of the recipient
     * @param content           the content of the message
     * @param deliveryStatus    the current delivery status
     */
    public MessageByConversation(MessageByConversationPrimaryKey key, String senderUsername, String recipientUsername, String content, String deliveryStatus) {
        this.key = key;
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.deliveryStatus = deliveryStatus;
    }

    public MessageByConversationPrimaryKey getKey() {
        return key;
    }

    public void setKey(MessageByConversationPrimaryKey key) {
        this.key = key;
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

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }


}