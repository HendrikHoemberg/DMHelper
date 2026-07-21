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
class AdventureDetailDensityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        PopulatedCampaignFixture.Seeded seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/adventures/{a}",
                        seeded.campaignId(), seeded.adventureId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void chaptersAreCollapsible() {
        assertThat(body).contains("data-chapter-block");
        assertThat(body).contains("<details");
    }

    @Test
    void largeChaptersDefaultToCollapsed() {
        // Chapter one has 2 scenes (open); chapter two has CHAPTER_TWO_SCENE_COUNT (collapsed).
        int chapterOneAt = body.indexOf("Teil 1: Auf der Straße");
        int chapterTwoAt = body.indexOf("Teil 2: Die Spinne");
        assertThat(chapterOneAt).isGreaterThan(-1);
        assertThat(chapterTwoAt).isGreaterThan(chapterOneAt);

        String chapterOneBlock = body.substring(
                body.lastIndexOf("<details", chapterOneAt), chapterOneAt);
        String chapterTwoBlock = body.substring(
                body.lastIndexOf("<details", chapterTwoAt), chapterTwoAt);

        assertThat(chapterOneBlock).as("small chapter stays open").contains("open");
        assertThat(chapterTwoBlock).as("chapter past the threshold defaults collapsed")
                .doesNotContain("open");
    }

    @Test
    void aSceneFilterIsAvailable() {
        assertThat(body).contains("data-scene-filter");
    }

    @Test
    void sceneStatusIsSpelledOutNotAbbreviatedToOneLetter() {
        assertThat(body)
                .as("a bare 'U' badge is unexplained; the status must read as a word")
                .contains("Unvisited");
    }

    @Test
    void aStatusLegendExplainsTheBadges() {
        assertThat(body).contains("data-status-legend");
    }

    @Test
    void chapterControlsAreBoundToTheChapterHeaderNotTheLastScene() {
        int chapterOneAt = body.indexOf("Teil 1: Auf der Straße");
        int lastSceneOfChapterOneAt = body.indexOf("Der Gang", chapterOneAt);
        int controlsAt = body.indexOf("data-chapter-controls", chapterOneAt);

        assertThat(lastSceneOfChapterOneAt).isGreaterThan(-1);
        assertThat(controlsAt)
                .as("controls after the last scene row appear to belong to that scene")
                .isLessThan(lastSceneOfChapterOneAt);
    }

    @Test
    void rowsCarryContentAffordances() {
        assertThat(body).contains("data-affordance=\"read-aloud\"");
        assertThat(body).contains("data-affordance=\"participants\"");
        assertThat(body).contains("data-affordance=\"checks\"");
    }

    @Test
    void progressCountersAreRetained() {
        assertThat(body).as("0/N done counters already worked and must survive")
                .contains("0/2 done");
    }
}
