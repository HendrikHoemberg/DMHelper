package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SessionReferenceCleaner {

    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;
    private final CampaignPackageKeyRepository packageKeys;
    private final ApplicationEventPublisher events;

    public record PresentationInvalidated(UUID campaignId, UUID contentId, boolean wholeCampaign) {}

    public SessionReferenceCleaner(CampaignSessionRepository sessions,
                                   SessionSceneVisitRepository visits,
                                   CampaignPackageKeyRepository packageKeys,
                                   ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.visits = visits;
        this.packageKeys = packageKeys;
        this.events = events;
    }

    public void detachMap(UUID mapId) {
        for (CampaignSession session : sessions.findAll()) {
            boolean changed = false;
            if (session.getWorkspaceMap() != null && mapId.equals(session.getWorkspaceMap().getId())) {
                session.setWorkspaceMap(null);
                changed = true;
            }
            if (session.getPresentedMap() != null && mapId.equals(session.getPresentedMap().getId())) {
                lowerCurtain(session);
                events.publishEvent(new PresentationInvalidated(
                        session.getCampaign().getId(), mapId, false));
                changed = true;
            }
            if (changed) sessions.save(session);
        }
    }

    public void detachHandout(UUID handoutId) {
        for (CampaignSession session : sessions.findAll()) {
            if (session.getPresentedHandout() != null
                    && handoutId.equals(session.getPresentedHandout().getId())) {
                lowerCurtain(session);
                sessions.save(session);
                events.publishEvent(new PresentationInvalidated(
                        session.getCampaign().getId(), handoutId, false));
            }
        }
    }

    public void detachPlanNote(UUID noteId) {
        for (CampaignSession session : sessions.findAll()) {
            if (session.getPlanNote() != null && noteId.equals(session.getPlanNote().getId())) {
                session.setPlanNote(null);
                sessions.save(session);
            }
        }
    }

    public void detachAttendee(UUID partyMemberId) {
        for (CampaignSession session : sessions.findAll()) {
            if (session.getAttendees().removeIf(member -> partyMemberId.equals(member.getId()))) {
                sessions.save(session);
            }
        }
    }

    public void detachScene(UUID sceneId) {
        var deleted = visits.findBySceneId(sceneId);
        for (var visit : deleted) {
            packageKeys.deleteByCampaignIdAndEntityTypeAndEntityIdIn(
                    visit.getSession().getCampaign().getId(),
                    CampaignContentType.SESSION_SCENE_VISIT.name(), java.util.List.of(visit.getId()));
        }
        visits.deleteBySceneId(sceneId);
    }

    public void detachCampaign(UUID campaignId) {
        events.publishEvent(new PresentationInvalidated(campaignId, null, true));
    }

    private void lowerCurtain(CampaignSession session) {
        session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
        session.setPresentedMap(null);
        session.setPresentedHandout(null);
    }
}
