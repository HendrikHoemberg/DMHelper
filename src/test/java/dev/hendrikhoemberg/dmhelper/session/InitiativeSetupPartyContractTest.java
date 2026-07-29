package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitiativeSetupPartyContractTest {

    @Test
    void initiativeSetupOffersAnAddPartyAction() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"));
        assertThat(html)
                .contains("data-add-party-to-encounter")
                .contains("addPartyToEncounter()")
                .doesNotContain("missingPartyCount");
    }

    @Test
    void trackerImplementsAddPartyAgainstThePlacementsEndpoint() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/combat-tracker.js"));
        assertThat(js)
                .contains("addPartyToEncounter")
                .contains("/placements/party")
                .contains("await this.reloadCombatants()")
                .contains("this.dispatchState()")
                .doesNotContain("this.refresh()", "partyMembers");
    }
}
