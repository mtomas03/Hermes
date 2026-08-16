package it.unibo.hermes.client.infrastructure.persistance;

import it.unibo.hermes.client.model.domain.SyncCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class SyncCursorRepository {

    private static final Logger log = LoggerFactory.getLogger(SyncCursorRepository.class);

    private final DatabaseInitializer db;

    public SyncCursorRepository(DatabaseInitializer db) {
        this.db = db;
    }

    public void upsert(SyncCursor cursor) {
        String sql = """
                INSERT INTO sync_cursor (conversation_id, last_synced_message_id, last_synced_at_epoch)
                VALUES (?, ?, ?)
                ON CONFLICT(conversation_id) DO UPDATE SET
                    last_synced_message_id = excluded.last_synced_message_id,
                    last_synced_at_epoch   = excluded.last_synced_at_epoch
                """;
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cursor.getConversationId());
            ps.setString(2, cursor.getLastSyncedMessageId());
            ps.setLong(3, cursor.getLastSyncedAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert sync cursor for {}", cursor.getConversationId(), e);
        }
    }

    public Optional<SyncCursor> findByConversation(String conversationId) {
        String sql = "SELECT * FROM sync_cursor WHERE conversation_id = ?";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SyncCursor(
                            rs.getString("conversation_id"),
                            rs.getString("last_synced_message_id"),
                            Instant.ofEpochMilli(rs.getLong("last_synced_at_epoch"))));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load sync cursor for {}", conversationId, e);
        }
        return Optional.empty();
    }
}
