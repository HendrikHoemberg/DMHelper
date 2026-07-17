package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.service.*;
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

@WebMvcTest(LibraryController.class)
class LibraryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService service;
    @MockitoBean private SpellService spellService;
    @MockitoBean private ConditionService conditionService;
    @MockitoBean private RuleSectionService ruleSectionService;
    @MockitoBean private EquipmentItemService equipmentItemService;
    @MockitoBean private MagicItemService magicItemService;
    @MockitoBean private CharacterClassService characterClassService;
    @MockitoBean private SpeciesService speciesService;
    @MockitoBean private BackgroundService backgroundService;
    @MockitoBean private FeatService featService;

    private StatBlock sampleSb() {
        StatBlock sb = new StatBlock();
        sb.setId(UUID.randomUUID());
        sb.setSource(ContentSource.SRD);
        sb.setName("Goblin");
        sb.setCr("1/4");
        sb.setType("Humanoid");
        sb.setSize("Small");
        sb.setAlignment("Neutral Evil");
        sb.setAc(15);
        sb.setHp("7 (2d6)");
        sb.setSpeed("30 ft.");
        sb.setStrScore(8); sb.setDexScore(14); sb.setConScore(10);
        sb.setIntScore(10); sb.setWisScore(8); sb.setChaScore(8);
        sb.setSkills("Stealth +6");
        sb.setSenses("darkvision 60 ft., passive Perception 9");
        sb.setLanguages("Common, Goblin");
        return sb;
    }

    @Test
    void shouldRenderLibraryPage() throws Exception {
        mockMvc.perform(get("/library"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Monster Library")));
    }

    @Test
    void shouldRenderStatblockCards() throws Exception {
        when(service.search(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of(sampleSb()));
        mockMvc.perform(get("/library/statblocks"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Goblin")));
    }

    @Test
    void shouldRenderDetail() throws Exception {
        StatBlock sb = sampleSb();
        when(service.findById(sb.getId())).thenReturn(sb);
        mockMvc.perform(get("/library/statblocks/{id}", sb.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Armor Class")));
    }

    @Test
    void customStatblockDetailRendersQuickNotesForItsCampaign() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        StatBlock sb = sampleSb();
        sb.setSource(ContentSource.CUSTOM);
        sb.setCampaign(campaign);
        when(service.findById(sb.getId())).thenReturn(sb);

        mockMvc.perform(get("/library/statblocks/{id}", sb.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "data-campaign-id=\"" + campaign.getId() + "\"")))
                .andExpect(content().string(containsString("data-target-type=\"STATBLOCK\"")));
    }

    @Test
    void shouldCreateCustom() throws Exception {
        StatBlock sb = sampleSb();
        sb.setSource(ContentSource.CUSTOM);
        sb.setName("Custom Goblin");
        when(service.createCustom(any(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sb);

        mockMvc.perform(post("/library/statblocks")
                        .param("campaignId", UUID.randomUUID().toString())
                        .param("name", "Custom Goblin")
                        .param("cr", "1/4").param("type", "Humanoid")
                        .param("ac", "15").param("hp", "7 (2d6)")
                        .param("speed", "30 ft.")
                        .param("strScore", "8").param("dexScore", "14")
                        .param("conScore", "10").param("intScore", "10")
                        .param("wisScore", "8").param("chaScore", "8")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Custom Goblin")));
    }

    @Test
    void shouldDeleteCustom() throws Exception {
        mockMvc.perform(delete("/library/statblocks/{id}", UUID.randomUUID())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/library"));
    }

    @Test
    void shouldRenderNewForm() throws Exception {
        mockMvc.perform(get("/library/statblocks/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create Custom Statblock")));
    }

    @Test
    void shouldPromoteStatBlockToGlobal() throws Exception {
        StatBlock sb = sampleSb();
        sb.setSource(ContentSource.CUSTOM);
        sb.setCampaignId(UUID.randomUUID());
        StatBlock promoted = sampleSb();
        promoted.setSource(ContentSource.CUSTOM);
        promoted.setCampaignId(null);
        when(service.promoteToGlobal(sb.getId())).thenReturn(promoted);

        mockMvc.perform(put("/library/statblocks/{id}/promote", sb.getId())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRenderAboutPage() throws Exception {
        mockMvc.perform(get("/library/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CC-BY-4.0")));
    }

    @Test
    void shouldSearchSpells() throws Exception {
        when(spellService.search(eq("fire"), isNull(), isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/spells").param("search", "fire"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchConditions() throws Exception {
        when(conditionService.search(isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/conditions"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchRules() throws Exception {
        when(ruleSectionService.search(isNull(), isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/rules"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchEquipment() throws Exception {
        when(equipmentItemService.search(isNull(), isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/equipment"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchMagicItems() throws Exception {
        when(magicItemService.search(isNull(String.class), isNull(String.class), isNull(String.class))).thenReturn(List.of());
        mockMvc.perform(get("/library/magic-items"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchClasses() throws Exception {
        when(characterClassService.search(isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/classes"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchSpecies() throws Exception {
        when(speciesService.search(isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/species"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchBackgrounds() throws Exception {
        when(backgroundService.search(isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/backgrounds"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSearchFeats() throws Exception {
        when(featService.search(isNull(), isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/feats"))
                .andExpect(status().isOk());
    }

    @Test
    void listExposesRequestedInitialTabAndSearch() throws Exception {
        mockMvc.perform(get("/library")
                        .param("tab", "spells")
                        .param("search", "Fireball"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("initialTab", "spells"))
                .andExpect(model().attribute("initialSearch", "Fireball"));
    }
}
