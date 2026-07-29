package dev.hendrikhoemberg.dmhelper.audio.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AudioWidgetTemplateContractTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void widgetHasNowPlayingArea() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("now-playing");
        assertThat(html).contains("cueName");
        assertThat(html).contains("sourceLabel");
    }

    @Test
    void widgetHasEnableButton() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("enablePlayback");
    }

    @Test
    void widgetHasPlayPauseControl() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("togglePlayback");
    }

    @Test
    void widgetHasSkipControl() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("skip");
    }

    @Test
    void widgetHasVolumeControl() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("setVolume");
        assertThat(html).contains("type=\"range\"");
    }

    @Test
    void widgetHasOverridePicker() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("override");
        assertThat(html).contains("clearOverride");
    }

    @Test
    void widgetHasClearOverrideButton() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("clearOverride");
    }

    @Test
    void widgetHasMuteButton() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("toggleMute");
    }

    @Test
    void widgetHasRetryButton() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("retry");
    }

    @Test
    void widgetHasPendingConfirmation() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("hasPendingConfirmation");
        assertThat(html).contains("confirmCue");
        assertThat(html).contains("declineCue");
    }

    @Test
    void widgetHasErrorLiveRegion() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("aria-live");
        assertThat(html).contains("errorMessage");
    }

    @Test
    void widgetHasVisiblePlayerMount() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("playerMount");
        assertThat(html).contains("playerViewport");
        assertThat(html).contains("480");
        assertThat(html).contains("270");
        assertThat(html).doesNotContain("display:none");
    }

    @Test
    void audioWidgetJsHasComponentFunction() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        assertThat(js).contains("audioCockpitWidget");
        assertThat(js).contains("IntersectionObserver");
        assertThat(js).contains("visibilityState");
    }

    @Test
    void registryJsHasRegisterFunction() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-registry.js"));
        assertThat(js).contains("registerAudioProvider");
        assertThat(js).contains("already registered");
    }

    @Test
    void youtubeJsHasYoutubeProvider() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-youtube.js"));
        assertThat(js).contains("YOUTUBE");
        assertThat(js).contains("enable");
        assertThat(js).contains("youtube.com/iframe_api");
        assertThat(js).contains("loadVideoById");
        assertThat(js).contains("playVideo");
        assertThat(js).contains("pauseVideo");
    }

    @Test
    void fakeJsIsGatedOnDataset() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-fake.js"));
        assertThat(js).contains("testAudioProvider");
        assertThat(js).contains("dataset");
    }

    @Test
    void cockpitIncludesAudioWidget() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        assertThat(html).contains("audio/_cockpit-widget");
        assertThat(html).contains("audio-provider-registry.js");
        assertThat(html).contains("audio-provider-youtube.js");
        assertThat(html).contains("audio-widget.js");
        assertThat(html).contains("th:if=\"${testAudioProvider}\"");
        assertThat(html).contains("data-test-audio-provider");
    }

    @Test
    void audioWidgetJsContainsVisibilityAndIntersectionChecks() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        assertThat(js).contains("document.visibilityState");
        assertThat(js).contains("IntersectionObserver");
    }

    @Test
    void audioWidgetJsMapsErrorCodes() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-youtube.js"));
        assertThat(js).contains("AUTOPLAY_BLOCKED");
        assertThat(js).contains("CONTENT_UNAVAILABLE");
        assertThat(js).contains("UNSUPPORTED_CONTROL");
    }

    @Test
    void fakeAdapterRecordsCommands() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-fake.js"));
        assertThat(js).contains("commands");
        assertThat(js).contains("injectFailure", "injectFailureOn");
    }

    @Test
    void cockpitAudioScriptsNotInDiceRollerPlayerOrCommandPalette() throws IOException {
        String dice = Files.readString(Path.of("src/main/resources/static/js/dice-roller.js"));
        String palette = Files.readString(Path.of("src/main/resources/static/js/command-palette.js"));
        assertThat(dice).doesNotContain("audio-provider-registry");
        assertThat(palette).doesNotContain("audio-provider-registry");
    }

    @Test
    void widgetHasAccessibleLabels() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html).contains("aria-label");
    }

    @Test
    void widgetRoutesFromRuntimeProviderAndNeverAutoEnables() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        String loadAndPlay = js.substring(js.indexOf("loadAndPlay:"), js.indexOf("clearProviderError:"));
        assertThat(js)
                .contains("window.addEventListener('cockpit-rails-refreshed'")
                .contains("cue.providerAvailable")
                .contains("cue.capabilities")
                .contains("desiredBrowserProvider")
                .doesNotContain("var providerId = 'YOUTUBE'");
        assertThat(loadAndPlay).doesNotContain("adapter.enable");
    }

    @Test
    void widgetCoalescesStateAndBoundsVictoryAndRetryWork() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        assertThat(js)
                .contains("AbortController")
                .contains("_stateSequence")
                .contains("_operationSequence")
                .contains("_retryInFlight")
                .contains("scheduleVictoryExpiry")
                .contains("victory/expire");
    }

    @Test
    void youtubeApiLoadHasFailureAndTimeoutBounds() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-youtube.js"));
        assertThat(js)
                .contains("API_TIMEOUT_MS")
                .contains("tag.onerror")
                .contains("readyTimeout")
                .contains("origin: window.location.origin");
    }

    private static int count(String s, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
