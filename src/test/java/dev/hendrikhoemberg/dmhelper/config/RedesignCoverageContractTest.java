package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 21: the machine-checkable half of the acceptance criteria. */
class RedesignCoverageContractTest {

    @Test
    void everyShippedPageIsAssignedAnApprovedArchetype() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (template.getFileName().toString().equals("error.html")) continue;
            String markup = TemplateRules.read(template);
            assertThat(markup).as("%s declares an archetype", template).contains("archetype='");
            assertThat(PageArchetypeContractTest.ARCHETYPES)
                    .as("%s uses an approved archetype", template)
                    .anySatisfy(archetype -> assertThat(markup).contains("archetype='" + archetype + "'"));
        }
    }

    @Test
    void noShippedPageUsesTheFormerBrownSurfaceSystem() {
        String source = CssRules.allApplicationCss() + "\n" + CssRules.allTemplateMarkup();
        for (String brown : java.util.List.of("#17120c", "#211a12", "#2b2318", "#3a3125", "#564936")) {
            assertThat(source).as("superseded brown %s", brown).doesNotContain(brown);
        }
    }

    /**
     * Acceptance criterion 3. Selection states are excluded because gold <em>is</em> the
     * answer to "which one is chosen" (spec 6.3): {@code .card[aria-current="true"]} and
     * {@code .card.is-selected} are approved uses, not decoration. The exclusion is narrowed
     * to those three state hooks so an ordinary {@code .card} border still fails.
     */
    @Test
    void goldIsAbsentFromGenericCardBordersAndDecorativeSeparators() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> rule.selector().matches(".*\\.(card|panel|divider|rule|section)\\b.*"))
                .filter(rule -> !rule.selector().matches(".*(aria-current|is-selected|\\.active)\\b.*"))
                .forEach(rule -> assertThat(
                        String.valueOf(rule.value("border")) + rule.value("border-color"))
                        .as("decorative gold in %s", rule.where())
                        .doesNotContain("--action-primary"));
    }

    @Test
    void everyDuplicateShellImplementationIsGone() {
        long shells = TemplateRules.allTemplates().stream()
                .filter(path -> TemplateRules.read(path).contains("class=\"app-shell\""))
                .count();
        assertThat(shells).isEqualTo(1);
    }
}
