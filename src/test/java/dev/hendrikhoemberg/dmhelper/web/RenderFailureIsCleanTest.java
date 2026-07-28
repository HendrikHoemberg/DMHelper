package dev.hendrikhoemberg.dmhelper.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A template that blows up half-way through a document must fail cleanly. Thymeleaf streams its
 * output by default, so a mid-render exception commits a 200 with half a page already on the wire
 * and then glues the error card onto it — the DM sees a broken screen that claims to have worked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RenderFailureIsCleanTest.FailingRenderConfiguration.class)
class RenderFailureIsCleanTest {

    private static final String MARKER = "render-failure-marker";

    @TestConfiguration
    static class FailingRenderConfiguration {
        @Controller
        static class FailingRenderController {
            @GetMapping("/__test/render-failure")
            String failMidRender() {
                return "gate/_render-failure";
            }
        }
    }

    @LocalServerPort private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    void aBrokenRenderIsReportedAsAFailureNotAsSuccess() throws Exception {
        assertThat(get("/__test/render-failure").statusCode())
                .as("a page that could not be rendered must not answer 200")
                .isEqualTo(500);
    }

    @Test
    void aBrokenRenderDoesNotLeakHalfAPageAroundTheErrorCard() throws Exception {
        String body = get("/__test/render-failure").body();

        assertThat(body)
                .as("partial output from the failed template must never reach the browser")
                .doesNotContain(MARKER);
        assertThat(body).contains("Something went wrong");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
