package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CockpitLayoutValidatorTest {
    private final CockpitLayoutValidator validator =
            new CockpitLayoutValidator(CockpitModuleRegistry.standard());
    private final CockpitLayoutDocument valid =
            new CockpitBuiltInPresetCatalog().require("builtin:exploration").layout();

    @Test
    void rejectsDuplicateUnknownDisallowedAndEmptyPrimaryModules() {
        assertThatThrownBy(() -> validator.validateForSave(withPrimary(List.of())))
                .hasMessageContaining("Primary requires a module");
        assertThatThrownBy(() -> validator.validateForSave(withPrimary(List.of("map", "map"))))
                .hasMessageContaining("appears more than once");
        assertThatThrownBy(() -> validator.validateForSave(withPrimary(List.of("pdf-reader"))))
                .hasMessageContaining("Unknown cockpit module");
        assertThatThrownBy(() -> validator.validateForSave(withRight(List.of("map"))))
                .hasMessageContaining("map is not allowed in RIGHT_SUPPORT");
    }

    @Test
    void rejectsInvalidRatiosCollapseAndActiveTab() {
        assertThatThrownBy(() -> validator.validateForSave(withRatios(0.1, 0.7, 0.2, 0.24)))
                .hasMessageContaining("Primary ratio");
        assertThatThrownBy(() -> validator.validateForSave(withCollapsed(CockpitZone.PRIMARY)))
                .hasMessageContaining("Only Bottom utility may collapse");
        assertThatThrownBy(() -> validator.validateForSave(withActive(CockpitZone.RIGHT_SUPPORT, "audio")))
                .hasMessageContaining("active tab");
    }

    private CockpitLayoutDocument withPrimary(List<String> keys) {
        return withZone(CockpitZone.PRIMARY,
                new CockpitLayoutDocument.ZoneLayout(
                        keys, keys.isEmpty() ? null : keys.getFirst(), false));
    }

    private CockpitLayoutDocument withRight(List<String> keys) {
        return withZone(CockpitZone.RIGHT_SUPPORT,
                new CockpitLayoutDocument.ZoneLayout(
                        keys, keys.isEmpty() ? null : keys.getFirst(), false));
    }

    private CockpitLayoutDocument withCollapsed(CockpitZone zone) {
        CockpitLayoutDocument.ZoneLayout old = valid.zones().get(zone);
        return withZone(zone, new CockpitLayoutDocument.ZoneLayout(
                old.moduleKeys(), old.activeModuleKey(), true));
    }

    private CockpitLayoutDocument withActive(CockpitZone zone, String key) {
        CockpitLayoutDocument.ZoneLayout old = valid.zones().get(zone);
        return withZone(zone, new CockpitLayoutDocument.ZoneLayout(
                old.moduleKeys(), key, old.collapsed()));
    }

    private CockpitLayoutDocument withRatios(double left, double primary, double right, double bottom) {
        return new CockpitLayoutDocument(valid.schemaVersion(), valid.name(), valid.zones(),
                new CockpitLayoutDocument.SplitRatios(left, primary, right, bottom),
                valid.compactModuleKeys());
    }

    private CockpitLayoutDocument withZone(
            CockpitZone zone, CockpitLayoutDocument.ZoneLayout replacement) {
        EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> zones =
                new EnumMap<>(valid.zones());
        zones.put(zone, replacement);
        return new CockpitLayoutDocument(valid.schemaVersion(), valid.name(), zones,
                valid.ratios(), valid.compactModuleKeys());
    }
}
