# Mapping Rules

## Package vs catalog ContentReference

Both `packageReference` and `catalogReference` are `contentReference` variants.
Use `PACKAGE` scope for campaign-scoped entities you create; use `CATALOG` scope for SRD / reference material.

```json
// PACKAGE reference — for custom content inside the same campaign
{ "scope": "PACKAGE", "type": "STATBLOCK", "key": "my-custom-goblin-chief" }

// CATALOG reference — for SRD content resolved via catalog
{ "scope": "CATALOG", "type": "SPECIES", "ruleset": "SRD_5_2", "sourceKey": "human" }
```

## Key uniqueness per CampaignContentType

Every `key` must be unique within its content type array. Keys must match
`^[a-z0-9][a-z0-9._-]{0,99}$`.

```json
// TWO maps with the same key -> DUPLICATE_KEY error
{ "maps": [
    { "key": "dungeon", "name": "Level 1", "grid": {...}, "document": {...}, "sortOrder": 1 },
    { "key": "dungeon", "name": "Level 2", "grid": {...}, "document": {...}, "sortOrder": 2 }
]}
```

## Scene transitions vs editorial order

Transitions define navigable paths between scenes. Editorial `sortOrder` on scenes/chapters
controls the reading order within the campaign document.

```json
// Scene with both sort order and transitions
{ "key": "cave-entrance", "title": "Cave Entrance", "sortOrder": 1,
  "transitions": [
    { "key": "go-deeper", "kind": "CHOICE", "label": "Descend into the dark",
      "targetSceneRef": { "scope": "PACKAGE", "type": "SCENE", "key": "cavern-of-shadows" },
      "sortOrder": 1 }
  ]
}
```

`CHOICE` transitions MUST have a `targetSceneRef`. `ENTRANCE`/`EXIT` transitions
may use `externalDestination` instead for cross-adventure links.

## Quest objectives + dependencies

Quest objectives can have `prerequisiteRefs` forming a DAG of dependencies.
Each `questObjective` references sibling objectives by `contentReference`.

```json
{ "key": "clear-outpost", "title": "Clear the outpost",
  "objectives": [
    { "key": "defeat-leader", "title": "Defeat the hobgoblin captain",
      "completionMode": "ALL", "sortOrder": 1,
      "prerequisiteRefs": [] },
    { "key": "secure-supplies", "title": "Secure the supply cache",
      "completionMode": "ALL", "sortOrder": 2,
      "prerequisiteRefs": [
        { "scope": "PACKAGE", "type": "OBJECTIVE", "key": "defeat-leader" }
      ] }
  ]
}
```

`completionMode`: `ALL` (every objective must complete) or `ANY` (any one satisfies the parent).

## Encounter waves + placement regions

Waves are ordered sub-groups of combatants. Each wave references a map `placementRegionKey`
where its combatants appear.

```json
// Encounter wave definition
{ "key": "first-wave", "name": "Goblin scouts", "sortOrder": 1,
  "status": "ACTIVE", "triggerKind": "MANUAL" },
{ "key": "second-wave", "name": "Hobgoblin reinforcements", "sortOrder": 2,
  "status": "RESERVE", "triggerKind": "ROUND", "triggerValue": "3" }

// Combatant assigned to a wave + placement region
{ "key": "goblin-1", "name": "Goblin Scout", "initiative": 14,
  "kind": "MONSTER", "groupLeader": false, "defeated": false,
  "sortOrder": 1, "maxHp": 7, "currentHp": 7, "tempHp": 0,
  "hidden": false, "concentrationCheckPending": false,
  "legendaryActionsUsed": 0, "legendaryResistancesUsed": 0,
  "legendaryActionsMax": 0, "legendaryResistancesMax": 0,
  "waveKey": "first-wave", "placementRegionKey": "north-entrance",
  "startX": 5, "startY": 3 }
```

## Map units (pixels vs cells)

All coordinate positions in the v2 format use **grid-cell units** (`[col, row]` order,
zero-indexed from top-left). The `cellPx` field on `mapGrid` defines the pixel-to-cell ratio.

```json
{ "grid": { "w": 40, "h": 30, "cellPx": 50, "gridType": "SQUARE" },
  "tokens": [
    { "key": "hero-token", "name": "Party", "positionX": 5, "positionY": 10,
      "sizeCols": 1, "sizeRows": 1, "hidden": false, "dead": false }
  ]
}
```

`cellPx` is declared in pixels. `positionX`/`positionY` are in cell coordinates.
Token `sizeCols`/`sizeRows` span multiple cells.

## Provenance required fields for non-original content

Non-catalog custom content MUST include a `provenance` block:

```json
{ "key": "my-homebrew-monster", "name": "Crystal Drake",
  "provenance": {
    "sourceTitle": "Tome of Forgotten Beasts",
    "editionVersion": "5e 2024",
    "sourceLocator": "p. 42",
    "licenseClassification": "THIRD_PARTY",
    "importedAt": "2025-06-01T00:00:00Z",
    "converterId": "dmhelper-agent",
    "converterVersion": "1.0.0",
    "extractionConfidence": "HIGH"
  }
}
```

`ORIGINAL` content (entirely created by the DM) may omit provenance, but
`licenseClassification` of `"ORIGINAL"` is recommended for clarity.

## Rollable table die-column parsing and range normalization

Rollable tables use `RANGE` addressing with `rangeStart`/`rangeEnd` fields for die-based
selection, or `WEIGHTED` addressing with a `weight` field for proportional selection.

### Die-column parsing

When source material presents a table with die columns (e.g. `d12`, `d100`), map the column
values to `rangeStart`/`rangeEnd`:

```json
// Source: "d100 01-20  Goblins"
// -> entry { "rangeStart": 1, "rangeEnd": 20, "resultText": "Goblins" }
```

Contiguous ranges are preserved as-is. Non-contiguous single values use the same start/end:
```json
// Source: "12  A deer crosses the path"
// -> entry { "rangeStart": 12, "rangeEnd": 12, "resultText": "A deer crosses the path" }
```

### Range normalization

Ranges must be **contiguous** within the table for the mode to be valid. Overlapping ranges or
gaps produce `TABLE_RANGE_OVERLAP` or `TABLE_RANGE_GAP` validation errors.

- If the source has gaps, fill them with a `"Nothing unusual"` or equivalent entry — do NOT
  silently compress ranges.
- If the source has overlaps, use `SOURCE_ANNOTATION` with `confidence: "LOW"`.

```json
// Correct: d100 table covering all 1..100 values
{"rangeStart": 1, "rangeEnd": 20, "resultText": "Goblins"},
{"rangeStart": 21, "rangeEnd": 35, "resultText": "Skeletons"},
{"rangeStart": 36, "rangeEnd": 100, "resultText": "Nothing unusual"}
```

### Weighted tables

Weighted tables do not require a contiguous range. Weights are relative — an entry with
`weight: 35` is 3.5× as likely as one with `weight: 10`.

```json
// Weighted entries
{"weight": 10, "resultText": "Rare encounter"},
{"weight": 35, "resultText": "Common encounter"}
```

## Trap and hazard mapping

Traps and hazards map to top-level `traps[]` / `hazards[]` with stable package keys. Scene prose
boxes that name a trap become `sections` with `kind: "TRAP"` or `"HAZARD"` and optional
`threatRef`. Encounter write-ups that place a trap on the initiative track become combatants with
`kind: "TRAP"`/`"HAZARD"` and matching `threatRef`. Map callouts become `maps[].threatPins[]`
with pixel coordinates — never invent coordinates from gridless art.

### Attack vs save mutual exclusion

A trap may declare **at most one** of:

- `attackBonus` (to-hit style)
- `save` (`mode: "SAVE"`, ability, optional DC)

If the source is ambiguous, omit both and annotate. Never invent a `+5` attack or a DC 15 save to
satisfy the schema.

### Disarm methods and severity

- Disarm rows need stable `key` values unique within the trap (`jam-gears`, `cut-wire`).
- Severity is `SETBACK` | `DANGEROUS` | `DEADLY` only when the source uses equivalent language;
  default to the mildest stated tier or annotate if unknown.
- Hazard `exposureMode` is required (`ON_ENTER`, `START_OF_TURN`, `PER_ROUND`, `CONTINUOUS`).

### Provenance

Published traps should carry `provenance` (title, locator, license, converter, confidence). Leave
numeric mechanics absent rather than inventing them; pair omissions with `SOURCE_ANNOTATION`.

## When to emit SOURCE_ANNOTATION instead of inventing data

If the source material is ambiguous or missing a required field:

- **NEVER** guess a DC, stat block, map coordinate, trap attack bonus, hazard save, or source key.
- **ALWAYS** emit a `SOURCE_ANNOTATION` on the owning entity with `confidence: "LOW"` or `"UNKNOWN"`.
- The `annotations` array at campaign root collects these. Each annotation references its owner.

```json
// Annotations array entry documenting uncertainty
{ "key": "ann-riddle-dc", "ownerRef": { "scope": "PACKAGE", "type": "SCENE", "key": "sphinx-chamber" },
  "fieldPath": "/campaign/settings/checks/0/dc",
  "message": "The module does not specify a DC for the sphinx's riddle. "
           + "A reasonable range is 15-20; set manually after review.",
  "confidence": "UNKNOWN",
  "status": "OPEN",
  "createdAt": "2025-06-01T00:00:00Z" }
```
