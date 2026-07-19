package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueAssignmentService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/audio")
public class AudioCueAssignmentApiController {

    private final AudioCueAssignmentService assignmentService;
    private final CampaignRepository campaignRepository;
    private final CampaignSettingsCodec settingsCodec;

    public AudioCueAssignmentApiController(AudioCueAssignmentService assignmentService,
                                           CampaignRepository campaignRepository,
                                           CampaignSettingsCodec settingsCodec) {
        this.assignmentService = assignmentService;
        this.campaignRepository = campaignRepository;
        this.settingsCodec = settingsCodec;
    }

    @PutMapping("/assignments/campaign")
    public ResponseEntity<?> assignCampaignCue(@PathVariable UUID campaignId,
                                                @RequestParam(required = false) UUID cueId) {
        try {
            assignmentService.assignCampaignCue(campaignId, cueId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/assignments/scenes/{sceneId}")
    public ResponseEntity<?> assignSceneCue(@PathVariable UUID campaignId,
                                             @PathVariable UUID sceneId,
                                             @RequestParam(required = false) UUID cueId) {
        try {
            assignmentService.assignSceneCue(sceneId, cueId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/assignments/encounters/{encounterId}")
    public ResponseEntity<?> assignEncounterCue(@PathVariable UUID campaignId,
                                                 @PathVariable UUID encounterId,
                                                 @RequestParam(required = false) UUID cueId,
                                                 @RequestParam(defaultValue = "combat") String role,
                                                 @RequestParam(required = false) Integer durationSeconds) {
        try {
            switch (role) {
                case "combat" -> assignmentService.assignEncounterCombatCue(encounterId, cueId);
                case "victory" -> assignmentService.assignEncounterVictoryCue(encounterId, cueId, durationSeconds);
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + role));
                }
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/assignments/locations/{locationId}")
    public ResponseEntity<?> assignLocationCue(@PathVariable UUID campaignId,
                                                @PathVariable UUID locationId,
                                                @RequestParam(required = false) UUID cueId) {
        try {
            assignmentService.assignLocationCue(locationId, cueId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateAudioSettings(@PathVariable UUID campaignId,
                                                  @RequestBody Map<String, String> body) {
        try {
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
            CampaignSettings current = settingsCodec.read(campaign);
            String modeStr = body.get("audioSwitchMode");
            AudioSwitchMode mode = modeStr != null ? AudioSwitchMode.valueOf(modeStr.toUpperCase()) : current.audioSwitchMode();
            CampaignSettings updated = new CampaignSettings(
                    current.levelingMode(), current.calendar(), current.currentDate(), mode);
            settingsCodec.write(campaign, updated);
            campaignRepository.save(campaign);
            return ResponseEntity.ok(Map.of("success", true, "audioSwitchMode", mode.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
