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

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keyboard operation across the whole product: every focusable control paints a visible
 * focus indicator, icon-only controls carry accessible names, and every reviewed page has a
 * logical landmark and heading structure. Pages reviewed are the standard set plus the two
 * viewport-owned workspaces (map editor and session cockpit).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class KeyboardOperationGateTest {

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

    private void open(String path) {
        page.setViewportSize(1440, 900);
        page.navigate("http://localhost:" + port + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    @Test
    void everyReviewedPageIsFullyKeyboardReachableWithVisibleFocus() {
        StringBuilder report = new StringBuilder();
        for (String path : reviewedPages()) {
            open(path);
            Object invisible = page.evaluate("""
                    () => {
                      const focusable = [...document.querySelectorAll(
                        'button:not([disabled]), [href], input:not([disabled]), '
                        + 'select:not([disabled]), textarea:not([disabled]), '
                        + '[tabindex]:not([tabindex="-1"])')]
                        .filter(el => el.offsetParent !== null);
                      const bad = [];
                      for (const el of focusable) {
                        el.focus();
                        const s = getComputedStyle(el);
                        if (s.outlineStyle === 'none' && s.boxShadow === 'none') {
                          bad.push(el.tagName.toLowerCase() + '.' + (el.className || '')
                              + ' @ ' + (el.outerHTML || '').slice(0, 80));
                        }
                      }
                      return bad;
                    }
                    """);
            @SuppressWarnings("unchecked")
            List<String> badList = (List<String>) invisible;
            if (!badList.isEmpty()) {
                report.append(path).append(" -> ").append(badList).append('\n');
            }
        }
        assertThat(report.toString()).as("controls without a visible focus indicator").isEmpty();
    }

    @Test
    void everyIconOnlyControlHasAnAccessibleName() {
        StringBuilder report = new StringBuilder();
        for (String path : reviewedPages()) {
            open(path);
            Object unnamed = page.evaluate("""
                    () => [...document.querySelectorAll('button, a')]
                            .filter(el => el.offsetParent !== null)
                            .filter(el => !el.textContent.trim()
                                       && !el.getAttribute('aria-label')
                                       && !el.getAttribute('aria-labelledby')
                                       && !el.getAttribute('title'))
                            .map(el => el.tagName.toLowerCase() + '.' + (el.className || '')
                                    + ' @ ' + (el.outerHTML || '').slice(0, 60))
                            .slice(0, 10)
                    """);
            @SuppressWarnings("unchecked")
            List<String> unnamedList = (List<String>) unnamed;
            if (!unnamedList.isEmpty()) {
                report.append(path).append(" -> ").append(unnamedList).append('\n');
            }
        }
        assertThat(report.toString()).as("unnamed icon-only controls").isEmpty();
    }

    @Test
    void landmarksAndHeadingOrderAreLogical() {
        StringBuilder report = new StringBuilder();
        for (String path : reviewedPages()) {
            open(path);
            @SuppressWarnings("unchecked")
            Map<String, Object> structure = (Map<String, Object>) page.evaluate("""
                    () => {
                      const levels = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')]
                        .map(h => Number(h.tagName[1]));
                      let skips = 0;
                      for (let i = 1; i < levels.length; i++) {
                        if (levels[i] - levels[i - 1] > 1) skips++;
                      }
                      return {
                        main: document.querySelectorAll('main').length,
                        nav: document.querySelectorAll('nav').length,
                        h1: levels.filter(l => l === 1).length,
                        skips,
                        levels
                      };
                    }
                    """);
            long main = ((Number) structure.get("main")).intValue();
            long nav = ((Number) structure.get("nav")).intValue();
            long h1 = ((Number) structure.get("h1")).intValue();
            long skips = ((Number) structure.get("skips")).intValue();
            if (main != 1 || nav < 1 || h1 != 1 || skips != 0) {
                report.append(path).append(" main=").append(main).append(" nav=").append(nav)
                        .append(" h1=").append(h1).append(" skips=").append(skips)
                        .append(" levels=").append(structure.get("levels")).append('\n');
            }
        }
        assertThat(report.toString()).as("landmark/heading structure").isEmpty();
    }
}
