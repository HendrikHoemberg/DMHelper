-- H2's ENUM type creates a CHECK constraint that blocks new Java enum values.
-- Convert to VARCHAR; Java-side validation handles the allowed values.
ALTER TABLE combat_log_entry ALTER COLUMN type VARCHAR(32) NOT NULL;
