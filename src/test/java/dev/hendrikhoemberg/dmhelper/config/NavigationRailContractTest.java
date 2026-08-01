package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec sections 8.2 and 8.3. */
class NavigationRailContractTest {

    private static final Path RAIL =
            Path.of("src/main/resources/templates/fragments/_rail.html");

    /**
     * One rail branch, resolved through the tree rather than by slicing source at an offset.
     * `data-rail-branch` exists purely so this test can name a branch; it carries no styling.
     */
    private static Element branch(String name) {
        Element branch = TemplateRules.parse(RAIL).selectFirst("[data-rail-branch=" + name + "]");
        assertThat(branch).as("the %s rail branch exists", name).isNotNull();
        return branch;
    }

    private static List<String> destinationsOf(String branchName) {
        return branch(branchName).select("a.rail__link").stream()
                .map(link -> link.attr("data-label"))
                .toList();
    }

    @Test
    void theCampaignRailUsesTheApprovedGroupsInOrder() {
        List<String> groups = branch("campaign").select(".rail__group > .rail__label").stream()
                .map(Element::text)
                .toList();

        assertThat(groups)
                .as("spec 8.2: the five campaign groups, in order")
                .containsExactly("Campaign", "Prepare", "Party & World", "Records", "Reference");
    }

    @Test
    void everyApprovedDestinationIsPresentExactlyOnceInTheCampaignRail() {
        assertThat(destinationsOf("campaign"))
                .as("spec 8.2: the campaign rail's destinations, each exactly once")
                .containsExactlyInAnyOrder("Campaign Home", "Run Session", "Adventures",
                        "Encounters", "Maps", "Handouts", "Audio", "Party", "Quests", "NPCs",
                        "Locations", "Factions", "Calendar", "Notes", "Treasury", "Ledger",
                        "Library", "Tables", "Traps", "Hazards");
    }

    @Test
    void runSessionIsNotDuplicatedAsAFooterButton() {
        assertThat(TemplateRules.read(RAIL))
                .as("spec 8.2: Run Session is a Campaign group entry, not a footer button")
                .doesNotContain("appnav-footer")
                .doesNotContain("rail__footer");
    }

    @Test
    void theGlobalRailDoesNotPretendToBeInsideACampaign() {
        assertThat(destinationsOf("global"))
                .as("spec 8.3: the global rail offers only campaign-independent destinations")
                .containsExactlyInAnyOrder("Campaigns", "Library", "Tables", "Traps", "Hazards",
                        "About");
    }

    @Test
    void collapsedLabelsRemainAccessible() {
        var document = TemplateRules.parse(RAIL);
        for (var link : document.select("a.rail__link")) {
            assertThat(link.hasAttr("data-label"))
                    .as("collapsed label for %s", link.outerHtml()).isTrue();
            assertThat(link.hasAttr("title"))
                    .as("collapsed affordance for %s", link.outerHtml()).isTrue();
            assertThat(link.select("span.rail__text").isEmpty())
                    .as("%s must keep its text node as the accessible name", link.outerHtml())
                    .isFalse();
        }
    }

    @Test
    void theCollapsedRailDoesNotHideLabelsFromScreenReaders() {
        var collapsed = CssRules.of("base.css").stream()
                .filter(rule -> rule.selector().contains(".app-shell--rail-collapsed")
                        && rule.selector().contains(".rail__text"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".rail__text has no collapsed rule"));
        assertThat(collapsed.value("display"))
                .as("display:none strips the accessible name; clip the label instead")
                .isNotEqualTo("none");
    }
}
