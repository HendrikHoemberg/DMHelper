package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapSettingsCommand;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService.RuntimeTokenDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService.RuntimeTokenSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(GameMapApiController.class)
class GameMapApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GameMapService service;
    @MockitoBean private RuntimeTokenProjectionService runtimeTokens;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private GameMap map(String name, long version) {
        GameMap m = new GameMap();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setGridWidth(30);
        m.setGridHeight(20);
        m.setCellSizePx(48);
        m.setVersion(version);
        return m;
    }

    @Test
    void shouldListMaps() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.findByCampaignId(campaignId)).thenReturn(List.of(map("Tavern", 0)));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/maps", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tavern"))
                .andExpect(jsonPath("$[0].gridType").value("SQUARE"))
                .andExpect(jsonPath("$[0].movementMode").value("GRID"));
    }

    @Test
    void shouldCreateMap() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.create(eq(campaignId), eq("Tavern"), eq(30), eq(20), eq(48)))
                .thenReturn(map("Tavern", 0));
        String body = """
                {"name":"Tavern","gridWidth":30,"gridHeight":20,"cellSizePx":48}""";

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/maps", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tavern"));
    }

    @Test
    void shouldRejectBlankMapName() throws Exception {
        String body = """
                {"name":"   "}""";

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/maps", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404ForMissingMap() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new NotFoundException("Map not found: " + id));

        mockMvc.perform(get("/api/v1/maps/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetDocumentWithVersion() throws Exception {
        GameMap m = map("Tavern", 3);
        when(service.findById(m.getId())).thenReturn(m);
        when(service.getDocument(m.getId()))
                .thenReturn(MapDocumentDto.createDefault(30, 20, 48));

        mockMvc.perform(get("/api/v1/maps/{id}/document", m.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.document.schemaVersion").value(2))
                .andExpect(jsonPath("$.document.layers.length()").value(3));
    }

    @Test
    void shouldSaveDocumentAndReturnNewVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateDocument(eq(id), anyString(), eq(3L))).thenReturn(4L);
        String doc = """
                {"schemaVersion":1,"grid":{"width":30,"height":20,"cellSizePx":48},"layers":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/document", id)
                        .param("expectedVersion", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void shouldReturn409OnStaleDocumentVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateDocument(eq(id), anyString(), eq(1L)))
                .thenThrow(new OptimisticLockingFailureException("stale"));
        String doc = """
                {"schemaVersion":1,"grid":{"width":30,"height":20,"cellSizePx":48},"layers":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/document", id)
                        .param("expectedVersion", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doc))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDeleteMap() throws Exception {
        mockMvc.perform(delete("/api/v1/maps/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldUpdateSettings() throws Exception {
        UUID id = UUID.randomUUID();
        GameMap m = map("SettingsMap", 2);
        m.setGridWidth(40);
        m.setGridHeight(30);
        m.setCellSizePx(64);

        MapDocumentDto doc = MapDocumentDto.createDefault(40, 30, 64);
        var result = new GameMapService.MapSettingsResult(3, m, doc);
        when(service.updateSettings(eq(id), any(MapSettingsCommand.class))).thenReturn(result);

        String body = """
                {"expectedVersion":2,"gridWidth":40,"gridHeight":30,"cellSizePx":64,"resizeMode":"PRESERVE","tokenResolutions":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/settings", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.document.grid.width").value(40))
                .andExpect(jsonPath("$.document.grid.height").value(30))
                .andExpect(jsonPath("$.document.grid.cellSizePx").value(64))
                .andExpect(jsonPath("$.document.grid.movementMode").value("GRID"))
                .andExpect(jsonPath("$.document.grid.showGrid").value(true))
                .andExpect(jsonPath("$.map.movementMode").value("GRID"))
                .andExpect(jsonPath("$.map.showGrid").value(true));
    }

    @Test
    void shouldReturn400ForInvalidSettingsDimensions() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateSettings(eq(id), any(MapSettingsCommand.class)))
                .thenThrow(new IllegalArgumentException("cellSizePx must be positive"));

        String body = """
                {"expectedVersion":1,"gridWidth":0,"gridHeight":0,"cellSizePx":0,"resizeMode":"PRESERVE","tokenResolutions":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/settings", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409ForStaleSettingsVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateSettings(eq(id), any(MapSettingsCommand.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        String body = """
                {"expectedVersion":1,"gridWidth":40,"gridHeight":30,"cellSizePx":64,"resizeMode":"PRESERVE","tokenResolutions":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/settings", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldUpdateMovementMode() throws Exception {
        UUID id = UUID.randomUUID();
        GameMap m = map("Tavern", 0);
        m.setMovementMode("FREEFORM");
        m.setShowGrid(false);
        when(service.updateMode(eq(id), eq("FREEFORM"), eq(false))).thenReturn(m);

        mockMvc.perform(patch("/api/v1/maps/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movementMode\":\"FREEFORM\",\"showGrid\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementMode").value("FREEFORM"))
                .andExpect(jsonPath("$.showGrid").value(false));
    }

    @Test
    void shouldReturnRuntimeTokens() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        RuntimeTokenDto dto = new RuntimeTokenDto(
                tokenId, RuntimeTokenSource.MARKER, null,
                "Chest", "OBJECT", 10, 20, 1, 1, "#888", null,
                false, null, null, false, false, List.of());
        when(runtimeTokens.project(eq(mapId), isNull())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/maps/{mapId}/runtime-tokens", mapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tokenId.toString()))
                .andExpect(jsonPath("$[0].source").value("MARKER"))
                .andExpect(jsonPath("$[0].name").value("Chest"));
    }

    @Test
    void shouldReturnRuntimeTokensWithEncounterId() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        when(runtimeTokens.project(eq(mapId), eq(encounterId))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/maps/{mapId}/runtime-tokens", mapId)
                        .param("encounterId", encounterId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
