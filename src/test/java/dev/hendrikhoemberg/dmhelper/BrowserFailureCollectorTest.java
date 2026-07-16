package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowserFailureCollectorTest {

    private static final String RESOURCE_503 =
            "Failed to load resource: the server responded with a status of 503 (Service Unavailable)";

    @Test
    void unexpectedResourceConsoleErrorsStillFailTheSmokeGuard() {
        var collector = new BrowserFailureCollector();

        collector.recordConsoleError("error", RESOURCE_503,
                "http://localhost:8080/api/v1/tokens/123/move:0:0");

        assertThatThrownBy(collector::assertNoFailures)
                .hasMessageContaining("console error")
                .hasMessageContaining("503");
    }

    @Test
    void declaredHttpFailureAllowsOnlyItsMatchingResourceConsoleError() {
        var collector = new BrowserFailureCollector();
        Pattern url = Pattern.compile(".*/api/v1/tokens/.+/move");
        collector.expectHttpFailure("PATCH", url, 503);

        collector.recordConsoleError("error", RESOURCE_503,
                "http://localhost:8080/api/v1/tokens/123/move:0:0");
        collector.recordResponse(response("PATCH",
                "http://localhost:8080/api/v1/tokens/123/move", 503));

        assertThatCode(collector::assertNoFailures).doesNotThrowAnyException();
    }

    @Test
    void declaredFailureDoesNotHideAResourceErrorFromAnotherUrl() {
        var collector = new BrowserFailureCollector();
        Pattern url = Pattern.compile(".*/api/v1/tokens/.+/move");
        collector.expectHttpFailure("PATCH", url, 503);

        collector.recordConsoleError("error", RESOURCE_503,
                "http://localhost:8080/missing-script.js:0:0");
        collector.recordResponse(response("PATCH",
                "http://localhost:8080/api/v1/tokens/123/move", 503));

        assertThatThrownBy(collector::assertNoFailures)
                .hasMessageContaining("missing-script.js");
    }

    private Response response(String method, String url, int status) {
        Request request = mock(Request.class);
        when(request.method()).thenReturn(method);
        Response response = mock(Response.class);
        when(response.request()).thenReturn(request);
        when(response.url()).thenReturn(url);
        when(response.status()).thenReturn(status);
        return response;
    }
}
