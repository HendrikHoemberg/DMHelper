package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2 and section 11.2: motion confirms a state change and then gets
 * out of the way, and a DM who asked the OS for less motion gets less motion everywhere.
 */
class MotionBudgetContractTest {

    private static final int TABLE_BUDGET_MS = 320;

    /** Theatrical by design and never in the path of a table-time action. */
    private static final Set<String> THEATRICAL = Set.of(
            ".handout-overlay .handout-frame",
            ".handout-overlay.closing .handout-frame",
            // The campaign cover opens into the dashboard as a deliberate navigation gesture.
            "::view-transition-group(campaign-cover)",
            "::view-transition-old(campaign-cover), ::view-transition-new(campaign-cover)",
            // The handout unfolds onto the table screen and is outside table-time controls.
            ".handout-overlay img",
            ".handout-overlay.closing img",
            ".page-header .rule-taper--gold");

    private static final Pattern MS = Pattern.compile("(\\d+)ms");

    @Test
    void interactionFeedbackStaysInsideTheTableBudget() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            if (THEATRICAL.contains(rule.selector())) continue;
            if (rule.selector().startsWith("@keyframes")) continue;

            List<String> declarations = new ArrayList<>();
            declarations.addAll(rule.values("transition"));
            declarations.addAll(rule.values("animation"));
            declarations.addAll(rule.values("animation-duration"));
            declarations.addAll(rule.values("transition-duration"));

            for (String declaration : declarations) {
                if (declaration.contains("--duration-theatrical")) {
                    offenders.add(rule.where() + " → theatrical duration on a control");
                    continue;
                }
                Matcher m = MS.matcher(declaration);
                while (m.find()) {
                    if (Integer.parseInt(m.group(1)) > TABLE_BUDGET_MS) {
                        offenders.add(rule.where() + " → " + m.group() + " (raw)");
                    }
                }
            }
        }

        assertThat(offenders).as("motion that delays a table-time action").isEmpty();
    }

    @Test
    void reducedMotionNeutralisesEveryAnimationGlobally() {
        String base = CssRules.read("base.css");

        assertThat(base).contains("@media (prefers-reduced-motion: reduce)");
        assertThat(base).contains("""
                  *, *::before, *::after {
                    animation-duration: 1ms !important;
                    animation-iteration-count: 1 !important;
                    transition-duration: 1ms !important;
                    scroll-behavior: auto !important;
                  }""");
    }
}
