package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.List;
import java.util.Map;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitBuiltInPresetCatalog {
    private static final CockpitLayoutDocument.SplitRatios DEFAULT_RATIOS =
            new CockpitLayoutDocument.SplitRatios(0.20, 0.56, 0.24, 0.24);

    private static final List<BuiltInPreset> PRESETS = List.of(
            preset("builtin:exploration", "Exploration",
                    zone("story"), zone("session-plan"), zone("party", "quick-notes"),
                    collapsedZone("audio", "session-log"),
                    Set.of("session-plan", "party", "quick-notes", "audio", "session-log")),
            preset("builtin:combat", "Combat",
                    zone("map"), zone("story", "party"), zone("encounter"),
                    zone("quick-notes", "audio"),
                    Set.of("story", "party", "encounter", "quick-notes", "audio")),
            preset("builtin:theatre-of-mind", "Theatre of Mind",
                    zone("encounter"), zone("story"), zone("party", "quick-notes"),
                    collapsedZone("audio", "session-log"),
                    Set.of("story", "party", "quick-notes", "audio", "session-log")),
            preset("builtin:presentation", "Presentation",
                    zone("map"), zone("story"), zone("presentation"), collapsedZone(),
                    Set.of("story")),
            preset("builtin:session-review", "Session Review",
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
                DEFAULT_RATIOS,
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
