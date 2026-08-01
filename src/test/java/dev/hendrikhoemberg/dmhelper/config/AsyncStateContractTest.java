package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 16: every asynchronous surface defines its full state set. */
class AsyncStateContractTest {

    @Test
    void loadingAndLiveRegionsAreAnnounced() {
        String states = TemplateRules.read(TemplateRules.ROOT.resolve("fragments/_states.html"));
        assertThat(states).contains("aria-live=\"polite\"").contains("role=\"status\"");
        assertThat(TemplateRules.read(TemplateRules.ROOT.resolve("fragments/_banner.html")))
                .contains("role=\"alert\"");
    }

    @Test
    void recoverableFailuresOfferRetry() {
        String states = TemplateRules.read(TemplateRules.ROOT.resolve("fragments/_states.html"));
        assertThat(states).contains("th:fragment=\"failed(title, description, retry)\"")
                .contains("th:fragment=\"unavailable(title, description, retry)\"");
    }

    @Test
    void skeletonsReserveLayoutRatherThanCollapseIt() {
        var skeleton = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().contains(".skeleton"))
                .toList();
        assertThat(skeleton).as("skeleton rules").isNotEmpty();
        assertThat(skeleton).anySatisfy(rule -> assertThat(rule.value("min-height")).isNotNull());
    }

    @Test
    void everyHtmxTargetDeclaresAnIndicatorOrSwapPreservingRegion() {
        for (Path template : TemplateRules.allTemplates()) {
            var document = TemplateRules.parse(template);
            for (var trigger : document.select("[hx-get], [hx-post], [hx-put], [hx-delete]")) {
                boolean declared = trigger.hasAttr("hx-indicator")
                        || trigger.hasAttr("hx-disabled-elt")
                        || trigger.hasAttr("hx-sync")
                        || trigger.hasAttr("data-no-indicator");
                assertThat(declared)
                        .as("%s: %s needs hx-indicator, hx-disabled-elt, hx-sync, or an explicit "
                                + "data-no-indicator opt-out", template, trigger.tagName())
                        .isTrue();
            }
        }
    }
}
