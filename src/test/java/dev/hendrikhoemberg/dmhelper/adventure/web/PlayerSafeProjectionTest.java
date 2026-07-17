package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.common.config.PinManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dmhelper.pin-enabled=true")
class PlayerSafeProjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PinManager pinManager;

    @Test
    void playerViewDoesNotLeakAdventureData() throws Exception {
        mockMvc.perform(get("/player"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("adventure"))))
                .andExpect(content().string(not(containsString("scene"))));
    }

    @Test
    void playerSafeJsonDoesNotContainDmOnlyFields() throws Exception {
        mockMvc.perform(get("/player"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("secrets"))))
                .andExpect(content().string(not(containsString("dmAdvice"))));
    }

    @Test
    void tableStateJsonDoesNotExposeStructuredDmFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/table/state"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("DM_ADVICE")
                .doesNotContain("READ_ALOUD")
                .doesNotContain("sourceLocator")
                .doesNotContain("sourceAnnotation")
                .doesNotContain("dmNote")
                .doesNotContain("prerequisites")
                .doesNotContain("outcomeNotes")
                .doesNotContain("completionMode")
                .doesNotContain("externalDestination")
                .doesNotContain("fieldPath");
    }

    @Test
    void tableStateJsonDoesNotLeakSheetFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/table/state"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("overridesMeta")
                .doesNotContain("deathSaveSuccesses")
                .doesNotContain("deathSaveFailures")
                .doesNotContain("hitDiceUsed")
                .doesNotContain("provenance")
                .doesNotContain("\"notes\"")
                .doesNotContain("playerName");
    }

    @Test
    void tableStateJsonDoesNotExposeWorldGraphDmFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/table/state"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("worldNpcs")
                .doesNotContain("worldLocations")
                .doesNotContain("factionClocks")
                .doesNotContain("inventoryText")
                .doesNotContain("reputationNotes")
                .doesNotContain("WORLD_NPC")
                .doesNotContain("FACTION_CLOCK");
    }

    @Test
    void tableStateJsonDoesNotLeakRawConditionsJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/table/state"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("conditionsJson")
                .doesNotContain("concentratingOn");
    }

    @Test
    void pinApiReturns403WithoutValidPin() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{id}/pins", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pinApiReturns200WithValidPin() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{id}/pins", UUID.randomUUID())
                        .cookie(new Cookie("dm_pin", pinManager.getPin())))
                .andExpect(status().isOk());
    }
}
