package dev.hendrikhoemberg.dmhelper.encounter.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EncounterPlacementFramingTest {

    @Test
    void firstCellIsNearCentreNotOnEdge() {
        Set<String> occupied = new HashSet<>();
        int[] cell = EncounterPlacementService.findCenteredFreeCell(occupied, 20, 20, 1, 1);
        assertThat(cell).isNotNull();
        assertThat(cell[0]).isBetween(8, 12);
        assertThat(cell[1]).isBetween(8, 12);
    }

    @Test
    void successiveCellsAreAllDifferentAndInside() {
        Set<String> occupied = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            int[] cell = EncounterPlacementService.findCenteredFreeCell(occupied, 10, 10, 1, 1);
            assertThat(cell).isNotNull();
            String key = cell[0] + "," + cell[1];
            assertThat(occupied).doesNotContain(key);
            occupied.add(key);
            assertThat(cell[0]).isBetween(0, 9);
            assertThat(cell[1]).isBetween(0, 9);
        }
    }

    @Test
    void tinyGridStillFindsACell() {
        Set<String> occupied = new HashSet<>();
        int[] cell = EncounterPlacementService.findCenteredFreeCell(occupied, 2, 2, 1, 1);
        assertThat(cell).isNotNull();
        assertThat(cell[0]).isBetween(0, 1);
        assertThat(cell[1]).isBetween(0, 1);
    }

    @Test
    void fullyOccupiedGridReturnsNull() {
        Set<String> occupied = new HashSet<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                occupied.add(c + "," + r);
            }
        }
        int[] cell = EncounterPlacementService.findCenteredFreeCell(occupied, 3, 3, 1, 1);
        assertThat(cell).isNull();
    }

    @Test
    void respectsTokenSize() {
        Set<String> occupied = new HashSet<>();
        int[] cell = EncounterPlacementService.findCenteredFreeCell(occupied, 10, 10, 2, 2);
        assertThat(cell).isNotNull();
        assertThat(cell[0]).isLessThanOrEqualTo(8);
        assertThat(cell[1]).isLessThanOrEqualTo(8);
    }
}
