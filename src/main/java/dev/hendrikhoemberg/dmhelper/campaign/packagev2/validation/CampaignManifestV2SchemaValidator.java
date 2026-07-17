package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportProblemCodes;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;

@Component
public final class CampaignManifestV2SchemaValidator {

    public static final String CAMPAIGN_V2_ID = "https://dmhelper/campaign-format-v2.schema.json";
    public static final String MAP_V2_ID = "https://dmhelper/map-document-v2.schema.json";

    private final Schema schema;

    public CampaignManifestV2SchemaValidator() {
        String campaignSchema = read("schemas/campaign-format-v2.schema.json");
        String mapSchema = read("schemas/map-document-v2.schema.json");
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(
                        CAMPAIGN_V2_ID, campaignSchema,
                        MAP_V2_ID, mapSchema)));
        this.schema = registry.getSchema(SchemaLocation.of(CAMPAIGN_V2_ID));
    }

    public List<CampaignImportProblem> validate(String json) {
        return schema.validate(json, InputFormat.JSON, context ->
                        context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream()
                .map(error -> new CampaignImportProblem(
                        ImportSeverity.ERROR,
                        ImportProblemCodes.SCHEMA_VIOLATION,
                        error.getInstanceLocation().toString(),
                        error.getMessage(),
                        "Match the field type, required fields, enum, range, or closed-object shape in campaign-format-v2.schema.json."))
                .sorted(Comparator.comparing(CampaignImportProblem::path)
                        .thenComparing(CampaignImportProblem::code))
                .toList();
    }

    private static String read(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Required campaign schema is unavailable: " + path, e);
        }
    }
}
