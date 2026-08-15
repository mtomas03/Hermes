package it.unibo.hermes.worker.entity;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for the {@code messages} Cassandra table.
 *
 * <p>The primary key is structured to support efficient range queries per conversation:
 * <ul>
 *   <li>{@code conversationId} serves as the partition key, placing all messages of a chat on the same node.</li>
 *   <li>{@code logicalTimestamp} serves as the primary clustering key, ordering messages chronologically.</li>
 *   <li>{@code messageId} serves as a secondary clustering key to guarantee uniqueness when timestamps coincide.</li>
 * </ul>
 */
@PrimaryKeyClass
public class ConversationMessageKey implements Serializable {

    @PrimaryKeyColumn(name = "conversation_id", type = PrimaryKeyType.PARTITIONED)
    private String conversationId;

    @PrimaryKeyColumn(name = "logical_timestamp", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private long logicalTimestamp;

    @PrimaryKeyColumn(name = "message_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private UUID messageId;

    /**
     * Constructs a composite key with all required identifier fields.
     *
     * @param conversationId   the unique identifier of the conversation
     * @param logicalTimestamp the Lamport logical timestamp of the message
     * @param messageId        the unique identifier of the message
     */
    public ConversationMessageKey(String conversationId, long logicalTimestamp, UUID messageId) {
        this.conversationId = conversationId;
        this.logicalTimestamp = logicalTimestamp;
        this.messageId = messageId;
    }

    public String getConversationId() { return conversationId; }

    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public long getLogicalTimestamp() { return logicalTimestamp; }

    public void setLogicalTimestamp(long logicalTimestamp) { this.logicalTimestamp = logicalTimestamp; }

    public UUID getMessageId() { return messageId; }

    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationMessageKey that)) return false;
        return logicalTimestamp == that.logicalTimestamp
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, logicalTimestamp, messageId);
    }
}