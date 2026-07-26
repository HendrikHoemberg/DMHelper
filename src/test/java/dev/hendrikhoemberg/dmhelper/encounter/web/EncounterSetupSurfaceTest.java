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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncounterSetupSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;

    private PreparationSurfaceFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/encounters/{e}/setup", seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theSetupSurfaceDeclaresItself() {
        assertThat(body).contains("data-surface=\"edit\"");
    }

    @Test
    void everySetupControlIsPresent() {
        assertThat(body).contains("libraryAdd");
        assertThat(body).contains("threatAdd");
        assertThat(body).contains("name=\"tactics\"");
        assertThat(body).contains("encounterRewardsForm");
        assertThat(body).contains("waveKey");
        assertThat(body).contains("prefill/party");
        assertThat(body).contains("combatant-quickadd");
    }

    @Test
    void controlsAreGroupedProgressivelyNotStacked() {
        assertThat(body).contains("data-setup-group=\"combatants\"");
        assertThat(body).contains("data-setup-group=\"waves\"");
        assertThat(body).contains("data-setup-group=\"notes\"");
        assertThat(body).contains("data-setup-group=\"rewards\"");
        assertThat(body).contains("<details");
    }

    @Test
    void destructiveActionsLiveHereNotOnTheReadSurface() {
        assertThat(body).contains("hx-confirm=\"Delete this encounter?\"");
    }

    @Test
    void itLinksBackToTheEncounterOverview() {
        assertThat(body).contains("/encounters/" + seeded.encounterId());
    }
}
