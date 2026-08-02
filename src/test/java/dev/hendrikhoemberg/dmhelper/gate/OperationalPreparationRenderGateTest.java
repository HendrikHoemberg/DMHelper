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
class OperationalPreparationRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/operational");

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
        seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
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
    void encounterSetupControlsAreNotMicroControlsAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.branchedEncounterId() + "/setup");
        double narrowest = ((Number) page.evaluate("""
                () => Math.min(...[...document.querySelectorAll(
                        '[data-setup-column] input, [data-setup-column] select')]
                        .filter(el => el.getClientRects().length > 0)
                        .map(el => el.getBoundingClientRect().width))
                """)).doubleValue();
        assertThat(narrowest).as("narrowest setup control").isGreaterThanOrEqualTo(64.0);
    }

    /**
     * The width floor above is necessary but not sufficient, and on its own it was actively
     * misleading: .setup-field carried min-width: 0, so the label wrapper collapsed under flex
     * pressure while the input inside kept its 16rem floor. Every control still measured well
     * over 64px — and the quantity box was painted on top of the search box. Measure whether a
     * control fits the field it belongs to, not just how wide it claims to be.
     */
    @Test
    void encounterSetupControlsStayInsideTheirOwnFieldAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.branchedEncounterId() + "/setup");
        Object overflowing = page.evaluate("""
                () => [...document.querySelectorAll('.setup-field')]
                        .filter(field => field.getClientRects().length > 0)
                        .flatMap(field => [...field.querySelectorAll('input, select')]
                            .filter(el => el.getBoundingClientRect().right
                                          > field.getBoundingClientRect().right + 1)
                            .map(el => (el.name || el.className) + ' in ' + field.className))
                """);
        assertThat((java.util.List<?>) overflowing)
                .as("a setup control wider than the field it sits in overlaps its neighbour")
                .isEmpty();
    }

    @Test
    void theActiveCombatantIsDistinguishableByMoreThanColor() {
        // Drive the prepared encounter into live combat so the tracker has an active row.
        open("/campaigns/" + seeded.campaignId());
        page.evaluate("""
                async ({ base, encounterId }) => {
                    await fetch(base + '/api/v1/encounters/' + encounterId + '/activate', { method: 'POST' });
                    await fetch(base + '/api/v1/encounters/' + encounterId + '/start-combat', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ acceptUnset: true })
                    });
                }
                """, java.util.Map.of(
                "base", "http://localhost:" + port,
                "encounterId", seeded.branchedEncounterId().toString()));
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.branchedEncounterId());
        page.waitForSelector(".combatant-row--active");
        Object signals = page.evaluate("""
                () => {
                  const row = document.querySelector('.combatant-row--active');
                  if (!row) return -1;
                  const s = getComputedStyle(row);
                  let count = 0;
                  if (s.borderLeftWidth !== '0px') count++;
                  if (s.boxShadow !== 'none') count++;
                  if (s.backgroundColor !== getComputedStyle(row.parentElement).backgroundColor) count++;
                  return count;
                }
                """);
        assertThat(((Number) signals).intValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void captureTheOperationalReviewSet() {
        String c = "/campaigns/" + seeded.campaignId();
        java.util.List<String[]> shots = java.util.List.of(
                new String[]{"encounters", c + "/encounters"},
                new String[]{"encounter-detail", c + "/encounters/" + seeded.branchedEncounterId()},
                new String[]{"encounter-setup", c + "/encounters/" + seeded.branchedEncounterId() + "/setup"},
                new String[]{"party", c + "/party"},
                new String[]{"sheets", c + "/sheets"},
                new String[]{"treasury", c + "/treasury"},
                new String[]{"ledger", c + "/ledger"},
                new String[]{"maps", c + "/maps"},
                new String[]{"handouts", c + "/handouts"},
                new String[]{"audio", c + "/audio/cues"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
