package dev.hendrikhoemberg.dmhelper.session.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CockpitSurfaceContractTest {
    private static final Path MODULES = Path.of("src/main/resources/templates/session/modules");

    private static String cockpit() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
    }

    private static String module(String name) throws Exception {
        return Files.readString(MODULES.resolve(name));
    }

    @Test
    void layoutChromeIsAbsentWhileLocked() throws Exception {
        assertThat(cockpit())
                .as("spec 12.5: no layout editing chrome while locked")
                .contains("th:if=\"${layoutEditing}\"");
    }

    @Test
    void everyModuleDeclaresItsRoleSoZonesCanRankThem() throws Exception {
        try (var files = Files.list(MODULES)) {
            for (Path module : files.toList()) {
                assertThat(Files.readString(module)).as(module.getFileName().toString())
                        .contains("data-module-role");
            }
        }
    }

    @Test
    void theEncounterModuleIsFullyOperableWithoutAMap() throws Exception {
        String encounter = module("_encounter.html");
        assertThat(encounter).contains("data-encounter-turn").contains("data-encounter-initiative");
        assertThat(encounter)
                .as("theatre of mind: the tracker must not require the map module")
                .doesNotContain("data-requires-map");
    }

    @Test
    void runtimeStateRolesAreDistinctAndSubstantial() throws Exception {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/cockpit-modules.css"));
        for (String role : List.of("[data-runtime-state=\"current-scene\"]",
                "[data-runtime-state=\"active-encounter\"]",
                "[data-runtime-state=\"map-mismatch\"]",
                "[data-runtime-state=\"active-turn\"]",
                "[data-runtime-state=\"session\"]",
                "[data-runtime-state=\"connection\"]")) {
            assertThat(css).as("style for %s", role).contains(role);
        }
    }
}
