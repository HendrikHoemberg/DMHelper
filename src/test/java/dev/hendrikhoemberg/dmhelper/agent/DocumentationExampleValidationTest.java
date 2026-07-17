package dev.hendrikhoemberg.dmhelper.agent;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportProblemCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentationExampleValidationTest {

    @Autowired CampaignPackageValidationPipeline pipeline;
    @TempDir Path tempDir;

    @Test
    void minimalValidHasNoErrors() throws Exception {
        var result = validateClasspath("docs-examples/minimal-valid.dmcampaign.json");
        assertThat(result.valid()).isTrue();
    }

    @Test
    void schemaErrorExampleFailsSchema() throws Exception {
        var result = validateClasspath("docs-examples/schema-error.dmcampaign.json");
        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).anyMatch(p ->
                p.code().equals(ImportProblemCodes.SCHEMA_VIOLATION));
    }

    @Test
    void semanticErrorExampleIsSchemaValidButSemanticallyInvalid() throws Exception {
        var result = validateClasspath("docs-examples/semantic-error.dmcampaign.json");
        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).anyMatch(p ->
                p.code().equals(ImportProblemCodes.UNRESOLVED_REFERENCE)
                        || p.code().equals(ImportProblemCodes.UNRESOLVED_CATALOG_REFERENCE));
    }

    @Test
    void documentedFlagshipFixturesStillExist() {
        assertThat(new ClassPathResource("campaigns/v2/minimal.dmcampaign.json").exists()).isTrue();
        assertThat(new ClassPathResource("campaigns/v2/feature-complete.dmcampaign/manifest.json").exists()).isTrue();
        assertThat(new ClassPathResource("campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json").exists()).isTrue();
    }

    private CampaignPackageValidationResult validateClasspath(String resourcePath) throws Exception {
        var resource = new ClassPathResource(resourcePath);
        byte[] json = resource.getInputStream().readAllBytes();
        var reader = new CampaignPackageReader(tempDir);
        var staged = reader.read(new ByteArrayInputStream(json), "fixture.json", "application/json");
        return pipeline.validate(staged);
    }
}
