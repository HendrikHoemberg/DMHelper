package dev.hendrikhoemberg.dmhelper.encounter;

import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EncounterRailOrderingTest {

    private final Comparator<String> naturalOrder = CockpitRuntimeModuleViewService.naturalOrder();

    @Test
    void numericSuffixesSortNaturally() {
        List<String> names = List.of("Burg Cragmaw 12", "Burg Cragmaw 3", "Burg Cragmaw 4", "Burg Cragmaw 6",
                "Burg Cragmaw 13", "Burg Cragmaw 14");
        List<String> sorted = names.stream().sorted(naturalOrder).toList();
        assertThat(sorted).containsExactly(
                "Burg Cragmaw 3", "Burg Cragmaw 4", "Burg Cragmaw 6",
                "Burg Cragmaw 12", "Burg Cragmaw 13", "Burg Cragmaw 14");
    }

    @Test
    void namesWithoutNumbersStayAlphabetical() {
        List<String> names = List.of("Zombie Horde", "Goblin Ambush", "Dragon Lair");
        List<String> sorted = names.stream().sorted(naturalOrder).toList();
        assertThat(sorted).containsExactly("Dragon Lair", "Goblin Ambush", "Zombie Horde");
    }

    @Test
    void embeddedNumbersSortNaturally() {
        List<String> names = List.of("Room 12 Battle", "Room 3 Fight", "Room 12 Ambush");
        List<String> sorted = names.stream().sorted(naturalOrder).toList();
        assertThat(sorted).containsExactly("Room 3 Fight", "Room 12 Ambush", "Room 12 Battle");
    }

    @Test
    void mixedPrefixesAndSuffixes() {
        List<String> names = List.of("Cave 2", "Cave 1", "Cave 10", "Cavern");
        List<String> sorted = names.stream().sorted(naturalOrder).toList();
        assertThat(sorted).containsExactly("Cave 1", "Cave 2", "Cave 10", "Cavern");
    }

    @Test
    void identicalStringsAreEqual() {
        assertThat(naturalOrder.compare("Same", "Same")).isZero();
    }

    @Test
    void emptyStrings() {
        assertThat(naturalOrder.compare("", "a")).isLessThan(0);
        assertThat(naturalOrder.compare("a", "")).isGreaterThan(0);
        assertThat(naturalOrder.compare("", "")).isZero();
    }
}
