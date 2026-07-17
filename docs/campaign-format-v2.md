# Campaign Format v2

## Container

Two canonical container formats:
- **`.dmcampaign` ZIP** — for asset-bearing packages. Root contains `manifest.json` and `assets/`.
- **`.dmcampaign.json`** — for asset-free packages. Single JSON file.

Legacy `.dmcampaign.json` (v1) is accepted for backward compatibility and migrated to v2.

## Safety Limits

| Limit | Value |
|-------|-------|
| Max upload size | 1 GiB |
| Max ZIP entries | 2,000 |
| Max manifest size | 10 MiB |
| Max per-asset size | 100 MiB |
| Max total expanded size | 1 GiB |
| Max compression ratio | 100:1 |
| Max normalized path length | 240 characters |

## Supported Asset Types

| Media Type | Extension | Magic Bytes |
|------------|-----------|-------------|
| `image/png` | `.png` | `89 50 4E 47 0D 0A 1A 0A` |
| `image/jpeg` | `.jpg` / `.jpeg` | `FF D8 FF` … `FF D9` |
| `image/gif` | `.gif` | `GIF87a` / `GIF89a` |
| `image/webp` | `.webp` | `RIFF` … `WEBP` |

Unsupported media types are rejected with `UNSUPPORTED_MEDIA_TYPE`.

## Package Keys

Keys match `^[a-z0-9][a-z0-9._-]{0,99}$`. Unique per `(campaign, content type)`.
Generated keys use a slugified display name plus the first 12 hex characters of SHA-256 over `type + ':' + stableIdentity`.

Key immutability: once bound to a local entity, the binding is permanent.

## Content References

Two scopes:

**Package reference:**
```json
{ "scope": "PACKAGE", "type": "STATBLOCK", "key": "goblin-chief" }
```

**Catalog reference:**
```json
{ "scope": "CATALOG", "type": "SPELL", "ruleset": "SRD_5_2", "sourceKey": "spell_fireball" }
```

The union is closed — providing `key` on a catalog reference or `sourceKey` on a package reference is a schema error.

SRD statblocks use catalog references and are resolved from the installed `SRD_5_2` catalog during
import. Campaign-owned custom statblocks use package references and are included in
`customStatBlocks`. A sheet spell's `sourceClassRef` is optional because runtime spell records may
legitimately have no originating class; when present it must be a valid class reference.

## Custom Compendium Arrays

## Character Sheet Fields

The v2 manifest carries full character sheet data within each party member's `sheet` object:

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key for the sheet |
| `abilityScores` | object | Map of ability name to score (e.g. `{"str": 10, "dex": 18}`) |
| `classLevels` | ClassLevelDto[] | Class levels with class references, optional subclassRef, levels, hit die rolls |
| `proficiencies` | object | Map of skill/ability to proficiency level |
| `speciesRef` | ContentReference \| null | Reference to the character's species |
| `backgroundRef` | ContentReference \| null | Reference to the character's background |
| `featRefs` | ContentReference[] | References to feats |
| `xp` | int | Experience points |
| `overrides` | object | Override values (e.g. custom speed). May contain `_meta` with per-field override reasons |
| `hitDiceUsed` | int | Number of hit dice already expended |
| `resources` | ResourceDto[] | Per-sheet resources (e.g. Cunning Action, Action Surge) |
| `spells` | SpellRefDto[] | Known/prepared spells with optional source class |
| `spellSlotsUsed` | object | Map of spell level to slots used (e.g. `{"1": 2, "2": 1}`) |
| `attacks` | AttackDto[] | Weapon/natural attacks with bonus, damage, range |
| `features` | FeatureDto[] | Class/racial features with action type, source, and body text |

### ClassLevelDto

| Field | Type | Description |
|-------|------|-------------|
| `classRef` | ContentReference | Base class (CATALOG or PACKAGE) |
| `level` | int | Levels in this class |
| `hitDieRolls` | int[] | HP rolls for levels 2..n |
| `subclassRef` | ContentReference \| null | Optional subclass class entry |

Internal sheet storage uses `classSourceKey` / `subclassSourceKey` strings; the package always uses typed content references.

### AttackDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Unique key within the sheet |
| `name` | string | Attack name |
| `attackBonus` | int | Attack roll modifier |
| `damageExpression` | string | Damage dice expression (e.g. `"1d8+4"`) |
| `damageType` | string | Damage type (e.g. `"piercing"`) |
| `range` | string \| null | Range description |
| `properties` | string \| null | Weapon properties |
| `ammunition` | string \| null | Ammunition type or remaining-count note |
| `notes` | string \| null | Free-text notes |

### FeatureDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Unique key within the sheet |
| `name` | string | Feature name |
| `actionType` | string \| null | Action type (`"action"`, `"bonus"`, `"reaction"`, `"passive"`, etc.) |
| `source` | string \| null | Source class or feature group |
| `body` | string \| null | Feature description/mechanics text |
| `resourceName` | string \| null | Associated resource name if this feature consumes uses |

### Party Member Live State

Party members carry live gameplay state fields at the party member level:

| Field | Type | Description |
|-------|------|-------------|
| `tempHp` | int | Temporary hit points |
| `inspiration` | boolean | Whether the character has inspiration |
| `exhaustion` | int | Exhaustion level (0-6) |
| `deathSaveSuccesses` | int | Successful death saves (0-3) |
| `deathSaveFailures` | int | Failed death saves (0-3) |
| `concentratingOn` | string \| null | Spell key or name the character is concentrating on |
| `conditionsJson` | string \| null | JSON array of active conditions |

### Item Assignment Inventory State

Item assignments carry an `inventoryState` field:

| Value | Description |
|-------|-------------|
| `EQUIPPED` | Currently worn/wielded |
| `CARRIED` | In inventory, not equipped |
| `STASHED` | In storage (e.g. bag of holding) |
| `CONSUMED` | Used up (e.g. potion) |
| `LOST` | Permanently lost |

The manifest carries nine optional arrays for campaign-scoped custom content beyond statblocks.
Each array contains DTOs following the same key/sourceKey pattern as `customStatBlocks`, with an
optional `provenance` block for source tracking.

| Array | Content Type | DTO Fields |
|-------|-------------|------------|
| `customSpells` | Custom spell definitions | key, sourceKey, name, level, school, castingTime, range, components, duration, description, higherLevel, ritual, concentration, provenance |
| `customConditions` | Custom conditions | key, sourceKey, name, description, provenance |
| `customRules` | Custom rule sections | key, sourceKey, name, body, parentKey, sortOrder, ruleset, provenance |
| `customEquipment` | Custom equipment items | key, sourceKey, name, category, cost, weight, properties, description, provenance |
| `customMagicItems` | Custom magic items | key, sourceKey, name, rarity, category, type, description, weight, cost, requiresAttunement, attunementDetail, provenance |
| `customClasses` | Custom character classes | key, sourceKey, name, hitDie, subclassOf, description, savingThrows, features, spellcasting, proficiencies, provenance |
| `customSpecies` | Custom species/races | key, sourceKey, name, size, speed, traits, description, provenance |
| `customBackgrounds` | Custom backgrounds | key, sourceKey, name, abilityScores, featRef, skills, tools, description, equipment, provenance |
| `customFeats` | Custom feats | key, sourceKey, name, category, prerequisite, benefit, provenance |

### Provenance Schema

Each custom entry may carry a `provenance` block tracking the origin of the content:

```json
{
  "sourceTitle": "Homebrew Codex",
  "editionVersion": "1.0",
  "sourceLocator": "p.12",
  "licenseClassification": "ORIGINAL",
  "extractionConfidence": "HIGH"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `sourceTitle` | string \| null | Title of the source document |
| `editionVersion` | string \| null | Version of the source edition |
| `sourceLocator` | string \| null | Page or section reference (e.g. `"p.12"`) |
| `licenseClassification` | enum | One of `ORIGINAL`, `SRD`, `OGL_COMPATIBLE`, `THIRD_PARTY`, `NON_REDISTRIBUTABLE`, `UNKNOWN` |
| `importedAt` | string (ISO-8601) \| null | When the content was imported |
| `converterId` | string \| null | Tool that performed the conversion |
| `converterVersion` | string \| null | Version of the converter |
| `sourceHash` | string \| null | Hash of the original source material |
| `extractionConfidence` | enum \| null | One of `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN` |

### subclassOf Resolution

The `customClasses` array stores `subclassOf` as a free-text string. On import:

- If the value matches a **SRD sourceKey** (e.g. `"class_wizard"`), the custom class is linked to
  that SRD parent class as a subclass.
- If the value matches a **package key** of another entry in `customClasses`, the custom class is
  linked to another campaign-scoped custom class.
- If neither resolution succeeds, `subclassOf` is stored as-is for deferred/link-time resolution.

### Dependency Rules

- Custom content arrays are **campaign-scoped only**. All entries in these arrays are imported into
  the target campaign and bound to it.
- **User-global custom content** (content with `campaign IS NULL`) is **not** automatically included
  in the export. The export only captures campaign-scoped custom entries. Global custom content
  must be manually included or handled at the application layer.
- References from campaign entities (character sheets, treasury assignments, tokens, encounters) to
  custom content use **package-scoped content references** with the appropriate `CampaignContentType`.
  SRD content continues to use `scope: CATALOG`. `LibraryContentReferenceResolver` is the single
  export/import path for this split.

### Non-Campaign Custom Export Rejection

When a non-campaign (user-global) custom entry is encountered during export — for example via a
sheet or treasury reference that points at `source=CUSTOM` and `campaign IS NULL` — export throws
`CampaignPackageException` with code `NON_CAMPAIGN_CUSTOM_DEPENDENCY`. Clone the content into the
campaign before export so the package stays self-contained.

### Example

```json
{
  "customSpells": [
    {
      "key": "arc-bolt",
      "sourceKey": "homebrew-arc-bolt",
      "name": "Arc Bolt",
      "level": 1,
      "school": "Evocation",
      "castingTime": "1 action",
      "range": "60 feet",
      "components": "V, S",
      "duration": "Instantaneous",
      "description": "A crackling bolt of force.",
      "higherLevel": null,
      "ritual": false,
      "concentration": false,
      "provenance": {
        "sourceTitle": "Homebrew Codex",
        "editionVersion": "1.0",
        "sourceLocator": "p.12",
        "licenseClassification": "ORIGINAL",
        "extractionConfidence": "HIGH"
      }
    }
  ]
}
```

## Open Session State

The manifest carries the current open session when one is active (nullable — `null` means IDLE):

| Field | Type | Description |
|-------|------|-------------|
| `session` | object \| null | `null` when no session is open; otherwise contains the fields below |
| `session.status` | `"RUNNING"` \| `"PAUSED"` \| `"REVIEW"` | Session lifecycle status |
| `session.presentationMode` | `"CURTAIN"` \| `"MAP"` \| `"HANDOUT"` | What is shown on the player view |
| `session.startedAt` | string (ISO-8601) | Real-world timestamp when the session started |
| `session.planNoteRef` | ContentReference \| null | Typed ref to a session-plan note |
| `session.workspaceMapRef` | ContentReference \| null | Typed ref to the current workspace map |
| `session.attendeeRefs` | ContentReference[] | Typed refs to attending party members |
| `session.presentedRef` | ContentReference \| null | Typed ref to the presented map or handout |
| `session.sceneVisits` | object[] | Ordered visit records: `{sceneRef, visitedAt}` timestamps |
| `session.draftBody` | string \| null | Session-log draft body; only present when `status` is `REVIEW` |

### Invariant checks

- `presentationMode` must be consistent with `presentedRef`: `CURTAIN` → `null`, `MAP` → map ref, `HANDOUT` → handout ref.
- `draftBody` must be `null` unless `status` is `REVIEW`.
- `attendeeRefs` reference campaign party members captured for that session; later roster deactivation does not rewrite historical attendance.
- `sceneVisits` timestamps must be monotonic within a session.

### Deterministic recovery on import

When a session is open at export time, import recreates its lifecycle status, timestamps, start
date, plan, workspace map, attendance, scene visits, review draft, and presentation selection using
stable package references. The receiving process restores the latest open presentation after
startup. The generated player PIN remains process-local and is intentionally not exported.

## Schema URLs

- Campaign manifest: `GET /api/v1/schemas/campaign-format-v2`
- Map document: `GET /api/v1/schemas/map-document-v2.schema.json`

Both are JSON Schema draft 2020-12 and resolve offline.

## Typed Catalog

`GET /api/v1/catalog` returns a `CatalogSnapshot` with typed entries. Each entry has:
- `type` — `CampaignContentType`
- `sourceKey` — catalog identifier
- `name` — display name
- `ruleset` — always `SRD_5_2`
- `source` — always `SRD`
- `aliases` — empty list

`GET /api/v1/catalog/snapshot` returns the checked-in snapshot as raw JSON with an `ETag` equal to the quoted SHA-256.

### Snapshot Hash Integrity

The checked-in catalog snapshot `src/main/resources/catalog/srd-5.2-catalog.json`
is verified against the live `CampaignCatalogService.snapshot()` by
`CatalogSnapshotFidelityTest`. Any change to catalog data must be accompanied
by a snapshot refresh (`-Ddmhelper.writeCatalogSnapshot=true`). A drift
between the two fails the build.

## Validation Pipeline

```text
container safety
  → source schema/semantic validation
  → compatibility migration (v1→v2)
  → v2 schema/deserialization
  → key/reference uniqueness
  → typed catalog resolution
  → spatial/state validation
  → asset descriptor/file validation
  → preview construction/retention
```

Earlier-stage errors prevent later calls.

## Import Preview

**`POST /campaigns/package-imports/previews`** — accept raw JSON/ZIP body with `Content-Type` and `X-DMHelper-Filename`.

Preview statuses:
- `BLOCKED` — import cannot proceed (errors present, no `previewId`)
- `READY` — validation passed; the DM may confirm without accepting warnings
- `CONFIRM_WARNINGS` — warnings require explicit acceptance

**`POST /campaigns/package-imports/{previewId}/confirm?acceptWarnings={boolean}`** — confirms import.

**`DELETE /campaigns/package-imports/{previewId}`** — discards staged preview.

Previews expire after 30 minutes. On expiry or discard, staging data is cleaned. A failed confirmation
keeps the preview available for retry; a committed import removes it. Missing/expired previews and
malformed container requests return `application/problem+json` with a stable `code`.

## Export

**`GET /campaigns/{campaignId}/package`** — canonical v2 export (JSON for asset-free, ZIP for assets).
Supports `includeCombatLog` and `includeDiceHistory` query params (both default `true`).

**`GET /campaigns/{campaignId}/export`** — legacy v1 JSON export.

## V1 Migration

Legacy v1 packages are migrated with:
- Deterministic package key generation
- Reference rewriting (name-based to key-based)
- Embedded image extraction to package assets
- Warning `LEGACY_REFERENCE_MIGRATED` per migrated reference
- Label `MIGRATED_FROM_V1`

## Campaign Settings

The v2 manifest carries full campaign settings:

| Field | Type | Description |
|-------|------|-------------|
| `levelingMode` | `"XP"` \| `"MILESTONE"` | Advancement method |
| `calendarConfig` | object | Custom month names/lengths, week days, epoch |
| `currentDate` | object | In-game year, month, day |
| `settings` | object | Arbitrary key-value settings (e.g. `difficulty`, `maxLevel`) |

All settings fields are persistent and included in the default export.

## Runtime Kind Enums

Tokens, combatants, and party members use the following `kind` values:

| Value | Usage |
|-------|-------|
| `PC` | Player character |
| `NPC` | Non-player character (friendly/neutral) |
| `MONSTER` | Hostile creature |
| `OBJECT` | Inanimate object (door, trap, hazard) |

These are stored as strings in the manifest. The importer preserves the exact value.

## Structured Scene Enums

Structured scenes introduce typed fields backed by string-valued enums:

| Enum | Values | Field |
|------|--------|-------|
| `SceneStatus` | `UNVISITED`, `VISITED`, `DONE` | `scene.status` |
| `SceneSectionKind` | `READ_ALOUD`, `DM_ADVICE`, `SECRET`, `FEATURE`, `TRAP`, `HAZARD`, `ENVIRONMENT`, `PUZZLE`, `TREASURE`, `DEVELOPMENT`, `CONSEQUENCE`, `SCALING` | `scene.sections[].kind` |
| `SceneCheckVisibility` | `PLAYER_FACING`, `DM_FACING`, `PASSIVE` | `scene.checks[].visibility` |
| `SceneParticipantDisposition` | `HOSTILE`, `UNFRIENDLY`, `NEUTRAL`, `FRIENDLY`, `ALLY`, `UNKNOWN` | `scene.participants[].disposition` |
| `SceneTransitionKind` | `CHOICE`, `ENTRANCE`, `EXIT` | `scene.transitions[].kind` |
| `SceneLinkRole` | `REFERENCE`, `HANDOUT`, `RULE`, `QUEST`, `TIMELINE_EVENT`, `RELATED_SCENE`, `NPC`, `LOCATION` | `scene.links[].role` |
| `SceneLinkTargetScope` | `PACKAGE`, `CATALOG` | `scene.links[].targetRef.scope` |

## Structured Quest Enums

| Enum | Values | Field |
|------|--------|-------|
| `QuestStatus` | `NOT_STARTED`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `FAILED`, `ABANDONED` | `quest.status` |
| `QuestObjectiveStatus` | `NOT_STARTED`, `ACTIVE`, `COMPLETED`, `FAILED`, `SKIPPED` | `quest.objectives[].status` |
| `QuestObjectiveCompletionMode` | `ALL`, `ANY` | `quest.objectives[].completionMode` |
| `QuestLinkRole` | `GIVER`, `REFERENCE`, `HANDOUT`, `RULE`, `RELATED_SCENE`, `NPC`, `LOCATION`, `FACTION`, `TIMELINE_EVENT`, `REWARD` | `quest.links[].role` |

## Source Annotation Enums

| Enum | Values | Field |
|------|--------|-------|
| `SourceAnnotationConfidence` | `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN` | `annotation.confidence` |
| `SourceAnnotationStatus` | `OPEN`, `RESOLVED`, `DISMISSED` | `annotation.status` |

## Structured Scene DTO Fields

Each `SceneDto` in the manifest carries the following fields beyond the v1 scene contract:

| Field | Type | Description |
|-------|------|-------------|
| `summary` | string \| null | Short DM-facing summary of the scene |
| `sourceLocator` | string \| null | Page/book reference (e.g. `"book:42"`) |
| `tags` | string[] | Free-text tags for filtering |
| `mapRegionKey` | string \| null | Unvalidated free-text key referencing a map region; **not** a resolved reference (see note below) |
| `sections` | SceneSectionDto[] | Ordered content blocks (read-aloud, DM advice, secret text, etc.) |
| `checks` | SceneCheckDto[] | Ability checks tied to the scene |
| `participants` | SceneParticipantDto[] | Creatures or NPCs present in the scene |
| `transitions` | SceneTransitionDto[] | Navigation options leaving the scene |
| `links` | SceneLinkDto[] | Cross-references to notes, handouts, rules, quests |

### SceneSectionDto

```json
{ "kind": "READ_ALOUD", "label": "Read Aloud", "body": "...", "sourceLocator": "book:5", "sortOrder": 1 }
```

`kind` must be one of the `SceneSectionKind` values. `label` is optional and defaults to the kind's display name.

### SceneCheckDto

```json
{
  "label": "Investigate", "ability": "wis", "skill": "perception", "dc": 12,
  "visibility": "PLAYER_FACING",
  "success": "You find tracks", "failure": "You see nothing", "partial": "Some disturbed dust",
  "ruleRef": { "scope": "CATALOG", "type": "RULE", "ruleset": "SRD_5_2", "sourceKey": "skill_perception" },
  "sourceLocator": "book:5", "sortOrder": 1
}
```

`ability` and `skill` are free-text strings (e.g. `"wis"`, `"perception"`). `visibility` controls whether the DC and results are player-facing, DM-only, or passive. `ruleRef` is an optional catalog reference to a compendium rule.

### SceneParticipantDto

```json
{
  "displayName": "Town Guard", "quantity": 2, "disposition": "FRIENDLY",
  "placementHint": "At the gate",
  "statblockRef": { "scope": "PACKAGE", "type": "STATBLOCK", "key": "town-guard" },
  "noteRef": { "scope": "PACKAGE", "type": "NOTE", "key": "note-guards" },
  "sourceLocator": "book:5", "sortOrder": 1
}
```

`disposition` is a `SceneParticipantDisposition` enum. `statblockRef` and `noteRef` are optional typed references. `placementHint` is free-text guidance for the DM.

### SceneTransitionDto

```json
{
  "key": "t-go-forest",
  "kind": "CHOICE",
  "label": "Head into the forest",
  "targetSceneRef": { "scope": "PACKAGE", "type": "SCENE", "key": "sc-forest" },
  "externalDestination": null,
  "condition": "if day time",
  "dmNote": "Forest is dangerous at night",
  "sourceLocator": "book:6",
  "sortOrder": 1
}
```

**Transition kind rules:**
- `CHOICE` — must provide `targetSceneRef`; `externalDestination` must be null. Represents a player-driven choice that leads to another structured scene.
- `ENTRANCE` — must provide `targetSceneRef`; `externalDestination` must be null. Represents an automatic entry into the scene (e.g., a door from another map region).
- `EXIT` — must provide `externalDestination` (free-text); `targetSceneRef` must be null. Represents leaving the adventure structure (e.g., "Overworld", "Town").

The CHECK constraint `ck_scene_transition_target` enforces the mutual exclusion at the database level.

### SceneLinkDto

```json
{
  "role": "RULE",
  "targetRef": { "scope": "CATALOG", "type": "RULE", "ruleset": "SRD_5_2", "sourceKey": "condition_frightened" },
  "displayText": "Frightened condition",
  "condition": "if the guard succeeds",
  "sortOrder": 1
}
```

`role` is a `SceneLinkRole` enum. `targetRef` follows the standard typed-reference rules (see below). `displayText` is optional human-readable text; `condition` is optional narrative context.

### Typed-Reference Rules

All content references in the item-6 DTOs follow the same `ContentReference` structure used throughout v2:

- **Package reference** — `{ "scope": "PACKAGE", "type": "<CampaignContentType>", "key": "<package-key>" }`.
  Used for references within the same campaign. The `type` must match a `CampaignContentType` enum value (e.g. `SCENE`, `OBJECTIVE`, `STATBLOCK`, `NOTE`).
- **Catalog reference** — `{ "scope": "CATALOG", "type": "<CampaignContentType>", "ruleset": "SRD_5_2", "sourceKey": "<stable-id>" }`.
  Used for references to SRD/compendium content. `ruleset` is required for catalog references.

The union is closed: providing `key` on a catalog reference or `sourceKey`/`ruleset` on a package reference is a schema error.

For item-6, the following `CampaignContentType` values appear in references:
`ADVENTURE`, `CHAPTER`, `SCENE`, `TRANSITION`, `QUEST`, `OBJECTIVE`, `STATBLOCK`, `NOTE`, `HANDOUT`, `RULE`, `PARTY_MEMBER`, `SOURCE_ANNOTATION`, `SESSION_OBJECTIVE_CHANGE`.

## Quest DTO Fields

### QuestDto

```json
{
  "key": "quest-treasure",
  "title": "Find the Lost Treasure",
  "status": "ACTIVE",
  "summary": "Find the lost treasure hidden in the dark forest.",
  "sourceLocator": "book:3",
  "tags": ["main", "treasure"],
  "rewards": "1000 XP and a magic item",
  "prerequisites": "Must be level 3+",
  "outcomeNotes": "Treasure contains a map to an even greater prize.",
  "links": [...],
  "objectives": [...],
  "createdAt": "2025-07-01T10:00:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | `QuestStatus` enum: `NOT_STARTED`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `FAILED`, `ABANDONED` |
| `summary` | string \| null | Short quest summary |
| `sourceLocator` | string \| null | Page/book reference |
| `tags` | string[] | Free-text tags |
| `rewards` | string \| null | Free-text reward description |
| `prerequisites` | string \| null | Free-text prerequisites |
| `outcomeNotes` | string \| null | Free-text outcome notes for the DM |
| `links` | QuestLinkDto[] | Cross-references (same structure as SceneLinkDto) |

### QuestObjectiveDto

```json
{
  "key": "obj-find-cave",
  "title": "Find the hidden cave",
  "description": "Search the forest for the entrance",
  "status": "NOT_STARTED",
  "completionMode": "ALL",
  "sortOrder": 1,
  "prerequisiteRefs": [
    { "scope": "PACKAGE", "type": "OBJECTIVE", "key": "obj-find-cave" }
  ],
  "sourceLocator": "book:3"
}
```

**Objective dependency semantics:**
- `completionMode` controls how sub-objectives of a parent are evaluated:
  - `ALL` — every prerequisite objective must reach `COMPLETED` before this objective can become `ACTIVE`.
  - `ANY` — any one prerequisite objective reaching `COMPLETED` is sufficient.
- Objective status is **DM-controlled**: only the DM (or automated DM tooling) transitions status via the UI or API. There is no automatic status inference from combat outcomes.
- Objectives with no `prerequisiteRefs` are available immediately (root objectives).
- The `prerequisiteRefs` list references other `OBJECTIVE` entries within the same campaign package via typed package references.

## Source Annotation Format

Source annotations track the provenance and confidence of content extracted from published adventures or external sources.

```json
{
  "key": "ann-village-scene",
  "ownerRef": { "scope": "PACKAGE", "type": "SCENE", "key": "sc-village" },
  "fieldPath": "sections[0].body",
  "message": "Paraphrased from original text",
  "confidence": "HIGH",
  "sourceLocator": "book:5",
  "status": "OPEN",
  "resolutionNote": "Verify against original",
  "createdAt": "2025-07-01T10:00:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `ownerRef` | ContentReference | Typed ref to the owning entity (scene, quest, etc.) |
| `fieldPath` | string \| null | JSON pointer to the annotated field |
| `message` | string | Annotation text |
| `confidence` | string | `SourceAnnotationConfidence` enum |
| `sourceLocator` | string \| null | Source page/book reference |
| `status` | string | `SourceAnnotationStatus` enum |
| `resolutionNote` | string \| null | Note on how the annotation was resolved |

Annotations are stored at the campaign level and follow the adapter order at position 980 (between Quest 950 and Notes 1000).

## Session Objective-Change Format

Session objective changes record the history of objective status transitions during a session.

```json
{
  "key": "obj-change-accept",
  "objectiveRef": { "scope": "PACKAGE", "type": "OBJECTIVE", "key": "obj-find-cave" },
  "previousStatus": "NOT_STARTED",
  "newStatus": "ACTIVE",
  "changedAt": "2025-07-16T18:05:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `objectiveRef` | ContentReference | Typed ref to the objective (always package-scoped) |
| `previousStatus` | string \| null | The `QuestObjectiveStatus` before the change |
| `newStatus` | string | The `QuestObjectiveStatus` after the change |
| `changedAt` | string (ISO-8601) | Real-world timestamp |

These are nested inside the `session` object under `session.objectiveChanges[]`.

## V1 Compatibility

Legacy v1 scenes (created before the item-6 structured scene migration) receive `null` or empty values for all new item-6 fields:

| Field | Legacy Value |
|-------|-------------|
| `summary` | `null` |
| `sourceLocator` | `null` |
| `tags` | `[]` |
| `mapRegionKey` | `null` |
| `sections` | `[]` |
| `checks` | `[]` |
| `participants` | `[]` |
| `transitions` | `[]` |
| `links` | `[]` |

Legacy campaigns (without a `quests` or `annotations` array) simply omit these sections. The import adapter treats `null` section arrays as empty — no migration defaulting is required.

## mapRegionKey Note

`mapRegionKey` is stored as **unvalidated free-text**. It is not resolved against any map region registry during import or export. A future delivery item (item 9) may introduce a proper typed reference. Until then, importing a package with `mapRegionKey` set simply preserves the string value; no referential integrity check is performed.

## Persistent vs Transient Classification

Every campaign-owned field is classified as either:

- **Persistent-exported** — included in v2 export and preserved on import. All fields not listed
  below are persistent-exported.
- **Intentionally transient** — excluded by design. Currently transient:
  - Runtime presentation state (curtain and map projection)
  - WebSocket connection state
  - In-memory caches and computed aggregates
  - Session PIN
  - Browser-local UI state

The manifest includes `metadata.exclusions` listing any persistent fields that are intentionally
omitted from the current export. An empty list means full coverage.

## Module Adapter Architecture

Export and import are decomposed into ordered section adapters:

```text
Adapters (order):
  CampaignSectionAdapter       (100) — campaign metadata and settings
  LibrarySectionAdapter        (200) — custom statblocks
  PartySectionAdapter          (300) — party roster, sheets, resources, spells
  MapSectionAdapter            (400) — maps, tokens, documents, map assets
  HandoutSectionAdapter        (500) — handouts, flags, and assets
  EncounterSectionAdapter      (600) — encounters, combatants, combat log
  TreasurySectionAdapter       (700) — item assignments
  LedgerSectionAdapter         (800) — ledger entries
  AdventureSectionAdapter      (900) — adventures, chapters, scenes
  QuestSectionAdapter          (950) — quests, objectives, dependencies
  SourceAnnotationSectionAdapter (980) — source annotations
  NotesSectionAdapter         (1000) — notes, links, quick notes
  SessionSectionAdapter       (1150) — session state, scene visits, objective changes
  CalendarSectionAdapter      (1100) — timeline events
  DiceSectionAdapter          (1200) — dice history
```

Each adapter implements `CampaignSectionExporter` and `CampaignSectionImporter`.
The `CampaignExportCoordinator` and `CampaignImportCoordinator` call them in order
and manage the shared key registry, asset store, and transaction boundary.

## Default-Complete Export

The `/campaigns/{id}/package` endpoint exports **all** persistent-exported fields by default.
No explicit opt-in is required. The manifest is self-contained — every entity, reference, and
asset needed to restore the campaign is included.

## Explicit History Opt-Outs

Two query parameters control optional history inclusion:

| Parameter | Default | Effect |
|-----------|---------|--------|
| `includeCombatLog` | `true` | When `false`, combat log entries are omitted from the manifest and `metadata.exclusions` lists `COMBAT_LOG` |
| `includeDiceHistory` | `true` | When `false`, dice history is omitted from the manifest and `metadata.exclusions` lists `DICE_HISTORY` |

Both default to `true`, matching the default-complete behavior. The UI presents both as
pre-checked checkboxes. The export manifest records exclusions so re-import knows what
was intentionally omitted.

## Atomicity

Import persists nothing until validation passes and the DM confirms. Campaign persistence, key binding,
and asset installation share one transaction. All package keys, including nested sheet/resource, token,
combatant, chapter, and scene keys, bind through persistence receipts in that transaction. On rollback,
no campaign row, key row, or installed asset survives.

## Fixture Locations

Four flagship fixtures verify the round-trip contract:

| Fixture | Path | Purpose |
|---------|------|---------|
| Minimal v2 | `src/test/resources/campaigns/v2/minimal.dmcampaign.json` | Asset-free, single entity — validates structural schema |
| Feature-complete v2 | `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` | Exercises every current section, relationship, history, and asset type |
| Published-adventure-shaped v2 | `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` | Exercises larger ordered adventure content and repeated references |
| Structured-adventure-quest v2 | `src/test/resources/campaigns/v2/structured-adventure-quest.dmcampaign/manifest.json` | Exercises structured scenes (sections, checks, participants, transitions, links), quests with objectives and dependencies, source annotations, and session objective changes |

Each fixture follows schema validate → dry-run → import → export → re-import → semantic
deep-compare. Imported database snapshots also receive a normalized, repository-backed projection
that is independent of the package-v2 adapters. The feature-complete fixture additionally runs the
combat-log opt-out, dice-history opt-out, and combined opt-out variants. If any fixture loses meaning,
changes an unrelated section, or violates rollback atomicity, the build fails.

## curl Examples

Preview a v2 JSON package:
```bash
curl -X POST http://localhost:8080/campaigns/package-imports/previews \
  -H "Content-Type: application/json" \
  -H "X-DMHelper-Filename: my-campaign.dmcampaign.json" \
  --data-binary @my-campaign.dmcampaign.json
```

Confirm import with warnings:
```bash
curl -X POST "http://localhost:8080/campaigns/package-imports/{previewId}/confirm?acceptWarnings=true"
```

Export v2 package:
```bash
curl -O http://localhost:8080/campaigns/{campaignId}/package
```
