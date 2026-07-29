package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CockpitRuntimeModuleViewServiceTest {

    @Autowired
    private CockpitRuntimeModuleViewService service;

    @Autowired
    private PopulatedCampaignFixture fixture;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private GameMapRepository gameMapRepository;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private CombatantRepository combatantRepository;

    @Autowired
    private PartyMemberRepository partyMemberRepository;

    @Autowired
    private CampaignSessionRepository sessionRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private AdventureService adventureService;

    @Autowired
    private QuickNoteRepository quickNoteRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void storyViewIsDetachedSafe() {
        var seeded = fixture.seed();
        adventureService.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
        entityManager.flush();
        var view = service.story(seeded.campaignId());
        entityManager.clear();
        assertThat(view.sceneId()).isEqualTo(seeded.richSceneId());
        assertThat(view.title()).isEqualTo("Der Schreibtisch");
        assertThat(view.readAloud()).isNotNull();
        assertThat(view.sections()).isNotEmpty();
        assertThat(view.checks()).isNotEmpty();
        assertThat(view.participants()).isNotEmpty();
        assertThat(view.transitions()).isNotEmpty();
        assertThat(view.links()).isNotEmpty();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void storyViewReturnsNullWhenNoScene() {
        var seeded = fixture.seed();
        var view = service.story(seeded.campaignId());
        entityManager.clear();
        assertThat(view).isNull();
    }

    @Test
    void mapViewIsDetachedSafe() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Test Map");
        map.setGridWidth(30);
        map.setGridHeight(20);
        map.setCellSizePx(48);
        map = gameMapRepository.save(map);
        entityManager.flush();

        var view = service.map(seeded.campaignId(), map.getId());
        entityManager.clear();
        assertThat(view.mapId()).isEqualTo(map.getId());
        assertThat(view.name()).isEqualTo("Test Map");
        assertThat(view.gridWidth()).isEqualTo(30);
        assertThat(view.gridHeight()).isEqualTo(20);
        assertThat(view.cellSizePx()).isEqualTo(48);
        assertThat(view.maps()).isNotEmpty();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void mapViewReturnsNullWhenNoMap() {
        var seeded = fixture.seed();
        var view = service.map(seeded.campaignId(), null);
        entityManager.clear();
        assertThat(view).isNull();
    }

    @Test
    void encounterViewIsDetachedSafe() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Encounter Map");
        map.setGridWidth(20);
        map.setGridHeight(15);
        map.setCellSizePx(48);
        map = gameMapRepository.save(map);

        Encounter active = new Encounter();
        active.setCampaign(campaign);
        active.setName("Active Fight");
        active.setMap(map);
        active.setStatus(Encounter.Status.ACTIVE);
        active = encounterRepository.save(active);

        Combatant c = new Combatant();
        c.setEncounter(active);
        c.setName("Goblin");
        c.setMaxHp(10);
        c.setCurrentHp(10);
        c.setSortOrder(0);
        combatantRepository.save(c);

        Encounter planned = new Encounter();
        planned.setCampaign(campaign);
        planned.setName("Planned Fight");
        planned.setMap(map);
        planned.setStatus(Encounter.Status.PLANNED);
        encounterRepository.save(planned);
        entityManager.flush();

        var view = service.encounter(seeded.campaignId());
        entityManager.clear();
        assertThat(view.activeEncounterId()).isEqualTo(active.getId());
        assertThat(view.activeEncounterName()).isEqualTo("Active Fight");
        assertThat(view.combatants()).isNotEmpty();
        assertThat(view.planned()).isNotEmpty();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void sessionPlanViewIsDetachedSafe() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        Note planNote = new Note();
        planNote.setCampaign(campaign);
        planNote.setType(NoteType.SESSION_PLAN);
        planNote.setTitle("Tonight's Plan");
        planNote.setBody("Plan details");
        planNote.setDmOnly(false);
        entityManager.persist(planNote);

        var view = service.sessionPlan(seeded.campaignId());
        entityManager.clear();
        assertThat(view.title()).isEqualTo("Tonight's Plan");
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void partyViewIsDetachedSafe() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName("Test Hero");
        member.setAc(16);
        member.setMaxHp(32);
        member.setCurrentHp(32);
        member.setInitiativeBonus(3);
        member.setSpeed(30);
        member.setPassivePerception(14);
        member.setPassiveInsight(12);
        member.setPassiveInvestigation(11);
        member.setActive(true);
        partyMemberRepository.save(member);
        entityManager.flush();

        var view = service.party(seeded.campaignId());
        entityManager.clear();
        assertThat(view.members()).isNotEmpty();
        assertThat(view.members().get(0).name()).isEqualTo("Test Hero");
        assertThat(view.members().get(0).ac()).isEqualTo(16);
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void quickNotesViewIsDetachedSafe() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        QuickNote qn = new QuickNote();
        qn.setCampaign(campaign);
        qn.setTargetType("CAMPAIGN");
        qn.setTargetId(seeded.campaignId());
        qn.setBody("Remember the hidden door.");
        quickNoteRepository.save(qn);
        entityManager.flush();

        var view = service.quickNotes(seeded.campaignId());
        entityManager.clear();
        assertThat(view.notes()).isNotEmpty();
        assertThat(view.notes().get(0).body()).isEqualTo("Remember the hidden door.");
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void referenceViewIsDetachedSafe() {
        var seeded = fixture.seed();
        var view = service.reference(seeded.campaignId());
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void audioViewIsDetachedSafe() {
        var seeded = fixture.seed();
        var view = service.audio(seeded.campaignId());
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    @Test
    void sessionLogViewIsDetachedSafe() {
        var seeded = fixture.seed();
        Campaign campaign = campaignRepository.findById(seeded.campaignId()).orElseThrow();
        CampaignSession session = new CampaignSession();
        session.setCampaign(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setStartedAt(Instant.now().minusSeconds(3600));
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        entityManager.flush();

        var view = service.sessionLog(seeded.campaignId(), CockpitModuleMode.STANDARD);
        entityManager.clear();
        assertThat(view).isNotNull();
        assertThat(allRecordComponentTypes(view.getClass()))
                .noneMatch(type -> type.isAnnotationPresent(Entity.class));
    }

    private static List<Class<?>> allRecordComponentTypes(Class<?> recordClass) {
        if (recordClass.getRecordComponents() == null) {
            return List.of();
        }
        java.lang.reflect.RecordComponent[] components = recordClass.getRecordComponents();
        java.util.ArrayList<Class<?>> types = new java.util.ArrayList<>();
        for (java.lang.reflect.RecordComponent rc : components) {
            types.add(rc.getType());
        }
        return types;
    }
}
