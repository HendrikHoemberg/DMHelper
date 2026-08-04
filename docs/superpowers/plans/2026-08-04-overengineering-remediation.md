# Overengineering Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove ~11,400 net lines of duplicated serialization, workspace-layout configurability, appearance testing, and test infrastructure resident in production code, withdrawing four capabilities deliberately and degrading none that survive.

**Architecture:** Five sequential stages, ordered by blast radius. Stages 1–2 are pure subtraction that cannot change observable behaviour. Stage 3 removes a user-facing capability and touches the DB. Stage 4 depends on Stage 3 having landed, because four of the gates it deletes assert properties of the layout engine Stage 3 removes. Stage 5 trims the export surface and reconciles documentation with what now exists.

**Tech Stack:** Java 25 / Spring Boot 4.1, Thymeleaf, htmx, Alpine, vanilla JS, H2 + Flyway, JUnit 5 + AssertJ + MockMvc + jsoup, Playwright.

**Source spec:** `docs/superpowers/specs/2026-08-04-overengineering-remediation-design.md`

## Global Constraints

- **Every stage ends green on `./mvnw test -P gates`.** A stage that cannot go green is not partially landed.
- **No new abstraction is introduced anywhere.** Every stage is subtraction. If a step appears to need a new abstraction, the step is wrong — stop and raise it.
- **These classes are shared with package v2 and must NOT be deleted:** `campaign/service/validation/ImportProblemCodes.java`, `campaign/service/validation/CampaignImportProblem.java`, `campaign/service/validation/ImportSeverity.java`.
- **The package-key subsystem is out of scope.** `CampaignPackageKey`, `CampaignPackageKeyRepository`, `CampaignPackageKeyService`, `PackageKeyGenerator` are not modified.
- **The god classes are out of scope.** `EncounterService`, `SheetService`, `LibraryController` are not refactored. `CampaignService` shrinks only because v1 code is removed from it.
- **The v2 manifest version is NOT bumped.** It stays `formatVersion: 2` throughout. The existing package `~/Documents/DnDCampaigns/lmop-de3.dmcampaign` must import successfully after every stage.
- **Branch:** all work lands on `spec/overengineering-remediation`, which already carries the spec commit.

## A note on TDD in a deletion plan

The standard red-green-refactor cycle does not apply to deleting code. For deletion tasks the equivalent discipline is:

1. Establish the suite is green **before** touching anything.
2. Delete.
3. Run the suite. Compilation errors and test failures are the signal — they enumerate exactly what referenced the deleted code.
4. Fix each referent, re-run until green.
5. Commit.

Real TDD (failing test first) applies to the three tasks that *write* code: Task 4, Task 12, and Task 14. Those tasks are marked and follow the normal cycle.

---

# Stage 1 — Free wins

No behaviour change. No user-visible change. No risk to table operation.

### Task 1: Remove the dead websocket dependency

**Files:**
- Modify: `pom.xml:78-81`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

- [ ] **Step 1: Confirm the dependency is genuinely unused**

Run all four checks. Every one must return no output.

```bash
grep -rn "springframework.web.socket" src/ || echo "CLEAN: no server import"
grep -rn "WebSocketHandler\|EnableWebSocket\|SimpMessag\|StompEndpoint" src/ || echo "CLEAN: no server usage"
grep -rn "new WebSocket\|EventSource" src/main/resources/ || echo "CLEAN: no client usage"
grep -rn 'hx-trigger="every' src/main/resources/templates/ || echo "CLEAN: no polling fallback"
```

Expected: four `CLEAN:` lines. If any check produces a match, **stop** — the dependency is live and this task is void.

- [ ] **Step 2: Remove the dependency block**

Delete these four lines from `pom.xml`:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket</artifactId>
		</dependency>
```

- [ ] **Step 3: Verify the application still starts and the suite is green**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS. A missing-bean failure here would mean the grep in Step 1 was wrong.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "chore: drop unused spring-boot-starter-websocket

Zero references in the repository: no server import, no handler, no
client-side WebSocket or EventSource, no polling fallback.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Relocate semantic snapshot infrastructure out of production code

These three classes are referenced by nothing but three test classes, and `CampaignSemanticComparator` throws `AssertionError`. They ship in the jar and are component-scanned at every startup.

**Files:**
- Move: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java` (404) → `src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignSemanticSnapshotService.java`
- Move: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java` (239) → `src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignSemanticComparator.java`
- Move: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java` (28) → `src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignSemanticSnapshot.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparatorTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `dev.hendrikhoemberg.dmhelper.support.CampaignSemanticSnapshotService` — a Spring `@Service` injected into tests via `@Autowired`; `dev.hendrikhoemberg.dmhelper.support.CampaignSemanticComparator.assertEquivalent(CampaignSemanticSnapshot, CampaignSemanticSnapshot)` — static. Task 16 uses the round-trip test these support.

- [ ] **Step 1: Confirm nothing in `src/main` references them**

```bash
grep -rln "CampaignSemanticSnapshot\|CampaignSemanticComparator" src/main/
```

Expected: only the three files being moved. Any other `src/main` hit means this task is void — stop.

- [ ] **Step 2: Move the three files with git**

```bash
git mv src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignSemanticSnapshotService.java
git mv src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignSemanticComparator.java
git mv src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignSemanticSnapshot.java
```

- [ ] **Step 3: Change the package declaration in each moved file**

In all three files, replace the first line:

```java
package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;
```

with:

```java
package dev.hendrikhoemberg.dmhelper.support;
```

- [ ] **Step 4: Keep the snapshot service injectable from the test context**

`CampaignSemanticSnapshotService` is annotated `@Service` and injected with `@Autowired`. Classes under `src/test` are not component-scanned by `@SpringBootTest` unless they sit under the application's base package — `dev.hendrikhoemberg.dmhelper.support` does, so scanning still finds it. Leave the `@Service` annotation in place.

Verify this assumption explicitly in Step 6 rather than assuming it. If the context fails to start with `NoSuchBeanDefinitionException`, add an explicit registration to each consuming test instead:

```java
@Import(CampaignSemanticSnapshotService.class)
```

- [ ] **Step 5: Fix imports in the three consuming tests**

In `CampaignCompleteRoundTripTest`, `CampaignSemanticComparatorTest`, and `CampaignSemanticSnapshotServiceTest`, replace every import of the form:

```java
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignSemanticSnapshotService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignSemanticComparator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignSemanticSnapshot;
```

with:

```java
import dev.hendrikhoemberg.dmhelper.support.CampaignSemanticSnapshotService;
import dev.hendrikhoemberg.dmhelper.support.CampaignSemanticComparator;
import dev.hendrikhoemberg.dmhelper.support.CampaignSemanticSnapshot;
```

Some of these tests sit in the same package as the moved classes and may have no import at all — those need the import **added**.

- [ ] **Step 6: Run the three affected tests**

```bash
./mvnw test -Dtest='CampaignCompleteRoundTripTest,CampaignSemanticComparatorTest,CampaignSemanticSnapshotServiceTest'
```

Expected: PASS. A `NoSuchBeanDefinitionException` here means Step 4's fallback is needed.

- [ ] **Step 7: Run the full suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: move semantic snapshot test infrastructure to src/test

CampaignSemanticSnapshotService, CampaignSemanticComparator and
CampaignSemanticSnapshot were referenced only by tests; the comparator
throws AssertionError. 671 lines leave the production jar and component
scanning.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Delete the fixture meta-tests

`PackageShapeProfileExtractor` reads a campaign package from a hardcoded personal path, extracts a structural profile, commits it, and `FixtureShapeCoverageTest` asserts the fixtures cover the same shape. These test the fixtures of other tests. The hardcoded path is already stale — it points at `lmop-de.dmcampaign`, but the file on disk is `lmop-de3.dmcampaign`.

**Files:**
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java` (266)
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java` (197)
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfile.java` (13)
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileTest.java` (48)
- Delete: the committed profile resource under `src/test/resources/`
- Possibly modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReadinessFixtureShapeTest.java` (34)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing. `PopulatedCampaignFixture`, `ReleaseRehearsalFixture` and `PreparationSurfaceFixture` are used by real tests and are NOT deleted.

- [ ] **Step 1: Locate the committed profile resource**

```bash
grep -rn "COMMITTED_PROFILE" src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java
find src/test/resources -name "*shape*" -o -name "*profile*"
```

Note the exact resource path — it is deleted in Step 3.

- [ ] **Step 2: Check whether `ReadinessFixtureShapeTest` depends on this machinery**

```bash
grep -n "PackageShapeProfile\|ProfileExtractor" src/test/java/dev/hendrikhoemberg/dmhelper/support/ReadinessFixtureShapeTest.java
```

If it references either class, it is part of the same meta-testing machinery — delete it too. If it does not, leave it alone.

- [ ] **Step 3: Delete the files**

```bash
git rm src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfile.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileTest.java
git rm <the profile resource path from Step 1>
```

- [ ] **Step 4: Run the full suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS. Any compilation error names a remaining referent — delete or fix it, then re-run.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test: delete fixture shape meta-tests

FixtureShapeCoverageTest asserted that test fixtures matched a profile
extracted from a hardcoded personal path that no longer exists. Tests of
the fixtures of other tests, unmaintained. The fixtures themselves stay.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

# Stage 2 — Excise campaign format v1

### Task 4: Rewrite the map-document contract test against the v2 schema

**This is a real TDD task and it MUST come before any v1 deletion.**

`GameMapServiceTest.dtoSerializationMatchesMapDocumentSchema` (`GameMapServiceTest.java:236-260`) borrows the **v1** `CampaignSchemaValidator` to check that `MapDocumentDto` serialization matches the map-document schema. This is a legitimate contract test with nothing to do with v1 campaigns — it just uses the v1 validator as a vehicle, wrapping the map document inside a `formatVersion: 1` campaign envelope. Deleting the validator would silently take this coverage with it.

`CampaignManifestV2SchemaValidator` already bundles `map-document-v2.schema.json` under the id `https://dmhelper/map-document-v2.schema.json` (`CampaignManifestV2SchemaValidator.java:26,32`), so no new class is needed — only a different envelope.

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java:5,236-260`

**Interfaces:**
- Consumes: `CampaignManifestV2SchemaValidator.validate(String json)` → `List<CampaignImportProblem>`, empty when valid.
- Produces: a map-document contract test that survives Task 8.

- [ ] **Step 1: Understand why the envelope is built from a fixture, not by hand**

The v2 schema declares **25 required top-level fields** and `additionalProperties: false`. Hand-writing a minimal envelope in the test would mean hardcoding 25 keys that drift the moment the manifest changes.

`src/test/resources/campaigns/v2/minimal.dmcampaign.json` is already the minimal valid v2 manifest — 26 keys, `maps: []`. The test loads it and injects the map document into `maps`, so the envelope stays correct for free.

Confirm the fixture is still shaped as expected:

```bash
python3 -c "
import json
m=json.load(open('src/test/resources/campaigns/v2/minimal.dmcampaign.json'))
print('keys:', len(m), '| maps:', m.get('maps'), '| formatVersion:', m.get('formatVersion'))
"
```

Expected: `keys: 26 | maps: [] | formatVersion: 2`.

- [ ] **Step 2: Write the failing test**

Replace the body of `dtoSerializationMatchesMapDocumentSchema`:

```java
    @Test
    void dtoSerializationMatchesMapDocumentSchema() throws Exception {
        var mapper = JsonMapper.builder().build();
        CampaignManifestV2SchemaValidator val = new CampaignManifestV2SchemaValidator();

        MapDocumentDto defaultDoc = MapDocumentDto.createDefault(30, 20, 48);
        assertThat(val.validate(manifestWrapping(mapper, defaultDoc))).isEmpty();

        MapDocumentDto richDoc = new MapDocumentDto(
                1,
                new MapDocumentDto.GridDto(10, 10, 48, "square", "GRID", true),
                List.of(
                        // ... retain the existing richDoc layer content verbatim ...
                ));
        assertThat(val.validate(manifestWrapping(mapper, richDoc))).isEmpty();
    }

    /**
     * Injects a serialized MapDocumentDto into the committed minimal v2 manifest. The
     * schema requires 25 top-level fields and forbids extras, so the envelope comes from
     * the fixture rather than being written out here.
     */
    private static String manifestWrapping(JsonMapper mapper, MapDocumentDto document)
            throws Exception {
        ObjectNode manifest = (ObjectNode) mapper.readTree(
                new ClassPathResource("campaigns/v2/minimal.dmcampaign.json").getInputStream());

        ObjectNode map = mapper.createObjectNode();
        map.put("key", "map-1");
        map.put("name", "DTO test map");
        map.put("movementMode", "GRID");
        map.put("showGrid", true);
        map.put("sortOrder", 0);
        map.set("grid", mapper.valueToTree(
                new MapDocumentDto.GridDto(30, 20, 48, "square", "GRID", true)));
        map.set("document", mapper.valueToTree(document));
        map.set("tokens", mapper.createArrayNode());

        ((ArrayNode) manifest.get("maps")).add(map);
        return mapper.writeValueAsString(manifest);
    }
```

Add the imports:

```java
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
```

The `map` entry's required fields are `key`, `name`, `grid`, `document`, `sortOrder` — the others are set because the old test set them and they exercise more of the schema.

- [ ] **Step 3: Run the test**

```bash
./mvnw test -Dtest='GameMapServiceTest#dtoSerializationMatchesMapDocumentSchema'
```

Expected: PASS. If it fails, each `CampaignImportProblem`'s `path` names the offending field — fix the `map` entry, not the assertion.

**Do not weaken the assertion.** `isEmpty()` stays. If the map *document* is what fails validation, that is a genuine finding about `MapDocumentDto` against `map-document-v2.schema.json` — stop and report it rather than adjusting the test.

- [ ] **Step 4: Prove the test still has teeth**

Temporarily corrupt the document — change `"cellPx":48` to `"cellPx":"forty-eight"` in `manifestWrapping` — and re-run.

Expected: FAIL. If it passes, the validator is not reaching the map document and the test is worthless; stop and investigate.

Restore the correct value and re-run to confirm PASS.

- [ ] **Step 5: Remove the now-unused v1 import**

Delete from `GameMapServiceTest.java:5`:

```java
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSchemaValidator;
```

- [ ] **Step 6: Run the whole test class**

```bash
./mvnw test -Dtest='GameMapServiceTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java
git commit -m "test: validate map document against v2 schema

The map-document contract test borrowed the v1 CampaignSchemaValidator
as a vehicle. Rewritten against CampaignManifestV2SchemaValidator, which
already bundles map-document-v2.schema.json, so the coverage survives v1
removal.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Remove the v1 endpoints and the legacy UI section

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java:206-258`
- Modify: `src/main/resources/templates/campaigns/settings.html:34,85-104`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `CampaignController` with no export/import endpoints. Task 6 removes the service methods they called.

- [ ] **Step 1: Delete the two endpoint methods and the DryRunResult record**

Remove from `CampaignController.java` in full:
- `exportCampaign` (lines 206-223), including its `@GetMapping("/{id}/export")`
- `importCampaign` (lines 225-245), including its `@PostMapping("/import")`
- the `DryRunResult` record (lines 247-258)

Then remove the imports that become unused. After deletion, run the compiler and let it tell you which — expect `MultipartFile`, `CampaignValidationResult`, `CampaignImportProblem`, `ContentDisposition`, `HttpHeaders`, `MediaType`, `URLEncoder`, `IOException`, and possibly `StandardCharsets` and `List`. Do not guess; remove exactly what the compiler flags as unused.

- [ ] **Step 2: Delete the legacy UI section**

Remove the entire `<section>` element from `templates/campaigns/settings.html:85-104` — the block beginning:

```html
    <section class="card u-mt-lg" data-settings-group="data">
        <h2>Legacy data</h2>
```

and ending at its closing `</section>`.

Also update the explanatory comment at line 34, which currently reads:

```html
         separated here; `data` is the legacy v1 package format, kept with `packages`. -->
```

Rewrite it so it no longer describes a `data` group that has ceased to exist. Read the full comment before editing — it spans more than one line.

- [ ] **Step 3: Remove the corresponding controller tests**

In `CampaignControllerTest.java`, delete every test that exercises `/campaigns/{id}/export` or `/campaigns/import`. Find them first:

```bash
grep -n "export\|import\|dryRun" src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java
```

Delete the matching `@Test` methods in full, plus any helper or fixture used only by them.

- [ ] **Step 4: Check for other references to the removed routes**

```bash
grep -rn "campaigns/import\|/export" src/main/resources/templates src/main/resources/static src/test/
```

Expected after Steps 1-3: no hits referring to campaign export/import. Hits for unrelated exports (e.g. a map export) are fine — read each before acting.

- [ ] **Step 5: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS. `CampaignService.exportToJson` and friends are now unreferenced but still present — that is expected and Task 6 removes them.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat!: remove v1 campaign export and import endpoints

Removes GET /campaigns/{id}/export, POST /campaigns/import, and the
Legacy data settings section. v1 campaign files can no longer be
exported or imported.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Remove v1 export and import from CampaignService

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` (1,306 → ~520)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java`
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` (908)

**Interfaces:**
- Consumes: `CampaignController` no longer calling these (Task 5).
- Produces: `CampaignService` retaining `create`, `findAll`, `findById`, `update`, `delete`, `setMilestoneMode`, `exportEntityIds`. Task 7 and Task 8 remove the classes these methods used.

- [ ] **Step 1: Delete the v1 methods**

Remove from `CampaignService.java` in full:

| Method | Lines |
|---|---|
| `exportToJson(UUID)` | 356-552 |
| `validateImport(String)` | 615-618 |
| `importFromJson(String)` | 619-628 |
| `importValidated(CampaignExportDto)` | 629-1146 |
| `putPersistedId(...)` | 1147-1150 |
| `toPartyMemberExport(PartyMember)` | 1151-1187 |
| `importSheet(...)` | 1188-1271 |
| `parseJsonMap(String)` | 1272-1281 |
| `parseJsonList(String)` | 1282-1291 |
| `parseSheetClassLevels(String)` | 1292-end |

**Keep `exportEntityIds(UUID)` (553-606) and `putIds(...)` (607-614)** unless the compiler proves them unreferenced. Check first:

```bash
grep -rn "exportEntityIds" src/main src/test
```

If the only callers were the deleted methods, delete them too. If anything else calls them — the agent SDK or a readiness assembler — they stay.

- [ ] **Step 2: Remove the constructor dependencies that are now unused**

`CampaignService`'s constructor (line 103) injects many repositories used only by the v1 import/export code. Compile and let the IDE/compiler identify unused fields:

```bash
./mvnw -q compile
```

Remove each field and its constructor parameter only when nothing in the remaining class body references it. Work one at a time and recompile — removing a still-used injection produces a confusing NPE at runtime rather than a compile error, because Spring will have already constructed the bean.

- [ ] **Step 3: Delete the round-trip test**

```bash
git rm src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
```

- [ ] **Step 4: Prune the v1 portions of CampaignServiceTest**

```bash
grep -n "exportToJson\|importFromJson\|validateImport\|CampaignExportDto" src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java
```

Delete the matching `@Test` methods in full. Tests covering `create`, `update`, `delete` and `setMilestoneMode` stay untouched.

- [ ] **Step 5: Fix CampaignCascadeDeleteTest**

`CampaignCascadeDeleteTest.java:88-91` imports the v1 validation stack into its test context. All **four** entries go — `CampaignCatalogResolver` on line 90 is deleted by Task 8 along with the other three:

```java
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSchemaValidator.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignCatalogResolver.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSemanticValidator.class,
```

Remove all four from the `@SpringBootTest(classes = {...})` list. Line 87 (`ContentDestinationRegistry`) and line 92 (`WorldService` and friends) stay.

- [ ] **Step 6: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Verify the real package still round-trips**

```bash
./mvnw spring-boot:run
```

Import `~/Documents/DnDCampaigns/lmop-de3.dmcampaign` through the v2 package import UI, then export it again from the same campaign. Confirm the import reports no errors and the export downloads. Stop the app.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: remove v1 export/import from CampaignService

Drops ~790 lines of v1 serialization from CampaignService, taking it from
1306 to ~520 lines, plus the 908-line v1 round-trip test.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Remove the v1 migration path from the package pipeline

Per spec §5.3, `ContainerKind.V1_JSON` is removed outright. Recognition of v1 files is not needed to produce a clean error: `CampaignPackageReader.readJson` already has an `UNSUPPORTED_FORMAT_VERSION` default arm, so deleting the `case 1 ->` line makes `formatVersion: 1` fall into it. That arm throws `CampaignPackageException` wrapping a `CampaignImportProblem` (`CampaignPackageReader.java:182-183`), which is caught at line 47 and surfaced as a problem — a readable message, not a stack trace. The pipeline never sees the file.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipeline.java:6,9,28-29,35-36,40-41,44-67,101-105`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/StagedCampaignPackage.java:13`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageReader.java:74`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java` (486)
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/FormatMigrationRegistry.java` (45)
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignFormatMigration.java` (10)
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java`

**Interfaces:**
- Consumes: nothing from Task 6.
- Produces: `CampaignPackageValidationPipeline` with a three-argument constructor `(CampaignManifestV2SchemaValidator, CampaignManifestV2SemanticValidator, CampaignCatalogService)`. A `.dmcampaign.json` declaring `formatVersion: 1` is rejected by `CampaignPackageReader` with `ImportProblemCodes.UNSUPPORTED_FORMAT_VERSION`.

- [ ] **Step 1: Simplify the pipeline's validate method**

In `CampaignPackageValidationPipeline.java`, replace lines 44-67 — the whole `if (staged.containerKind() == ContainerKind.V1_JSON) { ... } else { ... }` block — with the else-branch body alone:

```java
    public CampaignPackageValidationResult validate(StagedCampaignPackage staged) {
        try {
            List<CampaignImportProblem> problems = new ArrayList<>();
            String json = Files.readString(staged.manifestPath());
            List<CampaignImportProblem> schemaProblems = schema.validate(json);
            if (!schemaProblems.isEmpty()) return new CampaignPackageValidationResult(staged, null, 2,
                    Map.of(), schemaProblems, List.of());
            CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);
            int sourceVersion = manifest.formatVersion();
            Map<String, Path> migratedAssets = Map.of();
            List<String> migrationLabels = List.of();
```

The remainder of the method — from the `canonicalSchema` re-validation at line 69 onward — is unchanged. `migratedAssets` and `migrationLabels` are retained as empty constants so the existing result construction and asset-resolution loop below continue to compile unmodified.

- [ ] **Step 2: Remove the migration dependencies from the constructor**

Delete the `migrations` and `v1Validator` fields (lines 28-29), their constructor parameters (lines 35-36), and their assignments (lines 40-41). The constructor becomes:

```java
    public CampaignPackageValidationPipeline(CampaignManifestV2SchemaValidator schema,
                                             CampaignManifestV2SemanticValidator semantics,
                                             CampaignCatalogService catalog) {
        this.schema = schema;
        this.semantics = semantics;
        this.catalog = catalog;
    }
```

Also delete the now-unused imports on lines 6 (`FormatMigrationRegistry`) and 9 (`CampaignImportValidator`).

- [ ] **Step 3: Delete the now-dead `unsupported` helper**

`unsupported(...)` (lines 101-105) had exactly one caller, the V1 branch deleted in Step 1. With the reader rejecting v1 files before staging completes, nothing else can reach it. Delete the method.

Confirm before deleting:

```bash
grep -n "unsupported(" src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipeline.java
```

Expected: only the declaration remains after Step 1. If a second caller appears, keep the method and note why.

- [ ] **Step 4: Remove the V1_JSON container kind**

Two edits, in this order:

1. `CampaignPackageReader.java:74` — delete the line `case 1 -> StagedCampaignPackage.ContainerKind.V1_JSON;`. `formatVersion: 1` now falls to the existing `default ->` arm, which throws `UNSUPPORTED_FORMAT_VERSION`.
2. `StagedCampaignPackage.java:13` — remove `V1_JSON` from the enum, leaving `{ V2_JSON, V3_JSON, V2_ZIP }`.

Then confirm the variant is gone everywhere:

```bash
grep -rn "V1_JSON" src/main src/test
```

Expected: no output. Any test hit is a v1 test being deleted in Task 8 — remove it there, or here if it blocks compilation.

- [ ] **Step 5: Delete the migration classes**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/FormatMigrationRegistry.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignFormatMigration.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java
```

- [ ] **Step 6: Add a test that a v1 file is cleanly rejected**

The rejection now happens at read time, so the test drives `CampaignPackageReader`, not the pipeline. Add to the existing `CampaignPackageReaderTest` (find it first; create it only if absent):

```java
    @Test
    void v1JsonIsRejectedWithUnsupportedFormatVersion() {
        byte[] bytes = """
                {"formatVersion":1,"campaign":{"name":"Legacy"}}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(
                new ByteArrayInputStream(bytes), "legacy.dmcampaign.json", "application/json"))
                .isInstanceOf(CampaignPackageException.class)
                .extracting(e -> ((CampaignPackageException) e).problem().code())
                .isEqualTo(ImportProblemCodes.UNSUPPORTED_FORMAT_VERSION);
    }
```

Read `CampaignPackageReader`'s public entry point and `CampaignPackageException`'s accessor before writing this — the signatures above are the expected shape, not verified text. Match the real ones; do not add accessors to make this compile.

- [ ] **Step 7: Run the test, then the suite**

```bash
./mvnw test -Dtest='CampaignPackageReaderTest'
./mvnw test -P gates
```

Expected: PASS, then BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat!: remove the v1-to-v2 campaign migration path

A .dmcampaign.json declaring formatVersion 1 is now rejected at read time
with UNSUPPORTED_FORMAT_VERSION rather than migrated. Removes the V1_JSON
container kind, LegacyV1ToV2Migration, FormatMigrationRegistry and the
CampaignFormatMigration interface, and drops the double semantic validation
v1 files previously incurred.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Delete the v1 validation stack, schemas, and fixtures

**Files:**
- Delete: `campaign/service/CampaignExportDto.java` (371)
- Delete: `campaign/service/validation/CampaignSemanticValidator.java` (909)
- Delete: `campaign/service/validation/CampaignSchemaValidator.java` (60)
- Delete: `campaign/service/validation/CampaignImportValidator.java` (68)
- Delete: `campaign/service/validation/CampaignCatalogResolver.java` (76)
- Delete: `campaign/service/validation/CampaignValidationResult.java` (31)
- Delete: `src/main/resources/schemas/campaign-format.schema.json`
- Delete: `src/main/resources/schemas/map-document.schema.json`
- Delete: five v1 test classes, `src/test/resources/campaigns/v1/*`, v1 `docs-examples/*`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java:27,36`
- Keep: `ImportProblemCodes.java`, `CampaignImportProblem.java`, `ImportSeverity.java`

**Interfaces:**
- Consumes: Task 4's rewritten map-document test; Tasks 5-7 having removed all production callers.
- Produces: a codebase with exactly one campaign format.

> **Scope note — beyond the spec's delete list.** Spec §5.2 does not list
> `map-document.schema.json`. Deleting it also withdraws the served endpoint
> `GET /api/v1/schemas/map-document.schema.json`, which is a fifth capability
> withdrawal on top of the four the spec names. It is included here because the file
> is v1-only: its sole code consumer is `CampaignSchemaValidator` (`:20,26`), the only
> other reference is a `$ref` from `campaign-format.schema.json` (`:169`, also deleted),
> and v2 is served the equivalent shape at `/api/v1/schemas/map-document-v2.schema.json`.
> Serving a schema that describes a format the application can no longer read is a
> manifest that lies. Step 2 verifies the assumption before acting; if `docs/agent/`
> publishes the v1 URL as an external contract, **stop and raise it** rather than
> deleting. Record the withdrawal in Task 17 and in the Completion section.

- [ ] **Step 1: Confirm no production code references the stack**

```bash
grep -rln "CampaignExportDto\|CampaignSemanticValidator\|CampaignSchemaValidator\|CampaignImportValidator\|CampaignCatalogResolver\|CampaignValidationResult" src/main/
```

Expected: only the six files being deleted. Any other hit means Tasks 5-7 are incomplete — **stop and finish them first**.

- [ ] **Step 2: Delete the main classes and both v1 schemas**

`map-document.schema.json` is deleted because `CampaignSchemaValidator` was its only consumer — the v2 pipeline uses `map-document-v2.schema.json`. Confirm before deleting:

```bash
grep -rn "map-document.schema.json" src/ docs/
```

If only the deleted validator and `SchemaControllerTest` reference it, proceed. If `docs/agent/` publishes the URL as a contract, note it for Task 9.

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidator.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidator.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidator.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignCatalogResolver.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignValidationResult.java \
       src/main/resources/schemas/campaign-format.schema.json \
       src/main/resources/schemas/map-document.schema.json
```

- [ ] **Step 3: Delete the v1 tests and fixtures**

```bash
git rm src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidatorTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidatorTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java
git rm -r src/test/resources/campaigns/v1
git rm src/test/resources/docs-examples/minimal-valid.dmcampaign.json \
       src/test/resources/docs-examples/schema-error.dmcampaign.json \
       src/test/resources/docs-examples/semantic-error.dmcampaign.json
```

- [ ] **Step 4: Find and remove the documentation-example test**

The `docs-examples` fixtures exist to prove the authoring guide's examples validate. That test must go too:

```bash
grep -rln "docs-examples" src/test/java/
```

Delete each class it names, unless the class also covers v2 examples — in that case delete only the v1 test methods.

- [ ] **Step 5: Update SchemaControllerTest**

`SchemaControllerTest.java:27` requests `/api/v1/schemas/campaign-format` and line 36 requests `/api/v1/schemas/map-document.schema.json`. Both now 404.

Delete those two test methods. The v2 assertions at lines 55 and 64 stay untouched — they are the remaining contract.

- [ ] **Step 6: Prune unreachable problem codes**

`ImportProblemCodes` (164 lines) stays, but some codes were only ever raised by deleted v1 code. For each constant, check for remaining raisers:

```bash
grep -rn "ImportProblemCodes\." src/main/ | grep -o "ImportProblemCodes\.[A-Z_]*" | sort -u
```

Compare against the constants declared in the file and delete any that no longer appear in `src/main`. Do this conservatively — a code raised from a template or referenced in `docs/authoring/validation-errors.md` is still live. **`UNSUPPORTED_FORMAT_VERSION` is raised by `CampaignPackageReader`'s default arm (Task 7) and must stay.**

- [ ] **Step 7: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Verify the real package still imports**

```bash
./mvnw spring-boot:run
```

Import `~/Documents/DnDCampaigns/lmop-de3.dmcampaign`. Confirm no errors. Then confirm a v1 file is cleanly rejected: create `/tmp/legacy.dmcampaign.json` containing `{"formatVersion":1,"campaign":{"name":"Legacy"}}` and import it. Expected: a readable `UNSUPPORTED_FORMAT_VERSION` message, not a stack trace. Stop the app.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat!: delete the v1 campaign format stack

Removes CampaignExportDto, both v1 validators, the import validator, the
catalog resolver, the validation result, and both v1 JSON schemas, plus
their tests and fixtures. ~1,515 lines of main and ~750 of test.

ImportProblemCodes, CampaignImportProblem and ImportSeverity are retained
- package v2 depends on them.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: Reconcile v1 documentation

**Files:**
- Delete: `docs/campaign-format-v1.md`
- Modify: `docs/product/format-compatibility.md`
- Modify: `docs/campaign-format-v2.md`
- Modify: `docs/authoring/validation-errors.md`, `docs/authoring/README.md`
- Modify: `docs/agent/*.md`, `docs/README.md`

**Interfaces:**
- Consumes: Tasks 5-8 complete.
- Produces: documentation describing one format.

- [ ] **Step 1: Delete the v1 format specification**

```bash
git rm docs/campaign-format-v1.md
```

- [ ] **Step 2: Rewrite the compatibility table**

Replace the table in `docs/product/format-compatibility.md` with:

```markdown
# Format Compatibility

| Format | Read | Write | Notes |
|---|---|---|---|
| v2 JSON | yes | yes (asset-free) | |
| v2 ZIP `.dmcampaign` | yes | yes when assets present | |

Format v1 was removed on 2026-08-04. A `.dmcampaign.json` file declaring
`formatVersion: 1` is rejected with `UNSUPPORTED_FORMAT_VERSION`.
```

- [ ] **Step 3: Correct the v2 spec's backward-compatibility claim**

`docs/campaign-format-v2.md` currently states:

```
Legacy `.dmcampaign.json` (v1) is accepted for backward compatibility and migrated to v2.
```

Replace with:

```
Format v1 is not accepted. A `.dmcampaign.json` declaring `formatVersion: 1`
is rejected with `UNSUPPORTED_FORMAT_VERSION`.
```

- [ ] **Step 4: Sweep the remaining docs**

```bash
grep -rn "formatVersion.*1\|v1\|campaign-format.schema.json\|map-document.schema.json\|/campaigns/import\|{id}/export" docs/ --include="*.md"
```

Review every hit. Update statements that describe removed behaviour; leave historical references inside `docs/superpowers/plans/` and `docs/superpowers/specs/` alone — those are dated records, not current documentation.

Pay particular attention to any problem code removed in Task 8 Step 6 that `docs/authoring/validation-errors.md` still documents.

- [ ] **Step 5: Verify no doc test consumes the deleted examples**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "docs: reconcile documentation with v1 removal

Deletes the v1 format specification and corrects the compatibility table,
the v2 spec's backward-compatibility claim, and the authoring and agent
guides.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

# Stage 3 — Reduce the cockpit layout engine to fixed presets

**Before starting this stage:** the workbench template already server-renders the four-zone Exploration layout and is documented as "no-JS usable" (`_cockpit-workbench.html:4`). Several layout-edit controls in `cockpit.html` are already rendered `disabled aria-disabled="true"`. Much of this stage is deleting controls that are already inert.

## How a preset switch actually works

Spec §6.1 defines a preset as "a CSS class on the cockpit root **plus a server-rendered module-to-zone assignment**." Both halves are load-bearing, and the second one has a mechanism that already exists. Read this before touching Tasks 11-13.

`_cockpit-workbench.html` server-renders **only** the Exploration placement — `session-plan` in LEFT_SUPPORT, `story` in PRIMARY, `party` in RIGHT_SUPPORT, `quick-notes`/`audio`/`reference` in BOTTOM_UTILITY — and parks the other three module shells (`map`, `encounter`, `session-log`) in a hidden `[data-cockpit-depot]` (`:159-169`). Combat needs `map` in PRIMARY and `story`+`party` in LEFT_SUPPORT. **A CSS class cannot move a DOM node between two zone containers.** Something has to relocate the shells.

That something already exists and is retained: `renderLayout()` (`cockpit-layout.js:589-789`) walks the four zones, moves module shells between the depot and each zone's `[data-zone-panels]`, and builds the tab strip. It is driven by `window.cockpitLayoutConfig.presets`, which `cockpit.html:194-201` already serializes from the `cockpitPresets` model attribute via `th:inline="javascript"`. Once Task 11 reshapes `BuiltInPreset` to carry `moduleKeysByZone`, that assignment reaches the client for free. **No new mechanism, no new endpoint, no new model attribute.**

### What the rewritten cockpit-layout.js keeps

These are module *content* and *navigation* behaviours. Spec §6.4 leaves module content untouched, and §6.1 withdraws only docking, custom presets, split ratios, and schema versioning with repair — none of these:

| Behaviour | Current location | Why it stays |
|---|---|---|
| Module placement into zones + tab strip construction | `renderLayout()` :589-789 | Without it only Exploration ever renders |
| Tab selection | `selectTab()` :856, click handler :148-153 | `CockpitReferenceBrowserTest:82,105,146` and `CockpitModuleInitialLoadBrowserTest:140` click `[data-module-tab]`; both are retained by spec §7.1 |
| Roving-tabindex arrow navigation between tabs | `bindTabKeyboard()` :232-265 | `KeyboardOperationGateTest` is retained; tabs must stay keyboard-operable |
| Focus layer (focus a module, return to layout) | `bindFocusChrome()` :433-472, `_cockpit-workbench.html:172-177`, `_cockpit-module-shell.html:20` | Not in §6.1's removal list; `CockpitRuntimeModuleController:148` still serves `FOCUSED` mode |
| Module loading/empty/error messages | :1606-1614, reading `def.states` | Module content per §6.4 |
| Per-zone collapse | `toggleBottomUtility()` :947 generalised to all four zones | Explicitly retained by §6.1 |
| Digit-key preset shortcuts | `bindPresetShortcuts()` :211-232 | Explicitly retained by §6.1 |

### What it loses

`bindEditInteractions` (:272-423), `bindSplitters` (:423-433), `syncEditChrome`, `syncSplitterAria`, `clampRatios`, `resize`/`onSplitterKey`/`resizeToBound`/`onSplitterPointerDown`/`splitterBounds`/`workbenchMetrics` (:1230-1500+), `moveModule`/`reorderModule`/`removeModule`/`addModule`, `openArrangeMenu`/`closeArrangeMenu`, `openAddModuleDialog`/`closeAddModuleDialog`, `assertEditing`, `prepareLayoutMutation`, `allowedZonesFor`, `ensureZoneLayout`, `repairActive`, `maybeOfferDraftRecovery`, every `API_BASE` fetch, and all compact-mode plumbing.

### Corrected size projection

**Spec §6.3 projects `cockpit-layout.js` at 2,246 → ~200 and §10 counts −2,498 static lines. That projection is wrong** — it was made without accounting for module relocation and tab construction, and `renderLayout()` alone is ~200 lines. The realistic target is **2,246 → ~600**, and the static total for Stage 3 becomes ~2,100 rather than 2,498.

Spec §10 marks `~` figures as "projections for code being rewritten rather than deleted," so revising one is within the spec's own terms. **No requirement is repealed and no behaviour changes** — the four presets, the shortcuts, and per-zone collapse are all still delivered. Update the spec's §6.3 and §10 figures when Stage 3 lands, and use ~600 as the Completion target instead of the spec's number.

If the finished file lands materially above ~700 lines, something on the "loses" list survived — check it before committing.

### Task 10: Drop the preset persistence layer

**Files:**
- Create: `src/main/resources/db/migration/V30__drop_cockpit_layout_presets.sql`
- Delete: `session/service/CockpitLayoutPresetService.java` (200)
- Delete: `session/web/CockpitLayoutApiController.java` (65)
- Delete: `session/data/CockpitLayoutPreset.java` (59)
- Delete: `session/data/CockpitLayoutPresetRepository.java` (12)
- Delete: `src/test/.../session/service/CockpitLayoutPresetServiceTest.java` (163)
- Delete: `src/test/.../session/web/CockpitLayoutApiControllerTest.java` (106)
- Delete: `src/test/.../session/data/CockpitLayoutPresetRepositoryTest.java` (47)
- Modify: `session/web/SessionController.java:31,41,47,80`

**Interfaces:**
- Consumes: nothing from Stage 2.
- Produces: `SessionController` supplying `cockpitPresets` from `CockpitBuiltInPresetCatalog.all()` instead of `CockpitLayoutPresetService.list()`. Task 11 reshapes the catalog; Task 12's JS consumes the resulting model attribute.

**Destructive:** this migration destroys any custom presets in the local database. Approved in spec §6.5; no export path is provided.

- [ ] **Step 1: Confirm V30 is the next free version**

```bash
ls src/main/resources/db/migration/ | sort -V | tail -3
```

Expected: `V29__session_pre_review_status.sql` is the highest. If a V30 already exists, use the next free number throughout this task.

- [ ] **Step 2: Write the drop migration**

Create `src/main/resources/db/migration/V30__drop_cockpit_layout_presets.sql`:

```sql
-- Cockpit layouts became fixed presets: four built-in layouts selected client-side and
-- remembered in localStorage. User-defined presets, their versioning and their optimistic
-- locking are gone, so the table has no remaining reader or writer.
-- See docs/superpowers/specs/2026-08-04-overengineering-remediation-design.md section 6.
DROP TABLE IF EXISTS cockpit_layout_preset;
```

Confirm the exact table name against `V22__add_cockpit_layout_presets.sql` before finalising — if the entity used a different physical name, match it.

- [ ] **Step 3: Point SessionController at the built-in catalog**

In `SessionController.java`:
- Replace the `CockpitLayoutPresetService cockpitPresets` field (line 31) with `CockpitBuiltInPresetCatalog cockpitPresets`
- Update the constructor parameter (line 41) and assignment (line 47) to match
- Change line 80 from `model.addAttribute("cockpitPresets", cockpitPresets.list());` to:

```java
        model.addAttribute("cockpitPresets", cockpitPresets.all());
```

`cockpitPresets` has **two** consumers in `cockpit.html`, not one:

1. `:52-55` — the `<option>` loop, reading `preset.key` and `preset.name`.
2. `:197` — `window.cockpitLayoutConfig.presets`, serialized wholesale by `th:inline="javascript"`. This is how the module-to-zone assignment reaches the client (see the stage preamble). It needs no edit, but it is why `BuiltInPreset` must be JSON-serializable and why Task 11's `moduleKeysByZone` is not dead data.

`CockpitBuiltInPresetCatalog.BuiltInPreset` must expose `key()` and `name()` — verify before compiling, and if the record names differ, adjust the template rather than adding a shim.

Note that the old `PresetDto` carried a `builtIn` flag that `cockpit-layout.js:23-27` filtered on. Every preset is built-in now; Task 12 drops that filter rather than synthesising the flag.

- [ ] **Step 4: Delete the persistence classes and their tests**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetService.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiController.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPreset.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPresetRepository.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetServiceTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiControllerTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPresetRepositoryTest.java
```

- [ ] **Step 5: Verify Flyway and Hibernate agree**

`spring.jpa.hibernate.ddl-auto=validate` means Hibernate refuses to start if an entity has no table. The entity is deleted, so this should pass — but `FlywayMigrationTest` (480 lines) exists specifically to catch migration drift:

```bash
./mvnw test -Dtest='FlywayMigrationTest'
```

Expected: PASS. A failure here names the exact mismatch.

- [ ] **Step 6: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS **except** for browser gates asserting preset-editing UI, which Task 13 removes. Record any such failure by name and proceed — but only if the failure is in a gate class listed for deletion in Task 15. Any other failure must be fixed here.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat!: drop DB-backed custom cockpit layout presets

Adds V30 dropping cockpit_layout_preset and removes the preset service,
API controller, entity and repository. SessionController now serves the
four built-in presets directly.

Custom presets stored locally are destroyed by this migration.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 11: Reduce the layout domain to fixed presets

**Files:**
- Delete: `session/layout/CockpitLayoutResolver.java` (215)
- Delete: `session/layout/CockpitLayoutValidator.java` (115)
- Delete: `session/layout/CockpitLayoutDocument.java` (32)
- Delete: `session/layout/CockpitLayoutCodec.java` (29)
- Delete: `src/test/.../session/layout/CockpitLayoutResolverTest.java` (72), `CockpitLayoutValidatorTest.java` (76), `CockpitLayoutCodecTest.java` (27), `CockpitBuiltInPresetCatalogTest.java` (95)
- Modify: `session/layout/CockpitBuiltInPresetCatalog.java` (80)
- Modify: `session/layout/CockpitModuleRegistry.java` (89), `CockpitModuleDefinition.java` (27)
- Modify: `src/test/.../session/layout/CockpitModuleRegistryTest.java` (75)
- **Keep:** `session/layout/CockpitModuleStateContract.java` (8) — see below

> **Deviation from spec §6.2 — `CockpitModuleStateContract` is retained.** The spec lists
> it for deletion, but it is not layout machinery: it carries each module's loading, empty
> and error messages, read at `cockpit-layout.js:1606-1614` and reached through
> `CockpitModuleDefinition.states()`. Those are module *content*, which spec §6.4 leaves
> untouched, and deleting the record would silently drop the messages — degrading a
> surviving capability, which spec §10 forbids. Eight lines stay. Everything else in §6.2
> is deleted as listed.

**Interfaces:**
- Consumes: `SessionController` calling `CockpitBuiltInPresetCatalog.all()` (Task 10).
- Produces: `CockpitBuiltInPresetCatalog.all()` → `List<BuiltInPreset>` where `BuiltInPreset` exposes `key()`, `name()`, and a module-to-zone assignment; `CockpitModuleDefinition` with a single `zone()` rather than an `allowedZones()` set.

- [ ] **Step 1: Read the current shapes before changing them**

```bash
cat src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java
cat src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleDefinition.java
cat src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistry.java
```

The catalog currently builds presets via `preset(key, name, SplitRatios, zone(...)×4, Set<String> collapsible)`. The four preset definitions and their module assignments are the content being preserved — copy them out verbatim before editing.

- [ ] **Step 2: Reshape BuiltInPreset**

`SplitRatios` disappears with `CockpitLayoutDocument`. Rewrite `BuiltInPreset` to carry only what a CSS class needs:

```java
    public record BuiltInPreset(
            String key,
            String name,
            Map<CockpitZone, List<String>> moduleKeysByZone) {}
```

Drop the `SplitRatios` argument and the trailing collapsible `Set` — per-zone collapse becomes a uniform client-side affordance, not per-preset configuration.

Preserve all four presets and their exact assignments. These are the current definitions, transcribed from `CockpitBuiltInPresetCatalog.java:9-30` — zone order in the original `preset(...)` call is PRIMARY, LEFT_SUPPORT, RIGHT_SUPPORT, BOTTOM_UTILITY:

| Preset key | Name | PRIMARY | LEFT_SUPPORT | RIGHT_SUPPORT | BOTTOM_UTILITY |
|---|---|---|---|---|---|
| `builtin:exploration` | Exploration | `story` | `session-plan` | `party` | `quick-notes`, `audio`, `reference` |
| `builtin:combat` | Combat | `map` | `story`, `party` | `encounter` | `quick-notes`, `reference`, `audio`, `session-log` |
| `builtin:theatre-of-mind` | Theatre of Mind | `encounter` | `party`, `reference` | `story` | `quick-notes`, `audio`, `session-log` |
| `builtin:session-review` | Session Review | `session-log` | `session-plan` | `quick-notes`, `party` | *(collapsed — empty)* |

Verify this table against the file before relying on it; if the source has changed since, the source wins.

- [ ] **Step 3: Collapse allowedZones to a single zone, and change nothing else**

In `CockpitModuleDefinition`, replace `Set<CockpitZone> allowedZones()` with `CockpitZone zone()`, and update the compact constructor (`:17`, `:21`) accordingly — `allowedZones = Set.copyOf(...)` goes, and the `allowedZones.isEmpty()` guard becomes a `zone == null` guard. In `CockpitModuleRegistry`, update each definition to name its single home zone, taken from the Exploration preset's assignment in Step 1.

**Every other component of the record stays.** Spec §6.3's phrasing — "reduced to which modules exist, which zone each belongs to" — must not be applied literally:

| Component | Consumer that keeps it alive |
|---|---|
| `minWidthPx`, `minHeightPx` | `_cockpit-module-shell.html:8-9` |
| `focusSupported` | `CockpitRuntimeModuleController:148`, `_cockpit-module-shell.html:21` |
| `compactSupported` | `CockpitRuntimeModuleController:152` |
| `states` | `cockpit-layout.js:1606-1614` |

`CockpitRuntimeModuleController` is untouched by spec §6.4, so anything it reads is retained by definition. Only `allowedZones` is layout-configurability.

- [ ] **Step 4: Delete the document, resolver, validator, and codec**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutResolver.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutValidator.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutDocument.java \
       src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodec.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutResolverTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutValidatorTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodecTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java
```

`CockpitModuleStateContract` is **not** in this list — see the deviation note above.

`CockpitBuiltInPresetCatalogTest` is deleted because its single test asserts that built-ins pass `CockpitLayoutValidator`, which no longer exists. Task 12 Step 2 replaces this coverage with a preset-shape test.

- [ ] **Step 5: Compile and fix fallout**

```bash
./mvnw -q compile
```

Every error names a referent of a deleted class. Fix each. `CockpitRuntimeModuleViewService` and `CockpitRuntimeModuleController` must **not** need changes — if they do, they were coupled to layout configurability and the coupling should be removed rather than preserved.

`CockpitLayoutPresetService:176` referenced `compactModuleKeys`, but that class is already gone (Task 10), so the only remaining referents are the four `SplitRatios` arguments and the trailing `Set` in the catalog, both removed by Step 2.

- [ ] **Step 6: Update CockpitModuleRegistryTest**

Adjust assertions from `allowedZones()` to `zone()`. Delete any test asserting a module is permitted in multiple zones — that concept is gone.

- [ ] **Step 7: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS except for gates listed for deletion in Task 15. Same rule as Task 10 Step 6.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: reduce cockpit layout domain to fixed presets

Deletes the layout document, resolver, validator, codec and module state
contract. Presets carry only key, name and module-to-zone assignment;
modules declare a single home zone.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 12: Rewrite cockpit-layout.js

**This is a real TDD task** — the deliverable is new code, and its behaviour is asserted by a browser test.

**Read the stage preamble first.** This is a *trim* of the existing `CockpitLayoutController` class, not a from-scratch rewrite: the placement engine, tab handling, focus layer and module-state messages are all retained, and the edit/drag/splitter/preset-CRUD machinery is deleted around them. Working from the existing file is what keeps the retained behaviours intact.

**Files:**
- Rewrite: `src/main/resources/static/js/cockpit-layout.js` (2,246 → ~600, see preamble)
- Modify: `src/main/resources/templates/session/_cockpit-workbench.html` — add `data-cockpit-preset`, add a per-zone collapse button
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresetSwitchingBrowserTest.java`

**Interfaces:**
- Consumes: `window.cockpitLayoutConfig.presets` and `.defaultPresetKey` (`cockpit.html:194-201`, unchanged); `BuiltInPreset.key()`/`name()`/`moduleKeysByZone()` (Task 11).
- Produces: `[data-cockpit-workbench]` carrying `data-cockpit-preset="<key>"`; zones carrying `data-collapsed="true"` when collapsed; `localStorage` keys `dmhelper.cockpit.preset` and `dmhelper.cockpit.collapsed`.

### Two markup decisions, made here so Task 13 inherits them

**Use the existing hooks. Do not invent `data-cockpit-root`.** `[data-cockpit-workbench]` already exists on the `<section>` at `_cockpit-workbench.html:10` — and critically, it is the **direct parent of the four zone elements**. Task 13 puts `display: grid` and `grid-template-areas` on it and `grid-area` on the zones; that only works if the zones are its direct grid children. An attribute added to a wrapper in `cockpit.html` would put a `<section>` between the grid container and its zones and the layout would not apply. So `data-cockpit-preset` goes on `[data-cockpit-workbench]`.

**Collapse uses the existing `data-collapsed` attribute**, already present on every zone (`:20,49,78,107`) and asserted by the retained `CockpitWorkbenchTemplateContractTest:122`. Do not add a parallel `data-zone-collapsed` — that is addition, not subtraction.

- [ ] **Step 1: Write the failing browser test**

Create `CockpitPresetSwitchingBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.*;
import dev.hendrikhoemberg.dmhelper.support.PageReady;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class CockpitPresetSwitchingBrowserTest {

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private Page page;
    private ReleaseRehearsalFixture.Seeded seeded;

    @BeforeAll void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach void openCockpit() {
        seeded = fixture.seed();
        page = browser.newContext().newPage();
        PageReady.open(page, "http://localhost:" + port, cockpitPath());
    }

    @AfterEach void closePage() {
        if (page != null) page.close();
    }

    /** SessionController maps the cockpit at /campaigns/{campaignId}/session. */
    private String cockpitPath() {
        return "/campaigns/" + seeded.campaignId() + "/session";
    }

    private void reopen() {
        PageReady.open(page, "http://localhost:" + port, cockpitPath());
    }

    private String activePreset() {
        return page.locator("[data-cockpit-workbench]").getAttribute("data-cockpit-preset");
    }

    private String zoneOf(String moduleKey) {
        return page.locator("[data-cockpit-zone]:has(.cockpit-module[data-module-key='"
                + moduleKey + "'])").getAttribute("data-cockpit-zone");
    }

    @Test
    void opensOnExplorationByDefault() {
        assertThat(activePreset()).isEqualTo("builtin:exploration");
    }

    @Test
    void switchingPresetChangesTheWorkbenchAttribute() {
        page.selectOption("#cockpitPresetPicker", "builtin:combat");

        assertThat(activePreset()).isEqualTo("builtin:combat");
    }

    @Test
    void presetChoiceSurvivesReload() {
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        reopen();

        assertThat(activePreset()).isEqualTo("builtin:combat");
    }

    @Test
    void digitShortcutSelectsItsPreset() {
        page.keyboard().press("Digit2");

        assertThat(activePreset()).isEqualTo("builtin:combat");
    }

    @Test
    void collapsingAZoneMarksItAndSurvivesReload() {
        page.click("[data-cockpit-zone='RIGHT_SUPPORT'] [data-zone-collapse]");
        assertThat(page.locator("[data-cockpit-zone='RIGHT_SUPPORT']")
                .getAttribute("data-collapsed")).isEqualTo("true");

        reopen();

        assertThat(page.locator("[data-cockpit-zone='RIGHT_SUPPORT']")
                .getAttribute("data-collapsed")).isEqualTo("true");
    }

    // --- The placement engine: this is the coverage the stage preamble exists for. ---

    @Test
    void explorationPlacesModulesInTheirAssignedZones() {
        assertThat(zoneOf("story")).isEqualTo("PRIMARY");
        assertThat(zoneOf("session-plan")).isEqualTo("LEFT_SUPPORT");
        assertThat(zoneOf("party")).isEqualTo("RIGHT_SUPPORT");
        assertThat(zoneOf("quick-notes")).isEqualTo("BOTTOM_UTILITY");
    }

    @Test
    void switchingToCombatRelocatesModulesOutOfTheDepot() {
        page.selectOption("#cockpitPresetPicker", "builtin:combat");

        assertThat(zoneOf("map")).isEqualTo("PRIMARY");
        assertThat(zoneOf("story")).isEqualTo("LEFT_SUPPORT");
        assertThat(zoneOf("party")).isEqualTo("LEFT_SUPPORT");
        assertThat(zoneOf("encounter")).isEqualTo("RIGHT_SUPPORT");
        assertThat(zoneOf("session-log")).isEqualTo("BOTTOM_UTILITY");
    }

    @Test
    void everyPresetPlacesEveryModuleItAssigns() {
        for (String preset : List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            page.selectOption("#cockpitPresetPicker", preset);

            assertThat(page.locator("[data-cockpit-zone] .cockpit-module[data-module-key]").count())
                    .as("%s renders no modules", preset)
                    .isGreaterThan(0);
        }
    }

    @Test
    void clickingAZoneTabSelectsThatModulesPanel() {
        page.click("[data-module-tab='reference']");

        assertThat(page.locator("[data-module-panel='reference']").isVisible()).isTrue();
    }
}
```

`switchingToCombatRelocatesModulesOutOfTheDepot` is the test that would have caught the gap the stage preamble describes: `map`, `encounter` and `session-log` start life in `[data-cockpit-depot]`, and only the placement engine gets them into a zone. **Do not weaken it.** If it cannot pass, the placement engine was deleted and the implementation is wrong.

Transcribe the expected assignments from Task 11 Step 2's table, and let the source win if it has changed.

The route above is verified (`SessionController` maps `/campaigns/{campaignId}/session`). `PageReady` exposes `open` and `settlesAfterLoad` — there is **no** `settle` method, which is why `reopen()` re-navigates through `PageReady.open` rather than calling `page.reload()`. Re-navigation exercises `localStorage` persistence identically.

- [ ] **Step 2: Add a preset-shape unit test**

This replaces the coverage deleted with `CockpitBuiltInPresetCatalogTest` in Task 11. Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`:

```java
    @Test
    void everyPresetAssignsOnlyKnownModulesAndNeverRepeatsOne() {
        for (var preset : catalog.all()) {
            List<String> assigned = preset.moduleKeysByZone().values().stream()
                    .flatMap(List::stream).toList();

            assertThat(assigned).as("%s repeats a module", preset.key())
                    .doesNotHaveDuplicates();
            assertThat(assigned).as("%s assigns an unknown module", preset.key())
                    .allMatch(registry::contains);
        }
    }

    @Test
    void explorationExistsAndIsTheDefault() {
        assertThat(catalog.all()).extracting(BuiltInPreset::key)
                .contains("builtin:exploration");
    }
```

- [ ] **Step 3: Run both tests to verify they fail**

```bash
./mvnw test -P gates -Dtest='CockpitPresetSwitchingBrowserTest,CockpitBuiltInPresetCatalogTest'
```

Expected: FAIL. The browser tests fail because `data-cockpit-preset` does not exist yet; the catalog test fails to compile until `moduleKeysByZone()` exists from Task 11.

- [ ] **Step 4: Trim cockpit-layout.js down to the retained behaviours**

Work **inside the existing file**, deleting the "what it loses" list from the stage preamble and keeping the "what it keeps" list. Do not start from a blank file — the placement engine and tab handling are subtle and there is no reason to re-derive them.

Delete, in roughly this order:

1. `bindEditInteractions` (:272-423) and every drag/drop handler it registers.
2. `bindSplitters` (:423-433) and the whole resize block: `splitterBounds`, `workbenchMetrics`, `clampRatios`, `resize`, `onSplitterKey`, `resizeToBound`, `onSplitterPointerDown`, `syncSplitterAria`.
3. Mutation and CRUD: `moveModule`, `reorderModule`, `removeModule`, `addModule`, `openArrangeMenu`, `closeArrangeMenu`, `openAddModuleDialog`, `closeAddModuleDialog`, `assertEditing`, `prepareLayoutMutation`, `syncEditChrome`, `lockMode`, `maybeOfferDraftRecovery`.
4. Layout-document machinery: `allowedZonesFor`, `ensureZoneLayout`, `repairActive`, the schema-version and dirty-state fields, `INVALID_STORAGE_MSG`.
5. Persistence: `API_BASE` and every `fetch` against it. Preset choice now goes to `localStorage` only.
6. Compact-mode plumbing: the `compact` set in `renderLayout` (:593) and the `compactModuleKeys` reads at :1039-1040 and :1733. `def.compactSupported` stays — `CockpitRuntimeModuleController` still serves that mode.

Then make these four changes to what remains:

- **`renderLayout()`** — drop the ratio block (:596-603, the `--left-size`/`--primary-size`/`--right-size`/`--top-size`/`--bottom-size` custom properties). Task 13's CSS owns sizing now. Read the zone assignment from `preset.moduleKeysByZone[zone]` instead of `layout.zones[zone].moduleKeys`. Everything else in the method — the depot round-trip, panel construction, tab strip — is unchanged.
- **`applyPreset(key)`** — set `data-cockpit-preset` on `this.workbench`, write the key to `localStorage`, call `renderLayout()`. Drop the dirty-check and draft options.
- **Collapse** — generalise `toggleBottomUtility()` (:947) to any zone: toggle `data-collapsed` on the zone element, mirror it to the toggle button's `aria-expanded`, and persist the collapsed set under `dmhelper.cockpit.collapsed`. Apply it on mount.
- **`constructor`** — drop the `builtIn` filter at :23-27; every preset is built-in now (Task 10).

Keep the two `localStorage` keys namespaced as `dmhelper.cockpit.preset` and `dmhelper.cockpit.collapsed`, and keep the existing `storageKey(kind)` helper (:73) if it already namespaces per campaign — preset choice is per-browser, and matching the existing convention is cheaper than changing it.

- [ ] **Step 5: Add the two markup hooks**

Only two hooks are missing; the rest already exist.

- `[data-cockpit-workbench]` — **already present** (`_cockpit-workbench.html:10`). Add `data-cockpit-preset="builtin:exploration"` to it so a no-JS load still declares the layout it rendered.
- `[data-cockpit-zone="<ZONE>"]` — **already present** (`:18,47,76,105`).
- `data-collapsed` on each zone — **already present** (`:20,49,78,107`).
- `[data-zone-collapse]` — **missing.** Add one button per zone, inside each zone's existing `cockpit-zone__tabs` container:

```html
      <button type="button" class="btn btn-ghost cockpit-zone__collapse"
              data-zone-collapse aria-expanded="true">
        <span class="sr-only">Collapse zone</span>
      </button>
```

BOTTOM_UTILITY already has `#cockpitBottomUtility` as its `aria-controls` target from the old toggle; the new per-zone buttons control their own zone's `[data-zone-panels]`, so give each panel container an id if you wire `aria-controls`, or omit the attribute rather than pointing it at something that does not exist.

**The picker stays `disabled aria-disabled="true"` in the template** (`cockpit.html:51`) and is enabled by the script on mount. That is deliberate: a no-JS load must not present a control that cannot work, and the retained `CockpitWorkbenchTemplateContractTest:132-136` asserts exactly this markup state. Do not remove those attributes from the template.

- [ ] **Step 6: Run the new tests**

```bash
./mvnw test -P gates -Dtest='CockpitPresetSwitchingBrowserTest,CockpitBuiltInPresetCatalogTest'
```

Expected: PASS. Iterate on the script and markup until green. If a test needs the assertion weakened to pass, the implementation is wrong — fix the implementation.

- [ ] **Step 7: Run the browser tests that depend on the retained behaviours**

These are retained by spec §7.1 and exercise the tab handling and module content the trim must not have broken. They are the regression check on Step 4:

```bash
./mvnw test -P gates -Dtest='CockpitReferenceBrowserTest,CockpitModuleInitialLoadBrowserTest,CockpitMapTransitionBrowserTest,CockpitRuntimeMapPickerBrowserTest,CoreSessionLoopSmokeTest,KeyboardOperationGateTest'
```

Expected: PASS. `CockpitReferenceBrowserTest` clicks `[data-module-tab='reference']` at `:82,105,146` and `CockpitModuleInitialLoadBrowserTest:140` clicks `[data-module-tab='party']` — a failure here means tab selection went out with the edit machinery. `KeyboardOperationGateTest` covers the roving-tabindex navigation.

- [ ] **Step 8: Confirm the file landed in the expected size range**

```bash
wc -l src/main/resources/static/js/cockpit-layout.js
```

Expected: roughly 600, and in any case under 700. Materially above that means something on the stage preamble's "loses" list survived.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat!: replace the cockpit layout engine with fixed presets

cockpit-layout.js goes from 2246 to ~600 lines. Drag-and-drop docking,
custom presets, split ratios, layout schema versioning and repair, and
the layout API are gone. Module placement, zone tabs, the focus layer
and module state messages are retained: they are module navigation and
content, not layout configurability.

Preset choice and per-zone collapse are remembered in localStorage.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 13: Simplify the layout CSS and remove the dead edit controls

**Files:**
- Rewrite: `src/main/resources/static/css/cockpit-layout.css` (702 → ~250)
- Modify: `src/main/resources/templates/session/cockpit.html:45-68,80-90`
- Modify: `src/main/resources/templates/session/_cockpit-workbench.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java:82`

**Interfaces:**
- Consumes: `data-cockpit-preset` on `[data-cockpit-workbench]` and `data-collapsed` on zones (Task 12).
- Produces: four grid definitions keyed off `[data-cockpit-preset]` on the workbench, and a collapse rule.

- [ ] **Step 1: Remove the preset-management overflow menu**

Delete from `cockpit.html:56-68` the entire `<details id="cockpitPresetOverflow">` element — its four buttons (`duplicate`, `rename`, `delete`, `restore`) are already `disabled` and now have no service behind them.

- [ ] **Step 2: Remove the layout-edit controls**

Delete from `cockpit.html:80-90`:
- `<button id="cockpitLayoutModeButton">` ("Edit layout")
- `<button id="cockpitAddModuleButton">` ("Add module")
- `<button id="cockpitBottomUtilityToggle">` ("Show utilities") — superseded by the per-zone collapse button from Task 12

- [ ] **Step 3: Remove the layoutEditing model attribute**

Delete line 82 of `SessionController.java`:

```java
        model.addAttribute("layoutEditing", true);
```

Then remove every `th:if="${layoutEditing}"` and `data-layout-edit-only` in the session templates:

```bash
grep -rn "layoutEditing\|data-layout-edit-only" src/main/resources/templates/
```

- [ ] **Step 4: Remove the splitter markup**

Split ratios are gone, so the splitter elements are inert. Remove all three `[data-cockpit-splitter]` elements from `_cockpit-workbench.html` (`:40-43`, `:69-72`, `:98-101`).

Also remove the two now-meaningless state attributes on the workbench `<section>`: `data-layout-mode="locked"` (`:12`) and `data-bottom-collapsed="false"` (`:13`). There is no unlocked mode any more, and bottom collapse is now per-zone `data-collapsed` like every other zone.

While in this file, delete the edit-mode chrome that no longer has code behind it: `#cockpitModuleArrangeMenu` (`:180-188`), `#cockpitAddModuleDialog` (`:190-197`), `#cockpitLayoutExitDialog` (`:199-207`), and `#cockpitPresetNameDialog` (`:209-218`). Keep `#cockpitLayoutNotice` (`:220`) only if the trimmed script still writes to it; delete it otherwise.

**Keep** `#cockpitFocusLayer` (`:172-177`) and `[data-cockpit-depot]` (`:159-169`) — the focus layer is retained behaviour, and the depot is where the placement engine parks modules the active preset does not use.

- [ ] **Step 4b: Update CockpitWorkbenchTemplateContractTest to the new contract**

`CockpitWorkbenchTemplateContractTest` is retained, and Steps 1-4 deliberately remove markup it currently asserts. It will fail, and that failure is correct — the contract changed. Update these assertions:

| Line | Current assertion | Change to |
|---|---|---|
| `:91` | `[role=separator][aria-orientation]` has size 3 | assert size **0** — splitters are gone |
| `:97-98` | `#cockpitLayoutModeButton` exists, text "Edit layout" | delete — button removed in Step 2 |
| `:99-100` | every `[data-layout-edit-only]` is hidden | delete — attribute removed in Step 3 |
| `:122` | BOTTOM_UTILITY `data-collapsed` | keep — still the collapse attribute |
| `:124` | workbench `data-bottom-collapsed` | delete — attribute removed in Step 4 |
| `:137-144` | `#cockpitLayoutModeButton` / `#cockpitAddModuleButton` are disabled | delete both — buttons removed in Step 2 |

**Do not touch** `:88-90` (the four zones), `:92-96` (every registry module has exactly one shell), `:107-121` (the Exploration server-render placement and the depot contents), or `:132-136` (the picker is `disabled aria-disabled="true"` in markup). Those are the contract that still holds, and `:107-121` in particular is what guarantees a no-JS load still renders a usable cockpit.

Add one assertion for the new hook:

```java
        assertThat(document.select("[data-cockpit-workbench]").attr("data-cockpit-preset"))
                .isEqualTo("builtin:exploration");
```

- [ ] **Step 5: Rewrite the layout CSS**

Replace `cockpit-layout.css` with four grid definitions plus a collapse rule. The structural core:

```css
/* Four fixed cockpit layouts. The active one is selected by [data-cockpit-preset] on the
   workbench; zones are placed by grid-area. Replaced the resizable/dockable engine on
   2026-08-04. */

[data-cockpit-workbench] {
  display: grid;
  gap: var(--space-sm);
  height: 100%;
  grid-template-rows: 1fr auto;
}

[data-cockpit-preset="builtin:exploration"] {
  grid-template-columns: 18fr 64fr 18fr;
  grid-template-areas:
    "left primary right"
    "bottom bottom bottom";
}

[data-cockpit-preset="builtin:combat"] {
  grid-template-columns: 18fr 52fr 30fr;
  grid-template-areas:
    "left primary right"
    "bottom bottom bottom";
}

[data-cockpit-preset="builtin:theatre-of-mind"] {
  grid-template-columns: 19fr 50fr 31fr;
  grid-template-areas:
    "left primary right"
    "bottom bottom bottom";
}

[data-cockpit-preset="builtin:session-review"] {
  grid-template-columns: 22fr 56fr 22fr;
  grid-template-areas:
    "left primary right"
    "bottom bottom bottom";
}

[data-cockpit-zone="LEFT_SUPPORT"]  { grid-area: left; }
[data-cockpit-zone="PRIMARY"]       { grid-area: primary; }
[data-cockpit-zone="RIGHT_SUPPORT"] { grid-area: right; }
[data-cockpit-zone="BOTTOM_UTILITY"]{ grid-area: bottom; }

[data-cockpit-zone][data-collapsed="true"] > [data-zone-panels] { display: none; }
```

**Both the grid container and the preset selector must be `[data-cockpit-workbench]`** — the same element. `grid-area` only positions *direct children* of the grid container, and the four zones are direct children of the workbench `<section>` (`_cockpit-workbench.html:10`), not of any wrapper in `cockpit.html`. Putting `display: grid` on a wrapper instead would silently produce a page with no layout at all.

Session Review's BOTTOM_UTILITY is empty (`collapsedZone()` in the catalog). It collapses to nothing under `grid-template-rows: 1fr auto` without a special case; confirm visually in Step 9 rather than adding a rule for it.

The column ratios above are the current `SplitRatios` from `CockpitBuiltInPresetCatalog.java:9-30`, converted to `fr` units: exploration `0.18/0.64/0.18`, combat `0.18/0.52/0.30`, theatre-of-mind `0.19/0.50/0.31`, session-review `0.22/0.56/0.22`. The fourth ratio in each original tuple (`0.16`) is the bottom zone's share and is expressed by `grid-template-rows` rather than a column.

Retain from the old file only the rules that style zone chrome (tabs, panels, headers) rather than layout mechanics. Delete every rule referencing dragging, dropping, splitters, resize handles, or edit mode.

- [ ] **Step 6: Run the cockpit tests**

```bash
./mvnw test -P gates -Dtest='CockpitPresetSwitchingBrowserTest,CockpitWorkbenchTemplateContractTest,CockpitHydrationTest,CockpitPresentationContractTest,CockpitSurfaceContractTest'
```

Expected: PASS — but only after Step 4b. These tests assert template structure, so they name both hooks removed by mistake *and* hooks removed on purpose; Step 4b is what tells the two apart. A failure naming anything in Step 4b's "do not touch" column is a real defect.

- [ ] **Step 7: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS except gates listed for deletion in Task 15.

- [ ] **Step 8: Manual verification at both table viewports**

This stage's verification cannot be fully automated. Run the app:

```bash
./mvnw spring-boot:run
```

Open the cockpit and check at 1366×768 and at the primary table display:
- all four presets apply via the picker and via Digit1-Digit4
- every module renders in its assigned zone in every preset
- each zone collapses and expands
- the choice survives a reload
- no horizontal scrollbar on `body` in any preset

Stop the app. Record anything wrong as a defect to fix before committing.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat!: simplify cockpit layout CSS and remove edit controls

cockpit-layout.css goes from 702 to ~250 lines: four grid definitions and
a collapse rule. Removes the preset overflow menu, the Edit layout and Add
module buttons, the bottom-utility toggle, and the splitter elements.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

# Stage 4 — Consolidate the appearance gates

### Task 14: Rewrite ViewportAccessibilityGateTest as the consolidated overflow gate

**This is a real TDD task** — the deliverable is a new assertion set.

**Files:**
- Rewrite: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java` (480 → ~250)

**Interfaces:**
- Consumes: the fixed-preset cockpit from Stage 3; `ReleaseRehearsalFixture.Seeded`; `PageReady.open`.
- Produces: the single retained appearance gate. Task 15 deletes the rest on the strength of it.

- [ ] **Step 1: Record the current surface coverage before rewriting**

The render gates being deleted in Task 15 each cover surfaces this gate must absorb:

```bash
grep -hno '"/[a-zA-Z/{}$+.]*"' \
  src/test/java/dev/hendrikhoemberg/dmhelper/gate/ShellRenderGateTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gate/OperationalPreparationRenderGateTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReferenceWorkspaceRenderGateTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gate/NarrativePreparationRenderGateTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gate/MapEditorRenderGateTest.java | sort -u
```

Every distinct surface path in that output must appear in Step 2's surface list. This is the step that stops the consolidation from silently dropping coverage — **Step 2's list is the output of this command, not a list written from memory.**

At the time of writing, that command yields these distinct surfaces. Treat it as the expected result, not as a substitute for running it:

```
/campaigns                    /library                 /adventures
/campaigns/{id}               /library/about           /adventures/{aid}/scenes/{sid}
/party                        /library/hazards         /encounters
/quests                       /library/tables          /maps
/notes                        /library/traps           /maps/{mapId}/edit
/handouts                     /world/factions          /calendar
/ledger                       /world/locations         /audio/cues
/treasury                     /world/npcs              /setup
/sheets
```

The previous draft of this task listed 17 surfaces that were written by hand rather than derived. It **dropped** `/treasury`, `/sheets`, `/setup`, all three `/world/*` surfaces, and all four non-obvious `/library/*` surfaces, and it added `/library/rules` and `/library/spells`, which no deleted gate covered. Reconciling against Step 1 is not a formality.

- [ ] **Step 2: Write the consolidated gate**

Replace the file's test methods with two parameterized tests over surfaces × viewports:

```java
    private record Surface(String name, String path) {}

    private static final int[][] VIEWPORTS = {{1366, 768}, {1920, 1080}};

    /** Every surface the deleted render gates covered. See spec section 7.2. */
    private Stream<Surface> surfaces() {
        String c = "/campaigns/" + seeded.campaignId();
        return Stream.of(
                new Surface("campaign list", "/campaigns"),
                new Surface("campaign dashboard", c),
                new Surface("campaign setup", c + "/setup"),
                new Surface("cockpit", c + "/session"),
                new Surface("party", c + "/party"),
                new Surface("sheets", c + "/sheets"),
                new Surface("adventures", c + "/adventures"),
                new Surface("scene", c + "/adventures/" + seeded.adventureId()
                        + "/scenes/" + seeded.hostileSceneId()),
                new Surface("quests", c + "/quests"),
                new Surface("encounters", c + "/encounters"),
                new Surface("maps", c + "/maps"),
                new Surface("map editor", c + "/maps/" + seeded.playableMapId() + "/edit"),
                new Surface("notes", c + "/notes"),
                new Surface("handouts", c + "/handouts"),
                new Surface("ledger", c + "/ledger"),
                new Surface("treasury", c + "/treasury"),
                new Surface("calendar", c + "/calendar"),
                new Surface("audio cues", c + "/audio/cues"),
                new Surface("world locations", c + "/world/locations"),
                new Surface("world factions", c + "/world/factions"),
                new Surface("world npcs", c + "/world/npcs"),
                new Surface("library", "/library"),
                new Surface("library about", "/library/about"),
                new Surface("library tables", "/library/tables"),
                new Surface("library hazards", "/library/hazards"),
                new Surface("library traps", "/library/traps"));
    }

    @ParameterizedTest(name = "{0} has no horizontal overflow")
    @MethodSource("surfaceViewportMatrix")
    void surfaceDoesNotOverflowHorizontally(Surface surface, int width, int height) {
        page.setViewportSize(width, height);
        PageReady.open(page, "http://localhost:" + port, surface.path());

        Object overflow = page.evaluate(
                "() => document.documentElement.scrollWidth - document.documentElement.clientWidth");

        assertThat(((Number) overflow).intValue())
                .as("%s overflows horizontally at %dx%d", surface.name(), width, height)
                .isLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "{0} keeps content inside the viewport")
    @MethodSource("surfaceViewportMatrix")
    void surfaceDoesNotClipContent(Surface surface, int width, int height) {
        page.setViewportSize(width, height);
        PageReady.open(page, "http://localhost:" + port, surface.path());

        @SuppressWarnings("unchecked")
        List<String> clipped = (List<String>) page.evaluate("""
                () => {
                  const out = [];
                  for (const el of document.querySelectorAll('main *')) {
                    if (!el.textContent.trim()) continue;
                    const style = getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden') continue;
                    if (style.overflow === 'auto' || style.overflow === 'scroll') continue;
                    const box = el.getBoundingClientRect();
                    if (box.width === 0 || box.height === 0) continue;
                    if (box.right > window.innerWidth + 1) {
                      out.push((el.id || el.className || el.tagName) + ' extends past the right edge');
                    }
                  }
                  return out.slice(0, 5);
                }
                """);

        assertThat(clipped)
                .as("%s clips content at %dx%d", surface.name(), width, height)
                .isEmpty();
    }

    static Stream<Arguments> surfaceViewportMatrix() {
        // Built from surfaces() × VIEWPORTS. JUnit requires a static source, so this reads
        // the seeded ids through the shared PER_CLASS instance.
        return INSTANCE.surfaces().flatMap(surface ->
                Stream.of(VIEWPORTS).map(v -> Arguments.of(surface, v[0], v[1])));
    }
```

`INSTANCE` is the `PER_CLASS` test instance captured in `@BeforeAll`. If wiring a static `@MethodSource` to instance state proves awkward, make `surfaces()` static and pass `seeded` ids through a static field set in `@BeforeAll` — the mechanism does not matter, the coverage does.

Accessor names above are taken from `ReleaseRehearsalFixture.Seeded` (`:52-57`): the map id is **`playableMapId()`**, not `mapId()`; the scene surface uses `adventureId()` and `hostileSceneId()`. The cockpit route is `/campaigns/{campaignId}/session` — `SessionController` has no `/session/cockpit` mapping.

- [ ] **Step 3: Run the gate and confirm it passes on current code**

```bash
./mvnw test -P gates -Dtest='ViewportAccessibilityGateTest'
```

Expected: PASS. A failure is one of two things, and they need opposite responses:

- **A 404 or a blank render** means the path is wrong — the surface exists under a different route. Fix the path.
- **An overflow or clipping failure** is a **real defect** in that surface. Fix the CSS, not the assertion.

Do not resolve a 404 by deleting the surface from the list; that is how the coverage Step 1 protects gets dropped.

- [ ] **Step 4: Prove the gate has teeth**

Temporarily add to `src/main/resources/static/css/base.css`:

```css
main { min-width: 2400px; }
```

Re-run the gate.

Expected: FAIL on every surface at both viewports. If it passes, the gate is not measuring what it claims — fix it before proceeding.

Remove the temporary rule and re-run to confirm PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java
git commit -m "test: consolidate appearance gates into one overflow gate

Absorbs the surface coverage of the shell, operational, reference,
narrative and map-editor render gates into two parameterized assertions
over surfaces x viewports: no horizontal overflow, no clipped content.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 15: Delete the appearance gates

**Files:**
- Delete: 14 test classes (2,186 lines), listed below
- Modify: `docs/test-tiers.md`
- Modify: `.gitignore` if it carries a `target/ui-redesign` entry

**Interfaces:**
- Consumes: Task 14's consolidated gate carrying the retained coverage.
- Produces: a browser tier of **17** classes.

The spec's §7 arithmetic is 30 − 14 = 16, and that is right for the classes it names. Task 12 then adds `CockpitPresetSwitchingBrowserTest`, so the tier lands at 17. Spec §10's "30 classes to 16" should read 17 when the spec is updated; no class the spec retains has been dropped.

- [ ] **Step 1: Confirm the consolidated gate is green first**

```bash
./mvnw test -P gates -Dtest='ViewportAccessibilityGateTest'
```

Expected: PASS. Do not delete coverage before its replacement is proven.

- [ ] **Step 2: Delete the appearance gates**

```bash
git rm src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualReviewMatrixGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualFoundationRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/CockpitLaptopFitGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportMatrixGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/ShellRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/OperationalPreparationRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReferenceWorkspaceRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/NarrativePreparationRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/gate/MapEditorRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/visual/SurfaceNestingGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/visual/TypographyRenderGateTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitBottomZoneSizeBrowserTest.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/PartyRailAlignmentBrowserTest.java
```

If `src/test/java/dev/hendrikhoemberg/dmhelper/visual/` is now empty, remove the directory.

- [ ] **Step 3: Remove orphaned helpers**

Some deleted gates owned helpers no other test uses:

```bash
./mvnw -q test-compile
grep -rn "BrowserFailureCollector" src/test/java/ | head
```

Keep any helper still referenced by a retained test. Delete only those with no remaining referent.

- [ ] **Step 4: Remove the screenshot output directory**

```bash
grep -n "ui-redesign" .gitignore
rm -rf target/ui-redesign
```

Remove the `.gitignore` entry if present — nothing writes there now.

- [ ] **Step 5: Re-measure both tiers**

```bash
time ./mvnw test
time ./mvnw test -P gates
```

Record both wall-clock times and both test counts from the surefire summary. These are the real numbers for Step 6 — do not estimate them.

- [ ] **Step 6: Update the test tiers document**

In `docs/test-tiers.md`, update the table at the top with the measured figures from Step 5, and correct the "21 Playwright classes" claim at `:19` — that figure was already stale before this work (the tier held 30), and the tier now holds 17. Add a line recording what changed:

```markdown
**Updated 2026-08-04.** The appearance gates were consolidated into a single
overflow gate (`gate/ViewportAccessibilityGateTest`); 14 style and render gates
were deleted and `CockpitPresetSwitchingBrowserTest` was added, taking the browser
tier from 30 classes to 17. Visual design requirements remain in force as design
intent — they are no longer machine-enforced.
```

- [ ] **Step 7: Run the full suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "test: delete the appearance gates

Removes 14 style and render gate classes (~2,186 lines) whose coverage is
either absorbed by the consolidated overflow gate or asserted properties
of the deleted layout engine. Browser tier: 30 classes to 17.

Visual design requirements remain in force as intent; enforcement is no
longer automated.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

# Stage 5 — Trim the export surface and reconcile documentation

### Task 16: Remove dice-roll history from the export surface

The existing package `lmop-de3.dmcampaign` carries a `diceRolls` key at manifest top level, so the schema **must keep accepting the field**. It becomes optional-and-ignored: schema keeps the property, importer discards it, exporter stops writing it. The manifest version is **not** bumped.

**Files:**
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/packagev2/DiceSectionAdapter.java` (136)
- Delete: the `DiceSectionAdapter` test
- Modify: `campaign/packagev2/model/CampaignManifestV2.java` — remove `DiceRollDto` and the `diceRolls` component
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`

**Interfaces:**
- Consumes: Task 2's relocated round-trip infrastructure.
- Produces: a manifest with no `diceRolls` on write, tolerating it on read.

- [ ] **Step 1: Make `diceRolls` optional in the schema, keeping its definition**

Measured state: `diceRolls` **is** in the schema's top-level `required` array, and `additionalProperties` **is** `false`. Both facts matter, in opposite directions:

- Because it is `required`, a newly exported manifest that omits it would **fail validation**. So `diceRolls` must be removed from the `required` array.
- Because `additionalProperties` is `false`, an existing package that still carries it would **fail validation** if the property definition were deleted. So the property definition must stay.

Remove `"diceRolls"` from the top-level `required` array in `campaign-format-v2.schema.json`. Leave `properties.diceRolls` exactly as it is.

Confirm both conditions after editing:

```bash
python3 -c "
import json
s=json.load(open('src/main/resources/schemas/campaign-format-v2.schema.json'))
print('removed from required:', 'diceRolls' not in (s.get('required') or []))
print('definition retained:', 'diceRolls' in s.get('properties', {}))
"
```

Expected: both `True`. Note that `src/test/resources/campaigns/v2/minimal.dmcampaign.json` carries `diceRolls: []` and must continue to validate unchanged — it is the regression check for the second condition.

- [ ] **Step 2: Delete the adapter and its test**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/dice/packagev2/DiceSectionAdapter.java
git rm src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/DiceSectionAdapterTest.java
```

Confirm the test's exact path first — it may sit under `dice/packagev2/`.

- [ ] **Step 3: Remove the manifest component**

In `CampaignManifestV2.java`, remove the `DiceRollDto` record and the `diceRolls` component from the manifest record. Jackson must still tolerate the field on read — confirm the manifest deserialization is not configured with `FAIL_ON_UNKNOWN_PROPERTIES`:

```bash
grep -rn "FAIL_ON_UNKNOWN_PROPERTIES" src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/
```

Expected: no hits (that setting belonged to the deleted v1 `CampaignImportValidator`). If a hit remains, the importer would reject the old package — configure that mapper to ignore unknown properties.

- [ ] **Step 4: Update the manifest contract test**

`CampaignManifestV2ContractTest` (776 lines) asserts the manifest's component set. Remove its `diceRolls` expectations. Add one asserting tolerance:

```java
    @Test
    void manifestStillDeserializesWhenAPackageCarriesLegacyDiceRolls() {
        String json = """
                {"formatVersion":2,"campaign":{"key":"c-1","name":"Old"},"diceRolls":[]}""";

        CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);

        assertThat(manifest.campaign().name()).isEqualTo("Old");
    }
```

Adjust the envelope to the minimal valid shape established in Task 4 Step 1.

- [ ] **Step 5: Run the round-trip and contract tests**

```bash
./mvnw test -Dtest='CampaignManifestV2ContractTest,CampaignCompleteRoundTripTest,CampaignSectionRegistryTest'
```

Expected: PASS. `CampaignSectionRegistryTest` (576 lines) asserts the registered adapter set and will fail until its dice expectation is removed.

- [ ] **Step 6: Verify the real package still imports**

```bash
./mvnw spring-boot:run
```

Import `~/Documents/DnDCampaigns/lmop-de3.dmcampaign` — the one carrying `diceRolls`. It must import without error. Export the campaign and confirm the new manifest has no `diceRolls` key:

```bash
unzip -p <exported file> manifest.json | python3 -c "import json,sys; print('diceRolls' in json.load(sys.stdin))"
```

Expected: `False`. Stop the app.

- [ ] **Step 7: Run the suite and commit**

```bash
./mvnw test -P gates
git add -A
git commit -m "feat!: drop dice-roll history from campaign packages

A roll log is not campaign content. The exporter stops writing diceRolls;
the schema keeps the property and the importer ignores it, so packages
written before this change still import. Format stays v2.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 17: Reconcile documentation and archive superseded process artifacts

**Files:**
- Create: `docs/superpowers/archive/README.md`
- Move: superseded plans and specs into `docs/superpowers/archive/`
- Modify: `docs/dm-manual/*.md`, `docs/authoring/*.md`, `docs/agent/*.md`, `docs/README.md`, `docs/campaign-capabilities.md`

**Interfaces:**
- Consumes: all prior tasks complete.
- Produces: documentation that describes only what exists.

- [ ] **Step 1: Find every statement describing a removed capability**

```bash
grep -rniE "legacy|v1 json|dmcampaign\.json|custom preset|drag|dock|split ratio|edit layout|add module" \
  docs/dm-manual docs/authoring docs/agent docs/product docs/README.md docs/campaign-capabilities.md
```

Review every hit. Each is either a statement to correct or an incidental word match — read the surrounding paragraph before editing.

- [ ] **Step 2: Correct the DM manual**

`docs/dm-manual/` has ten chapters. Chapters covering backup, import/export, and the session cockpit will describe removed behaviour. Rewrite:
- any instruction to export or import legacy v1 JSON
- any description of creating, renaming, or deleting custom cockpit presets
- any description of dragging modules between zones or resizing them

Replace the cockpit-layout material with the fixed-preset behaviour: four layouts, picker or Digit1-Digit4, per-zone collapse, choice remembered per browser.

- [ ] **Step 3: Correct the capability matrix**

`docs/campaign-capabilities.md` is referenced by `SPEC.md` as the authority on implemented capabilities, and `GET /api/v1/capabilities` is documented alongside it. Remove entries for v1 import/export, custom layout presets, and — if it is listed — the served v1 map-document schema removed in Task 8.

Then check whether the endpoint itself reports them:

```bash
grep -rn "capabilities" src/main/java/dev/hendrikhoemberg/dmhelper/agent/
```

If `CapabilityManifest` advertises a removed capability, remove it there too — a manifest that lies is worse than no manifest.

- [ ] **Step 4: Archive superseded process artifacts**

`docs/superpowers/` holds ~74,000 lines across 46 plans, 16 specs, and 5 verification records — the spec §8.3 figures of 45 and 15 predate this remediation's own two documents. These are the record of why the code looks as it does and are **archived, not deleted**.

```bash
mkdir -p docs/superpowers/archive
```

Move plans and specs whose work is complete and superseded into `docs/superpowers/archive/`, preserving the `plans/` and `specs/` subdivision. **Leave in place:** this remediation's spec and plan, and anything an active plan references.

- [ ] **Step 5: Write the archive index**

Create `docs/superpowers/archive/README.md`:

```markdown
# Archived plans and specifications

Completed and superseded design documents, kept as the record of why the
codebase looks as it does. They describe the state of the product at the time
they were written and are **not** current documentation — several describe
capabilities since removed (see
`../specs/2026-08-04-overengineering-remediation-design.md`).

For current behaviour see `docs/campaign-capabilities.md` and `SPEC.md`.
```

- [ ] **Step 6: Update the docs index**

`docs/README.md` links the documentation set. Add the archive, and confirm every link it lists still resolves:

```bash
grep -o '](\([^)]*\))' docs/README.md | sed 's/](\(.*\))/\1/' | while read -r link; do
  case "$link" in
    http*) continue ;;
  esac
  [ -e "docs/$link" ] || [ -e "$link" ] || echo "BROKEN: $link"
done
```

Fix every `BROKEN:` line. `docs/campaign-format-v1.md` was deleted in Task 9 and will appear here if still linked.

- [ ] **Step 7: Run the suite**

```bash
./mvnw test -P gates
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Final verification against the spec's acceptance criteria**

Run the whole checklist from spec §9 in one pass:

```bash
./mvnw test -P gates
./mvnw spring-boot:run
```

With the app running, confirm:
1. Import `lmop-de3.dmcampaign` — no errors.
2. Export it — downloads, and the manifest carries no `diceRolls`.
3. Import `/tmp/legacy.dmcampaign.json` — clean `UNSUPPORTED_FORMAT_VERSION`, no stack trace.
4. Cockpit at 1366×768: all four presets, digit shortcuts, zone collapse, choice survives reload.
5. Session log still populates from scene visits.

Stop the app. Any failure is a defect to fix before the final commit.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "docs: reconcile documentation and archive superseded artifacts

Corrects the DM manual, authoring guide, agent reference and capability
matrix to describe only what exists. Moves completed plans and superseded
specs under docs/superpowers/archive/ with an index.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Completion

After Task 17, verify the outcome against spec §10:

```bash
find src/main/java -name "*.java" | xargs wc -l | tail -1        # expect ~52,100
find src/main/resources/static -type f | xargs wc -l | tail -1   # expect ~19,800
find src/test/java -name "*.java" | xargs wc -l | tail -1        # expect ~64,400
grep -rl '@Tag("browser")' src/test/java | wc -l                 # expect 17
wc -l src/main/resources/static/js/cockpit-layout.js              # expect ~600
```

Two targets differ from spec §10, both traced to the corrected Stage 3 projection in the Stage 3 preamble:

- **Static ~19,800, not ~19,400.** `cockpit-layout.js` lands at ~600 rather than ~200 because the module-placement engine and zone tabs are retained behaviour. Stage 3's static saving is ~2,100 rather than 2,498.
- **Browser tier 17, not 16.** Task 12 adds `CockpitPresetSwitchingBrowserTest`.

Both are projections that spec §10 marks with `~`, and neither repeals a requirement or changes what the application does. Update spec §6.3, §7, and §10 to match once this lands.

Figures within a few hundred lines of target are fine. A large divergence means a task deleted more or less than planned — investigate before merging.

**Capabilities deliberately withdrawn — five, not four.** The spec names v1 campaign import, v1 campaign export, custom cockpit layout presets, and automated enforcement of visual style requirements. Task 8 adds a fifth: the served v1 map-document schema at `GET /api/v1/schemas/map-document.schema.json`, withdrawn because it describes a format the application can no longer read. The v2 equivalent at `/api/v1/schemas/map-document-v2.schema.json` is unaffected.

No surviving capability is degraded. In particular, verify by hand before merging that the cockpit still does all of: place every module in its assigned zone across all four presets, switch zone tabs by click and by arrow key, open and leave module focus mode, and show module loading/empty/error messages. Those four are the behaviours most at risk from Stage 3, and only the first is covered by a new test.
