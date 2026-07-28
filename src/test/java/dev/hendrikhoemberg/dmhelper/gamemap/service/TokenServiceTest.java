package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
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
        var req = new TokenCreateRequest("Goblin", "MONSTER", 100, 200, 1, 1,
                "#e74c3c", false, 7, 7, null, null);
        TokenDto dto = tokenService.create(map.getId(), req);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("Goblin");
        assertThat(dto.kind()).isEqualTo("MONSTER");
        assertThat(dto.positionX()).isEqualTo(100);
        assertThat(dto.positionY()).isEqualTo(200);
        assertThat(dto.maxHp()).isEqualTo(7);
        assertThat(dto.currentHp()).isEqualTo(7);
        assertThat(dto.bloodied()).isFalse();
    }

    @Test
    void shouldDetectBloodied() {
        var req = new TokenCreateRequest("Hurt", "MONSTER", 0, 0, 1, 1,
                "#e74c3c", false, 3, 10, null, null);
        TokenDto dto = tokenService.create(map.getId(), req);
        assertThat(dto.bloodied()).isTrue();
    }

    @Test
    void shouldMoveToken() {
        var req = new TokenCreateRequest("Mover", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null);
        TokenDto created = tokenService.create(map.getId(), req);
        TokenDto moved = tokenService.move(created.id(), new TokenMoveRequest(500, 300));
        assertThat(moved.positionX()).isEqualTo(500);
        assertThat(moved.positionY()).isEqualTo(300);
    }

    @Test
    void shouldUpdateHp() {
        var req = new TokenCreateRequest("Healer", "PC", 0, 0, 1, 1,
                "#4a9eff", false, 10, 12, null, null);
        TokenDto created = tokenService.create(map.getId(), req);
        TokenDto updated = tokenService.updateHp(created.id(), new TokenHpRequest(5));
        assertThat(updated.currentHp()).isEqualTo(5);
        assertThat(updated.bloodied()).isTrue();
    }

    @Test
    void shouldDeleteToken() {
        var req = new TokenCreateRequest("DeleteMe", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null);
        TokenDto created = tokenService.create(map.getId(), req);
        tokenService.delete(created.id());
        assertThatThrownBy(() -> tokenService.findEntityById(created.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldListTokensByMap() {
        tokenService.create(map.getId(), new TokenCreateRequest("A", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null));
        tokenService.create(map.getId(), new TokenCreateRequest("B", "NPC", 100, 100, 1, 1,
                "#fff", false, null, null, null, null));

        List<TokenDto> tokens = tokenService.findByMapId(map.getId());
        assertThat(tokens).hasSize(2);
    }

    @Test
    void shouldDefaultDeadToFalse() {
        var req = new TokenCreateRequest("Alive", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null);
        TokenDto dto = tokenService.create(map.getId(), req);
        assertThat(dto.dead()).isFalse();
    }

    @Test
    void shouldMarkDead() {
        var req = new TokenCreateRequest("MarkMe", "MONSTER", 0, 0, 1, 1,
                "#e74c3c", false, 7, 7, null, null);
        TokenDto created = tokenService.create(map.getId(), req);
        TokenDto dead = tokenService.markDead(created.id(), true);
        assertThat(dead.dead()).isTrue();
    }

    @Test
    void shouldReviveToken() {
        var req = new TokenCreateRequest("ReviveMe", "MONSTER", 0, 0, 1, 1,
                "#e74c3c", false, 7, 7, null, null);
        TokenDto created = tokenService.create(map.getId(), req);
        tokenService.markDead(created.id(), true);
        TokenDto revived = tokenService.markDead(created.id(), false);
        assertThat(revived.dead()).isFalse();
    }

    @Test
    void shouldDuplicateToken() {
        var req = new TokenCreateRequest("CloneMe", "MONSTER", 100, 200, 2, 2,
                "#e74c3c", true, 7, 7, null, null);
        TokenDto original = tokenService.create(map.getId(), req);
        TokenDto copy = tokenService.duplicate(original.id(), 48, 48);

        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("CloneMe");
        assertThat(copy.kind()).isEqualTo("MONSTER");
        assertThat(copy.positionX()).isEqualTo(148);
        assertThat(copy.positionY()).isEqualTo(248);
        assertThat(copy.sizeCols()).isEqualTo(2);
        assertThat(copy.sizeRows()).isEqualTo(2);
        assertThat(copy.maxHp()).isEqualTo(7);
        assertThat(copy.hidden()).isTrue();
    }

    @Test
    void addingPartyTwiceDoesNotDuplicatePartyMemberTokens() {
        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName("Aria");
        member.setActive(true);
        member.setMaxHp(24);
        member.setCurrentHp(17);
        em.persist(member);
        em.flush();

        tokenService.addPartyToMap(map.getId());
        List<TokenDto> secondResult = tokenService.addPartyToMap(map.getId());

        assertThat(secondResult)
                .filteredOn(token -> member.getId().equals(token.partyMemberId()))
                .hasSize(1);
    }
}
