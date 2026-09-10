CREATE TABLE IF NOT EXISTS messages_by_conversation
(
    conversation_id     TEXT,
    logical_timestamp   BIGINT,
    message_id          UUID,
    sender_username     TEXT,
    recipient_username  TEXT,
    content             TEXT,
    physical_timestamp  TIMESTAMP,
    status              TEXT,
    PRIMARY KEY ((conversation_id), logical_timestamp, message_id)
) WITH CLUSTERING ORDER BY (logical_timestamp ASC, message_id ASC)
   AND default_time_to_live = 0;
