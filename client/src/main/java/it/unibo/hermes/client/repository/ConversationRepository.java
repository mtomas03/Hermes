package it.unibo.hermes.client.repository;

import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final SQLiteDatabaseConnection db;

    public ConversationRepository(SQLiteDatabaseConnection db) {
        this.db = db;
    }

    /**
     * Upserts a conversation into the database.
     * If a conversation with the same ID already exists, it will be updated.
     *
     * @param c the conversation to upsert
     */
    public void upsert(Conversation c) {
        String sql = """
                INSERT INTO conversation
                    (conversation_id, recipient_username)
                VALUES (?, ?)
                ON CONFLICT(conversation_id) DO UPDATE SET
                    recipient_username  = excluded.recipient_username
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.conversationId());
            ps.setString(2, c.recipientUsername().username());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert conversation {}", c.conversationId(), e);
        }
    }

    /**
     * Retrieves all conversations from the database.
     *
     * @return a list of all conversations
     */
    public List<Conversation> findAll() {
        String sql = "SELECT * FROM conversation";
        List<Conversation> result = new ArrayList<>();
        try (Connection conn = db.getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to load conversations", e);
        }
        return result;
    }

    /**
     * Finds a conversation by its ID.
     *
     * @param conversationId    the ID of the conversation to find
     * @return an Optional containing the conversation if found, or empty if not found
     */
    public Optional<Conversation> findByConversationId(String conversationId) {
        String sql = "SELECT * FROM conversation WHERE conversation_id = ?";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find conversation {}", conversationId, e);
        }
        return Optional.empty();
    }

    private Conversation mapRow(ResultSet rs) throws SQLException {
        User recipientUsername = new User(rs.getString("recipient_username"));
        return new Conversation(
                rs.getString("conversation_id"),
                recipientUsername);
    }
}
