ALTER TABLE extractor_programming_history
    ADD COLUMN IF NOT EXISTS actuator_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS actuator_type VARCHAR(20);

ALTER TABLE criadora_programming_history
    ADD COLUMN IF NOT EXISTS actuator_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS actuator_type VARCHAR(20);

ALTER TABLE bomba_programming_history
    ADD COLUMN IF NOT EXISTS actuator_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS actuator_type VARCHAR(20);

CREATE TABLE IF NOT EXISTS actuator_control_states (
    id BIGSERIAL PRIMARY KEY,
    actuator_type VARCHAR(20) NOT NULL,
    actuator_id BIGINT NOT NULL,
    actuator_name VARCHAR(100) NOT NULL,
    current_state BOOLEAN NOT NULL DEFAULT FALSE,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_actuator_control_state UNIQUE (actuator_type, actuator_id)
);

CREATE TABLE IF NOT EXISTS actuator_control_commands (
    id BIGSERIAL PRIMARY KEY,
    actuator_type VARCHAR(20) NOT NULL,
    actuator_id BIGINT NOT NULL,
    actuator_name VARCHAR(100) NOT NULL,
    command VARCHAR(3) NOT NULL,
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    nh3_ppm DOUBLE PRECISION,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    dispatched_at TIMESTAMPTZ,
    work_duration_seconds INTEGER
);

CREATE INDEX IF NOT EXISTS idx_actuator_control_commands_pending
    ON actuator_control_commands (dispatched_at, created_at);

CREATE INDEX IF NOT EXISTS idx_actuator_control_states_type
    ON actuator_control_states (actuator_type, actuator_id);
