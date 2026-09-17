package it.unibo.hermes.worker.entity.cassandra;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * Cassandra entity representing a record in the {@code conversations_by_user} table.
 */
@Table("conversations_by_user")
public class ConversationByUser {

    @PrimaryKeyColumn(name = "username", type = PrimaryKeyType.PARTITIONED)
    private String username;

    @PrimaryKeyColumn(name = "conversation_id", type = PrimaryKeyType.CLUSTERED)
    private String conversationId;

    @Column("other_participant")
    private String otherParticipant;

    public ConversationByUser(String username, String conversationId, String otherParticipant) {
        this.username = username;
        this.conversationId = conversationId;
        this.otherParticipant = otherParticipant;
    }

    public String getUsername() {
        return username;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getOtherParticipant() {
        return otherParticipant;
    }
}