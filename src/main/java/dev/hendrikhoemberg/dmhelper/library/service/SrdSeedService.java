package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class SrdSeedService {

    private static final Logger log = LoggerFactory.getLogger(SrdSeedService.class);
    private static final String SRD_DATA_PATH = "srd/srd-5.2-monsters.json";

    private final StatBlockRepository repository;
    private final ObjectMapper objectMapper;

    public SrdSeedService(StatBlockRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.existsBySource(StatBlock.Source.SRD)) {
            log.info("SRD data already seeded -- skipping");
            return;
        }

        log.info("Seeding SRD 5.2 monster data...");
        try {
            ClassPathResource resource = new ClassPathResource(SRD_DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                List<SrdMonsterEntry> entries = objectMapper.readValue(is,
                        new TypeReference<List<SrdMonsterEntry>>() {});
                int count = 0;
                for (SrdMonsterEntry entry : entries) {
                    StatBlock sb = entry.toStatBlock();
                    repository.save(sb);
                    count++;
                }
                log.info("Seeded {} SRD monsters", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed SRD data", e);
            throw new RuntimeException("Failed to seed SRD monster data", e);
        }
    }

    public record SrdMonsterEntry(
            String sourceKey, String name, String size, String type, String alignment,
            int ac, String hp, String speed,
            int strScore, int dexScore, int conScore, int intScore, int wisScore, int chaScore,
            String skills,
            String damageVulnerabilities, String damageResistances,
            String damageImmunities, String conditionImmunities,
            String senses, String languages,
            String traits, String actions, String bonusActions, String reactions,
            String legendaryActions, String legendaryDescription, String lairActions,
            String cr, int xp
    ) {
        public StatBlock toStatBlock() {
            StatBlock sb = new StatBlock();
            sb.setSource(StatBlock.Source.SRD);
            sb.setSourceKey(sourceKey);
            sb.setName(name);
            sb.setSize(size);
            sb.setType(type);
            sb.setAlignment(alignment);
            sb.setAc(ac);
            sb.setHp(hp);
            sb.setSpeed(speed);
            sb.setStrScore(strScore);
            sb.setDexScore(dexScore);
            sb.setConScore(conScore);
            sb.setIntScore(intScore);
            sb.setWisScore(wisScore);
            sb.setChaScore(chaScore);
            sb.setSkills(skills);
            sb.setDamageVulnerabilities(damageVulnerabilities);
            sb.setDamageResistances(damageResistances);
            sb.setDamageImmunities(damageImmunities);
            sb.setConditionImmunities(conditionImmunities);
            sb.setSenses(senses);
            sb.setLanguages(languages);
            sb.setTraits(traits);
            sb.setActions(actions);
            sb.setBonusActions(bonusActions);
            sb.setReactions(reactions);
            sb.setLegendaryActions(legendaryActions);
            sb.setLegendaryDescription(legendaryDescription);
            sb.setLairActions(lairActions);
            sb.setCr(cr);
            sb.setXp(xp);
            return sb;
        }
    }
}
