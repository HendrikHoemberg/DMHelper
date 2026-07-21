package dev.hendrikhoemberg.dmhelper.session.web;

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
class CockpitScenePickerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;
    private String cockpit;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        cockpit = mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void cockpitOffersASceneSelector() {
        assertThat(cockpit)
                .as("a DM must be able to set the current scene without leaving the cockpit")
                .contains("cockpitScenePicker");
    }

    @Test
    void everySceneInTheCampaignIsSelectable() {
        assertThat(cockpit).contains(seeded.richSceneId().toString());
        assertThat(cockpit).contains(seeded.secondSceneId().toString());
    }

    @Test
    void scenesAreGroupedByChapter() {
        assertThat(cockpit).contains("<optgroup");
        assertThat(cockpit).contains("Teil 1: Auf der Straße");
        assertThat(cockpit).contains("Teil 2: Die Spinne");
    }

    @Test
    void pickerIsPresentWhenNoSceneIsSet() {
        int emptyStateAt = cockpit.indexOf("No current scene");
        assertThat(emptyStateAt).as("empty-state branch is the one under test").isGreaterThan(-1);
        assertThat(cockpit.indexOf("cockpitScenePicker"))
                .as("picker must be reachable from the empty state")
                .isGreaterThan(-1);
    }

    @Test
    void standaloneStoryRailAlsoCarriesThePicker() throws Exception {
        String rail = mvc.perform(get("/campaigns/{c}/session/rails/story", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(rail)
                .as("the rail is re-fetched standalone after scene changes; it must not lose the picker")
                .contains("cockpitScenePicker");
    }
}
