package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({GameMapService.class,
        dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class MapDocumentContractTest {

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired
    private GameMapService mapService;

    @Autowired
    private CampaignRepository campaignRepo;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Contract Test");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void schemaVersionIsTwoForNewDocuments() {
        var map = mapService.create(campaign.getId(), "Test", 20, 15, 48);
        MapDocumentDto doc = mapService.getDocument(map.getId());
        assertThat(doc.schemaVersion()).isEqualTo(2);
    }

    @Test
    void saveDocumentPersistsRegionKeyAndCalibration() throws Exception {
        var map = mapService.create(campaign.getId(), "Region Test", 30, 20, 48);

        var mapper = new JsonMapper();
        MapDocumentDto doc = mapService.getDocument(map.getId());

        var regionPrimitive = new MapDocumentDto.PrimitiveDto(
                "REGION", 5, 5, 15, 12, "dungeon", "throne-room", "Throne Room", true);
        var image = new MapLayerDto.ImageDto("data:image/png;base64,AAAA", 0, 0, 30, 20,
                0.0, false, new MapDocumentDto.CalibrationDto(0, 0, 10, 10, 5, 2, 3));
        var layerWithImage = new MapLayerDto("bg", "Background", MapLayerDto.LayerType.IMAGE,
                true, false, List.of(), List.of(), image, true);

        MapDocumentDto updated = new MapDocumentDto(
                2,
                doc.grid(),
                List.of(layerWithImage),
                List.of(regionPrimitive),
                doc.customTerrain()
        );

        String json = mapper.writeValueAsString(updated);
        mapService.updateDocument(map.getId(), json, map.getVersion());

        MapDocumentDto reloaded = mapService.getDocument(map.getId());
        assertThat(reloaded.schemaVersion()).isEqualTo(2);

        var prim = reloaded.primitives().get(0);
        assertThat(prim.type()).isEqualTo("REGION");
        assertThat(prim.key()).isEqualTo("throne-room");
        assertThat(prim.label()).isEqualTo("Throne Room");
        assertThat(prim.playerVisible()).isTrue();

        var img = reloaded.layers().get(0).image();
        assertThat(img).isNotNull();
        assertThat(img.calibration()).isNotNull();
        assertThat(img.calibration().cellsBetween()).isEqualTo(5);
    }

    @Test
    void imageDtoGeometryRotationLockAndCalibrationSurviveRoundTrip() throws Exception {
        var map = mapService.create(campaign.getId(), "Image Test", 30, 20, 48);
        var mapper = new JsonMapper();

        var calibration = new MapDocumentDto.CalibrationDto(1.5, 2.0, 11.5, 12.0, 5, 0, 0);
        var image = new MapLayerDto.ImageDto("data:image/png;base64,AAAA", 2.5, 3.0, 20.0, 15.0,
                45.0, true, calibration);
        var layer = new MapLayerDto("bg", "Background", MapLayerDto.LayerType.IMAGE,
                true, true, List.of(), List.of(), image, true);

        var doc = mapService.getDocument(map.getId());
        var updated = new MapDocumentDto(2, doc.grid(), List.of(layer), List.of(), doc.customTerrain());

        String json = mapper.writeValueAsString(updated);
        mapService.updateDocument(map.getId(), json, map.getVersion());

        var reloaded = mapService.getDocument(map.getId());
        var img = reloaded.layers().get(0).image();
        assertThat(img).isNotNull();
        assertThat(img.x()).isEqualTo(2.5);
        assertThat(img.y()).isEqualTo(3.0);
        assertThat(img.width()).isEqualTo(20.0);
        assertThat(img.height()).isEqualTo(15.0);
        assertThat(img.rotationDeg()).isEqualTo(45.0);
        assertThat(img.locked()).isTrue();
        assertThat(img.calibration()).isNotNull();
        assertThat(img.calibration().ax()).isEqualTo(1.5);
        assertThat(img.calibration().ay()).isEqualTo(2.0);
        assertThat(img.calibration().bx()).isEqualTo(11.5);
        assertThat(img.calibration().by()).isEqualTo(12.0);
        assertThat(img.calibration().cellsBetween()).isEqualTo(5);
        assertThat(img.calibration().offsetXPx()).isEqualTo(0.0);
        assertThat(img.calibration().offsetYPx()).isEqualTo(0.0);
        assertThat(reloaded.grid().cellSizePx()).isEqualTo(48);
    }

    @Test
    void packageCompatibleImageCalibrationFields() throws Exception {
        var map = mapService.create(campaign.getId(), "Package Cal", 30, 20, 48);
        var mapper = new JsonMapper();

        // Calibration values that a package export would produce
        var calibration = new MapDocumentDto.CalibrationDto(0.5, 0.5, 9.5, 0.5, 10, 12, 8);
        var image = new MapLayerDto.ImageDto("data:image/png;base64,AAAA", 3.0, 2.0, 25.0, 18.0,
                90.0, true, calibration);
        var layer = new MapLayerDto("bg", "Background", MapLayerDto.LayerType.IMAGE,
                true, true, List.of(), List.of(), image, null);

        var doc = mapService.getDocument(map.getId());
        var updated = new MapDocumentDto(2, doc.grid(), List.of(layer), List.of(), doc.customTerrain());

        String json = mapper.writeValueAsString(updated);
        mapService.updateDocument(map.getId(), json, map.getVersion());

        var reloaded = mapService.getDocument(map.getId());
        var img = reloaded.layers().get(0).image();
        assertThat(img).isNotNull();
        assertThat(img.x()).isEqualTo(3.0);
        assertThat(img.y()).isEqualTo(2.0);
        assertThat(img.width()).isEqualTo(25.0);
        assertThat(img.height()).isEqualTo(18.0);
        assertThat(img.rotationDeg()).isEqualTo(90.0);
        assertThat(img.locked()).isTrue();
        assertThat(img.calibration()).isNotNull();
        assertThat(img.calibration().ax()).isEqualTo(0.5);
        assertThat(img.calibration().ay()).isEqualTo(0.5);
        assertThat(img.calibration().bx()).isEqualTo(9.5);
        assertThat(img.calibration().by()).isEqualTo(0.5);
        assertThat(img.calibration().cellsBetween()).isEqualTo(10);
        assertThat(img.calibration().offsetXPx()).isEqualTo(12);
        assertThat(img.calibration().offsetYPx()).isEqualTo(8);
    }
}
