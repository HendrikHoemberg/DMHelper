package dev.hendrikhoemberg.dmhelper.encounter.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncounterPrepSummaryTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PreparationSurfaceFixture fixture;

    private PreparationSurfaceFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc
                .perform(get("/campaigns/{c}/encounters/{e}", seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theOverviewReadsAsAPreparationSummary() {
        assertThat(body).contains("data-prep-summary");
        assertThat(body).contains("3 combatants");
        assertThat(body).contains(PreparationSurfaceFixture.MONSTER_NAME);
        assertThat(body).contains("Estimate:");
        assertThat(body).contains(PreparationSurfaceFixture.PREP_TACTICS);
        assertThat(body).contains(String.valueOf(PreparationSurfaceFixture.REWARD_XP_TOTAL));
        assertThat(body).contains(PreparationSurfaceFixture.PREP_SCENE_KEY);
    }

    @Test
    void theRunActionIsSingularAndObvious() {
        assertThat(body).contains("data-run-action");
        assertThat(body).contains("/encounters/" + seeded.encounterId() + "/run");
    }

    @Test
    void rawSetupFormsAreGone() {
        assertThat(body).doesNotContain("combatant-quickadd");
        assertThat(body).doesNotContain("libraryAdd");
        assertThat(body).doesNotContain("threatAdd");
        assertThat(body).doesNotContain("name=\"tactics\"");
        assertThat(body).doesNotContain("encounterRewardsForm");
        assertThat(body).doesNotContain("hx-delete");
    }

    @Test
    void theSetupSurfaceIsOneLinkAway() {
        assertThat(body).contains("/encounters/" + seeded.encounterId() + "/setup");
    }

    @Test
    void runActivatesTheEncounterAndLandsInTheCockpit() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/campaigns/{c}/encounters/{e}/run", seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + seeded.campaignId() + "/session"));

        String afterBody = mvc.perform(
                get("/campaigns/{c}/encounters/{e}", seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(afterBody).contains("ACTIVE");
    }

}
