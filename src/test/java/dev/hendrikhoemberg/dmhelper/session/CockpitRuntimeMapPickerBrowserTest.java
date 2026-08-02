package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import org.junit.jupiter.api.Tag;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class CockpitRuntimeMapPickerBrowserTest {

    @LocalServerPort private int port;
    @Autowired private CockpitInitialLoadFixtures fixtures;
    @Autowired private GameMapRepository maps;

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
    void workspaceMapPickerSelectsTheMapWhenMapListArrivesBeforeTheFragment() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        GameMap workspaceMap = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();

        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.waitForFunction(
                "(mapId) => window.Alpine.$data(document.querySelector('.session-cockpit')).maps"
                        + ".some(map => map.id === mapId)",
                workspaceMap.getId().toString());
        page.evaluate("""
                () => {
                  const originalRequest = window.dmRequest;
                  window.dmRequest = (url, options) => {
                    if (String(url).includes('/session/modules/map')) {
                      return new Promise((resolve, reject) => {
                        window.releaseRuntimeMapModuleRequest = () =>
                          originalRequest(url, options).then(resolve, reject);
                      });
                    }
                    return originalRequest(url, options);
                  };
                }
                """);
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => typeof window.releaseRuntimeMapModuleRequest === 'function'");

        assertThat(page.locator("#runtimeMapPicker").count())
                .as("the map list must arrive while the lazy Map fragment is still pending")
                .isZero();

        page.evaluate("() => window.releaseRuntimeMapModuleRequest()");
        waitForPicker(workspaceMap);
        assertPicker(workspaceMap);
    }

    @Test
    void workspaceMapPickerSelectsTheMapWhenFragmentArrivesBeforeTheMapList() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        GameMap workspaceMap = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();
        AtomicReference<Route> heldMapList = new AtomicReference<>();
        page.route("**/api/v1/campaigns/*/maps", heldMapList::set);

        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction(
                "() => document.querySelector('[data-runtime-module=\"map\"] "
                        + "[data-module-content]')?.getAttribute('data-module-loaded') === 'true'");

        assertThat(heldMapList.get())
                .as("the fixture must still be holding the map-list response")
                .isNotNull();
        assertThat(page.locator("#runtimeMapPicker").inputValue()).isEmpty();
        assertThat(page.locator("#runtimeMapPicker option").allTextContents())
                .containsExactly("No map");

        heldMapList.get().resume();
        waitForPicker(workspaceMap);
        assertPicker(workspaceMap);
    }

    private void waitForPicker(GameMap workspaceMap) {
        page.waitForFunction(
                "(mapId) => Array.from(document.querySelectorAll('#runtimeMapPicker option'))"
                        + ".some(option => option.value === mapId)",
                workspaceMap.getId().toString());
    }

    private void assertPicker(GameMap workspaceMap) {
        assertThat(page.evaluate(
                "() => window.Alpine.$data(document.querySelector('.session-cockpit')).currentMapId"))
                .as("cockpit state must retain the server-selected workspace map")
                .isEqualTo(workspaceMap.getId().toString());
        assertThat(page.locator("#runtimeMapPicker").inputValue())
                .isEqualTo(workspaceMap.getId().toString());
        assertThat(page.locator("#runtimeMapPicker option:checked").innerText())
                .isEqualTo("Fixture Map");
        assertThat(page.locator("#runtimeMapPicker option").allTextContents())
                .containsAll(List.of("No map", "Fixture Map"));
    }
}
