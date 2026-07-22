package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSafeChromeContractTest {

    @Test
    void pinDisplayIsHiddenInTableSafeMode() throws IOException {
        String navbar = Files.readString(
                Path.of("src/main/resources/templates/fragments/navbar.html"));
        assertThat(navbar)
                .as("PIN must carry data-screen-sensitive so table-safe mode hides it")
                .contains("data-screen-sensitive");
    }

    @Test
    void recentNotesCardIsHiddenInTableSafeMode() throws IOException {
        String dashboard = Files.readString(
                Path.of("src/main/resources/templates/campaigns/detail.html"));
        assertThat(dashboard)
                .as("Recent note titles can spoil quests; hide the card in table-safe mode")
                .contains("data-screen-sensitive");
    }
}
