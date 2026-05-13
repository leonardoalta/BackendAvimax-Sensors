-- Ensure only one mortality record per flock per day
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_mortality_flock_date'
    ) THEN
        ALTER TABLE mortality_records
        ADD CONSTRAINT uq_mortality_flock_date UNIQUE (flock_id, record_date);
    END IF;
END $$;
