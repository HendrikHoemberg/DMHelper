package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;

import java.util.UUID;

/**
 * Combined DM pin DTO for scene pins and threat pins on a map.
 * Threat pins are DM-only and must never enter player projection.
 */
public record MapPinDto(
        String pinKind,
        UUID id,
        String key,
        int x,
        int y,
        String title,
        UUID sceneId,
        String sceneKey,
        ThreatKind threatKind,
        UUID threatId) {

    public static final String KIND_SCENE = "SCENE";
    public static final String KIND_THREAT = "THREAT";
}
