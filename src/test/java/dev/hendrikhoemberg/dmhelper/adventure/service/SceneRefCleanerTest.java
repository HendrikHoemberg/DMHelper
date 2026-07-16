package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AdventureService.class, SceneRefCleaner.class, dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class SceneRefCleanerTest {

    @Autowired private AdventureService adventureService;
    @Autowired private SceneRefCleaner cleaner;
    @MockitoBean private SessionActivityRecorder sessionActivity;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneSectionRepository sceneSectionRepository;
    @Autowired private SceneCheckRepository sceneCheckRepository;
    @Autowired private SceneParticipantRepository sceneParticipantRepository;
    @Autowired private SceneTransitionRepository sceneTransitionRepository;
    @Autowired private SceneLinkRepository sceneLinkRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private Scene scene;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        campaign = campaignRepository.save(campaign);
        var a = adventureService.createAdventure(campaign.getId(), "A", null, null);
        var ch = adventureService.createChapter(a.getId(), "Ch", null);
        scene = adventureService.createScene(ch.getId(), "Scene", null, null);
    }

    @Test
    void detachMapNullsMapAndPin() {
        GameMap m = new GameMap();
        m.setCampaign(campaign);
        m.setName("Map");
        m.setGridWidth(10);
        m.setGridHeight(10);
        m.setCellSizePx(48);
        m = gameMapRepository.save(m);
        adventureService.linkMap(scene.getId(), m.getId(), 5, 5);

        cleaner.detachMap(m.getId());
        em.flush();
        em.clear();

        Scene reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getMap()).isNull();
        assertThat(reloaded.getPinX()).isNull();
        assertThat(reloaded.getPinY()).isNull();
    }

    @Test
    void detachEncounterNullsRef() {
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName("Enc");
        e = encounterRepository.save(e);
        adventureService.linkEncounter(scene.getId(), e.getId());

        cleaner.detachEncounter(e.getId());
        em.flush();
        em.clear();

        assertThat(sceneRepository.findById(scene.getId()).orElseThrow().getEncounter()).isNull();
    }

    @Test
    void detachStatBlockAndHandoutRemoveFromLists() {
        StatBlock sb = new StatBlock();
        sb.setName("Goblin Custom");
        sb.setSource(StatBlock.Source.CUSTOM);
        sb.setCr("1");
        sb.setType("humanoid");
        sb.setHp("10");
        sb = statBlockRepository.save(sb);
        adventureService.addStatBlock(scene.getId(), sb.getId());

        Handout h = new Handout();
        h.setCampaign(campaign);
        h.setTitle("Letter");
        h.setFileName("letter.png");
        h.setContentType("image/png");
        h = handoutRepository.save(h);
        adventureService.addHandout(scene.getId(), h.getId());

        cleaner.detachStatBlock(sb.getId());
        cleaner.detachHandout(h.getId());
        em.flush();
        em.clear();

        Scene reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getStatBlocks()).isEmpty();
        assertThat(reloaded.getHandouts()).isEmpty();
    }

    @Test
    void deletingSceneCascadesOwnedSections() {
        var s = buildSceneWithChildren(newScene());
        var section = new SceneSection();
        section.setScene(s);
        section.setKind(SceneSectionKind.READ_ALOUD);
        section.setSortOrder(0);
        s.getSections().add(section);
        em.flush();

        UUID sectionId = section.getId();
        adventureService.deleteScene(s.getId());
        em.flush();

        assertThat(sceneSectionRepository.findById(sectionId)).isEmpty();
        assertThat(sceneRepository.findById(s.getId())).isEmpty();
    }

    @Test
    void deletingSceneCascadesChecks() {
        var s = buildSceneWithChildren(newScene());
        var check = new SceneCheck();
        check.setScene(s);
        check.setSortOrder(0);
        s.getChecks().add(check);
        em.flush();

        UUID checkId = check.getId();
        adventureService.deleteScene(s.getId());
        em.flush();

        assertThat(sceneCheckRepository.findById(checkId)).isEmpty();
    }

    @Test
    void deletingSceneCascadesParticipants() {
        var s = buildSceneWithChildren(newScene());
        var participant = new SceneParticipant();
        participant.setScene(s);
        participant.setDisplayName("Goblin");
        participant.setQuantity(1);
        participant.setSortOrder(0);
        s.getParticipants().add(participant);
        em.flush();

        UUID pId = participant.getId();
        adventureService.deleteScene(s.getId());
        em.flush();

        assertThat(sceneParticipantRepository.findById(pId)).isEmpty();
    }

    @Test
    void deletingSceneCascadesTransitions() {
        var s = buildSceneWithChildren(newScene());
        var transition = new SceneTransition();
        transition.setScene(s);
        transition.setKind(SceneTransitionKind.CHOICE);
        transition.setSortOrder(0);
        s.getTransitions().add(transition);
        em.flush();

        UUID tId = transition.getId();
        adventureService.deleteScene(s.getId());
        em.flush();

        assertThat(sceneTransitionRepository.findById(tId)).isEmpty();
    }

    @Test
    void deletingSceneCascadesLinks() {
        var s = buildSceneWithChildren(newScene());
        var link = new SceneLink();
        link.setScene(s);
        link.setRole(SceneLinkRole.REFERENCE);
        link.setTargetScope(SceneLinkTargetScope.PACKAGE);
        link.setTargetType("HANDOUT");
        link.setTargetId(UUID.randomUUID());
        link.setSortOrder(0);
        s.getLinks().add(link);
        em.flush();

        UUID linkId = link.getId();
        adventureService.deleteScene(s.getId());
        em.flush();

        assertThat(sceneLinkRepository.findById(linkId)).isEmpty();
    }



    private Scene newScene() {
        var s = new Scene();
        s.setChapter(scene.getChapter());
        s.setTitle("CascadeScene");
        s.setSortOrder(99);
        return s;
    }

    private Scene buildSceneWithChildren(Scene s) {
        sceneRepository.saveAndFlush(s);
        return s;
    }
}
