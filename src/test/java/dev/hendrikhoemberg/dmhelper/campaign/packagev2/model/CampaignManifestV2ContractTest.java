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
import tools.jackson.databind.node.ObjectNode;

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
        assertThat(manifest.metadata().exclusions()).isEmpty();
        assertThat(manifest.campaign().settings()).isNotNull();
        assertThat(manifest.campaign().settings().levelingMode()).isEqualTo(CampaignManifestV2.LevelingMode.XP);
        assertThat(manifest.diceRolls()).isEmpty();
        assertThat(schema.validate(mapper.writeValueAsString(manifest))).isEmpty();
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
    void nestedSheetsAndResourcesRequirePackageKeys() throws Exception {
        JsonNode root = mapper.readTree(currentSurfaceManifest());
        JsonNode sheet = root.get("party").get(0).get("sheet");
        assertThat(sheet.get("key")).isNotNull();
        assertThat(sheet.get("resources").get(0).get("key")).isNotNull();

        ((ObjectNode) sheet).remove("key");
        assertThat(schema.validate(mapper.writeValueAsString(root)))
                .extracting(CampaignImportProblem::code)
                .contains("SCHEMA_VIOLATION");
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

    @Test
    void unknownExclusionRejectedBySchema() {
        String json = currentSurfaceManifest().replaceAll(
                "\"exclusions\"\\s*:\\s*\\[\\]",
                "\"exclusions\":[\"UNKNOWN_EXCLUSION\"]");
        assertThat(schema.validate(json))
                .extracting(CampaignImportProblem::code)
                .contains("SCHEMA_VIOLATION");
    }

    @Test
    void nonRuntimeTokenKindRejectedBySchema() {
        String json = currentSurfaceManifest().replaceAll(
                "\"kind\"\\s*:\\s*\"MONSTER\"",
                "\"kind\":\"creature\"");
        assertThat(schema.validate(json))
                .extracting(CampaignImportProblem::code)
                .contains("SCHEMA_VIOLATION");
    }

    @Test
    void nonRuntimeCombatantKindRejectedBySchema() {
        String json = currentSurfaceManifest().replaceAll(
                "\"kind\"\\s*:\\s*\"MONSTER\"",
                "\"kind\":\"player\"");
        assertThat(schema.validate(json))
                .extracting(CampaignImportProblem::code)
                .contains("SCHEMA_VIOLATION");
    }

    @Test
    void schemaAcceptsValidNoteLinkStructure() {
        String json = currentSurfaceManifest().replaceAll(
                "\"links\"\\s*:\\s*\\[\\]",
                "\"links\":[{\"targetType\":\"NOTE\",\"targetRef\":{\"scope\":\"PACKAGE\",\"type\":\"NOTE\",\"key\":\"missing-note\"},\"displayText\":\"Broken\",\"resolved\":false}]");
        assertThat(schema.validate(json)).isEmpty();
    }

    @Test
    void schemaAcceptsValidExclusionValues() {
        String json = currentSurfaceManifest().replaceAll(
                "\"exclusions\"\\s*:\\s*\\[\\]",
                "\"exclusions\":[\"COMBAT_LOG\",\"DICE_HISTORY\"]");
        assertThat(schema.validate(json)).isEmpty();
    }

    @Test
    void spellSourceClassReferenceIsOptional() throws Exception {
        JsonNode root = mapper.readTree(currentSurfaceManifest());
        ObjectNode spell = (ObjectNode) root.get("party").get(0).get("sheet").get("spells").get(0);
        spell.remove("sourceClassRef");

        assertThat(schema.validate(mapper.writeValueAsString(root))).isEmpty();
    }

    @Test
    void sessionPresentationAndDraftInvariantsAreEnforcedBySchema() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(
                fixture("campaigns/v2/feature-complete.dmcampaign/manifest.json"));
        ObjectNode session = (ObjectNode) root.get("session");
        session.set("presentedRef", mapper.readTree(
                "{\"scope\":\"PACKAGE\",\"type\":\"MAP\",\"key\":\"lower-crypt\"}"));
        assertThat(schema.validate(mapper.writeValueAsString(root)))
                .extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");

        session.remove("presentedRef");
        session.put("presentationMode", "HANDOUT");
        assertThat(schema.validate(mapper.writeValueAsString(root)))
                .extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");

        session.put("presentationMode", "CURTAIN");
        session.put("draftBody", "Draft must only exist during review");
        assertThat(schema.validate(mapper.writeValueAsString(root)))
                .extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");

        session.put("status", "REVIEW");
        session.remove("draftBody");
        assertThat(schema.validate(mapper.writeValueAsString(root)))
                .extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
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
                    "exclusions": []
                  },
                  "campaign": {
                    "key": "campaign-minimal",
                    "name": "Test Campaign",
                    "createdAt": "2025-01-01T00:00:00Z",
                    "settings": {
                      "levelingMode": "XP"
                    }
                  },
                  "assets": [],
                  "party": [],
                  "customStatBlocks": [],
                  "customSpells": [],
                  "customConditions": [],
                  "customRules": [],
                  "customEquipment": [],
                  "customMagicItems": [],
                  "customClasses": [],
                  "customSpecies": [],
                  "customBackgrounds": [],
                  "customFeats": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [{
                    "key": "test-encounter",
                    "name": "Test",
                    "combatants": [],
                    "status": "PLANNED",
                    "lairActionTriggered": false,
                    "combatLog": [],
                    "mapRef": %s
                  }],
                  "notes": [],
                  "quickNotes": [],
                  "assignments": [],
                  "ledgerEntries": [],
                  "timelineEvents": [],
                  "adventures": [],
                  "diceRolls": []
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

    @Test
    void structuredSceneWithAllNewFieldsValidates() throws Exception {
        String json = """
                {
                  "formatVersion": 2,
                  "metadata": {
                    "packageKey": "test-pkg",
                    "createdAt": "2025-01-01T00:00:00Z",
                    "generator": "DMHelper",
                    "catalogVersion": "1.0",
                    "catalogSha256": "abc123",
                    "exclusions": []
                  },
                  "campaign": {
                    "key": "campaign-structured",
                    "name": "Structured",
                    "createdAt": "2025-01-01T00:00:00Z",
                    "settings": { "levelingMode": "XP" }
                  },
                  "assets": [],
                  "party": [],
                  "customStatBlocks": [],
                  "customSpells": [],
                  "customConditions": [],
                  "customRules": [],
                  "customEquipment": [],
                  "customMagicItems": [],
                  "customClasses": [],
                  "customSpecies": [],
                  "customBackgrounds": [],
                  "customFeats": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [],
                  "notes": [],
                  "quickNotes": [],
                  "assignments": [],
                  "ledgerEntries": [],
                  "timelineEvents": [],
                  "adventures": [{
                    "key": "adv-structured",
                    "name": "Structured Adventure",
                    "sortOrder": 1,
                    "createdAt": "2025-01-01T00:00:00Z",
                    "chapters": [{
                      "key": "ch-structured",
                      "title": "Ch1",
                      "sortOrder": 1,
                      "scenes": [{
                        "key": "sc-structured",
                        "title": "Structured Scene",
                        "body": "Body text",
                        "status": "UNVISITED",
                        "sortOrder": 1,
                        "summary": "A summary",
                        "sourceLocator": "book:1",
                        "tags": ["exploration", "combat"],
                        "mapRegionKey": "north-wing",
                        "sections": [
                          { "kind": "READ_ALOUD", "label": "Read this", "body": "The room is dark...", "sourceLocator": "book:1", "sortOrder": 1 },
                          { "kind": "DM_ADVICE", "label": "DM Note", "body": "If players check...", "sortOrder": 2 },
                          { "kind": "SECRET", "body": "Hidden treasure under the rug", "sortOrder": 3 }
                        ],
                        "checks": [{
                          "label": "Perception", "ability": "wis", "skill": "perception",
                          "dc": 15, "visibility": "PLAYER_FACING",
                          "success": "You spot the hidden door", "failure": "You see nothing",
                          "partial": "You notice some dust",
                          "ruleRef": { "scope": "CATALOG", "type": "RULE", "ruleset": "SRD_5_2", "sourceKey": "observation-rule" },
                          "sourceLocator": "book:1", "sortOrder": 1
                        }],
                        "participants": [{
                          "displayName": "Goblin", "quantity": 3, "disposition": "HOSTILE",
                          "placementHint": "Behind the door",
                          "statblockRef": { "scope": "PACKAGE", "type": "STATBLOCK", "key": "statblock-goblin" },
                          "sourceLocator": "book:1", "sortOrder": 1
                        }],
                        "transitions": [
                          { "key": "t-choice", "kind": "CHOICE", "label": "Go north", "targetSceneRef": { "scope": "PACKAGE", "type": "SCENE", "key": "sc-north" }, "condition": "if door unlocked", "dmNote": "Leads to boss", "sortOrder": 1 },
                          { "key": "t-exit", "kind": "EXIT", "label": "Leave dungeon", "externalDestination": "Overworld", "sortOrder": 2 }
                        ],
                        "links": [{
                          "role": "REFERENCE", "targetRef": { "scope": "PACKAGE", "type": "NOTE", "key": "note-lore" },
                          "displayText": "See lore note", "sortOrder": 1
                        }]
                      }]
                    }]
                  }],
                  "annotations": [{
                    "key": "ann-source-1",
                    "ownerRef": { "scope": "PACKAGE", "type": "SCENE", "key": "sc-structured" },
                    "fieldPath": "/checks/0/dc",
                    "message": "DC inferred from source",
                    "confidence": "MEDIUM",
                    "sourceLocator": "book:1",
                    "status": "OPEN",
                    "createdAt": "2025-01-01T00:00:00Z"
                  }],
                  "diceRolls": []
                }
                """;
        assertThat(schema.validate(json)).isEmpty();
        CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);
        assertThat(manifest.annotations()).hasSize(1);
        assertThat(manifest.adventures().get(0).chapters().get(0).scenes().get(0).sections()).hasSize(3);
        assertThat(manifest.adventures().get(0).chapters().get(0).scenes().get(0).checks()).hasSize(1);
        assertThat(manifest.adventures().get(0).chapters().get(0).scenes().get(0).participants()).hasSize(1);
        assertThat(manifest.adventures().get(0).chapters().get(0).scenes().get(0).transitions()).hasSize(2);
        assertThat(manifest.adventures().get(0).chapters().get(0).scenes().get(0).links()).hasSize(1);
        assertThat(schema.validate(mapper.writeValueAsString(manifest))).isEmpty();
    }

    @Test
    void structuredQuestValidatesAndDeserializes() throws Exception {
        String json = """
                {
                  "formatVersion": 2,
                  "metadata": {
                    "packageKey": "test-pkg",
                    "createdAt": "2025-01-01T00:00:00Z",
                    "generator": "DMHelper",
                    "catalogVersion": "1.0",
                    "catalogSha256": "abc123",
                    "exclusions": []
                  },
                  "campaign": {
                    "key": "campaign-quest",
                    "name": "Quest Test",
                    "createdAt": "2025-01-01T00:00:00Z",
                    "settings": { "levelingMode": "XP" }
                  },
                  "assets": [],
                  "party": [],
                  "customStatBlocks": [],
                  "customSpells": [],
                  "customConditions": [],
                  "customRules": [],
                  "customEquipment": [],
                  "customMagicItems": [],
                  "customClasses": [],
                  "customSpecies": [],
                  "customBackgrounds": [],
                  "customFeats": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [],
                  "notes": [],
                  "quickNotes": [],
                  "assignments": [],
                  "ledgerEntries": [],
                  "timelineEvents": [],
                  "adventures": [],
                  "session": {
                    "key": "session-1",
                    "status": "RUNNING",
                    "startedAt": "2025-01-01T00:00:00Z",
                    "presentationMode": "MAP",
                    "presentedRef": { "scope": "PACKAGE", "type": "MAP", "key": "map-crypt" },
                    "attendeeRefs": [{ "scope": "PACKAGE", "type": "PARTY_MEMBER", "key": "party-member-aria" }],
                    "sceneVisits": [{ "key": "visit-crypt", "sceneRef": { "scope": "PACKAGE", "type": "SCENE", "key": "scene-crypt" }, "visitedAt": "2025-01-01T00:00:00Z" }],
                    "objectiveChanges": [{ "key": "obj-change-1", "objectiveRef": { "scope": "PACKAGE", "type": "OBJECTIVE", "key": "obj-find-cave" }, "newStatus": "COMPLETED", "changedAt": "2025-01-01T00:00:00Z" }]
                  },
                  "quests": [{
                    "key": "quest-treasure",
                    "title": "Find the Treasure",
                    "status": "ACTIVE",
                    "summary": "Find the lost treasure",
                    "tags": ["main", "treasure"],
                    "rewards": "500 XP",
                    "prerequisites": "Must have map",
                    "outcomeNotes": "Treasure found!",
                    "links": [
                      { "role": "GIVER", "targetRef": { "scope": "PACKAGE", "type": "PARTY_MEMBER", "key": "party-member-aria" }, "displayText": "Aria gives quest", "sortOrder": 1 },
                      { "role": "RULE", "targetRef": { "scope": "CATALOG", "type": "RULE", "ruleset": "SRD_5_2", "sourceKey": "exploration-rule" }, "displayText": "Exploration rules", "sortOrder": 2 }
                    ],
                    "objectives": [
                      { "key": "obj-find-cave", "title": "Find the cave", "status": "COMPLETED", "completionMode": "ALL", "sortOrder": 1, "sourceLocator": "ch1" },
                      { "key": "obj-find-treasure", "title": "Find the treasure", "description": "Search the cave", "status": "ACTIVE", "completionMode": "ANY", "sortOrder": 2, "prerequisiteRefs": [{ "scope": "PACKAGE", "type": "OBJECTIVE", "key": "obj-find-cave" }], "sourceLocator": "ch2" }
                    ],
                    "createdAt": "2025-01-01T00:00:00Z"
                  }],
                  "diceRolls": []
                }
                """;
        assertThat(schema.validate(json)).isEmpty();
        CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);
        assertThat(manifest.quests()).hasSize(1);
        assertThat(manifest.quests().get(0).objectives()).hasSize(2);
        assertThat(manifest.quests().get(0).links()).hasSize(2);
        assertThat(manifest.session().objectiveChanges()).hasSize(1);
        assertThat(schema.validate(mapper.writeValueAsString(manifest))).isEmpty();
    }

    @Test
    void unknownFieldsRejectedBySchema() {
        assertThat(schema.validate(currentSurfaceManifest().replaceAll(
                "\\}$",
                ",\"unknownField\":\"value\"}"))).extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
    }

    @Test
    void unknownSceneKindRejectedBySchema() {
        assertThat(schema.validate(currentSurfaceManifest().replaceAll(
                "\"status\"\\s*:\\s*\"UNVISITED\"",
                "\"status\":\"INVALID_STATUS\""))).extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
    }

    @Test
    void unknownTransitionKindRejectedBySchema() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(currentSurfaceManifest());
        var adventure = root.withArray("adventures").addObject();
        adventure.put("key", "adv-test").put("name", "Test").put("sortOrder", 1).put("createdAt", "2025-01-01T00:00:00Z");
        var chapter = adventure.withArray("chapters").addObject();
        chapter.put("key", "ch-test").put("title", "Ch1").put("sortOrder", 1);
        var scene = chapter.withArray("scenes").addObject();
        scene.put("key", "sc-test").put("title", "Test").put("sortOrder", 1);
        var transition = scene.withArray("transitions").addObject();
        transition.put("key", "t-bad").put("kind", "INVALID_TRANSITION").put("sortOrder", 1);
        assertThat(schema.validate(mapper.writeValueAsString(root)))
                .extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
    }

    @Test
    void structuredAdventureEnumsMatchJavaEnums() throws Exception {
        JsonNode defs = mapper.readTree(fixture("schemas/campaign-format-v2.schema.json")).get("$defs");
        assertSchemaEnumEqualsJava(defs, "sceneSection", "kind",
                dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind.class);
        assertSchemaEnumEqualsJava(defs, "sceneCheck", "visibility",
                dev.hendrikhoemberg.dmhelper.adventure.data.SceneCheckVisibility.class);
        assertSchemaEnumEqualsJava(defs, "sceneParticipant", "disposition",
                dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition.class);
        assertSchemaEnumEqualsJava(defs, "sceneTransition", "kind",
                dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransitionKind.class);
        assertSchemaEnumEqualsJava(defs, "sceneLink", "role",
                dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole.class);
        assertSchemaEnumEqualsJava(defs, "quest", "status",
                dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus.class);
        assertSchemaEnumEqualsJava(defs, "questObjective", "status",
                dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus.class);
        assertSchemaEnumEqualsJava(defs, "questObjective", "completionMode",
                dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveCompletionMode.class);
        assertSchemaEnumEqualsJava(defs, "questLink", "role",
                dev.hendrikhoemberg.dmhelper.quest.data.QuestLinkRole.class);
        assertSchemaEnumEqualsJava(defs, "sourceAnnotation", "confidence",
                dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence.class);
        assertSchemaEnumEqualsJava(defs, "sourceAnnotation", "status",
                dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationStatus.class);
        assertSchemaEnumEqualsJava(defs, "sessionObjectiveChange", "previousStatus",
                dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus.class);
        assertSchemaEnumEqualsJava(defs, "sessionObjectiveChange", "newStatus",
                dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus.class);
    }

    private static void assertSchemaEnumEqualsJava(JsonNode defs, String defName, String property,
                                                   Class<? extends Enum<?>> javaEnum) {
        JsonNode enumNode = defs.get(defName).get("properties").get(property).get("enum");
        assertThat(enumNode).as("$defs.%s.properties.%s.enum", defName, property).isNotNull();
        List<String> schemaValues = new ArrayList<>();
        enumNode.forEach(n -> schemaValues.add(n.textValue()));
        List<String> javaValues = java.util.Arrays.stream(javaEnum.getEnumConstants())
                .map(Enum::name)
                .toList();
        assertThat(schemaValues)
                .as("schema enum for %s.%s must match %s", defName, property, javaEnum.getSimpleName())
                .containsExactlyElementsOf(javaValues);
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
