package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.WikiLinkParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionPlanServiceTest {

    @Mock private NoteRepository noteRepository;
    @Mock private NoteService noteService;
    @Mock private WikiLinkParser parser;
    @Mock private MarkdownUtil markdown;
    @Mock private ContentDestinationRegistry destinations;
    @Mock private SceneRepository sceneRepository;
    @Mock private GameMapRepository gameMapRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private HandoutRepository handoutRepository;

    @InjectMocks private SessionPlanService service;

    private UUID campaignId;
    private Note plan;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        plan = new Note();
        plan.setId(UUID.randomUUID());
        plan.setTitle("Session Plan");
        plan.setType(NoteType.SESSION_PLAN);
    }

    @Test
    void resolvesExplicitPlanLinksInMarkdownOrderAndKeepsBrokenBeats() {
        plan.setBody("Meet [[NPC:Veyra]], then [[SCENE:Crypt Door]], " +
                "show [[HANDOUT:Inscription]], use [[MAP:Lower Crypt]], " +
                "and inspect [[ENCOUNTER:Missing Guards]].");

        when(noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN))
                .thenReturn(List.of(plan));
        when(noteService.renderBody(plan)).thenReturn("rendered body");
        when(parser.extractReferences(plan.getBody())).thenReturn(List.of(
                new WikiLinkParser.WikiLinkTarget("NPC", "Veyra"),
                new WikiLinkParser.WikiLinkTarget("SCENE", "Crypt Door"),
                new WikiLinkParser.WikiLinkTarget("HANDOUT", "Inscription"),
                new WikiLinkParser.WikiLinkTarget("MAP", "Lower Crypt"),
                new WikiLinkParser.WikiLinkTarget("ENCOUNTER", "Missing Guards")
        ));
        when(noteRepository.findByCampaignIdAndTitle(campaignId, "Veyra"))
                .thenReturn(List.of());
        Adventure adventure = new Adventure();
        adventure.setId(UUID.randomUUID());
        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        scene.setTitle("Crypt Door");
        scene.setChapter(chapter);
        when(sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(campaignId, "Crypt Door"))
                .thenReturn(List.of(scene));
        Handout handout = new Handout();
        handout.setId(UUID.randomUUID());
        handout.setTitle("Inscription");
        when(handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId))
                .thenReturn(List.of(handout));
        GameMap map = new GameMap();
        map.setId(UUID.randomUUID());
        map.setName("Lower Crypt");
        when(gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId))
                .thenReturn(List.of(map));
        when(destinations.campaign(ContentDestinationRegistry.CampaignType.SCENE, campaignId, scene.getId(),
                adventure.getId())).thenReturn("/campaigns/" + campaignId + "/adventures/" + adventure.getId() + "/scenes/" + scene.getId());
        when(destinations.campaign(ContentDestinationRegistry.CampaignType.MAP, campaignId, map.getId(), null))
                .thenReturn("/campaigns/" + campaignId + "/maps/" + map.getId() + "/play");
        when(destinations.campaign(ContentDestinationRegistry.CampaignType.HANDOUT, campaignId, handout.getId(), null))
                .thenReturn("/campaigns/" + campaignId + "/handouts#handout-" + handout.getId());
        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of());

        SessionPlanService.SessionPlan result = service.latest(campaignId).orElseThrow();

        assertThat(result.beats()).hasSize(5);
        assertThat(result.beats().get(0).position()).isEqualTo(0);
        assertThat(result.beats().get(0).type()).isEqualTo("NPC");
        assertThat(result.beats().get(0).label()).isEqualTo("Veyra");
        assertThat(result.beats().get(0).resolved()).isFalse();
        assertThat(result.beats().get(1).position()).isEqualTo(1);
        assertThat(result.beats().get(1).type()).isEqualTo("SCENE");
        assertThat(result.beats().get(1).label()).isEqualTo("Crypt Door");
        assertThat(result.beats().get(1).resolved()).isTrue();
        assertThat(result.beats().get(2).position()).isEqualTo(2);
        assertThat(result.beats().get(2).type()).isEqualTo("HANDOUT");
        assertThat(result.beats().get(2).label()).isEqualTo("Inscription");
        assertThat(result.beats().get(2).resolved()).isTrue();
        assertThat(result.beats().get(3).position()).isEqualTo(3);
        assertThat(result.beats().get(3).type()).isEqualTo("MAP");
        assertThat(result.beats().get(3).label()).isEqualTo("Lower Crypt");
        assertThat(result.beats().get(3).resolved()).isTrue();
        assertThat(result.beats().get(4).position()).isEqualTo(4);
        assertThat(result.beats().get(4).type()).isEqualTo("ENCOUNTER");
        assertThat(result.beats().get(4).label()).isEqualTo("Missing Guards");
        assertThat(result.beats().get(4).resolved()).isFalse();
    }

    @Test
    void returnsEmptyWhenNoSessionPlanExists() {
        when(noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN))
                .thenReturn(List.of());
        assertThat(service.latest(campaignId)).isEmpty();
    }
}
