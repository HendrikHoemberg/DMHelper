# Maps, Encounters & Party

## Map Editor — Import-First Workflow

The map grid (width, height, cell size) is the authoritative coordinate system. All background-image geometry and token positions derive from it.

### Map Settings

The editor sidebar exposes a **Map** section with width, height, and cell size inputs. Changing dimensions after creation is supported:

- **Expanding** the grid preserves all existing content.
- **Shrinking** the grid computes a preview of affected terrain cells, shapes, and tokens. The user may cancel or choose **Crop and continue**. Tokens outside the boundary are never silently deleted — they must be moved or detached explicitly. The completed resize is one undoable operation.
- Cell-size changes redraw the same logical grid at the new visual scale; they do not change cell coordinates or rescale the logical arrangement of content.

### Image Import

Import Image is the entry point. After decoding, the image is automatically **fit inside** the current grid:
- The whole image remains visible.
- Aspect ratio is preserved.
- The image is centered.
- Empty margins are allowed.
- The grid is not changed.

The image inspector then offers:

- **Fill and crop** — scales the image to cover the grid while preserving aspect ratio. Overflow is clipped at the map boundary; the operation does not destructively replace the source data URL.
- **Reset** — returns to the fit-inside geometry.
- **Rotate** — 90-degree clockwise or counter-clockwise increments.
- **Lock / unlock** — prevents dragging and transform handles while locked; persists across reload.

### Image Geometry

Selecting the Background layer selects its image and opens a **Background** section with X/Y position (in cells), width/height (in cells), and an aspect-ratio lock. Geometry can be adjusted through:
- **Drag handles** on the canvas
- **Numeric fields** in the sidebar

Both produce one undo boundary per committed edit.

### Calibration

Calibration aligns an image with a printed grid without changing the authoritative grid:

1. Select two points on the image.
2. Enter the number of grid cells the selected distance represents.
3. The editor adjusts only the image scale and offset around the first point.
4. Map cell size remains unchanged.

Calibration metadata is persisted in `ImageDto.calibration`, describing image-to-grid alignment rather than a proposed replacement cell size.

## Encounter Waves

Planned encounters can have multiple waves. Wave trigger kinds: `MANUAL` (spawn on demand), `ROUND` (spawn at a given round), `HP_THRESHOLD` (spawn when a combatant drops below a threshold), `CUSTOM`. Add waves from the encounter detail page.

## Encounter Rewards

Set XP (total or per-PC), currency grants (added to party ledger), custom loot items, quest objective refs, and notes. Save rewards as a draft, then **Apply** them on encounter completion to distribute XP, currency, and items, and advance quest objectives.

## Threat Combatants (Traps / Hazards)

Encounters can include non-creature combatants of kind `TRAP` or `HAZARD` via the encounter detail
**Add trap/hazard** search or `POST /api/v1/encounters/{id}/combatants/from-threat`. The active-turn
tracker card shows advisory mechanics (detection, attack, save, damage, exposure) with dice
**prefill** only — it does not apply HP or conditions. Use the tracker's existing damage buttons and
condition toggles on creature combatants when you resolve an effect, and verify the combat log.

## DM-Only Threat Map Pins

Workspace maps support DM-only threat pins (`/api/v1/maps/{id}/pins`) that mark trap/hazard
locations in pixel coordinates. Pins are never projected to the player view or table WebSocket.
Opening a pin loads the threat library detail for the DM.

## Rest & Batch Operations

The party summary bar and sheets overview provide a batch operations bar for multi-member rest, XP, condition, and loot operations. **Rest Preview** is a pure read — it shows what would be recovered without applying changes.
