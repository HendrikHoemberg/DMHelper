package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class SpeciesSeedService {

    private static final Logger log = LoggerFactory.getLogger(SpeciesSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-species.json";

    private final SpeciesRepository repository;
    private final ObjectMapper objectMapper;

    public SpeciesSeedService(SpeciesRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Species data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 species data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<SpeciesEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<SpeciesEntry>>() {});
                int count = 0;
                for (SpeciesEntry entry : entries) {
                    Species s = new Species();
                    s.setSource(ContentSource.SRD);
                    s.setSourceKey(entry.key());
                    s.setName(entry.name());
                    s.setDescription(entry.desc());
                    if (entry.traits() != null) {
                        for (Map<String, Object> trait : entry.traits()) {
                            Object type = trait.get("type");
                            Object desc = trait.get("desc");
                            String descStr = desc != null ? desc.toString() : null;
                            if ("SIZE".equals(type)) {
                                s.setSize(descStr);
                            } else if ("SPEED".equals(type)) {
                                s.setSpeed(descStr);
                            }
                        }
                        try {
                            s.setTraits(objectMapper.writeValueAsString(entry.traits()));
                        } catch (Exception ignored) {}
                    }
                    repository.save(s);
                    count++;
                }
                log.info("Seeded {} species", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed species data", e);
            throw new RuntimeException("Failed to seed SRD species data", e);
        }
    }

    public record SpeciesEntry(
            String key, String name, String desc,
            List<Map<String, Object>> traits
    ) {}
}
