package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.List;
import java.util.Map;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitBuiltInPresetCatalog {
    // The bottom strip is a utility rail for quick notes and audio, not a panel. At a
    // bottom ratio of 0.24 it took 227px of a 1000px viewport to hold one input line.
    // Combat is the only preset with an uncollapsed bottom zone, so this is the only
    // place it shows. 0.16 is the validator's supported minimum; the splitter still
    // lets a DM grow it for the session log.
    private static final CockpitLayoutDocument.SplitRatios DEFAULT_RATIOS =
            new CockpitLayoutDocument.SplitRatios(0.20, 0.56, 0.24, 0.16);

    private static final List<BuiltInPreset> PRESETS = List.of(
            preset("builtin:exploration", "Exploration",
                    zone("story"), zone("session-plan"), zone("party", "quick-notes"),
                    collapsedZone("audio", "session-log"),
                    Set.of("session-plan", "party", "quick-notes", "audio", "session-log")),
            preset("builtin:combat", "Combat",
                    zone("map"), zone("story", "party", "reference"), zone("encounter"),
                    zone("quick-notes", "audio"),
                    Set.of("story", "party", "reference", "encounter", "quick-notes", "audio")),
            preset("builtin:theatre-of-mind", "Theatre of Mind",
                    zone("encounter"), zone("story"), zone("party", "quick-notes"),
                    collapsedZone("audio", "session-log"),
                    Set.of("story", "party", "quick-notes", "audio", "session-log")),
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
