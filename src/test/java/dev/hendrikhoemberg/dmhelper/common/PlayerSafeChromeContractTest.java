package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSafeChromeContractTest {

    @Test
    void pinDisplayIsHiddenInPlayerSafeMode() throws IOException {
        String navbar = Files.readString(
                Path.of("src/main/resources/templates/fragments/navbar.html"));
        assertThat(navbar)
                .as("PIN must carry dm-only so player-safe mode hides it")
                .contains("class=\"pin-display dm-only\"");
    }

    @Test
    void recentNotesCardIsHiddenInPlayerSafeMode() throws IOException {
        String dashboard = Files.readString(
                Path.of("src/main/resources/templates/campaigns/detail.html"));
        assertThat(dashboard)
                .as("Recent note titles can spoil quests; hide the card in player-safe mode")
                .contains("card dash-card dm-only");
    }
}
