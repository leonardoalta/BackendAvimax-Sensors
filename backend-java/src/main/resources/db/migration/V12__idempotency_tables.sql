CREATE TABLE IF NOT EXISTS processed_mqtt_commands (
    command_id          BIGINT PRIMARY KEY,
    galpon_id           BIGINT,
    actuator_type       VARCHAR(20),
    actuator_id         BIGINT,
    action              VARCHAR(10),
    status              VARCHAR(20),
    message             TEXT,
    first_processed_at  TIMESTAMPTZ NOT NULL,
    last_seen_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_programming_configs (
    config_id           BIGINT PRIMARY KEY,
    galpon_id           BIGINT,
    actuator_type       VARCHAR(20),
    actuator_id         BIGINT,
    status              VARCHAR(20),
    message             TEXT,
    first_processed_at  TIMESTAMPTZ NOT NULL,
    last_seen_at        TIMESTAMPTZ NOT NULL
);
