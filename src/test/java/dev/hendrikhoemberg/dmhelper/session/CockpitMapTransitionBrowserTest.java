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
class CockpitMapTransitionBrowserTest {

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
    void battleMapSurvivesModuleReRender() {
        CockpitInitialLoadFixtures.TwoEncounters two = fixtures.campaignWithTwoEncountersOnTwoMaps();

        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + two.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");

        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => window.battleMap != null");
        page.waitForFunction("() => document.querySelectorAll('#battleCanvasWrap canvas').length > 0");

        page.evaluate("(mapId) => window.cockpitModules.load('map', { force: true, mapId })", two.mapA().toString());

        page.waitForFunction("() => document.querySelectorAll('#battleCanvasWrap canvas').length > 0");
        assertThat(page.locator("#battleCanvasWrap canvas").count()).isGreaterThan(0);

        boolean reattached = (boolean) page.evaluate("""
                () => {
                    const wrap = document.getElementById('battleCanvasWrap');
                    return wrap != null && wrap.contains(window.battleMap.stage.container());
                }
                """);
        assertThat(reattached).as("Konva stage container must be a child of #battleCanvasWrap after re-render").isTrue();
    }

    @Test
    void activatingAnEncounterOnAnotherMapMovesTheWholeMapSurface() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", seeded.mapA().toString());

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".activateEncounter(id, 'SUSPEND')", seeded.encounterB().toString());
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", seeded.mapB().toString());
        page.waitForSelector("#battleCanvasWrap canvas");

        assertThat(page.evaluate("() => window.battleMap.activeEncounterId"))
                .as("the map must know which encounter it is showing")
                .isEqualTo(seeded.encounterB().toString());
        assertThat(page.locator("#runtimeMapPicker").inputValue())
                .as("the picker must follow the transition")
                .isEqualTo(seeded.mapB().toString());

        var participants = page.locator(".battle-sidebar .token-list-item").allInnerTexts();
        assertThat(participants)
                .as("the participants list must describe encounter B, not the encounter before it")
                .isNotEmpty()
                .allSatisfy(text -> assertThat(text).contains("Hobgoblin"));
        assertThat(participants)
                .noneSatisfy(text -> assertThat(text).contains("Goblin 1"));

        assertThat(page.locator(".toast-error").count())
                .as("a coherent transition produces no error toast")
                .isZero();
    }

    @Test
    void aFailedMapLoadRecoversFromTheModulesOwnRetry() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");
        page.waitForSelector("#battleCanvasWrap canvas");

        failures.expectConsoleError(java.util.regex.Pattern.compile("runtime-tokens"));
        page.evaluate("""
                () => {
                  window.__navigations = 0;
                  const push = history.pushState.bind(history);
                  history.pushState = (...args) => { window.__navigations++; return push(...args); };
                  const original = window.dmRequest;
                  let failed = false;
                  window.dmRequest = (url, options) => {
                    if (!failed && String(url).includes('/runtime-tokens')) {
                      failed = true;
                      return Promise.reject(new window.DmRequestError('boom', 503, null));
                    }
                    return original(url, options);
                  };
                }
                """);

        String beforeMapId = seeded.mapA().toString();
        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id).catch(() => {})", seeded.encounterA().toString());
        page.waitForSelector("[data-runtime-module='map'] [data-module-retry]");

        page.click("[data-runtime-module='map'] [data-module-retry]");
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", beforeMapId);
        page.waitForSelector("#battleCanvasWrap canvas");

        assertThat(page.evaluate("() => window.__navigations")).isEqualTo(0);
        assertThat((Boolean) page.evaluate(
                "() => document.getElementById('battleCanvasWrap')"
                        + ".contains(window.battleMap.stage.container())"))
                .isTrue();
    }
}
