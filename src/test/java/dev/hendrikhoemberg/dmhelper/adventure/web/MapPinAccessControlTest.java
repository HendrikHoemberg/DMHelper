package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.threat.service.MapPinDto;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinService;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinWrite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dmhelper.pin-enabled=false")
class MapPinAccessControlTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MapThreatPinService mapThreatPinService;

    @Test
    void returnsPinsSuccessfullyWhenPinDisabled() throws Exception {
        when(mapThreatPinService.listCombinedPins(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/maps/{id}/pins", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void writeAndDeleteRoutesSucceedWhenPinDisabled() throws Exception {
        UUID mapId = UUID.randomUUID();
        UUID pinId = UUID.randomUUID();
        UUID threatId = UUID.randomUUID();

        MapPinDto dto = new MapPinDto(
                MapPinDto.KIND_THREAT, pinId, "k", 1, 1, "t",
                null, null, null, threatId);
        when(mapThreatPinService.create(eq(mapId), any(MapThreatPinWrite.class))).thenReturn(dto);
        when(mapThreatPinService.update(eq(mapId), eq(pinId), any(MapThreatPinWrite.class))).thenReturn(dto);

        String body = """
                {
                  "key": "k",
                  "threatKind": "TRAP",
                  "threatId": "%s",
                  "x": 1,
                  "y": 1,
                  "label": "t",
                  "sortOrder": 0
                }
                """.formatted(threatId);

        mockMvc.perform(post("/api/v1/maps/{id}/pins", mapId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/maps/{id}/pins/{pinId}", mapId, pinId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/maps/{id}/pins/{pinId}", mapId, pinId))
                .andExpect(status().isNoContent());
    }
}
