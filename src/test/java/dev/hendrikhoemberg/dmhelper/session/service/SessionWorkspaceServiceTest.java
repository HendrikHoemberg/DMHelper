package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableLinkService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionWorkspaceServiceTest {

    @Mock private CampaignRepository campaigns;
    @Mock private CampaignSessionRepository sessions;
    @Mock private AdventureService adventures;
    @Mock private EncounterRepository encounters;
    @Mock private SessionPlanService plans;
    @Mock private GameMapRepository maps;
    @Mock private HandoutRepository handouts;
    @Mock private PartyMemberRepository party;
    @Mock private CalendarService calendar;
    @Mock private dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository questRepository;
    @Mock private RollableTableLinkService rollableTableLinkService;

    @InjectMocks private SessionWorkspaceService service;

    private UUID campaignId;
    private Campaign campaign;
    private CampaignSession session;
    private Adventure adventure;
    private Chapter chapter;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);
        session = CampaignSession.idle(campaign);
        adventure = new Adventure();
        adventure.setId(UUID.randomUUID());
        chapter = new Chapter();
        chapter.setAdventure(adventure);
        when(campaigns.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(maps.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());
        when(handouts.findByCampaignIdOrderByTitleAsc(campaignId)).thenReturn(List.of());
        when(party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId)).thenReturn(List.of());
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(plans.latest(campaignId)).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(rollableTableLinkService.forScene(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
    }

    private Scene sceneWithMap(GameMap map) {
        Scene s = new Scene();
        s.setId(UUID.randomUUID());
        s.setChapter(chapter);
        s.setMap(map);
        return s;
    }

    @Test
    void storedSessionMapWinsOnOpenSession() {
        GameMap storedMap = gameMap("stored-map");
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setWorkspaceMap(storedMap);

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, null);

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.STORED_SESSION);
        assertThat(result.workspaceMap()).isEqualTo(storedMap);
    }

    @Test
    void openSessionWithNoWorkspaceMapDoesNotSilentlyAdoptEncounterMap() {
        GameMap encounterMap = gameMap("encounter-map");
        Encounter active = new Encounter();
        active.setMap(encounterMap);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setWorkspaceMap(null);
        when(encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(active));

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, null);

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.STORED_SESSION);
        assertThat(result.workspaceMap()).isNull();
    }

    @Test
    void activeEncounterMapTakesPriorityWhenNoStoredMap() {
        GameMap encounterMap = gameMap("encounter-map");
        Encounter active = new Encounter();
        active.setMap(encounterMap);
        session.setWorkspaceMap(null);
        when(encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(active));

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, null);

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.ACTIVE_ENCOUNTER);
    }

    @Test
    void currentSceneMapTakesPriorityWhenNoActiveEncounter() {
        GameMap sceneMap = gameMap("scene-map");
        Scene current = sceneWithMap(sceneMap);
        when(adventures.getCurrentScene(campaignId)).thenReturn(Optional.of(current));
        when(adventures.flattenedScenes(adventure.getId())).thenReturn(List.of(current));
        when(encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, null);

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.CURRENT_SCENE);
    }

    @Test
    void explicitMapUsedWhenNoOtherPriority() {
        GameMap explicitMap = gameMap("explicit-map");
        when(maps.findById(explicitMap.getId())).thenReturn(Optional.of(explicitMap));
        when(adventures.getCurrentScene(campaignId)).thenReturn(Optional.empty());
        when(encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, explicitMap.getId());

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.EXPLICIT_MAP);
    }

    @Test
    void noneWhenNoMapSourcesExist() {
        when(adventures.getCurrentScene(campaignId)).thenReturn(Optional.empty());
        when(encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, null);

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.NONE);
        assertThat(result.workspaceMap()).isNull();
    }

    @Test
    void activeEncounterWithoutMapFallsThroughToCurrentSceneMap() {
        GameMap sceneMap = gameMap("scene-map");
        Scene current = sceneWithMap(sceneMap);
        Encounter maplessEncounter = new Encounter();
        maplessEncounter.setMap(null);

        when(encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(maplessEncounter));
        when(adventures.getCurrentScene(campaignId)).thenReturn(Optional.of(current));
        when(adventures.flattenedScenes(adventure.getId())).thenReturn(List.of(current));

        SessionWorkspaceService.SessionWorkspace result = service.load(campaignId, null);

        assertThat(result.selectionSource()).isEqualTo(SessionWorkspaceService.SelectionSource.CURRENT_SCENE);
    }

    private GameMap gameMap(String name) {
        GameMap m = new GameMap();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setCampaign(campaign);
        return m;
    }
}
