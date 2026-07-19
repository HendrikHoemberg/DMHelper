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
        if (campaign == null) return silence();

        if (state.getManualOverrideCue() != null) {
            return resolved(state.getManualOverrideCue(), new AudioCueSource(
                    AudioCueSource.SourceKind.MANUAL_OVERRIDE,
                    state.getManualOverrideCue().getId(), "Manual override"));
        }

        if (state.getTemporaryVictoryCue() != null) {
            if (state.getVictoryUntil() != null && state.getVictoryUntil().isAfter(clock.instant())) {
                return applySwitchMode(state, priority(state.getTemporaryVictoryCue(),
                        AudioCueSource.SourceKind.VICTORY, state.getVictorySourceId(),
                        state.getVictorySourceLabel() != null
                                ? state.getVictorySourceLabel() : "Encounter victory"));
            }
            clearVictory(state);
        }

        return applySwitchMode(state, resolvePriority(campaign));
    }

    private ResolvedPriority resolvePriority(Campaign campaign) {
        Optional<Encounter> activeEncounter = encounterRepository.findByCampaignIdAndStatus(
                campaign.getId(), Encounter.Status.ACTIVE);
        if (activeEncounter.isPresent() && activeEncounter.get().getCombatAudioCue() != null) {
            Encounter encounter = activeEncounter.get();
            return priority(encounter.getCombatAudioCue(), AudioCueSource.SourceKind.COMBAT,
                    encounter.getId(), "Encounter: " + encounter.getName());
        }

        UUID sceneId = campaign.getCurrentSceneId();
        if (sceneId != null) {
            Scene scene = sceneRepository.findByIdAndCampaignId(campaign.getId(), sceneId).orElse(null);
            if (scene != null) {
                // Backward-compatible fallback for campaigns whose active encounter is attached
                // directly to the current scene but is not returned by the campaign query.
                Encounter attached = scene.getEncounter();
                if (activeEncounter.isEmpty() && attached != null
                        && attached.getStatus() == Encounter.Status.ACTIVE
                        && attached.getCombatAudioCue() != null) {
                    return priority(attached.getCombatAudioCue(), AudioCueSource.SourceKind.COMBAT,
                            attached.getId(), "Encounter: " + attached.getName());
                }
                if (scene.getSceneAudioCue() != null) {
                    return priority(scene.getSceneAudioCue(), AudioCueSource.SourceKind.SCENE,
                            scene.getId(), "Scene: " + scene.getTitle());
                }
                Optional<AudioCue> locationCue = sceneLocationResolver.resolve(sceneId, campaign.getId());
                if (locationCue.isPresent()) {
                    AudioCueSource source = sceneLocationResolver.sourceFor(
                                    sceneId, campaign.getId(), locationCue.get().getId())
                            .orElse(new AudioCueSource(AudioCueSource.SourceKind.LOCATION,
                                    null, "Scene location"));
                    return new ResolvedPriority(locationCue.get(), source);
                }
            }
        }

        if (campaign.getDefaultAudioCue() != null) {
            return priority(campaign.getDefaultAudioCue(), AudioCueSource.SourceKind.CAMPAIGN_DEFAULT,
                    campaign.getId(), "Campaign: " + campaign.getName());
        }
        return new ResolvedPriority(null, new AudioCueSource(
                AudioCueSource.SourceKind.CAMPAIGN_DEFAULT, campaign.getId(),
                "Campaign: " + campaign.getName()));
    }

    private ResolvedAudioCue applySwitchMode(SessionAudioState state, ResolvedPriority priority) {
        AudioCue candidate = priority.cue();
        if (state.getSwitchMode() == AudioSwitchMode.CONFIRM) {
            AudioCue accepted = state.getAcceptedAutomaticCue();
            AudioCue dismissed = state.getDismissedCandidateCue();

            if (candidate == null) {
                clearPending(state);
                state.setDismissedCandidateCue(null);
                return accepted != null ? resolved(accepted, acceptedSource(state)) : silence();
            }
            if (accepted != null && Objects.equals(accepted.getId(), candidate.getId())) {
                clearPending(state);
                state.setDismissedCandidateCue(null);
                storeAcceptedSource(state, priority.source());
                return resolved(accepted, priority.source());
            }
            if (dismissed != null && Objects.equals(dismissed.getId(), candidate.getId())) {
                clearPending(state);
                return accepted != null ? resolved(accepted, acceptedSource(state)) : silence();
            }

            state.setPendingCue(candidate);
            storePendingSource(state, priority.source());
            state.setDismissedCandidateCue(null);
            return accepted != null ? resolved(accepted, acceptedSource(state)) : silence();
        }

        state.setAcceptedAutomaticCue(candidate);
        if (candidate != null) storeAcceptedSource(state, priority.source());
        else clearAcceptedSource(state);
        clearPending(state);
        state.setDismissedCandidateCue(null);
        return candidate != null ? resolved(candidate, priority.source()) : silence();
    }

    private static ResolvedPriority priority(AudioCue cue, AudioCueSource.SourceKind kind,
                                             UUID sourceId, String label) {
        return new ResolvedPriority(cue, new AudioCueSource(kind, sourceId, label));
    }

    private static ResolvedAudioCue resolved(AudioCue cue, AudioCueSource source) {
        return new ResolvedAudioCue(cue, source);
    }

    private static ResolvedAudioCue silence() {
        return new ResolvedAudioCue(null, new AudioCueSource(
                AudioCueSource.SourceKind.CAMPAIGN_DEFAULT, null, null));
    }

    private static void storeAcceptedSource(SessionAudioState state, AudioCueSource source) {
        state.setAcceptedSourceKind(source.kind().name());
        state.setAcceptedSourceId(source.sourceId());
        state.setAcceptedSourceLabel(source.sourceLabel());
    }

    private static void storePendingSource(SessionAudioState state, AudioCueSource source) {
        state.setPendingSourceKind(source.kind().name());
        state.setPendingSourceId(source.sourceId());
        state.setPendingSourceLabel(source.sourceLabel());
    }

    private static AudioCueSource acceptedSource(SessionAudioState state) {
        AudioCueSource.SourceKind kind = parseKind(state.getAcceptedSourceKind());
        return new AudioCueSource(kind, state.getAcceptedSourceId(), state.getAcceptedSourceLabel());
    }

    private static AudioCueSource.SourceKind parseKind(String value) {
        if (value == null) return AudioCueSource.SourceKind.CAMPAIGN_DEFAULT;
        try {
            return AudioCueSource.SourceKind.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AudioCueSource.SourceKind.CAMPAIGN_DEFAULT;
        }
    }

    static void clearPending(SessionAudioState state) {
        state.setPendingCue(null);
        state.setPendingSourceKind(null);
        state.setPendingSourceId(null);
        state.setPendingSourceLabel(null);
    }

    static void clearAcceptedSource(SessionAudioState state) {
        state.setAcceptedSourceKind(null);
        state.setAcceptedSourceId(null);
        state.setAcceptedSourceLabel(null);
    }

    static void clearVictory(SessionAudioState state) {
        state.setTemporaryVictoryCue(null);
        state.setVictoryUntil(null);
        state.setVictorySourceId(null);
        state.setVictorySourceLabel(null);
    }

    private record ResolvedPriority(AudioCue cue, AudioCueSource source) {}
}
