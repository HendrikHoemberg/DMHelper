package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 6.1: tokens.css is the only source of raw visual values. Persisted terrain,
 * drawing, and sigil colors are domain data and are named in DOMAIN_COLOR_MARKERS.
 */
class RawVisualValueContractTest {

    /** Lines carrying these markers describe persisted domain data, not UI chrome. */
    private static final List<String> DOMAIN_COLOR_MARKERS =
            List.of("data-terrain-color", "data-domain-color", "th:style", "th:attr=\"style",
                    // maps/editor.html persists these as drawing/terrain data (shape fill/stroke
                    // defaults and the terrain palette), so their lines are exempt too.
                    "selectedShape?.fill", "selectedShape?.stroke", "fill: '#", "newTerrainColor");

    @Test
    void noStylesheetOutsideTheTokenLayerDeclaresARawColor() {
        for (String file : CssRules.ALL_FILES) {
            if (file.equals("tokens.css")) continue;
            assertThat(CssRules.rawColorLiterals(CssRules.read(file)))
                    .as("raw color literals in %s — use a semantic token", file)
                    .isEmpty();
        }
    }

    @Test
    void templatesDoNotInlineRawUiColors() {
        for (java.nio.file.Path template : TemplateRules_allTemplates()) {
            String markup = readTemplate(template);
            for (String line : markup.split("\n")) {
                if (DOMAIN_COLOR_MARKERS.stream().anyMatch(line::contains)) continue;
                assertThat(CssRules.rawColorLiterals(line))
                        .as("raw UI color in %s: %s", template, line.trim())
                        .isEmpty();
            }
        }
    }

    // Inlined from the future TemplateRules (Task 10); replace when it lands.
    private static List<java.nio.file.Path> TemplateRules_allTemplates() {
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources/templates"))) {
            return files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String readTemplate(java.nio.file.Path template) {
        try {
            return java.nio.file.Files.readString(template);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
