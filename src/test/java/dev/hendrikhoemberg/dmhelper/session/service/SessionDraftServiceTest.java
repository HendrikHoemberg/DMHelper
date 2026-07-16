package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SessionDraftServiceTest {

    @Mock private CampaignSessionRepository sessions;
    @Mock private SessionSceneVisitRepository visits;
    @Mock private CombatLogEntryRepository combatLogs;
    @Mock private CombatantRepository combatants;
    @Mock private LedgerEntryRepository ledgers;
    @Mock private QuickNoteRepository quickNotes;
    @Mock private QuickNoteService quickNoteService;
    @Mock private CalendarService calendar;

    @InjectMocks private SessionDraftService service;

    private UUID campaignId;
    private Campaign campaign;
    private CampaignSession session;
    private Instant startedAt;
    private Instant endedAt;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        session = CampaignSession.idle(campaign);
        session.setStartedAt(Instant.parse("2026-07-16T18:00:00Z"));
        session.setStartInGameYear(1492);
        session.setStartInGameMonth(6);
        session.setStartInGameDay(12);
        startedAt = Instant.parse("2026-07-16T18:00:00Z");
        endedAt = Instant.parse("2026-07-16T22:30:00Z");
        lenient().when(calendar.getCalendarConfig(campaignId)).thenReturn(CalendarService.DEFAULT_CALENDAR);
    }

    @Test
    void generatesDraftWithAllSections() {
        PartyMember pm = new PartyMember();
        pm.setCharacterName("Aria");
        pm.setPlayerName("Alice");
        session.setAttendees(List.of(pm));

        Scene scene = new Scene();
        scene.setTitle("The Dark Forest");
        SessionSceneVisit visit = new SessionSceneVisit();
        visit.setScene(scene);
        visit.setVisitedAt(Instant.parse("2026-07-16T18:15:00Z"));

        Encounter enc = new Encounter();
        enc.setId(UUID.randomUUID());
        enc.setName("Goblin Ambush");
        enc.setRound(4);
        CombatLogEntry logEntry = new CombatLogEntry();
        logEntry.setEncounter(enc);
        logEntry.setType(CombatLogEntry.EntryType.ENCOUNTER_ENDED);
        logEntry.setCreatedAt(Instant.parse("2026-07-16T19:00:00Z"));

        Combatant goblin = new Combatant();
        goblin.setId(UUID.randomUUID());
        goblin.setName("Goblin 1");
        CombatLogEntry defeated = log(enc, CombatLogEntry.EntryType.DEFEATED, 3,
                goblin.getId().toString(), "{}");
        CombatLogEntry damageOne = log(enc, CombatLogEntry.EntryType.DAMAGE, 2,
                goblin.getId().toString(), "{\"amount\":-20}");
        CombatLogEntry damageTwo = log(enc, CombatLogEntry.EntryType.DAMAGE, 4,
                goblin.getId().toString(), "{\"amount\":-17}");

        LedgerEntry ledger = new LedgerEntry();
        ledger.setDirection(LedgerEntry.Direction.GAIN);
        ledger.setAmount(new BigDecimal("100"));
        ledger.setCurrency("GP");
        ledger.setHolder("Aria");
        ledger.setNote("Quest reward");

        QuickNote qn = new QuickNote();
        qn.setTargetType("SCENE");
        qn.setTargetId(UUID.randomUUID());
        qn.setBody("Player asked about the old ruins");
        qn.setCreatedAt(Instant.parse("2026-07-16T20:00:00Z"));

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 13));
        lenient().when(calendar.getCalendarConfig(campaignId)).thenReturn(new CalendarService.CalendarConfig(
                new int[]{30, 30, 30, 30, 30, 30, 30},
                new String[]{"Hammer", "Alturiak", "Ches", "Tarsakh", "Mirtul", "Kythorn", "Flamerule"},
                new String[]{"Firstday"}));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of(visit));
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt))
                .thenReturn(List.of(damageOne, defeated, damageTwo, logEntry));
        lenient().when(combatants.findAllById(any())).thenReturn(List.of(goblin));
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of(ledger));
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of(qn));
        when(quickNoteService.targetLabel(qn)).thenReturn("The Dark Forest");

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("Session Date", "In-Game Date", "Attendance", "Scenes", "Encounters",
                "Loot & Ledger Changes", "Unresolved Quick Notes", "Recap", "Next-Session Hooks");
        assertThat(draft).contains("Aria — Alice");
        assertThat(draft).contains("The Dark Forest");
        assertThat(draft).contains("Started: 12 Flamerule 1492", "Ended: 13 Flamerule 1492");
        assertThat(draft).contains("Goblin Ambush — 4 rounds; defeated: Goblin 1; damage recorded: 37");
        assertThat(draft).contains("+100 GP");
        assertThat(draft).contains("SCENE / The Dark Forest: Player asked about the old ruins");
    }

    @Test
    void doesNotInventCurrencyForItemLedgerEntries() {
        var assignment = new dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment();
        assignment.setCustomText("Potion of Healing");
        assignment.setQuantity(2);
        LedgerEntry item = new LedgerEntry();
        item.setKind(LedgerEntry.Kind.ITEM);
        item.setDirection(LedgerEntry.Direction.GAIN);
        item.setItemAssignmentRef(assignment);
        item.setHolder("party stash");

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        lenient().when(calendar.getCalendarConfig(campaignId)).thenReturn(CalendarService.DEFAULT_CALENDAR);
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of(item));
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("+2× Potion of Healing — party stash");
        assertThat(draft).doesNotContain("+0 GP");
    }

    private CombatLogEntry log(Encounter encounter, CombatLogEntry.EntryType type, int round,
                               String combatantId, String payload) {
        CombatLogEntry entry = new CombatLogEntry();
        entry.setId(UUID.randomUUID());
        entry.setEncounter(encounter);
        entry.setType(type);
        entry.setRound(round);
        entry.setCombatantId(combatantId);
        entry.setPayload(payload);
        return entry;
    }

    @Test
    void handlesEmptySections() {
        PartyMember pm = new PartyMember();
        pm.setCharacterName("Borin");
        session.setAttendees(List.of(pm));

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("\u2014");
    }
}
