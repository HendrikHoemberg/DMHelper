# Documentation and Agent SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver delivery item 10 of the all-in-one DM readiness specification: publish audience-split documentation, a machine-readable capability manifest, a complete validation-error catalog, catalog/schema agent APIs that work offline from checked-in artifacts, executable documentation examples, and an agent conversion playbook so a source-conversion agent can author packages that validate, dry-run, import, and run correctly in DMHelper.

**Architecture:** Treat contracts as the single source of truth. Introduce a checked-in agent SDK under `src/main/resources/agent/` (capability manifest + validation-error catalog) served by thin REST controllers, kept in sync by tests. Expand human docs under `docs/` by audience (product, DM manual, authoring, architecture, agent) without a frontend build chain. Make documentation examples executable test fixtures: if an example stops validating or a documented fixture path disappears, the build fails. Do not invent new game features; document and contract-test what items 1–9 already implemented.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Jackson 3, JSON Schema draft 2020-12 (existing schemas), classpath resources, Maven Wrapper, JUnit 5 / AssertJ / MockMvc, existing package-v2 validators and flagship fixtures. Markdown only (no static-site generator).

## Global Constraints

- Master design §18 (Agent conversion playbook), §19 (Documentation deliverables), §21.1 (Contract tests), and delivery item 10 are authoritative. Do **not** start P3 expansion (item 11: world graph, travel, tables, clocks, fog, audio, interactive players).
- Offline/local-first: no CDN, no network generative services, no new frontend build chain. Schemas, catalog snapshot, capability manifest, and error catalog must work from the repository and from the running app without internet.
- Documentation examples are executable contracts. An example that fails schema/semantic validation or a dead fixture path **fails the build**.
- Do not claim all-in-one readiness until §23 conditions are met; item 10 closes the agent/docs gap and must keep the capability matrix honest about remaining `PARTIAL` / `UNSUPPORTED` items.
- Agent-facing read APIs (`/api/v1/schemas/**`, `/api/v1/catalog/**`, `/api/v1/capabilities`, `/api/v1/validation-errors`) must be usable without a DM PIN (same policy as schemas today). They expose no campaign content, only public contracts.
- Preserve existing package format version `2`. Do not introduce format version 3 in this plan.
- Prefer extending existing `docs/campaign-format-v2.md`, `docs/campaign-capabilities.md`, `SchemaController`, and `CatalogController` over parallel documentation systems.
- No copyrighted campaign content in fixtures or examples. Synthetic keys only.
- Tests use an isolated home directory: `-DargLine=-Duser.home=/tmp/dmhelper-docs-agent-sdk`.
- Complete every task with focused tests before moving on. Prefer TDD: failing test → implement → pass → commit.

---

## Audit result: what is already implemented

Verified against the master design status table (spec §22), `docs/campaign-capabilities.md`, git history, migrations V1–V11, and repository evidence as of 2026-07-17:

| # | Delivery item | Spec claim | Audit status | Key evidence |
|---|---|---|---|---|
| 1 | P0 runtime reliability | `IMPLEMENTED` | **Correctly implemented** | Quick notes without unresolved Thymeleaf placeholders; `ContentDestinationRegistry` + route contract tests; `dm-request.js` / correlated errors; package asset safety; difficulty labeled estimate |
| 2 | Campaign contract v1 repair | `IMPLEMENTED` | **Correctly implemented** | Closed `campaign-format.schema.json` / map schema; unified dry-run/import; v1 fixtures under `src/test/resources/campaigns/v1/` |
| 3 | Package v2 foundation | `IMPLEMENTED` | **Correctly implemented** | ZIP/JSON containers, package keys (V3), validation pipeline, preview store, staged assets, migration registry |
| 4 | Complete round-trip | `IMPLEMENTED` | **Correctly implemented** | Section adapters, coordinators, semantic snapshot/compare, flagship fixtures |
| 5 | Session cockpit | `IMPLEMENTED` | **Correctly implemented** | Real `/campaigns/{id}/session` page, story/encounter/plan rails, lifecycle, draft session log, resume order (V4) |
| 6 | Structured adventure/quest | `IMPLEMENTED` | **Correctly implemented** | Scene sections/checks/participants/transitions/links; quests/objectives/dependencies; source annotations (V5–V6) |
| 7 | Custom compendium expansion | `IMPLEMENTED` | **Correctly implemented** | Ownership + provenance for library types; package custom arrays (V7) |
| 8 | Character-sheet completion | `IMPLEMENTED` | **Correctly implemented** | Live party state, attacks/features, inventory states, rest preview, batch ops, package sheet fields (V8, V11 XP) |
| 9 | Encounter and map depth | `IMPLEMENTED` | **Correctly implemented** | Waves/spawn, prep/rewards, placement, HAZARD kind, undo boundaries; map `playerVisible`, calibration, region keys (V9–V10); capability matrix rows `SUPPORTED` |
| **10** | **Documentation/agent SDK** | **`PLANNED`** | **Accurate — foundations only** | See gap table below |
| 11 | P3 expansion | `PLANNED` | Out of scope | World graph, travel, fog gameplay, audio, interactive players |

### Foundations already present (reuse; do not rewrite)

| Area | What exists | Primary files |
|---|---|---|
| Schemas | v1 + v2 campaign and map schemas on classpath; served at `/api/v1/schemas/{name}` (PIN-exempt) | `src/main/resources/schemas/*`, `SchemaController` |
| Typed catalog | Live `CatalogSnapshot` from SRD services; checked-in `catalog/srd-5.2-catalog.json`; `/api/v1/catalog` + `/snapshot` | `CampaignCatalogService`, `CatalogController` |
| Authoring docs | Partial format guides and thin capability matrix | `docs/campaign-format-v1.md`, `docs/campaign-format-v2.md`, `docs/campaign-capabilities.md` |
| Fixtures | Minimal, feature-complete, published-adventure-shaped, structured-adventure-quest, current-surface | `src/test/resources/campaigns/v2/` |
| Validators | Shared problem shape `{severity, code, path, message, suggestion}` | `CampaignImportProblem`, pipeline + semantic validators |
| Destinations | Route registry with contract tests | `ContentDestinationRegistry`, `ContentDestinationRouteContractTest` |
| Vision | Large aspirational product spec | `SPEC.md` |

### Hard gaps this plan closes (Workstreams §18–§19, §21.1)

| Gap | Spec § | Severity |
|---|---|---|
| No machine-readable capability manifest for agents | §19.5, §21.1 | High |
| Capability matrix is a short Markdown table only; not served by API | §19.1 | High |
| Validation error codes are free strings with no catalog or agent-facing index | §19.3, §7.4 | High |
| No agent conversion playbook / mapping rules / verification checklist as product docs | §18, §19.5 | High |
| No DM manual (session cockpit, import preview, offline troubleshooting) | §19.2 | High |
| No architecture reference (module boundaries, I/E flow, security, testing) | §19.4 | Medium |
| `SPEC.md` mixes aspirational and implemented behavior without a clear status split | §19.1, §2 | Medium |
| Doc examples are not executable fixtures; no build failure when examples drift | §19 last paragraph, §21.1 | High |
| Checked-in catalog snapshot is not CI-asserted equal to live snapshot hash | §7.9, §21.1 | High |
| `/api/v1/catalog/**` still requires DM PIN (schemas do not) | §18 step 4, offline agent use | High |
| `campaign-format-v2.md` missing encounter waves/prep/rewards and map calibration/region docs from item 9 | §12–§13, §19.3 | Medium |
| No release notes / format compatibility matrix / known-limitations page | §19.1 | Medium |
| No dry-run repair examples for agents | §19.5 | Medium |
| Product/docs index is missing (`docs/README.md`) | §19 | Low |

---

## Delivery item 10 acceptance contract

- [ ] `src/main/resources/agent/capability-manifest.json` exists, is versioned, and lists every readiness capability with status in `{SUPPORTED, PARTIAL, UNSUPPORTED, EXPERIMENTAL}`.
- [ ] `GET /api/v1/capabilities` returns that manifest (JSON) without a DM PIN.
- [ ] `GET /api/v1/catalog` and `GET /api/v1/catalog/snapshot` work without a DM PIN; snapshot `ETag` equals the live `sha256`.
- [ ] Checked-in `catalog/srd-5.2-catalog.json` fails the build if it diverges from `CampaignCatalogService.snapshot()`.
- [ ] Every validation problem code emitted by package/v1 validators is registered in a typed catalog with severity, meaning, and repair guidance; catalog is served at `GET /api/v1/validation-errors` (PIN-free) and documented in Markdown.
- [ ] Documentation is split by audience under `docs/` with an index: product status, DM manual, authoring reference, architecture reference, agent guide.
- [ ] Authoring reference covers container rules, keys, references, map units, assets, provenance, error catalog, migrations, and points at the three flagship fixtures plus a minimal example.
- [ ] Agent guide includes the §18 playbook, non-invention policy, catalog usage, dry-run repair examples, and a deterministic verification checklist.
- [ ] At least three checked-in documentation examples (minimal valid, intentional schema error, intentional semantic error) validate or fail as documented; tests enforce that.
- [ ] `docs/campaign-capabilities.md` stays consistent with the machine-readable manifest (contract test).
- [ ] `SPEC.md` gains a short status banner distinguishing vision vs implemented readiness program; it does not silently claim unfinished P3 work as shipped.
- [ ] Master design §22 item 10 status becomes `IMPLEMENTED` only after gates pass; capability matrix remains honest about residual `PARTIAL`/`UNSUPPORTED` rows.
- [ ] Existing package, catalog, destination, round-trip, and smoke tests remain green.

---

## Domain contracts (authoritative)

### Capability manifest (`agent/capability-manifest.json`)

```json
{
  "manifestVersion": 1,
  "catalogVersion": "srd-5.2-dmhelper-1",
  "packageFormatVersions": [1, 2],
  "rulesets": ["SRD_5_2"],
  "endpoints": {
    "schemas": "/api/v1/schemas/{name}",
    "catalog": "/api/v1/catalog",
    "catalogSnapshot": "/api/v1/catalog/snapshot",
    "capabilities": "/api/v1/capabilities",
    "validationErrors": "/api/v1/validation-errors",
    "packagePreview": "/campaigns/package-imports/previews",
    "packageConfirm": "/campaigns/package-imports/{previewId}/confirm",
    "packageExport": "/campaigns/{campaignId}/package"
  },
  "capabilities": [
    {
      "id": "package.v2.round_trip",
      "name": "Campaign package v2 complete round-trip",
      "status": "SUPPORTED",
      "deliveryItem": 4,
      "notes": "Default export includes combat log and dice history; both are explicitly excludable."
    }
  ],
  "contentTypes": [
    "ADVENTURE", "CHAPTER", "SCENE", "TRANSITION", "QUEST", "OBJECTIVE",
    "MAP", "TOKEN", "ENCOUNTER", "ENCOUNTER_WAVE", "COMBATANT",
    "PARTY_MEMBER", "CHARACTER_SHEET", "NOTE", "QUICK_NOTE", "HANDOUT",
    "ASSIGNMENT", "LEDGER_ENTRY", "TIMELINE_EVENT", "SESSION",
    "STATBLOCK", "SPELL", "CONDITION", "RULE", "EQUIPMENT_ITEM", "MAGIC_ITEM",
    "CLASS", "SPECIES", "BACKGROUND", "FEAT", "SOURCE_ANNOTATION"
  ],
  "flagshipFixtures": [
    "classpath:campaigns/v2/minimal.dmcampaign.json",
    "classpath:campaigns/v2/feature-complete.dmcampaign/",
    "classpath:campaigns/v2/published-adventure-shaped.dmcampaign/"
  ]
}
```

Status enum (string, closed):

```text
SUPPORTED | PARTIAL | UNSUPPORTED | EXPERIMENTAL
```

### Validation error catalog entry

```json
{
  "code": "UNRESOLVED_REFERENCE",
  "defaultSeverity": "ERROR",
  "summary": "A typed reference points at a missing package or catalog key.",
  "when": "Semantic validation after schema validation.",
  "suggestionTemplate": "Create the missing entity or correct the key. Nearby keys may be listed in the problem message.",
  "repairHints": [
    "Prefer package keys over display names.",
    "For catalog refs, use GET /api/v1/catalog and match type+ruleset+sourceKey exactly."
  ],
  "exampleProblem": {
    "severity": "ERROR",
    "code": "UNRESOLVED_REFERENCE",
    "path": "/encounters/0/combatants/0/statBlockRef",
    "message": "Catalog STATBLOCK 'goblin-chief' does not exist.",
    "suggestion": "Use sourceKey 'goblin' from the SRD catalog or define a custom statblock."
  }
}
```

### Agent-facing problem shape (unchanged wire format)

```java
public record CampaignImportProblem(
        ImportSeverity severity, // ERROR | WARNING | INFO
        String code,
        String path,
        String message,
        String suggestion
) {}
```

---

## File structure (create / modify)

| Path | Responsibility |
|---|---|
| `src/main/resources/agent/capability-manifest.json` | Machine-readable capability + endpoint + fixture index |
| `src/main/resources/agent/validation-error-catalog.json` | Machine-readable error catalog |
| `src/main/java/.../agent/CapabilityManifest.java` | Typed records for the capability manifest |
| `src/main/java/.../agent/ValidationErrorCatalog.java` | Typed records for error catalog |
| `src/main/java/.../agent/AgentContractService.java` | Loads classpath JSON, exposes snapshots |
| `src/main/java/.../agent/web/AgentContractController.java` | `GET /api/v1/capabilities`, `GET /api/v1/validation-errors` |
| `src/main/java/.../campaign/service/validation/ImportProblemCodes.java` | Canonical code constants used by validators |
| `src/main/java/.../common/config/WebMvcConfig.java` | PIN-exempt agent contract routes |
| `src/test/java/.../agent/*` | Manifest, catalog fidelity, PIN-free, doc-example tests |
| `src/test/resources/docs-examples/` | Minimal valid + intentional invalid package examples |
| `docs/README.md` | Documentation index by audience |
| `docs/product/*` | Vision pointer, release notes, format compatibility, known limitations |
| `docs/dm-manual/*` | DM-facing how-to |
| `docs/authoring/*` | Format, errors, examples (format-v2 expanded or linked) |
| `docs/architecture/*` | Module/I-E/session/security/testing |
| `docs/agent/*` | Playbook, mapping rules, checklist, dry-run repair |
| `docs/campaign-capabilities.md` | Keep path; align with manifest (human matrix) |
| `docs/campaign-format-v2.md` | Expand item-9 fields; link error catalog |
| `SPEC.md` | Status banner only (no full rewrite) |
| `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` | Item 10 → `IMPLEMENTED` when done |

Package placement: new code lives under `dev.hendrikhoemberg.dmhelper.agent` (small, focused module). Validators keep using string codes via `ImportProblemCodes` constants to avoid a risky enum-refactor of every call site in one PR; the catalog still lists every constant.

---

### Task 1: Canonical import problem codes + coverage test

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportProblemCodes.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportProblemCodesCoverageTest.java`
- Modify (incremental, file-by-file as needed): validators that currently use string literals for `code` — start with `CampaignManifestV2SemanticValidator`, `CampaignPackageValidationPipeline`, `AssetSignatureValidator`, `CampaignPackageReader`, `CampaignImportValidator`, `CampaignSemanticValidator`, `CampaignSchemaValidator` / `CampaignManifestV2SchemaValidator`, `FormatMigrationRegistry`, `LegacyV1ToV2Migration`, `CampaignExportCoordinator` (if it emits codes)

**Interfaces:**
- Produces: `ImportProblemCodes` public `static final String` constants + `public static Set<String> all()`
- Consumes: none

- [ ] **Step 1: Write the failing coverage test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImportProblemCodesCoverageTest {

    private static final Pattern CODE_LITERAL = Pattern.compile(
            "new CampaignImportProblem\\([^;]*?\"([A-Z][A-Z0-9_]+)\"");
    private static final Pattern ERROR_HELPER = Pattern.compile(
            "\\berror\\([^;]*?\"([A-Z][A-Z0-9_]+)\"");
    private static final Pattern WARNING_HELPER = Pattern.compile(
            "\\bwarning\\([^;]*?\"([A-Z][A-Z0-9_]+)\"");
    private static final Pattern PROBLEM_HELPER = Pattern.compile(
            "problem\\(\\\"([A-Z][A-Z0-9_]+)\\\"");

    @Test
    void everyEmittedCodeIsRegistered() throws Exception {
        Set<String> emitted = new HashSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(path);
                for (Pattern p : new Pattern[]{CODE_LITERAL, ERROR_HELPER, WARNING_HELPER, PROBLEM_HELPER}) {
                    Matcher m = p.matcher(src);
                    while (m.find()) {
                        String code = m.group(1);
                        if (code.startsWith("SCHEMA_")) {
                            // schema validator prefixes JSON-schema keyword codes
                            emitted.add("SCHEMA_VIOLATION");
                        } else {
                            emitted.add(code);
                        }
                    }
                }
            }
        }
        assertThat(emitted)
                .as("Validators still emit unregistered codes: update ImportProblemCodes")
                .isSubsetOf(ImportProblemCodes.all());
        assertThat(ImportProblemCodes.all()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-docs-agent-sdk \
  -Dtest=ImportProblemCodesCoverageTest test
```

Expected: FAIL — `ImportProblemCodes` missing or empty / compilation failure.

- [ ] **Step 3: Implement `ImportProblemCodes` with every code currently emitted**

Harvest codes from the audit (non-exhaustive starter set — regenerate from the test’s failure list until green):

```java
package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ImportProblemCodes {
    private ImportProblemCodes() {}

    public static final String AMBIGUOUS_REFERENCE = "AMBIGUOUS_REFERENCE";
    public static final String ASSET_DIGEST_MISMATCH = "ASSET_DIGEST_MISMATCH";
    public static final String ASSET_EXTENSION_MISMATCH = "ASSET_EXTENSION_MISMATCH";
    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    public static final String ASSET_PATH_INVALID = "ASSET_PATH_INVALID";
    public static final String ASSET_READ_ERROR = "ASSET_READ_ERROR";
    public static final String ASSET_SIGNATURE_MISMATCH = "ASSET_SIGNATURE_MISMATCH";
    public static final String ASSET_SIZE_MISMATCH = "ASSET_SIZE_MISMATCH";
    public static final String CATALOG_SNAPSHOT_MISMATCH = "CATALOG_SNAPSHOT_MISMATCH";
    public static final String COMPRESSION_RATIO_EXCEEDED = "COMPRESSION_RATIO_EXCEEDED";
    public static final String DTO_SCHEMA_DRIFT = "DTO_SCHEMA_DRIFT";
    public static final String DUPLICATE_DEPENDENCY = "DUPLICATE_DEPENDENCY";
    public static final String DUPLICATE_KEY = "DUPLICATE_KEY";
    public static final String DUPLICATE_MANIFEST = "DUPLICATE_MANIFEST";
    public static final String DUPLICATE_NORMALIZED_PATH = "DUPLICATE_NORMALIZED_PATH";
    public static final String DUPLICATE_REFERENCE = "DUPLICATE_REFERENCE";
    public static final String DUPLICATE_REGION_KEY = "DUPLICATE_REGION_KEY";
    public static final String DUPLICATE_SESSION_ATTENDEE = "DUPLICATE_SESSION_ATTENDEE";
    public static final String DUPLICATE_SESSION_SCENE_VISIT = "DUPLICATE_SESSION_SCENE_VISIT";
    public static final String ENCRYPTED_ENTRY = "ENCRYPTED_ENTRY";
    public static final String GRID_MISMATCH = "GRID_MISMATCH";
    public static final String INVALID_ACTIVE_TURN = "INVALID_ACTIVE_TURN";
    public static final String INVALID_ASSIGNMENT_SOURCE = "INVALID_ASSIGNMENT_SOURCE";
    public static final String INVALID_COMBATANT_KIND = "INVALID_COMBATANT_KIND";
    public static final String INVALID_COMPLETION_MODE = "INVALID_COMPLETION_MODE";
    public static final String INVALID_CURRENT_SCENE_REF = "INVALID_CURRENT_SCENE_REF";
    public static final String INVALID_GEOMETRY = "INVALID_GEOMETRY";
    public static final String INVALID_GIVER = "INVALID_GIVER";
    public static final String INVALID_JSON = "INVALID_JSON";
    public static final String INVALID_PARTY_CURRENT_HP = "INVALID_PARTY_CURRENT_HP";
    public static final String INVALID_REFERENCE_TYPE = "INVALID_REFERENCE_TYPE";
    public static final String INVALID_RESOURCE_STATE = "INVALID_RESOURCE_STATE";
    public static final String INVALID_SESSION_PRESENTATION = "INVALID_SESSION_PRESENTATION";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String INVALID_TOKEN_KIND = "INVALID_TOKEN_KIND";
    public static final String INVALID_TRANSITION_TARGET = "INVALID_TRANSITION_TARGET";
    public static final String MANIFEST_MISSING = "MANIFEST_MISSING";
    public static final String MISSING_SESSION_DRAFT = "MISSING_SESSION_DRAFT";
    public static final String MISSING_SOURCE_ANNOTATION = "MISSING_SOURCE_ANNOTATION";
    public static final String NON_CAMPAIGN_CUSTOM_DEPENDENCY = "NON_CAMPAIGN_CUSTOM_DEPENDENCY";
    public static final String OUT_OF_BOUNDS = "OUT_OF_BOUNDS";
    public static final String PACKAGE_EXPANDED_TOO_LARGE = "PACKAGE_EXPANDED_TOO_LARGE";
    public static final String PACKAGE_READ_ERROR = "PACKAGE_READ_ERROR";
    public static final String PATH_TOO_LONG = "PATH_TOO_LONG";
    public static final String SCHEMA_VIOLATION = "SCHEMA_VIOLATION";
    public static final String SYMLINK_ENTRY = "SYMLINK_ENTRY";
    public static final String TOKEN_OUT_OF_BOUNDS = "TOKEN_OUT_OF_BOUNDS";
    public static final String TOO_MANY_ENTRIES = "TOO_MANY_ENTRIES";
    public static final String TRAVERSAL_ASSET_PATH = "TRAVERSAL_ASSET_PATH";
    public static final String UNEXPECTED_ROOT_ENTRY = "UNEXPECTED_ROOT_ENTRY";
    public static final String UNRESOLVED_ASSET_REFERENCE = "UNRESOLVED_ASSET_REFERENCE";
    public static final String UNRESOLVED_CATALOG_REFERENCE = "UNRESOLVED_CATALOG_REFERENCE";
    public static final String UNRESOLVED_PLACEMENT_REGION = "UNRESOLVED_PLACEMENT_REGION";
    public static final String UNRESOLVED_REFERENCE = "UNRESOLVED_REFERENCE";
    public static final String UNRESOLVED_WAVE_REFERENCE = "UNRESOLVED_WAVE_REFERENCE";
    public static final String UNSUPPORTED_FORMAT_VERSION = "UNSUPPORTED_FORMAT_VERSION";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    private static final Set<String> ALL;

    static {
        Set<String> codes = new LinkedHashSet<>();
        for (var field : ImportProblemCodes.class.getFields()) {
            if (field.getType() == String.class) {
                try {
                    codes.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
        }
        ALL = Collections.unmodifiableSet(codes);
    }

    public static Set<String> all() {
        return ALL;
    }
}
```

If the coverage test still finds codes, **add them** rather than weakening the regex. Optionally replace string literals in validators with `ImportProblemCodes.X` in the same task when touching a file; do not require a full-repo string-literal purge if the coverage test already passes via the registry including every emitted literal.

- [ ] **Step 4: Re-run coverage test**

Run the same Maven command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportProblemCodes.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportProblemCodesCoverageTest.java
git commit -m "$(cat <<'EOF'
feat: register canonical campaign import problem codes

Add ImportProblemCodes and a coverage test so every emitted validation
code is known to the upcoming agent error catalog.
EOF
)"
```

---

### Task 2: Validation error catalog resource + loader + API

**Files:**
- Create: `src/main/resources/agent/validation-error-catalog.json`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/agent/ValidationErrorCatalog.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/agent/AgentContractService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/agent/web/AgentContractController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/ValidationErrorCatalogTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/web/AgentContractControllerTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java`

**Interfaces:**
- Consumes: `ImportProblemCodes.all()`
- Produces: `GET /api/v1/validation-errors` → JSON catalog; `AgentContractService.validationErrors()`

- [ ] **Step 1: Write failing tests**

```java
@SpringBootTest
class ValidationErrorCatalogTest {
    @Autowired AgentContractService service;

    @Test
    void catalogCoversEveryImportProblemCode() {
        var codes = service.validationErrors().errors().stream()
                .map(ValidationErrorCatalog.Entry::code)
                .collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrderElementsOf(ImportProblemCodes.all());
    }

    @Test
    void everyEntryHasSummaryAndDefaultSeverity() {
        assertThat(service.validationErrors().errors()).allSatisfy(e -> {
            assertThat(e.code()).isNotBlank();
            assertThat(e.defaultSeverity()).isIn("ERROR", "WARNING", "INFO");
            assertThat(e.summary()).isNotBlank();
        });
    }
}
```

```java
@SpringBootTest
@AutoConfigureMockMvc
class AgentContractControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void validationErrorsArePinFree() throws Exception {
        mvc.perform(get("/api/v1/validation-errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogVersion").exists())
                .andExpect(jsonPath("$.errors").isArray());
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL** (missing class/resource/route).

- [ ] **Step 3: Implement catalog JSON**

Write `validation-error-catalog.json` with `catalogVersion: "1"` and one object per `ImportProblemCodes` constant. Use realistic summaries; for each code, copy message/suggestion language from the validator that emits it when practical. Minimum fields per entry:

```json
{
  "catalogVersion": "1",
  "errors": [
    {
      "code": "UNRESOLVED_REFERENCE",
      "defaultSeverity": "ERROR",
      "summary": "A typed reference points at a missing package or catalog key.",
      "when": "Semantic validation after schema validation.",
      "suggestionTemplate": "Create the missing entity or correct the key.",
      "repairHints": [
        "Use package keys, never display names, for version-2 references.",
        "Resolve catalog keys via GET /api/v1/catalog."
      ]
    }
  ]
}
```

Generate the skeleton programmatically if helpful (small main or unit helper), then hand-edit summaries for high-traffic codes: `SCHEMA_VIOLATION`, `UNRESOLVED_REFERENCE`, `UNRESOLVED_CATALOG_REFERENCE`, `DUPLICATE_KEY`, `ASSET_NOT_FOUND`, `TRAVERSAL_ASSET_PATH`, `UNSUPPORTED_FORMAT_VERSION`, `CATALOG_SNAPSHOT_MISMATCH`, `UNRESOLVED_WAVE_REFERENCE`, `UNRESOLVED_PLACEMENT_REGION`.

- [ ] **Step 4: Implement loader + controller**

```java
package dev.hendrikhoemberg.dmhelper.agent;

import java.util.List;

public record ValidationErrorCatalog(
        String catalogVersion,
        List<Entry> errors
) {
    public record Entry(
            String code,
            String defaultSeverity,
            String summary,
            String when,
            String suggestionTemplate,
            List<String> repairHints
    ) {}
}
```

```java
@Service
public class AgentContractService {
    private final ValidationErrorCatalog validationErrors;
    // capability manifest added in Task 3

    public AgentContractService(JsonMapper mapper) throws IOException {
        this.validationErrors = mapper.readValue(
                new ClassPathResource("agent/validation-error-catalog.json").getInputStream(),
                ValidationErrorCatalog.class);
    }

    public ValidationErrorCatalog validationErrors() {
        return validationErrors;
    }
}
```

```java
@RestController
@RequestMapping("/api/v1")
public class AgentContractController {
    private final AgentContractService service;

    public AgentContractController(AgentContractService service) {
        this.service = service;
    }

    @GetMapping("/validation-errors")
    public ValidationErrorCatalog validationErrors() {
        return service.validationErrors();
    }
}
```

PIN exemption in `WebMvcConfig.excludePathPatterns`:

```java
"/api/v1/schemas/**",
"/api/v1/catalog/**",
"/api/v1/capabilities",
"/api/v1/validation-errors",
"/api/v1/table/state",
```

(Also add `/api/v1/catalog/**` here even though the capabilities route arrives in Task 3 — catalog PIN freedom is required for agents.)

- [ ] **Step 5: Run tests — expect PASS.**

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/agent/validation-error-catalog.json \
        src/main/java/dev/hendrikhoemberg/dmhelper/agent \
        src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/agent
git commit -m "$(cat <<'EOF'
feat: serve validation error catalog for agents

Add a classpath error catalog covering every import problem code and
expose it at GET /api/v1/validation-errors without a DM PIN.
EOF
)"
```

---

### Task 3: Capability manifest resource + API + matrix contract

**Files:**
- Create: `src/main/resources/agent/capability-manifest.json`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/agent/CapabilityManifest.java`
- Modify: `AgentContractService`, `AgentContractController`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/CapabilityManifestContractTest.java`
- Modify: `docs/campaign-capabilities.md` (align rows; do not invent new features)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/CapabilityMatrixMarkdownSyncTest.java`

**Interfaces:**
- Produces: `GET /api/v1/capabilities` → `CapabilityManifest`
- Consumes: existing capability knowledge from `docs/campaign-capabilities.md` and delivery items 1–9

- [ ] **Step 1: Write failing contract tests**

```java
@SpringBootTest
class CapabilityManifestContractTest {
    @Autowired AgentContractService service;

    @Test
    void statusesAreClosedEnum() {
        assertThat(service.capabilities().capabilities())
                .allMatch(c -> Set.of("SUPPORTED", "PARTIAL", "UNSUPPORTED", "EXPERIMENTAL")
                        .contains(c.status()));
    }

    @Test
    void requiredReadinessCapabilitiesPresent() {
        var ids = service.capabilities().capabilities().stream()
                .map(CapabilityManifest.Capability::id)
                .collect(Collectors.toSet());
        assertThat(ids).contains(
                "p0.runtime_reliability",
                "package.v1.contract",
                "package.v2.foundation",
                "package.v2.round_trip",
                "session.cockpit",
                "adventure.structured_scenes",
                "quest.objectives",
                "compendium.custom_with_provenance",
                "character.sheet_completion",
                "encounter.prep_and_waves",
                "map.published_workflow",
                "agent.sdk",
                "world.graph",
                "map.fog_of_war"
        );
    }

    @Test
    void p3ItemsAreNotFalselySupported() {
        var byId = service.capabilities().capabilities().stream()
                .collect(Collectors.toMap(CapabilityManifest.Capability::id, c -> c));
        assertThat(byId.get("world.graph").status()).isEqualTo("UNSUPPORTED");
        assertThat(byId.get("map.fog_of_war").status()).isEqualTo("UNSUPPORTED");
        assertThat(byId.get("agent.sdk").status()).isIn("PARTIAL", "SUPPORTED");
    }

    @Test
    void flagshipFixturesExistOnClasspath() {
        for (String path : service.capabilities().flagshipFixtures()) {
            String resource = path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
            assertThat(new ClassPathResource(resource).exists())
                    .as("missing fixture %s", path)
                    .isTrue();
        }
    }
}
```

```java
class CapabilityMatrixMarkdownSyncTest {
    @Test
    void markdownRowsMatchManifestStatusesForSharedNames() throws Exception {
        // Parse docs/campaign-capabilities.md table rows and
        // assert each Notes/Capability name that maps to a known id
        // has the same status string as capability-manifest.json.
        // Implementation: load both files; for each markdown row,
        // if capability name equals a manifest capability name, statuses must match.
    }
}
```

Implement the markdown sync test fully (no stub body): parse `| ... | STATUS |` lines with a simple split, match on the **Capability** column text against `Capability.name`.

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Author `capability-manifest.json`**

Include at least these capabilities (status values must match `docs/campaign-capabilities.md` after you update both):

| id | status |
|---|---|
| `p0.runtime_reliability` | SUPPORTED |
| `package.v1.contract` | SUPPORTED |
| `package.v2.foundation` | SUPPORTED |
| `package.v2.round_trip` | SUPPORTED |
| `session.cockpit` | SUPPORTED |
| `adventure.structured_scenes` | SUPPORTED |
| `quest.objectives` | SUPPORTED |
| `compendium.custom_with_provenance` | SUPPORTED |
| `character.sheet_completion` | SUPPORTED |
| `encounter.prep_and_waves` | SUPPORTED |
| `map.published_workflow` | SUPPORTED |
| `encounter.difficulty_2024` | PARTIAL |
| `character.full_asi_schedule` | PARTIAL |
| `agent.sdk` | PARTIAL → flip to SUPPORTED in Task 10 after docs land |
| `world.graph` | UNSUPPORTED |
| `map.fog_of_war` | UNSUPPORTED |
| `travel.weather` | UNSUPPORTED |
| `player.interaction` | UNSUPPORTED |

Copy endpoint paths from the Domain contracts section. Set `catalogVersion` to `CampaignCatalogService.CATALOG_VERSION` (`srd-5.2-dmhelper-1`).

- [ ] **Step 4: Wire records + service + controller**

```java
public record CapabilityManifest(
        int manifestVersion,
        String catalogVersion,
        List<Integer> packageFormatVersions,
        List<String> rulesets,
        Map<String, String> endpoints,
        List<Capability> capabilities,
        List<String> contentTypes,
        List<String> flagshipFixtures
) {
    public record Capability(
            String id,
            String name,
            String status,
            Integer deliveryItem,
            String notes
    ) {}
}
```

```java
@GetMapping("/capabilities")
public CapabilityManifest capabilities() {
    return service.capabilities();
}
```

- [ ] **Step 5: Align `docs/campaign-capabilities.md`** so shared names/statuses match. Add missing rows for agent SDK / P3 markers if needed.

- [ ] **Step 6: Run tests — PASS.**

- [ ] **Step 7: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: add machine-readable capability manifest API

Expose readiness capabilities at GET /api/v1/capabilities and keep the
Markdown capability matrix synchronized via contract tests.
EOF
)"
```

---

### Task 4: Catalog snapshot fidelity + PIN-free catalog

**Files:**
- Modify: `WebMvcConfig` (if Task 2 did not already exempt `/api/v1/catalog/**`)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog/CatalogSnapshotFidelityTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogControllerTest.java` (add PIN-free assertion if missing)
- Modify: `docs/campaign-format-v2.md` Typed Catalog section — document snapshot hash obligation

**Interfaces:**
- Consumes: `CampaignCatalogService.snapshot()`, classpath `catalog/srd-5.2-catalog.json`
- Produces: build failure on drift

- [ ] **Step 1: Write failing fidelity test**

```java
@SpringBootTest
class CatalogSnapshotFidelityTest {
    @Autowired CampaignCatalogService catalogService;
    @Autowired JsonMapper mapper;

    @Test
    void checkedInSnapshotMatchesLiveService() throws Exception {
        CatalogSnapshot live = catalogService.snapshot();
        CatalogSnapshot file = mapper.readValue(
                new ClassPathResource("catalog/srd-5.2-catalog.json").getInputStream(),
                CatalogSnapshot.class);

        assertThat(file.version()).isEqualTo(live.version());
        assertThat(file.sha256()).isEqualTo(live.sha256());
        assertThat(file.entries()).hasSize(live.entries().size());
        // Order-sensitive: service sorts by type then sourceKey
        assertThat(file.entries()).isEqualTo(live.entries());
    }
}
```

- [ ] **Step 2: Run — may FAIL if snapshot is stale.**

- [ ] **Step 3: Refresh snapshot if needed**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-docs-agent-sdk \
  -Ddmhelper.writeCatalogSnapshot=true \
  -Dtest=CampaignCatalogServiceTest#writeSnapshotWhenRequested test
```

Confirm `src/main/resources/catalog/srd-5.2-catalog.json` updates. Re-run fidelity test → PASS.

- [ ] **Step 4: Assert catalog endpoints are PIN-free**

```java
@Test
void catalogEndpointsDoNotRequirePin() throws Exception {
    mvc.perform(get("/api/v1/catalog")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/catalog/snapshot"))
            .andExpect(status().isOk())
            .andExpect(header().exists("ETag"));
}
```

With `dmhelper.pin-enabled=true` (default), exclusion list must include `/api/v1/catalog/**`. If tests run with pin disabled, still keep the exclude for production safety.

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
test: enforce catalog snapshot fidelity and PIN-free access

Fail the build when the checked-in SRD catalog drifts from the live
snapshot, and allow agents to read catalog APIs without a DM PIN.
EOF
)"
```

---

### Task 5: Executable documentation examples

**Files:**
- Create: `src/test/resources/docs-examples/minimal-valid.dmcampaign.json`
- Create: `src/test/resources/docs-examples/schema-error.dmcampaign.json`
- Create: `src/test/resources/docs-examples/semantic-error.dmcampaign.json`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocumentationExampleValidationTest.java`
- Later docs will link these paths (Task 7–8)

**Interfaces:**
- Consumes: `CampaignPackageValidationPipeline` (or schema + semantic validators used by dry-run)
- Produces: green build only when examples behave as documented

- [ ] **Step 1: Write the test**

```java
@SpringBootTest
class DocumentationExampleValidationTest {
    @Autowired CampaignPackageValidationPipeline pipeline;
    // or the public dry-run entry point used by CampaignPackageController

    @Test
    void minimalValidHasNoErrors() throws Exception {
        var result = validateClasspath("docs-examples/minimal-valid.dmcampaign.json");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void schemaErrorExampleFailsSchema() throws Exception {
        var result = validateClasspath("docs-examples/schema-error.dmcampaign.json");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.problems()).anyMatch(p ->
                p.code().equals(ImportProblemCodes.SCHEMA_VIOLATION)
                        || p.code().startsWith("SCHEMA_"));
    }

    @Test
    void semanticErrorExampleIsSchemaValidButSemanticallyInvalid() throws Exception {
        var result = validateClasspath("docs-examples/semantic-error.dmcampaign.json");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.problems()).anyMatch(p ->
                p.code().equals(ImportProblemCodes.UNRESOLVED_REFERENCE)
                        || p.code().equals(ImportProblemCodes.UNRESOLVED_CATALOG_REFERENCE));
    }

    @Test
    void documentedFlagshipFixturesStillExist() {
        assertThat(new ClassPathResource("campaigns/v2/minimal.dmcampaign.json").exists()).isTrue();
        assertThat(new ClassPathResource("campaigns/v2/feature-complete.dmcampaign/manifest.json").exists()).isTrue();
        assertThat(new ClassPathResource("campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json").exists()).isTrue();
    }
}
```

Wire `validateClasspath` through the same pipeline dry-run uses (prefer public service method already tested in `CampaignPackageValidationPipelineTest`).

- [ ] **Step 2: Run — FAIL (missing examples).**

- [ ] **Step 3: Author examples**

1. **minimal-valid** — copy/adapt `campaigns/v2/minimal.dmcampaign.json`; ensure formatVersion 2, one campaign root, zero assets. Must dry-run with zero ERROR.
2. **schema-error** — same as minimal but set `"formatVersion": "two"` or add `"extraUnknownField": true` at root (v2 closed schema → `SCHEMA_VIOLATION` / additionalProperties failure).
3. **semantic-error** — schema-valid party or encounter that references a non-existent catalog spell/statblock key (e.g. `sourceKey: "definitely-not-a-real-goblin"`).

Keep files small (< 200 lines). Do not embed binary assets.

- [ ] **Step 4: Run — PASS.**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
test: add executable documentation campaign examples

Minimal valid, schema-error, and semantic-error packages are checked in
and validated so authoring docs cannot drift from the pipeline.
EOF
)"
```

---

### Task 6: Expand campaign format v2 authoring reference (item 9 fields + error catalog link)

**Files:**
- Modify: `docs/campaign-format-v2.md`
- Create: `docs/authoring/validation-errors.md` (human index of the JSON catalog)
- Create: `docs/authoring/README.md`
- Optional move is **not** required; keep `docs/campaign-format-v2.md` path stable and link from `docs/authoring/README.md`

- [ ] **Step 1: Document encounter prep depth**

Add sections (after encounters discussion / before fixtures):

```markdown
## Encounter Preparation (v2)

### Waves

Each encounter may include ordered `waves[]`:

| Field | Type | Notes |
|-------|------|-------|
| `key` | package key | Unique within the encounter |
| `name` | string | Display label |
| `sortOrder` | int | Editorial order |
| `status` | enum | `RESERVE` \| `PENDING` \| `ACTIVE` \| `DEPLETED` |
| `triggerKind` | enum | `MANUAL` \| `ROUND` \| `HP_THRESHOLD` \| `CUSTOM` |
| `triggerValue` | string \| null | e.g. `"3"` for round 3 |
| `notes` | string \| null | DM-only |

Combatants may set `waveKey` (package-local), `startX`/`startY` (pixels from top-left), and `placementRegionKey` (map region key).

### Prep and rewards

`prep` object: tactics, morale, surrender/flee, environment notes, source locator, optional scene ref.
`rewards` object: XP, currency entries, item grants, quest objective refs, free-form notes.
Reward application is **DM-confirmed at table**; the package only carries the authored draft.
```

- [ ] **Step 2: Document map calibration and regions**

```markdown
## Map Document Semantics

- Token coordinates: **pixels** from the top-left origin of the map canvas.
- Token sizes: **cell counts**.
- Region/primitive grid fields: **column/row** indices when the schema says so (see `map-document-v2.schema.json`).
- IMAGE layers may include `calibration` (two-point grid calibration), `rotationDeg`, `locked`, and `playerVisible`.
- REGION primitives require stable `key` + `label` for scene/encounter placement references.
- Layers/primitives with `playerVisible: false` are DM-only; player projection strips them. Tokens are not duplicated per presentation layer.
```

- [ ] **Step 3: Link error catalog and examples**

```markdown
## Validation Error Catalog

Machine-readable: `GET /api/v1/validation-errors` and
`src/main/resources/agent/validation-error-catalog.json`.

Human index: [validation-errors.md](authoring/validation-errors.md).

## Documentation Examples

| Example | Path | Expected |
|---------|------|----------|
| Minimal valid | `src/test/resources/docs-examples/minimal-valid.dmcampaign.json` | dry-run OK |
| Schema error | `src/test/resources/docs-examples/schema-error.dmcampaign.json` | SCHEMA_VIOLATION |
| Semantic error | `src/test/resources/docs-examples/semantic-error.dmcampaign.json` | UNRESOLVED_* |
```

- [ ] **Step 4: Generate human error index**

`docs/authoring/validation-errors.md` — table of `code | severity | summary` produced from the JSON (hand-written or one-time dump). Opening paragraph must state the JSON/API are authoritative if Markdown lags.

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs: expand campaign format v2 for encounters, maps, and errors

Document waves, prep/rewards, map calibration/regions, and link the
validation error catalog and executable examples.
EOF
)"
```

---

### Task 7: Agent guide (playbook, mapping rules, checklist, dry-run repair)

**Files:**
- Create: `docs/agent/README.md`
- Create: `docs/agent/conversion-playbook.md`
- Create: `docs/agent/mapping-rules.md`
- Create: `docs/agent/verification-checklist.md`
- Create: `docs/agent/dry-run-repair.md`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/AgentGuideContractTest.java`

**Interfaces:**
- Consumes: §18 steps, capability manifest endpoints, docs-examples
- Produces: agent-facing Markdown that tests can assert is present and references real paths

- [ ] **Step 1: Write contract test that required agent docs exist and reference real artifacts**

```java
class AgentGuideContractTest {
    @Test
    void agentGuideFilesExist() {
        for (String path : List.of(
                "docs/agent/README.md",
                "docs/agent/conversion-playbook.md",
                "docs/agent/mapping-rules.md",
                "docs/agent/verification-checklist.md",
                "docs/agent/dry-run-repair.md")) {
            assertThat(Path.of(path)).exists();
        }
    }

    @Test
    void playbookReferencesCatalogAndSchemas() throws Exception {
        String playbook = Files.readString(Path.of("docs/agent/conversion-playbook.md"));
        assertThat(playbook).contains("/api/v1/catalog");
        assertThat(playbook).contains("/api/v1/schemas/");
        assertThat(playbook).contains("must never invent");
        assertThat(playbook).contains("capability-manifest.json");
    }

    @Test
    void dryRunRepairReferencesExecutableExamples() throws Exception {
        String body = Files.readString(Path.of("docs/agent/dry-run-repair.md"));
        assertThat(body).contains("docs-examples/schema-error.dmcampaign.json");
        assertThat(body).contains("docs-examples/semantic-error.dmcampaign.json");
        assertThat(body).contains("UNRESOLVED_REFERENCE");
    }
}
```

- [ ] **Step 2: Run — FAIL (missing files).**

- [ ] **Step 3: Write `conversion-playbook.md`** — expand master-spec §18 into operational steps with DMHelper-specific commands:

```markdown
# Agent Conversion Playbook

## Non-invention policy

The agent must never invent a DC, stat, map coordinate, source key, or missing rule
merely to make validation pass. Record an unresolved `SOURCE_ANNOTATION` or use a
documented generic fallback. Prefer ERROR/WARNING visibility over silent guesses.

## Steps

1. Inventory the source (title, edition, ruleset, pages, maps, assets, rights).
2. Extract without invention; keep page locators and confidence.
3. Build an intermediate source model (headings, boxed text, keyed locations, creatures, checks, treasure, transitions, assets).
4. Resolve catalog content:
   - Read `GET /api/v1/catalog` or `src/main/resources/catalog/srd-5.2-catalog.json`
   - Record `version` + `sha256` in converter metadata / provenance
5. Create campaign-scoped custom content for non-catalog material with provenance.
6. Assign stable package keys (`^[a-z0-9][a-z0-9._-]{0,99}$`) before wiring references.
7. Attach assets under `assets/**` with safe relative paths only.
8. Validate locally against `campaign-format-v2.schema.json` and `map-document-v2.schema.json`.
9. Dry-run via package preview API; iterate on structured problems until no ERROR remains.
10. Review WARNINGs; do not suppress ambiguity.
11. Import preview: verify counts, provenance, degraded fields, sample scenes.
12. Post-import smoke: open session cockpit, follow a branch, activate an encounter, present a handout, export and re-import.

## Local offline mode

When the app is not running, use classpath artifacts only:

- `src/main/resources/schemas/campaign-format-v2.schema.json`
- `src/main/resources/schemas/map-document-v2.schema.json`
- `src/main/resources/catalog/srd-5.2-catalog.json`
- `src/main/resources/agent/capability-manifest.json`
- `src/main/resources/agent/validation-error-catalog.json`
```

- [ ] **Step 4: Write `mapping-rules.md`**

Cover, with concrete JSON snippets matching the real schema:

- Package vs catalog `ContentReference`
- Key uniqueness per `CampaignContentType`
- Scene transitions vs editorial order
- Quest objectives + dependencies
- Encounter waves + placement regions
- Map units (pixels vs cells)
- Provenance required fields for non-original content
- When to emit `SOURCE_ANNOTATION` instead of inventing data

- [ ] **Step 5: Write `verification-checklist.md`**

Checkbox list an agent or human can run:

```markdown
- [ ] `formatVersion` is `2`
- [ ] capability manifest `agent.sdk` is SUPPORTED for this app revision
- [ ] catalog `sha256` recorded in converter output
- [ ] schema validate (campaign + each map document)
- [ ] dry-run: zero ERROR
- [ ] warnings reviewed and accepted consciously
- [ ] flagship-shaped smoke: cockpit, encounter, handout, export/import
```

- [ ] **Step 6: Write `dry-run-repair.md`**

For each docs-example error package, show:

1. Command to preview
2. Sample problem JSON
3. Exact field fix
4. Re-validate success criteria

Use the real problem shape and codes from Task 1–2.

- [ ] **Step 7: Write `docs/agent/README.md` index.**

- [ ] **Step 8: Run `AgentGuideContractTest` — PASS.**

- [ ] **Step 9: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs: add agent conversion playbook and repair guide

Provide offline-capable mapping rules, verification checklist, and
dry-run repair examples wired to executable fixtures.
EOF
)"
```

---

### Task 8: DM manual

**Files:**
- Create: `docs/dm-manual/README.md`
- Create: `docs/dm-manual/01-first-run-and-backup.md`
- Create: `docs/dm-manual/02-campaign-preparation.md`
- Create: `docs/dm-manual/03-session-cockpit.md`
- Create: `docs/dm-manual/04-maps-encounters-party.md`
- Create: `docs/dm-manual/05-import-preview.md`
- Create: `docs/dm-manual/06-offline-and-troubleshooting.md`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/DmManualContractTest.java`

- [ ] **Step 1: Contract test for required chapters + critical phrases**

```java
class DmManualContractTest {
    @Test
    void chaptersExist() { /* assert files exist */ }

    @Test
    void sessionCockpitDocumentsResumeOrder() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/03-session-cockpit.md"));
        assertThat(body).containsIgnoringCase("active encounter");
        assertThat(body).containsIgnoringCase("current scene");
        assertThat(body).contains("/session");
    }

    @Test
    void importPreviewExplainsWarnings() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/05-import-preview.md"));
        assertThat(body).contains("acceptWarnings");
        assertThat(body).contains("ERROR");
        assertThat(body).contains("WARNING");
    }
}
```

- [ ] **Step 2: Author concise operational chapters** (not marketing). Required content:

| Chapter | Must cover |
|---|---|
| First run | Start JAR, browser open, PIN, data directory, startup backup |
| Campaign prep | Adventures/scenes, maps, encounters, party sheets, notes/handouts, calendar |
| Session cockpit | Layout (story/table/encounter/plan/quick access/lifecycle); start/resume/end; session log draft; keyboard actions |
| Maps/encounters/party | Published map calibrate; waves/rewards confirm; rest/batch ops pointer |
| Import preview | Preview counts, warnings vs errors, confirm flow, additive import |
| Offline/troubleshooting | No internet required; backup restore; common import errors → error catalog link |

Describe **implemented** behavior only. Where something is PARTIAL (2024 difficulty estimate, incomplete ASI tables), say so and link the capability matrix.

- [ ] **Step 3: Run contract test — PASS.**

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs: add DM manual for prep, cockpit, and import

Document first-run, session cockpit resume behavior, import preview
warnings, and offline troubleshooting against implemented features.
EOF
)"
```

---

### Task 9: Architecture reference + product status docs

**Files:**
- Create: `docs/architecture/README.md`
- Create: `docs/architecture/modules-and-ownership.md`
- Create: `docs/architecture/import-export-flow.md`
- Create: `docs/architecture/session-and-player-projection.md`
- Create: `docs/architecture/security-boundaries.md`
- Create: `docs/architecture/testing-strategy.md`
- Create: `docs/product/README.md`
- Create: `docs/product/known-limitations.md`
- Create: `docs/product/format-compatibility.md`
- Create: `docs/product/release-notes.md` (entry for readiness items 1–10)
- Create: `docs/README.md` (top-level index)
- Modify: `SPEC.md` (status banner only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java`

- [ ] **Step 1: Contract test**

```java
class DocsIndexContractTest {
    @Test
    void topLevelIndexLinksMajorAudiences() throws Exception {
        String index = Files.readString(Path.of("docs/README.md"));
        for (String needle : List.of(
                "dm-manual/", "authoring/", "architecture/", "agent/",
                "campaign-capabilities.md", "campaign-format-v2.md")) {
            assertThat(index).contains(needle);
        }
    }

    @Test
    void architectureDocumentsAdapterOrder() throws Exception {
        String body = Files.readString(Path.of("docs/architecture/import-export-flow.md"));
        assertThat(body).contains("CampaignImportCoordinator");
        assertThat(body).contains("CampaignExportCoordinator");
        assertThat(body).contains("CampaignSectionAdapter");
    }

    @Test
    void securityDocStatesPlayerSafetyBoundary() throws Exception {
        String body = Files.readString(Path.of("docs/architecture/security-boundaries.md"));
        assertThat(body).containsIgnoringCase("player");
        assertThat(body).contains("PIN");
        assertThat(body).containsIgnoringCase("untrusted");
    }
}
```

- [ ] **Step 2: Author architecture docs from real code**

Adapter order must match the live coordinator (verify in `CampaignExportCoordinator` / importer registration — do not copy stale order from format-v2.md if it differs; update format-v2.md if needed). Include:

- Module packages under `dev.hendrikhoemberg.dmhelper.*`
- Import pipeline stages (container → schema → semantic → catalog → spatial/assets → migrate → preview → atomic persist)
- Session workspace selection order (active encounter map → current scene map → plan → chooser → empty)
- Player-safe projection rules (server-side; DM-only fields never sent)
- Flyway migrations V1–V11 list (names only) + asset storage UUID rule
- Testing layers: unit, contract, round-trip fixtures, Playwright smoke

- [ ] **Step 3: Product status docs**

`docs/product/format-compatibility.md`:

| Format | Read | Write | Notes |
|---|---|---|---|
| v1 JSON | yes (migrate) | legacy paths only | Migrates to v2 on package import |
| v2 JSON | yes | yes (asset-free) | |
| v2 ZIP `.dmcampaign` | yes | yes when assets present | |

`docs/product/known-limitations.md` — pull honestly from capability matrix PARTIAL/UNSUPPORTED rows.

`docs/product/release-notes.md` — one section summarizing delivery items 1–10 outcomes (bullet list, not marketing fluff).

- [ ] **Step 4: SPEC.md banner** (insert after title block):

```markdown
> **Implementation status (readiness program):** Delivery items 1–9 of
> `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` are implemented.
> Item 10 (documentation/agent SDK) is the current gate. Item 11 (P3 expansion) is not
> started. This file remains the original product vision; for **implemented** capability
> status see `docs/campaign-capabilities.md` and `GET /api/v1/capabilities`.
```

Adjust the banner in Task 10 when item 10 flips to implemented.

- [ ] **Step 5: Write `docs/README.md` index** linking all audiences.

- [ ] **Step 6: Run contract tests — PASS.**

- [ ] **Step 7: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs: add architecture reference and product status index

Document module ownership, import/export flow, player-safety boundaries,
format compatibility, and known limitations for the readiness program.
EOF
)"
```

---

### Task 10: About page + agent.sdk SUPPORTED + master status flip + full verification

**Files:**
- Modify: `src/main/resources/templates/about.html` (link to docs audiences / API contract paths if appropriate)
- Modify: `src/main/resources/agent/capability-manifest.json` — set `agent.sdk` to `SUPPORTED`
- Modify: `docs/campaign-capabilities.md` — agent SDK row SUPPORTED
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` — item 10 `IMPLEMENTED`
- Modify: `SPEC.md` banner if needed
- Create/modify: any final `AgentSdkReleaseGateTest` aggregating critical assertions

- [ ] **Step 1: Write release-gate test**

```java
@SpringBootTest
@AutoConfigureMockMvc
class AgentSdkReleaseGateTest {
    @Autowired MockMvc mvc;
    @Autowired AgentContractService contracts;
    @Autowired CampaignCatalogService catalog;

    @Test
    void agentSdkIsSupported() {
        assertThat(contracts.capabilities().capabilities())
                .anyMatch(c -> "agent.sdk".equals(c.id()) && "SUPPORTED".equals(c.status()));
    }

    @Test
    void publicContractEndpointsRespond() throws Exception {
        mvc.perform(get("/api/v1/capabilities")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/validation-errors")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalog")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/schemas/campaign-format-v2")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/schemas/map-document-v2")).andExpect(status().isOk());
    }

    @Test
    void catalogHashIsStableAcrossCalls() {
        assertThat(catalog.snapshot().sha256()).isEqualTo(catalog.snapshot().sha256());
    }
}
```

- [ ] **Step 2: Flip statuses and about links**

About page: add a short “Contracts for converters” section with paths:

- `/api/v1/capabilities`
- `/api/v1/catalog`
- `/api/v1/validation-errors`
- `/api/v1/schemas/campaign-format-v2`

Do not claim all-in-one readiness complete if §23 still needs the manual acceptance session — only claim documentation/agent SDK delivery item done.

- [ ] **Step 3: Run focused suites**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-docs-agent-sdk \
  -Dtest=ImportProblemCodesCoverageTest,ValidationErrorCatalogTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,CatalogSnapshotFidelityTest,DocumentationExampleValidationTest,AgentGuideContractTest,DmManualContractTest,DocsIndexContractTest,AgentSdkReleaseGateTest,CatalogControllerTest,SchemaControllerTest,CampaignCatalogServiceTest,ContentDestinationRouteContractTest test
```

Expected: all PASS.

- [ ] **Step 4: Run broader regression**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-docs-agent-sdk test
```

Expected: full suite green. If a single unrelated flaky test appears, fix only if caused by this work (PIN exclusions must not open DM HTML routes).

- [ ] **Step 5: Update master design status table**

```markdown
| 10 | Documentation/agent SDK release | `IMPLEMENTED` |
| 11 | P3 expansion | `PLANNED` |
```

- [ ] **Step 6: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: complete documentation and agent SDK release gate

Mark agent.sdk SUPPORTED, publish about-page contract links, and flip
delivery item 10 to IMPLEMENTED after release-gate tests pass.
EOF
)"
```

---

## Self-review (plan vs master spec §18–§19, §21.1)

| Spec requirement | Task coverage |
|---|---|
| §19.1 product vision / capability matrix / release notes / limitations | Tasks 3, 9 |
| §19.2 DM manual | Task 8 |
| §19.3 authoring reference (container, keys, schema, units, assets, provenance, errors, migrations, examples) | Tasks 5–6 (+ existing format-v2) |
| §19.4 architecture reference | Task 9 |
| §19.5 agent guide + playbook + catalog + mapping + non-invention + dry-run repair + checklist | Tasks 4, 7 |
| §18 conversion steps | Task 7 playbook |
| §21.1 contract tests (schemas, catalog, destinations, fixtures) | Tasks 1–5, 10 (destinations already exist; re-run in gate) |
| Executable documentation examples | Task 5 |
| Machine-readable capability manifest | Task 3 |
| Typed catalog snapshot hash | Task 4 |
| Validation error catalog | Tasks 1–2 |
| PIN-free agent contract APIs | Tasks 2–4, 10 |
| Honest PARTIAL/UNSUPPORTED for P3 | Tasks 3, 9–10 |
| No item 11 P3 feature work | Global constraints |

**Placeholder scan:** No TBD/TODO steps; code and commands are concrete. Implementers must still harvest any extra problem codes the coverage test discovers — that is intentional feedback, not a placeholder.

**Type consistency:** `CapabilityManifest`, `ValidationErrorCatalog`, `ImportProblemCodes`, and endpoint paths are named consistently across tasks. Wire format of `CampaignImportProblem` is unchanged.

**Out of scope reminders:** fog gameplay, world graph, travel/weather, interactive players, format v3, rewriting SPEC.md into a full manual, bundling proprietary content.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-17-p1-documentation-agent-sdk.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks (`superpowers:subagent-driven-development`)
2. **Inline Execution** — run tasks in this session with checkpoints (`superpowers:executing-plans`)

Which approach?
