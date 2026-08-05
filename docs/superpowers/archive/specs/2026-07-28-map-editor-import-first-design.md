# Map Editor Import-First Workflow

**Date:** 2026-07-28
**Status:** Approved design
**Scope:** Map editor usability and persistence for imported background maps

## Context

The map editor is a Konva canvas island mounted by
`src/main/resources/templates/maps/editor.html` and implemented in
`src/main/resources/static/js/map/map-editor.js`. Map drawing content is stored as a
versioned JSON document. The relational `GameMap` also stores width, height, and cell
size.

Two user-facing problems motivate this work:

1. Grid dimensions cannot be changed from the editor after map creation, although the
   backend already has a generic map-update operation.
2. Background-image resizing is technically implemented through Konva selection
   handles, but the workflow is hidden behind the Select tool, has no numeric controls,
   and does not reliably expose the persisted lock state.

The primary workflow is importing existing maps, both maps with printed grids and
images without grids.

## Goals

- Make the grid editable after map creation.
- Make the map grid the authoritative coordinate system.
- Make imported-image fitting, scaling, and calibration discoverable and precise.
- Keep image operations independent from grid dimensions.
- Preserve the current local-first architecture, JSON document model, autosave, undo,
  player-safe projection, and campaign round-trip behavior.
- Prevent metadata/document grid drift.

## Non-goals

- Replacing the Konva editor with a different canvas engine.
- Adding image-based battle-map tokens.
- Introducing a separate asset-storage subsystem in this delivery.
- Scaling terrain, shapes, or token layouts when scaling a background image.
- Changing grid type; square grids remain the supported v1 mode.

## Product invariants

1. `GameMap.gridWidth`, `GameMap.gridHeight`, `GameMap.cellSizePx`, and
   `MapDocument.grid` must agree after every successful settings operation.
2. Importing, fitting, cropping, rotating, or calibrating an image never changes the
   authoritative map grid.
3. Background-image geometry is expressed in grid-cell units, as it is today.
4. Terrain, shapes, and tokens remain anchored to their existing logical cells when
   the background image is scaled.
5. Destructive canvas shrinking requires an explicit confirmation and is undoable.
6. An image lock is persisted and restored; the toolbar must reflect the persisted
   image lock state after reload.

## User workflow

### Map settings

The editor sidebar exposes width, height, and cell size under a Map section.

- Increasing width or height preserves all existing content.
- Decreasing width or height computes an affected-content preview first.
- The preview lists terrain cells, shapes, and tokens outside or intersecting the new
  boundary.
- The user can cancel or choose **Crop and continue**.
- Terrain cells outside the boundary are removed. Rectangles, lines, and polygons are
  clipped to the boundary; geometries that collapse to zero area/length are listed for
  explicit removal.
- Tokens outside the boundary are never silently deleted. The confirmation requires
  either moving them to valid cells (including a move-to-nearest-edge action) or
  detaching them from the map.
- The completed resize is one undoable operation.

Cell-size changes redraw the same logical grid at the new visual scale. They do not
change cell coordinates or rescale the logical arrangement of terrain, shapes, or
tokens.

### Image import

Import Image remains the entry point. After decoding, the image is automatically fit
inside the current authoritative grid:

- The whole image remains visible.
- Aspect ratio is preserved.
- The image is centered.
- Empty margins are allowed.
- The grid is not changed.

The image inspector then offers **Fill and crop**, which scales the image to cover the
grid while preserving aspect ratio. The displayed map viewport clips overflow; the
operation does not destructively replace the source image. **Reset** returns to the
fit-inside geometry.

Image import validates the file before it becomes part of the document. Unsupported
or corrupt files are rejected with an actionable message. Very large images are
downscaled client-side to a maximum 8192-pixel longest edge, with the resulting
dimensions shown before the image is embedded. This is necessary because the current
document stores images as base64 data URLs in a CLOB and page-unload keepalive requests
have a size limit.

### Image inspector

Selecting the Background layer selects its image and opens the Background section:

- X and Y position in cells
- Width and height in cells
- Aspect-ratio lock enabled by default
- Fit inside
- Fill and crop
- Reset
- Rotate 90 degrees clockwise or counter-clockwise
- Calibrate
- Lock/unlock image

Direct selection exposes resize handles for quick adjustment. Numeric edits and handle
drags use the same geometry command and each produce one undo boundary. The inspector
is disabled with an explanatory tooltip when no image exists.

### Calibration

Calibration uses the current grid as truth. The user selects two points on the image,
enters the number of grid cells represented by that distance, and the editor adjusts
only the image scale and offset. The current map cell size is unchanged.

The image is scaled around the first calibration point so that the selected reference
stays anchored. Calibration metadata remains persisted in `ImageDto.calibration`, but
its semantics describe image-to-grid alignment rather than a proposed replacement cell
size. The existing **Apply Cell Size** action is removed from this workflow.

## UI organization

The toolbar remains focused on drawing tools. Configuration moves into the existing
right sidebar:

- **Map:** width, height, cell size, resize action, compact grid summary.
- **Background:** image geometry and image actions, shown when an image exists or the
  Background layer is active.
- **Layers:** existing visibility, lock, and player-visibility controls.
- **Shape** and **Threat pins:** existing conditional sections.

The canvas displays an active image outline and handles. Selecting Background from the
layer list selects the image when present. The image lock checkbox is initialized from
the document and updated through layer-state events.

Resize and crop confirmations use the existing application styling and status/save
indicators. Existing keyboard shortcuts remain unchanged.

## Architecture and data flow

### Frontend

`MapEditor` remains the owner of canvas state and document history. It gains:

- A grid-settings command that updates the local document grid and stage rendering.
- A resize-preview calculation for document content and fetched token positions.
- A background-image inspector state bridge for Alpine.
- Fit-inside, fill-and-clip, reset, and grid-authoritative calibration helpers.
- Compound history snapshots for grid changes, so undo restores both the document and
  rendered grid state.

Image changes continue through document autosave. Grid settings use a dedicated
transactional settings operation because they update relational metadata and the JSON
document together.

### Backend

Add `PUT /api/v1/maps/{id}/settings`. The request accepts the expected map version, new
grid settings, resize mode, and explicit crop/token resolutions. The service operation
is transactional and:

1. checks optimistic version;
2. validates limits and square-grid constraints;
3. updates `MapDocument.grid`;
4. crops or validates static content;
5. applies explicit token resolutions;
6. updates `GameMap` metadata;
7. persists and returns the new version.

The existing whole-document endpoint remains responsible for normal editor content and
image autosaves. It must continue validating document schema versions and should reject
documents whose embedded grid disagrees with map metadata.

`ImageDto` keeps its existing geometry, rotation, lock, and calibration fields. No
separate asset table is introduced in this delivery.

## Error handling

- Invalid dimensions are rejected inline before a request is sent.
- Failed image decoding or normalization leaves the map unchanged.
- Invalid calibration points leave image geometry unchanged.
- A settings-save failure restores the previous local grid/document state and shows a
  retryable error.
- Optimistic conflicts retain the existing conflict state and backup-download action.
- Autosave errors continue retrying through the existing debounce path.
- No crop operation silently deletes tokens.

## Verification

### Pure geometry coverage

Test helpers for:

- fit-inside geometry;
- fill-and-clip geometry;
- aspect-ratio-preserving numeric resize;
- calibration scale and anchor math;
- grid-boundary crop and affected-content detection.

### Backend coverage

- Settings update keeps `GameMap` metadata and `MapDocument.grid` equal.
- Expansion preserves document content.
- Shrink requires confirmation data and applies explicit crop resolutions.
- Version conflicts reject stale settings operations.
- Invalid dimensions and grid mismatches are rejected.
- Image geometry and calibration round-trip through the document endpoint and campaign
  package format.

### Browser acceptance coverage

Exercise the import-first workflow end to end:

1. Create a map and open the editor.
2. Change width, height, and cell size after creation.
3. Expand the grid and verify existing content remains.
4. Preview and cancel a shrinking operation, then confirm a crop.
5. Import an image and verify fit-inside behavior.
6. Select the image, resize through handles, and edit numeric geometry.
7. Use Fit inside, Fill and crop, Reset, Rotate, Lock, and Unlock.
8. Calibrate a printed-grid image without changing map cell size.
9. Reload and verify all settings and image geometry persist.
10. Verify save failure and optimistic-conflict feedback.

## Scope boundary

This design is intentionally limited to map-grid settings and the import/background
workflow. It does not redesign terrain tools, shape authoring, tokens, threat pins,
player projection, or the broader session cockpit except where grid consistency requires
their coordinates to be validated or resolved.
