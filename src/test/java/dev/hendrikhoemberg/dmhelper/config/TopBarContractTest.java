package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 8.1: the top bar carries global utilities only. */
class TopBarContractTest {

    private static final Path TOPBAR =
            Path.of("src/main/resources/templates/fragments/_topbar.html");

    @Test
    void theTopBarCarriesOnlyGlobalUtilities() {
        String markup = TemplateRules.read(TOPBAR);
        assertThat(markup).contains("data-topbar-search")
                .contains("data-topbar-dice")
                .contains("data-topbar-session")
                .contains("data-topbar-overflow");
    }

    @Test
    void thePageCreationAndFilterActionsDoNotLiveInTheTopBar() {
        String markup = TemplateRules.read(TOPBAR).toLowerCase();
        for (String forbidden : java.util.List.of(">new ", ">create", ">filter", ">edit", ">delete")) {
            assertThat(markup).as("page action %s belongs to the page header", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @Test
    void everyIconOnlyControlHasAnAccessibleName() {
        var document = TemplateRules.parse(TOPBAR);
        for (var control : document.select("button, a")) {
            boolean hasText = !control.ownText().isBlank()
                    || !control.select("span:not([aria-hidden])").isEmpty();
            boolean hasLabel = control.hasAttr("aria-label") || control.hasAttr("aria-labelledby");
            assertThat(hasText || hasLabel)
                    .as("accessible name for %s", control.outerHtml())
                    .isTrue();
        }
    }
}
