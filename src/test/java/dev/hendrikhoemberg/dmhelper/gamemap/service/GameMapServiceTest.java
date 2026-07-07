package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(GameMapService.class)
class GameMapServiceTest {

    @Autowired private GameMapService service;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreateMapWithDefaultDocument() {
        GameMap map = service.create(campaign.getId(), "Tavern", 30, 20, 48);

        assertThat(map.getId()).isNotNull();
        assertThat(map.getName()).isEqualTo("Tavern");
        assertThat(map.getGridWidth()).isEqualTo(30);
        assertThat(map.getGridHeight()).isEqualTo(20);
        assertThat(map.getCellSizePx()).isEqualTo(48);
        assertThat(map.getSortOrder()).isEqualTo(0);
        assertThat(map.getGridType()).isEqualTo("SQUARE");
        assertThat(map.getDocument()).contains("\"schemaVersion\"");

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.schemaVersion()).isEqualTo(1);
        assertThat(doc.grid().width()).isEqualTo(30);
        assertThat(doc.grid().height()).isEqualTo(20);
        assertThat(doc.grid().gridType()).isEqualTo("square");
        assertThat(doc.layers()).hasSize(3);
        assertThat(doc.layers().get(0).id()).isEqualTo("terrain");
        assertThat(doc.layers().get(1).id()).isEqualTo("objects");
        assertThat(doc.layers().get(2).id()).isEqualTo("annotations");
        assertThat(doc.primitives()).isEmpty();
        assertThat(doc.customTerrain()).isEmpty();
    }

    @Test
    void shouldAssignIncrementingSortOrders() {
        service.create(campaign.getId(), "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaign.getId(), "Map 2", 20, 15, 48);
        assertThat(map2.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldFindByCampaignOrdered() {
        service.create(campaign.getId(), "Temple", 20, 15, 48);
        service.create(campaign.getId(), "Cave", 20, 15, 48);

        var maps = service.findByCampaignId(campaign.getId());
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isLessThan(maps.get(1).getSortOrder());
    }

    @Test
    void shouldUpdateDocumentAndBumpVersion() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        long initialVersion = map.getVersion();
        String newDoc = """
                {"schemaVersion":1,"grid":{"width":20,"height":15,"cellSizePx":48,"gridType":"square"},"layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","visible":true,"locked":false,"cells":[{"col":0,"row":0,"terrain":"wall"}],"shapes":[]}]}""";

        long newVersion = service.updateDocument(map.getId(), newDoc, initialVersion);

        assertThat(newVersion).isEqualTo(initialVersion + 1);
        assertThat(service.findById(map.getId()).getDocument()).isEqualTo(newDoc);
    }

    @Test
    void shouldRejectStaleVersion() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        String doc = """
                {"schemaVersion":1,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), doc, map.getVersion() + 7))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldRejectInvalidDocumentSchema() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        String badDoc = """
                {"schemaVersion":99,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), badDoc, map.getVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void shouldPreservePrimitivesAndCustomTerrain() {
        GameMap map = service.create(campaign.getId(), "Dungeon", 20, 15, 48);
        String doc = """
                {"schemaVersion":1,
                 "grid":{"width":20,"height":15,"cellSizePx":48,"gridType":"square"},
                 "layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","cells":[],"shapes":[]}],
                 "primitives":[{"type":"ROOM","startCol":2,"startRow":2,"endCol":10,"endRow":8},
                               {"type":"DOOR","startCol":10,"startRow":5,"endCol":10,"endRow":5}],
                 "customTerrain":[{"key":"moss","name":"Moss","fill":"#2a6e3a","walkable":true}]}""";

        service.updateDocument(map.getId(), doc, map.getVersion());

        MapDocumentDto parsed = service.getDocument(map.getId());
        assertThat(parsed.primitives()).hasSize(2);
        assertThat(parsed.primitives().get(0).type()).isEqualTo("ROOM");
        assertThat(parsed.customTerrain()).hasSize(1);
        assertThat(parsed.customTerrain().get(0).key()).isEqualTo("moss");
        // omitted visible/locked flags default to visible & unlocked
        assertThat(parsed.layers().get(0).visible()).isTrue();
        assertThat(parsed.layers().get(0).locked()).isFalse();
    }

    @Test
    void shouldDeleteMapAndReorder() {
        service.create(campaign.getId(), "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaign.getId(), "Map 2", 20, 15, 48);
        service.create(campaign.getId(), "Map 3", 20, 15, 48);

        service.delete(map2.getId());

        var maps = service.findByCampaignId(campaign.getId());
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isEqualTo(0);
        assertThat(maps.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldUpdateNameAndDimensions() {
        GameMap map = service.create(campaign.getId(), "Original", 20, 15, 48);
        GameMap updated = service.update(map.getId(), "Renamed", 40, 30, 64);

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getGridWidth()).isEqualTo(40);
        assertThat(updated.getGridHeight()).isEqualTo(30);
        assertThat(updated.getCellSizePx()).isEqualTo(64);
    }

    @Test
    void shouldThrowWhenMapNotFound() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Map not found");
    }
}
