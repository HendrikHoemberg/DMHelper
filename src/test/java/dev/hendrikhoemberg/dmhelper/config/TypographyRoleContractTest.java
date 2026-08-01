package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1: system UI for chrome, Alegreya for prose, Cinzel for at most
 * the one primary title in a view. Everything else reads as decoration.
 */
class TypographyRoleContractTest {

    /**
     * Rules whose font-family reaches the named face, by any of its token names. Scanning for
     * --font-body-serif alone saw one rule: the narrative surfaces in book.css go through the
     * --font-book alias, so the alias hop hid them from the contract entirely.
     */
    private static java.util.List<CssRules.Rule> rulesUsing(String... fontTokens) {
        var rules = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String family = rule.value("font-family");
                    return family != null
                            && java.util.Arrays.stream(fontTokens).anyMatch(family::contains);
                })
                .toList();
        assertThat(rules)
                .as("no rule uses %s at all — this scan would pass on an empty set",
                        java.util.Arrays.toString(fontTokens))
                .isNotEmpty();
        return rules;
    }

    /**
     * This can only police which <em>selector</em> is allowed to reach for the display face.
     * How many elements carry that selector is a markup property and is budgeted per route in
     * VisualFoundationRenderGateTest, because spec 7.1's real rule — one principal title per
     * view — is invisible from here.
     */
    @Test
    void cinzelIsReservedForTheWordmarkAndOnePrincipalTitle() {
        assertThat(rulesUsing("--font-display"))
                .as("Cinzel selectors")
                .allSatisfy(rule -> assertThat(rule.selector())
                        .as("Cinzel in %s", rule.where())
                        .containsAnyOf("data-display-title", ".app-brand", ".page-header__title"));
    }

    /** Narrative surfaces per spec 7.1: prose, read-aloud, in-world notes, document material. */
    @Test
    void narrativeTypeIsScopedToNarrativeSurfaces() {
        assertThat(rulesUsing("--font-body-serif", "--font-book"))
                .as("Alegreya selectors")
                .allSatisfy(rule -> assertThat(rule.selector())
                        .as("Alegreya in %s", rule.where())
                        .containsAnyOf(".prose", ".read-aloud", ".narrative", ".statblock",
                                ".book", ".note-body"));
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
