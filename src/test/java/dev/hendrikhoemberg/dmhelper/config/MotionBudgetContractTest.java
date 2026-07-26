package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Pattern DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s)\\b");
    private static final Pattern DURATION_TOKEN = Pattern.compile("(--duration-[a-z-]+)\\s*:\\s*([^;]+);");
    private static final Pattern TOKEN_REFERENCE = Pattern.compile("var\\((--duration-[a-z-]+)\\)");

    private static final Map<String, String> DURATION_TOKENS = durationTokens();

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
                Matcher m = DURATION.matcher(resolveDurationTokens(declaration));
                while (m.find()) {
                    double amountMs = Double.parseDouble(m.group(1)) * (m.group(2).equals("s") ? 1000 : 1);
                    if (amountMs > TABLE_BUDGET_MS) {
                        offenders.add(rule.where() + " → " + m.group() + " (" + amountMs + "ms)");
                    }
                }
            }
        }

        assertThat(offenders).as("motion that delays a table-time action").isEmpty();
    }

    @Test
    void secondsUnitsAreConvertedToMillisecondsForBudgetChecks() {
        assertThat(durationMilliseconds("animation: pulse 2s infinite")).containsExactly(2000.0);
        assertThat(durationMilliseconds("animation: skeleton-shimmer 1.4s ease-in-out infinite"))
                .containsExactly(1400.0);
        assertThat(durationMilliseconds("animation: pulse var(--duration-structural) infinite"))
                .containsExactly(320.0);
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

    private static Map<String, String> durationTokens() {
        Map<String, String> tokens = new HashMap<>();
        Matcher matcher = DURATION_TOKEN.matcher(CssRules.read("tokens.css"));
        while (matcher.find()) tokens.put(matcher.group(1), matcher.group(2).trim());
        return tokens;
    }

    private static String resolveDurationTokens(String declaration) {
        String resolved = declaration;
        for (int pass = 0; pass < DURATION_TOKENS.size(); pass++) {
            Matcher matcher = TOKEN_REFERENCE.matcher(resolved);
            StringBuffer next = new StringBuffer();
            boolean replaced = false;
            while (matcher.find()) {
                String value = DURATION_TOKENS.get(matcher.group(1));
                if (value == null) continue;
                matcher.appendReplacement(next, Matcher.quoteReplacement(value));
                replaced = true;
            }
            matcher.appendTail(next);
            resolved = next.toString();
            if (!replaced) break;
        }
        return resolved;
    }

    private static List<Double> durationMilliseconds(String declaration) {
        List<Double> durations = new ArrayList<>();
        Matcher matcher = DURATION.matcher(resolveDurationTokens(declaration));
        while (matcher.find()) {
            durations.add(Double.parseDouble(matcher.group(1)) * (matcher.group(2).equals("s") ? 1000 : 1));
        }
        return durations;
    }
}
