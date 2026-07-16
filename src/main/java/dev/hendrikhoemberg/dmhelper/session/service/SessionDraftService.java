package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SessionDraftService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm")
            .withLocale(Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;
    private final CombatLogEntryRepository combatLogs;
    private final LedgerEntryRepository ledgers;
    private final QuickNoteRepository quickNotes;
    private final CalendarService calendar;

    public SessionDraftService(CampaignSessionRepository sessions,
                               SessionSceneVisitRepository visits,
                               CombatLogEntryRepository combatLogs,
                               LedgerEntryRepository ledgers,
                               QuickNoteRepository quickNotes,
                               CalendarService calendar) {
        this.sessions = sessions;
        this.visits = visits;
        this.combatLogs = combatLogs;
        this.ledgers = ledgers;
        this.quickNotes = quickNotes;
        this.calendar = calendar;
    }

    public String generate(CampaignSession session, Instant endedAt) {
        UUID campaignId = session.getCampaign().getId();
        Instant startedAt = Objects.requireNonNull(session.getStartedAt(), "Session start time is required.");
        StringBuilder out = new StringBuilder();
        section(out, "Session Date", DAY.format(startedAt) + "–" + TIME.format(endedAt));
        section(out, "In-Game Date", formatGameDates(session, calendar.getCurrentDate(campaignId)));
        listSection(out, "Attendance", attendanceLines(session));
        listSection(out, "Scenes", sceneLines(session));
        listSection(out, "Encounters", encounterLines(campaignId, startedAt, endedAt));
        listSection(out, "Loot & Ledger Changes", ledgerLines(campaignId, startedAt, endedAt));
        listSection(out, "Unresolved Quick Notes", quickNoteLines(campaignId, startedAt, endedAt));
        out.append("## Recap\n\n\n## Next-Session Hooks\n\n");
        return out.toString();
    }

    private void section(StringBuilder out, String title, String body) {
        out.append("## ").append(title).append("\n").append(body).append("\n\n");
    }

    private void listSection(StringBuilder out, String title, List<String> lines) {
        out.append("## ").append(title).append("\n");
        if (lines.isEmpty()) lines = List.of("\u2014");
        for (String line : lines) out.append("- ").append(line).append("\n");
        out.append("\n");
    }

    private String formatGameDates(CampaignSession session, CalendarService.InGameDate current) {
        String started = formatDate(session.getStartInGameYear(), session.getStartInGameMonth(), session.getStartInGameDay());
        String ended = formatDate(current.year(), current.month(), current.day());
        return started.equals(ended) ? "Started: " + started : "Started: " + started + "\nEnded: " + ended;
    }

    private String formatDate(Integer year, Integer month, Integer day) {
        if (year == null) return "\u2014";
        return day + " " + monthName(month) + " " + year;
    }

    private String monthName(Integer month) {
        String[] names = {"January","February","March","April","May","June",
                "July","August","September","October","November","December"};
        return month != null && month >= 0 && month < 12 ? names[month] : "Month-" + month;
    }

    private List<String> attendanceLines(CampaignSession session) {
        return session.getAttendees().stream()
                .map(pm -> {
                    String name = pm.getCharacterName();
                    String player = pm.getPlayerName();
                    return player != null && !player.isBlank() ? name + " \u2014 " + player : name;
                }).toList();
    }

    private List<String> sceneLines(CampaignSession session) {
        return visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId()).stream()
                .map(v -> {
                    String title = v.getScene().getTitle();
                    return v.getCompletedAt() != null ? title + " \u2014 visited, completed" : title + " \u2014 visited";
                }).toList();
    }

    private List<String> encounterLines(UUID campaignId, Instant from, Instant to) {
        List<CombatLogEntry> logs = combatLogs.findSessionEvidence(campaignId, from, to);
        return logs.stream()
                .filter(log -> log.getType() == CombatLogEntry.EntryType.ENCOUNTER_ENDED)
                .map(log -> log.getEncounter().getName() + " \u2014 completed")
                .distinct().toList();
    }

    private List<String> ledgerLines(UUID campaignId, Instant from, Instant to) {
        return ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, from, to).stream()
                .map(le -> {
                    String dir = le.getDirection() == LedgerEntry.Direction.GAIN ? "+" : "-";
                    return dir + (le.getAmount() != null ? le.getAmount().toPlainString() : "0")
                            + " " + (le.getCurrency() != null ? le.getCurrency() : "GP")
                            + " \u2014 " + (le.getHolder() != null ? le.getHolder() : "party stash")
                            + (le.getNote() != null ? " \u2014 " + le.getNote() : "");
                }).toList();
    }

    private List<String> quickNoteLines(UUID campaignId, Instant from, Instant to) {
        return quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, from, to).stream()
                .map(qn -> qn.getTargetType() + " / " + qn.getTargetId() + ": " + qn.getBody()).toList();
    }
}
