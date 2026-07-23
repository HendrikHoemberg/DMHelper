package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntry;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntryRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SessionLogModuleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLogModuleService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm")
            .withLocale(Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
            .withLocale(Locale.ENGLISH);
    private static final tools.jackson.databind.ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    public record SessionLogView(
            String sessionStatus,
            String timeRange,
            List<LogEventView> currentEvents,
            List<SavedLogView> recentSavedLogs,
            int unresolvedQuickNoteCount,
            String reviewDraft) {}

    public record LogEventView(Instant occurredAt, String kind, String title, String detail, boolean warning) {}

    public record SavedLogView(UUID noteId, String title, Instant createdAt, String url) {}

    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;
    private final CombatLogEntryRepository combatLogs;
    private final SessionObjectiveChangeRepository objectiveChanges;
    private final SessionAuditEntryRepository auditRepo;
    private final QuickNoteRepository quickNotes;
    private final NoteRepository notes;
    private final AdventureService adventures;
    private final ZoneId zone;

    public SessionLogModuleService(CampaignSessionRepository sessions,
                                   SessionSceneVisitRepository visits,
                                   CombatLogEntryRepository combatLogs,
                                   SessionObjectiveChangeRepository objectiveChanges,
                                   SessionAuditEntryRepository auditRepo,
                                   QuickNoteRepository quickNotes,
                                   NoteRepository notes,
                                   AdventureService adventures,
                                   ZoneId zone) {
        this.sessions = sessions;
        this.visits = visits;
        this.combatLogs = combatLogs;
        this.objectiveChanges = objectiveChanges;
        this.auditRepo = auditRepo;
        this.quickNotes = quickNotes;
        this.notes = notes;
        this.adventures = adventures;
        this.zone = zone;
    }

    public SessionLogView sessionLog(UUID campaignId, CockpitModuleMode mode) {
        CampaignSession session = sessions.findByCampaignId(campaignId).orElse(null);
        if (session == null || session.getStatus() == CampaignSession.Status.IDLE) return null;

        Instant from = Objects.requireNonNull(session.getStartedAt(), "Session start time is required.");
        Instant to = Instant.now();

        List<LogEventView> events = gatherEvents(session, from, to);

        if (mode == CockpitModuleMode.COMPACT) {
            events = events.size() > 3 ? events.subList(0, 3) : events;
        }

        long unresolvedNoteCount = quickNotes.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId).size();

        List<SavedLogView> savedLogs = notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_LOG)
                .stream()
                .map(n -> new SavedLogView(n.getId(), n.getTitle(), n.getCreatedAt(),
                        "/campaigns/" + campaignId + "/notes/" + n.getId()))
                .toList();

        String reviewDraft = null;
        if (mode == CockpitModuleMode.FOCUSED && session.getStatus() == CampaignSession.Status.REVIEW) {
            reviewDraft = session.getDraftBody();
        }

        return new SessionLogView(
                session.getStatus().name(),
                formatSessionRange(session.getStartedAt(), session.getUpdatedAt()),
                events,
                savedLogs,
                (int) unresolvedNoteCount,
                reviewDraft);
    }

    private List<LogEventView> gatherEvents(CampaignSession session, Instant from, Instant to) {
        UUID sessionId = session.getId();
        UUID campaignId = session.getCampaign().getId();
        List<LogEventView> events = new ArrayList<>();

        events.addAll(visits.findBySessionIdOrderByVisitedAtAscIdAsc(sessionId).stream()
                .map(v -> new LogEventView(v.getVisitedAt(), "Scene visited",
                        v.getScene().getTitle(), null, false))
                .toList());

        events.addAll(combatLogs.findSessionEvidence(campaignId, from, to).stream()
                .filter(e -> e.getType() == CombatLogEntry.EntryType.ENCOUNTER_ENDED)
                .map(e -> new LogEventView(e.getCreatedAt(), "Encounter ended",
                        e.getEncounter().getName(), "Round " + e.getRound(), false))
                .toList());

        events.addAll(objectiveChanges.findBySessionIdOrderByChangedAtAscIdAsc(sessionId).stream()
                .map(c -> {
                    String title = c.getObjective() != null ? c.getObjective().getTitle() : "Unknown";
                    String detail = c.getPreviousStatus() + " \u2192 " + c.getNewStatus();
                    return new LogEventView(c.getChangedAt(), "Objective updated", title, detail, false);
                })
                .toList());

        events.addAll(auditRepo
                .findBySession_IdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(sessionId, from, to)
                .stream()
                .filter(e -> e.getEntryType() == SessionAuditEntry.EntryType.PRESENTATION_OVERRIDE)
                .map(e -> {
                    String title = extractTitleFromDetails(e.getDetails());
                    return new LogEventView(e.getCreatedAt(), "Presentation override",
                            title != null ? title : "Unknown", null, true);
                })
                .toList());

        events.sort(Comparator.comparing(LogEventView::occurredAt).reversed());
        return events;
    }

    public String formatSessionRange(Instant startedAt, Instant endedAt) {
        ZonedDateTime start = startedAt.atZone(zone);
        ZonedDateTime end = endedAt.atZone(zone);
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return DATE_FMT.format(start) + "\u2013" + TIME_FMT.format(end) + " " + zone.getId();
        }
        return DATE_FMT.format(start) + " " + zone.getId() + "\u2013" + DATE_FMT.format(end) + " " + zone.getId();
    }

    private static String extractTitleFromDetails(String details) {
        if (details == null || details.isBlank()) return null;
        try {
            return JSON.readTree(details).get("title").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
