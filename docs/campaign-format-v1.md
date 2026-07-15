# DMHelper Campaign Format — Version 1

## 1. Schema URLs and Checked-in Paths

| Schema | URL | File |
|--------|-----|------|
| Campaign format | `https://dmhelper/campaign-format.schema.json` | `src/main/resources/schemas/campaign-format.schema.json` |
| Map document | `https://dmhelper/map-document.schema.json` | `src/main/resources/schemas/map-document.schema.json` |

Both schemas use JSON Schema draft 2020-12 and are compiled into `CampaignSchemaValidator`.

## 2. Format and Container

- **formatVersion:** `1` (integer constant)
- **Encoding:** UTF-8 JSON
- **Extension:** `.dmcampaign.json`
- **Container:** Plain JSON file (no ZIP packaging). Binary assets (handout images, map backgrounds) are embedded as base64 data URLs.
- **Required top-level fields:** `formatVersion`, `campaign`
- **Additional top-level properties are rejected** (`additionalProperties: false` applies to every object in the schema).

## 3. Validation Order and Dry-Run Response

The import pipeline validates in this exact order:

1. **JSON parse** — file must be valid UTF-8 JSON
2. **Schema validation** — structure, types, enums, required fields, closed objects
3. **DTO deserialization** — JSON must map to `CampaignExportDto` (with `FAIL_ON_UNKNOWN_PROPERTIES`)
4. **Semantic validation** — cross-references, uniqueness, bounds, grid consistency, encounter state
5. **Persistence** — only after all previous stages pass

### Dry-run response

```
POST /campaigns/import (with dry-run parameter)
```

If valid:
```json
{
  "valid": true,
  "problems": []
}
```

If invalid (schema error):
```json
{
  "valid": false,
  "campaign": null,
  "problems": [
    {
      "severity": "ERROR",
      "code": "SCHEMA_ADDITIONAL_PROPERTIES",
      "path": "/campaign/unexpected",
      "message": "$.campaign: extraneous key [unexpected] is not permitted",
      "suggestion": "Match the field type, required fields, enum, range, or closed-object shape in campaign-format.schema.json."
    }
  ]
}
```

If invalid (semantic error):
```json
{
  "valid": false,
  "problems": [
    {
      "severity": "ERROR",
      "code": "UNRESOLVED_REFERENCE",
      "path": "/adventures/0/chapters/0/scenes/0/statblocks/0",
      "message": "StatBlock 'nonexistent-key' does not exist in this campaign document.",
      "suggestion": "Use sourceKey 'custom_goblin-captain' or define the missing statblock."
    }
  ]
}
```

## 4. Units Reference

| Quantity | Unit | Example |
|----------|------|---------|
| Map grid width/height | Cells | `"w": 20, "h": 15` |
| Cell size | Pixels | `"cellPx": 48` |
| Token position | Pixels from top-left origin | `"positionX": 100, "positionY": 200` |
| Token size | Cell units (width and height) | `"sizeCols": 1, "sizeRows": 1` |
| Pin position | Pixels from top-left origin | `"x": 200, "y": 150` |
| Map document coordinates | Grid columns/rows (zero-indexed, col then row) | `"startCol": 2, "startRow": 2` |
| Dates | ISO-8601 date-time | `"2024-01-15T10:30:00Z"` |
| Currency | cp, sp, ep, gp, pp (string) | `"currency": "gp"` |
| Currency amount | Number | `"amount": 100.0` |
| In-game date | Integer year/month/day | `"inGameYear": 1492, "inGameMonth": 3, "inGameDay": 15` |

## 5. Name/Key Reference Table

Version 1 uses display names as cross-references (not stable keys). The following table shows which name/key type each reference field uses:

| Source field | Refers to | Key type |
|---|---|---|
| `scene.map` | `map.name` | Display name |
| `scene.encounter` | `encounter.encounterKey` or `encounter.name` | Key (preferred), fallback to name |
| `scene.statblocks[i]` | `statBlock.sourceKey` | Source key |
| `scene.handouts[i]` | `handout.title` | Display title |
| `encounter.map` | `map.name` | Display name |
| `combatant.tokenId` | `token.id` | Token ID string |
| `combatant.statBlockKey` | `statBlock.sourceKey` | Source key |
| `combatant.partyMemberName` | `partyMember.characterName` | Character name |
| `token.statBlockKey` | `statBlock.sourceKey` | Source key |
| `token.partyMemberName` | `partyMember.characterName` | Character name |
| `assignment.holderName` | `partyMember.characterName` | Character name |
| `assignment.magicItemKey` | Magic item `sourceKey` | Compendium source key |
| `assignment.equipmentItemKey` | Equipment item `sourceKey` | Compendium source key |
| `quicknote.targetRef` (type MAP) | `map.key` | Map key (UUID string) |
| `quicknote.targetRef` (type STATBLOCK) | `statBlock.sourceKey` | Source key |
| `quicknote.targetRef` (type NOTE) | `note.title` | Display title |
| `quicknote.targetRef` (type HANDOUT) | `handout.title` | Display title |
| `quicknote.targetRef` (type PARTY_MEMBER) | `partyMember.characterName` | Character name |
| `quicknote.targetRef` (type ENCOUNTER) | `encounter.encounterKey` | Encounter key |
| `quicknote.targetRef` (type SCENE) | `scene.sceneKey` | Scene key |
| `ledger.itemAssignmentRef` | `assignment.id` | UUID |
| `timeline.noteTitle` | `note.title` | Display title |
| `sheet.speciesKey` | Species `sourceKey` | Compendium source key |
| `sheet.backgroundKey` | Background `sourceKey` | Compendium source key |
| `sheet.featRefs[i]` | Feat `sourceKey` | Compendium source key |
| `classLevel.classSourceKey` | Class `sourceKey` | Compendium source key |
| `spellRef.spellKey` | Spell `sourceKey` | Compendium source key |

### Ambiguity behavior

When a display name matches multiple entities, the semantic validator emits an `AMBIGUOUS_REFERENCE` warning (not an error). Import proceeds using the first match. Version 2 will require stable keys and reject ambiguous references.

## 6. Embedded Handout Rules

### MIME types
Only the following content types are accepted:
- `image/png`
- `image/jpeg`
- `image/gif`
- `image/webp`

### Data URL format
```
data:<contentType>;base64,<base64-encoded-bytes>
```

Example: `data:image/png;base64,iVBORw0KGgo...`

The schema requires the `imageData` field to match the pattern `^data:image/(png|jpeg|gif|webp);base64,`.

### Generated storage-name behavior
On import, the original `fileName` value is used as metadata only. The actual file is stored under a UUID-based name: `<uuid>.<ext>`. The extension is derived from the content type. The metadata `fileName` in the export is this generated storage name, not the original upload name.

## 7. Validation Commands

### Through the running app

```bash
# Dry-run a campaign file
curl -X POST -F "file=@path/to/campaign.dmcampaign.json" http://localhost:8080/campaigns/dry-run

# Import a campaign file
curl -X POST -F "file=@path/to/campaign.dmcampaign.json" http://localhost:8080/campaigns/import

# Export a campaign (replace <id> with the campaign UUID)
curl http://localhost:8080/campaigns/<id>/export
```

### Maven contract tests

```bash
# Focused contract suite
./mvnw -Dtest=CampaignSchemaValidatorTest,CampaignDtoSchemaCompatibilityTest,CampaignSemanticValidatorTest,CampaignImportValidatorTest,SchemaControllerTest,CampaignServiceTest,CampaignControllerTest,CampaignImportExportRoundTripTest,GameMapServiceTest,HandoutServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1

# Complete suite
./mvnw test -DargLine=-Duser.home=/tmp/dmhelper-p1-v1
```

## 8. Executable Example

The checked-in feature-complete fixture exercises every DTO section and nested record:

**`src/test/resources/campaigns/v1/feature-complete.dmcampaign.json`**

This fixture is used by the `flagshipFixturePipeline` integration test, which validates the complete pipeline: schema validate → dry-run → import → export → schema validate export → dry-run export → re-import → semantic comparison.

## 9. Not Represented by v1

The following campaign state is **not** represented in the version-1 format:

- Campaign settings (e.g., leveling mode, homebrew rules toggles)
- Current/active scene
- Party current HP (only max HP is persisted; current HP is runtime state)
- Handout visibility and presentation state (presented, given to players, DM-only toggles)
- Encounter combat log entries and dice history
- Calendar configuration (custom month names, lengths, era names)
- Calendar current date
- Complete custom compendium types (custom spells, items, classes, species, backgrounds, feats)
- Structured transitions between scenes
- Quest definitions and objectives

## 10. Version-1 Limitations

The omissions listed above are **version-1 design limitations**, not evidence that export is a complete backup. Version 1 is a contract checkpoint focused on schema-driven validation, closed-object shapes, and basic round-trip fidelity. A campaign exported in version-1 format cannot be used to restore all runtime and configuration state.

Full persistent-state round-trip, ZIP packaging, stable version-2 keys, preview confirmation, and migration infrastructure are the subjects of ongoing development in the campaign-contract-v2 workstream.
