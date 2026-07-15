package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import com.networknt.schema.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public final class CampaignSchemaValidator {
    public static final String CAMPAIGN_ID = "https://dmhelper/campaign-format.schema.json";
    public static final String MAP_ID = "https://dmhelper/map-document.schema.json";

    private final Schema schema;

    public CampaignSchemaValidator() {
        String campaignSchema = read("schemas/campaign-format.schema.json");
        String mapSchema = read("schemas/map-document.schema.json");
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(
                        CAMPAIGN_ID, campaignSchema,
                        MAP_ID, mapSchema)));
        this.schema = registry.getSchema(SchemaLocation.of(CAMPAIGN_ID));
    }

    public List<CampaignImportProblem> validate(String json) {
        return schema.validate(json, InputFormat.JSON, context ->
                        context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream()
                .map(error -> new CampaignImportProblem(
                        ImportSeverity.ERROR,
                        "SCHEMA_" + error.getKeyword()
                                .replaceAll("([a-z])([A-Z])", "$1_$2")
                                .toUpperCase(Locale.ROOT)
                                .replace('-', '_'),
                        error.getInstanceLocation().toString(),
                        error.getMessage(),
                        "Match the field type, required fields, enum, range, or closed-object shape in campaign-format.schema.json."))
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
