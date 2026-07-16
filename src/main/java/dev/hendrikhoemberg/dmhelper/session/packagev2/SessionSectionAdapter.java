package dev.hendrikhoemberg.dmhelper.session.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.InGameDateDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SessionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SessionSceneVisitDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.HANDOUT;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.MAP;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.NOTE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.PARTY_MEMBER;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SCENE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SESSION;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SESSION_SCENE_VISIT;

@Component
public class SessionSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;

    public SessionSectionAdapter(CampaignSessionRepository sessions, SessionSceneVisitRepository visits) {
        this.sessions = sessions;
        this.visits = visits;
    }

    @Override
    public String sectionName() { return "Session"; }

    @Override
    public int order() { return 1150; }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        CampaignSession session = sessions.findByCampaignId(context.campaignId()).orElse(null);
        if (session == null || session.getStatus() == CampaignSession.Status.IDLE) {
            target.session(null);
            return;
        }
        String sessionKey = context.key(SESSION, session.getId(), "session");
        InGameDateDto startDate = session.getStartInGameYear() != null
                ? new InGameDateDto(session.getStartInGameYear(), session.getStartInGameMonth(), session.getStartInGameDay())
                : null;
        ContentReference planRef = session.getPlanNote() != null
                ? context.packageRef(NOTE, session.getPlanNote().getId(), session.getPlanNote().getTitle()) : null;
        ContentReference mapRef = session.getWorkspaceMap() != null
                ? context.packageRef(MAP, session.getWorkspaceMap().getId(), session.getWorkspaceMap().getName()) : null;
        ContentReference presentedRef = null;
        if (session.getPresentationMode() == CampaignSession.PresentationMode.MAP && session.getPresentedMap() != null)
            presentedRef = context.packageRef(MAP, session.getPresentedMap().getId(), session.getPresentedMap().getName());
        else if (session.getPresentationMode() == CampaignSession.PresentationMode.HANDOUT && session.getPresentedHandout() != null)
            presentedRef = context.packageRef(HANDOUT, session.getPresentedHandout().getId(), session.getPresentedHandout().getTitle());
        List<ContentReference> attendeeRefs = session.getAttendees().stream()
                .map(pm -> context.packageRef(PARTY_MEMBER, pm.getId(), pm.getCharacterName())).toList();
        List<SessionSceneVisitDto> visitDtos = visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId()).stream()
                .map(v -> {
                    String vKey = context.key(SESSION_SCENE_VISIT, v.getId(), "visit-" + v.getScene().getTitle());
                    ContentReference sceneRef = context.packageRef(SCENE, v.getScene().getId(), v.getScene().getTitle());
                    return new SessionSceneVisitDto(vKey, sceneRef, v.getVisitedAt(), v.getCompletedAt());
                }).toList();
        target.session(new SessionDto(sessionKey, session.getStatus().name(),
                session.getStartedAt(), session.getPausedAt(), session.getReviewStartedAt(),
                startDate, planRef, mapRef, session.getPresentationMode().name(),
                presentedRef, attendeeRefs, visitDtos, session.getDraftBody()));
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        SessionDto dto = source.session();
        if (dto == null) return;
        CampaignSession session = CampaignSession.idle(context.campaign());
        session.setStatus(CampaignSession.Status.valueOf(dto.status()));
        session.setStartedAt(dto.startedAt());
        session.setPausedAt(dto.pausedAt());
        session.setReviewStartedAt(dto.reviewStartedAt());
        if (dto.startInGameDate() != null) {
            session.setStartInGameYear(dto.startInGameDate().year());
            session.setStartInGameMonth(dto.startInGameDate().month());
            session.setStartInGameDay(dto.startInGameDate().day());
        }
        session.setDraftBody(dto.draftBody());
        CampaignSession saved = sessions.save(session);
        context.register(SESSION, dto.key(), saved, saved.getId());
        context.defer("session-references:" + dto.key(), () -> restoreReferences(saved, dto, context));
    }

    private void restoreReferences(CampaignSession saved, SessionDto dto, CampaignImportContext context) {
        if (dto.planNoteRef() != null) {
            Note note = context.require(dto.planNoteRef(), NOTE, Note.class);
            saved.setPlanNote(note);
        }
        if (dto.workspaceMapRef() != null) {
            GameMap map = context.require(dto.workspaceMapRef(), MAP, GameMap.class);
            saved.setWorkspaceMap(map);
        }
        saved.setPresentationMode(CampaignSession.PresentationMode.valueOf(dto.presentationMode()));
        if ("MAP".equals(dto.presentationMode()) && dto.presentedRef() != null) {
            GameMap map = context.require(dto.presentedRef(), MAP, GameMap.class);
            saved.setPresentedMap(map);
        } else if ("HANDOUT".equals(dto.presentationMode()) && dto.presentedRef() != null) {
            Handout handout = context.require(dto.presentedRef(), HANDOUT, Handout.class);
            if (handout.isDmOnly()) {
                saved.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
            } else {
                saved.setPresentedHandout(handout);
            }
        }
        saved.getAttendees().clear();
        for (ContentReference ref : dto.attendeeRefs()) {
            PartyMember pm = context.require(ref, PARTY_MEMBER, PartyMember.class);
            saved.getAttendees().add(pm);
        }
        sessions.save(saved);
        for (SessionSceneVisitDto vDto : dto.sceneVisits()) {
            Scene scene = context.require(vDto.sceneRef(), SCENE, Scene.class);
            SessionSceneVisit visit = new SessionSceneVisit();
            visit.setSession(saved);
            visit.setScene(scene);
            visit.setVisitedAt(vDto.visitedAt());
            visit.setCompletedAt(vDto.completedAt());
            SessionSceneVisit savedVisit = visits.save(visit);
            context.register(SESSION_SCENE_VISIT, vDto.key(), savedVisit, savedVisit.getId());
        }
    }
}
