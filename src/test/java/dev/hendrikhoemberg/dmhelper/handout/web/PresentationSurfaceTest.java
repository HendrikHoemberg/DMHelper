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

    @Test
    void unavailableAndErrorPresentationStatesAreExplicit() throws Exception {
        assertThat(overlay())
                .contains("data-presentation-state")
                .contains("_states :: unavailable");
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
