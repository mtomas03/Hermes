package it.unibo.hermes.client.infrastructure.persistance;

import it.unibo.hermes.client.model.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.Optional;

/**
 * Persists the locally-authenticated user to enable session restore on relaunch.
 */
@Repository
public class LocalUserRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalUserRepository.class);

    private final DatabaseInitializer db;

    public LocalUserRepository(DatabaseInitializer db) {
        this.db = db;
    }

    public void save(User user) {
        String sql = "INSERT OR REPLACE INTO user (username) VALUES (?)";
        try (Connection conn = db.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.username());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save local user", e);
        }
    }

    public Optional<User> findFirst() {
        try (Connection conn = db.getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM user LIMIT 1")) {
            if (rs.next()) {
                return Optional.of(new User(rs.getString("username")));
            }
        } catch (SQLException e) {
            log.error("Failed to load local user", e);
        }
        return Optional.empty();
    }

    public void clear() {
        try (Connection conn = db.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM user");
        } catch (SQLException e) {
            log.error("Failed to clear local user", e);
        }
    }
}
