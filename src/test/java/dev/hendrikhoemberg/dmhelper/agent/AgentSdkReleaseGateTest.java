package dev.hendrikhoemberg.dmhelper.agent;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentSdkReleaseGateTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AgentContractService contracts;

    @Autowired
    CampaignCatalogService catalog;

    @Test
    void agentSdkIsSupported() {
        assertThat(contracts.capabilities().capabilities())
                .anyMatch(c -> "agent.sdk".equals(c.id()) && "SUPPORTED".equals(c.status()));
    }

    @Test
    void publicContractEndpointsRespond() throws Exception {
        mvc.perform(get("/api/v1/capabilities")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/validation-errors")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/schemas/campaign-format-v2")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/schemas/map-document-v2")).andExpect(status().isOk());
    }

    @Test
    void catalogHashIsStableAcrossCalls() {
        assertThat(catalog.snapshot().sha256()).isEqualTo(catalog.snapshot().sha256());
    }
}
