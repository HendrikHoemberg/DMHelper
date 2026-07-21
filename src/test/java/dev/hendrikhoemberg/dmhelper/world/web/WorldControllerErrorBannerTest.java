package dev.hendrikhoemberg.dmhelper.world.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorldController.class)
class WorldControllerErrorBannerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WorldService worldService;
    @MockitoBean private CampaignRepository campaignRepository;
    @MockitoBean private RollableTableRepository rollableTableRepository;
    @MockitoBean private WorldLocationTableLinkRepository locationTableLinkRepository;
    @MockitoBean private TableReferenceResolver referenceResolver;
    @MockitoBean private AudioCueRepository audioCueRepository;

    private UUID stubCampaign() {
        UUID id = UUID.randomUUID();
        Campaign c = new Campaign();
        c.setId(id);
        c.setName("Test");
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));
        return id;
    }

    @Test
    void npcListWithoutErrorShowsNoBanner() throws Exception {
        UUID cid = stubCampaign();
        when(worldService.getNpcs(cid)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/" + cid + "/world/npcs"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("alert-error"))));
    }

    @Test
    void failedNpcCreateShowsBannerWithMessage() throws Exception {
        UUID cid = stubCampaign();
        when(worldService.createNpc(eq(cid), any()))
                .thenThrow(new IllegalArgumentException("Name is required"));
        when(worldService.getFactions(cid)).thenReturn(List.of());
        when(worldService.getLocations(cid)).thenReturn(List.of());

        mockMvc.perform(post("/campaigns/" + cid + "/world/npcs").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-error")))
                .andExpect(content().string(containsString("Name is required")));
    }
}
