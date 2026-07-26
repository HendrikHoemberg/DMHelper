package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.3: "Modals, popovers, toasts and focused detail layers follow one
 * elevation and focus model." A ladder you can read beats 27 numbers you cannot.
 */
class ElevationModelContractTest {

    private static final List<String> LADDER = List.of(
            "--z-workspace-chrome", "--z-workspace-focus", "--z-workspace-menu",
            "--z-nav", "--z-popover", "--z-tooltip", "--z-ambient",
            "--z-sheet-scrim", "--z-sheet", "--z-overlay", "--z-modal",
            "--z-shortcut", "--z-safety-sweep", "--z-toast", "--z-filament",
            "--z-presentation-preview");

    @Test
    void theLadderIsDefinedInAscendingOrder() {
        String tokens = CssRules.read("tokens.css");
        int previousValue = Integer.MIN_VALUE;

        for (String rung : LADDER) {
            var matcher = java.util.regex.Pattern
                    .compile(java.util.regex.Pattern.quote(rung) + "\\s*:\\s*(\\d+)")
                    .matcher(tokens);
            assertThat(matcher.find()).as("%s defined", rung).isTrue();
            int value = Integer.parseInt(matcher.group(1));
            assertThat(value).as("%s above the rung below it", rung).isGreaterThan(previousValue);
            previousValue = value;
        }
    }

    @Test
    void noStylesheetInventsItsOwnElevation() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            for (String value : rule.values("z-index")) {
                String v = value.trim();
                if (v.startsWith("var(--z-")) continue;
                // Local stacking inside one component is fine; anything that competes
                // with another component's layer must name a rung.
                if (v.matches("-?[0-2]|auto|inherit")) continue;
                offenders.add(rule.where() + " → z-index: " + v);
            }
        }

        assertThat(offenders).as("raw elevations outside the ladder").isEmpty();
    }
}
