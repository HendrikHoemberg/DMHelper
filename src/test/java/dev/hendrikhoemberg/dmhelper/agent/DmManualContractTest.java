package dev.hendrikhoemberg.dmhelper.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DmManualContractTest {

    @Test
    void chaptersExist() {
        for (String path : List.of(
                "docs/dm-manual/README.md",
                "docs/dm-manual/01-first-run-and-backup.md",
                "docs/dm-manual/02-campaign-preparation.md",
                "docs/dm-manual/03-session-cockpit.md",
                "docs/dm-manual/04-maps-encounters-party.md",
                "docs/dm-manual/05-import-preview.md",
                "docs/dm-manual/06-offline-and-troubleshooting.md",
                "docs/dm-manual/07-rollable-tables.md",
                "docs/dm-manual/08-traps-and-hazards.md")) {
            assertThat(Path.of(path)).exists();
        }
    }

    @Test
    void sessionCockpitDocumentsResumeOrder() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/03-session-cockpit.md"));
        assertThat(body).containsIgnoringCase("active encounter");
        assertThat(body).containsIgnoringCase("current scene");
        assertThat(body).contains("/session");
    }

    @Test
    void importPreviewExplainsWarnings() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/05-import-preview.md"));
        assertThat(body).contains("acceptWarnings");
        assertThat(body).contains("ERROR");
        assertThat(body).contains("WARNING");
    }
}
