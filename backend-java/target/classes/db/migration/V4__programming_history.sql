CREATE TABLE IF NOT EXISTS extractor_programming_history (
    id BIGSERIAL PRIMARY KEY,
    extractor_id BIGINT NOT NULL REFERENCES extractors(id) ON DELETE CASCADE,
    temperature_on DOUBLE PRECISION NOT NULL,
    temperature_off DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS criadora_programming_history (
    id BIGSERIAL PRIMARY KEY,
    criadora_id BIGINT NOT NULL REFERENCES criadoras(id) ON DELETE CASCADE,
    temperature_on DOUBLE PRECISION NOT NULL,
    temperature_off DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS bomba_programming_history (
    id BIGSERIAL PRIMARY KEY,
    bomba_id BIGINT NOT NULL REFERENCES bombas(id) ON DELETE CASCADE,
    temperature_on DOUBLE PRECISION NOT NULL,
    temperature_off DOUBLE PRECISION NOT NULL,
    work_duration_seconds INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_extractor_programming_history_time ON extractor_programming_history(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_criadora_programming_history_time ON criadora_programming_history(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_bomba_programming_history_time ON bomba_programming_history(recorded_at DESC);
