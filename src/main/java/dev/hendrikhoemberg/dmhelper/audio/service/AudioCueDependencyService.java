package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AudioCueDependencyService {

    public static final String DEP_KIND_CAMPAIGN = "CAMPAIGN_DEFAULT";
    public static final String DEP_KIND_SCENE = "SCENE";
    public static final String DEP_KIND_ENCOUNTER_COMBAT = "ENCOUNTER_COMBAT";
    public static final String DEP_KIND_ENCOUNTER_VICTORY = "ENCOUNTER_VICTORY";
    public static final String DEP_KIND_LOCATION = "LOCATION";
    public static final String DEP_KIND_SESSION_OVERRIDE = "SESSION_OVERRIDE";
    public static final String DEP_KIND_SESSION_ACCEPTED = "SESSION_ACCEPTED";
    public static final String DEP_KIND_SESSION_PENDING = "SESSION_PENDING";
    public static final String DEP_KIND_SESSION_DISMISSED = "SESSION_DISMISSED";
    public static final String DEP_KIND_SESSION_VICTORY = "SESSION_VICTORY";

    private final SessionAudioStateRepository sessionAudioStateRepository;
    private final CampaignRepository campaignRepository;
    private final SceneRepository sceneRepository;
    private final EncounterRepository encounterRepository;
    private final WorldLocationRepository locationRepository;

    public AudioCueDependencyService(SessionAudioStateRepository sessionAudioStateRepository,
                                     CampaignRepository campaignRepository,
                                     SceneRepository sceneRepository,
                                     EncounterRepository encounterRepository,
                                     WorldLocationRepository locationRepository) {
        this.sessionAudioStateRepository = sessionAudioStateRepository;
        this.campaignRepository = campaignRepository;
        this.sceneRepository = sceneRepository;
        this.encounterRepository = encounterRepository;
        this.locationRepository = locationRepository;
    }

    public AudioCueDeletionImpact computeDeletionImpact(AudioCue cue) {
        List<AudioCueDependency> deps = new ArrayList<>();
        UUID cueId = cue.getId();

        campaignRepository.findAll().stream()
                .filter(campaign -> campaign.getDefaultAudioCue() != null
                        && cueId.equals(campaign.getDefaultAudioCue().getId()))
                .forEach(campaign -> deps.add(new AudioCueDependency(DEP_KIND_CAMPAIGN,
                        campaign.getId(), "Campaign default: " + campaign.getName(),
                        "/campaigns/" + campaign.getId())));
        sceneRepository.findBySceneAudioCueId(cueId).forEach(scene ->
                deps.add(new AudioCueDependency(DEP_KIND_SCENE, scene.getId(),
                        "Scene: " + scene.getTitle(), "/campaigns/"
                        + scene.getChapter().getAdventure().getCampaign().getId()
                        + "/adventures/" + scene.getChapter().getAdventure().getId()
                        + "/scenes/" + scene.getId())));
        encounterRepository.findByCombatAudioCueId(cueId).forEach(encounter ->
                deps.add(new AudioCueDependency(DEP_KIND_ENCOUNTER_COMBAT, encounter.getId(),
                        "Encounter combat: " + encounter.getName(),
                        "/encounters/" + encounter.getId())));
        encounterRepository.findByVictoryAudioCueId(cueId).forEach(encounter ->
                deps.add(new AudioCueDependency(DEP_KIND_ENCOUNTER_VICTORY, encounter.getId(),
                        "Encounter victory: " + encounter.getName(),
                        "/encounters/" + encounter.getId())));
        locationRepository.findByLocationAudioCueId(cueId).forEach(location ->
                deps.add(new AudioCueDependency(DEP_KIND_LOCATION, location.getId(),
                        "Location: " + location.getName(),
                        "/campaigns/" + location.getCampaign().getId() + "/world/locations/"
                                + location.getId())));

        for (var state : sessionAudioStateRepository.findByManualOverrideCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_OVERRIDE, state.getSession().getId(),
                    "Session manual override", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByAcceptedAutomaticCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_ACCEPTED, state.getSession().getId(),
                    "Session accepted cue", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByPendingCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_PENDING, state.getSession().getId(),
                    "Session pending cue", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByDismissedCandidateCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_DISMISSED, state.getSession().getId(),
                    "Session dismissed cue", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByTemporaryVictoryCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_VICTORY, state.getSession().getId(),
                    "Session victory cue", "/sessions/" + state.getSession().getId()));
        }

        deps.sort(Comparator.comparing(AudioCueDependency::kind)
                .thenComparing(AudioCueDependency::label)
                .thenComparing(AudioCueDependency::dependentId));
        return new AudioCueDeletionImpact(cue.getId(), cue.getName(), List.copyOf(deps));
    }

    @Transactional
    public void clearDependencies(AudioCue cue) {
        UUID cueId = cue.getId();
        var campaigns = campaignRepository.findAll().stream()
                .filter(campaign -> campaign.getDefaultAudioCue() != null
                        && cueId.equals(campaign.getDefaultAudioCue().getId()))
                .toList();
        campaigns.forEach(campaign -> campaign.setDefaultAudioCue(null));
        campaignRepository.saveAll(campaigns);

        var scenes = sceneRepository.findBySceneAudioCueId(cueId);
        scenes.forEach(scene -> scene.setSceneAudioCue(null));
        sceneRepository.saveAll(scenes);

        var combatEncounters = encounterRepository.findByCombatAudioCueId(cueId);
        combatEncounters.forEach(encounter -> encounter.setCombatAudioCue(null));
        encounterRepository.saveAll(combatEncounters);

        var victoryEncounters = encounterRepository.findByVictoryAudioCueId(cueId);
        victoryEncounters.forEach(encounter -> {
            encounter.setVictoryAudioCue(null);
            encounter.setVictoryCueDurationSeconds(null);
        });
        encounterRepository.saveAll(victoryEncounters);

        var locations = locationRepository.findByLocationAudioCueId(cueId);
        locations.forEach(location -> location.setLocationAudioCue(null));
        locationRepository.saveAll(locations);

        LinkedHashSet<SessionAudioState> states = new LinkedHashSet<>();
        sessionAudioStateRepository.findByManualOverrideCueId(cueId)
                .forEach(states::add);
        sessionAudioStateRepository.findByAcceptedAutomaticCueId(cueId)
                .forEach(states::add);
        sessionAudioStateRepository.findByPendingCueId(cueId)
                .forEach(states::add);
        sessionAudioStateRepository.findByDismissedCandidateCueId(cueId)
                .forEach(states::add);
        sessionAudioStateRepository.findByTemporaryVictoryCueId(cueId)
                .forEach(states::add);
        states.forEach(state -> {
            if (sameCue(state.getManualOverrideCue(), cueId)) state.setManualOverrideCue(null);
            if (sameCue(state.getAcceptedAutomaticCue(), cueId)) {
                state.setAcceptedAutomaticCue(null);
                AudioCueResolver.clearAcceptedSource(state);
            }
            if (sameCue(state.getPendingCue(), cueId)) AudioCueResolver.clearPending(state);
            if (sameCue(state.getDismissedCandidateCue(), cueId)) state.setDismissedCandidateCue(null);
            if (sameCue(state.getTemporaryVictoryCue(), cueId)) AudioCueResolver.clearVictory(state);
        });
        sessionAudioStateRepository.saveAll(states);
    }

    private static boolean sameCue(AudioCue candidate, UUID cueId) {
        return candidate != null && cueId.equals(candidate.getId());
    }
}
