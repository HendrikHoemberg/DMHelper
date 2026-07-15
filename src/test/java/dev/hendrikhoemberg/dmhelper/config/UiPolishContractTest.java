package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UiPolishContractTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of("src/main/resources", path));
    }

    @Test
    void globalFormFallbackThemesOnlyTextLikeControls() throws IOException {
        String css = read("static/css/base.css");

        assertThat(css)
                .contains(":where(")
                .contains("input[type=\"text\"]", "input[type=\"number\"]")
                .contains("input[type=\"search\"]", "input[type=\"email\"]")
                .contains("input[type=\"password\"]", "input[type=\"url\"]")
                .contains("input[type=\"tel\"]", "input[type=\"date\"]")
                .contains("input[type=\"time\"]", "select, textarea")
                .contains("background: var(--color-bg)")
                .contains("border-color: var(--color-accent)");

        String fallback = css.substring(css.indexOf("/* Global fallback theme"),
                css.indexOf(".navbar"));
        assertThat(fallback)
                .doesNotContain("checkbox", "radio", "range", "file", "color\"]")
                .doesNotContain("padding:", "width:", "font-size:");
    }
}
