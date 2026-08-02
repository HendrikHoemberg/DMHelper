package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 18.1 and acceptance criterion 11: completed migration leaves one semantic
 * token system.
 */
class LegacyVisualAliasRemovalTest {

    @Test
    void noLegacyAliasIsStillDefined() {
        assertThat(CssRules.read("tokens.css"))
                .as("the migration bridge must be gone")
                .doesNotContain("--color-");
    }

    @Test
    void noLegacyAliasIsStillReferenced() {
        String source = CssRules.allApplicationCss() + "\n" + CssRules.allTemplateMarkup();
        var matcher = java.util.regex.Pattern.compile("var\\(\\s*(--color-[a-z0-9-]+)")
                .matcher(source);
        var offenders = matcher.results().map(r -> r.group(1))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        assertThat(offenders).as("remaining legacy references").isEmpty();
    }
}
