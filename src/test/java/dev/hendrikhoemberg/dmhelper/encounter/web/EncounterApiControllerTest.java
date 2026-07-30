package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.PlacementDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.PlacementUpsertRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.PlacementMoveRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.ChangeEncounterMapRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.EncounterReadinessDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.ReadinessIssueDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.InitiativeSetupIncompleteException;

@WebMvcTest(EncounterApiController.class)
class EncounterApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EncounterService service;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private EncounterPlacementService placements;

    private EncounterDto enc(UUID id, String name, String status) {
        return new EncounterDto(id, UUID.randomUUID(), UUID.randomUUID(), name, status,
                1, 0, "RUNNING", 0, null, null, false, List.of(), List.of());
    }

    private EncounterDto enc(UUID id, String name, String status, String combatPhase, int round, int activeTurnIndex) {
        return new EncounterDto(id, UUID.randomUUID(), UUID.randomUUID(), name, status,
                round, activeTurnIndex, combatPhase, 0, null, null, false, List.of(), List.of());
    }

    private CombatantDto combatant(String name) {
        return new CombatantDto(UUID.randomUUID(), UUID.randomUUID(), name, 10, 0,
                20, 20, 0, "MONSTER", null, false,
                null, null, null, null,
                false, false, false, List.of(),
                null, false, 0, 0, 0, 0, null,
                null, null, null, null, null, null, null, false);
    }

    @Test
    void shouldCreateEncounter() throws Exception {
        UUID campId = UUID.randomUUID();
        UUID encId = UUID.randomUUID();
        when(service.create(eq(campId), any())).thenReturn(
                new EncounterDto(encId, campId, null, "New Encounter", "PLANNED",
                        0, -1, "SETUP", 0, null, null, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/encounters", campId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Encounter\",\"mapId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Encounter"))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void shouldListEncounters() throws Exception {
        UUID campId = UUID.randomUUID();
        when(service.list(campId)).thenReturn(List.of(
                enc(UUID.randomUUID(), "Encounter 1", "PLANNED"),
                enc(UUID.randomUUID(), "Encounter 2", "PLANNED")
        ));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/encounters", campId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Encounter 1"));
    }

    @Test
    void shouldActivateEncounter() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.activate(encId)).thenReturn(
                new EncounterDto(encId, UUID.randomUUID(), null, "Active Encounter", "ACTIVE",
                        0, -1, "SETUP", 0, null, null, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/encounters/{id}/activate", encId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldNextTurn() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.nextTurn(encId)).thenReturn(
                new EncounterDto(encId, UUID.randomUUID(), null, "Encounter", "ACTIVE",
                        2, 1, "RUNNING", 0, null, null, false, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/encounters/{id}/next-turn", encId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2));
    }

    @Test
    void shouldApplyDamage() throws Exception {
        UUID combatantId = UUID.randomUUID();
        when(service.applyDamage(eq(combatantId), anyInt()))
                .thenReturn(combatant("Hurt Monster"));

        mockMvc.perform(post("/api/v1/combatants/{id}/damage", combatantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hurt Monster"));
    }

    @Test
    void shouldGetActiveEncounter() throws Exception {
        UUID campId = UUID.randomUUID();
        UUID encId = UUID.randomUUID();
        when(service.findActiveByCampaignId(campId))
                .thenReturn(java.util.Optional.of(
                        new EncounterDto(encId, campId, null, "Active", "ACTIVE",
                                1, 0, "RUNNING", 0, null, null, false, List.of(), List.of())));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/encounters/active", campId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void noActiveEncounterIsANormalEmptyResponse() throws Exception {
        UUID campId = UUID.randomUUID();
        when(service.findActiveByCampaignId(campId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/encounters/active", campId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGetCombatants() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.getCombatants(encId)).thenReturn(List.of(
                combatant("Goblin"), combatant("Orc")));

        mockMvc.perform(get("/api/v1/encounters/{id}/combatants", encId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void startCombatPassesExplicitAcceptance() throws Exception {
        UUID encId = UUID.randomUUID();
        var started = new EncounterDto(encId, UUID.randomUUID(), null, "Started", "ACTIVE",
                1, 0, "RUNNING", 0, null, null, false, List.of(), List.of());
        when(service.startCombat(eq(encId), eq(true))).thenReturn(started);

        mockMvc.perform(post("/api/v1/encounters/{id}/start-combat", encId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptUnset\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combatPhase").value("RUNNING"))
                .andExpect(jsonPath("$.round").value(1));
    }

    @Test
    void incompleteSetupReturnsTypedConflict() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.startCombat(eq(encId), eq(false)))
                .thenThrow(new InitiativeSetupIncompleteException("2 combatant(s) still have unset initiative.", 2));

        mockMvc.perform(post("/api/v1/encounters/{id}/start-combat", encId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptUnset\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Initiative Setup Incomplete"))
                .andExpect(jsonPath("$.unsetCount").value(2));
    }

    @Test
    void setupCombatantsUsesTheUnfilteredSetupQuery() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.getInitiativeSetupCombatants(encId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/encounters/{id}/initiative-setup/combatants", encId))
                .andExpect(status().isOk());

        verify(service).getInitiativeSetupCombatants(encId);
    }

    @Test
    void legacySetupCombatantsRouteRemainsAvailable() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.getInitiativeSetupCombatants(encId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/encounters/{id}/setup-combatants", encId))
                .andExpect(status().isOk());
    }

    @Test
    void initiativeRequestAcceptsZeroNegativeAndNull() throws Exception {
        UUID combatantId = UUID.randomUUID();
        var dto = new CombatantDto(combatantId, UUID.randomUUID(), "Test", 0, 0,
                10, 10, 0, "MONSTER", null, false,
                null, null, null, null,
                false, false, false, List.of(),
                null, false, 0, 0, 0, 0, null,
                null, null, null, null, null, null, null, false);

        when(service.setInitiative(eq(combatantId), eq(0))).thenReturn(dto);
        when(service.setInitiative(eq(combatantId), eq(-3))).thenReturn(dto);
        when(service.setInitiative(eq(combatantId), isNull())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/combatants/{id}/initiative", combatantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiative\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/combatants/{id}/initiative", combatantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiative\":-3}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/combatants/{id}/initiative", combatantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiative\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAddThreatCombatant() throws Exception {
        UUID encId = UUID.randomUUID();
        UUID threatId = UUID.randomUUID();
        CombatantDto trapCombatant = new CombatantDto(
                UUID.randomUUID(), encId, "Spike Trap", 15, 0,
                0, 0, 0, "TRAP", null, false,
                null, null, null, null,
                false, false, false, List.of(),
                null, false, 0, 0, 0, 0, null,
                null, null, null, null,
                dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind.TRAP, threatId, null, false);
        when(service.addThreatCombatant(eq(encId), any())).thenReturn(trapCombatant);

        mockMvc.perform(post("/api/v1/encounters/{id}/combatants/from-threat", encId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"threatKind\":\"TRAP\",\"threatId\":\"" + threatId
                                + "\",\"name\":null,\"initiative\":15,\"waveId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("TRAP"))
                .andExpect(jsonPath("$.threatKind").value("TRAP"))
                .andExpect(jsonPath("$.name").value("Spike Trap"))
                .andExpect(jsonPath("$.maxHp").value(0));
    }

    @Test
    void shouldListPlacements() throws Exception {
        UUID encId = UUID.randomUUID();
        when(placements.list(encId)).thenReturn(List.of(
                new PlacementDto(UUID.randomUUID(), encId, UUID.randomUUID(), UUID.randomUUID(),
                        48, 48, 1, 1, "#55aa55", null)));

        mockMvc.perform(get("/api/v1/encounters/{id}/placements", encId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldUpsertPlacement() throws Exception {
        UUID encId = UUID.randomUUID();
        UUID combatantId = UUID.randomUUID();
        PlacementDto dto = new PlacementDto(UUID.randomUUID(), encId, combatantId, UUID.randomUUID(),
                100, 200, 1, 1, "#55aa55", null);
        when(placements.upsert(eq(encId), eq(combatantId), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/encounters/{id}/combatants/{combatantId}/placement", encId, combatantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionX\":100,\"positionY\":200,\"sizeCols\":1,\"sizeRows\":1,\"color\":\"#55aa55\",\"icon\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionX").value(100));
    }

    @Test
    void shouldMovePlacement() throws Exception {
        UUID encId = UUID.randomUUID();
        UUID combatantId = UUID.randomUUID();
        PlacementDto dto = new PlacementDto(UUID.randomUUID(), encId, combatantId, UUID.randomUUID(),
                150, 250, 1, 1, "#55aa55", null);
        when(placements.move(eq(encId), eq(combatantId), eq(150), eq(250))).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/encounters/{id}/combatants/{combatantId}/placement/move", encId, combatantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionX\":150,\"positionY\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionX").value(150));
    }

    @Test
    void shouldRemovePlacement() throws Exception {
        UUID encId = UUID.randomUUID();
        UUID combatantId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/encounters/{id}/combatants/{combatantId}/placement", encId, combatantId))
                .andExpect(status().isNoContent());

        verify(placements).remove(encId, combatantId);
    }

    @Test
    void shouldAutoPlace() throws Exception {
        UUID encId = UUID.randomUUID();
        when(placements.autoPlaceUnplaced(encId)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/encounters/{id}/placements/auto", encId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPlaceMissingParty() throws Exception {
        UUID encId = UUID.randomUUID();
        when(service.placeMissingParty(encId)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/encounters/{id}/placements/party", encId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetReadiness() throws Exception {
        UUID encId = UUID.randomUUID();
        when(placements.readiness(encId)).thenReturn(
                new EncounterReadinessDto(encId, true, UUID.randomUUID(), 0, 0, 0, List.of()));

        mockMvc.perform(get("/api/v1/encounters/{id}/readiness", encId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRun").value(true));
    }

    @Test
    void shouldChangeMap() throws Exception {
        UUID encId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        when(placements.changeMapAndResetPlacements(eq(encId), eq(mapId))).thenReturn(
                new EncounterReadinessDto(encId, true, mapId, 0, 0, 0, List.of()));

        mockMvc.perform(put("/api/v1/encounters/{id}/map", encId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mapId\":\"" + mapId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapId").value(mapId.toString()));
    }
}
