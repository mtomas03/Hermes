package it.unibo.hermes.client.infrastructure.persistance;

import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final DatabaseInitializer db;

    public ConversationRepository(DatabaseInitializer db) {
        this.db = db;
    }

    public void upsert(Conversation c) {
        String sql = """
                INSERT INTO conversation
                    (conversation_id, peer_username, last_activity_epoch)
                VALUES (?, ?, ?)
                ON CONFLICT(conversation_id) DO UPDATE SET
                    peer_username       = excluded.peer_username,
                    last_activity_epoch = excluded.last_activity_epoch,
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getConversationId());
            ps.setString(2, c.getOtherUser().username());
            ps.setLong(3, c.getLastActivity().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert conversation {}", c.getConversationId(), e);
        }
    }

    public List<Conversation> findAll() {
        String sql = "SELECT * FROM conversation ORDER BY last_activity_epoch DESC";
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

    public Optional<Conversation> findById(String conversationId) {
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

    public void updateLastActivity(String conversationId, Instant lastActivity) {
        String sql = "UPDATE conversation SET last_activity_epoch = ? WHERE conversation_id = ?";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lastActivity.toEpochMilli());
            ps.setString(2, conversationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update last activity for {}", conversationId, e);
        }
    }

    private Conversation mapRow(ResultSet rs) throws SQLException {
        User peer = new User(rs.getString("peer_username"));
        return new Conversation(
                rs.getString("conversation_id"),
                peer,
                Instant.ofEpochMilli(rs.getLong("last_activity_epoch")));
    }
}
