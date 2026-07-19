package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioRuntimeView;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.service.SessionAudioStateService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AudioRuntimeApiController.class)
class AudioRuntimeApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionAudioStateService audioStateService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private AudioCueRepository audioCueRepository;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void getStateReturns200() throws Exception {
        AudioRuntimeView view = runtimeView();
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        when(audioStateService.getRuntimeView(sessionId, campaignId)).thenReturn(view);

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/audio/runtime/state", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted").value(false))
                .andExpect(jsonPath("$.switchMode").value("AUTOMATIC"))
                .andExpect(jsonPath("$.hasPendingConfirmation").value(false));
    }

    @Test
    void getStateReturns200ForMissingSession() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        when(audioStateService.getRuntimeView(sessionId, campaignId))
                .thenThrow(new IllegalStateException("No audio state"));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/audio/runtime/state", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionableAvailable").value(false));
    }

    @Test
    void muteReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/mute", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).mute(sessionId);
    }

    @Test
    void unmuteReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/unmute", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).unmute(sessionId);
    }

    @Test
    void setOverrideReturns200() throws Exception {
        UUID cueId = UUID.randomUUID();
        AudioCue cue = cue();
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        when(audioCueRepository.findById(cueId)).thenReturn(java.util.Optional.of(cue));
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/override", campaignId)
                        .param("sessionId", sessionId.toString())
                        .param("cueId", cueId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).setManualOverride(eq(sessionId), any());
    }

    @Test
    void clearOverrideReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(delete("/api/v1/campaigns/{campaignId}/audio/runtime/override", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).clearManualOverride(sessionId);
    }

    @Test
    void confirmReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/confirm", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).confirm(sessionId);
    }

    @Test
    void declineReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/decline", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).decline(sessionId);
    }

    @Test
    void expireVictoryReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/victory/expire", campaignId)
                        .param("sessionId", sessionId.toString()))
                .andExpect(status().isOk());

        verify(audioStateService).expireVictory(sessionId);
    }

    @Test
    void acknowledgePlaybackReturns200() throws Exception {
        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/acknowledge", campaignId)
                        .param("sessionId", sessionId.toString())
                        .param("result", "completed"))
                .andExpect(status().isOk());

        verify(audioStateService).acknowledgePlaybackResult(sessionId, "completed");
    }

    private AudioRuntimeView runtimeView() {
        AudioCue cue = cue();
        return new AudioRuntimeView(
                cue, "SCENE", UUID.randomUUID(), "Scene",
                false, AudioSwitchMode.AUTOMATIC, false, true);
    }

    private AudioCue cue() {
        Campaign cCampaign = new Campaign();
        cCampaign.setId(campaignId);
        AudioCue c = new AudioCue();
        c.setId(UUID.randomUUID());
        c.setCampaign(cCampaign);
        c.setName("Test Cue");
        c.setReferenceKind(AudioReferenceKind.VIDEO);
        c.setProviderReference("dQw4w9WgXcQ");
        c.setCategory(AudioCategory.AMBIENT);
        c.setTransitionPreference(AudioTransitionPreference.CROSSFADE);
        return c;
    }
}
