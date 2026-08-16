package it.unibo.hermes.client.infrastructure.persistance;

import it.unibo.hermes.client.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initialises the local SQLite database schema on application start.
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;

    public DatabaseInitializer(AppProperties props) {
        if (props != null) {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + props.getDbPath());
            this.dataSource = ds;
            log.info("SQLite database configured at: {}", props.getDbPath());
        } else {
            // Subclass will override getDataSource() – leave null here
            this.dataSource = null;
        }
    }

    /**
     * Returns the DataSource. Overrideable by test subclasses.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    @PostConstruct
    public void init() {
        try (Connection conn = getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS local_user (
                        username TEXT NOT NULL
                    )""");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS conversation (
                        conversation_id       TEXT PRIMARY KEY,
                        peer_username         TEXT NOT NULL,
                        last_activity_epoch   INTEGER NOT NULL DEFAULT 0
                    )""");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS message (
                        message_id         TEXT PRIMARY KEY,
                        conversation_id    TEXT NOT NULL,
                        sender_username    TEXT NOT NULL,
                        recipient_username TEXT NOT NULL,
                        content            TEXT NOT NULL,
                        message_timestamp  INTEGER NOT NULL DEFAULT 0,
                        status             TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY (conversation_id) REFERENCES conversation(conversation_id)
                    )""");

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_message_conv
                        ON message(conversation_id, message_timestamp)""");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sync_cursor (
                        conversation_id        TEXT PRIMARY KEY,
                        last_synced_message_id TEXT,
                        last_synced_at_epoch   INTEGER NOT NULL DEFAULT 0
                    )""");

            conn.commit();
            log.info("Database schema initialised/verified");

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise database schema", e);
        }
    }
}
