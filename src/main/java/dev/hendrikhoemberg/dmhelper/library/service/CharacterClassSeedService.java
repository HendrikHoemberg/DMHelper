package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CharacterClassSeedService {

    private static final Logger log = LoggerFactory.getLogger(CharacterClassSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-classes.json";

    private static final Map<String, String> SAVE_NAME_MAP = Map.of(
            "strength", "str", "dexterity", "dex", "constitution", "con",
            "intelligence", "int", "wisdom", "wis", "charisma", "cha"
    );

    private final CharacterClassRepository repository;
    private final ObjectMapper objectMapper;

    public CharacterClassSeedService(CharacterClassRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Character class data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 character class data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Source: {}, fetched: {}", source, fetched);
                List<ClassEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<ClassEntry>>() {});
                int count = 0;
                for (ClassEntry entry : entries) {
                    CharacterClass c = new CharacterClass();
                    c.setSourceKey(entry.key());
                    c.setName(entry.name());
                    c.setHitDie(entry.hit_dice());
                    c.setDescription(entry.desc());
                    try {
                        c.setSavingThrows(objectMapper.writeValueAsString(normalizeSavingThrows(entry.saving_throws())));
                    } catch (Exception ignored) {}
                    try {
                        c.setFeatures(objectMapper.writeValueAsString(entry.features()));
                    } catch (Exception ignored) {}
                    try {
                        c.setSpellcasting(objectMapper.writeValueAsString(entry.spellcasting()));
                    } catch (Exception ignored) {}
                    if (entry.subclass_of() != null) {
                        if (entry.subclass_of() instanceof Map<?, ?> map) {
                            Object key = map.get("key");
                            if (key != null) {
                                c.setSubclassOf(key.toString());
                            }
                        } else {
                            c.setSubclassOf(entry.subclass_of().toString());
                        }
                    }
                    repository.save(c);
                    count++;
                }
                log.info("Seeded {} character classes", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed character class data", e);
            throw new RuntimeException("Failed to seed SRD character class data", e);
        }
    }

    public record ClassEntry(
            String key, String name, String desc, String hit_dice,
            Object saving_throws, Object features, Object spellcasting,
            Object subclass_of
    ) {}

    @SuppressWarnings("unchecked")
    private List<String> normalizeSavingThrows(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item instanceof Map<?, ?> m) {
                            String name = (String) m.get("name");
                            if (name != null) {
                                return SAVE_NAME_MAP.getOrDefault(name.toLowerCase(), name.toLowerCase());
                            }
                        }
                        return item.toString();
                    })
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
