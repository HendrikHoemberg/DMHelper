package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 8.4: one header contract, one principal title, one primary action. */
class PageHeaderContractTest {

    private static final Path HEADER =
            Path.of("src/main/resources/templates/fragments/_page-header.html");

    @Test
    void theHeaderExposesTheApprovedFiveSlots() {
        assertThat(TemplateRules.read(HEADER))
                .contains("th:fragment=\"page-header(title, summary, breadcrumb, primary, secondary)\"");
    }

    @Test
    void theHeaderRendersExactlyOneHeadingElement() {
        assertThat(TemplateRules.parse(HEADER).select("h1")).hasSize(1);
    }

    @Test
    void onlyTheHeaderTitleCarriesTheDisplayTypeface() {
        var document = TemplateRules.parse(HEADER);
        assertThat(document.select("[data-display-title]")).hasSize(1);
        assertThat(document.select("h1").first().hasAttr("data-display-title")).isTrue();
    }

    @Test
    void everyMigratedPageHasOneHeaderAndAtMostOnePrimaryAction() {
        for (Path template : TemplateRules.pageTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("fragments/_shell :: page")) continue;
            assertThat(markup.split("_page-header :: page-header", -1).length - 1)
                    .as("page headers in %s", template).isEqualTo(1);
            assertThat(markup.split("btn-primary", -1).length - 1)
                    .as("filled primary actions in %s (spec 6.3)", template)
                    .isLessThanOrEqualTo(1);
        }
    }
}
