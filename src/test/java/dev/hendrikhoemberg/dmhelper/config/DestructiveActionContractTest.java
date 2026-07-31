package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.3: "Destructive actions are visually distinct and never adjacent
 * to the most common runtime action without separation." At the table, a misclick is a
 * deleted encounter mid-combat.
 */
class DestructiveActionContractTest {

    @Test
    void unrelatedSeparatorDoesNotMaskAdjacentActions() {
        String fixture = """
                <div class=\"action-row\">
                    <button class=\"btn btn-primary\">Save</button>
                    <span class=\"action-row__destructive\">Unrelated marker</span>
                    <button class=\"btn btn-danger\">Delete</button>
                </div>
                """;

        assertThat(unsafeRows(fixture))
                .as("an unrelated separator must not satisfy the adjacent-action contract")
                .containsExactly("destructive control without an associated separator");
    }

    @Test
    void missingSeparatorDeclarationFailsEvenWithoutAPrimaryInTheSameRow() {
        String fixture = "<div class=\"separate-row\"><button class=\"btn btn-danger\">Delete</button></div>";

        assertThat(unsafeRows(fixture))
                .as("every destructive control must declare its own visual separator")
                .containsExactly("destructive control without an associated separator");
    }

    @Test
    void noPrimaryActionSitsBesideADestructiveOne() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                for (String ignored : unsafeRows(Files.readString(template))) {
                    offenders.add(template.toString());
                    break;
                }
            }
        }

        assertThat(offenders)
                .as("primary and destructive actions adjacent without a declared separator")
                .isEmpty();
    }

    /**
     * Check every destructive control. A separator only counts when it is declared on the
     * control or on an ancestor between that control and its nearest action-row boundary;
     * sibling content elsewhere cannot mask the missing declaration.
     */
    private static List<String> unsafeRows(String html) {
        Document document = Jsoup.parse(html);
        List<String> offenders = new ArrayList<>();

        for (Element destructive : document.select(".btn-danger")) {
            if (!hasDeclaredSeparator(destructive)) {
                offenders.add("destructive control without an associated separator");
            }
        }

        return offenders;
    }

    private static boolean hasDeclaredSeparator(Element destructive) {
        if (destructive.hasClass("action-row__destructive")) return true;

        for (Element ancestor : destructive.parents()) {
            if (ancestor.hasClass("action-row__destructive")) return true;
            if (ancestor.hasClass("action-row")) return false;
        }

        return false;
    }

    /**
     * The separator must be a real gap, not just a declared class. What that gap is made of
     * is a visual decision; that it exists at all is the safety contract.
     */
    @Test
    void theSeparatorIsARealVisualGap() {
        CssRules.Rule rule = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".action-row__destructive"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".action-row__destructive is not defined"));

        assertThat(rule.declares("padding-left") || rule.declares("margin-left")
                || rule.declares("border-left"))
                .as(".action-row__destructive must create horizontal separation")
                .isTrue();
    }

    @Test
    void destructiveButtonsAreDistinctFromEverythingElse() {
        CssRules.Rule danger = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".btn-danger"))
                .findFirst()
                .orElseThrow();

        assertThat(danger.body()).contains("color: var(--color-danger)");
    }
}
