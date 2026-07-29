package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;

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
    void unreviewedUploadCannotBecomePlayerVisibleThroughTheLegacyBooleanRoute() throws Exception {
        mvc.perform(put("/campaigns/{c}/handouts/{h}/dm-only", seeded.campaignId(), seeded.dmOnlyHandoutId())
                        .param("dmOnly", "false"))
                .andExpect(status().isNotFound());
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
    void cockpitPickerListsEveryHandoutForExactPreviewWithClassification() throws Exception {
        adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
        String html = mvc.perform(get("/campaigns/{c}/session/modules/presentation", seeded.campaignId())
                        .param("mode", "STANDARD"))
                .andReturn().getResponse().getContentAsString();

        var dbHandouts = handoutService.findByCampaignId(seeded.campaignId());
        assertThat(dbHandouts).isNotEmpty();
        assertThat(dbHandouts)
                .extracting(Handout::isDmOnly)
                .contains(true, false);

        assertThat(html)
                .contains(PopulatedCampaignFixture.PLAYER_HANDOUT_TITLE,
                        PopulatedCampaignFixture.DM_ONLY_HANDOUT_TITLE)
                .contains("presentationHandoutPicker");
    }

    @Test
    void cockpitPickerStillOffersExactPreviewWhenEveryHandoutIsUnsafe() throws Exception {
        handoutService.setDmOnly(seeded.playerHandoutId(), true);
        try {
            adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
            String html = mvc.perform(get("/campaigns/{c}/session/modules/presentation", seeded.campaignId())
                            .param("mode", "STANDARD"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .as("unsafe handouts remain previewable but require the override workflow")
                    .contains(PopulatedCampaignFixture.PLAYER_HANDOUT_TITLE,
                            PopulatedCampaignFixture.DM_ONLY_HANDOUT_TITLE)
                    .doesNotContain("All handouts are DM-only");
        } finally {
            handoutService.setDmOnly(seeded.playerHandoutId(), false);
        }
    }
}
