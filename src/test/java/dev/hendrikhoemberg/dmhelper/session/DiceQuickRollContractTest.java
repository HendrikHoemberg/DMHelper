package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiceQuickRollContractTest {

    @Test
    void diceDrawerOffersOneClickRollsForTheStandardDice() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/fragments/_dice-roller.html"));
        assertThat(html).contains("dice-quick-rolls");
        for (String die : new String[] {"d4", "d6", "d8", "d10", "d12", "d20", "d100"}) {
            assertThat(html)
                    .as("quick-roll button for %s", die)
                    .contains("data-quick-roll=\"1" + die + "\"");
        }
    }
}
