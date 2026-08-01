package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 9: every standard page is one of five archetypes. */
class PageArchetypeContractTest {

    static final List<String> ARCHETYPES =
            List.of("index", "detail", "form", "operational", "editor");

    @Test
    void layoutTokensExist() {
        String tokens = CssRules.read("tokens.css");
        for (String token : List.of("--topbar-height", "--rail-width-expanded",
                "--rail-width-collapsed", "--page-gap", "--page-pad", "--context-rail-width",
                "--measure-prose", "--page-max-index", "--page-max-detail", "--page-max-form")) {
            assertThat(tokens).as("%s is defined", token).contains(token + ":");
        }
    }

    @Test
    void everyArchetypeHasALayoutRule() {
        String base = CssRules.read("base.css");
        for (String archetype : ARCHETYPES) {
            assertThat(base).as("layout rule for %s", archetype).contains(".page--" + archetype);
        }
    }

    @Test
    void readingSurfacesAreBoundedAndOperationalSurfacesAreNot() {
        var rules = CssRules.of("base.css");
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).contains(".page--form");
            assertThat(rule.value("max-width")).contains("--page-max-form");
        });
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).contains(".page--detail");
            assertThat(rule.value("max-width")).contains("--page-max-detail");
        });
        var operational = rules.stream()
                .filter(rule -> rule.selector().equals(".page--operational"))
                .findFirst()
                .orElseThrow();
        assertThat(operational.value("max-width")).isNull();
    }

    @Test
    void theEditorArchetypeOwnsTheViewportAndDoesNotScrollTheDocument() {
        var editor = CssRules.of("base.css").stream()
                .filter(rule -> rule.selector().equals(".page--editor"))
                .findFirst()
                .orElseThrow();
        assertThat(editor.value("height")).contains("100");
        assertThat(editor.value("overflow")).isEqualTo("hidden");
    }
}
