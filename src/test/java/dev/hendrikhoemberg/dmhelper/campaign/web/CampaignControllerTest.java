package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignController.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService service;

    @MockitoBean
    private NoteService noteService;

    @MockitoBean
    private PartyMemberService partyMemberService;

    private Campaign sampleCampaign() {
        Campaign c = new Campaign();
        c.setId(UUID.randomUUID());
        c.setName("Test Campaign");
        c.setDescription("A test campaign");
        c.setCreatedAt(Instant.now());
        return c;
    }

    @Test
    void shouldRenderCampaignList() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleCampaign()));

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldRenderEmptyList() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No campaigns yet")));
    }

    @Test
    void shouldCreateCampaign() throws Exception {
        Campaign c = sampleCampaign();
        when(service.create(eq("Test Campaign"), any())).thenReturn(c);

        mockMvc.perform(post("/campaigns")
                        .param("name", "Test Campaign")
                        .param("description", "A test")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldRejectEmptyCampaignName() throws Exception {
        mockMvc.perform(post("/campaigns")
                        .param("name", "")
                        .param("description", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailProvidesDashboardModel() throws Exception {
        Campaign c = sampleCampaign();
        when(service.findById(c.getId())).thenReturn(c);
        when(noteService.findByCampaignIdAndType(eq(c.getId()), any())).thenReturn(List.of());
        when(noteService.findByCampaignId(c.getId())).thenReturn(List.of());
        when(partyMemberService.findActiveByCampaignId(c.getId())).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("partyMembers"))
                .andExpect(model().attributeExists("recentNotes"))
                .andExpect(model().attribute("campaignId", c.getId()));
    }

    @Test
    void shouldRenderDetail() throws Exception {
        Campaign c = sampleCampaign();
        when(service.findById(c.getId())).thenReturn(c);

        mockMvc.perform(get("/campaigns/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldUpdateCampaign() throws Exception {
        Campaign c = sampleCampaign();
        c.setName("Updated Name");
        when(service.update(eq(c.getId()), eq("Updated Name"), any())).thenReturn(c);

        mockMvc.perform(put("/campaigns/{id}", c.getId())
                        .param("name", "Updated Name")
                        .param("description", "Updated desc")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Updated Name")));
    }

    @Test
    void shouldDeleteCampaign() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/campaigns/{id}", id)
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/campaigns"));
    }

    @Test
    void shouldExportCampaign() throws Exception {
        Campaign c = sampleCampaign();
        when(service.findById(c.getId())).thenReturn(c);
        when(service.exportToJson(c.getId())).thenReturn("{\"formatVersion\":1}");

        mockMvc.perform(get("/campaigns/{id}/export", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition", containsString(".dmcampaign.json")));
    }

    @Test
    void shouldImportCampaign() throws Exception {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": { "name": "Imported", "description": "desc" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": []
                }
                """;
        Campaign c = sampleCampaign();
        c.setName("Imported");
        when(service.importFromJson(json)).thenReturn(c);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.dmcampaign.json", "application/json", json.getBytes());

        mockMvc.perform(multipart("/campaigns/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported")));
    }
}
