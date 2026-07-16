package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCockpitTemplateContractTest {

    @Test
    void cockpitOwnsOneRuntimeIslandAndAccessibleRailControls() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String rail = Files.readString(Path.of("src/main/resources/templates/session/_encounter-rail.html"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(count(html, "id=\"battleCanvasWrap\"")).isEqualTo(1);
        assertThat(count(rail, "encounter/_tracker :: tracker")).isEqualTo(1);
        assertThat(count(html, "session/_encounter-rail :: encounters")).isEqualTo(1);
        assertThat(js).contains("new BattleMap(");
        assertThat(js).doesNotContain("nextTurn(id)", "applyDamage(combatant", "projectTokens(");
        assertThat(html).contains("aria-label=\"Story rail\"", "aria-label=\"Encounter rail\"",
                "aria-label=\"Session plan\"", "aria-live=\"polite\"");
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
