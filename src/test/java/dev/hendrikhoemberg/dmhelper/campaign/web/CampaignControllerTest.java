package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignScaleService;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessRepairService;

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

    @MockitoBean
    private AudioCueRepository audioCueRepository;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private CampaignScaleService scaleService;

    @MockitoBean
    private CampaignReadinessFacade readinessFacade;

    @MockitoBean
    private ReadinessRepairService repairService;

    @MockitoBean
    private AdventureService adventureService;

    @MockitoBean
    private EncounterRepository encounterRepository;

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
    void listProvidesDeterministicCampaignSigils() throws Exception {
        Campaign campaign = sampleCampaign();
        when(service.findAll()).thenReturn(List.of(campaign));
        when(partyMemberService.findActiveByCampaignId(campaign.getId())).thenReturn(List.of());

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("campaignSigils"))
                .andExpect(content().string(containsString("campaign-sigil")));
    }

    @Test
    void shouldRenderEmptyList() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No campaigns yet")));
    }

    @Test
    void listCarriesTheImportDialogItsButtonsInvoke() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleCampaign()));

        mockMvc.perform(get("/campaigns"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("x-data=\"campaignImport\"")))
                .andExpect(content().string(containsString("id=\"importDialog\"")));
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
                .andExpect(model().attributeExists("sigil"))
                .andExpect(content().string(containsString("campaign-sigil")))
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void standaloneNewPageSubmitsWithoutAnHtmxTargetItCannotReach() throws Exception {
        String html = mockMvc.perform(get("/campaigns/new"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // #campaign-grid only exists on the campaign list; targeting it here swaps into nothing.
        assertThat(html).doesNotContain("campaign-grid");
        assertThat(html).contains("action=\"/campaigns\"");
        assertThat(html).contains("method=\"post\"");
    }

    @Test
    void theInlineFormOnTheListStillSwapsIntoTheCardGrid() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleCampaign()));

        String html = mockMvc.perform(get("/campaigns").param("fragment", "form"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("hx-post=\"/campaigns\"");
        assertThat(html).contains("hx-target=\"#campaign-grid\"");
        assertThat(html).contains("hx-swap=\"beforeend\"");
    }

    @Test
    void aPlainFormPostLandsInTheNewCampaign() throws Exception {
        Campaign c = sampleCampaign();
        when(service.create(eq("Test Campaign"), any())).thenReturn(c);

        mockMvc.perform(post("/campaigns")
                        .param("name", "Test Campaign")
                        .param("description", "A test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + c.getId()));
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
        when(scaleService.scaleOf(any())).thenReturn(new CampaignScaleService.CampaignScale(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        when(readinessFacade.reportForCampaign(any())).thenReturn(new CampaignReadinessReport(List.of()));
        when(adventureService.getCurrentScene(any())).thenReturn(Optional.empty());
        when(encounterRepository.findByCampaignIdAndStatus(any(), any())).thenReturn(Optional.empty());

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
        when(scaleService.scaleOf(any())).thenReturn(new CampaignScaleService.CampaignScale(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        when(readinessFacade.reportForCampaign(any())).thenReturn(new CampaignReadinessReport(List.of()));
        when(adventureService.getCurrentScene(any())).thenReturn(Optional.empty());
        when(encounterRepository.findByCampaignIdAndStatus(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Campaign")));
    }

    @Test
    void shouldUpdateCampaign() throws Exception {
        Campaign c = sampleCampaign();
        c.setName("Updated Name");
        when(service.update(eq(c.getId()), eq("Updated Name"), any())).thenReturn(c);
        when(scaleService.scaleOf(any())).thenReturn(new CampaignScaleService.CampaignScale(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        when(readinessFacade.reportForCampaign(any())).thenReturn(new CampaignReadinessReport(List.of()));
        when(adventureService.getCurrentScene(any())).thenReturn(Optional.empty());
        when(encounterRepository.findByCampaignIdAndStatus(any(), any())).thenReturn(Optional.empty());

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
}
