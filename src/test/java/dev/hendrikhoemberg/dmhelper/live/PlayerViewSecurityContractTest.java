package dev.hendrikhoemberg.dmhelper.live;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerViewSecurityContractTest {

    @Test
    void importedPresentationLabelsAreWrittenAsTextRatherThanHtml() throws IOException {
        String handoutRenderer = Files.readString(
                Path.of("src/main/resources/static/js/player/handout-renderer.js"));
        String playerView = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));

        assertThat(handoutRenderer)
                .contains("image.alt = state.handout.title || ''")
                .doesNotContain("innerHTML", "insertAdjacentHTML");

        String projectionRendering = playerView.substring(
                playerView.indexOf("function showHandout"),
                playerView.indexOf("function updateTokensOnly"));
        assertThat(projectionRendering)
                .contains("renderHandout(content, state)")
                .doesNotContain("innerHTML", "insertAdjacentHTML");

        String initiativeFn = playerView.substring(
                playerView.indexOf("function showInitiative"),
                playerView.indexOf("function updateTokensOnly"));
        assertThat(initiativeFn)
                .contains("name.textContent = combatant.name || ''")
                .contains("dot.textContent = String(condition)")
                .doesNotContain("innerHTML", "insertAdjacentHTML", "${state.handout.title}", "${c.name}");
    }

    @Test
    void playerViewUsesTextContentNeverInnerHtmlForCombatantData() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(js)
                .contains("name.textContent = combatant.name || ''")
                .contains("dot.textContent = String(condition).charAt(0).toUpperCase()")
                .doesNotContain("name.innerHTML")
                .doesNotContain("dot.innerHTML");
    }

    @Test
    void playerViewSetsImageAltFromTitleOnly() throws IOException {
        String renderer = Files.readString(
                Path.of("src/main/resources/static/js/player/handout-renderer.js"));
        assertThat(renderer)
                .contains("image.alt = state.handout.title || ''")
                .contains("image.src = state.handout.fileUrl");
    }

    @Test
    void playerViewDoesNotInsertAudioProviderData() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(js)
                .doesNotContain("audioProvider")
                .doesNotContain("YOUTUBE")
                .doesNotContain("iframe_api")
                .doesNotContain("cueName")
                .doesNotContain("sourceLabel");
    }

    @Test
    void playerViewDoesNotRenderErrorsFromAudioProviders() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(js)
                .doesNotContain("PROVIDER_OFFLINE")
                .doesNotContain("AUTOPLAY_BLOCKED")
                .doesNotContain("CONTENT_UNAVAILABLE");
    }

    @Test
    void playerViewDoesNotInsertIframesOrExternalScripts() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));
        assertThat(js)
                .doesNotContain("createElement('iframe')")
                .doesNotContain("createElement('script')")
                .doesNotContain("insertAdjacentHTML");
    }

    @Test
    void playerViewSupportsEmbeddedParam() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/live/web/PlayerViewController.java"));
        assertThat(controller).contains("embedded");
        assertThat(controller).contains("@RequestParam");
    }

    @Test
    void playerViewEmbeddedModeRemovesOuterChrome() throws IOException {
        String view = Files.readString(
                Path.of("src/main/resources/templates/player/view.html"));
        assertThat(view).contains("th:if=\"${!embedded}\"");
    }

    @Test
    void playerViewEmbeddedModeCanBeAtDefaultUrl() throws IOException {
        String controller = Files.readString(
                Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/live/web/PlayerViewController.java"));
        // The default without embedded param renders the full view
        assertThat(controller).contains("/player");
    }

    @Test
    void handoutRendererIsDomSafe() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/player/handout-renderer.js"));
        assertThat(js)
                .contains("image.src = state.handout.fileUrl")
                .contains("image.alt = state.handout.title || ''")
                .doesNotContain("innerHTML")
                .doesNotContain("insertAdjacentHTML");
    }
}