package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveStatus;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Spec 2026-07-22 section 8.3 and section 11.3: the rehearsal needs a campaign that is
 * session-ready, Phandelver-shaped and entirely synthetic.
 */
@SpringBootTest
@Transactional
class ReleaseRehearsalFixtureTest {

    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private CampaignReadinessFacade readiness;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private GameMapRepository mapRepository;
    @Autowired private PartyMemberRepository partyMemberRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private QuestRepository questRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private EncounterWaveRepository encounterWaveRepository;
    @Autowired private CombatantRepository combatantRepository;

    @Test
    void theSeededCampaignIsSessionReady() throws IOException {
        var seeded = fixture.seed();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).sessionReady())
                .as("a rehearsal that starts blocked proves nothing about the rehearsal")
                .isTrue();
        assertThat(campaignRepository.findById(seeded.campaignId()).orElseThrow().getCurrentSceneId())
                .as("the synthetic rehearsal starts from its approach scene")
                .isNotNull();
    }

    @Test
    void theSecondShapeBranchesAndCarriesTwoMapScales() throws IOException {
        var seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);

        assertThat(readiness.reportForCampaign(seeded.campaignId()).sessionReady()).isTrue();

        var maps = mapRepository.findByCampaignIdOrderBySortOrderAsc(seeded.campaignId());
        assertThat(maps).as("two playable maps").hasSize(2);
        assertThat(maps.stream().map(m -> m.getCellSizePx()).distinct().count())
                .as("different grid scales, so calibration is genuinely exercised")
                .isEqualTo(2);
    }

    @Test
    void theSecondShapeHasATheatreOfMindEncounter() throws IOException {
        var seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);

        assertThat(fixture.mapFreeHostileSceneCount(seeded))
                .as("exactly one hostile scene that runs without a map")
                .isEqualTo(1);
    }

    @Test
    void theSecondShapeAddsTheThirdBranchObjectiveAndWave() throws IOException {
        var seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        var scenes = sceneRepository.findByCampaignIdOrderByChapterAndSort(seeded.campaignId());
        var approach = scenes.stream().filter(s -> s.getTitle().equals("Mossbound Approach")).findFirst().orElseThrow();
        var ambush = scenes.stream().filter(s -> s.getTitle().equals("Lantern Vault Ambush")).findFirst().orElseThrow();

        assertThat(scenes).as("two chapters with four scenes").hasSize(4);
        assertThat(scenes).extracting(s -> s.getChapter().getTitle())
                .containsExactly("The Drowned Stair", "The Drowned Stair", "The Drowned Stair", "The Lantern Vault");
        assertThat(scenes.stream().filter(s -> s.getChapter().getTitle().equals("The Drowned Stair")))
                .allMatch(s -> s.getChapter().getAdventure().getId().equals(seeded.adventureId()));
        assertThat(scenes.stream().filter(s -> s.getChapter().getTitle().equals("The Lantern Vault")))
                .allMatch(s -> s.getChapter().getAdventure().getId().equals(seeded.adventureId()));
        assertThat(approach.getTransitions()).as("three-way approach branch").hasSize(3);
        assertThat(approach.getTransitions())
                .extracting(t -> t.getLabel(), t -> t.getTargetScene().getId())
                .containsExactly(
                        tuple("Descend to the undercroft", seeded.hostileSceneId()),
                        tuple("Take the tideglass gallery", seeded.branchSceneId()),
                        tuple("Cross the sealed bridge", ambush.getId()));
        assertThat(ambush.getMapRequirement()).isEqualTo(SceneMapRequirement.NONE);
        assertThat(ambush.getEncounter()).as("the prepared ambush encounter is linked").isNotNull();
        assertThat(ambush.getEncounter().getId()).isNotNull();
        assertThat(ambush.getEncounter().getName()).isEqualTo("Encounter: Lantern Vault Ambush");
        assertThat(ambush.getEncounter().getMap()).isNotNull();
        assertThat(ambush.getEncounter().getMap().getCampaign().getId()).isEqualTo(seeded.campaignId());
        assertThat(encounterRepository.findByCampaignIdOrderByNameAsc(seeded.campaignId()))
                .extracting(e -> e.getName())
                .containsExactly("Encounter: Lantern Vault Ambush", "Undercroft Alarm");
        var waves = encounterWaveRepository.findByEncounterIdOrderBySortOrderAsc(ambush.getEncounter().getId());
        assertThat(waves).extracting(w -> w.getWaveKey(), w -> w.getStatus(), w -> w.getTriggerKind())
                .containsExactly(
                        tuple("main", WaveStatus.ACTIVE, WaveTriggerKind.MANUAL),
                        tuple("vault-reinforcements", WaveStatus.PENDING, WaveTriggerKind.MANUAL));
        assertThat(combatantRepository.findByEncounterIdOrderBySortOrderAsc(ambush.getEncounter().getId()))
                .filteredOn(c -> c.getWave() != null && c.getWave().getWaveKey().equals("vault-reinforcements"))
                .extracting(c -> c.getName(), c -> c.getStatBlock().getName())
                .containsExactly(tuple("Vault reinforcements", "Bog Sentinel"));
        assertThat(questRepository.findByIdAndCampaignId(seeded.questId(), seeded.campaignId()).orElseThrow()
                .getObjectives()).as("third quest objective").hasSize(3);
    }

    @Test
    void branchedPreparedEncounterTopologyIsSyntheticAndCampaignIsolated() throws IOException {
        var first = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        var second = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);

        var firstEncounter = encounterRepository.findByCampaignIdOrderByNameAsc(first.campaignId()).stream()
                .filter(e -> e.getName().equals("Encounter: Lantern Vault Ambush")).findFirst().orElseThrow();
        var secondEncounter = encounterRepository.findByCampaignIdOrderByNameAsc(second.campaignId()).stream()
                .filter(e -> e.getName().equals("Encounter: Lantern Vault Ambush")).findFirst().orElseThrow();
        var firstReserve = encounterWaveRepository.findByEncounterIdAndWaveKey(firstEncounter.getId(), "vault-reinforcements")
                .orElseThrow();
        var secondReserve = encounterWaveRepository.findByEncounterIdAndWaveKey(secondEncounter.getId(), "vault-reinforcements")
                .orElseThrow();
        var firstCombatant = combatantRepository.findByEncounterIdOrderBySortOrderAsc(firstEncounter.getId()).stream()
                .filter(c -> c.getWave() != null && c.getWave().getId().equals(firstReserve.getId())).findFirst().orElseThrow();
        var secondCombatant = combatantRepository.findByEncounterIdOrderBySortOrderAsc(secondEncounter.getId()).stream()
                .filter(c -> c.getWave() != null && c.getWave().getId().equals(secondReserve.getId())).findFirst().orElseThrow();

        assertThat(readiness.reportForCampaign(first.campaignId()).sessionReady()).isTrue();
        assertThat(readiness.reportForCampaign(second.campaignId()).sessionReady()).isTrue();
        assertThat(first.ambushSceneId()).isNotNull();
        assertThat(first.branchedEncounterId()).isEqualTo(firstEncounter.getId());
        assertThat(first.branchedMainWaveId()).isEqualTo(
                encounterWaveRepository.findByEncounterIdAndWaveKey(firstEncounter.getId(), "main").orElseThrow().getId());
        assertThat(first.branchedReserveWaveId()).isEqualTo(firstReserve.getId());
        assertThat(first.branchedReserveCombatantIds()).containsExactly(firstCombatant.getId());
        assertThat(firstEncounter.getMap()).isNotNull();
        assertThat(firstEncounter.getMap().getCampaign().getId()).isEqualTo(first.campaignId());
        assertThat(firstReserve.getStatus()).isEqualTo(WaveStatus.PENDING);
        assertThat(firstCombatant.getName()).isEqualTo("Vault reinforcements");
        assertThat(firstEncounter.getEncounterKey()).isNotBlank().isNotEqualTo(secondEncounter.getEncounterKey());
        assertThat(firstCombatant.getNotes()).isNotBlank().isNotEqualTo(secondCombatant.getNotes());
        assertThat(fixture.textualContentOf(first)).contains(
                "Encounter: Lantern Vault Ambush", "vault-reinforcements", "Synthetic reserve wave.",
                "Vault reinforcements", firstEncounter.getEncounterKey(), firstCombatant.getNotes())
                .doesNotContain(secondEncounter.getEncounterKey(), secondCombatant.getNotes());
        assertThat(fixture.textualContentOf(second)).contains(
                "Encounter: Lantern Vault Ambush", "vault-reinforcements", "Synthetic reserve wave.",
                "Vault reinforcements", secondEncounter.getEncounterKey(), secondCombatant.getNotes())
                .doesNotContain(firstEncounter.getEncounterKey(), firstCombatant.getNotes());
    }

    @Test
    void theHostileSceneCanSeedAnEncounterFromResolvedParticipants() throws IOException {
        var seeded = fixture.seed();
        var hostile = sceneRepository.findByIdAndCampaignId(seeded.campaignId(), seeded.hostileSceneId()).orElseThrow();
        var map = mapRepository.findById(seeded.playableMapId()).orElseThrow();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).byState(ReadinessState.BLOCKER))
                .isEmpty();
        assertThat(hostile.getParticipants())
                .extracting(p -> p.getDisplayName(), p -> p.getQuantity(), p -> p.getDisposition(),
                        p -> p.getStatBlock().getName(), p -> p.getStatBlock().getId())
                .containsExactly(
                        tuple("Sentinel at the sluice", 1, SceneParticipantDisposition.HOSTILE, "Bog Sentinel", fixture.statBlockIdsOf(seeded).get(0)),
                        tuple("Skirmisher by the steps", 1, SceneParticipantDisposition.HOSTILE, "Bog Skirmisher", fixture.statBlockIdsOf(seeded).get(1)),
                        tuple("Skirmisher in the reeds", 1, SceneParticipantDisposition.HOSTILE, "Bog Skirmisher", fixture.statBlockIdsOf(seeded).get(2)),
                        tuple("Warden of the bell", 1, SceneParticipantDisposition.HOSTILE, "Marsh Warden", fixture.statBlockIdsOf(seeded).get(3)));
        assertThat(hostile.getMapRequirement()).isEqualTo(SceneMapRequirement.REQUIRED);
        assertThat(hostile.getMap()).isNotNull();
        assertThat(hostile.getMap().getId()).isEqualTo(seeded.playableMapId());
        assertThat(map.getGridWidth()).isEqualTo(20);
        assertThat(map.getGridHeight()).isEqualTo(15);
        assertThat(map.getCellSizePx()).isEqualTo(64);
        assertThat(map.isShowGrid()).isTrue();
        assertThat(hostile.getEncounter()).isNotNull();
        var encounter = encounterRepository.findByCampaignIdOrderByNameAsc(seeded.campaignId()).stream()
                .findFirst().orElseThrow();
        assertThat(hostile.getEncounter().getId()).isEqualTo(encounter.getId());
        assertThat(encounter.getName()).isEqualTo("Undercroft Alarm");
        assertThat(encounter.getStatus()).isEqualTo(Encounter.Status.PLANNED);
        assertThat(encounter.getCombatPhase()).isEqualTo(Encounter.CombatPhase.SETUP);
        assertThat(encounter.getMap().getId()).isEqualTo(seeded.playableMapId());
        assertThat(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .extracting(c -> c.getName(), c -> c.getKind(), c -> c.getStatBlock().getName(), c -> c.getStatBlock().getId(),
                        c -> c.getMaxHp(), c -> c.getCurrentHp())
                .containsExactly(
                        tuple("Bog Sentinel", "MONSTER", "Bog Sentinel", fixture.statBlockIdsOf(seeded).get(0), 18, 18),
                        tuple("Bog Skirmisher", "MONSTER", "Bog Skirmisher", fixture.statBlockIdsOf(seeded).get(1), 11, 11),
                        tuple("Bog Skirmisher", "MONSTER", "Bog Skirmisher", fixture.statBlockIdsOf(seeded).get(2), 11, 11),
                        tuple("Marsh Warden", "MONSTER", "Marsh Warden", fixture.statBlockIdsOf(seeded).get(3), 30, 30));
    }

    @Test
    void theSceneGraphAndQuestAreFullyLinked() throws IOException {
        var seeded = fixture.seed();
        var approach = sceneRepository.findByCampaignIdOrderByChapterAndSort(seeded.campaignId()).stream()
                .filter(s -> s.getTitle().equals("Mossbound Approach")).findFirst().orElseThrow();
        var quest = questRepository.findByIdAndCampaignId(seeded.questId(), seeded.campaignId()).orElseThrow();

        assertThat(approach.getTransitions())
                .extracting(t -> t.getLabel(), t -> t.getTargetScene().getId())
                .containsExactly(
                        tuple("Descend to the undercroft", seeded.hostileSceneId()),
                        tuple("Take the tideglass gallery", seeded.branchSceneId()));
        assertThat(quest.getObjectives())
                .extracting(o -> o.getTitle(), o -> o.getStatus(), o -> o.getDescription(), o -> o.getSourceLocator())
                .containsExactly(
                        tuple("Recover the wickstone", dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus.COMPLETED,
                                "Find the wickstone beneath the bell.", "Synthetic, quest 1"),
                        tuple("Relight the beacon", dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus.NOT_STARTED,
                                "Place the wickstone in the hollow lantern.", "Synthetic, quest 1"));
    }

    @Test
    void theAssetSetHasPlayerSafeAndDmOnlyHandouts() throws IOException {
        var seeded = fixture.seed();

        assertThat(handoutRepository.findById(seeded.playerSafeHandoutId()).orElseThrow().isDmOnly())
                .isFalse();
        assertThat(handoutRepository.findById(seeded.dmSourceHandoutId()).orElseThrow().isDmOnly())
                .isTrue();
    }

    @Test
    void thePartyHasFourMembers() throws IOException {
        var seeded = fixture.seed();

        assertThat(seeded.partyMemberIds()).hasSize(4);
        assertThat(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(seeded.campaignId()))
                .hasSize(4);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReleaseRehearsalFixture.Shape.class)
    void nothingInTheFixtureCameFromAPublishedCampaign(ReleaseRehearsalFixture.Shape shape) throws IOException {
        var seeded = fixture.seed(shape);
        String everything = fixture.textualContentOf(seeded);

        assertThat(everything.toLowerCase())
                .doesNotContain("phandelver", "klarg", "cragmaw", "wave echo", "sildar",
                        "gundren", "rockseeker", "neverwinter", "tresendar");

        mapRepository.findByCampaignIdOrderBySortOrderAsc(seeded.campaignId()).forEach(map ->
                assertThat(everything).as("all persisted fields of %s are in the provenance text", map.getName())
                        .contains(map.getName(), String.valueOf(map.getGridWidth()), String.valueOf(map.getGridHeight()),
                                String.valueOf(map.getCellSizePx()), String.valueOf(map.getSortOrder()), map.getGridType(),
                                map.getMovementMode(), String.valueOf(map.isShowGrid())));
        encounterRepository.findByCampaignIdOrderByNameAsc(seeded.campaignId()).forEach(encounter -> {
            assertThat(everything).as("all persisted fields of %s are in the provenance text", encounter.getName())
                    .contains(encounter.getName(), encounter.getStatus().name(), encounter.getCombatPhase().name(),
                            encounter.getEncounterKey(), String.valueOf(encounter.getRound()),
                            String.valueOf(encounter.getActiveTurnIndex()), String.valueOf(encounter.isLairActionTriggered()),
                            encounter.getMap() == null ? "" : encounter.getMap().getName());
            encounterWaveRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).forEach(wave ->
                    assertThat(everything).as("wave %s is in the provenance text", wave.getWaveKey())
                            .contains(wave.getWaveKey(), wave.getName(), wave.getStatus().name(), wave.getTriggerKind().name(),
                                    String.valueOf(wave.getSortOrder())));
            encounterWaveRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).stream()
                    .filter(wave -> wave.getNotes() != null)
                    .forEach(wave -> assertThat(everything).as("wave %s notes are in the provenance text", wave.getWaveKey())
                            .contains(wave.getNotes()));
            combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).forEach(combatant ->
                    assertThat(everything).as("combatant %s is in the provenance text", combatant.getName())
                            .contains(combatant.getName(), String.valueOf(combatant.getSortOrder()),
                                    String.valueOf(combatant.getMaxHp()), String.valueOf(combatant.getCurrentHp()),
                                    combatant.getKind(), String.valueOf(combatant.isDefeated()),
                                    String.valueOf(combatant.isHidden()), combatant.getConditionsJson()));
            combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).stream()
                    .filter(combatant -> combatant.getNotes() != null)
                    .forEach(combatant -> assertThat(everything)
                            .as("combatant %s notes are in the provenance text", combatant.getName())
                            .contains(combatant.getNotes()));
        });

        if (shape == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
            var reserveNote = combatantRepository.findById(seeded.branchedReserveCombatantIds().get(0))
                    .orElseThrow().getNotes();
            assertThat(everything).contains(
                    "Tideglass Gallery", "12", "10", "48", "Lantern Vault Ambush",
                    "The vault becomes hostile without a battle map.", "Vault silence",
                    "The lantern flame bends toward unseen footsteps.", "Encounter: Lantern Vault Ambush",
                    "main", "ACTIVE", "vault-reinforcements", "Vault reinforcements",
                    "Synthetic reserve wave.", reserveNote);
        }
    }

    @Test
    void seededIdsAreCampaignScopedAndRepeatable() throws IOException {
        var first = fixture.seed();
        var second = fixture.seed();

        assertThat(first.campaignId()).isNotEqualTo(second.campaignId());
        assertThat(first.adventureId()).isNotEqualTo(second.adventureId());
        assertThat(first.hostileSceneId()).isNotEqualTo(second.hostileSceneId());
        assertThat(sceneRepository.findByIdAndCampaignId(first.campaignId(), first.hostileSceneId())).isPresent();
        assertThat(sceneRepository.findByIdAndCampaignId(second.campaignId(), second.hostileSceneId())).isPresent();
        assertThat(questRepository.findByIdAndCampaignId(first.questId(), first.campaignId())).isPresent();
        assertThat(questRepository.findByIdAndCampaignId(second.questId(), second.campaignId())).isPresent();
        assertThat(handoutRepository.findByCampaignIdAndId(first.campaignId(), first.dmSourceHandoutId())).isPresent();
        assertThat(handoutRepository.findByCampaignIdAndId(second.campaignId(), second.dmSourceHandoutId())).isPresent();
        assertThat(campaignRepository.findById(first.campaignId())).isPresent();
        assertThat(mapRepository.findById(first.playableMapId()).orElseThrow().getCampaign().getId())
                .isEqualTo(first.campaignId());
        assertThat(mapRepository.findById(second.playableMapId()).orElseThrow().getCampaign().getId())
                .isEqualTo(second.campaignId());
        assertThat(partyMemberRepository.findById(first.partyMemberIds().get(0)).orElseThrow().getCampaign().getId())
                .isEqualTo(first.campaignId());
        assertThat(partyMemberRepository.findById(second.partyMemberIds().get(0)).orElseThrow().getCampaign().getId())
                .isEqualTo(second.campaignId());
        assertThat(statBlockRepository.findByCampaignIdOrderByNameAsc(first.campaignId())).hasSize(4);
        assertThat(statBlockRepository.findByCampaignIdOrderByNameAsc(second.campaignId())).hasSize(4);
    }

    @Test
    void provenanceTextIncludesEveryPersistedFixtureSurface() throws IOException {
        var seeded = fixture.seed();
        String text = fixture.textualContentOf(seeded);

        assertThat(text).contains(
                "Synthetic session rehearsal campaign", "Lanterns Below", "Synthetic source",
                "The Drowned Stair", "Mossbound Approach", "A lantern-marked path leads",
                "Synthetic, scene A1", "The low bell", "A low bell trembles",
                "Synthetic, A1", "Descend to the undercroft", "When the party follows the bell.",
                "The descent is slick.", "Take the tideglass gallery", "When the party avoids the bell.",
                "The gallery is narrow.", "Synthetic, scene A2", "Undercroft floor",
                "Undercroft position 1", "Bog Sentinel", "Bog Skirmisher", "Marsh Warden",
                "Beacon Undercroft", "20", "15", "64", "Beacon Approach (player map)", "map",
                "Undercroft reference page", "source", "image/png",
                "Light the hollow beacon", "Restore the beacon before the marsh tide rises.",
                "A safe route through the marsh", "Find the bell chamber", "The marsh crossing remains open.",
                "Recover the wickstone", "Find the wickstone beneath the bell.",
                "Relight the beacon", "Place the wickstone in the hollow lantern.",
                "Ilsa Fenwright", "Ordo Brack", "Nesh Vell", "Tamsin Aroe", "Undercroft Alarm",
                "MONSTER",
                "Beacon Approach", "Undercroft reference");
    }

    @Test
    void provenanceTextIsolatedAcrossSeededCampaigns() throws IOException {
        var first = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        var second = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        var firstEncounter = encounterRepository.findById(first.branchedEncounterId()).orElseThrow();
        var secondEncounter = encounterRepository.findById(second.branchedEncounterId()).orElseThrow();
        var firstCombatants = combatantRepository.findByEncounterIdOrderBySortOrderAsc(firstEncounter.getId());
        var secondCombatants = combatantRepository.findByEncounterIdOrderBySortOrderAsc(secondEncounter.getId());
        var firstReserveCombatant = combatantRepository.findById(first.branchedReserveCombatantIds().get(0)).orElseThrow();
        var secondReserveCombatant = combatantRepository.findById(second.branchedReserveCombatantIds().get(0)).orElseThrow();
        String firstText = fixture.textualContentOf(first);
        String secondText = fixture.textualContentOf(second);

        assertThat(firstEncounter.getEncounterKey()).isNotBlank().isNotEqualTo(secondEncounter.getEncounterKey());
        assertThat(firstCombatants).hasSize(5);
        assertThat(secondCombatants).hasSize(5);
        assertThat(firstReserveCombatant.getNotes()).isNotBlank().isNotEqualTo(secondReserveCombatant.getNotes());
        assertThat(firstText).contains(firstEncounter.getEncounterKey(), firstReserveCombatant.getNotes())
                .contains("Tideglass Gallery", "Lantern Vault Ambush", "Vault reinforcements")
                .doesNotContain(secondEncounter.getEncounterKey(), secondReserveCombatant.getNotes());
        assertThat(secondText).contains(secondEncounter.getEncounterKey(), secondReserveCombatant.getNotes())
                .contains("Tideglass Gallery", "Lantern Vault Ambush", "Vault reinforcements")
                .doesNotContain(firstEncounter.getEncounterKey(), firstReserveCombatant.getNotes());
        assertThat(encounterRepository.findByCampaignIdOrderByNameAsc(first.campaignId()))
                .extracting(Encounter::getCampaign).allMatch(campaign -> campaign.getId().equals(first.campaignId()));
        assertThat(encounterRepository.findByCampaignIdOrderByNameAsc(second.campaignId()))
                .extracting(Encounter::getCampaign).allMatch(campaign -> campaign.getId().equals(second.campaignId()));
        assertThat(mapRepository.findByCampaignIdOrderBySortOrderAsc(first.campaignId()))
                .allMatch(map -> map.getCampaign().getId().equals(first.campaignId()));
        assertThat(mapRepository.findByCampaignIdOrderBySortOrderAsc(second.campaignId()))
                .allMatch(map -> map.getCampaign().getId().equals(second.campaignId()));
    }

    @Test
    void branchedMapSurfacesAreCampaignSpecificInProvenanceText() throws IOException {
        var first = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        var second = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        var firstMaps = mapRepository.findByCampaignIdOrderBySortOrderAsc(first.campaignId());
        var secondMaps = mapRepository.findByCampaignIdOrderBySortOrderAsc(second.campaignId());
        String firstText = fixture.textualContentOf(first);
        String secondText = fixture.textualContentOf(second);

        assertThat(firstMaps).hasSize(2);
        assertThat(secondMaps).hasSize(2);
        assertThat(firstMaps).extracting(m -> m.getName())
                .doesNotContainAnyElementsOf(secondMaps.stream().map(m -> m.getName()).toList());
        assertThat(firstText).contains(firstMaps.get(0).getName(), firstMaps.get(1).getName())
                .doesNotContain(secondMaps.get(0).getName(), secondMaps.get(1).getName());
        assertThat(secondText).contains(secondMaps.get(0).getName(), secondMaps.get(1).getName())
                .doesNotContain(firstMaps.get(0).getName(), firstMaps.get(1).getName());
    }
}
