package dev.hendrikhoemberg.dmhelper.adventure.web;

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
class AdventureIndexTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        PopulatedCampaignFixture.Seeded seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/adventures", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void adventureCardReportsChapterAndSceneCounts() {
        int expectedScenes = 2 + PopulatedCampaignFixture.CHAPTER_TWO_SCENE_COUNT;
        assertThat(body).contains("data-adventure-counts");
        assertThat(body).contains("2 chapters");
        assertThat(body).contains(expectedScenes + " scenes");
    }

    @Test
    void adventureCardReportsCompletionProgress() {
        int expectedScenes = 2 + PopulatedCampaignFixture.CHAPTER_TWO_SCENE_COUNT;
        assertThat(body).contains("0/" + expectedScenes + " done");
    }

    @Test
    void descriptionPrecedesTheControlCluster() {
        int descriptionAt = body.indexOf("A two-part fixture adventure");
        int controlsAt = body.indexOf("card-actions");
        assertThat(descriptionAt).as("description must render").isGreaterThan(-1);
        assertThat(controlsAt).as("controls must render").isGreaterThan(-1);
        assertThat(descriptionAt)
                .as("controls between title and description break reading order")
                .isLessThan(controlsAt);
    }
}
