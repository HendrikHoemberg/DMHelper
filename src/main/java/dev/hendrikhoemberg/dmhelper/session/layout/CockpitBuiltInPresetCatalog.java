package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.List;
import java.util.Map;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitBuiltInPresetCatalog {
    private static final List<BuiltInPreset> PRESETS = List.of(
            preset("builtin:exploration", "Exploration",
                    new CockpitLayoutDocument.SplitRatios(0.22, 0.54, 0.24, 0.16),
                    zone("story"), zone("session-plan"), zone("party"),
                    zone("quick-notes", "audio", "reference"),
                    Set.of("session-plan", "party", "audio", "reference")),
            preset("builtin:combat", "Combat",
                    new CockpitLayoutDocument.SplitRatios(0.18, 0.52, 0.30, 0.20),
                    zone("map"), zone("story", "party"), zone("encounter"),
                    zone("quick-notes", "reference", "audio", "session-log"),
                    Set.of("story", "party", "quick-notes", "reference", "audio", "session-log")),
            preset("builtin:theatre-of-mind", "Theatre of Mind",
                    new CockpitLayoutDocument.SplitRatios(0.19, 0.50, 0.31, 0.16),
                    zone("encounter"), zone("party", "reference"), zone("story"),
                    zone("quick-notes", "audio", "session-log"),
                    Set.of("party", "reference", "quick-notes", "audio", "session-log")),
            preset("builtin:session-review", "Session Review",
                    new CockpitLayoutDocument.SplitRatios(0.22, 0.56, 0.22, 0.16),
                    zone("session-log"), zone("session-plan"), zone("quick-notes", "party"),
                    collapsedZone(),
                    Set.of("session-plan", "quick-notes", "party"))
    );

    public List<BuiltInPreset> all() {
        return PRESETS;
    }

    public BuiltInPreset require(String key) {
        return PRESETS.stream()
                .filter(preset -> preset.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown built-in cockpit preset: " + key));
    }

    private static BuiltInPreset preset(
            String key,
            String name,
            CockpitLayoutDocument.SplitRatios ratios,
            CockpitLayoutDocument.ZoneLayout primary,
            CockpitLayoutDocument.ZoneLayout left,
            CockpitLayoutDocument.ZoneLayout right,
            CockpitLayoutDocument.ZoneLayout bottom,
            Set<String> compactModuleKeys) {
        CockpitLayoutDocument layout = new CockpitLayoutDocument(
                CockpitLayoutDocument.CURRENT_SCHEMA_VERSION,
                name,
                Map.of(
                        CockpitZone.PRIMARY, primary,
                        CockpitZone.LEFT_SUPPORT, left,
                        CockpitZone.RIGHT_SUPPORT, right,
                        CockpitZone.BOTTOM_UTILITY, bottom),
                ratios,
                compactModuleKeys);
        return new BuiltInPreset(key, name, layout);
    }

    private static CockpitLayoutDocument.ZoneLayout zone(String... keys) {
        List<String> moduleKeys = List.of(keys);
        return new CockpitLayoutDocument.ZoneLayout(
                moduleKeys, moduleKeys.isEmpty() ? null : moduleKeys.getFirst(), false);
    }

    private static CockpitLayoutDocument.ZoneLayout collapsedZone(String... keys) {
        List<String> moduleKeys = List.of(keys);
        return new CockpitLayoutDocument.ZoneLayout(
                moduleKeys, moduleKeys.isEmpty() ? null : moduleKeys.getFirst(), true);
    }

    public record BuiltInPreset(String key, String name, CockpitLayoutDocument layout) {
    }
}
