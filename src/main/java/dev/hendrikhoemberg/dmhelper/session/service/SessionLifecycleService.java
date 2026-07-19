package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.audio.service.SessionAudioStateService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SessionLifecycleService {

    private final Clock clock;
    private final CampaignRepository campaigns;
    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;
    private final GameMapRepository maps;
    private final PartyMemberRepository party;
    private final NoteRepository notes;
    private final CalendarService calendar;
    private final SessionDraftService drafts;
    private final NoteService noteService;
    private final CampaignPackageKeyService packageKeys;
    private final ApplicationEventPublisher events;
    private final SessionObjectiveChangeRepository objectiveChanges;
    private final SessionReferenceCleaner sessionRefCleaner;
    private final SessionAudioStateService audioStateService;

    public SessionLifecycleService(Clock clock,
                                   CampaignRepository campaigns,
                                   CampaignSessionRepository sessions,
                                   SessionSceneVisitRepository visits,
                                   GameMapRepository maps,
                                   PartyMemberRepository party,
                                   NoteRepository notes,
                                   CalendarService calendar,
                                   SessionDraftService drafts,
                                   NoteService noteService,
                                    CampaignPackageKeyService packageKeys,
                                    ApplicationEventPublisher events,
                                    SessionObjectiveChangeRepository objectiveChanges,
                                    SessionReferenceCleaner sessionRefCleaner,
                                    SessionAudioStateService audioStateService) {
        this.clock = clock;
        this.campaigns = campaigns;
        this.sessions = sessions;
        this.visits = visits;
        this.maps = maps;
        this.party = party;
        this.notes = notes;
        this.calendar = calendar;
        this.drafts = drafts;
        this.noteService = noteService;
        this.packageKeys = packageKeys;
        this.events = events;
        this.objectiveChanges = objectiveChanges;
        this.sessionRefCleaner = sessionRefCleaner;
        this.audioStateService = audioStateService;
    }

    public CampaignSession start(UUID campaignId, UUID requestedMapId) {
        Campaign campaign = requireCampaign(campaignId);
        CampaignSession session = sessions.findByCampaignId(campaignId)
                .orElseGet(() -> CampaignSession.idle(campaign));
        requireStatus(session, CampaignSession.Status.IDLE, "Only an idle campaign can start a session.");
        Instant now = clock.instant();
        CalendarService.InGameDate date = calendar.getCurrentDate(campaignId);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setStartedAt(now);
        session.setPausedAt(null);
        session.setReviewStartedAt(null);
        session.setStartInGameYear(date.year());
        session.setStartInGameMonth(date.month());
        session.setStartInGameDay(date.day());
        session.setPlanNote(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(
                campaignId, NoteType.SESSION_PLAN).stream().findFirst().orElse(null));
        session.setWorkspaceMap(requestedMapId == null ? null : requireCampaignMap(campaignId, requestedMapId));
        session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
        session.setPresentedMap(null);
        session.setPresentedHandout(null);
        session.setDraftBody(null);
        session.getAttendees().clear();
        session.getAttendees().addAll(party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId));
        CampaignSession saved = sessions.save(session);
        visits.deleteBySessionId(saved.getId());
        audioStateService.createOrReset(saved.getId());
        return saved;
    }

    public CampaignSession pause(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.RUNNING, "Only a running session can be paused.");
        session.setStatus(CampaignSession.Status.PAUSED);
        session.setPausedAt(clock.instant());
        return sessions.save(session);
    }

    public CampaignSession resume(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.PAUSED, "Only a paused session can be resumed.");
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setPausedAt(null);
        return sessions.save(session);
    }

    public CampaignSession cancelReview(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.REVIEW, "Only a session under review can return to play.");
        session.setStatus(CampaignSession.Status.PAUSED);
        session.setReviewStartedAt(null);
        session.setDraftBody(null);
        return sessions.save(session);
    }

    public CampaignSession setAttendees(UUID campaignId, List<UUID> ids) {
        CampaignSession session = requireOpenSession(campaignId);
        List<PartyMember> selected = party.findAllById(ids);
        if (selected.size() != new HashSet<>(ids).size()) {
            throw new IllegalArgumentException("One or more attendee party members do not exist or were duplicated.");
        }
        if (selected.stream().anyMatch(member -> !member.getCampaign().getId().equals(campaignId))) {
            throw new IllegalArgumentException("Every attendee must belong to this campaign.");
        }
        session.getAttendees().clear();
        session.getAttendees().addAll(selected.stream()
                .sorted(Comparator.comparing(PartyMember::getCharacterName)).toList());
        return sessions.save(session);
    }

    public CampaignSession setWorkspaceMap(UUID campaignId, UUID mapId) {
        CampaignSession session = requireOpenSession(campaignId);
        session.setWorkspaceMap(mapId == null ? null : requireCampaignMap(campaignId, mapId));
        return sessions.save(session);
    }

    public CampaignSession beginReview(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        if (session.getStatus() != CampaignSession.Status.RUNNING && session.getStatus() != CampaignSession.Status.PAUSED)
            throw new IllegalStateException("Only a running or paused session can be reviewed.");
        Instant now = clock.instant();
        session.setStatus(CampaignSession.Status.REVIEW);
        session.setReviewStartedAt(now);
        session.setDraftBody(drafts.generate(session, now));
        return sessions.save(session);
    }

    public Note complete(UUID campaignId, String title, String body) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.REVIEW, "Review the session draft before saving it.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Session log title is required.");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("Session log body is required.");
        Note note = noteService.create(campaignId, NoteType.SESSION_LOG, title.strip(), body, "session-log", true);
        events.publishEvent(new SessionReferenceCleaner.PresentationInvalidated(
                campaignId, null, true));
        List<UUID> visitIds = visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId()).stream()
                .map(SessionSceneVisit::getId).toList();
        sessionRefCleaner.detachSessionObjectiveChanges(session.getId(), campaignId);
        audioStateService.deleteBySessionId(session.getId());
        resetToIdle(session);
        visits.deleteBySessionId(session.getId());
        packageKeys.deleteBindings(campaignId, CampaignContentType.SESSION_SCENE_VISIT, visitIds);
        sessions.save(session);
        return note;
    }

    private void resetToIdle(CampaignSession session) {
        session.setStatus(CampaignSession.Status.IDLE);
        session.setStartedAt(null);
        session.setPausedAt(null);
        session.setReviewStartedAt(null);
        session.setStartInGameYear(null);
        session.setStartInGameMonth(null);
        session.setStartInGameDay(null);
        session.setPlanNote(null);
        session.setWorkspaceMap(null);
        session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
        session.setPresentedMap(null);
        session.setPresentedHandout(null);
        session.setDraftBody(null);
        session.getAttendees().clear();
    }

    private Campaign requireCampaign(UUID campaignId) {
        return campaigns.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
    }

    private GameMap requireCampaignMap(UUID campaignId, UUID mapId) {
        return maps.findById(mapId)
                .filter(m -> m.getCampaign().getId().equals(campaignId))
                .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
    }

    private CampaignSession requireSession(UUID campaignId) {
        return sessions.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException("No session exists for this campaign."));
    }

    private CampaignSession requireOpenSession(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        if (!session.isOpen())
            throw new IllegalStateException("The session must be open to perform this operation.");
        return session;
    }

    private void requireStatus(CampaignSession session, CampaignSession.Status expected, String message) {
        if (session.getStatus() != expected)
            throw new IllegalStateException(message);
    }
}
