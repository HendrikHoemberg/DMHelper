package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class RuleSectionSeedService {

    private static final Logger log = LoggerFactory.getLogger(RuleSectionSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-rules.json";

    private final RuleSectionRepository repository;
    private final ObjectMapper objectMapper;

    public RuleSectionSeedService(RuleSectionRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Rule section data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 rule section data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<RuleEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<RuleEntry>>() {});
                int count = 0;
                for (RuleEntry entry : entries) {
                    RuleSection r = new RuleSection();
                    r.setSource(ContentSource.SRD);
                    r.setSourceKey(entry.key());
                    r.setName(entry.name());
                    r.setBody(entry.desc());
                    r.setRuleset(entry.ruleset());
                    r.setSortOrder(entry.index());
                    r.setInitialHeaderLevel(entry.initialHeaderLevel());
                    if (entry.parent() != null && !entry.parent().isBlank()) {
                        r.setParentKey(entry.parent());
                    }
                    repository.save(r);
                    count++;
                }
                log.info("Seeded {} rule sections", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed rule section data", e);
            throw new RuntimeException("Failed to seed SRD rule section data", e);
        }
    }

    public record RuleEntry(
            String key, String name, String desc, String ruleset,
            int index, int initialHeaderLevel, String parent
    ) {}
}
