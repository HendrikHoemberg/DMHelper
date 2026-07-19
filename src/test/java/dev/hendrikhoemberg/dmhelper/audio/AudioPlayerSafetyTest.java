package dev.hendrikhoemberg.dmhelper.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPlayerSafetyTest {

    @Test
    void playerHtmlNeverContainsAudioReferences() throws IOException {
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml)
                .doesNotContain("audio-provider-registry")
                .doesNotContain("audio-provider-youtube")
                .doesNotContain("audio-provider-fake")
                .doesNotContain("audio-widget")
                .doesNotContain("_cockpit-widget")
                .doesNotContain("audioWidget")
                .doesNotContain("audioCockpitWidget");
    }

    @Test
    void playerHtmlNeverContainsAudioCueIdentifiers() throws IOException {
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml)
                .doesNotContain("cueId")
                .doesNotContain("cueName")
                .doesNotContain("cueKey")
                .doesNotContain("audioCue");
    }

    @Test
    void playerHtmlNeverContainsProviderReferences() throws IOException {
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml)
                .doesNotContain("providerId")
                .doesNotContain("providerReference")
                .doesNotContain("YOUTUBE");
    }

    @Test
    void playerHtmlNeverContainsIframe() throws IOException {
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml).doesNotContain("iframe");
    }

    @Test
    void playerHtmlNeverContainsProviderErrors() throws IOException {
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml)
                .doesNotContain("PROVIDER_OFFLINE")
                .doesNotContain("AUTOPLAY_BLOCKED")
                .doesNotContain("CONTENT_UNAVAILABLE");
    }

    @Test
    void playerHtmlHasNoProviderScriptUrl() throws IOException {
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml)
                .doesNotContain("youtube.com/iframe_api")
                .doesNotContain("iframe_api");
    }

    @Test
    void playerJsNeverContainsAudioReferences() throws IOException {
        String playerJs = Files.readString(Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(playerJs)
                .doesNotContain("audioCockpitWidget")
                .doesNotContain("audioProvider")
                .doesNotContain("registerAudioProvider")
                .doesNotContain("audioWidget")
                .doesNotContain("AudioCue")
                .doesNotContain("cueName")
                .doesNotContain("sourceLabel");
    }

    @Test
    void playerJsNeverContainsProviderIdentifiers() throws IOException {
        String playerJs = Files.readString(Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(playerJs)
                .doesNotContain("YOUTUBE")
                .doesNotContain("providerReference")
                .doesNotContain("providerId");
    }

    @Test
    void playerJsNeverContainsProviderErrors() throws IOException {
        String playerJs = Files.readString(Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(playerJs)
                .doesNotContain("PROVIDER_OFFLINE")
                .doesNotContain("AUTOPLAY_BLOCKED")
                .doesNotContain("CONTENT_UNAVAILABLE");
    }

    @Test
    void playerJsNeverContainsIframe() throws IOException {
        String playerJs = Files.readString(Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(playerJs).doesNotContain("iframe");
    }

    @Test
    void playerJsNeverContainsProviderScriptUrl() throws IOException {
        String playerJs = Files.readString(Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(playerJs)
                .doesNotContain("youtube.com/iframe_api")
                .doesNotContain("iframe_api")
                .doesNotContain("createElement('script')");
    }

    @Test
    void websocketHandlerNeverContainsAudioReferences() throws IOException {
        String wsHandler = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/live/TableStateWebSocketHandler.java"));
        assertThat(wsHandler).doesNotContain("audio");
    }

    @Test
    void liveTableStateNeverContainsAudioFields() throws IOException {
        String lts = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java"));
        assertThat(lts)
                .doesNotContain("audio")
                .doesNotContain("cue")
                .doesNotContain("provider");
    }

    @Test
    void mapSnapshotNeverContainsAudioMetadata() throws IOException {
        String lts = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java"));
        int mapIdx = lts.indexOf("MapSnapshot");
        int handoutIdx = lts.indexOf("HandoutRef");
        String mapPart = lts.substring(mapIdx, handoutIdx);
        assertThat(mapPart)
                .doesNotContain("audio")
                .doesNotContain("cue")
                .doesNotContain("provider");
    }

    @Test
    void handoutRefNeverContainsAudioFields() throws IOException {
        String lts = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java"));
        int handoutIdx = lts.indexOf("HandoutRef");
        int combatantIdx = lts.indexOf("CombatantSnapshot");
        String handoutPart = lts.substring(handoutIdx, combatantIdx);
        assertThat(handoutPart)
                .doesNotContain("audio")
                .doesNotContain("cue")
                .doesNotContain("provider");
    }

    @Test
    void playerViewDoesNotReceiveAudioRuntimeData() throws IOException {
        String cockpitHtml = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String playerHtml = Files.readString(Path.of("src/main/resources/templates/player/view.html"));
        assertThat(playerHtml).doesNotContain("audio/runtime");
        assertThat(playerHtml).doesNotContain("runtime/state");
    }

    @Test
    void initialWebSocketMessageContainsNoAudioData() throws IOException {
        String wsHandler = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/live/TableStateWebSocketHandler.java"));
        assertThat(wsHandler)
                .doesNotContain("AudioRuntimeView")
                .doesNotContain("audioState")
                .doesNotContain("getRuntimeView");
    }
}
