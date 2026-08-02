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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@Tag("browser")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReferenceWorkspaceRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/reference");

    /** The compendium categories as the navigator exposes them, keyed by the ?tab= value. */
    private static final List<String> CATEGORY_TABS = List.of("monsters", "spells", "conditions",
            "rules", "equipment", "magic-items", "classes", "species", "backgrounds", "feats");

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        fixture.seed();
        Files.createDirectories(SHOTS);
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        failures = new BrowserFailureCollector();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
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

    private void open(String path) {
        PageReady.open(page, "http://localhost:" + port, path);
    }

    /**
     * The full-page category routes are {@code /library?tab=…}; the bare fragment routes
     * ({@code /library/spells}, …) return a card list with no shell and no title.
     */
    @Test
    void everyLibraryCategoryRendersWithItsOwnTitleAndTheSameCardGrid() {
        for (String tab : CATEGORY_TABS) {
            open("/library?tab=" + tab);
            String title = page.locator("h1").innerText();
            assertThat(title).as("title for %s", tab).isNotEqualTo("Library");
            assertThat(page.locator("[data-library-category][aria-current='page']").count())
                    .as("exactly one active category for %s", tab).isEqualTo(1);
            assertThat(page.locator(".ref-card").count())
                    .as("cards render for %s", tab).isGreaterThan(0);
        }
    }

    /** Metadata rows must sit at the same offset in every visible card of a category. */
    @Test
    void referenceCardsAlignTheirMetadataRow() {
        open("/library?tab=spells");
        Object misaligned = page.evaluate("""
                () => {
                  const cards = [...document.querySelectorAll('.ref-card')]
                      .filter(c => c.getClientRects().length > 0);
                  if (cards.length < 2) return 0;
                  const rows = cards.flatMap(c => [...c.querySelectorAll('.ref-card__meta')]);
                  if (rows.length < 2) return 0;
                  const tops = rows.map(r => Math.round(
                      r.getBoundingClientRect().top - r.closest('.ref-card').getBoundingClientRect().top));
                  return new Set(tops).size - 1;
                }
                """);
        assertThat(((Number) misaligned).intValue())
                .as("metadata rows must sit at the same offset in every card").isZero();
    }

    @Test
    void captureTheReferenceReviewSet() {
        List<String[]> shots = List.of(
                new String[]{"library-monsters", "/library"},
                new String[]{"library-spells", "/library?tab=spells"},
                new String[]{"library-magic-items", "/library?tab=magic-items"},
                new String[]{"tables", "/library/tables"},
                new String[]{"traps", "/library/traps"},
                new String[]{"hazards", "/library/hazards"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
