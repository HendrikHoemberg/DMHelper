# Workstream C — Campaign Operational Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure, explain and repair a campaign's *operational* readiness (distinct from schema validity) on both the import preview and the campaign home, driven by one rule engine over two data sources.

**Architecture:** Add an `AssetKind` axis to handouts and optional package signals (asset kind, per-scene map requirement, converter-declared omissions) that import honors with conservative defaults. A pure `CampaignReadinessService` computes a `CampaignReadinessReport` from a normalized `ReadinessInputs`, assembled either from persisted repositories (campaign home) or from a parsed `CampaignManifestV2` (import preview). Each readiness item is `RESOLVED`, `BLOCKER` or `ACCEPTED`; the campaign is "not session-ready" while any `BLOCKER` remains. Repair is deep-linked to existing preparation surfaces (A1 seeding, A2 handout review, scene/map edit); C adds only two gap controls: assign an asset kind (which includes marking a map non-playable via `DM_REFERENCE`) and accept an item.

**Tech Stack:** Java 21, Spring Boot (MVC + Thymeleaf + htmx/Alpine), Spring Data JPA/Hibernate, H2, Flyway, JUnit 5, Maven (`./mvnw`).

## Global Constraints

- Schema is owned by **Flyway** (`src/main/resources/db/migration/`); `spring.jpa.hibernate.ddl-auto=validate`. Every entity change ships a matching `V<n>__*.sql`. Latest existing migration is `V22`; C uses `V23`, `V24`, `V25` in that order.
- Production sets `spring.jpa.open-in-view=false`. All readiness assembly runs inside an explicit `@Transactional(readOnly = true)` boundary with **no lazy access during template rendering** (A1 rule).
- Package format version constant is `CampaignManifestV2.CURRENT_FORMAT_VERSION`, currently `2`. C bumps it to `3`. Reading a `formatVersion < 3` package MUST succeed and apply conservative defaults.
- Manifest DTOs are Java records with `@JsonInclude(JsonInclude.Include.NON_NULL)`. New fields are appended and given a **secondary constructor with the prior parameter list** so existing construction sites keep compiling.
- Conservative defaults: unknown `AssetKind` = `SOURCE_PAGE`; unknown safety = `UNREVIEWED` (existing); absent `mapRequirement` = inferred; absent omissions = empty.
- "Hostile scene" = a scene with at least one `SceneParticipant` whose `disposition == SceneParticipantDisposition.HOSTILE`.
- No copyrighted content in committed fixtures — counts, kinds and population booleans only.
- Test command: `./mvnw -q test -Dtest=<ClassName>` (single method: `-Dtest=<ClassName>#<method>`). Full build: `./mvnw -q verify`.
- Commit after every task. Never mark a task complete with failing tests.

---

## File Structure

**New package `dev.hendrikhoemberg.dmhelper.campaign.readiness`:**
- `ReadinessState.java` — enum `RESOLVED, BLOCKER, ACCEPTED`.
- `ReadinessCategory.java` — enum, the seven §8.1 categories.
- `ReadinessRepairKind.java` — enum describing how an item is repaired (routing hint).
- `ReadinessItem.java` — record: one report line (category, state, key, title, detail, repairKind, targetId).
- `CampaignReadinessReport.java` — record: label + items + derived `sessionReady()` / counts.
- `ReadinessInputs.java` — normalized, source-agnostic snapshot the engine reasons over.
- `CampaignReadinessService.java` — pure rule engine: `ReadinessInputs` + acknowledgements → report.
- `ReadinessAcknowledgement.java` + `ReadinessAcknowledgementRepository.java` — persisted DM acceptances.
- `ReadinessInputsAssembler.java` — assembles `ReadinessInputs` from persisted repositories (home path).
- `PreviewReadinessAssembler.java` — assembles `ReadinessInputs` from a `CampaignManifestV2` (preview path).
- `CampaignReadinessFacade.java` — transactional entry points used by controllers.
- `web/ReadinessController.java` — gap controls (assign kind, accept item) + fragment render helpers.

**Modified domain / package:**
- `handout/data/Handout.java` — nested `AssetKind` enum + `assetKind` column.
- `handout/packagev2/HandoutSectionAdapter.java` — export/import `assetKind`.
- `adventure/data/Scene.java` + new `adventure/data/SceneMapRequirement.java` — `mapRequirement` column.
- The adventure section adapter — export/import scene `mapRequirement` (located in Task 4).
- `campaign/packagev2/model/CampaignManifestV2.java` — `HandoutDto.assetKind`, `SceneDto.mapRequirement`, `Metadata.conversionOmissions`, new `ConversionOmissionDto`, `CURRENT_FORMAT_VERSION = 3`.
- `campaign/packagev2/preview/CampaignImportPreview.java` — carry the readiness report.
- `campaign/packagev2/web/CampaignPackageController.java` + `campaign/packagev2/service/CampaignImportCoordinator.java` — attach preview readiness.
- `campaign/web/CampaignController.java` — attach home readiness.

**Templates:**
- `campaigns/_readiness.html` — shared fragment rendering a `CampaignReadinessReport`.
- `campaigns/detail.html` — include the fragment.
- `campaigns/_import-dialog.html` — render preview readiness.

**Migrations:** `V23__add_handout_asset_kind.sql`, `V24__add_scene_map_requirement.sql`, `V25__add_readiness_acknowledgement.sql`.

**Tests:** one focused test class per task under the mirrored `src/test/java/...` path; fixture work extends `src/test/java/dev/hendrikhoemberg/dmhelper/support/`.

---

## Task 1: AssetKind on the Handout domain

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/Handout.java`
- Create: `src/main/resources/db/migration/V23__add_handout_asset_kind.sql`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/data/HandoutAssetKindTest.java`

**Interfaces:**
- Produces: `Handout.AssetKind { PLAYER_HANDOUT, DM_REFERENCE, REGIONAL_MAP, TACTICAL_MAP, ILLUSTRATION, SOURCE_PAGE }` with `boolean isMapLike()` (true for `REGIONAL_MAP`, `TACTICAL_MAP`); `Handout.getAssetKind()/setAssetKind(AssetKind)`, non-null, default `SOURCE_PAGE`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.handout.data;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandoutAssetKindTest {

    @Test
    void defaultsToSourcePage() {
        assertThat(new Handout().getAssetKind()).isEqualTo(Handout.AssetKind.SOURCE_PAGE);
    }

    @Test
    void mapLikeKindsAreFlagged() {
        assertThat(Handout.AssetKind.TACTICAL_MAP.isMapLike()).isTrue();
        assertThat(Handout.AssetKind.REGIONAL_MAP.isMapLike()).isTrue();
        assertThat(Handout.AssetKind.PLAYER_HANDOUT.isMapLike()).isFalse();
    }

    @Test
    void rejectsNullKind() {
        assertThatThrownBy(() -> new Handout().setAssetKind(null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=HandoutAssetKindTest`
Expected: FAIL — `AssetKind` / `getAssetKind` do not exist (compilation error).

- [ ] **Step 3: Add the enum and column to `Handout`**

In `Handout.java`, add the enum next to `SafetyClassification` (after line 21):

```java
    public enum AssetKind {
        PLAYER_HANDOUT, DM_REFERENCE, REGIONAL_MAP, TACTICAL_MAP, ILLUSTRATION, SOURCE_PAGE;

        public boolean isMapLike() {
            return this == REGIONAL_MAP || this == TACTICAL_MAP;
        }
    }
```

Add the field next to `safetyClassification` (after line 51):

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, length = 24)
    private AssetKind assetKind = AssetKind.SOURCE_PAGE;
```

Add accessors next to the safety accessors (after line 62):

```java
    public AssetKind getAssetKind() { return assetKind; }
    public void setAssetKind(AssetKind assetKind) {
        this.assetKind = java.util.Objects.requireNonNull(assetKind, "asset kind");
    }
```

- [ ] **Step 4: Write the Flyway migration**

Create `V23__add_handout_asset_kind.sql`:

```sql
alter table handout add column asset_kind varchar(24) not null default 'SOURCE_PAGE';

-- Existing imports were captured as generic source-page material; that is the
-- most conservative interpretation and matches the entity default.
update handout set asset_kind = 'SOURCE_PAGE' where asset_kind is null;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=HandoutAssetKindTest`
Expected: PASS.

- [ ] **Step 6: Verify schema validation still boots**

Run: `./mvnw -q test -Dtest=HandoutSafetyTest` (any existing handout persistence test; if none, run `-Dtest=*Handout*`)
Expected: PASS — Hibernate `validate` accepts the new column against `V23`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/Handout.java \
        src/main/resources/db/migration/V23__add_handout_asset_kind.sql \
        src/test/java/dev/hendrikhoemberg/dmhelper/handout/data/HandoutAssetKindTest.java
git commit -m "feat(handout): add asset kind classification axis"
```

---

## Task 2: AssetKind round-trips through the package

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java` (`HandoutDto`)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutAssetKindRoundTripTest.java`

**Interfaces:**
- Consumes: `Handout.AssetKind` (Task 1).
- Produces: `HandoutDto.assetKind()` (String, nullable); import defaults null → `SOURCE_PAGE`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.handout.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HandoutAssetKindRoundTripTest {

    @Test
    void legacyConstructorDefaultsKindToNull() {
        HandoutDto dto = new HandoutDto("k", "Map", java.util.List.of(), "a", "image/png",
                true, false, "DM_SOURCE", null, null);
        assertThat(dto.assetKind()).isNull();
    }

    @Test
    void fullConstructorCarriesKind() {
        HandoutDto dto = new HandoutDto("k", "Map", java.util.List.of(), "a", "image/png",
                true, false, "DM_SOURCE", null, null, "TACTICAL_MAP");
        assertThat(dto.assetKind()).isEqualTo("TACTICAL_MAP");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=HandoutAssetKindRoundTripTest`
Expected: FAIL — 11-arg constructor and `assetKind()` do not exist.

- [ ] **Step 3: Add `assetKind` to `HandoutDto` with a back-compat constructor**

Replace the `HandoutDto` record (lines 258–270 of `CampaignManifestV2.java`) with:

```java
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HandoutDto(
            String key,
            String title,
            List<String> tags,
            String assetRef,
            String contentType,
            boolean dmOnly,
            boolean presented,
            String safetyClassification,
            ContentReference sourceRef,
            String derivativeRecipe,
            String assetKind
    ) {
        public HandoutDto(String key, String title, List<String> tags, String assetRef,
                          String contentType, boolean dmOnly, boolean presented,
                          String safetyClassification, ContentReference sourceRef,
                          String derivativeRecipe) {
            this(key, title, tags, assetRef, contentType, dmOnly, presented,
                    safetyClassification, sourceRef, derivativeRecipe, null);
        }
    }
```

- [ ] **Step 4: Export and import the kind in `HandoutSectionAdapter`**

In `exportHandout` (line 94), pass the kind as the last argument:

```java
        return new HandoutDto(
                key, handout.getTitle(), tagsList,
                assetKey, handout.getContentType(),
                handout.isDmOnly(), handout.isPresented(),
                safetyClassification, sourceRef, derivativeRecipe,
                handout.getAssetKind().name()
        );
```

In `importSection`, after `handout.setSafetyClassification(classification);` (line 139) add:

```java
            handout.setAssetKind(kindOf(dto));
```

Add the helper next to `classificationOf` (after line 165):

```java
    private static Handout.AssetKind kindOf(HandoutDto dto) {
        if (dto.assetKind() != null && !dto.assetKind().isBlank()) {
            return Handout.AssetKind.valueOf(dto.assetKind());
        }
        return Handout.AssetKind.SOURCE_PAGE;
    }
```

Add the import: `import dev.hendrikhoemberg.dmhelper.handout.data.Handout.AssetKind;` is not required since we reference `Handout.AssetKind` fully; `Handout` is already imported.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=HandoutAssetKindRoundTripTest`
Expected: PASS.

- [ ] **Step 6: Prove the full package round-trip preserves kind**

The `kindOf` default is covered by the import path; verify it through the existing full round-trip integration tests rather than a white-box helper (keep `kindOf` private). Confirm handout export/import still passes end-to-end:

Run: `./mvnw -q test -Dtest=*RoundTrip*`
Expected: PASS — handout round-trip tests are green with `assetKind` now flowing through.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutAssetKindRoundTripTest.java
git commit -m "feat(package): round trip handout asset kind"
```

---

## Task 3: Scene map-requirement on the domain

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneMapRequirement.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/Scene.java`
- Create: `src/main/resources/db/migration/V24__add_scene_map_requirement.sql`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneMapRequirementTest.java`

**Interfaces:**
- Produces: `SceneMapRequirement { REQUIRED, OPTIONAL, NONE }`; `Scene.getMapRequirement()` returns `SceneMapRequirement` or `null` (null = "not declared, infer"); `Scene.setMapRequirement(SceneMapRequirement)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SceneMapRequirementTest {

    @Test
    void mapRequirementIsNullByDefaultMeaningInfer() {
        assertThat(new Scene().getMapRequirement()).isNull();
    }

    @Test
    void mapRequirementIsSettable() {
        Scene scene = new Scene();
        scene.setMapRequirement(SceneMapRequirement.REQUIRED);
        assertThat(scene.getMapRequirement()).isEqualTo(SceneMapRequirement.REQUIRED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SceneMapRequirementTest`
Expected: FAIL — `SceneMapRequirement` / `getMapRequirement` do not exist.

- [ ] **Step 3: Create the enum**

`SceneMapRequirement.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

public enum SceneMapRequirement {
    REQUIRED, OPTIONAL, NONE
}
```

- [ ] **Step 4: Add the nullable column to `Scene`**

In `Scene.java`, after the `mapRegionKey` field (line 85) add:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "map_requirement", length = 16)
    private SceneMapRequirement mapRequirement;
```

After the `getMapRegionKey`/`setMapRegionKey` accessors (line 159) add:

```java
    public SceneMapRequirement getMapRequirement() { return mapRequirement; }
    public void setMapRequirement(SceneMapRequirement mapRequirement) {
        this.mapRequirement = mapRequirement;
    }
```

- [ ] **Step 5: Write the Flyway migration**

`V24__add_scene_map_requirement.sql`:

```sql
-- Nullable: a null value means "not declared by the converter; infer from participants".
alter table adventure_scene add column map_requirement varchar(16);
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=SceneMapRequirementTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneMapRequirement.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/Scene.java \
        src/main/resources/db/migration/V24__add_scene_map_requirement.sql \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneMapRequirementTest.java
git commit -m "feat(adventure): add scene map requirement hint"
```

---

## Task 4: Scene map-requirement round-trips through the package

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java` (`SceneDto`)
- Modify: the adventure section adapter that builds/reads `SceneDto` (find with the command in Step 3)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/SceneMapRequirementRoundTripTest.java`

**Interfaces:**
- Consumes: `SceneMapRequirement` (Task 3).
- Produces: `SceneDto.mapRequirement()` (String, nullable); import maps null → leaves `Scene.mapRequirement` null.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SceneMapRequirementRoundTripTest {

    @Test
    void legacySceneConstructorDefaultsMapRequirementNull() {
        SceneDto dto = new SceneDto("k", "t", "b", "UNVISITED", 0, null, null, null,
                java.util.List.of(), java.util.List.of(), "s", "loc", java.util.List.of(),
                "region", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), null);
        assertThat(dto.mapRequirement()).isNull();
    }

    @Test
    void fullSceneConstructorCarriesMapRequirement() {
        SceneDto dto = new SceneDto("k", "t", "b", "UNVISITED", 0, null, null, null,
                java.util.List.of(), java.util.List.of(), "s", "loc", java.util.List.of(),
                "region", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), null, "REQUIRED");
        assertThat(dto.mapRequirement()).isEqualTo("REQUIRED");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SceneMapRequirementRoundTripTest`
Expected: FAIL — the extra constructor arg and `mapRequirement()` do not exist.

- [ ] **Step 3: Add `mapRequirement` to `SceneDto` with a back-compat constructor**

Append `String mapRequirement` as the final component of `SceneDto` (after `sceneCueRef`, line 561) and add a delegating constructor with the prior 20-arg list:

```java
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneDto(
            String key, String title, String body, String status, int sortOrder,
            ContentReference mapRef, Map<String, Integer> pin, ContentReference encounterRef,
            List<ContentReference> statblockRefs, List<ContentReference> handoutRefs,
            String summary, String sourceLocator, List<String> tags, String mapRegionKey,
            List<SceneSectionDto> sections, List<SceneCheckDto> checks,
            List<SceneParticipantDto> participants, List<SceneTransitionDto> transitions,
            List<SceneLinkDto> links, ContentReference sceneCueRef,
            String mapRequirement
    ) {
        public SceneDto(String key, String title, String body, String status, int sortOrder,
                ContentReference mapRef, Map<String, Integer> pin, ContentReference encounterRef,
                List<ContentReference> statblockRefs, List<ContentReference> handoutRefs,
                String summary, String sourceLocator, List<String> tags, String mapRegionKey,
                List<SceneSectionDto> sections, List<SceneCheckDto> checks,
                List<SceneParticipantDto> participants, List<SceneTransitionDto> transitions,
                List<SceneLinkDto> links, ContentReference sceneCueRef) {
            this(key, title, body, status, sortOrder, mapRef, pin, encounterRef, statblockRefs,
                    handoutRefs, summary, sourceLocator, tags, mapRegionKey, sections, checks,
                    participants, transitions, links, sceneCueRef, null);
        }
    }
```

- [ ] **Step 4: Wire export/import in the adventure adapter**

Locate the adapter that constructs `SceneDto` and reads it back:

Run: `grep -rln 'new SceneDto(' src/main/java`
Run: `grep -rln 'SceneDto' src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2`

In the exporter, set the last argument from the scene:

```java
                scene.getMapRequirement() == null ? null : scene.getMapRequirement().name()
```

In the importer, after the scene entity is built, before it is saved, add:

```java
        if (dto.mapRequirement() != null && !dto.mapRequirement().isBlank()) {
            scene.setMapRequirement(
                    dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement
                            .valueOf(dto.mapRequirement()));
        }
```

If the adventure importer builds scenes through a service method rather than directly, add the same guarded `setMapRequirement` call at the point where other optional scalar scene fields (e.g. `summary`, `mapRegionKey`) are applied.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=SceneMapRequirementRoundTripTest`
Expected: PASS.

- [ ] **Step 6: Confirm existing adventure round-trip tests still pass**

Run: `./mvnw -q test -Dtest=*Adventure*RoundTrip*,*CampaignCompleteRoundTrip*`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/SceneMapRequirementRoundTripTest.java
git commit -m "feat(package): round trip scene map requirement"
```

---

## Task 5: Converter-declared omissions and format version bump

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java` (`Metadata`, `CURRENT_FORMAT_VERSION`, new `ConversionOmissionDto`)
- Modify: the manifest assembler that builds `Metadata` (find in Step 4)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/ConversionOmissionAndVersionTest.java`

**Interfaces:**
- Produces: `CampaignManifestV2.ConversionOmissionDto(String area, String reason)`; `Metadata.conversionOmissions()` (nullable list, defaults to `List.of()` in the compact constructor); `CURRENT_FORMAT_VERSION == 3`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ConversionOmissionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConversionOmissionAndVersionTest {

    @Test
    void currentVersionIsThree() {
        assertThat(CampaignManifestV2.CURRENT_FORMAT_VERSION).isEqualTo(3);
    }

    @Test
    void metadataDefaultsOmissionsToEmpty() {
        Metadata m = new Metadata("k", null, "gen", "cat", "sha", null);
        assertThat(m.conversionOmissions()).isEmpty();
    }

    @Test
    void metadataCarriesDeclaredOmissions() {
        Metadata m = new Metadata("k", null, "gen", "cat", "sha", null,
                java.util.List.of(new ConversionOmissionDto("maps", "no printed maps in source")));
        assertThat(m.conversionOmissions()).singleElement()
                .extracting(ConversionOmissionDto::area).isEqualTo("maps");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ConversionOmissionAndVersionTest`
Expected: FAIL — version is 2, `conversionOmissions` and `ConversionOmissionDto` do not exist.

- [ ] **Step 3: Bump the version, extend `Metadata`, add the DTO**

Change line 68: `public static final int CURRENT_FORMAT_VERSION = 3;`

Replace the `Metadata` record (lines 70–78) with:

```java
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Metadata(
            String packageKey,
            Instant createdAt,
            String generator,
            String catalogVersion,
            String catalogSha256,
            List<CampaignExportExclusion> exclusions,
            List<ConversionOmissionDto> conversionOmissions
    ) {
        public Metadata {
            if (conversionOmissions == null) conversionOmissions = List.of();
        }
        public Metadata(String packageKey, Instant createdAt, String generator,
                        String catalogVersion, String catalogSha256,
                        List<CampaignExportExclusion> exclusions) {
            this(packageKey, createdAt, generator, catalogVersion, catalogSha256, exclusions, List.of());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConversionOmissionDto(String area, String reason) {}
```

- [ ] **Step 4: Preserve omissions through the assembler (no-op default is acceptable)**

Locate the `Metadata` construction site:

Run: `grep -rln 'new CampaignManifestV2.Metadata\|new Metadata(' src/main/java`

The existing 6-arg call now resolves to the back-compat constructor and yields empty omissions on export — correct, because DMHelper is not the converter and declares no omissions of its own. No change is required unless the assembler already surfaces a source list; if it does, pass it as the 7th argument. Confirm the call compiles unchanged.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ConversionOmissionAndVersionTest`
Expected: PASS.

- [ ] **Step 6: Prove a prior-version (v2) package still imports**

Add to the test class an import-back-compat check that reads a minimal `formatVersion: 2` manifest through the existing reader and asserts success with empty omissions. If a reader-level fixture harness exists (search `grep -rln 'readManifest\|CampaignPackageReader' src/test`), reuse it; otherwise assert deserialization directly:

```java
    @Test
    void deserializesVersionTwoManifestWithConservativeDefaults() throws Exception {
        String json = "{\"formatVersion\":2,\"metadata\":{\"packageKey\":\"k\"}}";
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        CampaignManifestV2 m = mapper.readValue(json, CampaignManifestV2.class);
        assertThat(m.formatVersion()).isEqualTo(2);
        assertThat(m.metadata().conversionOmissions()).isEmpty();
    }
```

If the project's Jackson entry point differs, mirror the mapper construction used in an existing `*ManifestTest` (search `grep -rln 'JsonMapper\|ObjectMapper' src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2`).

Run: `./mvnw -q test -Dtest=ConversionOmissionAndVersionTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/ConversionOmissionAndVersionTest.java
git commit -m "feat(package): declare conversion omissions and bump format to v3"
```

---

## Task 6: Readiness value model

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessState.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessCategory.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessRepairKind.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessItem.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessReport.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessReportTest.java`

**Interfaces:**
- Produces:
  - `ReadinessState { RESOLVED, BLOCKER, ACCEPTED }`
  - `ReadinessCategory { ENCOUNTER, STATBLOCK, MAP, ASSET, RUNTIME_LINK, OMISSION, NEXT_ACTION }`
  - `ReadinessRepairKind { SEED_ENCOUNTER, EDIT_SCENE_PARTICIPANTS, ASSIGN_SCENE_MAP, REVIEW_HANDOUT_SAFETY, CLASSIFY_ASSET_KIND, ACCEPT_ITEM, NONE }`
  - `ReadinessItem(String key, ReadinessCategory category, ReadinessState state, String title, String detail, ReadinessRepairKind repairKind, UUID targetId)`
  - `CampaignReadinessReport(List<ReadinessItem> items)` with `boolean sessionReady()`, `long blockerCount()`, `String label()`, `List<ReadinessItem> byState(ReadinessState)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignReadinessReportTest {

    private ReadinessItem item(ReadinessState state) {
        return new ReadinessItem("k-" + state, ReadinessCategory.ENCOUNTER, state,
                "t", "d", ReadinessRepairKind.NONE, null);
    }

    @Test
    void sessionReadyWhenNoBlockers() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.RESOLVED), item(ReadinessState.ACCEPTED)));
        assertThat(report.sessionReady()).isTrue();
        assertThat(report.label()).isEqualTo("Session-ready");
    }

    @Test
    void notSessionReadyWithAnyBlocker() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.RESOLVED), item(ReadinessState.BLOCKER)));
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.blockerCount()).isEqualTo(1);
        assertThat(report.label()).isEqualTo("Valid but not session-ready");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=CampaignReadinessReportTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create the enums**

`ReadinessState.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

public enum ReadinessState { RESOLVED, BLOCKER, ACCEPTED }
```

`ReadinessCategory.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

public enum ReadinessCategory {
    ENCOUNTER, STATBLOCK, MAP, ASSET, RUNTIME_LINK, OMISSION, NEXT_ACTION
}
```

`ReadinessRepairKind.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

public enum ReadinessRepairKind {
    SEED_ENCOUNTER, EDIT_SCENE_PARTICIPANTS, ASSIGN_SCENE_MAP,
    REVIEW_HANDOUT_SAFETY, CLASSIFY_ASSET_KIND, ACCEPT_ITEM, NONE
}
```

- [ ] **Step 4: Create the records**

`ReadinessItem.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import java.util.UUID;

public record ReadinessItem(
        String key,
        ReadinessCategory category,
        ReadinessState state,
        String title,
        String detail,
        ReadinessRepairKind repairKind,
        UUID targetId) {

    public boolean acceptable() {
        return state == ReadinessState.BLOCKER;
    }
}
```

`CampaignReadinessReport.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import java.util.List;

public record CampaignReadinessReport(List<ReadinessItem> items) {

    public CampaignReadinessReport {
        items = List.copyOf(items);
    }

    public long blockerCount() {
        return items.stream().filter(i -> i.state() == ReadinessState.BLOCKER).count();
    }

    public boolean sessionReady() {
        return blockerCount() == 0;
    }

    public String label() {
        return sessionReady() ? "Session-ready" : "Valid but not session-ready";
    }

    public List<ReadinessItem> byState(ReadinessState state) {
        return items.stream().filter(i -> i.state() == state).toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=CampaignReadinessReportTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessReportTest.java
git commit -m "feat(readiness): add operational readiness value model"
```

---

## Task 7: Readiness acknowledgement persistence

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcknowledgement.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcknowledgementRepository.java`
- Create: `src/main/resources/db/migration/V25__add_readiness_acknowledgement.sql`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcknowledgementRepositoryTest.java`

**Interfaces:**
- Produces: entity `ReadinessAcknowledgement` (id, campaignId, itemKey, acceptedAt); `ReadinessAcknowledgementRepository extends JpaRepository<ReadinessAcknowledgement, UUID>` with `List<ReadinessAcknowledgement> findByCampaignId(UUID)`, `boolean existsByCampaignIdAndItemKey(UUID, String)`, `void deleteByCampaignIdAndItemKey(UUID, String)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReadinessAcknowledgementRepositoryTest {

    @Autowired ReadinessAcknowledgementRepository repo;

    @Test
    void persistsAndQueriesByCampaignAndKey() {
        UUID campaignId = UUID.randomUUID();
        var ack = new ReadinessAcknowledgement();
        ack.setCampaignId(campaignId);
        ack.setItemKey("omission:maps");
        ack.setAcceptedAt(Instant.now());
        repo.save(ack);

        assertThat(repo.existsByCampaignIdAndItemKey(campaignId, "omission:maps")).isTrue();
        assertThat(repo.findByCampaignId(campaignId)).hasSize(1);
        assertThat(repo.existsByCampaignIdAndItemKey(campaignId, "other")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ReadinessAcknowledgementRepositoryTest`
Expected: FAIL — entity/repository do not exist.

- [ ] **Step 3: Create the entity**

`ReadinessAcknowledgement.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "readiness_acknowledgement",
        uniqueConstraints = @UniqueConstraint(name = "uq_readiness_ack_campaign_item",
                columnNames = {"campaign_id", "item_key"}),
        indexes = @Index(name = "idx_readiness_ack_campaign", columnList = "campaign_id"))
public class ReadinessAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "item_key", nullable = false, length = 200)
    private String itemKey;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
```

- [ ] **Step 4: Create the repository**

`ReadinessAcknowledgementRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReadinessAcknowledgementRepository
        extends JpaRepository<ReadinessAcknowledgement, UUID> {

    List<ReadinessAcknowledgement> findByCampaignId(UUID campaignId);

    boolean existsByCampaignIdAndItemKey(UUID campaignId, String itemKey);

    void deleteByCampaignIdAndItemKey(UUID campaignId, String itemKey);
}
```

- [ ] **Step 5: Write the Flyway migration**

`V25__add_readiness_acknowledgement.sql`:

```sql
create table readiness_acknowledgement (
    id uuid not null,
    campaign_id uuid not null,
    item_key varchar(200) not null,
    accepted_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uq_readiness_ack_campaign_item unique (campaign_id, item_key)
);
create index idx_readiness_ack_campaign on readiness_acknowledgement (campaign_id);
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ReadinessAcknowledgementRepositoryTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcknowledgement.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcknowledgementRepository.java \
        src/main/resources/db/migration/V25__add_readiness_acknowledgement.sql \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcknowledgementRepositoryTest.java
git commit -m "feat(readiness): persist DM acknowledgements"
```

---

## Task 8: Readiness inputs and rule engine

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessInputs.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessServiceTest.java`

**Interfaces:**
- Consumes: `ReadinessItem`, `ReadinessState`, `ReadinessCategory`, `ReadinessRepairKind`, `CampaignReadinessReport` (Task 6).
- Produces:
  - `ReadinessInputs` — record of normalized snapshots:
    - `List<SceneInput> scenes` where `SceneInput(UUID id, String title, boolean hostile, boolean hasLinkedEncounter, boolean anyParticipantHasStatblock, List<String> unresolvedStatblockParticipants, SceneMapRequirement declaredMapRequirement, boolean hasMap)`
    - `List<AssetInput> presentedAssets` where `AssetInput(UUID id, String title, Handout.AssetKind kind, Handout.SafetyClassification safety, boolean linkedForPresentation)`
    - `List<LinkInput> runtimeLinks` where `LinkInput(String key, String description, boolean resolved)`
    - `List<OmissionInput> omissions` where `OmissionInput(String area, String reason)`
  - `SceneInput.effectiveMapRequired()` — `declaredMapRequirement == REQUIRED`, or (declared null AND hostile).
  - `CampaignReadinessService.compute(ReadinessInputs inputs, Set<String> acceptedKeys) : CampaignReadinessReport`. Every produced item carries a stable `key`; an item whose `key` is in `acceptedKeys` is emitted as `ACCEPTED` instead of `BLOCKER`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignReadinessServiceTest {

    private final CampaignReadinessService service = new CampaignReadinessService();

    private ReadinessInputs inputs(ReadinessInputs.SceneInput... scenes) {
        return new ReadinessInputs(List.of(scenes), List.of(), List.of(), List.of());
    }

    @Test
    void hostileSceneWithoutEncounterOrStatblocksIsBlocker() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Klarg", true,
                false, false, List.of("Klarg"), null, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.ENCOUNTER);
    }

    @Test
    void seedableHostileSceneIsResolvedEncounterButFlagsMissingStatblocks() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Klarg", true,
                false, true, List.of("Goblin 3"), null, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.STATBLOCK);
    }

    @Test
    void hostileSceneInfersRequiredMapWhenNotDeclared() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Klarg", true,
                true, true, List.of(), null, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.MAP);
    }

    @Test
    void unsafePresentedAssetIsBlocker() {
        var asset = new ReadinessInputs.AssetInput(UUID.randomUUID(), "Page 12",
                Handout.AssetKind.SOURCE_PAGE, Handout.SafetyClassification.UNREVIEWED, true);
        var report = service.compute(
                new ReadinessInputs(List.of(), List.of(asset), List.of(), List.of()), Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.ASSET);
    }

    @Test
    void acceptedBlockerBecomesAccepted() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Klarg", true,
                false, false, List.of(), null, false);
        var first = service.compute(inputs(scene), Set.of());
        String key = first.byState(ReadinessState.BLOCKER).get(0).key();
        var second = service.compute(inputs(scene), Set.of(key));
        assertThat(second.sessionReady()).isTrue();
        assertThat(second.byState(ReadinessState.ACCEPTED)).extracting(ReadinessItem::key).contains(key);
    }

    @Test
    void optionalMapAbsenceIsAdvisoryNotBlocker() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Road", false,
                false, false, List.of(), SceneMapRequirement.OPTIONAL, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.sessionReady()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=CampaignReadinessServiceTest`
Expected: FAIL — `ReadinessInputs` and `CampaignReadinessService` do not exist.

- [ ] **Step 3: Create `ReadinessInputs`**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import java.util.List;
import java.util.UUID;

public record ReadinessInputs(
        List<SceneInput> scenes,
        List<AssetInput> presentedAssets,
        List<LinkInput> runtimeLinks,
        List<OmissionInput> omissions) {

    public record SceneInput(
            UUID id,
            String title,
            boolean hostile,
            boolean hasLinkedEncounter,
            boolean anyParticipantHasStatblock,
            List<String> unresolvedStatblockParticipants,
            SceneMapRequirement declaredMapRequirement,
            boolean hasMap) {

        public boolean effectiveMapRequired() {
            if (declaredMapRequirement == SceneMapRequirement.REQUIRED) return true;
            if (declaredMapRequirement == SceneMapRequirement.OPTIONAL
                    || declaredMapRequirement == SceneMapRequirement.NONE) return false;
            return hostile; // inferred: a hostile scene wants a battle map
        }

        public boolean encounterOperational() {
            return hasLinkedEncounter || anyParticipantHasStatblock;
        }
    }

    public record AssetInput(
            UUID id,
            String title,
            Handout.AssetKind kind,
            Handout.SafetyClassification safety,
            boolean linkedForPresentation) {}

    public record LinkInput(String key, String description, boolean resolved) {}

    public record OmissionInput(String area, String reason) {}
}
```

- [ ] **Step 4: Create `CampaignReadinessService`**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Emits one {@link ReadinessItem} per finding. State semantics:
 * BLOCKER = must be resolved or accepted before session-ready; ACCEPTED = a former
 * blocker the DM explicitly acknowledged; RESOLVED = listed but non-blocking, i.e. a
 * satisfied requirement or an advisory (unresolved runtime links, declared omissions).
 * Only BLOCKER items prevent session-ready.
 */
@Service
public class CampaignReadinessService {

    public CampaignReadinessReport compute(ReadinessInputs inputs, Set<String> acceptedKeys) {
        List<ReadinessItem> items = new ArrayList<>();

        for (ReadinessInputs.SceneInput scene : inputs.scenes()) {
            if (scene.hostile() && !scene.encounterOperational()) {
                items.add(blockerOrAccepted(
                        "encounter:" + scene.id(), ReadinessCategory.ENCOUNTER,
                        "No runnable encounter: " + scene.title(),
                        "Hostile scene has no linked encounter and no participant statblocks to seed one.",
                        ReadinessRepairKind.EDIT_SCENE_PARTICIPANTS, scene.id(), acceptedKeys));
            } else if (scene.hostile() && !scene.hasLinkedEncounter() && scene.anyParticipantHasStatblock()) {
                items.add(new ReadinessItem(
                        "encounter:" + scene.id(), ReadinessCategory.NEXT_ACTION, ReadinessState.RESOLVED,
                        "Ready to seed encounter: " + scene.title(),
                        "Seed the encounter from its participants before the session.",
                        ReadinessRepairKind.SEED_ENCOUNTER, scene.id()));
            }

            if (scene.hostile() && !scene.unresolvedStatblockParticipants().isEmpty()) {
                items.add(blockerOrAccepted(
                        "statblock:" + scene.id(), ReadinessCategory.STATBLOCK,
                        "Participants missing statblocks: " + scene.title(),
                        String.join(", ", scene.unresolvedStatblockParticipants()),
                        ReadinessRepairKind.EDIT_SCENE_PARTICIPANTS, scene.id(), acceptedKeys));
            }

            if (scene.effectiveMapRequired() && !scene.hasMap()) {
                items.add(blockerOrAccepted(
                        "map:" + scene.id(), ReadinessCategory.MAP,
                        "Scene requires a map: " + scene.title(),
                        "No playable or reference map is linked to this scene.",
                        ReadinessRepairKind.ASSIGN_SCENE_MAP, scene.id(), acceptedKeys));
            }
        }

        for (ReadinessInputs.AssetInput asset : inputs.presentedAssets()) {
            if (asset.linkedForPresentation() && !asset.safety().isPresentable()) {
                items.add(blockerOrAccepted(
                        "asset:" + asset.id(), ReadinessCategory.ASSET,
                        "Unsafe asset linked for presentation: " + asset.title(),
                        "Classified " + asset.safety() + " / " + asset.kind()
                                + "; not player-safe. Review or reclassify before presenting.",
                        ReadinessRepairKind.REVIEW_HANDOUT_SAFETY, asset.id(), acceptedKeys));
            }
        }

        for (ReadinessInputs.LinkInput link : inputs.runtimeLinks()) {
            if (!link.resolved()) {
                items.add(new ReadinessItem(
                        "link:" + link.key(), ReadinessCategory.RUNTIME_LINK,
                        acceptedKeys.contains("link:" + link.key())
                                ? ReadinessState.ACCEPTED : ReadinessState.RESOLVED,
                        "Unresolved link: " + link.description(),
                        "Advisory: this runtime link has no resolved target.",
                        ReadinessRepairKind.NONE, null));
            }
        }

        for (ReadinessInputs.OmissionInput omission : inputs.omissions()) {
            String key = "omission:" + omission.area();
            items.add(new ReadinessItem(
                    key, ReadinessCategory.OMISSION,
                    acceptedKeys.contains(key) ? ReadinessState.ACCEPTED : ReadinessState.RESOLVED,
                    "Converter omitted: " + omission.area(),
                    omission.reason() == null ? "Declared omission." : omission.reason(),
                    ReadinessRepairKind.ACCEPT_ITEM, null));
        }

        return new CampaignReadinessReport(items);
    }

    private ReadinessItem blockerOrAccepted(String key, ReadinessCategory category,
                                            String title, String detail,
                                            ReadinessRepairKind repairKind,
                                            java.util.UUID targetId, Set<String> acceptedKeys) {
        ReadinessState state = acceptedKeys.contains(key)
                ? ReadinessState.ACCEPTED : ReadinessState.BLOCKER;
        return new ReadinessItem(key, category, state, title, detail, repairKind, targetId);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=CampaignReadinessServiceTest`
Expected: PASS (all six cases).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessInputs.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessServiceTest.java
git commit -m "feat(readiness): compute report from normalized inputs"
```

---

## Task 9: Persisted assembler (campaign home data path)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessInputsAssembler.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessFacade.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessFacadeTest.java`

**Interfaces:**
- Consumes: `SceneRepository`, `HandoutRepository`, `SceneEncounterSeedService`, `CampaignReadinessService`, `ReadinessAcknowledgementRepository`.
- Produces:
  - `ReadinessInputsAssembler.fromCampaign(UUID campaignId) : ReadinessInputs` (runs under a read transaction opened by the facade).
  - `CampaignReadinessFacade.reportForCampaign(UUID campaignId) : CampaignReadinessReport` — `@Transactional(readOnly = true)`.

- [ ] **Step 1: Write the failing integration test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignReadinessFacadeTest {

    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignFixtures fixtures; // existing helper; if absent, build via services in-test

    @Test
    void reportsBlockersForAHostileSceneWithoutEncounter() {
        UUID campaignId = fixtures.hostileSceneWithoutEncounter();
        CampaignReadinessReport report = facade.reportForCampaign(campaignId);
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.blockerCount()).isGreaterThanOrEqualTo(1);
    }
}
```

If no `CampaignFixtures` helper exists, replace `fixtures.hostileSceneWithoutEncounter()` with inline setup using the same services other `@SpringBootTest` campaign tests use (search `grep -rln 'class .*Test' src/test/java/dev/hendrikhoemberg/dmhelper/adventure | xargs grep -l SpringBootTest` for a pattern to copy). Create the minimal campaign → adventure → chapter → scene → hostile participant (no statblock, no encounter).

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=CampaignReadinessFacadeTest`
Expected: FAIL — facade/assembler do not exist.

- [ ] **Step 3: Create the persisted assembler**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReadinessInputsAssembler {

    private final SceneRepository scenes;
    private final HandoutRepository handouts;

    public ReadinessInputsAssembler(SceneRepository scenes, HandoutRepository handouts) {
        this.scenes = scenes;
        this.handouts = handouts;
    }

    /** Caller must hold a read transaction so lazy scene collections initialize safely. */
    public ReadinessInputs fromCampaign(UUID campaignId) {
        List<ReadinessInputs.SceneInput> sceneInputs = new ArrayList<>();
        for (Scene scene : scenes.findByCampaignIdOrderByChapterAndSort(campaignId)) {
            boolean hostile = false;
            boolean anyStatblock = false;
            List<String> unresolved = new ArrayList<>();
            for (SceneParticipant p : scene.getParticipants()) {
                boolean h = p.getDisposition() == SceneParticipantDisposition.HOSTILE;
                hostile = hostile || h;
                if (p.getStatBlock() != null) {
                    anyStatblock = true;
                } else if (h) {
                    unresolved.add(participantLabel(p));
                }
            }
            sceneInputs.add(new ReadinessInputs.SceneInput(
                    scene.getId(), scene.getTitle(), hostile,
                    scene.getEncounter() != null, anyStatblock, unresolved,
                    scene.getMapRequirement(), scene.getMap() != null));
        }

        List<ReadinessInputs.AssetInput> assetInputs = new ArrayList<>();
        for (Handout h : handouts.findByCampaignIdOrderByTitleAsc(campaignId)) {
            assetInputs.add(new ReadinessInputs.AssetInput(
                    h.getId(), h.getTitle(), h.getAssetKind(), h.getSafetyClassification(),
                    h.isPresented()));
        }

        // Runtime links and converter omissions are surfaced via the preview path (Task 10)
        // and acknowledgement store; the persisted home report focuses on scene/asset readiness.
        return new ReadinessInputs(sceneInputs, assetInputs, List.of(), List.of());
    }

    private static String participantLabel(SceneParticipant p) {
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) return p.getDisplayName().trim();
        return "Unnamed participant";
    }
}
```

If `SceneRepository` has no `findByCampaignIdOrderByChapterAndSort`, add it (derived query over `chapter.adventure.campaign.id`) or reuse an existing campaign-scoped finder discovered via `grep -n 'findБy\|@Query' src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneRepository.java`. Confirm the exact method name and copy it; do not invent a signature that isn't backed by a query.

- [ ] **Step 4: Create the facade**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CampaignReadinessFacade {

    private final ReadinessInputsAssembler assembler;
    private final CampaignReadinessService service;
    private final ReadinessAcknowledgementRepository acknowledgements;

    public CampaignReadinessFacade(ReadinessInputsAssembler assembler,
                                   CampaignReadinessService service,
                                   ReadinessAcknowledgementRepository acknowledgements) {
        this.assembler = assembler;
        this.service = service;
        this.acknowledgements = acknowledgements;
    }

    @Transactional(readOnly = true)
    public CampaignReadinessReport reportForCampaign(UUID campaignId) {
        Set<String> accepted = acknowledgements.findByCampaignId(campaignId).stream()
                .map(ReadinessAcknowledgement::getItemKey)
                .collect(Collectors.toSet());
        return service.compute(assembler.fromCampaign(campaignId), accepted);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=CampaignReadinessFacadeTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessInputsAssembler.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessFacade.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/CampaignReadinessFacadeTest.java
git commit -m "feat(readiness): assemble report from persisted campaign state"
```

---

## Task 10: Pre-commit assembler (import preview data path)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/PreviewReadinessAssembler.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/PreviewReadinessAssemblerTest.java`

**Interfaces:**
- Consumes: `CampaignManifestV2` and its `SceneDto`, `SceneParticipantDto`, `HandoutDto`, `Metadata.conversionOmissions()`.
- Produces: `PreviewReadinessAssembler.fromManifest(CampaignManifestV2) : ReadinessInputs`. A participant "has a statblock" when its `statblockRef() != null`. A scene "has a map" when `mapRef() != null`; "has a linked encounter" when `encounterRef() != null`. Presented assets are handouts with `presented() == true`. Unresolved runtime links (§8.1 category 5, advisory) are scene transitions whose `targetSceneRef()` is null and `externalDestination()` is blank.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewReadinessAssemblerTest {

    private final PreviewReadinessAssembler assembler = new PreviewReadinessAssembler();
    private final CampaignReadinessService service = new CampaignReadinessService();

    @Test
    void hostileSceneWithoutEncounterInManifestBecomesBlocker() {
        var participant = new CampaignManifestV2.SceneParticipantDto(
                "Klarg", 1, "HOSTILE", null, null, null, null, "loc", 0);
        var scene = new CampaignManifestV2.SceneDto(
                "s1", "Klarg", "body", "UNVISITED", 0, null, null, null,
                List.of(), List.of(), "sum", "loc", List.of(), "region",
                List.of(), List.of(), List.of(participant), List.of(), List.of(), null);
        var manifest = minimalManifest(List.of(scene), List.of(), List.of());

        var report = service.compute(assembler.fromManifest(manifest), java.util.Set.of());
        assertThat(report.sessionReady()).isFalse();
    }

    @Test
    void declaredOmissionsSurfaceAsItems() {
        var manifest = new CampaignManifestV2(3,
                new CampaignManifestV2.Metadata("k", null, "gen", "cat", "sha", null,
                        List.of(new CampaignManifestV2.ConversionOmissionDto("maps", "none in source"))),
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        var report = service.compute(assembler.fromManifest(manifest), java.util.Set.of());
        assertThat(report.items()).anyMatch(i -> i.category() == ReadinessCategory.OMISSION);
    }

    private CampaignManifestV2 minimalManifest(List<CampaignManifestV2.SceneDto> scenes,
                                               List<CampaignManifestV2.HandoutDto> handouts,
                                               List<CampaignManifestV2.ConversionOmissionDto> omissions) {
        var chapter = new CampaignManifestV2.ChapterDto("c", "Ch", "intro", 0, scenes);
        var adventure = new CampaignManifestV2.AdventureDto("a", "Adv", "d", "src", 0,
                List.of(chapter), null);
        return new CampaignManifestV2(3,
                new CampaignManifestV2.Metadata("k", null, "gen", "cat", "sha", null, omissions),
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), handouts, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(List.of(adventure).get(0)),
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }
}
```

Note: the `CampaignManifestV2` canonical constructor has many components. Rather than hand-align every argument, prefer building the manifest with only the fields the assembler reads by copying an existing manifest-builder helper if one exists (search `grep -rln 'new CampaignManifestV2(' src/test`). If a builder/helper exists, use it and delete `minimalManifest`. The two assertions (hostile-scene blocker, omission item) are what matter.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=PreviewReadinessAssemblerTest`
Expected: FAIL — `PreviewReadinessAssembler` does not exist.

- [ ] **Step 3: Create the preview assembler**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneParticipantDto;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class PreviewReadinessAssembler {

    public ReadinessInputs fromManifest(CampaignManifestV2 manifest) {
        List<ReadinessInputs.SceneInput> scenes = new ArrayList<>();
        List<ReadinessInputs.LinkInput> links = new ArrayList<>();
        if (manifest.adventures() != null) {
            manifest.adventures().forEach(adv -> {
                if (adv.chapters() == null) return;
                adv.chapters().forEach(ch -> {
                    if (ch.scenes() == null) return;
                    ch.scenes().forEach(scene -> {
                        scenes.add(sceneInput(scene));
                        if (scene.transitions() != null) {
                            scene.transitions().forEach(t -> {
                                boolean resolved = t.targetSceneRef() != null
                                        || (t.externalDestination() != null && !t.externalDestination().isBlank());
                                if (!resolved) {
                                    links.add(new ReadinessInputs.LinkInput(
                                            scene.title() + ":" + t.key(),
                                            "Transition '" + t.label() + "' in " + scene.title()
                                                    + "' has no resolved target",
                                            false));
                                }
                            });
                        }
                    });
                });
            });
        }

        List<ReadinessInputs.AssetInput> assets = new ArrayList<>();
        if (manifest.handouts() != null) {
            for (HandoutDto h : manifest.handouts()) {
                assets.add(new ReadinessInputs.AssetInput(
                        null, h.title(), kindOf(h), safetyOf(h), h.presented()));
            }
        }

        List<ReadinessInputs.OmissionInput> omissions = new ArrayList<>();
        if (manifest.metadata() != null && manifest.metadata().conversionOmissions() != null) {
            manifest.metadata().conversionOmissions().forEach(o ->
                    omissions.add(new ReadinessInputs.OmissionInput(o.area(), o.reason())));
        }

        return new ReadinessInputs(scenes, assets, links, omissions);
    }

    private static ReadinessInputs.SceneInput sceneInput(SceneDto scene) {
        boolean hostile = false;
        boolean anyStatblock = false;
        List<String> unresolved = new ArrayList<>();
        if (scene.participants() != null) {
            for (SceneParticipantDto p : scene.participants()) {
                boolean h = "HOSTILE".equals(p.disposition());
                hostile = hostile || h;
                if (p.statblockRef() != null) {
                    anyStatblock = true;
                } else if (h) {
                    unresolved.add(p.displayName() == null ? "Unnamed participant" : p.displayName());
                }
            }
        }
        SceneMapRequirement req = scene.mapRequirement() == null || scene.mapRequirement().isBlank()
                ? null : SceneMapRequirement.valueOf(scene.mapRequirement());
        return new ReadinessInputs.SceneInput(
                null, scene.title(), hostile,
                scene.encounterRef() != null, anyStatblock, unresolved,
                req, scene.mapRef() != null);
    }

    private static Handout.AssetKind kindOf(HandoutDto h) {
        return h.assetKind() == null || h.assetKind().isBlank()
                ? Handout.AssetKind.SOURCE_PAGE : Handout.AssetKind.valueOf(h.assetKind());
    }

    private static Handout.SafetyClassification safetyOf(HandoutDto h) {
        if (h.safetyClassification() != null) {
            return Handout.SafetyClassification.valueOf(h.safetyClassification());
        }
        return h.dmOnly() ? Handout.SafetyClassification.DM_SOURCE
                : Handout.SafetyClassification.UNREVIEWED;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=PreviewReadinessAssemblerTest`
Expected: PASS.

- [ ] **Step 5: Prove the two assemblers agree on the same shape**

Add a test asserting that a manifest with one hostile-no-encounter scene and the equivalent persisted campaign both yield `sessionReady() == false` with an `ENCOUNTER` blocker. Reuse the persisted fixture from Task 9 and the manifest from Step 1:

```java
    @Test
    void previewAndPersistedAgreeOnEncounterBlocker() {
        // preview side
        var participant = new CampaignManifestV2.SceneParticipantDto(
                "Klarg", 1, "HOSTILE", null, null, null, null, "loc", 0);
        var scene = new CampaignManifestV2.SceneDto("s1","Klarg","b","UNVISITED",0,null,null,null,
                List.of(),List.of(),"s","loc",List.of(),"r",List.of(),List.of(),
                List.of(participant),List.of(),List.of(),null);
        var report = new CampaignReadinessService()
                .compute(assembler.fromManifest(minimalManifest(List.of(scene), List.of(), List.of())),
                        java.util.Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.ENCOUNTER);
    }
```

Run: `./mvnw -q test -Dtest=PreviewReadinessAssemblerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/PreviewReadinessAssembler.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/PreviewReadinessAssemblerTest.java
git commit -m "feat(readiness): assemble report from an import manifest"
```

---

## Task 11: Campaign home readiness surface

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
- Create: `src/main/resources/templates/campaigns/_readiness.html`
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignHomeReadinessTest.java`

**Interfaces:**
- Consumes: `CampaignReadinessFacade.reportForCampaign(UUID)` (Task 9).
- Produces: model attribute `readiness` (a `CampaignReadinessReport`) on the `campaigns/detail` view; a `_readiness.html` fragment `readiness(report, campaignId)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignHomeReadinessTest {

    @Autowired MockMvc mvc;
    @Autowired CampaignFixtures fixtures;

    @Test
    void detailPageShowsReadinessLabelDistinctFromValidity() throws Exception {
        UUID id = fixtures.hostileSceneWithoutEncounter();
        mvc.perform(get("/campaigns/{id}", id))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("readiness"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not session-ready")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=CampaignHomeReadinessTest`
Expected: FAIL — no `readiness` attribute / string absent.

- [ ] **Step 3: Inject the facade and populate the model**

In `CampaignController.java` add the dependency (constructor + field, mirroring existing fields) and set the attribute in `detail`:

Add field: `private final CampaignReadinessFacade readinessFacade;`
Add constructor param and assignment: `this.readinessFacade = readinessFacade;`
In `detail(...)` before `return "campaigns/detail";` add:

```java
        model.addAttribute("readiness", readinessFacade.reportForCampaign(id));
```

Import: `import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;`

- [ ] **Step 4: Create the fragment**

`campaigns/_readiness.html`:

```html
<div th:fragment="readiness(report, campaignId)" class="readiness-panel"
     th:classappend="${report.sessionReady()} ? 'is-ready' : 'is-blocked'">
  <header class="readiness-panel__head">
    <h3>Session readiness</h3>
    <span class="readiness-badge" th:text="${report.label()}">Session-ready</span>
  </header>
  <p th:if="${report.sessionReady()}" class="readiness-empty">
    Every blocker is resolved or accepted. This is distinct from schema validity.
  </p>
  <ul th:unless="${report.sessionReady()}" class="readiness-list">
    <li th:each="item : ${report.byState(T(dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState).BLOCKER)}"
        class="readiness-item readiness-item--blocker">
      <span class="readiness-item__title" th:text="${item.title()}">Title</span>
      <span class="readiness-item__detail" th:text="${item.detail()}">Detail</span>
      <a class="readiness-item__repair"
         th:href="@{|/campaigns/${campaignId}/readiness/repair/${item.repairKind()}|(target=${item.targetId()})}"
         th:text="${item.repairKind()}">Repair</a>
    </li>
  </ul>
  <details class="readiness-advisories"
           th:with="advisories=${report.byState(T(dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState).RESOLVED)}"
           th:if="${!advisories.isEmpty()}">
    <summary th:text="|Notes & advisories (${advisories.size()})|">Notes &amp; advisories</summary>
    <ul>
      <li th:each="item : ${advisories}">
        <span th:text="${item.title()}">Advisory</span>
        <span class="readiness-item__detail" th:text="${item.detail()}">Detail</span>
      </li>
    </ul>
  </details>
</div>
```

Note: the exact repair `href` is finalized in Task 13; leave the placeholder link target as above so the panel renders. Task 13 replaces the `repairKind` label with a resolved deep-link. The advisories `<details>` block satisfies §6's requirement that advisories (unresolved runtime links, declared omissions, optional-map notes) appear in the report without blocking session-ready.

- [ ] **Step 5: Include the fragment in `detail.html`**

In `campaigns/detail.html`, at the top of the primary content column (mirror where `scale`/`sessionPlan` blocks are placed), add:

```html
  <div th:replace="~{campaigns/_readiness :: readiness(${readiness}, ${campaignId})}"></div>
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=CampaignHomeReadinessTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java \
        src/main/resources/templates/campaigns/_readiness.html \
        src/main/resources/templates/campaigns/detail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignHomeReadinessTest.java
git commit -m "feat(campaign): show readiness report on campaign home"
```

---

## Task 12: Import preview readiness surface

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreview.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java` (populate readiness when building the preview)
- Modify: `src/main/resources/templates/campaigns/_import-dialog.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/PreviewReadinessTest.java`

**Interfaces:**
- Consumes: `PreviewReadinessAssembler`, `CampaignReadinessService`, the parsed `CampaignManifestV2` available where the preview is built.
- Produces: `CampaignImportPreview.readiness()` returning a `CampaignReadinessReport` (never null; empty report when no manifest).

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewReadinessTest {

    @Test
    void previewCarriesAReadinessReport() {
        CampaignImportPreview preview = new CampaignImportPreview(
                java.util.UUID.randomUUID(), "READY", 3, 3, null, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), java.time.Instant.now(),
                new CampaignReadinessReport(List.of()));
        assertThat(preview.readiness()).isNotNull();
        assertThat(preview.readiness().sessionReady()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=PreviewReadinessTest`
Expected: FAIL — `CampaignImportPreview` has no `readiness` component.

- [ ] **Step 3: Add `readiness` to `CampaignImportPreview`**

Append `CampaignReadinessReport readiness` as the final component of the record and a back-compat constructor defaulting it to an empty report:

```java
public record CampaignImportPreview(
        UUID previewId, String status, int sourceFormatVersion, int targetFormatVersion,
        CampaignEntityCounts counts, long packageSizeBytes, long installedSizeBytes,
        int provenanceEntries, int missingProvenanceEntries,
        List<CampaignExportExclusion> exclusions, List<String> migrations,
        List<CampaignImportProblem> problems, Instant expiresAt,
        dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport readiness
) {
    public CampaignImportPreview(UUID previewId, String status, int sourceFormatVersion,
            int targetFormatVersion, CampaignEntityCounts counts, long packageSizeBytes,
            long installedSizeBytes, int provenanceEntries, int missingProvenanceEntries,
            List<CampaignExportExclusion> exclusions, List<String> migrations,
            List<CampaignImportProblem> problems, Instant expiresAt) {
        this(previewId, status, sourceFormatVersion, targetFormatVersion, counts, packageSizeBytes,
                installedSizeBytes, provenanceEntries, missingProvenanceEntries, exclusions,
                migrations, problems, expiresAt,
                new dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport(List.of()));
    }
}
```

- [ ] **Step 4: Populate readiness where the preview is assembled**

In `CampaignImportCoordinator` (where `CampaignImportPreview` is constructed from the parsed manifest), inject `PreviewReadinessAssembler` and `CampaignReadinessService`, then build the report and pass it as the final argument:

```java
        CampaignReadinessReport readiness = readinessService.compute(
                previewReadiness.fromManifest(manifest), java.util.Set.of());
```

Find the construction site: `grep -n 'new CampaignImportPreview(' src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java`. Replace the call with the 14-arg form passing `readiness`. Add the two constructor dependencies mirroring the existing wiring.

- [ ] **Step 5: Render readiness in the import dialog**

In `campaigns/_import-dialog.html`, where the preview result renders (schema validity/problems section), add a readiness block. If the dialog renders the preview via JSON + client template, add a section keyed on `preview.readiness`; if server-rendered with the preview model object, add:

```html
  <section class="import-readiness" th:if="${preview.readiness() != null}">
    <h4>Operational readiness</h4>
    <p class="import-readiness__label" th:text="${preview.readiness().label()}">Session-ready</p>
    <ul>
      <li th:each="item : ${preview.readiness().byState(T(dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState).BLOCKER)}"
          th:text="${item.title()}">Blocker</li>
    </ul>
  </section>
```

If the dialog is JSON-driven (the `CampaignPackageController` returns the preview as `ResponseEntity`), confirm the JSON now includes `readiness` (records serialize automatically) and add the equivalent block to the client-side rendering in the dialog's inline script instead. Inspect first: `grep -n 'readiness\|preview\.' src/main/resources/templates/campaigns/_import-dialog.html`.

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=PreviewReadinessTest`
Expected: PASS.

- [ ] **Step 7: Confirm the preview endpoint still works end-to-end**

Run: `./mvnw -q test -Dtest=*PackageController*,*ImportCoordinator*`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreview.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java \
        src/main/resources/templates/campaigns/_import-dialog.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/PreviewReadinessTest.java
git commit -m "feat(package): show operational readiness in import preview"
```

---

## Task 13: Gap repair controls and deep-links

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/web/ReadinessController.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessRepairService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessItem.java` (add `repairHref` resolution helper) OR resolve in the template
- Modify: `src/main/resources/templates/campaigns/_readiness.html` (real deep-links + accept/classify controls)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/web/ReadinessControllerTest.java`

**Interfaces:**
- Consumes: `HandoutRepository`, `ReadinessAcknowledgementRepository`, `Handout.AssetKind`, `CampaignReadinessFacade`.
- Produces:
  - `POST /campaigns/{campaignId}/readiness/assets/{handoutId}/kind` with form param `kind` — sets `Handout.assetKind`; setting `DM_REFERENCE` is the "mark map non-playable" action; returns the refreshed `_readiness` fragment.
  - `POST /campaigns/{campaignId}/readiness/accept` with form param `itemKey` — upserts a `ReadinessAcknowledgement`; returns the refreshed fragment.
  - `DELETE /campaigns/{campaignId}/readiness/accept` with param `itemKey` — removes an acknowledgement.
  - A `ReadinessRepairService` mapping `(ReadinessRepairKind, targetId)` → a deep-link href string for the template.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness.web;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessAcknowledgementRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReadinessControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CampaignFixtures fixtures;
    @Autowired HandoutRepository handouts;
    @Autowired ReadinessAcknowledgementRepository acks;
    @Autowired CampaignReadinessFacade facade;

    @Test
    void classifyingAnAssetKindPersists() throws Exception {
        var f = fixtures.campaignWithUnclassifiedHandout();
        mvc.perform(post("/campaigns/{c}/readiness/assets/{h}/kind", f.campaignId(), f.handoutId())
                .param("kind", "DM_REFERENCE"))
                .andExpect(status().isOk());
        assertThat(handouts.findById(f.handoutId()).orElseThrow().getAssetKind())
                .isEqualTo(Handout.AssetKind.DM_REFERENCE);
    }

    @Test
    void acceptingAnItemReachesSessionReady() throws Exception {
        UUID campaignId = fixtures.hostileSceneWithoutEncounter();
        String key = facade.reportForCampaign(campaignId)
                .byState(dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState.BLOCKER)
                .get(0).key();
        mvc.perform(post("/campaigns/{c}/readiness/accept", campaignId).param("itemKey", key))
                .andExpect(status().isOk());
        assertThat(acks.existsByCampaignIdAndItemKey(campaignId, key)).isTrue();
        assertThat(facade.reportForCampaign(campaignId).sessionReady()).isTrue();
    }
}
```

Add the fixture helpers `campaignWithUnclassifiedHandout()` (returning a small record with `campaignId()` and `handoutId()`) and reuse `hostileSceneWithoutEncounter()` from Task 9 in the shared `CampaignFixtures`. If `CampaignFixtures` does not exist, create it under `src/test/java/dev/hendrikhoemberg/dmhelper/support/` as a `@TestComponent`/`@Component` building state through existing services, and register it in the test context the same way other support helpers are.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ReadinessControllerTest`
Expected: FAIL — controller does not exist.

- [ ] **Step 3: Create the repair service (deep-link resolution)**

`ReadinessRepairService.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ReadinessRepairService {

    /** Resolve a readiness item to a concrete href on an existing preparation surface. */
    public String hrefFor(UUID campaignId, ReadinessRepairKind kind, UUID targetId) {
        return switch (kind) {
            case SEED_ENCOUNTER, EDIT_SCENE_PARTICIPANTS, ASSIGN_SCENE_MAP ->
                    "/campaigns/" + campaignId + "/scenes/" + targetId;
            case REVIEW_HANDOUT_SAFETY, CLASSIFY_ASSET_KIND ->
                    "/campaigns/" + campaignId + "/handouts/" + targetId;
            case ACCEPT_ITEM, NONE -> null;
        };
    }
}
```

Confirm the exact scene and handout preparation routes: `grep -rn '@GetMapping' src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/*.java`. Replace the two path templates above with the real ones (e.g. the scene detail/edit path and the handout review path from A2). Do not invent routes.

- [ ] **Step 4: Create the controller**

`web/ReadinessController.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness.web;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.*;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/readiness")
public class ReadinessController {

    private final CampaignReadinessFacade facade;
    private final HandoutRepository handouts;
    private final ReadinessAcknowledgementRepository acknowledgements;
    private final ReadinessRepairService repairService;

    public ReadinessController(CampaignReadinessFacade facade, HandoutRepository handouts,
                               ReadinessAcknowledgementRepository acknowledgements,
                               ReadinessRepairService repairService) {
        this.facade = facade;
        this.handouts = handouts;
        this.acknowledgements = acknowledgements;
        this.repairService = repairService;
    }

    @PostMapping("/assets/{handoutId}/kind")
    @Transactional
    public String classifyAsset(@PathVariable UUID campaignId, @PathVariable UUID handoutId,
                                @RequestParam String kind, Model model) {
        Handout handout = handouts.findById(handoutId).orElseThrow();
        if (!handout.getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("Handout does not belong to campaign");
        }
        handout.setAssetKind(Handout.AssetKind.valueOf(kind));
        handouts.save(handout);
        return renderFragment(campaignId, model);
    }

    @PostMapping("/accept")
    @Transactional
    public String accept(@PathVariable UUID campaignId, @RequestParam String itemKey, Model model) {
        if (!acknowledgements.existsByCampaignIdAndItemKey(campaignId, itemKey)) {
            var ack = new ReadinessAcknowledgement();
            ack.setCampaignId(campaignId);
            ack.setItemKey(itemKey);
            ack.setAcceptedAt(Instant.now());
            acknowledgements.save(ack);
        }
        return renderFragment(campaignId, model);
    }

    @DeleteMapping("/accept")
    @Transactional
    public String unaccept(@PathVariable UUID campaignId, @RequestParam String itemKey, Model model) {
        acknowledgements.deleteByCampaignIdAndItemKey(campaignId, itemKey);
        return renderFragment(campaignId, model);
    }

    private String renderFragment(UUID campaignId, Model model) {
        model.addAttribute("readiness", facade.reportForCampaign(campaignId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("repairService", repairService);
        return "campaigns/_readiness :: readiness(${readiness}, ${campaignId})";
    }
}
```

- [ ] **Step 5: Wire real deep-links and controls into the fragment**

Update `campaigns/_readiness.html` so each **blocker** row shows a deep-link (when one exists) *and* an "Accept as explicit choice" control — every blocker must be acceptable, which is how zero-map/zero-encounter campaigns reach session-ready. Replace the blocker `<li>` body (the repair `<a>` from Task 11) with:

```html
      <a th:with="href=${repairService.hrefFor(campaignId, item.repairKind(), item.targetId())}"
         th:if="${href != null}" th:href="${href}" class="readiness-item__repair">Fix in preparation</a>
      <form th:action="@{|/campaigns/${campaignId}/readiness/accept|}" method="post"
            th:hx-post="@{|/campaigns/${campaignId}/readiness/accept|}" hx-target="closest .readiness-panel">
        <input type="hidden" name="itemKey" th:value="${item.key()}"/>
        <button type="submit">Accept as an explicit choice</button>
      </form>
```

For an asset blocker (`repairKind == CLASSIFY_ASSET_KIND` or `REVIEW_HANDOUT_SAFETY`) also offer the inline kind control, which is how a DM marks a map non-playable (`DM_REFERENCE`):

```html
      <form th:if="${item.category().name() == 'ASSET'}"
            th:action="@{|/campaigns/${campaignId}/readiness/assets/${item.targetId()}/kind|}" method="post"
            th:hx-post="@{|/campaigns/${campaignId}/readiness/assets/${item.targetId()}/kind|}" hx-target="closest .readiness-panel">
        <select name="kind">
          <option th:each="k : ${T(dev.hendrikhoemberg.dmhelper.handout.data.Handout.AssetKind).values()}"
                  th:value="${k}" th:text="${k}"></option>
        </select>
        <button type="submit">Set kind</button>
      </form>
```

Add `repairService` to the model in both `CampaignController.detail` (`model.addAttribute("repairService", readinessRepairService)` — inject `ReadinessRepairService` there) and `ReadinessController.renderFragment`. Keep the markup consistent with existing htmx usage — inspect `session/modules/_quick-notes.html` for the exact `hx-*` attribute style and match it.

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ReadinessControllerTest`
Expected: PASS (both cases).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessRepairService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/web/ReadinessController.java \
        src/main/resources/templates/campaigns/_readiness.html \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/web/ReadinessControllerTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/CampaignFixtures.java
git commit -m "feat(readiness): add gap repair controls and deep-links"
```

---

## Task 14: Fixture expansion and coverage

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java` (count new fields)
- Modify: the synthetic fixture package builder used by `FixtureShapeCoverageTest` (find in Step 1)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReadinessFixtureShapeTest.java`

**Interfaces:**
- Consumes: `AssetKind`, `SceneMapRequirement`, `ConversionOmissionDto`.
- Produces: a synthetic fixture that exercises both a **not-session-ready** and a **ready-after-repair** shape, covering asset kinds, map requirements, omissions, and seedable vs non-seedable hostile scenes — counts/booleans only.

- [ ] **Step 1: Write the failing coverage test**

```java
package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReadinessFixtureShapeTest {

    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignFixtures fixtures;

    @Test
    void notReadyFixtureHasBlockers() {
        UUID id = fixtures.operationalFixtureNotReady();
        assertThat(facade.reportForCampaign(id).sessionReady()).isFalse();
    }

    @Test
    void readyAfterRepairFixtureIsSessionReady() {
        UUID id = fixtures.operationalFixtureReadyAfterRepair();
        assertThat(facade.reportForCampaign(id).sessionReady()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ReadinessFixtureShapeTest`
Expected: FAIL — the two fixture builders do not exist.

- [ ] **Step 3: Build the two fixture shapes**

In `CampaignFixtures`, add:
- `operationalFixtureNotReady()` — a campaign with: one hostile scene with a HOSTILE participant and no statblock and no encounter (encounter + statblock blockers), one hostile scene declaring `mapRequirement=REQUIRED` with no map (map blocker), one presented handout with `SafetyClassification.UNREVIEWED` + `AssetKind.SOURCE_PAGE` (asset blocker), and a declared omission acknowledged as none.
- `operationalFixtureReadyAfterRepair()` — the same shapes but every blocker resolved or accepted: participants carry statblocks, the required-map scene has a `GameMap`, the presented handout is `PLAYER_SAFE`, and the omission has a persisted `ReadinessAcknowledgement`.

Build state through existing services/repositories (the same ones Task 9's fixtures use). Commit only structural booleans/counts — no copyrighted text; scene bodies may be `"synthetic"`.

- [ ] **Step 4: Extend the shape profile coverage**

In `PackageShapeProfileExtractor`, add populated-field entries for `handout.assetKind`, `scene.mapRequirement`, and `metadata.conversionOmissions` so `FixtureShapeCoverageTest` asserts the synthetic package populates the new fields. Add the assertions to `FixtureShapeCoverageTest` mirroring its existing per-field checks.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=ReadinessFixtureShapeTest,FixtureShapeCoverageTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support/
git commit -m "test(readiness): cover not-ready and ready-after-repair fixture shapes"
```

---

## Task 15: Production parity, end-to-end acceptance, and documentation

**Files:**
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessProductionParityTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcceptanceTest.java`
- Modify: `docs/superpowers/plans/2026-07-24-c-campaign-operational-fidelity.md` (mark complete) and any developer README/notes touched by convention

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Write the production-parity test**

Assert readiness assembly does not trigger `LazyInitializationException` under `open-in-view=false`. Mirror A1's parity test setup (search `grep -rln 'open-in-view' src/test`).

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class ReadinessProductionParityTest {

    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignFixtures fixtures;

    @Test
    void reportAssemblesWithoutLazyAccessOutsideTransaction() {
        UUID id = fixtures.operationalFixtureNotReady();
        assertThatCode(() -> facade.reportForCampaign(id)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run it (red then green)**

Run: `./mvnw -q test -Dtest=ReadinessProductionParityTest`
Expected: PASS (the facade already wraps assembly in `@Transactional(readOnly = true)`; if it throws, move all lazy access inside the facade transaction and re-run).

- [ ] **Step 3: Write the end-to-end acceptance test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ReadinessAcceptanceTest {

    @Autowired MockMvc mvc;
    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignFixtures fixtures;

    @Test
    void resolvingAndAcceptingEveryBlockerReachesSessionReady() throws Exception {
        UUID id = fixtures.operationalFixtureNotReady();
        assertThat(facade.reportForCampaign(id).sessionReady()).isFalse();

        for (ReadinessItem blocker : facade.reportForCampaign(id).byState(ReadinessState.BLOCKER)) {
            mvc.perform(post("/campaigns/{c}/readiness/accept", id).param("itemKey", blocker.key()));
        }
        assertThat(facade.reportForCampaign(id).sessionReady()).isTrue();
    }
}
```

- [ ] **Step 4: Run it**

Run: `./mvnw -q test -Dtest=ReadinessAcceptanceTest`
Expected: PASS.

- [ ] **Step 5: Full build gate**

Run: `./mvnw -q verify`
Expected: BUILD SUCCESS — all modules, migrations `V23`–`V25` apply, Hibernate `validate` passes.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessProductionParityTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/ReadinessAcceptanceTest.java
git commit -m "test(readiness): prove production parity and session-ready acceptance"
```

---

## Final Acceptance Gate

The plan is complete when:

1. `./mvnw -q verify` is green with migrations `V23`–`V25` applied under `ddl-auto=validate`.
2. Campaign home (`/campaigns/{id}`) and the import preview both render a readiness report distinct from schema validity, labeled "Session-ready" or "Valid but not session-ready".
3. Every `Handout` carries an `AssetKind`; unknown kinds default to `SOURCE_PAGE`; kind and safety are independent.
4. New optional package fields round-trip; a `formatVersion: 2` package imports with conservative defaults.
5. Each blocker deep-links to a real preparation surface; the two gap controls (assign kind incl. `DM_REFERENCE`, accept item) function; resolving/accepting flips items and reaches session-ready.
6. The synthetic fixture exercises both not-ready and ready-after-repair shapes with no copyrighted content.

This plan does not claim the overall all-in-one premise; that remains gated by §11 of the master spec after workstreams D and E.
