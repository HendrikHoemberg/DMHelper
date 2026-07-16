package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({AdventureService.class, SceneTransitionService.class, SceneRefCleaner.class, dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class AdventureServiceTest {

    @Autowired private AdventureService service;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EntityManager em;
    @MockitoBean private SessionActivityRecorder sessionActivity;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
    }

    private GameMap map(String name) {
        GameMap m = new GameMap();
        m.setCampaign(campaign);
        m.setName(name);
        m.setGridWidth(10);
        m.setGridHeight(10);
        m.setCellSizePx(48);
        return gameMapRepository.save(m);
    }

    private Encounter encounter(String name) {
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName(name);
        return encounterRepository.save(e);
    }

    @Test
    void createAssignsSequentialSortOrder() {
        var a1 = service.createAdventure(campaign.getId(), "One", null, null);
        var a2 = service.createAdventure(campaign.getId(), "Two", null, null);
        assertThat(a1.getSortOrder()).isEqualTo(0);
        assertThat(a2.getSortOrder()).isEqualTo(1);

        var ch1 = service.createChapter(a1.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a1.getId(), "Ch 2", null);
        assertThat(ch1.getSortOrder()).isEqualTo(0);
        assertThat(ch2.getSortOrder()).isEqualTo(1);

        var s1 = service.createScene(ch1.getId(), "Gate", "1", null);
        var s2 = service.createScene(ch1.getId(), "Shrine", "2", null);
        assertThat(s1.getSortOrder()).isEqualTo(0);
        assertThat(s2.getSortOrder()).isEqualTo(1);
        assertThat(s1.getStatus()).isEqualTo(SceneStatus.UNVISITED);
    }

    @Test
    void moveSwapsNeighborsAndIsNoOpAtEdges() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s1 = service.createScene(ch.getId(), "First", null, null);
        var s2 = service.createScene(ch.getId(), "Second", null, null);

        service.moveScene(s2.getId(), -1);
        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId());
        assertThat(scenes).extracting(Scene::getTitle).containsExactly("Second", "First");

        service.moveScene(s2.getId(), -1); // already first — no-op
        scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId());
        assertThat(scenes).extracting(Scene::getTitle).containsExactly("Second", "First");
    }

    @Test
    void moveSceneToChapterAppendsAtEnd() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch1 = service.createChapter(a.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a.getId(), "Ch 2", null);
        var s1 = service.createScene(ch1.getId(), "Moving", null, null);
        service.createScene(ch2.getId(), "Existing", null, null);

        service.moveSceneToChapter(s1.getId(), ch2.getId());

        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch2.getId());
        assertThat(scenes).extracting(Scene::getTitle).containsExactly("Existing", "Moving");
        assertThat(sceneRepository.findByChapterIdOrderBySortOrderAsc(ch1.getId())).isEmpty();
    }

    @Test
    void flattenedScenesWalksChaptersInOrder() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch1 = service.createChapter(a.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a.getId(), "Ch 2", null);
        service.createScene(ch1.getId(), "1a", null, null);
        service.createScene(ch1.getId(), "1b", null, null);
        service.createScene(ch2.getId(), "2a", null, null);

        assertThat(service.flattenedScenes(a.getId()))
                .extracting(Scene::getTitle).containsExactly("1a", "1b", "2a");
    }

    @Test
    void linksStorePinAndCanBeCleared() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);
        var m = map("Throne Room");
        var e = encounter("Ambush");

        s = service.linkMap(s.getId(), m.getId(), 576, 240);
        assertThat(s.getMap().getId()).isEqualTo(m.getId());
        assertThat(s.getPinX()).isEqualTo(576);
        assertThat(s.getPinY()).isEqualTo(240);

        s = service.linkEncounter(s.getId(), e.getId());
        assertThat(s.getEncounter().getId()).isEqualTo(e.getId());

        s = service.unlinkMap(s.getId());
        assertThat(s.getMap()).isNull();
        assertThat(s.getPinX()).isNull();
        assertThat(s.getPinY()).isNull();

        s = service.unlinkEncounter(s.getId());
        assertThat(s.getEncounter()).isNull();
    }

    @Test
    void deleteAdventureCascadesDownwardOnlyAndClearsCursor() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);
        var m = map("Survivor Map");
        var e = encounter("Survivor Encounter");
        service.linkMap(s.getId(), m.getId(), 10, 10);
        service.linkEncounter(s.getId(), e.getId());
        campaign.setCurrentSceneId(s.getId());
        campaignRepository.save(campaign);

        service.deleteAdventure(a.getId());
        em.flush();
        em.clear();

        assertThat(sceneRepository.findById(s.getId())).isEmpty();
        assertThat(gameMapRepository.findById(m.getId())).isPresent();
        assertThat(encounterRepository.findById(e.getId())).isPresent();
        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isNull();
    }

    @Test
    void deleteSceneClearsCursorWhenCurrent() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);
        campaign.setCurrentSceneId(s.getId());
        campaignRepository.save(campaign);

        service.deleteScene(s.getId());

        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isNull();
    }

    @Test
    void setCurrentSceneBumpsUnvisitedToVisitedOnly() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);

        service.setCurrentScene(campaign.getId(), s.getId());
        assertThat(service.findSceneById(s.getId()).getStatus()).isEqualTo(SceneStatus.VISITED);
        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isEqualTo(s.getId());

        service.setStatus(s.getId(), SceneStatus.DONE);
        service.setCurrentScene(campaign.getId(), s.getId());
        assertThat(service.findSceneById(s.getId()).getStatus()).isEqualTo(SceneStatus.DONE);

        verify(sessionActivity, times(2)).sceneSelected(eq(campaign.getId()), any());
        verify(sessionActivity, times(1)).sceneCompleted(any());
    }

    @Test
    void stepWalksAcrossChaptersAndStopsAtEdges() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch1 = service.createChapter(a.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a.getId(), "Ch 2", null);
        var s1 = service.createScene(ch1.getId(), "1a", null, null);
        var s2 = service.createScene(ch2.getId(), "2a", null, null);

        service.setCurrentScene(campaign.getId(), s1.getId());

        var next = service.stepCurrentScene(campaign.getId(), 1);
        assertThat(next).isPresent();
        assertThat(next.get().getId()).isEqualTo(s2.getId());

        // at the last scene: stays put
        var edge = service.stepCurrentScene(campaign.getId(), 1);
        assertThat(edge).isPresent();
        assertThat(edge.get().getId()).isEqualTo(s2.getId());

        var back = service.stepCurrentScene(campaign.getId(), -1);
        assertThat(back).isPresent();
        assertThat(back.get().getId()).isEqualTo(s1.getId());

        verify(sessionActivity, times(3)).sceneSelected(eq(campaign.getId()), any());
    }

    @Test
    void stepWithoutCursorIsEmptyAndStaleCursorSelfHeals() {
        assertThat(service.stepCurrentScene(campaign.getId(), 1)).isEmpty();

        campaign.setCurrentSceneId(UUID.randomUUID()); // points at nothing
        campaignRepository.save(campaign);
        assertThat(service.getCurrentScene(campaign.getId())).isEmpty();
        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isNull();

        verify(sessionActivity, never()).sceneSelected(any(), any());
        verify(sessionActivity, never()).sceneCompleted(any());
    }
}
