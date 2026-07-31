package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10: hierarchy is expressed through tokens. A var() that resolves
 * to nothing silently inherits, so the surface looks styled while carrying no decision.
 */
class DesignTokenContractTest {

    @Test
    void everyTokenReferencedByAStylesheetIsDefined() {
        Set<String> defined = CssRules.definedTokens();
        List<String> dangling = new ArrayList<>();

        for (String file : CssRules.ALL_FILES) {
            for (String token : CssRules.referencedTokens(CssRules.read(file))) {
                if (!defined.contains(token)) dangling.add(file + " → " + token);
            }
        }

        assertThat(dangling).as("var() references with no definition").isEmpty();
    }

    @Test
    void everyTokenReferencedByATemplateIsDefined() throws IOException {
        Set<String> defined = CssRules.definedTokens();
        List<String> dangling = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                for (String token : CssRules.referencedTokens(html)) {
                    if (!defined.contains(token)) dangling.add(template + " → " + token);
                }
            }
        }

        assertThat(dangling).as("var() references with no definition").isEmpty();
    }
}
