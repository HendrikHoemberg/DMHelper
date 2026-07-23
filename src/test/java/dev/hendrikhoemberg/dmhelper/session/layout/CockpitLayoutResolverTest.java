package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitLayoutResolverTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
    private final CockpitBuiltInPresetCatalog builtIns = new CockpitBuiltInPresetCatalog();
    private final CockpitLayoutResolver resolver = new CockpitLayoutResolver(registry, builtIns);

    @Test
    void omitsMissingKeysRepairsActiveTabsAndClampsRatios() {
        CockpitLayoutDocument damaged = damagedExploration(
                List.of("party", "removed-module"), "removed-module",
                new CockpitLayoutDocument.SplitRatios(.05, .80, .15, .90));

        CockpitLayoutResolver.Resolution result = resolver.resolve(damaged);

        assertThat(result.document().zones().get(CockpitZone.RIGHT_SUPPORT).moduleKeys())
                .containsExactly("party");
        assertThat(result.document().zones().get(CockpitZone.RIGHT_SUPPORT).activeModuleKey())
                .isEqualTo("party");
        assertThat(result.document().ratios().primary()).isBetween(.50, .65);
        assertThat(result.document().ratios().bottom()).isEqualTo(.40);
        assertThat(result.warnings()).anyMatch(w -> w.contains("removed-module"));
        assertThat(result.fallbackKey()).isNull();
    }

    @Test
    void unreadableOrFutureDocumentsFallBackWithoutThrowing() {
        CockpitLayoutDocument future = new CockpitLayoutDocument(
                99, "Future", Map.of(), new CockpitLayoutDocument.SplitRatios(0, 0, 0, 0), Set.of());
        CockpitLayoutResolver.Resolution result = resolver.resolve(future);
        assertThat(result.fallbackKey()).isEqualTo("builtin:exploration");
        assertThat(result.warnings()).contains("This layout uses an unsupported schema. Exploration was restored.");
    }

    @Test
    void invalidPlacementChoosesTheNearestBuiltIn() {
        CockpitLayoutDocument combatLike = invalidCombatWithEmptyPrimary();
        assertThat(resolver.resolve(combatLike).fallbackKey()).isEqualTo("builtin:combat");
    }

    private CockpitLayoutDocument damagedExploration(
            List<String> rightKeys,
            String rightActive,
            CockpitLayoutDocument.SplitRatios ratios) {
        CockpitLayoutDocument source = builtIns.require("builtin:exploration").layout();
        EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> zones =
                new EnumMap<>(source.zones());
        zones.put(CockpitZone.RIGHT_SUPPORT,
                new CockpitLayoutDocument.ZoneLayout(rightKeys, rightActive, false));
        return new CockpitLayoutDocument(1, source.name(), zones, ratios,
                source.compactModuleKeys());
    }

    private CockpitLayoutDocument invalidCombatWithEmptyPrimary() {
        CockpitLayoutDocument source = builtIns.require("builtin:combat").layout();
        EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> zones =
                new EnumMap<>(source.zones());
        zones.put(CockpitZone.PRIMARY,
                new CockpitLayoutDocument.ZoneLayout(List.of(), null, false));
        return new CockpitLayoutDocument(1, source.name(), zones, source.ratios(),
                source.compactModuleKeys());
    }
}
