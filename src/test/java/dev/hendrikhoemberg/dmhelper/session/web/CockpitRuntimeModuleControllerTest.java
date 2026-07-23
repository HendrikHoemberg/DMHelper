package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitModuleMode;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.PartyMemberView;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.PartyView;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.QuickNotesView;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.QuickNoteView;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.ReferenceView;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.AudioView;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService.SessionLogView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(CockpitRuntimeModuleController.class)
class CockpitRuntimeModuleControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CockpitRuntimeModuleViewService views;

    @MockitoBean
    private CockpitModuleRegistry registry;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void storyRouteReturnsModuleFragment() throws Exception {
        when(registry.require("story")).thenReturn(CockpitModuleRegistry.standard().require("story"));
        var view = new CockpitRuntimeModuleViewService.StoryView(
                UUID.randomUUID(), "Test Scene", null, null, "Read aloud text",
                List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of(), false,
                false, false, UUID.randomUUID(), null, null);
        when(views.story(campaignId)).thenReturn(view);

        mvc.perform(get("/campaigns/{cid}/session/modules/story", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_story :: body"));
    }

    @Test
    void mapRouteReturnsModuleFragment() throws Exception {
        when(registry.require("map")).thenReturn(CockpitModuleRegistry.standard().require("map"));
        when(views.map(any(), any())).thenReturn(null);

        mvc.perform(get("/campaigns/{cid}/session/modules/map", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_map :: body"));
    }

    @Test
    void encounterRouteReturnsModuleFragment() throws Exception {
        when(registry.require("encounter")).thenReturn(CockpitModuleRegistry.standard().require("encounter"));
        when(views.encounter(campaignId)).thenReturn(null);

        mvc.perform(get("/campaigns/{cid}/session/modules/encounter", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_encounter :: body"));
    }

    @Test
    void sessionPlanRouteReturnsModuleFragment() throws Exception {
        when(registry.require("session-plan")).thenReturn(CockpitModuleRegistry.standard().require("session-plan"));
        when(views.sessionPlan(campaignId)).thenReturn(null);

        mvc.perform(get("/campaigns/{cid}/session/modules/session-plan", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_session-plan :: body"));
    }

    @Test
    void partyRouteReturnsModuleFragment() throws Exception {
        when(registry.require("party")).thenReturn(CockpitModuleRegistry.standard().require("party"));
        when(views.party(campaignId)).thenReturn(new PartyView(List.of()));

        mvc.perform(get("/campaigns/{cid}/session/modules/party", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_party :: body"));
    }

    @Test
    void quickNotesRouteReturnsModuleFragment() throws Exception {
        when(registry.require("quick-notes")).thenReturn(CockpitModuleRegistry.standard().require("quick-notes"));
        when(views.quickNotes(campaignId)).thenReturn(new QuickNotesView(List.of()));

        mvc.perform(get("/campaigns/{cid}/session/modules/quick-notes", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_quick-notes :: body"));
    }

    @Test
    void presentationRouteReturnsModuleFragment() throws Exception {
        when(registry.require("presentation")).thenReturn(CockpitModuleRegistry.standard().require("presentation"));
        when(views.presentation(campaignId)).thenReturn(null);

        mvc.perform(get("/campaigns/{cid}/session/modules/presentation", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_presentation :: body"));
    }

    @Test
    void referenceRouteReturnsModuleFragment() throws Exception {
        when(registry.require("reference")).thenReturn(CockpitModuleRegistry.standard().require("reference"));
        when(views.reference(campaignId)).thenReturn(new ReferenceView());

        mvc.perform(get("/campaigns/{cid}/session/modules/reference", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_reference :: body"));
    }

    @Test
    void audioRouteReturnsModuleFragment() throws Exception {
        when(registry.require("audio")).thenReturn(CockpitModuleRegistry.standard().require("audio"));
        when(views.audio(campaignId)).thenReturn(new AudioView());

        mvc.perform(get("/campaigns/{cid}/session/modules/audio", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_audio :: body"));
    }

    @Test
    void sessionLogRouteReturnsModuleFragment() throws Exception {
        when(registry.require("session-log")).thenReturn(CockpitModuleRegistry.standard().require("session-log"));
        var sessionLogView = new dev.hendrikhoemberg.dmhelper.session.runtime.SessionLogModuleService.SessionLogView(
                "IDLE", null, List.of(), List.of(), 0, null);
        when(views.sessionLog(any(), any())).thenReturn(sessionLogView);

        mvc.perform(get("/campaigns/{cid}/session/modules/session-log", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-cockpit-module-fragment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-module-content-root")))
                .andExpect(view().name("session/modules/_session-log :: body"));
    }

    @Test
    void unknownModuleKeyReturns404() throws Exception {
        when(registry.require("unknown")).thenThrow(new IllegalArgumentException("Unknown cockpit module: unknown"));

        mvc.perform(get("/campaigns/{cid}/session/modules/unknown", campaignId)
                        .param("mode", "STANDARD"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedModeReturns400() throws Exception {
        when(registry.require("map")).thenReturn(CockpitModuleRegistry.standard().require("map"));

        mvc.perform(get("/campaigns/{cid}/session/modules/map", campaignId)
                        .param("mode", "COMPACT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapRouteReturns404ForForeignMapId() throws Exception {
        UUID foreignMapId = UUID.randomUUID();
        when(registry.require("map")).thenReturn(CockpitModuleRegistry.standard().require("map"));
        when(views.map(any(), any())).thenThrow(new NotFoundException("Map not found in campaign"));

        mvc.perform(get("/campaigns/{cid}/session/modules/map", campaignId)
                        .param("mapId", foreignMapId.toString())
                        .param("mode", "STANDARD"))
                .andExpect(status().isNotFound());
    }

}
