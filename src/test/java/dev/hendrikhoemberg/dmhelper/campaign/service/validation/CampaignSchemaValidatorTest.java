package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import org.junit.jupiter.api.Test;
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
}
