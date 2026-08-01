package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisualFoundationRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/visual-foundations");

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
        Files.createDirectories(SHOTS);
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures = new BrowserFailureCollector();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void open(String path) {
        page.navigate("http://localhost:" + port + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private String bg(String selector) {
        return (String) page.evaluate(
                "s => getComputedStyle(document.querySelector(s)).backgroundColor", selector);
    }

    @Test
    void theSurfaceLadderSeparatesChromeWorkspaceAndCards() {
        open("/campaigns/" + seeded.campaignId());
        assertThat(bg("body")).isEqualTo("rgb(16, 17, 19)");
        assertThat(bg(".app-main")).isEqualTo("rgb(24, 25, 29)");
        assertThat(bg(".card")).isEqualTo("rgb(36, 38, 44)");
    }

    @Test
    void focusIsVisibleAndGold() {
        open("/campaigns");
        page.keyboard().press("Tab");
        Object outline = page.evaluate("""
                () => {
                  const s = getComputedStyle(document.activeElement);
                  return { color: s.outlineColor, width: parseFloat(s.outlineWidth) };
                }
                """);
        @SuppressWarnings("unchecked")
        var o = (java.util.Map<String, Object>) outline;
        assertThat((String) o.get("color")).isEqualTo("rgb(201, 163, 92)");
        assertThat(((Number) o.get("width")).doubleValue()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    void iconsResolveFromTheSprite() {
        open("/campaigns");
        Object broken = page.evaluate("""
                () => [...document.querySelectorAll('svg.icon use')]
                        .filter(u => !u.getAttribute('href')?.includes('/icons/ui.svg#icon-'))
                        .length
                """);
        assertThat(((Number) broken).intValue()).isZero();
    }

    /**
     * Spec 7.1: Cinzel is the wordmark plus at most one principal title per view. The CSS
     * contract can only check which selector is allowed to reach for the display face; how
     * many elements carry it is a markup property and is visible only here. Today the
     * campaigns index renders one per card and campaign pages add the rail's Campaign Home
     * link — both of which spec 7.1 forbids ("Cinzel must not be used for card titles").
     *
     * <p>Ceilings are today's measured counts and may only fall: Task 13 owns the rail,
     * Task 15 the page header, Task 23 the campaign cards. The target is 1 everywhere.
     */
    /**
     * Counting elements would move with the fixture — seed one more campaign and the ceiling
     * breaks without the design changing. These are the three structural offences spec 7.1
     * actually names, so the measurement is independent of how many records exist.
     */
    @SuppressWarnings("unchecked")
    private List<String> displayTitleOffences() {
        return (List<String>) page.evaluate("""
                () => {
                  // The wordmark is the one chrome use spec 7.1 grants outright.
                  const titles = [...document.querySelectorAll('[data-display-title]')]
                        .filter(t => !t.closest('.navbar-brand, .app-brand'));
                  const main = document.querySelector('.app-main') || document.body;
                  const offences = new Set();
                  let principal = 0;
                  for (const title of titles) {
                    if (title.closest('.card')) offences.add('card-title');
                    else if (title.closest('nav, .appnav')) offences.add('nav-item');
                    else if (main.contains(title)) principal++;
                  }
                  if (principal > 1) offences.add('multiple-principal-titles');
                  return [...offences].sort();
                }
                """);
    }

    /**
     * Spec 7.1: Cinzel is the wordmark plus at most one principal title per view, and "must
     * not be used for card titles". The CSS contract can only police which selector may reach
     * for the display face; how it is applied is a markup property visible only here.
     *
     * <p>The allowances below are today's measured offences and may only shrink: Task 13 owns
     * the rail's Campaign Home link, Task 23 the campaign and library card titles. The target
     * is an empty list on every route.
     */
    @Test
    void displayTitleUseNeverGrows() {
        String c = "/campaigns/" + seeded.campaignId();
        var allowed = new java.util.LinkedHashMap<String, List<String>>();
        allowed.put("/campaigns", List.of("multiple-principal-titles"));  // Task 23
        allowed.put(c, List.of("nav-item"));                              // Task 13
        allowed.put(c + "/encounters", List.of("nav-item"));              // Task 13
        allowed.put(c + "/party", List.of("nav-item"));                   // Task 13
        allowed.put("/library", List.of());                               // already clean
        allowed.put(c + "/maps", List.of("nav-item"));                    // Task 13

        var measured = new java.util.LinkedHashMap<String, List<String>>();
        for (String route : allowed.keySet()) {
            open(route);
            measured.put(route, displayTitleOffences());
        }

        var regressions = measured.entrySet().stream()
                .filter(entry -> !allowed.get(entry.getKey()).containsAll(entry.getValue()))
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .toList();
        assertThat(regressions)
                .as("spec 7.1 display-title offences. Measured: %s", measured)
                .isEmpty();
    }

    @Test
    void captureTheFoundationReviewSet() {
        record Shot(String name, String path) {
        }
        String c = "/campaigns/" + seeded.campaignId();
        List<Shot> shots = List.of(
                new Shot("campaigns", "/campaigns"),
                new Shot("campaign-home", c),
                new Shot("encounters-index", c + "/encounters"),
                new Shot("party", c + "/party"),
                new Shot("library", "/library"),
                new Shot("maps", c + "/maps"),
                new Shot("session", c + "/session"));
        for (Shot shot : shots) {
            open(shot.path());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot.name() + ".png"))
                    .setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
