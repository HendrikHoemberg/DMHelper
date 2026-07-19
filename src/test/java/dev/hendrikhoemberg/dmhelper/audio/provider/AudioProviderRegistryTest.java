package dev.hendrikhoemberg.dmhelper.audio.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioProviderRegistryTest {

    @Test
    void looksUpYoutubeExactly() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup(AudioProviderId.YOUTUBE);
        assertThat(adapter).isInstanceOf(YouTubeProviderAdapter.class);
    }

    @Test
    void looksUpYoutubeByCaseInsensitiveString() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup("YouTube");
        assertThat(adapter).isInstanceOf(YouTubeProviderAdapter.class);
    }

    @Test
    void looksUpYoutubeByLowercaseString() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup("youtube");
        assertThat(adapter).isInstanceOf(YouTubeProviderAdapter.class);
    }

    @Test
    void looksUpYoutubeByUppercaseString() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup("YOUTUBE");
        assertThat(adapter).isInstanceOf(YouTubeProviderAdapter.class);
    }

    @Test
    void returnsUnsupportedForUnknownProviderId() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup("spotify");
        assertThat(adapter).isInstanceOf(UnsupportedAudioProviderAdapter.class);
        assertThat(adapter.id()).isEqualTo(AudioProviderId.of("spotify"));
    }

    @Test
    void returnsUnsupportedForOpaqueString() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup("some-random-provider-42");
        assertThat(adapter).isInstanceOf(UnsupportedAudioProviderAdapter.class);
        assertThat(adapter.id()).isEqualTo(AudioProviderId.of("some-random-provider-42"));
    }

    @Test
    void allRegisteredAdaptersHaveUniqueIds() {
        var adapters = AudioProviderRegistry.all();
        var ids = adapters.stream().map(AudioProviderAdapter::id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void registeredAdaptersCapabilitiesTruth_youtube() {
        AudioProviderAdapter yt = AudioProviderRegistry.lookup(AudioProviderId.YOUTUBE);
        assertThat(yt.capabilities().knownVideo()).isTrue();
        assertThat(yt.capabilities().knownPlaylist()).isTrue();
        assertThat(yt.capabilities().search()).isFalse();
        assertThat(yt.capabilities().crossfade()).isFalse();
    }

    @Test
    void registeredAdaptersCapabilitiesTruth_unsupported() {
        AudioProviderAdapter unsupported = AudioProviderRegistry.lookup("unknown");
        assertThat(unsupported.capabilities().knownVideo()).isFalse();
        assertThat(unsupported.capabilities().knownPlaylist()).isFalse();
        assertThat(unsupported.capabilities().playPause()).isFalse();
    }

    @Test
    void rejectsNullLookup() {
        assertThatThrownBy(() -> AudioProviderRegistry.lookup((String) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankLookup() {
        assertThatThrownBy(() -> AudioProviderRegistry.lookup(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
