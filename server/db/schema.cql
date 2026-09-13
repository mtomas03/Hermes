CREATE KEYSPACE IF NOT EXISTS hermes
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
     AND durable_writes = true;

USE hermes;

CREATE TABLE IF NOT EXISTS message_by_id
(
    message_id         UUID PRIMARY KEY,
    conversation_id    TEXT,
    sender_username    TEXT,
    recipient_username TEXT,
    content            TEXT,
    logical_timestamp  BIGINT,
    delivery_status    TEXT
);

CREATE TABLE IF NOT EXISTS messages_by_conversation
(
    conversation_id    TEXT,
    logical_timestamp  BIGINT,
    message_id         UUID,
    sender_username    TEXT,
    recipient_username TEXT,
    content            TEXT,
    delivery_status    TEXT,
    PRIMARY KEY ((conversation_id), logical_timestamp, message_id)
) WITH CLUSTERING ORDER BY (logical_timestamp DESC, message_id DESC);
