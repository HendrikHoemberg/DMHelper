package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneTransitionService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SceneController.class)
class SceneControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdventureService adventureService;
    @MockitoBean private SceneStructuredContentService structuredService;
    @MockitoBean private SceneTransitionService transitionService;
    @MockitoBean private CampaignRepository campaignRepository;
    @MockitoBean private GameMapRepository gameMapRepository;
    @MockitoBean private EncounterRepository encounterRepository;
    @MockitoBean private StatBlockRepository statBlockRepository;
    @MockitoBean private HandoutRepository handoutRepository;
    @MockitoBean private MarkdownUtil markdownUtil;

    private UUID campaignId, adventureId, sceneId, chapterId;
    private Campaign campaign;
    private Scene scene;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        adventureId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        sceneId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        Adventure a = new Adventure();
        a.setId(adventureId);
        a.setName("Module");
        a.setCampaign(campaign);

        Chapter ch = new Chapter();
        ch.setId(chapterId);
        ch.setTitle("Ch 1");
        ch.setAdventure(a);

        scene = new Scene();
        scene.setId(sceneId);
        scene.setTitle("Throne Room");
        scene.setChapter(ch);

        when(markdownUtil.toHtml(any())).thenReturn("<p>rendered</p>");
    }

    @Test
    void detailRendersScene() throws Exception {
        when(adventureService.findAdventureById(adventureId)).thenReturn(scene.getChapter().getAdventure());
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{cid}/adventures/{aid}/scenes/{sid}",
                        campaignId, adventureId, sceneId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("scene", "adventure"));
    }

    @Test
    void detailRendersQuickNotesForTheSceneTarget() throws Exception {
        when(adventureService.findAdventureById(adventureId)).thenReturn(scene.getChapter().getAdventure());
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{cid}/adventures/{aid}/scenes/{sid}",
                        campaignId, adventureId, sceneId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-target-type=\"SCENE\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-target-id=\"" + sceneId + "\"")));
    }

    @Test
    void setStatusUpdatesAndReturnsBadge() throws Exception {
        scene.setStatus(SceneStatus.VISITED);
        when(adventureService.setStatus(sceneId, SceneStatus.VISITED)).thenReturn(scene);
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);

        mockMvc.perform(put("/scenes/{id}/status", sceneId)
                        .param("status", "VISITED"))
                .andExpect(status().isOk());
    }

    @Test
    void setCurrentSceneReturnsScenePanel() throws Exception {
        when(adventureService.setCurrentScene(campaignId, sceneId)).thenReturn(scene);
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);

        mockMvc.perform(post("/campaigns/{cid}/current-scene", campaignId)
                        .param("sceneId", sceneId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void clearCurrentScene() throws Exception {
        mockMvc.perform(post("/campaigns/{cid}/current-scene", campaignId)
                        .param("sceneId", ""))
                .andExpect(status().isOk());
    }

    @Test
    void stepCurrentScene() throws Exception {
        when(adventureService.stepCurrentScene(campaignId, 1)).thenReturn(Optional.of(scene));

        mockMvc.perform(put("/campaigns/{cid}/current-scene/step", campaignId)
                        .param("direction", "1"))
                .andExpect(redirectedUrlPattern("/campaigns/" + campaignId + "/adventures/" + adventureId + "/scenes/" + sceneId + "*"));
    }

    private void setupSceneDetailMocks() {
        when(adventureService.findAdventureById(adventureId)).thenReturn(scene.getChapter().getAdventure());
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());
    }

    @Test
    void updateSectionReturnsErrorOnValidationFailure() throws Exception {
        setupSceneDetailMocks();
        doThrow(new IllegalArgumentException("Section label and body are required"))
                .when(structuredService).updateSection(any(), any(), any(), any());

        mockMvc.perform(post("/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/sections/{sectionId}",
                        campaignId, adventureId, chapterId, sceneId, UUID.randomUUID())
                        .param("kind", "READ_ALOUD")
                        .param("label", "")
                        .param("body", "")
                        .param("sortOrder", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Section label and body are required"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Section label and body are required")));
    }

    @Test
    void updateCheckReturnsErrorOnValidationFailure() throws Exception {
        setupSceneDetailMocks();
        doThrow(new NotFoundException("Check not found in scene"))
                .when(structuredService).updateCheck(any(), any(), any(), any());

        mockMvc.perform(post("/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/checks/{checkId}",
                        campaignId, adventureId, chapterId, sceneId, UUID.randomUUID())
                        .param("sortOrder", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Check not found in scene"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Check not found in scene")));
    }

    @Test
    void updateParticipantReturnsErrorOnValidationFailure() throws Exception {
        setupSceneDetailMocks();
        doThrow(new NotFoundException("Participant not found in scene"))
                .when(structuredService).updateParticipant(any(), any(), any(), any());

        mockMvc.perform(post("/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/participants/{participantId}",
                        campaignId, adventureId, chapterId, sceneId, UUID.randomUUID())
                        .param("displayName", "Goblin")
                        .param("quantity", "1")
                        .param("sortOrder", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Participant not found in scene"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Participant not found in scene")));
    }

    @Test
    void updateLinkReturnsErrorOnValidationFailure() throws Exception {
        setupSceneDetailMocks();
        doThrow(new NotFoundException("Link not found in scene"))
                .when(structuredService).updateLink(any(), any(), any(), any());

        mockMvc.perform(post("/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/links/{linkId}",
                        campaignId, adventureId, chapterId, sceneId, UUID.randomUUID())
                        .param("role", "REFERENCE")
                        .param("targetScope", "PACKAGE")
                        .param("targetType", "HANDOUT")
                        .param("targetId", UUID.randomUUID().toString())
                        .param("sortOrder", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Link not found in scene"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Link not found in scene")));
    }

    @Test
    void updateTransitionReturnsErrorOnValidationFailure() throws Exception {
        setupSceneDetailMocks();
        doThrow(new IllegalArgumentException("CHOICE transition requires a target scene"))
                .when(structuredService).updateTransition(any(), any(), any(), any());

        mockMvc.perform(post("/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/transitions/{transitionId}",
                        campaignId, adventureId, chapterId, sceneId, UUID.randomUUID())
                        .param("kind", "CHOICE")
                        .param("sortOrder", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "CHOICE transition requires a target scene"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CHOICE transition requires a target scene")));
    }
}
