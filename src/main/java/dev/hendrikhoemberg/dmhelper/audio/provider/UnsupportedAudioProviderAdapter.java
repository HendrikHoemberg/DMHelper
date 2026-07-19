package dev.hendrikhoemberg.dmhelper.audio.provider;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;

public class UnsupportedAudioProviderAdapter implements AudioProviderAdapter {

    private static final AudioProviderCapabilities NO_CAPABILITIES = new AudioProviderCapabilities(
        false, false, false, false, false, false, false, false, false, false
    );

    private final AudioProviderId providerId;

    public UnsupportedAudioProviderAdapter(AudioProviderId providerId) {
        this.providerId = providerId;
    }

    @Override
    public AudioProviderId id() {
        return providerId;
    }

    @Override
    public AudioAuthMode authMode() {
        return AudioAuthMode.NONE;
    }

    @Override
    public AudioProviderCapabilities capabilities() {
        return NO_CAPABILITIES;
    }

    @Override
    public ParsedAudioReference parseReference(String input) {
        return new ParsedAudioReference(AudioReferenceKind.VIDEO, input);
    }

    @Override
    public AudioProviderAvailability availability() {
        return AudioProviderAvailability.UNAVAILABLE;
    }

    @Override
    public void clearCredentials() {
    }
}
