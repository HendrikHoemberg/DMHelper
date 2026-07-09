package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantUpdateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.UpdateRequest;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
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

@WebMvcTest(EncounterController.class)
class EncounterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EncounterService encounterService;

    @MockitoBean
    private GameMapRepository mapRepo;

    private final UUID campaignId = UUID.randomUUID();

    private EncounterDto enc(UUID id, String name, String status) {
        return new EncounterDto(id, campaignId, null, name, status,
                0, -1, 0, null, null, false, List.of(), List.of());
    }

    @Test
    void shouldRenderEncounterList() throws Exception {
        when(encounterService.list(campaignId)).thenReturn(List.of(
                enc(UUID.randomUUID(), "Throne Room Fight", "PLANNED")));
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{campaignId}/encounters", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Throne Room Fight")));
    }

    @Test
    void shouldRenderNewForm() throws Exception {
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{campaignId}/encounters/new", campaignId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateEncounter() throws Exception {
        UUID encId = UUID.randomUUID();
        when(encounterService.create(eq(campaignId), any(CreateRequest.class)))
                .thenReturn(enc(encId, "Ambush", "PLANNED"));

        mockMvc.perform(post("/campaigns/{campaignId}/encounters", campaignId)
                        .param("name", "Ambush")
                        .param("mapId", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/encounters"));
    }

    @Test
    void shouldRenderEditForm() throws Exception {
        UUID encId = UUID.randomUUID();
        when(encounterService.getById(encId)).thenReturn(enc(encId, "Edit Me", "PLANNED"));
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{campaignId}/encounters/{id}/edit", campaignId, encId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Me")));
    }

    @Test
    void shouldUpdateEncounter() throws Exception {
        UUID encId = UUID.randomUUID();
        when(encounterService.update(eq(encId), any(UpdateRequest.class)))
                .thenReturn(enc(encId, "Updated", "PLANNED"));

        mockMvc.perform(put("/campaigns/{campaignId}/encounters/{id}", campaignId, encId)
                        .param("name", "Updated")
                        .param("mapId", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/encounters"));
    }

    @Test
    void shouldDeleteEncounter() throws Exception {
        UUID encId = UUID.randomUUID();

        mockMvc.perform(delete("/campaigns/{campaignId}/encounters/{id}", campaignId, encId))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect",
                        containsString("/campaigns/" + campaignId + "/encounters")));
    }

    @Test
    void shouldActivateEncounter() throws Exception {
        UUID encId = UUID.randomUUID();
        when(encounterService.activate(encId))
                .thenReturn(enc(encId, "Active", "ACTIVE"));

        mockMvc.perform(post("/campaigns/{campaignId}/encounters/{id}/activate", campaignId, encId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/encounters/" + encId));
    }

    @Test
    void shouldRenderEncounterDetail() throws Exception {
        UUID encId = UUID.randomUUID();
        when(encounterService.getById(encId)).thenReturn(enc(encId, "Detail View", "PLANNED"));
        when(encounterService.getCombatants(encId)).thenReturn(List.of());
        when(encounterService.calculateDifficulty(campaignId, encId))
                .thenReturn(new dev.hendrikhoemberg.dmhelper.encounter.service.CombatDifficultyCalculator.DifficultyResult(
                        "LOW", 0, 3000, "Test"));

        mockMvc.perform(get("/campaigns/{campaignId}/encounters/{id}", campaignId, encId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Detail View")));
    }
}
