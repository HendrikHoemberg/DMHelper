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
class SceneDetailPresentationTest {

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
    void readAloudTextIsPresentInFull() {
        assertThat(body)
                .as("verbatim boxed text must be rendered whole, not abbreviated")
                .contains(PopulatedCampaignFixture.READ_ALOUD_BODY);
    }

    @Test
    void allSectionKindsRenderTheirBodies() {
        assertThat(body).contains(PopulatedCampaignFixture.SECRET_BODY);
        assertThat(body).contains(PopulatedCampaignFixture.TREASURE_BODY);
    }

    @Test
    void sectionsRenderInTheMainColumnNotTheClampedRail() {
        int mainColumnStart = body.indexOf("id=\"sceneBody\"");
        int railStart = body.indexOf("id=\"actionRail\"");
        int readAloudAt = body.indexOf(PopulatedCampaignFixture.READ_ALOUD_BODY);

        assertThat(mainColumnStart).as("#sceneBody must exist").isGreaterThan(-1);
        assertThat(railStart).as("#actionRail must exist").isGreaterThan(mainColumnStart);
        assertThat(readAloudAt)
                .as("read-aloud body must sit inside #sceneBody, before the rail begins")
                .isBetween(mainColumnStart, railStart);
    }

    @Test
    void readAloudIsTypographicallyDistinguished() {
        assertThat(body)
                .as("read-aloud sections carry the distinguishing class used by the cockpit rail")
                .contains("structured-read-aloud");
    }

    @Test
    void metadataFormIsBehindADisclosure() {
        int disclosureAt = body.indexOf("data-structured-metadata");
        assertThat(disclosureAt).as("metadata block must exist").isGreaterThan(-1);
        String preceding = body.substring(Math.max(0, disclosureAt - 200), disclosureAt);
        assertThat(preceding)
                .as("prep-time admin fields must not occupy prime real estate unprompted")
                .contains("<details");
    }
}
