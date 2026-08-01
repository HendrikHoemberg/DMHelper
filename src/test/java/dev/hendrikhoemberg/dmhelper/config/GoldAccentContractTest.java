package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Gold denotes focus, selection and primary actions rather
 * than bordering every card." A gold border everywhere is a gold border nowhere.
 */
class GoldAccentContractTest {

    private static final java.util.List<String> GOLD_ALIASES = java.util.List.of(
            "--color-accent", "--color-gold-soft", "--gold-sheen", "--gold-sweep",
            "--color-attack-bonus", "--color-combatant-active", "--color-condition-active",
            "--color-legendary");

    private static final java.util.List<String> GOLD_ROLE_MARKERS = java.util.List.of(
            ".btn-primary", ":focus-visible", ":focus", ":checked", "::selection",
            "[aria-current", "[aria-selected", ".is-selected", ".app-brand",
            "[data-display-title", ".page-header__title", "--focus-ring", "--selection-",
            "--action-primary", ".combatant-row.active", ".turn-marker", "[data-dock-active",
            ".condition-chip", ".form-check", ".audio-btn-primary", ".form-tab.active",
            ".combatant-chip.active", ".note-type-chip.active", ".dice-toggle-btn.active",
            ".tool-btn.active", ".wizard-step.active", ".library-chip--accent",
            ".dice-input-row", ".badge-info", ".crit-high", ".book-cover", ".campaign-sigil",
            ".statblock-render", ".read-aloud", ".structured-read-aloud",
            ".participant-statblock__name");

    @Test
    void goldPaintsOnlyPrimaryActionSelectionAndFocus() {
        java.util.List<String> offenders = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> java.util.stream.Stream
                        .of("background", "background-color", "border-color", "outline-color", "color")
                        .map(rule::value)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(value -> value.contains("--action-primary")
                                || value.contains("--selection-accent")
                                || value.contains("--focus-ring")
                                || GOLD_ALIASES.stream().anyMatch(value::contains)))
                .filter(rule -> GOLD_ROLE_MARKERS.stream()
                        .noneMatch(marker -> rule.selector().contains(marker)))
                .map(CssRules.Rule::where)
                .toList();
        assertThat(offenders).as("gold outside an approved role").isEmpty();
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
