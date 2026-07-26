package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Gold denotes focus, selection and primary actions rather
 * than bordering every card." A gold border everywhere is a gold border nowhere.
 */
class GoldAccentContractTest {

    /** Selection, focus and primary-action states may border in gold. Nothing else may. */
    private static final List<String> EARNED = List.of(
            ":focus", "[aria-current", "[aria-selected", ".is-selected", ".active",
            ".btn-primary", ".audio-btn-primary", "::selection", "[data-display-title]");

    /** Reviewed exceptions: in-world surfaces whose gold edge is the identity itself. */
    private static final Set<String> IDENTITY_SURFACES = Set.of(
            ".book-cover",
            ".campaign-cover", // Campaign covers are in-world artifacts, not tool chrome.
            ".handout-overlay img", // A displayed handout keeps its material edge.
            ".statblock-render",
            ".read-aloud",
            ".rule-taper--gold",
            ".toast"); // The colored left rail communicates toast severity.

    @Test
    void goldBordersOnlyWhereItIsEarned() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            List<String> borderValues = new ArrayList<>();
            borderValues.addAll(rule.values("border"));
            borderValues.addAll(rule.values("border-color"));
            borderValues.addAll(rule.values("border-left"));
            borderValues.addAll(rule.values("border-bottom"));

            boolean gold = borderValues.stream().anyMatch(v ->
                    v.contains("--color-accent") || v.contains("--color-gold-soft")
                            || v.contains("gold-sheen"));
            if (!gold) continue;

            boolean earned = EARNED.stream().anyMatch(token -> rule.selector().contains(token))
                    || IDENTITY_SURFACES.stream().anyMatch(s -> rule.selector().contains(s));
            if (!earned) offenders.add(rule.where());
        }

        assertThat(offenders).as("gold borders that mean nothing").isEmpty();
    }

    @Test
    void aNeutralHoverHairlineExists() {
        assertThat(CssRules.read("tokens.css")).contains("--color-border-strong:");
    }

    @Test
    void cardHoverUsesTheNeutralHairline() {
        CssRules.Rule hover = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".card:hover"))
                .findFirst()
                .orElseThrow();

        assertThat(hover.value("border-color")).isEqualTo("var(--color-border-strong)");
    }
}
