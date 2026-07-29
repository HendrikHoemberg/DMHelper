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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiceQuickRollBrowserTest {

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
    void concurrentRollCallsCreateOnlyOneHistoryEntry() {
        openCockpit();

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) page.evaluate("""
                async () => {
                  const state = window.Alpine.$data(document.querySelector('.dice-panel'));
                  const before = state.history.length;
                  state.expression = '1d20';
                  await Promise.all([state.roll(), state.roll()]);
                  return { before, after: state.history.length };
                }
                """);

        assertThat(counts.get("after"))
                .isEqualTo(counts.get("before") + 1);
    }

    @Test
    void quickRollButtonsAreDisabledWhileARollIsPending() {
        openCockpit();
        page.evaluate("""
                () => {
                  const originalRequest = window.dmRequest;
                  window.dmRequest = (url, options) => {
                    if (url === '/api/v1/roll' && options?.method === 'POST') {
                      return new Promise((resolve, reject) => {
                        window.releaseQuickRollRequest = () =>
                          originalRequest(url, options).then(resolve, reject);
                      });
                    }
                    return originalRequest(url, options);
                  };
                  window.Alpine.$data(document.querySelector('.dice-panel')).open = true;
                }
                """);
        page.locator(".dice-panel").waitFor();

        page.locator("[data-quick-roll=\"1d20\"]").click();
        page.waitForFunction("() => typeof window.releaseQuickRollRequest === 'function'");

        assertThat(page.locator("[data-quick-roll]").all())
                .allSatisfy(button -> assertThat(button.isDisabled()).isTrue());

        page.evaluate("() => window.releaseQuickRollRequest()");
        page.waitForFunction(
                "() => window.Alpine.$data(document.querySelector('.dice-panel')).loading === false");
    }

    private void openCockpit() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session");
        page.waitForFunction(
                "() => window.Alpine?.$data(document.querySelector('.dice-panel'))?.history != null");
    }
}
