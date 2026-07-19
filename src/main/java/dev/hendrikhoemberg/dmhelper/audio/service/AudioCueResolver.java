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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AudioCueResolver {

    private final Clock clock;
    private final SceneLocationResolver sceneLocationResolver;
    private final SceneRepository sceneRepository;
    private final CampaignRepository campaignRepository;
    private final EncounterRepository encounterRepository;

    public AudioCueResolver(Clock clock,
                             SceneLocationResolver sceneLocationResolver,
                             SceneRepository sceneRepository,
                             CampaignRepository campaignRepository,
                             EncounterRepository encounterRepository) {
        this.clock = clock;
        this.sceneLocationResolver = sceneLocationResolver;
        this.sceneRepository = sceneRepository;
        this.campaignRepository = campaignRepository;
        this.encounterRepository = encounterRepository;
    }

    public ResolvedAudioCue resolve(SessionAudioState state, UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            return silence();
        }

        AudioCue manualOverride = state.getManualOverrideCue();
        if (manualOverride != null) {
            return resolveManual(state, manualOverride);
        }

        AudioCue victoryCue = resolveVictory(state);
        if (victoryCue != null) {
            return resolved(victoryCue, AudioCueSource.SourceKind.VICTORY);
        }

        ResolvedPriority priority = resolvePriority(campaign, state);
        return applySwitchMode(state, priority);
    }

    private ResolvedAudioCue resolveManual(SessionAudioState state, AudioCue cue) {
        if (state.getSwitchMode() == AudioSwitchMode.AUTOMATIC) {
            state.setAcceptedAutomaticCue(null);
            state.setPendingCue(null);
            state.setDismissedCandidateCue(null);
        }
        return resolved(cue, AudioCueSource.SourceKind.MANUAL_OVERRIDE);
    }

    private AudioCue resolveVictory(SessionAudioState state) {
        AudioCue victoryCue = state.getTemporaryVictoryCue();
        if (victoryCue == null) {
            return null;
        }
        Instant victoryUntil = state.getVictoryUntil();
        if (victoryUntil != null && victoryUntil.isAfter(clock.instant())) {
            return victoryCue;
        }
        return null;
    }

    private ResolvedPriority resolvePriority(Campaign campaign, SessionAudioState state) {
        UUID sceneId = campaign.getCurrentSceneId();

        if (sceneId != null) {
            Scene scene = sceneRepository.findByIdAndCampaignId(campaign.getId(), sceneId).orElse(null);

            if (scene != null) {
                AudioCue combatCue = resolveCombatCue(scene);
                if (combatCue != null) {
                    return new ResolvedPriority(combatCue, AudioCueSource.SourceKind.COMBAT);
                }

                AudioCue sceneCue = scene.getSceneAudioCue();
                if (sceneCue != null) {
                    return new ResolvedPriority(sceneCue, AudioCueSource.SourceKind.SCENE);
                }

                Optional<AudioCue> locationCue = sceneLocationResolver.resolve(sceneId, campaign.getId());
                if (locationCue.isPresent()) {
                    return new ResolvedPriority(locationCue.get(), AudioCueSource.SourceKind.LOCATION);
                }
            }
        }

        AudioCue defaultCue = campaign.getDefaultAudioCue();
        if (defaultCue != null) {
            return new ResolvedPriority(defaultCue, AudioCueSource.SourceKind.CAMPAIGN_DEFAULT);
        }

        return new ResolvedPriority(null, AudioCueSource.SourceKind.CAMPAIGN_DEFAULT);
    }

    private AudioCue resolveCombatCue(Scene scene) {
        Encounter encounter = scene.getEncounter();
        if (encounter != null && encounter.getStatus() == Encounter.Status.ACTIVE) {
            return encounter.getCombatAudioCue();
        }
        return null;
    }

    private ResolvedAudioCue applySwitchMode(SessionAudioState state, ResolvedPriority priority) {
        AudioCue candidate = priority.cue();
        AudioSwitchMode mode = state.getSwitchMode();

        if (mode == AudioSwitchMode.CONFIRM) {
            AudioCue accepted = state.getAcceptedAutomaticCue();
            AudioCue dismissed = state.getDismissedCandidateCue();

            if (candidate == null) {
                return accepted != null ? resolved(accepted, AudioCueSource.SourceKind.CAMPAIGN_DEFAULT) : silence();
            }

            if (accepted != null && Objects.equals(accepted.getId(), candidate.getId())) {
                state.setPendingCue(null);
                return resolved(accepted, AudioCueSource.SourceKind.CAMPAIGN_DEFAULT);
            }

            if (dismissed != null && Objects.equals(dismissed.getId(), candidate.getId())) {
                state.setPendingCue(null);
                return accepted != null ? resolved(accepted, AudioCueSource.SourceKind.CAMPAIGN_DEFAULT) : silence();
            }

            state.setPendingCue(candidate);
            state.setDismissedCandidateCue(null);
            if (accepted != null) {
                return resolved(accepted, AudioCueSource.SourceKind.CAMPAIGN_DEFAULT);
            }
            return silence();
        }

        // AUTOMATIC mode
        state.setAcceptedAutomaticCue(candidate);
        state.setPendingCue(null);
        state.setDismissedCandidateCue(null);

        if (candidate != null) {
            return resolved(candidate, priority.sourceKind());
        }
        return silence();
    }

    private ResolvedAudioCue resolved(AudioCue cue, AudioCueSource.SourceKind kind) {
        return new ResolvedAudioCue(cue, new AudioCueSource(kind, null, null));
    }

    private ResolvedAudioCue silence() {
        return new ResolvedAudioCue(null, new AudioCueSource(
                AudioCueSource.SourceKind.CAMPAIGN_DEFAULT, null, null));
    }

    private record ResolvedPriority(AudioCue cue, AudioCueSource.SourceKind sourceKind) {
    }
}
