package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioRuntimeView;
import dev.hendrikhoemberg.dmhelper.audio.service.SessionAudioStateService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/audio/runtime")
public class AudioRuntimeApiController {

    private final SessionAudioStateService audioStateService;
    private final CampaignRepository campaignRepository;
    private final AudioCueRepository audioCueRepository;

    public AudioRuntimeApiController(SessionAudioStateService audioStateService,
                                      CampaignRepository campaignRepository,
                                      AudioCueRepository audioCueRepository) {
        this.audioStateService = audioStateService;
        this.campaignRepository = campaignRepository;
        this.audioCueRepository = audioCueRepository;
    }

    @GetMapping("/state")
    public ResponseEntity<AudioRuntimeView> getState(@PathVariable UUID campaignId,
                                                       @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        try {
            AudioRuntimeView view = audioStateService.getRuntimeView(sessionId, campaignId);
            return ResponseEntity.ok(view);
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(AudioRuntimeView.unavailable());
        }
    }

    @PostMapping("/mute")
    public ResponseEntity<Void> mute(@PathVariable UUID campaignId,
                                      @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        audioStateService.mute(sessionId, campaignId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unmute")
    public ResponseEntity<Void> unmute(@PathVariable UUID campaignId,
                                        @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        audioStateService.unmute(sessionId, campaignId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/override")
    public ResponseEntity<Void> setOverride(@PathVariable UUID campaignId,
                                             @RequestParam UUID sessionId,
                                             @RequestParam UUID cueId) {
        requireCampaign(campaignId);
        AudioCue cue = audioCueRepository.findById(cueId)
                .orElseThrow(() -> new NotFoundException("Cue not found: " + cueId));
        if (!cue.getCampaign().getId().equals(campaignId)) {
            return ResponseEntity.notFound().build();
        }
        audioStateService.setManualOverride(sessionId, campaignId, cue);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/override")
    public ResponseEntity<Void> clearOverride(@PathVariable UUID campaignId,
                                               @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        audioStateService.clearManualOverride(sessionId, campaignId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@PathVariable UUID campaignId,
                                         @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        audioStateService.confirm(sessionId, campaignId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID campaignId,
                                         @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        audioStateService.decline(sessionId, campaignId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/victory/expire")
    public ResponseEntity<Void> expireVictory(@PathVariable UUID campaignId,
                                               @RequestParam UUID sessionId) {
        requireCampaign(campaignId);
        audioStateService.expireVictory(sessionId, campaignId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID campaignId,
                                             @RequestParam UUID sessionId,
                                             @RequestParam String result) {
        requireCampaign(campaignId);
        audioStateService.acknowledgePlaybackResult(sessionId, campaignId, result);
        return ResponseEntity.ok().build();
    }

    private void requireCampaign(UUID campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new NotFoundException("Campaign not found: " + campaignId);
        }
    }
}
