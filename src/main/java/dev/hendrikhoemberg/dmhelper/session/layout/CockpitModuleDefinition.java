package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.Set;

public record CockpitModuleDefinition(
        String key,
        String title,
        CockpitModuleSource source,
        int minWidthPx,
        int minHeightPx,
        Set<CockpitZone> allowedZones,
        boolean compactSupported,
        boolean focusSupported,
        CockpitScreenSafetyBehavior screenSafetyBehavior,
        CockpitModuleStateContract states) {

    public CockpitModuleDefinition {
        allowedZones = Set.copyOf(allowedZones);
        if (key == null || !key.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid cockpit module key.");
        }
        if (title == null || title.isBlank() || source == null || allowedZones.isEmpty()
                || screenSafetyBehavior == null || states == null
                || minWidthPx < 1 || minHeightPx < 1) {
            throw new IllegalArgumentException("Cockpit module metadata is incomplete.");
        }
    }
}
