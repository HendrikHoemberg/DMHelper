package dev.hendrikhoemberg.dmhelper.live;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerViewSecurityContractTest {

    @Test
    void importedPresentationLabelsAreWrittenAsTextRatherThanHtml() throws IOException {
        String javascript = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));
        String projectionRendering = javascript.substring(
                javascript.indexOf("function showHandout"),
                javascript.indexOf("function updateTokensOnly"));

        assertThat(projectionRendering)
                .contains("image.alt = state.handout.title || ''",
                        "name.textContent = combatant.name || ''",
                        "dot.textContent = String(condition)")
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
        String js = Files.readString(
                Path.of("src/main/resources/static/js/player/player-view.js"));
        int showHandout = js.indexOf("function showHandout");
        int showInitiative = js.indexOf("function showInitiative");
        String handoutFn = js.substring(showHandout, showInitiative);
        assertThat(handoutFn)
                .contains("image.alt = state.handout.title || ''")
                .doesNotContain("image.src = state.handout")
                .contains("image.src = `/player/files/");
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
}
