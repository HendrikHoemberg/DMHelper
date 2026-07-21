package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
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
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(PartyController.class)
class PartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService campaignService;

    @MockitoBean
    private PartyMemberService partyService;

    @MockitoBean
    private CharacterClassRepository classRepository;

    @MockitoBean
    private CampaignRepository campaignRepository;

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
                .andExpect(content().string(containsString("Party")))
                .andExpect(content().string(containsString("id=\"party-form-modal\"")));
    }

    @Test
    void shouldRenderNewForm() throws Exception {
        CharacterClass rogue = new CharacterClass();
        rogue.setSource(ContentSource.SRD);
        rogue.setName("Rogue");
        when(classRepository.findBySubclassOfIsNullOrderByNameAsc()).thenReturn(List.of(rogue));

        mockMvc.perform(get("/campaigns/{cid}/party/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/campaigns/" + campaignId + "/party")))
                .andExpect(model().attributeExists("classNames"));
    }

    @Test
    void shouldRenderEditForm() throws Exception {
        UUID pid = UUID.randomUUID();
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        CharacterClass rogue = new CharacterClass();
        rogue.setSource(ContentSource.SRD);
        rogue.setName("Rogue");
        PartyMember pm = new PartyMember();
        pm.setId(pid);
        pm.setCampaign(c);
        pm.setCharacterName("Thia");
        when(partyService.findById(pid)).thenReturn(pm);
        when(classRepository.findBySubclassOfIsNullOrderByNameAsc()).thenReturn(List.of(rogue));

        mockMvc.perform(get("/campaigns/{cid}/party/{pid}/edit", campaignId, pid))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/campaigns/" + campaignId + "/party/" + pid)))
                .andExpect(model().attributeExists("classNames"));
    }

    @Test
    void shouldRenderClassDropdownOptions() throws Exception {
        CharacterClass rogue = new CharacterClass();
        rogue.setSource(ContentSource.SRD);
        rogue.setName("Rogue");
        CharacterClass wizard = new CharacterClass();
        wizard.setSource(ContentSource.SRD);
        wizard.setName("Wizard");
        when(classRepository.findBySubclassOfIsNullOrderByNameAsc())
                .thenReturn(List.of(rogue, wizard));

        mockMvc.perform(get("/campaigns/{cid}/party/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString("Rogue")))
                .andExpect(content().string(containsString("Wizard")));
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
        pm.setCurrentHp(38);
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
    void shouldUpdateReturnCardFragment() throws Exception {
        UUID pid = UUID.randomUUID();
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        PartyMember pm = new PartyMember();
        pm.setId(pid);
        pm.setCampaign(c);
        pm.setCharacterName("Thia");
        pm.setActive(true);
        when(partyService.update(eq(pid), eq("Thia"), any(), any(), eq(16), eq(38), eq(4), eq(30),
                eq(17), eq(12), eq(14), any())).thenReturn(pm);

        mockMvc.perform(put("/campaigns/{cid}/party/{pid}", campaignId, pid)
                        .param("characterName", "Thia")
                        .param("classAndLevel", "Rogue 5")
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
