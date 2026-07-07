# M3: Reference Compendium — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Look up "Grappled", "Bag of Holding", and the Fighter level table without a PDF.

**Architecture:** 8 new read-only JPA entities (Condition, RuleSection, EquipmentItem, MagicItem, CharacterClass, Species, Background, Feat) in the `library/` package, each with a repository, seed service, and search service — following the exact M2 pattern of `SpellService` + `SpellSeedService`. Rules, Magic Items, Classes, Species, Backgrounds, and Feats are fetched from the open5e V2 API (`srd-2024` document) and committed as JSON seed files in `src/main/resources/srd/`. Conditions and Equipment are NOT in open5e's srd-2024 document and MUST be transcribed verbatim from the official SRD 5.2 PDF (CC-BY-4.0) into seed files — per §2.3.8 provenance rule, never guessed from memory. The Library page gains 8 new tabbed sections alongside existing Monster/Spell tabs, each with its own htmx-driven search and card renderer. The SRD key catalog endpoint is extended to include all compendium types.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA + JpaSpecificationExecutor, H2, Thymeleaf + htmx, Jackson 3 (`tools.jackson`), JUnit 5 + Mockito + AssertJ + Hamcrest

---

### Task 1: Create the JSON seed files (fetched from open5e API)

**Files:**
- Create: `src/main/resources/srd/srd-5.2-rules.json`
- Create: `src/main/resources/srd/srd-5.2-magic-items.json`
- Create: `src/main/resources/srd/srd-5.2-classes.json`
- Create: `src/main/resources/srd/srd-5.2-species.json`
- Create: `src/main/resources/srd/srd-5.2-backgrounds.json`
- Create: `src/main/resources/srd/srd-5.2-feats.json`

**Background:** The existing M2 seed files (`srd-5.2-monsters.json`, `srd-5.2-spells.json`) were pre-fetched and checked in. M3 follows the same pattern. Six compendium types are available from open5e's V2 API filtered by `document__key__in=srd-2024`. Equipment and Conditions are NOT available (see Task 2).

- [ ] **Step 1: Fetch Rules from open5e API and save as JSON**

Run the following curl commands to paginate through all 56+ rules:

Rules fits in one page; wrap in a provenance header object per §2.3.8 (source + date recorded in the file itself):

```bash
curl -s "https://api.open5e.com/v2/rules/?document__key__in=srd-2024&limit=100" | \
  jq '{_source: "open5e V2 API (srd-2024 document)", _fetched: "2026-07-07", results: .results}' \
  > src/main/resources/srd/srd-5.2-rules.json
```

Verify: `jq '.results | length' src/main/resources/srd/srd-5.2-rules.json` should show 56.

- [ ] **Step 2: Fetch Magic Items from open5e API and save as JSON (757 items, 16 pages)**

```bash
rm -f /tmp/magicitems_all.json
for page in $(seq 1 16); do
  curl -s "https://api.open5e.com/v2/magicitems/?document__key__in=srd-2024&limit=50&page=$page" | jq '.results[]' >> /tmp/magicitems_all.json
done
jq -s '{_source: "open5e V2 API (srd-2024 document)", _fetched: "2026-07-07", results: .}' \
  /tmp/magicitems_all.json > src/main/resources/srd/srd-5.2-magic-items.json
rm /tmp/magicitems_all.json
```

Verify: `jq '.results | length' src/main/resources/srd/srd-5.2-magic-items.json` should show 757.

- [ ] **Step 3: Fetch Classes from open5e API and save as JSON (24 entries, 12 pages)**

```bash
rm -f /tmp/classes_all.json
for page in $(seq 1 12); do
  curl -s "https://api.open5e.com/v2/classes/?document__key__in=srd-2024&limit=2&page=$page" | jq '.results[]' >> /tmp/classes_all.json
done
jq -s '{_source: "open5e V2 API (srd-2024 document)", _fetched: "2026-07-07", results: .}' \
  /tmp/classes_all.json > src/main/resources/srd/srd-5.2-classes.json
rm /tmp/classes_all.json
```

Verify: `jq '.results | length' src/main/resources/srd/srd-5.2-classes.json` should show 24 (12 base classes + 12 subclasses).

- [ ] **Step 4: Fetch Species from open5e API and save as JSON**

```bash
curl -s "https://api.open5e.com/v2/species/?document__key__in=srd-2024&limit=100" | \
  jq '{_source: "open5e V2 API (srd-2024 document)", _fetched: "2026-07-07", results: .results}' \
  > src/main/resources/srd/srd-5.2-species.json
```

Verify: `jq '.results | length' src/main/resources/srd/srd-5.2-species.json` should show 9.

- [ ] **Step 5: Fetch Backgrounds from open5e API and save as JSON**

```bash
curl -s "https://api.open5e.com/v2/backgrounds/?document__key__in=srd-2024&limit=100" | \
  jq '{_source: "open5e V2 API (srd-2024 document)", _fetched: "2026-07-07", results: .results}' \
  > src/main/resources/srd/srd-5.2-backgrounds.json
```

Verify: `jq '.results | length' src/main/resources/srd/srd-5.2-backgrounds.json` should show 4.

- [ ] **Step 6: Fetch Feats from open5e API and save as JSON (17 entries, 4 pages)**

```bash
rm -f /tmp/feats_all.json
for page in $(seq 1 4); do
  curl -s "https://api.open5e.com/v2/feats/?document__key__in=srd-2024&limit=5&page=$page" | jq '.results[]' >> /tmp/feats_all.json
done
jq -s '{_source: "open5e V2 API (srd-2024 document)", _fetched: "2026-07-07", results: .}' \
  /tmp/feats_all.json > src/main/resources/srd/srd-5.2-feats.json
rm /tmp/feats_all.json
```

Verify: `jq '.results | length' src/main/resources/srd/srd-5.2-feats.json` should show 17.

- [ ] **Step 7: Commit seed files**

```bash
git add src/main/resources/srd/srd-5.2-rules.json \
        src/main/resources/srd/srd-5.2-magic-items.json \
        src/main/resources/srd/srd-5.2-classes.json \
        src/main/resources/srd/srd-5.2-species.json \
        src/main/resources/srd/srd-5.2-backgrounds.json \
        src/main/resources/srd/srd-5.2-feats.json
git commit -m "feat: add open5e API-fetched seed data for rules, magic items, classes, species, backgrounds, feats"
```

---

### Task 2: Create verbatim SRD 5.2 seed files (conditions + equipment)

**Files:**
- Create: `src/main/resources/srd/srd-5.2-conditions.json`
- Create: `src/main/resources/srd/srd-5.2-equipment.json`

**Background:** open5e's V2 API has ZERO conditions with `srd-2024` document (only A5E content). open5e V2 has no working `/v2/equipment/` endpoint for srd-2024. Per §2.3.8 provenance rule, both must be extracted verbatim from the official SRD 5.2 PDF (CC-BY-4.0). **The PDFs have already been provided and pre-converted to text for this task.**

**Data already available (no need to fetch again):**

| Content | PDF Source | Pre-extracted Text |
|---------|------------|-------------------|
| 15 Conditions | `/home/hendrik/Documents/Coding/DMHelper/rulesglossary.pdf` | `/tmp/rulesglossary.txt` (1075 lines) |
| Weapons, Armor, Tools, Gear | `/home/hendrik/Documents/Coding/DMHelper/equipment.pdf` | `/tmp/equipment.txt` (928 lines) |

The text files were extracted with `pdftotext -layout`. All conditions and equipment descriptions come verbatim from these sources — fully compliant with §2.3.8.

**Quick reference — where each condition is in rulesglossary.txt:**

| Condition | Lines |
|-----------|-------|
| Blinded | 87-90 |
| Charmed | 197-202 |
| Deafened | 366-370 |
| Exhaustion | 415-427 |
| Frightened | 479-486 |
| Grappled | 432-440 |
| Incapacitated | 565-573 |
| Invisible | 577-585 |
| Paralyzed | 691-703 |
| Petrified | 723-741 |
| Poisoned | 691-695 |
| Prone | 710-718 |
| Restrained | 771-778 |
| Stunned | 906-913 |
| Unconscious | 1035-1049 |

**Quick reference — where each table is in equipment.txt:**

| Table | Lines |
|-------|-------|
| Simple Melee Weapons | 122-132 |
| Simple Ranged Weapons | 133-138 |
| Martial Melee Weapons | 139-157 |
| Martial Ranged Weapons | 158-167 |
| Armor (all types + Shield) | 196-214 |
| Artisan's Tools | 256-325 |
| Other Tools (Disguise, Forgery, Gaming, Herbalism, Musical, Navigator, Poison, Thieves) | 327-358 |
| Adventuring Gear table | 361-403 |
| Ammunition table | 409-418 |
| Adventuring Gear descriptions | 419-500+ |

**Weapons table column format:** `Name | Damage | Properties | Mastery | Weight | Cost` — parse each column carefully; the `Properties` column contains comma-separated tags like "Light", "Finesse, Light, Thrown (Range 20/60)", "Two-Handed". The `damage` field uses the format `XdY Type` (e.g. "1d4 Bludgeoning").

- [ ] **Step 1: Create conditions seed file**

Read `/tmp/rulesglossary.txt` at the line ranges above. Create `src/main/resources/srd/srd-5.2-conditions.json` as a provenance-wrapped object per §2.3.8:

```json
{
  "_source": "SRD 5.2 PDF (rulesglossary.pdf) — verbatim transcription",
  "_fetched": "2026-07-07",
  "results": [
    {
      "sourceKey": "blinded",
      "name": "Blinded",
      "description": "While you have the Blinded condition..."
    }
  ]
}
```

Schema for each condition entry: `sourceKey` (kebab-case), `name` (title case), `description` (verbatim text with `\n\n` between effects). 15 conditions total. Extract each from the pre-converted text — never fill from memory per §2.3.8.

- [ ] **Step 2: Create equipment seed file**

Read `/tmp/equipment.txt` at the line ranges above. Create `src/main/resources/srd/srd-5.2-equipment.json` as a provenance-wrapped object per §2.3.8:

```json
{
  "_source": "SRD 5.2 PDF (equipment.pdf) — verbatim transcription",
  "_fetched": "2026-07-07",
  "results": [
    {
      "sourceKey": "club",
      "name": "Club",
      "category": "WEAPON",
      "cost": "1 SP",
      "weight": "2 lb.",
      "properties": "{\"damage\":\"1d4 Bludgeoning\",\"properties\":[\"Light\"]}",
      "description": ""
    }
  ]
}
```

Schema for each entry: `sourceKey` (kebab-case), `name`, `category` (one of `WEAPON`, `ARMOR`, `GEAR`, `TOOL`), `cost`, `weight`, `properties` (JSON string — weapons: `{"damage":"1d4 Bludgeoning","properties":["Light","Finesse"]}`; armor: `{"ac":"11 + Dex modifier"}`; gear/tool: `{}`), `description` (verbatim text). Extract everything verbatim from the pre-converted text — per §2.3.8.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/srd/srd-5.2-conditions.json src/main/resources/srd/srd-5.2-equipment.json
git commit -m "feat: add verbatim SRD 5.2 seed data for conditions and equipment"
```

---

### Task 3: Create Condition entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Condition.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/ConditionRepository.java`

- [ ] **Step 1: Write Condition entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Condition.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "srd_condition", indexes = {
    @Index(name = "idx_condition_name", columnList = "name"),
})
public class Condition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

- [ ] **Step 2: Write ConditionRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/ConditionRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, UUID>,
        JpaSpecificationExecutor<Condition> {

    List<Condition> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Condition.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/ConditionRepository.java
git commit -m "feat: add Condition entity and repository"
```

---

### Task 4: Create RuleSection entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/RuleSection.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/RuleSectionRepository.java`

- [ ] **Step 1: Write RuleSection entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/RuleSection.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "rule_section", indexes = {
    @Index(name = "idx_rule_name", columnList = "name"),
    @Index(name = "idx_rule_ruleset", columnList = "ruleset"),
})
public class RuleSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(length = 100)
    private String parentKey;

    private int sortOrder;

    @Column(length = 100)
    private String ruleset;

    private int initialHeaderLevel;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParentKey() { return parentKey; }
    public void setParentKey(String parentKey) { this.parentKey = parentKey; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getRuleset() { return ruleset; }
    public void setRuleset(String ruleset) { this.ruleset = ruleset; }
    public int getInitialHeaderLevel() { return initialHeaderLevel; }
    public void setInitialHeaderLevel(int initialHeaderLevel) { this.initialHeaderLevel = initialHeaderLevel; }
}
```

- [ ] **Step 2: Write RuleSectionRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/RuleSectionRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleSectionRepository extends JpaRepository<RuleSection, UUID>,
        JpaSpecificationExecutor<RuleSection> {

    List<RuleSection> findByRulesetOrderBySortOrderAsc(String ruleset);

    List<RuleSection> findAllByOrderBySortOrderAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/RuleSection.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/RuleSectionRepository.java
git commit -m "feat: add RuleSection entity and repository"
```

---

### Task 5: Create EquipmentItem entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/EquipmentItem.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/EquipmentItemRepository.java`

- [ ] **Step 1: Write EquipmentItem entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/EquipmentItem.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "equipment_item", indexes = {
    @Index(name = "idx_equip_name", columnList = "name"),
    @Index(name = "idx_equip_category", columnList = "category"),
})
public class EquipmentItem {

    public enum Category { WEAPON, ARMOR, GEAR, TOOL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Category category;

    @Column(length = 50)
    private String cost;

    @Column(length = 50)
    private String weight;

    @Column(columnDefinition = "CLOB")
    private String properties;

    @Column(columnDefinition = "CLOB")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }
    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }
    public String getProperties() { return properties; }
    public void setProperties(String properties) { this.properties = properties; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Human-readable rendering of the {@code properties} JSON for display in the
     * equipment table, e.g. "1d4 Bludgeoning · Light, Finesse" or "AC 11 + Dex modifier".
     * Returns an empty string for gear/tools with no structured properties (`{}`).
     */
    @Transient
    public String getPropertiesDisplay() {
        if (properties == null || properties.isBlank()) return "";
        try {
            tools.jackson.databind.JsonNode node = PROPS_MAPPER.readTree(properties);
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (node.has("damage")) parts.add(node.get("damage").asText());
            if (node.has("ac")) parts.add("AC " + node.get("ac").asText());
            tools.jackson.databind.JsonNode tags = node.get("properties");
            if (tags != null && tags.isArray() && !tags.isEmpty()) {
                java.util.List<String> tagList = new java.util.ArrayList<>();
                for (tools.jackson.databind.JsonNode t : tags) tagList.add(t.asText());
                parts.add(String.join(", ", tagList));
            }
            return String.join(" · ", parts);
        } catch (Exception e) {
            return "";
        }
    }

    private static final tools.jackson.databind.ObjectMapper PROPS_MAPPER =
            new tools.jackson.databind.ObjectMapper();
}
```

- [ ] **Step 2: Write EquipmentItemRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/EquipmentItemRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, UUID>,
        JpaSpecificationExecutor<EquipmentItem> {

    List<EquipmentItem> findAllByOrderByNameAsc();

    List<EquipmentItem> findByCategoryOrderByNameAsc(EquipmentItem.Category category);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/EquipmentItem.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/EquipmentItemRepository.java
git commit -m "feat: add EquipmentItem entity and repository"
```

---

### Task 6: Create MagicItem entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/MagicItem.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/MagicItemRepository.java`

- [ ] **Step 1: Write MagicItem entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/MagicItem.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "magic_item", indexes = {
    @Index(name = "idx_magic_name", columnList = "name"),
    @Index(name = "idx_magic_rarity", columnList = "rarity"),
    @Index(name = "idx_magic_category", columnList = "category"),
})
public class MagicItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 50)
    private String rarity;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String type;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(length = 200)
    private String weight;

    @Column(length = 50)
    private String cost;

    private boolean requiresAttunement;

    @Column(columnDefinition = "CLOB")
    private String attunementDetail;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }
    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }
    public boolean isRequiresAttunement() { return requiresAttunement; }
    public void setRequiresAttunement(boolean requiresAttunement) { this.requiresAttunement = requiresAttunement; }
    public String getAttunementDetail() { return attunementDetail; }
    public void setAttunementDetail(String attunementDetail) { this.attunementDetail = attunementDetail; }
}
```

- [ ] **Step 2: Write MagicItemRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/MagicItemRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MagicItemRepository extends JpaRepository<MagicItem, UUID>,
        JpaSpecificationExecutor<MagicItem> {

    List<MagicItem> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/MagicItem.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/MagicItemRepository.java
git commit -m "feat: add MagicItem entity and repository"
```

---

### Task 7: Create CharacterClass entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClass.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClassRepository.java`

- [ ] **Step 1: Write CharacterClass entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClass.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "character_class", indexes = {
    @Index(name = "idx_class_name", columnList = "name"),
    @Index(name = "idx_class_subclassof", columnList = "subclassOf"),
})
public class CharacterClass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 10)
    private String hitDie;

    @Column(columnDefinition = "CLOB")
    private String savingThrows;

    @Column(columnDefinition = "CLOB")
    private String features;

    @Column(columnDefinition = "CLOB")
    private String spellcasting;

    @Column(length = 100)
    private String subclassOf;

    @Column(columnDefinition = "CLOB")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHitDie() { return hitDie; }
    public void setHitDie(String hitDie) { this.hitDie = hitDie; }
    public String getSavingThrows() { return savingThrows; }
    public void setSavingThrows(String savingThrows) { this.savingThrows = savingThrows; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public String getSpellcasting() { return spellcasting; }
    public void setSpellcasting(String spellcasting) { this.spellcasting = spellcasting; }
    public String getSubclassOf() { return subclassOf; }
    public void setSubclassOf(String subclassOf) { this.subclassOf = subclassOf; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

- [ ] **Step 2: Write CharacterClassRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClassRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CharacterClassRepository extends JpaRepository<CharacterClass, UUID>,
        JpaSpecificationExecutor<CharacterClass> {

    List<CharacterClass> findBySubclassOfIsNullOrderByNameAsc();

    List<CharacterClass> findBySubclassOfOrderByNameAsc(String subclassOf);

    List<CharacterClass> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClass.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClassRepository.java
git commit -m "feat: add CharacterClass entity and repository"
```

---

### Task 8: Create Species entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Species.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpeciesRepository.java`

- [ ] **Step 1: Write Species entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Species.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "species", indexes = {
    @Index(name = "idx_species_name", columnList = "name"),
})
public class Species {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 50)
    private String size;

    @Column(length = 50)
    private String speed;

    @Column(columnDefinition = "CLOB")
    private String traits;

    @Column(columnDefinition = "CLOB")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }
    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

- [ ] **Step 2: Write SpeciesRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpeciesRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpeciesRepository extends JpaRepository<Species, UUID>,
        JpaSpecificationExecutor<Species> {

    List<Species> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Species.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpeciesRepository.java
git commit -m "feat: add Species entity and repository"
```

---

### Task 9: Create Background entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Background.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/BackgroundRepository.java`

- [ ] **Step 1: Write Background entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Background.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "background", indexes = {
    @Index(name = "idx_bg_name", columnList = "name"),
})
public class Background {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String abilityScores;

    @Column(length = 100)
    private String featRef;

    @Column(columnDefinition = "CLOB")
    private String skills;

    @Column(columnDefinition = "CLOB")
    private String tools;

    @Column(columnDefinition = "CLOB")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbilityScores() { return abilityScores; }
    public void setAbilityScores(String abilityScores) { this.abilityScores = abilityScores; }
    public String getFeatRef() { return featRef; }
    public void setFeatRef(String featRef) { this.featRef = featRef; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

- [ ] **Step 2: Write BackgroundRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/BackgroundRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BackgroundRepository extends JpaRepository<Background, UUID>,
        JpaSpecificationExecutor<Background> {

    List<Background> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Background.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/BackgroundRepository.java
git commit -m "feat: add Background entity and repository"
```

---

### Task 10: Create Feat entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Feat.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/FeatRepository.java`

- [ ] **Step 1: Write Feat entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Feat.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "feat", indexes = {
    @Index(name = "idx_feat_name", columnList = "name"),
    @Index(name = "idx_feat_category", columnList = "category"),
})
public class Feat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(length = 500)
    private String prerequisite;

    @Column(columnDefinition = "CLOB")
    private String benefit;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPrerequisite() { return prerequisite; }
    public void setPrerequisite(String prerequisite) { this.prerequisite = prerequisite; }
    public String getBenefit() { return benefit; }
    public void setBenefit(String benefit) { this.benefit = benefit; }
}
```

- [ ] **Step 2: Write FeatRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/FeatRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeatRepository extends JpaRepository<Feat, UUID>,
        JpaSpecificationExecutor<Feat> {

    List<Feat> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/data/Feat.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/FeatRepository.java
git commit -m "feat: add Feat entity and repository"
```

---

### Task 11: Create read-only search services for all compendium types

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/ConditionService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/RuleSectionService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/EquipmentItemService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/MagicItemService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpeciesService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/BackgroundService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/FeatService.java`

Following the `SpellService` pattern exactly.

- [ ] **Step 1: Write ConditionService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConditionService {

    private final ConditionRepository repository;

    public ConditionService(ConditionRepository repository) {
        this.repository = repository;
    }

    public List<Condition> search(String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Condition> findAll() {
        return repository.findAllByOrderByNameAsc();
    }
}
```

- [ ] **Step 2: Write RuleSectionService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RuleSectionService {

    private final RuleSectionRepository repository;

    public RuleSectionService(RuleSectionRepository repository) {
        this.repository = repository;
    }

    public List<RuleSection> search(String search, String ruleset) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (ruleset != null && !ruleset.isBlank()) {
                predicates.add(cb.equal(root.get("ruleset"), ruleset));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("ruleset")), cb.asc(root.get("sortOrder")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<RuleSection> findAll() {
        return repository.findAllByOrderBySortOrderAsc();
    }
}
```

- [ ] **Step 3: Write EquipmentItemService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EquipmentItemService {

    private final EquipmentItemRepository repository;

    public EquipmentItemService(EquipmentItemRepository repository) {
        this.repository = repository;
    }

    public List<EquipmentItem> search(String search, EquipmentItem.Category category) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("category")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<EquipmentItem> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public List<EquipmentItem> findByCategory(EquipmentItem.Category category) {
        return repository.findByCategoryOrderByNameAsc(category);
    }
}
```

- [ ] **Step 4: Write MagicItemService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MagicItemService {

    private final MagicItemRepository repository;

    public MagicItemService(MagicItemRepository repository) {
        this.repository = repository;
    }

    public List<MagicItem> search(String search, String rarity, String category) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (rarity != null && !rarity.isBlank()) {
                predicates.add(cb.equal(root.get("rarity"), rarity));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<MagicItem> findAll() {
        return repository.findAllByOrderByNameAsc();
    }
}
```

- [ ] **Step 5: Write CharacterClassService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CharacterClassService {

    private final CharacterClassRepository repository;

    public CharacterClassService(CharacterClassRepository repository) {
        this.repository = repository;
    }

    public List<CharacterClass> search(String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("subclassOf")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<CharacterClass> findBaseClasses() {
        return repository.findBySubclassOfIsNullOrderByNameAsc();
    }

    public List<CharacterClass> findAll() {
        return repository.findAllByOrderByNameAsc();
    }
}
```

- [ ] **Step 6: Write SpeciesService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SpeciesService {

    private final SpeciesRepository repository;

    public SpeciesService(SpeciesRepository repository) {
        this.repository = repository;
    }

    public List<Species> search(String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Species> findAll() {
        return repository.findAllByOrderByNameAsc();
    }
}
```

- [ ] **Step 7: Write BackgroundService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BackgroundService {

    private final BackgroundRepository repository;

    public BackgroundService(BackgroundRepository repository) {
        this.repository = repository;
    }

    public List<Background> search(String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Background> findAll() {
        return repository.findAllByOrderByNameAsc();
    }
}
```

- [ ] **Step 8: Write FeatService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FeatService {

    private final FeatRepository repository;

    public FeatService(FeatRepository repository) {
        this.repository = repository;
    }

    public List<Feat> search(String search, String category) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("category")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Feat> findAll() {
        return repository.findAllByOrderByNameAsc();
    }
}
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/ConditionService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/RuleSectionService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/EquipmentItemService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/MagicItemService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpeciesService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/BackgroundService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/FeatService.java
git commit -m "feat: add read-only search services for all compendium types"
```

---

### Task 12: Create seed services for all compendium types

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/ConditionSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/RuleSectionSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/EquipmentItemSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/MagicItemSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpeciesSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/BackgroundSeedService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/FeatSeedService.java`

Following the `SpellSeedService` pattern exactly.

- [ ] **Step 1: Write ConditionSeedService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class ConditionSeedService {

    private static final Logger log = LoggerFactory.getLogger(ConditionSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-conditions.json";

    private final ConditionRepository repository;
    private final ObjectMapper objectMapper;

    public ConditionSeedService(ConditionRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void seedIfEmpty() {
        if (repository.count() > 0) {
            log.info("Condition data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 condition data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<ConditionEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<ConditionEntry>>() {});
                int count = 0;
                for (ConditionEntry entry : entries) {
                    Condition c = new Condition();
                    c.setSourceKey(entry.sourceKey());
                    c.setName(entry.name());
                    c.setDescription(entry.description());
                    repository.save(c);
                    count++;
                }
                log.info("Seeded {} conditions", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed condition data", e);
            throw new RuntimeException("Failed to seed SRD condition data", e);
        }
    }

    public record ConditionEntry(String sourceKey, String name, String description) {}
}
```

- [ ] **Step 2: Write RuleSectionSeedService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
            log.info("Rule data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 rules data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<RuleEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<RuleEntry>>() {});
                int count = 0;
                for (RuleEntry entry : entries) {
                    RuleSection r = new RuleSection();
                    r.setSourceKey(entry.key());
                    r.setName(entry.name());
                    r.setDescription(entry.desc());
                    r.setRuleset(entry.ruleset());
                    r.setSortOrder(entry.index());
                    r.setInitialHeaderLevel(entry.initialHeaderLevel());
                    r.setParentKey(entry.parent());
                    repository.save(r);
                    count++;
                }
                log.info("Seeded {} rule sections", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed rule data", e);
            throw new RuntimeException("Failed to seed SRD rule data", e);
        }
    }

    public record RuleEntry(
        String key, String name, String desc, String ruleset,
        int index, int initialHeaderLevel, String parent
    ) {}
}
```

- [ ] **Step 3: Write EquipmentItemSeedService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
            log.info("Equipment data already seeded -- skipping");
            return;
        }
        log.info("Seeding SRD 5.2 equipment data...");
        try {
            ClassPathResource resource = new ClassPathResource(DATA_PATH);
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                String source = root.path("_source").asText("");
                String fetched = root.path("_fetched").asText("");
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<EquipmentEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<EquipmentEntry>>() {});
                int count = 0;
                for (EquipmentEntry entry : entries) {
                    EquipmentItem e = new EquipmentItem();
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
            log.error("Failed to seed equipment data", e);
            throw new RuntimeException("Failed to seed SRD equipment data", e);
        }
    }

    public record EquipmentEntry(
        String sourceKey, String name, String category,
        String cost, String weight, String properties, String description
    ) {}
}
```

- [ ] **Step 4: Write MagicItemSeedService**

```java
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
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<MagicItemEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<MagicItemEntry>>() {});
                int count = 0;
                for (MagicItemEntry entry : entries) {
                    MagicItem m = new MagicItem();
                    m.setSourceKey(entry.key());
                    m.setName(entry.name());
                    m.setDescription(entry.desc());
                    m.setCategory(entry.category().get("name"));
                    String rarityName = entry.rarity().get("name");
                    m.setRarity(rarityName);
                    String type = null;
                    if (entry.weapon() != null) type = entry.weapon().get("name");
                    else if (entry.armor() != null) type = entry.armor().get("name");
                    m.setType(type);
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
        java.util.Map<String, String> category,
        java.util.Map<String, Object> rarity,
        java.util.Map<String, String> weapon,
        java.util.Map<String, String> armor,
        String weight, String cost,
        boolean requires_attunement, String attunement_detail
    ) {}
}
```

- [ ] **Step 5: Write CharacterClassSeedService**

```java
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

@Component
public class CharacterClassSeedService {

    private static final Logger log = LoggerFactory.getLogger(CharacterClassSeedService.class);
    private static final String DATA_PATH = "srd/srd-5.2-classes.json";

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
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<ClassEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<ClassEntry>>() {});
                int count = 0;
                for (ClassEntry entry : entries) {
                    CharacterClass c = new CharacterClass();
                    c.setSourceKey(entry.key());
                    c.setName(entry.name());
                    c.setHitDie(entry.hit_dice());
                    c.setDescription(entry.desc());
                    try { c.setSavingThrows(objectMapper.writeValueAsString(entry.saving_throws())); } catch (Exception ignored) {}
                    try { c.setFeatures(objectMapper.writeValueAsString(entry.features())); } catch (Exception ignored) {}
                    try { c.setSpellcasting(objectMapper.writeValueAsString(entry.spellcasting())); } catch (Exception ignored) {}
                    if (entry.subclass_of() != null) {
                        c.setSubclassOf(entry.subclass_of());
                    }
                    repository.save(c);
                    count++;
                }
                log.info("Seeded {} character classes", count);
            }
        } catch (Exception e) {
            log.error("Failed to seed class data", e);
            throw new RuntimeException("Failed to seed SRD class data", e);
        }
    }

    public record ClassEntry(
        String key, String name, String desc, String hit_dice,
        Object saving_throws, Object features, Object spellcasting,
        String subclass_of
    ) {}
}
```

- [ ] **Step 6: Write SpeciesSeedService**

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

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
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<SpeciesEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<SpeciesEntry>>() {});
                int count = 0;
                for (SpeciesEntry entry : entries) {
                    Species s = new Species();
                    s.setSourceKey(entry.key());
                    s.setName(entry.name());
                    s.setDescription(entry.desc());
                    try { s.setTraits(objectMapper.writeValueAsString(entry.traits())); } catch (Exception ignored) {}
                    if (entry.traits() instanceof List<?> traits) {
                        for (Object t : traits) {
                            if (t instanceof java.util.Map<?,?> m) {
                                String tname = (String) m.get("name");
                                String type = (String) m.get("type");
                                if ("SIZE".equals(type)) s.setSize((String) m.get("desc"));
                                if ("SPEED".equals(type)) s.setSpeed((String) m.get("desc"));
                            }
                        }
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
        String key, String name, String desc, Object traits
    ) {}
}
```

- [ ] **Step 7: Write BackgroundSeedService**

```java
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
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<BackgroundEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<BackgroundEntry>>() {});
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
```

- [ ] **Step 8: Write FeatSeedService**

```java
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
                log.info("Seeding from: {} (fetched {})", source, fetched);
                List<FeatEntry> entries = objectMapper.convertValue(
                        root.get("results"), new TypeReference<List<FeatEntry>>() {});
                int count = 0;
                for (FeatEntry entry : entries) {
                    Feat f = new Feat();
                    f.setSourceKey(entry.key());
                    f.setName(entry.name());
                    f.setBenefit(entry.desc());
                    f.setPrerequisite(entry.prerequisite());
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

    public record FeatEntry(String key, String name, String desc, String prerequisite) {}
}
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/ConditionSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/RuleSectionSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/EquipmentItemSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/MagicItemSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/SpeciesSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/BackgroundSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/FeatSeedService.java
git commit -m "feat: add seed services for all compendium types"
```

---

### Task 13: Wire seed services into DmhelperApplication

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java:1-32`

- [ ] **Step 1: Inject all seed services and call seedIfEmpty()**

Edit `DmhelperApplication.java` to add all 8 new seed services. Replace the entire constructor and seed() method:

```java
package dev.hendrikhoemberg.dmhelper;

import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@SpringBootApplication
public class DmhelperApplication {

    private final SrdSeedService srdSeedService;
    private final SpellSeedService spellSeedService;
    private final ConditionSeedService conditionSeedService;
    private final RuleSectionSeedService ruleSectionSeedService;
    private final EquipmentItemSeedService equipmentItemSeedService;
    private final MagicItemSeedService magicItemSeedService;
    private final CharacterClassSeedService characterClassSeedService;
    private final SpeciesSeedService speciesSeedService;
    private final BackgroundSeedService backgroundSeedService;
    private final FeatSeedService featSeedService;

    public DmhelperApplication(SrdSeedService srdSeedService,
                               SpellSeedService spellSeedService,
                               ConditionSeedService conditionSeedService,
                               RuleSectionSeedService ruleSectionSeedService,
                               EquipmentItemSeedService equipmentItemSeedService,
                               MagicItemSeedService magicItemSeedService,
                               CharacterClassSeedService characterClassSeedService,
                               SpeciesSeedService speciesSeedService,
                               BackgroundSeedService backgroundSeedService,
                               FeatSeedService featSeedService) {
        this.srdSeedService = srdSeedService;
        this.spellSeedService = spellSeedService;
        this.conditionSeedService = conditionSeedService;
        this.ruleSectionSeedService = ruleSectionSeedService;
        this.equipmentItemSeedService = equipmentItemSeedService;
        this.magicItemSeedService = magicItemSeedService;
        this.characterClassSeedService = characterClassSeedService;
        this.speciesSeedService = speciesSeedService;
        this.backgroundSeedService = backgroundSeedService;
        this.featSeedService = featSeedService;
    }

    public static void main(String[] args) {
        SpringApplication.run(DmhelperApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void seed() {
        srdSeedService.seedIfEmpty();
        spellSeedService.seedIfEmpty();
        conditionSeedService.seedIfEmpty();
        ruleSectionSeedService.seedIfEmpty();
        equipmentItemSeedService.seedIfEmpty();
        magicItemSeedService.seedIfEmpty();
        characterClassSeedService.seedIfEmpty();
        speciesSeedService.seedIfEmpty();
        backgroundSeedService.seedIfEmpty();
        featSeedService.seedIfEmpty();
    }
}
```

- [ ] **Step 2: Verify app starts and seeds correctly**

```bash
mvn clean spring-boot:run
```

Expected: App starts, logs show 8+ seeding lines (or "already seeded -- skipping"), no exceptions.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/DmhelperApplication.java
git commit -m "feat: wire all compendium seed services into application bootstrap"
```

---

### Task 14: Write tests for seed services

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CompendiumSeedServiceTest.java`

- [ ] **Step 1: Write integration test for all seed services**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CompendiumSeedServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({SrdSeedService.class, SpellSeedService.class,
         ConditionSeedService.class, RuleSectionSeedService.class,
         EquipmentItemSeedService.class, MagicItemSeedService.class,
         CharacterClassSeedService.class, SpeciesSeedService.class,
         BackgroundSeedService.class, FeatSeedService.class})
class CompendiumSeedServiceTest {

    @Autowired private ConditionRepository conditionRepository;
    @Autowired private RuleSectionRepository ruleSectionRepository;
    @Autowired private EquipmentItemRepository equipmentItemRepository;
    @Autowired private MagicItemRepository magicItemRepository;
    @Autowired private CharacterClassRepository characterClassRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private BackgroundRepository backgroundRepository;
    @Autowired private FeatRepository featRepository;

    @Autowired private ConditionSeedService conditionSeedService;
    @Autowired private RuleSectionSeedService ruleSectionSeedService;
    @Autowired private EquipmentItemSeedService equipmentSeedService;
    @Autowired private MagicItemSeedService magicItemSeedService;
    @Autowired private CharacterClassSeedService classSeedService;
    @Autowired private SpeciesSeedService speciesSeedService;
    @Autowired private BackgroundSeedService backgroundSeedService;
    @Autowired private FeatSeedService featSeedService;

    @BeforeEach
    void seedAll() {
        conditionSeedService.seedIfEmpty();
        ruleSectionSeedService.seedIfEmpty();
        equipmentSeedService.seedIfEmpty();
        magicItemSeedService.seedIfEmpty();
        classSeedService.seedIfEmpty();
        speciesSeedService.seedIfEmpty();
        backgroundSeedService.seedIfEmpty();
        featSeedService.seedIfEmpty();
    }

    @Test
    void shouldSeedConditions() {
        assertThat(conditionRepository.count()).isGreaterThan(0);
        Condition grappled = conditionRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase("Grappled"))
                .findFirst().orElse(null);
        if (grappled != null) {
            assertThat(grappled.getDescription()).isNotBlank();
        }
    }

    @Test
    void shouldSeedRules() {
        assertThat(ruleSectionRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedEquipment() {
        assertThat(equipmentItemRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedMagicItems() {
        assertThat(magicItemRepository.count()).isGreaterThan(100);
    }

    @Test
    void shouldSeedClasses() {
        assertThat(characterClassRepository.count()).isGreaterThan(0);
        assertThat(characterClassRepository.findBySubclassOfIsNullOrderByNameAsc()).isNotEmpty();
    }

    @Test
    void shouldSeedSpecies() {
        assertThat(speciesRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedBackgrounds() {
        assertThat(backgroundRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedFeats() {
        assertThat(featRepository.count()).isGreaterThan(0);
    }

    @Test
    void allEntriesShouldHaveSourceKeys() {
        conditionRepository.findAll().forEach(c -> assertThat(c.getSourceKey()).isNotBlank());
        ruleSectionRepository.findAll().forEach(r -> assertThat(r.getSourceKey()).isNotBlank());
        equipmentItemRepository.findAll().forEach(e -> assertThat(e.getSourceKey()).isNotBlank());
        magicItemRepository.findAll().forEach(m -> assertThat(m.getSourceKey()).isNotBlank());
        characterClassRepository.findAll().forEach(c -> assertThat(c.getSourceKey()).isNotBlank());
        speciesRepository.findAll().forEach(s -> assertThat(s.getSourceKey()).isNotBlank());
        backgroundRepository.findAll().forEach(b -> assertThat(b.getSourceKey()).isNotBlank());
        featRepository.findAll().forEach(f -> assertThat(f.getSourceKey()).isNotBlank());
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -Dtest=CompendiumSeedServiceTest
```

Expected: All tests pass. Seed counts match JSON file sizes.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CompendiumSeedServiceTest.java
git commit -m "test: add integration tests for compendium seed services"
```

---

### Task 15: Extend LibraryController with compendium tab routes

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java:1-231`

- [ ] **Step 1: Inject new services and add htmx search routes**

Replace `LibraryController.java` with the extended version. Add imports at top, inject all 8 new services in the constructor, and add 8 new `@GetMapping` methods:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.library.service.*;
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
    private final ConditionService conditionService;
    private final RuleSectionService ruleSectionService;
    private final EquipmentItemService equipmentItemService;
    private final MagicItemService magicItemService;
    private final CharacterClassService characterClassService;
    private final SpeciesService speciesService;
    private final BackgroundService backgroundService;
    private final FeatService featService;
    private final ObjectMapper objectMapper;

    public LibraryController(StatBlockService service,
                             SpellService spellService,
                             ConditionService conditionService,
                             RuleSectionService ruleSectionService,
                             EquipmentItemService equipmentItemService,
                             MagicItemService magicItemService,
                             CharacterClassService characterClassService,
                             SpeciesService speciesService,
                             BackgroundService backgroundService,
                             FeatService featService) {
        this.service = service;
        this.spellService = spellService;
        this.conditionService = conditionService;
        this.ruleSectionService = ruleSectionService;
        this.equipmentItemService = equipmentItemService;
        this.magicItemService = magicItemService;
        this.characterClassService = characterClassService;
        this.speciesService = speciesService;
        this.backgroundService = backgroundService;
        this.featService = featService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public String list() {
        return "library/list";
    }

    // ... existing StatBlock routes (GET/POST/PUT/DELETE /statblocks/*) remain unchanged ...
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

    // ... (keep all existing statblock and spell routes exactly as they are) ...

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    // ------- Compendium tab routes (read-only) -------

    @GetMapping("/conditions")
    public String searchConditions(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("conditions", conditionService.search(search));
        return "library/_condition-card :: condition-card-list";
    }

    @GetMapping("/rules")
    public String searchRules(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String ruleset,
                              Model model) {
        model.addAttribute("rules", ruleSectionService.search(search, ruleset));
        return "library/_rule-card :: rule-card-list";
    }

    @GetMapping("/equipment")
    public String searchEquipment(@RequestParam(required = false) String search,
                                  @RequestParam(required = false) String category,
                                  Model model) {
        EquipmentItem.Category cat = null;
        if (category != null && !category.isBlank()) {
            cat = EquipmentItem.Category.valueOf(category);
        }
        model.addAttribute("equipment", equipmentItemService.search(search, cat));
        return "library/_equipment-card :: equipment-card-list";
    }

    @GetMapping("/magic-items")
    public String searchMagicItems(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String rarity,
                                   @RequestParam(required = false) String category,
                                   Model model) {
        model.addAttribute("magicItems", magicItemService.search(search, rarity, category));
        return "library/_magic-item-card :: magic-item-card-list";
    }

    @GetMapping("/classes")
    public String searchClasses(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("classes", characterClassService.search(search));
        return "library/_class-card :: class-card-list";
    }

    @GetMapping("/species")
    public String searchSpecies(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("speciesList", speciesService.search(search));
        return "library/_species-card :: species-card-list";
    }

    @GetMapping("/backgrounds")
    public String searchBackgrounds(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("backgrounds", backgroundService.search(search));
        return "library/_background-card :: background-card-list";
    }

    @GetMapping("/feats")
    public String searchFeats(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String category,
                              Model model) {
        model.addAttribute("feats", featService.search(search, category));
        return "library/_feat-card :: feat-card-list";
    }

    // (keep existing private enrichStatBlock and parseJsonArray methods unchanged)
}
```

**Important:** Do NOT delete the existing StatBlock and Spell routes (lines 30-231 of the original). Add the 8 new compendium routes AFTER the `about()` method (after line 200). The existing constructor must gain the 8 new service parameters.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java
git commit -m "feat: add htmx routes for all compendium tab searches"
```

---

### Task 15b: Add class detail route to LibraryController

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassService.java`

- [ ] **Step 1: Add `findBySourceKey` lookup**

`sourceKey` is not the primary key (the PK is `UUID`), so add a derived query method to the repository and expose it through the service.

Add to `CharacterClassRepository.java`:

```java
Optional<CharacterClass> findBySourceKey(String sourceKey);
List<CharacterClass> findBySubclassOfOrderByNameAsc(String subclassOf);
```

Add to `CharacterClassService.java`:

```java
public Optional<CharacterClass> findBySourceKey(String sourceKey) {
    return repository.findBySourceKey(sourceKey);
}
```

- [ ] **Step 2: Add class detail route to LibraryController**

Add this `@GetMapping` method after the existing `/classes` search route:

```java
@GetMapping("/classes/{sourceKey}")
public String classDetail(@PathVariable String sourceKey, Model model) {
    CharacterClass cls = characterClassService.findBySourceKey(sourceKey)
            .orElseThrow(() -> new RuntimeException("Class not found: " + sourceKey));
    model.addAttribute("classDetail", cls);

    // Build levelFeatures map from features JSON
    List<Map<String, Object>> features = parseJsonList(cls.getFeatures());
    Map<Integer, List<Map<String, Object>>> levelFeatures = new TreeMap<>();
    if (features != null) {
        for (Map<String, Object> f : features) {
            int level = ((Number) f.getOrDefault("level", 1)).intValue();
            levelFeatures.computeIfAbsent(level, k -> new ArrayList<>()).add(f);
        }
    }
    model.addAttribute("levelFeatures", levelFeatures);

    // Parse saving throws into a readable comma-joined string
    model.addAttribute("savingThrows", formatSavingThrows(cls.getSavingThrows()));

    // Parse spellcasting
    model.addAttribute("spellcasting", parseJsonObject(cls.getSpellcasting()));

    // If this is a subclass, load the base class
    if (cls.getSubclassOf() != null && !cls.getSubclassOf().isBlank()) {
        characterClassService.findBySourceKey(cls.getSubclassOf())
                .ifPresent(base -> model.addAttribute("subclass", base));
    }

    // If this is a base class, load its subclasses
    List<CharacterClass> subclasses = characterClassService
            .findAll().stream()
            .filter(c -> cls.getSourceKey().equals(c.getSubclassOf()))
            .toList();
    model.addAttribute("subclasses", subclasses);

    return "library/class-detail";
}

private List<Map<String, Object>> parseJsonList(String json) {
    if (json == null || json.isBlank()) return null;
    try {
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
        return null;
    }
}

private Map<String, Object> parseJsonObject(String json) {
    if (json == null || json.isBlank()) return null;
    try {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
        return null;
    }
}

/**
 * Renders the saving-throws JSON as a readable string, e.g. "Strength, Constitution".
 * Handles both an array of strings and an array of {name/ability} objects; returns
 * null (so the template hides the section) for any other shape rather than leaking JSON.
 */
private String formatSavingThrows(String json) {
    if (json == null || json.isBlank()) return null;
    try {
        JsonNode node = objectMapper.readTree(json);
        List<String> parts = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) {
                if (n.isObject() && n.has("name")) parts.add(n.get("name").asText());
                else if (n.isObject() && n.has("ability")) parts.add(n.get("ability").asText());
                else if (n.isTextual()) parts.add(n.asText());
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    } catch (Exception e) {
        return null;
    }
}
```

This uses `tools.jackson.databind.JsonNode` — ensure the controller imports it (alongside the existing `ObjectMapper` and `TypeReference` imports) and `java.util.ArrayList`/`List` (already covered by the `java.util.*` import at the top of the controller).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/CharacterClassRepository.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassService.java
git commit -m "feat: add class detail page route with level progression table"
```

---

### Task 16: Extend LibraryApiController with extended SRD keys

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java:1-29`

- [ ] **Step 1: Add all compendium source keys to the SRD key catalog**

Replace `LibraryApiController.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryApiController {

    private final StatBlockService statBlockService;
    private final SpellService spellService;
    private final ConditionService conditionService;
    private final RuleSectionService ruleSectionService;
    private final EquipmentItemService equipmentItemService;
    private final MagicItemService magicItemService;
    private final CharacterClassService characterClassService;
    private final SpeciesService speciesService;
    private final BackgroundService backgroundService;
    private final FeatService featService;

    public LibraryApiController(StatBlockService statBlockService,
                                SpellService spellService,
                                ConditionService conditionService,
                                RuleSectionService ruleSectionService,
                                EquipmentItemService equipmentItemService,
                                MagicItemService magicItemService,
                                CharacterClassService characterClassService,
                                SpeciesService speciesService,
                                BackgroundService backgroundService,
                                FeatService featService) {
        this.statBlockService = statBlockService;
        this.spellService = spellService;
        this.conditionService = conditionService;
        this.ruleSectionService = ruleSectionService;
        this.equipmentItemService = equipmentItemService;
        this.magicItemService = magicItemService;
        this.characterClassService = characterClassService;
        this.speciesService = speciesService;
        this.backgroundService = backgroundService;
        this.featService = featService;
    }

    @GetMapping("/srd-keys")
    public List<String> srdKeys() {
        var statblocks = statBlockService.findAll().stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.SRD)
                .map(StatBlock::getSourceKey);
        var spells = spellService.findAll().stream().map(Spell::getSourceKey);
        var conditions = conditionService.findAll().stream().map(Condition::getSourceKey);
        var rules = ruleSectionService.findAll().stream().map(RuleSection::getSourceKey);
        var equipment = equipmentItemService.findAll().stream().map(EquipmentItem::getSourceKey);
        var magicItems = magicItemService.findAll().stream().map(MagicItem::getSourceKey);
        var classes = characterClassService.findAll().stream().map(CharacterClass::getSourceKey);
        var species = speciesService.findAll().stream().map(Species::getSourceKey);
        var backgrounds = backgroundService.findAll().stream().map(Background::getSourceKey);
        var feats = featService.findAll().stream().map(Feat::getSourceKey);
        return Stream.of(statblocks, spells, conditions, rules, equipment,
                        magicItems, classes, species, backgrounds, feats)
                .flatMap(s -> s)
                .sorted()
                .toList();
    }
}
```

- [ ] **Step 2: Update ApiControllerTest**

Modify `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java` — add `@MockitoBean` for all new services and update the test:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryApiController.class)
class LibraryApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService statBlockService;
    @MockitoBean private SpellService spellService;
    @MockitoBean private ConditionService conditionService;
    @MockitoBean private RuleSectionService ruleSectionService;
    @MockitoBean private EquipmentItemService equipmentItemService;
    @MockitoBean private MagicItemService magicItemService;
    @MockitoBean private CharacterClassService characterClassService;
    @MockitoBean private SpeciesService speciesService;
    @MockitoBean private BackgroundService backgroundService;
    @MockitoBean private FeatService featService;
    @MockitoBean private SrdSeedService srdSeedService;
    @MockitoBean private SpellSeedService spellSeedService;

    private StatBlock srd(String key) {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.SRD);
        sb.setSourceKey(key);
        return sb;
    }

    @Test
    void listsSortedSrdKeysFromAllSources() throws Exception {
        when(statBlockService.findAll()).thenReturn(List.of(srd("goblin")));
        when(spellService.findAll()).thenReturn(List.of());
        when(conditionService.findAll()).thenReturn(List.of());
        when(ruleSectionService.findAll()).thenReturn(List.of());
        when(equipmentItemService.findAll()).thenReturn(List.of());
        when(magicItemService.findAll()).thenReturn(List.of());
        when(characterClassService.findAll()).thenReturn(List.of());
        when(speciesService.findAll()).thenReturn(List.of());
        when(backgroundService.findAll()).thenReturn(List.of());
        when(featService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/library/srd-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("goblin"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
```

- [ ] **Step 3: Run tests**

```bash
mvn test -Dtest=LibraryApiControllerTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java
git commit -m "feat: extend SRD key catalog with all compendium types"
```

---

### Task 17: Update LibraryControllerTest for new compendium routes

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java:1-145`

- [ ] **Step 1: Add test cases for new compendium tab routes**

Add the following test methods to the existing `LibraryControllerTest` class, and add `@MockitoBean` declarations for all 8 new services:

```java
@MockitoBean private ConditionService conditionService;
@MockitoBean private RuleSectionService ruleSectionService;
@MockitoBean private EquipmentItemService equipmentItemService;
@MockitoBean private MagicItemService magicItemService;
@MockitoBean private CharacterClassService characterClassService;
@MockitoBean private SpeciesService speciesService;
@MockitoBean private BackgroundService backgroundService;
@MockitoBean private FeatService featService;

@Test
void shouldSearchConditions() throws Exception {
    when(conditionService.search(isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/conditions"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchRules() throws Exception {
    when(ruleSectionService.search(isNull(), isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/rules"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchEquipment() throws Exception {
    when(equipmentItemService.search(isNull(), isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/equipment"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchMagicItems() throws Exception {
    when(magicItemService.search(isNull(), isNull(), isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/magic-items"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchClasses() throws Exception {
    when(characterClassService.search(isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/classes"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchSpecies() throws Exception {
    when(speciesService.search(isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/species"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchBackgrounds() throws Exception {
    when(backgroundService.search(isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/backgrounds"))
            .andExpect(status().isOk());
}

@Test
void shouldSearchFeats() throws Exception {
    when(featService.search(isNull(), isNull())).thenReturn(List.of());
    mockMvc.perform(get("/library/feats"))
            .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -Dtest=LibraryControllerTest
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java
git commit -m "test: add controller tests for compendium tab routes"
```

---

### Task 18: Build Thymeleaf card fragment templates for all compendium types

**Files:**
- Create: `src/main/resources/templates/library/_condition-card.html`
- Create: `src/main/resources/templates/library/_rule-card.html`
- Create: `src/main/resources/templates/library/_equipment-card.html`
- Create: `src/main/resources/templates/library/_magic-item-card.html`
- Create: `src/main/resources/templates/library/_class-card.html`
- Create: `src/main/resources/templates/library/_species-card.html`
- Create: `src/main/resources/templates/library/_background-card.html`
- Create: `src/main/resources/templates/library/_feat-card.html`

- [ ] **Step 1: Create _condition-card.html**

```html
<th:block th:fragment="condition-card-list">
    <div class="card-grid" th:if="${conditions != null and !conditions.isEmpty()}">
        <div th:each="c : ${conditions}" class="card">
            <h3 class="card-title" th:text="${c.name}">Condition Name</h3>
            <p class="card-text" th:text="${c.description}">Description</p>
        </div>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 2: Create _rule-card.html**

Rule descriptions from open5e are raw Markdown — must be rendered server-side with commonmark before output:

```html
<th:block th:fragment="rule-card-list">
    <div class="card-grid" th:if="${rules != null and !rules.isEmpty()}">
        <div th:each="r : ${rules}" class="card">
            <h3 class="card-title" th:text="${r.name}">Rule Name</h3>
            <div class="card-text" th:utext="${@markdownUtil.toHtml(r.description)}">Description</div>
        </div>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 3: Create _equipment-card.html**

```html
<th:block th:fragment="equipment-card-list">
    <table class="equipment-table" th:if="${equipment != null and !equipment.isEmpty()}">
        <thead>
            <tr>
                <th>Name</th>
                <th>Category</th>
                <th>Cost</th>
                <th>Weight</th>
                <th>Properties</th>
            </tr>
        </thead>
        <tbody>
            <tr th:each="e : ${equipment}">
                <td th:text="${e.name}">Name</td>
                <td th:text="${e.category}">Category</td>
                <td th:text="${e.cost}">Cost</td>
                <td th:text="${e.weight}">Weight</td>
                <td th:text="${e.propertiesDisplay}">Properties</td>
            </tr>
        </tbody>
    </table>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 4: Create _magic-item-card.html**

Magic item descriptions from open5e are raw Markdown — must be rendered server-side with commonmark:

```html
<th:block th:fragment="magic-item-card-list">
    <div class="card-grid" th:if="${magicItems != null and !magicItems.isEmpty()}">
        <div th:each="m : ${magicItems}" class="card">
            <h3 class="card-title" th:text="${m.name}">Name</h3>
            <div class="badge-group">
                <span class="badge" th:classappend="'badge-' + ${m.rarity?.toLowerCase()}" th:text="${m.rarity}">Rarity</span>
                <span class="badge badge-srd" th:text="${m.category}">Category</span>
                <span class="badge badge-attune" th:if="${m.requiresAttunement}">Attunement</span>
            </div>
            <p class="card-text" th:utext="${@markdownUtil.toHtml(m.description)}">Description</p>
        </div>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 5: Create _class-card.html**

Cards show name, hit die, and subclass-of badge. Each card is a clickable link to the class detail page (see Step 10):

```html
<th:block th:fragment="class-card-list">
    <div class="card-grid" th:if="${classes != null and !classes.isEmpty()}">
        <a th:each="c : ${classes}" class="card card-clickable"
           th:href="@{/library/classes/__${c.sourceKey}__}">
            <h3 class="card-title" th:text="${c.name}">Class Name</h3>
            <div class="badge-group">
                <span class="badge badge-srd" th:text="${'HD: ' + c.hitDie}">HD</span>
                <span class="badge badge-type" th:if="${c.subclassOf != null}"
                      th:text="${'Subclass of ' + c.subclassOf}">Subclass</span>
            </div>
        </a>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 6: Create _species-card.html**

Species cards show size, speed, and traits rendered from the stored JSON:

```html
<th:block th:fragment="species-card-list">
    <div class="card-grid" th:if="${speciesList != null and !speciesList.isEmpty()}">
        <div th:each="s : ${speciesList}" class="card">
            <h3 class="card-title" th:text="${s.name}">Species Name</h3>
            <p th:if="${s.size != null}"><strong>Size:</strong> <span th:text="${s.size}">Size</span></p>
            <p th:if="${s.speed != null}"><strong>Speed:</strong> <span th:text="${s.speed}">Speed</span></p>
            <p th:if="${s.description != null}" class="card-text" th:utext="${@markdownUtil.toHtml(s.description)}">Description</p>
        </div>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 7: Create _background-card.html**

```html
<th:block th:fragment="background-card-list">
    <div class="card-grid" th:if="${backgrounds != null and !backgrounds.isEmpty()}">
        <div th:each="b : ${backgrounds}" class="card">
            <h3 class="card-title" th:text="${b.name}">Background Name</h3>
            <p class="card-text" th:text="${b.description}">Description</p>
        </div>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 8: Create _feat-card.html**

Feat benefit text from open5e is raw Markdown — must be rendered with commonmark:

```html
<th:block th:fragment="feat-card-list">
    <div class="card-grid" th:if="${feats != null and !feats.isEmpty()}">
        <div th:each="f : ${feats}" class="card">
            <h3 class="card-title" th:text="${f.name}">Feat Name</h3>
            <div class="badge-group">
                <span class="badge badge-srd" th:if="${f.category != null}" th:text="${f.category}">Category</span>
            </div>
            <p th:if="${f.prerequisite != null}"><em th:text="${'Prerequisite: ' + f.prerequisite}">Prerequisite</em></p>
            <p class="card-text" th:utext="${@markdownUtil.toHtml(f.benefit)}">Benefit</p>
        </div>
    </div>
    <th:block th:replace="~{common/_empty-state :: empty-state}"></th:block>
</th:block>
```

- [ ] **Step 9: Create Markdown rendering utility bean**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java` — a Spring `@Component` bean that uses `org.commonmark.parser.Parser` + `org.commonmark.renderer.html.HtmlRenderer` to convert Markdown strings to HTML. This is referenced from Thymeleaf as `@markdownUtil`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component("markdownUtil")
public class MarkdownUtil {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public String toHtml(String markdown) {
        if (markdown == null) return "";
        return renderer.render(parser.parse(markdown));
    }
}
```

Ensure `commonmark` and `commonmark-ext-gfm-tables` are in `pom.xml`:

```xml
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
</dependency>
```

(Version managed by Spring Boot 4.1 BOM if available; otherwise use latest 0.x release.)

- [ ] **Step 10: Create _class-detail.html**

Create `src/main/resources/templates/library/class-detail.html` — renders the full class page: name, hit die, saving throws, level progression table derived from the features JSON, features list, spellcasting (if present), subclass reference, and description.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/_base :: layout(~{::title}, ~{::main})}">
<head>
    <title th:text="${classDetail.name} + ' — Library'">Class Detail</title>
</head>
<body>
<main>
    <div class="page-header">
        <a th:href="@{/library}" class="btn btn-text">← Library</a>
        <h1 th:text="${classDetail.name}">Class Name</h1>
    </div>

    <div th:if="${subclass != null}" class="card" style="margin-bottom: var(--space-md);">
        <p><strong>Subclass of:</strong>
            <a th:href="@{/library/classes/__${classDetail.subclassOf}__}"
               th:text="${subclass.name}">Base Class</a></p>
    </div>

    <div class="card">
        <div class="badge-group">
            <span class="badge badge-srd" th:text="${'Hit Die: ' + classDetail.hitDie}">HD</span>
        </div>

        <div th:if="${savingThrows != null}">
            <h2>Saving Throws</h2>
            <p th:text="${savingThrows}">Saving Throws</p>
        </div>

        <!-- Level progression table -->
        <h2>Level Progression</h2>
        <table class="equipment-table" th:if="${levelFeatures != null and !levelFeatures.isEmpty()}">
            <thead>
                <tr>
                    <th>Level</th>
                    <th>Features</th>
                </tr>
            </thead>
            <tbody>
                <tr th:each="entry : ${levelFeatures}">
                    <td th:text="${entry.key}">1</td>
                    <td>
                        <span th:each="feat, iterStat : ${entry.value}" th:text="${feat.name}"
                              th:if="${entry.value != null}">Feature</span>
                        <span th:if="${iterStat.index < entry.value.size() - 1}">, </span>
                    </td>
                </tr>
            </tbody>
        </table>

        <!-- Spellcasting -->
        <div th:if="${spellcasting != null}">
            <h2>Spellcasting</h2>
            <p><strong>Ability:</strong> <span th:text="${spellcasting.ability}">—</span></p>
            <div th:if="${spellcasting.slots != null}">
                <p><strong>Spell Slots by Level:</strong></p>
                <table class="equipment-table">
                    <thead>
                        <tr>
                            <th>Class Level</th>
                            <th>1st</th><th>2nd</th><th>3rd</th><th>4th</th><th>5th</th>
                            <th>6th</th><th>7th</th><th>8th</th><th>9th</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr th:each="row : ${spellcasting.slots}">
                            <td th:text="${row.key}">1</td>
                            <td th:each="slot : ${row.value}" th:text="${slot}">0</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- Description -->
        <div th:if="${classDetail.description != null and !classDetail.description.isBlank()}">
            <h2>Description</h2>
            <p th:utext="${@markdownUtil.toHtml(classDetail.description)}">Description</p>
        </div>

        <!-- Subclasses -->
        <div th:if="${subclasses != null and !subclasses.isEmpty()}">
            <h2>Subclasses</h2>
            <ul>
                <li th:each="sc : ${subclasses}">
                    <a th:href="@{/library/classes/__${sc.sourceKey}__}" th:text="${sc.name}">Subclass</a>
                </li>
            </ul>
        </div>
    </div>
</main>
</body>
</html>
```

The controller (see Task 15b) builds the `levelFeatures` map from the `features` JSON: group features by level number, producing `{1: [{name: "Fighting Style"}, {name: "Second Wind"}], 2: [{name: "Action Surge"}], ...}`. The `spellcasting`, `savingThrows`, and `subclass` objects are parsed from their respective JSON fields.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/templates/library/_condition-card.html \
        src/main/resources/templates/library/_rule-card.html \
        src/main/resources/templates/library/_equipment-card.html \
        src/main/resources/templates/library/_magic-item-card.html \
        src/main/resources/templates/library/_class-card.html \
        src/main/resources/templates/library/_species-card.html \
        src/main/resources/templates/library/_background-card.html \
        src/main/resources/templates/library/_feat-card.html \
        src/main/resources/templates/library/class-detail.html \
        src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java
git commit -m "feat: add Thymeleaf templates for all compendium types, class detail page, and markdown utility"
```

---

### Task 19: Update Library list.html with 8 new tabs

**Files:**
- Modify: `src/main/resources/templates/library/list.html:1-184`

- [ ] **Step 1: Replace the tab section in list.html**

Replace the existing tab buttons (lines 14-19) and the content sections to add all 10 tabs (Monsters, Spells, Conditions, Rules, Equipment, Magic Items, Classes, Species, Backgrounds, Feats).

Replace the `<div class="page-header">` section (lines 12-21) and the entire `<div class="search-bar">` through `</div>` just before the closing `</main>` (lines 23-156) with:

```html
<div class="page-header">
    <h1>Library</h1>
    <div style="display: flex; gap: var(--space-md); align-items: center; flex-wrap: wrap;">
        <button class="form-tab active" id="tab-monsters"
                onclick="switchCompendiumTab('monsters')">Monsters</button>
        <button class="form-tab" id="tab-spells"
                onclick="switchCompendiumTab('spells')">Spells</button>
        <button class="form-tab" id="tab-conditions"
                onclick="switchCompendiumTab('conditions')">Conditions</button>
        <button class="form-tab" id="tab-rules"
                onclick="switchCompendiumTab('rules')">Rules</button>
        <button class="form-tab" id="tab-equipment"
                onclick="switchCompendiumTab('equipment')">Equipment</button>
        <button class="form-tab" id="tab-magic-items"
                onclick="switchCompendiumTab('magic-items')">Magic Items</button>
        <button class="form-tab" id="tab-classes"
                onclick="switchCompendiumTab('classes')">Classes</button>
        <button class="form-tab" id="tab-species"
                onclick="switchCompendiumTab('species')">Species</button>
        <button class="form-tab" id="tab-backgrounds"
                onclick="switchCompendiumTab('backgrounds')">Backgrounds</button>
        <button class="form-tab" id="tab-feats"
                onclick="switchCompendiumTab('feats')">Feats</button>
    </div>
    <a th:href="@{/library/statblocks/new}" class="btn btn-primary">+ New Homebrew</a>
</div>

<!-- Monsters Section -->
<div id="section-monsters" class="compendium-section">
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
                <option value="0">0</option><option value="1/8">1/8</option>
                <option value="1/4">1/4</option><option value="1/2">1/2</option>
                <option value="1">1</option><option value="2">2</option>
                <option value="3">3</option><option value="4">4</option>
                <option value="5">5</option><option value="6">6</option>
                <option value="7">7</option><option value="8">8</option>
                <option value="9">9</option><option value="10">10</option>
                <option value="11">11</option><option value="12">12</option>
                <option value="13">13</option><option value="14">14</option>
                <option value="15">15</option><option value="16">16</option>
                <option value="17">17</option><option value="18">18</option>
                <option value="19">19</option><option value="20">20</option>
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
                <option value="Aberration">Aberration</option><option value="Beast">Beast</option>
                <option value="Celestial">Celestial</option><option value="Construct">Construct</option>
                <option value="Dragon">Dragon</option><option value="Elemental">Elemental</option>
                <option value="Fey">Fey</option><option value="Fiend">Fiend</option>
                <option value="Giant">Giant</option><option value="Humanoid">Humanoid</option>
                <option value="Monstrosity">Monstrosity</option><option value="Ooze">Ooze</option>
                <option value="Plant">Plant</option><option value="Undead">Undead</option>
            </select>
        </div>
        <div class="form-group">
            <label>Source</label>
            <select name="source" id="librarySource"
                    hx-get="/library/statblocks" hx-trigger="change"
                    hx-target="#library-results"
                    hx-include="#librarySearch,#libraryCr,#libraryType">
                <option value="">All</option><option value="SRD">SRD</option>
                <option value="CUSTOM">Custom</option>
            </select>
        </div>
    </div>
    <div id="library-results" hx-get="/library/statblocks" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading...</p></div>
    </div>
</div>

<!-- Spells Section -->
<div id="section-spells" class="compendium-section" style="display:none;">
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
                <option value="0">Cantrip</option><option value="1">1st</option>
                <option value="2">2nd</option><option value="3">3rd</option>
                <option value="4">4th</option><option value="5">5th</option>
                <option value="6">6th</option><option value="7">7th</option>
                <option value="8">8th</option><option value="9">9th</option>
            </select>
        </div>
        <div class="form-group">
            <label>School</label>
            <select name="school" id="spellSchool"
                    hx-get="/library/spells" hx-trigger="change"
                    hx-target="#spell-results"
                    hx-include="#spellSearch,#spellLevel">
                <option value="">Any School</option>
                <option value="Abjuration">Abjuration</option><option value="Conjuration">Conjuration</option>
                <option value="Divination">Divination</option><option value="Enchantment">Enchantment</option>
                <option value="Evocation">Evocation</option><option value="Illusion">Illusion</option>
                <option value="Necromancy">Necromancy</option><option value="Transmutation">Transmutation</option>
            </select>
        </div>
    </div>
    <div id="spell-results" hx-get="/library/spells" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading spells...</p></div>
    </div>
</div>

<!-- Conditions Section -->
<div id="section-conditions" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search conditions..."
                   hx-get="/library/conditions"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#condition-results">
        </div>
    </div>
    <div id="condition-results" hx-get="/library/conditions" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading conditions...</p></div>
    </div>
</div>

<!-- Rules Section -->
<div id="section-rules" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search rules..."
                   hx-get="/library/rules"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#rule-results">
        </div>
    </div>
    <div id="rule-results" hx-get="/library/rules" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading rules...</p></div>
    </div>
</div>

<!-- Equipment Section -->
<div id="section-equipment" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search equipment..."
                   hx-get="/library/equipment"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#equipment-results"
                   hx-include="#equipmentCategory">
        </div>
        <div class="form-group">
            <label>Category</label>
            <select name="category" id="equipmentCategory"
                    hx-get="/library/equipment" hx-trigger="change"
                    hx-target="#equipment-results"
                    hx-include="#equipmentSearch">
                <option value="">All Categories</option>
                <option value="WEAPON">Weapons</option>
                <option value="ARMOR">Armor</option>
                <option value="GEAR">Adventuring Gear</option>
                <option value="TOOL">Tools</option>
            </select>
        </div>
    </div>
    <div id="equipment-results" hx-get="/library/equipment" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading equipment...</p></div>
    </div>
</div>

<!-- Magic Items Section -->
<div id="section-magic-items" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search magic items..."
                   hx-get="/library/magic-items"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#magic-item-results"
                   hx-include="#magicRarity,#magicCategory">
        </div>
        <div class="form-group">
            <label>Rarity</label>
            <select name="rarity" id="magicRarity"
                    hx-get="/library/magic-items" hx-trigger="change"
                    hx-target="#magic-item-results"
                    hx-include="#magicSearch,#magicCategory">
                <option value="">Any Rarity</option>
                <option value="Common">Common</option>
                <option value="Uncommon">Uncommon</option>
                <option value="Rare">Rare</option>
                <option value="Very Rare">Very Rare</option>
                <option value="Legendary">Legendary</option>
                <option value="Artifact">Artifact</option>
            </select>
        </div>
        <div class="form-group">
            <label>Category</label>
            <select name="category" id="magicCategory"
                    hx-get="/library/magic-items" hx-trigger="change"
                    hx-target="#magic-item-results"
                    hx-include="#magicSearch,#magicRarity">
                <option value="">All Categories</option>
                <option value="Armor">Armor</option>
                <option value="Weapon">Weapon</option>
                <option value="Wondrous Item">Wondrous Item</option>
                <option value="Ring">Ring</option>
                <option value="Rod">Rod</option>
                <option value="Staff">Staff</option>
                <option value="Wand">Wand</option>
                <option value="Potion">Potion</option>
                <option value="Scroll">Scroll</option>
            </select>
        </div>
    </div>
    <div id="magic-item-results" hx-get="/library/magic-items" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading magic items...</p></div>
    </div>
</div>

<!-- Classes Section -->
<div id="section-classes" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search classes..."
                   hx-get="/library/classes"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#class-results">
        </div>
    </div>
    <div id="class-results" hx-get="/library/classes" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading classes...</p></div>
    </div>
</div>

<!-- Species Section -->
<div id="section-species" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search species..."
                   hx-get="/library/species"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#species-results">
        </div>
    </div>
    <div id="species-results" hx-get="/library/species" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading species...</p></div>
    </div>
</div>

<!-- Backgrounds Section -->
<div id="section-backgrounds" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search backgrounds..."
                   hx-get="/library/backgrounds"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#background-results">
        </div>
    </div>
    <div id="background-results" hx-get="/library/backgrounds" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading backgrounds...</p></div>
    </div>
</div>

<!-- Feats Section -->
<div id="section-feats" class="compendium-section" style="display:none;">
    <div class="search-bar">
        <div class="form-group">
            <label>Search</label>
            <input type="text" name="search" placeholder="Search feats..."
                   hx-get="/library/feats"
                   hx-trigger="keyup changed delay:200ms"
                   hx-target="#feat-results"
                   hx-include="#featCategory">
        </div>
        <div class="form-group">
            <label>Category</label>
            <select name="category" id="featCategory"
                    hx-get="/library/feats" hx-trigger="change"
                    hx-target="#feat-results"
                    hx-include="#featSearch">
                <option value="">All Categories</option>
                <option value="General">General</option>
                <option value="Origin">Origin</option>
                <option value="Fighting Style">Fighting Style</option>
                <option value="Epic Boon">Epic Boon</option>
            </select>
        </div>
    </div>
    <div id="feat-results" hx-get="/library/feats" hx-trigger="load" hx-swap="innerHTML">
        <div class="empty-state"><p>Loading feats...</p></div>
    </div>
</div>
```

- [ ] **Step 2: Replace the JavaScript at the bottom of list.html**

Replace the existing `<script>` block (lines 161-182) with:

```html
<script>
    function switchCompendiumTab(tab) {
        document.querySelectorAll('.form-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.compendium-section').forEach(s => s.style.display = 'none');
        document.getElementById('tab-' + tab).classList.add('active');
        var section = document.getElementById('section-' + tab);
        if (section) section.style.display = 'block';
    }
</script>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/library/list.html
git commit -m "feat: extend Library page with 8 new compendium tabs"
```

---

### Task 20: Add CSS for compendium types

**Files:**
- Modify: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Add compendium-specific CSS**

Append to `src/main/resources/static/css/app.css`:

```css
/* Compendium sections */
.compendium-section { }

/* Rarity badges */
.badge-common { background: var(--color-text); color: var(--color-bg); }
.badge-uncommon { background: #2ecc71; color: #1a1a2e; }
.badge-rare { background: #3498db; color: #fff; }
.badge-very-rare { background: #9b59b6; color: #fff; }
.badge-legendary { background: #f39c12; color: #1a1a2e; }
.badge-artifact { background: #e74c3c; color: #fff; }
.badge-attune { background: var(--color-accent); color: #fff; }

/* Badge group */
.badge-group {
    display: flex;
    gap: var(--space-xs);
    flex-wrap: wrap;
    margin-bottom: var(--space-sm);
}

/* Equipment table */
.equipment-table {
    width: 100%;
    border-collapse: collapse;
    background: var(--color-surface);
    border-radius: var(--radius);
    overflow: hidden;
}
.equipment-table th,
.equipment-table td {
    padding: var(--space-sm) var(--space-md);
    text-align: left;
    border-bottom: 1px solid var(--color-border);
}
.equipment-table th {
    background: var(--color-surface-hover);
    font-weight: 600;
    color: var(--color-accent);
    font-size: var(--text-sm);
    text-transform: uppercase;
}
.equipment-table tr:hover td {
    background: var(--color-surface-hover);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat: add CSS for compendium rarity badges and equipment table"
```

---

### Task 21: Verify full build and end-to-end smoke test

**Files:** None (verification only)

- [ ] **Step 1: Run full build with tests**

```bash
mvn clean test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Start the app and verify the M3 DoD — three lookups without a PDF**

```bash
mvn spring-boot:run
```

Open http://localhost:8081/library and verify **the milestone Definition of Done**:

1. **"Grappled" (conditions):** Navigate to Conditions tab → "Grappled" condition card shows its description text verbatim from the SRD 5.2.
2. **"Bag of Holding" (magic items):** Navigate to Magic Items tab → search "Bag of Holding" → card shows rarity, category, and rendered description.
3. **Fighter level table (classes):** Navigate to Classes tab → click "Fighter" card → detail page renders:
   - Hit die (`d10`)
   - Level progression table (Level 1: Fighting Style, Second Wind; Level 2: Action Surge; …)
   - Saving throws (Strength, Constitution)
   - The Champion subclass listed in the Subclasses section

Also verify:
- All 10 tabs appear (Monsters, Spells, Conditions, Rules, Equipment, Magic Items, Classes, Species, Backgrounds, Feats)
- Search/filter works on each tab
- Equipment items show in the table with categories
- Species cards show size, speed, and description
- Rule section descriptions render as formatted HTML (not raw Markdown `##`/`*` characters)
- Markdown descriptions on rules, magic items, and feats are rendered as HTML

- [ ] **Step 3: Verify SRD key catalog**

```bash
curl -s http://localhost:8081/api/v1/library/srd-keys | head -c 500
```

Expected: Large JSON array containing keys from all compendium types (e.g., `"blinded"`, `"srd-2024_barbarian"`, `"srd-2024_bag-of-holding"`).

- [ ] **Step 4: Run import/export round-trip test**

```bash
mvn test -Dtest=CampaignImportExportRoundTripTest
```

Expected: PASS — existing campaign import/export still works (no regression).

---

### Task 21b: Update About page with SRD 5.2 PDF attribution

**Files:**
- Modify: `src/main/resources/templates/about.html`

**Background:** Conditions and equipment are transcribed verbatim from the official SRD 5.2 PDF (CC-BY-4.0) — a source beyond open5e. The About page must attribute this additional source alongside the existing open5e attribution. (§4.11: compendium entries must be "covered by the same About-screen CC-BY-4.0 attribution.")

- [ ] **Step 1: Update about.html**

Add the SRD 5.2 PDF attribution to the About page. Locate the existing open5e attribution text and add a new paragraph:

```html
<h3>System Reference Document 5.2 (SRD 5.2)</h3>
<p>The conditions and equipment entries are transcribed verbatim from the official
   <a href="https://www.dndbeyond.com/srd" target="_blank" rel="noopener">SRD 5.2</a>
   (Creative Commons Attribution 4.0 International License — CC-BY-4.0), published by
   Wizards of the Coast.</p>
```

Ensure the existing open5e attribution remains in place for monsters, spells, rules, magic items, classes, species, backgrounds, and feats.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/about.html
git commit -m "feat: add SRD 5.2 PDF attribution to About page for conditions and equipment"
```

---

### Task 22: Final commit and documentation

- [ ] **Step 1: Run all tests one final time**

```bash
mvn clean test
```

- [ ] **Step 2: Commit any remaining changes**

```bash
git add -A
git diff --cached --stat
git commit -m "feat(M3): reference compendium — conditions, rules, equipment, magic items, classes, species, backgrounds, feats"
```

- [ ] **Step 3: Mark M3 as done in SPEC.md**

Edit `SPEC.md` line 715 and change:

```
| M3 | **Reference compendium** | Conditions, rules sections, ...
```

to:

```
| M3 ✅ | **Reference compendium** | Conditions, rules sections, ...
```

```bash
git add SPEC.md
git commit -m "docs: mark M3 reference compendium as complete"
```

---

## Design Decisions

1. **Separate entities per type** (not a unified compendium table): Follows M2 pattern of separate `StatBlock` and `Spell` entities. Each type has different fields and indexes — cramming them into one table would mean nullable-everything columns and no type safety. JPA entities are the right tool here.

2. **Seed from checked-in JSON, not live API**: Consistent with M2 and the zero-internet-at-table policy. The JSON files are committed once and seeded at startup. Future SRD updates are a commit.

3. **Read-only services** (no CRUD for compendium): All compendium types are read-only reference data — no create/edit/delete endpoints. This matches the `SpellService` pattern and the spec's data model.

4. **Minimal duplication in services**: Each type gets its own service class because they have different filter parameters (conditions search by name only; equipment by category; magic items by rarity + category; feats by category). A single generic service would need a complex parameter object, losing clarity for little saved code.

5. **Card summaries in search, detail pages for types needing them**: Search results show compact cards (name + key badges/fields). Classes get a full detail page with level progression table, features, spellcasting, and subclass relationships — this satisfies the M3 DoD requirement of "look up the Fighter level table." Other compendium types (conditions, rules, equipment, magic items, species, backgrounds, feats) show their rendered content directly in their search cards, as their data fits a card without needing a separate page. Detail pages for additional types can be added later as needed.
