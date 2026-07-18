# Rollable Tables

Rollable tables let you define random-result tables for encounters, treasure, weather, rumors, events, and generic prompts. They are DM-only — players never see table content.

## Scope: User-global vs Campaign

Tables can be created at two levels:

- **Campaign-scoped** — visible only within one campaign, included in v2 export/import.
- **User-global (SRD/library)** — visible across all campaigns, not included in campaign packages.

Campaign-scoped tables are the primary authoring target. User-global tables are created from the library and linked to a campaign when needed.

## Range vs Weighted Mode

Tables use one of two addressing modes:

| Mode | Field | Description |
|------|-------|-------------|
| `RANGE` | `rangeStart` / `rangeEnd` | Roll a die expression (e.g. `1d20`) and match the result against inclusive ranges. |
| `WEIGHTED` | `weight` | Each entry has a relative weight; the system picks proportionally. |

### Range example (1d12 forest encounters)

```json
{"rangeStart": 1, "rangeEnd": 3, "resultText": "Goblins", "quantityExpression": "2d4"},
{"rangeStart": 4, "rangeEnd": 6, "resultText": "Skeletons", "quantityExpression": "1d4"},
{"rangeStart": 7, "rangeEnd": 8, "resultText": "Wolves"},
{"rangeStart": 9, "rangeEnd": 9, "resultText": "A deer crosses the path"}
```

### Weighted example (tavern rumors)

```json
{"weight": 10, "resultText": "A dragon was sighted"},
{"weight": 20, "resultText": "Hidden treasure in the crypt"},
{"weight": 35, "resultText": "The barkeep has nothing useful"}
```

## Physical Dice Compatibility

When the table mode is `RANGE`, you can roll physical dice and look up the result — the ranges are designed to match standard polyhedral dice (d6, d8, d12, d20, d100).

## Roll N / Duplicates

When rolling from the UI or API:

- **Roll count** — how many times to roll (1 by default).
- **Duplicate policy** — `ALLOW_DUPLICATES` lets the same entry appear multiple times; `REROLL_DUPLICATES` skips entries already selected.

## Nested Depth

An entry can reference another rollable table via `references` with type `ROLLABLE_TABLE`. When rolled, the system resolves the nested table recursively. Maximum depth: 5 levels.

```json
{"rangeStart": 10, "rangeEnd": 10, "resultText": "Sudden storm",
 "references": [{"scope": "PACKAGE", "type": "ROLLABLE_TABLE", "key": "rt-road-weather"}]}
```

## Scene / Location Linking

A table can be linked to:

- **Scene** — via `scene.links[]` with role `RANDOM_ENCOUNTERS`. The table appears in the session cockpit story rail.
- **World location** — via `worldLocation.tableLinks[]`. The table is available when the party is at that location.

## Draft Confirm / Discard

When a table roll produces an encounter or reward draft:

- **Confirm** — applies the draft: creates a planned encounter with combatants (ENCOUNTER tables) or adds items to the party stash (TREASURE tables).
- **Discard** — removes the draft without side effects.

Drafts are visible in the session cockpit story rail under "Pending drafts".

## Deletion Impact

Deleting a rollable table:

- Removes all linked scene references (the `links` entry is cleaned up).
- Removes all world location table links.
- Removes all pending drafts that reference the table.
- Does NOT affect already-confirmed encounters or treasury entries.

## Import / Export

Rollable tables are included in campaign package v2 export/import by default. The `rollableTables` array in the manifest carries all tables with their entries and references.

User-global tables are NOT included in campaign packages. Reference them via catalog-scoped content references instead.

## Troubleshooting

| Symptom | Likely cause |
|---------|-------------|
| Roll returns nothing | No entry matches the rolled value (RANGE) or all weights are 0 (WEIGHTED). |
| Nested roll exceeds depth | The chain exceeds 5 levels of nesting — simplify the table structure. |
| Encounter draft shows no creatures | The entry references a statblock that could not be resolved. |
| Table not visible in cockpit | The table is not linked to the current scene or location. |
| Import fails with TABLE_REFERENCE_CYCLE | A cycle exists in nested table references. Remove the cycle. |
