package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.handout.web.FileServeController;
import dev.hendrikhoemberg.dmhelper.live.web.CampaignTableController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandoutPreviewParityTest {

    @Test
    void previewAndPlayerEndpointsReturnSameBytes() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID handoutId = UUID.randomUUID();
        byte[] fileBytes = new byte[]{1, 2, 3, 4, 5};

        Handout handout = new Handout();
        handout.setId(handoutId);
        handout.setContentType("image/png");
        handout.setDmOnly(false);
        handout.setSafetyClassification(Handout.SafetyClassification.PLAYER_SAFE);

        TablePresentationService tablePresentationService = mock(TablePresentationService.class);
        HandoutService handoutService = mock(HandoutService.class);

        var preview = new TablePresentationService.HandoutPreview(
                LiveTableState.full("HANDOUT", null,
                        new LiveTableState.HandoutRef(handoutId.toString(), "Test", "image/png",
                                "/api/v1/campaigns/" + campaignId + "/table/handouts/" + handoutId + "/preview-file"),
                        null, null),
                "PLAYER_SAFE", false);

        when(tablePresentationService.previewHandout(campaignId, handoutId)).thenReturn(preview);
        when(tablePresentationService.isCurrentlyPresentedHandout(handoutId)).thenReturn(true);
        when(handoutService.findById(handoutId)).thenReturn(handout);
        when(handoutService.getFileContent(handoutId)).thenReturn(fileBytes);

        FileServeController fileController = new FileServeController(handoutService, tablePresentationService);
        CampaignTableController campaignController = new CampaignTableController(tablePresentationService);

        var previewResponse = fileController.servePreviewFile(campaignId, handoutId);
        assert previewResponse.getStatusCode().is2xxSuccessful();
        assert previewResponse.getBody() != null;
        assert previewResponse.getBody().length == fileBytes.length;
        assert previewResponse.getHeaders().get(HttpHeaders.CACHE_CONTROL).stream()
                .anyMatch(v -> v.contains("no-store"));

        campaignController.setPresentation(campaignId,
                new CampaignTableController.PresentationRequest("HANDOUT", handoutId.toString(), null, null));
        var playerResponse = fileController.servePlayerFile(handoutId);
        assert playerResponse.getStatusCode().is2xxSuccessful();
        assert playerResponse.getBody() != null;
        assert playerResponse.getBody().length == fileBytes.length;
        assert playerResponse.getHeaders().get(HttpHeaders.CACHE_CONTROL).stream()
                .anyMatch(v -> v.contains("no-store"));

        assert previewResponse.getBody().length == playerResponse.getBody().length;
        for (int i = 0; i < fileBytes.length; i++) {
            assert previewResponse.getBody()[i] == playerResponse.getBody()[i];
        }
    }
}