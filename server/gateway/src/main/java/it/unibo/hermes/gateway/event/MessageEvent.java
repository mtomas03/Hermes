package it.unibo.hermes.gateway.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Message event published to the Kafka messaging topic for asynchronous downstream processing.
 */
public class MessageEvent {

    private UUID messageId;
    private String conversationId;
    private String senderUsername;
    private String recipientUsername;
    private String content;
    private Long logicalTimestamp;
    private Instant physicalTimestamp;

    public MessageEvent() {
    }

    /**
     * Creates a new message event.
     *
     * @param messageId         the unique identifier of the message
     * @param conversationId    the unique identifier of the conversation
     * @param senderUsername    the username of the sender
     * @param recipientUsername the username of the recipient
     * @param content           the message content (text body)
     * @param logicalTimestamp  the Lamport timestamp establishing causal ordering
     * @param physicalTimestamp the timestamp in milliseconds
     */
    public MessageEvent(UUID messageId,
                        String conversationId,
                        String senderUsername,
                        String recipientUsername,
                        String content,
                        Long logicalTimestamp,
                        Instant physicalTimestamp) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.content = content;
        this.logicalTimestamp = logicalTimestamp;
        this.physicalTimestamp = physicalTimestamp;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID id) {
        this.messageId = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String id) {
        this.conversationId = id;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String id) {
        this.senderUsername = id;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String id) {
        this.recipientUsername = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String c) {
        this.content = c;
    }

    public Long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public void setLogicalTimestamp(Long logicalTimestamp) {
        this.logicalTimestamp = logicalTimestamp;
    }

    public Instant getPhysicalTimestamp() {
        return physicalTimestamp;
    }

    public void setPhysicalTimestamp(Instant physicalTimestamp) {
        this.physicalTimestamp = physicalTimestamp;
    }
}