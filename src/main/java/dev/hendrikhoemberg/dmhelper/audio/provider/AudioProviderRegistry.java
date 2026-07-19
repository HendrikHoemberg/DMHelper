package dev.hendrikhoemberg.dmhelper.audio.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AudioProviderRegistry {

    private static final Map<AudioProviderId, AudioProviderAdapter> adapters = new LinkedHashMap<>();

    static {
        register(new YouTubeProviderAdapter());
    }

    private static void register(AudioProviderAdapter adapter) {
        adapters.put(adapter.id(), adapter);
    }

    public static AudioProviderAdapter lookup(AudioProviderId id) {
        if (id == null) {
            throw new IllegalArgumentException("Provider ID must not be null");
        }
        return adapters.getOrDefault(id, new UnsupportedAudioProviderAdapter(id));
    }

    public static AudioProviderAdapter lookup(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider ID must not be null or blank");
        }
        AudioProviderId id = AudioProviderId.of(providerId);
        return lookup(id);
    }

    public static List<AudioProviderAdapter> all() {
        return List.copyOf(adapters.values());
    }

    private AudioProviderRegistry() {
    }
}
