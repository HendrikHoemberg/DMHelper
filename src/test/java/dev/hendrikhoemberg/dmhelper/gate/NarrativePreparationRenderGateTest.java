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
class NarrativePreparationRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/narrative");

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
    void campaignHomeShowsCurrentStateAboveTheFold() {
        open("/campaigns/" + seeded.campaignId());
        double y = ((Number) page.evaluate(
                "() => document.querySelector('[data-home-section=\"start\"]')"
                        + ".getBoundingClientRect().bottom")).doubleValue();
        assertThat(y).as("Start/Resume must be visible without scrolling").isLessThan(720);
    }

    @Test
    void sceneNarrativeStaysWithinAReadableMeasure() {
        open("/campaigns/" + seeded.campaignId() + "/adventures/" + seeded.adventureId()
                + "/scenes/" + seeded.hostileSceneId());
        double width = ((Number) page.evaluate(
                "() => document.querySelector('.prose').getBoundingClientRect().width"))
                .doubleValue();
        double fontSize = ((Number) page.evaluate(
                "() => parseFloat(getComputedStyle(document.querySelector('.prose')).fontSize)"))
                .doubleValue();
        assertThat(width / fontSize).as("characters per line ≈ width / font-size / 0.5")
                .isBetween(60.0 * 0.5, 90.0 * 0.5);
    }

    @Test
    void captureTheNarrativeReviewSet() {
        String c = "/campaigns/" + seeded.campaignId();
        List<String[]> shots = List.of(
                new String[]{"campaigns", "/campaigns"},
                new String[]{"campaign-home", c},
                new String[]{"adventures", c + "/adventures"},
                new String[]{"adventure-detail", c + "/adventures/" + seeded.adventureId()},
                new String[]{"quests", c + "/quests"},
                new String[]{"npcs", c + "/world/npcs"},
                new String[]{"notes", c + "/notes"},
                new String[]{"calendar", c + "/calendar"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
