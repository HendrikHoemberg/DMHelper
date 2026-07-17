-- Add live combat state columns to party_member for in-session tracking.
-- These are set-default fields that represent the "now" state of a character.

ALTER TABLE party_member ADD COLUMN IF NOT EXISTS temp_hp INTEGER NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN IF NOT EXISTS inspiration BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE party_member ADD COLUMN IF NOT EXISTS exhaustion INTEGER NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN IF NOT EXISTS death_save_successes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN IF NOT EXISTS death_save_failures INTEGER NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN IF NOT EXISTS concentrating_on VARCHAR(255);
ALTER TABLE party_member ADD COLUMN IF NOT EXISTS conditions_json CLOB;

-- Add attacks and features JSON columns to character_sheet for first-class attack/action tracking.
ALTER TABLE character_sheet ADD COLUMN IF NOT EXISTS attacks_json CLOB;
ALTER TABLE character_sheet ADD COLUMN IF NOT EXISTS features_json CLOB;

-- Add inventory state to item_assignment for character sheet inventory panel.
ALTER TABLE item_assignment ADD COLUMN IF NOT EXISTS inventory_state VARCHAR(16) NOT NULL DEFAULT 'CARRIED';
