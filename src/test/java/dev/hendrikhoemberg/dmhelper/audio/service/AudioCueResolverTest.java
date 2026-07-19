package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioCueResolverTest {

    @Mock private Clock clock;
    @Mock private SceneLocationResolver sceneLocationResolver;
    @Mock private SceneRepository sceneRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private EncounterRepository encounterRepository;

    @InjectMocks
    private AudioCueResolver resolver;

    private final Instant now = Instant.parse("2026-07-19T12:00:00Z");
    private final UUID campaignId = UUID.randomUUID();
    private Campaign campaign;
    private CampaignSession session;
    private SessionAudioState state;
    private AudioCue manualCue;
    private AudioCue victoryCue;
    private AudioCue combatCue;
    private AudioCue sceneCue;
    private AudioCue locationCue;
    private AudioCue defaultCue;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");

        session = new CampaignSession();
        session.setId(UUID.randomUUID());
        session.setCampaign(campaign);

        state = new SessionAudioState();
        state.setSession(session);

        manualCue = audioCue("Manual");
        victoryCue = audioCue("Victory");
        combatCue = audioCue("Combat");
        sceneCue = audioCue("Scene");
        locationCue = audioCue("Location");
        defaultCue = audioCue("Default");

        lenient().when(clock.instant()).thenReturn(now);
        lenient().when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    }

    @Test
    void manualOverrideReturnsTopPriority() {
        state.setManualOverrideCue(manualCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue()).isNotNull();
        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
        assertThat(result.source().kind()).isEqualTo(AudioCueSource.SourceKind.MANUAL_OVERRIDE);
    }

    @Test
    void manualOverrideMasksVictory() {
        state.setManualOverrideCue(manualCue);
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now.plusSeconds(30));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
    }

    @Test
    void manualOverrideMasksCombat() {
        state.setManualOverrideCue(manualCue);
        campaign.setCurrentSceneId(UUID.randomUUID());

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
    }

    @Test
    void manualOverrideMasksScene() {
        state.setManualOverrideCue(manualCue);
        campaign.setCurrentSceneId(UUID.randomUUID());

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
    }

    @Test
    void manualOverrideMasksLocation() {
        state.setManualOverrideCue(manualCue);
        campaign.setCurrentSceneId(UUID.randomUUID());

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
    }

    @Test
    void manualOverrideMasksDefault() {
        state.setManualOverrideCue(manualCue);
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
    }

    @Test
    void clearManualOverrideFallsBack() {
        state.setManualOverrideCue(manualCue);
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue withOverride = resolver.resolve(state, campaignId);
        assertThat(withOverride.cue().getId()).isEqualTo(manualCue.getId());

        state.setManualOverrideCue(null);

        ResolvedAudioCue withoutOverride = resolver.resolve(state, campaignId);
        assertThat(withoutOverride.cue().getId()).isEqualTo(defaultCue.getId());
    }

    @Test
    void victoryOverlayResolvesBeforeExpiry() {
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now.plusSeconds(30));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(victoryCue.getId());
        assertThat(result.source().kind()).isEqualTo(AudioCueSource.SourceKind.VICTORY);
    }

    @Test
    void victoryOverlayRequiresConfirmationInConfirmMode() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setAcceptedAutomaticCue(sceneCue);
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now.plusSeconds(30));
        state.setVictorySourceId(UUID.randomUUID());
        state.setVictorySourceLabel("Victory: Keep");

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue()).isSameAs(sceneCue);
        assertThat(state.getPendingCue()).isSameAs(victoryCue);
        assertThat(state.getPendingSourceKind()).isEqualTo(AudioCueSource.SourceKind.VICTORY.name());
        assertThat(state.getPendingSourceLabel()).isEqualTo("Victory: Keep");
    }

    @Test
    void victoryExpiryFallsBack() {
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now.minusSeconds(1));
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(sceneCue.getId());
    }

    @Test
    void victoryMasksCombat() {
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now.plusSeconds(30));
        campaign.setCurrentSceneId(UUID.randomUUID());

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(victoryCue.getId());
    }

    @Test
    void combatCueFromActiveEncounter() {
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithEncounter(combatCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(combatCue.getId());
        assertThat(result.source().kind()).isEqualTo(AudioCueSource.SourceKind.COMBAT);
    }

    @Test
    void combatCueUsesCampaignActiveEncounterEvenWhenNotAttachedToCurrentScene() {
        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID());
        encounter.setCampaign(campaign);
        encounter.setName("Independent encounter");
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter.setCombatAudioCue(combatCue);
        when(encounterRepository.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(encounter));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue()).isSameAs(combatCue);
        assertThat(result.source().sourceId()).isEqualTo(encounter.getId());
    }

    @Test
    void combatMasksSceneCue() {
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithEncounter(combatCue);
        scene.setSceneAudioCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(combatCue.getId());
    }

    @Test
    void sceneCueFromCurrentScene() {
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(sceneCue.getId());
        assertThat(result.source().kind()).isEqualTo(AudioCueSource.SourceKind.SCENE);
    }

    @Test
    void sceneMasksLocationCue() {
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(sceneCue.getId());
    }

    @Test
    void locationCueFromSceneLinks() {
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(null);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));
        when(sceneLocationResolver.resolve(campaign.getCurrentSceneId(), campaignId))
                .thenReturn(Optional.of(locationCue));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(locationCue.getId());
        assertThat(result.source().kind()).isEqualTo(AudioCueSource.SourceKind.LOCATION);
    }

    @Test
    void locationMasksDefault() {
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(null);
        campaign.setDefaultAudioCue(defaultCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));
        when(sceneLocationResolver.resolve(campaign.getCurrentSceneId(), campaignId))
                .thenReturn(Optional.of(locationCue));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(locationCue.getId());
    }

    @Test
    void defaultCueWhenNoHigherPriority() {
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(defaultCue.getId());
        assertThat(result.source().kind()).isEqualTo(AudioCueSource.SourceKind.CAMPAIGN_DEFAULT);
    }

    @Test
    void silenceWhenNoCueAvailable() {
        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.isSilence()).isTrue();
        assertThat(result.cue()).isNull();
    }

    @Test
    void noCurrentSceneFallsThroughToDefault() {
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(defaultCue.getId());
    }

    @Test
    void deletedCueIsSkippedInPriority() {
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(defaultCue.getId());
    }

    @Test
    void automaticModeSwitchesImmediately() {
        state.setSwitchMode(AudioSwitchMode.AUTOMATIC);
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(defaultCue.getId());
    }

    @Test
    void confirmModeSetsPendingCandidate() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue()).isNull();
        assertThat(state.getPendingCue()).isNotNull();
        assertThat(state.getPendingCue().getId()).isEqualTo(sceneCue.getId());
    }

    @Test
    void confirmModeReturnsAcceptedCue() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setAcceptedAutomaticCue(sceneCue);
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(sceneCue.getId());
    }

    @Test
    void confirmModeSetsPendingWhenCandidateDiffers() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setAcceptedAutomaticCue(defaultCue);
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(defaultCue.getId());
        assertThat(state.getPendingCue()).isNotNull();
        assertThat(state.getPendingCue().getId()).isEqualTo(sceneCue.getId());
    }

    @Test
    void confirmModeSkipsDismissedCandidate() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setDismissedCandidateCue(sceneCue);
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue()).isNull();
        assertThat(state.getPendingCue()).isNull();
    }

    @Test
    void confirmModeRePromotesAfterPriorityChanges() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setDismissedCandidateCue(sceneCue);
        campaign.setDefaultAudioCue(defaultCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.isSilence()).isTrue();
        assertThat(state.getPendingCue()).isNotNull();
        assertThat(state.getPendingCue().getId()).isEqualTo(defaultCue.getId());
        assertThat(state.getDismissedCandidateCue()).isNull();
    }

    @Test
    void silenceCountsAsPriorityChangeAfterDecline() {
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setDismissedCandidateCue(sceneCue);

        resolver.resolve(state, campaignId);
        assertThat(state.getDismissedCandidateCue()).isNull();

        campaign.setCurrentSceneId(UUID.randomUUID());
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(sceneWithCue(sceneCue)));
        resolver.resolve(state, campaignId);

        assertThat(state.getPendingCue()).isSameAs(sceneCue);
    }

    @Test
    void muteDoesNotAffectResolution() {
        state.setMuted(true);
        state.setManualOverrideCue(manualCue);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(manualCue.getId());
    }

    @Test
    void victoryWithExpiryAtExactNow() {
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now);

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.isSilence()).isTrue();
    }

    @Test
    void encounterEndFallsBackAfterVictoryExpiry() {
        state.setTemporaryVictoryCue(victoryCue);
        state.setVictoryUntil(now.minusSeconds(1));
        campaign.setCurrentSceneId(UUID.randomUUID());
        Scene scene = sceneWithCue(sceneCue);
        when(sceneRepository.findByIdAndCampaignId(campaignId, campaign.getCurrentSceneId()))
                .thenReturn(Optional.of(scene));

        ResolvedAudioCue result = resolver.resolve(state, campaignId);

        assertThat(result.cue().getId()).isEqualTo(sceneCue.getId());
    }

    private Scene sceneWithEncounter(AudioCue combatAudioCue) {
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID());
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter.setCombatAudioCue(combatAudioCue);
        encounter.setCampaign(campaign);
        scene.setEncounter(encounter);
        return scene;
    }

    private Scene sceneWithCue(AudioCue cue) {
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        scene.setSceneAudioCue(cue);
        return scene;
    }

    private static AudioCue audioCue(String name) {
        AudioCue cue = new AudioCue();
        cue.setId(UUID.randomUUID());
        cue.setName(name);
        return cue;
    }
}
