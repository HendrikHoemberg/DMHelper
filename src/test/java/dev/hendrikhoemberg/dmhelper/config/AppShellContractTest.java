package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 18.2: no feature template may duplicate global app-shell markup. */
class AppShellContractTest {

    /** Standalone documents that legitimately do not use the campaign shell. */
    private static final List<String> EXEMPT = List.of("error.html");

    private static boolean exempt(Path template) {
        return EXEMPT.contains(template.getFileName().toString());
    }

    @Test
    void everyPageTemplateDelegatesToTheSharedShell() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (exempt(template)) continue;
            assertThat(TemplateRules.read(template))
                    .as("%s must render through fragments/_shell :: page", template)
                    .contains("fragments/_shell :: page");
        }
    }

    @Test
    void noPageTemplateHandRollsTheShell() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (exempt(template)) continue;
            String markup = TemplateRules.read(template);
            assertThat(markup)
                    .as("%s hand-rolls shell markup", template)
                    .doesNotContain("class=\"app-shell\"")
                    .doesNotContain("fragments/navbar")
                    .doesNotContain("fragments/_appnav")
                    .doesNotContain("~{fragments/head :: head}");
        }
    }

    @Test
    void theShellIsImplementedExactlyOnce() {
        long implementations = TemplateRules.allTemplates().stream()
                .filter(path -> TemplateRules.read(path).contains("class=\"app-shell\""))
                .count();
        assertThat(implementations).as("app-shell implementations").isEqualTo(1);
    }
}
