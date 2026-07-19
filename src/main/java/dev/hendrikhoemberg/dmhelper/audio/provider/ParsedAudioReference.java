package dev.hendrikhoemberg.dmhelper.audio.provider;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;

public record ParsedAudioReference(AudioReferenceKind kind, String id) {
    public ParsedAudioReference {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Reference ID must not be blank");
        }
    }
}
