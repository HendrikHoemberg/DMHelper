package dev.hendrikhoemberg.dmhelper.agent;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportProblemCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ValidationErrorCatalogTest {

    @Autowired
    AgentContractService service;

    @Test
    void catalogCoversEveryImportProblemCode() {
        var codes = service.validationErrors().errors().stream()
                .map(ValidationErrorCatalog.Entry::code)
                .collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrderElementsOf(ImportProblemCodes.all());
    }

    @Test
    void everyEntryHasSummaryAndDefaultSeverity() {
        assertThat(service.validationErrors().errors()).allSatisfy(e -> {
            assertThat(e.code()).isNotBlank();
            assertThat(e.defaultSeverity()).isIn("ERROR", "WARNING", "INFO");
            assertThat(e.summary()).isNotBlank();
        });
    }
}
