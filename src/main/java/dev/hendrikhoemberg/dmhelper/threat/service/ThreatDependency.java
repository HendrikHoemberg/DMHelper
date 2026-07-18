package dev.hendrikhoemberg.dmhelper.threat.service;

import java.util.UUID;

public record ThreatDependency(
        String kind, UUID dependentId, String label, String destination) {
}
