-- V10__mortality_add_observations.sql
-- Add observations column to mortality_records table

ALTER TABLE mortality_records ADD COLUMN IF NOT EXISTS observations VARCHAR(1000);
