package dev.hendrikhoemberg.dmhelper.audio.provider;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YouTubeProviderAdapterTest {

    private final YouTubeProviderAdapter adapter = new YouTubeProviderAdapter();

    @Test
    void parsesRawVideoId() {
        ParsedAudioReference ref = adapter.parseReference("dQw4w9WgXcQ");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void parsesRawVideoIdThatStartsWithPlaylistPrefix() {
        ParsedAudioReference ref = adapter.parseReference("PLdQw4w9WgX");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("PLdQw4w9WgX");
    }

    @Test
    void parsesRawPlaylistId() {
        ParsedAudioReference ref = adapter.parseReference("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.PLAYLIST);
        assertThat(ref.id()).isEqualTo("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf");
    }

    @Test
    void parsesWatchUrl() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void parsesPlaylistUrl() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.PLAYLIST);
        assertThat(ref.id()).isEqualTo("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf");
    }

    @Test
    void parsesEmbedUrl() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void parsesShortUrl() {
        ParsedAudioReference ref = adapter.parseReference("https://youtu.be/dQw4w9WgXcQ");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void parsesMobileUrl() {
        ParsedAudioReference ref = adapter.parseReference("https://m.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void rejectsHttpScheme() {
        assertThatThrownBy(() -> adapter.parseReference("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLookalikeDomain() {
        assertThatThrownBy(() -> adapter.parseReference("https://www.youtubee.com/watch?v=dQw4w9WgXcQ"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUserInfoHostTrick() {
        assertThatThrownBy(() -> adapter.parseReference("https://www.youtube.com@evil.com/watch?v=dQw4w9WgXcQ"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankVideoId() {
        assertThatThrownBy(() -> adapter.parseReference("https://www.youtube.com/watch?v="))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankPlaylistId() {
        assertThatThrownBy(() -> adapter.parseReference("https://www.youtube.com/playlist?list="))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCharactersInId() {
        assertThatThrownBy(() -> adapter.parseReference("https://www.youtube.com/watch?v=!!invalid!!"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdWithInvalidLength_rawId() {
        assertThatThrownBy(() -> adapter.parseReference("ab"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMixedUnknownShape() {
        assertThatThrownBy(() -> adapter.parseReference("https://www.youtube.com/watc?something"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScriptUrl() {
        assertThatThrownBy(() -> adapter.parseReference("javascript:alert(1)"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDataUrl() {
        assertThatThrownBy(() -> adapter.parseReference("data:text/html,<script>alert(1)</script>"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dropsTimestampParameter() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=123s");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void dropsTrackingParameter() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void dropsFragment() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ#fragment");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void dropsListParamWhenVideoPresentInWatchUrl() {
        ParsedAudioReference ref = adapter.parseReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf");
        assertThat(ref.kind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(ref.id()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void idIsYoutube() {
        assertThat(adapter.id()).isEqualTo(AudioProviderId.YOUTUBE);
    }

    @Test
    void authModeIsNone() {
        assertThat(adapter.authMode()).isEqualTo(AudioAuthMode.NONE);
    }

    @Test
    void capabilitiesAreCorrect() {
        AudioProviderCapabilities caps = adapter.capabilities();
        assertThat(caps.knownVideo()).isTrue();
        assertThat(caps.knownPlaylist()).isTrue();
        assertThat(caps.playPause()).isTrue();
        assertThat(caps.skip()).isTrue();
        assertThat(caps.volume()).isTrue();
        assertThat(caps.queue()).isTrue();
        assertThat(caps.search()).isFalse();
        assertThat(caps.crossfade()).isFalse();
        assertThat(caps.visiblePlayer()).isTrue();
        assertThat(caps.initialGesture()).isTrue();
    }

    @Test
    void availabilityIsAvailable() {
        assertThat(adapter.availability()).isEqualTo(AudioProviderAvailability.AVAILABLE);
    }
}
