package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
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
}
