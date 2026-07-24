package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
}
