package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class SessionEncounterEvidenceIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CampaignRepository campaignRepo;
    @Autowired
    private PartyMemberRepository partyRepo;
    @Autowired
    private CampaignSessionRepository sessionRepo;
    @Autowired
    private SessionLifecycleService lifecycle;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Session Evidence Test");
        campaign = campaignRepo.save(campaign);
        campaignId = campaign.getId();

        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Test Hero");
        pm.setPlayerName("Tester");
        pm.setActive(true);
        pm.setMaxHp(20);
        pm.setCurrentHp(20);
        partyRepo.save(pm);

        lifecycle.start(campaignId, null);
    }

    @Test
    void retainsDefeatedNameThroughFullEndpointLifecycle() throws Exception {
        UUID encounterId = createEncounter("Test Battle");
        UUID combatantId = addCombatant(encounterId, "Goblin 1", 10);
        activateEncounter(encounterId);
        applyDamage(combatantId, -999);
        markDefeated(combatantId, false);
        markDefeated(combatantId, true);
        removeCombatant(combatantId);
        endEncounter(encounterId);

        UUID emptyEncounterId = createEncounter("Empty Encounter");
        activateEncounter(emptyEncounterId);
        endEncounter(emptyEncounterId);

        CampaignSession session = lifecycle.beginReview(campaignId);
        String draft = session.getDraftBody();

        assertThat(draft).contains("Test Battle");
        assertThat(draft).contains("Goblin 1");
        assertThat(draft).contains("damage recorded: 999");
        assertThat(draft).contains("Empty Encounter \u2014 completed; no defeat or damage evidence recorded");
    }

    private UUID createEncounter(String name) throws Exception {
        String json = mvc.perform(post("/api/v1/campaigns/{campaignId}/encounters", campaignId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID addCombatant(UUID encounterId, String name, int maxHp) throws Exception {
        String json = mvc.perform(post("/api/v1/encounters/{id}/combatants", encounterId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"maxHp\":" + maxHp + ",\"kind\":\"MONSTER\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private void activateEncounter(UUID encounterId) throws Exception {
        mvc.perform(post("/api/v1/encounters/{id}/activate", encounterId))
                .andExpect(status().isOk());
    }

    private void endEncounter(UUID encounterId) throws Exception {
        mvc.perform(post("/api/v1/encounters/{id}/end", encounterId))
                .andExpect(status().isOk());
    }

    private void applyDamage(UUID combatantId, int amount) throws Exception {
        mvc.perform(post("/api/v1/combatants/{id}/damage", combatantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private void markDefeated(UUID combatantId, boolean defeated) throws Exception {
        mvc.perform(put("/api/v1/combatants/{id}/defeated", combatantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defeated\":" + defeated + "}"))
                .andExpect(status().isOk());
    }

    private void removeCombatant(UUID combatantId) throws Exception {
        mvc.perform(delete("/api/v1/combatants/{id}", combatantId))
                .andExpect(status().isNoContent());
    }
}
