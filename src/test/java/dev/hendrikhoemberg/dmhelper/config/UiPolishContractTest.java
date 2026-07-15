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

        int fallbackStart = css.indexOf("/* Global fallback theme");
        int fallbackEnd = css.indexOf("::selection");
        assertThat(fallbackStart).isGreaterThanOrEqualTo(0);
        assertThat(fallbackEnd).isGreaterThan(fallbackStart);

        String fallback = css.substring(fallbackStart, fallbackEnd);

        assertThat(fallback)
                .contains(":where(")
                .contains("input[type=\"text\"]", "input[type=\"number\"]")
                .contains("input[type=\"search\"]", "input[type=\"email\"]")
                .contains("input[type=\"password\"]", "input[type=\"url\"]")
                .contains("input[type=\"tel\"]", "input[type=\"date\"]")
                .contains("input[type=\"time\"]", "select, textarea")
                .contains("background: var(--color-bg)")
                .contains("border-color: var(--color-accent)")
                .contains(":where(:focus)")
                .doesNotContain("):focus");
        assertThat(fallback)
                .doesNotContain("checkbox", "radio", "range", "file", "color\"]")
                .doesNotContain("padding:", "width:", "font-size:");
    }

    @Test
    void emptyStateKeepsCompatibilityAndAddsIconAwareVariant() throws IOException {
        String fragment = read("templates/common/_empty-state.html");

        assertThat(fragment)
                .contains("th:fragment=\"empty-state(message, actionText, actionHref, description)\"")
                .contains("th:fragment=\"empty-state-with-icon(message, actionText, actionHref, description, icon)\"")
                .contains("empty-state__icon", "empty-state__title")
                .contains("empty-state__desc", "empty-state__cta")
                .contains("aria-hidden=\"true\"");
    }

    @Test
    void primaryEmptySectionsExposeSpecifiedActions() throws IOException {
        assertThat(read("templates/encounter/list.html"))
                .contains("No encounters yet", "New encounter", "⚔");
        assertThat(read("templates/treasury/list.html"))
                .contains("The party stash is empty", "Add item", "◇");
        assertThat(read("templates/calendar/_timeline-list.html"))
                .contains("No events on the timeline", "Add event", "✦");
        assertThat(read("templates/maps/list.html"))
                .contains("No maps yet", "New map", "⌖");
        assertThat(read("templates/handout/list.html"))
                .contains("No handouts yet", "Upload handout", "▧");
        assertThat(read("templates/party/list.html"))
                .contains("No heroes yet", "Add member", "♜");
    }
}
