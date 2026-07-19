package dev.hendrikhoemberg.dmhelper.audio.service;

import java.util.List;
import java.util.UUID;

public record AudioCueDeletionImpact(
        UUID cueId,
        String cueName,
        List<AudioCueDependency> dependencies
) {
    public boolean hasDependents() {
        return !dependencies.isEmpty();
    }
}
