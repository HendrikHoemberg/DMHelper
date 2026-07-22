package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneTransitionService;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(SessionApiController.class)
class SessionApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionLifecycleService lifecycle;

    @MockitoBean
    private AdventureService adventures;

    @MockitoBean
    private SessionWorkspaceService workspaces;

    @MockitoBean
    private SceneTransitionService sceneTransitionService;

    @MockitoBean
    private QuestService questService;

    @MockitoBean
    private SceneEncounterSeedService encounterSeeder;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void startsWithNullableMapAndReturnsTypedState() throws Exception {
        CampaignSession running = mock(CampaignSession.class);
        when(running.getStatus()).thenReturn(CampaignSession.Status.RUNNING);
        when(running.getWorkspaceMap()).thenReturn(null);
        when(running.getPresentationMode()).thenReturn(CampaignSession.PresentationMode.CURTAIN);
        when(running.getAttendees()).thenReturn(List.of());
        when(running.getDraftBody()).thenReturn(null);
        when(lifecycle.start(campaignId, null)).thenReturn(running);

        mvc.perform(post("/api/v1/campaigns/{id}/session/start", campaignId)
                        .contentType(APPLICATION_JSON).content("{\"mapId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.presentationMode").value("CURTAIN"))
                .andExpect(jsonPath("$.attendeeIds").isArray())
                .andExpect(jsonPath("$.draftBody").doesNotExist());
    }

    @Test
    void followTransitionReturnsUpdatedScene() throws Exception {
        var transitionId = UUID.randomUUID();
        var targetScene = new dev.hendrikhoemberg.dmhelper.adventure.data.Scene();
        targetScene.setId(UUID.randomUUID());
        targetScene.setTitle("Next Room");
        when(sceneTransitionService.followTransition(campaignId, transitionId)).thenReturn(targetScene);
        SessionWorkspaceService.SessionWorkspace ws = mock(SessionWorkspaceService.SessionWorkspace.class);
        when(workspaces.load(any(), any())).thenReturn(ws);

        mvc.perform(post("/api/v1/campaigns/{id}/session/current-scene/follow-transition", campaignId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"transitionId\":\"" + transitionId + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void objectiveStatusChangeAppearsInDraft() throws Exception {
        var objectiveId = UUID.randomUUID();
        var objective = new QuestObjective();
        objective.setId(objectiveId);
        objective.setStatus(QuestObjectiveStatus.COMPLETED);
        when(questService.setObjectiveStatus(campaignId, objectiveId, QuestObjectiveStatus.COMPLETED))
                .thenReturn(new QuestService.ObjectiveStatusUpdate(objective, UUID.randomUUID()));

        mvc.perform(put("/api/v1/campaigns/{id}/session/quests/objectives/{oid}/status", campaignId, objectiveId)
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidSceneDirectionBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{id}/session/current-scene/step", campaignId)
                        .contentType(APPLICATION_JSON).content("{\"direction\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adventures);
    }

    @Test
    void seedsTheCurrentStorySceneAndReturnsTheTypedReport() throws Exception {
        UUID sceneId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        when(encounterSeeder.seedFromScene(campaignId, sceneId))
                .thenReturn(new SceneEncounterSeedService.SeedResult(
                        encounterId, "Encounter: Klarg", 4,
                        List.of("Unresolved wolf"), false));

        mvc.perform(post("/api/v1/campaigns/{campaignId}/session/scenes/{sceneId}/seed-encounter",
                        campaignId, sceneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterId").value(encounterId.toString()))
                .andExpect(jsonPath("$.combatantsAdded").value(4))
                .andExpect(jsonPath("$.skippedParticipants[0]").value("Unresolved wolf"))
                .andExpect(jsonPath("$.alreadyExisted").value(false));

        verify(encounterSeeder).seedFromScene(campaignId, sceneId);
    }
}
