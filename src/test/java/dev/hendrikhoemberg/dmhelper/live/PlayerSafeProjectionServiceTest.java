package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(PlayerSafeProjectionService.class)
class PlayerSafeProjectionServiceTest {

    @Autowired private PlayerSafeProjectionService service;
    @Autowired private jakarta.persistence.EntityManager em;

    private GameMap gameMap;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Test");
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
    void shouldStripHiddenTokens() {
        Token visible = new Token();
        visible.setMap(gameMap);
        visible.setName("Goblin");
        visible.setHidden(false);
        em.persist(visible);

        Token hidden = new Token();
        hidden.setMap(gameMap);
        hidden.setName("Assassin");
        hidden.setHidden(true);
        em.persist(hidden);
        em.flush();

        var result = service.projectTokens(gameMap);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Goblin");
    }

    @Test
    void shouldComputeBloodiedFlag() {
        Token token = new Token();
        token.setMap(gameMap);
        token.setName("Orc");
        token.setMaxHp(30);
        token.setCurrentHp(10);
        token.setHidden(false);
        em.persist(token);
        em.flush();

        var result = service.projectTokens(gameMap);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().bloodied()).isTrue();
    }

    @Test
    void shouldStripAnnotationsLayer() {
        gameMap.setDocument("{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[]},{\"id\":\"l2\",\"name\":\"Annotations\",\"type\":\"ANNOTATIONS\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[]}],\"primitives\":[],\"customTerrain\":[]}");
        em.merge(gameMap);
        em.flush();

        var result = service.projectMapDocument(gameMap);
        assertThat(result).isNotNull();
        assertThat(result.layers()).hasSize(1);
        assertThat(result.layers().getFirst().name()).isEqualTo("Terrain");
    }

    @Test
    void shouldReProjectMapDocumentAfterModification() {
        String initialDoc = "{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[{\"col\":1,\"row\":1,\"terrain\":\"floor\"}],\"shapes\":[]}],\"primitives\":[],\"customTerrain\":[]}";
        String modifiedDoc = "{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[{\"col\":5,\"row\":5,\"terrain\":\"wall\"}],\"shapes\":[]}],\"primitives\":[],\"customTerrain\":[]}";

        gameMap.setDocument(initialDoc);
        em.merge(gameMap);
        em.flush();

        MapDocumentDto firstProjection = service.projectMapDocument(gameMap);
        assertThat(firstProjection.layers().getFirst().cells().getFirst().terrain()).isEqualTo("floor");

        gameMap.setDocument(modifiedDoc);
        em.merge(gameMap);
        em.flush();

        MapDocumentDto secondProjection = service.projectMapDocument(gameMap);
        assertThat(secondProjection.layers().getFirst().cells().getFirst().terrain()).isEqualTo("wall");
    }

    @Test
    void shouldStripInvisibleLayerContent() {
        gameMap.setDocument("{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[]},{\"id\":\"l2\",\"name\":\"DM Secrets\",\"type\":\"OBJECTS\",\"visible\":false,\"locked\":true,\"cells\":[{\"col\":5,\"row\":5,\"terrain\":\"wall\"}],\"shapes\":[{\"type\":\"rect\",\"points\":[1,1,3,3],\"fill\":\"#ff0000\",\"stroke\":\"#000\",\"strokeWidth\":1}],\"image\":{\"dataUrl\":\"data:image/png;base64,abc123\",\"x\":0,\"y\":0,\"width\":10,\"height\":10}}],\"primitives\":[],\"customTerrain\":[]}");
        em.merge(gameMap);
        em.flush();

        var result = service.projectMapDocument(gameMap);
        assertThat(result).isNotNull();
        assertThat(result.layers()).hasSize(2);

        var invisibleLayer = result.layers().get(1);
        assertThat(invisibleLayer.name()).isEqualTo("DM Secrets");
        assertThat(invisibleLayer.visible()).isFalse();
        assertThat(invisibleLayer.cells()).isEmpty();
        assertThat(invisibleLayer.shapes()).isEmpty();
        assertThat(invisibleLayer.image()).isNull();
    }
}
