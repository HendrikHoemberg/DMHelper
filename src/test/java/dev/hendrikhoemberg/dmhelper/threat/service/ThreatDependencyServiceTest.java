package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPin;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ThreatDependencyService.class, TrapService.class, HazardService.class,
        ThreatValidator.class, ThreatReferenceResolver.class, CustomContentSupport.class,
        LibraryReferenceCleaner.class})
class ThreatDependencyServiceTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @Autowired private ThreatDependencyService dependencyService;
    @Autowired private TrapRepository trapRepository;
    @Autowired private HazardRepository hazardRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneSectionRepository sceneSectionRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CombatantRepository combatantRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private MapThreatPinRepository mapThreatPinRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private Trap trap;
    private Hazard hazard;

    @BeforeEach
    void setUp() {
        mapThreatPinRepository.deleteAll();
        combatantRepository.deleteAll();
        encounterRepository.deleteAll();
        sceneSectionRepository.deleteAll();
        sceneRepository.deleteAll();
        chapterRepository.deleteAll();
        adventureRepository.deleteAll();
        gameMapRepository.deleteAll();
        trapRepository.deleteAll();
        hazardRepository.deleteAll();
        campaignRepository.deleteAll();

        campaign = new Campaign();
        campaign.setName("Dep Campaign");
        campaign = campaignRepository.save(campaign);

        trap = new Trap();
        trap.setSourceKey("dep-trap");
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trap.setName("Dep Trap");
        trap.setDescription("desc");
        trap.setSeverity(ThreatSeverity.SETBACK);
        trap.setResetMode(ThreatResetMode.NONE);
        trap = trapRepository.save(trap);

        hazard = new Hazard();
        hazard.setSourceKey("dep-hazard");
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(campaign);
        hazard.setName("Dep Hazard");
        hazard.setDescription("desc");
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setExposureMode(HazardExposureMode.CONTINUOUS);
        hazard = hazardRepository.save(hazard);
        em.flush();
    }

    @Test
    void reportsSceneCombatantAndMapPinDependencies() {
        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Adv");
        adventure = adventureRepository.save(adventure);

        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Ch");
        chapter = chapterRepository.save(chapter);

        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Crypt");
        scene.setSortOrder(0);
        scene = sceneRepository.save(scene);

        SceneSection section = new SceneSection();
        section.setScene(scene);
        section.setKind(SceneSectionKind.TRAP);
        section.setLabel("Floor spike");
        section.setBody("prose");
        section.setSortOrder(0);
        section.setThreatKind(ThreatKind.TRAP);
        section.setThreatId(trap.getId());
        sceneSectionRepository.save(section);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Ambush");
        encounter = encounterRepository.save(encounter);

        Combatant combatant = new Combatant();
        combatant.setEncounter(encounter);
        combatant.setName("Spike Trap Token");
        combatant.setThreatKind(ThreatKind.TRAP);
        combatant.setThreatId(trap.getId());
        combatantRepository.save(combatant);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Map");
        map.setSortOrder(0);
        map = gameMapRepository.save(map);

        MapThreatPin pin = new MapThreatPin();
        pin.setMap(map);
        pin.setPinKey("pin-1");
        pin.setThreatKind(ThreatKind.TRAP);
        pin.setThreatId(trap.getId());
        pin.setXPx(10);
        pin.setYPx(20);
        pin.setLabel("Spike");
        pin.setSortOrder(0);
        mapThreatPinRepository.save(pin);
        em.flush();

        ThreatDeletionImpact impact = dependencyService.computeDeletionImpact(ThreatKind.TRAP, trap.getId());
        assertThat(impact.hasDependents()).isTrue();
        assertThat(impact.dependencies())
                .extracting(ThreatDependency::kind)
                .containsExactlyInAnyOrder(
                        ThreatDependencyService.DEP_KIND_SCENE,
                        ThreatDependencyService.DEP_KIND_COMBATANT,
                        ThreatDependencyService.DEP_KIND_MAP_PIN);
        assertThat(impact.dependencies())
                .anyMatch(d -> d.label().contains("Crypt") || d.label().contains("Floor spike"));
    }

    @Test
    void emptyImpactForUnusedHazard() {
        ThreatDeletionImpact impact = dependencyService.computeDeletionImpact(
                ThreatKind.HAZARD, hazard.getId());
        assertThat(impact.hasDependents()).isFalse();
        assertThat(impact.threatKind()).isEqualTo(ThreatKind.HAZARD);
        assertThat(impact.threatId()).isEqualTo(hazard.getId());
    }
}
