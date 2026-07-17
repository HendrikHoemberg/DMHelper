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

## When to emit SOURCE_ANNOTATION instead of inventing data

If the source material is ambiguous or missing a required field:

- **NEVER** guess a DC, stat block, map coordinate, or source key.
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
