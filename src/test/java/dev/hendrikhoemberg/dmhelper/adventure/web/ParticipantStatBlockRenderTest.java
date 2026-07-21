package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
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

/**
 * The imported LMoP package resolves 65 of its 67 scene participants to a statblock, and every
 * one of those links landed in the database. No template ever read them, so a DM saw
 * "Klarg (Grottenschrat) · Hostile" and had to go looking for AC and HP the app already knew.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParticipantStatBlockRenderTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private AdventureService adventures;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
    }

    private String scenePage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String cockpit() throws Exception {
        adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
        return mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void scenePageNamesTheLinkedStatBlock() throws Exception {
        assertThat(scenePage())
                .as("a participant resolved to a statblock must say which one")
                .contains(PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_NAME);
    }

    @Test
    void scenePageShowsArmourClassAndHitPoints() throws Exception {
        String html = scenePage();
        assertThat(html)
                .as("AC must be readable without leaving the scene page")
                .contains("AC " + PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_AC);
        assertThat(html)
                .as("HP must be readable without leaving the scene page")
                .contains(PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_HP);
    }

    @Test
    void scenePageLinksThroughToTheFullStatBlock() throws Exception {
        assertThat(scenePage())
                .as("the DM must be able to open the full statblock in one click")
                .contains("/library/statblocks/");
    }

    @Test
    void cockpitShowsArmourClassAndHitPoints() throws Exception {
        String html = cockpit();
        assertThat(html).contains(PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_NAME);
        assertThat(html)
                .as("at the table, AC and HP must be on the story rail")
                .contains("AC " + PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_AC);
        assertThat(html).contains(PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_HP);
    }

    @Test
    void participantWithoutAStatBlockStillRenders() throws Exception {
        assertThat(scenePage())
                .as("the 2-of-67 unlinked case must not break the row")
                .contains("Namenloser Bote");
    }
}
