package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.EncounterTestFixtures;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EncounterApiController.class)
class EncounterCombatantStatblockTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EncounterService service;

    @MockitoBean
    private EncounterPlacementService placements;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void aLibraryBackedCombatantExposesItsSourceStatblock() throws Exception {
        var f = EncounterTestFixtures.encounterWithGoblinFromLibrary();
        when(service.getCombatantStatblock(f.combatantId()))
                .thenReturn(new EncounterService.StatblockRef(f.statblockId(), "Goblin Warrior"));

        mvc.perform(get("/api/v1/encounters/{eid}/combatants/{cid}/statblock",
                        f.encounterId(), f.combatantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statblockId").value(f.statblockId().toString()))
                .andExpect(jsonPath("$.name").value("Goblin Warrior"));
    }

    @Test
    void anAdHocCombatantHasNoStatblock() throws Exception {
        var f = EncounterTestFixtures.encounterWithQuickAddedNpc();
        when(service.getCombatantStatblock(f.combatantId()))
                .thenReturn(null);

        mvc.perform(get("/api/v1/encounters/{eid}/combatants/{cid}/statblock",
                        f.encounterId(), f.combatantId()))
                .andExpect(status().isNoContent());
    }
}
