package dev.hendrikhoemberg.dmhelper.handout.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class PresentationSurfaceTest {

    private static String overlay() throws Exception {
        return Files.readString(
                Path.of("src/main/resources/templates/handout/_present-overlay.html"));
    }

    @Test
    void thePresentationCanvasCarriesNoDmChrome() throws Exception {
        var document = Jsoup.parse(overlay(), "", Parser.xmlParser());
        for (String chrome : List.of(".app-topbar", ".rail", ".page-header", ".toolbar",
                ".btn-danger")) {
            assertThat(document.select(chrome))
                    .as("DM chrome %s must not reach the presentation surface", chrome)
                    .isEmpty();
        }
        assertThat(overlay())
                .as("no fragment include may drag DM chrome in either")
                .doesNotContain("_topbar ::").doesNotContain("_rail ::")
                .doesNotContain("_page-header ::").doesNotContain("_toolbar ::");
    }

    private static String ruleBody(String file, String selector) throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css").resolve(file));
        Matcher matcher = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]*)\\}")
                .matcher(css);
        assertThat(matcher.find()).as("%s defines %s", file, selector).isTrue();
        return matcher.group(1);
    }

    @Test
    void thePresentedAssetReceivesTheViewportRatherThanACard() throws Exception {
        String overlay = ruleBody("components.css", ".handout-overlay");
        assertThat(overlay).contains("position: fixed").contains("inset: 0");
        assertThat(overlay).doesNotContain("var(--surface-raised)");
    }

    /**
     * Spec 11.11 asks for explicit unavailable <em>and</em> error states. Only the first
     * shipped, and its retry slot held a Close button rather than anything that re-fetches.
     */
    @Test
    void unavailableAndErrorPresentationStatesAreExplicit() throws Exception {
        var document = Jsoup.parse(overlay(), "", Parser.xmlParser());

        assertThat(overlay())
                .contains("data-presentation-state")
                .contains("_states :: unavailable")
                .contains("_states :: failed");

        assertThat(document.select("#presentationError"))
                .as("an asset that fails to load needs its own state, not an empty canvas")
                .isNotEmpty();
        assertThat(document.select("[data-presentation-retry]"))
                .as("spec 16: a recoverable failure offers a Retry")
                .isNotEmpty()
                .allSatisfy(retry -> assertThat(
                        retry.hasAttr("data-presentation-route")
                                || retry.attr("th:attr").contains("data-presentation-route="))
                        .as("the Retry re-fetches the presentation route")
                        .isTrue());
    }

    /** Spec 15: this is a blocking overlay and has to declare itself as one. */
    @Test
    void thePresentationSurfaceIsPartOfTheElevationModel() throws Exception {
        var root = Jsoup.parse(overlay(), "", Parser.xmlParser()).selectFirst(".handout-overlay");
        assertThat(root).isNotNull();
        assertThat(root.attr("role")).isEqualTo("dialog");
        assertThat(root.attr("aria-modal")).isEqualTo("true");
        assertThat(root.attr("th:attr"))
                .as("the overlay names itself for assistive technology")
                .contains("aria-label=");
        assertThat(root.hasAttr("data-presentation-overlay"))
                .as("ui-overlay.js adopts the surface through this hook, which is what gives "
                        + "it Escape, a focus trap, and focus restoration")
                .isTrue();
        assertThat(root.select("[data-overlay-dismiss]"))
                .as("a keyboard user needs a focusable way out")
                .isNotEmpty();
    }

    @Test
    void shieldSemanticsMatchTheDmPreview() throws Exception {
        String card = Files.readString(
                Path.of("src/main/resources/templates/handout/_card.html"));
        assertThat(card).contains("tone='shield'");
        assertThat(overlay())
                .as("the overlay states its player-safe status the same way")
                .contains("data-handout-visibility");
    }
}
