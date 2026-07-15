package dev.hendrikhoemberg.dmhelper.campaign.packagev2.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CampaignManifestV2ContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CampaignManifestV2SchemaValidator schema = new CampaignManifestV2SchemaValidator();

    @Test
    void minimalFixtureValidatesAndDeserializes() throws Exception {
        String json = fixture("campaigns/v2/minimal.dmcampaign.json");
        assertThat(schema.validate(json)).isEmpty();
        CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);
        assertThat(manifest.formatVersion()).isEqualTo(2);
        assertThat(manifest.campaign().key()).isEqualTo("campaign-minimal");
    }

    @Test
    void serializedCurrentSurfaceDtoValidates() throws Exception {
        CampaignManifestV2 manifest = mapper.readValue(
                fixture("campaigns/v2/current-surface.dmcampaign/manifest.json"),
                CampaignManifestV2.class);
        assertThat(schema.validate(mapper.writeValueAsString(manifest))).isEmpty();
    }

    @Test
    void everyEntityKeyUsesThePackageKeyDefinition() throws Exception {
        JsonNode root = mapper.readTree(fixture("campaigns/v2/current-surface.dmcampaign/manifest.json"));
        List<String> keys = collectEntityKeys(root);
        assertThat(keys).isNotEmpty().allMatch(k -> k.matches("^[a-z0-9][a-z0-9._-]{0,99}$"));
    }

    @Test
    void packageAndCatalogReferenceBranchesAreClosed() {
        assertThat(schema.validate(minimalWithReference("""
                {"scope":"PACKAGE","type":"MAP","key":"crypt","sourceKey":"not-allowed"}
                """))).extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
        assertThat(schema.validate(minimalWithReference("""
                {"scope":"CATALOG","type":"SPELL","ruleset":"SRD_5_2","sourceKey":"fireball","key":"not-allowed"}
                """))).extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
    }

    @Test
    void v2MapImageRejectsDataUrls() {
        String json = currentSurfaceManifest().replaceAll(
                "\"assetRef\"\\s*:\\s*\"asset-crypt-map\"",
                "\"dataUrl\":\"data:image/png;base64,AAAA\"");
        assertThat(schema.validate(json)).isNotEmpty();
    }

    private String fixture(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String minimalWithReference(String referenceJson) {
        return """
                {
                  "formatVersion": 2,
                  "metadata": {
                    "packageKey": "test-pkg",
                    "createdAt": "2025-01-01T00:00:00Z",
                    "generator": "DMHelper",
                    "catalogVersion": "1.0",
                    "catalogSha256": "abc123",
                    "exclusions": [
                      "CAMPAIGN_SETTINGS","CURRENT_SCENE","PARTY_CURRENT_HP",
                      "HANDOUT_PRESENTATION_STATE","COMBAT_LOG","DICE_HISTORY",
                      "CALENDAR_CONFIGURATION","CALENDAR_CURRENT_DATE",
                      "CUSTOM_COMPENDIUM_NON_STATBLOCK","STRUCTURED_SCENE_TRANSITIONS",
                      "QUESTS_AND_OBJECTIVES"
                    ]
                  },
                  "campaign": {
                    "key": "campaign-minimal",
                    "name": "Test Campaign"
                  },
                  "assets": [],
                  "party": [],
                  "customStatBlocks": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [{
                    "key": "test-encounter",
                    "name": "Test",
                    "combatants": [],
                    "status": "PLANNED",
                    "mapRef": %s
                  }],
                  "notes": [],
                  "quickNotes": [],
                  "assignments": [],
                  "ledgerEntries": [],
                  "timelineEvents": [],
                  "adventures": []
                }
                """.formatted(referenceJson);
    }

    private String currentSurfaceManifest() {
        try {
            return fixture("campaigns/v2/current-surface.dmcampaign/manifest.json");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> collectEntityKeys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        collectKeys(node, keys);
        return keys;
    }

    private static void collectKeys(JsonNode node, List<String> keys) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            JsonNode keyNode = node.get("key");
            if (keyNode != null && keyNode.isTextual()) {
                keys.add(keyNode.textValue());
            }
            node.propertyNames().forEach(field -> collectKeys(node.get(field), keys));
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, keys));
        }
    }
}
