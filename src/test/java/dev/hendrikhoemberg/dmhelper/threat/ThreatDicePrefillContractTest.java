package dev.hendrikhoemberg.dmhelper.threat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatDicePrefillContractTest {

    @Test
    void sharedDiceRollerScriptExistsWithSingleRegistrationAndPrefill() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/dice-roller.js"));
        assertThat(count(js, "Alpine.data('diceRoller'")).isEqualTo(1);
        assertThat(js).contains("dice-roller-prefill");
        assertThat(js).contains("expressionInput");
        assertThat(js).contains("this.open = true");
        assertThat(js).contains("this.advantage = false");
        assertThat(js).contains("this.disadvantage = false");
        assertThat(js).contains("this.result = null");
        assertThat(js).contains("event.detail.label");
        assertThat(js).contains("this.label");
    }

    @Test
    void bothShellsLoadSharedDiceRollerScript() throws IOException {
        String navbar = Files.readString(Path.of("src/main/resources/templates/fragments/navbar.html"));
        String cockpit = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        assertThat(navbar).contains("/js/dice-roller.js");
        assertThat(cockpit).contains("/js/dice-roller.js");
        assertThat(count(navbar, "Alpine.data('diceRoller'")).isEqualTo(0);
        assertThat(count(cockpit, "Alpine.data('diceRoller'")).isEqualTo(0);
    }

    @Test
    void mechanicsCardDispatchesPrefillWithoutPostingRoll() throws IOException {
        String card = Files.readString(Path.of("src/main/resources/templates/threat/_mechanics-card.html"));
        assertThat(card).contains("dice-roller-prefill");
        assertThat(card).contains("detail: { expression:");
        assertThat(card).doesNotContain("/api/v1/roll");
        assertThat(card).doesNotContain("dmRequest");
        assertThat(card).contains("1d20");
    }

    @Test
    void diceRollerFragmentExposesExpressionInputRef() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/fragments/_dice-roller.html"));
        assertThat(html).contains("x-ref=\"expressionInput\"");
        assertThat(html).contains("x-show=\"label\"");
        assertThat(html).contains("x-text=\"label\"");
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
