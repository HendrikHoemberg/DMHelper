package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** The encounter module must stay usable at rail width, not only when focused. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class CockpitModuleFitTest {

    @LocalServerPort int port;
    @Autowired ReleaseRehearsalFixture fixture;

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;
    ReleaseRehearsalFixture.Seeded seeded;

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    final BrowserFailureCollector failures = new BrowserFailureCollector();

    @BeforeEach
    void openPage() throws Exception {
        seeded = fixture.seedForRehearsal(ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP);
        failures.clear();
        // The minimum supported viewport, not a comfortable one: this test exists to prove
        // the rail holds at the size a DM's laptop actually has.
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
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

    @Test
    void initiativeSetupFitsTheEncounterRail() {
        // Start the session.
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("a[href$='/session'], button[data-run-session]").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
        page.locator("button[x-ref='sessionButton']").click();
        Locator lifecycle = page.locator("#sessionLifecycleDialog");
        lifecycle.waitFor();
        page.waitForResponse(response -> response.url().endsWith("/session/start") && response.status() == 200,
                () -> lifecycle.locator("button").filter(new Locator.FilterOptions().setHasText("Start")).first().click());
        page.waitForFunction("() => performance.getEntriesByType('navigation')[0]?.type === 'reload'");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("() => document.readyState === 'complete' && window.cockpitLayout?.mounted === true");
        page.waitForFunction("() => document.querySelector('[data-session-status]')?.dataset.sessionStatus === 'RUNNING'");

        // Navigate from the current (approach) scene to the hostile scene via a transition.
        String before = page.textContent("[data-runtime-module='story'] [data-current-scene]");
        page.locator("[data-runtime-module='story'] [data-scene-transition]").first().click();
        page.waitForFunction(
            "previous => document.querySelector(\"[data-runtime-module='story'] [data-current-scene]\")?.textContent !== previous",
            before);

        // Seed and activate the encounter, then apply the combat preset so the initiative-setup
        // panel renders inside the ~370 px rail.
        page.locator("[data-runtime-module='story'] [data-seed-scene-encounter]").first().click();
        page.evaluate("() => window.cockpitLayout.applyPreset('builtin:combat', { skipDirtyCheck: true })");
        page.waitForFunction("() => document.querySelector('#cockpitPresetPicker')?.value === 'builtin:combat'");
        page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");
        page.waitForFunction(
            "() => document.querySelectorAll('[data-runtime-module=\"encounter\"] input[data-initiative-input]').length > 0");

        // Nothing inside the rail may spill outside it.
        Object fit = page.evaluate("""
                () => {
                  const panel = document.querySelector("[data-runtime-module='encounter'] [data-initiative-setup]");
                  const overflow = panel.scrollWidth - panel.clientWidth;
                  const input = panel.querySelector('input[data-initiative-input]');
                  const roll = panel.querySelector('button[data-roll-unset-initiative]');
                  const inside = el => {
                    if (!el) return false;
                    const r = el.getBoundingClientRect();
                    const p = panel.getBoundingClientRect();
                    return r.left >= p.left - 1 && r.right <= p.right + 1;
                  };
                  const primary = Array.from(panel.querySelectorAll('[data-initiative-primary]'));
                  return {
                    overflow,
                    inputInside: inside(input),
                    rollInside: inside(roll),
                    primaryCount: primary.length,
                    primaryInside: primary.every(inside)
                  };
                }
                """);
        assertThat(fit.toString())
                .as("the initiative panel and its controls stay inside the rail")
                .isEqualTo("{overflow=0, inputInside=true, rollInside=true, "
                        + "primaryCount=2, primaryInside=true}");
    }

    @Test
    void activatingAnEncounterShowsItsMap() {
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("a[href$='/session'], button[data-run-session]").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
        page.locator("button[x-ref='sessionButton']").click();
        Locator lifecycle = page.locator("#sessionLifecycleDialog");
        lifecycle.waitFor();
        page.waitForResponse(response -> response.url().endsWith("/session/start") && response.status() == 200,
                () -> lifecycle.locator("button").filter(new Locator.FilterOptions().setHasText("Start")).first().click());
        page.waitForFunction("() => performance.getEntriesByType('navigation')[0]?.type === 'reload'");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("() => document.readyState === 'complete' && window.cockpitLayout?.mounted === true");
        page.waitForFunction("() => document.querySelector('[data-session-status]')?.dataset.sessionStatus === 'RUNNING'");

        page.evaluate("() => window.cockpitLayout.applyPreset('builtin:combat', { skipDirtyCheck: true })");
        page.waitForFunction("() => document.querySelector('#cockpitPresetPicker')?.value === 'builtin:combat'");

        page.locator(".planned-encounter-row button").first().click();
        page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");

        page.waitForFunction(
                "() => !document.querySelector(\"[data-runtime-module='map']\")?.innerText.includes('No map selected')");
        assertThat(page.locator("[data-runtime-module='map']").innerText())
                .as("the activated encounter's map is displayed")
                .doesNotContain("No map selected");
    }
}
