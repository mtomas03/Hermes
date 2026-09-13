package it.unibo.hermes.gateway.entity;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for the {@code messages_by_conversation} Cassandra table.
 */
@PrimaryKeyClass
public class MessageByConversationPrimaryKey implements Serializable {

    @PrimaryKeyColumn(
            name = "conversation_id",
            ordinal = 0,
            type = PrimaryKeyType.PARTITIONED)
    private String conversationId;

    @PrimaryKeyColumn(
            name = "logical_timestamp",
            ordinal = 1,
            type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.ASCENDING)
    private Long logicalTimestamp;

    @PrimaryKeyColumn(
            name = "message_id",
            ordinal = 2,
            type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.ASCENDING)
    private UUID messageId;

    public MessageByConversationPrimaryKey(String conversationId, long logicalTimestamp, UUID messageId) {
        this.conversationId = conversationId;
        this.logicalTimestamp = logicalTimestamp;
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public void setLogicalTimestamp(long logicalTimestamp) {
        this.logicalTimestamp = logicalTimestamp;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageByConversationPrimaryKey that)) return false;
        return Objects.equals(logicalTimestamp, that.logicalTimestamp)
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, logicalTimestamp, messageId);
    }
}
