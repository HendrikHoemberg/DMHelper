package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Component
public final class CampaignImportValidator {
    private final ObjectMapper mapper;
    private final CampaignSchemaValidator schemaValidator;
    private final CampaignSemanticValidator semanticValidator;

    public CampaignImportValidator(ObjectMapper mapper,
                                   CampaignSchemaValidator schemaValidator,
                                   CampaignSemanticValidator semanticValidator) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.schemaValidator = schemaValidator;
        this.semanticValidator = semanticValidator;
    }

    public CampaignValidationResult validate(String json) {
        try {
            mapper.readTree(json);
        } catch (JacksonException e) {
            return new CampaignValidationResult(Optional.empty(), List.of(invalidJson(e)));
        }

        List<CampaignImportProblem> schemaProblems = schemaValidator.validate(json);
        if (!schemaProblems.isEmpty()) {
            return new CampaignValidationResult(Optional.empty(), schemaProblems);
        }

        CampaignExportDto dto;
        try {
            dto = mapper.readValue(json, CampaignExportDto.class);
        } catch (JacksonException e) {
            return new CampaignValidationResult(Optional.empty(), List.of(dtoDrift(e)));
        }

        List<CampaignImportProblem> semanticProblems = semanticValidator.validate(dto);
        return new CampaignValidationResult(Optional.of(dto), semanticProblems);
    }

    private static CampaignImportProblem invalidJson(JacksonException e) {
        return new CampaignImportProblem(
                ImportSeverity.ERROR,
                ImportProblemCodes.INVALID_JSON,
                "/",
                "Campaign file is not valid JSON.",
                "Fix the JSON syntax near line " + (e.getLocation() != null ? e.getLocation().getLineNr() : "?") + ".");
    }

    private static CampaignImportProblem dtoDrift(JacksonException e) {
        return new CampaignImportProblem(
                ImportSeverity.ERROR,
                ImportProblemCodes.DTO_SCHEMA_DRIFT,
                "/",
                "Schema-valid JSON cannot be deserialized into CampaignExportDto. " + e.getOriginalMessage(),
                "Report this as a schema/DTO compatibility issue.");
    }
}
