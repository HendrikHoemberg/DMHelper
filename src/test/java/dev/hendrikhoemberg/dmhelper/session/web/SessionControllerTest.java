package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitBuiltInPresetCatalog;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleDefinition;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.SessionWorkspace;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.StructuredSceneView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private SessionWorkspaceService workspaces;
    @MockitoBean private AdventureService adventures;
    @MockitoBean private SceneEncounterSeedService encounterSeeder;
    @MockitoBean private CockpitModuleRegistry cockpitModules;
    @MockitoBean private CockpitLayoutPresetService cockpitPresets;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private CockpitRuntimeModuleViewService moduleViews;

    @MockitoBean(name = "calendarService")
    private CalendarService calendarService;

    private final UUID campaignId = UUID.randomUUID();
    private final CockpitModuleRegistry standardRegistry = CockpitModuleRegistry.standard();
    private final CockpitBuiltInPresetCatalog builtIns = new CockpitBuiltInPresetCatalog();

    @BeforeEach
    void stubCockpitLayoutCatalog() {
        List<CockpitModuleDefinition> modules = standardRegistry.all();
        when(cockpitModules.all()).thenReturn(modules);
        List<CockpitLayoutPresetService.PresetDto> presets = builtIns.all().stream()
                .map(preset -> new CockpitLayoutPresetService.PresetDto(
                        preset.key(), null, preset.name(), true, 0, preset.layout(), List.of()))
                .toList();
        when(cockpitPresets.list()).thenReturn(presets);
    }

    @Test
    void rendersCockpitEvenWhenNoMapExists() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        when(workspaces.load(campaignId, null)).thenReturn(ws);
        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/cockpit"))
                .andExpect(model().attribute("workspace", ws))
                .andExpect(model().attributeExists("cockpitModules", "cockpitPresets",
                        "cockpitDefaultPresetKey", "cockpitModuleByKey"));
    }

    @Test
    void testProfileInjectsFakeAudioProviderWithoutARequestSwitch() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        when(workspaces.load(campaignId, null)).thenReturn(ws);

        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-test-audio-provider=\"FAKE\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/js/audio-provider-fake.js")));
    }

    @Test
    void passesExplicitMapSelectionToWorkspacePolicy() throws Exception {
        UUID mapId = UUID.randomUUID();
        when(workspaces.load(campaignId, mapId)).thenReturn(mapWorkspace());
        mvc.perform(get("/campaigns/{id}/session", campaignId).param("mapId", mapId.toString()))
                .andExpect(status().isOk());
        verify(workspaces).load(campaignId, mapId);
    }

    @Test
    void structuredSceneViewPopulatedWhenCurrentScene() throws Exception {
        SessionWorkspace ws = workspaceWithCurrentScene();
        when(workspaces.load(campaignId, null)).thenReturn(ws);

        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workspace"));
    }

    @Test
    void storyModuleOffersEncounterCreationForAnEligibleCurrentScene() throws Exception {
        UUID sceneId = UUID.randomUUID();
        var view = new CockpitRuntimeModuleViewService.StoryView(
                sceneId, "Throne Room", null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of(),
                true, false, false, UUID.randomUUID(), null, null, null);
        when(moduleViews.story(campaignId)).thenReturn(view);

        mvc.perform(get("/campaigns/{id}/session/rails/story", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Create an encounter here")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-scene-id=\"" + sceneId + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "seedCurrentScene($el.dataset.sceneId)")));
    }

    @Test
    void storyRailFragmentReturnsPartialHtml() throws Exception {
        mvc.perform(get("/campaigns/{id}/session/rails/story", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/modules/_story :: body"));
    }

    @Test
    void encounterRailFragmentReturnsPartialHtml() throws Exception {
        var view = new CockpitRuntimeModuleViewService.EncounterView(
                null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
        when(moduleViews.encounter(campaignId)).thenReturn(view);
        mvc.perform(get("/campaigns/{id}/session/rails/encounter", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/_encounter-rail :: encounters"));
    }

    @Test
    void attendanceEditorIncludesAnInactiveStoredAttendee() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        PartyMember inactive = new PartyMember();
        inactive.setId(UUID.randomUUID());
        inactive.setCharacterName("Retired Hero");
        inactive.setActive(false);
        ws.session().setStatus(CampaignSession.Status.RUNNING);
        ws.session().getAttendees().add(inactive);
        when(workspaces.load(campaignId, null)).thenReturn(ws);

        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attribute("attendanceMembers", List.of(inactive)))
                .andExpect(model().attribute("attendeeIds", List.of(inactive.getId().toString())));
    }

    private SessionWorkspace workspaceWithCurrentScene() {
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        var adventure = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
        adventure.setId(UUID.randomUUID());
        adventure.setName("Test Adventure");
        adventure.setCampaign(campaign);
        var chapter = new dev.hendrikhoemberg.dmhelper.adventure.data.Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setTitle("Chapter 1");
        chapter.setAdventure(adventure);
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        scene.setTitle("Throne Room");
        scene.setChapter(chapter);
        StructuredSceneView structured = new StructuredSceneView(scene,
                scene.getSections(), scene.getChecks(), scene.getParticipants(),
                scene.getTransitions(), scene.getLinks(), java.util.Map.of());
        return new SessionWorkspace(campaign, CampaignSession.idle(campaign),
                null, SessionWorkspaceService.SelectionSource.NONE,
                scene, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12),
                structured, List.of(), List.of());
    }

    static SessionWorkspace emptyWorkspace() {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        return new SessionWorkspace(
                campaign,
                CampaignSession.idle(campaign),
                null,
                SessionWorkspaceService.SelectionSource.NONE,
                null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12),
                null, List.of(), List.of());
    }

    static SessionWorkspace mapWorkspace() {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        var map = new dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap();
        map.setId(UUID.randomUUID());
        map.setName("Test Map");
        return new SessionWorkspace(
                campaign,
                CampaignSession.idle(campaign),
                map,
                SessionWorkspaceService.SelectionSource.EXPLICIT_MAP,
                null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12),
                null, List.of(), List.of());
    }
}
