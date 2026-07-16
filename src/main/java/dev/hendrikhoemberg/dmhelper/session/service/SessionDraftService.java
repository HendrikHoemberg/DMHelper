package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class SessionDraftService {
    private static final Logger log = LoggerFactory.getLogger(SessionDraftService.class);
    private static final tools.jackson.databind.ObjectMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm")
            .withLocale(Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final SessionSceneVisitRepository visits;
    private final CombatLogEntryRepository combatLogs;
    private final CombatantRepository combatants;
    private final LedgerEntryRepository ledgers;
    private final QuickNoteRepository quickNotes;
    private final QuickNoteService quickNoteService;
    private final CalendarService calendar;

    public SessionDraftService(SessionSceneVisitRepository visits,
                               CombatLogEntryRepository combatLogs,
                               CombatantRepository combatants,
                               LedgerEntryRepository ledgers,
                               QuickNoteRepository quickNotes,
                               QuickNoteService quickNoteService,
                               CalendarService calendar) {
        this.visits = visits;
        this.combatLogs = combatLogs;
        this.combatants = combatants;
        this.ledgers = ledgers;
        this.quickNotes = quickNotes;
        this.quickNoteService = quickNoteService;
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
        String[] monthNames = calendar.getCalendarConfig(session.getCampaign().getId()).monthNames();
        String started = formatDate(session.getStartInGameYear(), session.getStartInGameMonth(),
                session.getStartInGameDay(), monthNames);
        String ended = formatDate(current.year(), current.month(), current.day(), monthNames);
        return started.equals(ended) ? "Started: " + started : "Started: " + started + "\nEnded: " + ended;
    }

    private String formatDate(Integer year, Integer month, Integer day, String[] monthNames) {
        if (year == null) return "\u2014";
        String monthName = month != null && month >= 0 && month < monthNames.length
                ? monthNames[month] : "Month-" + month;
        return day + " " + monthName + " " + year;
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
        Map<UUID, List<CombatLogEntry>> byEncounter = new LinkedHashMap<>();
        for (CombatLogEntry entry : logs) {
            byEncounter.computeIfAbsent(entry.getEncounter().getId(), ignored -> new ArrayList<>()).add(entry);
        }
        List<String> lines = new ArrayList<>();
        for (List<CombatLogEntry> evidence : byEncounter.values()) {
            if (evidence.stream().noneMatch(entry -> entry.getType() == CombatLogEntry.EntryType.ENCOUNTER_ENDED))
                continue;
            Encounter encounter = evidence.getFirst().getEncounter();
            int rounds = evidence.stream().mapToInt(CombatLogEntry::getRound).max().orElse(0);
            rounds = Math.max(rounds, encounter.getRound());
            Set<UUID> defeatedIds = finalDefeatedIds(evidence);
            Map<UUID, String> defeatedNames = new LinkedHashMap<>();
            for (Combatant combatant : combatants.findAllById(defeatedIds)) {
                defeatedNames.put(combatant.getId(), combatant.getName());
            }
            List<String> names = defeatedIds.stream().map(defeatedNames::get).filter(Objects::nonNull).toList();
            long damage = evidence.stream()
                    .filter(entry -> entry.getType() == CombatLogEntry.EntryType.DAMAGE)
                    .mapToLong(this::recordedDamage).sum();
            StringBuilder line = new StringBuilder(encounter.getName()).append(" \u2014 ");
            if (rounds > 0) line.append(rounds).append(rounds == 1 ? " round" : " rounds");
            else line.append("completed");
            if (!names.isEmpty()) line.append("; defeated: ").append(String.join(", ", names));
            if (damage > 0) line.append("; damage recorded: ").append(damage);
            lines.add(line.toString());
        }
        return lines;
    }

    private Set<UUID> finalDefeatedIds(List<CombatLogEntry> evidence) {
        Set<UUID> result = new LinkedHashSet<>();
        for (CombatLogEntry entry : evidence) {
            if (entry.getType() != CombatLogEntry.EntryType.DEFEATED
                    && entry.getType() != CombatLogEntry.EntryType.REVIVED) continue;
            try {
                UUID id = UUID.fromString(entry.getCombatantId());
                if (entry.getType() == CombatLogEntry.EntryType.DEFEATED) result.add(id);
                else result.remove(id);
            } catch (IllegalArgumentException ignored) {
                // An empty or malformed combatant ID is not reliable evidence.
            }
        }
        return result;
    }

    private long recordedDamage(CombatLogEntry entry) {
        try {
            var amount = JSON.readTree(entry.getPayload()).get("amount");
            return amount == null || !amount.isNumber() ? 0 : Math.abs(amount.asLong());
        } catch (Exception e) {
            log.warn("Ignoring malformed damage payload in combat log {}", entry.getId());
            return 0;
        }
    }

    private List<String> ledgerLines(UUID campaignId, Instant from, Instant to) {
        return ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, from, to).stream()
                .map(this::ledgerLine).toList();
    }

    private String ledgerLine(LedgerEntry entry) {
        String direction = entry.getDirection() == LedgerEntry.Direction.GAIN ? "+" : "-";
        String change;
        if (entry.getKind() == LedgerEntry.Kind.ITEM && entry.getItemAssignmentRef() != null) {
            var assignment = entry.getItemAssignmentRef();
            change = direction + assignment.getQuantity() + "\u00d7 " + assignment.getItemName();
        } else if (entry.getAmount() != null) {
            change = direction + entry.getAmount().toPlainString() + " "
                    + (entry.getCurrency() != null ? entry.getCurrency() : "currency");
        } else {
            change = direction + "ledger item";
        }
        return change + " \u2014 " + (entry.getHolder() != null ? entry.getHolder() : "party stash")
                + (entry.getNote() != null && !entry.getNote().isBlank() ? " \u2014 " + entry.getNote() : "");
    }

    private List<String> quickNoteLines(UUID campaignId, Instant from, Instant to) {
        return quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, from, to).stream()
                .map(qn -> qn.getTargetType() + " / " + quickNoteService.targetLabel(qn)
                        + ": " + qn.getBody()).toList();
    }
}
