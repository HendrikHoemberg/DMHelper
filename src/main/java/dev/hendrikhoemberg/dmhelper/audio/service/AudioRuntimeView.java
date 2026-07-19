package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;

import java.util.UUID;

public record AudioRuntimeView(
        AudioCue cue,
        String sourceKind,
        UUID sourceId,
        String sourceLabel,
        boolean muted,
        AudioSwitchMode switchMode,
        boolean hasPendingConfirmation,
        boolean actionableAvailable
) {
}
