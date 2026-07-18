package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.service.MapPinDto;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinService;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinWrite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MapPinApiController.class)
class MapPinApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MapThreatPinService mapThreatPinService;

    @Test
    void returnsCombinedSceneAndThreatPinsAsJson() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        UUID threatPinId = UUID.randomUUID();
        UUID threatId = UUID.randomUUID();

        MapPinDto scenePin = new MapPinDto(
                MapPinDto.KIND_SCENE, sceneId, "14", 576, 240, "Throne Room",
                sceneId, "14", null, null);
        MapPinDto threatPin = new MapPinDto(
                MapPinDto.KIND_THREAT, threatPinId, "needle-pin", 100, 200, "Needle",
                null, null, ThreatKind.TRAP, threatId);

        when(mapThreatPinService.listCombinedPins(mapId)).thenReturn(List.of(scenePin, threatPin));

        mockMvc.perform(get("/api/v1/maps/{id}/pins", mapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pinKind").value("SCENE"))
                .andExpect(jsonPath("$[0].sceneId").value(sceneId.toString()))
                .andExpect(jsonPath("$[0].sceneKey").value("14"))
                .andExpect(jsonPath("$[0].x").value(576))
                .andExpect(jsonPath("$[0].y").value(240))
                .andExpect(jsonPath("$[0].title").value("Throne Room"))
                .andExpect(jsonPath("$[1].pinKind").value("THREAT"))
                .andExpect(jsonPath("$[1].key").value("needle-pin"))
                .andExpect(jsonPath("$[1].threatKind").value("TRAP"))
                .andExpect(jsonPath("$[1].threatId").value(threatId.toString()))
                .andExpect(jsonPath("$[1].x").value(100))
                .andExpect(jsonPath("$[1].y").value(200));
    }

    @Test
    void createsThreatPin() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID threatId = UUID.randomUUID();
        UUID pinId = UUID.randomUUID();

        MapPinDto created = new MapPinDto(
                MapPinDto.KIND_THREAT, pinId, "door-needle", 48, 96, "Door",
                null, null, ThreatKind.TRAP, threatId);
        when(mapThreatPinService.create(eq(mapId), any(MapThreatPinWrite.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/maps/{id}/pins", mapId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "door-needle",
                                  "threatKind": "TRAP",
                                  "threatId": "%s",
                                  "x": 48,
                                  "y": 96,
                                  "label": "Door",
                                  "sortOrder": 0
                                }
                                """.formatted(threatId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pinKind").value("THREAT"))
                .andExpect(jsonPath("$.id").value(pinId.toString()))
                .andExpect(jsonPath("$.key").value("door-needle"));
    }

    @Test
    void updatesThreatPin() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID pinId = UUID.randomUUID();
        UUID threatId = UUID.randomUUID();

        MapPinDto updated = new MapPinDto(
                MapPinDto.KIND_THREAT, pinId, "door-needle", 64, 128, "Updated",
                null, null, ThreatKind.TRAP, threatId);
        when(mapThreatPinService.update(eq(mapId), eq(pinId), any(MapThreatPinWrite.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/maps/{id}/pins/{pinId}", mapId, pinId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "door-needle",
                                  "threatKind": "TRAP",
                                  "threatId": "%s",
                                  "x": 64,
                                  "y": 128,
                                  "label": "Updated",
                                  "sortOrder": 1
                                }
                                """.formatted(threatId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.x").value(64));
    }

    @Test
    void deletesThreatPin() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID pinId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/maps/{id}/pins/{pinId}", mapId, pinId))
                .andExpect(status().isNoContent());

        verify(mapThreatPinService).delete(mapId, pinId);
    }
}
