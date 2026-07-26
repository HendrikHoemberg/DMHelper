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
 * Spec 2026-07-22 section 10.3: consistent dark controls, labels and focus rings. A surface
 * that ships without a stylesheet is not a styling omission — it is a surface that looks
 * like a different application.
 */
class ControlConsistencyContractTest {

    @Test
    void everyButtonInAGovernedTemplateDeclaresItsRole() throws IOException {
        Pattern button = Pattern.compile("<button(?![^>]*\\bclass=)[^>]*>");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                Matcher m = button.matcher(Files.readString(template));
                while (m.find()) offenders.add(template + " → " + m.group());
            }
        }

        assertThat(offenders).as("buttons with no btn class").isEmpty();
    }

    @Test
    void theReadinessReportIsStyled() {
        String surfaces = CssRules.read("surfaces.css");

        assertThat(surfaces).contains(
                ".readiness-panel {",
                ".readiness-panel.is-blocked",
                ".readiness-panel.is-ready",
                ".readiness-badge {",
                ".readiness-item {",
                ".readiness-item--blocker",
                ".readiness-item__title",
                ".readiness-item__detail",
                ".readiness-item__actions");
    }

    @Test
    void readinessControlsUseTheAppVocabulary() throws IOException {
        String readiness = Files.readString(
                Path.of("src/main/resources/templates/campaigns/_readiness.html"));

        assertThat(readiness)
                .contains("class=\"btn btn-primary btn-xs\">Accept<")
                .contains("class=\"btn btn-xs\">Set kind<")
                .contains("class=\"form-input form-input--xs\"")
                .contains("class=\"btn btn-ghost btn-xs readiness-item__repair\"");
    }

    @Test
    void aCompactFormInputVariantExists() {
        assertThat(CssRules.read("components.css")).contains(".form-input--xs {");
    }

    /** Spec section 10.3: "Primary actions are singular and obvious within each module." */
    @Test
    void noSurfaceOffersTwoPrimaryActions() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                int count = html.split("btn-primary", -1).length - 1;
                if (count > 1) offenders.add(template + " → " + count + " primary actions");
            }
        }

        assertThat(offenders).as("more than one primary action in one template").isEmpty();
    }

    /** Spec section 10.3: consistent error placement — one fragment, used everywhere. */
    @Test
    void fieldErrorsUseTheSharedFragment() throws IOException {
        assertThat(Files.readString(Path.of("src/main/resources/templates/common/_field-error.html")))
                .contains("th:fragment=\"field-error(field)\"")
                .contains("class=\"field-error\"")
                .contains("role=\"alert\"");

        assertThat(CssRules.read("components.css"))
                .contains(".field-error {")
                .contains("color: var(--color-danger)");

        Pattern adHoc = Pattern.compile("th:errors=\"\\*\\{[^}]+}\"");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                if (adHoc.matcher(html).find() && !html.contains("common/_field-error")) {
                    offenders.add(template.toString());
                }
            }
        }

        assertThat(offenders).as("errors rendered outside the shared fragment").isEmpty();
    }
}
