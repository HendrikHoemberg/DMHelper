package dev.hendrikhoemberg.dmhelper.encounter.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class OperationalSurfaceContractTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    /** The two columns are load-bearing: encounter.css keys its grid off them. */
    @Test
    void setupDeclaresTheTwoOperationalColumns() throws Exception {
        assertThat(read("encounter/setup.html"))
                .contains("data-setup-column=\"sources\"")
                .contains("data-setup-column=\"settings\"");
    }

    /** Spec 11.4: setup and live combat must not be mistaken for each other mid-session. */
    @Test
    void setupAndLiveCombatDeclareDistinctModes() throws Exception {
        assertThat(read("encounter/setup.html")).contains("data-encounter-mode=\"setup\"");
        assertThat(read("encounter/_tracker.html")).contains("data-encounter-mode=\"live\"");
    }

    /** Player-safe state is a safety semantic; it must not drift per feature. */
    @Test
    void playerSafeHandoutsUseTheStableShieldTone() throws Exception {
        assertThat(read("handout/_card.html")).contains("tone='shield'");
    }
}
