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
            stmt.execute("INSERT INTO adventure (id, campaign_id, name, sort_order, created_at) "
                    + "SELECT RANDOM_UUID(), id, 'Castle Ravenloft', 0, CURRENT_TIMESTAMP FROM campaign WHERE name = 'Curse of Strahd'");
            stmt.execute("INSERT INTO adventure_chapter (id, adventure_id, title, sort_order) "
                    + "SELECT RANDOM_UUID(), a.id, 'Chapter 1', 0 FROM adventure a WHERE a.name = 'Castle Ravenloft'");
            stmt.execute("INSERT INTO adventure_scene (id, chapter_id, title, sort_order, status) "
                    + "SELECT RANDOM_UUID(), ac.id, 'The Gate', 0, 'UNVISITED' FROM adventure_chapter ac WHERE ac.title = 'Chapter 1'");
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

    @Test
    void appliesV5AfterBaseline() {
        Integer appliedV5 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '5' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV5).isEqualTo(1);
    }

    @Test
    void appliesV6AfterBaseline() {
        Integer appliedV6 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '6' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV6).isEqualTo(1);
    }

    @Test
    void appliesV7AfterBaseline() {
        Integer appliedV7 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '7' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV7).isEqualTo(1);
    }

    @Test
    void v7ProvenanceAndOwnershipColumnsExistOnSpell() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SPELL' AND column_name = 'SOURCE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SPELL' AND column_name = 'CAMPAIGN_ID_FK'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v5TablesExistAfterUpgrade() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'QUEST'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SCENE_SECTION'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void appliesV13AfterBaseline() {
        Integer appliedV13 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '13' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV13).isEqualTo(1);
    }

    @Test
    void v13TablesExistAfterUpgrade() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ROLLABLE_TABLE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ROLLABLE_TABLE_ENTRY'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TABLE_ROLL_LOG'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void appliesV15AfterBaseline() {
        Integer appliedV15 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '15' AND \"success\" = TRUE",
                Integer.class);
        assertThat(appliedV15).isEqualTo(1);
    }

    @Test
    void v15TablesExistAfterUpgrade() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'AUDIO_CUE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SESSION_AUDIO_STATE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void legacySceneHasNullStructuredFieldsAfterV5() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM adventure_scene WHERE summary IS NULL AND source_locator IS NULL AND tags IS NULL AND map_region_key IS NULL",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
