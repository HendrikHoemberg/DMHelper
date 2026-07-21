package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.LiveTableState;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CampaignTableControllerTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

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
}
