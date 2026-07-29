package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CockpitModuleRegistryTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();

    @Test
    void registersTheStableCatalogExactlyOnce() {
        assertThat(registry.all()).extracting(CockpitModuleDefinition::key)
                .containsExactly("story", "map", "encounter", "session-plan", "party",
                        "quick-notes", "presentation", "reference", "audio", "session-log");
        assertThat(registry.all()).extracting(CockpitModuleDefinition::key)
                .doesNotHaveDuplicates();
    }

    @Test
    void mapIsPrimaryOnlyAndEncounterIsDmSensitive() {
        assertThat(registry.require("map").allowedZones())
                .isEqualTo(Set.of(CockpitZone.PRIMARY));
        assertThat(registry.require("encounter").screenSafetyBehavior())
                .isEqualTo(CockpitScreenSafetyBehavior.HIDE);
    }

    @Test
    void everyModuleHasUsableStateCopyAndDimensions() {
        assertThat(registry.all()).allSatisfy(module -> {
            assertThat(module.minWidthPx()).isGreaterThanOrEqualTo(220);
            assertThat(module.minHeightPx()).isGreaterThanOrEqualTo(112);
            assertThat(module.states().emptyMessage()).isNotBlank();
            assertThat(module.states().loadingMessage()).isNotBlank();
            assertThat(module.states().errorMessage()).isNotBlank();
        });
    }

    @Test
    void unknownModuleCannotBeRequired() {
        assertThatThrownBy(() -> registry.require("floating-pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown cockpit module: floating-pdf");
    }

    @Test
    void allModulesUseEndpointSources() {
        assertThat(registry.all())
                .allSatisfy(module -> {
                    assertThat(module.source().kind())
                            .isEqualTo(CockpitModuleSource.Kind.ENDPOINT);
                    assertThat(module.source().value())
                            .isEqualTo("/campaigns/{campaignId}/session/modules/" + module.key());
                });
    }

    @Test
    void duplicateRegistryKeysFailAtStartup() {
        CockpitModuleDefinition story = registry.require("story");
        assertThatThrownBy(() -> new CockpitModuleRegistry(List.of(story, story)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate cockpit module: story");
    }

    @Test
    void registryExposesOnlyDmFacingModules() {
        var keys = registry.all().stream().map(CockpitModuleDefinition::key).toList();

        assertThat(keys).containsExactlyInAnyOrder(
                "story", "map", "encounter", "party",
                "quick-notes", "session-plan", "session-log", "audio", "reference");
        assertThat(keys).doesNotContain("presentation");
    }
}
