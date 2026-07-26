package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SceneSeedEncounterControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private AdventureService adventures;
    @Autowired private EncounterRepository encounterRepository;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
    }

    @Test
    @Disabled("seed result moves to the structure editor in Task 6")
    void scenePageOffersTheActionBeforeSeedingAndTheLinkAfter() throws Exception {
        String before = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("Start encounter from this scene");

        long encountersBefore = encounterRepository.count();

        String rail = mvc.perform(post("/campaigns/{c}/adventures/{a}/scenes/{s}/seed-encounter",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(rail)
                .as("the rail must come back showing what was built and what was not")
                .contains("Encounter: Der Schreibtisch")
                .contains("Namenloser Bote")
                .contains("Linked Encounter");

        mvc.perform(post("/campaigns/{c}/adventures/{a}/scenes/{s}/seed-encounter",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk());

        assertThat(encounterRepository.count()).isEqualTo(encountersBefore + 1);

        String after = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(after)
                .as("once linked, the scene shows its encounter instead of offering to build one")
                .contains("Linked Encounter")
                .doesNotContain("Start encounter from this scene");
    }

    @Test
    void aSceneWithNoResolvableParticipantsDoesNotOfferTheAction() throws Exception {
        String html = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.secondSceneId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("Start encounter from this scene");
    }
}
