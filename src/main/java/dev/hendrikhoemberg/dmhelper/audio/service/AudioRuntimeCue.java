package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.provider.AudioProviderAvailability;
import dev.hendrikhoemberg.dmhelper.audio.provider.AudioProviderCapabilities;
import dev.hendrikhoemberg.dmhelper.audio.provider.AudioProviderRegistry;

import java.util.UUID;

public record AudioRuntimeCue(
        UUID id,
        String name,
        String providerId,
        AudioReferenceKind referenceKind,
        String providerReference,
        String cachedTitle,
        String artistOrOwner,
        Integer volumeHint,
        AudioTransitionPreference transitionPreference,
        AudioProviderCapabilities capabilities,
        boolean providerAvailable
) {
    public static AudioRuntimeCue from(AudioCue cue) {
        if (cue == null) return null;
        AudioProviderCapabilities capabilities = AudioProviderCapabilities.NONE;
        boolean available = false;
        if (cue.getProviderId() != null && !cue.getProviderId().isBlank()) {
            try {
                var adapter = AudioProviderRegistry.lookup(cue.getProviderId());
                capabilities = adapter.capabilities();
                available = adapter.availability() == AudioProviderAvailability.AVAILABLE;
            } catch (RuntimeException ignored) {
                // Imported opaque providers are deliberately unavailable.
            }
        }
        return new AudioRuntimeCue(cue.getId(), cue.getName(), cue.getProviderId(),
                cue.getReferenceKind(), cue.getProviderReference(), cue.getCachedTitle(),
                cue.getArtistOrOwner(), cue.getVolumeHint(), cue.getTransitionPreference(),
                capabilities, available);
    }
}
