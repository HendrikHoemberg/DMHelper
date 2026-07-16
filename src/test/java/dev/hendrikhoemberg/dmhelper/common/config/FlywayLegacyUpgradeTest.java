package dev.hendrikhoemberg.dmhelper.common.config;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The upgrade path for every existing install: a database whose schema was built by the old
 * ddl-auto=update, with no Flyway history. Flyway must adopt it in place — stamping it as
 * baselined rather than re-running V1 against populated tables — and its data must survive.
 */
@SpringBootTest
class FlywayLegacyUpgradeTest {

    private static Path dbDir;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CampaignRepository campaigns;

    @DynamicPropertySource
    static void legacyDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:file:" + dbDir.resolve("legacy") + ";DB_CLOSE_DELAY=-1");
    }

    /** Builds a pre-Flyway database: the V1 schema plus a campaign, and no schema history table. */
    @BeforeAll
    static void createLegacyDatabase() throws IOException, SQLException {
        dbDir = Files.createTempDirectory("dmhelper-legacy");
        String ddl = Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql"));

        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:file:" + dbDir.resolve("legacy"), "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            stmt.execute("INSERT INTO campaign (id, name, created_at, milestone_leveling) VALUES "
                    + "(RANDOM_UUID(), 'Curse of Strahd', CURRENT_TIMESTAMP, FALSE)");
        }
    }

    @Test
    void adoptsTheLegacySchemaAsBaselinedRatherThanReRunningIt() {
        String type = jdbc.queryForObject(
                "SELECT \"type\" FROM \"flyway_schema_history\" WHERE \"version\" = '1'", String.class);

        assertThat(type).isEqualTo("BASELINE");
    }

    @Test
    void preservesDataThatExistedBeforeTheUpgrade() {
        assertThat(campaigns.findAll())
                .extracting(Campaign::getName)
                .contains("Curse of Strahd");
    }

    @Test
    void appliesV3AfterBaseline() {
        Integer appliedV3 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '3' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV3).isEqualTo(1);
    }

    @Test
    void v3TableExistsAfterUpgrade() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'CAMPAIGN_PACKAGE_KEY'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void appliesV4AfterBaseline() {
        Integer appliedV4 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '4' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV4).isEqualTo(1);
    }

    @Test
    void v4TableExistsAfterUpgrade() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'CAMPAIGN_SESSION'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
