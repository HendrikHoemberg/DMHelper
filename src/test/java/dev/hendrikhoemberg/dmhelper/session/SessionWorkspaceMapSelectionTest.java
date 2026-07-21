package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionWorkspaceMapSelectionTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameMapRepository mapRepository;
    @Autowired private AdventureService adventureService;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SessionLifecycleService lifecycleService;
    @Autowired private SessionWorkspaceService workspaceService;

    private UUID campaignId;
    private GameMap sceneMap;
    private GameMap otherMap;

    @BeforeEach
    void seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Selection Test");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        sceneMap = new GameMap();
        sceneMap.setCampaign(campaign);
        sceneMap.setName("Scene Map");
        sceneMap = mapRepository.save(sceneMap);

        otherMap = new GameMap();
        otherMap.setCampaign(campaign);
        otherMap.setName("Other Map");
        otherMap = mapRepository.save(otherMap);

        Adventure adventure = adventureService.createAdventure(campaignId, "A", null, null);
        Chapter chapter = adventureService.createChapter(adventure.getId(), "C1", null);
        Scene scene = adventureService.createScene(chapter.getId(), "S1", null, null);
        scene.setMap(sceneMap);
        sceneRepository.save(scene);
        adventureService.setCurrentScene(campaignId, scene.getId());
    }

    @Test
    void openSessionWithoutStoredMapFallsThroughToCurrentSceneMap() {
        lifecycleService.start(campaignId, null);

        SessionWorkspaceService.SessionWorkspace ws = workspaceService.load(campaignId, null);

        assertThat(ws.workspaceMap()).isNotNull();
        assertThat(ws.workspaceMap().getId()).isEqualTo(sceneMap.getId());
        assertThat(ws.selectionSource())
                .isEqualTo(SessionWorkspaceService.SelectionSource.CURRENT_SCENE);
    }

    @Test
    void explicitlyRequestedMapWinsEvenWithOpenSession() {
        lifecycleService.start(campaignId, null);

        SessionWorkspaceService.SessionWorkspace ws =
                workspaceService.load(campaignId, otherMap.getId());

        assertThat(ws.workspaceMap().getId()).isEqualTo(otherMap.getId());
        assertThat(ws.selectionSource())
                .isEqualTo(SessionWorkspaceService.SelectionSource.EXPLICIT_MAP);
    }
}
