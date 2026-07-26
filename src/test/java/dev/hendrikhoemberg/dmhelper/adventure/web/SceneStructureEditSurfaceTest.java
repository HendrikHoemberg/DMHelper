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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SceneStructureEditSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        MvcResult result = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}/structure",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn();
        body = result.getResponse().getContentAsString();
    }

    @Test
    void declaredSurfaceIsEdit() {
        assertThat(body).contains("data-surface=\"edit\"");
    }

    /**
     * Every htmx control on this page must swap into a container the page actually has, and the
     * handler behind it must return markup that belongs in that container. The map/encounter/
     * statblock/handout selectors live in the scene form, so they swap the form pane back.
     */
    @Test
    void formControlsSwapIntoAContainerThePageHas() throws Exception {
        assertThat(body).contains("id=\"sceneEditPane\"");
        assertThat(body).contains("id=\"sceneStructureEditor\"");
        assertThat(body).contains("id=\"sceneBody\"");

        String fragment = mvc.perform(post(
                        "/campaigns/{c}/adventures/{a}/scenes/{s}/unlink-map",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment)
                .as("unlinking a map returns the scene form, not the read rail")
                .contains("name=\"sceneKey\"")
                .doesNotContain("Set as Current Scene");
    }

    @Test
    void createMarkersPresent() {
        assertThat(body).contains("data-create=\"section\"", "data-create=\"check\"",
                "data-create=\"participant\"", "data-create=\"transition\"", "data-create=\"link\"");
        assertThat(body).contains("data-structured-metadata", "name=\"mapRegionKey\"");
    }

    @Test
    void sceneFieldsAndDeletePresent() {
        assertThat(body).contains("name=\"sceneKey\"");
        assertThat(body).contains("hx-confirm=\"Delete this scene?\"");
        assertThat(body).contains("Move earlier");
        assertThat(body).contains("Move later");
    }

    @Test
    void livePreviewRendersReadAloudText() {
        assertThat(body).contains("id=\"sceneBody\"");
        assertThat(body).contains(PopulatedCampaignFixture.READ_ALOUD_BODY);
    }

    @Test
    void addCheckReturnsStructureEditorWithoutCurrentScene() throws Exception {
        String fragment = mvc.perform(post("/campaigns/{c}/adventures/{a}/chapters/{ch}/scenes/{s}/checks",
                        seeded.campaignId(), seeded.adventureId(), seeded.chapterOneId(), seeded.richSceneId())
                        .param("label", "Test Check")
                        .param("ability", "wis")
                        .param("skill", "perception")
                        .param("dc", "12")
                        .param("visibility", "PLAYER_FACING")
                        .param("sortOrder", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(fragment).contains("Test Check");
        assertThat(fragment).contains("data-create=\"check\"");
        assertThat(fragment).doesNotContain("Set as Current Scene");
    }

    @Test
    void linksBackToReadSurfacePresent() {
        assertThat(body).contains("Done");
        assertThat(body).contains("/scenes/" + seeded.richSceneId());
    }
}
