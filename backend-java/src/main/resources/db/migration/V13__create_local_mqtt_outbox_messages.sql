CREATE TABLE IF NOT EXISTS local_mqtt_outbox_messages (
    id              BIGSERIAL PRIMARY KEY,
    topic           VARCHAR(500) NOT NULL,
    payload         TEXT NOT NULL,
    qos             INTEGER NOT NULL DEFAULT 1,
    retained        BOOLEAN NOT NULL DEFAULT FALSE,
    message_type    VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON local_mqtt_outbox_messages(status);
