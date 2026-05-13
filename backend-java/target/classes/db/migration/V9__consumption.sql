-- V9__consumption.sql
-- Creates the consumption_records table for tracking feed consumption

CREATE TABLE consumption_records (
    id                      BIGSERIAL PRIMARY KEY,
    flock_id                BIGINT NOT NULL,
    age                     INTEGER NOT NULL,
    record_date             DATE NOT NULL,
    total_consumption_kg    DOUBLE PRECISION NOT NULL,
    birds_count_used        INTEGER NOT NULL,
    consumption_per_bird_kg DOUBLE PRECISION NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consumption_record_flock FOREIGN KEY (flock_id) REFERENCES flocks(id) ON DELETE CASCADE
);

CREATE INDEX idx_consumption_records_flock_id ON consumption_records(flock_id);
CREATE INDEX idx_consumption_records_record_date ON consumption_records(record_date DESC);
CREATE INDEX idx_consumption_records_flock_date ON consumption_records(flock_id, record_date DESC);
