# P1 Package v2 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the secure, previewable, schema-driven campaign package version 2 foundation: ZIP and asset-free JSON containers, immutable package-local keys, typed content references and catalog snapshots, deterministic v1 migration, staged asset validation, explicit warning confirmation, and atomic import/export for the campaign sections already executable in version 1.

**Architecture:** Keep `CampaignExportDto`, the v1 schemas, and the existing direct v1 JSON endpoint as a frozen compatibility surface. Add a separate `campaign/packagev2` module whose reader stages untrusted input, whose migration registry converts validated v1 documents into a canonical `CampaignManifestV2`, and whose validator produces a retained preview before any persistence. A small relational key registry preserves package keys without adding key columns to every domain table; the v2 coordinator adapts the currently supported manifest sections through the existing persistence flow, binds imported keys in the same transaction, and installs only already-validated assets with rollback cleanup.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA, H2, Flyway, Jackson 3, networknt JSON Schema Validator 3.0.2, JSON Schema draft 2020-12, Apache Commons Compress from the Spring Boot dependency BOM, Thymeleaf, Alpine.js, JUnit 5, AssertJ, Mockito, Playwright 1.54, Maven Wrapper.

## Global Constraints

- Runtime package authoring, validation, preview, import, export, catalog access, and migration work without internet access.
- No new frontend build chain or runtime CDN is introduced.
- Canonical asset-bearing packages use the `.dmcampaign` ZIP container with exactly one root `manifest.json` and asset entries below `assets/`.
- Plain `.dmcampaign.json` remains accepted for version 1 and for asset-free version 2; an asset-free v2 export uses JSON, while any v2 export with assets uses ZIP.
- Format version 1 remains frozen. Its DTO, schema, fixtures, and direct import behavior continue to pass unchanged.
- Format version 2 keys match `^[a-z0-9][a-z0-9._-]{0,99}$`, are unique within `(campaign, content type)`, and never change after binding to a local entity.
- Version 2 references never use display names. Package references contain `scope`, `type`, and `key`; catalog references contain `scope`, `type`, `ruleset`, and `sourceKey`.
- The v2 schema initially covers every section that the executable v1 DTO currently persists. Absent future entities such as quests, objectives, transitions, and custom non-statblock rules content are rejected as unknown properties until their dedicated workstream adds schema, DTO, validator, and persistence support together.
- Every campaign-owned entity represented by the v2 schema has a key, including nested chapters, scenes, tokens, combatants, sheets, and sheet resources.
- Bundled catalog identifiers use ruleset `SRD_5_2` and source `SRD`; catalog records are sorted by `(type, sourceKey)` before hashing.
- The catalog hash is lowercase hexadecimal SHA-256 over canonical UTF-8 JSON with no insignificant whitespace.
- The package reader accepts at most 2,000 ZIP entries, a 10 MiB manifest, 100 MiB per expanded asset, 1 GiB total expanded content, a 100:1 per-entry compression ratio, and normalized paths no longer than 240 characters.
- Raw package upload is streamed to staging and capped at 1 GiB without raising the global Spring multipart limit.
- Supported v2 asset media types in this milestone are exactly `image/png`, `image/jpeg`, `image/gif`, and `image/webp`. Audio remains unsupported and is rejected, not silently retained.
- Asset paths use forward slashes, Unicode NFC normalization, and only `assets/maps/`, `assets/handouts/`, or `assets/portraits/`; absolute paths, backslashes, `.`/`..` segments, symlinks, encrypted entries, duplicate normalized case-folded paths, and unlisted root entries are errors.
- Asset validation checks declared media type, extension, magic bytes, expanded size, and SHA-256. A mismatch is an error.
- A preview expires after 30 minutes. Preview creation, expiration, discard, validation failure, and successful import remove their staging data; a failed confirmation retains the valid preview for retry until expiry.
- Preview creation performs no database or final asset-directory writes. Warnings require `acceptWarnings=true` at confirmation. Errors never produce a confirmable preview.
- Import remains additive and never overwrites an existing campaign.
- Campaign persistence, key binding, and final asset installation share one transaction outcome. On rollback, no campaign row, package-key row, or installed asset survives.
- The manifest contains an explicit `exclusions` array. Until the complete-round-trip milestone, exports list the exact persistent fields not represented by the foundation DTO.
- Existing map image data URLs and v1 embedded handout data are extracted to package assets on export/migration; version 2 manifests never contain binary data URLs.
- DM PIN checks continue to cover every new import, preview, confirmation, discard, export, and live catalog endpoint. Player clients receive none of these payloads.
- Tests use an isolated home directory through `-DargLine=-Duser.home=/tmp/dmhelper-p1-v2`.

---

## Scope Boundary

This is master-spec delivery item 3, **Package v2 foundation**. It includes the container, keys, schemas, current-surface DTO, validation pipeline, typed catalog, preview, migrations, staged assets, atomic confirmation, and executable fixtures.

It does not claim complete backup fidelity. The v2 foundation export must place these exact values in `metadata.exclusions` until delivery item 4 implements them:

```text
CAMPAIGN_SETTINGS
CURRENT_SCENE
PARTY_CURRENT_HP
HANDOUT_PRESENTATION_STATE
COMBAT_LOG
DICE_HISTORY
CALENDAR_CONFIGURATION
CALENDAR_CURRENT_DATE
CUSTOM_COMPENDIUM_NON_STATBLOCK
STRUCTURED_SCENE_TRANSITIONS
QUESTS_AND_OBJECTIVES
```

The next **Complete Round-trip** plan will remove those exclusions one by one, split the compatibility adapter into per-module `CampaignSectionImporter`/`CampaignSectionExporter` implementations, add the three flagship semantic-deep-compare fixtures, and make the v2 format a complete recovery artifact. Session cockpit work remains delivery item 5.

Open P0 tracker/map mutation feedback, correlation identifiers, and encounter difficulty labeling remain separately required release-gate work. This package plan does not mark those items complete.

## File Structure

### Files created

- `src/main/resources/db/migration/V3__add_campaign_package_keys.sql` — immutable `(campaign, type, entity, package key)` registry with uniqueness and campaign cascade.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java` — stable type vocabulary used by keys, references, schemas, and catalogs.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKey.java` — JPA row for a package-local key binding.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyRepository.java` — lookup and uniqueness operations.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/PackageKeyGenerator.java` — deterministic valid key generation.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyService.java` — get/create and imported-key binding API.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java` — canonical v2 manifest for the currently executable campaign surface.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/ContentReference.java` — closed package-or-catalog reference union.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/AssetDescriptor.java` — asset key, safe package path, media metadata, byte count, and digest.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CatalogSnapshot.java` — typed entries plus version and hash.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageLimits.java` — exact reader limits from Global Constraints.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageException.java` — structured container/asset failure carrying one safe import problem.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/StagedCampaignPackage.java` — owned staging directory, manifest bytes, container kind, and staged asset paths.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriteRequest.java` — canonical manifest plus keyed streaming asset sources for the writer.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageReader.java` — bounded raw upload and safe ZIP/JSON staging.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriter.java` — deterministic JSON/ZIP writer with no embedded binaries.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/AssetSignatureValidator.java` — image signature, extension, size, and digest verification.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/CampaignFormatMigration.java` — versioned migration interface.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/CampaignMigrationResult.java` — migrated manifest/assets/problems/migration messages.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/FormatMigrationRegistry.java` — exact source-version dispatch.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java` — deterministic keys, typed references, and embedded-asset extraction.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SchemaValidator.java` — offline v2 schema validation.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java` — keys, references, catalog, spatial, asset, and state validation.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationResult.java` — canonical manifest, staging owner, source version, problems, and migration labels.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipeline.java` — read-result to canonical confirmable package pipeline.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java` — typed preview counts.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreview.java` — public preview response.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/PendingCampaignImport.java` — internal retained canonical manifest and staging ownership.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStore.java` — 30-minute lifecycle and cleanup.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignPreviewBuilder.java` — deterministic counts, sizes, provenance, exclusions, migrations, and problems.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPersistenceReceipt.java` — created campaign plus JSON-pointer-to-local-UUID bindings.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/HandoutImportSource.java` — validated external handout stream metadata for persistence.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/PreparedV1Import.java` — compatibility DTO, key-pointer plan, and external handout sources.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/V2CompatibilityAdapter.java` — canonical v2/current-v1 conversion and key-pointer binding plan.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/AssetInstaller.java` — validated staged-asset access and final-install coordination.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java` — warning gate, single transaction, persistence, key binding, and success cleanup.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportCoordinator.java` — current-surface v2 manifest and external-asset construction.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageArtifact.java` — export filename, media type, manifest, and asset sources.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog/CampaignCatalogService.java` — typed canonical catalog assembly and hashing.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageController.java` — streaming preview, confirmation, discard, and v2 export routes.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogController.java` — typed catalog endpoint.
- `src/main/resources/schemas/campaign-format-v2.schema.json` — closed v2 manifest contract.
- `src/main/resources/schemas/map-document-v2.schema.json` — map document contract whose image layers reference assets.
- `src/main/resources/catalog/srd-5.2-catalog.json` — checked-in typed catalog snapshot generated from bundled seed data.
- `src/main/resources/static/js/campaign-import.js` — raw streaming upload, preview rendering, warning acceptance, confirmation, discard, and retry UI.
- `src/main/resources/templates/campaigns/_import-dialog.html` — accessible preview/confirmation dialog.
- `src/test/resources/campaigns/v2/minimal.dmcampaign.json` — smallest asset-free v2 fixture.
- `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json` — canonical manifest for the ZIP fixture.
- `src/test/resources/campaigns/v2/current-surface.dmcampaign/assets/handouts/players-map.png` — valid small PNG test asset.
- `src/test/resources/campaigns/v2/current-surface.dmcampaign/assets/maps/crypt-map.webp` — valid small WebP test asset.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyServiceTest.java` — stable generation, imported binding, and collision tests.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java` — DTO/schema bidirectional compatibility.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageReaderTest.java` — container and archive attack matrix.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriterTest.java` — canonical container choice and binary separation.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java` — deterministic migration and reference rewrite coverage.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipelineTest.java` — ordered validation and structured problems.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStoreTest.java` — expiry/discard/cleanup behavior.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java` — warning gate, transaction, binding, and rollback.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java` — preview → confirm → export → re-preview contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog/CampaignCatalogServiceTest.java` — uniqueness, canonical order, snapshot, and hash.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageControllerTest.java` — streaming endpoint and response contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogControllerTest.java` — typed live catalog contract.
- `docs/campaign-format-v2.md` — container, keys, references, assets, errors, preview, migration, exclusions, and examples.

### Files modified

- `pom.xml` — add Spring-BOM-managed Apache Commons Compress.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` — expose validated-v1 persistence with pointer receipts while preserving public v1 methods.
- `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java` — accept an already-validated staged asset stream and retain rollback cleanup.
- `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java` — retain `/srd-keys` as legacy and document the typed replacement.
- `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java` — serve v2 schemas through the existing safe classpath route.
- `src/main/resources/templates/campaigns/list.html` — add package import entry point and dialog.
- `src/main/resources/templates/campaigns/detail.html` — make v2 the primary export and retain explicit v1 JSON export.
- `src/main/resources/templates/fragments/head.html` — load `campaign-import.js` with the existing local scripts.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java` — pointer receipt and v1 compatibility tests.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` — frozen v1 regression after persistence extraction.
- `src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java` — staged stream and rollback cleanup tests.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java` — V3 table/constraint verification.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java` — V1-baselined install upgrades through V3 without data loss.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java` — v2 schema route coverage.
- `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` — preview warnings, confirmation, and imported campaign browser flow.
- `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` — record only package-v2-foundation completion and keep complete round-trip open.

## Contract Decisions Locked by This Plan

| Concern | Package v2 foundation decision |
|---|---|
| v1 compatibility | Frozen v1 DTO/schema/direct endpoint remain available and tested. |
| Canonical v2 container | ZIP when `assets` is non-empty; JSON when it is empty. |
| ZIP root | `manifest.json` plus `assets/**` only. |
| Local key storage | Generic `campaign_package_key` registry, not key columns copied into every domain table. |
| Key immutability | Existing binding wins; attempting to bind a different key to the same local entity is an error. |
| Key uniqueness scope | Campaign plus `CampaignContentType`. The same text may be reused by two different types. |
| Generated keys | Slugged display name plus the first 12 hex characters of SHA-256 over `type + ':' + stableIdentity`. |
| Reference union | `scope=PACKAGE` requires `key`; `scope=CATALOG` requires `ruleset` and `sourceKey`; union branches reject each other's fields. |
| Unsupported v2 sections | Schema error through `additionalProperties: false`; never accepted and dropped. |
| Embedded image data | Extracted to assets during v1 migration/export; forbidden by v2 schemas. |
| Catalog mismatch | Snapshot hash mismatch is a warning; an unresolved typed content reference is an error. |
| Preview success | `status=READY` with no errors and no warnings, or `status=CONFIRM_WARNINGS` with warnings. |
| Preview failure | HTTP 200 with `status=BLOCKED`, no retained `previewId`, and structured problems. |
| Confirmation | Requires a retained preview ID; warnings require `acceptWarnings=true`. |
| Staging ownership | `PendingCampaignImport` is the sole owner until discard, expiry, or committed import. |
| Retry after failure | Valid preview remains available when confirmation fails before commit; it expires normally. |
| Atomic assets | Handout assets use generated final storage names and transaction rollback deletion; map assets become validated runtime data URLs only after confirmation. |
| Export exclusions | Required manifest metadata, not log output or documentation-only caveats. |
| Large upload | Raw request-body streaming endpoint; existing multipart endpoint stays v1-only. |
| Current-surface adapter | Temporary compatibility boundary; delivery item 4 replaces it with module-owned section adapters. |

## Manifest v2 Field Map

`CampaignManifestV2` follows the current `CampaignExportDto` field vocabulary where values are already authoritative, with these exact structural changes:

| v2 location | Required change from v1 |
|---|---|
| `/metadata` | Add `packageKey`, `createdAt`, `generator`, `catalogVersion`, `catalogSha256`, and `exclusions`. |
| `/campaign` | Add required `key`. |
| Every entity/nested entity | Add required `key`; replace v1 UUID/name identity fields such as assignment `id` with `key`. |
| Every cross-reference | Replace a string/UUID with `ContentReference`. |
| `/customStatBlocks` | Rename v1 `statBlocks`; each entry has a package key and no catalog ownership ambiguity. |
| `/assets` | Add descriptors; descriptors contain no bytes. |
| `/handouts/*` | Replace `fileName` and `imageData` with `assetRef`; retain `title`, `tags`, and `contentType`. |
| `/maps/*/document/layers/*/image` | Replace `dataUrl` with `assetRef`; retain `x`, `y`, `width`, and `height` in grid-cell units. |
| Token statblock/party member | `statBlockRef` and `partyMemberRef`. |
| Encounter map | `mapRef`. |
| Combatant token/statblock/party member | `tokenRef`, `statBlockRef`, and `partyMemberRef`. |
| Sheet species/background/feats/classes/spells | Typed catalog references. |
| Quick note target | A package reference; `targetType` is derived from the reference type and removed. |
| Assignment holder/item | Package party-member ref and typed catalog item ref. |
| Ledger assignment | Package assignment ref. |
| Timeline note | Package note ref. |
| Scene map/encounter/statblocks/handouts | Typed package/catalog references. |

All top-level section arrays are required and may be empty. `CampaignManifestV2` normalizes nested lists with `List.copyOf`, but the schema remains authoritative for required values and ranges.

## Migration Analysis

- `V3__add_campaign_package_keys.sql` is additive. It creates one new table and does not rewrite existing domain rows.
- Existing entities receive keys lazily on their first v2 export. Imported v2 entities bind the supplied key during the import transaction.
- Lazy key creation uses the entity UUID as `stableIdentity`, so renaming an entity before its first export changes only the readable slug; renaming after first export never changes its stored key.
- The key table has `ON DELETE CASCADE` from campaign. Stale bindings for individually deleted entities are harmless and are ignored because exporters enumerate live domain rows; later module adapters may delete them eagerly.
- V1 documents are validated by the frozen v1 pipeline before migration. The migrator never attempts to rescue an invalid v1 reference.
- V1 name references produce `WARNING/LEGACY_REFERENCE_MIGRATED` with the original JSON Pointer and generated key. Ambiguity remains an error from the v1 semantic validator.
- V1 embedded handouts and map image data URLs are decoded only after media/signature checks, written under generated safe staging paths, and replaced by asset references.
- Migration is deterministic: identical v1 bytes and the same checked-in catalog snapshot produce byte-equivalent canonical v2 JSON and identical asset descriptors.
- No original file is modified. Migration output exists only in the staging directory and retained preview.
- The v2 schema intentionally exposes only the current executable persistence surface. Adding a new section requires a schema/DTO/semantic/persistence change in one commit; this preserves the rule that schema-valid input imports.
- V2 foundation exports are more honest but still incomplete recovery artifacts because they declare exclusions. Delivery item 4 is responsible for making those exclusions disappear and for semantic deep comparison across all persistent fields.

---

### Task 1: Define the closed v2 DTO, schemas, and executable fixtures

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/ContentReference.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/AssetDescriptor.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SchemaValidator.java`
- Create: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Create: `src/main/resources/schemas/map-document-v2.schema.json`
- Create: `src/test/resources/campaigns/v2/minimal.dmcampaign.json`
- Create: `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java`

**Interfaces:**
- Produces: `CampaignManifestV2.CURRENT_FORMAT_VERSION == 2`.
- Produces: `ContentReference.packageRef(CampaignContentType, String)` and `ContentReference.catalogRef(CampaignContentType, String ruleset, String sourceKey)`.
- Produces: schema IDs `https://dmhelper/campaign-format-v2.schema.json` and `https://dmhelper/map-document-v2.schema.json`.
- Produces: `List<CampaignImportProblem> CampaignManifestV2SchemaValidator.validate(String json)`.
- Consumes: the exact v1 scalar/nested field shapes documented by `CampaignExportDto`; no persistence is added in this task.

- [ ] **Step 1: Write the failing schema/DTO contract test**

Create `CampaignManifestV2ContractTest` with these tests, using a real `CampaignManifestV2SchemaValidator` configured with both classpath schemas:

```java
@Test
void minimalFixtureValidatesAndDeserializes() throws Exception {
    String json = fixture("campaigns/v2/minimal.dmcampaign.json");
    assertThat(schema.validate(json)).isEmpty();
    CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);
    assertThat(manifest.formatVersion()).isEqualTo(2);
    assertThat(manifest.campaign().key()).isEqualTo("campaign-minimal");
}

@Test
void serializedCurrentSurfaceDtoValidates() throws Exception {
    CampaignManifestV2 manifest = mapper.readValue(
            fixture("campaigns/v2/current-surface.dmcampaign/manifest.json"),
            CampaignManifestV2.class);
    assertThat(schema.validate(mapper.writeValueAsString(manifest))).isEmpty();
}

@Test
void everyEntityKeyUsesThePackageKeyDefinition() throws Exception {
    JsonNode root = mapper.readTree(fixture("campaigns/v2/current-surface.dmcampaign/manifest.json"));
    List<String> keys = collectEntityKeys(root);
    assertThat(keys).isNotEmpty().allMatch(k -> k.matches("^[a-z0-9][a-z0-9._-]{0,99}$"));
}

@Test
void packageAndCatalogReferenceBranchesAreClosed() {
    assertThat(schema.validate(minimalWithReference("""
            {"scope":"PACKAGE","type":"MAP","key":"crypt","sourceKey":"not-allowed"}
            """))).extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
    assertThat(schema.validate(minimalWithReference("""
            {"scope":"CATALOG","type":"SPELL","ruleset":"SRD_5_2","sourceKey":"fireball","key":"not-allowed"}
            """))).extracting(CampaignImportProblem::code).contains("SCHEMA_VIOLATION");
}

@Test
void v2MapImageRejectsDataUrls() {
    String json = currentSurfaceManifest().replace(
            "\"assetRef\":\"asset-crypt-map\"",
            "\"dataUrl\":\"data:image/png;base64,AAAA\"");
    assertThat(schema.validate(json)).isNotEmpty();
}
```

- [ ] **Step 2: Run the contract test and verify the v2 types are absent**

Run:

```bash
./mvnw -Dtest=CampaignManifestV2ContractTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL during test compilation because the v2 model and validator do not exist.

- [ ] **Step 3: Add the exact content-type and reference union**

Create `CampaignContentType` with this initial vocabulary:

```java
public enum CampaignContentType {
    CAMPAIGN,
    ADVENTURE, CHAPTER, SCENE,
    MAP, TOKEN,
    ENCOUNTER, COMBATANT,
    PARTY_MEMBER, CHARACTER_SHEET, SHEET_RESOURCE,
    NOTE, QUICK_NOTE, HANDOUT,
    ASSIGNMENT, LEDGER_ENTRY, TIMELINE_EVENT,
    STATBLOCK, SPELL, CONDITION, RULE, EQUIPMENT_ITEM, MAGIC_ITEM,
    CLASS, SPECIES, BACKGROUND, FEAT
}
```

Create the closed union:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentReference(
        Scope scope,
        CampaignContentType type,
        String key,
        String ruleset,
        String sourceKey
) {
    public enum Scope { PACKAGE, CATALOG }

    public static ContentReference packageRef(CampaignContentType type, String key) {
        return new ContentReference(Scope.PACKAGE, type, key, null, null);
    }

    public static ContentReference catalogRef(
            CampaignContentType type, String ruleset, String sourceKey) {
        return new ContentReference(Scope.CATALOG, type, null, ruleset, sourceKey);
    }
}
```

Create `AssetDescriptor` as:

```java
public record AssetDescriptor(
        String key,
        String path,
        String mediaType,
        long sizeBytes,
        String sha256,
        String originalName
) {}
```

- [ ] **Step 4: Add `CampaignManifestV2` using the manifest field map**

Copy the current scalar value fields from the nested records in `CampaignExportDto`, then apply every row in **Manifest v2 Field Map**. Use this exact top-level record and metadata contract:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CampaignManifestV2(
        int formatVersion,
        Metadata metadata,
        CampaignDto campaign,
        List<AssetDescriptor> assets,
        List<PartyMemberDto> party,
        List<StatBlockDto> customStatBlocks,
        List<HandoutDto> handouts,
        List<MapDto> maps,
        List<EncounterDto> encounters,
        List<NoteDto> notes,
        List<QuickNoteDto> quickNotes,
        List<AssignmentDto> assignments,
        List<LedgerEntryDto> ledgerEntries,
        List<TimelineEventDto> timelineEvents,
        List<AdventureDto> adventures
) {
    public static final int CURRENT_FORMAT_VERSION = 2;

    public record Metadata(
            String packageKey,
            Instant createdAt,
            String generator,
            String catalogVersion,
            String catalogSha256,
            List<String> exclusions
    ) {}
}
```

Use required `String key` as the first component of every entity record. Use `ContentReference` for every reference named in the field map. Use `String assetRef` in `HandoutDto` and v2 map `ImageDto`. Retain all non-reference value fields from v1 without renaming, except the top-level renames explicitly listed in the field map.

- [ ] **Step 5: Add closed draft-2020-12 schemas**

Create `campaign-format-v2.schema.json` with:

- required top-level properties matching the record constructor exactly;
- `additionalProperties: false` on every known object;
- `$defs.key` with the exact key regex and maximum length 100;
- `$defs.packageReference` and `$defs.catalogReference` joined by `oneOf`;
- `const: 2` for `formatVersion`;
- a required `metadata.exclusions` array of unique strings;
- all current scalar ranges/enums from the repaired v1 schema;
- the exact reference and asset replacements in **Manifest v2 Field Map**;
- `$ref: "https://dmhelper/map-document-v2.schema.json"` for map documents;
- no `imageData`, `dataUrl`, name-reference, UUID-reference, or open `object` payload in the v2 contract.

Create `map-document-v2.schema.json` by retaining v1 grid, layer, cell, shape, primitive, and terrain units, changing only image to this closed shape:

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["assetRef", "x", "y", "width", "height"],
  "properties": {
    "assetRef": { "$ref": "https://dmhelper/campaign-format-v2.schema.json#/$defs/key" },
    "x": { "type": "number" },
    "y": { "type": "number" },
    "width": { "type": "number", "exclusiveMinimum": 0 },
    "height": { "type": "number", "exclusiveMinimum": 0 }
  }
}
```

Implement `CampaignManifestV2SchemaValidator` by following the repaired v1 validator's offline networknt registry pattern, registering both v2 schema IDs before compiling the campaign schema and returning `SCHEMA_VIOLATION` problems with JSON Pointer paths.

- [ ] **Step 6: Add the two fixtures**

The minimal fixture must contain every required top-level array as empty, a valid catalog version/hash pair, and all eleven exclusions from **Scope Boundary**. The current-surface manifest must contain at least one of every top-level entity type, one nested sheet/resource, map/token/image, encounter/combatant, adventure/chapter/scene, each reference branch, and the two asset descriptors used by the ZIP fixture.

- [ ] **Step 7: Serve and test both v2 schemas**

Keep `SchemaController`'s safe filename rule. Add tests for:

```text
GET /api/v1/schemas/campaign-format-v2
GET /api/v1/schemas/map-document-v2.schema.json
```

Expected: HTTP 200, `application/schema+json`, and the exact `$id` values above.

- [ ] **Step 8: Run the contract tests**

Run:

```bash
./mvnw -Dtest=CampaignManifestV2ContractTest,SchemaControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: PASS; fixtures deserialize, DTO output validates, union branches are closed, and `dataUrl` is rejected.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SchemaValidator.java src/main/resources/schemas src/test/resources/campaigns/v2 src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java src/test/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaControllerTest.java
git commit -m "feat: define campaign package v2 contract"
```

### Task 2: Persist immutable package-local keys

**Files:**
- Create: `src/main/resources/db/migration/V3__add_campaign_package_keys.sql`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKey.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/PackageKeyGenerator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`

**Interfaces:**
- Produces: `String CampaignPackageKeyService.getOrCreate(UUID campaignId, CampaignContentType type, UUID entityId, String displayName)`.
- Produces: `void CampaignPackageKeyService.bindImported(UUID campaignId, CampaignContentType type, UUID entityId, String packageKey)`.
- Produces: `Optional<String> CampaignPackageKeyService.find(UUID campaignId, CampaignContentType type, UUID entityId)`.

- [ ] **Step 1: Write failing service and migration tests**

Cover these behaviors:

```java
assertThat(keys.getOrCreate(campaignId, MAP, mapId, "The Crypt"))
        .matches("the-crypt-[0-9a-f]{12}")
        .isEqualTo(keys.getOrCreate(campaignId, MAP, mapId, "Renamed Crypt"));

keys.bindImported(campaignId, SCENE, sceneId, "crypt-entry");
assertThat(keys.find(campaignId, SCENE, sceneId)).contains("crypt-entry");

assertThatThrownBy(() -> keys.bindImported(campaignId, SCENE, sceneId, "different-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already bound");

assertThatThrownBy(() -> keys.bindImported(campaignId, SCENE, otherSceneId, "crypt-entry"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already used");
```

Add Flyway assertions for the table and both unique constraints after a fresh migration and after a baselined legacy V1 database upgrades through versions 2 and 3.

- [ ] **Step 2: Run the tests and verify V3 is absent**

Run:

```bash
./mvnw -Dtest=CampaignPackageKeyServiceTest,FlywayMigrationTest,FlywayLegacyUpgradeTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL because the V3 table and key service do not exist.

- [ ] **Step 3: Create the additive migration**

Create this table and indexes in `V3__add_campaign_package_keys.sql`:

```sql
create table campaign_package_key (
    id uuid not null,
    campaign_id uuid not null,
    entity_type varchar(40) not null,
    entity_id uuid not null,
    package_key varchar(100) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_package_key_campaign foreign key (campaign_id)
        references campaign on delete cascade,
    constraint uq_package_key_entity unique (campaign_id, entity_type, entity_id),
    constraint uq_package_key_value unique (campaign_id, entity_type, package_key)
);

create index idx_package_key_campaign_type
    on campaign_package_key (campaign_id, entity_type);
```

- [ ] **Step 4: Implement deterministic generation and immutable binding**

`PackageKeyGenerator.generate(type, displayName, stableIdentity)` must:

1. normalize the name with NFKD;
2. remove combining marks;
3. lowercase with `Locale.ROOT`;
4. replace runs outside `[a-z0-9._-]` with `-`;
5. trim punctuation from both ends;
6. use the lowercased type name when the slug is empty;
7. truncate the slug so `slug + '-' + 12 hex chars` is at most 100 characters;
8. compute the suffix from SHA-256 of `type.name() + ':' + stableIdentity`.

`bindImported` validates the regex before any repository write, returns normally for an identical existing binding, and throws a domain `IllegalArgumentException` for either uniqueness conflict.

- [ ] **Step 5: Run the key and upgrade tests**

Run the command from Step 2.

Expected: PASS; an existing campaign survives the upgrade and V3 is present.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V3__add_campaign_package_keys.sql src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java
git commit -m "feat: persist immutable campaign package keys"
```

### Task 3: Publish the typed catalog and checked-in snapshot

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CatalogSnapshot.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog/CampaignCatalogService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogController.java`
- Create: `src/main/resources/catalog/srd-5.2-catalog.json`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog/CampaignCatalogServiceTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogControllerTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java`

**Interfaces:**
- Produces: `CatalogSnapshot CampaignCatalogService.snapshot()`.
- Produces: `Optional<CatalogSnapshot.Entry> CampaignCatalogService.resolve(CampaignContentType type, String ruleset, String sourceKey)`.
- Produces: `GET /api/v1/catalog` and `GET /api/v1/catalog/snapshot`.

- [ ] **Step 1: Write failing catalog tests**

Assert that:

- entries exist for SRD statblocks, spells, conditions, rules, equipment, magic items, classes, species, backgrounds, and feats;
- every tuple `(type, ruleset, sourceKey)` is unique;
- entries are globally sorted by `type`, then `sourceKey`;
- `ruleset` is exactly `SRD_5_2`, `source` is exactly `SRD`, and `aliases` is an empty list in this milestone;
- `sha256` matches canonical serialization of the `entries` array;
- classpath `catalog/srd-5.2-catalog.json` deep-equals `snapshot()`;
- `/api/v1/catalog` returns typed records rather than strings;
- the legacy `/api/v1/library/srd-keys` endpoint still returns its old response.

- [ ] **Step 2: Run the tests and verify the typed service is absent**

```bash
./mvnw -Dtest=CampaignCatalogServiceTest,CatalogControllerTest,LibraryApiControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL during compilation because `CampaignCatalogService` and `CatalogSnapshot` do not exist.

- [ ] **Step 3: Add the exact snapshot records**

```java
public record CatalogSnapshot(
        String version,
        String sha256,
        List<Entry> entries
) {
    public record Entry(
            CampaignContentType type,
            String sourceKey,
            String name,
            String ruleset,
            String source,
            List<String> aliases
    ) {}
}
```

Set version to `srd-5.2-dmhelper-1`. Assemble entries from the existing repositories, filter statblocks to `Source.SRD`, canonicalize with a dedicated Jackson mapper configured for stable property ordering, and hash only the canonical `entries` JSON bytes.

- [ ] **Step 4: Generate the checked-in snapshot deterministically**

Add a test-only `writeSnapshotWhenRequested` method guarded by system property `dmhelper.writeCatalogSnapshot`. It must seed the in-memory catalog, serialize `snapshot()` with the canonical mapper, and write only to `src/main/resources/catalog/srd-5.2-catalog.json`.

Run:

```bash
./mvnw -Dtest=CampaignCatalogServiceTest#writeSnapshotWhenRequested -Ddmhelper.writeCatalogSnapshot=true test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: PASS and create/update the snapshot. Run it a second time and verify `git diff` does not change.

- [ ] **Step 5: Add typed endpoints and preserve the legacy list**

`GET /api/v1/catalog` returns the full `CatalogSnapshot`. `GET /api/v1/catalog/snapshot` returns the classpath JSON with `application/json` and an `ETag` equal to the quoted SHA-256. Do not exclude these routes from the PIN interceptor.

- [ ] **Step 6: Run the catalog suite**

Run the command from Step 2.

Expected: PASS with snapshot equality and unchanged legacy behavior.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CatalogSnapshot.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogController.java src/main/resources/catalog src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CatalogControllerTest.java src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java
git commit -m "feat: publish typed SRD catalog snapshot"
```

### Task 4: Read and write packages without trusting archive metadata

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageLimits.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageException.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/StagedCampaignPackage.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriteRequest.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/AssetSignatureValidator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageReader.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageReaderTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriterTest.java`

**Interfaces:**
- Produces: `StagedCampaignPackage CampaignPackageReader.read(InputStream input, String originalFilename, String mediaType)`.
- Produces: `void CampaignPackageWriter.write(CampaignPackageWriteRequest request, OutputStream output)`.
- Produces: `AssetSignatureValidator.Result validate(Path file, AssetDescriptor descriptor)`.

- [ ] **Step 1: Write the archive attack matrix**

Create ZIP fixtures in-memory with Commons Compress and assert stable problem codes for:

```text
PACKAGE_TOO_LARGE
TOO_MANY_ENTRIES
MANIFEST_MISSING
DUPLICATE_MANIFEST
UNEXPECTED_ROOT_ENTRY
ABSOLUTE_ASSET_PATH
TRAVERSAL_ASSET_PATH
BACKSLASH_ASSET_PATH
SYMLINK_ASSET
ENCRYPTED_ENTRY
DUPLICATE_NORMALIZED_PATH
PATH_TOO_LONG
ASSET_TOO_LARGE
PACKAGE_EXPANDED_TOO_LARGE
COMPRESSION_RATIO_EXCEEDED
UNSUPPORTED_MEDIA_TYPE
ASSET_EXTENSION_MISMATCH
ASSET_SIGNATURE_MISMATCH
ASSET_DIGEST_MISMATCH
```

Also assert a valid ZIP and valid asset-free JSON stage successfully, and closing `StagedCampaignPackage` recursively deletes only its owned random directory.

- [ ] **Step 2: Run tests and verify archive support is absent**

```bash
./mvnw -Dtest=CampaignPackageReaderTest,CampaignPackageWriterTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL because the IO module does not exist.

- [ ] **Step 3: Add Commons Compress and exact limits**

Add `org.apache.commons:commons-compress` without a version so Spring Boot's BOM controls it. Define:

```java
public record CampaignPackageLimits(
        long maxUploadBytes,
        int maxEntries,
        long maxManifestBytes,
        long maxAssetBytes,
        long maxExpandedBytes,
        double maxCompressionRatio,
        int maxNormalizedPathLength
) {
    public static CampaignPackageLimits defaults() {
        return new CampaignPackageLimits(
                1_073_741_824L, 2_000, 10_485_760L,
                104_857_600L, 1_073_741_824L, 100.0d, 240);
    }
}
```

- [ ] **Step 4: Implement safe staging**

Stream the request to `${user.home}/.dmhelper/import-staging/<random UUID>/upload` through a bounded copy. For ZIP input, use `ZipFile` to inspect central-directory metadata before extraction, normalize and validate all names before writing any entry, then extract with `CREATE_NEW` and `NOFOLLOW_LINKS`. Record actual expanded bytes while copying and re-check sizes/ratios against actual counts.

`StagedCampaignPackage` exposes only:

```java
public enum ContainerKind { V1_JSON, V2_JSON, V2_ZIP }
public Path stagingDirectory();
public Path manifestPath();
public Map<String, Path> assetsByNormalizedPath();
public long uploadedBytes();
public long expandedBytes();
public void close();
```

Never expose an arbitrary caller-supplied path resolution method.

Represent a rejected container/asset as:

```java
public final class CampaignPackageException extends IllegalArgumentException {
    private final CampaignImportProblem problem;

    public CampaignPackageException(CampaignImportProblem problem) {
        super(problem.message());
        this.problem = problem;
    }

    public CampaignImportProblem problem() { return problem; }
}
```

Define the writer input independently of the later export coordinator:

```java
public record CampaignPackageWriteRequest(
        String filename,
        CampaignManifestV2 manifest,
        Map<String, InputStreamSource> assetSources
) {}
```

- [ ] **Step 5: Implement signature validation**

Use exact signatures:

- PNG: `89 50 4E 47 0D 0A 1A 0A`;
- JPEG: begins `FF D8 FF` and ends `FF D9`;
- GIF: `GIF87a` or `GIF89a`;
- WebP: `RIFF` bytes 0–3 and `WEBP` bytes 8–11.

Map media types to extensions `.png`, `.jpg`/`.jpeg`, `.gif`, and `.webp`. Compare lowercase SHA-256 and descriptor byte count to actual bytes.

- [ ] **Step 6: Implement deterministic writing**

For asset-free artifacts write canonical manifest JSON directly. For asset-bearing artifacts write ZIP entries in this order: `manifest.json`, then assets sorted by normalized path. Set every ZIP entry timestamp to `0L`, stream each source, and fail export if size/digest differs from its descriptor.

- [ ] **Step 7: Run the IO tests**

Run the command from Step 2.

Expected: PASS for both valid containers and every named attack case; staging roots are empty after each test.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io
git commit -m "feat: stage campaign packages safely"
```

### Task 5: Migrate validated v1 documents deterministically

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/CampaignFormatMigration.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/CampaignMigrationResult.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/FormatMigrationRegistry.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java`

**Interfaces:**
- Produces: `CampaignMigrationResult FormatMigrationRegistry.toCurrent(StagedCampaignPackage source)`.
- Consumes: `CampaignImportValidator.validate(String)` for v1 before any mapping.
- Produces: canonical v2 manifest, staged asset descriptors/paths, structured warnings, and migration labels.

- [ ] **Step 1: Write failing migration tests**

Test all of these with `feature-complete.dmcampaign.json`:

- two migrations of identical bytes serialize to identical canonical v2 JSON;
- every entity and nested entity has a valid unique key within its type;
- map, encounter, token, combatant, scene, quick-note, ledger, timeline, assignment, sheet, and catalog references point to the generated key/type tuple;
- v1 handout `imageData` becomes `assets/handouts/<asset-key>.<ext>`;
- map image `dataUrl` becomes `assets/maps/<asset-key>.<ext>`;
- no serialized v2 manifest contains `data:` or `imageData`;
- warnings identify every migrated name-based reference by original JSON Pointer;
- invalid v1 input returns its existing v1 errors and no v2 output;
- a source already at version 2 is returned without a migration label.

- [ ] **Step 2: Run the migration test**

```bash
./mvnw -Dtest=LegacyV1ToV2MigrationTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL because the migration registry is absent.

- [ ] **Step 3: Define the migration contracts**

```java
public interface CampaignFormatMigration {
    int sourceVersion();
    CampaignMigrationResult migrate(StagedCampaignPackage source);
}

public record CampaignMigrationResult(
        CampaignManifestV2 manifest,
        Map<String, Path> assetsByKey,
        List<CampaignImportProblem> problems,
        List<String> migrations
) {}
```

`FormatMigrationRegistry` must reject an absent, non-integer, less-than-1, or greater-than-2 `formatVersion` with `UNSUPPORTED_FORMAT_VERSION`.

- [ ] **Step 4: Implement deterministic v1 keys and references**

Use JSON Pointer as the stable identity for v1 records without an ID, and use the v1 UUID/key when present. Build all entity-key indexes before mapping any reference. Resolve references using the same uniqueness rules already enforced by `CampaignSemanticValidator`; never select the first display-name match.

Emit `LEGACY_REFERENCE_MIGRATED` once per migrated reference path and `MIGRATED_FROM_V1` once at `/formatVersion`. Copy the exact eleven exclusions into metadata.

- [ ] **Step 5: Extract embedded assets during migration**

Decode only schema-accepted image media types. Validate signature before writing. Name staging files from the generated asset key, never v1 `fileName`. Compute descriptor size/digest from decoded bytes and use `CREATE_NEW`.

- [ ] **Step 6: Run the migration tests**

Run the command from Step 2.

Expected: PASS; repeated canonical output is byte-identical and contains no binary data URL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration
git commit -m "feat: migrate campaign v1 documents to v2"
```

### Task 6: Build the v2 semantic pipeline and retained preview

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SchemaValidator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationResult.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipeline.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreview.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/PendingCampaignImport.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStore.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignPreviewBuilder.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipelineTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStoreTest.java`

**Interfaces:**
- Produces: `CampaignPackageValidationResult CampaignPackageValidationPipeline.validate(StagedCampaignPackage)`.
- Produces: `CampaignImportPreview CampaignImportPreviewStore.retain(CampaignPackageValidationResult)`.
- Produces: `PendingCampaignImport CampaignImportPreviewStore.require(UUID previewId)` and `void discard(UUID previewId)`.

- [ ] **Step 1: Write failing ordered-pipeline tests**

Assert this order by mocks and exact problem codes:

```text
container safety
→ source schema/semantic validation
→ compatibility migration
→ v2 schema/deserialization
→ key/reference uniqueness
→ typed catalog resolution
→ spatial/state validation
→ asset descriptor/file validation
→ preview construction/retention
```

Earlier-stage errors prevent later calls. V1 migration warnings survive into the preview. A v2 catalog-hash mismatch yields `WARNING/CATALOG_SNAPSHOT_MISMATCH`; an unresolved catalog tuple yields `ERROR/UNRESOLVED_CATALOG_REFERENCE`.

- [ ] **Step 2: Write failing preview lifecycle tests**

Use a mutable `Clock` to prove:

- `READY` and `CONFIRM_WARNINGS` previews receive IDs and expiry timestamps;
- `BLOCKED` results have `previewId == null` and immediately close staging;
- `require` after 30 minutes throws `NotFoundException` and deletes staging;
- discard is idempotent and deletes staging;
- startup cleanup removes abandoned child directories but never the configured staging root.

- [ ] **Step 3: Run the tests**

```bash
./mvnw -Dtest=CampaignPackageValidationPipelineTest,CampaignImportPreviewStoreTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL because the pipeline and preview store do not exist.

- [ ] **Step 4: Implement schema and semantic validation**

Reuse `CampaignManifestV2SchemaValidator` from Task 1. Semantic validation must cover:

- key uniqueness per type and all package references;
- exact catalog tuple resolution;
- asset key/path uniqueness and descriptor-to-staged-file agreement;
- token pixel bounds, map primitive/pin bounds, and document/grid agreement;
- encounter status, one active encounter, active-turn indices, and token ownership;
- party/sheet class level totals and resource current/max uses;
- assignment holder/item rules and attunement warning;
- scene, quick-note, timeline-note, and ledger-assignment targets;
- handout/map image media type suitability;
- exclusion names from the allowed foundation exclusion vocabulary.

Use JSON Pointers and the existing `CampaignImportProblem` record for every result.

Return the canonical retained inputs through this exact result:

```java
public record CampaignPackageValidationResult(
        StagedCampaignPackage stagedPackage,
        CampaignManifestV2 manifest,
        int sourceFormatVersion,
        Map<String, Path> assetsByKey,
        List<CampaignImportProblem> problems,
        List<String> migrations
) {
    public boolean valid() {
        return problems.stream().noneMatch(p -> p.severity() == ImportSeverity.ERROR);
    }
}
```

- [ ] **Step 5: Implement typed preview records**

```java
public record CampaignEntityCounts(
        int partyMembers, int customStatBlocks, int handouts, int maps, int tokens,
        int encounters, int combatants, int notes, int quickNotes, int assignments,
        int ledgerEntries, int timelineEvents, int adventures, int chapters, int scenes,
        int assets
) {}

public record CampaignImportPreview(
        UUID previewId,
        String status,
        int sourceFormatVersion,
        int targetFormatVersion,
        CampaignEntityCounts counts,
        long packageSizeBytes,
        long installedSizeBytes,
        int provenanceEntries,
        int missingProvenanceEntries,
        List<String> exclusions,
        List<String> migrations,
        List<CampaignImportProblem> problems,
        Instant expiresAt
) {}
```

Statuses are exactly `BLOCKED`, `READY`, and `CONFIRM_WARNINGS`.

- [ ] **Step 6: Implement preview retention and cleanup**

Use `ConcurrentHashMap<UUID, PendingCampaignImport>`, injected `Clock`, and an injected staging root. `@PostConstruct` cleans abandoned child directories. Every public method first expires stale entries. Only the store closes a retained package.

- [ ] **Step 7: Run the pipeline/preview tests**

Run the command from Step 3.

Expected: PASS with exact stage short-circuiting and zero staging residue.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview
git commit -m "feat: preview validated campaign packages"
```

### Task 7: Confirm imports atomically and preserve supplied keys

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPersistenceReceipt.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/HandoutImportSource.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/PreparedV1Import.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/V2CompatibilityAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/AssetInstaller.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java`

**Interfaces:**
- Produces: `CampaignPersistenceReceipt CampaignService.persistValidatedV1(CampaignExportDto dto)` and `CampaignPersistenceReceipt CampaignService.persistValidatedV1(CampaignExportDto dto, Map<String, HandoutImportSource> externalHandouts)`.
- Produces: `PreparedV1Import V2CompatibilityAdapter.prepare(PendingCampaignImport pending)`.
- Produces: `Campaign CampaignImportCoordinator.confirm(UUID previewId, boolean acceptWarnings)`.
- Consumes: `CampaignPackageKeyService.bindImported(...)` and validated staged assets.

- [ ] **Step 1: Write failing persistence-receipt tests**

Refactor expectations around exact input pointers. A feature v1 import receipt must include local UUIDs for:

```text
/campaign
/party/0
/party/0/sheet
/party/0/sheet/resources/0
/statBlocks/0
/handouts/0
/maps/0
/maps/0/tokens/0
/encounters/0
/encounters/0/combatants/0
/notes/0
/quicknotes/0
/assignments/0
/ledger/0
/timeline/0
/adventures/0
/adventures/0/chapters/0
/adventures/0/chapters/0/scenes/0
```

The existing `importFromJson(String)` still returns the imported campaign and all existing v1 round-trip tests remain unchanged.

- [ ] **Step 2: Write failing coordinator tests**

Cover:

- warnings plus `acceptWarnings=false` perform zero repository/file writes;
- `acceptWarnings=true` imports and binds every supplied v2 key to the receipt UUID at the matching pointer;
- a key-binding failure rolls back campaign rows and installed handout files;
- an asset read/hash failure before persistence writes nothing;
- a persistence failure after one installed file deletes that file on rollback;
- a committed import removes the preview/staging directory;
- a failed confirmation retains the preview for retry.

- [ ] **Step 3: Run the tests and verify the receipt/coordinator are absent**

```bash
./mvnw -Dtest=CampaignServiceTest,CampaignImportExportRoundTripTest,HandoutServiceTest,CampaignImportCoordinatorTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL during compilation for the new contracts while existing v1 tests still compile.

- [ ] **Step 4: Extract validated v1 persistence without changing its behavior**

Move only the body after `requireImportable()` into:

```java
CampaignPersistenceReceipt persistValidatedV1(CampaignExportDto dto)
```

Define the receipt as:

```java
public record CampaignPersistenceReceipt(
        Campaign campaign,
        Map<String, UUID> entityIdsByPointer
) {
    public UUID requireEntityId(String pointer) {
        UUID id = entityIdsByPointer.get(pointer);
        if (id == null) throw new IllegalStateException("No persisted entity for " + pointer);
        return id;
    }
}
```

Keep `importFromJson` as:

```java
public Campaign importFromJson(String json) {
    CampaignExportDto dto = importValidator.validate(json).requireImportable();
    return persistValidatedV1(dto).campaign();
}
```

Build the receipt as entities are saved using their actual array index and the pointer list from Step 1. Do not re-query by mutable names.

- [ ] **Step 5: Implement the compatibility adapter**

`PreparedV1Import` contains:

```java
public record PreparedV1Import(
        CampaignExportDto dto,
        Map<String, ImportedKey> importedKeysByV1Pointer,
        Map<String, HandoutImportSource> externalHandoutsByV1Pointer
) {
    public record ImportedKey(CampaignContentType type, String key) {}
}

public record HandoutImportSource(
        String originalDisplayName,
        String contentType,
        InputStreamSource content,
        long expectedSize,
        String expectedSha256
) {}
```

Convert typed v2 references back to the unique v1 values expected by current persistence, but retain key bindings separately by exact pointer. Convert validated map image assets to runtime data URLs. Set compatibility handout `imageData` to `null` only when the same `/handouts/{index}` pointer exists in `externalHandoutsByV1Pointer`; `persistValidatedV1(dto, externalHandouts)` must reject either a missing external source or an unpaired source. Do not reconstruct an untrusted filename.

- [ ] **Step 6: Add staged stream installation**

Add to `HandoutService`:

```java
public Handout createImported(
        UUID campaignId,
        String title,
        String tags,
        String originalDisplayName,
        String contentType,
        InputStreamSource content,
        long expectedSize,
        String expectedSha256) throws IOException
```

Also add `InputStreamSource getFileSource(UUID id)` for bounded v2 export; it returns a `FileSystemResource` for the internally generated storage file after the same existence check as `getFileContent`.

Generate the final UUID storage name, stream with `CREATE_NEW`, recompute size/digest, delete immediately on mismatch, and keep the existing transaction-synchronization rollback cleanup. Retain the byte-array overload as a delegating v1 compatibility method.

- [ ] **Step 7: Implement the confirmation transaction**

`CampaignImportCoordinator.confirm` performs exactly:

1. load retained preview;
2. reject errors and unaccepted warnings;
3. verify every staged asset again;
4. prepare the v1 compatibility payload;
5. call `persistValidatedV1(prepared.dto(), prepared.externalHandoutsByV1Pointer())` in the coordinator transaction;
6. bind all supplied keys using receipt pointers;
7. register preview cleanup after commit;
8. return the created campaign.

If any step throws, transaction rollback and handout synchronization remove final writes; the preview remains retained.

- [ ] **Step 8: Run coordinator and frozen-v1 tests**

Run the command from Step 3.

Expected: PASS; both atomic v2 confirmation and the full existing v1 contract remain green.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java
git commit -m "feat: confirm campaign package imports atomically"
```

### Task 8: Export the current campaign surface as canonical v2

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageArtifact.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportCoordinator.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java`

**Interfaces:**
- Produces: `CampaignPackageArtifact CampaignExportCoordinator.export(UUID campaignId)`.
- Produces: `CampaignPackageArtifact` with filename, media type, manifest, and `Map<String, InputStreamSource>` keyed by asset key.
- Consumes: current domain repositories, `CampaignPackageKeyService`, and `CampaignCatalogService.snapshot()`.

- [ ] **Step 1: Write failing export tests**

Create one asset-free campaign and one campaign with a handout and map image. Assert:

- asset-free filename ends `.dmcampaign.json` and media type is `application/json`;
- asset-bearing filename ends `.dmcampaign` and media type is `application/vnd.dmhelper.campaign+zip`;
- two exports of unchanged data preserve every package key;
- renaming a map between exports changes `name` but not `key`;
- every package reference resolves in the emitted manifest;
- every catalog reference resolves against the recorded snapshot;
- handout bytes and map image bytes occur only as ZIP assets;
- metadata contains the exact eleven exclusions;
- export never writes to the database except creating previously absent key bindings;
- the exported artifact passes the package reader and v2 validation pipeline.

- [ ] **Step 2: Run the export tests**

```bash
./mvnw -Dtest=CampaignPackageWriterTest,CampaignPackageV2IntegrationTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL because the export coordinator does not exist.

- [ ] **Step 3: Add artifact and coordinator contracts**

```java
public record CampaignPackageArtifact(
        String filename,
        MediaType mediaType,
        CampaignManifestV2 manifest,
        Map<String, InputStreamSource> assetSources
) {
    public boolean zipped() { return !assetSources.isEmpty(); }
    public CampaignPackageWriteRequest writeRequest() {
        return new CampaignPackageWriteRequest(filename, manifest, assetSources);
    }
}
```

The coordinator enumerates entities in the same deterministic order as v1 export, obtains/stores a key for every entity, builds all reference indexes before DTO mapping, and throws on any unresolved local relationship.

- [ ] **Step 4: Externalize current image assets**

Handouts use their file content through a bounded `InputStreamSource`. Map image layers decode the existing data URL once, validate its signature, and become asset sources. Deduplicate identical bytes only when media type and SHA-256 are identical; each manifest asset key still maps deterministically.

- [ ] **Step 5: Record catalog and exclusions**

Set metadata catalog version/hash from `CampaignCatalogService.snapshot()`, generator to `DMHelper/0.0.1-SNAPSHOT`, and exclusions to the exact ordered list in **Scope Boundary**.

- [ ] **Step 6: Run the export/integration tests**

Run the command from Step 2.

Expected: PASS; both container choices re-enter the validation pipeline and keys survive renames.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageArtifact.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportCoordinator.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/io/CampaignPackageWriterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java
git commit -m "feat: export canonical campaign v2 packages"
```

### Task 9: Add streaming preview, warning confirmation, and v2 export UI

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageControllerTest.java`
- Create: `src/main/resources/static/js/campaign-import.js`
- Create: `src/main/resources/templates/campaigns/_import-dialog.html`
- Modify: `src/main/resources/templates/campaigns/list.html`
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Produces: `POST /campaigns/package-imports/previews` with raw JSON/ZIP body.
- Produces: `POST /campaigns/package-imports/{previewId}/confirm?acceptWarnings={boolean}`.
- Produces: `DELETE /campaigns/package-imports/{previewId}`.
- Produces: `GET /campaigns/{campaignId}/package` for canonical v2 export.
- Retains: `GET /campaigns/{campaignId}/export` as explicit legacy v1 JSON export.

- [ ] **Step 1: Write failing controller tests**

Assert:

- preview accepts `application/json` and `application/vnd.dmhelper.campaign+zip` raw bodies plus `X-DMHelper-Filename`;
- absent/unsupported media type and missing filename return safe HTTP 400 problem details;
- blocked validation returns HTTP 200 with no preview ID;
- warning preview returns `CONFIRM_WARNINGS`;
- confirmation without acceptance returns HTTP 400 and leaves preview retained;
- accepted confirmation returns HTTP 201 with `Location: /campaigns/{id}`;
- discard returns HTTP 204 and is idempotent;
- v2 export selects JSON or ZIP content type and filename from the artifact;
- no response exposes staging or final filesystem paths.

- [ ] **Step 2: Run controller tests**

```bash
./mvnw -Dtest=CampaignPackageControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: FAIL because the controller is absent.

- [ ] **Step 3: Implement streaming endpoints**

Read `HttpServletRequest.getInputStream()` directly through `CampaignPackageReader`; do not accept `MultipartFile` on the v2 preview route. Use `StreamingResponseBody` for ZIP export. Map validation results to the preview record and use safe `ProblemDetail` responses for request/confirmation errors.

- [ ] **Step 4: Add the accessible import dialog**

The dialog must include:

- file picker accepting `.dmcampaign,.dmcampaign.json`;
- upload/progress state;
- counts and package/installed size;
- migrations and exclusions;
- grouped ERROR/WARNING/INFO problems with path, message, and suggestion;
- warning acceptance checkbox shown only for `CONFIRM_WARNINGS`;
- disabled confirm button for `BLOCKED` or unaccepted warnings;
- discard/cancel that calls DELETE;
- retained file/error state for retryable network failures.

Use `aria-live="polite"`, visible focus, a labelled dialog, and restore focus to the import button on close.

- [ ] **Step 5: Implement browser behavior without multipart**

`campaign-import.js` sends the selected `File` as the fetch body, sets its media type based on extension, includes `X-DMHelper-Filename`, renders the typed preview, and redirects to the returned `Location` only after successful confirmation. Use `window.dmToast` for actionable failures and never clear the selected file on failure.

- [ ] **Step 6: Make v2 the primary UI path**

On campaign list and detail, label the actions exactly:

```text
Import Campaign Package
Export Campaign Package
Export Legacy v1 JSON
```

The v1 export remains available but visually secondary. Remove the current auto-submit v1 file form from the detail page.

- [ ] **Step 7: Add guarded browser smoke coverage**

Use `BrowserFailureCollector` and exercise:

1. open import dialog;
2. preview the migrated v1 feature fixture;
3. verify warning confirmation is required;
4. accept warnings and import;
5. land on the new campaign;
6. request v2 export;
7. assert no console/page/request/HTTP failures.

- [ ] **Step 8: Run web tests**

```bash
./mvnw -Dtest=CampaignPackageControllerTest,CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: PASS with no malformed requests or browser errors.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageController.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageControllerTest.java src/main/resources/static/js/campaign-import.js src/main/resources/templates/campaigns src/main/resources/templates/fragments/head.html src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat: preview and confirm campaign package imports"
```

### Task 10: Prove the foundation end to end and publish the contract

**Files:**
- Create: `src/test/resources/campaigns/v2/current-surface.dmcampaign/assets/handouts/players-map.png`
- Create: `src/test/resources/campaigns/v2/current-surface.dmcampaign/assets/maps/crypt-map.webp`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java`
- Create: `docs/campaign-format-v2.md`
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`

**Interfaces:**
- Verifies: reader → v1/v2 validation → migration → v2 validation → preview → warning confirmation → atomic import → v2 export → second preview.
- Documents: only behavior proved by Tasks 1–9.

- [ ] **Step 1: Add the current-surface package fixture**

Build the checked-in ZIP fixture during the test from the manifest directory and the two tiny valid image files. Do not check in a binary ZIP whose contents cannot be reviewed; the writer test constructs canonical `.dmcampaign` bytes and reopens them.

- [ ] **Step 2: Add the foundation round-trip test**

The test must prove:

```text
v1 feature fixture
→ frozen v1 validation
→ deterministic v2 migration
→ warning preview
→ confirmed import
→ canonical v2 export
→ safe reader
→ v2 schema/semantic/asset validation
→ READY second preview
```

Compare all fields represented by `CampaignManifestV2` after normalizing metadata timestamps and generated local storage names. Separately assert that the eleven exclusions remain exactly present.

- [ ] **Step 3: Run the focused package-v2 suite**

```bash
./mvnw -Dtest=CampaignManifestV2ContractTest,CampaignPackageKeyServiceTest,CampaignCatalogServiceTest,CatalogControllerTest,CampaignPackageReaderTest,CampaignPackageWriterTest,LegacyV1ToV2MigrationTest,CampaignPackageValidationPipelineTest,CampaignImportPreviewStoreTest,CampaignImportCoordinatorTest,CampaignPackageV2IntegrationTest,CampaignPackageControllerTest,SchemaControllerTest,CampaignServiceTest,CampaignImportExportRoundTripTest,HandoutServiceTest,FlywayMigrationTest,FlywayLegacyUpgradeTest,CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 4: Write the v2 authoring reference**

`docs/campaign-format-v2.md` must include:

- ZIP and JSON container decision;
- exact safety limits and allowed asset types;
- key regex, uniqueness, immutability, and generation notes;
- package/catalog reference union examples;
- full schema and live catalog URLs;
- catalog version/hash verification;
- validation pipeline and complete problem-code table;
- preview statuses and confirmation protocol;
- v1 migration behavior and warning meanings;
- coordinate and size units;
- atomicity and cleanup guarantees;
- exact foundation exclusions and the complete-round-trip boundary;
- curl examples that use raw `--data-binary`, not multipart, for v2 preview;
- minimal JSON and generated current-surface ZIP fixture locations.

- [ ] **Step 5: Update the master-spec implementation status honestly**

Record completion of delivery item 3 only: container safety, key registry, current-surface schema, typed catalog, v1 migration, preview/confirmation, and atomic staged assets. Keep delivery item 4 and every exclusion open. Do not change the all-in-one readiness definition.

- [ ] **Step 6: Run the complete suite**

```bash
./mvnw test -DargLine=-Duser.home=/tmp/dmhelper-p1-v2
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 7: Inspect generated and filesystem state**

Run:

```bash
find /tmp/dmhelper-p1-v2/.dmhelper/import-staging -mindepth 1 -print
git diff --check
git status --short
```

Expected: `find` prints nothing or the staging directory does not exist; `git diff --check` exits 0; status contains only the intended documentation/fixture changes after Tasks 1–9 have been committed.

- [ ] **Step 8: Commit the verified foundation record**

```bash
git add src/test/resources/campaigns/v2 src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java docs/campaign-format-v2.md docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md
git commit -m "docs: publish campaign package v2 foundation"
```

## Completion Gate

This plan is complete only when:

- v1 JSON schema, DTO, dry-run, direct import, export, and round-trip tests remain green;
- both v2 schemas validate against draft 2020-12 offline and resolve each other without network access;
- every schema-valid v2 fixture deserializes and every serialized v2 DTO validates;
- every represented campaign-owned entity has a valid immutable package-local key;
- package and catalog reference union branches are closed and all references resolve exactly;
- the typed catalog is unique, sorted, versioned, hashed, checked in, served live, and reproducible;
- asset-free packages use JSON and asset-bearing packages use ZIP;
- no v2 manifest contains base64 image data;
- traversal, absolute paths, backslashes, symlinks, encrypted entries, normalized duplicates, bombs, MIME spoofing, digest mismatches, and configured size/count limits are covered by passing tests;
- v1 migration is deterministic and reports every name-reference fallback as a warning;
- preview performs no persistence/final installation and reports counts, sizes, provenance, exclusions, migrations, and structured problems;
- warnings cannot be confirmed accidentally;
- persistence, package-key binding, and final asset installation commit or roll back together;
- failed/expired/discarded/successful imports leave no orphan staging data;
- raw streaming upload supports packages beyond the former multipart limit without raising a global request limit;
- current-surface v1 → v2 migration → import → v2 export → second preview preserves every represented v2 field;
- the exact eleven incomplete-round-trip exclusions remain visible in exported metadata and documentation;
- new routes remain DM/PIN protected and no player payload changes;
- focused tests and the complete Maven suite report zero failures and zero errors;
- the master spec records package-v2-foundation completion without claiming complete round-trip or all-in-one readiness.

## Next Plan Boundary

After this completion gate, write the separate **Complete Round-trip** design/implementation plan from master-spec delivery item 4. It must replace `V2CompatibilityAdapter` with module-owned import/export adapters, add every currently excluded persistent field, add optional combat-log/dice-history export flags whose exclusions are recorded, and maintain the three flagship semantic-deep-compare fixtures. Do not start the session cockpit until that round-trip gate is complete.
