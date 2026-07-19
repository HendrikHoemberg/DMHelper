package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueDeletionImpact;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueService;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueValidationException;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueWrite;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/audio/cues")
public class AudioCueApiController {

    private final AudioCueService service;

    public AudioCueApiController(AudioCueService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AudioCueResponse>> list(@PathVariable UUID campaignId) {
        var cues = service.listByCampaign(campaignId).stream()
                .map(AudioCueWebMapper::fromCue)
                .toList();
        return ResponseEntity.ok(cues);
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable UUID campaignId,
                                    @RequestBody AudioCueWrite write) {
        try {
            var cue = service.create(campaignId, write);
            return ResponseEntity.created(
                            URI.create("/campaigns/" + campaignId + "/audio/cues/" + cue.getId()))
                    .body(AudioCueWebMapper.fromCue(cue));
        } catch (AudioCueValidationException e) {
            return validationProblem(e);
        }
    }

    @GetMapping("/{cueId}")
    public ResponseEntity<?> get(@PathVariable UUID campaignId,
                                  @PathVariable UUID cueId) {
        var cue = service.findById(cueId);
        if (!cue.getCampaign().getId().equals(campaignId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AudioCueWebMapper.fromCue(cue));
    }

    @PutMapping("/{cueId}")
    public ResponseEntity<?> update(@PathVariable UUID campaignId,
                                    @PathVariable UUID cueId,
                                    @RequestBody AudioCueWrite write) {
        try {
            var cue = service.update(cueId, write, campaignId);
            return ResponseEntity.ok(AudioCueWebMapper.fromCue(cue));
        } catch (AudioCueValidationException e) {
            return validationProblem(e);
        }
    }

    @PostMapping("/{cueId}/clone")
    public ResponseEntity<AudioCueResponse> clone(@PathVariable UUID campaignId,
                                                   @PathVariable UUID cueId,
                                                   @RequestParam String cueKey) {
        var clone = service.cloneCue(cueId, campaignId, cueKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AudioCueWebMapper.fromCue(clone));
    }

    @DeleteMapping("/{cueId}")
    public ResponseEntity<?> delete(@PathVariable UUID campaignId,
                                    @PathVariable UUID cueId,
                                    @RequestParam(defaultValue = "false") boolean confirmed) {
        try {
            service.deleteCue(cueId, confirmed);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return conflictProblem(e.getMessage());
        }
    }

    @GetMapping("/{cueId}/deletion-impact")
    public ResponseEntity<AudioCueDeletionImpact> deletionImpact(@PathVariable UUID campaignId,
                                                                  @PathVariable UUID cueId) {
        return ResponseEntity.ok(service.computeDeletionImpact(cueId));
    }

    private ResponseEntity<ProblemDetail> validationProblem(AudioCueValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("problems", e.problems());
        return ResponseEntity.badRequest().body(problem);
    }

    private static ResponseEntity<ProblemDetail> conflictProblem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                detail != null && !detail.isBlank()
                        ? detail
                        : "This cue still has dependents; set confirmed=true to proceed");
        problem.setTitle("Conflict");
        problem.setType(URI.create("urn:dmhelper:conflict"));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
