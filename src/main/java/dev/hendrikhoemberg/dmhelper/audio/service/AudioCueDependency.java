package dev.hendrikhoemberg.dmhelper.audio.service;

import java.util.UUID;

public record AudioCueDependency(
        String kind,
        UUID dependentId,
        String label,
        String destination
) {
}
