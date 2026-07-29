package dev.hendrikhoemberg.dmhelper.campaign.packagev2.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CatalogSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CampaignCatalogService catalogService;

    @Test
    void typedCatalogReturnsRecords() throws Exception {
        String content = mvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();

        assertThat(content).containsPattern("\"type\"\\s*:\\s*\"STATBLOCK\"");
        assertThat(content).containsPattern("\"type\"\\s*:\\s*\"SPELL\"");
        assertThat(content).contains("\"version\":\"srd-5.2-dmhelper-1\"");
    }

    @Test
    void snapshotReturnsFileWithEtag() throws Exception {
        CatalogSnapshot snapshot = catalogService.snapshot();
        mvc.perform(get("/api/v1/catalog/snapshot"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string("ETag", "\"" + snapshot.sha256() + "\""));
    }

    @Test
    void catalogEndpointsDoNotRequirePin() throws Exception {
        mvc.perform(get("/api/v1/catalog")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog/snapshot"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }
}
