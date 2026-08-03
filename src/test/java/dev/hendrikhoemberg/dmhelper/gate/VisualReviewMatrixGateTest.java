package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
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
import java.util.regex.Pattern;

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
    private static final Path ULTRAWIDE = MATRIX.resolve("ultrawide");
    private static final Path EMPTY = MATRIX.resolve("empty");
    private static final Path DEGRADED = MATRIX.resolve("degraded");
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

    private void open(String path) {
        PageReady.open(page, "http://localhost:" + port, path);
    }

    /**
     * Start each subtree empty. The "exactly N entries" assertions are the only thing keeping
     * a surface from silently dropping out of the matrix, and without this a renamed or
     * removed surface leaves its old directory behind and inflates the count on every
     * subsequent run that did not begin with {@code clean}.
     */
    private static Path freshSubtree(Path root) throws Exception {
        if (Files.exists(root)) {
            try (var entries = Files.walk(root)) {
                entries.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
        }
        Files.createDirectories(root);
        return root;
    }

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
                // Spec 20.3's "scene detail/edit": /scenes/{id}/edit is an htmx fragment
                // endpoint that 500s when navigated to directly. /structure is the editing
                // page the Edit action on scene detail actually opens.
                new Surface("scene-structure", c + "/adventures/" + seeded.adventureId()
                        + "/scenes/" + seeded.hostileSceneId() + "/structure"),
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
        freshSubtree(POPULATED);
        for (Surface surface : surfaces()) {
            Path dir = POPULATED.resolve(surface.name());
            Files.createDirectories(dir);
            open(surface.path());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve("populated.png")).setFullPage(true));
        }
        assertThat(POPULATED.toFile().listFiles()).hasSize(surfaces().size());
    }

    @Test
    void captureEmptyStatesFromTheUnseededCampaign() throws Exception {
        freshSubtree(EMPTY);
        String empty = "/campaigns/" + seeded.emptyCampaignId();
        for (String surface : List.of("/adventures", "/encounters", "/maps", "/handouts",
                "/audio/cues", "/notes", "/party", "/treasury", "/ledger", "/quests",
                "/world/npcs", "/world/locations", "/world/factions")) {
            Path dir = EMPTY.resolve(surface.substring(1).replace('/', '-'));
            Files.createDirectories(dir);
            open(empty + surface);
            assertThat(page.locator(".state--empty").count()).as("empty state on %s", surface).isEqualTo(1);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("empty.png")).setFullPage(true));
        }
    }

    /**
     * Acceptance criterion 8 — "standard pages use ultrawide space intentionally" — is a
     * human judgement call made against this directory, so the directory has to contain an
     * ultrawide capture. It previously held nothing above 1440px wide, which made the
     * criterion unjudgeable from its own recorded evidence. The two viewport-owning
     * workspaces are excluded: they are gated on geometry, not on margin discipline.
     */
    @Test
    void captureTheUltrawideReviewSet() throws Exception {
        freshSubtree(ULTRAWIDE);
        page.setViewportSize(2560, 1440);
        List<Surface> standard = surfaces().stream()
                .filter(surface -> !surface.name().equals("map-editor"))
                .filter(surface -> !surface.name().equals("session-cockpit"))
                .toList();
        for (Surface surface : standard) {
            Path dir = ULTRAWIDE.resolve(surface.name());
            Files.createDirectories(dir);
            open(surface.path());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve("ultrawide.png")).setFullPage(true));
        }
        assertThat(ULTRAWIDE.toFile().listFiles()).hasSize(standard.size());
    }

    /**
     * Spec 20.3 asks the matrix to cover error and unavailable states too, not only populated
     * and empty ones. These are the two the product can be driven into deterministically: the
     * error page, and the presentation surface with no handout behind it.
     */
    @Test
    void captureDegradedAndErrorStates() throws Exception {
        freshSubtree(DEGRADED);

        String missing = "/campaigns/" + seeded.campaignId() + "/no-such-page";
        failures.expectHttpFailure("GET", Pattern.compile(".*" + Pattern.quote(missing)), 404);
        open(missing);
        assertThat(page.locator("[data-error-action]").count())
                .as("spec 11.10: the error page names the failed action")
                .isGreaterThan(0);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(DEGRADED.resolve("error-page.png")).setFullPage(true));

        open("/campaigns/" + seeded.campaignId() + "/handouts/"
                + java.util.UUID.randomUUID() + "/present");
        assertThat(page.locator("[data-presentation-state]").getAttribute("data-presentation-state"))
                .as("a handout that is gone renders the overlay's own unavailable state")
                .isEqualTo("unavailable");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(DEGRADED.resolve("presentation-unavailable.png")));

        assertThat(DEGRADED.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void captureOverlayAndTransientSurfaces() throws Exception {
        Path dir = freshSubtree(OVERLAYS);
        open("/campaigns/" + seeded.campaignId());
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

        // Spec 20.3 lists dialogs alongside the palette and the roller. The confirmation is
        // the dialog a DM meets most often, and the presentation overlay is the product's
        // one full-viewport surface.
        open("/campaigns/" + seeded.campaignId() + "/maps");
        page.locator("[hx-confirm]").first().click();
        page.locator("#confirmDialog").waitFor();
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("confirm-dialog.png")));
        page.keyboard().press("Escape");

        open("/campaigns/" + seeded.campaignId() + "/handouts");
        page.locator("[hx-get$='/present']").first().click();
        page.locator("#handoutOverlay").waitFor();
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("presentation.png")));
        page.keyboard().press("Escape");

        assertThat(dir.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(5);
    }
}
