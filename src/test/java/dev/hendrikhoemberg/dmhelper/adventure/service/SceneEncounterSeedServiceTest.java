package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class SceneEncounterSeedServiceTest {

    @Autowired private SceneEncounterSeedService seeder;
    @Autowired private EncounterService encounters;
    @Autowired private AdventureService adventures;
    @Autowired private PopulatedCampaignFixture fixture;

    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        seeded = fixture.seed();
    }

    @Test
    void seedsOneCombatantPerParticipantCopyWithTheStatblocksHp() {
        var result = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(result.encounterName()).contains("Der Schreibtisch");

        assertThat(result.combatantsAdded()).isEqualTo(2);

        var combatants = encounters.getCombatants(result.encounterId());
        assertThat(combatants).hasSize(2);
        assertThat(combatants).allSatisfy(c -> {
            assertThat(c.name()).contains("Sp\u00e4her der Redbrands");
            assertThat(c.kind()).isEqualTo("MONSTER");
            assertThat(c.maxHp()).isEqualTo(16);
        });
        assertThat(combatants).extracting("groupId").containsOnly(combatants.get(0).groupId());
        assertThat(combatants).filteredOn("groupLeader", true).hasSize(1);
    }

    @Test
    void reportsParticipantsItCouldNotResolveRatherThanDroppingThem() {
        var result = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        assertThat(result.skippedParticipants())
                .as("a participant that silently vanishes is a monster the DM forgets to run")
                .containsExactly("Namenloser Bote");
    }

    @Test
    void linksTheEncounterBackToTheScene() {
        var result = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        var scene = adventures.findSceneById(seeded.richSceneId());
        assertThat(scene.getEncounter()).isNotNull();
        assertThat(scene.getEncounter().getId()).isEqualTo(result.encounterId());
    }

    @Test
    void runningItTwiceDoesNotDuplicateTheEncounterOrTheCombatants() {
        var first = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());
        var second = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.encounterId()).isEqualTo(first.encounterId());
        assertThat(second.combatantsAdded()).isZero();
        assertThat(encounters.getCombatants(first.encounterId())).hasSize(2);
    }

    @Test
    void aSceneWithNoStatblockLinkedParticipantsCannotBeSeeded() {
        var empty = adventures.findSceneById(seeded.secondSceneId());
        assertThat(seeder.canSeed(empty))
                .as("the action must be absent for a scene the app knows nothing about")
                .isFalse();

        var rich = adventures.findSceneById(seeded.richSceneId());
        assertThat(seeder.canSeed(rich)).isTrue();
    }
}
