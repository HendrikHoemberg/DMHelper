-- V9__encounter_map_depth.sql

ALTER TABLE encounter ADD COLUMN IF NOT EXISTS prep_json CLOB;
ALTER TABLE encounter ADD COLUMN IF NOT EXISTS rewards_json CLOB;

CREATE TABLE encounter_wave (
    id UUID NOT NULL PRIMARY KEY,
    encounter_id UUID NOT NULL,
    wave_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    trigger_kind VARCHAR(24) NOT NULL,
    trigger_value VARCHAR(255),
    notes CLOB,
    CONSTRAINT fk_wave_encounter FOREIGN KEY (encounter_id)
        REFERENCES encounter (id) ON DELETE CASCADE,
    CONSTRAINT uq_wave_key_per_encounter UNIQUE (encounter_id, wave_key)
);

CREATE INDEX idx_wave_encounter ON encounter_wave (encounter_id);

ALTER TABLE combatant ADD COLUMN IF NOT EXISTS wave_id UUID;
ALTER TABLE combatant ADD COLUMN IF NOT EXISTS start_x INT;
ALTER TABLE combatant ADD COLUMN IF NOT EXISTS start_y INT;
ALTER TABLE combatant ADD COLUMN IF NOT EXISTS placement_region_key VARCHAR(100);

ALTER TABLE combatant ADD CONSTRAINT fk_combatant_wave
    FOREIGN KEY (wave_id) REFERENCES encounter_wave (id) ON DELETE SET NULL;

-- Backfill a main wave for every existing encounter
INSERT INTO encounter_wave (id, encounter_id, wave_key, name, sort_order, status, trigger_kind)
SELECT RANDOM_UUID(), e.id, 'main', 'Main', 0, 'ACTIVE', 'MANUAL'
FROM encounter e
WHERE NOT EXISTS (
    SELECT 1 FROM encounter_wave w WHERE w.encounter_id = e.id AND w.wave_key = 'main'
);

-- Attach orphan combatants to main wave
UPDATE combatant c
SET wave_id = (
    SELECT w.id FROM encounter_wave w
    WHERE w.encounter_id = c.encounter_id AND w.wave_key = 'main'
)
WHERE c.wave_id IS NULL;
