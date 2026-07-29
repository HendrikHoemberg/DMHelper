package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitModuleRegistry {
    private final Map<String, CockpitModuleDefinition> modules;

    public CockpitModuleRegistry() {
        this(standardDefinitions());
    }

    CockpitModuleRegistry(List<CockpitModuleDefinition> definitions) {
        LinkedHashMap<String, CockpitModuleDefinition> indexed = new LinkedHashMap<>();
        for (CockpitModuleDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate cockpit module: " + definition.key());
            }
        }
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    public static CockpitModuleRegistry standard() {
        return new CockpitModuleRegistry(standardDefinitions());
    }

    private static List<CockpitModuleDefinition> standardDefinitions() {
        return List.of(
                endpoint("story", "Story", 240, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT), true, true,
                        "Select a scene to begin."),
                endpoint("map", "Map", 420, 300,
                        Set.of(CockpitZone.PRIMARY), false, true,
                        "Choose or create a workspace map."),
                endpoint("encounter", "Encounter", 280, 260,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.RIGHT_SUPPORT), true, true,
                        "Link or create an encounter for this scene."),
                endpoint("session-plan", "Session plan", 220, 180,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT), true, true,
                        "Create a session plan when the session starts."),
                endpoint("party", "Party", 220, 140,
                        Set.of(CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY),
                        true, true, "Add party members to this campaign."),
                endpoint("quick-notes", "Quick notes", 220, 140,
                        Set.of(CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY), true, true,
                        "Capture a note from the command action."),
                endpoint("reference", "Reference", 260, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT),
                        true, true, "Search rules and compendium content."),
                endpoint("audio", "Audio", 220, 112,
                        Set.of(CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY),
                        true, true, "No audio cue is selected."),
                endpoint("session-log", "Session log", 280, 180,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.BOTTOM_UTILITY), true, true,
                        "Session events will appear after play begins.")
        );
    }

    public List<CockpitModuleDefinition> all() {
        return List.copyOf(modules.values());
    }

    public CockpitModuleDefinition require(String key) {
        CockpitModuleDefinition definition = modules.get(key);
        if (definition == null) throw new IllegalArgumentException("Unknown cockpit module: " + key);
        return definition;
    }

    public boolean contains(String key) {
        return modules.containsKey(key);
    }

    private static CockpitModuleDefinition endpoint(
            String key, String title, int minWidth, int minHeight,
            Set<CockpitZone> zones, boolean compact, boolean focus,
            String empty) {
        return new CockpitModuleDefinition(key, title,
                new CockpitModuleSource(CockpitModuleSource.Kind.ENDPOINT,
                        "/campaigns/{campaignId}/session/modules/" + key),
                minWidth, minHeight, zones, compact, focus,
                new CockpitModuleStateContract(empty, "Loading " + title + "…",
                        title + " could not refresh. Existing content was kept.", true));
    }
}
