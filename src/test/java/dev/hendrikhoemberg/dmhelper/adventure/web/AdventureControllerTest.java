package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdventureController.class)
class AdventureControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdventureService adventureService;
    @MockitoBean private CampaignRepository campaignRepository;

    private UUID campaignId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    }

    @Test
    void listShowsAdventureOverview() throws Exception {
        when(adventureService.findAdventuresByCampaign(campaignId)).thenReturn(List.of());
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{campaignId}/adventures", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("adventures"));
    }

    @Test
    void detailShowsChapters() throws Exception {
        UUID aId = UUID.randomUUID();
        Adventure a = new Adventure();
        a.setId(aId);
        a.setName("Module");
        when(adventureService.findAdventureById(aId)).thenReturn(a);
        when(adventureService.findChaptersByAdventure(aId)).thenReturn(List.of());
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{campaignId}/adventures/{id}", campaignId, aId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("adventure", "chapters"));
    }

    @Test
    void createRedirectsToDetail() throws Exception {
        UUID aId = UUID.randomUUID();
        Adventure a = new Adventure();
        a.setId(aId);
        a.setName("New Module");
        when(adventureService.createAdventure(eq(campaignId), any(), any(), any())).thenReturn(a);

        mockMvc.perform(post("/campaigns/{campaignId}/adventures", campaignId)
                        .param("name", "New Module"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/adventures/" + aId));
    }

    @Test
    void deleteRedirectsToList() throws Exception {
        UUID aId = UUID.randomUUID();

        mockMvc.perform(delete("/campaigns/{campaignId}/adventures/{id}", campaignId, aId))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect",
                        "/campaigns/" + campaignId + "/adventures"));
    }

    @Test
    void chapterCreateReturnsChapterListFragment() throws Exception {
        UUID aId = UUID.randomUUID();
        Chapter ch = new Chapter();
        ch.setId(UUID.randomUUID());
        ch.setTitle("Ch 1");
        Adventure a = new Adventure();
        a.setId(aId);
        a.setName("Module");
        when(adventureService.findAdventureById(aId)).thenReturn(a);
        when(adventureService.createChapter(eq(aId), any(), any())).thenReturn(ch);
        when(adventureService.findChaptersByAdventure(aId)).thenReturn(List.of(ch));
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/campaigns/{campaignId}/adventures/{aId}/chapters", campaignId, aId)
                        .param("title", "Ch 1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("chapters", "adventure"));
    }
}
