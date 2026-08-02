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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@Tag("browser")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MapEditorRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/map-editor");

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

    @Test
    void nothingEssentialClipsAtTheMinimumViewport() {
        for (int[] viewport : new int[][]{{1280, 720}, {1440, 900}, {1920, 1080}}) {
            page.setViewportSize(viewport[0], viewport[1]);
            open("/campaigns/" + seeded.campaignId() + "/maps/" + seeded.playableMapId() + "/edit");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> geometry = (java.util.Map<String, Object>) page.evaluate("""
                    () => {
                      const clipped = [...document.querySelectorAll(
                          '.mapedit__commandbar [data-command], .mapedit__rail [data-tool]')]
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.width === 0 || r.height === 0
                              || r.right > innerWidth + 1 || r.bottom > innerHeight + 1;
                        }).map(el => el.dataset.command ?? el.dataset.tool);
                      return {
                        clipped,
                        docOverflow: document.documentElement.scrollHeight
                                   - document.documentElement.clientHeight
                      };
                    }
                    """);
            assertThat((java.util.List<?>) geometry.get("clipped"))
                    .as("clipped controls at %dx%d", viewport[0], viewport[1]).isEmpty();
            assertThat(((Number) geometry.get("docOverflow")).intValue())
                    .as("document scroll at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void captureTheEditorReviewSet() {
        for (int[] viewport : new int[][]{{1280, 720}, {1920, 1080}}) {
            page.setViewportSize(viewport[0], viewport[1]);
            open("/campaigns/" + seeded.campaignId() + "/maps/" + seeded.playableMapId() + "/edit");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve("editor-" + viewport[0] + ".png")));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(2);
    }
}
