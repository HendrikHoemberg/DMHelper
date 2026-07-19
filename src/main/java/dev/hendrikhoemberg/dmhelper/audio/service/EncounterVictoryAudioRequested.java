package dev.hendrikhoemberg.dmhelper.audio.service;

import java.util.UUID;

public record EncounterVictoryAudioRequested(
        UUID campaignId,
        UUID encounterId,
        String encounterName,
        UUID cueId,
        Integer durationSeconds
) {}
