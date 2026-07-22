package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.LiveTableState;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignTableControllerTest {

    @Test
    void refreshAndAoeUpdatesRetainCampaignScope() {
        TablePresentationService service = mock(TablePresentationService.class);
        CampaignTableController controller = new CampaignTableController(service);
        UUID campaignId = UUID.randomUUID();
        List<LiveTableState.AoeTemplateSnapshot> aoes = List.of();
        when(service.broadcastCurrentState(campaignId)).thenReturn(LiveTableState.curtain());

        controller.refresh(campaignId);
        controller.updateAoEs(campaignId, aoes);

        verify(service).updateAoEs(campaignId, aoes);
        verify(service, org.mockito.Mockito.times(2)).broadcastCurrentState(campaignId);
    }

    @Test
    void previewEndpointDelegatesToService() {
        TablePresentationService service = mock(TablePresentationService.class);
        CampaignTableController controller = new CampaignTableController(service);
        UUID campaignId = UUID.randomUUID();
        UUID handoutId = UUID.randomUUID();
        var preview = new TablePresentationService.HandoutPreview(
                LiveTableState.curtain(), "DM_SOURCE", true);
        when(service.previewHandout(campaignId, handoutId)).thenReturn(preview);

        var result = controller.previewHandout(campaignId, handoutId);

        verify(service).previewHandout(campaignId, handoutId);
        assert result.state().mode().equals("CURTAIN");
        assert result.classification().equals("DM_SOURCE");
        assert result.requiresOverride();
    }
}