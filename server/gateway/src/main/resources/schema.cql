CREATE TABLE IF NOT EXISTS messages_by_conversation
(
    conversation_id     TEXT,
    message_id          UUID,
    sender_username     TEXT,
    recipient_username  TEXT,
    content             TEXT,
    logical_timestamp   BIGINT,
    status              TEXT,
    PRIMARY KEY ((conversation_id), logical_timestamp, message_id)
) WITH CLUSTERING ORDER BY (logical_timestamp ASC, message_id ASC)
   AND default_time_to_live = 0;
