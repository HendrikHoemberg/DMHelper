package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardService;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ThreatController.class)
class ThreatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrapRepository trapRepository;

    @MockitoBean
    private HazardRepository hazardRepository;

    @MockitoBean
    private TrapService trapService;

    @MockitoBean
    private HazardService hazardService;

    @MockitoBean
    private MarkdownUtil markdownUtil;

    private final UUID campaignId = UUID.randomUUID();

    private Trap trap(UUID id, String name, ContentSource source) {
        Trap t = new Trap();
        t.setId(id);
        t.setName(name);
        t.setSource(source);
        t.setSourceKey(name.toLowerCase().replace(' ', '-'));
        t.setSeverity(ThreatSeverity.DANGEROUS);
        t.setResetMode(ThreatResetMode.NONE);
        t.setDescription("A **spiked** pit");
        return t;
    }

    private Hazard hazard(UUID id, String name, ContentSource source) {
        Hazard h = new Hazard();
        h.setId(id);
        h.setName(name);
        h.setSource(source);
        h.setSourceKey(name.toLowerCase().replace(' ', '-'));
        h.setSeverity(ThreatSeverity.SETBACK);
        h.setExposureMode(HazardExposureMode.ON_ENTER);
        h.setDescription("Toxic mist");
        return h;
    }

    @Test
    void listTrapsReturns200WithCreateAndScope() throws Exception {
        when(trapRepository.findVisibleByCampaignId(campaignId)).thenReturn(List.of(
                trap(UUID.randomUUID(), "Spike Pit", ContentSource.CUSTOM)));

        mockMvc.perform(get("/library/traps")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Spike Pit")))
                .andExpect(content().string(containsString("New Trap")))
                .andExpect(content().string(containsString("/library/traps/new?campaignId=" + campaignId)));
    }

    @Test
    void listHazardsReturns200WithCreateAndScope() throws Exception {
        when(hazardRepository.findVisibleByCampaignId(campaignId)).thenReturn(List.of(
                hazard(UUID.randomUUID(), "Lava Field", ContentSource.CUSTOM)));

        mockMvc.perform(get("/library/hazards")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lava Field")))
                .andExpect(content().string(containsString("New Hazard")))
                .andExpect(content().string(containsString("/library/hazards/new?campaignId=" + campaignId)));
    }

    @Test
    void listTrapsFiltersByText() throws Exception {
        Trap spike = trap(UUID.randomUUID(), "Spike Pit", ContentSource.CUSTOM);
        spike.setDescription("Spikes at the bottom");
        Trap net = trap(UUID.randomUUID(), "Falling Net", ContentSource.CUSTOM);
        net.setDescription("A weighted net drops from the ceiling");
        when(trapRepository.findVisibleByCampaignId(any())).thenReturn(List.of(spike, net));

        mockMvc.perform(get("/library/traps")
                        .param("campaignId", campaignId.toString())
                        .param("text", "spike"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Spike Pit")))
                .andExpect(content().string(not(containsString("Falling Net"))));
    }

    @Test
    void newTrapFormReturns200() throws Exception {
        mockMvc.perform(get("/library/traps/new")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("threatEditor")));
    }

    @Test
    void newHazardFormReturns200() throws Exception {
        mockMvc.perform(get("/library/hazards/new")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("threatEditor")));
    }

    @Test
    void trapDetailReturns200WithClonePromoteDeleteAndSafeMarkdown() throws Exception {
        UUID id = UUID.randomUUID();
        Trap t = trap(id, "Poison Darts", ContentSource.CUSTOM);
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        t.setCampaign(campaign);
        when(trapRepository.findDetailedById(id)).thenReturn(Optional.of(t));
        when(markdownUtil.toHtml(any())).thenReturn("<p>A <strong>spiked</strong> pit</p>");

        mockMvc.perform(get("/library/traps/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Poison Darts")))
                .andExpect(content().string(containsString("Clone")))
                .andExpect(content().string(containsString("Promote")))
                .andExpect(content().string(containsString("Delete")))
                .andExpect(content().string(containsString("threatManagement")));
    }

    @Test
    void hazardDetailReturns200WithManagementControls() throws Exception {
        UUID id = UUID.randomUUID();
        Hazard h = hazard(id, "Acid Pool", ContentSource.CUSTOM);
        when(hazardRepository.findDetailedById(id)).thenReturn(Optional.of(h));
        when(markdownUtil.toHtml(any())).thenReturn("<p>Toxic mist</p>");

        mockMvc.perform(get("/library/hazards/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Acid Pool")))
                .andExpect(content().string(containsString("Clone")))
                .andExpect(content().string(containsString("Delete")));
    }

    @Test
    void editTrapReturns200ForCustom() throws Exception {
        UUID id = UUID.randomUUID();
        Trap t = trap(id, "My Trap", ContentSource.CUSTOM);
        when(trapRepository.findDetailedById(id)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/library/traps/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Trap")))
                .andExpect(content().string(containsString("threatEditor")));
    }

    @Test
    void editTrapReturns403ForSrd() throws Exception {
        UUID id = UUID.randomUUID();
        Trap t = trap(id, "SRD Trap", ContentSource.SRD);
        when(trapRepository.findDetailedById(id)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/library/traps/{id}/edit", id))
                .andExpect(status().isForbidden());
    }

    @Test
    void editHazardReturns403ForSrd() throws Exception {
        UUID id = UUID.randomUUID();
        Hazard h = hazard(id, "SRD Hazard", ContentSource.SRD);
        when(hazardRepository.findDetailedById(id)).thenReturn(Optional.of(h));

        mockMvc.perform(get("/library/hazards/{id}/edit", id))
                .andExpect(status().isForbidden());
    }
}
