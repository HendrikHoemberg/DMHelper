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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-31 section 4: every supported viewport, every zoom gate across the primary
 * range, reduced motion, and the minimum runtime text size. This is the product-wide
 * viewport matrix — the pages reviewed here are every standard page plus the two
 * viewport-owned workspaces (map editor and session cockpit).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class ViewportMatrixGateTest {

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}, {2560, 1440}};

    /**
     * Zoom is emulated by shrinking the viewport, because that is exactly what browser zoom
     * does to the CSS viewport: 125% of 1440x900 is 1152x720 CSS px.
     *
     * <p>The zoom gates run over the primary range only. Crossing every viewport with every
     * zoom would gate 1280x720 @ 150% = 853x480 CSS px, which contradicts spec section 4's
     * 1280x720 floor and this program's own minimum-viewport assertions (ShellRenderGateTest
     * requires .app-main to be at least 960px wide). 1440 @ 150% = 960x600 is the narrowest
     * CSS viewport the product commits to.
     */
    private static final int[][] ZOOMED = {
            {1440, 900, 125}, {1440, 900, 150}, {1920, 1080, 125}, {1920, 1080, 150}};

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

    /** Every page the product ships: the standard review set plus the two viewport owners. */
    private List<String> reviewedPages() {
        String c = "/campaigns/" + seeded.campaignId();
        return Stream.concat(
                Stream.of("/campaigns"),
                Stream.concat(campaignPages().stream(),
                        Stream.of("/library", "/library/tables", "/library/traps",
                                "/library/hazards", "/library/about",
                                c + "/maps/" + seeded.playableMapId() + "/edit",
                                c + "/session"))).toList();
    }

    private void assertNoHorizontalOverflow(String label) {
        for (String path : reviewedPages()) {
            PageReady.open(page, "http://localhost:" + port, path);
            int overflow = ((Number) page.evaluate(
                    "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"))
                    .intValue();
            assertThat(overflow).as("%s at %s", path, label).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void everyReviewedPageSurvivesEverySupportedViewport() {
        for (int[] viewport : VIEWPORTS) {
            page.setViewportSize(viewport[0], viewport[1]);
            assertNoHorizontalOverflow("%dx%d".formatted(viewport[0], viewport[1]));
        }
    }

    @Test
    void everyReviewedPageSurvivesTheZoomGatesAcrossThePrimaryRange() {
        for (int[] gate : ZOOMED) {
            double zoom = gate[2] / 100.0;
            page.setViewportSize((int) (gate[0] / zoom), (int) (gate[1] / zoom));
            assertNoHorizontalOverflow("%dx%d @ %d%%".formatted(gate[0], gate[1], gate[2]));
        }
    }

    @Test
    void reducedMotionRemovesNonEssentialAnimation() {
        BrowserContext reduced = browser.newContext(new Browser.NewContextOptions()
                .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE)
                .setViewportSize(1440, 900));
        Page reducedPage = reduced.newPage();
        try {
            PageReady.open(reducedPage, "http://localhost:" + port,
                    "/campaigns/" + seeded.campaignId());
            Object animated = reducedPage.evaluate("""
                    () => [...document.querySelectorAll('*')]
                            .filter(el => {
                              const s = getComputedStyle(el);
                              return s.animationName !== 'none'
                                  && parseFloat(s.animationDuration) > 0.05;
                            }).length
                    """);
            assertThat(((Number) animated).intValue()).isZero();
        } finally {
            reduced.close();
        }
    }

    @Test
    void requiredRuntimeTextNeverFallsBelowTheMinimumSize() {
        page.setViewportSize(1280, 720);
        PageReady.open(page, "http://localhost:" + port,
                "/campaigns/" + seeded.campaignId() + "/session");
        /* Module titles count: naming the pane you are reading is required runtime
           information, not optional provenance (spec 7.2). The utility rank used to set
           them to --text-xs. */
        Object tooSmall = page.evaluate("""
                () => [...document.querySelectorAll('.cockpit-module [data-runtime-state],'
                        + ' .cockpit-module [data-runtime-action],'
                        + ' .cockpit-module__header-title, .cockpit-zone__tab-label')]
                        .filter(el => el.getBoundingClientRect().width > 0)
                        .filter(el => parseFloat(getComputedStyle(el).fontSize) < 14)
                        .map(el => el.className + ': ' + getComputedStyle(el).fontSize)
                """);
        assertThat((List<?>) tooSmall).as("runtime text below --text-sm").isEmpty();
    }
}
