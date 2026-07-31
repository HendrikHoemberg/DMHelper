package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1. Two rules:
 *   1. the type scale lives in tokens.css and nowhere else;
 *   2. on runtime surfaces nothing required drops below --text-sm.
 *
 * The allowlist below is the reviewed answer to "truly secondary metadata". Adding to it is
 * a deliberate act; that is the point.
 */
class TypeScaleContractTest {

    private static final Set<String> SECONDARY_METADATA = Set.of(
            ".runtime-party .chip-detail",
            ".runtime-party .chip-status",
            ".runtime-session-log .session-log-status",
            ".runtime-session-log .session-log-time",
            ".runtime-session-log .session-log-section-title",
            ".runtime-session-log .log-event-time",
            ".runtime-session-log .log-event-detail",
            ".runtime-session-log .session-log-unresolved-badge",
            ".runtime-session-log .empty-state-subtle",
            ".session-plan-module-inner .beat-broken-label",
            "[data-module-remove], [data-module-retry]",
            ".scene-status-badge",
            ".beat-type",
            ".audio-source, .audio-owner",
            ".planned-encounter-row__meta",
            ".encounter-chip",
            ".group-count",
            // A count badge on a module tab and a rail section label are orientation, not
            // content: both sit beside the title they qualify and are reviewed as secondary.
            ".cockpit-module__attention",
            ".planned-encounter-section-label");

    @Test
    void onlyTokensCssCarriesAnAbsoluteFontSize() {
        List<String> offenders = new ArrayList<>();

        for (String file : CssRules.ALL_FILES) {
            if (file.equals("tokens.css")) continue;
            for (CssRules.Rule rule : CssRules.of(file)) {
                for (String value : rule.values("font-size")) {
                    if (value.matches(".*\\d\\s*(px|rem)\\b.*")) {
                        offenders.add(rule.where() + " → font-size: " + value);
                    }
                }
            }
        }

        assertThat(offenders).as("absolute font sizes outside tokens.css").isEmpty();
    }

    @Test
    void runtimeSurfacesStayAtOrAboveTextSmUnlessAllowlisted() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.RUNTIME_FILES)) {
            String value = rule.value("font-size");
            if (value == null || !value.contains("var(--text-xs)")) continue;
            if (!SECONDARY_METADATA.contains(rule.selector())) {
                offenders.add(rule.where());
            }
        }

        assertThat(offenders)
                .as("runtime text below --text-sm that is not reviewed secondary metadata")
                .isEmpty();
    }

    @Test
    void noTemplateSetsAFontSizeInline() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                if (Files.readString(template).matches("(?s).*style=\"[^\"]*font-size[^\"]*\".*")) {
                    offenders.add(template.toString());
                }
            }
        }

        assertThat(offenders).as("inline font-size defeats the scale").isEmpty();
    }

    @Test
    void noTemplateEmbeddedStyleCarriesARawFontSize() throws IOException {
        Pattern rawFontSize = Pattern.compile(
                "(?s)<style\\b[^>]*>.*?font-size\\s*:\\s*[^;}]*(?:px|rem|em)\\b.*?</style>");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                if (rawFontSize.matcher(Files.readString(template)).find()) {
                    offenders.add(template.toString());
                }
            }
        }

        assertThat(offenders).as("raw font sizes in embedded template styles").isEmpty();
    }
}
