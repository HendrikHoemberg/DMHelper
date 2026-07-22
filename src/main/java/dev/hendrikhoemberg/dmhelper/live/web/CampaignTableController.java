package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.LiveTableState;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/table")
public class CampaignTableController {

    private final TablePresentationService presentationService;

    public CampaignTableController(TablePresentationService presentationService) {
        this.presentationService = presentationService;
    }

    @PutMapping("/presentation")
    public LiveTableState setPresentation(@PathVariable UUID campaignId,
                                           @RequestBody PresentationRequest request) {
        return switch (request.mode()) {
            case "MAP" -> presentationService.presentMap(campaignId, UUID.fromString(request.ref()));
            case "HANDOUT" -> presentationService.presentHandout(campaignId, UUID.fromString(request.ref()),
                    request.emergencyOverride != null && request.emergencyOverride,
                    request.acknowledgement);
            case "CURTAIN" -> presentationService.curtain(campaignId);
            default -> throw new IllegalArgumentException("Unknown presentation mode: " + request.mode());
        };
    }

    @GetMapping("/handouts/{id}/preview")
    public TablePresentationService.HandoutPreview previewHandout(@PathVariable UUID campaignId,
                                                                   @PathVariable UUID id) {
        return presentationService.previewHandout(campaignId, id);
    }

    @PostMapping("/refresh")
    public LiveTableState refresh(@PathVariable UUID campaignId) {
        return presentationService.broadcastCurrentState(campaignId);
    }

    @PostMapping("/aoes")
    public LiveTableState updateAoEs(@PathVariable UUID campaignId,
                                      @RequestBody List<LiveTableState.AoeTemplateSnapshot> aoes) {
        presentationService.updateAoEs(campaignId, aoes);
        return presentationService.broadcastCurrentState(campaignId);
    }

    public record PresentationRequest(String mode, String ref, Boolean emergencyOverride, String acknowledgement) {}
}