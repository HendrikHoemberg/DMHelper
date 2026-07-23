package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CockpitBuiltInPresetCatalogTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
    private final CockpitLayoutValidator validator = new CockpitLayoutValidator(registry);
    private final CockpitBuiltInPresetCatalog catalog = new CockpitBuiltInPresetCatalog();

    @Test
    void exposesTheFiveApprovedPresetsInOrder() {
        assertThat(catalog.all()).extracting(CockpitBuiltInPresetCatalog.BuiltInPreset::key)
                .containsExactly("builtin:exploration", "builtin:combat",
                        "builtin:theatre-of-mind", "builtin:presentation",
                        "builtin:session-review");
    }

    @Test
    void everyBuiltInPassesTheSameValidatorAsCustomPresets() {
        assertThatCode(() -> catalog.all().forEach(p -> validator.validateForSave(p.layout())))
                .doesNotThrowAnyException();
    }

    @Test
    void combatPresetHasTheApprovedModulePlacement() {
        CockpitLayoutDocument layout = catalog.require("builtin:combat").layout();
        assertThat(layout.zones().get(CockpitZone.PRIMARY).moduleKeys()).containsExactly("map");
        assertThat(layout.zones().get(CockpitZone.LEFT_SUPPORT).moduleKeys())
                .containsExactly("story", "party");
        assertThat(layout.zones().get(CockpitZone.RIGHT_SUPPORT).moduleKeys())
                .containsExactly("encounter");
        assertThat(layout.zones().get(CockpitZone.BOTTOM_UTILITY).moduleKeys())
                .containsExactly("quick-notes", "audio");
    }
}
