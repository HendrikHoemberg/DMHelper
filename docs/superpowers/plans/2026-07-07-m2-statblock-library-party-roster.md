# M2: Statblock Library & Party Roster — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find "Goblin" in <100 ms; create a custom monster; enter the party once.

**Architecture:** Spring Data JPA entities for `StatBlock` (flat table with JSON-blob traits/actions, indexed `name`/`cr`/`type` columns, includes `xp` for encounter building) and `PartyMember` (ManyToOne to Campaign). 331 SRD 5.2 monsters + 339 SRD 5.2 spells (genuine D&D 5.5e stats from open5e.com, CC-BY-4.0) bundled as JSON in `src/main/resources/srd/`, seeded on first run. `Spell` entity is read-only reference data; no spell CRUD. `GET /library/statblocks/srd-keys` lists all valid SRD source keys for external generators. Library search/filter uses JPA Specifications. Thymeleaf + htmx UI with a 5.5e-format statblock renderer and spell cards. Party roster with compact summary bar. Campaign export/import extended to include party members and campaign-scoped custom statblocks.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA + JpaSpecificationExecutor, H2, Thymeleaf + htmx, Jackson 3 (`tools.jackson`), JUnit 5 + Mockito + AssertJ + Hamcrest

---

### Task 1: Create StatBlock entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlock.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlockRepository.java`

- [ ] **Step 1: Create library package directories**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/library/data`

- [ ] **Step 2: Write StatBlock entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlock.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stat_block", indexes = {
        @Index(name = "idx_statblock_source", columnList = "source"),
        @Index(name = "idx_statblock_campaign", columnList = "campaign_id"),
        @Index(name = "idx_statblock_name", columnList = "name"),
        @Index(name = "idx_statblock_cr", columnList = "cr"),
        @Index(name = "idx_statblock_type", columnList = "type")
})
public class StatBlock {

    public enum Source { SRD, CUSTOM }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 10)
    private String cr;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(length = 100)
    private String size;

    @Column(length = 100)
    private String alignment;

    private int ac;

    @Column(nullable = false, length = 50)
    private String hp;

    @Column(length = 200)
    private String speed;

    private int strScore;
    private int dexScore;
    private int conScore;
    private int intScore;
    private int wisScore;
    private int chaScore;

    private Integer strSave;
    private Integer dexSave;
    private Integer conSave;
    private Integer intSave;
    private Integer wisSave;
    private Integer chaSave;

    @Column(length = 500)
    private String skills;

    @Column(length = 500)
    private String damageVulnerabilities;

    @Column(length = 500)
    private String damageResistances;

    @Column(length = 500)
    private String damageImmunities;

    @Column(length = 500)
    private String conditionImmunities;

    @Column(length = 500)
    private String senses;

    @Column(length = 1000)
    private String languages;

    @Column(columnDefinition = "CLOB")
    private String traits;

    @Column(columnDefinition = "CLOB")
    private String actions;

    @Column(columnDefinition = "CLOB")
    private String bonusActions;

    @Column(columnDefinition = "CLOB")
    private String reactions;

    @Column(columnDefinition = "CLOB")
    private String legendaryActions;

    @Column(length = 500)
    private String legendaryDescription;

    @Column(columnDefinition = "CLOB")
    private String lairActions;

    @Column(length = 100)
    private String sourceKey;

    private int xp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCr() { return cr; }
    public void setCr(String cr) { this.cr = cr; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public int getAc() { return ac; }
    public void setAc(int ac) { this.ac = ac; }

    public String getHp() { return hp; }
    public void setHp(String hp) { this.hp = hp; }

    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }

    public int getStrScore() { return strScore; }
    public void setStrScore(int strScore) { this.strScore = strScore; }

    public int getDexScore() { return dexScore; }
    public void setDexScore(int dexScore) { this.dexScore = dexScore; }

    public int getConScore() { return conScore; }
    public void setConScore(int conScore) { this.conScore = conScore; }

    public int getIntScore() { return intScore; }
    public void setIntScore(int intScore) { this.intScore = intScore; }

    public int getWisScore() { return wisScore; }
    public void setWisScore(int wisScore) { this.wisScore = wisScore; }

    public int getChaScore() { return chaScore; }
    public void setChaScore(int chaScore) { this.chaScore = chaScore; }

    public Integer getStrSave() { return strSave; }
    public void setStrSave(Integer strSave) { this.strSave = strSave; }

    public Integer getDexSave() { return dexSave; }
    public void setDexSave(Integer dexSave) { this.dexSave = dexSave; }

    public Integer getConSave() { return conSave; }
    public void setConSave(Integer conSave) { this.conSave = conSave; }

    public Integer getIntSave() { return intSave; }
    public void setIntSave(Integer intSave) { this.intSave = intSave; }

    public Integer getWisSave() { return wisSave; }
    public void setWisSave(Integer wisSave) { this.wisSave = wisSave; }

    public Integer getChaSave() { return chaSave; }
    public void setChaSave(Integer chaSave) { this.chaSave = chaSave; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getDamageVulnerabilities() { return damageVulnerabilities; }
    public void setDamageVulnerabilities(String v) { this.damageVulnerabilities = v; }

    public String getDamageResistances() { return damageResistances; }
    public void setDamageResistances(String v) { this.damageResistances = v; }

    public String getDamageImmunities() { return damageImmunities; }
    public void setDamageImmunities(String v) { this.damageImmunities = v; }

    public String getConditionImmunities() { return conditionImmunities; }
    public void setConditionImmunities(String v) { this.conditionImmunities = v; }

    public String getSenses() { return senses; }
    public void setSenses(String senses) { this.senses = senses; }

    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }

    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }

    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }

    public String getBonusActions() { return bonusActions; }
    public void setBonusActions(String bonusActions) { this.bonusActions = bonusActions; }

    public String getReactions() { return reactions; }
    public void setReactions(String reactions) { this.reactions = reactions; }

    public String getLegendaryActions() { return legendaryActions; }
    public void setLegendaryActions(String legendaryActions) { this.legendaryActions = legendaryActions; }

    public String getLegendaryDescription() { return legendaryDescription; }
    public void setLegendaryDescription(String v) { this.legendaryDescription = v; }

    public String getLairActions() { return lairActions; }
    public void setLairActions(String lairActions) { this.lairActions = lairActions; }

    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Write StatBlockRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlockRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatBlockRepository extends JpaRepository<StatBlock, UUID>,
        JpaSpecificationExecutor<StatBlock> {

    List<StatBlock> findAllByOrderByNameAsc();

    List<StatBlock> findBySourceOrderByNameAsc(StatBlock.Source source);

    List<StatBlock> findByCampaignIdOrderByNameAsc(UUID campaignId);

    Optional<StatBlock> findBySourceAndSourceKey(StatBlock.Source source, String sourceKey);

    boolean existsBySource(StatBlock.Source source);
}
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/ && git commit -m "feat: add StatBlock entity and repository with search indexes"
```

---

### Task 2: Create PartyMember entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMember.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMemberRepository.java`

- [ ] **Step 1: Create party package directories**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/party/data`

- [ ] **Step 2: Write PartyMember entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMember.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "party_member")
public class PartyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 255)
    private String characterName;

    @Column(length = 255)
    private String playerName;

    @Column(length = 255)
    private String classAndLevel;

    @Column(nullable = false)
    private int ac;

    @Column(nullable = false)
    private int maxHp;

    @Column(nullable = false)
    private int initiativeBonus;

    @Column(nullable = false)
    private int speed;

    @Column(nullable = false)
    private int passivePerception;

    @Column(nullable = false)
    private int passiveInsight;

    @Column(nullable = false)
    private int passiveInvestigation;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getClassAndLevel() { return classAndLevel; }
    public void setClassAndLevel(String classAndLevel) { this.classAndLevel = classAndLevel; }

    public int getAc() { return ac; }
    public void setAc(int ac) { this.ac = ac; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getInitiativeBonus() { return initiativeBonus; }
    public void setInitiativeBonus(int initiativeBonus) { this.initiativeBonus = initiativeBonus; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public int getPassivePerception() { return passivePerception; }
    public void setPassivePerception(int passivePerception) { this.passivePerception = passivePerception; }

    public int getPassiveInsight() { return passiveInsight; }
    public void setPassiveInsight(int passiveInsight) { this.passiveInsight = passiveInsight; }

    public int getPassiveInvestigation() { return passiveInvestigation; }
    public void setPassiveInvestigation(int passiveInvestigation) { this.passiveInvestigation = passiveInvestigation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
```

- [ ] **Step 3: Write PartyMemberRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMemberRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartyMemberRepository extends JpaRepository<PartyMember, UUID> {

    List<PartyMember> findByCampaignIdOrderByCharacterNameAsc(UUID campaignId);

    List<PartyMember> findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(UUID campaignId);
}
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/party/data/ && git commit -m "feat: add PartyMember entity and repository"
```

---

### Task 3: Create SRD monster JSON seed data

**Files:**
- Create: `src/main/resources/srd/srd-5.2-monsters.json`

- [ ] **Step 1: Create srd directory**

Run: `mkdir -p src/main/resources/srd`

- [ ] **Step 2: Fetch SRD 5.2 monsters from open5e.com**

Run the generator script which fetches all 331 SRD 5.2 (D&D 5.5e / 2024 rules) monsters from the open5e v2 API and transforms them into DMHelper's flat JSON schema:

```bash
python3 bin/generate-srd-json.py
```

The script paginates through `https://api.open5e.com/v2/creatures/?document__key__in=srd-2024` (331 monsters, CC-BY-4.0) and maps each to a JSON entry with fields: `sourceKey`, `name`, `size`, `type`, `alignment`, `ac`, `hp`, `speed`, `strScore` through `chaScore`, `skills`, `damageVulnerabilities` through `conditionImmunities`, `senses`, `languages`, `traits`, `actions`, `bonusActions`, `reactions`, `legendaryActions`, `legendaryDescription`, `lairActions`, `cr`.

The `traits`, `actions`, `bonusActions`, `reactions`, `legendaryActions`, and `lairActions` fields are JSON strings (escaped JSON arrays of `{"name":"...","description":"..."}` objects) because they are stored as CLOB text columns in the database. The renderer parses them client-side.

Key 5.5e differences from 2014 SRD: goblins are Fey type (not Humanoid), kobolds are Dragon type, Nimble Escape is a bonus action, adult dragons have Spellcasting and Rend attacks, zombies have lower HP at the same CR.

- [ ] **Step 3: Verify file**

Run: `ls -la src/main/resources/srd/srd-5.2-monsters.json`
Expected: File exists, ~331 monster entries, non-empty.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/srd/ bin/ && git commit -m "feat: add SRD 5.2 monster seed data and generator script"
```


---

### Task 4: Create SrdSeedService

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SrdSeedService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java`

- [ ] **Step 1: Create service package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/library/service`

- [ ] **Step 2: Write SrdSeedService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SrdSeedService.java`:

```java
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
```

- [ ] **Step 3: Wire SrdSeedService into DmhelperApplication**

Read `src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java`. It currently just has a `main()` method. Add a constructor-injected `SrdSeedService` and an `@EventListener(ApplicationReadyEvent.class)` method:

```java
package dev.hendrikhoemberg.dmhelper;

import dev.hendrikhoemberg.dmhelper.library.service.SrdSeedService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class DmhelperApplication {

    private final SrdSeedService srdSeedService;

    public DmhelperApplication(SrdSeedService srdSeedService) {
        this.srdSeedService = srdSeedService;
    }

    public static void main(String[] args) {
        SpringApplication.run(DmhelperApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        srdSeedService.seedIfEmpty();
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SrdSeedService.java src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java && git commit -m "feat: add SrdSeedService with idempotent SRD data seeding on first run"
```

---

### Task 4b: Create Spell entity, seed data, and SpellSeedService

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Spell.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpellRepository.java`
- Create: `src/main/resources/srd/srd-5.2-spells.json`
- Create: `bin/generate-srd-spells.py`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpellSeedService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java`

Spells are bundled as read-only reference data per SPEC §4.6 — no CRUD, just seed + display. The open5e srd-2024 document contains 339 spells.

- [ ] **Step 1: Write spell generator script**

Create `bin/generate-srd-spells.py`:

```python
#!/usr/bin/env python3
"""Fetch D&D 5.5e SRD 5.2 spells from open5e.com."""

import json, os, sys, time, urllib.request, urllib.error

API_BASE = "https://api.open5e.com"
PAGE_SIZE = 100
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "srd", "srd-5.2-spells.json",
)

def api_get(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url,
                headers={"Accept": "application/json", "User-Agent": "DMHelper/1.0"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode())
        except Exception as e:
            if attempt == 2: raise
            time.sleep(2 ** attempt)

def main():
    entries = []
    page = 1
    while True:
        url = f"{API_BASE}/v2/spells/?document__key__in=srd-2024&limit={PAGE_SIZE}&page={page}"
        data = api_get(url)
        results = data.get("results", [])
        if not results:
            break
        for s in results:
            components = s.get("components", "") or ""
            material = s.get("material_component", "") or ""
            if material:
                components = f"{components} ({material})"
            entries.append({
                "sourceKey": s["key"].removeprefix("srd-2024_"),
                "name": s["name"],
                "level": s.get("level", 0),
                "school": (s.get("school") or {}).get("name", ""),
                "castingTime": s.get("casting_time", ""),
                "range": s.get("range_text", ""),
                "components": components,
                "duration": s.get("duration", ""),
                "description": s.get("desc", ""),
                "higherLevel": s.get("higher_level", ""),
                "ritual": s.get("ritual", False),
                "concentration": s.get("concentration", False),
            })
        print(f"  Page {page}: {len(results)} spells ({len(entries)} total)")
        if not data.get("next"): break
        page += 1

    entries.sort(key=lambda s: (s["level"], s["name"]))
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(entries)} spells to {OUTPUT_PATH}")

if __name__ == "__main__":
    main()
```

Run: `python3 bin/generate-srd-spells.py`

- [ ] **Step 2: Write Spell entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Spell.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "spell", indexes = {
    @Index(name = "idx_spell_name", columnList = "name"),
    @Index(name = "idx_spell_level", columnList = "level"),
    @Index(name = "idx_spell_school", columnList = "school"),
})
public class Spell {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    private int level;

    @Column(length = 50)
    private String school;

    @Column(length = 100)
    private String castingTime;

    @Column(length = 100)
    private String range;

    @Column(length = 200)
    private String components;

    @Column(length = 100)
    private String duration;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(columnDefinition = "CLOB")
    private String higherLevel;

    private boolean ritual;
    private boolean concentration;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String v) { this.sourceKey = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public int getLevel() { return level; }
    public void setLevel(int v) { this.level = v; }
    public String getSchool() { return school; }
    public void setSchool(String v) { this.school = v; }
    public String getCastingTime() { return castingTime; }
    public void setCastingTime(String v) { this.castingTime = v; }
    public String getRange() { return range; }
    public void setRange(String v) { this.range = v; }
    public String getComponents() { return components; }
    public void setComponents(String v) { this.components = v; }
    public String getDuration() { return duration; }
    public void setDuration(String v) { this.duration = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getHigherLevel() { return higherLevel; }
    public void setHigherLevel(String v) { this.higherLevel = v; }
    public boolean isRitual() { return ritual; }
    public void setRitual(boolean v) { this.ritual = v; }
    public boolean isConcentration() { return concentration; }
    public void setConcentration(boolean v) { this.concentration = v; }
}
```

- [ ] **Step 3: Write SpellRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpellRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpellRepository extends JpaRepository<Spell, UUID>,
        JpaSpecificationExecutor<Spell> {

    List<Spell> findAllByOrderByLevelAscNameAsc();

    boolean existsBySourceKey(String sourceKey);
}
```

- [ ] **Step 4: Write SpellSeedService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpellSeedService.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
                List<SpellEntry> entries = objectMapper.readValue(is,
                        new TypeReference<List<SpellEntry>>() {});
                int count = 0;
                for (SpellEntry entry : entries) {
                    Spell s = new Spell();
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
```

- [ ] **Step 5: Wire SpellSeedService into DmhelperApplication**

Add constructor injection of `SpellSeedService` and call `spellSeedService.seedIfEmpty()` after `srdSeedService.seedIfEmpty()` in the `seed()` event listener.

- [ ] **Step 5b: Write SpellService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpellService.java` (read-only, spells are reference data only):

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SpellService {

    private final SpellRepository repository;

    public SpellService(SpellRepository repository) {
        this.repository = repository;
    }

    public List<Spell> search(String search, Integer level, String school) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (level != null) {
                predicates.add(cb.equal(root.get("level"), level));
            }
            if (school != null && !school.isBlank()) {
                predicates.add(cb.equal(root.get("school"), school));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("level")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Spell> findAll() {
        return repository.findAllByOrderByLevelAscNameAsc();
    }
}
```

- [ ] **Step 6: Verify compilation and commit**

Run: `./mvnw compile`
```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Spell.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpellRepository.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpellSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpellService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java \
        bin/generate-srd-spells.py \
        src/main/resources/srd/srd-5.2-spells.json && \
        git commit -m "feat: add Spell entity, seed data, and SpellSeedService (339 SRD 5.2 spells)"
```

---

### Task 5: Write StatBlockService tests (TDD -- red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockServiceTest.java`

- [ ] **Step 1: Create test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/library/service`

- [ ] **Step 2: Write failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(StatBlockService.class)
class StatBlockServiceTest {

    @Autowired
    private StatBlockRepository repository;

    @Autowired
    private StatBlockService service;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
    }

    private StatBlock createCustom(String name, String cr, String type, int ac, String hp) {
        return service.createCustom(campaignId, name, cr, type, ac, hp, "30 ft.",
                10, 10, 10, 10, 10, 10,
                hp, "30 ft.",
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 10", null);
    }

    @Test
    void shouldCreateCustomStatBlock() {
        StatBlock sb = createCustom("Amber Knight", "5", "Humanoid", 18, "75 (10d8 + 30)");
        assertThat(sb.getId()).isNotNull();
        assertThat(sb.getSource()).isEqualTo(StatBlock.Source.CUSTOM);
        assertThat(sb.getCampaignId()).isEqualTo(campaignId);
        assertThat(sb.getName()).isEqualTo("Amber Knight");
    }

    @Test
    void shouldFindByCampaign() {
        createCustom("Custom A", "1", "Beast", 12, "10");
        createCustomForOtherCampaign();
        assertThat(service.findByCampaignId(campaignId)).hasSize(1);
    }

    private void createCustomForOtherCampaign() {
        service.createCustom(UUID.randomUUID(), "Custom B", "2", "Giant", 14, "30",
                10, 10, 10, 10, 10, 10,
                "30", "40 ft.",
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 10", "Giant");
    }

    @Test
    void shouldSearchByName() {
        createCustom("Fire Elemental Adept", "2", "Elemental", 14, "30");
        createCustom("Ice Knight", "3", "Humanoid", 16, "45");
        var results = service.search(null, null, null, "Fire");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Fire Elemental Adept");
    }

    @Test
    void shouldFilterByCr() {
        createCustom("CR One", "1", "Beast", 12, "20");
        createCustom("CR Three", "3", "Beast", 14, "40");
        var results = service.search(null, "1", null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCr()).isEqualTo("1");
    }

    @Test
    void shouldFilterByType() {
        createCustom("Beast Monster", "1/2", "Beast", 12, "15");
        createCustom("Undead Monster", "1/2", "Undead", 12, "18");
        var results = service.search(null, null, "Undead", null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo("Undead");
    }

    @Test
    void shouldUpdateCustomStatBlock() {
        StatBlock created = createCustom("Original", "1", "Beast", 12, "10");
        StatBlock updated = service.updateCustom(created.getId(), "Renamed", "3", "Beast",
                16, "45", "40 ft.",
                16, 10, 16, 12, 14, 10,
                "45", "40 ft.",
                null, null, null, null, null, null,
                null, null, null, null, null,
                "darkvision 60 ft.", "Common, Giant");
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getCr()).isEqualTo("3");
    }

    @Test
    void shouldDeleteCustomStatBlock() {
        StatBlock created = createCustom("Delete Me", "1/8", "Beast", 10, "5");
        service.delete(created.getId());
        assertThat(repository.findById(created.getId())).isEmpty();
    }

    @Test
    void shouldNotDeleteSrdStatBlock() {
        StatBlock srd = new StatBlock();
        srd.setSource(StatBlock.Source.SRD);
        srd.setName("Protected Goblin");
        srd.setCr("1/4");
        srd.setType("Humanoid");
        srd.setHp("7");
        srd.setAc(15);
        srd = repository.save(srd);
        assertThatThrownBy(() -> service.delete(srd.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete SRD");
    }

    @Test
    void shouldPromoteToGlobal() {
        StatBlock custom = createCustom("Campaign Monster", "2", "Giant", 14, "50");
        assertThat(custom.getCampaignId()).isNotNull();
        StatBlock promoted = service.promoteToGlobal(custom.getId());
        assertThat(promoted.getCampaignId()).isNull();
        assertThat(promoted.getSource()).isEqualTo(StatBlock.Source.CUSTOM);
    }

    @Test
    void shouldNotPromoteSrdToGlobal() {
        StatBlock srd = new StatBlock();
        srd.setSource(StatBlock.Source.SRD);
        srd.setName("SRD Monster");
        srd.setCr("1");
        srd.setType("Beast");
        srd.setHp("10");
        srd.setAc(12);
        srd = repository.save(srd);
        assertThatThrownBy(() -> service.promoteToGlobal(srd.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCloneSrdAsCustom() {
        StatBlock srd = new StatBlock();
        srd.setSource(StatBlock.Source.SRD);
        srd.setName("Goblin");
        srd.setCr("1/4");
        srd.setType("Humanoid");
        srd.setHp("7 (2d6)");
        srd.setAc(15);
        srd.setSpeed("30 ft.");
        srd.setStrScore(8);
        srd.setDexScore(14);
        srd.setConScore(10);
        srd.setIntScore(10);
        srd.setWisScore(8);
        srd.setChaScore(8);
        srd.setTraits("[{\\\\"name\\\":\\\"Nimble Escape\\\",\\\"description\\\":\\\"Test\\\"}]");
        srd.setActions("[{\\\\"name\\\":\\\"Scimitar\\\",\\\"description\\\":\\\"Test\\\"}]");
        srd = repository.save(srd);

        StatBlock cloned = service.cloneAsCustom(srd.getId(), campaignId, "Goblin Boss");
        assertThat(cloned.getId()).isNotEqualTo(srd.getId());
        assertThat(cloned.getSource()).isEqualTo(StatBlock.Source.CUSTOM);
        assertThat(cloned.getCampaignId()).isEqualTo(campaignId);
        assertThat(cloned.getName()).isEqualTo("Goblin Boss");
        assertThat(cloned.getTraits()).isEqualTo(srd.getTraits());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=StatBlockServiceTest`
Expected: Compilation fails -- `StatBlockService` class not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockServiceTest.java && git commit -m "test: add StatBlockService tests (TDD red phase)"
```

---

### Task 6: Implement StatBlockService (TDD -- green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java`

- [ ] **Step 1: Write StatBlockService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StatBlockService {

    private final StatBlockRepository repository;

    public StatBlockService(StatBlockRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StatBlock> search(StatBlock.Source source, String cr, String type, String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (cr != null && !cr.isBlank()) {
                predicates.add(cb.equal(root.get("cr"), cr));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }

            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    @Transactional(readOnly = true)
    public StatBlock findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("StatBlock not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StatBlock> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public List<StatBlock> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public StatBlock createCustom(UUID campaignId, String name, String cr, String type,
                                  int ac, String hp, String speed,
                                  int str, int dex, int con, int intel, int wis, int cha,
                                  String hpValue, String speedValue,
                                  Integer strSave, Integer dexSave, Integer conSave,
                                  Integer intSave, Integer wisSave, Integer chaSave,
                                  String skills, String damageVuln, String damageRes,
                                  String damageImm, String condImm,
                                  String senses, String languages) {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.CUSTOM);
        sb.setCampaignId(campaignId);
        sb.setName(name);
        sb.setCr(cr);
        sb.setType(type);
        sb.setAc(ac);
        sb.setHp(hp);
        sb.setSpeed(speed);
        sb.setStrScore(str);
        sb.setDexScore(dex);
        sb.setConScore(con);
        sb.setIntScore(intel);
        sb.setWisScore(wis);
        sb.setChaScore(cha);
        sb.setStrSave(strSave);
        sb.setDexSave(dexSave);
        sb.setConSave(conSave);
        sb.setIntSave(intSave);
        sb.setWisSave(wisSave);
        sb.setChaSave(chaSave);
        sb.setSkills(skills);
        sb.setDamageVulnerabilities(damageVuln);
        sb.setDamageResistances(damageRes);
        sb.setDamageImmunities(damageImm);
        sb.setConditionImmunities(condImm);
        sb.setSenses(senses);
        sb.setLanguages(languages);
        return repository.save(sb);
    }

    public StatBlock updateCustom(UUID id, String name, String cr, String type,
                                  int ac, String hp, String speed,
                                  int str, int dex, int con, int intel, int wis, int cha,
                                  String hpValue, String speedValue,
                                  Integer strSave, Integer dexSave, Integer conSave,
                                  Integer intSave, Integer wisSave, Integer chaSave,
                                  String skills, String damageVuln, String damageRes,
                                  String damageImm, String condImm,
                                  String senses, String languages) {
        StatBlock sb = findById(id);
        if (sb.getSource() != StatBlock.Source.CUSTOM) {
            throw new IllegalArgumentException("Cannot edit SRD statblocks");
        }
        sb.setName(name);
        sb.setCr(cr);
        sb.setType(type);
        sb.setAc(ac);
        sb.setHp(hp);
        sb.setSpeed(speed);
        sb.setStrScore(str);
        sb.setDexScore(dex);
        sb.setConScore(con);
        sb.setIntScore(intel);
        sb.setWisScore(wis);
        sb.setChaScore(cha);
        sb.setStrSave(strSave);
        sb.setDexSave(dexSave);
        sb.setConSave(conSave);
        sb.setIntSave(intSave);
        sb.setWisSave(wisSave);
        sb.setChaSave(chaSave);
        sb.setSkills(skills);
        sb.setDamageVulnerabilities(damageVuln);
        sb.setDamageResistances(damageRes);
        sb.setDamageImmunities(damageImm);
        sb.setConditionImmunities(condImm);
        sb.setSenses(senses);
        sb.setLanguages(languages);
        return repository.save(sb);
    }

    public void delete(UUID id) {
        StatBlock sb = findById(id);
        if (sb.getSource() != StatBlock.Source.CUSTOM) {
            throw new IllegalArgumentException("Cannot delete SRD statblocks");
        }
        repository.delete(sb);
    }

    public StatBlock cloneAsCustom(UUID sourceId, UUID targetCampaignId, String newName) {
        StatBlock original = findById(sourceId);
        StatBlock clone = new StatBlock();
        clone.setSource(StatBlock.Source.CUSTOM);
        clone.setCampaignId(targetCampaignId);
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setCr(original.getCr());
        clone.setType(original.getType());
        clone.setSize(original.getSize());
        clone.setAlignment(original.getAlignment());
        clone.setAc(original.getAc());
        clone.setHp(original.getHp());
        clone.setSpeed(original.getSpeed());
        clone.setStrScore(original.getStrScore());
        clone.setDexScore(original.getDexScore());
        clone.setConScore(original.getConScore());
        clone.setIntScore(original.getIntScore());
        clone.setWisScore(original.getWisScore());
        clone.setChaScore(original.getChaScore());
        clone.setStrSave(original.getStrSave());
        clone.setDexSave(original.getDexSave());
        clone.setConSave(original.getConSave());
        clone.setIntSave(original.getIntSave());
        clone.setWisSave(original.getWisSave());
        clone.setChaSave(original.getChaSave());
        clone.setSkills(original.getSkills());
        clone.setDamageVulnerabilities(original.getDamageVulnerabilities());
        clone.setDamageResistances(original.getDamageResistances());
        clone.setDamageImmunities(original.getDamageImmunities());
        clone.setConditionImmunities(original.getConditionImmunities());
        clone.setSenses(original.getSenses());
        clone.setLanguages(original.getLanguages());
        clone.setTraits(original.getTraits());
        clone.setActions(original.getActions());
        clone.setBonusActions(original.getBonusActions());
        clone.setReactions(original.getReactions());
        clone.setLegendaryActions(original.getLegendaryActions());
        clone.setLegendaryDescription(original.getLegendaryDescription());
        clone.setLairActions(original.getLairActions());
        clone.setXp(original.getXp());
        return repository.save(clone);
    }

    public StatBlock promoteToGlobal(UUID id) {
        StatBlock sb = findById(id);
        if (sb.getSource() != StatBlock.Source.CUSTOM) {
            throw new IllegalArgumentException("Only custom statblocks can be promoted");
        }
        sb.setCampaignId(null);
        return repository.save(sb);
    }
}
```

- [ ] **Step 2: Fix StatBlockServiceTest compilation**

In `StatBlockServiceTest.java`, fix the JSON escaping for the `shouldCloneSrdAsCustom` test. The `setTraits` and `setActions` calls use `\"` escaping. In the actual Java string, change:
```java
srd.setTraits("[{\\\\"name\\\":\\\"Nimble Escape\\\",\\\"description\\\":\\\"Test\\\"}]");
```
to:
```java
srd.setTraits("[{\"name\":\"Nimble Escape\",\"description\":\"Test\"}]");
```
(Remove the extra escaping -- those are double-backslash artifacts from the plan formatting.)

- [ ] **Step 3: Run tests**

Run: `./mvnw test -pl . -Dtest=StatBlockServiceTest`
Expected: All 11 tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java && git commit -m "feat: implement StatBlockService with search, CRUD, and clone-as-custom"
```

---

### Task 7: Create CSS additions for library and party

**Files:**
- Modify: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Read existing CSS for context**

Read `src/main/resources/static/css/app.css`. It is ~386 lines from M1. Do NOT edit existing styles -- only append new styles at the end.

- [ ] **Step 2: Append library and party CSS**

Edit `src/main/resources/static/css/app.css` -- append the following at the end of the file:

```css
/* Library / Statblock Styles */

.search-bar {
  display: flex; gap: var(--space-sm); flex-wrap: wrap; align-items: flex-end;
  margin-bottom: var(--space-lg); padding: var(--space-md);
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius);
}
.search-bar .form-group { margin-bottom: 0; flex: 1; min-width: 160px; }
.search-bar select {
  width: 100%; padding: var(--space-sm) var(--space-md);
  font-size: var(--text-base); font-family: inherit; color: var(--color-text);
  background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius);
}
.search-bar select:focus { outline: none; border-color: var(--color-accent); }

.statblock-card {
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius); padding: var(--space-md);
  transition: border-color var(--transition), box-shadow var(--transition);
  display: flex; flex-direction: column; gap: var(--space-xs);
}
.statblock-card:hover { border-color: var(--color-accent); box-shadow: var(--shadow); }
.statblock-card h3 { font-size: var(--text-lg); margin: 0; }
.statblock-card h3 a { color: var(--color-text); text-decoration: none; }
.statblock-card h3 a:hover { color: var(--color-accent); }
.statblock-meta { display: flex; gap: var(--space-sm); font-size: var(--text-sm); color: var(--color-text-muted); flex-wrap: wrap; }
.badge-srd { background: var(--color-accent); color: #fff; font-size: var(--text-sm); padding: 1px 6px; border-radius: 4px; }
.badge-custom { background: var(--color-success); color: #fff; font-size: var(--text-sm); padding: 1px 6px; border-radius: 4px; }

/* Statblock Renderer (5.5e parchment style) */
.statblock-render {
  max-width: 500px; margin: 0 auto;
  background: #f5f0e8; color: #2c1810;
  border: 2px solid #8b7355; border-radius: 4px;
  padding: var(--space-lg);
  font-family: 'Bookman Old Style', Georgia, serif;
  box-shadow: 2px 2px 8px rgba(0,0,0,0.4);
}
.statblock-render h2 { font-size: 1.5rem; font-weight: 700; margin-bottom: 4px; font-variant: small-caps; }
.statblock-render .sb-subtitle { font-style: italic; font-size: 0.875rem; margin-bottom: var(--space-md); color: #5c3d2e; }
.statblock-render .sb-rule { border: none; border-top: 2px solid #8b2e1c; margin: var(--space-sm) 0; }
.statblock-render .sb-rule-thin { border: none; border-top: 1px solid #8b2e1c; margin: var(--space-xs) 0; }
.statblock-render .sb-stat-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: var(--space-xs); font-size: 0.875rem; margin-bottom: var(--space-sm); }
.statblock-render .sb-stat-row span { font-weight: 600; }
.statblock-render .sb-abilities { display: grid; grid-template-columns: repeat(6, 1fr); gap: var(--space-xs); text-align: center; margin-bottom: var(--space-sm); }
.statblock-render .sb-abilities div { font-size: 0.75rem; }
.statblock-render .sb-abilities .score { font-size: 1rem; font-weight: 700; }
.statblock-render .sb-props { font-size: 0.875rem; margin-bottom: var(--space-sm); }
.statblock-render .sb-props dt { font-weight: 700; display: inline; }
.statblock-render .sb-props dd { display: inline; margin-right: 1em; }
.statblock-render h3 { font-size: 1.1rem; font-weight: 700; font-variant: small-caps; margin-top: var(--space-md); margin-bottom: var(--space-xs); border-bottom: 1px solid #8b2e1c; padding-bottom: 2px; }
.statblock-render .sb-ability { margin-bottom: var(--space-sm); }
.statblock-render .sb-ability .sb-ability-name { font-weight: 700; font-style: italic; }
.statblock-render .sb-ability .sb-ability-text { font-size: 0.875rem; line-height: 1.5; }

/* Party Roster Styles */
.party-summary-bar {
  display: flex; gap: var(--space-md); overflow-x: auto;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius); margin-bottom: var(--space-lg); align-items: center;
}
.party-summary-bar .bar-label { font-size: var(--text-sm); font-weight: 600; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.5px; white-space: nowrap; }
.party-member-chip { display: flex; flex-direction: column; align-items: center; padding: var(--space-xs) var(--space-sm); border-right: 1px solid var(--color-border); min-width: 80px; }
.party-member-chip:last-child { border-right: none; }
.party-member-chip .chip-name { font-size: var(--text-sm); font-weight: 600; color: var(--color-accent); margin-bottom: 2px; }
.party-member-chip .chip-stats { font-size: 0.7rem; color: var(--color-text-muted); display: flex; gap: 6px; }

.party-member-card {
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius); padding: var(--space-md);
  display: grid; grid-template-columns: 1fr auto;
  gap: var(--space-sm) var(--space-md);
}
.party-member-card:hover { border-color: var(--color-accent); }
.party-member-card h3 { font-size: var(--text-lg); grid-column: 1; }
.party-member-card .pm-player { font-size: var(--text-sm); color: var(--color-text-muted); }
.party-member-card .pm-stats { display: flex; gap: var(--space-md); flex-wrap: wrap; font-size: var(--text-sm); grid-column: 1; }
.party-member-card .pm-stats dt { color: var(--color-text-muted); font-size: 0.7rem; text-transform: uppercase; }
.party-member-card .pm-stats dd { font-weight: 600; font-size: var(--text-base); }
.party-member-card .pm-actions { grid-column: 2; grid-row: 1 / 4; display: flex; flex-direction: column; gap: var(--space-xs); }
.pm-inactive { opacity: 0.4; }

/* Form tabs */
.form-tabs { display: flex; gap: 0; margin-bottom: var(--space-md); border-bottom: 2px solid var(--color-border); }
.form-tab { padding: var(--space-xs) var(--space-md); font-size: var(--text-sm); border: none; background: none; color: var(--color-text-muted); cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -2px; }
.form-tab:hover { color: var(--color-text); }
.form-tab.active { color: var(--color-accent); border-bottom-color: var(--color-accent); }
.form-tab-content { display: none; }
.form-tab-content.active { display: block; }

/* Ability score grid in form */
.ability-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: var(--space-sm); }
.ability-grid .form-group { text-align: center; }
.ability-grid .form-group input { text-align: center; width: 100%; }
.ability-grid .form-group label { font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.5px; }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/app.css && git commit -m "feat: add CSS for statblock renderer, library search, party roster, and forms"
```

---

### Task 8: Create library Thymeleaf templates

**Files:**
- Create: `src/main/resources/templates/library/list.html`
- Create: `src/main/resources/templates/library/detail.html`
- Create: `src/main/resources/templates/library/_card.html`
- Create: `src/main/resources/templates/library/_statblock-renderer.html`
- Create: `src/main/resources/templates/library/_form.html`
- Create: `src/main/resources/templates/about.html`

- [ ] **Step 1: Create library templates directory**

Run: `mkdir -p src/main/resources/templates/library`

- [ ] **Step 2: Write statblock renderer fragment**

Create `src/main/resources/templates/library/_statblock-renderer.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="statblock-render" th:fragment="renderer(sb)">
    <h2 th:text="${sb.name}">Monster Name</h2>
    <div class="sb-subtitle" th:with="st=((${sb.size} ?: '') + ' ' + (${sb.type} ?: '') + (${sb.alignment} ? ', ' + ${sb.alignment} : ''))" th:text="${st}"></div>
    <hr class="sb-rule">

    <div class="sb-stat-row">
        <div><span>Armor Class</span> <span th:text="${sb.ac}">10</span></div>
        <div><span>Hit Points</span> <span th:text="${sb.hp}">1</span></div>
        <div><span>Speed</span> <span th:text="${sb.speed}">30 ft.</span></div>
    </div>
    <hr class="sb-rule-thin">

    <div class="sb-abilities">
        <div><span class="score" th:text="${sb.strScore}">10</span><br>STR</div>
        <div><span class="score" th:text="${sb.dexScore}">10</span><br>DEX</div>
        <div><span class="score" th:text="${sb.conScore}">10</span><br>CON</div>
        <div><span class="score" th:text="${sb.intScore}">10</span><br>INT</div>
        <div><span class="score" th:text="${sb.wisScore}">10</span><br>WIS</div>
        <div><span class="score" th:text="${sb.chaScore}">10</span><br>CHA</div>
    </div>
    <hr class="sb-rule-thin">

    <th:block th:if="${sb.skills != null and !sb.skills.isBlank()}"><div class="sb-props"><dt>Skills</dt> <dd th:text="${sb.skills}"></dd></div></th:block>
    <th:block th:if="${sb.damageVulnerabilities != null and !sb.damageVulnerabilities.isBlank()}"><div class="sb-props"><dt>Damage Vulnerabilities</dt> <dd th:text="${sb.damageVulnerabilities}"></dd></div></th:block>
    <th:block th:if="${sb.damageResistances != null and !sb.damageResistances.isBlank()}"><div class="sb-props"><dt>Damage Resistances</dt> <dd th:text="${sb.damageResistances}"></dd></div></th:block>
    <th:block th:if="${sb.damageImmunities != null and !sb.damageImmunities.isBlank()}"><div class="sb-props"><dt>Damage Immunities</dt> <dd th:text="${sb.damageImmunities}"></dd></div></th:block>
    <th:block th:if="${sb.conditionImmunities != null and !sb.conditionImmunities.isBlank()}"><div class="sb-props"><dt>Condition Immunities</dt> <dd th:text="${sb.conditionImmunities}"></dd></div></th:block>
    <div class="sb-props"><dt>Senses</dt> <dd th:text="${sb.senses}"></dd></div>
    <div class="sb-props"><dt>Languages</dt> <dd th:text="${sb.languages} ?: '--'"></dd></div>
    <div class="sb-props"><dt>Challenge</dt> <dd th:text="${sb.cr}">0</dd></div>
    <hr class="sb-rule">

    <th:block th:if="${sb.traits != null and !sb.traits.isBlank()}">
        <h3>Traits</h3>
        <div class="sb-ability" th:each="trait : ${sb.traitsParsed}">
            <div class="sb-ability-name" th:text="${trait.name}">Trait Name</div>
            <div class="sb-ability-text" th:text="${trait.description}">Description.</div>
        </div>
    </th:block>

    <th:block th:if="${sb.actions != null and !sb.actions.isBlank()}">
        <h3>Actions</h3>
        <div class="sb-ability" th:each="action : ${sb.actionsParsed}">
            <div class="sb-ability-name" th:text="${action.name}">Action Name</div>
            <div class="sb-ability-text" th:text="${action.description}">Description.</div>
        </div>
    </th:block>

    <th:block th:if="${sb.bonusActions != null and !sb.bonusActions.isBlank()}">
        <h3>Bonus Actions</h3>
        <div class="sb-ability" th:each="ba : ${sb.bonusActionsParsed}">
            <div class="sb-ability-name" th:text="${ba.name}"></div>
            <div class="sb-ability-text" th:text="${ba.description}"></div>
        </div>
    </th:block>

    <th:block th:if="${sb.reactions != null and !sb.reactions.isBlank()}">
        <h3>Reactions</h3>
        <div class="sb-ability" th:each="r : ${sb.reactionsParsed}">
            <div class="sb-ability-name" th:text="${r.name}"></div>
            <div class="sb-ability-text" th:text="${r.description}"></div>
        </div>
    </th:block>

    <th:block th:if="${sb.legendaryActions != null and !sb.legendaryActions.isBlank()}">
        <h3>Legendary Actions</h3>
        <p class="sb-ability-text" th:if="${sb.legendaryDescription != null}" th:text="${sb.legendaryDescription}"></p>
        <div class="sb-ability" th:each="la : ${sb.legendaryActionsParsed}">
            <div class="sb-ability-name" th:text="${la.name}"></div>
            <div class="sb-ability-text" th:text="${la.description}"></div>
        </div>
    </th:block>

    <th:block th:if="${sb.lairActions != null and !sb.lairActions.isBlank()}">
        <h3>Lair Actions</h3>
        <div class="sb-ability" th:each="la : ${sb.lairActionsParsed}">
            <div class="sb-ability-name" th:text="${la.name}"></div>
            <div class="sb-ability-text" th:text="${la.description}"></div>
        </div>
    </th:block>
</div>
</html>
```

- [ ] **Step 3: Write statblock card fragment**

Create `src/main/resources/templates/library/_card.html` with both a `card(sb)` fragment for individual cards and a `card-list(statblocks)` fragment for the full list (used by both the initial load and htmx search responses):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="statblock-card" th:fragment="card(sb)">
    <h3>
        <a th:href="@{/library/statblocks/{id}(id=${sb.id})}" th:text="${sb.name}">Name</a>
    </h3>
    <div class="statblock-meta">
        <span th:text="'CR ' + ${sb.cr}">CR 1/4</span>
        <span th:text="${sb.type}">Type</span>
        <span th:if="${sb.size != null}" th:text="${sb.size}"></span>
        <span th:if="${sb.source.name() == 'SRD'}" class="badge-srd">SRD</span>
        <span th:if="${sb.source.name() == 'CUSTOM'}" class="badge-custom">Custom</span>
    </div>
    <div class="card-actions" style="margin-top: auto;">
        <th:block th:if="${sb.source.name() == 'SRD'}">
            <form hx-post="@{/library/statblocks/{id}/clone(id=${sb.id})}" hx-target="body" style="display:inline;">
                <button type="submit" class="btn btn-ghost">Clone &amp; Edit</button>
            </form>
        </th:block>
        <th:block th:if="${sb.source.name() == 'CUSTOM'}">
            <a th:href="@{/library/statblocks/{id}/edit(id=${sb.id})}" class="btn btn-ghost">Edit</a>
            <button class="btn btn-danger"
                    hx-delete="@{/library/statblocks/{id}(id=${sb.id})}"
                    hx-confirm="Delete this statblock?"
                    hx-target="closest .statblock-card"
                    hx-swap="outerHTML">Delete</button>
            <th:block th:if="${sb.campaignId != null}">
                <button class="btn btn-ghost"
                        hx-put="@{/library/statblocks/{id}/promote(id=${sb.id})}"
                        hx-confirm="Promote to global? It will be available in all campaigns."
                        hx-target="closest .statblock-card"
                        hx-swap="outerHTML">Promote to Global</button>
            </th:block>
        </th:block>
    </div>
</div>

<div class="card-grid" th:fragment="card-list(statblocks)" th:if="${statblocks != null}">
    <th:block th:if="${statblocks.isEmpty()}">
        <div class="empty-state"><p>No statblocks match your search.</p></div>
    </th:block>
    <th:block th:each="sb : ${statblocks}">
        <th:block th:replace="~{library/_card :: card(sb=${sb})}"></th:block>
    </th:block>
</div>
</html>
```

- [ ] **Step 4: Write library form fragment**

Create `src/main/resources/templates/library/_form.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="detail-section" th:fragment="form(sb, campaignId)" id="statblock-form">
    <h2 th:text="${sb == null ? 'Create Custom Statblock' : 'Edit ' + sb.name}">Create Statblock</h2>

    <form th:action="${sb == null ? @{/library/statblocks} : @{/library/statblocks/{id}(id=${sb.id})}}"
          th:method="${sb == null ? 'post' : 'put'}"
          hx-target="${sb == null ? '#library-results' : '#statblock-form'}"
          th:hx-swap="${sb == null ? 'beforeend' : 'outerHTML'}">

        <input type="hidden" name="campaignId" th:value="${campaignId}">

        <div class="form-tabs">
            <button type="button" class="form-tab active" onclick="switchFormTab(event, 'tab-basic')">Basic</button>
            <button type="button" class="form-tab" onclick="switchFormTab(event, 'tab-abilities')">Abilities</button>
            <button type="button" class="form-tab" onclick="switchFormTab(event, 'tab-combat')">Combat</button>
        </div>

        <div class="form-tab-content active" id="tab-basic">
            <div class="form-group">
                <label>Name *</label>
                <input type="text" name="name" required th:value="${sb?.name}" placeholder="e.g. Goblin Boss">
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: var(--space-md);">
                <div class="form-group"><label>Size</label><input type="text" name="size" th:value="${sb?.size}" placeholder="Medium"></div>
                <div class="form-group"><label>Type *</label><input type="text" name="type" required th:value="${sb?.type}" placeholder="Humanoid"></div>
                <div class="form-group"><label>Alignment</label><input type="text" name="alignment" th:value="${sb?.alignment}" placeholder="Neutral Evil"></div>
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md);">
                <div class="form-group"><label>CR *</label><input type="text" name="cr" required th:value="${sb?.cr}" placeholder="1/4" style="width: 120px;"></div>
                <div class="form-group"><label>AC *</label><input type="number" name="ac" required th:value="${sb?.ac}" placeholder="15" style="width: 80px;"></div>
            </div>
            <div class="form-group"><label>HP * (e.g. "7 (2d6)")</label><input type="text" name="hp" required th:value="${sb?.hp}" placeholder="7 (2d6)"></div>
            <div class="form-group"><label>Speed</label><input type="text" name="speed" th:value="${sb?.speed}" placeholder="30 ft."></div>
        </div>

        <div class="form-tab-content" id="tab-abilities">
            <div class="ability-grid">
                <div class="form-group"><label>STR</label><input type="number" name="strScore" th:value="${sb?.strScore}" value="10"></div>
                <div class="form-group"><label>DEX</label><input type="number" name="dexScore" th:value="${sb?.dexScore}" value="10"></div>
                <div class="form-group"><label>CON</label><input type="number" name="conScore" th:value="${sb?.conScore}" value="10"></div>
                <div class="form-group"><label>INT</label><input type="number" name="intScore" th:value="${sb?.intScore}" value="10"></div>
                <div class="form-group"><label>WIS</label><input type="number" name="wisScore" th:value="${sb?.wisScore}" value="10"></div>
                <div class="form-group"><label>CHA</label><input type="number" name="chaScore" th:value="${sb?.chaScore}" value="10"></div>
            </div>
        </div>

        <div class="form-tab-content" id="tab-combat">
            <div class="form-group"><label>Skills</label><input type="text" name="skills" th:value="${sb?.skills}"></div>
            <div class="form-group"><label>Damage Vulnerabilities</label><input type="text" name="damageVulnerabilities" th:value="${sb?.damageVulnerabilities}"></div>
            <div class="form-group"><label>Damage Resistances</label><input type="text" name="damageResistances" th:value="${sb?.damageResistances}"></div>
            <div class="form-group"><label>Damage Immunities</label><input type="text" name="damageImmunities" th:value="${sb?.damageImmunities}"></div>
            <div class="form-group"><label>Condition Immunities</label><input type="text" name="conditionImmunities" th:value="${sb?.conditionImmunities}"></div>
            <div class="form-group"><label>Senses</label><input type="text" name="senses" th:value="${sb?.senses}" placeholder="passive Perception 10"></div>
            <div class="form-group"><label>Languages</label><input type="text" name="languages" th:value="${sb?.languages}"></div>
            <div class="form-group"><label>Traits (JSON array)</label><textarea name="traits" rows="4" th:text="${sb?.traits}"></textarea></div>
            <div class="form-group"><label>Actions (JSON array)</label><textarea name="actions" rows="6" th:text="${sb?.actions}"></textarea></div>
            <div class="form-group"><label>Bonus Actions (JSON array)</label><textarea name="bonusActions" rows="3" th:text="${sb?.bonusActions}"></textarea></div>
            <div class="form-group"><label>Reactions (JSON array)</label><textarea name="reactions" rows="3" th:text="${sb?.reactions}"></textarea></div>
            <div class="form-group"><label>Legendary Actions (JSON array)</label><textarea name="legendaryActions" rows="6" th:text="${sb?.legendaryActions}"></textarea></div>
            <div class="form-group"><label>Legendary Description</label><textarea name="legendaryDescription" rows="2" th:text="${sb?.legendaryDescription}"></textarea></div>
            <div class="form-group"><label>Lair Actions (JSON array)</label><textarea name="lairActions" rows="3" th:text="${sb?.lairActions}"></textarea></div>
        </div>

        <div class="form-actions">
            <button type="button" class="btn btn-ghost"
                    th:if="${sb != null}"
                    hx-get="@{/library/statblocks/{id}(id=${sb.id})}"
                    hx-target="#statblock-form"
                    hx-swap="outerHTML">Cancel</button>
            <button type="submit" class="btn btn-primary" th:text="${sb == null ? 'Create' : 'Save'}">Create</button>
        </div>
    </form>
</div>
</html>
```

- [ ] **Step 5: Write library list page**

Create `src/main/resources/templates/library/list.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title>DMHelper -- Library</title>
    <script th:src="@{/vendor/alpine.min.js}"></script>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
    <div class="app-layout">
        <main>
            <div class="page-header">
                <h1>Monster Library</h1>
                <a th:href="@{/library/statblocks/new}" class="btn btn-primary">+ New Homebrew</a>
            </div>

            <div class="search-bar">
                <div class="form-group">
                    <label>Search</label>
                    <input type="text" name="search" id="librarySearch" placeholder="Search by name..."
                           hx-get="/library/statblocks"
                           hx-trigger="keyup changed delay:200ms"
                           hx-target="#library-results"
                           hx-include="#libraryCr,#libraryType,#librarySource">
                </div>
                <div class="form-group">
                    <label>CR</label>
                    <select name="cr" id="libraryCr"
                            hx-get="/library/statblocks" hx-trigger="change"
                            hx-target="#library-results"
                            hx-include="#librarySearch,#libraryType,#librarySource">
                        <option value="">Any CR</option>
                        <option value="0">0</option>
                        <option value="1/8">1/8</option>
                        <option value="1/4">1/4</option>
                        <option value="1/2">1/2</option>
                        <option value="1">1</option>
                        <option value="2">2</option>
                        <option value="3">3</option>
                        <option value="4">4</option>
                        <option value="5">5</option>
                        <option value="6">6</option>
                        <option value="7">7</option>
                        <option value="8">8</option>
                        <option value="9">9</option>
                        <option value="10">10</option>
                        <option value="11">11</option>
                        <option value="12">12</option>
                        <option value="13">13</option>
                        <option value="14">14</option>
                        <option value="15">15</option>
                        <option value="16">16</option>
                        <option value="17">17</option>
                        <option value="18">18</option>
                        <option value="19">19</option>
                        <option value="20">20</option>
                        <option value="30">30</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Type</label>
                    <select name="type" id="libraryType"
                            hx-get="/library/statblocks" hx-trigger="change"
                            hx-target="#library-results"
                            hx-include="#librarySearch,#libraryCr,#librarySource">
                        <option value="">Any Type</option>
                        <option value="Aberration">Aberration</option>
                        <option value="Beast">Beast</option>
                        <option value="Celestial">Celestial</option>
                        <option value="Construct">Construct</option>
                        <option value="Dragon">Dragon</option>
                        <option value="Elemental">Elemental</option>
                        <option value="Fey">Fey</option>
                        <option value="Fiend">Fiend</option>
                        <option value="Giant">Giant</option>
                        <option value="Humanoid">Humanoid</option>
                        <option value="Monstrosity">Monstrosity</option>
                        <option value="Ooze">Ooze</option>
                        <option value="Plant">Plant</option>
                        <option value="Undead">Undead</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Source</label>
                    <select name="source" id="librarySource"
                            hx-get="/library/statblocks" hx-trigger="change"
                            hx-target="#library-results"
                            hx-include="#librarySearch,#libraryCr,#libraryType">
                        <option value="">All</option>
                        <option value="SRD">SRD</option>
                        <option value="CUSTOM">Custom</option>
                    </select>
                </div>
            </div>

            <div id="library-results" hx-get="/library/statblocks" hx-trigger="load" hx-swap="innerHTML">
                <div class="empty-state"><p>Loading...</p></div>
            </div>
        </main>
        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>

    <script>
        function switchFormTab(event, tabId) {
            document.querySelectorAll('.form-tab').forEach(t => t.classList.remove('active'));
            event.target.classList.add('active');
            document.querySelectorAll('.form-tab-content').forEach(c => c.classList.remove('active'));
            document.getElementById(tabId).classList.add('active');
        }
    </script>
</body>
</html>
```

Add a **Spells tab** to the library page by inserting the following after the "New Homebrew" button in the page-header div and before the monster search-bar:

```html
<div style="display: flex; gap: var(--space-md); align-items: center;">
    <button class="form-tab active" id="tab-monsters"
            onclick="switchLibraryTab('monsters')">Monsters</button>
    <button class="form-tab" id="tab-spells"
            onclick="switchLibraryTab('spells')">Spells</button>
</div>
```

Then add a spell search bar and results section after the monster results `div`:

```html
<div id="spell-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" id="spellSearch" placeholder="Search spells..."
                   hx-get="/library/spells"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#spell-results"
                   hx-include="#spellLevel,#spellSchool">
        </div>
        <div class="form-group">
            <label>Level</label>
            <select name="level" id="spellLevel"
                    hx-get="/library/spells" hx-trigger="change"
                    hx-target="#spell-results"
                    hx-include="#spellSearch,#spellSchool">
                <option value="">Any Level</option>
                <option value="0">Cantrip</option>
                <option value="1">1st</option>
                <option value="2">2nd</option>
                <option value="3">3rd</option>
                <option value="4">4th</option>
                <option value="5">5th</option>
                <option value="6">6th</option>
                <option value="7">7th</option>
                <option value="8">8th</option>
                <option value="9">9th</option>
            </select>
        </div>
        <div class="form-group">
            <label>School</label>
            <select name="school" id="spellSchool"
                    hx-get="/library/spells" hx-trigger="change"
                    hx-target="#spell-results"
                    hx-include="#spellSearch,#spellLevel">
                <option value="">Any School</option>
                <option value="Abjuration">Abjuration</option>
                <option value="Conjuration">Conjuration</option>
                <option value="Divination">Divination</option>
                <option value="Enchantment">Enchantment</option>
                <option value="Evocation">Evocation</option>
                <option value="Illusion">Illusion</option>
                <option value="Necromancy">Necromancy</option>
                <option value="Transmutation">Transmutation</option>
            </select>
        </div>
    </div>
    <div id="spell-results" hx-get="/library/spells" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading spells...</p></div>
    </div>
</div>
```

Add the tab-switching JavaScript alongside the existing `switchFormTab`:

```javascript
function switchLibraryTab(tab) {
    document.querySelectorAll('.form-tab').forEach(t => t.classList.remove('active'));
    if (tab === 'monsters') {
        document.getElementById('tab-monsters').classList.add('active');
        document.querySelector('.search-bar').style.display = 'flex';
        document.getElementById('library-results').style.display = 'block';
        document.getElementById('spell-section').style.display = 'none';
    } else {
        document.getElementById('tab-spells').classList.add('active');
        document.querySelector('.search-bar').style.display = 'none';
        document.getElementById('library-results').style.display = 'none';
        document.getElementById('spell-section').style.display = 'block';
    }
}
```

- [ ] **Step 6: Write statblock detail page**

Create `src/main/resources/templates/library/detail.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper -- ' + ${sb.name}">DMHelper -- Statblock</title>
    <script th:src="@{/vendor/alpine.min.js}"></script>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
    <div class="app-layout">
        <main>
            <div class="page-header">
                <a href="/library" class="btn btn-ghost">&larr; Back to Library</a>
                <div style="display: flex; gap: var(--space-sm);">
                    <th:block th:if="${sb.source.name() == 'SRD'}">
                        <form hx-post="@{/library/statblocks/{id}/clone(id=${sb.id})}" hx-target="body">
                            <button type="submit" class="btn btn-primary">Clone &amp; Edit</button>
                        </form>
                    </th:block>
                    <th:block th:if="${sb.source.name() == 'CUSTOM'}">
                        <a th:href="@{/library/statblocks/{id}/edit(id=${sb.id})}" class="btn btn-primary">Edit</a>
                        <button class="btn btn-danger"
                                hx-delete="@{/library/statblocks/{id}(id=${sb.id})}"
                                hx-confirm="Delete this statblock?"
                                hx-target="body" hx-swap="outerHTML">Delete</button>
                    </th:block>
                </div>
            </div>
            <div id="statblock-detail">
                <th:block th:replace="~{library/_statblock-renderer :: renderer(sb=${sb})}"></th:block>
            </div>
        </main>
        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 7: Write About page with SRD attribution**

Create `src/main/resources/templates/about.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title>DMHelper -- About</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
    <div class="app-layout">
        <main>
            <div class="page-header">
                <h1>About DMHelper</h1>
            </div>
            <div class="detail-section">
                <p>DMHelper is a local-first web application for running D&amp;D 5.5e (2024 rules) campaigns.</p>
                <h2>Open Source Licenses</h2>
                <h3>SRD 5.2 Content</h3>
                <p>The bundled monster statblocks and spell reference data are derived from the D&amp;D Systems Reference Document 5.2 (SRD 5.2), released under the Creative Commons Attribution 4.0 International License (CC-BY-4.0).</p>
                <p>This product includes material from the SRD 5.2 available at <a href="https://dnd.wizards.com/resources/systems-reference-document">Wizards of the Coast SRD page</a>.</p>
                <p>SRD 5.2 content is copyright Wizards of the Coast LLC.</p>
                <p>SRD 5.2 data sourced via the open5e community API (open5e.com).</p>
                <h2>Frontend Libraries</h2>
                <p>DMHelper includes htmx, Alpine.js, Konva.js, and commonmark-java, each used under their respective open-source licenses. See <code>VENDOR.md</code> for details.</p>
            </div>
        </main>
        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 7b: Write spell display fragment**

Create `src/main/resources/templates/library/_spell-card.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="statblock-card" th:fragment="spell-card(spell)">
    <h3 th:text="${spell.name}">Spell Name</h3>
    <div class="statblock-meta">
        <span th:if="${spell.level == 0}" class="badge-srd">Cantrip</span>
        <span th:unless="${spell.level == 0}" class="badge-srd" th:text="'Level ' + ${spell.level}"></span>
        <span th:text="${spell.school}">School</span>
        <span th:if="${spell.ritual}">Ritual</span>
        <span th:if="${spell.concentration}">Concentration</span>
    </div>
    <div class="spell-details">
        <p><strong>Casting Time:</strong> <span th:text="${spell.castingTime}"></span></p>
        <p><strong>Range:</strong> <span th:text="${spell.range}"></span></p>
        <p><strong>Components:</strong> <span th:text="${spell.components}"></span></p>
        <p><strong>Duration:</strong> <span th:text="${spell.duration}"></span></p>
    </div>
    <div class="sb-rule-thin" style="margin: var(--space-sm) 0; border-color: var(--color-border);"></div>
    <div th:utext="${spell.description}"></div>
    <div th:if="${spell.higherLevel != null and !spell.higherLevel.isBlank()}" style="margin-top: var(--space-sm);">
        <strong>At Higher Levels:</strong>
        <span th:utext="${spell.higherLevel}"></span>
    </div>
</div>

<div class="card-grid" th:fragment="spell-card-list(spells)" th:if="${spells != null}">
    <th:block th:if="${spells.isEmpty()}">
        <div class="empty-state"><p>No spells match your search.</p></div>
    </th:block>
    <th:block th:each="spell : ${spells}">
        <th:block th:replace="~{library/_spell-card :: spell-card(spell=${spell})}"></th:block>
    </th:block>
</div>
</html>
```

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/library/ src/main/resources/templates/about.html && git commit -m "feat: add library templates -- list, card, detail, statblock renderer, spell cards, create/edit form, and about page with SRD attribution"
```

---

### Task 9: Create LibraryController (TDD -- red then green)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`

- [ ] **Step 1: Create test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/library/web`

- [ ] **Step 2: Write controller tests (TDD red)**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.library.service.SpellService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryController.class)
class LibraryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService service;
    @MockitoBean private SpellService spellService;

    private StatBlock sampleSb() {
        StatBlock sb = new StatBlock();
        sb.setId(UUID.randomUUID());
        sb.setSource(StatBlock.Source.SRD);
        sb.setName("Goblin");
        sb.setCr("1/4");
        sb.setType("Humanoid");
        sb.setSize("Small");
        sb.setAlignment("Neutral Evil");
        sb.setAc(15);
        sb.setHp("7 (2d6)");
        sb.setSpeed("30 ft.");
        sb.setStrScore(8); sb.setDexScore(14); sb.setConScore(10);
        sb.setIntScore(10); sb.setWisScore(8); sb.setChaScore(8);
        sb.setSkills("Stealth +6");
        sb.setSenses("darkvision 60 ft., passive Perception 9");
        sb.setLanguages("Common, Goblin");
        return sb;
    }

    @Test
    void shouldRenderLibraryPage() throws Exception {
        mockMvc.perform(get("/library"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Monster Library")));
    }

    @Test
    void shouldRenderStatblockCards() throws Exception {
        when(service.search(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of(sampleSb()));
        mockMvc.perform(get("/library/statblocks"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Goblin")));
    }

    @Test
    void shouldRenderDetail() throws Exception {
        StatBlock sb = sampleSb();
        when(service.findById(sb.getId())).thenReturn(sb);
        mockMvc.perform(get("/library/statblocks/{id}", sb.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Armor Class")));
    }

    @Test
    void shouldCreateCustom() throws Exception {
        StatBlock sb = sampleSb();
        sb.setSource(StatBlock.Source.CUSTOM);
        when(service.createCustom(any(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyString(), anyString(),
                any(), any(), any(), any(), any(), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sb);

        mockMvc.perform(post("/library/statblocks")
                        .param("campaignId", UUID.randomUUID().toString())
                        .param("name", "Custom Goblin")
                        .param("cr", "1/4").param("type", "Humanoid")
                        .param("ac", "15").param("hp", "7 (2d6)")
                        .param("speed", "30 ft.")
                        .param("strScore", "8").param("dexScore", "14")
                        .param("conScore", "10").param("intScore", "10")
                        .param("wisScore", "8").param("chaScore", "8")
                        .param("hpValue", "").param("speedValue", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Custom Goblin")));
    }

    @Test
    void shouldDeleteCustom() throws Exception {
        mockMvc.perform(delete("/library/statblocks/{id}", UUID.randomUUID())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/library"));
    }

    @Test
    void shouldRenderNewForm() throws Exception {
        mockMvc.perform(get("/library/statblocks/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create Custom Statblock")));
    }

    @Test
    void shouldPromoteStatBlockToGlobal() throws Exception {
        StatBlock sb = sampleSb();
        sb.setSource(StatBlock.Source.CUSTOM);
        sb.setCampaignId(UUID.randomUUID());
        StatBlock promoted = sampleSb();
        promoted.setSource(StatBlock.Source.CUSTOM);
        promoted.setCampaignId(null);
        when(service.promoteToGlobal(sb.getId())).thenReturn(promoted);

        mockMvc.perform(put("/library/statblocks/{id}/promote", sb.getId())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRenderAboutPage() throws Exception {
        mockMvc.perform(get("/library/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CC-BY-4.0")));
    }

    @Test
    void shouldSearchSpells() throws Exception {
        when(spellService.search(eq("fire"), isNull(), isNull())).thenReturn(List.of());
        mockMvc.perform(get("/library/spells").param("search", "fire"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldListSrdKeys() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/library/statblocks/srd-keys"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=LibraryControllerTest`
Expected: Compilation fails -- `LibraryController` class not found

- [ ] **Step 4: Write LibraryController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.library.service.SpellService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/library")
public class LibraryController {

    private final StatBlockService service;
    private final SpellService spellService;
    private final ObjectMapper objectMapper;

    public LibraryController(StatBlockService service, SpellService spellService) {
        this.service = service;
        this.spellService = spellService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public String list() {
        return "library/list";
    }

    @GetMapping("/statblocks")
    public String search(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String cr,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String source,
                         Model model) {
        StatBlock.Source sourceEnum = null;
        if (source != null && !source.isBlank()) {
            sourceEnum = StatBlock.Source.valueOf(source);
        }
        List<StatBlock> results = service.search(sourceEnum, cr, type, search);
        model.addAttribute("statblocks", results);
        return "library/_card :: card-list";
    }

    @GetMapping("/statblocks/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        return "library/detail";
    }

    @GetMapping("/statblocks/new")
    public String newForm(Model model) {
        model.addAttribute("sb", null);
        model.addAttribute("campaignId", null);
        return "library/_form :: form";
    }

    @GetMapping("/statblocks/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        model.addAttribute("sb", sb);
        model.addAttribute("campaignId", sb.getCampaignId());
        return "library/_form :: form";
    }

    @PostMapping("/statblocks")
    public String create(@RequestParam(required = false) UUID campaignId,
                         @RequestParam String name, @RequestParam String cr,
                         @RequestParam String type, @RequestParam int ac,
                         @RequestParam String hp, @RequestParam String speed,
                         @RequestParam int strScore, @RequestParam int dexScore,
                         @RequestParam int conScore, @RequestParam int intScore,
                         @RequestParam int wisScore, @RequestParam int chaScore,
                         @RequestParam(required = false) String hpValue,
                         @RequestParam(required = false) String speedValue,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String alignment,
                         @RequestParam(required = false) Integer strSave,
                         @RequestParam(required = false) Integer dexSave,
                         @RequestParam(required = false) Integer conSave,
                         @RequestParam(required = false) Integer intSave,
                         @RequestParam(required = false) Integer wisSave,
                         @RequestParam(required = false) Integer chaSave,
                         @RequestParam(required = false) String skills,
                         @RequestParam(required = false) String damageVulnerabilities,
                         @RequestParam(required = false) String damageResistances,
                         @RequestParam(required = false) String damageImmunities,
                         @RequestParam(required = false) String conditionImmunities,
                         @RequestParam(required = false) String senses,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String traits,
                         @RequestParam(required = false) String actions,
                         @RequestParam(required = false) String bonusActions,
                         @RequestParam(required = false) String reactions,
                         @RequestParam(required = false) String legendaryActions,
                         @RequestParam(required = false) String legendaryDescription,
                         @RequestParam(required = false) String lairActions,
                         Model model) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        StatBlock sb = service.createCustom(campaignId, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                hpValue, speedValue,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages);
        if (size != null) sb.setSize(size);
        if (alignment != null) sb.setAlignment(alignment);
        if (traits != null) sb.setTraits(traits);
        if (actions != null) sb.setActions(actions);
        if (bonusActions != null) sb.setBonusActions(bonusActions);
        if (reactions != null) sb.setReactions(reactions);
        if (legendaryActions != null) sb.setLegendaryActions(legendaryActions);
        if (legendaryDescription != null) sb.setLegendaryDescription(legendaryDescription);
        if (lairActions != null) sb.setLairActions(lairActions);
        model.addAttribute("sb", sb);
        model.addAttribute("statblocks", List.of(sb));
        return "library/_card :: card";
    }

    @PutMapping("/statblocks/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam String name, @RequestParam String cr,
                         @RequestParam String type, @RequestParam int ac,
                         @RequestParam String hp, @RequestParam String speed,
                         @RequestParam int strScore, @RequestParam int dexScore,
                         @RequestParam int conScore, @RequestParam int intScore,
                         @RequestParam int wisScore, @RequestParam int chaScore,
                         @RequestParam(required = false) String hpValue,
                         @RequestParam(required = false) String speedValue,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String alignment,
                         @RequestParam(required = false) Integer strSave,
                         @RequestParam(required = false) Integer dexSave,
                         @RequestParam(required = false) Integer conSave,
                         @RequestParam(required = false) Integer intSave,
                         @RequestParam(required = false) Integer wisSave,
                         @RequestParam(required = false) Integer chaSave,
                         @RequestParam(required = false) String skills,
                         @RequestParam(required = false) String damageVulnerabilities,
                         @RequestParam(required = false) String damageResistances,
                         @RequestParam(required = false) String damageImmunities,
                         @RequestParam(required = false) String conditionImmunities,
                         @RequestParam(required = false) String senses,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String traits,
                         @RequestParam(required = false) String actions,
                         @RequestParam(required = false) String bonusActions,
                         @RequestParam(required = false) String reactions,
                         @RequestParam(required = false) String legendaryActions,
                         @RequestParam(required = false) String legendaryDescription,
                         @RequestParam(required = false) String lairActions,
                         Model model) {
        StatBlock sb = service.updateCustom(id, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                hpValue, speedValue,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages);
        if (size != null) sb.setSize(size);
        if (alignment != null) sb.setAlignment(alignment);
        if (traits != null) sb.setTraits(traits);
        if (actions != null) sb.setActions(actions);
        if (bonusActions != null) sb.setBonusActions(bonusActions);
        if (reactions != null) sb.setReactions(reactions);
        if (legendaryActions != null) sb.setLegendaryActions(legendaryActions);
        if (legendaryDescription != null) sb.setLegendaryDescription(legendaryDescription);
        if (lairActions != null) sb.setLairActions(lairActions);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        return "library/detail";
    }

    @DeleteMapping("/statblocks/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/statblocks/{id}/clone")
    public String clone(@PathVariable UUID id, Model model) {
        StatBlock original = service.findById(id);
        StatBlock cloned = service.cloneAsCustom(id, null, original.getName() + " (custom)");
        enrichStatBlock(cloned);
        model.addAttribute("sb", cloned);
        return "library/detail";
    }

    @PutMapping("/statblocks/{id}/promote")
    public String promoteToGlobal(@PathVariable UUID id, Model model) {
        StatBlock promoted = service.promoteToGlobal(id);
        enrichStatBlock(promoted);
        model.addAttribute("sb", promoted);
        return "library/_card :: card";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    // ------- Spell routes (read-only reference) -------

    @GetMapping("/spells")
    public String searchSpells(@RequestParam(required = false) String search,
                               @RequestParam(required = false) Integer level,
                               @RequestParam(required = false) String school,
                               Model model) {
        List<Spell> spells = spellService.search(search, level, school);
        model.addAttribute("spells", spells);
        return "library/_spell-card :: spell-card-list";
    }

    // ------- SRD key catalog -------

    @GetMapping("/statblocks/srd-keys")
    @ResponseBody
    public List<String> srdKeys() {
        return service.findAll().stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.SRD)
                .map(StatBlock::getSourceKey)
                .sorted()
                .toList();
    }

    private void enrichStatBlock(StatBlock sb) {
        sb.setTraitsParsed(parseJsonArray(sb.getTraits()));
        sb.setActionsParsed(parseJsonArray(sb.getActions()));
        sb.setBonusActionsParsed(parseJsonArray(sb.getBonusActions()));
        sb.setReactionsParsed(parseJsonArray(sb.getReactions()));
        sb.setLegendaryActionsParsed(parseJsonArray(sb.getLegendaryActions()));
        sb.setLairActionsParsed(parseJsonArray(sb.getLairActions()));
    }

    private List<Map<String, String>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of(Map.of("name", "(parse error)", "description", json));
        }
    }
}
```

- [ ] **Step 5: Add transient fields to StatBlock for parsed JSON**

Edit `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlock.java` and add these transient fields at the end of the class (before closing brace):

```java
    @Transient
    private transient List<Map<String, String>> traitsParsed;

    @Transient
    private transient List<Map<String, String>> actionsParsed;

    @Transient
    private transient List<Map<String, String>> bonusActionsParsed;

    @Transient
    private transient List<Map<String, String>> reactionsParsed;

    @Transient
    private transient List<Map<String, String>> legendaryActionsParsed;

    @Transient
    private transient List<Map<String, String>> lairActionsParsed;

    public List<Map<String, String>> getTraitsParsed() { return traitsParsed; }
    public void setTraitsParsed(List<Map<String, String>> v) { this.traitsParsed = v; }

    public List<Map<String, String>> getActionsParsed() { return actionsParsed; }
    public void setActionsParsed(List<Map<String, String>> v) { this.actionsParsed = v; }

    public List<Map<String, String>> getBonusActionsParsed() { return bonusActionsParsed; }
    public void setBonusActionsParsed(List<Map<String, String>> v) { this.bonusActionsParsed = v; }

    public List<Map<String, String>> getReactionsParsed() { return reactionsParsed; }
    public void setReactionsParsed(List<Map<String, String>> v) { this.reactionsParsed = v; }

    public List<Map<String, String>> getLegendaryActionsParsed() { return legendaryActionsParsed; }
    public void setLegendaryActionsParsed(List<Map<String, String>> v) { this.legendaryActionsParsed = v; }

    public List<Map<String, String>> getLairActionsParsed() { return lairActionsParsed; }
    public void setLairActionsParsed(List<Map<String, String>> v) { this.lairActionsParsed = v; }
```

- [ ] **Step 6: Run controller tests**

Run: `./mvnw test -pl . -Dtest=LibraryControllerTest`
Expected: All 10 tests pass

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlock.java src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java && git commit -m "feat: add LibraryController with search, CRUD, clone; add transient parsed JSON fields to StatBlock"
```

---

### Task 10: Write PartyMemberService tests (TDD -- red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberServiceTest.java`

- [ ] **Step 1: Create test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/party/service`

- [ ] **Step 2: Write failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PartyMemberService.class})
class PartyMemberServiceTest {

    @Autowired private PartyMemberRepository repository;
    @Autowired private PartyMemberService service;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        // The campaign is not saved via repository here; the service is expected to
        // create party members with an existing campaign reference. We use a
        // persisted dummy via entityManager in the actual test, or the DataJpaTest
        // saves the campaign. For simplicity, we test with the service directly.
        // The service needs a pre-saved Campaign -- we inject via EntityManager.
    }

    @Autowired
    private jakarta.persistence.EntityManager em;

    @BeforeEach
    void persistCampaign() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreatePartyMember() {
        PartyMember pm = service.create(campaign.getId(), "Thia", "Anna",
                "Rogue 5", 16, 38, 4, 30, 17, 12, 14, "darkvision, fey ancestry");
        assertThat(pm.getId()).isNotNull();
        assertThat(pm.getCharacterName()).isEqualTo("Thia");
        assertThat(pm.getPassivePerception()).isEqualTo(17);
        assertThat(pm.isActive()).isTrue();
    }

    @Test
    void shouldFindByCampaign() {
        service.create(campaign.getId(), "Thia", "Anna", "Rogue 5", 16, 38, 4, 30, 17, 12, 14, null);
        service.create(campaign.getId(), "Bruenor", "Bob", "Fighter 5", 18, 45, 2, 25, 13, 10, 9, null);
        var members = service.findByCampaignId(campaign.getId());
        assertThat(members).hasSize(2);
        assertThat(members.get(0).getCharacterName()).isEqualTo("Bruenor"); // alphabetical
    }

    @Test
    void shouldFindActiveOnly() {
        service.create(campaign.getId(), "Thia", "Anna", "Rogue 5", 16, 38, 4, 30, 17, 12, 14, null);
        var pm = service.create(campaign.getId(), "Inactive PC", "Dan", "Wizard 3", 12, 18, 2, 30, 11, 15, 18, null);
        service.setActive(pm.getId(), false);

        var active = service.findActiveByCampaignId(campaign.getId());
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getCharacterName()).isEqualTo("Thia");
    }

    @Test
    void shouldUpdatePartyMember() {
        var pm = service.create(campaign.getId(), "Thia", "Anna", "Rogue 5", 16, 38, 4, 30, 17, 12, 14, null);
        var updated = service.update(pm.getId(), "Thia", "Anna", "Rogue 6", 17, 45, 4, 30, 18, 12, 14, "expertise in stealth");
        assertThat(updated.getClassAndLevel()).isEqualTo("Rogue 6");
        assertThat(updated.getAc()).isEqualTo(17);
    }

    @Test
    void shouldDeletePartyMember() {
        var pm = service.create(campaign.getId(), "Delete Me", "X", "Wizard 1", 10, 6, 0, 30, 10, 10, 10, null);
        service.delete(pm.getId());
        assertThat(repository.findById(pm.getId())).isEmpty();
    }

    @Test
    void shouldToggleActive() {
        var pm = service.create(campaign.getId(), "Toggle", "T", "Cleric 1", 18, 10, 1, 25, 15, 15, 10, null);
        assertThat(pm.isActive()).isTrue();
        service.setActive(pm.getId(), false);
        var reloaded = repository.findById(pm.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=PartyMemberServiceTest`
Expected: Compilation fails -- `PartyMemberService` class not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberServiceTest.java && git commit -m "test: add PartyMemberService tests (TDD red phase)"
```

---

### Task 11: Implement PartyMemberService (TDD -- green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberService.java`

- [ ] **Step 1: Create service package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/party/service`

- [ ] **Step 2: Write PartyMemberService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberService.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PartyMemberService {

    private final PartyMemberRepository repository;
    private final EntityManager em;

    public PartyMemberService(PartyMemberRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    public PartyMember create(UUID campaignId, String characterName, String playerName,
                              String classAndLevel, int ac, int maxHp, int initiativeBonus,
                              int speed, int passivePerception, int passiveInsight,
                              int passiveInvestigation, String notes) {
        Campaign campaign = em.getReference(Campaign.class, campaignId);
        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName(characterName);
        pm.setPlayerName(playerName);
        pm.setClassAndLevel(classAndLevel);
        pm.setAc(ac);
        pm.setMaxHp(maxHp);
        pm.setInitiativeBonus(initiativeBonus);
        pm.setSpeed(speed);
        pm.setPassivePerception(passivePerception);
        pm.setPassiveInsight(passiveInsight);
        pm.setPassiveInvestigation(passiveInvestigation);
        pm.setNotes(notes);
        pm.setActive(true);
        return repository.save(pm);
    }

    @Transactional(readOnly = true)
    public List<PartyMember> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByCharacterNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public List<PartyMember> findActiveByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public PartyMember findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Party member not found: " + id));
    }

    public PartyMember update(UUID id, String characterName, String playerName,
                              String classAndLevel, int ac, int maxHp, int initiativeBonus,
                              int speed, int passivePerception, int passiveInsight,
                              int passiveInvestigation, String notes) {
        PartyMember pm = findById(id);
        pm.setCharacterName(characterName);
        pm.setPlayerName(playerName);
        pm.setClassAndLevel(classAndLevel);
        pm.setAc(ac);
        pm.setMaxHp(maxHp);
        pm.setInitiativeBonus(initiativeBonus);
        pm.setSpeed(speed);
        pm.setPassivePerception(passivePerception);
        pm.setPassiveInsight(passiveInsight);
        pm.setPassiveInvestigation(passiveInvestigation);
        pm.setNotes(notes);
        return repository.save(pm);
    }

    public void delete(UUID id) {
        PartyMember pm = findById(id);
        repository.delete(pm);
    }

    public void setActive(UUID id, boolean active) {
        PartyMember pm = findById(id);
        pm.setActive(active);
        repository.save(pm);
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./mvnw test -pl . -Dtest=PartyMemberServiceTest`
Expected: All 7 tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberService.java && git commit -m "feat: implement PartyMemberService with CRUD and active toggle"
```

---

### Task 12: Create party Thymeleaf templates

**Files:**
- Create: `src/main/resources/templates/party/list.html`
- Create: `src/main/resources/templates/party/_card.html`
- Create: `src/main/resources/templates/party/_form.html`
- Create: `src/main/resources/templates/party/_summary-bar.html`

- [ ] **Step 1: Create party templates directory**

Run: `mkdir -p src/main/resources/templates/party`

- [ ] **Step 2: Write party summary bar fragment**

Create `src/main/resources/templates/party/_summary-bar.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="party-summary-bar" th:fragment="summary-bar(members)">
    <span class="bar-label">Party</span>
    <th:block th:if="${members == null or members.isEmpty()}">
        <span style="color: var(--color-text-muted); font-size: var(--text-sm);">No party members yet.</span>
    </th:block>
    <th:block th:each="pm : ${members}">
        <div class="party-member-chip">
            <span class="chip-name" th:text="${pm.characterName}">Name</span>
            <span class="chip-stats">
                <span th:text="'AC ' + ${pm.ac}">AC 16</span>
                <span th:text="'PP ' + ${pm.passivePerception}">PP 17</span>
            </span>
        </div>
    </th:block>
</div>
</html>
```

- [ ] **Step 3: Write party member card fragment**

Create `src/main/resources/templates/party/_card.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="party-member-card" th:fragment="card(pm)" th:classappend="${!pm.active} ? 'pm-inactive'">
    <h3 th:text="${pm.characterName}">Character Name</h3>
    <div class="pm-player">
        <span th:if="${pm.playerName != null and !pm.playerName.isBlank()}">
            Played by <span th:text="${pm.playerName}"></span>
        </span>
        <span th:if="${pm.classAndLevel != null and !pm.classAndLevel.isBlank()}"
              th:text="' | ' + ${pm.classAndLevel}"></span>
    </div>
    <dl class="pm-stats">
        <div><dt>AC</dt><dd th:text="${pm.ac}">16</dd></div>
        <div><dt>HP</dt><dd th:text="${pm.maxHp}">38</dd></div>
        <div><dt>Init</dt><dd th:text="'+' + ${pm.initiativeBonus}">+4</dd></div>
        <div><dt>Speed</dt><dd th:text="${pm.speed + ' ft.'}">30 ft.</dd></div>
        <div><dt>P. Perception</dt><dd th:text="${pm.passivePerception}">17</dd></div>
        <div><dt>P. Insight</dt><dd th:text="${pm.passiveInsight}">12</dd></div>
        <div><dt>P. Investigation</dt><dd th:text="${pm.passiveInvestigation}">14</dd></div>
    </dl>
    <div class="pm-actions">
        <a th:href="@{/campaigns/{cid}/party/{pid}/edit(cid=${pm.campaign.id}, pid=${pm.id})}" class="btn btn-ghost">Edit</a>
        <th:block th:if="${pm.active}">
            <button class="btn btn-ghost"
                    hx-put="@{/campaigns/{cid}/party/{pid}/toggle-active(cid=${pm.campaign.id}, pid=${pm.id})}"
                    hx-target="closest .party-member-card" hx-swap="outerHTML">Mark Inactive</button>
        </th:block>
        <th:block th:if="${!pm.active}">
            <button class="btn btn-ghost"
                    hx-put="@{/campaigns/{cid}/party/{pid}/toggle-active(cid=${pm.campaign.id}, pid=${pm.id})}"
                    hx-target="closest .party-member-card" hx-swap="outerHTML">Mark Active</button>
        </th:block>
        <button class="btn btn-danger"
                hx-delete="@{/campaigns/{cid}/party/{pid}(cid=${pm.campaign.id}, pid=${pm.id})}"
                hx-confirm="Remove this party member?"
                hx-target="closest .party-member-card" hx-swap="outerHTML">Remove</button>
    </div>
</div>
</html>
```

- [ ] **Step 4: Write party form fragment**

Create `src/main/resources/templates/party/_form.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="inline-form" th:fragment="form(campaignId, pm)">
    <h3 th:text="${pm == null ? 'Add Party Member' : 'Edit ' + pm.characterName}">Add Party Member</h3>
    <form th:action="${pm == null ? @{/campaigns/{cid}/party(cid=${campaignId})} : @{/campaigns/{cid}/party/{pid}(cid=${campaignId}, pid=${pm.id})}}"
          th:method="${pm == null ? 'post' : 'put'}"
          hx-target="${pm == null ? '#party-grid' : 'body'}"
          th:hx-swap="${pm == null ? 'beforeend' : 'outerHTML'}">
        <div class="form-group"><label>Character Name *</label><input type="text" name="characterName" required th:value="${pm?.characterName}" placeholder="e.g. Thia"></div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md);">
            <div class="form-group"><label>Player Name</label><input type="text" name="playerName" th:value="${pm?.playerName}" placeholder="Anna"></div>
            <div class="form-group"><label>Class &amp; Level</label><input type="text" name="classAndLevel" th:value="${pm?.classAndLevel}" placeholder="Rogue 5"></div>
        </div>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-md);">
            <div class="form-group"><label>AC *</label><input type="number" name="ac" required th:value="${pm?.ac}" placeholder="16"></div>
            <div class="form-group"><label>Max HP *</label><input type="number" name="maxHp" required th:value="${pm?.maxHp}" placeholder="38"></div>
            <div class="form-group"><label>Init Bonus *</label><input type="number" name="initiativeBonus" required th:value="${pm?.initiativeBonus}" placeholder="4"></div>
            <div class="form-group"><label>Speed *</label><input type="number" name="speed" required th:value="${pm?.speed}" placeholder="30"></div>
        </div>
        <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-md);">
            <div class="form-group"><label>Passive Perception *</label><input type="number" name="passivePerception" required th:value="${pm?.passivePerception}" placeholder="17"></div>
            <div class="form-group"><label>Passive Insight *</label><input type="number" name="passiveInsight" required th:value="${pm?.passiveInsight}" placeholder="12"></div>
            <div class="form-group"><label>Passive Investigation *</label><input type="number" name="passiveInvestigation" required th:value="${pm?.passiveInvestigation}" placeholder="14"></div>
        </div>
        <div class="form-group"><label>Notes</label><textarea name="notes" rows="3" th:text="${pm?.notes}" placeholder="e.g. darkvision, fey ancestry"></textarea></div>
        <div class="form-actions">
            <button type="button" class="btn btn-ghost"
                    th:if="${pm != null}"
                    hx-get="@{/campaigns/{cid}/party(cid=${campaignId})}" hx-target="body" hx-swap="outerHTML">Cancel</button>
            <button type="submit" class="btn btn-primary" th:text="${pm == null ? 'Add' : 'Save'}">Add</button>
        </div>
    </form>
</div>
</html>
```

- [ ] **Step 5: Write party list page**

Create `src/main/resources/templates/party/list.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper -- Party | ' + ${campaign.name}">DMHelper -- Party</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
    <div class="app-layout">
        <main>
            <div class="page-header">
                <div>
                    <h1 th:text="${campaign.name + ' -- Party'}">Campaign -- Party</h1>
                    <a th:href="@{/campaigns/{id}(id=${campaign.id})}" class="btn btn-ghost" style="margin-top: var(--space-xs);">&larr; Back to Campaign</a>
                </div>
                <button class="btn btn-primary"
                        hx-get="@{/campaigns/{cid}/party/new(cid=${campaign.id})}"
                        hx-target="previous .card-grid"
                        hx-swap="beforeend">+ Add Member</button>
            </div>

            <th:block th:replace="~{party/_summary-bar :: summary-bar(members=${activeMembers})}"></th:block>

            <div id="party-grid" class="card-grid">
                <th:block th:if="${members == null or members.isEmpty()}">
                    <div class="empty-state"><p>No party members yet. Add your first player character!</p></div>
                </th:block>
                <th:block th:each="pm : ${members}">
                    <th:block th:replace="~{party/_card :: card(pm=${pm})}"></th:block>
                </th:block>
            </div>
        </main>
        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/party/ && git commit -m "feat: add party templates -- list, card, form, summary bar"
```

---

### Task 13: Create PartyController (TDD -- red then green)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java`

- [ ] **Step 1: Create test and web packages**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/party/web`

- [ ] **Step 2: Write controller tests (TDD red)**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PartyController.class)
class PartyControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CampaignService campaignService;
    @MockitoBean private PartyMemberService partyService;

    private UUID campaignId = UUID.randomUUID();

    @Test
    void shouldRenderPartyList() throws Exception {
        Campaign c = new Campaign(); c.setId(campaignId); c.setName("Test");
        when(campaignService.findById(campaignId)).thenReturn(c);
        when(partyService.findByCampaignId(campaignId)).thenReturn(List.of());
        when(partyService.findActiveByCampaignId(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{cid}/party", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Party")));
    }

    @Test
    void shouldCreatePartyMember() throws Exception {
        Campaign c = new Campaign(); c.setId(campaignId); c.setName("Test");
        PartyMember pm = new PartyMember();
        pm.setId(UUID.randomUUID());
        pm.setCampaign(c);
        pm.setCharacterName("Thia");
        pm.setAc(16);
        pm.setMaxHp(38);
        pm.setPassivePerception(17);

        when(partyService.create(eq(campaignId), eq("Thia"), any(), any(), eq(16), eq(38), eq(4), eq(30),
                eq(17), eq(12), eq(14), any())).thenReturn(pm);

        mockMvc.perform(post("/campaigns/{cid}/party", campaignId)
                        .param("characterName", "Thia")
                        .param("ac", "16").param("maxHp", "38")
                        .param("initiativeBonus", "4").param("speed", "30")
                        .param("passivePerception", "17").param("passiveInsight", "12")
                        .param("passiveInvestigation", "14")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thia")));
    }

    @Test
    void shouldDeletePartyMember() throws Exception {
        UUID pid = UUID.randomUUID();
        mockMvc.perform(delete("/campaigns/{cid}/party/{pid}", campaignId, pid)
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect",
                        containsString("/campaigns/" + campaignId + "/party")));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=PartyControllerTest`
Expected: Compilation fails -- `PartyController` class not found

- [ ] **Step 4: Write PartyController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/party")
public class PartyController {

    private final CampaignService campaignService;
    private final PartyMemberService partyService;

    public PartyController(CampaignService campaignService, PartyMemberService partyService) {
        this.campaignService = campaignService;
        this.partyService = partyService;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignService.findById(campaignId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("members", partyService.findByCampaignId(campaignId));
        model.addAttribute("activeMembers", partyService.findActiveByCampaignId(campaignId));
        return "party/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("pm", null);
        return "party/_form :: form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("pm", partyService.findById(id));
        return "party/_form :: form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String characterName,
                         @RequestParam(required = false) String playerName,
                         @RequestParam(required = false) String classAndLevel,
                         @RequestParam int ac, @RequestParam int maxHp,
                         @RequestParam int initiativeBonus, @RequestParam int speed,
                         @RequestParam int passivePerception, @RequestParam int passiveInsight,
                         @RequestParam int passiveInvestigation,
                         @RequestParam(required = false) String notes,
                         Model model) {
        PartyMember pm = partyService.create(campaignId, characterName, playerName,
                classAndLevel, ac, maxHp, initiativeBonus, speed,
                passivePerception, passiveInsight, passiveInvestigation, notes);
        model.addAttribute("pm", pm);
        return "party/_card :: card";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId, @PathVariable UUID id,
                         @RequestParam String characterName,
                         @RequestParam(required = false) String playerName,
                         @RequestParam(required = false) String classAndLevel,
                         @RequestParam int ac, @RequestParam int maxHp,
                         @RequestParam int initiativeBonus, @RequestParam int speed,
                         @RequestParam int passivePerception, @RequestParam int passiveInsight,
                         @RequestParam int passiveInvestigation,
                         @RequestParam(required = false) String notes) {
        partyService.update(id, characterName, playerName, classAndLevel,
                ac, maxHp, initiativeBonus, speed,
                passivePerception, passiveInsight, passiveInvestigation, notes);
        return "redirect:/campaigns/" + campaignId + "/party";
    }

    @PutMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        PartyMember pm = partyService.findById(id);
        partyService.setActive(id, !pm.isActive());
        model.addAttribute("pm", partyService.findById(id));
        return "party/_card :: card";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        partyService.delete(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/party")
                .build();
    }
}
```

- [ ] **Step 5: Run controller tests**

Run: `./mvnw test -pl . -Dtest=PartyControllerTest`
Expected: All 3 tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java && git commit -m "feat: add PartyController with CRUD and active toggle endpoints"
```

---

### Task 14: Add navigation links (navbar + campaign detail)

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/templates/campaigns/detail.html`

- [ ] **Step 1: Update navbar with Library link**

Read `src/main/resources/templates/fragments/navbar.html`. It has:
```html
<nav class="navbar" th:fragment="navbar">
    <a href="/campaigns" class="navbar-brand">DMHelper</a>
    ...
```

Add nav links between the brand and the right section. Edit the navbar to:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<nav class="navbar" th:fragment="navbar">
    <div style="display: flex; align-items: center; gap: var(--space-lg);">
        <a href="/campaigns" class="navbar-brand">DMHelper</a>
        <nav style="display: flex; gap: var(--space-md);">
            <a href="/campaigns" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Campaigns</a>
            <a href="/library" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Library</a>
            <a href="/library/about" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">About</a>
        </nav>
    </div>
    <div class="navbar-right">
        <label class="dm-toggle">
            <input type="checkbox" id="dmModeCheckbox"
                   onclick="document.body.classList.toggle('dm-mode-off')">
            DM Mode
        </label>
        <span class="pin-display" id="pinDisplay">PIN: &#x2014;</span>
    </div>
</nav>
</html>
```

- [ ] **Step 2: Add Party link to campaign detail page**

Read `src/main/resources/templates/campaigns/detail.html`. Add a party roster link after the description. Edit the detail page to add this block between the description and the Edit section:

```html
                <h2>Party</h2>
                <div class="detail-actions" style="margin-bottom: var(--space-lg);">
                    <a th:href="@{/campaigns/{id}/party(id=${campaign.id})}" class="btn btn-primary">
                        Manage Party Roster
                    </a>
                </div>
```

Insert this after the `detail-description` div and before the `<h2>Edit</h2>`.

- [ ] **Step 3: Verify compilation and visual check**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html src/main/resources/templates/campaigns/detail.html && git commit -m "feat: add Library nav link and Party link on campaign detail page"
```

---

### Task 15: Update CampaignService for party/statblock export/import

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

- [ ] **Step 1: Add typed DTOs for export**

Edit `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`. Replace the `List<Object>` fields with typed records.

Replace the entire file with:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import tools.jackson.annotation.JsonInclude;

import java.util.List;

public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<PartyMemberExportDto> party,
        List<StatBlockExportDto> statBlocks,
        List<Object> handouts,
        List<Object> maps,
        List<Object> encounters,
        List<Object> notes
) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static CampaignExportDto from(
            dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign,
            List<PartyMemberExportDto> party,
            List<StatBlockExportDto> statBlocks) {
        return new CampaignExportDto(
                CURRENT_FORMAT_VERSION,
                new CampaignDto(campaign.getName(), campaign.getDescription()),
                party,
                statBlocks,
                List.of(), List.of(), List.of(), List.of()
        );
    }

    public record CampaignDto(
            String name,
            @JsonInclude(JsonInclude.Include.NON_DEFAULT) String description
    ) {
        public CampaignDto {
            if (description != null && description.isBlank()) {
                description = null;
            }
        }
    }

    public record PartyMemberExportDto(
            String characterName, String playerName, String classAndLevel,
            int ac, int maxHp, int initiativeBonus, int speed,
            int passivePerception, int passiveInsight, int passiveInvestigation,
            String notes, boolean active
    ) {
        public static PartyMemberExportDto from(
                dev.hendrikhoemberg.dmhelper.party.data.PartyMember pm) {
            return new PartyMemberExportDto(
                    pm.getCharacterName(), pm.getPlayerName(), pm.getClassAndLevel(),
                    pm.getAc(), pm.getMaxHp(), pm.getInitiativeBonus(), pm.getSpeed(),
                    pm.getPassivePerception(), pm.getPassiveInsight(),
                    pm.getPassiveInvestigation(), pm.getNotes(), pm.isActive()
            );
        }
    }

    public record StatBlockExportDto(
            String sourceKey, String name, String cr, String type,
            String size, String alignment,
            int ac, String hp, String speed,
            int strScore, int dexScore, int conScore, int intScore, int wisScore, int chaScore,
            Integer strSave, Integer dexSave, Integer conSave,
            Integer intSave, Integer wisSave, Integer chaSave,
            String skills,
            String damageVulnerabilities, String damageResistances,
            String damageImmunities, String conditionImmunities,
            String senses, String languages,
            String traits, String actions, String bonusActions, String reactions,
            String legendaryActions, String legendaryDescription, String lairActions,
            int xp
    ) {
        public static StatBlockExportDto from(
                dev.hendrikhoemberg.dmhelper.library.data.StatBlock sb) {
            return new StatBlockExportDto(
                    sb.getSourceKey(), sb.getName(), sb.getCr(), sb.getType(),
                    sb.getSize(), sb.getAlignment(),
                    sb.getAc(), sb.getHp(), sb.getSpeed(),
                    sb.getStrScore(), sb.getDexScore(), sb.getConScore(),
                    sb.getIntScore(), sb.getWisScore(), sb.getChaScore(),
                    sb.getStrSave(), sb.getDexSave(), sb.getConSave(),
                    sb.getIntSave(), sb.getWisSave(), sb.getChaSave(),
                    sb.getSkills(),
                    sb.getDamageVulnerabilities(), sb.getDamageResistances(),
                    sb.getDamageImmunities(), sb.getConditionImmunities(),
                    sb.getSenses(), sb.getLanguages(),
                    sb.getTraits(), sb.getActions(), sb.getBonusActions(), sb.getReactions(),
                    sb.getLegendaryActions(), sb.getLegendaryDescription(), sb.getLairActions(),
                    sb.getXp()
            );
        }
    }
}
```

- [ ] **Step 2: Update CampaignService.exportToJson()**

Edit `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`. Add constructor injection of `PartyMemberRepository` and `StatBlockRepository`:

```java
public class CampaignService {

    private final CampaignRepository repository;
    private final PartyMemberRepository partyMemberRepository;
    private final StatBlockRepository statBlockRepository;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           StatBlockRepository statBlockRepository) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.statBlockRepository = statBlockRepository;
        this.objectMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    // ... existing methods unchanged ...

    @Transactional(readOnly = true)
    public String exportToJson(UUID id) {
        Campaign campaign = findById(id);
        var party = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(id).stream()
                .map(CampaignExportDto.PartyMemberExportDto::from).toList();
        var statBlocks = statBlockRepository.findByCampaignIdOrderByNameAsc(id).stream()
                .map(CampaignExportDto.StatBlockExportDto::from).toList();
        CampaignExportDto dto = CampaignExportDto.from(campaign, party, statBlocks);
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export campaign", e);
        }
    }
}
```

Also add the imports:
```java
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
```

- [ ] **Step 3: Update import for importFromJson**

In `importFromJson()`, after creating the campaign from the DTO, loop through party and statblocks to import them. Also inject `PartyMemberService` and `StatBlockService` into `CampaignService`:

```java
    private final PartyMemberService partyMemberService;
    private final StatBlockService statBlockService;

    public CampaignService(CampaignRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           StatBlockRepository statBlockRepository,
                           PartyMemberService partyMemberService,
                           StatBlockService statBlockService) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.statBlockRepository = statBlockRepository;
        this.partyMemberService = partyMemberService;
        this.statBlockService = statBlockService;
        this.objectMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public Campaign importFromJson(String json) {
        // ... existing validation and campaign creation ...

        Campaign saved = create(dto.campaign().name(), dto.campaign().description());

        if (dto.party() != null) {
            for (var pmDto : dto.party()) {
                partyMemberService.create(saved.getId(),
                        pmDto.characterName(), pmDto.playerName(),
                        pmDto.classAndLevel(), pmDto.ac(), pmDto.maxHp(),
                        pmDto.initiativeBonus(), pmDto.speed(),
                        pmDto.passivePerception(), pmDto.passiveInsight(),
                        pmDto.passiveInvestigation(), pmDto.notes());
            }
        }
        if (dto.statBlocks() != null) {
            for (var sbDto : dto.statBlocks()) {
                StatBlock sb = statBlockService.createCustom(saved.getId(),
                        sbDto.name(), sbDto.cr(), sbDto.type(),
                        sbDto.ac(), sbDto.hp(), sbDto.speed(),
                        sbDto.strScore(), sbDto.dexScore(), sbDto.conScore(),
                        sbDto.intScore(), sbDto.wisScore(), sbDto.chaScore(),
                        null, null,
                        sbDto.strSave(), sbDto.dexSave(), sbDto.conSave(),
                        sbDto.intSave(), sbDto.wisSave(), sbDto.chaSave(),
                        sbDto.skills(),
                        sbDto.damageVulnerabilities(), sbDto.damageResistances(),
                        sbDto.damageImmunities(), sbDto.conditionImmunities(),
                        sbDto.senses(), sbDto.languages());
                if (sbDto.size() != null) sb.setSize(sbDto.size());
                if (sbDto.alignment() != null) sb.setAlignment(sbDto.alignment());
                if (sbDto.traits() != null) sb.setTraits(sbDto.traits());
                if (sbDto.actions() != null) sb.setActions(sbDto.actions());
                if (sbDto.bonusActions() != null) sb.setBonusActions(sbDto.bonusActions());
                if (sbDto.reactions() != null) sb.setReactions(sbDto.reactions());
                if (sbDto.legendaryActions() != null) sb.setLegendaryActions(sbDto.legendaryActions());
                if (sbDto.legendaryDescription() != null) sb.setLegendaryDescription(sbDto.legendaryDescription());
                if (sbDto.lairActions() != null) sb.setLairActions(sbDto.lairActions());
                sb.setXp(sbDto.xp());
            }
        }

        return saved;
    }
```

Also add the imports:
```java
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
```

- [ ] **Step 4: Run existing tests**

Run: `./mvnw test -pl . -Dtest=CampaignServiceTest`
Expected: All tests pass (the new repositories must be mocked or injected). If tests fail due to missing beans, add `@MockitoBean` fields for `PartyMemberRepository` and `StatBlockRepository` to `CampaignServiceTest`:

```java
    @MockitoBean
    private PartyMemberRepository partyMemberRepository;

    @MockitoBean
    private StatBlockRepository statBlockRepository;

    @MockitoBean
    private PartyMemberService partyMemberService;

    @MockitoBean
    private StatBlockService statBlockService;
```

Note: In a `@DataJpaTest` with `@Import(CampaignService.class)`, the `@MockitoBean` must be used for the new repositories and services since they are now constructor-injected dependencies. Add these four fields to `CampaignServiceTest`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/ src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java && git commit -m "feat: extend campaign export/import with full party and custom statblock support"
```

---

### Task 16: Final verification

- [ ] **Step 1: Run all tests**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Build JAR**

Run: `./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS, JAR in target

- [ ] **Step 3: Manual smoke test**

Run: `java -jar target/dmhelper-0.0.1-SNAPSHOT.jar`
Open `http://localhost:8081`:

1. **Library:** Click "Library" in navbar. Verify 331 SRD 5.2 monsters are seeded (Goblin Warrior/Minion/Boss as Fey, Kobold Warrior as Dragon, etc.). Search "Goblin" — results appear. Filter by CR 5 — should filter. Click a monster — statblock detail renders in 5.5e format. Clone an SRD monster — new custom statblock created.

2. **Party:** Go to a campaign detail, click "Manage Party Roster". Add party members. Verify summary bar shows AC and passive Perception. Edit a member. Mark one inactive. Delete one.

3. **Export:** Export the campaign -- verify the JSON contains party members and custom statblocks.

4. **Homebrew:** From library, click "+ New Homebrew". Create a monster. Verify it appears in results. Edit it. Delete it. Create another custom, click "Promote to Global" -- verify it's shown without campaign badge.

5. **Attribution:** Click "About" in the navbar. Verify CC-BY-4.0 attribution text is shown.

6. **Import:** Export a campaign with party + custom statblocks, then import it. Verify party members and statblocks appear in the imported campaign.

7. **Performance:** Search for "Goblin" -- verify results appear in <100 ms.

8. **Spells:** Switch to "Spells" tab in Library. Verify 339 spells are seeded. Search "Fireball" — verify result appears. Filter by level 3 — shows only 3rd-level spells. Filter by school Evocation — combined filter works.

9. **SRD key catalog:** Visit `/library/statblocks/srd-keys` — verify 331 source keys returned as JSON array.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: final verification -- all tests pass, manual smoke test checklist confirmed"
```

---

## Task Summary

| Task | Component | Key Files |
|---|---|---|
| 1 | StatBlock entity + repo (incl. xp field) | `library/data/StatBlock.java`, `StatBlockRepository.java` |
| 2 | PartyMember entity + repo | `party/data/PartyMember.java`, `PartyMemberRepository.java` |
| 3 | SRD seed data (open5e srd-2024 API) | `srd/srd-5.2-monsters.json`, `bin/generate-srd-json.py` |
| 4 | SrdSeedService (incl. xp) | `library/service/SrdSeedService.java`, `DmhelperApplication.java` |
| 4b | Spell entity, seed data, SpellService | `library/data/Spell.java`, `SpellRepository.java`, `library/service/SpellSeedService.java`, `SpellService.java`, `srd/srd-5.2-spells.json`, `bin/generate-srd-spells.py` |
| 5 | StatBlockService tests (red) | `library/service/StatBlockServiceTest.java` |
| 6 | StatBlockService impl (green, incl. xp in clone) | `library/service/StatBlockService.java` |
| 7 | CSS additions | `static/css/app.css` |
| 8 | Library/spell templates + About page | `templates/library/{list,detail,_card,_form,_statblock-renderer,_spell-card}.html`, `templates/about.html` |
| 9 | LibraryController (red+green, incl. spells + SRD key catalog) | `library/web/LibraryController.java`, `LibraryControllerTest.java` |
| 10 | PartyMemberService tests (red) | `party/service/PartyMemberServiceTest.java` |
| 11 | PartyMemberService impl (green) | `party/service/PartyMemberService.java` |
| 12 | Party templates | `templates/party/{list,_card,_form,_summary-bar}.html` |
| 13 | PartyController (red+green) | `party/web/PartyController.java`, `PartyControllerTest.java` |
| 14 | Navbar + campaign links | `fragments/navbar.html`, `campaigns/detail.html` |
| 15 | Export/import update (incl. xp in DTO) | `campaign/service/CampaignExportDto.java`, `CampaignService.java` |
| 16 | Final verification | Manual smoke test, all tests pass |
