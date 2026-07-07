package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.library.service.SpellSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.SrdSeedService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PartyController.class)
class PartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService campaignService;

    @MockitoBean
    private PartyMemberService partyService;

    @MockitoBean
    private SrdSeedService srdSeedService;

    @MockitoBean
    private SpellSeedService spellSeedService;

    private UUID campaignId = UUID.randomUUID();

    @Test
    void shouldRenderPartyList() throws Exception {
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        when(campaignService.findById(campaignId)).thenReturn(c);
        when(partyService.findByCampaignId(campaignId)).thenReturn(List.of());
        when(partyService.findActiveByCampaignId(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/party", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Party")));
    }

    @Test
    void shouldCreatePartyMember() throws Exception {
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        PartyMember pm = new PartyMember();
        pm.setId(UUID.randomUUID());
        pm.setCampaign(c);
        pm.setCharacterName("Thia");
        pm.setAc(16);
        pm.setMaxHp(38);
        pm.setPassivePerception(17);

        when(partyService.create(eq(campaignId), eq("Thia"), any(), any(), eq(16), eq(38), eq(4), eq(30),
                eq(17), eq(12), eq(14), any())).thenReturn(pm);

        mockMvc.perform(post("/campaigns/{cid}/party", campaignId)
                        .param("characterName", "Thia")
                        .param("ac", "16").param("maxHp", "38")
                        .param("initiativeBonus", "4").param("speed", "30")
                        .param("passivePerception", "17").param("passiveInsight", "12")
                        .param("passiveInvestigation", "14")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thia")));
    }

    @Test
    void shouldDeletePartyMember() throws Exception {
        UUID pid = UUID.randomUUID();
        mockMvc.perform(delete("/campaigns/{cid}/party/{pid}", campaignId, pid)
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect",
                        containsString("/campaigns/" + campaignId + "/party")));
    }
}
