package dev.hendrikhoemberg.dmhelper.audio.provider;

public interface AudioProviderAdapter {

    AudioProviderId id();

    AudioAuthMode authMode();

    AudioProviderCapabilities capabilities();

    ParsedAudioReference parseReference(String input);

    AudioProviderAvailability availability();

    void clearCredentials();
}
