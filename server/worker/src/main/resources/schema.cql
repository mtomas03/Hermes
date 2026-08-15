CREATE KEYSPACE IF NOT EXISTS hermes
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
     AND durable_writes = true;

USE hermes;

-- Lookup table: fast access by messageId
-- Used by Worker for idempotency checks and status updates.
CREATE TABLE IF NOT EXISTS message_by_id
(
    message_id         UUID PRIMARY KEY,
    conversation_id    TEXT,
    sender_username    TEXT,
    recipient_username TEXT,
    content            TEXT,
    logical_timestamp  BIGINT,
    physical_timestamp TIMESTAMP,
    delivery_status    TEXT,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP
);

-- Conversation-ordered table: partitioned by conversation_id.
-- Read by Gateway during pull-based synchronisation.
-- Worker writes here on persist; Gateway reads here on sync.
CREATE TABLE IF NOT EXISTS messages
(
    conversation_id    TEXT,
    logical_timestamp  BIGINT,
    message_id         UUID,
    sender_username    TEXT,
    recipient_username TEXT,
    content            TEXT,
    delivery_status    TEXT,
    physical_timestamp TIMESTAMP,
    PRIMARY KEY (conversation_id, logical_timestamp, message_id)
) WITH CLUSTERING ORDER BY (logical_timestamp ASC, message_id ASC);
