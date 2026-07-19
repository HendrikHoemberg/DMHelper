package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AudioRuntimeCueTest {

    @Test
    void youtubeRuntimeCueDeclaresInstalledCapabilities() {
        AudioCue cue = cue("youtube", "dQw4w9WgXcQ");

        AudioRuntimeCue runtime = AudioRuntimeCue.from(cue);

        assertThat(runtime.providerAvailable()).isTrue();
        assertThat(runtime.providerId()).isEqualTo("youtube");
        assertThat(runtime.capabilities().playPause()).isTrue();
        assertThat(runtime.capabilities().volume()).isTrue();
        assertThat(runtime.capabilities().visiblePlayer()).isTrue();
        assertThat(runtime.capabilities().crossfade()).isFalse();
    }

    @Test
    void opaqueProviderIsPreservedButUnavailable() {
        AudioCue cue = cue("future-provider", "opaque-reference");

        AudioRuntimeCue runtime = AudioRuntimeCue.from(cue);

        assertThat(runtime.providerId()).isEqualTo("future-provider");
        assertThat(runtime.providerReference()).isEqualTo("opaque-reference");
        assertThat(runtime.providerAvailable()).isFalse();
        assertThat(runtime.capabilities().playPause()).isFalse();
    }

    private static AudioCue cue(String providerId, String reference) {
        AudioCue cue = new AudioCue();
        cue.setId(UUID.randomUUID());
        cue.setName("Runtime cue");
        cue.setProviderId(providerId);
        cue.setReferenceKind(AudioReferenceKind.VIDEO);
        cue.setProviderReference(reference);
        return cue;
    }
}
