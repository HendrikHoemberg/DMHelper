package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
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

@DataJpaTest
@Import({AudioCueDependencyService.class})
class AudioCueDependencyServiceTest {

    @Autowired private AudioCueDependencyService dependencyService;
    @Autowired private AudioCueRepository audioCueRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignSessionRepository sessionRepository;
    @Autowired private SessionAudioStateRepository stateRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private WorldLocationRepository locationRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private AudioCue cue;
    private CampaignSession session;

    @BeforeEach
    void setUp() {
        stateRepository.deleteAll();
        audioCueRepository.deleteAll();
        sessionRepository.deleteAll();
        campaignRepository.deleteAll();

        campaign = new Campaign();
        campaign.setName("Dep Campaign");
        campaign = campaignRepository.save(campaign);

        session = new CampaignSession();
        session.setCampaign(campaign);
        session = sessionRepository.save(session);

        cue = new AudioCue();
        cue.setCampaign(campaign);
        cue.setCueKey("dep-cue");
        cue.setName("Dep Cue");
        cue.setReferenceKind(AudioReferenceKind.VIDEO);
        cue.setProviderReference("dQw4w9WgXcQ");
        cue.setCategory(AudioCategory.AMBIENT);
        cue.setTransitionPreference(AudioTransitionPreference.CROSSFADE);
        cue = audioCueRepository.save(cue);
        em.flush();
    }

    @Test
    void emptyImpactForUnusedCue() {
        AudioCueDeletionImpact impact = dependencyService.computeDeletionImpact(cue);
        assertThat(impact.hasDependents()).isFalse();
        assertThat(impact.cueId()).isEqualTo(cue.getId());
        assertThat(impact.cueName()).isEqualTo("Dep Cue");
    }

    @Test
    void reportsSessionStateDependencies() {
        SessionAudioState state = new SessionAudioState();
        state.setSession(session);
        state.setManualOverrideCue(cue);
        state.setAcceptedAutomaticCue(cue);
        state.setPendingCue(cue);
        state.setDismissedCandidateCue(cue);
        state.setTemporaryVictoryCue(cue);
        stateRepository.save(state);
        em.flush();

        AudioCueDeletionImpact impact = dependencyService.computeDeletionImpact(cue);
        assertThat(impact.hasDependents()).isTrue();
        assertThat(impact.dependencies())
                .extracting(AudioCueDependency::kind)
                .containsExactlyInAnyOrder(
                        AudioCueDependencyService.DEP_KIND_SESSION_OVERRIDE,
                        AudioCueDependencyService.DEP_KIND_SESSION_ACCEPTED,
                        AudioCueDependencyService.DEP_KIND_SESSION_PENDING,
                        AudioCueDependencyService.DEP_KIND_SESSION_DISMISSED,
                        AudioCueDependencyService.DEP_KIND_SESSION_VICTORY);
    }

    @Test
    void reportsAndClearsEveryAuthoredAndRuntimeDependency() {
        campaign.setDefaultAudioCue(cue);
        campaignRepository.save(campaign);

        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Adventure");
        em.persist(adventure);
        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Chapter");
        em.persist(chapter);
        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Scene");
        scene.setSceneAudioCue(cue);
        em.persist(scene);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Encounter");
        encounter.setCombatAudioCue(cue);
        encounter.setVictoryAudioCue(cue);
        encounter.setVictoryCueDurationSeconds(30);
        encounter = encounterRepository.save(encounter);

        WorldLocation location = new WorldLocation();
        location.setCampaign(campaign);
        location.setName("Location");
        location.setLocationAudioCue(cue);
        location = locationRepository.save(location);

        SessionAudioState state = new SessionAudioState();
        state.setSession(session);
        state.setManualOverrideCue(cue);
        state.setAcceptedAutomaticCue(cue);
        state.setAcceptedSourceKind(AudioCueSource.SourceKind.SCENE.name());
        state.setPendingCue(cue);
        state.setPendingSourceKind(AudioCueSource.SourceKind.COMBAT.name());
        state.setDismissedCandidateCue(cue);
        state.setTemporaryVictoryCue(cue);
        state.setVictorySourceId(encounter.getId());
        state.setVictorySourceLabel("Victory: Encounter");
        state = stateRepository.save(state);
        UUID stateId = state.getId();
        UUID sceneId = scene.getId();
        UUID encounterId = encounter.getId();
        UUID locationId = location.getId();
        em.flush();

        AudioCueDeletionImpact impact = dependencyService.computeDeletionImpact(cue);
        assertThat(impact.dependencies()).extracting(AudioCueDependency::kind)
                .containsExactlyInAnyOrder(
                        AudioCueDependencyService.DEP_KIND_CAMPAIGN,
                        AudioCueDependencyService.DEP_KIND_SCENE,
                        AudioCueDependencyService.DEP_KIND_ENCOUNTER_COMBAT,
                        AudioCueDependencyService.DEP_KIND_ENCOUNTER_VICTORY,
                        AudioCueDependencyService.DEP_KIND_LOCATION,
                        AudioCueDependencyService.DEP_KIND_SESSION_OVERRIDE,
                        AudioCueDependencyService.DEP_KIND_SESSION_ACCEPTED,
                        AudioCueDependencyService.DEP_KIND_SESSION_PENDING,
                        AudioCueDependencyService.DEP_KIND_SESSION_DISMISSED,
                        AudioCueDependencyService.DEP_KIND_SESSION_VICTORY);

        dependencyService.clearDependencies(cue);
        em.flush();
        em.clear();

        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow().getDefaultAudioCue()).isNull();
        assertThat(sceneRepository.findById(sceneId).orElseThrow().getSceneAudioCue()).isNull();
        Encounter clearedEncounter = encounterRepository.findById(encounterId).orElseThrow();
        assertThat(clearedEncounter.getCombatAudioCue()).isNull();
        assertThat(clearedEncounter.getVictoryAudioCue()).isNull();
        assertThat(clearedEncounter.getVictoryCueDurationSeconds()).isNull();
        assertThat(locationRepository.findById(locationId).orElseThrow().getLocationAudioCue()).isNull();
        SessionAudioState clearedState = stateRepository.findById(stateId).orElseThrow();
        assertThat(clearedState.getManualOverrideCue()).isNull();
        assertThat(clearedState.getAcceptedAutomaticCue()).isNull();
        assertThat(clearedState.getAcceptedSourceKind()).isNull();
        assertThat(clearedState.getPendingCue()).isNull();
        assertThat(clearedState.getPendingSourceKind()).isNull();
        assertThat(clearedState.getDismissedCandidateCue()).isNull();
        assertThat(clearedState.getTemporaryVictoryCue()).isNull();
        assertThat(clearedState.getVictorySourceLabel()).isNull();
    }
}
