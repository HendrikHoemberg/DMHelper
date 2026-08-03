package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 14. Kept deliberately narrow: an unlabelled control and a confirmation that
 * does not name its consequence are defects no screenshot review reliably catches. Action
 * placement and destructive separation are reviewed against the capture set instead — the
 * character-distance heuristic that used to live here produced both false passes and false
 * failures.
 */
class FormContractTest {

    @Test
    void everyControlHasALabelInTheSameOrder() {
        for (Path template : TemplateRules.allTemplates()) {
            var document = TemplateRules.parse(template);
            for (var control : document.select("input:not([type=hidden]), select, textarea")) {
                String id = control.attr("id");
                boolean labelled = (!id.isBlank() && !document.select("label[for=" + id + "]").isEmpty())
                        || control.hasAttr("aria-label")
                        || control.hasAttr("aria-labelledby")
                        || !control.parents().select("label").isEmpty();
                assertThat(labelled)
                        .as("%s: unlabelled control %s", template, control.outerHtml())
                        .isTrue();
            }
        }
    }

    @Test
    void fieldErrorsSitBesideTheirControlAndAreAnnounced() {
        String fragment = TemplateRules.read(TemplateRules.ROOT.resolve("common/_field-error.html"));
        assertThat(fragment).contains("role=\"alert\"").contains("aria-live");
    }

    /**
     * Spec 14: a confirmation names the affected entity and the consequence. The entity is
     * editorial and reviewed against the capture set; the consequence is structural, because
     * it is a separate declaration the trigger either carries or does not.
     *
     * <p>The previous version of this test keyed off the literal string {@code data-confirm}
     * and so skipped every {@code hx-confirm} in the product — 41 triggers across 35
     * templates — because {@code hx-confirm} does not contain it. It could not fail.
     */
    @Test
    void everyConfirmationTriggerDeclaresItsConsequence() {
        List<String> offenders = new ArrayList<>();
        int triggers = 0;

        for (Path template : TemplateRules.allTemplates()) {
            for (Element element : TemplateRules.parse(template).getAllElements()) {
                if (!asksForConfirmation(element)) continue;
                triggers++;
                if (!declaresConsequence(element)) {
                    offenders.add(template + ": " + element.outerHtml());
                }
            }
        }

        assertThat(triggers)
                .as("the confirmation scan must find the product's confirmations to mean anything")
                .isGreaterThan(30);
        assertThat(offenders)
                .as("confirmations with no data-confirm-consequence")
                .isEmpty();
    }

    private static boolean asksForConfirmation(Element element) {
        return element.hasAttr("hx-confirm")
                || element.hasAttr("th:hx-confirm")
                || element.hasAttr("data-confirm");
    }

    /** Declared directly, through Thymeleaf's generic attribute form, or via th:attr. */
    private static boolean declaresConsequence(Element element) {
        return element.hasAttr("data-confirm-consequence")
                || element.hasAttr("th:data-confirm-consequence")
                || element.attr("th:attr").contains("data-confirm-consequence=");
    }
}
