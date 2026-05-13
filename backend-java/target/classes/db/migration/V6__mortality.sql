CREATE TABLE IF NOT EXISTS mortality_records (
    id BIGSERIAL PRIMARY KEY,
    flock_id BIGINT NOT NULL REFERENCES flocks(id) ON DELETE CASCADE,
    record_date DATE NOT NULL,
    age_days INTEGER NOT NULL,
    male_count INTEGER NOT NULL DEFAULT 0,
    female_count INTEGER NOT NULL DEFAULT 0,
    total_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mortality_record_date ON mortality_records(record_date DESC);
