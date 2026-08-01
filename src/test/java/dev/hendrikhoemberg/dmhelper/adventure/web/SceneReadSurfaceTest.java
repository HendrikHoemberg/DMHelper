package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SceneReadSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        MvcResult result = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn();
        body = result.getResponse().getContentAsString();
    }

    @Test
    void noAuthoringFormsRemainBesideTheProse() {
        assertThat(body)
                .as("the read surface must not contain authoring forms")
                .doesNotContain("data-create")
                .doesNotContain("data-structured-metadata")
                .doesNotContain("hx-delete")
                .doesNotContain("hx-vals='{\"direction\"");
    }

    @Test
    void theStructuredContentIsStillReadable() {
        assertThat(body)
                .as("participant display names must render")
                .contains("Sp\u00e4her der Redbrands");
        assertThat(body)
                .as("statblock names must render on participants")
                .contains(PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_NAME);
        assertThat(body)
                .as("statblock AC must render")
                .contains("AC " + PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_AC);
        assertThat(body)
                .as("check labels must render")
                .contains("Brief entziffern");
        assertThat(body)
                .as("check DC must render")
                .contains("DC 13");
        assertThat(body)
                .as("transition labels must render")
                .contains("Weiter in den Gang");
        assertThat(body)
                .as("link display text must render")
                .contains("Der Gang");
    }

    @Test
    void runtimeActionsSurvive() {
        assertThat(body)
                .as("Set as Current Scene button must be present")
                .contains("Set as Current Scene");
        assertThat(body)
                .as("Start encounter button must be present")
                .contains("Create an encounter here");
        assertThat(body)
                .as("status badge must render")
                .contains("status-badge-container");
    }

    @Test
    void anExplicitEditActionLeadsToTheEditSurface() {
        String structureUrl = "/campaigns/" + seeded.campaignId()
                + "/adventures/" + seeded.adventureId()
                + "/scenes/" + seeded.richSceneId() + "/structure";
        assertThat(body)
                .as("a link to the structure editor must exist")
                .contains(structureUrl);
    }

    @Test
    void thePageHeaderCarriesNoDestructiveAction() {
        int headerStart = body.indexOf("data-action-region");
        assertThat(headerStart).as("the shared header action region must exist").isGreaterThan(-1);
        String headerSection = body.substring(headerStart, body.indexOf("</div>", headerStart));
        assertThat(headerSection)
                .as("page header must not contain a delete/danger button")
                .doesNotContain("btn-danger");
    }

    @Test
    void proseStillOutranksTheRail() {
        int mainColumnStart = body.indexOf("id=\"sceneBody\"");
        int railStart = body.indexOf("id=\"sceneRail\"");
        int readAloudAt = body.indexOf(PopulatedCampaignFixture.READ_ALOUD_BODY);

        assertThat(mainColumnStart).as("#sceneBody must exist").isGreaterThan(-1);
        assertThat(railStart).as("#sceneRail must exist").isGreaterThan(mainColumnStart);
        assertThat(readAloudAt)
                .as("read-aloud body must sit inside #sceneBody, before the rail begins")
                .isBetween(mainColumnStart, railStart);
    }
}
