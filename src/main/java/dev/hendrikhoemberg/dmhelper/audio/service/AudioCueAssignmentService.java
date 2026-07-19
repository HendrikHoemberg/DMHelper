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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AudioCueAssignmentService {

    private static final int VICTORY_DURATION_MIN = 5;
    private static final int VICTORY_DURATION_MAX = 600;

    private final CampaignRepository campaignRepository;
    private final SceneRepository sceneRepository;
    private final EncounterRepository encounterRepository;
    private final WorldLocationRepository locationRepository;
    private final AudioCueRepository audioCueRepository;

    public AudioCueAssignmentService(CampaignRepository campaignRepository,
                                     SceneRepository sceneRepository,
                                     EncounterRepository encounterRepository,
                                     WorldLocationRepository locationRepository,
                                     AudioCueRepository audioCueRepository) {
        this.campaignRepository = campaignRepository;
        this.sceneRepository = sceneRepository;
        this.encounterRepository = encounterRepository;
        this.locationRepository = locationRepository;
        this.audioCueRepository = audioCueRepository;
    }

    public void assignCampaignCue(UUID campaignId, UUID cueId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
        AudioCue cue = cueId != null ? findCampaignCue(campaignId, cueId) : null;
        campaign.setDefaultAudioCue(cue);
        campaignRepository.save(campaign);
    }

    public void assignSceneCue(UUID sceneId, UUID cueId) {
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found: " + sceneId));
        AudioCue cue = cueId != null ? findCueById(cueId) : null;
        UUID campaignId = scene.getChapter().getAdventure().getCampaign().getId();
        if (cue != null && !cue.getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("Cue does not belong to the same campaign");
        }
        scene.setSceneAudioCue(cue);
        sceneRepository.save(scene);
    }

    public void assignSceneCue(UUID campaignId, UUID sceneId, UUID cueId) {
        Scene scene = sceneRepository.findByIdAndCampaignId(campaignId, sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found in campaign"));
        AudioCue cue = cueId != null ? findCampaignCue(campaignId, cueId) : null;
        scene.setSceneAudioCue(cue);
        sceneRepository.save(scene);
    }

    public void assignEncounterCombatCue(UUID encounterId, UUID cueId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        AudioCue cue = cueId != null ? findCueById(cueId) : null;
        if (cue != null && !cue.getCampaign().getId().equals(encounter.getCampaign().getId())) {
            throw new IllegalArgumentException("Cue does not belong to the same campaign");
        }
        encounter.setCombatAudioCue(cue);
        encounterRepository.save(encounter);
    }

    public void assignEncounterCombatCue(UUID campaignId, UUID encounterId, UUID cueId) {
        Encounter encounter = findCampaignEncounter(campaignId, encounterId);
        encounter.setCombatAudioCue(cueId != null ? findCampaignCue(campaignId, cueId) : null);
        encounterRepository.save(encounter);
    }

    public void assignEncounterVictoryCue(UUID encounterId, UUID cueId, Integer durationSeconds) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        AudioCue cue = cueId != null ? findCueById(cueId) : null;
        if (cue != null && !cue.getCampaign().getId().equals(encounter.getCampaign().getId())) {
            throw new IllegalArgumentException("Cue does not belong to the same campaign");
        }
        if (cue != null && durationSeconds != null) {
            if (durationSeconds < VICTORY_DURATION_MIN || durationSeconds > VICTORY_DURATION_MAX) {
                throw new IllegalArgumentException(
                        "Victory cue duration must be between " + VICTORY_DURATION_MIN
                                + " and " + VICTORY_DURATION_MAX + " seconds");
            }
        }
        encounter.setVictoryAudioCue(cue);
        encounter.setVictoryCueDurationSeconds(cue != null ? durationSeconds : null);
        encounterRepository.save(encounter);
    }

    public void assignEncounterVictoryCue(UUID campaignId, UUID encounterId, UUID cueId,
                                          Integer durationSeconds) {
        Encounter encounter = findCampaignEncounter(campaignId, encounterId);
        AudioCue cue = cueId != null ? findCampaignCue(campaignId, cueId) : null;
        validateVictoryDuration(cue, durationSeconds);
        encounter.setVictoryAudioCue(cue);
        encounter.setVictoryCueDurationSeconds(cue != null ? durationSeconds : null);
        encounterRepository.save(encounter);
    }

    public void assignLocationCue(UUID locationId, UUID cueId) {
        WorldLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
        AudioCue cue = cueId != null ? findCueById(cueId) : null;
        if (cue != null && !cue.getCampaign().getId().equals(location.getCampaign().getId())) {
            throw new IllegalArgumentException("Cue does not belong to the same campaign");
        }
        location.setLocationAudioCue(cue);
        locationRepository.save(location);
    }

    public void assignLocationCue(UUID campaignId, UUID locationId, UUID cueId) {
        WorldLocation location = locationRepository.findByIdAndCampaignId(locationId, campaignId)
                .orElseThrow(() -> new NotFoundException("Location not found in campaign"));
        location.setLocationAudioCue(cueId != null ? findCampaignCue(campaignId, cueId) : null);
        locationRepository.save(location);
    }

    private Encounter findCampaignEncounter(UUID campaignId, UUID encounterId) {
        return encounterRepository.findByIdAndCampaignId(encounterId, campaignId)
                .orElseThrow(() -> new NotFoundException("Encounter not found in campaign"));
    }

    private AudioCue findCampaignCue(UUID campaignId, UUID cueId) {
        return audioCueRepository.findById(cueId)
                .filter(cue -> campaignId.equals(cue.getCampaign().getId()))
                .orElseThrow(() -> new NotFoundException("Audio cue not found in campaign"));
    }

    private void validateVictoryDuration(AudioCue cue, Integer durationSeconds) {
        if (cue == null) {
            if (durationSeconds != null) {
                throw new IllegalArgumentException("Victory duration requires a victory cue");
            }
            return;
        }
        if (durationSeconds != null && (durationSeconds < VICTORY_DURATION_MIN
                || durationSeconds > VICTORY_DURATION_MAX)) {
            throw new IllegalArgumentException(
                    "Victory cue duration must be between " + VICTORY_DURATION_MIN
                            + " and " + VICTORY_DURATION_MAX + " seconds");
        }
    }

    private AudioCue findCueById(UUID cueId) {
        return audioCueRepository.findById(cueId)
                .orElseThrow(() -> new NotFoundException("Audio cue not found: " + cueId));
    }
}
