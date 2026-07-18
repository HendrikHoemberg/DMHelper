package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;

import java.util.UUID;

/**
 * Finite union used by scenes, encounters, and map pins.
 * Exactly one of {@code trap} or {@code hazard} is non-null matching {@code kind}.
 */
public record ThreatCardView(
        ThreatKind kind,
        UUID id,
        String name,
        String descriptionHtml,
        TrapResponse trap,
        HazardResponse hazard
) {}
