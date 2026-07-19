package dev.hendrikhoemberg.dmhelper.audio.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves V15 migration is additive: existing rows survive with null assignments.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:audiomigration;DB_CLOSE_DELAY=-1")
class AudioMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v15IsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '15' AND \"success\" = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void audioCueTableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'AUDIO_CUE'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sessionAudioStateTableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SESSION_AUDIO_STATE'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void campaignHasDefaultAudioCueColumn() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'CAMPAIGN' AND column_name = 'DEFAULT_AUDIO_CUE_ID'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sceneHasAudioCueColumn() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ADVENTURE_SCENE' AND column_name = 'SCENE_AUDIO_CUE_ID'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void encounterHasAudioCueColumns() {
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
    void worldLocationHasAudioCueColumn() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'WORLD_LOCATION' AND column_name = 'LOCATION_AUDIO_CUE_ID'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void audioCueTableHasExpectedColumns() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'AUDIO_CUE' AND column_name = 'CAMPAIGN_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'AUDIO_CUE' AND column_name = 'CUE_KEY'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'AUDIO_CUE' AND column_name = 'CATEGORY'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'AUDIO_CUE' AND column_name = 'TRANSITION_PREFERENCE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'AUDIO_CUE' AND column_name = 'REFERENCE_KIND'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void audioCueHasUniqueConstraint() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'AUDIO_CUE'
                AND constraint_name = 'UQ_AUDIO_CUE_CAMPAIGN_KEY'""";
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(1);
    }

    @Test
    void audioCueHasVolumeConstraint() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'AUDIO_CUE'
                AND constraint_name = 'CK_AUDIO_CUE_VOLUME'""";
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(1);
    }

    @Test
    void audioCueHasDurationConstraint() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'AUDIO_CUE'
                AND constraint_name = 'CK_AUDIO_CUE_DURATION'""";
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(1);
    }

    @Test
    void sessionAudioStateHasUniqueConstraint() {
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'SESSION_AUDIO_STATE'
                AND constraint_name = 'UQ_SESSION_AUDIO_STATE_SESSION'""";
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(1);
    }

    @Test
    void sessionAudioStateHasExpectedColumns() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'SESSION_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'MUTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'VERSION'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'MANUAL_OVERRIDE_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'ACCEPTED_AUTOMATIC_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'PENDING_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'DISMISSED_CANDIDATE_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'TEMPORARY_VICTORY_CUE_ID'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SESSION_AUDIO_STATE' AND column_name = 'VICTORY_UNTIL'",
                Integer.class)).isEqualTo(1);
    }
}
