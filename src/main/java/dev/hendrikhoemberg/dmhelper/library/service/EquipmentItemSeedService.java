package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class EquipmentItemSeedService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentItemSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-equipment.json";

    private final EquipmentItemRepository repository;
    private final ObjectMapper objectMapper;

    public EquipmentItemSeedService(EquipmentItemRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Equipment item data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 equipment item data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<EquipmentEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<EquipmentEntry>>() {});
                int count = 0;
                for (EquipmentEntry entry : entries) {
                    EquipmentItem e = new EquipmentItem();
                    e.setSource(ContentSource.SRD);
                    e.setSourceKey(entry.sourceKey());
                    e.setName(entry.name());
                    e.setCategory(EquipmentItem.Category.valueOf(entry.category()));
                    e.setCost(entry.cost());
                    e.setWeight(entry.weight());
                    e.setProperties(entry.properties());
                    e.setDescription(entry.description());
                    repository.save(e);
                    count++;
                }
                log.info("Seeded {} equipment items", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed equipment item data", e);
            throw new RuntimeException("Failed to seed SRD equipment item data", e);
        }
    }

    public record EquipmentEntry(
            String sourceKey, String name, String category,
            String cost, String weight, String properties, String description
    ) {}
}
