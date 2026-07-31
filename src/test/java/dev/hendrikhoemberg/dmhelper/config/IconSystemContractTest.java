package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class IconSystemContractTest {

    private static final Path SPRITE = Path.of("src/main/resources/static/icons/ui.svg");

    /** Every icon the application chrome needs. Extend deliberately, never ad hoc. */
    private static final List<String> REQUIRED = List.of(
            "search", "dice", "menu", "chevron-right", "chevron-down", "close", "plus",
            "edit", "trash", "play", "pause", "check", "alert-triangle", "alert-circle",
            "info", "shield", "eye", "eye-off", "book", "scroll", "map", "swords",
            "users", "user", "castle", "coins", "gem", "note", "calendar", "flag",
            "location", "music", "sparkles", "layers", "brush", "fill", "select",
            "square", "circle", "line", "polygon", "door", "corridor", "room", "pin",
            "undo", "redo", "download", "upload", "settings", "help", "external-link",
            "grip", "filter", "sort", "star", "clock", "heart", "skull", "concentration");

    private static String sprite() throws Exception {
        return Files.readString(SPRITE);
    }

    @Test
    void everyRequiredSymbolExists() throws Exception {
        String svg = sprite();
        for (String name : REQUIRED) {
            assertThat(svg).as("symbol %s", name).contains("id=\"icon-" + name + "\"");
        }
    }

    @Test
    void everySymbolSharesOneGeometryAndStrokeContract() throws Exception {
        var symbols = java.util.regex.Pattern.compile("<symbol[^>]*>")
                .matcher(sprite()).results().map(r -> r.group()).toList();
        assertThat(symbols).hasSizeGreaterThanOrEqualTo(REQUIRED.size());
        for (String symbol : symbols) {
            assertThat(symbol).as("viewBox on %s", symbol).contains("viewBox=\"0 0 24 24\"");
        }
    }

    /**
     * A <use> reference into an external sprite clones only the <symbol> subtree, and
     * inherited properties resolve from the <use> element's position in the referencing
     * document — not from the sprite's own root. Painting attributes declared on the
     * sprite's root <svg> are therefore dropped, and every icon renders as a black fill.
     * The stroke contract must live in components.css on .icon, where it does inherit.
     */
    @Test
    void theStrokeContractLivesWhereExternalUseCanInheritIt() {
        var icon = CssRules.of("components.css").stream()
                .filter(rule -> rule.selector().equals(".icon"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".icon is not styled in components.css"));
        assertThat(icon.value("fill")).isEqualTo("none");
        assertThat(icon.value("stroke")).isEqualTo("currentColor");
        assertThat(icon.value("stroke-width")).isEqualTo("1.75");
        assertThat(icon.value("stroke-linecap")).isEqualTo("round");
        assertThat(icon.value("stroke-linejoin")).isEqualTo("round");
    }

    @Test
    void theIconFragmentIsDecorativeOnly() throws Exception {
        String fragment = Files.readString(
                Path.of("src/main/resources/templates/common/_icon.html"));
        assertThat(fragment).contains("aria-hidden=\"true\"").contains("focusable=\"false\"");
        assertThat(fragment).doesNotContain("aria-label");
    }

    /**
     * Matched by codepoint, not by entity prefix: "&#x26" and "&#x27" also match the
     * ordinary escapes &#x26; (ampersand) and &#x27; (apostrophe), which are legitimate.
     *
     * @Disabled until Task 13: the only remaining emoji in chrome live in
     * fragments/navbar.html and fragments/_appnav.html, which Task 12 and Task 13 rewrite.
     */
    @Disabled("re-enabled in Task 13")
    @Test
    void applicationChromeCarriesNoEmojiPictograms() {
        String markup = CssRules.allTemplateMarkup();

        var literal = java.util.regex.Pattern
                .compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}]")
                .matcher(markup);
        assertThat(literal.find())
                .as("literal emoji or pictogram in chrome — use ~{common/_icon :: icon}")
                .isFalse();

        var escaped = java.util.regex.Pattern
                .compile("&#x([0-9a-fA-F]{4,5});")
                .matcher(markup);
        while (escaped.find()) {
            int codepoint = Integer.parseInt(escaped.group(1), 16);
            boolean pictogram = (codepoint >= 0x1F300 && codepoint <= 0x1FAFF)
                    || (codepoint >= 0x2600 && codepoint <= 0x27BF)
                    || (codepoint >= 0x2B00 && codepoint <= 0x2BFF);
            assertThat(pictogram)
                    .as("escaped pictogram %s in chrome — use ~{common/_icon :: icon}",
                            escaped.group())
                    .isFalse();
        }
    }
}
