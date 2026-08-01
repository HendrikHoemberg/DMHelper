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
