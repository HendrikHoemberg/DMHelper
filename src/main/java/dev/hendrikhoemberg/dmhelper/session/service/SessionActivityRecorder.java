package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SessionActivityRecorder {

    private final Clock clock;
    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;
    private final SessionObjectiveChangeRepository objectiveChanges;

    public SessionActivityRecorder(Clock clock,
                                   CampaignSessionRepository sessions,
                                   SessionSceneVisitRepository visits,
                                   SessionObjectiveChangeRepository objectiveChanges) {
        this.clock = clock;
        this.sessions = sessions;
        this.visits = visits;
        this.objectiveChanges = objectiveChanges;
    }

    public void sceneSelected(UUID campaignId, Scene scene) {
        sessions.findByCampaignId(campaignId)
                .filter(session -> session.getStatus() == CampaignSession.Status.RUNNING)
                .ifPresent(session -> visits.findBySessionIdAndSceneId(session.getId(), scene.getId())
                        .orElseGet(() -> {
                            SessionSceneVisit visit = new SessionSceneVisit();
                            visit.setSession(session);
                            visit.setScene(scene);
                            visit.setVisitedAt(clock.instant());
                            return visits.save(visit);
                        }));
    }

    /**
     * Records an objective status change when a session is running.
     *
     * @return the generated session-change id, or empty when no row is written
     *         (status unchanged, no session, or session not running)
     */
    public Optional<UUID> recordObjectiveChange(UUID campaignId, UUID objectiveId,
                                       QuestObjectiveStatus previousStatus,
                                       QuestObjectiveStatus newStatus) {
        if (previousStatus == newStatus) return Optional.empty();
        return sessions.findByCampaignId(campaignId)
                .filter(session -> session.getStatus() == CampaignSession.Status.RUNNING)
                .map(session -> {
                    SessionObjectiveChange change = new SessionObjectiveChange();
                    change.setSession(session);
                    QuestObjective objective = new QuestObjective();
                    objective.setId(objectiveId);
                    change.setObjective(objective);
                    change.setPreviousStatus(previousStatus);
                    change.setNewStatus(newStatus);
                    change.setChangedAt(clock.instant());
                    return objectiveChanges.save(change).getId();
                });
    }

    public void sceneCompleted(Scene scene) {
        UUID campaignId = scene.getChapter().getAdventure().getCampaign().getId();
        sessions.findByCampaignId(campaignId)
                .filter(session -> session.getStatus() == CampaignSession.Status.RUNNING)
                .ifPresent(session -> {
                    SessionSceneVisit visit = visits.findBySessionIdAndSceneId(session.getId(), scene.getId())
                            .orElseGet(() -> {
                                SessionSceneVisit created = new SessionSceneVisit();
                                created.setSession(session);
                                created.setScene(scene);
                                created.setVisitedAt(clock.instant());
                                return created;
                            });
                    if (visit.getCompletedAt() == null) visit.setCompletedAt(clock.instant());
                    visits.save(visit);
                });
    }
}
