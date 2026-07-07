package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class ConditionSeedService {

    private static final Logger log = LoggerFactory.getLogger(ConditionSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-conditions.json";

    private final ConditionRepository repository;
    private final ObjectMapper objectMapper;

    public ConditionSeedService(ConditionRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Condition data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 condition data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<ConditionEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<ConditionEntry>>() {});
                int count = 0;
                for (ConditionEntry entry : entries) {
                    Condition c = new Condition();
                    c.setSourceKey(entry.sourceKey());
                    c.setName(entry.name());
                    c.setDescription(entry.description());
                    repository.save(c);
                    count++;
                }
                log.info("Seeded {} conditions", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed condition data", e);
            throw new RuntimeException("Failed to seed SRD condition data", e);
        }
    }

    public record ConditionEntry(String sourceKey, String name, String description) {}
}
