package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:threatmigration;DB_CLOSE_DELAY=-1")
class ThreatMigrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private SceneSectionRepository sceneSectionRepository;
    @Autowired private CombatantRepository combatantRepository;

    @Test
    void v14IsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '14' AND \"success\" = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void flywayReportsTwentyTwoMigrations() {
        // Count only versioned migrations (exclude SCHEMA/BASELINE marker rows if present).
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL",
                Integer.class);
        assertThat(count).isEqualTo(22);
        String current = jdbc.queryForObject(
                "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = TRUE ORDER BY \"installed_rank\" DESC FETCH FIRST 1 ROWS ONLY",
                String.class);
        assertThat(current).isEqualTo("22");
    }

    @Test
    void v14CreatesThreatTables() {
        for (String table : List.of(
                "TRAP", "HAZARD", "TRAP_DISARM_METHOD", "TRAP_DAMAGE_TYPE",
                "HAZARD_DAMAGE_TYPE", "THREAT_REFERENCE", "MAP_THREAT_PIN")) {
            Integer present = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(present).as(table).isEqualTo(1);
        }
    }

    @Test
    void v14AddsNullableThreatColumnsToSceneSectionAndCombatant() {
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'SCENE_SECTION' AND column_name = 'THREAT_KIND' AND is_nullable = 'YES'
                """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'SCENE_SECTION' AND column_name = 'THREAT_ID' AND is_nullable = 'YES'
                """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'COMBATANT' AND column_name = 'THREAT_KIND' AND is_nullable = 'YES'
                """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'COMBATANT' AND column_name = 'THREAT_ID' AND is_nullable = 'YES'
                """,
                Integer.class)).isEqualTo(1);
    }

    @Test
    void threatReferenceRequiresExactlyOneOwner() {
        Integer checks = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'THREAT_REFERENCE'
                AND constraint_name = 'CK_THREAT_REFERENCE_OWNER'
                """,
                Integer.class);
        assertThat(checks).isEqualTo(1);
    }

    @Test
    @Transactional
    void existingProseSectionsAndCombatantsMigrateWithNullThreatFields() {
        Campaign campaign = new Campaign();
        campaign.setName("Migration Prose Campaign");
        em.persist(campaign);

        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Migration Adventure");
        adventure.setSortOrder(0);
        em.persist(adventure);

        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Ch 1");
        chapter.setSortOrder(0);
        em.persist(chapter);

        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Prose Scene");
        scene.setSortOrder(0);
        em.persist(scene);

        SceneSection section = new SceneSection();
        section.setScene(scene);
        section.setKind(SceneSectionKind.TRAP);
        section.setLabel("Prose trap only");
        section.setBody("There is a pit covered with leaves.");
        section.setSortOrder(0);
        em.persist(section);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Prose Encounter");
        em.persist(encounter);

        Combatant combatant = new Combatant();
        combatant.setEncounter(encounter);
        combatant.setName("Prose Threat");
        combatant.setInitiative(10);
        combatant.setSortOrder(0);
        combatant.setMaxHp(0);
        combatant.setCurrentHp(0);
        combatant.setKind("TRAP");
        em.persist(combatant);

        em.flush();
        UUID sectionId = section.getId();
        UUID combatantId = combatant.getId();
        em.clear();

        SceneSection loadedSection = sceneSectionRepository.findById(sectionId).orElseThrow();
        assertThat(loadedSection.getKind()).isEqualTo(SceneSectionKind.TRAP);
        assertThat(loadedSection.getBody()).isEqualTo("There is a pit covered with leaves.");
        assertThat(loadedSection.getThreatKind()).isNull();
        assertThat(loadedSection.getThreatId()).isNull();

        Combatant loadedCombatant = combatantRepository.findById(combatantId).orElseThrow();
        assertThat(loadedCombatant.getKind()).isEqualTo("TRAP");
        assertThat(loadedCombatant.getName()).isEqualTo("Prose Threat");
        assertThat(loadedCombatant.getThreatKind()).isNull();
        assertThat(loadedCombatant.getThreatId()).isNull();
    }
}
