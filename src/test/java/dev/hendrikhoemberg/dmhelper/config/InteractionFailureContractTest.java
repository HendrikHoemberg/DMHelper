package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionFailureContractTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of("src/main/resources", path));
    }

    @Test
    void timeCriticalMutationSurfacesHaveNoEmptyCatchBlocks() throws IOException {
        assertThat(read("templates/encounter/_tracker.html")).doesNotContain("catch (e) {}");
        assertThat(read("templates/session/cockpit.html"))
                .doesNotContain("catch (e) {}", ".catch(() => {})");
        assertThat(read("static/js/map/battle-map.js"))
                .doesNotContain("catch (e) {}", "catch (e) { /* non-critical */ }");
        assertThat(read("static/js/session-cockpit.js"))
                .doesNotContain("catch (e) {}", ".catch(() => {})");
        assertThat(read("static/js/session-cockpit.js"))
                .doesNotContain("fetch(");
    }

    @Test
    void mutationSurfacesUseCheckedRequests() throws IOException {
        assertThat(read("templates/encounter/_tracker.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("templates/fragments/navbar.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("static/js/map/battle-map.js"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("static/js/session-cockpit.js"))
                .contains("window.dmRequest", "window.reportActionFailure");
    }

    @Test
    void audioWidgetUsesCheckedRequestsNotBareFetch() throws IOException {
        assertThat(read("static/js/audio-widget.js"))
                .contains("window.dmRequest")
                .contains("window.reportActionFailure");
    }

    @Test
    void audioWidgetMutationPathsHaveNoEmptyCatches() throws IOException {
        String js = read("static/js/audio-widget.js");
        for (var mutation : new String[]{"confirmCue:", "declineCue:", "setOverride:", "clearOverride:", "toggleMute:"}) {
            int fnStart = js.indexOf(mutation);
            int fnEnd = js.indexOf("},", fnStart);
            String fnBody = js.substring(fnStart, fnEnd);
            assertThat(fnBody)
                    .as("Mutation function '" + mutation.replace(":", "") + "' must not have empty catches")
                    .doesNotContain(".catch(function () {})")
                    .doesNotContain(".catch(function() {})");
        }
    }

    @Test
    void audioCueEditorHandlesServerErrors() throws IOException {
        String editor = read("static/js/audio-cue-editor.js");
        assertThat(editor)
                .contains("this.unsupportedMessage = 'Server error: ' + res.status")
                .contains("this.unsupportedMessage = 'Network error: ' + err.message");
    }

    @Test
    void errorSurfacesNeverContainRawProviderResponse() throws IOException {
        assertThat(read("static/js/audio-widget.js"))
                .doesNotContain("rawProviderResponse");
        assertThat(read("static/js/audio-provider-youtube.js"))
                .doesNotContain("rawProviderResponse");
    }

    @Test
    void errorSurfacesNeverLeakAccessCredentials() throws IOException {
        assertThat(read("static/js/audio-widget.js"))
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken")
                .doesNotContain("deviceId")
                .doesNotContain("authorizationCode");
        assertThat(read("static/js/audio-provider-youtube.js"))
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken")
                .doesNotContain("deviceId")
                .doesNotContain("authorizationCode");
    }

    @Test
    void audioWidgetRetryIsUserInitiatedNotAutomaticLoop() throws IOException {
        String js = read("static/js/audio-widget.js");
        int retryStart = js.indexOf("retry:");
        int retryEnd = js.indexOf("},", retryStart);
        String retryFn = js.substring(retryStart, retryEnd);
        assertThat(retryFn)
                .contains("errorMessage = ''")
                .contains("fetchState()")
                .doesNotContain("setTimeout")
                .doesNotContain("setInterval")
                .doesNotContain("location.reload");
    }
}
