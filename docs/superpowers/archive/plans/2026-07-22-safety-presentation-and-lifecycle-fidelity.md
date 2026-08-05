# Safety, Presentation Derivatives, and Lifecycle Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete corrective-spec workstream A2 by making the cockpit genuinely table-safe, allowing only reviewed or derived player assets to reach the external display, and producing accessible lifecycle dialogs and faithful session evidence.

**Architecture:** Add an explicit handout safety classification and provenance model in additive Flyway migration V18, then enforce it at one presentation boundary shared by the cockpit, preview, player file endpoint, and restored sessions. Replace the CSS-only DM Mode convention with a declarative runtime-module safety contract whose controller immediately hides and makes sensitive content inert. Keep lifecycle, timezone formatting, and encounter evidence server-owned; the browser remains a thin Alpine/htmx layer over existing Spring services.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Thymeleaf, Alpine.js, htmx, browser Canvas, JUnit 5, MockMvc, Mockito, AssertJ, Playwright.

## Global Constraints

- Workstream A remains a release blocker and lands before cockpit layout workstream B1.
- Production and test execution both use `spring.jpa.open-in-view=false`.
- No new Maven, npm, runtime, image-processing, docking, or frontend-framework dependency.
- Preserve the existing Spring/Thymeleaf/htmx/Alpine/Konva architecture.
- The external `/player` endpoint remains the authoritative server-filtered table projection.
- `DM_SOURCE` and `UNREVIEWED` assets are blocked from ordinary presentation.
- An emergency override never changes an asset's classification and always writes a durable session audit entry.
- Derivatives are non-destructive PNG assets; the source bytes and source database row remain unchanged.
- Table-safe state removes sensitive controls from keyboard focus immediately; an animation may decorate the transition but may not delay protection.
- Read-aloud content and the exact player projection remain visible in Table-safe state.
- Session time uses the configured `dmhelper.time-zone`, includes its zone label, and prints both dates across local midnight.
- Encounter summaries reduce ordered `DEFEATED`/`REVIVED` evidence to final state and retain name snapshots even if a combatant is removed.
- Do not commit private campaign packages, source PDFs, derived campaign images, or `artifacts/` screenshots.
- Follow TDD: run every specified red test and observe the described failure before changing production code.

---

## File Structure

### Database and domain

- Create `src/main/resources/db/migration/V18__add_handout_safety_and_session_audit.sql`
  - Adds handout classification/provenance and durable session audit entries without dropping legacy columns.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/Handout.java`
  - Owns `SafetyClassification`, source relationship, derivative recipe, and the `isPresentable()` invariant.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionAuditEntry.java`
  - Stores emergency presentation evidence for one campaign-session run.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionAuditEntryRepository.java`
  - Supplies chronological audit evidence to presentation and session-draft services.

### Handout safety and presentation

- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java`
  - Owns classification transitions, safe derivative file creation, rollback cleanup, and campaign scoping.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java`
  - Exposes review/classification and derivative creation to the server-rendered gallery.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutApiController.java`
  - Returns DTOs rather than detached JPA entities and removes the unsafe boolean toggle contract.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutViewDto.java`
  - Stable JSON shape for gallery and cockpit consumers.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/FileServeController.java`
  - Serves live player bytes only for the currently authorized presentation and preview bytes only through the DM route.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationService.java`
  - Centralizes safe presentation, preview projection, emergency acknowledgement, audit, and restore-time revalidation.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java`
  - Adds the authorized file URL to `HandoutRef` so live and preview states share the same renderer.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/CampaignTableController.java`
  - Adds exact handout preview and typed override fields to the presentation request.
- Modify `src/main/resources/static/js/player/player-view.js`
  - Delegates handout DOM creation to the shared renderer and uses the server-authorized `fileUrl`.
- Create `src/main/resources/static/js/player/handout-renderer.js`
  - Exports the DOM-safe handout renderer used by both `/player` and cockpit preview.
- Create `src/main/resources/static/js/handout-derivative-editor.js`
  - Produces an accessible crop/redaction PNG and recipe using browser Canvas.
- Create `src/main/resources/templates/handout/_derivative-dialog.html`
  - Numeric and pointer controls for crop/redaction without requiring drag input.
- Create `src/main/resources/templates/session/_presentation-preview.html`
  - Uses the same player renderer in preview mode and owns the two-step emergency confirmation.
- Modify `src/main/resources/templates/handout/_card.html`, `handout/list.html`, and `session/cockpit.html`
  - Replace “Make player-visible” with explicit review, derivative, preview, and presentation states.

### Screen safety

- Create `src/main/resources/static/js/screen-safety.js`
  - Applies `PRIVATE`/`TABLE_SAFE`, validates module declarations, and synchronously manages `inert` and `aria-hidden`.
- Modify `src/main/resources/templates/session/cockpit.html`
  - Declares runtime-module safety behavior and renders the command-bar safety control.
- Modify the current runtime fragments under `src/main/resources/templates/session/`, `notes/_quicknotes-strip.html`, and `encounter/_tracker.html`
  - Mark sensitive subtrees through the declarative contract, not a free-standing CSS naming convention.
- Modify `src/main/resources/static/css/base.css` and `cockpit.css`
  - Style the Table-safe shield state and generic safety attributes.
- Modify `src/main/resources/static/js/map/battle-map.js`, `session-cockpit.js`, `ui-elevation.js`, and `keyboard.js`
  - Replace DM Mode names/events with Screen safety names while preserving the existing shortcut.

### Lifecycle and evidence

- Modify `src/main/resources/templates/session/_lifecycle-dialog.html`
  - Uses native `<dialog>` so closed content never enters layout and the rest of the page becomes inert.
- Modify `src/main/resources/static/js/session-cockpit.js` and `static/css/cockpit.css`
  - Own native modal open/close, focus trap/restoration, bounded scrolling, and reduced motion.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/session/config/SessionConfig.java`
  - Provides the configured application `ZoneId`.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`
  - Formats local time ranges, includes safety overrides, and reduces final encounter state.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
  - Writes combatant name snapshots in defeat/revive evidence.

### Package compatibility and documentation

- Modify `CampaignManifestV2.java`, `campaign-format-v2.schema.json`, `HandoutSectionAdapter.java`, `SessionSectionAdapter.java`, and `LegacyV1ToV2Migration.java`
  - Round-trip optional A2 fields while safely defaulting old packages.
- Modify `docs/dm-manual/03-session-cockpit.md`, `06-handouts.md`, and `10-campaign-packages.md`
  - Document Screen safety, classification, derivatives, overrides, timezone output, and compatibility.

---

### Task 1: Add the V18 Safety and Audit Schema

**Files:**
- Create: `src/main/resources/db/migration/V18__add_handout_safety_and_session_audit.sql`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/Handout.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionAuditEntry.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionAuditEntryRepository.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/data/HandoutSafetyPersistenceTest.java`

**Interfaces:**
- Produces: `Handout.SafetyClassification { DM_SOURCE, PLAYER_SAFE, PLAYER_DERIVATIVE, UNREVIEWED }`.
- Produces: `boolean Handout.isPresentable()` and `boolean Handout.isDerivative()`.
- Produces: `SessionAuditEntry.EntryType.PRESENTATION_OVERRIDE` and `SessionAuditEntryRepository.findBySession_IdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(UUID, Instant, Instant)`.
- Migration compatibility: every existing `dm_only=true` row becomes `DM_SOURCE`; every existing `dm_only=false` row becomes `UNREVIEWED`. No existing row is inferred player-safe.

- [ ] **Step 1: Write red migration and persistence tests**

Add these assertions to `FlywayMigrationTest`:

```java
@Test
void v18AddsSafetyClassificationAndSessionAudit() {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_name='HANDOUT' AND column_name='SAFETY_CLASSIFICATION'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_name='SESSION_AUDIT_ENTRY'",
            Integer.class)).isEqualTo(1);
}
```

In `FlywayLegacyUpgradeTest`, insert one `dm_only=true` and one `dm_only=false` handout in the legacy setup, then assert:

```java
@Test
void v18DefaultsLegacyHandoutsWithoutTrustingTheOldPlayerVisibleToggle() {
    assertThat(jdbc.queryForList(
            "SELECT safety_classification FROM handout ORDER BY title", String.class))
            .containsExactly("DM_SOURCE", "UNREVIEWED");
}
```

Create `HandoutSafetyPersistenceTest` with a derivative/source pair and one audit entry. Assert the enum values, source ID, recipe JSON, audit content ID, and chronological repository query.

- [ ] **Step 2: Run the red tests**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,HandoutSafetyPersistenceTest test
```

Expected: compilation fails because `SafetyClassification` and the audit types do not exist; after test compilation is temporarily limited to the Flyway tests, they fail because V18 has not created the columns/table.

- [ ] **Step 3: Create the additive migration**

Create `V18__add_handout_safety_and_session_audit.sql`:

```sql
alter table handout add column safety_classification varchar(24) not null default 'UNREVIEWED';
alter table handout add column source_handout_id uuid;
alter table handout add column derivative_recipe CLOB;

update handout
set safety_classification = case when dm_only then 'DM_SOURCE' else 'UNREVIEWED' end;

alter table handout add constraint fk_handout_source
    foreign key (source_handout_id) references handout on delete restrict;
alter table handout add constraint ck_handout_safety_classification
    check (safety_classification in ('DM_SOURCE','PLAYER_SAFE','PLAYER_DERIVATIVE','UNREVIEWED'));
alter table handout add constraint ck_handout_derivative_source
    check ((safety_classification = 'PLAYER_DERIVATIVE' and source_handout_id is not null and derivative_recipe is not null)
        or (safety_classification <> 'PLAYER_DERIVATIVE' and source_handout_id is null and derivative_recipe is null));
create index idx_handout_source on handout (source_handout_id);

create table session_audit_entry (
    id uuid not null,
    session_id uuid not null,
    entry_type varchar(32) not null,
    content_type varchar(32) not null,
    content_id uuid not null,
    details CLOB not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_session_audit_session foreign key (session_id)
        references campaign_session on delete cascade,
    constraint ck_session_audit_type check (entry_type in ('PRESENTATION_OVERRIDE'))
);
create index idx_session_audit_order on session_audit_entry (session_id, created_at, id);
```

- [ ] **Step 4: Implement the domain invariants**

Add to `Handout`:

```java
public enum SafetyClassification {
    DM_SOURCE, PLAYER_SAFE, PLAYER_DERIVATIVE, UNREVIEWED;

    public boolean isPresentable() {
        return this == PLAYER_SAFE || this == PLAYER_DERIVATIVE;
    }
}

@Enumerated(EnumType.STRING)
@Column(name = "safety_classification", nullable = false, length = 24)
private SafetyClassification safetyClassification = SafetyClassification.UNREVIEWED;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "source_handout_id")
private Handout sourceHandout;

@Column(name = "derivative_recipe", columnDefinition = "CLOB")
private String derivativeRecipe;

public boolean isPresentable() { return safetyClassification.isPresentable(); }
public boolean isDerivative() { return safetyClassification == SafetyClassification.PLAYER_DERIVATIVE; }
```

Create `SessionAuditEntry` as a JPA entity with UUID ID, lazy `CampaignSession session`, enum `EntryType`, `contentType`, `contentId`, CLOB `details`, and immutable `createdAt`. Its `@PrePersist` sets `createdAt = Instant.now()` only when no explicit test timestamp was supplied. Presentation override details use the JSON shape `{"title":"Scanned page 12","classification":"DM_SOURCE"}` so later reporting never depends on mutable handout state.

Create the repository method exactly as declared in the Interfaces block.

- [ ] **Step 5: Run migration, legacy-upgrade, and persistence tests**

Run the command from Step 2.

Expected: all three classes pass; legacy rows preserve files and boolean columns while receiving conservative classifications.

- [ ] **Step 6: Commit the schema foundation**

```bash
git add src/main/resources/db/migration/V18__add_handout_safety_and_session_audit.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/Handout.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionAuditEntry.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionAuditEntryRepository.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/handout/data/HandoutSafetyPersistenceTest.java
git commit -m "feat(handout): add asset safety classification"
```

---

### Task 2: Preserve Safety Metadata Through Campaign Packages

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/HandoutSectionAdapterTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java`

**Interfaces:**
- Produces: optional `HandoutDto.safetyClassification`, `sourceRef`, and `derivativeRecipe` fields.
- Compatibility fallback: old V2 `dmOnly=true` imports as `DM_SOURCE`; `dmOnly=false` imports as `UNREVIEWED`.
- A package may never import `presented=true` for an unsafe classification.

- [ ] **Step 1: Add red adapter and schema tests**

Extend `HandoutSectionAdapterTest` with a source and derivative. Assert export contains:

```java
assertThat(derivativeDto.safetyClassification()).isEqualTo("PLAYER_DERIVATIVE");
assertThat(derivativeDto.sourceRef().key()).isEqualTo(sourceDto.key());
assertThat(derivativeDto.derivativeRecipe()).contains("cropWidth");
```

Add an old-V2 import case whose handout has `dmOnly=false` and no new fields; assert its imported classification is `UNREVIEWED` and `presented` is false. Add schema contract assertions for all four enum values and optional source/recipe fields.

- [ ] **Step 2: Run the red package tests**

```bash
./mvnw -Dtest=HandoutSectionAdapterTest,CampaignManifestV2ContractTest,CampaignCompleteRoundTripTest test
```

Expected: compilation fails because the DTO accessors and constructor fields do not exist.

- [ ] **Step 3: Extend the manifest and schema**

Change `HandoutDto` to:

```java
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
        String derivativeRecipe
) {}
```

Add optional schema properties:

```json
"safetyClassification": {
  "type": "string",
  "enum": ["DM_SOURCE", "PLAYER_SAFE", "PLAYER_DERIVATIVE", "UNREVIEWED"]
},
"sourceRef": { "$ref": "#/$defs/contentReference" },
"derivativeRecipe": { "type": "string", "minLength": 2 }
```

Do not add the optional fields to the schema's `required` array; old V2 packages remain valid.

- [ ] **Step 4: Implement two-pass derivative import**

Export the classification and recipe, and use `context.packageRef(CampaignContentType.HANDOUT, source.getId(), source.getTitle())` for derivatives. During import:

1. create every file/row with its conservative classification and register every package key;
2. in a second loop resolve `sourceRef`, set the source and recipe, and validate that only `PLAYER_DERIVATIVE` has both;
3. set `presented` only when `classification.isPresentable()`.

Legacy V1 migration constructs `HandoutDto` with `DM_SOURCE`, null source, and null recipe. Update every direct `HandoutDto` constructor in tests with explicit values rather than overloaded constructors.

- [ ] **Step 5: Run package tests and the semantic round trip**

Run the command from Step 2.

Expected: zero failures; source/derivative relationships and conservative old-package defaults survive export/import.

- [ ] **Step 6: Commit package compatibility**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java \
  src/main/resources/schemas/campaign-format-v2.schema.json \
  src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/HandoutSectionAdapterTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java
git commit -m "feat(package): round trip handout safety metadata"
```

---

### Task 3: Replace the Unsafe Boolean Toggle With Explicit Review

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutViewDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutApiController.java`
- Modify: `src/main/resources/templates/handout/_card.html`
- Modify: `src/main/resources/templates/handout/list.html`
- Replace tests in: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutDmOnlyToggleTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutServiceTest.java`

**Interfaces:**
- Produces: `Handout classify(UUID campaignId, UUID handoutId, SafetyClassification classification)`.
- Produces: `HandoutViewDto from(Handout)` with IDs, title, content type, classification, presentability, derivative source ID, and presented state.
- Permitted manual targets: `DM_SOURCE`, `PLAYER_SAFE`, `UNREVIEWED`. `PLAYER_DERIVATIVE` is created only by the derivative workflow.

- [ ] **Step 1: Write failing classification tests**

Replace toggle assertions with:

```java
@Test
void unreviewedUploadCannotBecomePlayerVisibleThroughTheLegacyBooleanRoute() throws Exception {
    mvc.perform(put("/campaigns/{c}/handouts/{h}/dm-only", campaignId, handoutId)
            .param("dmOnly", "false"))
            .andExpect(status().isNotFound());
}

@Test
void explicitReviewCanClassifyAnAssetPlayerSafe() throws Exception {
    mvc.perform(put("/campaigns/{c}/handouts/{h}/classification", campaignId, handoutId)
            .param("classification", "PLAYER_SAFE"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Reviewed player-safe")));
    assertThat(handouts.findById(handoutId).orElseThrow().isPresentable()).isTrue();
}
```

Add service tests for cross-campaign rejection, unsupported manual `PLAYER_DERIVATIVE`, and automatic curtain/detach when a presented safe asset becomes unsafe.

- [ ] **Step 2: Run the red tests**

```bash
./mvnw -Dtest=HandoutServiceTest,HandoutDmOnlyToggleTest,HandoutControllerTest test
```

Expected: the new classification route is 404 and the old route still returns success.

- [ ] **Step 3: Implement the classification transition**

`classify` must load by both campaign and handout ID, reject `PLAYER_DERIVATIVE`, clear source/recipe for the three permitted targets, synchronize legacy `dmOnly` to `!classification.isPresentable()`, and detach an unsafe handout from current session presentation before saving.

Create this DTO instead of serializing `Handout`:

```java
public record HandoutViewDto(
        UUID id,
        String title,
        String contentType,
        String safetyClassification,
        boolean presentable,
        boolean presented,
        UUID sourceHandoutId
) {
    public static HandoutViewDto from(Handout handout) {
        return new HandoutViewDto(handout.getId(), handout.getTitle(), handout.getContentType(),
                handout.getSafetyClassification().name(), handout.isPresentable(),
                handout.isPresented(), handout.getSourceHandout() == null
                        ? null : handout.getSourceHandout().getId());
    }
}
```

The upload path keeps its default `UNREVIEWED`. Remove both MVC/API `dm-only` mutation routes; return DTOs from `HandoutApiController`.

- [ ] **Step 4: Replace gallery language and actions**

Render exactly one classification badge. Unsafe cards offer **Review as player-safe**, **Mark as DM source**, and **Create player derivative**. Safe cards offer **Preview player output** and **Mark unreviewed**. Remove “Make player-visible”; classification and current presentation are separate concepts.

- [ ] **Step 5: Run classification tests**

Run the command from Step 2.

Expected: unsafe boolean routes are gone, explicit classification is campaign-scoped, and unsafe transitions invalidate current presentation.

- [ ] **Step 6: Commit explicit review**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/handout \
  src/main/resources/templates/handout \
  src/test/java/dev/hendrikhoemberg/dmhelper/handout
git commit -m "feat(handout): require explicit player safety review"
```

---

### Task 4: Create Non-Destructive Crop and Redaction Derivatives

**Files:**
- Create: `src/main/resources/static/js/handout-derivative-editor.js`
- Create: `src/main/resources/templates/handout/_derivative-dialog.html`
- Modify: `src/main/resources/templates/handout/list.html`
- Modify: `src/main/resources/templates/handout/_card.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutDerivativeServiceTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/HandoutDerivativeTemplateContractTest.java`

**Interfaces:**
- Consumes: `Handout.SafetyClassification.PLAYER_DERIVATIVE` and source relationship from Task 1.
- Produces: `Handout createDerivative(UUID campaignId, UUID sourceId, String title, String recipeJson, MultipartFile png)`.
- Recipe JSON shape: `{sourceWidth,sourceHeight,cropX,cropY,cropWidth,cropHeight,redactions:[{x,y,width,height}]}` using source-pixel coordinates.

- [ ] **Step 1: Write red service and template contracts**

The service test submits a real 2×2 PNG and asserts:

```java
assertThat(derivative.getSafetyClassification()).isEqualTo(PLAYER_DERIVATIVE);
assertThat(derivative.getSourceHandout().getId()).isEqualTo(source.getId());
assertThat(derivative.getContentType()).isEqualTo("image/png");
assertThat(handoutService.getFileContent(source.getId())).isEqualTo(originalBytes);
assertThat(handoutService.getFileContent(derivative.getId())).isEqualTo(derivedBytes);
```

Also assert rollback deletes the newly written PNG, cross-campaign sources are rejected, non-PNG output is rejected, and crop/redaction rectangles with non-positive dimensions are rejected.

The template contract requires numeric crop inputs, an editable redaction list, pointer handlers, a `canvas.toBlob(callback, 'image/png')` call, and a multipart POST. It must not require pointer dragging to submit valid coordinates.

- [ ] **Step 2: Run the red derivative tests**

```bash
./mvnw -Dtest=HandoutDerivativeServiceTest,HandoutDerivativeTemplateContractTest test
```

Expected: compilation fails because `createDerivative` and the editor assets do not exist.

- [ ] **Step 3: Implement server-side validation and atomic storage**

Parse the recipe with the existing Jackson mapper into:

```java
public record DerivativeRecipe(int sourceWidth, int sourceHeight,
        int cropX, int cropY, int cropWidth, int cropHeight,
        List<RedactionRect> redactions) {}
public record RedactionRect(int x, int y, int width, int height) {}
```

Require an image source, `image/png` output, a valid PNG signature, positive source/crop dimensions, crop bounds inside the source, and every redaction inside the crop. Use JDK `ImageIO` to verify the recipe's source dimensions against the stored source image and the submitted PNG dimensions against `cropWidth`/`cropHeight`; reject forged dimension metadata. Reuse `storeFile` and `registerRollbackCleanup`; persist the derivative only after the file is durable. Do not modify source classification, bytes, title, or tags.

- [ ] **Step 4: Implement the accessible Canvas editor**

The dialog loads `/files/{sourceId}` into an image and canvas. Numeric inputs are authoritative. Pointer drag updates those same inputs. Rendering performs:

```javascript
ctx.drawImage(image, cropX, cropY, cropWidth, cropHeight,
              0, 0, cropWidth, cropHeight);
for (const rect of redactions) {
    ctx.fillStyle = '#000';
    ctx.fillRect(rect.x - cropX, rect.y - cropY, rect.width, rect.height);
}
```

On save, call `canvas.toBlob`, append `file`, `title`, and serialized recipe to `FormData`, then POST to `/campaigns/{campaignId}/handouts/{sourceId}/derivatives`. Report failures with the shared toast/retry mechanism and redirect to the new card anchor on success.

- [ ] **Step 5: Run derivative tests and a browser smoke**

Run:

```bash
./mvnw -Dtest=HandoutDerivativeServiceTest,HandoutDerivativeTemplateContractTest,HandoutControllerTest test
```

Then use Playwright against a disposable synthetic campaign: crop an uploaded image, add a numeric redaction, save, and confirm the source and derivative cards display different file IDs.

Expected: tests pass; source bytes remain byte-identical; the derivative is immediately classified `PLAYER_DERIVATIVE`.

- [ ] **Step 6: Commit derivatives**

```bash
git add src/main/resources/static/js/handout-derivative-editor.js \
  src/main/resources/templates/handout \
  src/main/java/dev/hendrikhoemberg/dmhelper/handout \
  src/test/java/dev/hendrikhoemberg/dmhelper/handout
git commit -m "feat(handout): create safe image derivatives"
```

---

### Task 5: Enforce Exact Preview, Presentation, and Audited Override

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/CampaignTableController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/FileServeController.java`
- Create: `src/main/resources/static/js/player/handout-renderer.js`
- Modify: `src/main/resources/static/js/player/player-view.js`
- Create: `src/main/resources/templates/session/_presentation-preview.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/FileServeControllerSecurityTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/web/CampaignTableControllerTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/live/HandoutPreviewParityTest.java`

**Interfaces:**
- Produces: `HandoutRef(String id, String title, String contentType, String fileUrl)`.
- Produces: `HandoutPreview previewHandout(UUID campaignId, UUID handoutId)`.
- Produces: `LiveTableState presentHandout(UUID campaignId, UUID handoutId, boolean emergencyOverride, String acknowledgement)`.
- Required acknowledgement: `I understand this may expose DM content`.

- [ ] **Step 1: Write red authorization and parity tests**

Add service cases proving ordinary presentation rejects `DM_SOURCE` and `UNREVIEWED`, accepts both safe classifications, and restore lowers the curtain when a formerly safe handout is reclassified.

For override, assert the unsafe handout is presented only when both `emergencyOverride=true` and the exact acknowledgement are supplied; assert one `PRESENTATION_OVERRIDE` audit row has `contentType=HANDOUT`, the handout ID, and immutable JSON details containing the title and classification at override time. Verify its timestamp and confirm that classification is unchanged.

`HandoutPreviewParityTest` fetches the preview file bytes, performs an authorized live presentation, fetches `/player/files/{id}`, and asserts byte-for-byte equality and `Cache-Control: no-store` on both.

- [ ] **Step 2: Run the red presentation tests**

```bash
./mvnw -Dtest=TablePresentationServiceTest,FileServeControllerSecurityTest,CampaignTableControllerTest,HandoutPreviewParityTest test
```

Expected: unsafe `dmOnly=false` rows remain presentable, no preview endpoint exists, `HandoutRef` has no file URL, and no audit is written.

- [ ] **Step 3: Centralize authorization in `TablePresentationService`**

Add `requireCampaignHandout` exactly as a campaign-scoped lookup rather than relying on a caller-supplied ID:

```java
public record HandoutPreview(LiveTableState state, String classification,
                             boolean requiresOverride) {}

private Handout requireCampaignHandout(UUID campaignId, UUID handoutId) {
    return handoutRepository.findById(handoutId)
            .filter(handout -> handout.getCampaign().getId().equals(campaignId))
            .orElseThrow(() -> new NotFoundException("Handout not found in campaign"));
}
```

`previewHandout` never mutates session or broadcasts. It returns a `HANDOUT` state whose file URL is `/api/v1/campaigns/{campaignId}/table/handouts/{id}/preview-file`. Ordinary `presentHandout` requires `handout.isPresentable()`. Override requires an open session and exact acknowledgement, serializes `{"title": handout.getTitle(), "classification": handout.getSafetyClassification().name()}` into the audit details, saves and flushes that audit entry before updating session presentation, and leaves classification unchanged. Live projection uses `/player/files/{id}`.

- [ ] **Step 4: Make file delivery consume the same authorization**

Add the campaign-scoped preview-file GET. Both preview and player methods obtain the handout through service authorization, call the same `getFileContent(id)`, set the stored content type, and return `no-store`. The player route additionally requires `isCurrentlyPresentedHandout(id)`.

Create `player/handout-renderer.js` with a single DOM-safe implementation:

```javascript
export function renderHandout(container, state) {
    container.replaceChildren();
    const wrapper = document.createElement('div');
    wrapper.className = 'pv-handout';
    const image = document.createElement('img');
    image.src = state.handout.fileUrl;
    image.alt = state.handout.title || '';
    wrapper.appendChild(image);
    container.appendChild(wrapper);
}

window.dmhelperRenderHandout = renderHandout;
```

Import `renderHandout` from `player-view.js` and make its `showHandout` call that function. Load the same module from the cockpit and call `window.dmhelperRenderHandout(previewContainer, preview.state)` after the preview response arrives. Never accept a file URL from a request body; only server projection creates it.

- [ ] **Step 5: Add the two-stage cockpit preview**

Before every handout presentation, load `HandoutPreview`, pass `preview.state` to `window.dmhelperRenderHandout` in a bounded preview panel, and show classification. Safe content offers **Present to table**. Unsafe content offers **Present anyway…**, then a second panel containing the warning and **Confirm emergency presentation**. The final request sends the exact acknowledgement; closing either panel does not mutate presentation. Add `<script type="module" th:src="@{/js/player/handout-renderer.js}"></script>` to the cockpit so preview and `/player` execute the same renderer source.

- [ ] **Step 6: Run presentation tests and player security contracts**

```bash
./mvnw -Dtest=TablePresentationServiceTest,FileServeControllerSecurityTest,CampaignTableControllerTest,HandoutPreviewParityTest,PlayerViewSecurityContractTest test
```

Expected: unsafe ordinary requests are rejected, preview/live bytes match, override is audited, and player rendering remains DOM-safe.

- [ ] **Step 7: Commit the safe presentation boundary**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/live \
  src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/FileServeController.java \
  src/main/resources/static/js/player/handout-renderer.js \
  src/main/resources/static/js/player/player-view.js \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/templates/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/live \
  src/test/java/dev/hendrikhoemberg/dmhelper/handout/FileServeControllerSecurityTest.java
git commit -m "feat(presentation): enforce reviewed player output"
```

---

### Task 6: Replace DM Mode With a Declarative Screen-Safety Contract

**Files:**
- Create: `src/main/resources/static/js/screen-safety.js`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: runtime fragments under `src/main/resources/templates/session/`
- Modify: `src/main/resources/templates/notes/_quicknotes-strip.html`
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/static/js/ui-elevation.js`
- Modify: `src/main/resources/static/js/keyboard.js`
- Replace: `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmModeCoverageTest.java` with `ScreenSafetyCoverageTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmSensitiveFieldCoverageTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeModuleSafetyContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Modes: `PRIVATE` and `TABLE_SAFE`.
- Module declaration: `data-runtime-module="<stable-key>" data-table-safe-behavior="FILTER|HIDE|PLAYER_PROJECTION"`.
- Sensitive subtree declaration: `data-screen-sensitive`.
- Events: `screen-safety-toggle` and `screen-safety-changed` with `{ mode }`.

- [ ] **Step 1: Write red module and browser safety tests**

`RuntimeModuleSafetyContractTest` parses `cockpit.html` and all inserted runtime fragments. Assert stable unique keys for story, map, encounter, session-plan, party, quick-notes, presentation, audio, and session-log/lifecycle; assert every declaration uses one of the three behaviors.

In `CoreSessionLoopSmokeTest`, toggle Table-safe and assert:

```java
assertThat(dmPage.locator("[data-screen-sensitive]:visible").count()).isZero();
assertThat(dmPage.locator("[data-screen-sensitive] :focus").count()).isZero();
assertThat(dmPage.locator("[data-runtime-module='story'] .structured-read-aloud:visible").count())
        .isGreaterThan(0);
assertThat(dmPage.locator("[data-runtime-module='presentation'] iframe:visible").count())
        .isGreaterThan(0);
```

Use `Tab` repeatedly and collect active elements; none may be inside a sensitive subtree. Assert the command bar visibly says **Table-safe** and contains no “DM Mode” or “PLAYER-SAFE” copy.

- [ ] **Step 2: Run the red safety tests**

```bash
./mvnw -Dtest=RuntimeModuleSafetyContractTest,ScreenSafetyCoverageTest,DmSensitiveFieldCoverageTest,CoreSessionLoopSmokeTest test
```

Expected: compilation fails until the coverage test is renamed, then contract assertions fail because modules do not declare behavior and quick notes/session plan remain visible and focusable.

- [ ] **Step 3: Declare behavior on every current runtime module**

Use these initial behaviors:

| Module key | Behavior |
|---|---|
| `story` | `FILTER` |
| `map` | `FILTER` |
| `encounter` | `HIDE` |
| `session-plan` | `HIDE` |
| `party` | `FILTER` |
| `quick-notes` | `HIDE` |
| `presentation` | `PLAYER_PROJECTION` |
| `audio` | `FILTER` |
| `session-log` | `HIDE` |

Mark quick notes, unrevealed scene content, checks, participants/statblocks, transitions, hidden-token controls, map editing controls, party mechanical detail, and audio ownership/source fields with `data-screen-sensitive`. Do not mark read-aloud or the player-preview iframe.

- [ ] **Step 4: Implement synchronous protection**

`screen-safety.js` must set `body.dataset.screenSafety` before starting any visual sweep. For `HIDE`, set the module root `inert=true` and `aria-hidden=true`. For `FILTER`, do the same on every `[data-screen-sensitive]`. On `PRIVATE`, remove only attributes the controller owns. Validate unknown/missing behaviors by reporting a visible non-blocking error and defaulting that module to `HIDE`.

Generic CSS uses the declarations:

```css
body[data-screen-safety="TABLE_SAFE"] [data-table-safe-behavior="HIDE"],
body[data-screen-safety="TABLE_SAFE"] [data-screen-sensitive] {
  display: none !important;
}
```

The shield-steel command bar treatment keys from `body[data-screen-safety="TABLE_SAFE"]`; reduced-motion skips the decorative sweep but not the synchronous state change.

- [ ] **Step 5: Rename state throughout the active runtime**

Rename user copy, Alpine state, battle-map setter, body attributes, and custom events to Screen safety terminology. The keyboard shortcut remains `Ctrl+Shift+D` for compatibility but dispatches `screen-safety-toggle`. Do not leave a second legacy listener that can desynchronize the checkbox and runtime state.

- [ ] **Step 6: Run safety tests**

Run the command from Step 2.

Expected: all sensitive content is hidden and inert immediately; read-aloud and exact projection remain; every runtime module has an explicit behavior.

- [ ] **Step 7: Commit Screen safety**

```bash
git add src/main/resources/static/js src/main/resources/static/css \
  src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/common \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(session): enforce declarative screen safety"
```

---

### Task 7: Make Session Lifecycle a Native Accessible Modal

**Files:**
- Modify: `src/main/resources/templates/session/_lifecycle-dialog.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Produces: native `<dialog id="sessionLifecycleDialog">` controlled through `showModal()`/`close()`.
- Preserves: `openLifecycle()`, `closeLifecycle()`, `trapLifecycleFocus(event)`, and `x-ref="sessionButton"`.

- [ ] **Step 1: Write red structure and browser geometry tests**

The template contract requires `<dialog`, `@cancel.prevent`, `@click.self`, `showModal()`, `close()`, and no `x-show`/`:hidden` on the dialog.

The browser smoke runs at 1366×768 and 1920×1080. At each viewport it opens the lifecycle and asserts the geometry against the active viewport dimensions; the 1366×768 assertions are:

```java
BoundingBox box = lifecycle.boundingBox();
assertThat(box.x()).isGreaterThan(0);
assertThat(box.y()).isGreaterThan(0);
assertThat(box.x() + box.width()).isLessThanOrEqualTo(1366);
assertThat(box.y() + box.height()).isLessThanOrEqualTo(768);
assertThat(dmPage.evaluate("document.documentElement.scrollHeight"))
        .isEqualTo(dmPage.evaluate("document.documentElement.clientHeight"));
```

Assert initial focus, Tab/Shift+Tab trapping, Escape close, and restoration to the Session button.

- [ ] **Step 2: Run the red lifecycle tests**

```bash
./mvnw -Dtest=SessionCockpitTemplateContractTest,CoreSessionLoopSmokeTest test
```

Expected: contract fails because the fragment is a normal-flow `<div>` and no `showModal()` call exists.

- [ ] **Step 3: Convert the fragment and controller**

Change the existing outer element to `<dialog id="sessionLifecycleDialog" class="lifecycle-dialog" x-ref="lifecycleDialog">`, add `@cancel.prevent="closeLifecycle()"`, `@click.self="closeLifecycle()"`, `@keydown.tab="trapLifecycleFocus($event)"`, and `aria-labelledby="sessionLifecycleTitle"`, keep the existing header/body/footer markup inside a `lifecycle-dialog__panel`, and close with `</dialog>`. Remove `x-show` and `:hidden` from the dialog itself.

`openLifecycle` records `document.activeElement`, sets state, calls `showModal()`, and focuses the first enabled control on the next Alpine tick. `closeLifecycle` closes the dialog, clears state, and restores the recorded element if still connected, otherwise `sessionButton`.

- [ ] **Step 4: Add bounded modal styling**

Set `max-inline-size: min(42rem, calc(100vw - 2rem))`, `max-block-size: calc(100dvh - 2rem)`, internal `overflow:auto`, centered margins, shield border, and a fixed translucent `::backdrop`. Under `prefers-reduced-motion`, disable dialog transitions. `dialog:not([open]) { display:none }` guarantees it never contributes to layout.

- [ ] **Step 5: Run lifecycle tests**

Run the command from Step 2.

Expected: modal is centered and bounded, document height does not change, focus cannot escape, and Escape restores focus.

- [ ] **Step 6: Commit lifecycle fidelity**

```bash
git add src/main/resources/templates/session/_lifecycle-dialog.html \
  src/main/resources/static/js/session-cockpit.js src/main/resources/static/css/cockpit.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "fix(session): render lifecycle as accessible modal"
```

---

### Task 8: Format Session Time in the Configured Zone and Include Safety Audits

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application.properties`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/config/SessionConfig.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java`

**Interfaces:**
- Produces: named `ZoneId applicationZoneId` bean from `dmhelper.time-zone`.
- Produces: `String formatSessionRange(Instant startedAt, Instant endedAt)`.
- Consumes: chronological `SessionAuditEntryRepository` evidence.

- [ ] **Step 1: Write red same-day, cross-midnight, and audit tests**

Construct `SessionDraftService` with `ZoneId.of("Europe/Berlin")`. Assert:

```java
assertThat(service.formatSessionRange(
        Instant.parse("2026-07-16T18:00:00Z"),
        Instant.parse("2026-07-16T20:30:00Z")))
        .isEqualTo("16 July 2026, 20:00–22:30 Europe/Berlin");

assertThat(service.formatSessionRange(
        Instant.parse("2026-07-16T21:30:00Z"),
        Instant.parse("2026-07-16T22:30:00Z")))
        .isEqualTo("16 July 2026, 23:30 Europe/Berlin–17 July 2026, 00:30 Europe/Berlin");
```

Add one override audit and assert the draft contains `Presentation Safety Overrides` with asset title, classification, and local timestamp.

- [ ] **Step 2: Run the red draft tests**

```bash
./mvnw -Dtest=SessionDraftServiceTest test
```

Expected: UTC-formatted assertions fail and the audit section is absent.

- [ ] **Step 3: Add the zone bean and formatter**

Set `dmhelper.time-zone=Europe/Berlin` in production and test properties. Add:

```java
@Bean
ZoneId applicationZoneId(@Value("${dmhelper.time-zone}") String value) {
    return ZoneId.of(value);
}
```

Inject the bean into `SessionDraftService`. Format local `ZonedDateTime` values with English month names. Same local date prints the date once and zone ID once; different local dates print both complete date-times and the zone ID on both sides.

- [ ] **Step 4: Render override evidence in the draft**

Query the current session ID and time window. Add `Presentation Safety Overrides` after `Table Rolls`. Each line uses the immutable audit details and never reads the asset's current classification as historical truth.

- [ ] **Step 5: Run draft tests**

Run the command from Step 2.

Expected: exact same-day/cross-day strings and audit lines pass.

- [ ] **Step 6: Commit timezone and audit output**

```bash
git add src/main/resources/application.properties src/test/resources/application.properties \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/config/SessionConfig.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java
git commit -m "fix(session): record local time and safety overrides"
```

---

### Task 9: Retain Final Defeated State Through Real Tracker Endpoints

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java`

**Interfaces:**
- Defeat/revive payload: `{"name":"<combatant name>"}` plus future-compatible fields.
- Final reducer: ordered log entries produce one final defeated state per combatant ID and retain the latest nonblank name snapshot.
- No-evidence copy: `<encounter> — completed; no defeat or damage evidence recorded`.

- [ ] **Step 1: Write the red endpoint-driven integration test**

Use `@SpringBootTest` and `@AutoConfigureMockMvc`. Start a real session, create/activate an encounter, and drive:

```java
mvc.perform(post("/api/v1/combatants/{id}/damage", combatantId)
        .contentType(APPLICATION_JSON).content("{\"amount\":-999}"))
        .andExpect(status().isOk());
mvc.perform(put("/api/v1/combatants/{id}/defeated", combatantId)
        .contentType(APPLICATION_JSON).content("{\"defeated\":false}"))
        .andExpect(status().isOk());
mvc.perform(put("/api/v1/combatants/{id}/defeated", combatantId)
        .contentType(APPLICATION_JSON).content("{\"defeated\":true}"))
        .andExpect(status().isOk());
mvc.perform(delete("/api/v1/combatants/{id}", combatantId))
        .andExpect(status().isNoContent());
mvc.perform(post("/api/v1/encounters/{id}/end", encounterId))
        .andExpect(status().isOk());
```

Begin session review through the lifecycle service and assert the generated draft contains the removed combatant's name exactly once as finally defeated. Add a second endpoint-driven encounter with activate/end only and assert the explicit no-evidence copy.

- [ ] **Step 2: Run the red evidence tests**

```bash
./mvnw -Dtest=SessionDraftServiceTest,SessionEncounterEvidenceIntegrationTest test
```

Expected: the removed combatant name is absent because current code resolves names from the deleted row; the no-evidence phrase is absent.

- [ ] **Step 3: Snapshot names at the evidence source**

Replace `{}` defeat/revive payloads in automatic damage, explicit HP, and explicit mark-defeated paths with JSON produced by one helper:

```java
private String defeatedStatePayload(Combatant combatant) {
    try {
        return JSON_MAPPER.writeValueAsString(Map.of("name", combatant.getName()));
    } catch (Exception failure) {
        throw new IllegalStateException("Could not record defeated-state evidence", failure);
    }
}
```

Do not swallow serialization errors: failing to record evidence must roll back the combat mutation.

- [ ] **Step 4: Reduce final state from ordered immutable evidence**

Replace the ID-only reducer with a `LinkedHashMap<UUID, DefeatState>` carrying name and boolean. Apply entries in repository order. `DEFEATED` sets true, `REVIVED` sets false; malformed IDs are ignored with a warning. Resolve missing legacy names from remaining combatants only as a fallback. When no defeat names and no damage exist, append the explicit no-evidence copy.

- [ ] **Step 5: Run service and endpoint evidence tests**

Run the command from Step 2.

Expected: automatic defeat, revive, final defeat, combatant removal, and no-evidence completion all produce faithful draft lines through actual HTTP endpoints.

- [ ] **Step 6: Commit encounter evidence fidelity**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java
git commit -m "fix(session): preserve final encounter evidence"
```

---

### Task 10: Document, Verify, and Rehearse A2

**Files:**
- Modify: `docs/dm-manual/03-session-cockpit.md`
- Modify: `docs/dm-manual/06-handouts.md`
- Modify: `docs/dm-manual/10-campaign-packages.md`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Consumes every A2 interface delivered by Tasks 1–9.
- Produces release evidence for F3, F4, F5, F9, and F10 only; it does not claim the overall all-in-one release gate.

- [ ] **Step 1: Update operator documentation**

Document:

- Private versus Table-safe and the `Ctrl+Shift+D` shortcut;
- which runtime content disappears and why read-aloud/player preview remains;
- all four handout classifications;
- crop/redaction derivative provenance and source retention;
- exact preview, ordinary presentation, and two-confirmation audited override;
- configured timezone output and cross-midnight formatting;
- final defeated/revived evidence behavior;
- old package import defaulting to `DM_SOURCE`/`UNREVIEWED` rather than trusting `dmOnly=false`.

- [ ] **Step 2: Add the complete A2 browser scenario**

Extend `CoreSessionLoopSmokeTest` to:

1. toggle Table-safe and verify sensitive content is neither visible nor focusable;
2. verify read-aloud and exact player preview remain;
3. attempt ordinary presentation of an unreviewed handout and observe a non-2xx response with visible recovery;
4. create and preview a derivative, present it, and verify the player's fetched bytes equal preview bytes;
5. perform an emergency override and verify the second confirmation plus audit row;
6. open/close lifecycle with keyboard and verify geometry/focus;
7. complete a cross-midnight session with a defeat/revive/final-defeat sequence and inspect the saved log.

- [ ] **Step 3: Run focused A2 verification**

```bash
git diff --check
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,HandoutSafetyPersistenceTest,HandoutSectionAdapterTest,CampaignCompleteRoundTripTest,HandoutServiceTest,HandoutDerivativeServiceTest,HandoutDerivativeTemplateContractTest,TablePresentationServiceTest,FileServeControllerSecurityTest,HandoutPreviewParityTest,RuntimeModuleSafetyContractTest,ScreenSafetyCoverageTest,DmSensitiveFieldCoverageTest,SessionCockpitTemplateContractTest,SessionDraftServiceTest,SessionEncounterEvidenceIntegrationTest,CoreSessionLoopSmokeTest test
```

Expected: zero failures/errors and no `LazyInitializationException`.

- [ ] **Step 4: Run the complete suite**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`, zero failures, and zero errors with the normal documented command.

- [ ] **Step 5: Perform private-campaign acceptance without retaining repository artifacts**

Against a disposable copy of the private Phandelver campaign at 1366×768:

1. confirm quick notes, plan, quest mechanics, statblocks, hidden tokens, and encounter mechanics disappear immediately in Table-safe;
2. confirm keyboard Tab never enters those regions;
3. classify the scanned source page `DM_SOURCE` and confirm ordinary presentation is blocked;
4. crop/redact a player derivative, compare exact preview to `/player`, and present it;
5. run and complete the Klarg encounter including defeat, revive, and final defeat;
6. complete lifecycle review across a controlled local-midnight clock boundary;
7. confirm the session log includes both local dates, `Europe/Berlin`, final defeated names, and the emergency override only if one was exercised;
8. inspect browser/application logs for console errors, unhandled rejections, silent non-2xx requests, and lazy access.

Store any screenshots only under ignored `artifacts/`; do not add them to Git.

- [ ] **Step 6: Record rollback and migration evidence**

V18 is additive. A code rollback may leave its extra columns/table in place and legacy `dm_only` remains populated, but the old build must not be used for player presentation because it cannot enforce the new classification. Before rollback, lower the curtain for every open session. For a full database rollback, stop the app and restore the automatic pre-V18 backup; do not hand-edit Flyway history or drop V18 objects from a live database.

- [ ] **Step 7: Commit documentation and release evidence**

```bash
git add docs/dm-manual/03-session-cockpit.md docs/dm-manual/06-handouts.md \
  docs/dm-manual/10-campaign-packages.md \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "docs: verify A2 player safety workflow"
```

- [ ] **Step 8: Record final repository evidence**

```bash
git status --short
git log -10 --oneline
git ls-files artifacts
```

Expected: clean tracked state, ten A2 task commits, and no tracked `artifacts/` files. The next dependency-ordered plan is A3 initiative setup; B1 cockpit tiling starts only after A3 passes.
