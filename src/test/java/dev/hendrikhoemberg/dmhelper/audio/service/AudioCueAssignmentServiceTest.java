package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AudioCueAssignmentService.class, AudioCueService.class, AudioCueValidator.class, AudioCueDependencyService.class})
class AudioCueAssignmentServiceTest {

    @Autowired private AudioCueAssignmentService assignmentService;
    @Autowired private AudioCueRepository audioCueRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private WorldLocationRepository locationRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private Campaign otherCampaign;
    private AudioCue campaignCue;
    private AudioCue otherCampaignCue;
    private UUID campaignId;
    private UUID otherCampaignId;

    @BeforeEach
    void setUp() {
        audioCueRepository.deleteAll();
        campaignRepository.deleteAll();

        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        otherCampaign = campaignRepository.save(otherCampaign);
        otherCampaignId = otherCampaign.getId();

        campaignCue = createCue(campaign, "campaign-cue");
        otherCampaignCue = createCue(otherCampaign, "other-cue");
        em.flush();
    }

    private AudioCue createCue(Campaign camp, String cueKey) {
        AudioCue cue = new AudioCue();
        cue.setCampaign(camp);
        cue.setCueKey(cueKey);
        cue.setName(cueKey);
        cue.setReferenceKind(dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind.VIDEO);
        cue.setProviderReference("ref");
        cue.setCategory(dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory.AMBIENT);
        cue.setTransitionPreference(dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference.CROSSFADE);
        return audioCueRepository.save(cue);
    }

    @Test
    void assignsCampaignDefaultCue() {
        assignmentService.assignCampaignCue(campaignId, campaignCue.getId());
        em.flush();
        em.clear();
        Campaign updated = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(updated.getDefaultAudioCue()).isNotNull();
        assertThat(updated.getDefaultAudioCue().getId()).isEqualTo(campaignCue.getId());
    }

    @Test
    void clearsCampaignDefaultCueWithNull() {
        assignmentService.assignCampaignCue(campaignId, campaignCue.getId());
        em.flush();
        assignmentService.assignCampaignCue(campaignId, null);
        em.flush();
        em.clear();
        Campaign updated = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(updated.getDefaultAudioCue()).isNull();
    }

    @Test
    void rejectsCrossCampaignCueForCampaign() {
        assertThatThrownBy(() -> assignmentService.assignCampaignCue(campaignId, otherCampaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsSceneCue() {
        Scene scene = createScene(campaign);
        assignmentService.assignSceneCue(scene.getId(), campaignCue.getId());
        em.flush();
        em.clear();
        Scene updated = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(updated.getSceneAudioCue()).isNotNull();
        assertThat(updated.getSceneAudioCue().getId()).isEqualTo(campaignCue.getId());
    }

    @Test
    void clearsSceneCueWithNull() {
        Scene scene = createScene(campaign);
        assignmentService.assignSceneCue(scene.getId(), campaignCue.getId());
        em.flush();
        assignmentService.assignSceneCue(scene.getId(), null);
        em.flush();
        em.clear();
        Scene updated = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(updated.getSceneAudioCue()).isNull();
    }

    @Test
    void rejectsCrossCampaignCueForScene() {
        Scene scene = createScene(campaign);
        assertThatThrownBy(() -> assignmentService.assignSceneCue(scene.getId(), otherCampaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCrossCampaignScene() {
        Scene scene = createScene(otherCampaign);
        assertThatThrownBy(() -> assignmentService.assignSceneCue(scene.getId(), campaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsEncounterCombatCue() {
        Encounter encounter = createEncounter(campaign);
        assignmentService.assignEncounterCombatCue(encounter.getId(), campaignCue.getId());
        em.flush();
        em.clear();
        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getCombatAudioCue()).isNotNull();
        assertThat(updated.getCombatAudioCue().getId()).isEqualTo(campaignCue.getId());
    }

    @Test
    void assignsEncounterVictoryCue() {
        Encounter encounter = createEncounter(campaign);
        assignmentService.assignEncounterVictoryCue(encounter.getId(), campaignCue.getId(), 30);
        em.flush();
        em.clear();
        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getVictoryAudioCue()).isNotNull();
        assertThat(updated.getVictoryAudioCue().getId()).isEqualTo(campaignCue.getId());
        assertThat(updated.getVictoryCueDurationSeconds()).isEqualTo(30);
    }

    @Test
    void clearsEncounterCombatCueWithNull() {
        Encounter encounter = createEncounter(campaign);
        assignmentService.assignEncounterCombatCue(encounter.getId(), campaignCue.getId());
        em.flush();
        assignmentService.assignEncounterCombatCue(encounter.getId(), null);
        em.flush();
        em.clear();
        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getCombatAudioCue()).isNull();
    }

    @Test
    void clearsEncounterVictoryCueWithNull() {
        Encounter encounter = createEncounter(campaign);
        assignmentService.assignEncounterVictoryCue(encounter.getId(), campaignCue.getId(), 30);
        em.flush();
        assignmentService.assignEncounterVictoryCue(encounter.getId(), null, null);
        em.flush();
        em.clear();
        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getVictoryAudioCue()).isNull();
        assertThat(updated.getVictoryCueDurationSeconds()).isNull();
    }

    @Test
    void acceptsVictoryDurationOnlyWhenVictoryCueExists() {
        Encounter encounter = createEncounter(campaign);
        assignmentService.assignEncounterVictoryCue(encounter.getId(), campaignCue.getId(), 30);
        em.flush();
        em.clear();
        Encounter updated = encounterRepository.findById(encounter.getId()).orElseThrow();
        assertThat(updated.getVictoryAudioCue()).isNotNull();
        assertThat(updated.getVictoryCueDurationSeconds()).isEqualTo(30);
    }

    @Test
    void rejectsVictoryDurationOutOfBounds() {
        Encounter encounter = createEncounter(campaign);
        assertThatThrownBy(() -> assignmentService.assignEncounterVictoryCue(encounter.getId(), campaignCue.getId(), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assignmentService.assignEncounterVictoryCue(encounter.getId(), campaignCue.getId(), 601))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCrossCampaignCueForEncounter() {
        Encounter encounter = createEncounter(campaign);
        assertThatThrownBy(() -> assignmentService.assignEncounterCombatCue(encounter.getId(), otherCampaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCrossCampaignEncounter() {
        Encounter encounter = createEncounter(otherCampaign);
        assertThatThrownBy(() -> assignmentService.assignEncounterCombatCue(encounter.getId(), campaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsLocationCue() {
        WorldLocation location = createLocation(campaign);
        assignmentService.assignLocationCue(location.getId(), campaignCue.getId());
        em.flush();
        em.clear();
        WorldLocation updated = locationRepository.findById(location.getId()).orElseThrow();
        assertThat(updated.getLocationAudioCue()).isNotNull();
        assertThat(updated.getLocationAudioCue().getId()).isEqualTo(campaignCue.getId());
    }

    @Test
    void clearsLocationCueWithNull() {
        WorldLocation location = createLocation(campaign);
        assignmentService.assignLocationCue(location.getId(), campaignCue.getId());
        em.flush();
        assignmentService.assignLocationCue(location.getId(), null);
        em.flush();
        em.clear();
        WorldLocation updated = locationRepository.findById(location.getId()).orElseThrow();
        assertThat(updated.getLocationAudioCue()).isNull();
    }

    @Test
    void rejectsCrossCampaignCueForLocation() {
        WorldLocation location = createLocation(campaign);
        assertThatThrownBy(() -> assignmentService.assignLocationCue(location.getId(), otherCampaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCrossCampaignLocation() {
        WorldLocation location = createLocation(otherCampaign);
        assertThatThrownBy(() -> assignmentService.assignLocationCue(location.getId(), campaignCue.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnNonexistentCampaign() {
        assertThatThrownBy(() -> assignmentService.assignCampaignCue(UUID.randomUUID(), campaignCue.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsOnNonexistentScene() {
        assertThatThrownBy(() -> assignmentService.assignSceneCue(UUID.randomUUID(), campaignCue.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsOnNonexistentEncounter() {
        assertThatThrownBy(() -> assignmentService.assignEncounterCombatCue(UUID.randomUUID(), campaignCue.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsOnNonexistentLocation() {
        assertThatThrownBy(() -> assignmentService.assignLocationCue(UUID.randomUUID(), campaignCue.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsOnNonexistentCue() {
        Scene scene = createScene(campaign);
        assertThatThrownBy(() -> assignmentService.assignSceneCue(scene.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void crossCampaignAttemptDoesNotLeakForeignTargetName() {
        Scene scene = createScene(campaign);
        try {
            assignmentService.assignSceneCue(scene.getId(), otherCampaignCue.getId());
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).doesNotContain(otherCampaign.getName());
            assertThat(e.getMessage()).doesNotContain(otherCampaignCue.getName());
        }
    }

    @Test
    void crossCampaignAttemptDoesNotLeakForeignCueName() {
        WorldLocation location = createLocation(campaign);
        try {
            assignmentService.assignLocationCue(location.getId(), otherCampaignCue.getId());
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).doesNotContain(otherCampaign.getName());
            assertThat(e.getMessage()).doesNotContain(otherCampaignCue.getName());
        }
    }

    private Scene createScene(Campaign camp) {
        dev.hendrikhoemberg.dmhelper.adventure.data.Adventure adventure = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
        adventure.setCampaign(camp);
        adventure.setName("Test Adventure");
        em.persist(adventure);

        dev.hendrikhoemberg.dmhelper.adventure.data.Chapter chapter = new dev.hendrikhoemberg.dmhelper.adventure.data.Chapter();
        chapter.setTitle("Test Chapter");
        chapter.setAdventure(adventure);
        em.persist(chapter);

        Scene scene = new Scene();
        scene.setTitle("Test Scene");
        scene.setChapter(chapter);
        em.persist(scene);
        return scene;
    }

    private Encounter createEncounter(Campaign camp) {
        Encounter encounter = new Encounter();
        encounter.setCampaign(camp);
        encounter.setName("Test Encounter");
        return encounterRepository.save(encounter);
    }

    private WorldLocation createLocation(Campaign camp) {
        WorldLocation location = new WorldLocation();
        location.setCampaign(camp);
        location.setName("Test Location");
        return locationRepository.save(location);
    }
}
