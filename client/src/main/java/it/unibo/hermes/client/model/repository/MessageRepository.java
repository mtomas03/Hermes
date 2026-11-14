package it.unibo.hermes.client.model.repository;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);

    private static final String STATUS_RANK_SQL = """
            CASE %s
                WHEN 'PENDING'      THEN 1
                WHEN 'SENT'         THEN 2
                WHEN 'STORED'       THEN 3
                WHEN 'DELIVERING'   THEN 4
                WHEN 'DELIVERED'    THEN 5
                WHEN 'ACKNOWLEDGED' THEN 6
                ELSE 0
            END
            """;

    private final SQLiteDatabaseConnection db;

    public MessageRepository(SQLiteDatabaseConnection db) {
        this.db = db;
    }

    /**
     * Inserts a message if absent or advances its status monotonically if it already exists.
     *
     * @param msg the message to persist or update
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean upsertMessage(Message msg) {
        String sql = """
                INSERT INTO message
                    (message_id, conversation_id, sender_username, recipient_username,
                     content, logical_timestamp, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(message_id) DO UPDATE SET status = excluded.status
                WHERE (message.status = 'PENDING' AND excluded.status = 'FAILED')
                   OR (message.status <> 'FAILED'
                       AND excluded.status <> 'FAILED'
                       AND (%s) > (%s))
                """.formatted(
                STATUS_RANK_SQL.formatted("excluded.status"),
                STATUS_RANK_SQL.formatted("message.status"));
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
            log.error("Failed to upsert message {}", msg.getMessageId(), e);
            return false;
        }
    }

    /**
     * Updates the status of a message
     * only if the transition is monotonically valid.
     *
     * @param messageId the identifier of the message to update
     * @param status    the new message status to set
     */
    public void updateStatus(String messageId, MessageStatus status) {
        String sql = """
                UPDATE message
                SET status = ?
                WHERE message_id = ?
                  AND ((status = 'PENDING' AND ? = 'FAILED')
                       OR (status <> 'FAILED'
                           AND ? <> 'FAILED'
                           AND (%s) > (%s)))
                """.formatted(
                STATUS_RANK_SQL.formatted("?"),
                STATUS_RANK_SQL.formatted("status"));
        String nextStatus = status.name();
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nextStatus);
            ps.setString(2, messageId);
            ps.setString(3, nextStatus);
            ps.setString(4, nextStatus);
            ps.setString(5, nextStatus);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update status for message {}", messageId, e);
        }
    }

    /**
     * Retrieves all messages for a given conversation,
     * ordered by logical timestamp and message ID.
     *
     * @param conversationId the identifier of the conversation
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
     * Retrieves the messages sent by {@code senderUsername} that the server has not confirmed yet
     * (status {@link MessageStatus#PENDING}), in the order in which they were created locally.
     *
     * @param senderUsername the local user who created the messages
     * @return the unconfirmed outbound messages, oldest first
     */
    public List<Message> findPendingBySender(String senderUsername) {
        String sql = """
                SELECT * FROM message
                WHERE status = 'PENDING' AND sender_username = ?
                ORDER BY rowid
                """;
        List<Message> result = new ArrayList<>();
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, senderUsername);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to load pending messages of {}", senderUsername, e);
        }
        return result;
    }

    /**
     * Retrieves the last logical timestamp for a given conversation.
     *
     * @param conversationId the identifier of the conversation
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

    /**
     * Returns how many times a message has been attempted to be delivered.
     *
     * @param messageId the identifier of the message
     * @return the number of recorded delivery attempts, or 0 if not found
     */
    public int getDeliveryAttempts(String messageId) {
        String sql = "SELECT delivery_attempts FROM message WHERE message_id = ?";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("delivery_attempts");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to read delivery attempts for message {}", messageId, e);
        }
        return 0;
    }

    /**
     * Increments and returns the delivery attempt count
     * for a {@code PENDING} message.
     *
     * @param messageId the identifier of the message
     * @return the updated attempt count
     */
    public int incrementDeliveryAttempts(String messageId) {
        String updateSql = """
                UPDATE message
                SET delivery_attempts = delivery_attempts + 1
                WHERE message_id = ? AND status = 'PENDING'
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to increment delivery attempts for message {}", messageId, e);
        }
        return getDeliveryAttempts(messageId);
    }

    /**
     * Clears all messages from the database.
     */
    public void clear() {
        try (Connection conn = db.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM message");
        } catch (SQLException e) {
            log.error("Failed to clear local messages", e);
        }
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
