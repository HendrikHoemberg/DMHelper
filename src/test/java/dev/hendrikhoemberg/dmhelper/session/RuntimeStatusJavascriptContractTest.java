package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeStatusJavascriptContractTest {

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void dmRequestDispatchesLifecycleEventsForSuccessNetworkAndHttpFailures() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("<body></body>");
            page.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/dm-request.js").toAbsolutePath()));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) page.evaluate("""
                    async () => {
                      window.fetch = async (url) => {
                        if (url === '/network') throw new Error('offline');
                        if (url === '/http') {
                          return {ok: false, status: 422, headers: {get: () => ''}};
                        }
                        return {ok: true, status: 204, headers: {get: () => ''}};
                      };
                      const events = [];
                      for (const type of ['dm:request-start', 'dm:request-success', 'dm:request-failure']) {
                        document.addEventListener(type, event => events.push({
                          type,
                          url: event.detail.url,
                          status: event.detail.response?.status ?? event.detail.error?.status ?? null
                        }));
                      }
                      await window.dmRequest('/success', {method: 'POST'});
                      try { await window.dmRequest('/network', {method: 'POST'}); } catch (_) {}
                      try { await window.dmRequest('/http', {method: 'POST'}); } catch (_) {}
                      return events;
                    }
                    """);

            assertThat(events).extracting(event -> event.get("type"))
                    .containsExactly(
                            "dm:request-start", "dm:request-success",
                            "dm:request-start", "dm:request-failure",
                            "dm:request-start", "dm:request-failure");
            assertThat(events).extracting(event -> event.get("url"))
                    .containsExactly("/success", "/success", "/network", "/network", "/http", "/http");
            assertThat(events).extracting(event -> event.get("status"))
                    .containsExactly(null, 204, null, 0, null, 422);
        }
    }

    @Test
    void runtimeStatusRespondsToDmRequestSuccessAndFailureWithoutUsingTablePolling() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("""
                    <div id="runtimeStatus">
                      <span data-status-save data-state="idle">Up to date</span>
                      <span data-status-table data-state="disconnected">No table screen</span>
                    </div>
                    """);
            page.evaluate("""
                    () => {
                      window.fetch = async url => {
                        if (url === '/api/table/status') {
                          window.tablePolls = (window.tablePolls || 0) + 1;
                          return {ok: true, json: async () => ({connected: 1})};
                        }
                        if (window.dmFailure) return {ok: false, status: 500, headers: {get: () => ''}};
                        return new Promise(resolve => window.resolveDm = resolve);
                      };
                    }
                    """);
            page.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/dm-request.js").toAbsolutePath()));
            page.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/runtime-status.js").toAbsolutePath()));

            @SuppressWarnings("unchecked")
            Map<String, Object> states = (Map<String, Object>) page.evaluate("""
                    async () => {
                      await new Promise(resolve => setTimeout(resolve, 0));
                      const pending = window.dmRequest('/action', {method: 'POST'});
                      await new Promise(resolve => setTimeout(resolve, 0));
                      const busy = document.querySelector('[data-status-save]').dataset.state;
                      window.resolveDm({ok: true, status: 204, headers: {get: () => ''}});
                      await pending;
                      const saved = document.querySelector('[data-status-save]').dataset.state;
                      window.dmFailure = true;
                      try { await window.dmRequest('/failed-action', {method: 'POST'}); } catch (_) {}
                      return {
                        busy,
                        saved,
                        failed: document.querySelector('[data-status-save]').dataset.state,
                        table: document.querySelector('[data-status-table]').dataset.state,
                        tablePolls: window.tablePolls
                      };
                    }
                    """);

            assertThat(states)
                    .containsEntry("busy", "busy")
                    .containsEntry("saved", "saved")
                    .containsEntry("failed", "error")
                    .containsEntry("table", "connected")
                    .containsEntry("tablePolls", 1);
        }
    }
}
