package it.unibo.hermes.gateway.entity;

import it.unibo.hermes.gateway.domain.MessageStatus;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

/**
 * Cassandra entity representing a record in the {@code messages_by_conversation} table.
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

    public MessageByConversation(
            UUID messageId,
            String conversationId,
            String senderUsername,
            String recipientUsername,
            String content,
            Long logicalTimestamp,
            MessageStatus status) {
        this.key = new MessageByConversationPrimaryKey(conversationId, logicalTimestamp, messageId);
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.deliveryStatus = status.name();
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

    public MessageStatus getDeliveryStatus() {
        return MessageStatus.valueOf(deliveryStatus);
    }
}