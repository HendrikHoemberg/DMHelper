package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitModuleClientContractTest {

    @Test
    void moduleControllerExportsExpectedApi() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/cockpit-modules.js"));
        assertThat(js).contains(
                "class CockpitModuleController",
                "new AbortController()",
                "cockpit:module-visibility",
                "cockpit:module-refresh",
                "cockpit:module-invalidate",
                "cockpit:module-state",
                "cockpit:module-loaded",
                "cockpit:module-mode",
                "cockpit:module-load-failed",
                "cockpit:module-content-ready",
                "cockpit:layout-applied",
                "seedInitialLoads",
                "data-cockpit-module-fragment",
                "replaceChildren");
        assertThat(js).doesNotContain("outerHTML =", "window.location.reload()");
    }
}
