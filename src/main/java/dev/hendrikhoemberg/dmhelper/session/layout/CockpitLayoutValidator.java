package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitLayoutValidator {
    private final CockpitModuleRegistry registry;

    public CockpitLayoutValidator(CockpitModuleRegistry registry) {
        this.registry = registry;
    }

    public void validateForSave(CockpitLayoutDocument layout) {
        List<String> problems = new ArrayList<>();
        if (layout == null) {
            throw new IllegalArgumentException("Layout is required");
        }
        if (layout.schemaVersion() != CockpitLayoutDocument.CURRENT_SCHEMA_VERSION) {
            problems.add("Layout schema version must be 1");
        }
        if (layout.name() == null || layout.name().isBlank() || layout.name().trim().length() > 80) {
            problems.add("Preset name must contain 1 to 80 characters");
        }
        if (layout.zones() == null
                || !layout.zones().keySet().equals(EnumSet.allOf(CockpitZone.class))) {
            problems.add("Layout must contain all four cockpit zones");
        }

        Set<String> seenModules = new HashSet<>();
        Set<String> placedModules = new HashSet<>();
        if (layout.zones() != null) {
            for (CockpitZone zone : CockpitZone.values()) {
                CockpitLayoutDocument.ZoneLayout zoneLayout = layout.zones().get(zone);
                if (zoneLayout == null) {
                    problems.add(zone + " zone layout is required");
                    continue;
                }
                if (zoneLayout.collapsed() && zone != CockpitZone.BOTTOM_UTILITY) {
                    problems.add("Only Bottom utility may collapse");
                }
                List<String> keys = zoneLayout.moduleKeys();
                if (zone == CockpitZone.PRIMARY && (keys == null || keys.isEmpty())) {
                    problems.add("Primary requires a module");
                }
                if (keys != null) {
                    for (String key : keys) {
                        if (!seenModules.add(key)) {
                            problems.add("Module '" + key + "' appears more than once");
                        }
                        if (!registry.contains(key)) {
                            problems.add("Unknown cockpit module: " + key);
                            continue;
                        }
                        placedModules.add(key);
                        CockpitModuleDefinition definition = registry.require(key);
                        if (!definition.allowedZones().contains(zone)) {
                            problems.add(key + " is not allowed in " + zone);
                        }
                    }
                }
                String active = zoneLayout.activeModuleKey();
                if (keys == null || keys.isEmpty()) {
                    if (active != null) {
                        problems.add(zone + " active tab must be null when the zone is empty");
                    }
                } else if (active == null || !keys.contains(active)) {
                    problems.add(zone + " active tab must be one of the zone modules");
                }
            }
        }

        if (layout.compactModuleKeys() != null) {
            for (String key : layout.compactModuleKeys()) {
                if (!placedModules.contains(key)) {
                    problems.add("Compact module '" + key + "' is not placed in the layout");
                    continue;
                }
                if (!registry.contains(key) || !registry.require(key).compactSupported()) {
                    problems.add("Module '" + key + "' does not support compact mode");
                }
            }
        }

        CockpitLayoutDocument.SplitRatios ratios = layout.ratios();
        if (ratios == null
                || !Double.isFinite(ratios.left())
                || !Double.isFinite(ratios.primary())
                || !Double.isFinite(ratios.right())
                || !Double.isFinite(ratios.bottom())) {
            problems.add("Layout ratios must be finite numbers");
        } else {
            double horizontal = ratios.left() + ratios.primary() + ratios.right();
            if (Math.abs(horizontal - 1.0) > 0.001) {
                problems.add("Horizontal ratios must sum to 1.0");
            }
            if (ratios.primary() < 0.50 || ratios.primary() > 0.65) {
                problems.add("Primary ratio must be between 0.50 and 0.65");
            }
            if (ratios.bottom() < 0.16 || ratios.bottom() > 0.40) {
                problems.add("Bottom ratio must be between 0.16 and 0.40");
            }
            if (ratios.left() <= 0 || ratios.right() <= 0) {
                problems.add("Left and right ratios must be positive");
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }
}
