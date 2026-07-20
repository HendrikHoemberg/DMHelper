package dev.hendrikhoemberg.dmhelper.audio.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AudioNavContractTest {

    @Test
    void appnavExposesCampaignMusicCuesLink() throws IOException {
        String nav = Files.readString(Path.of("src/main/resources/templates/fragments/_appnav.html"));
        assertThat(nav)
                .as("campaign sidebar must link to the audio cue library")
                .contains("/audio/cues")
                .contains("data-label=\"Music\"");
        assertThat(nav)
                .as("Music is campaign-scoped prep, not the global library")
                .contains("th:href=\"@{/campaigns/{id}/audio/cues(id=${campaignId})}\"");
    }
}
