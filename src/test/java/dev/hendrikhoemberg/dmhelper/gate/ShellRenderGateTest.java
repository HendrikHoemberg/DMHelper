package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShellRenderGateTest {

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}, {2560, 1440}};

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private CampaignRepository campaigns;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        failures = new BrowserFailureCollector();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closeContext() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
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

    @Test
    void noStandardPageScrollsHorizontallyAtAnySupportedViewport() {
        for (int[] viewport : VIEWPORTS) {
            page.setViewportSize(viewport[0], viewport[1]);
            for (String path : standardPages()) {
                page.navigate("http://localhost:" + port + path);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                int overflow = ((Number) page.evaluate(
                        "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"))
                        .intValue();
                assertThat(overflow)
                        .as("horizontal overflow on %s at %dx%d", path, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    void everyStandardPageHasExactlyOneShellOneHeadingAndAtMostOnePrimaryAction() {
        page.setViewportSize(1440, 900);
        for (String path : standardPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) page.evaluate("""
                    () => ({
                      shells: document.querySelectorAll('.app-shell').length,
                      rails: document.querySelectorAll('nav.rail').length,
                      h1: document.querySelectorAll('h1').length,
                      primary: document.querySelectorAll('.btn-primary').length,
                      archetype: document.body.dataset.archetype
                    })
                    """);
            assertThat(((Number) counts.get("shells")).intValue()).as("shells on %s", path).isEqualTo(1);
            assertThat(((Number) counts.get("rails")).intValue()).as("rails on %s", path).isEqualTo(1);
            assertThat(((Number) counts.get("h1")).intValue()).as("h1 on %s", path).isEqualTo(1);
            assertThat(((Number) counts.get("primary")).intValue())
                    .as("filled primary actions on %s", path).isLessThanOrEqualTo(1);
            assertThat((String) counts.get("archetype"))
                    .as("archetype on %s", path)
                    .isIn("index", "detail", "form", "operational", "editor");
        }
    }

    /**
     * Spec 8.1: the top bar carries campaign identity. Asserted in the browser because the
     * only way this fails is a model attribute nobody publishes — the fragment shipped
     * bound to {@code ${campaignName}}, which no controller, advice or {@code th:with} ever
     * set, so the chip was dead markup on every page and the template still parsed clean.
     */
    @Test
    void everyCampaignScopedPageNamesItsCampaignInTheTopBar() {
        page.setViewportSize(1440, 900);
        String expected = campaigns.findById(seeded.campaignId()).orElseThrow().getName();
        assertThat(expected).as("the fixture campaign has a name to show").isNotBlank();
        for (String path : campaignPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".app-topbar__campaign").textContent().trim())
                    .as("campaign identity in the top bar on %s", path)
                    .isEqualTo(expected);
        }
    }

    @Test
    void theRailStaysUsableExpandedAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator("nav.rail").isVisible()).isTrue();
        assertThat(page.locator("nav.rail a.rail__link").count()).isGreaterThanOrEqualTo(20);
        int mainWidth = ((Number) page.evaluate(
                "() => document.querySelector('.app-main').getBoundingClientRect().width")).intValue();
        assertThat(mainWidth).as("main column width at 1280").isGreaterThanOrEqualTo(960);
    }
}
