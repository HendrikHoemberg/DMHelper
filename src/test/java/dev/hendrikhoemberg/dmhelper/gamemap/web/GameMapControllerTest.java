package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameMapController.class)
class GameMapControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GameMapService service;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private SceneRepository sceneRepository;

    @MockitoBean
    private EncounterRepository encounterRepository;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        when(campaignRepository.findById(any())).thenReturn(Optional.of(campaign));
    }

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
    void shouldTruncateLongReferenceLists() throws Exception {
        UUID campaignId = UUID.randomUUID();
        GameMap m = map("Tavern");
        when(service.findByCampaignId(campaignId)).thenReturn(List.of(m));
        when(sceneRepository.findByMapId(m.getId())).thenReturn(List.of(
                scene("Scene A"), scene("Scene B"), scene("Scene C"), scene("Scene D")));
        when(encounterRepository.findByMapIdOrderByNameAsc(m.getId()))
                .thenReturn(List.of(encounter("Encounter E")));

        mockMvc.perform(get("/campaigns/{campaignId}/maps", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+2 more")));
    }

    private Scene scene(String title) {
        Scene s = new Scene();
        s.setTitle(title);
        return s;
    }

    private Encounter encounter(String name) {
        Encounter e = new Encounter();
        e.setName(name);
        return e;
    }

    @Test
    void shouldRenderNewMapPageForNormalNavigation() throws Exception {
        mockMvc.perform(get("/campaigns/{campaignId}/maps/new", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/new"));
    }

    @Test
    void shouldRenderNewMapFormFragmentForHtmxNavigation() throws Exception {
        mockMvc.perform(get("/campaigns/{campaignId}/maps/new", UUID.randomUUID())
                        .header("HX-Request", "true"))
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
                .andExpect(view().name("maps/editor"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Threat pins")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("threat-pin")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-map-control=\"map-section\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-image-control=\"background-section\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gridWidth")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gridHeight")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cellSizePx")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fit-inside")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fill-cover")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("imageWidth")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("imageHeight")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("imageLocked")));
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
