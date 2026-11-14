package it.unibo.hermes.client.model.repository;

import it.unibo.hermes.client.infrastructure.config.AppProperties;
import it.unibo.hermes.client.infrastructure.exception.SQLitePersistenceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the shared SQLite connection, connection PRAGMAs and schema versioning.
 */
@Component
public class SQLiteDatabaseConnection {

    public static final int SCHEMA_VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger(SQLiteDatabaseConnection.class);
    private static final int BUSY_TIMEOUT_MS = 5000;

    private final DataSource dataSource;
    private final ReentrantLock connectionLock = new ReentrantLock();
    private Connection managedConnection;

    public SQLiteDatabaseConnection(AppProperties props) {
        if (props != null) {
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setBusyTimeout(BUSY_TIMEOUT_MS);
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            SQLiteDataSource ds = new SQLiteDataSource(config);
            ds.setUrl("jdbc:sqlite:" + props.getDbPath());
            this.dataSource = ds;
            log.info("SQLite database configured at: {}", props.getDbPath());
        } else {
            // Subclass will override getDataSource() - leave null here
            this.dataSource = null;
        }
    }

    /**
     * Returns the DataSource. Overrideable by test subclasses.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Returns a connection to the SQLite database.
     */
    public Connection getConnection() throws SQLException {
        connectionLock.lock();
        try {
            if (managedConnection == null || managedConnection.isClosed()) {
                DataSource ds = getDataSource();
                if (ds == null) {
                    throw new SQLException("SQLite DataSource is not configured");
                }
                Connection raw = ds.getConnection();
                configureConnection(raw);
                managedConnection = raw;
            }
            return wrapConnection(managedConnection);
        } catch (SQLException | RuntimeException e) {
            connectionLock.unlock();
            throw e;
        }
    }

    /**
     * Executes the given operation in a transaction,
     * committing if successful or rolling back on failure.
     *
     * @param operation the operation to execute in a transaction
     */
    public void inTransaction(Runnable operation) {
        try (Connection conn = getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                operation.run();
                conn.commit();
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Database transaction failed", e);
        }
    }

    private void configureConnection(Connection conn) throws SQLException {
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
        }
    }

    private Connection wrapConnection(Connection physical) {
        AtomicBoolean proxyClosed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (proxyClosed.compareAndSet(false, true)) {
                            try {
                                if (connectionLock.getHoldCount() == 1
                                        && !physical.isClosed()
                                        && !physical.getAutoCommit()) {
                                    physical.rollback();
                                    physical.setAutoCommit(true);
                                }
                            } finally {
                                connectionLock.unlock();
                            }
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return proxyClosed.get() || physical.isClosed();
                    }
                    if (proxyClosed.get()) {
                        throw new SQLException("Connection lease has already been closed");
                    }
                    try {
                        return method.invoke(physical, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }
        );
    }

    @PostConstruct
    public void init() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            int currentVersion = readUserVersion(stmt);
            if (currentVersion > SCHEMA_VERSION) {
                throw new IllegalStateException(
                        "Unsupported database schema version " + currentVersion
                                + " (expected <= " + SCHEMA_VERSION + ")");
            }

            conn.setAutoCommit(false);
            try {
                createSchema(stmt);
                stmt.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                conn.commit();
                log.info("Database schema initialised (version {})", SCHEMA_VERSION);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise database schema", e);
        }
    }

    private int readUserVersion(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void createSchema(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    username TEXT NOT NULL
                )""");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS auth_token (
                    id         INTEGER PRIMARY KEY CHECK (id = 1),
                    raw_token  TEXT    NOT NULL,
                    expires_at INTEGER NOT NULL
                )""");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversation (
                    conversation_id       TEXT PRIMARY KEY,
                    recipient_username    TEXT NOT NULL
                )""");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS message (
                    message_id         TEXT PRIMARY KEY,
                    conversation_id    TEXT NOT NULL,
                    sender_username    TEXT NOT NULL,
                    recipient_username TEXT NOT NULL,
                    content            TEXT NOT NULL,
                    logical_timestamp  INTEGER NOT NULL DEFAULT 0,
                    status             TEXT NOT NULL DEFAULT 'PENDING',
                    delivery_attempts  INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (conversation_id) REFERENCES conversation(conversation_id)
                )""");

        stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_message_conv_logical
                    ON message(conversation_id, logical_timestamp)""");
    }

    @PreDestroy
    public void close() {
        connectionLock.lock();
        try {
            if (managedConnection != null && !managedConnection.isClosed()) {
                managedConnection.close();
                log.info("Closed SQLite managed connection");
            }
        } catch (SQLException e) {
            log.warn("Error closing SQLite managed connection", e);
        } finally {
            managedConnection = null;
            connectionLock.unlock();
        }
    }
}
