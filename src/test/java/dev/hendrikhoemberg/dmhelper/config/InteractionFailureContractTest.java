package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionFailureContractTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of("src/main/resources", path));
    }

    @Test
    void timeCriticalMutationSurfacesHaveNoEmptyCatchBlocks() throws IOException {
        assertThat(read("templates/encounter/_tracker.html")).doesNotContain("catch (e) {}");
        assertThat(read("templates/maps/battle.html"))
                .doesNotContain("catch (e) {}", ".catch(() => {})");
        assertThat(read("static/js/map/battle-map.js"))
                .doesNotContain("catch (e) {}", "catch (e) { /* non-critical */ }");
    }

    @Test
    void mutationSurfacesUseCheckedRequests() throws IOException {
        assertThat(read("templates/encounter/_tracker.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("templates/fragments/navbar.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("templates/maps/battle.html"))
                .contains("window.dmRequest", "window.reportActionFailure");
        assertThat(read("static/js/map/battle-map.js"))
                .contains("window.dmRequest", "window.reportActionFailure");
    }
}
