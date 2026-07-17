package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PlayerSafeProjectionService.class)
class PlayerSafeMapProjectionTest {

    @Autowired
    private PlayerSafeProjectionService projection;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private GameMap gameMap;
    private final JsonMapper mapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Projection Test");
        em.persist(campaign);

        gameMap = new GameMap();
        gameMap.setCampaign(campaign);
        gameMap.setName("Test Map");
        gameMap.setGridWidth(20);
        gameMap.setGridHeight(15);
        gameMap.setCellSizePx(48);
        em.persist(gameMap);
        em.flush();
    }

    @Test
    void playerProjectionStripsDmOnlyLayersAndPrimitives() throws Exception {
        var doc = new MapDocumentDto(
                2,
                new MapDocumentDto.GridDto(20, 15, 48, "square", "GRID", true),
                List.of(
                        new MapLayerDto("terrain", "Terrain", MapLayerDto.LayerType.TERRAIN,
                                true, false, List.of(), List.of(), null, true),
                        new MapLayerDto("annotations", "DM Notes", MapLayerDto.LayerType.ANNOTATIONS,
                                true, false, List.of(), List.of(), null, null),
                        new MapLayerDto("dm-layer", "DM Secrets", MapLayerDto.LayerType.OBJECTS,
                                true, false, List.of(), List.of(), null, false)
                ),
                List.of(
                        new MapDocumentDto.PrimitiveDto("ROOM", 2, 2, 10, 8, null, null, null, null),
                        new MapDocumentDto.PrimitiveDto("REGION", 0, 0, 5, 5, "dungeon", "secret", "Secret", false)
                ),
                List.of()
        );

        gameMap.setDocument(mapper.writeValueAsString(doc));
        em.merge(gameMap);
        em.flush();

        var result = projection.projectMapDocument(gameMap);
        assertThat(result).isNotNull();

        var layerNames = result.layers().stream().map(MapLayerDto::name).toList();
        assertThat(layerNames).containsExactly("Terrain");

        var primitiveTypes = result.primitives().stream().map(MapDocumentDto.PrimitiveDto::type).toList();
        assertThat(primitiveTypes).containsExactly("ROOM");
    }

    @Test
    void playerProjectionPreservesVisiblePrimitives() throws Exception {
        var doc = new MapDocumentDto(
                2,
                new MapDocumentDto.GridDto(20, 15, 48, "square", "GRID", true),
                List.of(
                        new MapLayerDto("terrain", "Terrain", MapLayerDto.LayerType.TERRAIN,
                                true, false, List.of(), List.of(), null, true)
                ),
                List.of(
                        new MapDocumentDto.PrimitiveDto("ROOM", 2, 2, 10, 8, null, null, null, null),
                        new MapDocumentDto.PrimitiveDto("REGION", 0, 0, 5, 5, "dungeon", "entrance", "Entrance", true)
                ),
                List.of()
        );

        gameMap.setDocument(mapper.writeValueAsString(doc));
        em.merge(gameMap);
        em.flush();

        var result = projection.projectMapDocument(gameMap);
        assertThat(result).isNotNull();
        assertThat(result.primitives()).hasSize(2);
    }

    @Test
    void playerProjectionHandlesNullPrimitivesField() throws Exception {
        var doc = new MapDocumentDto(
                2,
                new MapDocumentDto.GridDto(20, 15, 48, "square", "GRID", true),
                List.of(
                        new MapLayerDto("terrain", "Terrain", MapLayerDto.LayerType.TERRAIN,
                                true, false, List.of(), List.of(), null, true)
                ),
                List.of(),
                List.of()
        );

        gameMap.setDocument(mapper.writeValueAsString(doc));
        em.merge(gameMap);
        em.flush();

        var result = projection.projectMapDocument(gameMap);
        assertThat(result).isNotNull();
        assertThat(result.layers()).hasSize(1);
    }
}
