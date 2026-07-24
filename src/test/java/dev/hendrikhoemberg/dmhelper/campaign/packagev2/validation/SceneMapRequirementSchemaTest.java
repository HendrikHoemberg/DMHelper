package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for Workstream C: the v3 schema-carried scene {@code mapRequirement} signal must be
 * accepted by manifest schema validation. The scene definition uses {@code additionalProperties:false},
 * so an unknown property is rejected — a v3 package declaring a scene map requirement would fail
 * import validation if {@code mapRequirement} is not declared in the schema.
 */
class SceneMapRequirementSchemaTest {

    private final CampaignManifestV2SchemaValidator schema = new CampaignManifestV2SchemaValidator();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void schemaAcceptsSceneMapRequirement() throws Exception {
        String json = readFixture(
                "campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json");

        // Baseline: the unmodified fixture is schema-valid, proving the harness itself is sound.
        assertThat(schema.validate(json)).isEmpty();

        ObjectNode root = (ObjectNode) mapper.readTree(json);
        root.put("formatVersion", 3);
        ObjectNode scene = (ObjectNode) root.get("adventures").get(0)
                .get("chapters").get(0).get("scenes").get(0);
        scene.put("mapRequirement", "REQUIRED");
        String v3Json = mapper.writeValueAsString(root);

        java.util.List<CampaignImportProblem> problems = schema.validate(v3Json);
        assertThat(problems).as(problems.toString()).isEmpty();
    }

    private static String readFixture(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
