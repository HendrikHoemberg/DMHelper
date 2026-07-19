package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AudioCueController.class)
class AudioCueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AudioCueService service;

    @MockitoBean
    private AudioCueRepository repository;

    @MockitoBean
    private MarkdownUtil markdownUtil;

    @MockitoBean
    private SceneRepository sceneRepository;

    @MockitoBean
    private EncounterRepository encounterRepository;

    @MockitoBean
    private WorldLocationRepository locationRepository;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    private AudioCue cue(UUID id, String name) {
        AudioCue c = new AudioCue();
        c.setId(id);
        c.setCueKey(name.toLowerCase().replace(' ', '-'));
        c.setName(name);
        c.setReferenceKind(AudioReferenceKind.VIDEO);
        c.setProviderReference("dQw4w9WgXcQ");
        c.setCategory(AudioCategory.AMBIENT);
        c.setTransitionPreference(AudioTransitionPreference.CROSSFADE);
        return c;
    }

    @Test
    void listReturns200WithCreateLink() throws Exception {
        when(service.listByCampaign(campaignId)).thenReturn(List.of(
                cue(UUID.randomUUID(), "Test Cue")));

        mockMvc.perform(get("/campaigns/{campaignId}/audio/cues", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Cue")))
                .andExpect(content().string(containsString("New Cue")));
    }

    @Test
    void newFormReturns200() throws Exception {
        mockMvc.perform(get("/campaigns/{campaignId}/audio/cues/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("audioCueEditor")));
    }

    @Test
    void detailReturns200WithCueData() throws Exception {
        UUID id = UUID.randomUUID();
        AudioCue c = cue(id, "Detail Cue");
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        c.setCampaign(campaign);
        when(service.findById(id)).thenReturn(c);
        when(markdownUtil.toHtml(any())).thenReturn("");

        mockMvc.perform(get("/campaigns/{campaignId}/audio/cues/{cueId}", campaignId, id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Detail Cue")))
                .andExpect(content().string(containsString("audioCueManagement")));
    }

    @Test
    void editFormReturns200WithDto() throws Exception {
        UUID id = UUID.randomUUID();
        AudioCue c = cue(id, "Edit Cue");
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        c.setCampaign(campaign);
        when(service.findById(id)).thenReturn(c);

        mockMvc.perform(get("/campaigns/{campaignId}/audio/cues/{cueId}/edit", campaignId, id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit")))
                .andExpect(content().string(containsString("audioCueEditor")));
    }
}
