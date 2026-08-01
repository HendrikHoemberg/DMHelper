package dev.hendrikhoemberg.dmhelper.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page header is now the shared {@code fragments/_page-header.html} fragment (spec 8.4),
 * owned by every shell page since Task 19. The campaign eyebrow and tapered rule of the
 * pre-migration design are retired, so these tests assert the shared contract directly and
 * police that the old chrome stays gone. The per-page count contract lives in the
 * {@code config} package; this class is deliberately complementary.
 */
class PageHeaderContractTest {

    private static final Path HEADER =
            Path.of("src/main/resources/templates/fragments/_page-header.html");

    private static final List<String> CAMPAIGN_LIST_PAGES = List.of(
            "src/main/resources/templates/encounter/list.html",
            "src/main/resources/templates/maps/list.html",
            "src/main/resources/templates/notes/list.html",
            "src/main/resources/templates/audio/list.html",
            "src/main/resources/templates/party/list.html",
            "src/main/resources/templates/sheet/overview.html",
            "src/main/resources/templates/treasury/list.html",
            "src/main/resources/templates/world/npcs-list.html",
            "src/main/resources/templates/world/locations-list.html",
            "src/main/resources/templates/world/factions-list.html",
            "src/main/resources/templates/quest/list.html");

    @Test
    void theSharedHeaderRendersOnePrincipalTitleAndAnActionRegion() throws IOException {
        Document header = Jsoup.parse(Files.readString(HEADER));
        assertThat(header.select("h1")).as("the shared header renders exactly one h1").hasSize(1);
        assertThat(header.select("h1[data-display-title]"))
                .as("the shared title is the one display-title in the header").hasSize(1);
        assertThat(header.select("[data-action-region]"))
                .as("the shared header exposes the action region").isNotEmpty();
    }

    @Test
    void theLegacyCampaignEyebrowAndTaperedRuleAreRetired() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            List<Path> shellPages = files.filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("fragments/_shell :: page");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            assertThat(shellPages).as("shell pages scanned").isNotEmpty();
            for (Path page : shellPages) {
                String html = Files.readString(page);
                assertThat(html).as("%s eyebrow", page)
                        .doesNotContain("page-header-eyebrow");
                assertThat(html).as("%s tapered rule", page)
                        .doesNotContain("rule-taper");
            }
        }
    }

    @Test
    void noAsciiDoubleHyphenPseudoDashes() throws IOException {
        for (String page : CAMPAIGN_LIST_PAGES) {
            assertThat(Files.readString(Path.of(page)))
                    .as("%s must use — not --", page)
                    .doesNotContain(" -- ");
        }
    }
}
