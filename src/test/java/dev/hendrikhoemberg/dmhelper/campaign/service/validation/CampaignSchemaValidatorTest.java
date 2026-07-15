package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignSchemaValidatorTest {
    private final CampaignSchemaValidator validator = new CampaignSchemaValidator();

    @Test
    void acceptsTheSmallestV1Document() {
        var problems = validator.validate("""
                {"formatVersion":1,"campaign":{"name":"Smallest"}}
                """);
        assertThat(problems).isEmpty();
    }

    @Test
    void reportsJsonPointerAndStableCodeForSchemaFailure() {
        var problems = validator.validate("""
                {"formatVersion":1,"campaign":{"name":""}}
                """);
        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(ImportSeverity.ERROR);
            assertThat(problem.code()).isEqualTo("SCHEMA_MIN_LENGTH");
            assertThat(problem.path()).isEqualTo("/campaign/name");
            assertThat(problem.message()).contains("must");
        });
    }

    @Test
    void acceptsRuntimeMapDocumentShapesThroughCampaignRef() {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": {"name": "Closed map"},
                  "maps": [{
                    "key":"map-1", "name":"Map", "movementMode":"GRID", "showGrid":true,
                    "grid":{"w":10,"h":8,"cellPx":48,"gridType":"SQUARE"},
                    "document":{
                      "schemaVersion":1,
                      "grid":{"width":10,"height":8,"cellSizePx":48,"gridType":"square","movementMode":"GRID","showGrid":true},
                      "layers":[
                        {"id":"terrain","name":"Terrain","type":"TERRAIN","cells":[],"shapes":[]},
                        {"id":"objects","name":"Objects","type":"OBJECTS","cells":[],"shapes":[]},
                        {"id":"annotations","name":"Annotations (DM only)","type":"ANNOTATIONS","cells":[],"shapes":[
                          {"type":"rect","points":[0,0,5,5],"fill":"#ff0000","stroke":"#000","strokeWidth":1,"label":"box"}
                        ]},
                        {"id":"bg","name":"Background","type":"IMAGE","cells":[],"shapes":[],
                         "image":{"dataUrl":"data:image/png;base64,AAAA","x":0,"y":0,"width":10,"height":10}}
                      ],
                      "primitives":[
                        {"type":"ROOM","startCol":2,"startRow":2,"endCol":10,"endRow":8},
                        {"type":"DOOR","startCol":10,"startRow":5,"endCol":10,"endRow":5}
                      ],
                      "customTerrain":[
                        {"key":"moss","name":"Moss","fill":"#2a6e3a","walkable":true}
                      ]
                    },
                    "tokens":[]
                  }]
                }
                """;
        assertThat(validator.validate(json)).isEmpty();
    }

    @Test
    void rejectsUnknownNestedMapProperty() {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": {"name": "Closed map"},
                  "maps": [{
                    "key":"map-1", "name":"Map", "movementMode":"GRID", "showGrid":true,
                    "grid":{"w":10,"h":8,"cellPx":48,"gridType":"SQUARE"},
                    "document":{
                      "schemaVersion":1,
                      "grid":{"width":10,"height":8,"cellSizePx":48,"gridType":"square","movementMode":"GRID","showGrid":true,"mystery":1},
                      "layers":[],"primitives":[],"customTerrain":[]
                    },
                    "tokens":[]
                  }]
                }
                """;

        assertThat(validator.validate(json))
                .extracting(CampaignImportProblem::code)
                .containsExactly("SCHEMA_ADDITIONAL_PROPERTIES");
    }

    @Test
    void bothSchemaDocumentsValidateAgainstDraft202012MetaSchema() throws Exception {
        SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
        Schema metaSchema = registry.getSchema(SchemaLocation.of(Dialects.getDraft202012().getId()));
        ObjectMapper mapper = new ObjectMapper();

        for (String resource : List.of(
                "schemas/campaign-format.schema.json",
                "schemas/map-document.schema.json")) {
            JsonNode schemaNode = mapper.readTree(resource(resource));
            assertThat(metaSchema.validate(schemaNode)).as(resource).isEmpty();
        }
    }

    private static String resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
