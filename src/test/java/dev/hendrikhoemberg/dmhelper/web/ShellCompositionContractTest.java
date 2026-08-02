package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Spec 8 and 9 composition rules, checked on what the server writes.
 *
 * <p>These assertions used to run in {@code ShellRenderGateTest}, one Playwright navigation
 * per page. Nothing they look at is built by script: no JavaScript in this application
 * creates an {@code .app-shell}, a {@code nav.rail}, an {@code h1}, a {@code .btn-primary} or
 * the {@code data-archetype} attribute, and {@code rail.js} only toggles a modifier class on
 * the shell Thymeleaf already rendered. A browser was therefore paying 38 page loads to
 * observe markup MockMvc hands over directly.
 *
 * <p>What stayed in the browser is the part a response body genuinely cannot answer:
 * horizontal overflow and the rail/main width arithmetic, which need a layout engine, and
 * {@code /library}'s composed DOM, which does not exist until ten {@code hx-trigger="load"}
 * panes have swapped in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShellCompositionContractTest {

    private static final List<String> ARCHETYPES =
            List.of("index", "detail", "form", "operational", "editor");

    @Autowired MockMvc mvc;
    @Autowired ReleaseRehearsalFixture fixture;
    @Autowired CampaignRepository campaigns;

    private ReleaseRehearsalFixture.Seeded seeded;

    @BeforeEach
    void seed() throws Exception {
        seeded = fixture.seed();
    }

    private List<String> campaignPages() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of(c, c + "/adventures", c + "/encounters", c + "/maps",
                c + "/handouts", c + "/audio/cues", c + "/notes", c + "/party", c + "/sheets",
                c + "/treasury", c + "/ledger", c + "/quests", c + "/world/npcs",
                c + "/world/locations", c + "/world/factions", c + "/calendar");
    }

    private List<String> standardPages() {
        return Stream.concat(
                Stream.of("/campaigns"),
                Stream.concat(campaignPages().stream(),
                        Stream.of("/library", "/library/tables", "/library/traps",
                                "/library/hazards", "/library/about"))).toList();
    }

    private Document render(String path) throws Exception {
        return Jsoup.parse(mvc.perform(get(path))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void everyStandardPageHasExactlyOneShellOneHeadingAndAtMostOnePrimaryAction() throws Exception {
        List<String> pages = standardPages();
        assertThat(pages).as("no page was checked — this test has gone blind").isNotEmpty();

        for (String path : pages) {
            Document page = render(path);
            assertThat(page.select(".app-shell")).as("shells on %s", path).hasSize(1);
            assertThat(page.select("nav.rail")).as("rails on %s", path).hasSize(1);
            assertThat(page.select("h1")).as("h1 on %s", path).hasSize(1);
            assertThat(page.select(".btn-primary"))
                    .as("filled primary actions on %s", path).hasSizeLessThanOrEqualTo(1);
            assertThat(page.body().attr("data-archetype"))
                    .as("archetype on %s", path).isIn(ARCHETYPES);
        }
    }

    /**
     * Spec 8.1: the top bar carries campaign identity. Worth its own test because the only
     * way it fails is a model attribute nobody publishes — the fragment shipped bound to
     * {@code ${campaignName}}, which no controller, advice or {@code th:with} ever set, so
     * the chip was dead markup on every page and the template still parsed clean.
     */
    @Test
    void everyCampaignScopedPageNamesItsCampaignInTheTopBar() throws Exception {
        String expected = campaigns.findById(seeded.campaignId()).orElseThrow().getName();
        assertThat(expected).as("the fixture campaign has a name to show").isNotBlank();

        for (String path : campaignPages()) {
            assertThat(render(path).select(".app-topbar__campaign").text().trim())
                    .as("campaign identity in the top bar on %s", path)
                    .isEqualTo(expected);
        }
    }
}
