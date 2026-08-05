# Map Editor Import-First Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the map grid editable after creation and make imported background maps easy to fit, calibrate, resize, and persist without changing the authoritative grid.

**Architecture:** Keep the existing Thymeleaf + Alpine shell and Konva `MapEditor` island. Put pure image/grid geometry in a small dependency-free ES module, persist grid changes through a transactional map-settings endpoint that updates `GameMap` and `MapDocument.grid` together, and keep ordinary image edits on the existing versioned document autosave path. Use Playwright browser contracts because the project intentionally has no Node/npm test toolchain.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring MVC/JPA, Jackson 3 (`tools.jackson`), H2/Flyway, Thymeleaf, Alpine.js, vanilla ES modules, Konva.js, JUnit 5, AssertJ, MockMvc, Playwright 1.54.0.

## Global Constraints

- Preserve Spring Boot 4.1.0 and Java 25; do not introduce Boot-3-era APIs.
- Use Jackson 3 imports from `tools.jackson`; do not add Jackson 2 dependencies.
- Do not add Node/npm, a bundler, or a runtime CDN; frontend assets remain vendored/static.
- Keep the map grid authoritative: image import, fitting, cropping, rotation, and calibration must not modify width, height, or cell size.
- Keep `GameMap.gridWidth/gridHeight/cellSizePx` equal to `MapDocument.grid` after every successful settings operation.
- Keep image geometry in grid-cell units and preserve the existing `ImageDto` data URL storage model; no asset-storage subsystem is part of this change.
- Preserve optimistic document versioning, debounced autosave, conflict backup behavior, campaign package round-trip, player-safe projections, and existing keyboard shortcuts.
- Use `apply_patch` for source edits and run the narrowest relevant test after each task.

---

## File Map

### Create

- `src/main/resources/static/js/map/geometry.js` — pure fit, crop, calibration, and bounds helpers used by `MapEditor`.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapSettingsCommand.java` — typed settings/crop/token-resolution request objects shared by service and controller.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapGridResizeService.java` — server-side document-grid validation and static-content crop logic.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapGridResizeServiceTest.java` — unit tests for document-grid and geometry persistence rules.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java` — focused Playwright acceptance test for the import-first editor workflow.

### Modify

- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java` — transactional map-settings update and token-resolution application.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java` — `PUT /api/v1/maps/{id}/settings`, request validation, response mapping.
- `src/main/resources/static/js/map/shared.js` — reusable map-boundary clipping/rendering helper for image overflow.
- `src/main/resources/static/js/map/map-editor.js` — settings commands, resize preview/apply, image inspector geometry, authoritative calibration, image lock synchronization, and settings save handling.
- `src/main/resources/templates/maps/editor.html` — Map and Background sidebar sections, confirmation dialogs, Alpine bridge state, and stable test selectors.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java` — service-level metadata/document consistency and optimistic-lock tests.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java` — settings endpoint request/response/conflict tests.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java` — editor template contract assertions for new configuration controls.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentContractTest.java` — image geometry/calibration round-trip assertions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java` — package export preserves calibrated image geometry.

---

### Task 1: Extract and lock down image/grid geometry math

**Files:**
- Create: `src/main/resources/static/js/map/geometry.js`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java` (geometry test method only)

**Interfaces:**
- Produces `fitInsideGeometry(gridWidth, gridHeight, imageWidth, imageHeight) -> {x, y, width, height}`.
- Produces `fillCoverGeometry(gridWidth, gridHeight, imageWidth, imageHeight) -> {x, y, width, height}`.
- Produces `calibratedImageGeometry(image, pointA, pointB, cellsBetween, cellSizePx) -> {x, y, width, height, scale}`.
- Produces `boundsImpact(document, gridWidth, gridHeight) -> {outsideCells, affectedShapes}`.

- [ ] **Step 1: Write the failing browser geometry contract.**

  Create a focused `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@ActiveProfiles("playwright")`, a `@LocalServerPort`, a Playwright Chromium fixture, and a campaign/map seeded through `CampaignRepository` and `GameMapService`. Navigate to the map editor and import the ES module from the served application:

  ```java
  Object result = page.evaluate("""
      async () => {
          const g = await import('/js/map/geometry.js');
          return {
              fit: g.fitInsideGeometry(30, 20, 1600, 900),
              fill: g.fillCoverGeometry(30, 20, 1600, 900),
              calibration: g.calibratedImageGeometry(
                  {x: 0, y: 0, width: 30, height: 20},
                  {x: 2, y: 3}, {x: 7, y: 3}, 5, 48)
          };
      }
      """);
  assertThat(result).isEqualTo(Map.of(
      "fit", Map.of("x", 0.0, "y", 1.5625, "width", 30.0, "height", 16.875),
      "fill", Map.of("x", -2.7777777778, "y", 0.0, "width", 35.5555555556, "height", 20.0),
      "calibration", Map.of("x", 0.0, "y", 0.0, "width", 30.0, "height", 20.0, "scale", 1.0)
  ));
  ```

  Use a tolerance comparison for floating-point values rather than exact equality.

- [ ] **Step 2: Run the focused test and verify it fails.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest#geometryHelpersUseTheAuthoritativeGrid test`

  Expected: FAIL because `/js/map/geometry.js` does not exist.

- [ ] **Step 3: Implement the minimal pure geometry module.**

  Implement the four exported functions with no DOM/Konva dependencies. `fitInsideGeometry` uses `Math.min(gridWidth / imageWidth, gridHeight / imageHeight)`, centers the result, and preserves aspect ratio. `fillCoverGeometry` uses `Math.max(gridWidth / imageWidth, gridHeight / imageHeight)`, centers the result, and may return negative x/y for clipped overflow. `calibratedImageGeometry` computes the current rendered distance in pixels, scales toward `cellsBetween * cellSizePx`, and adjusts x/y around `pointA` so point A remains fixed. `boundsImpact` walks terrain cells and shape bounding boxes against `[0, gridWidth) × [0, gridHeight)`.

- [ ] **Step 4: Run the focused test and verify it passes.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest#geometryHelpersUseTheAuthoritativeGrid test`

  Expected: PASS with no browser console or page errors.

- [ ] **Step 5: Commit the geometry boundary.**

  ```bash
  git add src/main/resources/static/js/map/geometry.js src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
  git commit -m "feat(map): extract import geometry helpers"
  ```

---

### Task 2: Add transactional authoritative-grid settings persistence

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapSettingsCommand.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapGridResizeService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapGridResizeServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`

**Interfaces:**
- `MapSettingsCommand(long expectedVersion, int gridWidth, int gridHeight, int cellSizePx, ResizeMode resizeMode, List<TokenResolution> tokenResolutions)`.
- `ResizeMode` values: `PRESERVE` and `CROP`.
- `TokenResolution(UUID tokenId, TokenAction action, int positionX, int positionY)`.
- `TokenAction` values: `MOVE` and `REMOVE`; `REMOVE` is the explicit implementation of “detach from the map” because `Token.map` is mandatory in the current model.
- `MapGridResizeService.resize(MapDocumentDto document, int width, int height, ResizeMode mode) -> MapDocumentDto`.
- `GameMapService.updateSettings(UUID mapId, MapSettingsCommand command) -> GameMapService.MapSettingsResult`.
- `PUT /api/v1/maps/{id}/settings` accepts `{expectedVersion, gridWidth, gridHeight, cellSizePx, resizeMode, tokenResolutions}` and returns `{version, map, document}`.

- [ ] **Step 1: Write failing service tests for grid consistency.**

  Add tests to `MapGridResizeServiceTest` for:

  ```java
  @Test
  void preserveResizeChangesOnlyTheEmbeddedGrid() {
      MapDocumentDto document = documentWithCellAndShape(20, 15);
      MapDocumentDto resized = service.resize(document, 40, 30, ResizeMode.PRESERVE);

      assertThat(resized.grid().width()).isEqualTo(40);
      assertThat(resized.grid().height()).isEqualTo(30);
      assertThat(resized.layers().get(0).cells()).hasSize(1);
      assertThat(resized.layers().get(1).shapes()).hasSize(1);
  }

  @Test
  void cropResizeRemovesOutOfBoundsCellsAndClipsShapes() {
      MapDocumentDto document = documentWithCellAndShape(20, 15);
      MapDocumentDto cropped = service.resize(document, 10, 10, ResizeMode.CROP);

      assertThat(cropped.layers().get(0).cells())
              .extracting(MapLayerDto.CellDto::col, MapLayerDto.CellDto::row)
              .containsExactly(tuple(2, 3));
      assertThat(cropped.layers().get(1).shapes().get(0).points())
              .containsExactly(8.0, 8.0, 2.0, 2.0);
  }

  @Test
  void cropResizeRejectsUnknownOrCollapsedGeometryInsteadOfSilentlyDroppingIt() {
      MapDocumentDto document = documentWithShape(
              "triangle", List.of(8.0, 8.0, 12.0, 8.0, 10.0, 12.0));

      assertThatThrownBy(() -> service.resize(document, 10, 10, ResizeMode.CROP))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("triangle");
  }
  ```

  Add private fixtures in the same test class that create a schema-version-2 document
  with a Terrain layer containing cell (2,3), an Objects layer containing a rectangle
  `[8,8,5,5]`, and the requested third shape for the invalid-geometry case.

  Add `GameMapServiceTest` coverage that creates a 20×15 map, calls `updateSettings` to 40×30×64, and asserts both `updated.getGridWidth()`/height/cell size and `getDocument(mapId).grid()` match. Add a stale-version assertion for `OptimisticLockingFailureException`.

- [ ] **Step 2: Run the focused service tests and verify they fail.**

  Run: `./mvnw -q -Dtest=MapGridResizeServiceTest,GameMapServiceTest test`

  Expected: FAIL because the command/service types do not exist.

- [ ] **Step 3: Implement typed settings and document resize logic.**

  Add `MapSettingsCommand` records/enums. Implement `MapGridResizeService` to create a new `MapDocumentDto.GridDto` with the requested dimensions and existing movement/show-grid values. In `CROP` mode, retain cells inside bounds, clip `rect`, `line`, and `polygon` shape points to the rectangular boundary, and reject a shape that collapses to zero area/length with a validation exception that includes its layer and index. In `PRESERVE` mode, reject any document content outside the requested boundary instead of silently dropping it.

- [ ] **Step 4: Implement the transactional service operation.**

  In `GameMapService.updateSettings`, load the map, compare `command.expectedVersion()` with `map.getVersion()`, load the document, invoke `MapGridResizeService`, validate the resulting document grid against the requested metadata, apply `MOVE` resolutions through `TokenRepository`, apply `REMOVE` resolutions through the existing token deletion path, update the four `GameMap` fields, save/flush once, and return the new version plus map/document data. Keep the operation in one transaction. Reject token resolutions for tokens that do not belong to the target map.

- [ ] **Step 5: Add the API endpoint and controller tests.**

  Add request/response records to `GameMapApiController` and map `PUT /api/v1/maps/{id}/settings` to the service. Add MockMvc tests for a successful 30×20→40×30 settings request, malformed dimensions returning 400, and a stale service exception returning 409. Assert the response includes the new version and `document.grid.width`, `document.grid.height`, and `document.grid.cellSizePx`.

- [ ] **Step 6: Run the focused backend tests and verify they pass.**

  Run: `./mvnw -q -Dtest=MapGridResizeServiceTest,GameMapServiceTest,GameMapApiControllerTest test`

  Expected: PASS.

- [ ] **Step 7: Commit the persistence boundary.**

  ```bash
  git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapSettingsCommand.java \
          src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapGridResizeService.java \
          src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java \
          src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java \
          src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapGridResizeServiceTest.java \
          src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java \
          src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java
  git commit -m "feat(map): persist authoritative grid settings atomically"
  ```

---

### Task 3: Implement MapEditor grid-settings state and resize preview

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/static/js/map/shared.js`
- Modify: `src/main/resources/static/js/map/geometry.js`

**Interfaces:**
- `MapEditor.previewGridResize(width, height) -> {outsideCells, affectedShapes, affectedTokens}`.
- `MapEditor.applyGridSettings({width, height, cellSizePx, resizeMode, tokenResolutions}) -> Promise<void>`.
- `MapEditor.updateImageGeometry(patch) -> void`.
- `MapEditor.fitBackgroundImage(mode) -> void`, where `mode` is `FIT_INSIDE` or `FILL_COVER`.
- `MapEditor.resetBackgroundImage() -> void`.
- `MapEditor.calibrateBackgroundImage(ax, ay, bx, by, cellsBetween) -> void`.

- [ ] **Step 1: Add a browser test for local grid state before implementation.**

  Extend `MapEditorBrowserTest` with a map document containing one terrain cell, one rectangle shape, and a background image. Call `window.mapEditor.previewGridResize(10, 10)` and assert the returned impact includes the out-of-bounds cell/shape. Call `window.mapEditor.applyGridSettings({width: 40, height: 30, cellSizePx: 64, resizeMode: 'PRESERVE', tokenResolutions: []})`, wait for `/api/v1/maps/{id}/settings`, and assert `window.mapEditor.gridWidth`, `gridHeight`, and `cellSizePx` update together.

- [ ] **Step 2: Run the focused browser test and verify it fails.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest#gridSettingsPreviewAndApplyAreAtomic test`

  Expected: FAIL because the MapEditor methods do not exist.

- [ ] **Step 3: Add grid settings state and impact calculation.**

  Initialize `MapEditor` from `document.grid` when the document loads, not only from server-rendered constructor values. Add `previewGridResize` using `boundsImpact`, the document’s layers, and the editor’s token snapshot. Make expansion return an empty impact. Make shrink preview list all affected static content and tokens without mutating the document.

- [ ] **Step 4: Add the apply command and history boundary.**

  Add a settings snapshot containing `gridWidth`, `gridHeight`, `cellSizePx`, and the document. For `PRESERVE`, reject an impact instead of cropping. For `CROP`, apply the selected static-content crop and token resolutions locally, update `document.grid`, redraw the grid/stage, and send the full settings request. On failure restore the snapshot and rerender. Update `docVersion` from the response and emit `map-gridstate` for Alpine. Ensure undo/redo restores the document grid and stage dimensions.

- [ ] **Step 5: Add map-boundary clipping for background images.**

  Add a shared helper in `shared.js` that provides the map rectangle in pixel units. Render the image layer through a clipped group/viewport so negative x/y from Fill and crop never paints outside the authoritative map boundary. Use the same boundary for PNG export.

- [ ] **Step 6: Run the focused browser test and verify it passes.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest#gridSettingsPreviewAndApplyAreAtomic test`

  Expected: PASS, with the map-settings request containing matching outer settings and `document.grid`.

- [ ] **Step 7: Commit the editor state boundary.**

  ```bash
  git add src/main/resources/static/js/map/map-editor.js src/main/resources/static/js/map/shared.js src/main/resources/static/js/map/geometry.js src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
  git commit -m "feat(map): add authoritative grid resize commands"
  ```

---

### Task 4: Implement the background-image inspector and import behavior

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentContractTest.java`

**Interfaces:**
- Alpine receives `map-image-state` with `{present, selected, locked, x, y, width, height, rotationDeg, aspectRatio}`.
- Alpine calls `updateImageField(field, value)`, `fitImage(mode)`, `resetImage()`, `startCalibration()`, and `setImageLocked(locked)`.

- [ ] **Step 1: Add a template contract test for stable controls.**

  Extend `GameMapControllerTest.shouldRenderEditorPage` to assert the editor HTML contains `map-settings`, `background-inspector`, `gridWidth`, `gridHeight`, `cellSizePx`, `fit-inside`, `fill-cover`, `imageWidth`, `imageHeight`, and `imageLocked`.

- [ ] **Step 2: Run the focused template test and verify it fails.**

  Run: `./mvnw -q -Dtest=GameMapControllerTest#shouldRenderEditorPage test`

  Expected: FAIL because the new sections/selectors do not exist.

- [ ] **Step 3: Implement the image geometry commands.**

  Refactor `commitImageTransform` to use `updateImageGeometry`. Add numeric field updates with finite-positive validation, aspect-ratio-preserving updates when the lock is enabled, and an undo snapshot before each committed edit. Add `fitBackgroundImage('FIT_INSIDE')`, `fitBackgroundImage('FILL_COVER')`, and `resetBackgroundImage` using `geometry.js`; keep all values in cell units. Replace the current calibration implementation so it updates image x/y/width/height using `calibratedImageGeometry` and never assigns `this.cellSizePx` from calibration.

- [ ] **Step 4: Fix image selection and lock synchronization.**

  On document load and every `renderDocument`, emit `map-image-state` from `layerDto('image').image`. Set the Alpine `imageLocked` value from that event. Make locked images non-draggable and prevent selection transforms while locked; unlocking must rerender the node with `draggable: true`.

- [ ] **Step 5: Normalize imports before storing the data URL.**

  In `importBackgroundImage`, decode the image, downscale the longest edge to 8192 pixels when necessary, preserve transparency when the source requires it, and show the normalized dimensions in the status/confirmation text. Fit the normalized image inside the current grid and persist only after successful decode/normalization.

- [ ] **Step 6: Run the document round-trip tests.**

  Add assertions to `MapDocumentContractTest` that `ImageDto` geometry, rotation, lock, and calibration survive serialization unchanged and that calibration does not alter `GridDto.cellSizePx`. Run: `./mvnw -q -Dtest=MapDocumentContractTest test`

  Expected: PASS.

- [ ] **Step 7: Commit the image model boundary.**

  ```bash
  git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentContractTest.java src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java
  git commit -m "feat(map): add discoverable background image inspector"
  ```

---

### Task 5: Build the sidebar settings and safe resize confirmations

**Files:**
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Alpine `toolbar()` state adds `gridWidth`, `gridHeight`, `cellSizePx`, `resizePreview`, `resizeDialogOpen`, `imageState`, and `imageLocked`.
- Alpine methods call `previewResize()`, `cancelResize()`, `confirmResize()`, `updateMapSetting(field, value)`, `updateImageField(field, value)`, `fitImage(mode)`, and `resetImage()`.

- [ ] **Step 1: Add the failing UI acceptance assertions.**

  In `MapEditorBrowserTest`, open the editor and assert the Map section is visible, the Background section is hidden when no image exists, and the resize dialog reports the count of affected cells/shapes when width/height is reduced.

- [ ] **Step 2: Run the focused browser test and verify it fails.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest#settingsAndResizeConfirmationAreDiscoverable test`

  Expected: FAIL because the sidebar state and dialog do not exist.

- [ ] **Step 3: Add the Map section.**

  Add labeled number inputs with `min="5"`, `max="100"` for width/height and `min="16"`, `max="128"`, `step="8"` for cell size. Add a Resize canvas button that calls `previewResize`. Keep the inputs synchronized from `map-gridstate` after load/apply. Disable submit while a settings save is in flight.

- [ ] **Step 4: Add the resize preview dialog.**

  Render the proposed dimensions, current dimensions, affected terrain-cell count, shape count, and token count. Provide Cancel and Crop and continue actions. If tokens are affected, render explicit Move to edge/Remove controls backed by `TokenResolution`; never submit an implicit token deletion. Show server validation errors inside the dialog and restore the old values after failure.

- [ ] **Step 5: Add the Background section.**

  Bind numeric x/y/width/height inputs to `imageState`. Add an aspect-ratio checkbox, Fit inside, Fill and crop, Reset, Rotate CCW/CW, Calibrate, and Lock controls. Add `data-map-control`/`data-image-control` attributes so browser tests can use stable selectors without depending on visible text.

- [ ] **Step 6: Run the focused browser test and verify it passes.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest#settingsAndResizeConfirmationAreDiscoverable test`

  Expected: PASS, including cancel-without-mutation and confirm-with-save behavior.

- [ ] **Step 7: Commit the visible workflow.**

  ```bash
  git add src/main/resources/templates/maps/editor.html src/main/resources/static/js/map/map-editor.js src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
  git commit -m "feat(map): expose grid and image settings in editor"
  ```

---

### Task 6: Add end-to-end import, calibration, persistence, and failure coverage

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java`

- [ ] **Step 1: Add a browser test for image import and handle/numeric resize.**

  Use Playwright `setInputFiles` with an in-memory PNG payload. Wait for `window.mapEditor.document.layers` to contain the image, select the Background layer, assert the inspector is visible, edit `imageWidth`, and assert the persisted document width changes. Use the Konva transformer or call the same public command through the UI event to verify handle resize uses the same path.

- [ ] **Step 2: Add browser assertions for fit, fill, reset, rotation, lock, and calibration.**

  Assert Fit inside leaves the image fully inside the grid, Fill and crop produces geometry covering the grid with overflow clipped, Reset returns to fit geometry, rotation changes `rotationDeg`, lock prevents dragging/transforming, and calibration changes image geometry while `document.grid.cellSizePx` remains the original value.

- [ ] **Step 3: Add reload persistence assertions.**

  Wait for the save indicator to become `Saved`, reload the editor URL, wait for `window.mapEditor.document`, and assert the grid settings, image geometry, rotation, calibration, and lock state are restored. Assert the map list card reports the new dimensions.

- [ ] **Step 4: Add safe failure coverage.**

  Use Playwright routing to return 409 from `/settings` and assert the resize dialog remains open with a conflict/error state. Use an invalid image file and assert no image layer is created. Submit a shrink with an affected token and assert no request is sent until a resolution is selected.

- [ ] **Step 5: Add backend round-trip and schema checks.**

  Assert `GameMapService.updateSettings` preserves movement mode/show-grid fields, rejects a document whose embedded grid differs from the requested settings, and returns a version that the next document save accepts. Keep `MapDocumentContractTest` coverage for package-compatible image calibration fields.

  Extend `MapSectionAdapterTest.exportsImageAssetRefs` with a calibrated `ImageDto` whose
  x/y/width/height/rotation/calibration values are non-default, then assert the exported
  manifest image has the same geometry and calibration after asset extraction.

- [ ] **Step 6: Run the focused end-to-end suite.**

  Run: `./mvnw -q -Dtest=MapEditorBrowserTest,GameMapApiControllerTest,GameMapServiceTest,MapDocumentContractTest,MapSectionAdapterTest test`

  Expected: PASS with no browser console, pageerror, failed-request, or lazy-initialization failures.

- [ ] **Step 7: Commit the acceptance coverage.**

  ```bash
  git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java
  git commit -m "test(map): cover import-first editor workflow"
  ```

---

### Task 7: Documentation, compatibility, and release verification

**Files:**
- Modify: `docs/dm-manual/04-maps-encounters-party.md`
- Modify: `docs/campaign-format-v2.md` to clarify that calibration stores image-to-grid alignment and does not propose a new map cell size.

- [ ] **Step 1: Update the DM manual.**

  Replace the current brief calibration paragraph with the import-first workflow: the grid is authoritative, imported images fit inside by default, Fill and crop is optional, calibration changes image geometry rather than cell size, and Map Settings supports post-creation grid resizing with a crop confirmation.

- [ ] **Step 2: Run targeted contract and formatting checks.**

  Run:

  ```bash
  ./mvnw -q -Dtest=MapEditorBrowserTest,GameMapApiControllerTest,GameMapControllerTest,GameMapServiceTest,MapGridResizeServiceTest,MapDocumentContractTest test
  git diff --check
  ```

  Expected: all tests pass and `git diff --check` emits no output.

- [ ] **Step 3: Run the project release gate relevant to map/editor surfaces.**

  Run: `./mvnw -q -Dtest=ViewportAccessibilityGateTest,ReleaseGateIndexContractTest test`

  Expected: PASS with no template truncation, viewport/accessibility, or release-index regressions.

- [ ] **Step 4: Commit documentation and final verification changes.**

  ```bash
  git add docs/dm-manual/04-maps-encounters-party.md docs/campaign-format-v2.md
  git commit -m "docs(map): document import-first editor workflow"
  ```

---

## Final verification checklist

- [ ] A map created at 30×20 can be changed to 40×30 after creation.
- [ ] `GameMap` metadata and `MapDocument.grid` remain identical after settings save and reload.
- [ ] Imported images fit inside the grid without changing it.
- [ ] Background images can be resized through handles and numeric fields.
- [ ] Fill and crop clips overflow at the map boundary without destroying the source data URL.
- [ ] Calibration changes image geometry only; cell size remains unchanged.
- [ ] Image lock survives reload and prevents transforms while locked.
- [ ] Shrinking the grid previews affected content and never silently deletes tokens.
- [ ] Autosave, optimistic conflicts, backup download, undo/redo, export, and package round-trip remain functional.
- [ ] Focused map tests and the relevant release gates pass.
