package it.unibo.hermes.client.infrastructure.persistance;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);

    private final DatabaseInitializer db;

    public MessageRepository(DatabaseInitializer db) {
        this.db = db;
    }

    /**
     * Insert a message if it does not already exist (idempotent – enables
     * safe replay during sync without duplication).
     */
    public void insertIfAbsent(Message msg) {
        String sql = """
                INSERT OR IGNORE INTO message
                    (message_id, conversation_id, sender_username, recipient_username,
                     content, message_timestamp, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getMessageId());
            ps.setString(2, msg.getConversationId());
            ps.setString(3, msg.getSenderUsername());
            ps.setString(4, msg.getRecipientUsername());
            ps.setString(5, msg.getContent());
            ps.setLong(6, msg.getTimestamp().toEpochMilli());
            ps.setString(7, msg.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert message {}", msg.getMessageId(), e);
        }
    }

    public void updateStatus(String messageId, MessageStatus status) {
        String sql = "UPDATE message SET status = ? WHERE message_id = ?";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update status for message {}", messageId, e);
        }
    }

    public List<Message> findByConversation(String conversationId) {
        String sql = """
                SELECT * FROM message
                WHERE conversation_id = ?
                ORDER BY message_timestamp ASC
                """;
        List<Message> result = new ArrayList<>();
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to load messages for conversation {}", conversationId, e);
        }
        return result;
    }

    public Optional<Message> findById(String messageId) {
        String sql = "SELECT * FROM message WHERE message_id = ?";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find message {}", messageId, e);
        }
        return Optional.empty();
    }

    /**
     * Returns the message with the highest timestamp for the given conversation.
     */
    public Optional<Message> findLastMessage(String conversationId) {
        String sql = """
                SELECT * FROM message
                WHERE conversation_id = ?
                ORDER BY message_timestamp DESC
                LIMIT 1
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find last message for {}", conversationId, e);
        }
        return Optional.empty();
    }

    private Message mapRow(ResultSet rs) throws SQLException {
        return new Message(
                rs.getString("message_id"),
                rs.getString("conversation_id"),
                rs.getString("sender_username"),
                rs.getString("recipient_username"),
                rs.getString("content"),
                Instant.ofEpochMilli(rs.getLong("message_timestamp")),
                MessageStatus.valueOf(rs.getString("status")));
    }
}
