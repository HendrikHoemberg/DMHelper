package dev.hendrikhoemberg.dmhelper.campaign.packagev2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyHandoutSafetyToleranceTest {

    private static final Path FIXTURE = Path.of(
            "src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json");

    private final JsonMapper strictMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final CampaignManifestV2SchemaValidator schemaValidator = new CampaignManifestV2SchemaValidator();

    @Test
    void theFixtureStillExercisesTheRetiredFields() throws Exception {
        assertThat(Files.readString(FIXTURE)).contains("safetyClassification");
    }

    @Test
    void schemaValidationAcceptsPackagesThatStillCarrySafetyClassification() throws Exception {
        String json = Files.readString(FIXTURE);

        List<CampaignImportProblem> problems = schemaValidator.validate(json);

        assertThat(problems)
                .as("packages authored before the DM-only cut must still validate")
                .isEmpty();
    }

    @Test
    void deserialisationAcceptsAllThreeRetiredHandoutFields() throws Exception {
        String json = """
                {"key":"legacy","title":"Karte","tags":[],"assetRef":"a1",
                 "contentType":"image/png","dmOnly":true,"presented":false,
                 "safetyClassification":"DM_SOURCE",
                 "sourceRef":{"scope":"PACKAGE","type":"HANDOUT","key":"source-h1"},
                 "derivativeRecipe":"{}"}
                """;

        assertThatCode(() -> strictMapper.readValue(json, CampaignManifestV2.HandoutDto.class))
                .as("FAIL_ON_UNKNOWN_PROPERTIES must not reject retired fields")
                .doesNotThrowAnyException();
    }
}
