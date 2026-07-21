package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransition;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.service.SessionPlanService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.SessionWorkspace;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.StructuredSceneView;
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

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void rendersCockpitEvenWhenNoMapExists() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        when(workspaces.load(campaignId, null)).thenReturn(ws);
        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/cockpit"))
                .andExpect(model().attribute("workspace", ws));
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
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        var adv = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
        adv.setId(UUID.randomUUID());
        adv.setName("Test Adv");
        var ch = new dev.hendrikhoemberg.dmhelper.adventure.data.Chapter();
        ch.setId(UUID.randomUUID());
        ch.setTitle("Ch 1");
        ch.setAdventure(adv);
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        scene.setTitle("Throne Room");
        scene.setChapter(ch);
        SceneTransition transition = new SceneTransition();
        transition.setId(UUID.randomUUID());
        transition.setKind(dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransitionKind.CHOICE);
        transition.setLabel("Go outside");
        Scene targetScene = new Scene();
        targetScene.setId(UUID.randomUUID());
        targetScene.setTitle("Next Room");
        transition.setTargetScene(targetScene);
        scene.setTransitions(List.of(transition));
        StructuredSceneView ssv = new StructuredSceneView(scene,
                scene.getSections(), scene.getChecks(),
                scene.getParticipants(), scene.getTransitions(),
                scene.getLinks(), java.util.Map.of());
        SessionWorkspace ws = new SessionWorkspace(
                campaign, CampaignSession.idle(campaign),
                null, SessionWorkspaceService.SelectionSource.NONE,
                scene, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12),
                ssv, List.of(), List.of());
        when(workspaces.load(campaignId, null)).thenReturn(ws);

        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workspace"));
    }

    @Test
    void storyRailFragmentReturnsPartialHtml() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        when(workspaces.load(campaignId, null)).thenReturn(ws);
        mvc.perform(get("/campaigns/{id}/session/rails/story", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/_story-rail :: story"));
    }

    @Test
    void encounterRailFragmentReturnsPartialHtml() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        when(workspaces.load(campaignId, null)).thenReturn(ws);
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
