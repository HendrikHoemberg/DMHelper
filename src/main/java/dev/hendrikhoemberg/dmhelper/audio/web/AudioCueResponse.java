package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;

import java.time.Instant;
import java.util.UUID;

public record AudioCueResponse(
        UUID id,
        UUID campaignId,
        String cueKey,
        String name,
        String providerId,
        AudioReferenceKind referenceKind,
        String providerReference,
        String cachedTitle,
        String artistOrOwner,
        String artworkUrl,
        Integer durationSeconds,
        AudioCategory category,
        Integer volumeHint,
        AudioTransitionPreference transitionPreference,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
