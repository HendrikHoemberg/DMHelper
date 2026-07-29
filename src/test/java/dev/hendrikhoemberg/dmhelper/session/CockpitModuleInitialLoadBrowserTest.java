package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CockpitModuleInitialLoadBrowserTest {

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
    void mapAndEncounterModulesLoadOnFirstPaintWhenCombatPresetIsRestored() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        String url = "http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session";

        // 1. Arrive, switch to Combat so the preset is persisted to localStorage.
        page.navigate(url);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => window.cockpitLayout.currentPresetKey === 'builtin:combat'");

        // 2. Reload. This is the DM reopening the cockpit at the table.
        List<String> moduleRequests = new ArrayList<>();
        page.onRequest(request -> {
            if (request.url().contains("/session/modules/")) moduleRequests.add(request.url());
        });
        page.reload();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");

        page.waitForFunction(
                "() => document.querySelector('[data-runtime-module=\"map\"] "
                        + "[data-module-content]')?.getAttribute('data-module-loaded') === 'true'");
        page.waitForFunction(
                "() => document.querySelector('[data-runtime-module=\"encounter\"] "
                        + "[data-module-content]')?.getAttribute('data-module-loaded') === 'true'");

        assertThat(moduleRequests)
                .as("visible Combat modules must fetch on first paint, not after a preset toggle")
                .anyMatch(u -> u.contains("/session/modules/map"))
                .anyMatch(u -> u.contains("/session/modules/encounter"))
                .anyMatch(u -> u.contains("/session/modules/story") && u.contains("mode=COMPACT"));

        int mapBodyLength = (int) page.evaluate(
                "() => document.querySelector('[data-runtime-module=\"map\"] "
                        + ".cockpit-module__body').innerText.trim().length");
        int encounterBodyLength = (int) page.evaluate(
                "() => document.querySelector('[data-runtime-module=\"encounter\"] "
                        + ".cockpit-module__body').innerText.trim().length");
        assertThat(mapBodyLength).as("map module body").isGreaterThan(0);
        assertThat(encounterBodyLength).as("encounter module body").isGreaterThan(0);
        assertThat(page.locator("[data-runtime-module='story'] .runtime-story--compact").count())
                .as("restored Combat Story body must match the preset's COMPACT mode")
                .isEqualTo(1);

        // Party is an inactive left-rail tab in Combat. It may stay queued until selected,
        // but the stale STANDARD body must never be shown as if it were mode-correct.
        List<Route> heldPartyRequests = new CopyOnWriteArrayList<>();
        AtomicBoolean failureInjected = new AtomicBoolean();
        failures.expectHttpFailure(
                "GET", Pattern.compile(".*/session/modules/party\\?.*"), 503);
        page.route("**/session/modules/party**", route -> {
            if (failureInjected.get()) {
                route.resume();
            } else {
                heldPartyRequests.add(route);
            }
        });
        page.locator("[data-module-tab='party']").click();
        page.waitForFunction(
                "() => !document.querySelector("
                        + "'[data-runtime-module=\"party\"] [data-module-status]').hidden");

        assertThat(page.locator(
                "[data-runtime-module='party'] .cockpit-module__body").isHidden())
                .as("a stale STANDARD body must stay hidden while COMPACT is loading")
                .isTrue();
        assertThat(page.locator(
                "[data-runtime-module='party'] [data-module-status]").isVisible())
                .as("loading chrome must remain visible outside the hidden stale body")
                .isTrue();

        page.waitForCondition(() -> heldPartyRequests.size() >= 2);
        List<Route> requestSnapshot = List.copyOf(heldPartyRequests);
        failureInjected.set(true);
        requestSnapshot.subList(0, requestSnapshot.size() - 1)
                .forEach(route -> route.abort("aborted"));
        requestSnapshot.getLast().fulfill(new Route.FulfillOptions()
                .setStatus(503)
                .setContentType("text/plain")
                .setBody("Fixture party load failure"));
        page.waitForSelector("[data-runtime-module='party'] [data-module-error]:not([hidden])");
        assertThat(page.locator(
                "[data-runtime-module='party'] .cockpit-module__body").isHidden())
                .as("a stale STANDARD body must remain hidden after COMPACT fails")
                .isTrue();
        assertThat(page.locator(
                "[data-runtime-module='party'] [data-module-error]").isVisible())
                .as("error chrome must remain available outside the hidden stale body")
                .isTrue();

        page.locator("[data-runtime-module='party'] [data-module-retry]").click();
        page.waitForSelector("[data-runtime-module='party'] .runtime-party--compact");
        assertThat(moduleRequests)
                .as("an inactive server-rendered body must refetch before first use")
                .anyMatch(u -> u.contains("/session/modules/party") && u.contains("mode=COMPACT"));
        assertThat(page.locator("[data-runtime-module='party'] .runtime-party--compact").count())
                .as("restored Combat Party body must match the preset's COMPACT mode")
                .isEqualTo(1);
    }
}
