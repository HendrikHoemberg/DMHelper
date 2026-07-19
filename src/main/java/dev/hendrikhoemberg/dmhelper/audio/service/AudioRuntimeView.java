package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;

import java.time.Instant;
import java.util.UUID;

public record AudioRuntimeView(
        AudioRuntimeCue cue,
        AudioRuntimeCue pendingCue,
        String sourceKind,
        UUID sourceId,
        String sourceLabel,
        boolean muted,
        AudioSwitchMode switchMode,
        boolean hasPendingConfirmation,
        boolean actionableAvailable,
        Instant victoryUntil
) {
    public static AudioRuntimeView unavailable() {
        return new AudioRuntimeView(null, null, null, null, null,
                false, null, false, false, null);
    }
}
