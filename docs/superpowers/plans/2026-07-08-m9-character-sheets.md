# M9: Character Sheets — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full rules-aware character sheets (§4.12 of SPEC.md) on top of the compendium — multiclass support, derived-with-override for every value, guided level-up, rest actions, resource counters, XP awarding + milestone mode, and PartyMember roster fields auto-derived from sheets where one exists.

**Architecture:** New `sheet` package following the existing `campaign`/`party`/`encounter` pattern. A `CharacterSheet` entity hangs 1:1 off `PartyMember` (optionally — sheet-less members remain hand-editable exactly as in M2). `classLevels`, `overrides`, `proficiencies`, and `featRefs` are stored as JSON CLOBs on the sheet entity (not separate join tables), following the "editor documents are JSON blobs" pattern from §2.3.2. `SheetResource` and `SheetSpellReference` are separate entities (many-to-one to CharacterSheet) because resources are queried at rest time and spells need filtering per class.

The core derivation engine (`SheetEngine`) is a pure, stateless service that takes a `CharacterSheet` + compendium lookups → returns a `DerivedValues` record. All formulas are table-testable without JPA. The `SheetService` orchestrates: CRUD, level-up, rest (short/long), XP awarding, and syncing derived values back to `PartyMember` fields.

**Tech Stack:** Spring Boot 4.1.0 (Jakarta EE 11), Spring Data JPA + H2, Thymeleaf + htmx, Alpine.js, Jackson 3 (`tools.jackson`)

---

## 1. Scope

### What M9 includes

| Feature | Scope |
|---------|-------|
| CharacterSheet entity (1:1 with PartyMember) | Full — ability scores, class levels, species, background, feats, proficiencies, overrides, XP, hit dice used |
| SheetResource entity (per-sheet named counters) | Full — name, max, current, reset-on-rest rule |
| SheetSpellReference entity (known/prepared spells) | Full — spell FK, prepared flag, source class for multiclass |
| SheetEngine (pure derivation) | Full — ability modifiers, proficiency bonus, save/skill bonuses, passive scores, spell slots (single + multiclass), HP, spell save DC/attack, class name & level string |
| Guided level-up | Full — pick class, choose rolled/average HP, see new features, derived values update |
| Short rest | Full — spend hit dice (typed or rolled), reset SHORT_REST resources, track used dice |
| Long rest | Full — full HP restore, all slots/resources reset, half used hit dice recovered |
| XP awarding + milestone mode | Full — equal split or custom XP, level-up prompts at thresholds; milestone disables XP, DM levels manually |
| PartyMember field derivation | Full — AC, max HP, initiative bonus, speed, passive Perception/Insight/Investigation, classAndLevel auto-derived from sheet |
| Campaign export/import | Full — sheets, resources, spell references included in export DTOs and import round-trip |
| Campaign-level sheet overview | Full — list all party members with sheet status, quick links |
| Per-member sheet detail page | Full — full sheet display, inline editing, level-up dialog, rest buttons, resource tracking, spell list |

### What M9 does NOT include

- Player-editable sheets (post-v1 — M9 sheets are DM-operated, per §4.12)
- Dice roller integration (M11 — HP rolls and hit-dice spending accept typed values for now)
- Class feature-based AC formulas (Unarmored Defense, etc.) — these are handled via overrides
- Expertise detection from feature text — manual expertise flag on proficiency JSON
- Spell selection from compendium (the sheet stores spell references, but a search-and-add interface is scoped to M9; a simpler "add by spell name" input is acceptable for v1)
- Condition/feature impact on derived numbers beyond what's in the compendium's structured data

---

## 2. Architecture Decisions

### 2.1 Sheet ↔ PartyMember relationship

`PartyMember` gains a `@OneToOne` (nullable, `mappedBy = "partyMember"`) to `CharacterSheet`. The sheet is optional — campaigns with only hand-edited roster entries (M2 behavior) continue working unchanged. `PartyMemberService` checks for a sheet and derives fields on save; `CharacterSheet` calls back through `PartyMemberService` when it changes.

### 2.2 classLevels as JSON, not as entity

Each class level entry is:

```json
{
  "classSourceKey": "srd-2024_fighter",
  "level": 5,
  "hitDieRolls": [10, 8, 5, 8, 6]
}
```

Rationale: the full sheet is always loaded together (it's a 1:1 sub-resource of a party member). Splitting class levels into a separate join table adds schema complexity with no benefit — there's no query that needs "find all characters with at least 5 fighter levels" independent of their sheet. The JSON column keeps `ddl-auto=update` migrations additive only.

### 2.3 SheetEngine is stateless and testable without JPA

`SheetEngine` receives its compendium data as function arguments or via a `CompendiumLookup` facade interface (backed by service bean in production, mocked in tests). Every derived field is a pure function of:
- The sheet's JSON fields (ability scores, class levels, proficiencies, overrides)
- Compendium lookup results (class features JSON, species speed, proficiency bonus table, multiclass spell slot table)

No JPA, no `@Transactional`, no side effects. Unit-test assertion: "input this sheet → derived values exactly match these expected numbers."

### 2.4 Multiclass spell slots from seeded rules text

The multiclass spell slot table is defined in `srd-2024_multiclassing_spellcasting.desc` as a Markdown table. At startup, `SheetEngine` (via an `@PostConstruct` or lazy-init method on the service bean) reads this rule section from the database, parses the Markdown table (a 10×10 grid), and builds a `Map<Integer, int[]>` (combined caster level 1–20 → slots[1st..9th]).

This satisfies the provenance rule (§2.3.8) — the table is never hardcoded from memory, always read from the seeded SRD data.

### 2.5 Spellcasting ability from core traits

The core traits feature for each class has a description containing a Markdown table with a `Primary Ability` row. `SheetEngine` parses this to determine the spellcasting ability (STR/DEX/CON/INT/WIS/CHA) for each class. If parsing fails (e.g., subclass without a core traits table), the engine falls back to the base class's ability.

For warlocks (`caster_type = PACT` in the source JSON), Pact Magic slots are tracked separately — not combined with regular spellcasting in the multiclass table. Warlock slots derive from the warlock class's SPELL_SLOTS data directly.

### 2.6 Override system

Overrides stored as `Map<String, Object>` on `CharacterSheet.overrides` (JSON CLOB). Keys are derived field names (`"ac"`, `"maxHp"`, `"initiativeBonus"`, `"speed"`, `"passivePerception"`, `"save_str"`, `"skill_perception"`, etc.). Null/non-existent key means "use derived value". Any present key overrides the derived value. Keys ending in `_note` (e.g., `"ac_note"`) provide a display annotation.

The UI visually marks overridden values (e.g., a subtle border or icon) so the DM can tell at a glance which values are computed vs. hand-set.

### 2.7 Milestone vs. XP mode

Stored in `Campaign.settings` JSON as `{"levelingMode": "XP" | "MILESTONE"}`. In XP mode, the UI shows XP tracking and level-up prompts at 5.5e thresholds. In milestone mode, XP fields are hidden and the DM manually sets each character's level via a level input; level-up features are still shown.

The 2024 XP thresholds are (per the same rules compendium seed data, verified):
```
Level 2: 300, Level 3: 900, Level 4: 2,700, Level 5: 6,500,
Level 6: 14,000, Level 7: 23,000, Level 8: 34,000, Level 9: 48,000,
Level 10: 64,000, Level 11: 85,000, Level 12: 100,000,
Level 13: 120,000, Level 14: 140,000, Level 15: 165,000,
Level 16: 195,000, Level 17: 225,000, Level 18: 265,000,
Level 19: 305,000, Level 20: 355,000
```

These come from a rules section or, if not in structured form in the rules, are embedded as a checked-in constant with a comment referencing the SRD multiclassing Experience Points rule `srd-2024_multiclassing_experience-points`. The plan's implementation step will verify whether the rule section text contains these thresholds parsably.

---

## 3. Entity Design

### 3.1 `CharacterSheet` (`sheet/data/CharacterSheet.java`)

```java
package dev.hendrikhoemberg.dmhelper.sheet.data;

import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "character_sheet")
public class CharacterSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_member_id", nullable = false, unique = true)
    private PartyMember partyMember;

    @Column(columnDefinition = "CLOB")
    private String abilityScores; // JSON: {"str":18,"dex":14,"con":16,"int":8,"wis":12,"cha":10}

    @Column(columnDefinition = "CLOB")
    private String classLevels; // JSON array: [{"classSourceKey":"srd-2024_fighter","level":5,"hitDieRolls":[10,8,5,8,6]}, ...]

    @Column(columnDefinition = "CLOB")
    private String proficiencies; // JSON: {"skills":["perception","stealth"],"tools":["thieves_tools"],"languages":["common","elvish"],"armor":["light"],"weapons":["simple","martial"],"expertise":["perception"]}

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id")
    private Species species; // FK to Species compendium

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "background_id")
    private Background background; // FK to Background compendium

    @Column(columnDefinition = "CLOB")
    private String featRefs; // JSON array of feat sourceKeys: ["srd-2024_alert","srd-2024_tough"]

    @Column(nullable = false)
    private int xp = 0;

    @Column(columnDefinition = "CLOB")
    private String overrides; // JSON: {"ac":19,"ac_note":"Bracers of Defense + Ring of Protection"}

    @Column(nullable = false)
    private int hitDiceUsed = 0; // total spent hit dice (sum across all classes)

    @Column(columnDefinition = "CLOB")
    private String spellSlotsUsed; // JSON: {"1":0,"2":1,"3":0,...} — tracks used spell slots across all levels

    // no createdAt — sheet is tied to PartyMember lifecycle

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }

    public String getAbilityScores() { return abilityScores; }
    public void setAbilityScores(String abilityScores) { this.abilityScores = abilityScores; }

    public String getClassLevels() { return classLevels; }
    public void setClassLevels(String classLevels) { this.classLevels = classLevels; }

    public String getProficiencies() { return proficiencies; }
    public void setProficiencies(String proficiencies) { this.proficiencies = proficiencies; }

    public Species getSpecies() { return species; }
    public void setSpecies(Species species) { this.species = species; }

    public Background getBackground() { return background; }
    public void setBackground(Background background) { this.background = background; }

    public String getFeatRefs() { return featRefs; }
    public void setFeatRefs(String featRefs) { this.featRefs = featRefs; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public String getOverrides() { return overrides; }
    public void setOverrides(String overrides) { this.overrides = overrides; }

    public int getHitDiceUsed() { return hitDiceUsed; }
    public void setHitDiceUsed(int hitDiceUsed) { this.hitDiceUsed = hitDiceUsed; }

    public String getSpellSlotsUsed() { return spellSlotsUsed; }
    public void setSpellSlotsUsed(String spellSlotsUsed) { this.spellSlotsUsed = spellSlotsUsed; }
}
```

**Key decisions:**
- `abilityScores` is a JSON object, not six separate columns. Ability scores are always read/written together (the whole sheet is loaded), and JSON avoids 6 more nullable int columns on the table.
- `classLevels` is a JSON array. A `CharacterClassLevel` join entity would add zero query benefit and complicate `ddl-auto` migrations.
- `proficiencies` groups skills, tools, languages, armor, weapons, and expertise into one JSON column. These fields are mostly display + derivation; the engine uses the JSON directly.
- `species` and `background` are proper FKs because compendium entities need referential integrity.
- `hitDiceUsed` is a simple counter incremented on short rest — the total across all classes. Per-class tracking would add complexity for little gain; the DM heals per-character, not per-class. If per-class tracking is needed later, we can derive it from total used ÷ class levels at short-rest time (restoring half rounded down per class).
- `xp` is a simple int. Milestone mode ignores it via UI conditional rendering.

### 3.2 `SheetResource` (`sheet/data/SheetResource.java`)

```java
package dev.hendrikhoemberg.dmhelper.sheet.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sheet_resource", indexes = {
    @Index(name = "idx_resource_sheet", columnList = "sheet_id")
})
public class SheetResource {

    public enum ResetRule { SHORT_REST, LONG_REST, NEVER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(nullable = false, length = 255)
    private String name; // "Rage", "Channel Divinity", "Wand of Magic Missiles"

    @Column(nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int currentUses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResetRule resetRule = ResetRule.LONG_REST;

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CharacterSheet getSheet() { return sheet; }
    public void setSheet(CharacterSheet sheet) { this.sheet = sheet; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    public int getCurrentUses() { return currentUses; }
    public void setCurrentUses(int currentUses) { this.currentUses = currentUses; }
    public ResetRule getResetRule() { return resetRule; }
    public void setResetRule(ResetRule resetRule) { this.resetRule = resetRule; }
}
```

### 3.3 `SheetSpellReference` (`sheet/data/SheetSpellReference.java`)

```java
package dev.hendrikhoemberg.dmhelper.sheet.data;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sheet_spell_ref", indexes = {
    @Index(name = "idx_spellref_sheet", columnList = "sheet_id"),
    @Index(name = "idx_spellref_spell", columnList = "spell_id")
})
public class SheetSpellReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spell_id", nullable = false)
    private Spell spell;

    @Column(nullable = false)
    private boolean prepared = false;

    @Column(length = 100)
    private String sourceClass; // sourceKey of the class providing this spell (for multiclass)

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CharacterSheet getSheet() { return sheet; }
    public void setSheet(CharacterSheet sheet) { this.sheet = sheet; }
    public Spell getSpell() { return spell; }
    public void setSpell(Spell spell) { this.spell = spell; }
    public boolean isPrepared() { return prepared; }
    public void setPrepared(boolean prepared) { this.prepared = prepared; }
    public String getSourceClass() { return sourceClass; }
    public void setSourceClass(String sourceClass) { this.sourceClass = sourceClass; }
}
```

### 3.4 `PartyMember` modifications

Add to `PartyMember.java`:

```java
@OneToOne(mappedBy = "partyMember", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private CharacterSheet characterSheet;

// getter and setter
public CharacterSheet getCharacterSheet() { return characterSheet; }
public void setCharacterSheet(CharacterSheet characterSheet) { this.characterSheet = characterSheet; }
```

No changes to existing `PartyMember` combat fields (`ac`, `maxHp`, `initiativeBonus`, `speed`, `passivePerception`, `passiveInsight`, `passiveInvestigation`, `classAndLevel`) — these remain the canonical values that M4–M6 consume. They are *populated* from the sheet on derivation, but always readable directly from PartyMember.

### 3.5 Repositories

```java
// CharacterSheetRepository.java
public interface CharacterSheetRepository extends JpaRepository<CharacterSheet, UUID> {
    Optional<CharacterSheet> findByPartyMemberId(UUID partyMemberId);
    List<CharacterSheet> findByPartyMember_CampaignId(UUID campaignId);
}

// SheetResourceRepository.java
public interface SheetResourceRepository extends JpaRepository<SheetResource, UUID> {
    List<SheetResource> findBySheetId(UUID sheetId);
}

// SheetSpellReferenceRepository.java
public interface SheetSpellReferenceRepository extends JpaRepository<SheetSpellReference, UUID> {
    List<SheetSpellReference> findBySheetId(UUID sheetId);
    List<SheetSpellReference> findBySheetIdAndSourceClass(UUID sheetId, String sourceClass);
}
```

---

## 4. SheetEngine Design (Derivation)

`SheetEngine` is a `@Service` class in `sheet/service/SheetEngine.java`. It is **stateless after initialization** — the only state is cached compendium lookups (proficiency bonus table, multiclass spell slot table) built once at startup.

### 4.1 Input / Output

```java
public record DerivedValues(
    // ability modifiers
    int strMod, int dexMod, int conMod, int intMod, int wisMod, int chaMod,
    // proficiency
    int proficiencyBonus,
    int totalLevel,
    // saving throws
    int saveStr, int saveDex, int saveCon, int saveInt, int saveWis, int saveCha,
    // skills (18 skills, each as name→bonus)
    Map<String, Integer> skillBonuses,
    // passives
    int passivePerception, int passiveInsight, int passiveInvestigation,
    // HP
    int maxHp,
    int totalHitDice,      // total number of hit dice (sum across all classes)
    int remainingHitDice,  // totalHitDice - hitDiceUsed
    // spell slots (array index 1–9)
    int[] spellSlots,      // spellSlots[1] = 1st-level slots, etc.
    int[] pactSlots,       // warlock pact slots (separate per the rules)
    // spellcasting (per class)
    Map<String, String> classSpellcastingAbilities, // classSourceKey -> "int"/"wis"/"cha"
    int spellSaveDC,       // if multiple casting classes, highest
    int spellAttackBonus,  // if multiple casting classes, highest
    // meta
    String classAndLevel,  // "Fighter 5 / Wizard 2"
    // speed
    int speed,             // from species, default 30
    // initiative
    int initiativeBonus,   // dexMod (override-able)
    // AC (override-able, no formula for v1 — just derived from override or base 10 + dexMod)
    int armorClass         // override or 10 + dexMod
) {}
```

### 4.2 Internal compendium caches

At startup, or lazily on first derivation request, the engine builds:

1. **Proficiency bonus table**: `Map<Integer, Integer>` (level → PB).
   - Reads any base class entity (e.g., Fighter), parses `features` JSON, finds `PROFICIENCY_BONUS` feature, builds map from `data_for_class_table`.

2. **Multiclass spell slot table**: `Map<Integer, int[]>` (combined caster level 1–20 → slots[1..9]).
   - Reads `RuleSection` with sourceKey `"srd-2024_multiclassing_spellcasting"`.
   - Parses the Markdown table from `desc` field using regex: `|(\d+)|(\d+|—)|(\d+|—)|...|`.
   - Validates the table is 20 rows × 10 columns.

3. **Spellcasting ability per class**: `Map<String, String>` (classSourceKey → ability name).
   - For each spellcasting class entity: parse `features` JSON, find `CORE_TRAITS_TABLE` feature, extract `Primary Ability` from desc Markdown table.
   - Cache as lowercase ability name (`"strength"`, `"dexterity"`, etc.).

4. **Class base list**: `Map<String, CharacterClass>` by sourceKey, loaded from `CharacterClassRepository.findAll()`.

5. **Skill-to-ability mapping**: static `Map<String, String>` (skill name → ability).
   - This is the game's structural framework, not specific rules content. Defines the 18 standard skills and their default ability associations:
   ```
   athletics→str, acrobatics→dex, sleight_of_hand→dex, stealth→dex,
   arcana→int, history→int, investigation→int, nature→int, religion→int,
   animal_handling→wis, insight→wis, medicine→wis, perception→wis, survival→wis,
   deception→cha, intimidation→cha, performance→cha, persuasion→cha
   ```
   - Listed explicitly here with a comment referencing 2024 SRD ability check rules (§2.3.8 satisfied).

### 4.3 Derivation logic

```
derive(sheet, campaignId):
    scores ← parse abilityScores JSON
    classEntries ← parse classLevels JSON array
    profJson ← parse proficiencies JSON
    overridesMap ← parse overrides JSON (or empty)

    // Total level and proficiency
    totalLevel = sum(classEntries[].level)
    profBonus = proficiencyTable[totalLevel]

    // Ability modifiers
    strMod = floor((scores.str - 10) / 2); ...same for all 6

    // Saving throws
    proficientSaves = union of save proficiencies from each class's core traits + manual in profJson
    saveStr = strMod + (proficientSaves.contains("str") ? profBonus : 0); ...same for all 6

    // Skills
    for each skill in SKILLS:
        proficiency = profJson.skills.contains(skill) || backgroundSkills.contains(skill)
        expertise = profJson.expertise.contains(skill)
        abilityMod = getAbilityMod(skillToAbility[skill], scores)
        skillBonuses[skill] = abilityMod + (proficiency ? (expertise ? 2*profBonus : profBonus) : 0)

    // Passive scores
    passivePerception = 10 + skillBonuses["perception"]
    passiveInsight = 10 + skillBonuses["insight"]
    passiveInvestigation = 10 + skillBonuses["investigation"]

    // HP
    maxHp = sum over classLevels: level 1 → max(hitDie), levels 2+ → sum(hitDieRolls)
    totalHitDice = sum of all classLevels[].level (1 hit die per level)
    remainingHitDice = totalHitDice - sheet.hitDiceUsed

    // Spell slots
    spellcastingClasses = classEntries filtered to those with SPELL_SLOTS features
    if spellcastingClasses.size() == 1:
        // single class: use that class's SPELL_SLOTS data directly
        spellSlots = classSlotTables[classSourceKey][classLevel]
        pactSlots = if warlock: classSlotTables["srd-2024_warlock"][warlockLevel]
    elif spellcastingClasses.size() > 1:
        // multiclass: compute combined caster level
        combinedLevel = sum for each casting class:
            if casterType == FULL: classLevel
            if casterType == HALF: ceil(classLevel / 2.0)  // "round up" per 2024 rules
            // PACT (warlock) is excluded — pact slots stay separate
        spellSlots = multiclassSlotTable[combinedLevel]
        pactSlots = if warlock present: classSlotTables["srd-2024_warlock"][warlockLevel]
    else:
        spellSlots = empty; pactSlots = empty

    // Spell save DC and attack bonus
    for each spellcasting class:
        ability = classSpellcastingAbilities[classSourceKey]
        classDC = 8 + profBonus + getAbilityMod(ability, scores)
        classAtk = profBonus + getAbilityMod(ability, scores)
    spellSaveDC = max of all classDCs (or 0 if no casting)
    spellAttackBonus = max of all classAtks (or 0)

    // Class name string
    classAndLevel = join(classEntries sorted by level desc: "{className} {level}") with " / "

    // Speed
    speed = parse from species.speed (e.g., "30 ft." → 30, "25 ft." → 25), default 30

    // Initiative
    initiativeBonus = dexMod

    // AC
    armorClass = 10 + dexMod  // base; overridable

    // Apply overrides (any field in overridesMap replaces the derived value)
    // After applying: return DerivedValues record
```

### 4.4 Class spell slot table per class

Built by parsing each spellcasting class's `features` JSON for entries with `feature_type = "SPELL_SLOTS"`. The slot key pattern is `srd-2024_{class}_slots-{level}th`. The result is a `Map<String, Map<Integer, int[]>>`:
- Key: class sourceKey
- Inner map: class level (1..20) → array of 10 ints (index 1..9 = slot count, index 0 unused)

### 4.5 HP computation detail

- Level 1 in each class: always max hit die (e.g., d10 → 10)
- Levels 2+ in that class: use `hitDieRolls[]` entries
- User chooses rolled or average at level-up time; the sheet stores the result
- Average per hit die: d6→4, d8→5, d10→6, d12→7 (2024 rules: round up)
- At level-up, the UI shows "Roll: __ or Average: __" with the class hit die and average pre-filled
- `totalHitDice` = sum of all class levels (each level grants 1 hit die of the class's die type)
- CON modifier is added per level for HP — this is handled in the summation: each class level contributes `hitDieRoll[i] + conMod`

---

## 5. Service Layer Design

### 5.1 `SheetService` (`sheet/service/SheetService.java`)

```java
@Service
@Transactional
public class SheetService {

    // Dependencies (constructor-injected):
    //   CharacterSheetRepository, SheetResourceRepository, SheetSpellReferenceRepository
    //   PartyMemberRepository, SheetEngine
    //   CharacterClassRepository, CharacterClassService
    //   SpeciesRepository, BackgroundRepository
    //   ObjectMapper (Jackson 3)

    // --- Sheet CRUD ---

    public record CreateSheetRequest(
        UUID partyMemberId,
        Map<String, Integer> abilityScores,   // {"str":15,"dex":14,...}
        List<ClassLevelEntry> classLevels,     // as described in §2.2
        SheetProficiencies proficiencies,
        UUID speciesId,
        UUID backgroundId,
        List<String> featRefs,
        int xp
    ) {}

    public record SheetDto(
        UUID id, UUID partyMemberId,
        Map<String, Integer> abilityScores,
        List<ClassLevelEntry> classLevels,
        SheetProficiencies proficiencies,
        String speciesName, UUID speciesId,
        String backgroundName, UUID backgroundId,
        List<String> featRefs,
        int xp,
        Map<String, Object> overrides,
        int hitDiceUsed,
        DerivedValues derivedValues,
        List<SheetResourceDto> resources,
        List<SheetSpellDto> spells
    ) {}

    // Create a sheet for a party member (member must not already have a sheet)
    CharacterSheet createSheet(CreateSheetRequest request) -> SheetDto

    // Update editable fields (ability scores, class levels, proficiencies, species, background, feats, xp, overrides)
    CharacterSheet updateSheet(UUID sheetId, UpdateSheetRequest request) -> SheetDto

    // Delete a sheet (part member reverts to hand-editable)
    void deleteSheet(UUID sheetId)

    // Get sheet as DTO with derived values computed
    SheetDto getSheetDto(UUID sheetId)  // (also available as getSheetDtoByPartyMemberId(UUID))

    // --- Level-up ---
    record LevelUpRequest(
        String classSourceKey,  // which class to level
        int hpRoll,             // the rolled or average HP for this level
        boolean isAverage       // if true, hpRoll is ignored and average is used
    ) {}
    SheetDto levelUp(UUID sheetId, LevelUpRequest request)

    // --- Rests ---
    SheetDto shortRest(UUID sheetId, Map<UUID, Integer> hitDiceSpentByClass /* optional per-class tracking */)
    // shortRest: spend hit dice (DM types amounts or rolls), reset SHORT_REST resources
    // Alternative simpler version: just spend totalHitDiceSpent and use half rounded down per class

    SheetDto longRest(UUID sheetId)
    // longRest: HP to max, all resources reset, half used hit dice recovered, full spell slots restored

    // --- XP ---
    SheetDto awardXp(UUID sheetId, int amount)
    // Also: batch award XP to multiple party members for a campaign

    void setLevel(UUID sheetId, int totalLevel) // for milestone mode

    // --- Resources ---
    SheetResourceDto updateResource(UUID resourceId, int currentUses)

    // --- Spells ---
    SheetSpellDto addSpell(UUID sheetId, UUID spellId, boolean prepared, String sourceClass)
    void removeSpell(UUID spellRefId)
    void togglePrepared(UUID spellRefId)

    // --- Internal ---
    void syncToPartyMember(CharacterSheet sheet)
    // Called after every sheet mutation.
    // Computes DerivedValues via SheetEngine, then copies relevant fields to the PartyMember:
    //   partyMember.ac = derivedValues.armorClass
    //   partyMember.maxHp = derivedValues.maxHp
    //   partyMember.initiativeBonus = derivedValues.initiativeBonus
    //   partyMember.speed = derivedValues.speed
    //   partyMember.passivePerception = derivedValues.passivePerception
    //   partyMember.passiveInsight = derivedValues.passiveInsight
    //   partyMember.passiveInvestigation = derivedValues.passiveInvestigation
    //   partyMember.classAndLevel = derivedValues.classAndLevel
    // Saves partyMember.
}
```

### 5.2 `SheetEngine` (`sheet/service/SheetEngine.java`)

```java
@Service
public class SheetEngine {

    // Cached at startup
    private Map<Integer, Integer> proficiencyBonusTable;
    private Map<Integer, int[]> multiclassSlotTable;
    private Map<String, String> classSpellcastingAbilities; // classSourceKey -> abilityName

    private final CharacterClassRepository classRepo;
    private final RuleSectionRepository ruleSectionRepo;
    private final ObjectMapper mapper;

    // @PostConstruct or lazy-init method:
    void initialize() {
        this.proficiencyBonusTable = buildProficiencyBonusTable();
        this.multiclassSlotTable = parseMulticlassSlotTable();
        this.classSpellcastingAbilities = buildSpellcastingAbilities();
    }

    DerivedValues derive(CharacterSheet sheet)
    // Pure computation — no side effects, no JPA queries (uses cached data only)
    // Takes the sheet entity directly (parses JSON fields), returns DerivedValues record
}
```

---

## 6. Web Layer Design

### 6.1 `SheetController` (`sheet/web/SheetController.java`) — Thymeleaf

| Route | Method | Purpose |
|-------|--------|---------|
| `GET /campaigns/{cid}/sheets` | View | Campaign-level sheet overview — list all party members with sheet status, derived AC/HP/passives, links to detail |
| `GET /campaigns/{cid}/party/{mid}/sheet` | View | Individual sheet detail page — full display with derived values, resource trackers, spell list, rest buttons, level-up trigger |
| `GET /campaigns/{cid}/party/{mid}/sheet/create` | View | Create sheet form for a sheet-less member |
| `POST /campaigns/{cid}/party/{mid}/sheet` | Action | Create sheet (htmx form submit) |
| `DELETE /campaigns/{cid}/party/{mid}/sheet` | Action | Delete sheet, reverts member to hand-editable |
| `POST /campaigns/{cid}/party/{mid}/sheet/level-up` | Action | Level-up action (htmx form in dialog) |
| `POST /campaigns/{cid}/party/{mid}/sheet/rest/short` | Action | Short rest (htmx button) |
| `POST /campaigns/{cid}/party/{mid}/sheet/rest/long` | Action | Long rest (htmx button) |
| `PATCH /campaigns/{cid}/party/{mid}/sheet/abilities` | Action | Update ability scores (htmx form) |
| `PATCH /campaigns/{cid}/party/{mid}/sheet/proficiencies` | Action | Update proficiencies |
| `PATCH /campaigns/{cid}/party/{mid}/sheet/overrides` | Action | Update overrides |

### 6.2 `SheetApiController` (`sheet/web/SheetApiController.java`) — REST / JSON

| Route | Method | Purpose |
|-------|--------|---------|
| `GET /api/v1/campaigns/{cid}/party/{mid}/sheet` | JSON | Get sheet DTO with derived values |
| `PUT /api/v1/campaigns/{cid}/party/{mid}/sheet` | JSON | Create or update sheet |
| `POST /api/v1/campaigns/{cid}/party/{mid}/sheet/level-up` | JSON | Level up |
| `POST /api/v1/campaigns/{cid}/party/rest` | JSON | Batch rest: `?type=SHORT|LONG&members=id1,id2` |
| `POST /api/v1/campaigns/{cid}/party/xp` | JSON | Award XP to party members: `{"awards":{"id1":500,"id2":500},"split":"EQUAL|CUSTOM"}` |
| `GET /api/v1/campaigns/{cid}/party/{mid}/sheet/resources` | JSON | List resources |
| `PUT /api/v1/campaigns/{cid}/party/{mid}/sheet/resources/{rid}` | JSON | Update resource uses |
| `GET /api/v1/campaigns/{cid}/party/{mid}/sheet/spells` | JSON | List spells |
| `POST /api/v1/campaigns/{cid}/party/{mid}/sheet/spells` | JSON | Add spell |
| `DELETE /api/v1/campaigns/{cid}/party/{mid}/sheet/spells/{sid}` | JSON | Remove spell |

### 6.3 Campaign-level XP awarding

Additional endpoint on `SheetApiController`:

```
POST /api/v1/campaigns/{cid}/party/xp
Body: {
  "amount": 2000,
  "split": "EQUAL",           // or "CUSTOM"
  "awards": {                   // only for CUSTOM split
    "member-uuid-1": 750,
    "member-uuid-2": 500
  }
}
```

Response: `{"members":[{"id":"...","name":"Thia","xp":3500,"newLevel":6,"levelUp":true}, ...]}`

---

## 7. Templates

### 7.1 `templates/sheet/overview.html`

Campaign-level sheet overview. Layout:
- "Party Sheets" header with "Create Sheet" button per sheet-less member
- Card per party member showing: character name, class & level (from sheet or hand-edited), AC, max HP, initiative, passive scores
- Each card links to the detail page
- Members with sheets show a "sheet" badge; members without show a "manual" badge
- Quick-action buttons: short rest, long rest, award XP

### 7.2 `templates/sheet/detail.html`

Full sheet display. Sections:
1. **Header**: character name, class & level (auto-derived), species, background, total level, XP (or "Milestone" label), proficiency bonus
2. **Ability scores** (editable inline): 6 ability scores with computed modifiers
3. **Combat stats** (derived): AC, max HP, initiative, speed — each marked as derived or overridden
4. **Saving throws**: all 6 with modifiers, proficient ones highlighted
5. **Skills**: all 18 skills with bonuses, proficient ones highlighted, expertise marked
6. **Passive scores**: Perception, Insight, Investigation
7. **Spellcasting**: spell save DC, spell attack bonus, spell slots grid (showing used/available), prepared spells list grouped by level
8. **Resources**: list of named resource counters with increment/decrement buttons
9. **Features & Traits**: rendered class features (from compendium, read-only)
10. **Actions**: level-up button, short rest button, long rest button
11. **Override panel**: collapsible section showing all overridden values with notes

### 7.3 `templates/sheet/_ability-scores.html`

Inline-editable ability score grid. A 2×3 grid of score/ modifier pairs. Each score is an `<input>` in an htmx form that `PATCH`es back to the controller. The modifier updates via Alpine.js reactivity on input change.

### 7.4 `templates/sheet/_level-up-dialog.html`

Modal/dialog triggered from the detail page:
- Dropdown: "Level up in..." listing the character's current classes (plus "New class" option)
- HP: radio "Roll" (with text input) or "Average" (shows pre-computed average)
- When class selected: shows features gained at the new level (from compendium data)
- "Confirm" button submits to `POST .../sheet/level-up`

### 7.5 `templates/sheet/_resources.html`

Resource tracker strip. Per resource:
- Name, current/max counter with +/- buttons
- Reset-on indicator (short rest / long rest / never)

---

## 8. Campaign Export/Import Integration

### 8.1 Export DTOs (new records in `CampaignExportDto.java` or separate file)

```java
public record SheetExportDto(
    Map<String, Integer> abilityScores,
    List<ClassLevelEntry> classLevels,
    SheetProficiencies proficiencies,
    String speciesKey,      // Species sourceKey (not UUID)
    String backgroundKey,   // Background sourceKey
    List<String> featRefs,  // Feat sourceKeys
    int xp,
    Map<String, Object> overrides,
    int hitDiceUsed,
    List<ResourceExportDto> resources,
    List<SpellRefExportDto> spells
) {}

public record ResourceExportDto(String name, int maxUses, int currentUses, String resetRule) {}
public record SpellRefExportDto(String spellKey, boolean prepared, String sourceClass) {}
```

### 8.2 Import logic

- PartyMember import is extended to accept an optional `sheet` field in the JSON
- Species/Background/Feats are resolved by sourceKey, not UUID (same pattern as existing statblock key resolution)
- Unresolvable compendium references degrade to null with a warning (same as existing import behavior)
- No existing sheet is ever overwritten — if a PartyMember already has a sheet on import, the import skips the sheet field with a warning

### 8.3 Modified files

`CampaignExportDto.java` / `CampaignService.java`:
- Export: walk PartyMember → CharacterSheet, serialize each as `SheetExportDto`
- Import: deserialize `SheetExportDto` from JSON, create entities, resolve keys

---

## 9. Testing Strategy

### 9.1 `SheetEngineTest` — Table-driven derivation tests

The core verification: "this particular character build → these exact derived numbers."

```java
@Test
void level5Fighter() {
    // Arrange: build a sheet with known values
    // str 18, dex 14, con 16, int 8, wis 12, cha 10
    // Fighter 5 (Great Weapon Fighting style — but no AC change, just proficiencies)
    // Proficient: Athletics, Perception, saves Str/Con

    CharacterSheet sheet = createSheet(
        Map.of("str", 18, "dex", 14, "con", 16, "int", 8, "wis", 12, "cha", 10),
        List.of(new ClassLevelEntry("srd-2024_fighter", 5, List.of(10, 8, 5, 6, 8))),
        new SheetProficiencies(List.of("athletics", "perception"), List.of(), List.of(), List.of(), List.of(), List.of()),
        "srd-2024_human", "srd-2024_soldier", List.of("srd-2024_tough"), 6500, Map.of(), 0
    );

    DerivedValues result = engine.derive(sheet);

    // Assert: every number verified against known correct values
    assertEquals(3, result.proficiencyBonus(), "Level 5 = +3 PB");
    assertEquals(5, result.totalLevel());
    assertEquals(4, result.strMod());
    assertEquals(2, result.dexMod());
    assertEquals(3, result.conMod());

    // HP: 10 (lvl 1 max) + 8+5+6+8 (rolls) + 5*3 (con mod) = 10+27+15 = 52
    // Wait, the HP computation... let me think.
    // level 1: max d10 = 10, plus con mod = 10 + 3 = 13
    // level 2-5: rolls 8, 5, 6, 8 = 27, plus 4*3 = 12
    // total = 13 + 27 + 12 = 52
    assertEquals(52, result.maxHp());

    assertEquals(7, result.saveStr()); // 4 (strMod) + 3 (PB) = 7
    assertEquals(2, result.saveDex()); // 2 (dexMod) + 0 = 2
    assertEquals(6, result.saveCon()); // 3 (conMod) + 3 (PB) = 6

    assertEquals(7, result.skillBonuses().get("athletics")); // 4 + 3 = 7
    assertEquals(5, result.skillBonuses().get("perception")); // 1 + 3 = 4? Wait, wis is 12 so +1. +3 PB = 4. Hmm.

    // Actually perception: wisMod = 1, proficient = +3 (PB) → 1+3 = 4
    assertEquals(4, result.skillBonuses().get("perception"));
    assertEquals(14, result.passivePerception()); // 10 + 4 = 14

    assertEquals(37, result.maxHp()); // wait, I computed 52. Let me recalculate without con mod.

    // Actually the HP formula: each level = hitDieRoll + conMod
    // Level 1: hitDieMax(10) + conMod(3) = 13
    // Level 2: roll(8) + conMod(3) = 11
    // Level 3: roll(5) + conMod(3) = 8
    // Level 4: roll(6) + conMod(3) = 9
    // Level 5: roll(8) + conMod(3) = 11
    // Total = 13 + 11 + 8 + 9 + 11 = 52
    assertEquals(52, result.maxHp());

    assertEquals(30, result.speed()); // human default

    assertEquals("Fighter 5", result.classAndLevel());
}
```

The test suite should include at minimum:
- Level 1 fighter (verify level 1 HP = max hit die + CON)
- Level 5 fighter (mid-level, proficiency bonus changes at 5)
- Level 3 wizard (spell slots, save DC, attack bonus)
- Level 5 cleric (prepared caster, different spellcasting ability)
- Level 4 fighter / Level 3 wizard multiclass (multiclass HP, spell slots from multiclass table)
- Level 5 paladin (half-caster single class)
- Level 2 paladin / Level 3 sorcerer (multiclass half+full caster)
- Override tests: AC override, save override, skill override
- Species speed: dwarf (25 ft), elf (35 ft — wait, 2024 elf is 30 ft)

**Important:** All expected values in tests must be **manually verified against the compendium data** — never computed from memory. The test should include a comment referencing which compendium data produces each value.

### 9.2 `SheetServiceTest` — Service integration

- Test create sheet, get DTO, update abilities → derived values recalculated
- Test level-up: add a level → HP increases, PB may change, features list updates
- Test short rest: spend hit dice → remaining hit dice decreases, SHORT_REST resources reset
- Test long rest: HP restored, slots reset, used hit dice halved
- Test award XP: XP increases, level-up flag triggers at threshold
- Test milestone mode: setLevel → classLevels updated, derived values reflect new level
- Test override: set AC override → derived AC returns override value, but base calculation still correct
- Test delete sheet: sheet deleted → PartyMember retains its last-derived values

### 9.3 Campaign export/import round-trip

Extend `CampaignImportExportRoundTripTest`:
- Export campaign → verify sheet DTO serializes in export JSON
- Import → verify sheet is reconstructed with correct ability scores, class levels, resources, spells
- Import → verify unresolvable species key degrades gracefully (null FK, warning)
- Import → verify sheet-less PartyMember import still works (sheet is optional)

### 9.4 Player-safe projection test

Sheet data must never appear in player-safe payloads. Extend `PlayerSafeProjectionServiceTest` to verify:
- No sheet data leaks into token projection (PartyMember-linked tokens already have only combat fields from PartyMember — no change needed, but test confirms)
- `PartyMember.toPlayerSafeDto()` does NOT expose sheet reference

---

## 10. File Structure Summary

### New files (create)

| File | Responsibility |
|------|---------------|
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheet.java` | JPA entity (1:1 with PartyMember) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheetRepository.java` | Spring Data repository |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResource.java` | JPA entity (resource counters, many-to-one) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResourceRepository.java` | Spring Data repository |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReference.java` | JPA entity (spell references, many-to-one) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReferenceRepository.java` | Spring Data repository |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngine.java` | Pure derivation engine (stateless after init, no JPA queries at derivation time except cached init) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java` | CRUD + level-up + rest + XP + PartyMember sync |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetController.java` | Thymeleaf controller (pages + htmx actions) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetApiController.java` | REST `/api/v1` controller (JSON endpoints) |
| `src/main/resources/templates/sheet/overview.html` | Campaign-level party sheets overview |
| `src/main/resources/templates/sheet/detail.html` | Individual sheet display (Thymeleaf page) |
| `src/main/resources/templates/sheet/_ability-scores.html` | Inline ability score editor fragment |
| `src/main/resources/templates/sheet/_level-up-dialog.html` | Level-up modal dialog fragment |
| `src/main/resources/templates/sheet/_resources.html` | Resource tracker strip fragment |
| `src/main/resources/templates/sheet/_spell-list.html` | Spell list fragment (grouped by level) |
| `src/main/resources/templates/sheet/_derived-stats.html` | Derived combat stats display fragment |
| `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngineTest.java` | Table-driven derivation tests |
| `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetServiceTest.java` | Service-level unit tests |

### Modified files

| File | Change |
|------|--------|
| `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMember.java` | Add `@OneToOne` to `CharacterSheet` |
| `src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberService.java` | Add sheet-aware derivation: `syncFromSheet()` called when sheet changes; ensure `updatePartyMember()` preserves hand-edited values when no sheet |
| `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java` | Add sheet link in member card, add sheet creation button for sheet-less members |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java` | Add `SheetExportDto`, `ResourceExportDto`, `SpellRefExportDto` inner records |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` | Wire sheets/resources/spells into export/import; implement key-based resolution for species/background/feats |
| `src/main/resources/templates/fragments/navbar.html` | Add "Sheets" link to campaign nav |
| `src/main/resources/templates/party/_card.html` | Show sheet-derived indicators (sheet badge, derived AC/HP) |
| `src/main/resources/templates/party/list.html` | Add sheet overview link |
| `src/main/resources/templates/party/_summary-bar.html` | Derive from sheet when available (no structural change — values are already on PartyMember, just now auto-populated) |
| `src/main/resources/templates/campaigns/detail.html` | Add Sheets section link |
| `src/main/resources/static/css/app.css` | Add sheet-specific styles (stat blocks, override markers, resource trackers, level-up dialog) |
| `src/main/resources/application.properties` | No changes needed |
| `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` | Extend to cover sheets |
| `src/test/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberServiceTest.java` | Add sheet derivation sync tests |

---

## 11. Task Breakdown

### Task 1: CharacterSheet entity, repository, and PartyMember relationship

**Files:**
- Create: `CharacterSheet.java`, `CharacterSheetRepository.java`
- Modify: `PartyMember.java`

- [ ] **Step 1: Create `CharacterSheet` JPA entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheet.java` with the design from §3.1:
- Fields: `id`, `partyMember` (OneToOne), `abilityScores` (CLOB JSON), `classLevels` (CLOB JSON), `proficiencies` (CLOB JSON), `species` (ManyToOne to Species), `background` (ManyToOne to Background), `featRefs` (CLOB JSON), `xp`, `overrides` (CLOB JSON), `hitDiceUsed`
- No `@PrePersist` needed (tied to PartyMember lifecycle)
- Getters and setters for all fields

- [ ] **Step 2: Create `CharacterSheetRepository`**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheetRepository.java`:
- `findByPartyMemberId(UUID) → Optional<CharacterSheet>`
- `findByPartyMember_CampaignId(UUID) → List<CharacterSheet>` (for campaign-level overview)

- [ ] **Step 3: Add relationship to `PartyMember`**

In `PartyMember.java`, add:
```java
@OneToOne(mappedBy = "partyMember", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private CharacterSheet characterSheet;

public CharacterSheet getCharacterSheet() { return characterSheet; }
public void setCharacterSheet(CharacterSheet characterSheet) { this.characterSheet = characterSheet; }
```

- [ ] **Step 4: Add campaign-level settings for leveling mode**

Verify `Campaign.settings` JSON structure supports a `levelingMode` field. If not yet present, the field is stored as a string in the JSON map; no entity change needed. The SheetService reads it from `campaign.getSettings()`.

- [ ] **Step 5: Build and verify**

```bash
mvn compile
```

Verify `ddl-auto=update` creates the `character_sheet` table with proper columns and the FK to `party_member`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheet.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheetRepository.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMember.java
git commit -m "feat(m9): add CharacterSheet entity and PartyMember relationship"
```

---

### Task 2: SheetResource and SheetSpellReference entities + repositories

**Files:**
- Create: `SheetResource.java`, `SheetResourceRepository.java`
- Create: `SheetSpellReference.java`, `SheetSpellReferenceRepository.java`

- [ ] **Step 1: Create `SheetResource` entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResource.java` per §3.2:
- Fields: `id`, `sheet` (ManyToOne), `name`, `maxUses`, `currentUses`, `resetRule` (enum: SHORT_REST, LONG_REST, NEVER)
- Standard getters/setters

- [ ] **Step 2: Create `SheetResourceRepository`**

```java
public interface SheetResourceRepository extends JpaRepository<SheetResource, UUID> {
    List<SheetResource> findBySheetId(UUID sheetId);
    void deleteBySheetId(UUID sheetId);
}
```

- [ ] **Step 3: Create `SheetSpellReference` entity**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReference.java` per §3.3:
- Fields: `id`, `sheet` (ManyToOne), `spell` (ManyToOne to Spell), `prepared`, `sourceClass`

- [ ] **Step 4: Create `SheetSpellReferenceRepository`**

```java
public interface SheetSpellReferenceRepository extends JpaRepository<SheetSpellReference, UUID> {
    List<SheetSpellReference> findBySheetId(UUID sheetId);
    List<SheetSpellReference> findBySheetIdAndSourceClass(UUID sheetId, String sourceClass);
    void deleteBySheetId(UUID sheetId);
}
```

- [ ] **Step 5: Build and verify**

```bash
mvn compile
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResource.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResourceRepository.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReference.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReferenceRepository.java
git commit -m "feat(m9): add SheetResource and SheetSpellReference entities"
```

---

### Task 3: SheetEngine — pure derivation service

**Files:**
- Create: `SheetEngine.java`

This is the most critical task — it contains all the rules math. Follows the design in §4.

- [ ] **Step 1: Create `SheetEngine` class skeleton**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngine.java`:
- Constructor-inject `CharacterClassRepository` and `RuleSectionRepository`
- `ObjectMapper` field for JSON parsing
- `@PostConstruct void initialize()` — builds caches
- Field: `Map<Integer, Integer> proficiencyBonusTable`
- Field: `Map<Integer, int[]> multiclassSlotTable`
- Field: `Map<String, String> classSpellcastingAbilities` (classSourceKey → "str"/"dex"/etc.)
- Field: `Map<String, Map<Integer, int[]>> classSlotTables` (classSourceKey → classLevel → slots[10])

- [ ] **Step 2: Implement `buildProficiencyBonusTable()`**

Find any base class entity, parse its `features` JSON, locate the `PROFICIENCY_BONUS` feature, iterate `data_for_class_table`, build `Map<Integer, Integer>` from level → parsed int from `column_value`.

- [ ] **Step 3: Implement `parseMulticlassSlotTable()`**

Read rule section `"srd-2024_multiclassing_spellcasting"` from `RuleSectionRepository`.
Parse the Markdown table from the `desc` field. The table starts after the line `|Level|1|2|3|4|5|6|7|8|9|`. Regex extraction:
- Split on newlines, find the header row, then parse each data row
- Row format: `| 1 | 2 | — | — | ...` — parse ints, treat `—` (em-dash) as 0
- Build `Map<Integer, int[]>` where key is combined caster level (1–20), value is `int[10]` (index 1..9 = slot count)

**Note for implementation:** Verify the exact character used for empty cells in the stored Markdown. The desc preview showed `\u2014` (em-dash). The parsing must handle this.

- [ ] **Step 4: Implement `buildSpellcastingAbilities()`**

For each class entity that has `SPELL_SLOTS` type features in its `features` JSON:
- Find the `CORE_TRAITS_TABLE` feature
- Parse its `desc` field for the `Primary Ability` table row
- Map the ability string to lowercase identifier
- Store: `Map<String, String>` (classSourceKey → ability)

- [ ] **Step 5: Implement `buildClassSlotTables()`**

For each spellcasting class:
- Parse `features` JSON
- Collect features with `feature_type = "SPELL_SLOTS"`
- From each feature's key, extract the spell level (e.g., `srd-2024_wizard_slots-3rd` → spell level 3)
- From each feature's `data_for_class_table`, map class level → slot count
- Build nested map: class sourceKey → (class level → int[10])

- [ ] **Step 6: Implement `derive(CharacterSheet)` method**

Implement the full derivation per §4.3. Key steps:
1. Parse JSON fields: `abilityScores`, `classLevels`, `proficiencies`, `overrides`, `featRefs`
2. Compute total level, proficiency bonus
3. Compute ability modifiers
4. Compute saving throw bonuses (union of all class save proficiencies + manual proficiencies)
5. Compute skill bonuses (proficiencies from classes + background + manual; expertise doubles)
6. Compute passive scores
7. Compute HP (level 1 max + sum of rolls/averages + CON per level)
8. Compute spell slots (single-class from class tables; multiclass from multiclass table)
9. Compute spell save DC / attack bonus per casting class, take highest
10. Compute classAndLevel string
11. Compute speed from species
12. Compute initiative bonus (dexMod)
13. Compute AC (10 + dexMod)
14. Apply overrides
15. Return `DerivedValues` record

- [ ] **Step 7: Skill-to-ability static map**

Create a `private static final Map<String, String> SKILL_ABILITY_MAP` with the 18 skills per §4.2 item 5.

- [ ] **Step 8: Build and verify**

```bash
mvn compile
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngine.java
git commit -m "feat(m9): add SheetEngine derivation service with compendium-backed tables"
```

---

### Task 4: SheetService — CRUD, level-up, rests, XP

**Files:**
- Create: `SheetService.java`
- Modify: `PartyMemberService.java`

- [ ] **Step 1: Create `SheetService` skeleton**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java`:
- `@Service @Transactional`
- Constructor-inject: `CharacterSheetRepository`, `SheetResourceRepository`, `SheetSpellReferenceRepository`, `PartyMemberRepository`, `SheetEngine`, `CharacterClassRepository`, `SpeciesRepository`, `BackgroundRepository`, `ObjectMapper`

- [ ] **Step 2: Define DTO records**

Inner records in `SheetService`:
- `CreateSheetRequest` (per §5.1)
- `UpdateSheetRequest` (same structure, all fields optional)
- `SheetDto` (full sheet with derived values, resources, spells)
- `LevelUpRequest` (classSourceKey, hpRoll, isAverage)
- `SheetResourceDto` (id, name, maxUses, currentUses, resetRule)
- `SheetSpellDto` (id, spellName, spellLevel, prepared, sourceClass)

- [ ] **Step 3: Implement `createSheet()`**

1. Fetch `PartyMember` by ID, verify no existing sheet
2. Build `CharacterSheet` entity, set JSON fields from request
3. Fetch and set `Species`, `Background` by UUID
4. Save sheet
5. Call `syncToPartyMember()`
6. Return `SheetDto` with derived values

- [ ] **Step 4: Implement `getSheetDto()`**

1. Fetch sheet by ID or by partyMemberId
2. Compute `DerivedValues` via `SheetEngine`
3. Fetch resources and spells
4. Build and return `SheetDto`

- [ ] **Step 5: Implement `updateSheet()`**

Update editable fields: ability scores, proficiencies, species, background, feat refs, overrides, XP. Re-derive and sync to party member.

- [ ] **Step 6: Implement `deleteSheet()`**

1. Delete resources and spell references (via repository delete methods)
2. Delete sheet
3. Re-sync PartyMember (still called with no sheet → fields unchanged from last-derived values)

- [ ] **Step 7: Implement `levelUp()`**

1. Parse `classLevels` from sheet JSON
2. Find or create the entry for `classSourceKey`
3. Increment level by 1
4. Append HP roll (or average) to `hitDieRolls[]`
5. Save, re-derive, sync

- [ ] **Step 8: Implement `shortRest()`**

1. Accept `hitDiceSpent` amount (total, or per-class map for future)
2. Increment `hitDiceUsed` on sheet
3. Reset all resources with `resetRule = SHORT_REST` (set `currentUses = maxUses`)
4. Save, re-derive, sync

- [ ] **Step 9: Implement `longRest()`**

1. Set `hitDiceUsed = max(0, hitDiceUsed - totalHitDice / 2)` (recover half rounded down)
2. Reset all resources with `resetRule = LONG_REST` or `SHORT_REST`
3. Save, re-derive, sync
   - max HP remains at derived value (full HP is the derived max — the engine computes maxHP as derived, and current HP is on PartyMember; long rest restores PartyMember.currentHp? No — current HP is not on CharacterSheet)
   
   **Important:** `currentHp` is on `PartyMember`, not `CharacterSheet`. On long rest, the service should also set `partyMember.currentHp = maxHp` and save the PartyMember. Actually, `PartyMember.maxHp` was set by derivation — we reset current to that value directly here, or better: delegate to PartyMemberService.

   Actually, wait — `PartyMember` doesn't have a `currentHp` field in the current entity. Let me check...

   Looking at the data model: `PartyMember` has `maxHp` but not `currentHp`. Current HP is tracked on `Combatant` during encounters. For long rest, the relevant action is: all spell slots restored, resources reset, used hit dice halved. HP is restored naturally when the next encounter starts (combatants are pre-filled from party members' maxHp). So long rest on the sheet doesn't need to touch any HP field — it just resets resources and hit dice.

- [ ] **Step 10: Implement `awardXp()` and `setLevel()`**

1. `awardXp`: increment `xp` field, check against level thresholds, return whether level-up is needed
2. `setLevel`: for milestone mode, directly update classLevels JSON to set total levels
   - This needs DMs input — which class gets the level? A simpler approach: show a "set total level" input that distributes levels across existing classes (or prompts which class to level). MVP: just set the total level and adjust the first class level entry.

- [ ] **Step 11: Implement `syncToPartyMember()`**

Called after every sheet change:
1. Compute `DerivedValues` via `SheetEngine.derive(sheet)`
2. Copy to PartyMember fields: `ac`, `maxHp`, `initiativeBonus`, `speed`, `passivePerception`, `passiveInsight`, `passiveInvestigation`, `classAndLevel`
3. Save PartyMember

- [ ] **Step 12: Implement resource update**

`updateResource(UUID resourceId, int currentUses)` — simple field update.

- [ ] **Step 13: Implement spell management**

`addSpell()` — create `SheetSpellReference`, verify spell exists in library
`removeSpell()` — delete reference
`togglePrepared()` — flip boolean

- [ ] **Step 14: Build and verify**

```bash
mvn test-compile
```

- [ ] **Step 15: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java
git commit -m "feat(m9): add SheetService with CRUD, level-up, rests, XP awarding"
```

---

### Task 5: SheetController + templates (Thymeleaf UI)

**Files:**
- Create: `SheetController.java`
- Create: `overview.html`, `detail.html`, `_ability-scores.html`, `_level-up-dialog.html`, `_resources.html`, `_spell-list.html`, `_derived-stats.html`
- Modify: `navbar.html`, `party/_card.html`, `party/list.html`, `campaigns/detail.html`, `party/_summary-bar.html`
- Modify: `app.css`

- [ ] **Step 1: Create `SheetController`**

`@Controller @RequestMapping("/campaigns/{campaignId}")`.

Routes per §6.1:
- `GET /sheets` → overview page
- `GET /party/{memberId}/sheet` → detail page
- `GET /party/{memberId}/sheet/create` → create form
- `POST /party/{memberId}/sheet` → create sheet action
- `DELETE /party/{memberId}/sheet` → delete sheet action
- `POST /party/{memberId}/sheet/level-up` → level up
- `POST /party/{memberId}/sheet/rest/short` → short rest
- `POST /party/{memberId}/sheet/rest/long` → long rest
- `PATCH /party/{memberId}/sheet/abilities` → update ability scores
- `PATCH /party/{memberId}/sheet/overrides` → update overrides

Each method follows the existing pattern: add `Campaign` to model via `@ModelAttribute`, delegate to `SheetService`, return Thymeleaf view name or htmx fragment.

- [ ] **Step 2: Create `templates/sheet/overview.html`**

Per §7.1. Uses Thymeleaf layout (extends `fragments/head.html`):
- Campaign context header
- Card grid: each PartyMember with sheet status badge, derived stats (from PartyMember fields — which are synced)
- Member without sheet: shows "Create Sheet" button
- Member with sheet: shows stat summary + "View Sheet" link
- Quick actions: rest batch, XP award form

- [ ] **Step 3: Create `templates/sheet/detail.html`**

Per §7.2. Full sheet display:
- Ability scores in a 2×3 grid with modifiers
- Combat stats section (AC, HP, initiative, speed)
- Saving throws list
- Skills list (18 skills with bonuses)
- Spellcasting section (save DC, attack bonus, slots grid, spell list)
- Resources section
- Features section (rendered from class features JSON)
- Action buttons: Level Up, Short Rest, Long Rest
- Override panel

- [ ] **Step 4: Create `_ability-scores.html` fragment**

Inline-editable grid: 6 inputs, one per ability. Each input is wrapped in an htmx form that patches to `PATCH .../sheet/abilities`. After save, the fragment is re-rendered with updated modifiers.

- [ ] **Step 5: Create `_level-up-dialog.html` fragment**

Modal dialog (using Alpine.js for show/hide, not a separate page):
- Class selector dropdown (existing classes + "Add new class")
- HP mode: radio "Roll" with text input, or "Average" (pre-computed: ceil(hitDieMax/2 + CON))
- Features gained at new level (read-only, fetched from compendium)
- Submit button posts to `POST .../sheet/level-up`, htmx swaps the detail page content

- [ ] **Step 6: Create `_resources.html` fragment**

Per resource: name, `currentUses / maxUses`, +/- buttons. Each button triggers an htmx request to update the resource. Rendered inside the detail page.

- [ ] **Step 7: Create `_spell-list.html` fragment**

Spells grouped by level. Each spell: name, source class (for multiclass), prepared toggle checkbox. "Add spell" form with spell name autocomplete (or simple text input with spell lookup for MVP).

- [ ] **Step 8: Create `_derived-stats.html` fragment**

Read-only display of derived combat stats: AC, max HP, initiative, speed, passive scores. Each value is marked `[override]` visually if an override is active.

- [ ] **Step 9: Modify `navbar.html`**

Add "Sheets" link in the campaign navigation section (alongside Maps, Encounters, Notes, etc.):
```html
<li><a th:href="@{/campaigns/{id}/sheets(id=${campaign.id})}">Sheets</a></li>
```

- [ ] **Step 10: Modify `party/_card.html`, `party/list.html`, `campaigns/detail.html`**

- `party/_card.html`: Add sheet badge (check if member has sheet), link to sheet detail
- `party/list.html`: Add "Sheets" link to campaign-level sheet overview
- `campaigns/detail.html`: Add Sheets section

- [ ] **Step 11: Verify `party/_summary-bar.html`**

No structural change needed — the summary bar reads from `PartyMember` fields directly, which are already synced by the sheet engine. Verify that AC, passives, etc. update correctly after sheet derivation.

- [ ] **Step 12: Add CSS styles**

In `app.css`, add styles for:
- `.sheet-stat` — derived stat display with override marker
- `.sheet-ability-grid` — 2×3 ability score grid
- `.sheet-skill-list` — skill rows with proficiency/expertise indicators
- `.sheet-resource` — resource counter with +/- buttons
- `.sheet-override` — visual indicator for overridden values (e.g., dashed underline, small icon)
- `.sheet-level-up-dialog` — modal styles
- `.sheet-spell-group` — spell grouping by level

Follow existing design system: CSS custom properties for colors, dark-first theme, consistent spacing.

- [ ] **Step 13: Build and verify**

```bash
mvn compile
```

Start app, navigate to a campaign's sheets overview, verify page renders. Create a sheet for a party member, verify detail page.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetController.java \
        src/main/resources/templates/sheet/ \
        src/main/resources/templates/fragments/navbar.html \
        src/main/resources/templates/party/_card.html \
        src/main/resources/templates/party/list.html \
        src/main/resources/templates/campaigns/detail.html \
        src/main/resources/static/css/app.css
git commit -m "feat(m9): add sheet Thymeleaf UI — overview, detail, level-up, rests"
```

---

### Task 6: SheetApiController (REST endpoints)

**Files:**
- Create: `SheetApiController.java`

- [ ] **Step 1: Create `SheetApiController`**

`@RestController @RequestMapping("/api/v1/campaigns/{campaignId}")`.

Routes per §6.2:
- `GET /party/{memberId}/sheet` → `SheetDto` JSON
- `PUT /party/{memberId}/sheet` → create or update (upsert)
- `POST /party/{memberId}/sheet/level-up` → leveled-up `SheetDto`
- `POST /party/rest?type=SHORT|LONG&members=id1,id2` → batch rest results
- `POST /party/xp` → award XP, return per-member results
- Resources endpoints
- Spells endpoints

Each method follows the existing REST controller pattern (return `ResponseEntity`, use `ProblemDetail` for errors).

The batch rest endpoint accepts a `members` query parameter (comma-separated UUIDs) and a `type` parameter (SHORT or LONG). Applies the rest action to each member's sheet in a single transaction.

- [ ] **Step 2: Build and verify**

```bash
mvn compile
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetApiController.java
git commit -m "feat(m9): add SheetApiController with REST endpoints"
```

---

### Task 7: Tests

**Files:**
- Create: `SheetEngineTest.java`
- Create: `SheetServiceTest.java`
- Modify: `CampaignImportExportRoundTripTest.java`, `PartyMemberServiceTest.java`, `PlayerSafeProjectionServiceTest.java`

- [ ] **Step 1: Create `SheetEngineTest`**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngineTest.java`:

**Test setup:** Uses `@SpringBootTest` with real repositories (H2 in-memory) seeded with test compendium data. The engine's `@PostConstruct` initializes caches from the seeded data.

Alternatively (and preferred for speed): a plain `@ExtendWith(MockitoExtension.class)` test that:
1. Creates a `SheetEngine` with mocked repositories (returning known JSON for classes and rules)
2. Manually calls `initialize()` after setting up mock returns
3. Tests derivation functions in isolation

Use the `@SpringBootTest` approach for integration-level tests and `@MockitoExtension` for pure unit tests. The table-driven tests can be pure unit tests if the engine's compendium lookups are isolated behind an interface.

**Required test cases (minimum):**
- [ ] Level 1 fighter — verify HP = max(d10) + CON
- [ ] Level 5 fighter — verify PB = +3, extra attack shown as feature
- [ ] Level 3 wizard — verify spell slots (4×1st, 2×2nd), save DC (8+2+INT), attack bonus (2+INT)
- [ ] Level 5 cleric — verify slots, WIS-based DC
- [ ] Level 4 fighter / level 3 wizard multiclass — multiclass HP correct, spell slots from multiclass table
- [ ] Level 5 paladin — half-caster single class slots
- [ ] Level 2 paladin / level 3 sorcerer multiclass — combined caster level = 1 (half 2) + 3 = 4
- [ ] Override: AC override takes precedence
- [ ] Override: skill override takes precedence
- [ ] Species speed: dwarf 25, wood elf 35 (if in data)
- [ ] Proficient saves from class data
- [ ] Skill proficiency from class core traits + background

Each test must include a comment documenting which compendium source produced each expected value.

- [ ] **Step 2: Create `SheetServiceTest`**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetServiceTest.java`:

Use `@SpringBootTest` with H2 and a test campaign + party member. Tests:
- [ ] Create sheet → verify saved, derived values on DTO correct
- [ ] Update ability scores → derived values recalculated
- [ ] Level up → HP increases, features list updated
- [ ] Short rest → hit dice used increases, SHORT_REST resources reset
- [ ] Long rest → hit dice used halves, LONG_REST resources reset
- [ ] Award XP → XP field updated, level-up flag returned
- [ ] Milestone setLevel → class levels JSON updated
- [ ] Override AC → derived AC returns override, PartyMember.ac reflects it
- [ ] Delete sheet → sheet gone, PartyMember retains last-derived values
- [ ] syncToPartyMember → all PartyMember combat fields match derived values
- [ ] Sheet-less PartyMember → getSheetDto returns 404, PartyMember fields unchanged

- [ ] **Step 3: Extend `CampaignImportExportRoundTripTest`**

- [ ] Export campaign with sheets → JSON contains `sheet` field under each PartyMember
- [ ] Re-import → sheets reconstructed with correct data
- [ ] Import with unresolvable species key → FK null, import succeeds with warning
- [ ] Import of campaign with no sheets → works (sheet is optional)

- [ ] **Step 4: Extend `PartyMemberServiceTest`**

- [ ] Create PartyMember → verify no sheet (optional)
- [ ] Add sheet → PartyMember fields derived
- [ ] Update sheet → PartyMember fields re-derived
- [ ] Delete sheet → PartyMember fields unchanged from last derivation

- [ ] **Step 5: Verify `PlayerSafeProjectionServiceTest`**

- [ ] Confirm sheet data never appears in player-safe token projection (tokens reference PartyMember fields only)
- [ ] The existing test for DM-only data filtering passes with sheets present

- [ ] **Step 6: Run full test suite**

```bash
mvn test
```

All tests must pass before considering M9 complete.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngineTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetServiceTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberServiceTest.java
git commit -m "feat(m9): add tests for SheetEngine, SheetService, and export/import round-trip"
```

---

## 12. Verification Checklist (before marking M9 done)

- [ ] `mvn compile` passes (all Java compiles)
- [ ] `mvn test` passes (all tests, including M1-M8 regression)
- [ ] Create a PartyMember → create a sheet: ability scores entered, modifiers computed correctly
- [ ] Level up a fighter from 1 to 5: HP increments correctly per level, PB changes at 5
- [ ] Create a wizard: spell slots match 2024 progression, save DC and attack bonus correct
- [ ] Multiclass fighter 4 / wizard 3: spell slots from multiclass table, HP correct
- [ ] Short rest: hit dice spent, SHORT_REST resources reset
- [ ] Long rest: half used hit dice recovered, all resources reset
- [ ] Set an AC override: derived AC reflects override, PartyMember.ac synced
- [ ] PartyMember with sheet shows derived values in roster, summary bar
- [ ] PartyMember without sheet works identically to M2 behavior
- [ ] Campaign export includes sheets; re-import reconstructs them correctly
- [ ] All derived numbers verified against compendium data, not from memory (provenance rule)
- [ ] No sheet data leaks into player-safe projection
- [ ] Code compiles with `tools.jackson` (Jackson 3) imports only
- [ ] `ddl-auto=update` handles new tables without data loss on existing DB

---

## 13. Implementation Notes

### 13.1 HP formula detail

```
For each class in classLevels:
    dieType = hitDie from CharacterClass entity (d6→6, d8→8, d10→10, d12→12)
    for i in 1..classLevels[i].level:
        if i == 1:
            hp += dieType          // max at level 1
        else:
            hp += hitDieRolls[i-2] // rolls are stored for levels 2+
        hp += conMod               // CON mod per level
```

The `hitDieRolls` array for a level-N character has N-1 entries (level 1 doesn't roll).

### 13.2 Feature rendering

Class features are displayed as read-only text from the compendium. The features JSON has the feature name, description, and `gained_at` levels. On the sheet detail page, features are filtered: show only features whose `gained_at` includes a level ≤ the character's level in that class. Features are grouped by class.

### 13.3 Spell slot display

The UI shows a slot tracker: for each spell level (1–9), show "used / available" with +/- buttons. Used count is per-sheet, stored in a simple JSON map on the CharacterSheet as `spellSlotsUsed` (or as a simple `String spellSlotsUsed` JSON column holding `{"1":0,"2":1,"3":0,...}`).

Actually, this field is NOT in the entity design above. We need to add it. Let me add it as `spellSlotsUsed` (CLOB JSON) on `CharacterSheet`:

```java
@Column(columnDefinition = "CLOB")
private String spellSlotsUsed; // JSON: {"1":0,"2":1,"3":0,...}
```

This is reset on long rest. Warlock pact slots are stored in the same map under the pact slot level.

**Implementation correction:** Add `spellSlotsUsed` field to `CharacterSheet` entity during Task 1.

### 13.4 Pact Magic (Warlock)

Warlocks use Pact Magic, not Spellcasting. They have fewer slots that are always at max level and recover on short rest. The engine treats warlock slots separately:
- Warlock slots are derived from the warlock class's SPELL_SLOTS data (for single-class warlocks)
- In multiclass, warlock slots remain separate — the multiclass table does not include warlock levels
- Warlock slot level = max spell level available at current warlock level
- Warlock slot count = from SPELL_SLOTS data for that class at that level

### 13.5 2024 rules version note

The 2024 rules (5.5e, SRD 5.2) have several changes from 2014 rules that affect derivation:
- Proficiency bonus progression is unchanged (same table)
- Multiclass spell slot table is unchanged
- Half-caster multiclass contribution: "half your levels (round UP)" — same as 2014
- **Important:** Paladins get spellcasting at level 1 (not level 2 as in 2014)
- All subclasses are gained at level 3

The SPELL_SLOTS data in the compendium reflects the 2024 progression directly, so no special handling is needed — the engine reads what the data says.

### 13.6 Milestone mode UI

When campaign leveling mode is MILESTONE:
- XP fields are hidden on the sheet detail
- The "Award XP" button is hidden or disabled
- "Level Up" button triggers a dialog that asks which class to level (no XP threshold check)
- Level-up still shows new features at the new level
- The sheet overview shows total level without XP number

---

*Plan written 2026-07-08 for M9: Character Sheets. References SPEC.md §4.12 and data model §3.*
