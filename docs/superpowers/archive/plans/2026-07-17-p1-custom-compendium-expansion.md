# Custom Compendium Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver delivery item 7 of the all-in-one DM readiness specification: every existing library/compendium content type supports bundled SRD, user-global custom, and campaign-scoped custom ownership with visible provenance, shared content identifiers, package-v2 export of campaign-scoped dependencies, promote/delete rules, and complete round-trip fidelity.

**Architecture:** Extend the proven StatBlock ownership pattern (`Source {SRD, CUSTOM}` + optional `campaign` FK) to every seeded library type. Add a shared `ContentProvenance` embeddable and a typed `LicenseClassification` enum. Generalize catalog/package reference resolution so party sheets, treasury, encounters, and scenes can reference campaign-owned custom content without inventing a second identity scheme. Expand the existing `LibrarySectionAdapter` and package-v2 manifest with optional custom-content arrays for each type. Keep SRD seed data read-only; custom create/clone/edit/promote flows mirror StatBlock. Unsupported rules mechanics (disease, curse, vehicle, trap automation, rollable tables) store as typed generic `RULE` entries rather than inventing new mechanical engines reserved for later delivery items.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Jackson, Thymeleaf, HTMX, Alpine, JSON Schema draft 2020-12, Maven Wrapper, JUnit/AssertJ/MockMvc, Playwright smoke coverage.

## Global Constraints

- Master design Workstream E (§10) and delivery item 7 are authoritative. Do not start character-sheet completion (item 8), encounter/map depth (item 9), documentation/agent SDK release (item 10), or P3 world/travel/tables/fog/audio work in this plan.
- Bundled SRD rows remain read-only. Never mutate SRD seed content through create/edit/delete/promote paths.
- Campaign export includes every **campaign-scoped** custom dependency. User-global custom content is **not** embedded in a campaign package unless it is cloned into the campaign first. Catalog/SRD references remain `scope: CATALOG`.
- Every package-owned custom entry has a stable package key matching `^[a-z0-9][a-z0-9._-]{0,99}$` and uses `ContentReference.Scope.PACKAGE`. Display names are presentation only.
- Provenance is visible in the DM UI and exportable. Player-safe projections never include provenance, converter identity, license classification, or source hashes unless the DM explicitly puts that text into a handout body.
- Promotion of campaign-scoped content to user-global never rewrites source attribution or provenance fields.
- Existing v1 packages and v2 packages without the new custom arrays must still validate and import with empty custom non-statblock collections.
- Use a reviewed Flyway migration (`V7__…`). Do not rely on Hibernate schema generation.
- Keep offline/local-first: no CDN, no new frontend build chain, no network generative provenance services.
- Complete every task with focused tests before moving on. Prefer extending existing library, package-v2, and round-trip tests over parallel frameworks.

---

## Audit result: what is already implemented

The master design delivery table (spec §22) and repository evidence as of 2026-07-17:

| # | Delivery item | Status | Evidence |
|---|---|---|---|
| 1 | P0 runtime reliability | **Implemented** | Quick notes, destination registry, visible/retryable errors, package asset safety, honest difficulty estimate; plans `2026-07-15-p0-interaction-integrity.md`, `2026-07-16-p0-runtime-reliability-completion.md`; capability matrix `SUPPORTED`. |
| 2 | Campaign contract v1 repair | **Implemented** | Executable v1 schema, closed objects, unified dry-run/import, unresolved-ref blocking, fixtures under `src/test/resources/campaigns/v1/`. |
| 3 | Package v2 foundation | **Implemented** | ZIP/JSON reader/writer, safety limits, stable keys, typed catalog snapshot, preview, migrations, staged assets, atomic import; migrations V3+. |
| 4 | Complete round-trip | **Implemented** | Module section adapters, flagship fixtures (`minimal`, `feature-complete`, `published-adventure-shaped`), semantic snapshot/compare, combat-log/dice opt-outs. |
| 5 | Session cockpit | **Implemented** | `/campaigns/{id}/session`, lifecycle, workspace map selection, story/encounter rails, plan, draft session log, keyboard actions, session package adapter; migration V4. |
| 6 | Structured adventure/quest model | **Implemented** | Scene sections/checks/participants/transitions/links, quests/objectives/dependencies, source annotations, session objective changes; migration V5–V6; fixtures `structured-adventure-quest.dmcampaign`; capability rows `SUPPORTED`. |
| **7** | **Custom compendium expansion** | **Not started (this plan)** | Only `StatBlock` has `Source` + campaign ownership + create/clone/edit/promote + package export. Spells, conditions, rules, equipment, magic items, classes, species, backgrounds, and feats are SRD-global tables with unique `source_key` and read-only library UI. No shared provenance model. Party import resolves catalog refs by `sourceKey` only. |
| 8–11 | Character completion, encounter/map depth, docs/agent SDK, P3 | Planned | Out of scope. |

**Current custom-content boundary (capability matrix):**

| Capability | Status | Notes |
|---|---|---|
| Custom campaign-scoped **statblocks** | `SUPPORTED` | Create/edit/clone/promote, package keys, `customStatBlocks` round-trip. |
| Campaign-scoped non-statblock custom content | `UNSUPPORTED` | Explicit item-7 gap in `docs/campaign-capabilities.md`. |
| Provenance on imported custom content | `PARTIAL` | `SourceAnnotation` covers conversion confidence on scenes/quests; not entry-level provenance (title/license/converter/hash). |
| Typed catalog snapshot | `SUPPORTED` | SRD-only entries for all current library types via `CampaignCatalogService`. |

**What already works and must not regress:**

- SRD seed services and `srd-5.2-*.json` / catalog snapshot hash.
- `StatBlockService` custom create/update/delete/clone/promote.
- `LibrarySectionAdapter` export/import of `customStatBlocks`.
- `StatBlockReferenceResolver` CATALOG vs PACKAGE resolution.
- `ContentDestinationRegistry` library destinations and command-palette search.
- Sheet/party/treasury catalog references to SRD spells, classes, species, backgrounds, feats, magic items.

**Hard gaps this plan closes:**

1. Non-statblock types cannot be custom or campaign-owned.
2. Unique global `source_key` columns block multiple custom entries and campaign isolation.
3. Sheets/party/treasury cannot resolve `PACKAGE` refs for non-statblock content.
4. Export does not include custom spells/items/classes/etc., so dependency closure is incomplete.
5. No first-class provenance on custom entries.
6. Library UI is read-only for every tab except monsters.
7. Promote/delete reference rules exist only for statblocks.

---

## Delivery item 7 acceptance contract

- [ ] Every library type in the existing library tabs supports **SRD** (read-only), **user-global CUSTOM** (`campaign == null`), and **campaign-scoped CUSTOM** (`campaign != null`).
- [ ] Covered types: `STATBLOCK`, `SPELL`, `CONDITION`, `RULE`, `EQUIPMENT_ITEM`, `MAGIC_ITEM`, `CLASS` (including subclasses via existing `subclassOf`), `SPECIES`, `BACKGROUND`, `FEAT`.
- [ ] Unsupported rules content (disease, curse, vehicle, trap table text, etc.) can be stored as a campaign-scoped custom `RULE` with free-form body and provenance, without inventing mechanical automation fields.
- [ ] Every non-original custom entry can store provenance: source title, edition/version, locator, license classification, import timestamp, converter identity/version, optional source hash, extraction confidence.
- [ ] Provenance is shown on DM library detail views and survives package export/import; player payloads never include it.
- [ ] Create, clone, edit, delete, list/search with scope filter, and promote-to-global work for each custom type.
- [ ] Promote clears `campaign` only; provenance and license remain unchanged.
- [ ] Delete of campaign custom content detaches or blocks according to documented reference rules (mirror/extend `SceneRefCleaner` and any sheet/treasury holders).
- [ ] Campaign package v2 exports all campaign-scoped custom entries of every type; sheets/encounters/treasury/scenes that reference them use `scope: PACKAGE` keys.
- [ ] Catalog/SRD references remain `scope: CATALOG` and are not duplicated into the package.
- [ ] Import dry-run rejects unresolved package/catalog refs, duplicate keys, SRD key collisions on custom entries that claim an SRD sourceKey, and invalid provenance enums.
- [ ] Flagship fixture(s) exercise at least one campaign-scoped custom entry per content type, package references from a sheet and treasury assignment, and provenance fields; round-trip semantic compare passes.
- [ ] Existing fixtures without the new arrays remain valid (arrays default to empty).
- [ ] Capability matrix marks campaign-scoped non-statblock custom content `SUPPORTED` only after the gates below pass.

---

## Enum and ownership contract

These values are authoritative for Java enums, schema, docs, and tests. Persist with `@Enumerated(EnumType.STRING)`.

```java
// Shared by all library entities (replace StatBlock.Source with this shared type over time).
public enum ContentSource {
    SRD,    // bundled seed; read-only; catalog-addressable
    CUSTOM  // user-created or imported; editable; package- or global-scoped
}

// Who may redistribute / how the entry was obtained. Advisory for the DM; never auto-redacts.
public enum LicenseClassification {
    ORIGINAL,              // DM-authored homebrew with no external source claim
    SRD,                   // derived from SRD (usually after clone)
    OGL_COMPATIBLE,        // claimed OGL/compatible open content
    THIRD_PARTY,           // third-party material user has rights to use locally
    NON_REDISTRIBUTABLE,   // keep local; export still allowed as user-owned package
    UNKNOWN
}

// Reuse existing conversion confidence for provenance extraction quality.
// SourceAnnotationConfidence: HIGH, MEDIUM, LOW, UNKNOWN
```

**Ownership matrix:**

| `source` | `campaign` | Meaning | Package export | Catalog addressable |
|---|---|---|---|---|
| `SRD` | `null` | Bundled seed | Never (referenced by CATALOG) | Yes |
| `CUSTOM` | `null` | User-global library | Never (unless later cloned into a campaign) | No |
| `CUSTOM` | non-null | Campaign-scoped | Yes, in type-specific custom array | No |

**Identity rules:**

- Package identity: `CampaignPackageKey` binding for campaign-scoped CUSTOM rows.
- Catalog identity: `(type, ruleset=SRD_5_2, sourceKey)` for SRD rows only.
- Local `sourceKey` on CUSTOM rows is a stable slug for UI/search and optional cross-campaign promotion identity; it is **not** a catalog key. Package refs never use it.
- Uniqueness after migration:
  - SRD: unique `source_key` among rows with `source = 'SRD'`.
  - Global CUSTOM: unique `source_key` among rows with `source = 'CUSTOM' AND campaign_id_fk IS NULL` (application-enforced if H2 partial unique indexes are awkward; document the chosen approach and test it).
  - Campaign CUSTOM: unique `(campaign_id_fk, source_key)` among custom rows for that campaign.

**Provenance embeddable fields (nullable unless noted):**

| Field | Type | Notes |
|---|---|---|
| `sourceTitle` | string(255) | Book/site title |
| `editionVersion` | string(100) | Edition or version string |
| `sourceLocator` | string(500) | Page/section URL fragment |
| `licenseClassification` | enum | Default `UNKNOWN` for imports without data; `ORIGINAL` for DM create |
| `importedAt` | Instant | Set on package import; null for local creates unless set |
| `converterId` | string(100) | Converter name |
| `converterVersion` | string(50) | Converter version |
| `sourceHash` | string(128) | Optional content hash |
| `extractionConfidence` | enum | Reuse `SourceAnnotationConfidence` |

Unresolved field-level conversion issues continue to use existing `SourceAnnotation` rows with `ownerType` matching the content type and `ownerId` the entity UUID. Do not duplicate that system inside provenance.

---

## File map

| Area | Create | Modify |
|---|---|---|
| Migration | `src/main/resources/db/migration/V7__add_custom_compendium_ownership.sql` | `FlywayMigrationTest`, `FlywayLegacyUpgradeTest` |
| Shared model | `library/data/ContentSource.java`, `LicenseClassification.java`, `ContentProvenance.java` | All library entities + repositories |
| Reference resolution | `library/packagev2/LibraryContentReferenceResolver.java` (generalize) | `StatBlockReferenceResolver` (delegate or replace), `PartySectionAdapter`, treasury/encounter adapters that resolve catalog-only today, `SheetEngine`/`SheetService` lookups that use `findBySourceKey` alone |
| Services | Shared helpers in `library/service/CustomContentSupport.java` (slug, ownership checks, provenance apply) | Every `*Service` under `library/service/` for create/update/delete/clone/promote/search-with-scope |
| Package v2 | DTO records on `CampaignManifestV2` for each custom array; schema `$defs` | `LibrarySectionAdapter`, `CampaignManifestAssembler`, semantic validator, snapshot/comparator, preview counts, `LegacyV1ToV2Migration` defaults |
| Fixtures | Extend `feature-complete` and/or add `custom-compendium.dmcampaign` | Existing v2 fixtures stay valid with empty arrays |
| UI | Forms/fragments for each type's create/edit; provenance panel fragment | `LibraryController`, templates under `library/`, list tab filters for source/scope |
| Destinations/search | — | `ContentDestinationRegistry` detail routes for non-statblock custom IDs where filtered-tab-only destinations are insufficient; `CommandPaletteService` ranking already searches all types — ensure custom rows are included |
| Docs | — | `docs/campaign-format-v2.md`, `docs/campaign-capabilities.md`, master spec §22 row 7 after release |
| Tests | Ownership/provenance persistence tests; package adapter tests; reference resolution tests; controller contract tests; round-trip fixture tests | Existing library, party, cascade-delete, complete-round-trip, palette, player-safety tests |

**Manifest arrays (all optional with default `[]` on assemble; required keys stay schema-compatible):**

```text
customStatBlocks      // already required array (may be empty)
customSpells
customConditions
customRules
customEquipment
customMagicItems
customClasses
customSpecies
customBackgrounds
customFeats
```

Keep `customStatBlocks` required as today so existing schema consumers do not break. New arrays: required in schema as arrays (may be empty) **or** optional with assembler always emitting `[]` — pick one approach and lock it in Task 3 schema tests. Recommendation: **required arrays defaulting to `[]`** for deterministic export, matching `customStatBlocks`.

**Adapter order:** keep `LibrarySectionAdapter` at order `200` (before party/encounters that may reference library content). Import registers package keys before party section resolves PACKAGE refs.

---

### Task 1: Schema migration and shared ownership model

**Files:**
- Create: `src/main/resources/db/migration/V7__add_custom_compendium_ownership.sql`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/ContentSource.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/LicenseClassification.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/ContentProvenance.java`
- Modify: `Spell`, `Condition`, `RuleSection`, `EquipmentItem`, `MagicItem`, `CharacterClass`, `Species`, `Background`, `Feat`, and `StatBlock` entities
- Modify: corresponding repositories
- Modify: `StatBlock.Source` — migrate call sites to `ContentSource` (delete nested enum after compile-clean)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/library/data/CustomContentOwnershipPersistenceTest.java`
- Test: update Flyway upgrade tests

**Interfaces:**
- Produces: every library entity exposes `ContentSource getSource()`, `Campaign getCampaign()`, `ContentProvenance getProvenance()` / setters; repositories can query by campaign and by SRD sourceKey.

- [ ] **Step 1: Write the failing persistence test**

```java
@DataJpaTest
class CustomContentOwnershipPersistenceTest {

    @Autowired SpellRepository spells;
    @Autowired CampaignRepository campaigns;
    @Autowired TestEntityManager em;

    @Test
    void campaignScopedCustomSpellRoundTripsOwnershipAndProvenance() {
        Campaign campaign = campaigns.save(newCampaign("Provenance Campaign"));
        Spell spell = new Spell();
        spell.setSource(ContentSource.CUSTOM);
        spell.setCampaign(campaign);
        spell.setSourceKey("homebrew-arc-bolt");
        spell.setName("Arc Bolt");
        spell.setLevel(1);
        spell.setSchool("Evocation");
        spell.setCastingTime("1 action");
        spell.setRange("60 feet");
        spell.setComponents("V, S");
        spell.setDuration("Instantaneous");
        spell.setDescription("A crackling bolt.");
        spell.setRitual(false);
        spell.setConcentration(false);
        ContentProvenance p = new ContentProvenance();
        p.setSourceTitle("Homebrew Codex");
        p.setEditionVersion("1.0");
        p.setSourceLocator("p.12");
        p.setLicenseClassification(LicenseClassification.ORIGINAL);
        p.setExtractionConfidence(SourceAnnotationConfidence.HIGH);
        spell.setProvenance(p);

        Spell saved = spells.saveAndFlush(spell);
        em.clear();

        Spell loaded = spells.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(loaded.getCampaign().getId()).isEqualTo(campaign.getId());
        assertThat(loaded.getProvenance().getSourceTitle()).isEqualTo("Homebrew Codex");
        assertThat(loaded.getProvenance().getLicenseClassification())
                .isEqualTo(LicenseClassification.ORIGINAL);
    }

    @Test
    void srdSpellRemainsCatalogAddressableWithoutCampaign() {
        // seed or insert SRD row; assert source=SRD, campaign=null, findBySourceAndSourceKey works
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=CustomContentOwnershipPersistenceTest test
```

Expected: FAIL (types/columns missing).

- [ ] **Step 3: Add enums and embeddable**

```java
package dev.hendrikhoemberg.dmhelper.library.data;

public enum ContentSource { SRD, CUSTOM }

public enum LicenseClassification {
    ORIGINAL, SRD, OGL_COMPATIBLE, THIRD_PARTY, NON_REDISTRIBUTABLE, UNKNOWN
}

@Embeddable
public class ContentProvenance {
    @Column(name = "prov_source_title", length = 255)
    private String sourceTitle;
    @Column(name = "prov_edition_version", length = 100)
    private String editionVersion;
    @Column(name = "prov_source_locator", length = 500)
    private String sourceLocator;
    @Enumerated(EnumType.STRING)
    @Column(name = "prov_license", length = 30)
    private LicenseClassification licenseClassification;
    @Column(name = "prov_imported_at")
    private Instant importedAt;
    @Column(name = "prov_converter_id", length = 100)
    private String converterId;
    @Column(name = "prov_converter_version", length = 50)
    private String converterVersion;
    @Column(name = "prov_source_hash", length = 128)
    private String sourceHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "prov_confidence", length = 20)
    private SourceAnnotationConfidence extractionConfidence;
    // getters/setters
}
```

- [ ] **Step 4: Write Flyway V7**

For each of `spell`, `condition`, `rule_section`, `equipment_item`, `magic_item`, `character_class`, `species`, `background`, `feat`:

1. Add `source` enum/`varchar` NOT NULL DEFAULT `'SRD'`.
2. Add `campaign_id_fk uuid` NULL FK → `campaign(id)` ON DELETE CASCADE (campaign delete removes campaign-scoped custom rows).
3. Add provenance columns listed above.
4. Drop the global UNIQUE constraint on `source_key`.
5. Backfill: all existing rows `source = 'SRD'`, provenance null, campaign null.
6. Create a unique index supporting SRD catalog keys, e.g. unique index on `source_key` where `source = 'SRD'` if the DB supports filtered indexes; otherwise enforce SRD uniqueness in seed services + a repository check and document that CUSTOM keys are application-scoped.

For `stat_block`:

1. Keep existing `source` column values (`SRD`/`CUSTOM`) compatible with `ContentSource`.
2. Add the same provenance columns.
3. Map entity field from nested `Source` to shared `ContentSource`.

Also add indexes: `(campaign_id_fk, name)` or `(campaign_id_fk, source_key)` for campaign library queries.

- [ ] **Step 5: Update entities and repositories**

Example repository methods (repeat per type):

```java
Optional<Spell> findBySourceAndSourceKey(ContentSource source, String sourceKey);
List<Spell> findByCampaignIdOrderByNameAsc(UUID campaignId);
List<Spell> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);
boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);
boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
```

Update every seed service: when creating SRD rows set `source = ContentSource.SRD` explicitly.

- [ ] **Step 6: Run persistence + Flyway tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=CustomContentOwnershipPersistenceTest,FlywayMigrationTest,FlywayLegacyUpgradeTest,CompendiumSeedServiceTest,SpellSeedServiceTest,CharacterClassSeedDataTest,StatBlockServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V7__add_custom_compendium_ownership.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/library \
  src/test/java/dev/hendrikhoemberg/dmhelper/library \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config
git commit -m "$(cat <<'EOF'
feat: add ownership and provenance columns for all library types

Unify ContentSource across the compendium and prepare campaign-scoped
custom content beyond statblocks.
EOF
)"
```

---

### Task 2: Custom content services (CRUD, clone, promote, delete rules)

**Files:**
- Create: `library/service/CustomContentSupport.java`
- Create: `library/service/LibraryReferenceCleaner.java` (or extend `SceneRefCleaner` + new sheet/treasury detach)
- Modify: `SpellService`, `ConditionService`, `RuleSectionService`, `EquipmentItemService`, `MagicItemService`, `CharacterClassService`, `SpeciesService`, `BackgroundService`, `FeatService`, `StatBlockService`
- Test: `library/service/CustomSpellServiceTest.java` (pattern exemplar) plus one promote/delete test covering reference detach

**Interfaces:**
- Consumes: Task 1 ownership fields.
- Produces: service methods with identical semantics across types:

```java
// Exemplar — mirror for each type with type-specific fields.
Spell createCustom(UUID campaignIdOrNull, SpellWrite request, ContentProvenance provenanceOrNull);
Spell updateCustom(UUID id, SpellWrite request, ContentProvenance provenanceOrNull);
Spell cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName);
Spell promoteToGlobal(UUID id); // campaign -> null only
void deleteCustom(UUID id);     // rejects SRD; runs reference cleanup
List<Spell> search(ContentSource sourceOrNull, UUID campaignIdOrNull, String textOrNull, /* type filters */);
```

**Reference rules (document in service Javadoc and tests):**

| Action | Behavior |
|---|---|
| Delete campaign CUSTOM with no inbound refs | Delete row + package key binding |
| Delete with inbound sheet/encounter/treasury/scene refs | Prefer detach-to-null or block with actionable error — **choose block with message listing dependency counts** for safety (sheets must not silently lose class/spell identity). Statblock already detaches scene links; keep that. For hard required sheet fields (class levels), **block delete**. |
| Promote | `campaign = null`; keep provenance; rebind uniqueness as global custom; drop campaign package key binding (entity leaves the campaign) |
| Clone SRD → custom | Copy fields; `source=CUSTOM`; default provenance `license=SRD`, `sourceTitle` from catalog name; new sourceKey slug |

- [ ] **Step 1: Failing service tests for spell create/clone/promote/delete**

```java
@Test
void createCampaignSpellRejectsBlankNameAndSrdKeyCollision() { … }

@Test
void promoteClearsCampaignAndKeepsProvenance() { … }

@Test
void deleteBlockedWhenSheetReferencesSpell() { … }

@Test
void cannotMutateSrdSpell() { … }
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=CustomSpellServiceTest test
```

- [ ] **Step 3: Implement `CustomContentSupport`**

```java
@Component
public class CustomContentSupport {
    public String slugify(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public void assertCustom(ContentSource source) {
        if (source != ContentSource.CUSTOM) {
            throw new IllegalArgumentException("Only custom content can be modified");
        }
    }

    public ContentProvenance defaultForCreate(LicenseClassification license) {
        ContentProvenance p = new ContentProvenance();
        p.setLicenseClassification(license != null ? license : LicenseClassification.ORIGINAL);
        p.setExtractionConfidence(SourceAnnotationConfidence.HIGH);
        return p;
    }

    public ContentProvenance defaultForSrdClone() {
        ContentProvenance p = defaultForCreate(LicenseClassification.SRD);
        return p;
    }
}
```

- [ ] **Step 4: Implement service methods on each library service**

Keep field mapping explicit (no reflection magic). For `RuleSection`, allow custom rules with free-form `body`, optional `parentKey`/`sortOrder`, and `ruleset` defaulting to `SRD_5_2` or `CUSTOM` string — use `"CUSTOM"` for homebrew rules so catalog filters stay clean.

Wire `LibraryReferenceCleaner` to:

- scan sheet spell refs, feat JSON, class levels JSON, species/background FKs;
- scan treasury magic item / equipment refs if stored by sourceKey;
- scan scene participant/statblock-style links if any non-statblock content types appear;
- delete `CampaignPackageKey` bindings for the entity.

- [ ] **Step 5: Run focused service tests + existing StatBlockServiceTest**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=CustomSpellServiceTest,StatBlockServiceTest,LibraryReferenceCleanerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: add create, clone, promote, and delete for custom library content

Mirror the statblock ownership workflow across spells and the other
compendium types with shared slug and provenance helpers.
EOF
)"
```

---

### Task 3: Package v2 DTOs, schema, adapter, and validation

**Files:**
- Modify: `CampaignManifestV2.java` (new DTO records + list fields)
- Modify: `CampaignManifestAssembler.java`
- Modify: `library/packagev2/LibrarySectionAdapter.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `CampaignManifestV2SemanticValidator.java`, `CampaignImportPreviewStore.java`, `CampaignEntityCounts.java`, `CampaignSemanticSnapshotService.java`, `CampaignSemanticComparator.java` (if type-specific)
- Create: `library/packagev2/LibraryContentReferenceResolver.java`
- Modify: `StatBlockReferenceResolver.java` to delegate
- Modify: `PartySectionAdapter.java` (and any other catalog-only resolvers)
- Test: `library/packagev2/LibrarySectionAdapterTest.java`
- Test: extend `CampaignCompleteRoundTripTest` / package validation tests
- Fixture: `src/test/resources/campaigns/v2/custom-compendium.dmcampaign/manifest.json` (or extend feature-complete)

**Interfaces:**
- Consumes: campaign-scoped CUSTOM entities + package keys.
- Produces: manifest arrays; import registers `CampaignContentType` keys; resolver returns entities for CATALOG and PACKAGE.

**DTO exemplar (spell):**

```java
public record CustomSpellDto(
        String key,
        String sourceKey,
        String name,
        int level,
        String school,
        String castingTime,
        String range,
        String components,
        String duration,
        String description,
        String higherLevel,
        boolean ritual,
        boolean concentration,
        ProvenanceDto provenance
) {}

public record ProvenanceDto(
        String sourceTitle,
        String editionVersion,
        String sourceLocator,
        LicenseClassification licenseClassification,
        Instant importedAt,
        String converterId,
        String converterVersion,
        String sourceHash,
        SourceAnnotationConfidence extractionConfidence
) {}
```

Mirror field sets from the JPA entities for conditions, rules, equipment, magic items, classes (include `subclassOf` as package or catalog ref string — for custom subclasses store package key of parent class when parent is package-scoped; when parent is SRD store the SRD sourceKey in `subclassOf` and document that `subclassOf` remains a sourceKey/package-key hybrid resolved at import), species, backgrounds, feats.

**Class subclass rule (explicit):**

- `subclassOf` continues to store a **sourceKey-like string** for SRD parents.
- When the parent class is campaign CUSTOM, store the **package key** of the parent and set a boolean or parallel field only if needed. Prefer: on export, if parent is CUSTOM campaign, write `subclassOf` as the parent package key and add semantic validation that the key exists in `customClasses`. On import, resolve package key first, else SRD sourceKey. Document in format docs.

- [ ] **Step 1: Failing adapter + schema tests**

```java
@Test
void exportsCampaignScopedCustomSpellWithProvenance() { … }

@Test
void importsPackageSpellAndResolvesSheetPackageRef() { … }

@Test
void schemaRejectsUnknownPropertyOnCustomSpell() { … }

@Test
void dryRunFailsOnUnresolvedPackageSpellRef() { … }
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=LibrarySectionAdapterTest,CampaignPackageValidationPipelineTest test
```

- [ ] **Step 3: Extend schema**

Add `$defs/provenance`, `$defs/customSpell`, … and top-level arrays. Set `additionalProperties: false`. Require `key` + type-essential fields. Provenance object optional.

Update root `required` array if new arrays are required-as-empty.

- [ ] **Step 4: Implement assembler + LibrarySectionAdapter**

Export only `source == CUSTOM && campaign.id == context.campaignId()`. Order each list by package key ascending for determinism.

Import:

```java
for (CustomSpellDto dto : nullToEmpty(source.customSpells())) {
    Spell spell = new Spell();
    spell.setSource(ContentSource.CUSTOM);
    spell.setCampaign(context.campaign());
    // map fields…
    spell.setProvenance(fromDto(dto.provenance()));
    if (spell.getProvenance() != null && spell.getProvenance().getImportedAt() == null) {
        spell.getProvenance().setImportedAt(Instant.now());
    }
    spells.save(spell);
    context.register(CampaignContentType.SPELL, dto.key(), spell, spell.getId());
}
```

- [ ] **Step 5: Implement `LibraryContentReferenceResolver`**

```java
@Component
public class LibraryContentReferenceResolver {
    public ContentReference referenceFor(CampaignContentType type, Object entity, CampaignExportContext ctx) { … }

    public <T> T resolve(ContentReference ref, CampaignContentType expectedType,
                         Class<T> clazz, CampaignImportContext ctx) {
        if (ref.scope() == ContentReference.Scope.PACKAGE) {
            return ctx.require(ref, expectedType, clazz);
        }
        // CATALOG: ruleset must be SRD_5_2; load by source + sourceKey
        …
    }
}
```

Replace `PartySectionAdapter.resolveSpell/Species/Background` and class/feat import paths to use this resolver so PACKAGE refs work. When exporting sheet spell refs for campaign custom spells, emit PACKAGE refs via the same helper.

**Dependency closure:** if a sheet in the campaign references a user-global CUSTOM spell (campaign null), either:

1. **Reject export** with a clear problem code `NON_CAMPAIGN_CUSTOM_DEPENDENCY`, or
2. **Auto-clone into campaign** on export.

Choose **(1) reject with actionable message** for YAGNI/safety. DMs promote or clone into the campaign before export. Test this explicitly.

- [ ] **Step 6: Semantic validator keys + preview counts**

Register keys for all custom arrays. Extend provenance coverage metrics to all custom types. Extend `OWNERSHIP_QUERIES` for each new campaign-owned type.

- [ ] **Step 7: Fixture + round-trip test**

Add a campaign-scoped custom entry for every type; sheet uses PACKAGE spell + PACKAGE feat; treasury uses PACKAGE magic item; scene or note may link RULE. Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=LibrarySectionAdapterTest,CampaignCompleteRoundTripTest,CampaignPackageValidationPipelineTest,CampaignSemanticSnapshotServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: round-trip campaign-scoped custom compendium content

Export and import custom spells, rules, items, classes, and related
types with provenance and package-scoped content references.
EOF
)"
```

---

### Task 4: Library UI and destinations

**Files:**
- Modify: `LibraryController.java`, `LibraryApiController.java`
- Create/Modify: form fragments per type (start with spell + rule + magic item as templates; reuse pattern for the rest)
- Create: `templates/library/_provenance-fields.html`
- Create: `templates/library/_scope-badge.html`
- Modify: list cards to show SRD/CUSTOM/campaign badges and scope filter controls
- Modify: `ContentDestinationRegistry` to open UUID detail routes for custom non-class types when `entityId` present
- Test: `LibraryControllerCustomContentTest.java`, `LibraryTemplateContractTest.java`, destination/palette tests

**UI requirements:**

- Each tab: filter chips or select for `All | SRD | Custom | This campaign` when a campaign context exists. Global library (`/library`) shows SRD + global custom; campaign pages that deep-link to library may pass `campaignId`.
- Create button on each tab (hidden/disabled for pure SRD browse if desired — show Create Custom).
- Detail view shows provenance panel for CUSTOM; SRD shows “Bundled SRD 5.2” badge only.
- Clone on SRD and custom detail pages.
- Promote only when `campaign != null`.
- Edit/delete only when CUSTOM.
- Failed mutations use existing global error handling / flash / toast patterns — no empty catches.
- Class detail by UUID as well as sourceKey: add `/library/classes/id/{id}` or resolve sourceKey first then id fallback so custom classes without catalog keys work. Update destination registry accordingly.

- [ ] **Step 1: Failing controller tests**

```java
@Test
void createCustomSpellForCampaignReturnsCard() { … }

@Test
void editSrdSpellReturnsError() { … }

@Test
void provenanceFragmentRenderedForCustomOnly() { … }

@Test
void destinationForCustomSpellUsesStableDetailRoute() { … }
```

- [ ] **Step 2: Implement routes**

Mirror statblock routes under each type prefix, e.g.:

```text
GET  /library/spells/new
GET  /library/spells/{id}
GET  /library/spells/{id}/edit
POST /library/spells
PUT  /library/spells/{id}
DELETE /library/spells/{id}
POST /library/spells/{id}/clone
PUT  /library/spells/{id}/promote
```

Repeat for conditions, rules, equipment, magic-items, classes, species, backgrounds, feats. Prefer shared private helpers in the controller to avoid a 2000-line copy-paste explosion; extracting a small `LibraryMutationController` per type is acceptable if clearer.

- [ ] **Step 3: Templates**

`_provenance-fields.html` fields: sourceTitle, editionVersion, sourceLocator, licenseClassification select, converterId/version (optional advanced), confidence select. Read-only importedAt/sourceHash when present.

- [ ] **Step 4: Wire destinations + palette**

Ensure command palette search includes custom rows (services already `findAll` — filter should not drop CUSTOM). For types that only had filtered-tab destinations, add detail routes when `entityId != null`:

```java
case SPELL -> entityId != null
        ? "/library/spells/" + entityId
        : filtered("spells", displayName);
```

- [ ] **Step 5: Run UI/contract tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=LibraryControllerTest,LibraryControllerCustomContentTest,LibraryTemplateContractTest,LibraryApiControllerTest,ContentDestinationRegistryTest,CommandPaletteServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: author custom spells and other library content in the UI

Add create, edit, clone, promote, provenance, and stable destinations
for non-statblock compendium types.
EOF
)"
```

---

### Task 5: Runtime consumers and security

**Files:**
- Modify: `SheetService`, `SheetEngine` to resolve spells/classes/feats/species/background by id or by (source, sourceKey) with campaign preference
- Modify: encounter/combatant creation if it only searches SRD statblocks — already supports custom statblocks; verify
- Modify: treasury if magic items resolve by sourceKey only
- Test: sheet tests with PACKAGE/custom campaign spell; player-safety test asserting provenance absent from player JSON
- Test: PIN coverage for any new DM mutation routes (library routes should already sit behind DM PIN if all `/library/**` is protected — verify `PinInterceptor` patterns)

**Resolution preference when looking up by sourceKey inside a campaign context:**

1. Campaign-scoped CUSTOM with matching sourceKey  
2. User-global CUSTOM with matching sourceKey  
3. SRD with matching sourceKey  

Never return another campaign’s custom row.

- [ ] **Step 1: Failing sheet test using campaign custom spell**

```java
@Test
void sheetCanPrepareCampaignScopedCustomSpell() { … }
```

- [ ] **Step 2: Implement campaign-aware lookup helpers on repositories/services**

```java
public Optional<Spell> resolveForCampaign(UUID campaignId, String sourceKey) {
    return Optional.ofNullable(repo.findByCampaignIdAndSourceKey(campaignId, sourceKey))
            .or(() -> Optional.ofNullable(repo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey)))
            .or(() -> Optional.ofNullable(repo.findBySourceAndSourceKey(ContentSource.SRD, sourceKey)));
}
```

Prefer storing sheet associations by entity UUID FK where they already are (`SheetSpellReference` → `Spell`). Fix any path that persists only sourceKey strings for feats/classes if PACKAGE custom content would be ambiguous — if class levels JSON stores sourceKeys, document that campaign custom classes must use unique sourceKeys within the campaign and export rewrites to ContentReference PACKAGE keys (already the package direction of truth).

- [ ] **Step 3: Player safety**

```java
@Test
void playerPayloadDoesNotIncludeContentProvenance() { … }
```

- [ ] **Step 4: Run**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=SheetServiceTest,SheetEngineTest,PlayerSafeProjectionTest,LibraryControllerCustomContentTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: resolve campaign custom library content from sheets and runtime

Prefer campaign-scoped custom entries when binding spells and related
references without leaking provenance to players.
EOF
)"
```

---

### Task 6: Documentation, capabilities, and release gate

**Files:**
- Modify: `docs/campaign-format-v2.md` — custom arrays, provenance schema, subclassOf resolution, dependency rules, non-campaign custom export rejection
- Modify: `docs/campaign-capabilities.md` — mark campaign-scoped non-statblock custom content `SUPPORTED`
- Modify: master spec delivery table row 7 → `IMPLEMENTED` only after gates pass
- Optional: short note in `SPEC.md` only if it currently claims custom content is statblock-only and would mislead

- [ ] **Step 1: Update format docs with examples**

Include one minimal JSON snippet:

```json
{
  "customSpells": [
    {
      "key": "arc-bolt",
      "sourceKey": "homebrew-arc-bolt",
      "name": "Arc Bolt",
      "level": 1,
      "school": "Evocation",
      "castingTime": "1 action",
      "range": "60 feet",
      "components": "V, S",
      "duration": "Instantaneous",
      "description": "A crackling bolt of force.",
      "higherLevel": null,
      "ritual": false,
      "concentration": false,
      "provenance": {
        "sourceTitle": "Homebrew Codex",
        "editionVersion": "1.0",
        "sourceLocator": "p.12",
        "licenseClassification": "ORIGINAL",
        "extractionConfidence": "HIGH"
      }
    }
  ]
}
```

- [ ] **Step 2: Full automated gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit \
  -Dtest=CampaignCompleteRoundTripTest,CampaignPackageValidationPipelineTest,LibrarySectionAdapterTest,CustomContentOwnershipPersistenceTest,CustomSpellServiceTest,LibraryControllerCustomContentTest,PlayerSafeProjectionTest,CoreSessionLoopSmokeTest test

./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit test

git diff --check
git status --short
```

Expected: all Maven commands exit 0; Surefire 0 failures/errors; `git diff --check` clean; status only intentional item-7 files.

- [ ] **Step 3: Manual audit checklist**

- [ ] `rg "Source\\.CUSTOM|ContentSource" src/main/java/dev/hendrikhoemberg/dmhelper/library` — no leftover nested `StatBlock.Source` if removed
- [ ] `rg "findBySourceKey\\(" src/main/java` — every campaign-sensitive path uses campaign-aware resolution or is documented SRD-only
- [ ] No empty `catch` on new library mutation JS/HTMX paths
- [ ] Import of old `minimal.dmcampaign.json` / feature-complete still works
- [ ] Promote then export: promoted content is **not** in campaign package; campaign clones remain

- [ ] **Step 4: Flip status docs**

Only after Step 2–3 pass, set:

- `docs/campaign-capabilities.md`: campaign-scoped non-statblock custom content → `SUPPORTED`
- master spec §22 row 7 → `IMPLEMENTED`

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs: mark custom compendium expansion ready

Document package custom-content arrays, provenance, and capability
status after the item-7 verification gate.
EOF
)"
```

---

## Commit checkpoints

1. `feat: add ownership and provenance columns for all library types` — Task 1  
2. `feat: add create, clone, promote, and delete for custom library content` — Task 2  
3. `feat: round-trip campaign-scoped custom compendium content` — Task 3  
4. `feat: author custom spells and other library content in the UI` — Task 4  
5. `feat: resolve campaign custom library content from sheets and runtime` — Task 5  
6. `docs: mark custom compendium expansion ready` — Task 6  

Each commit must pass its focused test command and `git diff --check`.

---

## Out of scope (explicit)

| Item | Why |
|---|---|
| Full character creation/advancement UX | Delivery item 8 |
| Encounter waves/rewards depth, published-map calibration | Delivery item 9 |
| Generated agent SDK, capability manifest machine format, full DM manual rewrite | Delivery item 10 |
| Rollable tables, travel, weather, fog of war, audio, world graph NPCs/factions | P3 / item 11 |
| Separate mechanical trap/hazard/disease/vehicle engines | Store as custom RULE or scene sections; no automation here |
| Auto-embedding user-global custom content into campaign export | Reject with `NON_CAMPAIGN_CUSTOM_DEPENDENCY` instead |
| Changing SRD seed corpus or ruleset beyond ownership columns | Catalog remains SRD_5_2 |

---

## Definition of done

The delivery is complete when a DM can create campaign-scoped custom entries for every library type, attach provenance, reference them from sheets/treasury/encounters via stable package keys, promote or delete them under documented rules, export/import/re-export without semantic loss or silent degradation, keep SRD content read-only and catalog-addressable, exclude provenance from player payloads, and the capability matrix plus master delivery table row 7 reflect `SUPPORTED` / `IMPLEMENTED` only after the full automated gate is green.

---

## Spec coverage self-review

| Master spec requirement | Task |
|---|---|
| §10.1 Unified ownership (SRD / user-global / campaign) for listed types | 1–2, 4 |
| §10.1 traps/hazards/diseases/curses/vehicles/tables | RULE generic storage (Task 2–3); mechanical engines out of scope |
| §10.2 Provenance fields + visibility | 1, 3, 4, 5 |
| §10.3 Reference without copying SRD; promote keeps attribution | 2–3, 5 |
| §10.4 list/search/view/create/clone/edit/scope/provenance | 2, 4 |
| §10.4 export includes campaign-scoped custom dependencies | 3 |
| §10.4 delete/promote reference rules | 2 |
| §10.4 shared content identifiers across sheets/encounters/treasury/notes/scenes/search | 3, 5, destinations in 4 |
| §10.4 unsupported rules as typed generic rule entry | Task 2 RuleSection custom path |
| §4.3 stable keys | package keys in Task 3 |
| §4.5 complete round-trip | Task 3 + 6 |
| §4.8 player safety | Task 5 |
| §7.2 keyed custom spells/rules/items/classes/… | Task 3 |
| §7.9 catalog remains typed SRD snapshot | unchanged; catalog still SRD-only |
| Delivery item 7 only | Global constraints + out-of-scope table |

**Placeholder scan:** none intentional.  
**Type consistency:** `ContentSource`, `LicenseClassification`, `ContentProvenance` / `ProvenanceDto`, package arrays, and `LibraryContentReferenceResolver` names are used consistently across tasks.
