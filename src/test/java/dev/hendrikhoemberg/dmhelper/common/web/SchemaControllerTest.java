package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(SchemaController.class)
class SchemaControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void servesCampaignSchemaWithCorrectContentTypeAndId() throws Exception {
        mockMvc.perform(get("/api/v1/schemas/campaign-format"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/schema+json"))
                .andExpect(jsonPath("$['$schema']").value("https://json-schema.org/draft/2020-12/schema"))
                .andExpect(jsonPath("$['$id']").value(CampaignSchemaValidator.CAMPAIGN_ID));
    }

    @Test
    void servesMapDocumentSchemaWithCorrectId() throws Exception {
        mockMvc.perform(get("/api/v1/schemas/map-document.schema.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['$id']").value(CampaignSchemaValidator.MAP_ID));
    }

    @Test
    void nonexistentSchemaReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/schemas/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void traversalLikeNamesReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/schemas/../application.properties"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesCampaignV2SchemaWithCorrectContentTypeAndId() throws Exception {
        mockMvc.perform(get("/api/v1/schemas/campaign-format-v2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/schema+json"))
                .andExpect(jsonPath("$['$schema']").value("https://json-schema.org/draft/2020-12/schema"))
                .andExpect(jsonPath("$['$id']").value(CampaignManifestV2SchemaValidator.CAMPAIGN_V2_ID));
    }

    @Test
    void servesMapDocumentV2SchemaWithCorrectId() throws Exception {
        mockMvc.perform(get("/api/v1/schemas/map-document-v2.schema.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['$id']").value(CampaignManifestV2SchemaValidator.MAP_V2_ID));
    }
}
