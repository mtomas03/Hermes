package it.unibo.hermes.client.repository;

import it.unibo.hermes.client.model.domain.AuthToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * Persists the single JWT of the logged-in user so that
 * it survives an application restart until its expiry. Holds at most one row.
 */
@Repository
public class AuthTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenRepository.class);

    private final SQLiteDatabaseConnection db;

    public AuthTokenRepository(SQLiteDatabaseConnection db) {
        this.db = db;
    }

    /**
     * Replaces any stored token with the given one.
     *
     * @param token the token to store
     */
    public void save(AuthToken token) {
        String sql = "INSERT OR REPLACE INTO auth_token (id, raw_token, expires_at) VALUES (1, ?, ?)";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.rawToken());
            ps.setLong(2, token.expiresAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to persist auth token: {}", e.getMessage());
        }
    }

    /**
     * Returns the stored token (expired or not), if any.
     *
     * @return an Optional containing the token if present, or empty if not
     */
    public Optional<AuthToken> find() {
        try (Connection conn = db.getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT raw_token, expires_at FROM auth_token WHERE id = 1")) {
            if (rs.next()) {
                return Optional.of(new AuthToken(rs.getString("raw_token"),
                        Instant.ofEpochMilli(rs.getLong("expires_at"))));
            }
        } catch (SQLException e) {
            log.error("Failed to load auth token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Deletes every stored token.
     */
    public void clear() {
        try (Connection conn = db.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM auth_token");
        } catch (SQLException e) {
            log.error("Failed to clear auth token: {}", e.getMessage());
        }
    }
}
