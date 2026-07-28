package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({TokenService.class, GameMapService.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class TokenServiceTest {

    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired private TokenService tokenService;
    @Autowired private GameMapService mapService;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;
    private GameMap map;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);
        em.flush();
        map = mapService.create(campaign.getId(), "Test Map", 30, 20, 48);
    }

    @Test
    void shouldCreateToken() {
        var req = new MapMarkerRequest("Goblin", "MONSTER", 100, 200, 1, 1,
                "#e74c3c", false, null, null);
        MapMarkerDto dto = tokenService.create(map.getId(), req);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("Goblin");
        assertThat(dto.kind()).isEqualTo("MONSTER");
        assertThat(dto.positionX()).isEqualTo(100);
        assertThat(dto.positionY()).isEqualTo(200);
    }

    @Test
    void shouldMoveToken() {
        var req = new MapMarkerRequest("Mover", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null);
        MapMarkerDto created = tokenService.create(map.getId(), req);
        MapMarkerDto moved = tokenService.move(created.id(), new TokenMoveRequest(500, 300));
        assertThat(moved.positionX()).isEqualTo(500);
        assertThat(moved.positionY()).isEqualTo(300);
    }

    @Test
    void shouldDeleteToken() {
        var req = new MapMarkerRequest("DeleteMe", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null);
        MapMarkerDto created = tokenService.create(map.getId(), req);
        tokenService.delete(created.id());
        assertThatThrownBy(() -> tokenService.findEntityById(created.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldListTokensByMap() {
        tokenService.create(map.getId(), new MapMarkerRequest("A", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null));
        tokenService.create(map.getId(), new MapMarkerRequest("B", "NPC", 100, 100, 1, 1,
                "#fff", false, null, null));

        List<MapMarkerDto> tokens = tokenService.findByMapId(map.getId());
        assertThat(tokens).hasSize(2);
    }

    @Test
    void shouldDuplicateToken() {
        var req = new MapMarkerRequest("CloneMe", "MONSTER", 100, 200, 2, 2,
                "#e74c3c", true, null, null);
        MapMarkerDto original = tokenService.create(map.getId(), req);
        MapMarkerDto copy = tokenService.duplicate(original.id(), 48, 48);

        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("CloneMe");
        assertThat(copy.kind()).isEqualTo("MONSTER");
        assertThat(copy.positionX()).isEqualTo(148);
        assertThat(copy.positionY()).isEqualTo(248);
        assertThat(copy.sizeCols()).isEqualTo(2);
        assertThat(copy.hidden()).isTrue();
    }
}
