package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignImportValidatorTest {

    private ObjectMapper mapper;

    @Mock
    private CampaignSchemaValidator schemaValidator;

    @Mock
    private CampaignSemanticValidator semanticValidator;

    private CampaignImportValidator validator;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        validator = new CampaignImportValidator(mapper, schemaValidator, semanticValidator);
    }

    @Test
    void malformedJsonReturnsInvalidJsonProblem() {
        String json = "{ not valid";

        CampaignValidationResult result = validator.validate(json);

        assertThat(result.problems()).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "INVALID_JSON", "/",
                "Campaign file is not valid JSON.",
                "Fix the JSON syntax near line 1."));
        assertThat(result.campaign()).isEmpty();
        verifyNoInteractions(schemaValidator, semanticValidator);
    }

    @Test
    void schemaErrorsPreventDtdDeserialization() {
        String json = "{\"formatVersion\":1}";
        when(schemaValidator.validate(json)).thenReturn(List.of(
                new CampaignImportProblem(ImportSeverity.ERROR, "SCHEMA_REQUIRED", "/campaign", "required field", "fix")));

        CampaignValidationResult result = validator.validate(json);

        assertThat(result.problems()).hasSize(1);
        assertThat(result.campaign()).isEmpty();
        verify(schemaValidator).validate(json);
        verifyNoInteractions(semanticValidator);
    }

    @Test
    void dtoDriftAfterSchemaBecomesDtoSchemaDrift() {
        String json = "{\"formatVersion\":1,\"unknownField\":\"x\"}";
        when(schemaValidator.validate(json)).thenReturn(List.of());

        CampaignValidationResult result = validator.validate(json);

        assertThat(result.problems()).containsExactly(new CampaignImportProblem(
                ImportSeverity.ERROR, "DTO_SCHEMA_DRIFT", "/",
                "Schema-valid JSON cannot be deserialized into CampaignExportDto. Unrecognized property \"unknownField\" (class dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto), not marked as ignorable",
                "Report this as a schema/DTO compatibility issue."));
        assertThat(result.campaign()).isEmpty();
        verify(schemaValidator).validate(json);
        verifyNoInteractions(semanticValidator);
    }

    @Test
    void semanticValidationRunsExactlyOnceOnValidDto() {
        String json = """
                {"formatVersion":1,"campaign":{"name":"Test"},"party":[],"statBlocks":[],"handouts":[],"maps":[],"encounters":[],"notes":[],"quicknotes":[],"assignments":[],"ledger":[],"timeline":[],"adventures":[]}
                """;
        when(schemaValidator.validate(json)).thenReturn(List.of());
        when(semanticValidator.validate(any(CampaignExportDto.class))).thenReturn(List.of(
                new CampaignImportProblem(ImportSeverity.WARNING, "WARN", "/", "warning", "fix")));

        CampaignValidationResult result = validator.validate(json);

        assertThat(result.problems()).hasSize(1);
        assertThat(result.campaign()).isPresent();
        verify(schemaValidator).validate(json);
        verify(semanticValidator).validate(any(CampaignExportDto.class));
    }

    @Test
    void validDocumentStoresParsedDto() {
        String json = """
                {"formatVersion":1,"campaign":{"name":"Test","description":"desc"},"party":[],"statBlocks":[],"handouts":[],"maps":[],"encounters":[],"notes":[],"quicknotes":[],"assignments":[],"ledger":[],"timeline":[],"adventures":[]}
                """;
        when(schemaValidator.validate(json)).thenReturn(List.of());
        when(semanticValidator.validate(any(CampaignExportDto.class))).thenReturn(List.of());

        CampaignValidationResult result = validator.validate(json);

        assertThat(result.valid()).isTrue();
        assertThat(result.campaign()).isPresent();
        assertThat(result.campaign().get().formatVersion()).isEqualTo(1);
        assertThat(result.campaign().get().campaign().name()).isEqualTo("Test");
        verify(schemaValidator).validate(json);
        verify(semanticValidator).validate(any(CampaignExportDto.class));
    }
}
