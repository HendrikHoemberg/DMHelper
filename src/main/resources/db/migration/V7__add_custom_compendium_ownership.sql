-- Add ownership and provenance columns to all library entity tables.
--
-- Background: each entity below was previously SRD-only (source_key UNIQUE).
-- This migration adds source, campaign FK, and provenance columns so custom,
-- campaign-scoped content can be stored alongside SRD content.
--
-- H2 note: the UNIQUE constraints on source_key were created inline in V1 with
-- auto-generated names. In PostgreSQL the name follows the convention
-- table_column_key. We use DROP CONSTRAINT IF EXISTS with the PostgreSQL naming
-- pattern; in H2 tests this is a no-op and the constraint is effectively
-- handled by application-level uniqueness checks in seed services and
-- repository methods (existsBySourceAndSourceKey).
--
-- For stat_block the source column already exists (values SRD/CUSTOM) and is
-- left unchanged; only provenance columns are added.

-- =============================================================================
-- spell
-- =============================================================================
ALTER TABLE spell ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE spell ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE spell ADD CONSTRAINT IF NOT EXISTS fk_spell_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE spell ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE spell DROP CONSTRAINT IF EXISTS spell_source_key_key;
CREATE INDEX IF NOT EXISTS idx_spell_campaign ON spell(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_spell_campaign_name ON spell(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_spell_source ON spell(source);
CREATE INDEX IF NOT EXISTS idx_spell_source_key ON spell(source_key);

-- =============================================================================
-- srd_condition
-- =============================================================================
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE srd_condition ADD CONSTRAINT IF NOT EXISTS fk_condition_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE srd_condition ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE srd_condition DROP CONSTRAINT IF EXISTS srd_condition_source_key_key;
CREATE INDEX IF NOT EXISTS idx_condition_campaign ON srd_condition(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_condition_campaign_name ON srd_condition(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_condition_source ON srd_condition(source);
CREATE INDEX IF NOT EXISTS idx_condition_source_key ON srd_condition(source_key);

-- =============================================================================
-- rule_section
-- =============================================================================
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE rule_section ADD CONSTRAINT IF NOT EXISTS fk_rule_section_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE rule_section ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE rule_section DROP CONSTRAINT IF EXISTS rule_section_source_key_key;
CREATE INDEX IF NOT EXISTS idx_rule_section_campaign ON rule_section(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_rule_section_campaign_name ON rule_section(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_rule_section_source ON rule_section(source);
CREATE INDEX IF NOT EXISTS idx_rule_section_source_key ON rule_section(source_key);

-- =============================================================================
-- equipment_item
-- =============================================================================
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE equipment_item ADD CONSTRAINT IF NOT EXISTS fk_equipment_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE equipment_item ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE equipment_item DROP CONSTRAINT IF EXISTS equipment_item_source_key_key;
CREATE INDEX IF NOT EXISTS idx_equipment_campaign ON equipment_item(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_equipment_campaign_name ON equipment_item(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_equipment_source ON equipment_item(source);
CREATE INDEX IF NOT EXISTS idx_equipment_source_key ON equipment_item(source_key);

-- =============================================================================
-- magic_item
-- =============================================================================
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE magic_item ADD CONSTRAINT IF NOT EXISTS fk_magic_item_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE magic_item ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE magic_item DROP CONSTRAINT IF EXISTS magic_item_source_key_key;
CREATE INDEX IF NOT EXISTS idx_magic_item_campaign ON magic_item(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_magic_item_campaign_name ON magic_item(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_magic_item_source ON magic_item(source);
CREATE INDEX IF NOT EXISTS idx_magic_item_source_key ON magic_item(source_key);

-- =============================================================================
-- character_class
-- =============================================================================
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE character_class ADD CONSTRAINT IF NOT EXISTS fk_character_class_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE character_class ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE character_class DROP CONSTRAINT IF EXISTS character_class_source_key_key;
CREATE INDEX IF NOT EXISTS idx_class_campaign ON character_class(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_class_campaign_name ON character_class(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_class_source ON character_class(source);
CREATE INDEX IF NOT EXISTS idx_class_source_key ON character_class(source_key);

-- =============================================================================
-- species
-- =============================================================================
ALTER TABLE species ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE species ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE species ADD CONSTRAINT IF NOT EXISTS fk_species_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE species ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE species DROP CONSTRAINT IF EXISTS species_source_key_key;
CREATE INDEX IF NOT EXISTS idx_species_campaign ON species(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_species_campaign_name ON species(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_species_source ON species(source);
CREATE INDEX IF NOT EXISTS idx_species_source_key ON species(source_key);

-- =============================================================================
-- background
-- =============================================================================
ALTER TABLE background ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE background ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE background ADD CONSTRAINT IF NOT EXISTS fk_background_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE background ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE background DROP CONSTRAINT IF EXISTS background_source_key_key;
CREATE INDEX IF NOT EXISTS idx_background_campaign ON background(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_background_campaign_name ON background(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_background_source ON background(source);
CREATE INDEX IF NOT EXISTS idx_background_source_key ON background(source_key);

-- =============================================================================
-- feat
-- =============================================================================
ALTER TABLE feat ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'SRD';
ALTER TABLE feat ADD COLUMN IF NOT EXISTS campaign_id_fk UUID;
ALTER TABLE feat ADD CONSTRAINT IF NOT EXISTS fk_feat_campaign FOREIGN KEY (campaign_id_fk) REFERENCES campaign(id) ON DELETE CASCADE;
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE feat ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
ALTER TABLE feat DROP CONSTRAINT IF EXISTS feat_source_key_key;
CREATE INDEX IF NOT EXISTS idx_feat_campaign ON feat(campaign_id_fk);
CREATE INDEX IF NOT EXISTS idx_feat_campaign_name ON feat(campaign_id_fk, name);
CREATE INDEX IF NOT EXISTS idx_feat_source ON feat(source);
CREATE INDEX IF NOT EXISTS idx_feat_source_key ON feat(source_key);

-- =============================================================================
-- stat_block (only provenance columns; source column already exists)
-- =============================================================================
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_source_title VARCHAR(255);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_edition_version VARCHAR(100);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_source_locator VARCHAR(500);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_license VARCHAR(30);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_imported_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_converter_id VARCHAR(100);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_converter_version VARCHAR(50);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_source_hash VARCHAR(128);
ALTER TABLE stat_block ADD COLUMN IF NOT EXISTS prov_confidence VARCHAR(20);
