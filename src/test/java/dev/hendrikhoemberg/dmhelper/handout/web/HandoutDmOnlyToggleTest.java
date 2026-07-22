package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HandoutDmOnlyToggleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private HandoutRepository handouts;
    @Autowired private HandoutService handoutService;
    @Autowired private AdventureService adventures;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
    }

    @Test
    void handoutCardOffersAControlToLeaveDmOnly() throws Exception {
        String html = mvc.perform(get("/campaigns/{c}/handouts", seeded.campaignId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("a DM must be able to fix their own import without calling the API by hand")
                .contains("/dm-only");
    }

    @Test
    void togglingDmOnlyOffMakesTheHandoutPresentable() throws Exception {
        String card = mvc.perform(put("/campaigns/{c}/handouts/{h}/dm-only",
                        seeded.campaignId(), seeded.dmOnlyHandoutId())
                        .param("dmOnly", "false"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(handouts.findById(seeded.dmOnlyHandoutId()).orElseThrow().isDmOnly())
                .isFalse();
        assertThat(card)
                .as("the endpoint must return the refreshed card so htmx can swap it in place")
                .contains(PopulatedCampaignFixture.DM_ONLY_HANDOUT_TITLE)
                .doesNotContain(">DM only<");
    }

    @Test
    void togglingAPresentedHandoutToDmOnlyStillDetachesIt() {
        handoutService.setPresented(seeded.playerHandoutId(), true);
        assertThat(handouts.findById(seeded.playerHandoutId()).orElseThrow().isPresented()).isTrue();

        handoutService.setDmOnly(seeded.playerHandoutId(), true);

        var after = handouts.findById(seeded.playerHandoutId()).orElseThrow();
        assertThat(after.isPresented())
                .as("a handout going DM-only must stop being presented to the table")
                .isFalse();
        assertThat(after.isDmOnly()).isTrue();

        handoutService.setDmOnly(seeded.playerHandoutId(), false);
    }

    @Test
    void cockpitPickerListsNonDmOnlyHandouts() throws Exception {
        adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
        String html = mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(PopulatedCampaignFixture.PLAYER_HANDOUT_TITLE);
        assertThat(html)
                .as("a DM-only handout must never appear in the player-facing picker")
                .doesNotContain(PopulatedCampaignFixture.DM_ONLY_HANDOUT_TITLE);
    }

    @Test
    void cockpitPickerSaysSoWhenEveryHandoutIsDmOnly() throws Exception {
        handoutService.setDmOnly(seeded.playerHandoutId(), true);
        try {
            adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
            String html = mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .as("an empty picker is indistinguishable from a broken one")
                    .contains("All handouts are DM-only");
        } finally {
            handoutService.setDmOnly(seeded.playerHandoutId(), false);
        }
    }
}
