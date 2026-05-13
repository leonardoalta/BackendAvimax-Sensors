-- V8__weight.sql
-- Creates the weight_records table for tracking bird weight measurements

CREATE TABLE weight_records (
    id                   BIGSERIAL PRIMARY KEY,
    flock_id             BIGINT NOT NULL,
    sampled_birds_count  INTEGER NOT NULL,
    average_weight       DOUBLE PRECISION NOT NULL,
    age                  INTEGER NOT NULL,
    record_date          DATE NOT NULL,
    gender               VARCHAR(20) NOT NULL,
    location             VARCHAR(30) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_weight_record_flock FOREIGN KEY (flock_id) REFERENCES flocks(id) ON DELETE CASCADE
);

-- Create indexes for common queries
CREATE INDEX idx_weight_records_flock_id ON weight_records(flock_id);
CREATE INDEX idx_weight_records_gender ON weight_records(gender);
CREATE INDEX idx_weight_records_record_date ON weight_records(record_date DESC);
CREATE INDEX idx_weight_records_location ON weight_records(location);
CREATE INDEX idx_weight_records_flock_gender ON weight_records(flock_id, gender, record_date DESC);
