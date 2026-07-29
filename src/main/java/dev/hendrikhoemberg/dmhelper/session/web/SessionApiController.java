package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneTransitionService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.service.SessionEncounterService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionEncounterService.ActiveEncounterDisposition;
import dev.hendrikhoemberg.dmhelper.session.service.SessionEncounterService.EncounterActivationDto;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/session")
public class SessionApiController {

    public record StartRequest(UUID mapId) {}
    public record AttendanceRequest(List<UUID> partyMemberIds) {}
    public record WorkspaceMapRequest(UUID mapId) {}
    public record CurrentSceneRequest(UUID sceneId) {}
    public record StepSceneRequest(int direction) {}
    public record FollowTransitionRequest(UUID transitionId) {}
    public record CompleteRequest(String title, String body) {}
    public record ActivateEncounterRequest(ActiveEncounterDisposition activeEncounterDisposition) {}
    public record SessionStateDto(String status, UUID workspaceMapId, String presentationMode,
                                  List<UUID> attendeeIds, String draftBody) {}
    public record SessionSceneDto(UUID id, String title, String status, UUID mapId,
                                  UUID encounterId, UUID previousId, UUID nextId) {}
    public record CompleteResultDto(UUID noteId, String url) {}
    public record ObjectiveStatusRequest(String status) {}
    public record ObjectiveStatusResult(UUID objectiveId, String status, String sessionChangeId) {}

    private final SessionLifecycleService lifecycle;
    private final AdventureService adventures;
    private final SessionWorkspaceService workspaces;
    private final SessionEncounterService sessionEncounterService;
    private final SceneTransitionService sceneTransitionService;
    private final QuestService questService;
    private final SceneEncounterSeedService encounterSeeder;

    public SessionApiController(SessionLifecycleService lifecycle,
                                AdventureService adventures,
                                SessionWorkspaceService workspaces,
                                SessionEncounterService sessionEncounterService,
                                SceneTransitionService sceneTransitionService,
                                QuestService questService,
                                SceneEncounterSeedService encounterSeeder) {
        this.lifecycle = lifecycle;
        this.adventures = adventures;
        this.workspaces = workspaces;
        this.sessionEncounterService = sessionEncounterService;
        this.sceneTransitionService = sceneTransitionService;
        this.questService = questService;
        this.encounterSeeder = encounterSeeder;
    }

    @PostMapping("/start")
    SessionStateDto start(@PathVariable UUID campaignId, @RequestBody StartRequest request) {
        return state(lifecycle.start(campaignId, request.mapId()));
    }

    @PostMapping("/pause")
    SessionStateDto pause(@PathVariable UUID campaignId) {
        return state(lifecycle.pause(campaignId));
    }

    @PostMapping("/resume")
    SessionStateDto resume(@PathVariable UUID campaignId) {
        return state(lifecycle.resume(campaignId));
    }

    @PostMapping("/cancel-review")
    SessionStateDto cancelReview(@PathVariable UUID campaignId) {
        return state(lifecycle.cancelReview(campaignId));
    }

    @PostMapping("/review")
    SessionStateDto review(@PathVariable UUID campaignId) {
        return state(lifecycle.beginReview(campaignId));
    }

    @PutMapping("/attendance")
    SessionStateDto attendance(@PathVariable UUID campaignId, @RequestBody AttendanceRequest request) {
        return state(lifecycle.setAttendees(campaignId, List.copyOf(request.partyMemberIds())));
    }

    @PutMapping("/workspace-map")
    SessionStateDto workspaceMap(@PathVariable UUID campaignId, @RequestBody WorkspaceMapRequest request) {
        return state(lifecycle.setWorkspaceMap(campaignId, request.mapId()));
    }

    @PutMapping("/current-scene")
    SessionSceneDto currentScene(@PathVariable UUID campaignId, @RequestBody CurrentSceneRequest request) {
        return sceneData(adventures.setCurrentScene(campaignId, request.sceneId()), campaignId);
    }

    @PostMapping("/current-scene/step")
    SessionSceneDto stepScene(@PathVariable UUID campaignId, @RequestBody StepSceneRequest request) {
        if (Math.abs(request.direction()) != 1)
            throw new IllegalArgumentException("Scene direction must be -1 or 1.");
        Scene result = adventures.stepCurrentScene(campaignId, request.direction())
                .orElseThrow(() -> new IllegalStateException("No current scene to step from."));
        return sceneData(result, campaignId);
    }

    @PostMapping("/current-scene/follow-transition")
    SessionSceneDto followTransition(@PathVariable UUID campaignId, @RequestBody FollowTransitionRequest request) {
        Scene target = sceneTransitionService.followTransition(campaignId, request.transitionId());
        return sceneData(target, campaignId);
    }

    @PostMapping("/scenes/{sceneId}/seed-encounter")
    SceneEncounterSeedService.SeedResult seedEncounter(@PathVariable UUID campaignId,
                                                        @PathVariable UUID sceneId) {
        return encounterSeeder.seedFromScene(campaignId, sceneId);
    }

    @PostMapping("/encounters/{encounterId}/activate")
    EncounterActivationDto activateEncounter(
            @PathVariable UUID campaignId,
            @PathVariable UUID encounterId,
            @RequestBody ActivateEncounterRequest request) {
        return sessionEncounterService.activate(
                campaignId, encounterId, request.activeEncounterDisposition());
    }

    @PostMapping("/abandon")
    SessionStateDto abandonSession(@PathVariable UUID campaignId) {
        return state(lifecycle.abandon(campaignId));
    }

    @PostMapping("/complete")
    CompleteResultDto complete(@PathVariable UUID campaignId, @RequestBody CompleteRequest request) {
        Note note = lifecycle.complete(campaignId, request.title(), request.body());
        return new CompleteResultDto(note.getId(), "/campaigns/" + campaignId + "/notes/" + note.getId());
    }

    @PutMapping("/quests/objectives/{objectiveId}/status")
    ObjectiveStatusResult setObjectiveStatus(@PathVariable UUID campaignId,
                                              @PathVariable UUID objectiveId,
                                              @RequestBody ObjectiveStatusRequest request) {
        QuestObjectiveStatus newStatus;
        try {
            newStatus = QuestObjectiveStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.status());
        }
        if (!isDmUser()) {
            throw new NotFoundException("Objective not found");
        }
        QuestService.ObjectiveStatusUpdate update =
                questService.setObjectiveStatus(campaignId, objectiveId, newStatus);
        String sessionChangeId = update.sessionChangeId() == null
                ? null
                : update.sessionChangeId().toString();
        return new ObjectiveStatusResult(update.objective().getId(),
                update.objective().getStatus().name(),
                sessionChangeId);
    }

    private boolean isDmUser() {
        return true;
    }

    private SessionStateDto state(CampaignSession session) {
        return new SessionStateDto(session.getStatus().name(),
                session.getWorkspaceMap() == null ? null : session.getWorkspaceMap().getId(),
                session.getPresentationMode().name(),
                session.getAttendees().stream().map(PartyMember::getId).toList(),
                session.getDraftBody());
    }

    private SessionSceneDto sceneData(Scene scene, UUID campaignId) {
        SessionWorkspaceService.SessionWorkspace ws = workspaces.load(campaignId, null);
        return new SessionSceneDto(scene.getId(), scene.getTitle(), scene.getStatus().name(),
                scene.getMap() == null ? null : scene.getMap().getId(),
                scene.getEncounter() == null ? null : scene.getEncounter().getId(),
                ws.previousScene() == null ? null : ws.previousScene().getId(),
                ws.nextScene() == null ? null : ws.nextScene().getId());
    }
}
