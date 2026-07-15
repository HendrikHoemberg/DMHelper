package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignDtoSchemaCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CampaignSchemaValidator schema = new CampaignSchemaValidator();

    @Test
    void everyCheckedInValidFixtureDeserializesIntoTheStrictDto() throws Exception {
        for (String name : new String[]{"minimal", "feature-complete"}) {
            String json = resource("campaigns/v1/" + name + ".dmcampaign.json");
            assertThat(schema.validate(json)).as(name).isEmpty();
            assertThat(mapper.readValue(json, CampaignExportDto.class).formatVersion()).isEqualTo(1);
        }
    }

    @Test
    void serializingTheFeatureCompleteDtoProducesSchemaValidJson() throws Exception {
        CampaignExportDto dto = mapper.readValue(
                resource("campaigns/v1/feature-complete.dmcampaign.json"),
                CampaignExportDto.class);
        String serialized = mapper.writeValueAsString(dto);
        assertThat(schema.validate(serialized)).isEmpty();
    }

    @Test
    void everySchemaDefObjectHasExactlyTheRecordComponentsAndAdditionalPropertiesFalse() throws Exception {
        JsonNode campaignSchema = mapper.readTree(
                resource("schemas/campaign-format.schema.json"));
        JsonNode mapSchema = mapper.readTree(
                resource("schemas/map-document.schema.json"));

        Map<Class<?>, JsonNode> recordDefs = new LinkedHashMap<>();
        // Campaign schema $defs
        recordDefs.put(CampaignExportDto.class, campaignSchema);
        recordDefs.put(CampaignExportDto.CampaignDto.class, defs(campaignSchema, "campaign"));
        recordDefs.put(CampaignExportDto.PartyMemberExportDto.class, defs(campaignSchema, "partyMember"));
        recordDefs.put(CampaignExportDto.SheetExportDto.class, defs(campaignSchema, "sheet"));
        recordDefs.put(CampaignExportDto.ClassLevelExportDto.class, defs(campaignSchema, "classLevel"));
        recordDefs.put(CampaignExportDto.ResourceExportDto.class, defs(campaignSchema, "resource"));
        recordDefs.put(CampaignExportDto.SpellRefExportDto.class, defs(campaignSchema, "spellRef"));
        recordDefs.put(CampaignExportDto.StatBlockExportDto.class, defs(campaignSchema, "statBlock"));
        recordDefs.put(CampaignExportDto.HandoutExportDto.class, defs(campaignSchema, "handout"));
        recordDefs.put(CampaignExportDto.MapExportDto.class, defs(campaignSchema, "map"));
        recordDefs.put(CampaignExportDto.MapExportDto.GridDto.class, defs(campaignSchema, "mapGrid"));
        recordDefs.put(CampaignExportDto.MapExportDto.TokenExportDto.class, defs(campaignSchema, "token"));
        recordDefs.put(CampaignExportDto.EncounterExportDto.class, defs(campaignSchema, "encounter"));
        recordDefs.put(CampaignExportDto.CombatantExportDto.class, defs(campaignSchema, "combatant"));
        recordDefs.put(CampaignExportDto.NoteExportDto.class, defs(campaignSchema, "note"));
        recordDefs.put(CampaignExportDto.QuickNoteExportDto.class, defs(campaignSchema, "quicknote"));
        recordDefs.put(CampaignExportDto.AssignmentExportDto.class, defs(campaignSchema, "assignment"));
        recordDefs.put(CampaignExportDto.LedgerExportDto.class, defs(campaignSchema, "ledgerEntry"));
        recordDefs.put(CampaignExportDto.TimelineExportDto.class, defs(campaignSchema, "timelineEvent"));
        recordDefs.put(CampaignExportDto.AdventureExportDto.class, defs(campaignSchema, "adventure"));
        recordDefs.put(CampaignExportDto.ChapterExportDto.class, defs(campaignSchema, "chapter"));
        recordDefs.put(CampaignExportDto.SceneExportDto.class, defs(campaignSchema, "scene"));
        // Map document schema $defs
        recordDefs.put(MapDocumentDto.class, mapSchema);
        recordDefs.put(MapDocumentDto.GridDto.class, defs(mapSchema, "documentGrid"));
        recordDefs.put(MapLayerDto.class, defs(mapSchema, "layer"));
        recordDefs.put(MapLayerDto.CellDto.class, defs(mapSchema, "cell"));
        recordDefs.put(MapLayerDto.ShapeDto.class, defs(mapSchema, "shape"));
        recordDefs.put(MapLayerDto.ImageDto.class, defs(mapSchema, "image"));
        recordDefs.put(MapDocumentDto.PrimitiveDto.class, defs(mapSchema, "primitive"));
        recordDefs.put(MapDocumentDto.TerrainDefDto.class, defs(mapSchema, "terrain"));

        for (var entry : recordDefs.entrySet()) {
            Class<?> recordClass = entry.getKey();
            JsonNode defNode = entry.getValue();

            JsonNode apNode = defNode.get("additionalProperties");
            assertThat(apNode).as("%s schema must have additionalProperties", recordClass.getSimpleName()).isNotNull();
            assertThat(apNode.asBoolean()).as("%s schema must have additionalProperties: false", recordClass.getSimpleName()).isFalse();

            JsonNode properties = defNode.get("properties");
            assertThat(properties).as("%s schema must have a properties object", recordClass.getSimpleName()).isNotNull();

            Set<String> schemaPropertyNames = new TreeSet<>();
            schemaPropertyNames.addAll(properties.propertyNames());

            Set<String> recordComponentNames = Arrays.stream(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(toCollection(TreeSet::new));

            assertThat(schemaPropertyNames)
                    .as("%s schema properties must match record components exactly", recordClass.getSimpleName())
                    .containsExactlyElementsOf(recordComponentNames);
        }
    }

    private static JsonNode defs(JsonNode schema, String name) {
        return schema.get("$defs").get(name);
    }

    private static String resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
