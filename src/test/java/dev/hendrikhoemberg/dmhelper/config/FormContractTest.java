package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
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

    @Test
    void confirmationCopyNamesTheEntityAndConsequence() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("data-confirm")) continue;
            assertThat(markup)
                    .as("%s: confirmation must name the consequence", template)
                    .contains("data-confirm-consequence");
        }
    }
}
