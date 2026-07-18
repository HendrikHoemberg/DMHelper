package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPin;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({MapThreatPinService.class, ThreatReferenceResolver.class})
class MapThreatPinServiceTest {

    @MockitoBean private CampaignPackageKeyService packageKeyService;
    @MockitoBean private ConditionRepository conditionRepository;
    @MockitoBean private EquipmentItemRepository equipmentItemRepository;
    @MockitoBean private MagicItemRepository magicItemRepository;
    @MockitoBean private StatBlockRepository statBlockRepository;

    @Autowired private MapThreatPinService service;
    @Autowired private MapThreatPinRepository pinRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private TrapRepository trapRepository;
    @Autowired private HazardRepository hazardRepository;
    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;

    private Campaign campaign;
    private Campaign otherCampaign;
    private GameMap map;
    private GameMap otherMap;
    private Trap trap;
    private Trap foreignTrap;
    private Hazard hazard;

    @BeforeEach
    void setUp() {
        pinRepository.deleteAll();
        sceneRepository.deleteAll();
        chapterRepository.deleteAll();
        adventureRepository.deleteAll();
        trapRepository.deleteAll();
        hazardRepository.deleteAll();
        gameMapRepository.deleteAll();
        campaignRepository.deleteAll();

        campaign = new Campaign();
        campaign.setName("Pin Campaign");
        campaign = campaignRepository.save(campaign);

        otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        otherCampaign = campaignRepository.save(otherCampaign);

        map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Dungeon");
        map.setGridWidth(10);
        map.setGridHeight(8);
        map.setCellSizePx(48);
        map = gameMapRepository.save(map);

        otherMap = new GameMap();
        otherMap.setCampaign(otherCampaign);
        otherMap.setName("Other Map");
        otherMap.setGridWidth(10);
        otherMap.setGridHeight(8);
        otherMap.setCellSizePx(48);
        otherMap = gameMapRepository.save(otherMap);

        trap = new Trap();
        trap.setSourceKey("needle-trap");
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trap.setName("Poison Needle");
        trap.setDescription("A needle trap.");
        trap.setSeverity(ThreatSeverity.SETBACK);
        trap.setResetMode(ThreatResetMode.NONE);
        trap = trapRepository.save(trap);

        foreignTrap = new Trap();
        foreignTrap.setSourceKey("foreign-trap");
        foreignTrap.setSource(ContentSource.CUSTOM);
        foreignTrap.setCampaign(otherCampaign);
        foreignTrap.setName("Foreign Trap");
        foreignTrap.setDescription("Not visible here.");
        foreignTrap.setSeverity(ThreatSeverity.DANGEROUS);
        foreignTrap.setResetMode(ThreatResetMode.NONE);
        foreignTrap = trapRepository.save(foreignTrap);

        hazard = new Hazard();
        hazard.setSourceKey("acid-pool");
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(campaign);
        hazard.setName("Acid Pool");
        hazard.setDescription("Acid.");
        hazard.setSeverity(ThreatSeverity.DANGEROUS);
        hazard.setExposureMode(HazardExposureMode.ON_ENTER);
        hazard = hazardRepository.save(hazard);
    }

    @Test
    void createsPinWithStableKeyAndCoordinates() {
        MapThreatPinWrite write = new MapThreatPinWrite(
                "needle-at-door", ThreatKind.TRAP, trap.getId(),
                100, 200, "Door needle", 1);

        MapPinDto dto = service.create(map.getId(), write);

        assertThat(dto.pinKind()).isEqualTo(MapPinDto.KIND_THREAT);
        assertThat(dto.key()).isEqualTo("needle-at-door");
        assertThat(dto.x()).isEqualTo(100);
        assertThat(dto.y()).isEqualTo(200);
        assertThat(dto.title()).isEqualTo("Door needle");
        assertThat(dto.threatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(dto.threatId()).isEqualTo(trap.getId());
        assertThat(dto.sceneId()).isNull();
        assertThat(dto.sceneKey()).isNull();
        assertThat(pinRepository.findByMapIdAndPinKey(map.getId(), "needle-at-door")).isPresent();
    }

    @Test
    void rejectsInvalidKey() {
        MapThreatPinWrite write = new MapThreatPinWrite(
                "Bad Key!", ThreatKind.TRAP, trap.getId(), 10, 10, null, 0);

        assertThatThrownBy(() -> service.create(map.getId(), write))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }

    @Test
    void rejectsOutOfBoundsCoordinates() {
        // max exclusive: width*cell=480, height*cell=384
        assertThatThrownBy(() -> service.create(map.getId(),
                new MapThreatPinWrite("oob-x", ThreatKind.TRAP, trap.getId(), 480, 0, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounds");

        assertThatThrownBy(() -> service.create(map.getId(),
                new MapThreatPinWrite("oob-y", ThreatKind.TRAP, trap.getId(), 0, 384, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounds");

        assertThatThrownBy(() -> service.create(map.getId(),
                new MapThreatPinWrite("neg", ThreatKind.TRAP, trap.getId(), -1, 0, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounds");
    }

    @Test
    void acceptsEdgeCoordinatesInsideBounds() {
        MapPinDto dto = service.create(map.getId(),
                new MapThreatPinWrite("edge", ThreatKind.TRAP, trap.getId(), 479, 383, null, 0));
        assertThat(dto.x()).isEqualTo(479);
        assertThat(dto.y()).isEqualTo(383);
    }

    @Test
    void rejectsInvisibleThreatFromOtherCampaign() {
        assertThatThrownBy(() -> service.create(map.getId(),
                new MapThreatPinWrite("foreign", ThreatKind.TRAP, foreignTrap.getId(), 10, 10, null, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsMissingThreat() {
        assertThatThrownBy(() -> service.create(map.getId(),
                new MapThreatPinWrite("missing", ThreatKind.TRAP, UUID.randomUUID(), 10, 10, null, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsMissingMap() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(),
                new MapThreatPinWrite("orphan", ThreatKind.TRAP, trap.getId(), 10, 10, null, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void defaultsTitleToThreatNameWhenLabelBlank() {
        MapPinDto dto = service.create(map.getId(),
                new MapThreatPinWrite("auto-label", ThreatKind.TRAP, trap.getId(), 10, 10, "  ", 0));
        assertThat(dto.title()).isEqualTo("Poison Needle");
    }

    @Test
    void listsThreatPinsInSortOrder() {
        service.create(map.getId(),
                new MapThreatPinWrite("b-pin", ThreatKind.TRAP, trap.getId(), 10, 10, "B", 2));
        service.create(map.getId(),
                new MapThreatPinWrite("a-pin", ThreatKind.HAZARD, hazard.getId(), 20, 20, "A", 1));

        List<MapPinDto> pins = service.listThreatPins(map.getId());
        assertThat(pins).extracting(MapPinDto::key).containsExactly("a-pin", "b-pin");
    }

    @Test
    void listCombinedIncludesSceneAndThreatPins() {
        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Adv");
        adventure = adventureRepository.save(adventure);

        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Ch1");
        chapter.setSortOrder(0);
        chapter = chapterRepository.save(chapter);

        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Throne Room");
        scene.setSceneKey("14");
        scene.setMap(map);
        scene.setPinX(576);
        scene.setPinY(240);
        Scene savedScene = sceneRepository.save(scene);
        UUID sceneId = savedScene.getId();

        service.create(map.getId(),
                new MapThreatPinWrite("trap-pin", ThreatKind.TRAP, trap.getId(), 50, 50, "Needle", 0));

        List<MapPinDto> pins = service.listCombinedPins(map.getId());
        assertThat(pins).hasSize(2);
        assertThat(pins.stream().anyMatch(p -> MapPinDto.KIND_SCENE.equals(p.pinKind())
                && p.sceneId().equals(sceneId)
                && "14".equals(p.sceneKey())
                && p.x() == 576 && p.y() == 240
                && "Throne Room".equals(p.title()))).isTrue();
        assertThat(pins.stream().anyMatch(p -> MapPinDto.KIND_THREAT.equals(p.pinKind())
                && "trap-pin".equals(p.key())
                && p.threatId().equals(trap.getId()))).isTrue();
    }

    @Test
    void updateChangesCoordinatesAndLabel() {
        MapPinDto created = service.create(map.getId(),
                new MapThreatPinWrite("upd", ThreatKind.TRAP, trap.getId(), 10, 10, "Old", 0));

        MapPinDto updated = service.update(map.getId(), created.id(),
                new MapThreatPinWrite("upd", ThreatKind.TRAP, trap.getId(), 30, 40, "New", 5));

        assertThat(updated.x()).isEqualTo(30);
        assertThat(updated.y()).isEqualTo(40);
        assertThat(updated.title()).isEqualTo("New");
        assertThat(updated.key()).isEqualTo("upd");
    }

    @Test
    void updateRejectsPinFromOtherMap() {
        MapPinDto created = service.create(map.getId(),
                new MapThreatPinWrite("own", ThreatKind.TRAP, trap.getId(), 10, 10, null, 0));

        assertThatThrownBy(() -> service.update(otherMap.getId(), created.id(),
                new MapThreatPinWrite("own", ThreatKind.TRAP, trap.getId(), 20, 20, null, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesPinWithRouteMapOwnership() {
        MapPinDto created = service.create(map.getId(),
                new MapThreatPinWrite("del", ThreatKind.TRAP, trap.getId(), 10, 10, null, 0));

        service.delete(map.getId(), created.id());

        assertThat(pinRepository.findById(created.id())).isEmpty();
    }

    @Test
    void deleteRejectsPinFromOtherMap() {
        MapPinDto created = service.create(map.getId(),
                new MapThreatPinWrite("del-own", ThreatKind.TRAP, trap.getId(), 10, 10, null, 0));

        assertThatThrownBy(() -> service.delete(otherMap.getId(), created.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(pinRepository.findById(created.id())).isPresent();
    }

    @Test
    void rejectsDuplicateKeyOnSameMap() {
        service.create(map.getId(),
                new MapThreatPinWrite("dup", ThreatKind.TRAP, trap.getId(), 10, 10, null, 0));

        assertThatThrownBy(() -> service.create(map.getId(),
                new MapThreatPinWrite("dup", ThreatKind.HAZARD, hazard.getId(), 20, 20, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }
}
