package dev.hendrikhoemberg.dmhelper.sheet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SheetTemplateContractTest {

    @Test
    void sheetUsesLayoutWithSectionNavAndStatGrids() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/sheet/detail.html"));
        assertThat(detail).contains("sheet-layout");
        assertThat(detail).contains("sheet-toc");
        assertThat(detail).contains("id=\"sheet-combat\"");
        assertThat(detail).contains("id=\"sheet-skills\"");
        assertThat(detail).contains("stat-grid");
        assertThat(detail).doesNotContain("<dl>\n                    <dt>Class &amp; Level</dt>");
    }

    @Test
    void derivedStatsRenderAsTiles() throws IOException {
        String derived = Files.readString(Path.of("src/main/resources/templates/sheet/_derived-stats.html"));
        assertThat(derived).contains("stat-grid stat-grid--tiles");
    }
}
