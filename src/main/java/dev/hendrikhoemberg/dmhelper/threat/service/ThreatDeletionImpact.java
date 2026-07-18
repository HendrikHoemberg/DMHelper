package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;

import java.util.List;
import java.util.UUID;

public record ThreatDeletionImpact(
        ThreatKind threatKind, UUID threatId, List<ThreatDependency> dependencies) {

    public boolean hasDependents() {
        return !dependencies.isEmpty();
    }
}
