package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class FeatSeedService {

    private static final Logger log = LoggerFactory.getLogger(FeatSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-feats.json";

    private final FeatRepository repository;
    private final ObjectMapper objectMapper;

    public FeatSeedService(FeatRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Feat data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 feat data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<FeatEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<FeatEntry>>() {});
                int count = 0;
                for (FeatEntry entry : entries) {
                    Feat f = new Feat();
                    f.setSourceKey(entry.key());
                    f.setName(entry.name());
                    f.setCategory(entry.type());
                    f.setPrerequisite(entry.prerequisite());
                    f.setBenefit(entry.desc());
                    repository.save(f);
                    count++;
                }
                log.info("Seeded {} feats", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed feat data", e);
            throw new RuntimeException("Failed to seed SRD feat data", e);
        }
    }

    public record FeatEntry(
            String key, String name, String desc,
            String type, String prerequisite
    ) {}
}
