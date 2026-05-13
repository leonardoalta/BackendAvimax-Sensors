CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS flocks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    total_birds INTEGER NOT NULL,
    male_count INTEGER NOT NULL,
    female_count INTEGER NOT NULL,
    flock_date DATE NOT NULL,
    bird_lot VARCHAR(80) NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_flocks_single_active
    ON flocks ((status))
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS sensor_readings (
    id BIGSERIAL PRIMARY KEY,
    flock_id BIGINT NOT NULL REFERENCES flocks(id),
    recorded_at TIMESTAMPTZ NOT NULL,
    gateway_id VARCHAR(80),
    source_topic VARCHAR(255),
    temperature_c DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION,
    nh3_ppm DOUBLE PRECISION
);

SELECT create_hypertable('sensor_readings', 'recorded_at', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_sensor_readings_flock_time
    ON sensor_readings (flock_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_sensor_readings_time
    ON sensor_readings (recorded_at DESC);
