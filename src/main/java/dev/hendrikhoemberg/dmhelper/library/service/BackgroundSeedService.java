package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class BackgroundSeedService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-backgrounds.json";

    private final BackgroundRepository repository;
    private final ObjectMapper objectMapper;

    public BackgroundSeedService(BackgroundRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Background data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 background data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<BackgroundEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<BackgroundEntry>>() {});
                int count = 0;
                for (BackgroundEntry entry : entries) {
                    Background b = new Background();
                    b.setSourceKey(entry.key());
                    b.setName(entry.name());
                    b.setDescription(entry.desc());
                    repository.save(b);
                    count++;
                }
                log.info("Seeded {} backgrounds", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed background data", e);
            throw new RuntimeException("Failed to seed SRD background data", e);
        }
    }

    public record BackgroundEntry(String key, String name, String desc) {}
}
