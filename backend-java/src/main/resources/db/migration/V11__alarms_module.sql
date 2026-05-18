CREATE TABLE IF NOT EXISTS alarm_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    variable VARCHAR(20) NOT NULL,
    condition_type VARCHAR(20) NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    unit VARCHAR(10) NOT NULL,
    minimum_duration_seconds INTEGER NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alarm_rules_active ON alarm_rules (active);

CREATE TABLE IF NOT EXISTS alarms (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alarm_rules(id) ON DELETE RESTRICT,
    rule_name VARCHAR(120) NOT NULL,
    variable VARCHAR(20) NOT NULL,
    detected_value DOUBLE PRECISION NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    unit VARCHAR(10) NOT NULL,
    condition_type VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_alarms_rule_status ON alarms (rule_id, status);
CREATE INDEX IF NOT EXISTS idx_alarms_status ON alarms (status);
CREATE INDEX IF NOT EXISTS idx_alarms_activated_at_desc ON alarms (activated_at DESC);

CREATE TABLE IF NOT EXISTS alarm_events (
    id BIGSERIAL PRIMARY KEY,
    alarm_id BIGINT NOT NULL REFERENCES alarms(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    description VARCHAR(500) NOT NULL,
    event_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alarm_events_alarm_event_at ON alarm_events (alarm_id, event_at DESC);

CREATE TABLE IF NOT EXISTS alarm_rule_states (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL UNIQUE REFERENCES alarm_rules(id) ON DELETE CASCADE,
    condition_met BOOLEAN NOT NULL DEFAULT FALSE,
    met_since TIMESTAMPTZ,
    last_value DOUBLE PRECISION,
    last_evaluated_at TIMESTAMPTZ
);
