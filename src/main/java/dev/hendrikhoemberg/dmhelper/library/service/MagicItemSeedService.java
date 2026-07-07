package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class MagicItemSeedService {

    private static final Logger log = LoggerFactory.getLogger(MagicItemSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-magic-items.json";

    private final MagicItemRepository repository;
    private final ObjectMapper objectMapper;

    public MagicItemSeedService(MagicItemRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Magic item data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 magic item data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<MagicItemEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<MagicItemEntry>>() {});
                int count = 0;
                for (MagicItemEntry entry : entries) {
                    MagicItem m = new MagicItem();
                    m.setSourceKey(entry.key());
                    m.setName(entry.name());
                    m.setDescription(entry.desc());
                    if (entry.category() != null) {
                        m.setCategory(entry.category().get("name"));
                    }
                    if (entry.rarity() != null) {
                        Object rarityName = entry.rarity().get("name");
                        m.setRarity(rarityName != null ? rarityName.toString() : null);
                    }
                    if (entry.weapon() != null) {
                        Object weaponName = entry.weapon().get("name");
                        m.setType(weaponName != null ? weaponName.toString() : null);
                    } else if (entry.armor() != null) {
                        Object armorName = entry.armor().get("name");
                        m.setType(armorName != null ? armorName.toString() : null);
                    }
                    m.setWeight(entry.weight());
                    m.setCost(entry.cost());
                    m.setRequiresAttunement(entry.requires_attunement());
                    m.setAttunementDetail(entry.attunement_detail());
                    repository.save(m);
                    count++;
                }
                log.info("Seeded {} magic items", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed magic item data", e);
            throw new RuntimeException("Failed to seed SRD magic item data", e);
        }
    }

    public record MagicItemEntry(
            String key, String name, String desc,
            Map<String, String> category,
            Map<String, Object> rarity,
            Map<String, Object> weapon,
            Map<String, Object> armor,
            String weight, String cost,
            boolean requires_attunement, String attunement_detail
    ) {}
}
