package dev.hendrikhoemberg.dmhelper.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseNotesConsistencyTest {

    private static final Path RELEASE_NOTES = Path.of("docs/product/release-notes.md");

    @Test
    void atmosphereMusicIsNoLongerDescribedAsUnstarted() throws Exception {
        String text = Files.readString(RELEASE_NOTES);
        assertThat(text)
                .as("atmosphere/music shipped in roadmap row 6; release notes must not list it as remaining")
                .doesNotContain("Atmosphere/music is the remaining required P3 feature package");
    }

    @Test
    void deliveryItem11RecordsTheMusicSlice() throws Exception {
        String text = Files.readString(RELEASE_NOTES);
        assertThat(text)
                .as("Item 11 must record the shipped atmosphere/music slice")
                .containsIgnoringCase("music");
    }
}
