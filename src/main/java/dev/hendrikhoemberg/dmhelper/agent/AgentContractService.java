package dev.hendrikhoemberg.dmhelper.agent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Service
public class AgentContractService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ValidationErrorCatalog validationErrors;

    public AgentContractService() {
        try {
            this.validationErrors = MAPPER.readValue(
                    new ClassPathResource("agent/validation-error-catalog.json").getInputStream(),
                    ValidationErrorCatalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load validation error catalog from classpath", e);
        }
    }

    public ValidationErrorCatalog validationErrors() {
        return validationErrors;
    }
}
