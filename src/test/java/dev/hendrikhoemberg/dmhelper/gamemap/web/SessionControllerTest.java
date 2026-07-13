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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameMapService gameMapService;

    @Test
    void redirectsToFirstMapPlayWhenMapsExist() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        GameMap map = new GameMap();
        map.setId(mapId);
        when(gameMapService.findByCampaignId(campaignId)).thenReturn(List.of(map));

        mockMvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/maps/" + mapId + "/play"));
    }

    @Test
    void redirectsToMapsListWhenNoMaps() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(gameMapService.findByCampaignId(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/maps"));
    }
}
