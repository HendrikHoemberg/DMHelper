package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampaignAdminSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;
    @Autowired private CampaignFixtures campaignFixtures;

    private MockMvc mvc;
    private PreparationSurfaceFixture.Seeded seeded;
    private String home;
    private String settings;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        home = mvc.perform(get("/campaigns/{id}", seeded.campaignId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        settings = mvc.perform(get("/campaigns/{id}/settings", seeded.campaignId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theHomeCarriesNoAdminTooling() {
        assertThat(home).doesNotContain("Export Campaign Package");
        assertThat(home).doesNotContain("Import Campaign Package");
        assertThat(home).doesNotContain("Delete Campaign");
        assertThat(home).as("no inline campaign edit form on the read surface")
                .doesNotContain("id=\"editDescription\"");
    }

    @Test
    void theHomeLinksToTheAdminSurface() {
        assertThat(home).contains("/campaigns/" + seeded.campaignId() + "/settings");
    }

    @Test
    void theAdminSurfaceCarriesEveryToolThatLeftTheHome() {
        assertThat(settings).contains("data-surface=\"admin\"");
        assertThat(settings).contains("Export Campaign Package");
        assertThat(settings).contains("Import Campaign Package");
        assertThat(settings).contains("Delete Campaign");
        assertThat(settings).contains("id=\"editDescription\"");
        assertThat(settings).as("the import dialog must travel with its button")
                .contains("campaignImport");
    }

    @Test
    void theHomeOffersDirectRunEntryPoints() {
        assertThat(home).contains("data-run-entry=\"session\"");
        assertThat(home).contains("/campaigns/" + seeded.campaignId() + "/session");
    }

    @Test
    void theHomeStillShowsReadinessAndScale() {
        assertThat(home).contains("readiness-panel");
        assertThat(home).contains("scale-panel");
    }

    @Test
    void readinessReportGroupsBlockersAndMakesRepairTheClearerAction() throws Exception {
        var campaignId = campaignFixtures.operationalFixtureNotReady();
        String rendered = mvc.perform(get("/campaigns/{id}", campaignId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var document = Jsoup.parse(rendered);
        var panel = document.selectFirst(".readiness-panel");
        assertThat(panel).isNotNull();
        assertThat(panel.selectFirst("h3").text()).isEqualTo("Campaign readiness");
        assertThat(panel.selectFirst(".readiness-badge").text()).isEqualTo("Not ready — 4 blockers");
        assertThat(panel.selectFirst(".readiness-panel__intro").text())
                .contains("Resolve these blockers")
                .contains("explicitly choose to continue");

        assertThat(panel.select(".readiness-group"))
                .extracting(group -> group.attr("data-readiness-category"))
                .containsExactly("ENCOUNTER", "STATBLOCK", "MAP");
        assertThat(panel.select(".readiness-group__heading"))
                .extracting(org.jsoup.nodes.Element::text)
                .containsExactly(
                        "Encounters 1 blocker",
                        "Participants 1 blocker",
                        "Maps 2 blockers");
        assertThat(panel.select(".readiness-group__summary"))
                .extracting(org.jsoup.nodes.Element::text)
                .containsExactly(
                        "Hostile scenes need a runnable encounter.",
                        "Hostile participants need resolvable statblocks.",
                        "Required scenes need a playable or reference map.");

        String acceptEndpoint = "/campaigns/" + campaignId + "/readiness/accept";
        var acceptForms = panel.select("form[action=\"" + acceptEndpoint + "\"]");

        assertThat(acceptForms).hasSize(4);
        assertThat(acceptForms).allSatisfy(form -> {
            assertThat(form.attr("hx-post")).isEqualTo(acceptEndpoint);
            assertThat(form.attr("hx-target")).isEqualTo("closest .readiness-panel");
            assertThat(form.attr("hx-swap")).isEqualTo("outerHTML");
            assertThat(form.selectFirst("button").hasClass("btn-primary")).isFalse();
        });

        var repairLinks = panel.select(".readiness-item__repair");
        assertThat(repairLinks).hasSize(4);
        assertThat(repairLinks)
                .extracting(org.jsoup.nodes.Element::text)
                .contains("Prepare encounter", "Review participants", "Add map");
        assertThat(repairLinks).allSatisfy(link ->
                assertThat(link.attr("href")).startsWith("/campaigns/" + campaignId + "/"));
        assertThat(panel.select("[data-readiness-category=MAP] .readiness-item__repair"))
                .hasSize(2)
                .allSatisfy(link -> assertThat(link.attr("href")).contains("/adventures/").contains("/scenes/"));
        assertThat(acceptForms.select("button"))
                .extracting(org.jsoup.nodes.Element::text)
                .contains(
                        "Run without encounter",
                        "Run without statblocks",
                        "Run without map");
        assertThat(panel.select(".readiness-item__title"))
                .extracting(org.jsoup.nodes.Element::text)
                .contains(
                        "Participants missing statblocks: Ambush Encounter",
                        "Scene requires a map: The Dark Cave");
        assertThat(panel.select(".readiness-item__content, .readiness-item__actions"))
                .allSatisfy(container -> assertThat(container.tagName()).isEqualTo("div"));
    }
}
