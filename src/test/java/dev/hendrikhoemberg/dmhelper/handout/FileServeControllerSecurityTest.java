package dev.hendrikhoemberg.dmhelper.handout;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.handout.web.FileServeController;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(FileServeController.class)
class FileServeControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HandoutService handoutService;
    @MockitoBean private TablePresentationService tablePresentationService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private UUID handoutId;
    private Handout unpublished;

    @BeforeEach
    void setUp() {
        handoutId = UUID.randomUUID();
        unpublished = new Handout();
        unpublished.setId(handoutId);
        unpublished.setTitle("Secret Letter");
        unpublished.setFileName("letter.png");
        unpublished.setContentType("image/png");
        unpublished.setPresented(false);
        unpublished.setDmOnly(true);
        unpublished.setSafetyClassification(Handout.SafetyClassification.DM_SOURCE);
    }

    @Test
    void playerFileEndpointReturns404ForUnpresentedHandout() throws Exception {
        when(handoutService.findById(handoutId)).thenReturn(unpublished);

        mockMvc.perform(get("/player/files/" + handoutId))
                .andExpect(status().isNotFound());
    }

    @Test
    void playerFileEndpointReturns200ForPresentedHandout() throws Exception {
        unpublished.setDmOnly(false);
        when(handoutService.findById(handoutId)).thenReturn(unpublished);
        when(handoutService.getFileContent(handoutId)).thenReturn(new byte[]{1,2,3});
        when(tablePresentationService.isCurrentlyPresentedHandout(handoutId)).thenReturn(true);

        mockMvc.perform(get("/player/files/" + handoutId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void playerFileEndpointServesAnAuthorizedEmergencyOverrideDespiteLegacyDmOnlyFlag() throws Exception {
        when(handoutService.findById(handoutId)).thenReturn(unpublished);
        when(handoutService.getFileContent(handoutId)).thenReturn(new byte[]{1,2,3});
        when(tablePresentationService.isCurrentlyPresentedHandout(handoutId)).thenReturn(true);

        mockMvc.perform(get("/player/files/" + handoutId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void getFileEndpointReturns200ForExistingHandout() throws Exception {
        when(handoutService.findById(handoutId)).thenReturn(unpublished);
        when(handoutService.getFileContent(handoutId)).thenReturn(new byte[]{1,2,3});

        mockMvc.perform(get("/files/" + handoutId))
                .andExpect(status().isOk());
    }

    @Test
    void previewFileEndpointReturnsFileWithNoStore() throws Exception {
        UUID campaignId = UUID.randomUUID();
        var preview = new dev.hendrikhoemberg.dmhelper.live.TablePresentationService.HandoutPreview(
                dev.hendrikhoemberg.dmhelper.live.LiveTableState.curtain(), "DM_SOURCE", true);
        when(tablePresentationService.previewHandout(campaignId, handoutId)).thenReturn(preview);
        when(handoutService.findById(handoutId)).thenReturn(unpublished);
        when(handoutService.getFileContent(handoutId)).thenReturn(new byte[]{1,2,3});

        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/table/handouts/" + handoutId + "/preview-file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
