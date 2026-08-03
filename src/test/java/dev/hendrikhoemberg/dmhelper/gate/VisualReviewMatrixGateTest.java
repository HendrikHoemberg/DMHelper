package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
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
@Tag("browser")
class VisualReviewMatrixGateTest {

    // Each test owns a subtree, so the three capture tests cannot corrupt each other's
    // counts. JUnit does not guarantee method order, and a shared parent directory made the
    // "exactly N entries" assertion depend on which test ran first.
    private static final Path MATRIX = Path.of("target/ui-redesign/matrix");
    private static final Path POPULATED = MATRIX.resolve("populated");
    private static final Path EMPTY = MATRIX.resolve("empty");
    private static final Path OVERLAYS = MATRIX.resolve("overlays");

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    private record Surface(String name, String path) {}

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
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

    private List<Surface> surfaces() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of(
                new Surface("campaign-selection", "/campaigns"),
                new Surface("campaign-home", c),
                new Surface("adventure-index", c + "/adventures"),
                new Surface("adventure-detail", c + "/adventures/" + seeded.adventureId()),
                new Surface("scene-detail", c + "/adventures/" + seeded.adventureId()
                        + "/scenes/" + seeded.hostileSceneId()),
                new Surface("quests", c + "/quests"),
                new Surface("npcs", c + "/world/npcs"),
                new Surface("locations", c + "/world/locations"),
                new Surface("factions", c + "/world/factions"),
                new Surface("notes", c + "/notes"),
                new Surface("calendar", c + "/calendar"),
                new Surface("encounter-index", c + "/encounters"),
                new Surface("encounter-detail", c + "/encounters/" + seeded.branchedEncounterId()),
                new Surface("encounter-setup", c + "/encounters/" + seeded.branchedEncounterId() + "/setup"),
                new Surface("party", c + "/party"),
                new Surface("sheets", c + "/sheets"),
                new Surface("treasury", c + "/treasury"),
                new Surface("ledger", c + "/ledger"),
                new Surface("handouts", c + "/handouts"),
                new Surface("audio", c + "/audio/cues"),
                new Surface("maps-index", c + "/maps"),
                new Surface("map-editor", c + "/maps/" + seeded.playableMapId() + "/edit"),
                new Surface("library-monsters", "/library"),
                new Surface("library-spells", "/library/spells"),
                new Surface("library-conditions", "/library/conditions"),
                new Surface("library-rules", "/library/rules"),
                new Surface("library-equipment", "/library/equipment"),
                new Surface("library-magic-items", "/library/magic-items"),
                new Surface("library-classes", "/library/classes"),
                new Surface("library-species", "/library/species"),
                new Surface("library-backgrounds", "/library/backgrounds"),
                new Surface("library-feats", "/library/feats"),
                new Surface("tables", "/library/tables"),
                new Surface("traps", "/library/traps"),
                new Surface("hazards", "/library/hazards"),
                new Surface("campaign-settings", c + "/settings"),
                new Surface("about", "/library/about"),
                new Surface("session-cockpit", c + "/session"));
    }

    @Test
    void capturePopulatedStatesForEverySurface() throws Exception {
        for (Surface surface : surfaces()) {
            Path dir = POPULATED.resolve(surface.name());
            Files.createDirectories(dir);
            page.navigate("http://localhost:" + port + surface.path());
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("populated.png")).setFullPage(true));
        }
        assertThat(POPULATED.toFile().listFiles()).hasSize(surfaces().size());
    }

    @Test
    void captureEmptyStatesFromTheUnseededCampaign() throws Exception {
        String empty = "/campaigns/" + seeded.emptyCampaignId();
        for (String surface : List.of("/adventures", "/encounters", "/maps", "/handouts",
                "/audio/cues", "/notes", "/party", "/treasury", "/ledger", "/quests",
                "/world/npcs", "/world/locations", "/world/factions")) {
            Path dir = EMPTY.resolve(surface.substring(1).replace('/', '-'));
            Files.createDirectories(dir);
            page.navigate("http://localhost:" + port + empty + surface);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            assertThat(page.locator(".state--empty").count()).as("empty state on %s", surface).isEqualTo(1);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("empty.png")).setFullPage(true));
        }
    }

    @Test
    void captureOverlayAndTransientSurfaces() throws Exception {
        Path dir = OVERLAYS;
        Files.createDirectories(dir);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.keyboard().press("Control+k");
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("command-palette.png")));
        page.keyboard().press("Escape");
        // The palette's leave transition keeps it a visible modal for a few frames; the
        // global shortcut manager swallows shortcuts while any modal is visible, so wait
        // for the close to finish before the next shortcut (Ctrl+R below).
        page.locator(".command-palette-overlay").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
        page.keyboard().press("Control+r");
        page.locator("#diceRoller").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("dice-roller.png")));
        page.keyboard().press("Escape");
        page.evaluate("() => window.dmToast.show('Saved', 'success')");
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("toast.png")));
        assertThat(dir.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(3);
    }
}
