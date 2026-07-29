package dev.hendrikhoemberg.dmhelper.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DmManualCockpitAccuracyTest {

    private static final Path MANUAL = Path.of("docs/dm-manual/03-session-cockpit.md");
    private static final Path LAYOUT_JS =
            Path.of("src/main/resources/static/js/cockpit-layout.js");

    @Test
    void manualMatchesTheDmOnlyCockpitContract() throws IOException {
        String manual = Files.readString(MANUAL);
        String js = Files.readString(LAYOUT_JS);

        for (String key : new String[] {
                "builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review"}) {
            assertThat(js).as("preset %s must exist in the layout controller", key).contains(key);
        }
        assertThat(js)
                .as("Presentation preset was removed by the DM-only cut")
                .doesNotContain("builtin:presentation");

        assertThat(manual)
                .as("the manual must not document a Presentation preset or module")
                .doesNotContain("| Presentation |")
                .doesNotContain("Five immutable built-ins")
                .doesNotContain("Ten modules ship")
                .doesNotContain("Ten runtime modules")
                .doesNotContain("transitional content shells")
                .doesNotContain("in B1")
                .doesNotContain("in **B2**")
                .doesNotContain("Presentation, Party")
                .doesNotContain("Screen Safety")
                .doesNotContain("Player preview")
                .doesNotContain("player view")
                .doesNotContain("player table")
                .doesNotContain("Focus handout picker")
                .doesNotContain("Present current map");
        assertThat(manual)
                .as("only four built-in preset shortcuts exist")
                .doesNotContain("Alt+Shift+5")
                .doesNotContain("`Alt+Shift+1`…`5`");
        assertThat(manual)
                .as("the PIN gate was removed; loopback binding is the compensating control")
                .doesNotContain("PIN interceptor");
        assertThat(manual)
                .as("COMPACT is a per-preset set, not a zone rule")
                .doesNotContain("Modules serving in the **Bottom utility** zone render in `COMPACT`");
        assertThat(manual)
                .as("the manual must explain mixed initial delivery and discard semantics")
                .contains("Four modules are server-rendered for first paint")
                .contains("The other five — Map, Encounter, Reference, Audio, and Session log — "
                        + "load from their module endpoints on first visibility")
                .contains("Campaign changes remain")
                .contains("No `SESSION_LOG` note is created");
    }
}
