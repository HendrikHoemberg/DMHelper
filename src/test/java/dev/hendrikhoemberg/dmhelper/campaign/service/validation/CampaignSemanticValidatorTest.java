package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CampaignSemanticValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CampaignCatalogResolver catalog = permissiveCatalog();
    private final CampaignSemanticValidator validator = new CampaignSemanticValidator(catalog);

    @Test
    void validFeatureCompleteFixtureProducesNoProblems() throws Exception {
        String json = resource("campaigns/v1/feature-complete.dmcampaign.json");
        CampaignExportDto dto = mapper.readValue(json, CampaignExportDto.class);
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void duplicateMapKey() throws Exception {
        var problems = validate(tree -> {
            JsonNode maps = tree.get("maps");
            ObjectNode map1 = (ObjectNode) maps.get(0).deepCopy();
            map1.put("name", "Duplicate Map");
            map1.putArray("tokens");
            ((ArrayNode) maps).add(map1);
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "DUPLICATE_REFERENCE", "/maps/1/key",
                "Map key 'map-crypt' is used by multiple maps.",
                "Use a unique key for each map."
        ));
    }

    @Test
    void ambiguousMapReference() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("encounters").get(0)).put("map", (String) null);
            JsonNode maps = tree.get("maps");
            ObjectNode map1 = (ObjectNode) maps.get(0).deepCopy();
            map1.put("key", "map-crypt-2");
            map1.put("name", "The Crypt");
            map1.putArray("tokens");
            ((ArrayNode) maps).add(map1);
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "AMBIGUOUS_REFERENCE", "/adventures/0/chapters/0/scenes/0/map",
                "Map name 'The Crypt' matches multiple maps (map-crypt, map-crypt-2).",
                "Reference the map by its unique key instead of name."
        ));
    }

    @Test
    void unresolvedEncounterMap() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("encounters").get(0)).put("map", "NonExistentMap");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "UNRESOLVED_REFERENCE", "/encounters/0/map",
                "Map 'NonExistentMap' does not exist in this campaign document.",
                "Use map name 'The Crypt' or define the missing map."
        ));
    }

    @Test
    void unresolvedCombatantTokenId() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("encounters").get(0).get("combatants").get(0)).put("tokenId", "nonexistent");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "UNRESOLVED_REFERENCE", "/encounters/0/combatants/0/tokenId",
                "Token 'nonexistent' does not exist in this campaign document.",
                "Use token ID 'token-goblin-1' or define the missing token."
        ));
    }

    @Test
    void unresolvedAssignmentHolderName() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("assignments").get(0)).put("holderName", "Nobody");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "UNRESOLVED_REFERENCE", "/assignments/0/holderName",
                "Party member 'Nobody' does not exist in this campaign document.",
                "Use character name 'Aria' or define the missing party member."
        ));
    }

    @Test
    void unresolvedLedgerAssignmentRef() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("ledger").get(0)).put("itemAssignmentRef", "00000000-0000-0000-0000-000000000999");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "UNRESOLVED_REFERENCE", "/ledger/0/itemAssignmentRef",
                "Assignment '00000000-0000-0000-0000-000000000999' does not exist in this campaign document.",
                "Use assignment ID '00000000-0000-0000-0000-000000000101' or define the missing assignment."
        ));
    }

    @Test
    void unresolvedTimelineNoteReference() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("timeline").get(0)).put("noteTitle", "Unknown Note");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "UNRESOLVED_REFERENCE", "/timeline/0/noteTitle",
                "Note 'Unknown Note' does not exist in this campaign document.",
                "Use note title 'Crypt Lore' or define the missing note."
        ));
    }

    @Test
    void unresolvedQuickNoteTargetRef() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("quicknotes").get(0)).put("targetRef", "");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "UNRESOLVED_REFERENCE", "/quicknotes/0/targetRef",
                "Quick note target reference is blank for target type 'CAMPAIGN'.",
                "Provide a non-blank target reference for the quick note."
        ));
    }

    @Test
    void campaignQuickNoteRequiresExactSentinel() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("quicknotes").get(0)).put("targetRef", "campaign-ref"));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("UNRESOLVED_REFERENCE");
            assertThat(problem.path()).isEqualTo("/quicknotes/0/targetRef");
        });
    }

    @Test
    void sceneQuickNoteRequiresFullScenePath() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("quicknotes").get(7)).put("targetRef", "crypt-entry"));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("UNRESOLVED_REFERENCE");
            assertThat(problem.path()).isEqualTo("/quicknotes/7/targetRef");
        });
    }

    @Test
    void duplicatePartyNameMakesConsumerAmbiguous() throws Exception {
        var problems = validate(tree -> {
            ObjectNode duplicate = (ObjectNode) tree.get("party").get(0).deepCopy();
            ((ArrayNode) tree.get("party")).add(duplicate);
        });

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("DUPLICATE_REFERENCE");
            assertThat(problem.path()).isEqualTo("/party/1/characterName");
        });
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("AMBIGUOUS_REFERENCE");
            assertThat(problem.path()).isEqualTo("/quicknotes/2/targetRef");
        });
    }

    @Test
    void duplicateNoteTitleMakesConsumerAmbiguous() throws Exception {
        var problems = validate(tree -> {
            ObjectNode duplicate = (ObjectNode) tree.get("notes").get(0).deepCopy();
            ((ArrayNode) tree.get("notes")).add(duplicate);
        });

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("DUPLICATE_REFERENCE");
            assertThat(problem.path()).isEqualTo("/notes/1/title");
        });
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("AMBIGUOUS_REFERENCE");
            assertThat(problem.path()).isEqualTo("/quicknotes/4/targetRef");
        });
    }

    @Test
    void duplicateSceneKeyWithinChapterMakesFullPathAmbiguous() throws Exception {
        var problems = validate(tree -> {
            ArrayNode scenes = (ArrayNode) tree.get("adventures").get(0).get("chapters").get(0).get("scenes");
            scenes.add(scenes.get(0).deepCopy());
        });

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("DUPLICATE_REFERENCE");
            assertThat(problem.path()).isEqualTo("/adventures/0/chapters/0/scenes/1/sceneKey");
        });
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("AMBIGUOUS_REFERENCE");
            assertThat(problem.path()).isEqualTo("/quicknotes/7/targetRef");
        });
    }

    @Test
    void globalStatBlockKeyIsAcceptedForToken() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("maps").get(0).get("tokens").get(0))
                        .put("statBlockKey", "srd-global-goblin"));

        assertThat(problems).isEmpty();
    }

    @Test
    void outOfBoundsTokenPositionX() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("maps").get(0).get("tokens").get(0)).put("positionX", 960);
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "OUT_OF_BOUNDS", "/maps/0/tokens/0/positionX",
                "Token positionX (960) with sizeCols=1, cellPx=48 exceeds map width of 960 px.",
                "Ensure x + sizeCols * cellPx <= map width in pixels."
        ));
    }

    @Test
    void negativeTokenPositionX() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("maps").get(0).get("tokens").get(0)).put("positionX", -1));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("OUT_OF_BOUNDS");
            assertThat(problem.path()).isEqualTo("/maps/0/tokens/0/positionX");
        });
    }

    @Test
    void embeddedMapCellMustRemainInsideGrid() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("maps").get(0).get("document").get("layers").get(0)
                        .get("cells").get(0)).put("col", 20));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("OUT_OF_BOUNDS");
            assertThat(problem.path()).isEqualTo("/maps/0/document/layers/0/cells/0/col");
        });
    }

    @Test
    void embeddedMapPrimitiveMustRemainInsideGrid() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("maps").get(0).get("document").get("primitives").get(0))
                        .put("endCol", 20));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("OUT_OF_BOUNDS");
            assertThat(problem.path()).isEqualTo("/maps/0/document/primitives/0/endCol");
        });
    }

    @Test
    void embeddedMapShapeExtentMustRemainInsideGrid() throws Exception {
        var problems = validate(tree -> {
            ArrayNode points = ((ObjectNode) tree.get("maps").get(0).get("document").get("layers").get(1)
                    .get("shapes").get(0)).putArray("points");
            points.add(18).add(0).add(3).add(1);
        });

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("OUT_OF_BOUNDS");
            assertThat(problem.path()).isEqualTo("/maps/0/document/layers/1/shapes/0/points");
        });
    }

    @Test
    void embeddedMapImageExtentMustRemainInsideGrid() throws Exception {
        var problems = validate(tree ->
                ((ObjectNode) tree.get("maps").get(0).get("document").get("layers").get(3)
                        .get("image")).put("x", 1));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("OUT_OF_BOUNDS");
            assertThat(problem.path()).isEqualTo("/maps/0/document/layers/3/image");
        });
    }

    @Test
    void outOfBoundsScenePin() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("adventures").get(0).get("chapters").get(0).get("scenes").get(0).get("pin"))
                    .put("x", 960);
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "OUT_OF_BOUNDS", "/adventures/0/chapters/0/scenes/0/pin",
                "Pin x coordinate (960) is outside the map bounds [0, 960).",
                "Adjust pin x to be within the range 0 <= x < map width in pixels."
        ));
    }

    @Test
    void gridMismatch() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("maps").get(0).get("document").get("grid")).put("gridType", "hex");
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "GRID_MISMATCH", "/maps/0/document/grid",
                "Embedded map document grid (gridType='hex') does not match outer map grid (gridType='SQUARE').",
                "Set the embedded document grid gridType to the lowercase equivalent of the outer map grid type."
        ));
    }

    @Test
    void invalidStateMultipleActiveEncounters() throws Exception {
        var problems = validate(tree -> {
            ((ObjectNode) tree.get("encounters").get(0)).put("status", "ACTIVE");
            JsonNode encounters = tree.get("encounters");
            ObjectNode enc1 = (ObjectNode) encounters.get(0).deepCopy();
            enc1.put("encounterKey", "second-encounter");
            enc1.put("name", "Second Encounter");
            enc1.put("status", "ACTIVE");
            enc1.putArray("combatants");
            ((ArrayNode) encounters).add(enc1);
        });
        assertThat(problems).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "INVALID_STATE", "/encounters/1/status",
                "Campaign has 2 active encounters. At most one encounter may have ACTIVE status.",
                "Set at most one encounter to ACTIVE status."
        ));
    }

    private List<CampaignImportProblem> validate(Consumer<JsonNode> mutator) throws Exception {
        String json = resource("campaigns/v1/feature-complete.dmcampaign.json");
        JsonNode tree = mapper.readTree(json);
        mutator.accept(tree);
        CampaignExportDto dto = mapper.treeToValue(tree, CampaignExportDto.class);
        return validator.validate(dto);
    }

    private static String resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static CampaignCatalogResolver permissiveCatalog() {
        CampaignCatalogResolver catalog = mock(CampaignCatalogResolver.class);
        lenient().when(catalog.hasStatBlock(anyString())).thenReturn(true);
        lenient().when(catalog.hasSpecies(anyString())).thenReturn(true);
        lenient().when(catalog.hasBackground(anyString())).thenReturn(true);
        lenient().when(catalog.hasCharacterClass(anyString())).thenReturn(true);
        lenient().when(catalog.hasFeat(anyString())).thenReturn(true);
        lenient().when(catalog.hasSpell(anyString())).thenReturn(true);
        lenient().when(catalog.hasMagicItem(anyString())).thenReturn(true);
        lenient().when(catalog.hasEquipmentItem(anyString())).thenReturn(true);
        return catalog;
    }
}
