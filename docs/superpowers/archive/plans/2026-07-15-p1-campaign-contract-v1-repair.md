# P1 Campaign Contract v1 Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing format-version-1 campaign schema, Jackson DTO, dry-run endpoint, importer, exporter, and embedded map schema one executable contract before version 2 work begins.

**Architecture:** Add a small validation pipeline in `campaign/service/validation`: parse once, validate the raw document against the published draft-2020-12 schemas, deserialize only schema-valid input, then run v1 cross-reference and spatial checks. `CampaignService` retains its current export and persistence orchestration for this checkpoint, but both dry-run and real import consume the same `CampaignValidationResult`; no entity or file is created until that result is importable. Runtime schema validation uses networknt's Jackson-3-compatible validator, while checked-in fixtures exercise both directions of the contract: schema-valid input must deserialize/import, and every exported document must validate.

**Tech Stack:** Java 25, Spring Boot 4.1, Jackson 3, networknt JSON Schema Validator 3.0.2, JSON Schema draft 2020-12, Spring MVC, Spring Data JPA, H2, JUnit 5, AssertJ, Mockito, Maven Wrapper.

## Global Constraints

- Runtime features and validation must work without internet access; schemas and referenced schemas load only from the classpath.
- No new frontend build chain or runtime CDN is introduced.
- Format version remains exactly `1`; this checkpoint does not introduce `.dmcampaign` ZIP containers or format-version-2 keys.
- Plain `.dmcampaign.json` remains the only container handled by this checkpoint.
- Existing v1 names and IDs remain the reference vocabulary; duplicate or ambiguous names are errors when a v1 reference depends on them.
- JSON Schema draft 2020-12 remains authoritative and every closed contract object sets `additionalProperties: false`.
- `campaign-format.schema.json` references `map-document.schema.json`; it does not duplicate the map document shape.
- Dry-run and real import execute the same parse, schema, DTO, semantic, and catalog-resolution pipeline.
- Schema or semantic errors block import before the first database write or handout-file write.
- Warnings do not block v1 import; warning confirmation belongs to the package-v2 preview milestone.
- Successful validation is response status metadata, not a synthetic warning/problem.
- Token `positionX` and `positionY` are pixels from the map's top-left; token `sizeCols` and `sizeRows` are grid-cell units.
- Scene pins are pixels from the map's top-left. Map-document cells and primitives are zero-based grid-cell coordinates. Map-document shape points are grid-cell coordinates.
- Normal and imported handouts use generated UUID storage names. A package-supplied `fileName` is extension/display metadata only and is never used as a storage path.
- This plan repairs the current v1 surface; adding campaign settings/current scene, party current HP, full handout state, combat/dice logs, calendar settings, and every other persistent field remains the separate “Complete round-trip” milestone from master-spec delivery item 4.
- Tests use an isolated home directory through `-DargLine=-Duser.home=/tmp/dmhelper-p1-v1`.

## File Structure

### Files created

- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportSeverity.java` — `ERROR`, `WARNING`, and `INFO` vocabulary.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportProblem.java` — stable structured problem contract.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignValidationResult.java` — problems plus the parsed DTO for internal import reuse.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidator.java` — offline draft-2020-12 validation of raw v1 JSON.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidator.java` — pure v1 uniqueness, reference, state, and spatial checks.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidator.java` — ordered parse/schema/DTO/semantic pipeline.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java` — schema/meta-schema, fixture, unknown-property, and `$ref` coverage.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java` — schema-valid-to-DTO and DTO-to-schema compatibility.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidatorTest.java` — exact v1 problem codes and JSON pointers.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidatorTest.java` — stage ordering and parse-once behavior.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java` — served schema and referenced-schema contract.
- `src/test/resources/campaigns/v1/minimal.dmcampaign.json` — smallest accepted v1 document.
- `src/test/resources/campaigns/v1/feature-complete.dmcampaign.json` — every current v1 DTO section and nested shape.
- `src/test/resources/campaigns/v1/invalid-unknown-property.dmcampaign.json` — closed-contract regression fixture.
- `docs/campaign-format-v1.md` — implemented v1 contract, validation sequence, field units, and explicit v1 exclusions.

### Files modified

- `pom.xml` — add the Jackson-3 JSON Schema validator dependency and version property.
- `src/main/resources/schemas/map-document.schema.json` — match `MapDocumentDto`/`MapLayerDto` exactly and close every object.
- `src/main/resources/schemas/campaign-format.schema.json` — match `CampaignExportDto` exactly, close every object, and reference the map schema.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java` — omit optional nulls consistently and document canonical v1 output.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` — delegate validation, import only a validated DTO, fail export on missing assets, and eliminate silent reference degradation.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java` — return structured dry-run metadata and accept `.dmcampaign.json` explicitly.
- `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java` — expose UUID-named byte-array storage for validated embedded v1 handouts.
- `src/main/resources/templates/campaigns/detail.html` — label v1 JSON import/export accurately and accept `.dmcampaign.json`.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java` — shared-validation and pre-persistence rejection coverage.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` — exported-schema and validated-import coverage.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java` — structured dry-run response contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java` — imported storage-name isolation and collision coverage.
- `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` — record only the v1 contract checkpoint as complete.

## Contract Decisions Locked by This Plan

| Concern | v1 checkpoint decision |
|---|---|
| Unknown JSON properties | Schema error; Jackson remains strict as a defense in depth. |
| Missing optional top-level arrays | Accepted and interpreted as empty, preserving existing v1 inputs. |
| Missing values used by `Enum.valueOf`, map creation, or non-null columns | Schema error through exact `required` lists. |
| Null optional strings/maps/lists in exports | Omitted through `NON_NULL`; schemas describe omission, not nullable values. |
| Map grid type | Relational map grid is `SQUARE`; embedded map-document grid is `square`, matching the two existing Java models. |
| Schema-valid but semantically unresolved reference | Structured `ERROR`; import is blocked. |
| Duplicate name used as a v1 reference | `AMBIGUOUS_REFERENCE` error at both duplicates and consumers. |
| Missing handout source file during export | Export fails visibly; it does not emit `imageData: null`. |
| Imported handout filename | Never used as a path; storage gets a new UUID name with an allow-listed extension. |
| Dry-run success | `{ "valid": true, "status": "READY", "problems": [] }`. |
| Dry-run failure | HTTP 200 with `valid: false`, `status: "BLOCKED"`, and structured problems so authoring agents can repair the file. |
| Direct import failure | HTTP 400 through the existing validation-error handler; no campaign or asset survives. |
| v2 preview/warning confirmation | Not part of this checkpoint. |

## Migration Analysis

- No relational model changes are required, so this checkpoint creates no Flyway migration and does not alter existing database rows.
- Closing v1 schema objects is a compatibility repair, not a new runtime restriction: `CampaignService` already enables `FAIL_ON_UNKNOWN_PROPERTIES`, so documents containing unknown fields are currently rejected by import even though the published schema accepts them.
- Correcting the map enums and nested shapes makes the published schema accept the documents emitted and consumed by `MapDocumentDto`/`MapLayerDto`; it does not migrate stored map CLOBs.
- Removing `campaign.currentScene` from the v1 schema documents the current executable behavior. The field is not present in `CampaignExportDto` and strict import already rejects it. Stable current-scene representation belongs to format version 2 and the complete round-trip milestone.
- Optional top-level section arrays remain optional and map to empty collections, preserving small legacy v1 authoring files.
- Existing exported v1 files that the runtime can import continue to import. Files that only passed the over-permissive old schema receive structured repair errors instead of failing later in Jackson or persistence.
- Stored handout files are not renamed in place. Only newly imported handouts receive generated storage names, matching the existing normal-upload policy.
- The implementation report must call out this behavioral tightening and the exact fixture set used to prove compatibility.

---

### Task 1: Introduce the offline schema-validation and problem contracts

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportSeverity.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportProblem.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignValidationResult.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidator.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java`

**Interfaces:**
- Consumes: raw UTF-8 campaign JSON and classpath resources `schemas/campaign-format.schema.json` and `schemas/map-document.schema.json`.
- Produces: `CampaignSchemaValidator.validate(String json) -> List<CampaignImportProblem>` and the shared problem/result types used by every later task.

- [ ] **Step 1: Add a failing schema-validator smoke test**

Create `CampaignSchemaValidatorTest` with the initial contract:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignSchemaValidatorTest {
    private final CampaignSchemaValidator validator = new CampaignSchemaValidator();

    @Test
    void acceptsTheSmallestV1Document() {
        var problems = validator.validate("""
                {"formatVersion":1,"campaign":{"name":"Smallest"}}
                """);

        assertThat(problems).isEmpty();
    }

    @Test
    void reportsJsonPointerAndStableCodeForSchemaFailure() {
        var problems = validator.validate("""
                {"formatVersion":1,"campaign":{"name":""}}
                """);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(ImportSeverity.ERROR);
            assertThat(problem.code()).isEqualTo("SCHEMA_MIN_LENGTH");
            assertThat(problem.path()).isEqualTo("/campaign/name");
            assertThat(problem.message()).contains("must");
        });
    }
}
```

- [ ] **Step 2: Run the test and verify the missing contracts fail compilation**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: test compilation fails because `CampaignSchemaValidator`, `CampaignImportProblem`, and `ImportSeverity` do not exist.

- [ ] **Step 3: Add the Jackson-3-compatible validator dependency**

Add the property and dependency to `pom.xml`:

```xml
<properties>
    <java.version>25</java.version>
    <commonmark.version>0.22.0</commonmark.version>
    <json-schema-validator.version>3.0.2</json-schema-validator.version>
</properties>
```

```xml
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>${json-schema-validator.version}</version>
</dependency>
```

Use the 3.x line because this application is on Jackson 3 (`tools.jackson.*`) and Java 25. Do not add a second Jackson version.

- [ ] **Step 4: Add the shared problem vocabulary**

Create the three records/enums:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

public enum ImportSeverity {
    ERROR, WARNING, INFO
}
```

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

public record CampaignImportProblem(
        ImportSeverity severity,
        String code,
        String path,
        String message,
        String suggestion
) {
    public CampaignImportProblem {
        path = path == null || path.isBlank() ? "/" : path;
    }
}
```

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;

import java.util.List;
import java.util.Optional;

public record CampaignValidationResult(
        Optional<CampaignExportDto> campaign,
        List<CampaignImportProblem> problems
) {
    public CampaignValidationResult {
        campaign = campaign == null ? Optional.empty() : campaign;
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public boolean valid() {
        return problems.stream().noneMatch(p -> p.severity() == ImportSeverity.ERROR);
    }

    public CampaignExportDto requireImportable() {
        if (!valid() || campaign.isEmpty()) {
            String summary = problems.stream()
                    .filter(p -> p.severity() == ImportSeverity.ERROR)
                    .map(p -> p.path() + ": " + p.message())
                    .findFirst()
                    .orElse("Campaign import did not produce a validated document");
            throw new IllegalArgumentException(summary);
        }
        return campaign.orElseThrow();
    }
}
```

- [ ] **Step 5: Implement offline draft-2020-12 schema validation**

Create `CampaignSchemaValidator` so it loads both schemas once, resolves the campaign schema's `$ref` from the in-memory classpath map, uses JSON Pointer paths, and converts validator errors deterministically:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class CampaignSchemaValidator {
    public static final String CAMPAIGN_ID = "https://dmhelper/campaign-format.schema.json";
    public static final String MAP_ID = "https://dmhelper/map-document.schema.json";

    private final Schema schema;

    public CampaignSchemaValidator() {
        String campaignSchema = read("schemas/campaign-format.schema.json");
        String mapSchema = read("schemas/map-document.schema.json");
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(
                        CAMPAIGN_ID, campaignSchema,
                        MAP_ID, mapSchema)));
        this.schema = registry.getSchema(SchemaLocation.of(CAMPAIGN_ID));
    }

    public List<CampaignImportProblem> validate(String json) {
        return schema.validate(json, InputFormat.JSON, context ->
                        context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream()
                .map(error -> new CampaignImportProblem(
                        ImportSeverity.ERROR,
                        "SCHEMA_" + error.getKeyword().toUpperCase(Locale.ROOT).replace('-', '_'),
                        error.getInstanceLocation().toString(),
                        error.getMessage(),
                        "Match the field type, required fields, enum, range, or closed-object shape in campaign-format.schema.json."))
                .sorted(Comparator.comparing(CampaignImportProblem::path)
                        .thenComparing(CampaignImportProblem::code))
                .toList();
    }

    private static String read(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Required campaign schema is unavailable: " + path, e);
        }
    }
}
```

- [ ] **Step 6: Run the smoke test**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: both tests pass.

- [ ] **Step 7: Commit the validation foundation**

```bash
git add pom.xml src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java
git commit -m "feat: add campaign schema validation foundation"
```

### Task 2: Make the map-document schema match the runtime DTO

**Files:**
- Modify: `src/main/resources/schemas/map-document.schema.json`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`

**Interfaces:**
- Consumes: `MapDocumentDto`, `MapLayerDto`, and the `https://dmhelper/map-document.schema.json` identifier.
- Produces: one closed embedded-map contract referenced by both standalone map validation and campaign validation.

- [ ] **Step 1: Add failing tests for the actual map DTO shapes**

Add tests that serialize `MapDocumentDto.createDefault(...)` and a rich document containing an image layer, lowercase shape discriminators, a flat `points` array, grid movement fields, primitives, and custom terrain:

```java
@Test
void acceptsRuntimeMapDocumentShapesThroughCampaignRef() throws Exception {
    String json = resource("campaigns/v1/feature-complete.dmcampaign.json");
    assertThat(validator.validate(json)).isEmpty();
}

@Test
void rejectsUnknownNestedMapProperty() {
    String json = """
            {
              "formatVersion": 1,
              "campaign": {"name": "Closed map"},
              "maps": [{
                "key":"map-1", "name":"Map", "movementMode":"GRID", "showGrid":true,
                "grid":{"w":10,"h":8,"cellPx":48,"gridType":"SQUARE"},
                "document":{
                  "schemaVersion":1,
                  "grid":{"width":10,"height":8,"cellSizePx":48,"gridType":"square","movementMode":"GRID","showGrid":true,"mystery":1},
                  "layers":[],"primitives":[],"customTerrain":[]
                },
                "tokens":[]
              }]
            }
            """;

    assertThat(validator.validate(json))
            .extracting(CampaignImportProblem::code)
            .containsExactly("SCHEMA_ADDITIONAL_PROPERTIES");
}

private static String resource(String path) throws Exception {
    try (var in = new ClassPathResource(path).getInputStream()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the tests and capture the existing drift**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest,GameMapServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: failures show at least `IMAGE` missing from the layer enum, shape representation mismatch, and the nested unknown property being accepted.

- [ ] **Step 3: Replace the map-document schema with the exact DTO contract**

Keep the existing `$schema`, `$id`, title, and units description. Apply all of these exact changes:

- root, `grid`, layer, cell, shape, image, primitive, and custom-terrain objects each set `additionalProperties: false`;
- root requires `schemaVersion`, `grid`, and `layers`;
- grid requires `width`, `height`, `cellSizePx`, and `gridType`; its properties also include `movementMode` with enum `GRID|FREEFORM` and `showGrid` boolean;
- grid `gridType` remains lowercase `square` because `MapDocumentDto.GridDto` uses lowercase;
- layer `type` enum is `TERRAIN|OBJECTS|ANNOTATIONS|IMAGE`;
- layer `image` is an object with required `dataUrl`, `x`, `y`, `width`, and `height`, not a string;
- shape `type` enum is lowercase `rect|circle|line|polygon`;
- shape contains `points` as a flat numeric array, `fill`, `stroke`, `strokeWidth`, and `label`; remove the nonexistent `x`, `y`, `width`, `height`, and `radius` fields;
- primitive `type` enum is `ROOM|CORRIDOR|DOOR|REGION` and exposes `startCol`, `startRow`, `endCol`, `endRow`, and `terrain`;
- all coordinates are finite numbers/integers with non-negative minima where the runtime requires non-negative coordinates;
- image `dataUrl` accepts only `^data:image/(png|jpeg|gif|webp);base64,` in v1.

The corrected shape and image definitions are:

```json
"shape": {
  "type": "object",
  "additionalProperties": false,
  "required": ["type", "points"],
  "properties": {
    "type": { "type": "string", "enum": ["rect", "circle", "line", "polygon"] },
    "points": { "type": "array", "items": { "type": "number" } },
    "fill": { "type": "string" },
    "stroke": { "type": "string" },
    "strokeWidth": { "type": "number", "minimum": 0 },
    "label": { "type": "string" }
  }
},
"image": {
  "type": "object",
  "additionalProperties": false,
  "required": ["dataUrl", "x", "y", "width", "height"],
  "properties": {
    "dataUrl": { "type": "string", "pattern": "^data:image/(png|jpeg|gif|webp);base64," },
    "x": { "type": "number" },
    "y": { "type": "number" },
    "width": { "type": "number", "exclusiveMinimum": 0 },
    "height": { "type": "number", "exclusiveMinimum": 0 }
  }
}
```

- [ ] **Step 4: Add a direct DTO-to-schema assertion to `GameMapServiceTest`**

Serialize the default and rich documents with the application's Jackson mapper, wrap them in the minimal campaign envelope, and assert `CampaignSchemaValidator.validate(...)` returns an empty list. This catches future DTO changes even when campaign import tests do not create that map feature.

- [ ] **Step 5: Run focused map/schema tests**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest,GameMapServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit the map contract repair**

```bash
git add src/main/resources/schemas/map-document.schema.json src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java
git commit -m "fix: align map schema with runtime document"
```

### Task 3: Close and exhaustively test the campaign v1 schema/DTO contract

**Files:**
- Modify: `src/main/resources/schemas/campaign-format.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Create: `src/test/resources/campaigns/v1/minimal.dmcampaign.json`
- Create: `src/test/resources/campaigns/v1/feature-complete.dmcampaign.json`
- Create: `src/test/resources/campaigns/v1/invalid-unknown-property.dmcampaign.json`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSchemaValidatorTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java`

**Interfaces:**
- Consumes: every record component in `CampaignExportDto` and `MapDocumentDto`.
- Produces: canonical v1 fixtures and the invariant “schema valid ⇔ strict DTO readable; DTO serialization ⇒ schema valid.”

- [ ] **Step 1: Check in minimal and feature-complete fixtures**

The minimal fixture is exactly:

```json
{
  "formatVersion": 1,
  "campaign": {
    "name": "Minimal Contract Fixture"
  }
}
```

The feature-complete fixture must contain every current top-level DTO section and every nested record at least once. Use deterministic v1 references:

```text
map key: map-crypt
map name: The Crypt
token id: token-goblin-1
party member: Aria
statblock sourceKey: custom_goblin-captain
encounterKey: crypt-guardians
handout title: Warning Plaque
note title: Crypt Lore
assignment id: 00000000-0000-0000-0000-000000000101
ledger id: 00000000-0000-0000-0000-000000000102
timeline id: 00000000-0000-0000-0000-000000000103
scene key: crypt-entry
```

Include a 1×1 base64 PNG data URL, all four map layer types, all four shape types, all four primitive types, a custom terrain entry, a sheet with class levels/resources/spells/slots, two combatants, quick notes for `CAMPAIGN`, `MAP`, `PARTY_MEMBER`, `STATBLOCK`, `NOTE`, `HANDOUT`, `ENCOUNTER`, and `SCENE`, linked assignment/ledger/timeline entries, and one adventure/chapter/scene. Use `CAMPAIGN` as the campaign target sentinel and `Crypt Descent/Chapter 1/crypt-entry` as the scene target path. Use catalog keys already present in seed data for species, background, class, feat, spell, magic item, and equipment item.

The invalid fixture is the minimal fixture plus `"unexpected": true` inside `campaign`.

- [ ] **Step 2: Add bidirectional compatibility tests before changing the schema**

Create `CampaignDtoSchemaCompatibilityTest`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignDtoSchemaCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CampaignSchemaValidator schema = new CampaignSchemaValidator();

    @Test
    void everyCheckedInValidFixtureDeserializesIntoTheStrictDto() throws Exception {
        for (String name : new String[]{"minimal", "feature-complete"}) {
            String json = resource("campaigns/v1/" + name + ".dmcampaign.json");
            assertThat(schema.validate(json)).as(name).isEmpty();
            assertThat(mapper.readValue(json, CampaignExportDto.class).formatVersion()).isEqualTo(1);
        }
    }

    @Test
    void serializingTheFeatureCompleteDtoProducesSchemaValidJson() throws Exception {
        CampaignExportDto dto = mapper.readValue(
                resource("campaigns/v1/feature-complete.dmcampaign.json"),
                CampaignExportDto.class);

        String serialized = mapper.writeValueAsString(dto);

        assertThat(schema.validate(serialized)).isEmpty();
    }

    private static String resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

Add a reflection-backed property-set test so a later optional Java component cannot escape the rich fixture by serializing as null:

```java
@Test
void everyClosedSchemaObjectHasExactlyItsJavaRecordComponents() throws Exception {
    JsonNode campaignSchema = mapper.readTree(resource("schemas/campaign-format.schema.json"));
    JsonNode mapSchema = mapper.readTree(resource("schemas/map-document.schema.json"));

    Map<Class<?>, JsonNode> contracts = Map.ofEntries(
            entry(CampaignExportDto.class, campaignSchema),
            entry(CampaignExportDto.CampaignDto.class, def(campaignSchema, "campaign")),
            entry(CampaignExportDto.PartyMemberExportDto.class, def(campaignSchema, "partyMember")),
            entry(CampaignExportDto.SheetExportDto.class, def(campaignSchema, "sheet")),
            entry(CampaignExportDto.ClassLevelExportDto.class, def(campaignSchema, "classLevel")),
            entry(CampaignExportDto.ResourceExportDto.class, def(campaignSchema, "resource")),
            entry(CampaignExportDto.SpellRefExportDto.class, def(campaignSchema, "spellRef")),
            entry(CampaignExportDto.StatBlockExportDto.class, def(campaignSchema, "statBlock")),
            entry(CampaignExportDto.HandoutExportDto.class, def(campaignSchema, "handout")),
            entry(CampaignExportDto.MapExportDto.class, def(campaignSchema, "map")),
            entry(CampaignExportDto.MapExportDto.GridDto.class, def(campaignSchema, "mapGrid")),
            entry(CampaignExportDto.MapExportDto.TokenExportDto.class, def(campaignSchema, "token")),
            entry(CampaignExportDto.EncounterExportDto.class, def(campaignSchema, "encounter")),
            entry(CampaignExportDto.CombatantExportDto.class, def(campaignSchema, "combatant")),
            entry(CampaignExportDto.NoteExportDto.class, def(campaignSchema, "note")),
            entry(CampaignExportDto.QuickNoteExportDto.class, def(campaignSchema, "quicknote")),
            entry(CampaignExportDto.AssignmentExportDto.class, def(campaignSchema, "assignment")),
            entry(CampaignExportDto.LedgerExportDto.class, def(campaignSchema, "ledgerEntry")),
            entry(CampaignExportDto.TimelineExportDto.class, def(campaignSchema, "timelineEvent")),
            entry(CampaignExportDto.AdventureExportDto.class, def(campaignSchema, "adventure")),
            entry(CampaignExportDto.ChapterExportDto.class, def(campaignSchema, "chapter")),
            entry(CampaignExportDto.SceneExportDto.class, def(campaignSchema, "scene")),
            entry(MapDocumentDto.class, mapSchema),
            entry(MapDocumentDto.GridDto.class, def(mapSchema, "documentGrid")),
            entry(MapLayerDto.class, def(mapSchema, "layer")),
            entry(MapLayerDto.CellDto.class, def(mapSchema, "cell")),
            entry(MapLayerDto.ShapeDto.class, def(mapSchema, "shape")),
            entry(MapLayerDto.ImageDto.class, def(mapSchema, "image")),
            entry(MapDocumentDto.PrimitiveDto.class, def(mapSchema, "primitive")),
            entry(MapDocumentDto.TerrainDefDto.class, def(mapSchema, "terrain")));

    contracts.forEach((recordType, schemaNode) -> {
        Set<String> javaFields = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(toCollection(TreeSet::new));
        Set<String> schemaFields = new TreeSet<>();
        schemaNode.path("properties").propertyNames().forEachRemaining(schemaFields::add);
        assertThat(schemaFields).as(recordType.getSimpleName()).isEqualTo(javaFields);
        assertThat(schemaNode.path("additionalProperties").booleanValue()).isFalse();
    });
}

private static JsonNode def(JsonNode schema, String name) {
    return schema.path("$defs").path(name);
}
```

Use these exact `$defs` names in the repaired schemas. This test is the automated Java DTO/schema compatibility guard required by the master specification; the rich fixture separately verifies types, constraints, enums, and `$ref` behavior.

- [ ] **Step 3: Run the compatibility tests and record every drift category**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest,CampaignDtoSchemaCompatibilityTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: the rich fixture fails until the schema covers the actual DTO and the DTO omits null optionals consistently.

- [ ] **Step 4: Make null emission canonical in the export DTO**

Annotate `CampaignExportDto` and every nested export record with:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
```

Keep primitive fields emitted. Do not change JSON field names, reference representation, or `CURRENT_FORMAT_VERSION`.

- [ ] **Step 5: Repair and close `campaign-format.schema.json`**

Apply this checklist mechanically against every record component in `CampaignExportDto`:

- root and every `$defs` object use `additionalProperties: false`;
- retain optional top-level arrays for legacy compatibility, but constrain every present array item fully;
- remove `campaign.currentScene`, because no v1 DTO/importer field consumes it;
- root `campaign` contains only `name` and `description`;
- map `document` becomes `{ "$ref": "https://dmhelper/map-document.schema.json" }`;
- outer map `grid.gridType` enum is uppercase `SQUARE`;
- map `movementMode` enum is `GRID|FREEFORM`;
- add `ledger.itemAssignmentRef` as UUID string;
- use `format: uuid` for assignment, ledger, and timeline IDs, but keep map/token IDs as non-empty strings because v1 authored fixtures use package-local strings;
- use `format: date-time` for quick-note `createdAt`, ledger `timestamp`, and any other `Instant` string;
- define complete class-level, resource, spell-reference, ability-score, proficiency, override, and spell-slot structures instead of unconstrained arrays;
- constrain `resetRule` to the actual `SheetResource.ResetRule` values;
- constrain encounter/combatant, note, quick-note target, assignment, ledger, scene-status, and map enums to Java enum/string values used by the importer;
- require every value dereferenced without a null guard by `CampaignService`, including encounter `status`, assignment `id`, ledger `kind`/`direction`, quick-note `targetType`/`targetRef`/`body`, and handout `title`/`fileName`/`contentType`/`imageData`;
- describe units for token position/size, scene pins, grids, currency, and dates;
- constrain handout `contentType` to `image/png|image/jpeg|image/gif|image/webp` and `imageData` to a matching image data URL prefix;
- keep v1 name-based reference descriptions explicit; do not describe them as v2 stable keys.

Use these exact top-level closure rules:

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["formatVersion", "campaign"],
  "properties": {
    "formatVersion": { "type": "integer", "const": 1 },
    "campaign": { "$ref": "#/$defs/campaign" },
    "party": { "type": "array", "items": { "$ref": "#/$defs/partyMember" } },
    "statBlocks": { "type": "array", "items": { "$ref": "#/$defs/statBlock" } },
    "handouts": { "type": "array", "items": { "$ref": "#/$defs/handout" } },
    "maps": { "type": "array", "items": { "$ref": "#/$defs/map" } },
    "encounters": { "type": "array", "items": { "$ref": "#/$defs/encounter" } },
    "notes": { "type": "array", "items": { "$ref": "#/$defs/note" } },
    "quicknotes": { "type": "array", "items": { "$ref": "#/$defs/quicknote" } },
    "assignments": { "type": "array", "items": { "$ref": "#/$defs/assignment" } },
    "ledger": { "type": "array", "items": { "$ref": "#/$defs/ledgerEntry" } },
    "timeline": { "type": "array", "items": { "$ref": "#/$defs/timelineEvent" } },
    "adventures": { "type": "array", "items": { "$ref": "#/$defs/adventure" } }
  }
}
```

- [ ] **Step 6: Validate both schema documents against the draft-2020-12 meta-schema**

Add this contract to `CampaignSchemaValidatorTest` for both checked-in schema resources:

```java
SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
Schema metaSchema = registry.getSchema(
        SchemaLocation.of(Dialects.getDraft202012().getId()));

for (String resource : List.of(
        "schemas/campaign-format.schema.json",
        "schemas/map-document.schema.json")) {
    JsonNode schemaNode = new ObjectMapper().readTree(resource(resource));
    assertThat(metaSchema.validate(schemaNode)).as(resource).isEmpty();
}
```

This test validates schema syntax/vocabulary. The fixture tests separately validate instance behavior and cross-schema `$ref` resolution.

- [ ] **Step 7: Test schema serving and reference IDs**

Create `SchemaControllerTest` as a `@WebMvcTest(SchemaController.class)` and assert:

```java
mockMvc.perform(get("/api/v1/schemas/campaign-format"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/schema+json"))
        .andExpect(jsonPath("$['$schema']").value("https://json-schema.org/draft/2020-12/schema"))
        .andExpect(jsonPath("$['$id']").value(CampaignSchemaValidator.CAMPAIGN_ID));

mockMvc.perform(get("/api/v1/schemas/map-document.schema.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['$id']").value(CampaignSchemaValidator.MAP_ID));
```

Also assert a nonexistent schema returns 404 and traversal-like names return 404.

- [ ] **Step 8: Run all schema/DTO/controller contract tests**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest,CampaignDtoSchemaCompatibilityTest,SchemaControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: all selected tests pass; both valid fixtures validate and deserialize; the unknown-property fixture fails with `SCHEMA_ADDITIONAL_PROPERTIES`.

- [ ] **Step 9: Commit the authoritative v1 schema**

```bash
git add src/main/resources/schemas src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java src/test/resources/campaigns/v1 src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java
git commit -m "fix: make campaign v1 schema authoritative"
```

### Task 4: Add deterministic v1 semantic validation

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidator.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidatorTest.java`

**Interfaces:**
- Consumes: a schema-valid `CampaignExportDto` and read-only global catalog lookups already exposed by repositories.
- Produces: `CampaignSemanticValidator.validate(CampaignExportDto) -> List<CampaignImportProblem>` with stable codes and JSON Pointer paths.

- [ ] **Step 1: Write failing reference and spatial tests**

Build tests from the feature-complete fixture by editing its Jackson tree. Cover these exact cases independently:

```text
DUPLICATE_REFERENCE     /maps/1/key
AMBIGUOUS_REFERENCE     /adventures/0/chapters/0/scenes/0/map
UNRESOLVED_REFERENCE    /encounters/0/map
UNRESOLVED_REFERENCE    /encounters/0/combatants/0/tokenId
UNRESOLVED_REFERENCE    /assignments/0/holderName
UNRESOLVED_REFERENCE    /ledger/0/itemAssignmentRef
UNRESOLVED_REFERENCE    /timeline/0/noteTitle
UNRESOLVED_REFERENCE    /quicknotes/0/targetRef
OUT_OF_BOUNDS           /maps/0/tokens/0/positionX
OUT_OF_BOUNDS           /adventures/0/chapters/0/scenes/0/pin
GRID_MISMATCH           /maps/0/document/grid
INVALID_STATE           /encounters/1/status
```

For `INVALID_STATE`, use two `ACTIVE` encounters; v1 allows at most one active encounter per campaign.

- [ ] **Step 2: Run the semantic tests and verify the validator is absent**

Run:

```bash
./mvnw -Dtest=CampaignSemanticValidatorTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: compilation fails because `CampaignSemanticValidator` does not exist.

- [ ] **Step 3: Implement indexed validation, not repeated scans**

Create one immutable index per reference namespace at the start of `validate`:

```java
record V1Index(
        Map<String, CampaignExportDto.MapExportDto> mapsByKey,
        Map<String, List<CampaignExportDto.MapExportDto>> mapsByName,
        Map<String, CampaignExportDto.EncounterExportDto> encountersByKey,
        Map<String, List<CampaignExportDto.EncounterExportDto>> encountersByName,
        Map<String, CampaignExportDto.StatBlockExportDto> statblocksByKey,
        Map<String, List<CampaignExportDto.StatBlockExportDto>> statblocksByName,
        Map<String, List<CampaignExportDto.PartyMemberExportDto>> partyByName,
        Map<String, List<CampaignExportDto.HandoutExportDto>> handoutsByTitle,
        Map<String, List<CampaignExportDto.NoteExportDto>> notesByTitle,
        Map<String, CampaignExportDto.AssignmentExportDto> assignmentsById,
        Map<String, CampaignExportDto.SceneExportDto> scenesByPath,
        Map<String, CampaignExportDto.MapExportDto.TokenExportDto> tokensById
) {}
```

Use empty lists for omitted top-level arrays. Emit problems in document order, then sort by `path` and `code` before returning.

- [ ] **Step 4: Implement the complete v1 semantic matrix**

Validate every current v1 reference and invariant:

| Consumer | Required target/invariant |
|---|---|
| map key/name and token ID | unique in their v1 namespace |
| party character name | unique when referenced by token, combatant, assignment, or quick note |
| statblock source key | unique among package statblocks; otherwise resolve an existing global SRD/custom key |
| encounter key/name | unique when referenced by scene or quick note |
| handout title, note title | unique when referenced by scene, timeline, or quick note |
| map token `statBlockKey`/`partyMemberName` | package/global statblock and package party member |
| encounter map/combatant token/statblock/party member | exact v1 target |
| scene map/encounter/statblocks/handouts | exact v1 target; name fallback is allowed only when unique |
| quick note target | target exists for all seven supported non-campaign target types; `CAMPAIGN` is the exact campaign sentinel; scenes use `Adventure name/Chapter title/sceneKey` |
| assignment | holder exists and exactly one of magic item, equipment item, or nonblank custom text is present |
| ledger assignment reference | assignment ID exists |
| timeline note reference | unique note title exists |
| sheet species/background/spell/feat/class keys | exact global catalog type exists |
| map token bounds | `x >= 0`, `y >= 0`, `x + sizeCols*cellPx <= w*cellPx`, `y + sizeRows*cellPx <= h*cellPx` |
| embedded map grid | width/height/cell size match outer map; grid type is the documented lowercase equivalent |
| cells/primitives/shapes/images | coordinates and extents remain within the embedded map grid |
| scene pin | `0 <= x < w*cellPx` and `0 <= y < h*cellPx` |
| encounter state | at most one `ACTIVE`; active-turn index is `-1` for no active combatant or within combatant bounds; round and log sequence are non-negative |

Repository-dependent catalog checks must be read-only and must never accept a key from the wrong type. Extract small private helpers such as `resolveUniqueName`, `problem`, `validateMap`, and `validateScene`; keep the public class focused on orchestration.

Require unique adventure names, chapter titles within an adventure, and scene keys within a chapter before constructing scene paths. A missing or duplicate component makes every affected `SCENE` quick-note target an `AMBIGUOUS_REFERENCE` or `UNRESOLVED_REFERENCE`; never fall back to the old scene database UUID.

- [ ] **Step 5: Assert exact problem payloads**

Each test checks all five fields. A representative unresolved problem is:

```java
assertThat(problems).containsExactly(new CampaignImportProblem(
        ImportSeverity.ERROR,
        "UNRESOLVED_REFERENCE",
        "/adventures/0/chapters/0/scenes/0/encounter",
        "Encounter 'crypt-guards' does not exist in this campaign document.",
        "Use encounterKey 'crypt-guardians' or define the missing encounter."
));
```

- [ ] **Step 6: Run semantic tests**

Run:

```bash
./mvnw -Dtest=CampaignSemanticValidatorTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: all semantic tests pass with stable order, code, path, message, and suggestion.

- [ ] **Step 7: Commit semantic validation**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidator.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignSemanticValidatorTest.java
git commit -m "feat: validate campaign v1 references and bounds"
```

### Task 5: Make dry-run and import share one ordered validation pipeline

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidator.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidatorTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java`

**Interfaces:**
- Consumes: `CampaignSchemaValidator`, strict Jackson `ObjectMapper`, and `CampaignSemanticValidator`.
- Produces: `CampaignImportValidator.validate(String) -> CampaignValidationResult`, `CampaignService.validateImport(String) -> CampaignValidationResult`, and structured controller `DryRunResult`.

- [ ] **Step 1: Write failing pipeline-order tests**

Mock the two validators and verify:

- malformed JSON returns one `INVALID_JSON` problem and calls neither schema nor semantic validation;
- schema errors prevent DTO deserialization/semantic validation;
- DTO drift after a clean schema result becomes `DTO_SCHEMA_DRIFT`, not a stack trace;
- semantic validation runs exactly once on a schema-valid DTO;
- a valid document stores the parsed DTO in `CampaignValidationResult`, allowing import to reuse it.

Representative malformed JSON assertion:

```java
assertThat(result.problems()).containsExactly(new CampaignImportProblem(
        ImportSeverity.ERROR,
        "INVALID_JSON",
        "/",
        "Campaign file is not valid JSON.",
        "Fix the JSON syntax near line 1, column 20."
));
assertThat(result.campaign()).isEmpty();
```

- [ ] **Step 2: Implement the pipeline**

Create `CampaignImportValidator`:

```java
@Component
public final class CampaignImportValidator {
    private final ObjectMapper mapper;
    private final CampaignSchemaValidator schemaValidator;
    private final CampaignSemanticValidator semanticValidator;

    public CampaignImportValidator(ObjectMapper mapper,
                                   CampaignSchemaValidator schemaValidator,
                                   CampaignSemanticValidator semanticValidator) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.schemaValidator = schemaValidator;
        this.semanticValidator = semanticValidator;
    }

    public CampaignValidationResult validate(String json) {
        try {
            mapper.readTree(json);
        } catch (JacksonException e) {
            return new CampaignValidationResult(Optional.empty(), List.of(invalidJson(e)));
        }

        List<CampaignImportProblem> schemaProblems = schemaValidator.validate(json);
        if (!schemaProblems.isEmpty()) {
            return new CampaignValidationResult(Optional.empty(), schemaProblems);
        }

        CampaignExportDto dto;
        try {
            dto = mapper.readValue(json, CampaignExportDto.class);
        } catch (JacksonException e) {
            return new CampaignValidationResult(Optional.empty(), List.of(dtoDrift(e)));
        }

        List<CampaignImportProblem> semanticProblems = semanticValidator.validate(dto);
        return new CampaignValidationResult(Optional.of(dto), semanticProblems);
    }
}
```

Use Jackson's concrete parse exception location to create the syntax suggestion. Do not include raw source content, local paths, or stack traces.

- [ ] **Step 3: Replace the hand-written dry-run method in `CampaignService`**

Inject `CampaignImportValidator`, remove the existing `List<String> validateImport` logic, and expose:

```java
@Transactional(readOnly = true)
public CampaignValidationResult validateImport(String json) {
    return importValidator.validate(json);
}
```

Do not retain “Validation passed” as a list entry.

- [ ] **Step 4: Gate `importFromJson` with the same result**

Refactor the public method start to:

```java
public Campaign importFromJson(String json) {
    CampaignExportDto dto = importValidator.validate(json).requireImportable();
    return importValidated(dto);
}

private Campaign importValidated(CampaignExportDto dto) {
    // Existing persistence order begins here; no JSON parsing or validation lives below this line.
}
```

Because `CampaignService` is already transactional, validation and persistence occur inside one transaction. The first call to `create(...)` must remain below `requireImportable()`.

- [ ] **Step 5: Return structured dry-run metadata**

Replace the controller's string-list result with:

```java
public record DryRunResult(
        boolean valid,
        String status,
        List<CampaignImportProblem> problems
) {
    static DryRunResult from(CampaignValidationResult result) {
        return new DryRunResult(
                result.valid(),
                result.valid() ? "READY" : "BLOCKED",
                result.problems());
    }
}
```

The dry-run branch calls `service.validateImport(json)` once and returns `ResponseEntity.ok(DryRunResult.from(result))`.

- [ ] **Step 6: Add controller and service rejection tests**

Assert this exact dry-run JSON shape:

```json
{
  "valid": false,
  "status": "BLOCKED",
  "problems": [{
    "severity": "ERROR",
    "code": "UNRESOLVED_REFERENCE",
    "path": "/encounters/0/map",
    "message": "Map 'Missing' does not exist in this campaign document.",
    "suggestion": "Use a unique map name declared in /maps."
  }]
}
```

In `CampaignServiceTest`, record `repository.count()` before invalid import and assert it is unchanged afterward. Also verify the semantic validator is invoked by both `validateImport` and `importFromJson`.

Update the three campaign service test configurations to import `CampaignSchemaValidator`, `CampaignSemanticValidator`, and `CampaignImportValidator`. Mock only the global catalog repositories in the narrow service tests; use real repositories or explicit seed rows in the round-trip integration test. This keeps the new constructor dependency visible instead of bypassing validation in tests.

- [ ] **Step 7: Run pipeline/service/controller tests**

Run:

```bash
./mvnw -Dtest=CampaignImportValidatorTest,CampaignServiceTest,CampaignControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: all selected tests pass; invalid direct import returns HTTP 400 and invalid dry-run returns HTTP 200 with `BLOCKED` metadata.

- [ ] **Step 8: Commit the shared validation path**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignImportValidator.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign
git commit -m "fix: share campaign dry-run and import validation"
```

### Task 6: Remove silent loss from v1 handouts and reference persistence

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`

**Interfaces:**
- Consumes: validated handout data URLs and semantically resolved v1 references.
- Produces: `HandoutService.createImported(UUID campaignId, String title, String tags, String originalFileName, String contentType, byte[] bytes)` and fail-fast persistence after validation.

- [ ] **Step 1: Write failing storage-isolation tests**

Add tests proving that `originalFileName` values `../../outside.png`, `/tmp/absolute.png`, and `duplicate.png` never become stored paths. Two imports with the same original name must produce distinct storage names matching:

```text
^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(png|jpg|gif|webp)$
```

Assert the files exist beneath the isolated `${user.home}/.dmhelper/files` directory and no file exists outside it.

- [ ] **Step 2: Add validated byte-array handout creation**

Implement:

```java
public Handout createImported(UUID campaignId,
                              String title,
                              String tags,
                              String originalFileName,
                              String contentType,
                              byte[] bytes) throws IOException {
    Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
    String extension = extensionFor(contentType);
    String storageName = UUID.randomUUID() + extension;

    Handout handout = new Handout();
    handout.setCampaign(campaign);
    handout.setTitle(title.trim());
    handout.setTags(tags == null ? "" : tags.trim());
    handout.setContentType(contentType);
    handout.setFileName(storageName);

    Files.createDirectories(filesDir);
    Files.write(filesDir.resolve(storageName), bytes, StandardOpenOption.CREATE_NEW);
    return handoutRepository.save(handout);
}

private static String extensionFor(String contentType) {
    return switch (contentType) {
        case "image/png" -> ".png";
        case "image/jpeg" -> ".jpg";
        case "image/gif" -> ".gif";
        case "image/webp" -> ".webp";
        default -> throw new IllegalArgumentException("Unsupported handout content type: " + contentType);
    };
}
```

The supplied name is never resolved against a directory. Keep it only as method-level metadata for future provenance support.

- [ ] **Step 3: Make export fail when an advertised handout asset is unavailable**

Replace the exporter catch-and-null behavior with a thrown exception whose public message names the handout title but not the local file path:

```java
catch (IOException e) {
    throw new IllegalStateException(
            "Cannot export campaign because handout '" + h.getTitle() + "' has no readable asset.", e);
}
```

Add a test asserting export fails and does not return schema-invalid JSON with a null `imageData`.

- [ ] **Step 4: Import handouts only through `HandoutService`**

Decode the already schema-validated data URL, verify its prefix agrees with `contentType`, and call `createImported`. Remove direct `Path.resolve(hDto.fileName())`, direct `Files.write`, hand-built `Handout`, and all catch-and-continue branches from `CampaignService`.

- [ ] **Step 5: Replace post-validation warning branches with invariant failures**

For tokens, combatants, scenes, quick notes, assignments, ledger entries, timeline events, and sheets:

- resolve through the maps built during import;
- use `orElseThrow(() -> new IllegalStateException("Validated reference disappeared: ..."))` if a target unexpectedly vanishes;
- remove every `System.err.println("WARNING: ...")` in the import path;
- never save a quick note without `targetId`;
- never save a reference-bearing row with an unresolved target.

Before exporting quick notes, add `CAMPAIGN` for the campaign ID and `Adventure name/Chapter title/sceneKey` for every scene ID to the target-reference mapping. During import, build the same scene-path-to-new-ID index and resolve `SCENE` quick notes from it. Set `QuickNote.createdAt` from the validated ISO-8601 `createdAt` value so the v1 field round-trips rather than being replaced by import time.

These exceptions indicate a validation/persistence race or code defect, not user-repairable input, and therefore roll back the transaction.

- [ ] **Step 6: Add an atomic database rollback assertion**

Force `HandoutService.createImported` to throw `IOException` after campaign creation. Assert the transaction leaves zero campaigns, zero handouts, and zero imported child rows. Also assert the failing storage method does not leave a final asset file.

- [ ] **Step 7: Run handout and round-trip tests**

Run:

```bash
./mvnw -Dtest=HandoutServiceTest,CampaignImportExportRoundTripTest,CampaignServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: all selected tests pass; traversal/collision cases remain contained; missing export assets and injected import I/O failures are visible and rollback database state.

- [ ] **Step 8: Commit fail-fast v1 persistence**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
git commit -m "fix: prevent silent loss in campaign v1 import"
```

### Task 7: Prove the v1 contract end to end and publish its implemented scope

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Create: `docs/campaign-format-v1.md`
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`

**Interfaces:**
- Consumes: all contracts from Tasks 1–6 and the feature-complete fixture.
- Produces: an executable v1 authoring fixture, honest v1 documentation, and a recorded delivery checkpoint.

- [ ] **Step 1: Add the flagship fixture pipeline test**

Add one integration test that executes the exact sequence:

```java
String source = resource("campaigns/v1/feature-complete.dmcampaign.json");

CampaignValidationResult firstDryRun = campaignService.validateImport(source);
assertThat(firstDryRun.valid()).isTrue();
assertThat(firstDryRun.problems()).isEmpty();

Campaign firstImport = campaignService.importFromJson(source);
String exported = campaignService.exportToJson(firstImport.getId());
assertThat(schemaValidator.validate(exported)).isEmpty();

CampaignValidationResult secondDryRun = campaignService.validateImport(exported);
assertThat(secondDryRun.valid()).isTrue();

Campaign secondImport = campaignService.importFromJson(exported);
assertCurrentV1SemanticsEqual(firstImport.getId(), secondImport.getId());
```

`assertCurrentV1SemanticsEqual` compares every field represented by `CampaignExportDto`, normalizing only regenerated database IDs and regenerated handout storage names. It must compare reference meaning, not raw UUIDs.

In this integration test, import real `CampaignSchemaValidator`, `CampaignSemanticValidator`, `CampaignImportValidator`, and `HandoutService` beans. Remove the existing mocked `HandoutService`, create explicit catalog rows for every referenced species/background/class/feat/spell/item key, and keep all file assertions under the isolated test home.

- [ ] **Step 2: Add schema-invalid/import-rejected parity tests**

For the unknown-property fixture and one unresolved-reference variant, assert:

- dry-run is blocked with the exact problem;
- direct import throws before persistence;
- repository counts and the asset directory are unchanged;
- a schema-valid feature fixture is accepted by both paths.

- [ ] **Step 3: Label the UI as v1 JSON**

Update the detail-page controls to “Export v1 JSON” and “Import v1 JSON”. Change the input to:

```html
<input type="file" name="file" accept=".dmcampaign.json,application/json"
       onchange="this.form.requestSubmit()" class="hidden">
```

Do not add the package-v2 preview UI in this checkpoint.

- [ ] **Step 4: Write the implemented v1 contract guide**

`docs/campaign-format-v1.md` must contain:

1. schema URLs and checked-in paths;
2. format/container statement (`formatVersion: 1`, UTF-8 JSON, `.dmcampaign.json`);
3. exact validation order and dry-run response example;
4. units table for maps, tokens, pins, shapes, dates, and currency;
5. v1 name/key reference table and ambiguity behavior;
6. embedded-handout MIME/data-URL rules and generated storage-name behavior;
7. commands to validate through the running app and the Maven contract tests;
8. a link to `src/test/resources/campaigns/v1/feature-complete.dmcampaign.json` as the executable example;
9. an explicit “Not represented by v1” list: campaign settings/current scene, party current HP, handout visibility/presentation state, encounter combat log/dice history, calendar configuration/current date, complete custom compendium types, and structured transitions/quests;
10. a statement that those omissions are version-1 limitations, not evidence that export is a complete backup.

- [ ] **Step 5: Record the checkpoint in the master spec**

Under Workstream B, add only:

```markdown
> **Implementation status:** The campaign-contract-v1 checkpoint made the published v1 campaign
> and map schemas executable, closed them against unknown fields, unified dry-run and import
> validation, blocked unresolved v1 references before persistence, and added checked-in contract
> fixtures. ZIP packaging, stable version-2 keys, preview confirmation, migrations, and complete
> persistent-state round-trip remain open in delivery items 3 and 4.
```

- [ ] **Step 6: Run the focused contract suite**

Run:

```bash
./mvnw -Dtest=CampaignSchemaValidatorTest,CampaignDtoSchemaCompatibilityTest,CampaignSemanticValidatorTest,CampaignImportValidatorTest,SchemaControllerTest,CampaignServiceTest,CampaignControllerTest,CampaignImportExportRoundTripTest,GameMapServiceTest,HandoutServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: all selected tests pass with zero failures and zero errors.

- [ ] **Step 7: Run the complete suite**

Run:

```bash
./mvnw test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

Expected: `BUILD SUCCESS`, zero failures, and zero errors.

- [ ] **Step 8: Verify contract invariants directly**

Run:

```bash
rg -n 'System\.err\.println\("WARNING:|Validation passed|filesDir\.resolve\(hDto\.fileName|currentScene' src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/main/resources/schemas/campaign-format.schema.json
```

Expected: no matches.

- [ ] **Step 9: Check the final diff**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` exits 0; status lists only the intended v1 contract documentation changes after Tasks 1–6 have been committed.

- [ ] **Step 10: Commit the verified checkpoint record**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java src/main/resources/templates/campaigns/detail.html docs/campaign-format-v1.md docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md
git commit -m "docs: publish campaign v1 contract checkpoint"
```

## Completion Gate

This plan is complete only when:

- both checked-in schemas validate against the draft-2020-12 meta-schema offline;
- `campaign-format.schema.json` resolves `map-document.schema.json` without network access;
- every closed v1 object rejects unknown properties;
- the minimal and feature-complete fixtures validate and deserialize into `CampaignExportDto`;
- a serialized feature-complete DTO validates against the published schema;
- every schema-valid fixture reaches semantic validation without DTO drift;
- malformed JSON, schema violations, duplicate/ambiguous names, unresolved references, state errors, and spatial errors return stable codes and JSON Pointer paths;
- dry-run and real import call the same validator and agree on accept/reject behavior;
- validation errors occur before the first database or asset write;
- imported handout filenames cannot escape or collide in storage;
- missing export assets and unexpected post-validation reference failures are visible and never silently discarded;
- feature fixture → dry-run → import → export → schema validation → dry-run → second import preserves every field represented by the v1 DTO;
- the v1 guide explicitly lists persistent state not represented by v1;
- the focused suite and the complete Maven suite report zero failures and zero errors;
- the master spec records only the v1 repair, leaving package v2 and complete round-trip work open.

## Next Plan Boundary

After this completion gate, write the separate **Package v2 Foundation** design/plan from delivery item 3. That next slice owns ZIP container safety, stable package-local keys, v1-to-v2 migration, typed catalog identifiers, warning confirmation, import preview, and staged asset installation. Do not pull those contracts into this v1 repair branch.
