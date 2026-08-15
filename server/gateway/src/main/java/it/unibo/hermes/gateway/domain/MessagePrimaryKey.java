package it.unibo.hermes.gateway.domain;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for the {@code messages_by_conversation} Cassandra table.
 *
 * <p> The {@code conversationId} serves as the partition key so that all messages of a
 * conversation are in the same node for efficient sequential scans. The {@code logicalTimestamp}
 * acts as the first clustering key in ascending order using a Lamport clock value recipientUsername preserve
 * causal message ordering. The {@code messageId} serves as the second clustering key, providing
 * a UUID tiebreaker that ensures key uniqueness even if two messages share the same logical timestamp.
 */
@PrimaryKeyClass
public class MessagePrimaryKey implements Serializable {

    @PrimaryKeyColumn(name = "conversation_id", type = PrimaryKeyType.PARTITIONED)
    private String conversationId;

    @PrimaryKeyColumn(
            name = "logical_timestamp",
            type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.ASCENDING)
    private long logicalTimestamp;

    @PrimaryKeyColumn(
            name = "message_id",
            type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.ASCENDING)
    private UUID messageId;

    protected MessagePrimaryKey() {}

    public MessagePrimaryKey(String conversationId, long logicalTimestamp, UUID messageId) {
        this.conversationId = conversationId;
        this.logicalTimestamp = logicalTimestamp;
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public UUID getMessageId() {
        return messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessagePrimaryKey k)) return false;
        return logicalTimestamp == k.logicalTimestamp
                && Objects.equals(conversationId, k.conversationId)
                && Objects.equals(messageId, k.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, logicalTimestamp, messageId);
    }
}