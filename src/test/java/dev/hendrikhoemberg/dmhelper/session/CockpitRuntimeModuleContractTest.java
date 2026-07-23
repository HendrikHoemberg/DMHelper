package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitRuntimeModuleContractTest {

    @Test
    void moduleShellDeclaresEndpointDataAttributes() throws IOException {
        String shell = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html"));
        assertThat(shell).contains(
                "data-module-endpoint",
                "data-module-loaded",
                "data-module-stale",
                "data-module-content");
        assertThat(count(shell, "data-module-content")).isEqualTo(1);
    }

    private static int count(String s, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
