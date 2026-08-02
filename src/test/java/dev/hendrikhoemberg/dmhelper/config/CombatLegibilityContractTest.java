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
    void theActiveCombatantGetsSubstantialRowTreatment() {
        var active = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().contains(".combatant-row--active"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".combatant-row--active is not styled"));
        long signals = java.util.stream.Stream
                .of(active.value("background"), active.value("border-left"),
                        active.value("box-shadow"), active.value("outline"))
                .filter(java.util.Objects::nonNull)
                .count();
        assertThat(signals)
                .as("active turn needs more than a thin colored border (spec 12.5)")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void everyRuntimeStateCombinesColorWithSomethingElse() {
        String tracker = CssRules.allTemplateMarkup();
        for (String state : java.util.List.of("defeated", "concentrating", "unresolved-initiative")) {
            assertThat(tracker)
                    .as("state %s needs an icon or explicit label, not color alone", state)
                    .contains("data-combatant-state=\"" + state + "\"");
        }
    }

    @Test
    void combatNumbersUseTabularFigures() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).containsAnyOf(".combatant-hp", ".combatant-row");
            assertThat(rule.value("font-variant-numeric")).contains("tabular-nums");
        });
    }

}
