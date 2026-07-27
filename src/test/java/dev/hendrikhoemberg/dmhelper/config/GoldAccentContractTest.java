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

    private static final List<String> RENDERED_PROPERTIES = List.of(
            "color", "background", "background-color", "background-image",
            "border", "border-color", "border-top", "border-top-color",
            "border-right", "border-right-color", "border-bottom", "border-bottom-color",
            "border-left", "border-left-color", "border-inline", "border-inline-color",
            "border-inline-start", "border-inline-start-color", "border-inline-end",
            "border-inline-end-color", "border-block", "border-block-color",
            "border-block-start", "border-block-start-color", "border-block-end",
            "border-block-end-color", "outline", "outline-color", "box-shadow",
            "text-shadow", "accent-color",
            // This custom property is consumed by rendered declarations in the same
            // stylesheet and must not provide a way around the visual-property contract.
            "--library-chip-color");

    private static final List<String> GOLD_REFERENCES = List.of(
            "--color-accent", "--color-gold-soft", "--gold-sheen", "--gold-sweep",
            "--color-attack-bonus", "--color-info", "--color-condition-active",
            "--color-legendary", "--color-combatant-active");

    /** Reviewed semantic states. Deliberately no generic ".active" exemption. */
    private static final List<String> EARNED_STATES = List.of(
            ":focus", ":focus-visible", ":checked", "::selection", "[aria-current",
            "[aria-selected", ".is-selected", ".btn-primary", ".audio-btn-primary",
            ".form-tab.active", ".combatant-row.active", ".combatant-chip.active",
            ".note-type-chip.active", ".dice-toggle-btn.active", ".tool-btn.active",
            ".wizard-step.active", "[data-dock-active=\"true\"]", ".turn-marker",
            ".dice-input-row button", ".library-chip--accent");

    /** Reviewed exceptions: in-world surfaces whose gold edge is the identity itself. */
    private static final Set<String> IDENTITY_SURFACES = Set.of(
            ".book-cover",
            ".campaign-cover", // Campaign covers are in-world artifacts, not tool chrome.
            ".handout-overlay img", // A displayed handout keeps its material edge.
            ".statblock-render",
            ".participant-statblock__name", // Inline participant statblock identity.
            ".read-aloud",
            ".structured-read-aloud", // Structured read-aloud content is an in-world text surface.
            ".badge-info", // Read-aloud labels are an in-world identity surface.
            ".campaign-sigil", // Campaign marks belong to the in-world cover identity.
            ".dice-history-item .value.crit-high",
            ".dice-result-total.crit-high"); // Critical results are explicitly gold in the dice spec.

    @Test
    void goldOnlyPaintsReviewedSemanticOrIdentitySurfaces() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            boolean gold = RENDERED_PROPERTIES.stream()
                    .flatMap(property -> rule.values(property).stream())
                    .anyMatch(GoldAccentContractTest::containsGoldReference);
            if (!gold) continue;

            for (String selector : splitSelectors(rule.selector())) {
                String trimmedSelector = selector.trim();
                boolean earned = EARNED_STATES.stream().anyMatch(trimmedSelector::contains)
                        || IDENTITY_SURFACES.stream().anyMatch(trimmedSelector::contains);
                if (!earned) {
                    List<String> properties = RENDERED_PROPERTIES.stream()
                            .filter(property -> rule.values(property).stream()
                                    .anyMatch(GoldAccentContractTest::containsGoldReference))
                            .toList();
                    offenders.add(rule.file() + " { " + trimmedSelector + " } " + properties);
                }
            }
        }

        assertThat(offenders).as("gold declarations that mean nothing").isEmpty();
    }

    @Test
    void partyMemberNamesUseNeutralTextInsteadOfAmbientGold() {
        CssRules.Rule rule = findRule("components.css", ".party-member-chip .chip-name");

        assertThat(rule.value("color")).isEqualTo("var(--color-text)");
    }

    @Test
    void appNavigationCollapseHoverUsesNeutralChromeInsteadOfAmbientGold() {
        CssRules.Rule rule = findRule("base.css", ".appnav-collapse:hover");

        assertThat(rule.value("color")).isEqualTo("var(--color-text)");
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
        CssRules.Rule hover = findRule("components.css", ".card:hover");

        assertThat(hover.value("border-color")).isEqualTo("var(--color-border-strong)");
    }

    private static boolean containsGoldReference(String value) {
        return GOLD_REFERENCES.stream().anyMatch(value::contains);
    }

    private static CssRules.Rule findRule(String file, String selector) {
        return CssRules.of(file).stream()
                .filter(rule -> rule.selector().equals(selector))
                .findFirst()
                .orElseThrow();
    }
}
