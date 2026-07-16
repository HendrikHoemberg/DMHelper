package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class SessionActivityRecorder {

    private final Clock clock;
    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;

    public SessionActivityRecorder(Clock clock,
                                   CampaignSessionRepository sessions,
                                   SessionSceneVisitRepository visits) {
        this.clock = clock;
        this.sessions = sessions;
        this.visits = visits;
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
