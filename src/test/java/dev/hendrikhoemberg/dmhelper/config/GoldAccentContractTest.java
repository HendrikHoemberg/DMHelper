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
            ".structured-read-aloud", // Structured read-aloud content is an in-world text surface.
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
            borderValues.addAll(rule.values("border-top-color"));
            borderValues.addAll(rule.values("border-right-color"));
            borderValues.addAll(rule.values("border-bottom-color"));
            borderValues.addAll(rule.values("border-left-color"));

            boolean gold = borderValues.stream().anyMatch(v ->
                    v.contains("--color-accent") || v.contains("--color-gold-soft")
                            || v.contains("gold-sheen"));
            if (!gold) continue;

            for (String selector : splitSelectors(rule.selector())) {
                String trimmedSelector = selector.trim();
                boolean earned = EARNED.stream().anyMatch(trimmedSelector::contains)
                        || IDENTITY_SURFACES.stream().anyMatch(trimmedSelector::contains);
                if (!earned) offenders.add(rule.file() + " { " + trimmedSelector + " }");
            }
        }

        assertThat(offenders).as("gold borders that mean nothing").isEmpty();
    }

    private static List<String> splitSelectors(String selectorList) {
        List<String> selectors = new ArrayList<>();
        int start = 0;
        int parentheses = 0;
        for (int i = 0; i < selectorList.length(); i++) {
            char c = selectorList.charAt(i);
            if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (c == ',' && parentheses == 0) {
                selectors.add(selectorList.substring(start, i));
                start = i + 1;
            }
        }
        selectors.add(selectorList.substring(start));
        return selectors;
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
