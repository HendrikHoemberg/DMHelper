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
class EncounterSetupFragmentTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;

    private PreparationSurfaceFixture.Seeded seeded;
    private String html;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        html = mvc.perform(get("/campaigns/{cid}/encounters/{eid}/setup",
                        seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void setupPageShipsTheAlpineComponentsItsWidgetsNeed() {
        assertThat(html).contains("Alpine.data('libraryAdd'");
        assertThat(html).contains("Alpine.data('threatAdd'");
        assertThat(html).doesNotContain("${encounterId}");
        assertThat(html).contains("data-encounter-id=\"" + seeded.encounterId() + "\"");
    }

    @Test
    void setupPageListsEveryCombatantWithItsHp() {
        assertThat(html).contains("Hooded Ambusher A");
        assertThat(html).contains("Hooded Ambusher B");
        assertThat(html).contains("Aral Quickfoot");
        assertThat(html).contains("data-combatant-row");
    }
}
