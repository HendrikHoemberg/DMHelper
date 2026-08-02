package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
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
    void runtimeStatusRespondsToDmRequestSuccessAndFailure() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("""
                    <div id="runtimeStatus">
                      <span data-status-save data-state="idle">Up to date</span>
                    </div>
                    """);
            page.evaluate("""
                    () => {
                      window.fetch = async () => {
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
                        failed: document.querySelector('[data-status-save]').dataset.state
                      };
                    }
                    """);

            assertThat(states)
                    .containsEntry("busy", "busy")
                    .containsEntry("saved", "saved")
                    .containsEntry("failed", "error");
        }
    }

    @Test
    void aFailedDmRequestRemainsAuthoritativeUntilALaterCleanRequestCycle() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("""
                    <div id="runtimeStatus">
                      <span data-status-save data-state="idle">Up to date</span>
                    </div>
                    """);
            page.evaluate("""
                    () => {
                      window.fetch = async url => {
                        if (url === '/dm-a') {
                          return new Promise((resolve, reject) => window.dmA = {resolve, reject});
                        }
                        if (url === '/dm-b') {
                          return new Promise((resolve, reject) => window.dmB = {resolve, reject});
                        }
                        if (url === '/clean') {
                          return new Promise(resolve => window.clean = {resolve});
                        }
                        return {ok: true, status: 204, headers: {get: () => ''}};
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
                      const requestA = window.dmRequest('/dm-a', {method: 'POST'}).catch(() => {});
                      const requestB = window.dmRequest('/dm-b', {method: 'POST'});
                      await new Promise(resolve => setTimeout(resolve, 0));
                      const busy = document.querySelector('[data-status-save]').dataset.state;

                      window.dmA.reject(new Error('offline'));
                      await requestA;
                      const afterFailure = document.querySelector('[data-status-save]').dataset.state;

                      window.dmB.resolve({ok: true, status: 204, headers: {get: () => ''}});
                      await requestB;
                      const afterConcurrentSuccess = document.querySelector('[data-status-save]').dataset.state;

                      const cleanRequest = window.dmRequest('/clean', {method: 'POST'});
                      await new Promise(resolve => setTimeout(resolve, 0));
                      const cleanBusy = document.querySelector('[data-status-save]').dataset.state;
                      window.clean.resolve({ok: true, status: 204, headers: {get: () => ''}});
                      await cleanRequest;
                      const cleanSaved = document.querySelector('[data-status-save]').dataset.state;
                      return {busy, afterFailure, afterConcurrentSuccess, cleanBusy, cleanSaved};
                    }
                    """);

            assertThat(states)
                    .containsEntry("busy", "busy")
                    .containsEntry("afterFailure", "error")
                    .containsEntry("afterConcurrentSuccess", "error")
                    .containsEntry("cleanBusy", "busy")
                    .containsEntry("cleanSaved", "saved");
        }
    }

    @Test
    void aFailedHtmxRequestRemainsAuthoritativeOverConcurrentDmSuccess() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("""
                    <div id="runtimeStatus">
                      <span data-status-save data-state="idle">Up to date</span>
                    </div>
                    """);
            page.evaluate("""
                    () => {
                      window.fetch = async url => {
                        if (url === '/dm-b') {
                          return new Promise(resolve => window.dmB = {resolve});
                        }
                        return {ok: true, status: 204, headers: {get: () => ''}};
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
                      document.body.dispatchEvent(new CustomEvent('htmx:beforeRequest', {
                        detail: {requestConfig: {verb: 'POST'}}
                      }));
                      const requestB = window.dmRequest('/dm-b', {method: 'POST'});
                      await new Promise(resolve => setTimeout(resolve, 0));
                      const busy = document.querySelector('[data-status-save]').dataset.state;

                      document.body.dispatchEvent(new CustomEvent('htmx:responseError'));
                      const afterFailure = document.querySelector('[data-status-save]').dataset.state;

                      window.dmB.resolve({ok: true, status: 204, headers: {get: () => ''}});
                      await requestB;
                      const afterConcurrentSuccess = document.querySelector('[data-status-save]').dataset.state;

                      document.body.dispatchEvent(new CustomEvent('htmx:beforeRequest', {
                        detail: {requestConfig: {verb: 'POST'}}
                      }));
                      const cleanBusy = document.querySelector('[data-status-save]').dataset.state;
                      document.body.dispatchEvent(new CustomEvent('htmx:afterRequest', {
                        detail: {requestConfig: {verb: 'POST'}, xhr: {status: 204}}
                      }));
                      const cleanSaved = document.querySelector('[data-status-save]').dataset.state;
                      return {busy, afterFailure, afterConcurrentSuccess, cleanBusy, cleanSaved};
                    }
                    """);

            assertThat(states)
                    .containsEntry("busy", "busy")
                    .containsEntry("afterFailure", "error")
                    .containsEntry("afterConcurrentSuccess", "error")
                    .containsEntry("cleanBusy", "busy")
                    .containsEntry("cleanSaved", "saved");
        }
    }

    @Test
    void validationFailuresNeverTouchTheSaveIndicator() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/runtime-status.js"));
        assertThat(js)
                .contains("kind === 'validation'")
                .contains("data-status-input");
    }

    @Test
    void aFailedHtmxRequestSettlesOnceWhenResponseErrorPrecedesAfterRequest() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("""
                    <div id="runtimeStatus">
                      <span data-status-save data-state="idle">Up to date</span>
                    </div>
                    """);
            page.evaluate("""
                    () => {
                      window.fetch = async url => {
                        return {ok: true, status: 204, headers: {get: () => ''}};
                      };
                    }
                    """);
            page.addScriptTag(new Page.AddScriptTagOptions()
                    .setPath(Path.of("src/main/resources/static/js/runtime-status.js").toAbsolutePath()));

            @SuppressWarnings("unchecked")
            Map<String, Object> states = (Map<String, Object>) page.evaluate("""
                    async () => {
                      await new Promise(resolve => setTimeout(resolve, 0));
                      const failedRequest = {verb: 'POST'};
                      const failedXhr = {status: 500};
                      const concurrentRequest = {verb: 'POST'};
                      const concurrentXhr = {status: 204};
                      document.body.dispatchEvent(new CustomEvent('htmx:beforeRequest', {
                        detail: {requestConfig: failedRequest, xhr: failedXhr}
                      }));
                      document.body.dispatchEvent(new CustomEvent('htmx:beforeRequest', {
                        detail: {requestConfig: concurrentRequest, xhr: concurrentXhr}
                      }));
                      const busy = document.querySelector('[data-status-save]').dataset.state;

                      document.body.dispatchEvent(new CustomEvent('htmx:responseError', {
                        detail: {requestConfig: failedRequest, xhr: failedXhr}
                      }));
                      document.body.dispatchEvent(new CustomEvent('htmx:afterRequest', {
                        detail: {requestConfig: failedRequest, xhr: failedXhr}
                      }));
                      const afterFailedRequest = document.querySelector('[data-status-save]').dataset.state;

                      const cleanRequest = {verb: 'POST'};
                      const cleanXhr = {status: 204};
                      document.body.dispatchEvent(new CustomEvent('htmx:beforeRequest', {
                        detail: {requestConfig: cleanRequest, xhr: cleanXhr}
                      }));
                      const cleanBusy = document.querySelector('[data-status-save]').dataset.state;

                      document.body.dispatchEvent(new CustomEvent('htmx:afterRequest', {
                        detail: {requestConfig: concurrentRequest, xhr: concurrentXhr}
                      }));
                      const afterConcurrentSuccess = document.querySelector('[data-status-save]').dataset.state;

                      document.body.dispatchEvent(new CustomEvent('htmx:afterRequest', {
                        detail: {requestConfig: cleanRequest, xhr: cleanXhr}
                      }));
                      const afterOverlappingClean = document.querySelector('[data-status-save]').dataset.state;

                      const laterRequest = {verb: 'POST'};
                      const laterXhr = {status: 204};
                      document.body.dispatchEvent(new CustomEvent('htmx:beforeRequest', {
                        detail: {requestConfig: laterRequest, xhr: laterXhr}
                      }));
                      document.body.dispatchEvent(new CustomEvent('htmx:afterRequest', {
                        detail: {requestConfig: laterRequest, xhr: laterXhr}
                      }));
                      const laterSaved = document.querySelector('[data-status-save]').dataset.state;
                      return {busy, afterFailedRequest, afterConcurrentSuccess, cleanBusy, afterOverlappingClean, laterSaved};
                    }
                    """);

            assertThat(states)
                    .containsEntry("busy", "busy")
                    .containsEntry("afterFailedRequest", "error")
                    .containsEntry("afterConcurrentSuccess", "busy")
                    .containsEntry("cleanBusy", "busy")
                    .containsEntry("afterOverlappingClean", "busy")
                    .containsEntry("laterSaved", "saved");
        }
    }
}
