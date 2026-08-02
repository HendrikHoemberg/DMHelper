package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Gold denotes focus, selection and primary actions rather
 * than bordering every card." A gold border everywhere is a gold border nowhere.
 */
class GoldAccentContractTest {

    private static final java.util.List<String> GOLD_ALIASES = java.util.List.of(
            "--gold-sheen", "--gold-sweep");

    /**
     * Selectors where gold is one of the roles spec 5.3 grants it: the single primary action,
     * current selection, keyboard focus, campaign identity.
     */
    private static final java.util.List<String> GOLD_ROLE_MARKERS = java.util.List.of(
            ".btn-primary", ":focus-visible", ":focus", ":checked", "::selection",
            "[aria-current", "[aria-selected", "[aria-pressed", ".is-selected", ".app-brand",
            "[data-display-title", ".page-header__title", "--focus-ring", "--selection-",
            "--action-primary", ".combatant-row.active", ".turn-marker", "[data-dock-active",
            ".condition-chip", ".form-check", ".audio-btn-primary", ".form-tab.active",
            ".combatant-chip.active", ".note-type-chip.active", ".dice-toggle-btn.active",
            ".tool-btn.active", ".wizard-step.active", ".book-cover", ".campaign-sigil",
            ".terrain-swatch.active");

    /**
     * Decorative gold that spec 6.3 says to remove — "decorative gold borders, rules, and
     * generic badges are removed" — still standing because the surfaces belong to later
     * tasks. Task 23 owns the campaign and library cards, Task 53 the release sweep.
     *
     * <p>{@code .badge-info} — an <em>information</em> badge filled with the primary-action
     * colour — was the sharpest entry and is gone: Task 16 replaced the four legacy semantic
     * badge rules with the {@code .badge--*} tone set, which paints from {@code --state-*}.
     *
     * <p>This list is debt, not permission. It may only shrink, and
     * {@link #theAcknowledgedGoldDebtNeverGrows()} is what stops Part 2 from quietly
     * appending to it the way this list grew from eleven entries to thirty-four.
     */
    private static final java.util.List<String> GOLD_DEBT_MARKERS = java.util.List.of(
            ".library-chip--accent", ".dice-input-row", ".crit-high",
            ".statblock-render", ".read-aloud", ".structured-read-aloud",
            ".participant-statblock__name");

    private static final int GOLD_DEBT_BUDGET = 7;

    @Test
    void theAcknowledgedGoldDebtNeverGrows() {
        assertThat(GOLD_DEBT_MARKERS)
                .as("decorative gold awaiting Tasks 15/23/53 — budgets only ratchet down")
                .hasSizeLessThanOrEqualTo(GOLD_DEBT_BUDGET);
    }

    private static java.util.List<String> allowedGoldSelectors() {
        return java.util.stream.Stream
                .concat(GOLD_ROLE_MARKERS.stream(), GOLD_DEBT_MARKERS.stream())
                .toList();
    }

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
                .filter(rule -> allowedGoldSelectors().stream()
                        .noneMatch(marker -> rule.selector().contains(marker)))
                .map(CssRules.Rule::where)
                .toList();
        assertThat(offenders).as("gold outside an approved role").isEmpty();
    }

    /** Selection states the spec grants gold (section 5.3): a selected card is not a
     *  generic card, and its gold border is the selection affordance, not decoration. */
    private static final java.util.List<String> SELECTION_MARKERS = java.util.List.of(
            "[aria-current", "[aria-selected", "[aria-pressed", ":checked",
            ".is-selected", ".combatant-row.active", ".turn-marker");

    @Test
    void noGenericCardOrSeparatorIsGold() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().matches(".*\\.(card|panel|section|divider|rule)\\b.*"))
                .filter(rule -> SELECTION_MARKERS.stream()
                        .noneMatch(marker -> rule.selector().contains(marker)))
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
