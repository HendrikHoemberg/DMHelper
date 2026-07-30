package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacementRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(EncounterPlacementService.class)
class EncounterPlacementServiceTest {

    @Autowired private EncounterPlacementService service;
    @Autowired private CombatantRepository combatants;
    @Autowired private EncounterTokenPlacementRepository placements;
    @Autowired private GameMapRepository maps;
    @Autowired private PartyMemberRepository partyMembers;
    @Autowired private EntityManager em;

    private PlacementsFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new PlacementsFixture(em, combatants, maps, partyMembers);
    }

    @Test
    void upsertRejectsCombatantFromAnotherEncounter() {
        Encounter first = fixture.encounterWithMap("First");
        Encounter second = fixture.encounterWithMap("Second");
        Combatant foreignCombatant = fixture.combatant(second, "Goblin");

        assertThatThrownBy(() -> service.upsert(
                first.getId(), foreignCombatant.getId(),
                new EncounterPlacementService.PlacementUpsertRequest(48, 48, 1, 1, "#55aa55", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Combatant does not belong to encounter");
    }

    @Test
    void upsertClampsFootprintInsideMapBounds() {
        Encounter encounter = fixture.encounterWithMap("Room", 10, 8, 48);
        Combatant ogre = fixture.combatant(encounter, "Ogre");

        EncounterPlacementService.PlacementDto result = service.upsert(
                encounter.getId(), ogre.getId(),
                new EncounterPlacementService.PlacementUpsertRequest(9 * 48, 7 * 48, 2, 2, "#55aa55", null));

        assertThat(result.positionX()).isEqualTo(8 * 48);
        assertThat(result.positionY()).isEqualTo(6 * 48);
    }

    @Test
    void movingPlacementDoesNotChangeCombatState() {
        Encounter encounter = fixture.encounterWithMap("Room");
        Combatant goblin = fixture.combatant(encounter, "Goblin");
        goblin.setCurrentHp(3);
        goblin.setDefeated(true);
        combatants.save(goblin);
        service.upsert(encounter.getId(), goblin.getId(),
                new EncounterPlacementService.PlacementUpsertRequest(0, 0, 1, 1, "#55aa55", null));

        service.move(encounter.getId(), goblin.getId(), 96, 144);

        Combatant unchanged = combatants.findById(goblin.getId()).orElseThrow();
        assertThat(unchanged.getCurrentHp()).isEqualTo(3);
        assertThat(unchanged.isDefeated()).isTrue();
    }

    @Test
    void movingPlacementRejectsEncounterThatDoesNotOwnIt() {
        Encounter owner = fixture.encounterWithMap("Owner");
        Encounter other = fixture.encounterWithMap("Other");
        Combatant goblin = fixture.combatant(owner, "Goblin");
        service.upsert(owner.getId(), goblin.getId(),
                new EncounterPlacementService.PlacementUpsertRequest(
                        0, 0, 1, 1, "#55aa55", null));

        assertThatThrownBy(() -> service.move(other.getId(), goblin.getId(), 96, 144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Placement does not belong to encounter");

        var unchanged = placements.findByCombatantId(goblin.getId()).orElseThrow();
        assertThat(unchanged.getPositionX()).isZero();
        assertThat(unchanged.getPositionY()).isZero();
    }

    @Test
    void changingMapRejectsMapFromAnotherCampaign() {
        Encounter encounter = fixture.encounterWithMap("Owner");
        Encounter other = fixture.encounterWithMap("Other");

        assertThatThrownBy(() -> service.changeMapAndResetPlacements(
                encounter.getId(), other.getMap().getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Map does not belong to encounter campaign");

        assertThat(encounter.getMap().getName()).isEqualTo("Map for Owner");
    }

    @Test
    void autoPlaceUsesExplicitStartingCoordinates() {
        Encounter encounter = fixture.encounterWithMap("Room", 10, 8, 48);
        Combatant goblin = fixture.combatant(encounter, "Goblin");
        goblin.setStartX(96);
        goblin.setStartY(144);
        combatants.save(goblin);

        List<EncounterPlacementService.PlacementDto> result =
                service.autoPlaceUnplaced(encounter.getId());

        assertThat(result).singleElement().satisfies(placement -> {
            assertThat(placement.positionX()).isEqualTo(96);
            assertThat(placement.positionY()).isEqualTo(144);
        });
    }

    @Test
    void autoPlaceUsesNamedRegionWhenCoordinatesAreUnset() {
        Encounter encounter = fixture.encounterWithMap("Room", 10, 8, 48);
        encounter.getMap().setDocument("""
                {"schemaVersion":2,
                 "grid":{"width":10,"height":8,"cellSizePx":48,"gridType":"square"},
                 "layers":[],
                 "primitives":[{"type":"REGION","startCol":4,"startRow":2,
                   "endCol":8,"endRow":6,"key":"reinforcements"}]}
                """);
        maps.save(encounter.getMap());
        Combatant goblin = fixture.combatant(encounter, "Goblin");
        goblin.setPlacementRegionKey("reinforcements");
        combatants.save(goblin);

        List<EncounterPlacementService.PlacementDto> result =
                service.autoPlaceUnplaced(encounter.getId());

        assertThat(result).singleElement().satisfies(placement -> {
            assertThat(placement.positionX()).isEqualTo(6 * 48);
            assertThat(placement.positionY()).isEqualTo(4 * 48);
        });
    }

    @Test
    void combatantCannotBePlacedOnDifferentMap() {
        Encounter encounter = fixture.encounterWithMap("Room", 10, 8, 48);
        Combatant goblin = fixture.combatant(encounter, "Goblin");
        encounter.setMap(null);
        em.merge(encounter);
        em.flush();

        assertThatThrownBy(() -> service.upsert(
                encounter.getId(), goblin.getId(),
                new EncounterPlacementService.PlacementUpsertRequest(0, 0, 1, 1, "#55aa55", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Encounter has no map assigned");
    }

    @Test
    void removePlacementLeavesCombatantInRoster() {
        Encounter encounter = fixture.encounterWithMap("Room");
        Combatant goblin = fixture.combatant(encounter, "Goblin");
        service.upsert(encounter.getId(), goblin.getId(),
                new EncounterPlacementService.PlacementUpsertRequest(0, 0, 1, 1, "#55aa55", null));

        service.remove(encounter.getId(), goblin.getId());

        assertThat(placements.findByCombatantId(goblin.getId())).isEmpty();
        assertThat(combatants.findById(goblin.getId())).isPresent();
    }

    @Test
    void placeMissingPartyTwiceYieldsOneCombatantPerActivePartyMember() {
        Encounter encounter = fixture.encounterWithMap("Room");

        Campaign campaign = em.find(Campaign.class, encounter.getCampaign().getId());
        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Test Hero");
        pm.setClassAndLevel("Fighter 1");
        pm.setMaxHp(10);
        pm.setCurrentHp(10);
        pm.setActive(true);
        em.persist(pm);
        em.flush();

        Combatant hero = fixture.combatant(encounter, "Test Hero");
        hero.setPartyMember(pm);
        combatants.save(hero);
        em.flush();

        List<EncounterPlacementService.PlacementDto> first = service.placeUnplacedPartyCombatants(encounter.getId());
        List<EncounterPlacementService.PlacementDto> second = service.placeUnplacedPartyCombatants(encounter.getId());

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @Test
    void encounterWithoutMapReportsMissingMapIssue() {
        Encounter encounter = fixture.encounterWithMap("Room");
        encounter.setMap(null);
        em.merge(encounter);
        em.flush();

        EncounterPlacementService.EncounterReadinessDto readiness = service.readiness(encounter.getId());

        assertThat(readiness.canRun()).isFalse();
        assertThat(readiness.issues()).anyMatch(i ->
                i.code().equals("MISSING_MAP") && i.severity().equals("ERROR"));
    }

    @Test
    void rosterWithUnplacedCombatantsReportsIssue() {
        Encounter encounter = fixture.encounterWithMap("Room");
        fixture.combatant(encounter, "Unplaced Goblin");
        fixture.combatant(encounter, "Another Unplaced");

        EncounterPlacementService.EncounterReadinessDto readiness = service.readiness(encounter.getId());

        assertThat(readiness.issues()).anyMatch(i ->
                i.code().equals("UNPLACED_COMBATANTS") && i.severity().equals("WARNING"));
        assertThat(readiness.unplacedCombatantCount()).isEqualTo(2);
        assertThat(readiness.placedCombatantCount()).isEqualTo(0);
    }

    @Test
    void unplacedCombatantsAreNotesRatherThanBlockers() {
        assertThat(EncounterPlacementService.verdictFor(true, false, 4, 4))
                .isEqualTo(ReadinessVerdict.RUNNABLE_WITH_NOTES);
        assertThat(EncounterPlacementService.verdictFor(true, false, 4, 0))
                .isEqualTo(ReadinessVerdict.RUNNABLE);
        assertThat(EncounterPlacementService.verdictFor(false, false, 4, 0))
                .isEqualTo(ReadinessVerdict.BLOCKED);
        assertThat(EncounterPlacementService.verdictFor(true, true, 4, 0))
                .isEqualTo(ReadinessVerdict.BLOCKED);
    }

    static class PlacementsFixture {
        private final EntityManager em;
        private final CombatantRepository combatants;
        private final GameMapRepository maps;
        private final PartyMemberRepository partyMembers;

        PlacementsFixture(EntityManager em, CombatantRepository combatants,
                          GameMapRepository maps, PartyMemberRepository partyMembers) {
            this.em = em;
            this.combatants = combatants;
            this.maps = maps;
            this.partyMembers = partyMembers;
        }

        Encounter encounterWithMap(String name) {
            return encounterWithMap(name, 30, 20, 48);
        }

        Encounter encounterWithMap(String name, int gridWidth, int gridHeight, int cellSize) {
            Campaign campaign = new Campaign();
            campaign.setName("Campaign for " + name);
            em.persist(campaign);

            GameMap map = new GameMap();
            map.setCampaign(campaign);
            map.setName("Map for " + name);
            map.setGridWidth(gridWidth);
            map.setGridHeight(gridHeight);
            map.setCellSizePx(cellSize);
            map.setSortOrder(0);
            em.persist(map);

            Encounter encounter = new Encounter();
            encounter.setCampaign(campaign);
            encounter.setMap(map);
            encounter.setName(name);
            em.persist(encounter);

            em.flush();
            return encounter;
        }

        Combatant combatant(Encounter encounter, String name) {
            Combatant c = new Combatant();
            c.setEncounter(encounter);
            c.setName(name);
            c.setKind("NPC");
            c.setMaxHp(10);
            c.setCurrentHp(10);
            c.setSortOrder((int) combatants.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).size());
            combatants.save(c);
            return c;
        }

        Combatant placedCombatant(Encounter encounter, int x, int y) {
            Combatant c = combatant(encounter, "Placed-" + UUID.randomUUID().toString().substring(0, 6));
            return c;
        }

        Encounter activeEncounterWithMap() {
            Encounter e = encounterWithMap("Active Encounter");
            e.setStatus(Encounter.Status.ACTIVE);
            em.merge(e);
            em.flush();
            return e;
        }

        Encounter plannedEncounterWithMap(String name) {
            return encounterWithMap(name);
        }
    }
}
