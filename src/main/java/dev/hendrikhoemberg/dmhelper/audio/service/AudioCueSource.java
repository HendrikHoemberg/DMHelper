package dev.hendrikhoemberg.dmhelper.audio.service;

import java.util.UUID;

public record AudioCueSource(
        SourceKind kind,
        UUID sourceId,
        String sourceLabel
) {
    public enum SourceKind {
        MANUAL_OVERRIDE,
        VICTORY,
        COMBAT,
        SCENE,
        LOCATION,
        CAMPAIGN_DEFAULT
    }
}
