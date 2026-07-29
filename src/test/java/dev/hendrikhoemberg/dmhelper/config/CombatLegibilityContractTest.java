package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1: "Numeric combat values use tabular figures and remain
 * legible at arm's length." The tracker is where that sentence is either true or false.
 */
class CombatLegibilityContractTest {

    private static final List<String> COMBAT_NUMERALS =
            List.of(".init-badge", ".combatant-hp", ".hp-display", ".hp-delta-input, .detail-hp input");

    @Test
    void everyCombatNumeralIsTabularAndAtLeastTextBase() {
        for (String selector : COMBAT_NUMERALS) {
            CssRules.Rule rule = CssRules.of("components.css").stream()
                    .filter(r -> r.selector().equals(selector))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing rule: " + selector));

            assertThat(rule.body())
                    .as("%s tabular figures", selector)
                    .contains("font-variant-numeric: tabular-nums");
            assertThat(rule.value("font-size"))
                    .as("%s size", selector)
                    .isIn("var(--text-base)", "var(--text-lg)");
        }
    }

    @Test
    void theTrackerRowShowsANumericHpReadout() throws IOException {
        String tracker = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"));

        assertThat(tracker)
                .contains("class=\"combatant-hp u-num\"")
                .contains("x-text=\"hpLabel(c)\"");
    }

    @Test
    void hpLabelHandlesMissingMaximumsWithoutPrintingNull() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/combat-tracker.js"));

        assertThat(js)
                .contains("hpLabel(c)")
                .contains("if (!c || c.currentHp == null) return '—';");
    }
}
