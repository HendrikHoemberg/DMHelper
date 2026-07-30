package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
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

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FailureSignallingBrowserTest {

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
    void anInvalidDiceExpressionDoesNotSayTheSessionFailedToSave() {
        failures.expectHttpFailure("POST", Pattern.compile(".*/api/v1/roll"), 400);

        UUID campaignId = fixtures.campaignWithRunningSession();
        String url = "http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session";

        page.route("**/api/v1/roll", route -> {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(400)
                    .setContentType("application/problem+json")
                    .setBody("{\"detail\":\"Unrecognised dice expression: 'bad'. Try 2d6 or 1d20.\",\"correlationId\":\"abc-123\"}"));
        });

        page.navigate(url);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.waitForSelector("[data-status-save][data-state='idle']");

        page.evaluate("() => window.dispatchEvent(new CustomEvent('dice-roller-toggle'))");
        page.waitForSelector(".dice-panel:not(.closed)");
        page.fill(".dice-input-row input[type='text']", "bad");
        page.click(".dice-input-row button:last-child");

        page.waitForSelector("[data-status-input]:not([hidden])", new Page.WaitForSelectorOptions().setTimeout(5000));

        String saveState = page.evaluate(
                "() => document.querySelector('[data-status-save]').dataset.state").toString();
        String inputText = page.evaluate(
                "() => document.querySelector('[data-status-input]').textContent").toString();

        assertThat(saveState).isNotEqualTo("error");
        assertThat(inputText).contains("not accepted");
    }

    @Test
    void aGenuineSaveFailureDoesEnterTheErrorState() {
        try (BrowserContext isolated = browser.newContext()) {
            Page p = isolated.newPage();
            p.setContent("""
                    <div id="runtimeStatus">
                      <span data-status-save data-state="idle">Up to date</span>
                      <span data-status-input data-state="rejected" hidden></span>
                    </div>
                    """);
            p.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/dm-request.js").toAbsolutePath()));
            p.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/runtime-status.js").toAbsolutePath()));

            @SuppressWarnings("unchecked")
            Map<String, Object> states = (Map<String, Object>) p.evaluate("""
                    async () => {
                      await new Promise(resolve => setTimeout(resolve, 0));
                      window.fetch = async () => ({ok: true, status: 204, headers: {get: () => ''}});
                      const startReq = window.dmRequest('/save', {method: 'POST'});
                      await new Promise(resolve => setTimeout(resolve, 0));
                      document.dispatchEvent(new CustomEvent('dm:request-failure', {
                        detail: {
                          url: '/save',
                          options: {method: 'POST'},
                          error: {kind: 'server', status: 500, message: 'Internal error'}
                        }
                      }));
                      return {
                        state: document.querySelector('[data-status-save]').dataset.state
                      };
                    }
                    """);

            assertThat(states)
                    .containsEntry("state", "error");
        }
    }

    @Test
    void aValidationFailureOffersNoRetryAndDemotesTheCorrelationId() {
        try (BrowserContext isolated = browser.newContext()) {
            Page p = isolated.newPage();
            p.setContent("<body></body>");
            p.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/dm-request.js").toAbsolutePath()));
            p.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/ui-elevation.js").toAbsolutePath()));

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) p.evaluate("""
                    async () => {
                      await new Promise(resolve => setTimeout(resolve, 50));
                      window.reportActionFailure('Roll failed', {
                        kind: 'validation',
                        status: 400,
                        message: 'Unrecognised dice expression',
                        correlationId: 'abc-123-def-456'
                      }, null);
                      await new Promise(resolve => setTimeout(resolve, 50));
                      const toast = document.querySelector('.toast');
                      if (!toast) return {noToast: true};
                      return {
                        text: toast.textContent,
                        hasAction: !!toast.querySelector('.toast-action'),
                        hasReference: !!toast.querySelector('.toast__reference'),
                        uuidInBody: toast.textContent.includes('abc-123-def-456')
                      };
                    }
                    """);

            assertThat(result)
                    .containsEntry("hasAction", false)
                    .containsEntry("hasReference", true)
                    .containsEntry("uuidInBody", false);
        }
    }

    @Test
    void identicalFailuresCoalesceIntoOneToast() {
        try (BrowserContext isolated = browser.newContext()) {
            Page p = isolated.newPage();
            p.setContent("<body></body>");
            p.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/dm-request.js").toAbsolutePath()));
            p.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/ui-elevation.js").toAbsolutePath()));

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) p.evaluate("""
                    async () => {
                      await new Promise(resolve => setTimeout(resolve, 50));
                      for (let i = 0; i < 3; i++) {
                        window.reportActionFailure('Save failed', {
                          kind: 'server',
                          status: 500,
                          message: 'Server error',
                          retryable: true
                        }, () => {});
                        await new Promise(resolve => setTimeout(resolve, 10));
                      }
                      await new Promise(resolve => setTimeout(resolve, 50));
                      const toasts = document.querySelectorAll('.toast');
                      const countEl = document.querySelector('[data-toast-count]');
                      return {
                        toastCount: toasts.length,
                        dedupeCount: countEl ? parseInt(countEl.textContent, 10) : 0
                      };
                    }
                    """);

            assertThat(result)
                    .containsEntry("toastCount", 1)
                    .containsEntry("dedupeCount", 3);
        }
    }

    @Test
    void aModuleThatFailedWhileHiddenShowsItsErrorWhenMadeVisible() {
        BrowserContext isolated = browser.newContext();
        try {
            Page p = isolated.newPage();

            UUID campaignId = fixtures.campaignWithRunningSession();
            String url = "http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session";

            p.route("**/session/modules/encounter**", route -> {
                route.fulfill(new Route.FulfillOptions()
                        .setStatus(500)
                        .setContentType("text/plain")
                        .setBody("Fixture encounter load failure"));
            });

            p.navigate(url);
            p.waitForFunction("() => window.cockpitLayout?.mounted === true");
            p.waitForSelector("[data-status-save][data-state='idle']");

            p.selectOption("#cockpitPresetPicker", "builtin:combat");
            p.waitForFunction(
                "() => window.cockpitLayout.currentPresetKey === 'builtin:combat'");

            p.evaluate("() => window.cockpitModules.load('encounter', { force: true })");
            p.waitForFunction("() => document.querySelectorAll("
                + "'[data-module-key=\"encounter\"] [data-module-retry]').length > 0");

            String errorHidden = p.evaluate("() => document.querySelector("
                + "'[data-module-key=\"encounter\"] [data-module-error]')?.hidden?.toString()").toString();
            assertThat(errorHidden).isEqualTo("false");
        } finally {
            isolated.close();
        }
    }

    @Test
    void theAttentionBadgeIsSeparatedFromTheTabLabel() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        String url = "http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session";

        page.navigate(url);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => window.cockpitLayout.currentPresetKey === 'builtin:combat'");

        page.evaluate("() => { window.cockpitLayout.setAttention('reference', 3); }");

        String tabLabel = page.evaluate("""
                () => {
                  const tab = document.querySelector('[data-module-tab="reference"]');
                  const label = tab?.querySelector('.cockpit-zone__tab-label');
                  return label ? label.textContent : tab?.textContent || '';
                }
                """).toString();

        assertThat(tabLabel).isEqualTo("Reference");
    }
}
