package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitBuiltInPresetCatalogTest {
    private final CockpitBuiltInPresetCatalog catalog = new CockpitBuiltInPresetCatalog();
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();

    @Test
    void everyPresetAssignsOnlyKnownModulesAndNeverRepeatsOne() {
        for (var preset : catalog.all()) {
            List<String> assigned = preset.moduleKeysByZone().values().stream()
                    .flatMap(List::stream).toList();

            assertThat(assigned).as("%s repeats a module", preset.key())
                    .doesNotHaveDuplicates();
            assertThat(assigned).as("%s assigns an unknown module", preset.key())
                    .allMatch(registry::contains);
        }
    }

    @Test
    void explorationExistsAndIsTheDefault() {
        assertThat(catalog.all()).extracting(CockpitBuiltInPresetCatalog.BuiltInPreset::key)
                .contains("builtin:exploration");
    }
}
