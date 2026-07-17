package dev.hendrikhoemberg.dmhelper.world.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorldController.class)
class WorldControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WorldService worldService;
    @MockitoBean private CampaignRepository campaignRepository;

    private UUID campaignId, npcId, locationId, factionId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        npcId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        factionId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    }

    // ---- NPCs ----

    @Test
    void listNpcsRendersPage() throws Exception {
        when(worldService.getNpcs(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/npcs", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("npcs"))
                .andExpect(view().name("world/npcs-list"));
    }

    @Test
    void npcDetailRendersPage() throws Exception {
        WorldNpc npc = new WorldNpc();
        npc.setId(npcId);
        npc.setName("Test NPC");
        when(worldService.getNpc(campaignId, npcId)).thenReturn(npc);

        mockMvc.perform(get("/campaigns/{cid}/world/npcs/{nid}", campaignId, npcId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("npc"))
                .andExpect(view().name("world/npcs-detail"));
    }

    @Test
    void newNpcFormRendersPage() throws Exception {
        when(worldService.getFactions(campaignId)).thenReturn(List.of());
        when(worldService.getLocations(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/npcs/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("npc"))
                .andExpect(view().name("world/npcs-form"));
    }

    @Test
    void createNpcRedirectsToDetail() throws Exception {
        WorldNpc npc = new WorldNpc();
        npc.setId(npcId);
        npc.setName("New NPC");
        when(worldService.createNpc(any(), any())).thenReturn(npc);

        mockMvc.perform(post("/campaigns/{cid}/world/npcs", campaignId)
                        .param("name", "New NPC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/npcs/" + npcId));
    }

    @Test
    void editNpcFormRendersPage() throws Exception {
        WorldNpc npc = new WorldNpc();
        npc.setId(npcId);
        npc.setName("Test NPC");
        when(worldService.getNpc(campaignId, npcId)).thenReturn(npc);
        when(worldService.getFactions(campaignId)).thenReturn(List.of());
        when(worldService.getLocations(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/npcs/{nid}/edit", campaignId, npcId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("npc"))
                .andExpect(view().name("world/npcs-form"));
    }

    @Test
    void updateNpcRedirectsToDetail() throws Exception {
        mockMvc.perform(put("/campaigns/{cid}/world/npcs/{nid}", campaignId, npcId)
                        .param("name", "Updated NPC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/npcs/" + npcId));
    }

    @Test
    void deleteNpcReturnsRedirect() throws Exception {
        mockMvc.perform(delete("/campaigns/{cid}/world/npcs/{nid}", campaignId, npcId))
                .andExpect(status().isOk())
                .andExpect(header().exists("HX-Redirect"));
    }

    // ---- Locations ----

    @Test
    void listLocationsRendersPage() throws Exception {
        when(worldService.getLocations(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/locations", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("locations"))
                .andExpect(view().name("world/locations-list"));
    }

    @Test
    void locationDetailRendersPage() throws Exception {
        WorldLocation loc = new WorldLocation();
        loc.setId(locationId);
        loc.setName("Test Location");
        when(worldService.getLocation(campaignId, locationId)).thenReturn(loc);

        mockMvc.perform(get("/campaigns/{cid}/world/locations/{lid}", campaignId, locationId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("location"))
                .andExpect(view().name("world/locations-detail"));
    }

    @Test
    void newLocationFormRendersPage() throws Exception {
        when(worldService.getLocations(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/locations/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("location"))
                .andExpect(view().name("world/locations-form"));
    }

    @Test
    void createLocationRedirectsToDetail() throws Exception {
        WorldLocation loc = new WorldLocation();
        loc.setId(locationId);
        loc.setName("New Location");
        when(worldService.createLocation(any(), any())).thenReturn(loc);

        mockMvc.perform(post("/campaigns/{cid}/world/locations", campaignId)
                        .param("name", "New Location"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/locations/" + locationId));
    }

    @Test
    void editLocationFormRendersPage() throws Exception {
        WorldLocation loc = new WorldLocation();
        loc.setId(locationId);
        loc.setName("Test Location");
        when(worldService.getLocation(campaignId, locationId)).thenReturn(loc);
        when(worldService.getLocations(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/locations/{lid}/edit", campaignId, locationId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("location"))
                .andExpect(view().name("world/locations-form"));
    }

    @Test
    void updateLocationRedirectsToDetail() throws Exception {
        mockMvc.perform(put("/campaigns/{cid}/world/locations/{lid}", campaignId, locationId)
                        .param("name", "Updated Location"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/locations/" + locationId));
    }

    @Test
    void deleteLocationReturnsRedirect() throws Exception {
        mockMvc.perform(delete("/campaigns/{cid}/world/locations/{lid}", campaignId, locationId))
                .andExpect(status().isOk())
                .andExpect(header().exists("HX-Redirect"));
    }

    // ---- Factions ----

    @Test
    void listFactionsRendersPage() throws Exception {
        when(worldService.getFactions(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/factions", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("factions"))
                .andExpect(view().name("world/factions-list"));
    }

    @Test
    void factionDetailRendersPage() throws Exception {
        Faction faction = new Faction();
        faction.setId(factionId);
        faction.setName("Test Faction");
        when(worldService.getFaction(campaignId, factionId)).thenReturn(faction);
        when(worldService.getRelationships(campaignId)).thenReturn(List.of());
        when(worldService.getClocks(campaignId)).thenReturn(List.of());
        when(worldService.getNpcs(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/world/factions/{fid}", campaignId, factionId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("faction"))
                .andExpect(view().name("world/factions-detail"));
    }

    @Test
    void newFactionFormRendersPage() throws Exception {
        mockMvc.perform(get("/campaigns/{cid}/world/factions/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("faction"))
                .andExpect(view().name("world/factions-form"));
    }

    @Test
    void createFactionRedirectsToDetail() throws Exception {
        Faction faction = new Faction();
        faction.setId(factionId);
        faction.setName("New Faction");
        when(worldService.createFaction(any(), any())).thenReturn(faction);

        mockMvc.perform(post("/campaigns/{cid}/world/factions", campaignId)
                        .param("name", "New Faction"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }

    @Test
    void editFactionFormRendersPage() throws Exception {
        Faction faction = new Faction();
        faction.setId(factionId);
        faction.setName("Test Faction");
        when(worldService.getFaction(campaignId, factionId)).thenReturn(faction);

        mockMvc.perform(get("/campaigns/{cid}/world/factions/{fid}/edit", campaignId, factionId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("faction"))
                .andExpect(view().name("world/factions-form"));
    }

    @Test
    void updateFactionRedirectsToDetail() throws Exception {
        mockMvc.perform(put("/campaigns/{cid}/world/factions/{fid}", campaignId, factionId)
                        .param("name", "Updated Faction"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }

    @Test
    void deleteFactionReturnsRedirect() throws Exception {
        mockMvc.perform(delete("/campaigns/{cid}/world/factions/{fid}", campaignId, factionId))
                .andExpect(status().isOk())
                .andExpect(header().exists("HX-Redirect"));
    }

    // ---- Relationships & Clocks ----

    @Test
    void createRelationshipRedirectsToFaction() throws Exception {
        mockMvc.perform(post("/campaigns/{cid}/world/factions/{fid}/relationships", campaignId, factionId)
                        .param("kind", "ALLY")
                        .param("fromType", "NPC")
                        .param("fromId", UUID.randomUUID().toString())
                        .param("toType", "NPC")
                        .param("toId", UUID.randomUUID().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }

    @Test
    void deleteRelationshipRedirectsToFaction() throws Exception {
        UUID relId = UUID.randomUUID();
        mockMvc.perform(delete("/campaigns/{cid}/world/factions/{fid}/relationships/{rid}",
                        campaignId, factionId, relId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }

    @Test
    void createClockRedirectsToFaction() throws Exception {
        mockMvc.perform(post("/campaigns/{cid}/world/factions/{fid}/clocks", campaignId, factionId)
                        .param("title", "Test Clock")
                        .param("segments", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }

    @Test
    void updateClockRedirectsToFaction() throws Exception {
        UUID clockId = UUID.randomUUID();
        mockMvc.perform(put("/campaigns/{cid}/world/factions/{fid}/clocks/{clkid}",
                        campaignId, factionId, clockId)
                        .param("title", "Updated Clock")
                        .param("segments", "6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }

    @Test
    void deleteClockRedirectsToFaction() throws Exception {
        UUID clockId = UUID.randomUUID();
        mockMvc.perform(delete("/campaigns/{cid}/world/factions/{fid}/clocks/{clkid}",
                        campaignId, factionId, clockId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/world/factions/" + factionId));
    }
}
