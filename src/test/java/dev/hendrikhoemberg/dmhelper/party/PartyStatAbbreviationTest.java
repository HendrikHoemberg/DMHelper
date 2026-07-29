package dev.hendrikhoemberg.dmhelper.party;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PartyStatAbbreviationTest {

    /** PI / PInv / PP / Spd are not standard abbreviations; they must explain themselves. */
    @Test
    void everyAbbreviatedPartyStatCarriesAnExpandedTitle() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/party/_summary-bar.html"));
        assertThat(html)
                .contains("title=\"Armour Class\"")
                .contains("title=\"Hit Points\"")
                .contains("title=\"Passive Perception\"")
                .contains("title=\"Temporary Hit Points\"")
                .contains("title=\"Passive Insight\"")
                .contains("title=\"Passive Investigation\"")
                .contains("title=\"Speed (feet per turn)\"");
    }
}
