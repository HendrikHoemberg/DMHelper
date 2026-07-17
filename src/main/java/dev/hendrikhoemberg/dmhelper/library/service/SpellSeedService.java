package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class SpellSeedService {

    private static final Logger log = LoggerFactory.getLogger(SpellSeedService.class);
    private static final String SPELL_DATA_PATH = "srd/srd-5.2-spells.json";

    private final SpellRepository repository;
    private final ObjectMapper objectMapper;

    public SpellSeedService(SpellRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Spell data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 spell data...");
        try {
            ClassPathResource resource = new ClassPathResource(SPELL_DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                List<SpellEntry> entries = objectMapper.convertValue(root.get("results"),
                        new TypeReference<List<SpellEntry>>() {});
                int count = 0;
                for (SpellEntry entry : entries) {
                    Spell s = new Spell();
                    s.setSource(ContentSource.SRD);
                    s.setSourceKey(entry.sourceKey());
                    s.setName(entry.name());
                    s.setLevel(entry.level());
                    s.setSchool(entry.school());
                    s.setCastingTime(entry.castingTime());
                    s.setRange(entry.range());
                    s.setComponents(entry.components());
                    s.setDuration(entry.duration());
                    s.setDescription(entry.description());
                    s.setHigherLevel(entry.higherLevel());
                    s.setRitual(entry.ritual());
                    s.setConcentration(entry.concentration());
                    repository.save(s);
                    count++;
                }
                log.info("Seeded {} spells", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed spell data", e);
            throw new RuntimeException("Failed to seed SRD spell data", e);
        }
    }

    public record SpellEntry(
        String sourceKey, String name, int level, String school,
        String castingTime, String range, String components, String duration,
        String description, String higherLevel, boolean ritual, boolean concentration
    ) {}
}
