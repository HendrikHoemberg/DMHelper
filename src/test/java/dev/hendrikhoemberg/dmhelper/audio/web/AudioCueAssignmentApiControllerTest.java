package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueAssignmentService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AudioCueAssignmentApiController.class)
class AudioCueAssignmentApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AudioCueAssignmentService service;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private CampaignSettingsCodec settingsCodec;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID cueId = UUID.randomUUID();
    private final UUID sceneId = UUID.randomUUID();
    private final UUID encounterId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign = new dev.hendrikhoemberg.dmhelper.campaign.data.Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        when(campaignRepository.findById(campaignId)).thenReturn(java.util.Optional.of(campaign));
        dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings current = dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings.defaults();
        when(settingsCodec.read(any())).thenReturn(current);
    }

    @Test
    void assignCampaignCueReturnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/campaign", campaignId)
                        .param("cueId", cueId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).assignCampaignCue(campaignId, cueId);
    }

    @Test
    void assignCampaignCueReturnsOkWithNull() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/campaign", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).assignCampaignCue(campaignId, null);
    }

    @Test
    void assignSceneCueReturnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/scenes/{sceneId}", campaignId, sceneId)
                        .param("cueId", cueId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).assignSceneCue(campaignId, sceneId, cueId);
    }

    @Test
    void assignEncounterCueReturnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/encounters/{encounterId}", campaignId, encounterId)
                        .param("cueId", cueId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).assignEncounterCombatCue(campaignId, encounterId, cueId);
    }

    @Test
    void assignEncounterVictoryCueReturnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/encounters/{encounterId}", campaignId, encounterId)
                        .param("cueId", cueId.toString())
                        .param("role", "victory")
                        .param("durationSeconds", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).assignEncounterVictoryCue(campaignId, encounterId, cueId, 30);
    }

    @Test
    void assignLocationCueReturnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/locations/{locationId}", campaignId, locationId)
                        .param("cueId", cueId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).assignLocationCue(campaignId, locationId, cueId);
    }

    @Test
    void assignCampaignSettingsReturnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/settings", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audioSwitchMode\":\"AUTOMATIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void assignCampaignSettingsReturnsOkWithConfirm() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/settings", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audioSwitchMode\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void rejectsInvalidRole() throws Exception {
        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/encounters/{encounterId}", campaignId, encounterId)
                        .param("cueId", cueId.toString())
                        .param("role", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceErrorReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Cue does not belong to the same campaign"))
                .when(service).assignCampaignCue(any(), any());

        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/campaign", campaignId)
                        .param("cueId", cueId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cue does not belong to the same campaign"));
    }

    @Test
    void crossCampaignAttemptReturnsNotFoundWithoutLeakingInfo() throws Exception {
        doThrow(new dev.hendrikhoemberg.dmhelper.common.NotFoundException(
                "Audio cue not found in campaign"))
                .when(service).assignSceneCue(any(), any(), any());

        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/assignments/scenes/{sceneId}", campaignId, sceneId)
                        .param("cueId", cueId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                        "The requested item could not be found. Reload and try again."));
    }
}
