package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.PageReady;
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
@Tag("browser")
class ShellRenderGateTest {

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}, {2560, 1440}};

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

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

    private void open(String path) {
        PageReady.open(page, "http://localhost:" + port, path);
    }

    @Test
    void noStandardPageScrollsHorizontallyAtAnySupportedViewport() {
        for (int[] viewport : VIEWPORTS) {
            page.setViewportSize(viewport[0], viewport[1]);
            for (String path : standardPages()) {
                open(path);
                int overflow = ((Number) page.evaluate(
                        "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"))
                        .intValue();
                assertThat(overflow)
                        .as("horizontal overflow on %s at %dx%d", path, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(1);
            }
        }
    }

    /**
     * The composition rules themselves live in {@code web.ShellCompositionContractTest}, which
     * reads them off the server's response for all 22 routes and needs no browser: nothing
     * they look at is built by script.
     *
     * <p>{@code /library} is the exception, and it is why this test stayed. Ten result panes
     * are empty in that response and arrive later from {@code hx-trigger="load"}, so a pane
     * is free to swap in a second heading or a second filled primary that a response-body
     * assertion would never see.
     */
    @Test
    void theLibraryPageStillComposesCleanlyOnceItsPanesHaveLoaded() {
        page.setViewportSize(1440, 900);
        open("/library");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) page.evaluate("""
                () => ({
                  shells: document.querySelectorAll('.app-shell').length,
                  rails: document.querySelectorAll('nav.rail').length,
                  h1: document.querySelectorAll('h1').length,
                  primary: document.querySelectorAll('.btn-primary').length,
                  panes: document.querySelectorAll('[hx-trigger~="load"]').length
                })
                """);
        assertThat(((Number) counts.get("panes")).intValue())
                .as("deferred panes on /library — at 0 the page has stopped deferring and "
                        + "this test is watching nothing")
                .isGreaterThan(0);
        assertThat(((Number) counts.get("shells")).intValue()).as("shells after load").isEqualTo(1);
        assertThat(((Number) counts.get("rails")).intValue()).as("rails after load").isEqualTo(1);
        assertThat(((Number) counts.get("h1")).intValue()).as("h1 after load").isEqualTo(1);
        assertThat(((Number) counts.get("primary")).intValue())
                .as("filled primary actions after load").isLessThanOrEqualTo(1);
    }

    @Test
    void theRailStaysUsableExpandedAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        open("/campaigns/" + seeded.campaignId());
        assertThat(page.locator("nav.rail").isVisible()).isTrue();
        assertThat(page.locator("nav.rail a.rail__link").count()).isGreaterThanOrEqualTo(20);
        int mainWidth = ((Number) page.evaluate(
                "() => document.querySelector('.app-main').getBoundingClientRect().width")).intValue();
        assertThat(mainWidth).as("main column width at 1280").isGreaterThanOrEqualTo(960);
    }
}
