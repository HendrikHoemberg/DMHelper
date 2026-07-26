package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.live.TableStateWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 2026-07-22 section 10.3 and section 11.3: save, loading, connection and error status
 * are visible without dominating the command bar — and without scrolling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RuntimeStatusSurfaceTest {

    @Autowired private MockMvc mvc;
    @Autowired private TableStateWebSocketHandler handler;

    @Test
    void tableStatusEndpointReportsTheConnectedCount() throws Exception {
        mvc.perform(get("/api/table/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(handler.connectedCount()));
    }

    @Test
    void theCockpitDeclaresTheStatusCluster() throws IOException {
        String cockpit = Files.readString(
                Path.of("src/main/resources/templates/session/cockpit.html"));

        assertThat(cockpit)
                .contains("id=\"runtimeStatus\"")
                .contains("data-status-save")
                .contains("data-status-table")
                .contains("role=\"status\"")
                .contains("aria-live=\"polite\"");
    }

    @Test
    void theStatusScriptCoversAllFourStates() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/runtime-status.js"));

        assertThat(js)
                .contains("htmx:beforeRequest")
                .contains("htmx:afterRequest")
                .contains("htmx:responseError")
                .contains("htmx:sendError")
                .contains("'busy'")
                .contains("'saved'")
                .contains("'error'")
                .contains("/api/table/status");
    }

    @Test
    void theStatusClusterStaysQuietUntilItHasSomethingToSay() throws IOException {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/cockpit-layout.css"));

        assertThat(css)
                .contains("#runtimeStatus")
                .contains("[data-status-save][data-state=\"idle\"]")
                .contains("[data-status-save][data-state=\"error\"]");
    }
}
