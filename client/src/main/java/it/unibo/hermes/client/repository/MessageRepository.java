package it.unibo.hermes.client.repository;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);

    private final SQLiteDatabaseConnection db;

    public MessageRepository(SQLiteDatabaseConnection db) {
        this.db = db;
    }

    /**
     * Inserts a message if it does not already exist.
     *
     * @param msg   the message to persist
     * @return {@code true} if the message is now durably stored,
     *         {@code false} if a database error occurred
     */
    public boolean insertIfAbsent(Message msg) {
        String sql = """
                INSERT OR IGNORE INTO message
                    (message_id, conversation_id, sender_username, recipient_username,
                     content, logical_timestamp, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getMessageId());
            ps.setString(2, msg.getConversationId());
            ps.setString(3, msg.getSenderUsername());
            ps.setString(4, msg.getRecipientUsername());
            ps.setString(5, msg.getContent());
            ps.setLong(6, msg.getLogicalTimestamp());
            ps.setString(7, msg.getStatus().name());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.error("Failed to insert message {}", msg.getMessageId(), e);
            return false;
        }
    }

    /**
     * Updates the status of a message.
     *
     * @param messageId     the identifier of the message to update
     * @param status        the new message status to set
     */
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

    /**
     * Retrieves all messages for a given conversation,
     * ordered by logical timestamp and message ID.
     *
     * @param conversationId    the identifier of the conversation
     * @return a list of messages belonging to the specified conversation
     */
    public List<Message> findByConversation(String conversationId) {
        String sql = """
                SELECT * FROM message
                WHERE conversation_id = ?
                ORDER BY logical_timestamp, message_id
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

    /**
     * Retrieves the last logical timestamp for a given conversation.
     *
     * @param conversationId    the identifier of the conversation
     * @return the last logical timestamp, or 0 if no messages exist
     */
    public long getLastLogicalTimestamp(String conversationId) {
        String sql = """
                SELECT MAX(logical_timestamp) AS last_logical
                FROM message
                WHERE conversation_id = ?
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("last_logical");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find last logical timestamp for {}", conversationId, e);
        }
        return 0L;
    }

    private Message mapRow(ResultSet rs) throws SQLException {
        return new Message(
                rs.getString("message_id"),
                rs.getString("conversation_id"),
                rs.getString("sender_username"),
                rs.getString("recipient_username"),
                rs.getString("content"),
                rs.getLong("logical_timestamp"),
                MessageStatus.valueOf(rs.getString("status")));
    }
}
