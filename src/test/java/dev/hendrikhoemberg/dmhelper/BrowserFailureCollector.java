package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserFailureCollector {
    private final List<String> failures = new CopyOnWriteArrayList<>();

    private record ExpectedHttpFailure(String method, Pattern url, int status) {
        boolean matches(Response response) {
            return method.equals(response.request().method())
                    && status == response.status()
                    && url.matcher(response.url()).matches();
        }
    }

    private final List<ExpectedHttpFailure> expectedHttpFailures = new CopyOnWriteArrayList<>();

    void expectHttpFailure(String method, Pattern url, int status) {
        expectedHttpFailures.add(new ExpectedHttpFailure(method, url, status));
    }

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
            if (response.status() < 400) return;
            var expected = expectedHttpFailures.stream()
                    .filter(candidate -> candidate.matches(response))
                    .findFirst();
            if (expected.isPresent()) {
                expectedHttpFailures.remove(expected.get());
                return;
            }
            failures.add("HTTP " + response.status() + ": "
                    + response.request().method() + " " + response.url());
        });
    }

    private static final Pattern RESOURCE_LOAD_FAILURE =
            Pattern.compile("Failed to load resource: the server responded with a status of \\d+ ");

    private void recordConsoleError(ConsoleMessage message) {
        if ("error".equals(message.type())) {
            String text = message.text();
            if (RESOURCE_LOAD_FAILURE.matcher(text).find()) {
                return;
            }
            failures.add("console error: " + text);
        }
    }

    void assertNoFailures() {
        assertThat(expectedHttpFailures).as("declared HTTP failures that never occurred").isEmpty();
        assertThat(failures).as("unexpected browser failures").isEmpty();
    }

    void clear() {
        expectedHttpFailures.clear();
        failures.clear();
    }
}
