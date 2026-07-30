package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncounterDiscoverabilityBrowserTest {

    @LocalServerPort private int port;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

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

    @BeforeEach
    void openPage() {
        failures.clear();
        context = browser.newContext();
        page = guardedPage(context);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private Page guardedPage(BrowserContext browserContext) {
        Page guarded = browserContext.newPage();
        failures.attach(guarded);
        return guarded;
    }

    @Test
    void runningAnEncounterFromExplorationSurfacesTheInitiativeOrder() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:exploration");
        page.waitForFunction("() => window.cockpitLayout.current?.name === 'Exploration'");
        assertThat(page.locator(".combatant-row").count()).isZero();

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());

        page.waitForSelector("#cockpitLayoutNotice button, .combatant-row");
        if (page.locator("#cockpitLayoutNotice button").count() > 0) {
            page.locator("#cockpitLayoutNotice button").first().click();
        }
        page.waitForSelector(".combatant-row");
        assertThat(page.locator(".tracker-list").isVisible()).isTrue();
    }

    @Test
    void reRunningAFinishedEncounterTellsTheDmItIsResuming() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => window.cockpitLayout.current?.name === 'Combat'");

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", seeded.mapA().toString());

        page.evaluate("(id) => window.dmRequest(`/api/v1/encounters/${id}/end`, { method: 'POST' })",
                seeded.encounterA().toString());

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());
        page.waitForSelector("#encounterFinishedDialog[open]");
        assertThat(page.locator("#encounterFinishedDialog h2").innerText())
                .isEqualTo("This encounter has already been fought");
        assertThat(page.locator("[data-resume-finished]").isVisible()).isTrue();
        assertThat(page.locator("[data-reset-finished]").isVisible()).isTrue();
    }

    @Test
    void theCurrentScenesEncounterAppearsBeforeTheUnrelatedList() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector(".planned-encounter-row");

        // "Second Encounter" sorts last alphabetically; it comes first only because it is the
        // encounter belonging to the scene the DM is in.
        var titles = page.locator(".planned-encounter-row__title").allInnerTexts()
                .stream().map(String::trim).toList();
        assertThat(titles).containsExactly("Second Encounter", "First Encounter");
        assertThat(page.locator(".planned-encounter-section-label").first().innerText().trim())
                .as("the rail must say why that encounter is at the top")
                .isEqualToIgnoringCase("This scene");
    }

    @Test
    void endingAnEncounterReportsTheXpItWasWorth() {
        var seeded = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector(".combatant-row");

        page.evaluate("""
                async (ids) => {
                  for (const id of ids) {
                    await window.dmRequest('/api/v1/combatants/' + id + '/defeated', {
                      method: 'PUT', headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ defeated: true }),
                    });
                  }
                }
                """, seeded.memberIds().stream().map(java.util.UUID::toString).toList());

        page.click("[data-end-encounter]");
        page.click("[data-encounter-end-dialog] .btn-danger");
        page.waitForSelector(".summary-stats");

        assertThat(page.locator(".summary-stats").innerText())
                .as("four 50 XP goblins are 200 XP; concluding must say so")
                .contains("200");
    }
}
