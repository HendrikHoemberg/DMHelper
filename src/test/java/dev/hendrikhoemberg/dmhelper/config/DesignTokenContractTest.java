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

    private static String tokenValue(String name) {
        var matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(name) + "\\s*:\\s*([^;]+);")
                .matcher(CssRules.read("tokens.css"));
        assertThat(matcher.find()).as("%s is defined", name).isTrue();
        return matcher.group(1).trim();
    }

    @Test
    void semanticRolesKeepTheirApprovedValues() {
        assertThat(tokenValue("--surface-canvas")).isEqualTo("#101113");
        assertThat(tokenValue("--surface-navigation")).isEqualTo("#151619");
        assertThat(tokenValue("--surface-workspace")).isEqualTo("#18191d");
        assertThat(tokenValue("--surface-panel")).isEqualTo("#1d1f24");
        assertThat(tokenValue("--surface-raised")).isEqualTo("#24262c");
        assertThat(tokenValue("--surface-inset")).isEqualTo("#111216");
        assertThat(tokenValue("--border-subtle")).isEqualTo("#30333a");
        assertThat(tokenValue("--border-strong")).isEqualTo("#686c75");
        assertThat(tokenValue("--text-primary")).isEqualTo("#eee8dc");
        assertThat(tokenValue("--text-secondary")).isEqualTo("#b8b4aa");
        assertThat(tokenValue("--text-tertiary")).isEqualTo("#929089");
        assertThat(tokenValue("--action-primary")).isEqualTo("#c9a35c");
        assertThat(tokenValue("--action-primary-hover")).isEqualTo("#ddb977");
        assertThat(tokenValue("--state-success")).isEqualTo("#89ad69");
        assertThat(tokenValue("--state-warning")).isEqualTo("#e0a34d");
        assertThat(tokenValue("--state-danger")).isEqualTo("#d36a61");
        assertThat(tokenValue("--state-info")).isEqualTo("#74a3c1");
        assertThat(tokenValue("--state-shield")).isEqualTo("#8fa8b8");
        assertThat(tokenValue("--state-concentration")).isEqualTo("#b58ac1");
    }

    @Test
    void approvedTextAndStatePairsMeetWcagAa() {
        assertThat(ColorContrast.ratio("#eee8dc", "#101113")).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorContrast.ratio("#b8b4aa", "#18191d")).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorContrast.ratio("#929089", "#1d1f24")).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorContrast.ratio("#101113", "#c9a35c")).isGreaterThanOrEqualTo(4.5);
        for (String state : java.util.List.of(
                "#89ad69", "#e0a34d", "#d36a61", "#74a3c1", "#8fa8b8", "#b58ac1")) {
            assertThat(ColorContrast.ratio(state, "#1d1f24")).isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void strongInteractiveBoundaryMeetsThreeToOne() {
        assertThat(ColorContrast.ratio("#686c75", "#111216")).isGreaterThanOrEqualTo(3.0);
    }
}
