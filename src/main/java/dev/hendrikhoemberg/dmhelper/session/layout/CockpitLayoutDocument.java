package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CockpitLayoutDocument(
        int schemaVersion,
        String name,
        Map<CockpitZone, ZoneLayout> zones,
        SplitRatios ratios,
        Set<String> compactModuleKeys) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CockpitLayoutDocument {
        zones = zones == null ? Map.of() : Map.copyOf(zones);
        compactModuleKeys = compactModuleKeys == null ? Set.of() : Set.copyOf(compactModuleKeys);
    }

    public record ZoneLayout(
            List<String> moduleKeys,
            String activeModuleKey,
            boolean collapsed) {
        public ZoneLayout {
            moduleKeys = moduleKeys == null ? List.of() : List.copyOf(moduleKeys);
        }
    }

    public record SplitRatios(double left, double primary, double right, double bottom) {
    }
}
