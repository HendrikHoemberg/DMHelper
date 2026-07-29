package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntry;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntryRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionLogModuleServiceTest {

    @Autowired
    private SessionLogModuleService service;

    @Autowired
    private PopulatedCampaignFixture fixture;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignSessionRepository sessionRepository;

    @Autowired
    private SessionSceneVisitRepository visitRepository;

    @Autowired
    private CombatLogEntryRepository combatLogRepository;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private SessionObjectiveChangeRepository objectiveChangeRepository;

    @Autowired
    private SessionAuditEntryRepository auditRepository;

    @Autowired
    private QuickNoteRepository quickNoteRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private AdventureService adventureService;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void sessionLogViewIsDetachedSafe() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(view.sessionStatus()).isEqualTo("RUNNING");
        assertThat(view.timeRange()).isNotNull();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(jakarta.persistence.Entity.class));
    }

    @Test
    void sessionLogViewReturnsNullWhenNoSession() {
        var seeded = fixture.seed();
        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view).isNull();
    }

    @Test
    void compactModeShowsLastThreeEventsAndUnresolvedCount() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());

        var chapter = chapterRepository.findById(seeded.chapterOneId()).orElseThrow();
        adventureService.createScene(chapter.getId(), "First Scene", "S1", "desc");
        adventureService.createScene(chapter.getId(), "Second Scene", "S2", "desc");
        adventureService.createScene(chapter.getId(), "Third Scene", "S3", "desc");
        adventureService.createScene(chapter.getId(), "Fourth Scene", "S4", "desc");

        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(chapter.getId());
        scenes.forEach(s -> {
            var visit = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
            visit.setSession(session);
            visit.setScene(s);
            visit.setVisitedAt(Instant.now());
            visitRepository.save(visit);
        });
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.COMPACT);
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(view.currentEvents()).hasSize(3);
        assertThat(view.reviewDraft()).isNull();
    }

    @Test
    void compactModeShowsNoEventsWhenNoneExist() {
        var seeded = fixture.seed();
        startSession(seeded.campaignId());

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.COMPACT);
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(view.currentEvents()).isEmpty();
        assertThat(view.unresolvedQuickNoteCount()).isZero();
    }

    @Test
    void standardModeShowsFullTimeline() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());
        var chapter = chapterRepository.findById(seeded.chapterOneId()).orElseThrow();
        adventureService.createScene(chapter.getId(), "First Scene", "S1", "desc");
        adventureService.createScene(chapter.getId(), "Second Scene", "S2", "desc");

        var firstScene = adventureService.createScene(chapter.getId(), "First Scene", "S1", "desc");
        var secondScene = adventureService.createScene(chapter.getId(), "Second Scene", "S2", "desc");
        var v1 = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        v1.setSession(session); v1.setScene(firstScene); v1.setVisitedAt(Instant.now());
        visitRepository.save(v1);
        var v2 = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        v2.setSession(session); v2.setScene(secondScene); v2.setVisitedAt(Instant.now());
        visitRepository.save(v2);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        assertThat(view).isNotNull();
        assertThat(view.currentEvents()).hasSize(2);
    }

    @Test
    void focusedModeIncludesReviewDraftWhenStatusIsReview() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());
        session.setStatus(CampaignSession.Status.REVIEW);
        session.setDraftBody("## Recap\nThe party explored the crypt.\n");
        sessionRepository.save(session);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.FOCUSED);
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(view.reviewDraft()).isEqualTo("## Recap\nThe party explored the crypt.\n");
    }

    @Test
    void focusedModeExcludesReviewDraftWhenStatusIsNotReview() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());
        session.setDraftBody("## Recap\nShould not appear.\n");
        sessionRepository.save(session);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.FOCUSED);
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(view.reviewDraft()).isNull();
    }

    @Test
    void sceneVisitsAppearAsEvents() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());
        var chapter = chapterRepository.findById(seeded.chapterOneId()).orElseThrow();
        var scene = adventureService.createScene(chapter.getId(), "The Crypt Entrance", "S1", "desc");

        var visit = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        visit.setSession(session);
        visit.setScene(scene);
        visit.setVisitedAt(Instant.now());
        visitRepository.save(visit);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.currentEvents()).anySatisfy(e -> {
            assertThat(e.kind()).isEqualTo("Scene visited");
            assertThat(e.title()).isEqualTo("The Crypt Entrance");
            assertThat(e.warning()).isFalse();
        });
    }

    @Test
    void encounterEndedAppearsAsEvent() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        var session = startSession(seeded.campaignId());

        Encounter enc = new Encounter();
        enc.setCampaign(campaign);
        enc.setName("Goblin Fight");
        enc.setStatus(Encounter.Status.DONE);
        enc = encounterRepository.save(enc);

        CombatLogEntry log = new CombatLogEntry();
        log.setEncounter(enc);
        log.setType(CombatLogEntry.EntryType.ENCOUNTER_ENDED);
        log.setCombatantId("");
        log.setSequence(1);
        log.setRound(3);
        combatLogRepository.save(log);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.currentEvents()).anySatisfy(e -> {
            assertThat(e.kind()).isEqualTo("Encounter ended");
            assertThat(e.title()).isEqualTo("Goblin Fight");
            assertThat(e.warning()).isFalse();
        });
    }

    @Test
    void objectiveChangeAppearsAsEvent() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());

        seedObjectiveChange(session, "Defeat the boss", "ACTIVE", "COMPLETED");

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.currentEvents()).anySatisfy(e -> {
            assertThat(e.kind()).isEqualTo("Objective updated");
            assertThat(e.title()).isEqualTo("Defeat the boss");
            assertThat(e.warning()).isFalse();
        });
    }

    @Test
    void recentSavedLogsAppearInStandardMode() {
        var seeded = fixture.seed();
        var campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        startSession(seeded.campaignId());

        Note logNote = new Note();
        logNote.setCampaign(campaign);
        logNote.setType(NoteType.SESSION_LOG);
        logNote.setTitle("Crypt Session");
        logNote.setBody("Session notes here");
        logNote.setDmOnly(true);
        noteRepository.save(logNote);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.recentSavedLogs()).anySatisfy(sl -> {
            assertThat(sl.title()).isEqualTo("Crypt Session");
            assertThat(sl.noteId()).isEqualTo(logNote.getId());
            assertThat(sl.url()).contains("/notes/" + logNote.getId());
        });
    }

    @Test
    void unresolvedQuickNoteCountIsReported() {
        var seeded = fixture.seed();
        var campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        startSession(seeded.campaignId());

        QuickNote qn = new QuickNote();
        qn.setCampaign(campaign);
        qn.setTargetType("CAMPAIGN");
        qn.setTargetId(seeded.campaignId());
        qn.setBody("Unresolved clue");
        quickNoteRepository.save(qn);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.unresolvedQuickNoteCount()).isPositive();
    }

    @Test
    void eventsOrderedByOccurredAtDesc() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());
        var chapter = chapterRepository.findById(seeded.chapterOneId()).orElseThrow();
        var first = adventureService.createScene(chapter.getId(), "First", "S1", "desc");
        var second = adventureService.createScene(chapter.getId(), "Second", "S2", "desc");
        var third = adventureService.createScene(chapter.getId(), "Third", "S3", "desc");

        var v1 = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        v1.setSession(session); v1.setScene(first); v1.setVisitedAt(Instant.now().minusSeconds(10));
        visitRepository.save(v1);
        var v2 = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        v2.setSession(session); v2.setScene(second); v2.setVisitedAt(Instant.now().minusSeconds(5));
        visitRepository.save(v2);
        var v3 = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        v3.setSession(session); v3.setScene(third); v3.setVisitedAt(Instant.now());
        visitRepository.save(v3);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        List<SessionLogModuleService.LogEventView> events = view.currentEvents();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).title()).isEqualTo("Third");
        assertThat(events.get(1).title()).isEqualTo("Second");
        assertThat(events.get(2).title()).isEqualTo("First");
    }

    @Test
    void logEventKindLabelsAreReadable() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());
        var chapter = chapterRepository.findById(seeded.chapterOneId()).orElseThrow();
        var scene = adventureService.createScene(chapter.getId(), "Any Scene", "S1", "desc");

        var visit = new dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit();
        visit.setSession(session); visit.setScene(scene); visit.setVisitedAt(Instant.now());
        visitRepository.save(visit);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.currentEvents()).allSatisfy(e -> {
            assertThat(e.kind()).doesNotContain("_");
            assertThat(e.kind()).doesNotMatch("[A-Z_]+");
        });
    }

    @Test
    void eventsExcludeNonFinalEncounterEntries() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        var session = startSession(seeded.campaignId());

        Encounter enc = new Encounter();
        enc.setCampaign(campaign);
        enc.setName("Ongoing Fight");
        enc.setStatus(Encounter.Status.ACTIVE);
        enc = encounterRepository.save(enc);

        CombatLogEntry dmg = new CombatLogEntry();
        dmg.setEncounter(enc);
        dmg.setType(CombatLogEntry.EntryType.DAMAGE);
        dmg.setCombatantId("");
        dmg.setSequence(1);
        dmg.setRound(1);
        combatLogRepository.save(dmg);

        CombatLogEntry turn = new CombatLogEntry();
        turn.setEncounter(enc);
        turn.setType(CombatLogEntry.EntryType.TURN_START);
        turn.setCombatantId("");
        turn.setSequence(2);
        turn.setRound(1);
        combatLogRepository.save(turn);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view.currentEvents()).noneMatch(e -> e.title().contains("Ongoing Fight"));
    }

    @Test
    void sessionStatusReflectsCurrentState() {
        var seeded = fixture.seed();
        var session = startSession(seeded.campaignId());

        var runningView = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        assertThat(runningView.sessionStatus()).isEqualTo("RUNNING");

        session.setStatus(CampaignSession.Status.PAUSED);
        sessionRepository.save(session);
        entityManager.flush();

        var pausedView = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        assertThat(pausedView.sessionStatus()).isEqualTo("PAUSED");
    }

    private CampaignSession startSession(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        CampaignSession session = new CampaignSession();
        session.setCampaign(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setStartedAt(Instant.now().minusSeconds(3600));
        session.setUpdatedAt(Instant.now());
        session = sessionRepository.save(session);
        entityManager.flush();
        return session;
    }

    private void seedObjectiveChange(CampaignSession session, String objectiveTitle,
                                      String previousStatus, String newStatus) {
        var campaign = session.getCampaign();
        Quest quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Test Quest");
        entityManager.persist(quest);

        QuestObjective obj = new QuestObjective();
        obj.setQuest(quest);
        obj.setTitle(objectiveTitle);
        entityManager.persist(obj);
        entityManager.flush();

        SessionObjectiveChange change = new SessionObjectiveChange();
        change.setSession(session);
        change.setObjective(obj);
        change.setPreviousStatus(QuestObjectiveStatus.valueOf(previousStatus));
        change.setNewStatus(QuestObjectiveStatus.valueOf(newStatus));
        change.setChangedAt(Instant.now());
        objectiveChangeRepository.save(change);
        entityManager.flush();
    }

    private static List<Class<?>> allRecordComponentTypes(Class<?> recordClass) {
        if (recordClass.getRecordComponents() == null) return List.of();
        java.lang.reflect.RecordComponent[] components = recordClass.getRecordComponents();
        java.util.ArrayList<Class<?>> types = new java.util.ArrayList<>();
        for (java.lang.reflect.RecordComponent rc : components) {
            types.add(rc.getType());
        }
        return types;
    }
}
