package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 15: five levels, shared accessibility rules. */
class OverlayContractTest {

    private static final Path OVERLAY =
            Path.of("src/main/resources/templates/fragments/_overlay.html");

    @Test
    void blockingOverlaysDeclareRoleAndAccessibleName() {
        var document = TemplateRules.parse(OVERLAY);
        var dialog = document.selectFirst("[th:fragment^=dialog]");
        assertThat(dialog).isNotNull();
        assertThat(dialog.attr("role")).isEqualTo("dialog");
        assertThat(dialog.attr("aria-modal")).isEqualTo("true");
        assertThat(dialog.hasAttr("aria-labelledby")).isTrue();
    }

    @Test
    void everyOverlayLevelHasItsOwnElevationAndScrimRule() {
        String css = CssRules.allApplicationCss();
        for (String level : List.of(".popover", ".side-sheet", ".dialog", ".toast")) {
            assertThat(css).as("style for %s", level).contains(level);
        }
        var dialog = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().equals(".dialog__backdrop"))
                .findFirst().orElseThrow();
        assertThat(dialog.value("background")).contains("--scrim");
    }

    @Test
    void aPersistentErrorIsNeverOnlyAToast() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("dmToast.show")) continue;
            assertThat(markup)
                    .as("%s raises a toast; persistent failure also needs a banner or state "
                            + "region (spec 15)", template)
                    .containsAnyOf("_banner :: banner", "_states :: failed", "_states :: unavailable");
        }
    }

    @Test
    void theHiddenAttributeActuallyHidesEveryOverlayLevel() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        for (String level : List.of(".dialog", ".side-sheet", ".popover")) {
            assertThat(rules)
                    .as("%s[hidden] must reset display", level)
                    .anySatisfy(rule -> {
                        assertThat(rule.selector()).contains(level + "[hidden]");
                        assertThat(rule.value("display")).isEqualTo("none");
                    });
        }
    }

    @Test
    void overlaysBoundThemselvesToTheViewport() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        for (String level : List.of(".dialog__panel", ".popover")) {
            var rule = rules.stream().filter(r -> r.selector().equals(level)).findFirst().orElseThrow();
            assertThat(rule.value("max-height")).as("%s max-height", level).isNotNull();
            assertThat(rule.value("overflow")).as("%s overflow", level).isNotNull();
        }
        var sheet = rules.stream()
                .filter(rule -> rule.selector().equals(".side-sheet[role=\"complementary\"]"))
                .findFirst().orElseThrow();
        assertThat(sheet.value("max-height")).as("shared side-sheet max-height").isNotNull();
        assertThat(sheet.value("overflow")).as("shared side-sheet overflow").isNotNull();
    }
}
