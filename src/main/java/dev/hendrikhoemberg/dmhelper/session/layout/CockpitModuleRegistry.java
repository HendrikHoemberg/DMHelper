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
                module("story", "Story", "session/_story-rail :: story", 240, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.FILTER, "Select a scene to begin."),
                module("map", "Map", "session/cockpit :: map-module", 420, 300,
                        Set.of(CockpitZone.PRIMARY), false, true,
                        CockpitScreenSafetyBehavior.FILTER, "Choose or create a workspace map."),
                module("encounter", "Encounter", "session/_encounter-rail :: encounters", 280, 260,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.RIGHT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Link or create an encounter for this scene."),
                module("session-plan", "Session plan", "session/_session-plan :: plan", 220, 180,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Create a session plan when the session starts."),
                module("party", "Party", "party/_summary-bar :: summary-bar", 220, 140,
                        Set.of(CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY),
                        true, true, CockpitScreenSafetyBehavior.FILTER, "Add party members to this campaign."),
                deferred("quick-notes", "Quick notes", 220, 140,
                        Set.of(CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Capture a note from the command action."),
                deferred("presentation", "Presentation", 280, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.RIGHT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.FILTER, "Nothing is currently presented."),
                deferred("reference", "Reference", 260, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT),
                        true, true, CockpitScreenSafetyBehavior.HIDE, "Search rules and compendium content."),
                module("audio", "Audio", "audio/_cockpit-widget :: cockpit-widget", 220, 112,
                        Set.of(CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY),
                        true, true, CockpitScreenSafetyBehavior.FILTER, "No audio cue is selected."),
                deferred("session-log", "Session log", 280, 180,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.BOTTOM_UTILITY), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Session events will appear after play begins.")
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

    private static CockpitModuleDefinition module(
            String key, String title, String fragment, int minWidth, int minHeight,
            Set<CockpitZone> zones, boolean compact, boolean focus,
            CockpitScreenSafetyBehavior safety, String empty) {
        return new CockpitModuleDefinition(key, title,
                new CockpitModuleSource(CockpitModuleSource.Kind.THYMELEAF_FRAGMENT, fragment),
                minWidth, minHeight, zones, compact, focus, safety,
                new CockpitModuleStateContract(empty, "Loading " + title + "…",
                        title + " could not refresh. Existing content was kept.", true));
    }

    private static CockpitModuleDefinition deferred(
            String key, String title, int minWidth, int minHeight, Set<CockpitZone> zones,
            boolean compact, boolean focus, CockpitScreenSafetyBehavior safety, String empty) {
        return module(key, title, "session/_cockpit-deferred-modules :: " + key,
                minWidth, minHeight, zones, compact, focus, safety, empty);
    }
}
