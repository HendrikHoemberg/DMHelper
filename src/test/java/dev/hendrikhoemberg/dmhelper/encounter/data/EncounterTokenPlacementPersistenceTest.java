package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EncounterTokenPlacementPersistenceTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    EncounterTokenPlacementRepository placements;

    @Test
    void oneCombatantHasAtMostOnePlacement() {
        Combatant combatant = TestEncounterGraph.persistCombatant(entityManager);
        GameMap map = combatant.getEncounter().getMap();
        placements.saveAndFlush(TestEncounterGraph.placement(combatant, map, 48, 96));

        assertThatThrownBy(() ->
                placements.saveAndFlush(TestEncounterGraph.placement(combatant, map, 96, 96)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingEncounterCascadesItsPlacements() {
        Combatant combatant = TestEncounterGraph.persistCombatant(entityManager);
        placements.saveAndFlush(
                TestEncounterGraph.placement(combatant, combatant.getEncounter().getMap(), 48, 96));
        UUID encounterId = combatant.getEncounter().getId();

        entityManager.remove(combatant.getEncounter());
        entityManager.flush();

        assertThat(placements.findByEncounterIdOrderByCombatantSortOrderAsc(encounterId)).isEmpty();
    }
}
