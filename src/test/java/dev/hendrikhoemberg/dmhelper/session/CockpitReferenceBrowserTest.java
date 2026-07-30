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
class CockpitReferenceBrowserTest {

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

    private void openCombat() {
        var grouped = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + grouped.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
    }

    @Test
    void everySearchResultRowHasVisibleText() {
        openCombat();
        page.click("[data-module-tab='reference']");
        page.waitForSelector(".reference-search-form input");
        page.fill(".reference-search-form input", "Gob");
        page.waitForSelector("[data-reference-result]");

        var rows = page.locator("[data-reference-result]");
        assertThat(rows.count()).isGreaterThan(0);

        for (int i = 0; i < rows.count(); i++) {
            assertThat(rows.nth(i).innerText()).isNotEmpty();
        }

        assertThat(rows.first().locator(".reference-item__title").innerText()).isEqualTo("Goblin");
    }

    @Test
    void openingAStatblockLeavesTheInitiativeOrderVisible() {
        openCombat();
        assertThat(page.locator(".tracker-list").boundingBox()).isNotNull();
        page.click("[data-module-tab='reference']");
        page.waitForSelector(".reference-search-form input");
        page.fill(".reference-search-form input", "Gob");
        page.waitForSelector("[data-reference-result]");
        page.locator("[data-reference-result]").first().click();
        page.waitForSelector(".statblock-render");
        assertThat(page.locator(".combatant-row").first().isVisible()).isTrue();
    }

    @Test
    void theDiceRollerDocksBesideTheTrackerNotOverIt() {
        openCombat();
        page.evaluate("() => window.dispatchEvent(new CustomEvent('dice-roller-toggle'))");
        page.waitForSelector(".dice-panel:not(.closed)");
        var dice = page.locator(".dice-panel").boundingBox();
        var tracker = page.locator(".tracker-list").boundingBox();
        assertThat(dice).isNotNull();
        assertThat(tracker).isNotNull();
        assertThat(dice.x + dice.width).isLessThanOrEqualTo(tracker.x + 1);
        assertThat(page.locator(".dice-panel-close").getAttribute("aria-label"))
                .isEqualTo("Close dice roller");
    }

    @Test
    void thePausedSessionDialogSaysPaused() {
        openCombat();
        page.evaluate("() => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".pauseSession()");
        page.waitForFunction("() => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".sessionStatus === 'PAUSED'");
        page.evaluate("() => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".openLifecycle()");
        page.waitForSelector("#sessionLifecycleDialog");
        String heading = page.locator("[data-lifecycle-heading]").all().stream()
                .filter(l -> l.isVisible()).findFirst().orElseThrow().innerText().trim();
        assertThat(heading).isEqualTo("Session Paused");
    }

    @Test
    void theInRunStatblockShowsTheCreaturesActions() {
        openCombat();
        page.click("[data-module-tab='reference']");
        page.waitForSelector(".reference-search-form input");
        page.fill(".reference-search-form input", "Gob");
        page.waitForSelector("[data-reference-result]");
        page.locator("[data-reference-result]").first().click();
        page.waitForSelector(".statblock-render");
        assertThat(page.locator(".statblock-render").innerText())
                .contains("Speed").contains("Actions");
    }
}
