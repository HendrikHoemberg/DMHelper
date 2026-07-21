package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(TokenApiController.class)
class TokenApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TokenService service;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private TokenDto token(String name) {
        return new TokenDto(UUID.randomUUID(), name, "NPC", 0, 0, 1, 1,
                "#fff", false, 10, 10, false, false, null, null);
    }

    @Test
    void shouldListTokens() throws Exception {
        UUID mapId = UUID.randomUUID();
        when(service.findByMapId(mapId)).thenReturn(List.of(token("Goblin")));

        mockMvc.perform(get("/api/v1/maps/{mapId}/tokens", mapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Goblin"));
    }

    @Test
    void shouldCreateToken() throws Exception {
        UUID mapId = UUID.randomUUID();
        TokenDto dto = token("Goblin");
        when(service.create(eq(mapId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/maps/{mapId}/tokens", mapId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Goblin\",\"kind\":\"MONSTER\",\"positionX\":100,\"positionY\":200,\"sizeCols\":1,\"sizeRows\":1,\"color\":\"#e74c3c\",\"hidden\":false,\"currentHp\":7,\"maxHp\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Goblin"));
    }

    @Test
    void shouldMoveToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.move(eq(id), any())).thenReturn(token("Moved"));

        mockMvc.perform(patch("/api/v1/tokens/{id}/move", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionX\":500,\"positionY\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Moved"));
    }

    @Test
    void shouldDeleteToken() throws Exception {
        mockMvc.perform(delete("/api/v1/tokens/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAddPartyToMap() throws Exception {
        UUID mapId = UUID.randomUUID();
        when(service.addPartyToMap(mapId)).thenReturn(List.of());
        mockMvc.perform(post("/api/v1/maps/{mapId}/tokens/add-party", mapId))
                .andExpect(status().isOk());
    }
}
