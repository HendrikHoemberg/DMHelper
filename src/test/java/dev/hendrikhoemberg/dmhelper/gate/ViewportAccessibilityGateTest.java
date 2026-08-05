package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.support.PageReady;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single retained appearance gate: every render surface the deleted shell, operational,
 * reference, narrative and map-editor gates covered, asserted against horizontal overflow and
 * clipped content at both supported viewports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class ViewportAccessibilityGateTest {

    private static ViewportAccessibilityGateTest INSTANCE;

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private SessionLifecycleService lifecycleService;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        INSTANCE = this;
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
        lifecycleService.start(seeded.campaignId(), seeded.playableMapId());
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

    private record Surface(String name, String path) {}

    private static final int[][] VIEWPORTS = {{1366, 768}, {1920, 1080}};

    /** Every surface the deleted render gates covered. See spec section 7.2. */
    private Stream<Surface> surfaces() {
        String c = "/campaigns/" + seeded.campaignId();
        return Stream.of(
                new Surface("campaign list", "/campaigns"),
                new Surface("campaign dashboard", c),
                new Surface("encounter setup", c + "/encounters/" + seeded.branchedEncounterId() + "/setup"),
                new Surface("cockpit", c + "/session"),
                new Surface("party", c + "/party"),
                new Surface("sheets", c + "/sheets"),
                new Surface("adventures", c + "/adventures"),
                new Surface("scene", c + "/adventures/" + seeded.adventureId()
                        + "/scenes/" + seeded.hostileSceneId()),
                new Surface("quests", c + "/quests"),
                new Surface("encounters", c + "/encounters"),
                new Surface("maps", c + "/maps"),
                new Surface("map editor", c + "/maps/" + seeded.playableMapId() + "/edit"),
                new Surface("notes", c + "/notes"),
                new Surface("handouts", c + "/handouts"),
                new Surface("ledger", c + "/ledger"),
                new Surface("treasury", c + "/treasury"),
                new Surface("calendar", c + "/calendar"),
                new Surface("audio cues", c + "/audio/cues"),
                new Surface("world locations", c + "/world/locations"),
                new Surface("world factions", c + "/world/factions"),
                new Surface("world npcs", c + "/world/npcs"),
                new Surface("library", "/library"),
                new Surface("library about", "/library/about"),
                new Surface("library tables", "/library/tables"),
                new Surface("library hazards", "/library/hazards"),
                new Surface("library traps", "/library/traps"));
    }

    @ParameterizedTest(name = "{0} has no horizontal overflow")
    @MethodSource("surfaceViewportMatrix")
    void surfaceDoesNotOverflowHorizontally(Surface surface, int width, int height) {
        page.setViewportSize(width, height);
        PageReady.open(page, "http://localhost:" + port, surface.path());

        Object overflow = page.evaluate(
                "() => document.documentElement.scrollWidth - document.documentElement.clientWidth");

        assertThat(((Number) overflow).intValue())
                .as("%s overflows horizontally at %dx%d", surface.name(), width, height)
                .isLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "{0} keeps content inside the viewport")
    @MethodSource("surfaceViewportMatrix")
    void surfaceDoesNotClipContent(Surface surface, int width, int height) {
        page.setViewportSize(width, height);
        PageReady.open(page, "http://localhost:" + port, surface.path());

        @SuppressWarnings("unchecked")
        List<String> clipped = (List<String>) page.evaluate("""
                () => {
                  const out = [];
                  for (const el of document.querySelectorAll('main *')) {
                    if (!el.textContent.trim()) continue;
                    const style = getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden') continue;
                    if (style.overflow === 'auto' || style.overflow === 'scroll') continue;
                    const box = el.getBoundingClientRect();
                    if (box.width === 0 || box.height === 0) continue;
                    if (box.right > window.innerWidth + 1) {
                      out.push((el.id || el.className || el.tagName) + ' extends past the right edge');
                    }
                  }
                  return out.slice(0, 5);
                }
                """);

        assertThat(clipped)
                .as("%s clips content at %dx%d", surface.name(), width, height)
                .isEmpty();
    }

    static Stream<Arguments> surfaceViewportMatrix() {
        // Built from surfaces() × VIEWPORTS. JUnit requires a static source, so this reads
        // the seeded ids through the shared PER_CLASS instance.
        return INSTANCE.surfaces().flatMap(surface ->
                Stream.of(VIEWPORTS).map(v -> Arguments.of(surface, v[0], v[1])));
    }
}
