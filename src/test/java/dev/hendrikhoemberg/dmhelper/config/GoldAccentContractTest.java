package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Gold denotes focus, selection and primary actions rather
 * than bordering every card." A gold border everywhere is a gold border nowhere.
 */
class GoldAccentContractTest {

    private static final java.util.List<String> GOLD_ROLE_MARKERS = java.util.List.of(
            ".btn-primary", ":focus-visible", "[aria-current", "[aria-selected",
            ".is-selected", ".app-brand", "[data-display-title", ".page-header__title",
            "--focus-ring", "--selection-", "--action-primary");

    @Test
    void goldPaintsOnlyPrimaryActionSelectionAndFocus() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> java.util.stream.Stream
                        .of("background", "background-color", "border-color", "outline-color", "color")
                        .map(rule::value)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(value -> value.contains("--action-primary")
                                || value.contains("--selection-accent")
                                || value.contains("--focus-ring")))
                .forEach(rule -> assertThat(GOLD_ROLE_MARKERS)
                        .as("gold outside an approved role in %s", rule.where())
                        .anySatisfy(marker -> assertThat(rule.selector()).contains(marker)));
    }

    @Test
    void noGenericCardOrSeparatorIsGold() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().matches(".*\\.(card|panel|section|divider|rule)\\b.*"))
                .forEach(rule -> {
                    String border = rule.value("border");
                    String borderColor = rule.value("border-color");
                    assertThat(String.valueOf(border) + borderColor)
                            .as("decorative gold in %s", rule.where())
                            .doesNotContain("--action-primary")
                            .doesNotContain("--selection-accent");
                });
    }
}
