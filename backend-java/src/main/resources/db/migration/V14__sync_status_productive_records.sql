-- Phase 2: sync tracking fields for productive records
-- central_record_id: ID assigned by central after sync (used for ACK correlation and delta updates)
-- sync_status: PENDING | SYNCED | FAILED | DELETED_LOGICAL

ALTER TABLE mortality_records
    ADD COLUMN IF NOT EXISTS central_record_id BIGINT,
    ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20) DEFAULT 'PENDING';

ALTER TABLE weight_records
    ADD COLUMN IF NOT EXISTS central_record_id BIGINT,
    ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20) DEFAULT 'PENDING';

ALTER TABLE consumption_records
    ADD COLUMN IF NOT EXISTS central_record_id BIGINT,
    ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20) DEFAULT 'PENDING';
