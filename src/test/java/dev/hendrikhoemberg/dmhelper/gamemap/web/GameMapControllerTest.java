package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameMapController.class)
class GameMapControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GameMapService service;

    private GameMap map(String name) {
        GameMap m = new GameMap();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setGridWidth(30);
        m.setGridHeight(20);
        m.setCellSizePx(48);
        return m;
    }

    @Test
    void shouldRenderMapList() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.findByCampaignId(campaignId)).thenReturn(List.of(map("Tavern")));

        mockMvc.perform(get("/campaigns/{campaignId}/maps", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/list"));
    }

    @Test
    void shouldRenderNewMapForm() throws Exception {
        mockMvc.perform(get("/campaigns/{campaignId}/maps/new", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/_form :: form"));
    }

    @Test
    void shouldCreateMapAndReturnCard() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.create(eq(campaignId), eq("Tavern"), eq(30), eq(20), eq(48)))
                .thenReturn(map("Tavern"));

        mockMvc.perform(post("/campaigns/{campaignId}/maps", campaignId)
                        .param("name", "Tavern"))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/_card :: card"));
    }

    @Test
    void shouldRenderEditorPage() throws Exception {
        GameMap m = map("Tavern");
        when(service.findById(m.getId())).thenReturn(m);

        mockMvc.perform(get("/campaigns/{campaignId}/maps/{mapId}/edit", UUID.randomUUID(), m.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/editor"));
    }

    @Test
    void shouldDeleteMap() throws Exception {
        mockMvc.perform(delete("/campaigns/{campaignId}/maps/{mapId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRedirectPlayToSessionCockpit() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();

        mockMvc.perform(get("/campaigns/{campaignId}/maps/{mapId}/play", campaignId, mapId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/session?mapId=" + mapId));
    }
}
