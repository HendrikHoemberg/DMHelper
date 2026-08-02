package dev.hendrikhoemberg.dmhelper.session.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class CockpitSurfaceContractTest {
    private static String cockpit() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
    }

    @Test
    void layoutChromeIsAbsentWhileLocked() throws Exception {
        assertThat(cockpit())
                .as("spec 12.5: no layout editing chrome while locked")
                .contains("th:if=\"${layoutEditing}\"");
    }
}
