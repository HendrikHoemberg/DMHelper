package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;

import java.util.UUID;

public record AudioCueWrite(
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
        UUID id
) {
    public AudioCueWrite withCueKey(String cueKey) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withName(String name) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withProviderId(String providerId) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withReferenceKind(AudioReferenceKind referenceKind) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withProviderReference(String providerReference) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withCachedTitle(String cachedTitle) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withArtistOrOwner(String artistOrOwner) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withArtworkUrl(String artworkUrl) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withDurationSeconds(Integer durationSeconds) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withCategory(AudioCategory category) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withVolumeHint(Integer volumeHint) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withTransitionPreference(AudioTransitionPreference transitionPreference) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }

    public AudioCueWrite withNotes(String notes) {
        return new AudioCueWrite(cueKey, name, providerId, referenceKind, providerReference,
                cachedTitle, artistOrOwner, artworkUrl, durationSeconds, category, volumeHint,
                transitionPreference, notes, id);
    }
}
