package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.UUID;

public record TableDependency(
        String kind, UUID dependentId, String label, String path) {
}
