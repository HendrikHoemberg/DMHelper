package dev.hendrikhoemberg.dmhelper.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

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
    void v5IsTheLatestMigration() {
        Integer appliedV5 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '5' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV5).isEqualTo(1);
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
}
