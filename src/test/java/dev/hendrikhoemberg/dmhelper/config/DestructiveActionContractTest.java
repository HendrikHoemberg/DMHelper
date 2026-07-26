package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.3: "Destructive actions are visually distinct and never adjacent
 * to the most common runtime action without separation." At the table, a misclick is a
 * deleted encounter mid-combat.
 */
class DestructiveActionContractTest {

    /** btn-primary and btn-danger touching, with nothing between them. */
    private static final Pattern ADJACENT = Pattern.compile(
            "(?s)btn-primary[^>]*>.{0,400}?btn-danger|btn-danger[^>]*>.{0,400}?btn-primary");

    @Test
    void noPrimaryActionSitsBesideADestructiveOne() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                Matcher m = ADJACENT.matcher(html);
                while (m.find()) {
                    String span = m.group();
                    // A declared separator is exactly what makes this safe.
                    if (span.contains("action-row__destructive")) continue;
                    offenders.add(template.toString());
                    break;
                }
            }
        }

        assertThat(offenders)
                .as("primary and destructive actions adjacent without a declared separator")
                .isEmpty();
    }

    @Test
    void theSeparatorIsARealVisualGap() {
        CssRules.Rule rule = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".action-row__destructive"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".action-row__destructive is not defined"));

        assertThat(rule.body())
                .contains("margin-left: auto")
                .contains("padding-left: var(--space-lg)")
                .contains("border-left: 1px solid var(--color-border)");
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
