package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;

import java.util.UUID;

public record MapThreatPinWrite(
        String key,
        ThreatKind threatKind,
        UUID threatId,
        int x,
        int y,
        String label,
        int sortOrder) {}
