package dev.hendrikhoemberg.dmhelper.session.layout;

public record CockpitModuleDefinition(
        String key,
        String title,
        CockpitModuleSource source,
        int minWidthPx,
        int minHeightPx,
        CockpitZone zone,
        boolean compactSupported,
        boolean focusSupported,
        CockpitModuleStateContract states) {

    public CockpitModuleDefinition {
        if (key == null || !key.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid cockpit module key.");
        }
        if (title == null || title.isBlank() || source == null || zone == null
                || states == null
                || minWidthPx < 1 || minHeightPx < 1) {
            throw new IllegalArgumentException("Cockpit module metadata is incomplete.");
        }
    }
}
