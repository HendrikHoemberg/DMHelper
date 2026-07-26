package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1: system UI for chrome, Alegreya for prose, Cinzel for at most
 * the one primary title in a view. Everything else reads as decoration.
 */
class TypographyRoleContractTest {

    /** The only selectors allowed to reach for Cinzel. See the plan's interpretation note. */
    private static final Set<String> DISPLAY_SELECTORS = Set.of(
            "[data-display-title]",
            ".page-header h1",
            ".cockpit-topbar__title",
            ".navbar-brand",
            ".appnav-home",
            ".book-cover h3");

    /** Alegreya is for narrative prose and read-aloud, not for chrome. */
    private static final Set<String> BOOK_SELECTORS = Set.of(
            ".statblock-render",
            ".note-body",
            ".read-aloud",
            ".structured-read-aloud > p",
            ".scene-body",
            ".handout-prose");

    @Test
    void cinzelIsReservedForPrimaryTitles() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            String value = rule.value("font-family");
            if (value == null || !value.contains("var(--font-display)")) continue;
            if (!DISPLAY_SELECTORS.contains(rule.selector())) offenders.add(rule.where());
        }

        assertThat(offenders).as("Cinzel outside the primary-title role").isEmpty();
    }

    @Test
    void alegreyaIsReservedForProse() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            String value = rule.value("font-family");
            if (value == null || !value.contains("var(--font-book)")) continue;
            if (!BOOK_SELECTORS.contains(rule.selector())) offenders.add(rule.where());
        }

        assertThat(offenders).as("Alegreya outside narrative prose").isEmpty();
    }

    @Test
    void theDisplayTitleContractExists() {
        assertThat(CssRules.read("base.css"))
                .contains("[data-display-title] {")
                .contains("font-family: var(--font-display)");
    }
}
