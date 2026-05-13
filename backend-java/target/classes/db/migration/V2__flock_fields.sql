ALTER TABLE flocks
    ADD COLUMN IF NOT EXISTS total_birds INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS male_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS female_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flock_date DATE NOT NULL DEFAULT CURRENT_DATE,
    ADD COLUMN IF NOT EXISTS bird_lot VARCHAR(80) NOT NULL DEFAULT 'PENDIENTE';

ALTER TABLE flocks
    ALTER COLUMN total_birds DROP DEFAULT,
    ALTER COLUMN male_count DROP DEFAULT,
    ALTER COLUMN female_count DROP DEFAULT,
    ALTER COLUMN flock_date DROP DEFAULT,
    ALTER COLUMN bird_lot DROP DEFAULT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_flocks_total_equals_sex'
    ) THEN
        ALTER TABLE flocks
            ADD CONSTRAINT chk_flocks_total_equals_sex
            CHECK (total_birds = male_count + female_count);
    END IF;
END $$;