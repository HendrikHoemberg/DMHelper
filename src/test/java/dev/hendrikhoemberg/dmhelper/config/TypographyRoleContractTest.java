package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1: system UI for chrome, Alegreya for prose, Cinzel for at most
 * the one primary title in a view. Everything else reads as decoration.
 */
class TypographyRoleContractTest {

    @Test
    void cinzelIsReservedForTheWordmarkAndOnePrincipalTitle() {
        var displayRules = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String family = rule.value("font-family");
                    return family != null && family.contains("--font-display");
                })
                .toList();
        assertThat(displayRules)
                .as("Cinzel selectors")
                .allSatisfy(rule -> assertThat(rule.selector())
                        .as("Cinzel in %s", rule.where())
                        .containsAnyOf("data-display-title", ".app-brand", ".page-header__title"));
    }

    @Test
    void narrativeTypeIsScopedToNarrativeSurfaces() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String family = rule.value("font-family");
                    return family != null && family.contains("--font-body-serif");
                })
                .forEach(rule -> assertThat(rule.selector())
                        .as("Alegreya in %s", rule.where())
                        .containsAnyOf(".prose", ".read-aloud", ".narrative", ".statblock", ".book"));
    }

    @Test
    void tabularFiguresAreDeclaredForNumericSurfaces() {
        var tabular = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String value = rule.value("font-variant-numeric");
                    return value != null && value.contains("tabular-nums");
                })
                .map(CssRules.Rule::selector)
                .toList();
        assertThat(String.join(" ", tabular))
                .contains(".data-table")
                .contains(".u-num");
    }
}
