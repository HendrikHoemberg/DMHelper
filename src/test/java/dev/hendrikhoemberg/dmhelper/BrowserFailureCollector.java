package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserFailureCollector {
    private final List<String> failures = new CopyOnWriteArrayList<>();

    void attach(Page page) {
        page.onConsoleMessage(this::recordConsoleError);
        page.onPageError(message -> failures.add("page error: " + message));
        page.onRequestFailed(request -> {
            String failure = request.failure();
            if (!"net::ERR_ABORTED".equals(failure)) {
                failures.add("request failed: " + request.method() + " " + request.url() + " — " + failure);
            }
        });
        page.onResponse(response -> {
            if (response.status() >= 400) {
                failures.add("HTTP " + response.status() + ": "
                        + response.request().method() + " " + response.url());
            }
        });
    }

    private void recordConsoleError(ConsoleMessage message) {
        if ("error".equals(message.type())) {
            failures.add("console error: " + message.text());
        }
    }

    void assertNoFailures() {
        assertThat(failures).as("unexpected browser failures").isEmpty();
    }

    void clear() {
        failures.clear();
    }
}
