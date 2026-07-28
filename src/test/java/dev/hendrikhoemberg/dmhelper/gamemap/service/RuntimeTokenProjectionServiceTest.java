package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacement;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacementRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService.RuntimeTokenDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService.RuntimeTokenSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(RuntimeTokenProjectionService.class)
class RuntimeTokenProjectionServiceTest {

    @Autowired private RuntimeTokenProjectionService service;
    @Autowired private TokenRepository tokens;
    @Autowired private EncounterTokenPlacementRepository placements;
    @Autowired private CombatantRepository combatants;
    @Autowired private GameMapRepository maps;
    @Autowired private EntityManager em;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new Fixture();
    }

    @Test
    void markerProjectsCorrectly() {
        GameMap map = fixture.map();
        Token marker = marker(map, "Treasure Chest", "OBJECT", 48, 96, 1, 1, "#ffd700", "chest", false);
        em.flush();

        List<RuntimeTokenDto> result = service.project(map.getId(), null);

        assertThat(result).hasSize(1);
        RuntimeTokenDto dto = result.get(0);
        assertThat(dto.source()).isEqualTo(RuntimeTokenSource.MARKER);
        assertThat(dto.combatantId()).isNull();
        assertThat(dto.name()).isEqualTo("Treasure Chest");
        assertThat(dto.kind()).isEqualTo("OBJECT");
        assertThat(dto.positionX()).isEqualTo(48);
        assertThat(dto.positionY()).isEqualTo(96);
        assertThat(dto.sizeCols()).isEqualTo(1);
        assertThat(dto.sizeRows()).isEqualTo(1);
        assertThat(dto.color()).isEqualTo("#ffd700");
        assertThat(dto.icon()).isEqualTo("chest");
        assertThat(dto.hidden()).isFalse();
        assertThat(dto.currentHp()).isNull();
        assertThat(dto.maxHp()).isNull();
        assertThat(dto.bloodied()).isFalse();
        assertThat(dto.defeated()).isFalse();
        assertThat(dto.conditions()).isEmpty();
    }

    @Test
    void placementUsesCombatantHpDefeatAndVisibility() {
        GameMap map = fixture.map();
        Encounter encounter = fixture.activeEncounter(map);
        Combatant combatant = fixture.combatant(encounter, "Goblin");
        placed(encounter, map, combatant, 96, 144);

        combatant.setCurrentHp(3);
        combatant.setMaxHp(10);
        combatant.setDefeated(true);
        combatant.setHidden(true);
        combatants.save(combatant);
        em.flush();

        List<RuntimeTokenDto> result = service.project(map.getId(), encounter.getId());

        RuntimeTokenDto token = result.stream()
                .filter(item -> combatant.getId().equals(item.combatantId()))
                .findFirst().orElseThrow();

        assertThat(token.source()).isEqualTo(RuntimeTokenSource.COMBATANT);
        assertThat(token.currentHp()).isEqualTo(3);
        assertThat(token.maxHp()).isEqualTo(10);
        assertThat(token.bloodied()).isTrue();
        assertThat(token.defeated()).isTrue();
        assertThat(token.hidden()).isTrue();
    }

    @Test
    void inactiveEncounterPlacementsAreNotIncludedByDefault() {
        GameMap map = fixture.map();
        Encounter planned = fixture.encounter(map, Encounter.Status.PLANNED);
        fixture.combatant(planned, "Goblin");
        placed(planned, map, fixture.lastCombatant, 96, 144);
        em.flush();

        assertThat(service.project(map.getId(), null))
                .noneMatch(token -> token.source() == RuntimeTokenSource.COMBATANT);
    }

    @Test
    void explicitEncounterOnWrongMapIsRejected() {
        GameMap map1 = fixture.map();
        GameMap map2 = fixture.map();
        Encounter encounter = fixture.activeEncounter(map1);
        Combatant combatant = fixture.combatant(encounter, "Goblin");
        placed(encounter, map1, combatant, 96, 144);
        em.flush();

        assertThatThrownBy(() -> service.project(map2.getId(), encounter.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Encounter does not belong to map");
    }

    @Test
    void defaultUsesActiveEncounterOnSameMap() {
        GameMap map = fixture.map();
        Encounter active = fixture.activeEncounter(map);
        Combatant combatant = fixture.combatant(active, "Goblin");
        placed(active, map, combatant, 0, 0);
        em.flush();

        List<RuntimeTokenDto> result = service.project(map.getId(), null);
        assertThat(result).anyMatch(token -> combatant.getId().equals(token.combatantId()));
    }

    @Test
    void defaultReturnsOnlyMarkersWhenNoActiveEncounter() {
        GameMap map = fixture.map();
        marker(map, "Rock", "OBJECT", 0, 0, 1, 1, "#888", null, false);

        Encounter planned = fixture.encounter(map, Encounter.Status.PLANNED);
        Combatant combatant = fixture.combatant(planned, "Hidden");
        placed(planned, map, combatant, 0, 0);
        em.flush();

        List<RuntimeTokenDto> result = service.project(map.getId(), null);
        assertThat(result).allMatch(token -> token.source() == RuntimeTokenSource.MARKER);
    }

    @Test
    void explicitEncounterIsAcceptedWhenBelongsToRequestedMap() {
        GameMap map = fixture.map();
        Encounter active = fixture.activeEncounter(map);
        Combatant combatant = fixture.combatant(active, "Goblin");
        placed(active, map, combatant, 100, 200);
        em.flush();

        List<RuntimeTokenDto> result = service.project(map.getId(), active.getId());

        assertThat(result).anyMatch(token -> combatant.getId().equals(token.combatantId()));
    }

    @Test
    void projectReturnsBothMarkersAndCombatants() {
        GameMap map = fixture.map();
        marker(map, "Barrel", "OBJECT", 10, 10, 1, 1, "#8B4513", null, false);
        Encounter active = fixture.activeEncounter(map);
        Combatant goblin = fixture.combatant(active, "Goblin");
        placed(active, map, goblin, 50, 50);
        em.flush();

        List<RuntimeTokenDto> result = service.project(map.getId(), null);

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(t -> t.source() == RuntimeTokenSource.MARKER).hasSize(1);
        assertThat(result).filteredOn(t -> t.source() == RuntimeTokenSource.COMBATANT).hasSize(1);
    }

    @Test
    void conditionsAreParsedFromJson() {
        GameMap map = fixture.map();
        Encounter encounter = fixture.activeEncounter(map);
        Combatant combatant = fixture.combatant(encounter, "Poisoned Goblin");
        combatant.setConditionsJson("""
                [
                  {"sourceKey":"poisoned","name":"Poisoned","durationRounds":2},
                  {"sourceKey":"prone","name":"Prone","durationRounds":0}
                ]
                """);
        combatants.save(combatant);
        placed(encounter, map, combatant, 0, 0);
        em.flush();

        RuntimeTokenDto token = service.project(map.getId(), encounter.getId()).stream()
                .filter(t -> combatant.getId().equals(t.combatantId()))
                .findFirst().orElseThrow();

        assertThat(token.conditions()).containsExactly("poisoned", "prone");
    }

    @Test
    void hiddenMarkerIsProjectedWithHiddenFlag() {
        GameMap map = fixture.map();
        marker(map, "Secret", "OBJECT", 0, 0, 1, 1, "#000", null, true);
        em.flush();

        RuntimeTokenDto dto = service.project(map.getId(), null).get(0);
        assertThat(dto.hidden()).isTrue();
    }

    private Token marker(GameMap map, String name, String kind, int x, int y,
                          int cols, int rows, String color, String icon, boolean hidden) {
        Token t = new Token();
        t.setMap(map);
        t.setName(name);
        t.setKind(kind);
        t.setPositionX(x);
        t.setPositionY(y);
        t.setSizeCols(cols);
        t.setSizeRows(rows);
        t.setColor(color);
        t.setIcon(icon);
        t.setHidden(hidden);
        return tokens.save(t);
    }

    private void placed(Encounter encounter, GameMap map, Combatant combatant, int x, int y) {
        EncounterTokenPlacement p = new EncounterTokenPlacement();
        p.setEncounter(encounter);
        p.setCombatant(combatant);
        p.setMap(map);
        p.setPositionX(x);
        p.setPositionY(y);
        p.setSizeCols(1);
        p.setSizeRows(1);
        p.setColor("#7b68ee");
        em.persist(p);
    }

    class Fixture {
        private int counter;
        Combatant lastCombatant;

        Campaign campaign() {
            Campaign c = new Campaign();
            c.setName("Campaign-" + counter++);
            em.persist(c);
            return c;
        }

        GameMap map() {
            Campaign c = campaign();
            GameMap m = new GameMap();
            m.setCampaign(c);
            m.setName("Map-" + counter++);
            m.setGridWidth(30);
            m.setGridHeight(20);
            m.setCellSizePx(48);
            m.setSortOrder(0);
            em.persist(m);
            return m;
        }

        Encounter encounter(GameMap map, Encounter.Status status) {
            Encounter e = new Encounter();
            e.setCampaign(map.getCampaign());
            e.setMap(map);
            e.setName("Encounter-" + counter++);
            e.setStatus(status);
            em.persist(e);
            return e;
        }

        Encounter activeEncounter(GameMap map) {
            return encounter(map, Encounter.Status.ACTIVE);
        }

        Combatant combatant(Encounter encounter, String name) {
            Combatant c = new Combatant();
            c.setEncounter(encounter);
            c.setName(name);
            c.setKind("NPC");
            c.setMaxHp(10);
            c.setCurrentHp(10);
            c.setSortOrder((int) combatants.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).size());
            lastCombatant = combatants.save(c);
            return lastCombatant;
        }
    }
}
