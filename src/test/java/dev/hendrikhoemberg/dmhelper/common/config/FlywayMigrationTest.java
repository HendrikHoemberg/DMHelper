package dev.hendrikhoemberg.dmhelper.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the failure mode that made the first Flyway attempt look broken: on Spring Boot 4,
 * flyway-core alone is not auto-configured, so migrations silently never run.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:flywaytest;DB_CLOSE_DELAY=-1")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliesTheBaselineMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void baselineMigrationCreatesTheCoreSchema() {
        Integer campaignTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'CAMPAIGN'",
                Integer.class);

        assertThat(campaignTables).isEqualTo(1);
    }

    @Test
    void v3CreatesCampaignPackageKeyTable() {
        Integer appliedV3 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '3' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV3).isEqualTo(1);
    }

    @Test
    void packageKeyTableHasExpectedConstraints() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'CAMPAIGN_PACKAGE_KEY'
                AND constraint_name IN ('UQ_PACKAGE_KEY_ENTITY', 'UQ_PACKAGE_KEY_VALUE', 'FK_PACKAGE_KEY_CAMPAIGN')""";
        Integer constraints = jdbc.queryForObject(sql, Integer.class);
        assertThat(constraints).isEqualTo(3);
    }

    @Test
    void v4CreatesCampaignSessionTable() {
        Integer appliedV4 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '4' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV4).isEqualTo(1);
    }

    @Test
    void v4CreatesExpectedTables() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'CAMPAIGN_SESSION'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'CAMPAIGN_SESSION_ATTENDEE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SESSION_SCENE_VISIT'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v5IsApplied() {
        Integer appliedV5 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '5' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV5).isEqualTo(1);
    }

    @Test
    void v8AddsCharacterSheetColumns() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '8' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'PARTY_MEMBER' AND column_name = 'TEMP_HP'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'PARTY_MEMBER' AND column_name = 'INSPIRATION'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v9IsApplied() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '9' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v9CreatesEncounterWaveAndPrepColumns() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ENCOUNTER_WAVE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'PREP_JSON'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'REWARDS_JSON'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'WAVE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'START_X'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'START_Y'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'PLACEMENT_REGION_KEY'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v9HasExpectedConstraints() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'ENCOUNTER_WAVE'
                AND constraint_name IN ('UQ_WAVE_KEY_PER_ENCOUNTER', 'FK_WAVE_ENCOUNTER')""";
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(2);
        String combatantConstraintSql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'COMBATANT'
                AND constraint_name = 'FK_COMBATANT_WAVE'""";
        assertThat(jdbc.queryForObject(combatantConstraintSql, Integer.class)).isEqualTo(1);
    }

    @Test
    void v7AddsProvenanceAndOwnershipToSpell() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SPELL' AND column_name = 'SOURCE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SPELL' AND column_name = 'CAMPAIGN_ID_FK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SPELL' AND column_name = 'PROV_SOURCE_TITLE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v6MakesLinkTargetIdNullable() {
        Integer sceneLinkNullable = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'SCENE_LINK' AND column_name = 'TARGET_ID' AND is_nullable = 'YES'
                """,
                Integer.class);
        Integer questLinkNullable = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'QUEST_LINK' AND column_name = 'TARGET_ID' AND is_nullable = 'YES'
                """,
                Integer.class);
        assertThat(sceneLinkNullable).isEqualTo(1);
        assertThat(questLinkNullable).isEqualTo(1);
    }

    @Test
    void v5CreatesNewTables() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SCENE_SECTION'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SCENE_CHECK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SCENE_PARTICIPANT'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SCENE_TRANSITION'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SCENE_LINK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'QUEST'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'QUEST_OBJECTIVE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'QUEST_OBJECTIVE_DEPENDENCY'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'QUEST_LINK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SOURCE_ANNOTATION'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SESSION_OBJECTIVE_CHANGE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v5AddsNewColumnsToAdventureScene() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ADVENTURE_SCENE' AND column_name = 'SUMMARY'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ADVENTURE_SCENE' AND column_name = 'SOURCE_LOCATOR'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ADVENTURE_SCENE' AND column_name = 'TAGS'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ADVENTURE_SCENE' AND column_name = 'MAP_REGION_KEY'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13IsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '13' AND \"success\" = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void v13CreatesRollableTable() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ROLLABLE_TABLE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13CreatesRollableTableEntry() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ROLLABLE_TABLE_ENTRY'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13CreatesRollableTableEntryReference() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ROLLABLE_TABLE_ENTRY_REFERENCE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13CreatesWorldLocationTableLink() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'WORLD_LOCATION_TABLE_LINK'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13CreatesTableRollLog() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TABLE_ROLL_LOG'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13RollableTableHasExpectedColumns() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ROLLABLE_TABLE' AND column_name = 'SOURCE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ROLLABLE_TABLE' AND column_name = 'CAMPAIGN_ID_FK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ROLLABLE_TABLE' AND column_name = 'ADDRESS_MODE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v13RollableTableHasEntryKeyUniqueConstraint() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'ROLLABLE_TABLE_ENTRY'
                AND constraint_name = 'UQ_ROLLABLE_TABLE_ENTRY_KEY'""";
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(1);
    }

    @Test
    void v15IsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '15' AND \"success\" = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void v15CreatesAudioCueTable() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'AUDIO_CUE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SESSION_AUDIO_STATE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v15AddsAudioCueColumnsToCampaign() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'CAMPAIGN' AND column_name = 'DEFAULT_AUDIO_CUE_ID'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v15AddsAudioCueColumnsToAdventureScene() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ADVENTURE_SCENE' AND column_name = 'SCENE_AUDIO_CUE_ID'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v15AddsAudioCueColumnsToEncounter() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'COMBAT_AUDIO_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'VICTORY_AUDIO_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'VICTORY_CUE_DURATION_SECONDS'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v15AddsAudioCueColumnsToWorldLocation() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'WORLD_LOCATION' AND column_name = 'LOCATION_AUDIO_CUE_ID'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v18AddsSafetyClassificationAndSessionAudit() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '18' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name='SESSION_AUDIT_ENTRY'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v19EnforcesHandoutAndAuditSafetyInvariants() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '19' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_name IN (
                    'CK_SESSION_AUDIT_TYPE')
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void v28DropsHandoutSafetyClassification() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '28' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name='HANDOUT' AND column_name='SAFETY_CLASSIFICATION'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name='HANDOUT' AND column_name='SOURCE_HANDOUT_ID'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name='HANDOUT' AND column_name='DERIVATIVE_RECIPE'",
                Integer.class)).isZero();
    }

    @Test
    void v20AddsExplicitInitiativeSetupState() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" "
                        + "WHERE \"version\" = '20' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'ENCOUNTER'
                  AND column_name = 'COMBAT_PHASE'
                  AND is_nullable = 'NO'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'COMBATANT'
                  AND column_name = 'INITIATIVE'
                  AND is_nullable = 'YES'
            """, Integer.class)).isEqualTo(1);
    }

    @Test
    void combatPhaseIsRestrictedToKnownDomainValues() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'ENCOUNTER'
                  AND constraint_name = 'CK_ENCOUNTER_COMBAT_PHASE'
                  AND constraint_type = 'CHECK'
                """, Integer.class)).isEqualTo(1);

        UUID campaignId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign (milestone_leveling, created_at, id, name)
                VALUES (FALSE, CURRENT_TIMESTAMP, ?, 'Constraint Test')
                """, campaignId);
        try {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO encounter (
                        active_turn_index, lair_action_triggered, round, log_sequence,
                        campaign_id, id, name, status, combat_phase
                    ) VALUES (-1, FALSE, 0, 0, ?, ?, 'Invalid Phase', 'PLANNED', 'INVALID')
                    """, campaignId, encounterId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM campaign WHERE id = ?", campaignId);
        }
    }

    @Test
    void v12CreatesWorldGraphTables() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '12' AND \"success\" = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        for (String table : List.of(
                "WORLD_NPC", "WORLD_LOCATION", "FACTION", "WORLD_RELATIONSHIP", "FACTION_CLOCK",
                "WORLD_LOCATION_ENCOUNTER", "WORLD_LOCATION_TRAVEL")) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(count).as(table).isEqualTo(1);
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SCENE_PARTICIPANT' AND column_name = 'WORLD_NPC_ID'",
                Integer.class)).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_name = 'SCENE_PARTICIPANT' AND constraint_name = 'FK_SCENE_PARTICIPANT_WORLD_NPC'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v22CreatesApplicationLocalCockpitPresetStorage() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '22' AND \"success\" = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'COCKPIT_LAYOUT_PRESET'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'COCKPIT_LAYOUT_PRESET' AND column_name = 'CAMPAIGN_ID'
                """, Integer.class)).isZero();
    }
}
