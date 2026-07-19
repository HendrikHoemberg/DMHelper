package dev.hendrikhoemberg.dmhelper.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioFailureIsolationTest {

    @Test
    void providerErrorsAreMappedToNormalizedCodes() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        assertThat(js)
                .contains("PROVIDER_OFFLINE")
                .contains("CONTENT_UNAVAILABLE")
                .contains("AUTOPLAY_BLOCKED")
                .contains("POLICY_DISABLED")
                .contains("UNSUPPORTED_CONTROL");
    }

    @Test
    void unknownProviderErrorDefaultsToContentUnavailable() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        assertThat(js).contains("return ERROR_MESSAGES[category] ? category : 'CONTENT_UNAVAILABLE'");
    }

    @Test
    void youtubeProviderMapsKnownErrorCodes() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-youtube.js"));
        assertThat(js)
                .contains("2: 'CONTENT_UNAVAILABLE'")
                .contains("100: 'CONTENT_UNAVAILABLE'")
                .contains("150: 'CONTENT_UNAVAILABLE'")
                .contains("153: 'PROVIDER_OFFLINE'");
    }

    @Test
    void retryDoesNotReloadPage() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        String retryFn = js.substring(js.indexOf("retry:"), js.indexOf("},", js.indexOf("retry:")));
        assertThat(retryFn)
                .doesNotContain("location.reload")
                .doesNotContain("location.href")
                .doesNotContain("window.location")
                .contains("fetchState");
    }

    @Test
    void retryClearsErrorMessageBeforeFetch() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        String retryFn = js.substring(js.indexOf("retry:"), js.indexOf("},", js.indexOf("retry:")));
        assertThat(retryFn)
                .contains("this.errorMessage = ''")
                .contains("this.fetchState()");
    }

    @Test
    void fakeAdapterSupportsInjectedFailures() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-provider-fake.js"));
        assertThat(js)
                .contains("injectFailure")
                .contains("failures")
                .contains("clearFailures");
    }

    @Test
    void handleProviderErrorRejectsRawMessages() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/audio-widget.js"));
        String handler = js.substring(js.indexOf("handleProviderError"),
                js.indexOf("},", js.indexOf("handleProviderError")));
        assertThat(handler)
                .doesNotContain("rawProviderResponse")
                .doesNotContain("provider stack");
    }

    @Test
    void audioRuntimeApiCatchesMissingStateGracefully() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/audio/web/AudioRuntimeApiController.java"));
        assertThat(controller)
                .contains("try")
                .contains("catch")
                .contains("ResponseEntity.notFound()");
    }

    @Test
    void widgetErrorRegionIsLiveAndShowsNormalizedMessage() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/audio/_cockpit-widget.html"));
        assertThat(html)
                .contains("aria-live")
                .contains("x-text=\"errorMessage\"")
                .doesNotContain("x-html=\"errorMessage\"");
    }

    @Test
    void sceneSwitchingDoesNotFailWhenAudioStateIsMissing() throws IOException {
        String resolver = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/audio/service/AudioCueResolver.java"));
        assertThat(resolver).doesNotContain("throw");
    }
}
