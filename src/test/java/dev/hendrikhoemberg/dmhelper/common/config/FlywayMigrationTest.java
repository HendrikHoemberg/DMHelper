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
}
