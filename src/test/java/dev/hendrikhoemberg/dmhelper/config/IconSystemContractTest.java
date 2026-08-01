package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
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
     * Spec section 7.3 removes emoji and mixed Unicode pictograms from chrome. The original
     * ranges covered emoji only, so they matched none of the pictograms this product
     * actually ships: the map editor's tool rail, the tracker's turn arrows and its U+23C0
     * concentration badge, the collapsed rail's data-icon glyphs. They are widened here to
     * the arrow, technical, geometric-shape and supplemental-arrow blocks.
     *
     * <p>Disabling the test until Task 13 hid the count instead of holding it, so this is a
     * ratchet rather than a switch: the offenders belong to Task 12 (navbar), Task 13 (rail)
     * and Task 38 (map editor), and the number may only fall on the way there.
     *
     * <p>Matched by codepoint, not by entity prefix: "&#x26" and "&#x27" also match the
     * ordinary escapes &#x26; (ampersand) and &#x27; (apostrophe), which are legitimate.
     */
    private static final int PICTOGRAM_BUDGET = 58;

    private static final List<int[]> PICTOGRAM_RANGES = List.of(
            new int[]{0x1F300, 0x1FAFF}, new int[]{0x2600, 0x27BF}, new int[]{0x2B00, 0x2BFF},
            new int[]{0x2190, 0x21FF}, new int[]{0x2300, 0x23FF}, new int[]{0x25A0, 0x25FF},
            new int[]{0x2900, 0x297F});

    private static boolean isPictogram(int codepoint) {
        return PICTOGRAM_RANGES.stream().anyMatch(r -> codepoint >= r[0] && codepoint <= r[1]);
    }

    @Test
    void pictogramUseInApplicationChromeNeverGrows() throws Exception {
        var perTemplate = new java.util.TreeMap<String, Integer>();
        Path templates = Path.of("src/main/resources/templates");
        try (var files = Files.walk(templates)) {
            for (Path template : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                String markup = Files.readString(template);
                int count = (int) markup.codePoints().filter(IconSystemContractTest::isPictogram).count();
                var escaped = java.util.regex.Pattern
                        .compile("&#x([0-9a-fA-F]{4,5});").matcher(markup);
                while (escaped.find()) {
                    if (isPictogram(Integer.parseInt(escaped.group(1), 16))) count++;
                }
                if (count > 0) perTemplate.put(templates.relativize(template).toString(), count);
            }
        }
        int total = perTemplate.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total)
                .as("pictograms in chrome — use ~{common/_icon :: icon}. By template: %s",
                        perTemplate)
                .isLessThanOrEqualTo(PICTOGRAM_BUDGET);
    }
}
