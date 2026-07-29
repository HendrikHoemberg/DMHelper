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
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollGroupCodec;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollOutcome;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
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
import java.time.ZoneId;
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
    @Mock private SessionObjectiveChangeRepository objectiveChanges;
    @Mock private QuestRepository questRepository;
    @Mock private QuestObjectiveRepository questObjectiveRepository;
    @Mock private TableRollLogRepository tableRollLogs;
    @Mock private TableRollGroupCodec codec;
    private SessionDraftService service;
    private final ZoneId zone = ZoneId.of("Europe/Berlin");

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
        service = new SessionDraftService(visits, combatLogs, combatants, ledgers, quickNotes,
                quickNoteService, calendar, objectiveChanges, questRepository,
                questObjectiveRepository, tableRollLogs, codec, zone);
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
                goblin.getId().toString(), "{\"name\":\"Goblin 1\"}");
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
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("Session Date", "In-Game Date", "Attendance", "Scenes", "Encounters",
                "Table Rolls", "Loot & Ledger Changes", "Unresolved Quick Notes", "Recap", "Next-Session Hooks");
        assertThat(draft).contains("Aria \u2014 Alice");
        assertThat(draft).contains("The Dark Forest");
        assertThat(draft).contains("Started: 12 Flamerule 1492", "Ended: 13 Flamerule 1492");
        assertThat(draft).contains("Goblin Ambush \u2014 4 rounds; defeated: Goblin 1; damage recorded: 37");
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
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("+2\u00d7 Potion of Healing \u2014 party stash");
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
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(objectiveChanges.findBySessionIdOrderByChangedAtAscIdAsc(session.getId())).thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("\u2014");
    }

    @Test
    void includesQuestProgressSectionAfterScenes() {
        UUID questId = UUID.randomUUID();
        Quest quest = new Quest();
        quest.setId(questId);
        quest.setTitle("Find the Artifact");

        UUID objId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setTitle("Talk to the sage");

        SessionObjectiveChange change = new SessionObjectiveChange();
        change.setId(UUID.randomUUID());
        change.setObjective(obj);
        change.setPreviousStatus(QuestObjectiveStatus.NOT_STARTED);
        change.setNewStatus(QuestObjectiveStatus.ACTIVE);
        change.setChangedAt(Instant.parse("2026-07-16T19:00:00Z"));

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(objectiveChanges.findBySessionIdOrderByChangedAtAscIdAsc(session.getId())).thenReturn(List.of(change));
        when(questObjectiveRepository.findById(objId)).thenReturn(java.util.Optional.of(obj));
        lenient().when(questRepository.findById(questId)).thenReturn(java.util.Optional.of(quest));
        lenient().when(questObjectiveRepository.findByQuestIdOrderBySortOrderAsc(any())).thenReturn(List.of(obj));

        obj.setQuest(quest);

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("## Scenes");
        assertThat(draft).contains("## Quest Progress");
        assertThat(draft).contains("## Encounters");
        int scenesIdx = draft.indexOf("## Scenes");
        int questProgressIdx = draft.indexOf("## Quest Progress");
        int encountersIdx = draft.indexOf("## Encounters");
        assertThat(scenesIdx).isLessThan(questProgressIdx);
        assertThat(questProgressIdx).isLessThan(encountersIdx);
        assertThat(draft).contains("Find the Artifact | Talk to the sage | NOT_STARTED \u2192 ACTIVE");
    }

    @Test
    void showsObjectiveChangesInChronologicalOrder() {
        UUID questId = UUID.randomUUID();
        Quest quest = new Quest();
        quest.setId(questId);
        quest.setTitle("Test Quest");

        UUID objId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setTitle("Step one");
        obj.setQuest(quest);

        SessionObjectiveChange first = new SessionObjectiveChange();
        first.setId(UUID.randomUUID());
        first.setObjective(obj);
        first.setPreviousStatus(QuestObjectiveStatus.NOT_STARTED);
        first.setNewStatus(QuestObjectiveStatus.ACTIVE);
        first.setChangedAt(Instant.parse("2026-07-16T18:30:00Z"));

        SessionObjectiveChange second = new SessionObjectiveChange();
        second.setId(UUID.randomUUID());
        second.setObjective(obj);
        second.setPreviousStatus(QuestObjectiveStatus.ACTIVE);
        second.setNewStatus(QuestObjectiveStatus.COMPLETED);
        second.setChangedAt(Instant.parse("2026-07-16T19:00:00Z"));

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(objectiveChanges.findBySessionIdOrderByChangedAtAscIdAsc(session.getId())).thenReturn(List.of(first, second));
        when(questObjectiveRepository.findById(objId)).thenReturn(java.util.Optional.of(obj));
        lenient().when(questRepository.findById(questId)).thenReturn(java.util.Optional.of(quest));
        lenient().when(questObjectiveRepository.findByQuestIdOrderBySortOrderAsc(any())).thenReturn(List.of(obj));

        String draft = service.generate(session, endedAt);

        assertThat(draft.indexOf("NOT_STARTED")).isLessThan(draft.indexOf("ACTIVE \u2192 COMPLETED"));
    }

    @Test
    void repeatedChangesToSameObjectiveAppearInOrder() {
        UUID questId = UUID.randomUUID();
        Quest quest = new Quest();
        quest.setId(questId);
        quest.setTitle("Multi Step");

        UUID objId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setTitle("The objective");
        obj.setQuest(quest);

        SessionObjectiveChange change1 = new SessionObjectiveChange();
        change1.setId(UUID.randomUUID());
        change1.setObjective(obj);
        change1.setPreviousStatus(null);
        change1.setNewStatus(QuestObjectiveStatus.NOT_STARTED);
        change1.setChangedAt(Instant.parse("2026-07-16T18:00:00Z"));

        SessionObjectiveChange change2 = new SessionObjectiveChange();
        change2.setId(UUID.randomUUID());
        change2.setObjective(obj);
        change2.setPreviousStatus(QuestObjectiveStatus.NOT_STARTED);
        change2.setNewStatus(QuestObjectiveStatus.ACTIVE);
        change2.setChangedAt(Instant.parse("2026-07-16T18:30:00Z"));

        SessionObjectiveChange change3 = new SessionObjectiveChange();
        change3.setId(UUID.randomUUID());
        change3.setObjective(obj);
        change3.setPreviousStatus(QuestObjectiveStatus.ACTIVE);
        change3.setNewStatus(QuestObjectiveStatus.COMPLETED);
        change3.setChangedAt(Instant.parse("2026-07-16T19:00:00Z"));

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(objectiveChanges.findBySessionIdOrderByChangedAtAscIdAsc(session.getId())).thenReturn(List.of(change1, change2, change3));
        when(questObjectiveRepository.findById(objId)).thenReturn(java.util.Optional.of(obj));
        lenient().when(questRepository.findById(questId)).thenReturn(java.util.Optional.of(quest));
        lenient().when(questObjectiveRepository.findByQuestIdOrderBySortOrderAsc(any())).thenReturn(List.of(obj));

        String draft = service.generate(session, endedAt);

        assertThat(draft.indexOf("null \u2192 NOT_STARTED")).isLessThan(draft.indexOf("NOT_STARTED \u2192 ACTIVE"));
        assertThat(draft.indexOf("NOT_STARTED \u2192 ACTIVE")).isLessThan(draft.indexOf("ACTIVE \u2192 COMPLETED"));
    }

    @Test
    void includesTableRollsFromSessionWindow() {
        TableRollLog log1 = new TableRollLog();
        log1.setId(UUID.randomUUID());
        log1.setTableNameSnapshot("Forest Encounters");
        log1.setResultJson("{}");

        TableRollLog log2 = new TableRollLog();
        log2.setId(UUID.randomUUID());
        log2.setTableNameSnapshot("Forest Encounters");
        log2.setResultJson("{}");

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of(log1, log2));
        when(codec.decode("{}", log1.getId())).thenReturn(List.of(
                new TableRollOutcome("forest-enc", "Forest Encounters",
                        new DiceResult("1d12", List.of(), 0, 5, false, false),
                        "old-shrine", "An old shrine", null, List.of(), List.of())
        ));
        when(codec.decode("{}", log2.getId())).thenReturn(List.of(
                new TableRollOutcome("forest-enc", "Forest Encounters",
                        new DiceResult("1d12", List.of(), 0, 3, false, false),
                        "wolves", "2 wolves", null, List.of(), List.of()),
                new TableRollOutcome("forest-enc", "Forest Encounters",
                        new DiceResult("1d12", List.of(), 0, 8, false, false),
                        "wolves", "2 wolves", null, List.of(), List.of())
        ));

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("## Table Rolls");
        assertThat(draft).contains("Forest Encounters \u2014 old-shrine \u2014 An old shrine");
        assertThat(draft).contains("Forest Encounters \u2014 wolves \u2014 2 wolves");
    }

    @Test
    void formatsSessionRangeOnSameDay() {
        assertThat(service.formatSessionRange(
                Instant.parse("2026-07-16T18:00:00Z"),
                Instant.parse("2026-07-16T20:30:00Z")))
                .isEqualTo("16 July 2026, 20:00\u201322:30 Europe/Berlin");
    }

    @Test
    void formatsSessionRangeAcrossMidnight() {
        assertThat(service.formatSessionRange(
                Instant.parse("2026-07-16T21:30:00Z"),
                Instant.parse("2026-07-16T22:30:00Z")))
                .isEqualTo("16 July 2026, 23:30 Europe/Berlin\u201317 July 2026, 00:30 Europe/Berlin");
    }

    @Test
    void anEncounterStillRunningAtSessionEndIsRecordedAsUnfinished() {
        Encounter enc = new Encounter();
        enc.setId(UUID.randomUUID());
        enc.setName("Bereich 2: Goblinwachposten");
        enc.setRound(3);

        CombatLogEntry damage = log(enc, CombatLogEntry.EntryType.DAMAGE, 2,
                UUID.randomUUID().toString(), "{\"amount\":-15}");

        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of(damage));
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).contains("Bereich 2: Goblinwachposten");
        assertThat(draft).contains("still in progress");
    }

    @Test
    void draftOmitsPresentationSafetySection() {
        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId())).thenReturn(List.of());
        when(combatLogs.findSessionEvidence(campaignId, startedAt, endedAt)).thenReturn(List.of());
        when(ledgers.findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(quickNotes.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());
        when(tableRollLogs.findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(campaignId, startedAt, endedAt))
                .thenReturn(List.of());

        String draft = service.generate(session, endedAt);

        assertThat(draft).doesNotContain("Presentation Safety Overrides");
        assertThat(draft).contains("## Scenes", "## Encounters", "## Recap");
    }

}
