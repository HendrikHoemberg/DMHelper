package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Component
public final class CockpitBuiltInPresetCatalog {
    private static final List<BuiltInPreset> PRESETS = List.of(
            new BuiltInPreset("builtin:exploration", "Exploration", Map.of(
                    CockpitZone.PRIMARY, List.of("story"),
                    CockpitZone.LEFT_SUPPORT, List.of("session-plan"),
                    CockpitZone.RIGHT_SUPPORT, List.of("party"),
                    CockpitZone.BOTTOM_UTILITY, List.of("quick-notes", "audio", "reference"))),
            new BuiltInPreset("builtin:combat", "Combat", Map.of(
                    CockpitZone.PRIMARY, List.of("map"),
                    CockpitZone.LEFT_SUPPORT, List.of("story", "party"),
                    CockpitZone.RIGHT_SUPPORT, List.of("encounter"),
                    CockpitZone.BOTTOM_UTILITY, List.of("quick-notes", "reference", "audio", "session-log"))),
            new BuiltInPreset("builtin:theatre-of-mind", "Theatre of Mind", Map.of(
                    CockpitZone.PRIMARY, List.of("encounter"),
                    CockpitZone.LEFT_SUPPORT, List.of("party", "reference"),
                    CockpitZone.RIGHT_SUPPORT, List.of("story"),
                    CockpitZone.BOTTOM_UTILITY, List.of("quick-notes", "audio", "session-log"))),
            new BuiltInPreset("builtin:session-review", "Session Review", Map.of(
                    CockpitZone.PRIMARY, List.of("session-log"),
                    CockpitZone.LEFT_SUPPORT, List.of("session-plan"),
                    CockpitZone.RIGHT_SUPPORT, List.of("quick-notes", "party"),
                    CockpitZone.BOTTOM_UTILITY, List.of()))
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

    public record BuiltInPreset(
            String key,
            String name,
            Map<CockpitZone, List<String>> moduleKeysByZone) {}
}
