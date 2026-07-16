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
}
