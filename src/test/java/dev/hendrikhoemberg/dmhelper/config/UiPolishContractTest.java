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

    @Test
    void denseListsUseTheSharedBoundedGrid() throws IOException {
        assertThat(read("static/css/components.css"))
                .contains(".card-grid--bounded")
                .contains("repeat(auto-fill, minmax(300px, 360px))");
        assertThat(read("templates/encounter/list.html"))
                .contains("card-grid card-grid--bounded empty-state-host");
        assertThat(read("templates/maps/list.html"))
                .contains("card-grid card-grid--bounded empty-state-host");
        assertThat(read("templates/treasury/list.html"))
                .contains("card-grid card-grid--bounded")
                .doesNotContain("flex-direction:column; gap: var(--space-sm)");
    }

    @Test
    void bestiaryCardsExposeAlignedCrAndSemanticTypeChips() throws IOException {
        String card = read("templates/library/_card.html");
        String css = read("static/css/components.css");

        assertThat(card).contains(
                "library-card__header",
                "library-card__title",
                "library-card__cr",
                "library-card__meta",
                "library-chip library-chip--",
                "data-statblock-link");
        assertThat(css).contains(
                "font-variant-numeric: tabular-nums",
                ".library-chip--accent { --library-chip-color: var(--color-accent); }",
                ".library-chip--success { --library-chip-color: var(--color-success); }",
                ".library-chip--danger { --library-chip-color: var(--color-danger); }",
                ".library-chip--warning { --library-chip-color: var(--color-warning); }",
                ".library-chip--shield { --library-chip-color: var(--color-shield); }");
    }

    @Test
    void siblingLibraryResultsReuseTitleAndMetadataHierarchy() throws IOException {
        assertThat(read("templates/library/_spell-card.html"))
                .contains("library-card__title", "library-card__meta");
        assertThat(read("templates/library/_magic-item-card.html"))
                .contains("library-card__title", "library-card__meta");
        assertThat(read("templates/library/_class-card.html"))
                .contains("library-card__title", "library-card__meta");
        assertThat(read("templates/library/_equipment-card.html"))
                .contains("library-table__name", "library-table__meta");
    }

    @Test
    void spellTitleKeepsSharedDisplayHierarchySpecificity() throws IOException {
        assertThat(read("static/css/components.css"))
                .contains(".statblock-card > h3:not(.library-card__title)");
    }

    @Test
    void focusVisibleTargetsInteractiveElementsWithTokenizedRing() throws IOException {
        String css = read("static/css/base.css");
        assertThat(css).contains(
                ":where(a, button, input, select, textarea, [tabindex]):focus-visible",
                "outline: 2px solid var(--color-accent)",
                "outline-offset: 2px",
                "border-radius: var(--radius)");
    }

    @Test
    void mutedTokenClearsAaOnBothAppBackgrounds() {
        assertThat(contrast(0xb3a88f, 0x17120c)).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(0xb3a88f, 0x211a12)).isGreaterThanOrEqualTo(4.5);
    }

    private static double contrast(int foreground, int background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int rgb) {
        double red = channel((rgb >> 16) & 0xff);
        double green = channel((rgb >> 8) & 0xff);
        double blue = channel(rgb & 0xff);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.04045
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
