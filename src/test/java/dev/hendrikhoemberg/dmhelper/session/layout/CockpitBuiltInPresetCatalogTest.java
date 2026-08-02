package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CockpitBuiltInPresetCatalogTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
    private final CockpitLayoutValidator validator = new CockpitLayoutValidator(registry);
    private final CockpitBuiltInPresetCatalog catalog = new CockpitBuiltInPresetCatalog();

    private static java.util.List<String> zone(CockpitBuiltInPresetCatalog.BuiltInPreset preset,
                                               CockpitZone zone) {
        return preset.layout().zones().get(zone).moduleKeys();
    }

    @Test
    void exposesTheFiveApprovedPresetsInOrder() {
        assertThat(catalog.all()).extracting(CockpitBuiltInPresetCatalog.BuiltInPreset::key)
                .containsExactly("builtin:exploration", "builtin:combat",
                        "builtin:theatre-of-mind",
                        "builtin:session-review");
    }

    @Test
    void everyBuiltInPassesTheSameValidatorAsCustomPresets() {
        assertThatCode(() -> catalog.all().forEach(p -> validator.validateForSave(p.layout())))
                .doesNotThrowAnyException();
    }

    @Test
    void explorationLeadsWithTheStoryWorkflow() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:exploration");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("story");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("session-plan");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("party");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "audio", "reference");
        assertThat(preset.layout().zones().get(CockpitZone.BOTTOM_UTILITY).collapsed())
                .as("quick notes are the default bottom module, not a collapsed drawer")
                .isFalse();
    }

    @Test
    void combatLeadsWithTheMapAndKeepsTheEncounterProminent() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:combat");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("map");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("encounter");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("story", "party");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "reference", "audio", "session-log");
        assertThat(preset.layout().compactModuleKeys()).contains("story", "party");
        assertThat(preset.layout().compactModuleKeys())
                .as("the encounter is prominent support, never compact").doesNotContain("encounter");
    }

    @Test
    void theatreOfMindLeadsWithTheEncounterAndExpandsTheStory() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:theatre-of-mind");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("encounter");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("story");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("party", "reference");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "audio", "session-log");
        assertThat(preset.layout().compactModuleKeys()).doesNotContain("story");
    }

    @Test
    void sessionReviewLeadsWithTheLogDraft() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:session-review");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("session-log");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("session-plan");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("quick-notes", "party");
    }

    @Test
    void everyPresetSatisfiesTheLayoutValidator() {
        var validator = new CockpitLayoutValidator(CockpitModuleRegistry.standard());
        for (var preset : new CockpitBuiltInPresetCatalog().all()) {
            assertThatCode(() -> validator.validateForSave(preset.layout()))
                    .as("validation problems for %s", preset.key())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void noPresetLeavesTheBottomZoneEmptyAndUncollapsed() {
        for (var preset : new CockpitBuiltInPresetCatalog().all()) {
            var bottom = preset.layout().zones().get(CockpitZone.BOTTOM_UTILITY);
            assertThat(bottom.moduleKeys().isEmpty() && !bottom.collapsed())
                    .as("%s wastes bottom space", preset.key()).isFalse();
        }
    }
}
