package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueDeletionImpact;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueDependency;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueService;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueValidationException;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueValidationProblem;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AudioCueApiController.class)
class AudioCueApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AudioCueService service;

    private final UUID campaignId = UUID.randomUUID();

    private Campaign campaign(UUID id) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setName("Test Campaign");
        return c;
    }

    private AudioCue cue(UUID id, String name) {
        AudioCue c = new AudioCue();
        c.setId(id);
        c.setCampaign(campaign(campaignId));
        c.setCueKey(name.toLowerCase().replace(' ', '-'));
        c.setName(name);
        c.setReferenceKind(AudioReferenceKind.VIDEO);
        c.setProviderReference("dQw4w9WgXcQ");
        c.setCategory(AudioCategory.AMBIENT);
        c.setTransitionPreference(AudioTransitionPreference.CROSSFADE);
        return c;
    }

    @Test
    void listReturns200() throws Exception {
        when(service.listByCampaign(campaignId)).thenReturn(List.of(cue(UUID.randomUUID(), "Test")));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/audio/cues", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test"));
    }

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        AudioCue c = cue(id, "New Cue");
        when(service.create(any(), any())).thenReturn(c);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/cues", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cueKey":"new-cue","name":"New Cue","providerId":"youtube",
                                "providerReference":"dQw4w9WgXcQ","category":"AMBIENT",
                                "transitionPreference":"CROSSFADE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Cue"))
                .andExpect(jsonPath("$.cueKey").value("new-cue"));
    }

    @Test
    void createReturns400OnValidationError() throws Exception {
        when(service.create(any(), any())).thenThrow(
                new AudioCueValidationException(List.of(
                        new AudioCueValidationProblem("CUE_FIELD_REQUIRED", "/name", "Name is required"))));

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/cues", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0].code").value("CUE_FIELD_REQUIRED"));
    }

    @Test
    void getReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, campaignId)).thenReturn(cue(id, "Detail Cue"));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/audio/cues/{cueId}", campaignId, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Detail Cue"));
    }

    @Test
    void getReturns404WhenCueBelongsToDifferentCampaign() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id, campaignId)).thenThrow(
                new dev.hendrikhoemberg.dmhelper.common.NotFoundException("Audio cue not found: " + id));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/audio/cues/{cueId}", campaignId, id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        AudioCue c = cue(id, "Updated");
        when(service.update(any(), any(), any())).thenReturn(c);

        mockMvc.perform(put("/api/v1/campaigns/{campaignId}/audio/cues/{cueId}", campaignId, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cueKey":"updated","name":"Updated","providerId":"youtube",
                                "providerReference":"dQw4w9WgXcQ","category":"AMBIENT",
                                "transitionPreference":"CROSSFADE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void cloneReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        AudioCue cloned = cue(UUID.randomUUID(), "Cloned");
        when(service.cloneCue(any(), any(), any())).thenReturn(cloned);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/audio/cues/{cueId}/clone", campaignId, id)
                        .param("cueKey", "cloned-key"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cloned"));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/campaigns/{campaignId}/audio/cues/{cueId}", campaignId, id)
                        .param("confirmed", "true"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletionImpactReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        var impact = new AudioCueDeletionImpact(id, "Test",
                List.of(new AudioCueDependency("SESSION_OVERRIDE", UUID.randomUUID(), "Session", "/sessions/x")));
        when(service.computeDeletionImpact(id, campaignId)).thenReturn(impact);

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/audio/cues/{cueId}/deletion-impact", campaignId, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencies[0].kind").value("SESSION_OVERRIDE"))
                .andExpect(jsonPath("$.dependencies.length()").value(1));
    }
}
