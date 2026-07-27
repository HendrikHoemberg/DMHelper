package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public final class BrowserFailureCollector {
    private final List<String> failures = new CopyOnWriteArrayList<>();

    private static final class ExpectedHttpFailure {
        private final String method;
        private final Pattern url;
        private final int status;
        private final AtomicBoolean responseSeen = new AtomicBoolean();
        private final AtomicBoolean consoleErrorConsumed = new AtomicBoolean();

        private ExpectedHttpFailure(String method, Pattern url, int status) {
            this.method = method;
            this.url = url;
            this.status = status;
        }

        boolean matches(Response response) {
            return method.equals(response.request().method())
                    && status == response.status()
                    && url.matcher(response.url()).matches();
        }

        boolean consumeResponse(Response response) {
            return !responseSeen.get() && matches(response)
                    && responseSeen.compareAndSet(false, true);
        }

        boolean consumeConsoleError(int consoleStatus, String location) {
            return status == consoleStatus
                    && location != null
                    && url.matcher(location).find()
                    && consoleErrorConsumed.compareAndSet(false, true);
        }

        boolean responseWasSeen() {
            return responseSeen.get();
        }
    }

    private final List<ExpectedHttpFailure> expectedHttpFailures = new CopyOnWriteArrayList<>();

    void expectHttpFailure(String method, Pattern url, int status) {
        expectedHttpFailures.add(new ExpectedHttpFailure(method, url, status));
    }

    public void attach(Page page) {
        page.onConsoleMessage(this::recordConsoleError);
        page.onPageError(message -> failures.add("page error: " + message));
        page.onRequestFailed(request -> {
            String failure = request.failure();
            if (!"net::ERR_ABORTED".equals(failure)) {
                failures.add("request failed: " + request.method() + " " + request.url() + " — " + failure);
            }
        });
        page.onResponse(this::recordResponse);
    }

    private static final Pattern RESOURCE_LOAD_FAILURE =
            Pattern.compile("Failed to load resource: the server responded with a status of (\\d+) ");

    private void recordConsoleError(ConsoleMessage message) {
        recordConsoleError(message.type(), message.text(), message.location());
    }

    void recordConsoleError(String type, String message, String location) {
        if (!"error".equals(type)) return;
        var resourceFailure = RESOURCE_LOAD_FAILURE.matcher(message);
        if (resourceFailure.find()) {
            int status = Integer.parseInt(resourceFailure.group(1));
            boolean expected = expectedHttpFailures.stream()
                    .anyMatch(candidate -> candidate.consumeConsoleError(status, location));
            if (expected) return;
        }
        failures.add("console error: " + message + " at " + location);
    }

    void recordResponse(Response response) {
        if (response.status() < 400) return;
        boolean expected = expectedHttpFailures.stream()
                .anyMatch(candidate -> candidate.consumeResponse(response));
        if (expected) return;
        failures.add("HTTP " + response.status() + ": "
                + response.request().method() + " " + response.url());
    }

    public void assertNoFailures() {
        assertThat(expectedHttpFailures)
                .as("declared HTTP failures that never occurred")
                .allMatch(ExpectedHttpFailure::responseWasSeen);
        assertThat(failures).as("unexpected browser failures").isEmpty();
    }

    public void clear() {
        expectedHttpFailures.clear();
        failures.clear();
    }
}
