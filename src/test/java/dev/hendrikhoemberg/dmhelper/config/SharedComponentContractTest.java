package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 10: features specialize content, never recreate the primitives. */
class SharedComponentContractTest {

    private static final List<String> REQUIRED_FRAGMENTS = List.of(
            "fragments/_page-header.html|page-header(title, summary, breadcrumb, primary, secondary)",
            "fragments/_toolbar.html|toolbar(action, searchValue, searchPlaceholder, filters, actions)",
            "fragments/_toolbar.html|table-toolbar(selectionLabel, actions)",
            "fragments/_badge.html|badge(tone, icon, label)",
            "fragments/_context-rail.html|rail(body)",
            "fragments/_context-rail.html|rail-section(title, body)",
            "fragments/_states.html|empty(icon, title, description, cta)",
            "fragments/_states.html|loading(label)",
            "fragments/_states.html|skeleton(count)",
            "fragments/_states.html|unavailable(title, description, retry)",
            "fragments/_states.html|failed(title, description, retry)",
            "fragments/_banner.html|banner(tone, title, body, actions)",
            "fragments/_status.html|save-status(id)",
            "fragments/_overlay.html|dialog(id, title, body, actions)",
            "fragments/_overlay.html|side-sheet(id, title, body)",
            "fragments/_overlay.html|popover(id, label, body)",
            "fragments/_overlay.html|toast-region");

    @Test
    void everySharedContractExistsWithItsApprovedSignature() {
        for (String required : REQUIRED_FRAGMENTS) {
            String[] parts = required.split("\\|", 2);
            Path file = TemplateRules.ROOT.resolve(parts[0]);
            assertThat(file).as("%s exists", parts[0]).exists();
            assertThat(TemplateRules.read(file))
                    .as("fragment %s in %s", parts[1], parts[0])
                    .contains("th:fragment=\"" + parts[1] + "\"");
        }
    }

    @Test
    void featureTemplatesDoNotRecreateThePrimitives() {
        for (Path template : TemplateRules.allTemplates()) {
            if (template.toString().contains("/fragments/")) continue;
            String markup = TemplateRules.read(template);
            for (String privateCopy : List.of("class=\"toolbar\"", "class=\"page-rail\"",
                    "class=\"toast\"", "class=\"side-sheet\"", "class=\"page-header\"")) {
                assertThat(markup)
                        .as("%s reimplements %s — use the shared fragment", template, privateCopy)
                        .doesNotContain(privateCopy);
            }
        }
    }

    @Test
    void badgeTonesAreTheStableSemanticSet() {
        String css = CssRules.allApplicationCss();
        for (String tone : List.of("success", "warning", "danger", "info", "shield", "neutral")) {
            assertThat(css).as("badge tone %s", tone).contains(".badge--" + tone);
        }
    }
}
