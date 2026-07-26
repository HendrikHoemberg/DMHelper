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
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import org.junit.jupiter.api.Test;
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
    @Autowired private CombatantRepository combatantRepository;

    @Test
    void theSeededCampaignIsSessionReady() throws IOException {
        var seeded = fixture.seed();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).sessionReady())
                .as("a rehearsal that starts blocked proves nothing about the rehearsal")
                .isTrue();
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
    void theAssetSetCoversAllThreeClassifications() throws IOException {
        var seeded = fixture.seed();

        assertThat(handoutRepository.findById(seeded.playerSafeHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.PLAYER_SAFE);
        assertThat(handoutRepository.findById(seeded.dmSourceHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.DM_SOURCE);
        assertThat(handoutRepository.findById(seeded.derivativeHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.PLAYER_DERIVATIVE);
        assertThat(handoutRepository.findById(seeded.derivativeHandoutId()).orElseThrow().getSourceHandout().getId())
                .isEqualTo(seeded.dmSourceHandoutId());
    }

    @Test
    void thePartyHasFourMembers() throws IOException {
        var seeded = fixture.seed();

        assertThat(seeded.partyMemberIds()).hasSize(4);
        assertThat(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(seeded.campaignId()))
                .hasSize(4);
    }

    @Test
    void nothingInTheFixtureCameFromAPublishedCampaign() throws IOException {
        var seeded = fixture.seed();
        String everything = fixture.textualContentOf(seeded);

        assertThat(everything.toLowerCase())
                .doesNotContain("phandelver", "klarg", "cragmaw", "wave echo", "sildar",
                        "gundren", "rockseeker", "neverwinter", "tresendar");
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
                "Undercroft reference page", "source", "Undercroft player extract", "image/png",
                "Light the hollow beacon", "Restore the beacon before the marsh tide rises.",
                "A safe route through the marsh", "Find the bell chamber", "The marsh crossing remains open.",
                "Recover the wickstone", "Find the wickstone beneath the bell.",
                "Relight the beacon", "Place the wickstone in the hollow lantern.",
                "Ilsa Fenwright", "Ordo Brack", "Nesh Vell", "Tamsin Aroe", "Undercroft Alarm",
                "MONSTER");
        assertThat(text).contains("sourceWidth", "cropWidth", "redactions");
    }

    @Test
    void provenanceTextIsolatedAcrossSeededCampaigns() throws IOException {
        var first = fixture.seed();
        var second = fixture.seed();
        var firstEncounter = encounterRepository.findByCampaignIdOrderByNameAsc(first.campaignId()).stream()
                .findFirst().orElseThrow();
        var secondEncounter = encounterRepository.findByCampaignIdOrderByNameAsc(second.campaignId()).stream()
                .findFirst().orElseThrow();
        var firstCombatants = combatantRepository.findByEncounterIdOrderBySortOrderAsc(firstEncounter.getId());
        var secondCombatants = combatantRepository.findByEncounterIdOrderBySortOrderAsc(secondEncounter.getId());
        String firstText = fixture.textualContentOf(first);
        String secondText = fixture.textualContentOf(second);

        assertThat(firstEncounter.getEncounterKey()).isNotBlank().isNotEqualTo(secondEncounter.getEncounterKey());
        assertThat(firstCombatants).hasSize(4);
        assertThat(secondCombatants).hasSize(4);
        assertThat(firstCombatants.get(0).getNotes()).isNotBlank().isNotEqualTo(secondCombatants.get(0).getNotes());
        assertThat(firstText).contains(firstEncounter.getEncounterKey(), firstCombatants.get(0).getNotes())
                .doesNotContain(secondEncounter.getEncounterKey(), secondCombatants.get(0).getNotes());
        assertThat(secondText).contains(secondEncounter.getEncounterKey(), secondCombatants.get(0).getNotes())
                .doesNotContain(firstEncounter.getEncounterKey(), firstCombatants.get(0).getNotes());
        assertThat(encounterRepository.findByCampaignIdOrderByNameAsc(first.campaignId())).containsExactly(firstEncounter);
        assertThat(encounterRepository.findByCampaignIdOrderByNameAsc(second.campaignId())).containsExactly(secondEncounter);
    }
}
