package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
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

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampaignDashboardScaleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private PopulatedCampaignFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{id}", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void dashboardReportsCampaignScale() {
        assertThat(body).contains("data-scale-panel");
        int expectedScenes = 2 + PopulatedCampaignFixture.CHAPTER_TWO_SCENE_COUNT;
        assertThat(body).contains("data-scale=\"scenes\"");
        assertThat(body).contains(">" + expectedScenes + "<");
    }

    @Test
    void everyLoadedContentTypeHasAnEntryPoint() {
        String c = "/campaigns/" + seeded.campaignId();
        assertThat(body).contains(c + "/adventures");
        assertThat(body).contains(c + "/quests");
        assertThat(body).contains(c + "/world/npcs");
        assertThat(body).contains(c + "/world/locations");
        assertThat(body).contains(c + "/world/factions");
    }

    @Test
    void editFormIsDemotedBehindADisclosure() {
        int formAt = body.indexOf("id=\"editName\"");
        assertThat(formAt).as("edit form must still exist").isGreaterThan(-1);
        int detailsAt = body.lastIndexOf("<details", formAt);
        int gridEndAt = body.lastIndexOf("dash-grid", formAt);
        assertThat(detailsAt)
                .as("edit form must sit inside a disclosure, not a top-level dashboard card")
                .isGreaterThan(gridEndAt);
    }
}
