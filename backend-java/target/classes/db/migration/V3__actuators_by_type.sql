CREATE TABLE IF NOT EXISTS extractors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS extractor_programming (
    id BIGSERIAL PRIMARY KEY,
    extractor_id BIGINT NOT NULL UNIQUE REFERENCES extractors(id) ON DELETE CASCADE,
    temperature_on DOUBLE PRECISION NOT NULL,
    temperature_off DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS criadoras (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS criadora_programming (
    id BIGSERIAL PRIMARY KEY,
    criadora_id BIGINT NOT NULL UNIQUE REFERENCES criadoras(id) ON DELETE CASCADE,
    temperature_on DOUBLE PRECISION NOT NULL,
    temperature_off DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS bombas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS bomba_programming (
    id BIGSERIAL PRIMARY KEY,
    bomba_id BIGINT NOT NULL UNIQUE REFERENCES bombas(id) ON DELETE CASCADE,
    temperature_on DOUBLE PRECISION NOT NULL,
    temperature_off DOUBLE PRECISION NOT NULL,
    work_duration_seconds INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_bomba_programming_work_duration'
    ) THEN
        ALTER TABLE bomba_programming
            ADD CONSTRAINT chk_bomba_programming_work_duration
            CHECK (work_duration_seconds > 0);
    END IF;
END $$;
