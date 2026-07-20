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

## Encounter Preparation (v2)

### Waves

Each encounter may include ordered `waves[]`:

| Field | Type | Notes |
|-------|------|-------|
| `key` | package key | Unique within the encounter |
| `name` | string | Display label |
| `sortOrder` | int | Editorial order |
| `status` | enum | `RESERVE` \| `PENDING` \| `ACTIVE` \| `DEPLETED` |
| `triggerKind` | enum | `MANUAL` \| `ROUND` \| `HP_THRESHOLD` \| `CUSTOM` |
| `triggerValue` | string \| null | e.g. `"3"` for round 3 |
| `notes` | string \| null | DM-only |

Combatants may set `waveKey` (package-local), `startX`/`startY` (pixels from top-left), and `placementRegionKey` (map region key).

### Prep and rewards

`prep` object: tactics, morale, surrender/flee, environment notes, source locator, optional scene ref.
`rewards` object: XP, currency entries, item grants, quest objective refs, free-form notes.
Reward application is **DM-confirmed at table**; the package only carries the authored draft.

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

For item-6 and world-graph, the following `CampaignContentType` values appear in references:
`ADVENTURE`, `CHAPTER`, `SCENE`, `TRANSITION`, `QUEST`, `OBJECTIVE`, `STATBLOCK`, `NOTE`, `HANDOUT`, `RULE`, `PARTY_MEMBER`, `SOURCE_ANNOTATION`, `SESSION_OBJECTIVE_CHANGE`,
`WORLD_NPC`, `WORLD_LOCATION`, `FACTION`, `WORLD_RELATIONSHIP`, `FACTION_CLOCK`.

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

## World Graph Arrays

The manifest carries five optional arrays for world graph entities. These represent the campaign's
world-building state — NPCs, locations, factions, relationships, and faction progress clocks.

| Array | Description |
|-------|-------------|
| `worldNpcs` | NPCs with faction/location affiliations, appearance, voice, motivation, secrets |
| `worldLocations` | Named locations with kind, parent hierarchy, travel/encounter refs, secrets |
| `factions` | Faction organizations with goals, resources, reputation notes |
| `worldRelationships` | Typed directed relationships between NPCs, factions, and locations |
| `factionClocks` | Progress clocks tracking faction goals, with segment count and filled amount |

### WorldNpcDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `name` | string | NPC display name |
| `role` | string \| null | Role or title (e.g. "Harbor contact") |
| `disposition` | string \| null | `WorldDisposition` enum: `HOSTILE`, `UNFRIENDLY`, `NEUTRAL`, `FRIENDLY`, `ALLY`, `UNKNOWN` |
| `factionRef` | ContentReference \| null | Package reference to an entry in `factions` |
| `locationRef` | ContentReference \| null | Package reference to an entry in `worldLocations` |
| `noteRef` | ContentReference \| null | Package reference to an entry in `notes` |
| `statblockRef` | ContentReference \| null | Package or catalog reference to a statblock |
| `appearance` | string \| null | Physical description |
| `voice` | string \| null | Voice description |
| `motivation` | string \| null | What drives the NPC |
| `secret` | string \| null | DM-only secret (not exposed to player endpoints) |
| `inventoryText` | string \| null | Free-text inventory |
| `status` | string \| null | `WorldNpcStatus` enum: `ALIVE`, `DEAD`, `MISSING`, `UNKNOWN` |
| `tags` | string[] | Free-text tags |
| `sourceLocator` | string \| null | Page/book reference |
| `createdAt` | string (ISO-8601) | Creation timestamp |

### WorldLocationDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `name` | string | Location display name |
| `kind` | string \| null | `LocationKind` enum: `SITE`, `REGION`, `SETTLEMENT`, `PLANE`, `OTHER` |
| `parentLocationRef` | ContentReference \| null | Package reference to a parent location |
| `mapRef` | ContentReference \| null | Package reference to a map |
| `mapRegionKey` | string \| null | Free-text map region key |
| `noteRef` | ContentReference \| null | Package reference to a note |
| `summary` | string \| null | Short public summary |
| `services` | string \| null | Available services (shops, temples, etc.) |
| `secrets` | string \| null | DM-only secrets (not exposed to player endpoints) |
| `occupantNpcRefs` | ContentReference[] | Package references to NPCs at this location |
| `encounterRefs` | ContentReference[] | Package references to encounters at this location |
| `travelLocationRefs` | ContentReference[] | Package references to reachable locations |
| `tags` | string[] | Free-text tags |
| `sourceLocator` | string \| null | Page/book reference |
| `createdAt` | string (ISO-8601) | Creation timestamp |

### FactionDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `name` | string | Faction display name |
| `goals` | string \| null | Faction's current goals |
| `resources` | string \| null | Faction's available resources |
| `reputationNotes` | string \| null | Notes on faction reputation |
| `noteRef` | ContentReference \| null | Package reference to a note |
| `tags` | string[] | Free-text tags |
| `sourceLocator` | string \| null | Page/book reference |
| `createdAt` | string (ISO-8601) | Creation timestamp |

### WorldRelationshipDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `kind` | string | `RelationshipKind` enum: `ALLY`, `ENEMY`, `RIVAL`, `MEMBER_OF`, `LEADS`, `SERVES`, `RELATED`, `KNOWS`, `OWNS`, `LOCATED_IN`, `TRAVELS_TO`, `OTHER` |
| `fromRef` | ContentReference | Source entity reference (WORLD_NPC, FACTION, WORLD_LOCATION) |
| `toRef` | ContentReference | Target entity reference |
| `directed` | boolean | Whether the relationship has a direction |
| `knowledge` | string | `RelationshipKnowledge` enum: `PUBLIC`, `SECRET` |
| `status` | string | `RelationshipStatus` enum: `ACTIVE`, `STRAINED`, `BROKEN`, `UNKNOWN` |
| `notes` | string \| null | DM-only notes |
| `sourceLocator` | string \| null | Page/book reference |
| `sortOrder` | int | Editorial sorting |

### FactionClockDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `factionRef` | ContentReference | Package reference to the owning faction |
| `title` | string | Clock display title |
| `segments` | int | Total segments (typically 4, 6, or 8) |
| `filled` | int | Number of filled segments |
| `objectiveRef` | ContentReference \| null | Package reference to a quest objective |
| `sceneRef` | ContentReference \| null | Package reference to a scene |
| `notes` | string \| null | DM-only notes |
| `sourceLocator` | string \| null | Page/book reference |
| `sortOrder` | int | Editorial sorting |

### Content Types

World graph entities use the following `CampaignContentType` values in references:
`WORLD_NPC`, `WORLD_LOCATION`, `FACTION`, `WORLD_RELATIONSHIP`, `FACTION_CLOCK`.

World entity references follow the same typed-reference rules as other sections. NPC `factionRef`
must reference a `FACTION`, `locationRef` must reference a `WORLD_LOCATION`, and relationship
`fromRef`/`toRef` must use the correct entity type for the relationship kind.

### Player Safety

World graph routes are DM-only (PIN-gated under `/campaigns/{campaignId}/world/`). The `/player`
endpoint and WebSocket table state do not include world entity data. NPC secrets and location
secrets are never exposed to player-facing endpoints.

## Rollable Tables

The manifest carries an optional `rollableTables` array. Each entry defines a random-result table
with entries that may reference statblocks, magic items, equipment, spells, or nested tables.

### RollableTableDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `sourceKey` | string | Stable source identifier |
| `name` | string | Display name |
| `description` | string \| null | Free-text description |
| `addressMode` | string | `RANGE` or `WEIGHTED` |
| `rollExpression` | string \| null | Dice expression for range mode (e.g. `"1d20"`) |
| `category` | string | `ENCOUNTER`, `TREASURE`, `WEATHER`, `RUMOR`, `EVENT`, or `GENERIC` |
| `tags` | string[] | Free-text tags |
| `entries` | RollableTableEntryDto[] | Table entries |
| `createdAt` | string (ISO-8601) | Creation timestamp |
| `provenance` | ProvenanceDto \| null | Source provenance |

### RollableTableEntryDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Entry key (unique within the table) |
| `rangeStart` | int \| null | Start of the range (inclusive, RANGE mode) |
| `rangeEnd` | int \| null | End of the range (inclusive, RANGE mode) |
| `weight` | int \| null | Relative weight (WEIGHTED mode) |
| `resultText` | string \| null | Display text for the rolled result |
| `quantityExpression` | string \| null | Dice expression for quantity (e.g. `"2d4"`) |
| `references` | ContentReference[] | Linked entities (statblocks, items, nested tables) |

### Example

```json
{
  "key": "rt-forest-encounters",
  "sourceKey": "custom_forest-encounters",
  "name": "Forest Encounters",
  "addressMode": "RANGE",
  "rollExpression": "1d12",
  "category": "ENCOUNTER",
  "tags": ["forest", "random", "encounter"],
  "createdAt": "2025-07-16T18:00:00Z",
  "entries": [
    {
      "key": "fe-goblins",
      "rangeStart": 1,
      "rangeEnd": 3,
      "resultText": "Goblins",
      "quantityExpression": "2d4",
      "references": [
        {"scope": "PACKAGE", "type": "STATBLOCK", "key": "statblock-goblin-captain"}
      ]
    },
    {
      "key": "fe-nested-weather",
      "rangeStart": 10,
      "rangeEnd": 10,
      "resultText": "Sudden storm",
      "references": [
        {"scope": "PACKAGE", "type": "ROLLABLE_TABLE", "key": "rt-road-weather"}
      ]
    }
  ]
}
```

### World Location Table Links

World locations may carry a `tableLinks` array linking rollable tables to specific locations:

```json
{
  "key": "wl-ruins-approach",
  "name": "Ruins Approach",
  "kind": "SITE",
  "tableLinks": [
    {
      "role": "RANDOM_ENCOUNTERS",
      "tableRef": {"scope": "PACKAGE", "type": "ROLLABLE_TABLE", "key": "rt-ruins-random-encounters"},
      "sortOrder": 1
    }
  ]
}
```

### Scene Links to Tables

Scenes can link to rollable tables via `scene.links[]` with role `RANDOM_ENCOUNTERS`:

```json
{
  "role": "RANDOM_ENCOUNTERS",
  "targetRef": {"scope": "PACKAGE", "type": "ROLLABLE_TABLE", "key": "rt-ruins-random-encounters"},
  "displayText": "Random encounters",
  "sortOrder": 1
}
```

### Module Adapter

Rollable tables use their own adapter at position 150, between Campaign (100) and Library (200):

```text
CampaignSectionAdapter       (100) — campaign metadata and settings
RollableTableSectionAdapter  (150) — rollable tables, entries, references
LibrarySectionAdapter        (200) — custom statblocks (+ threat library closure seed)
ThreatSectionAdapter         (250) — traps and hazards
```

### Export Closure and Local Roll Evidence

Export begins with campaign-owned tables plus tables linked by campaign scenes and world locations,
then follows nested table references transitively. Referenced user-global table definitions and the
exact user-global custom statblocks/items/spells they require are copied into the package with their
source keys and provenance; on import they become campaign-scoped `CUSTOM` rows. Unreferenced global
content is excluded. Catalog-scoped SRD entry references remain catalog references.

`table_roll_log` rows—including grouped result JSON, table key/name snapshots, draft status,
resolution time, and created target IDs—are persistent local operational evidence and are not
exported. Deleting a table clears the nullable definition link but preserves these snapshots and
draft states.

## Traps and Hazards

The manifest carries optional top-level `traps` and `hazards` arrays (default empty for older v2
packages). Threats are DM-only reusable definitions with typed mechanics, provenance, and
references. Scene sections, encounter combatants, and map pins reference them via `threatRef`
content references with type `TRAP` or `HAZARD`.

### TrapDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `sourceKey` | string | Stable source identifier |
| `name` | string | Display name |
| `description` | string | Sanitized Markdown body |
| `severity` | string | `SETBACK`, `DANGEROUS`, or `DEADLY` |
| `minLevel` / `maxLevel` | int \| null | Optional level band 1–20 |
| `triggerDescription` | string \| null | Escaped plain trigger text |
| `triggerAreaHint` | string \| null | Area hint |
| `detectionPassiveThreshold` | int \| null | Passive Perception threshold (0–40 representational) |
| `detectionCheck` | ThreatCheckDto \| null | Active detection check/save |
| `disarmMethods` | TrapDisarmMethodDto[] | Stable-keyed disarm options |
| `attackBonus` | int \| null | Mutually exclusive with `save` |
| `save` | ThreatCheckDto \| null | Mutually exclusive with `attackBonus` |
| `damage` | ThreatDamageDto \| null | Expression + damage type enums |
| `additionalEffect` | string \| null | Escaped plain text |
| `resetMode` | string | `NONE`, `MANUAL`, or `AUTOMATIC` |
| `resetTiming` | string \| null | Required when `resetMode` is `AUTOMATIC` |
| `statBlockRef` | ContentReference \| null | Optional related creature |
| `countermeasureNotes` | string \| null | Escaped plain text |
| `conditionRefs` | ContentReference[] | Applied-condition library targets |
| `salvageItemRefs` | ContentReference[] | Equipment/magic salvage targets |
| `createdAt` | string (ISO-8601) \| null | Creation timestamp |
| `provenance` | ProvenanceDto \| null | Source provenance |

### HazardDto

| Field | Type | Description |
|-------|------|-------------|
| `key` / `sourceKey` / `name` / `description` / `severity` | | Same identity pattern as traps |
| `exposureMode` | string | `ON_ENTER`, `START_OF_TURN`, `PER_ROUND`, or `CONTINUOUS` |
| `exposureText` / `areaHint` | string \| null | Escaped plain text |
| `check` | ThreatCheckDto \| null | Save or check on exposure |
| `damage` | ThreatDamageDto \| null | Expression + types |
| `escalationText` / `endingConditions` | string \| null | Escaped plain text |
| `conditionRefs` / `salvageItemRefs` | ContentReference[] | Library targets |
| `createdAt` / `provenance` | | Same as traps |

### Scene, Combatant, and Map Integration

```json
{
  "kind": "TRAP",
  "label": "Spike Pit",
  "sortOrder": 0,
  "threatRef": {"scope": "PACKAGE", "type": "TRAP", "key": "trap-spike-pit"}
}
```

```json
{
  "key": "combatant-spike-pit",
  "name": "Spike Pit",
  "kind": "TRAP",
  "maxHp": 0,
  "currentHp": 0,
  "threatRef": {"scope": "PACKAGE", "type": "TRAP", "key": "trap-spike-pit"}
}
```

```json
{
  "key": "pin-spike-pit",
  "x": 240,
  "y": 192,
  "label": "Spike pit",
  "threatRef": {"scope": "PACKAGE", "type": "TRAP", "key": "trap-spike-pit"},
  "sortOrder": 0
}
```

Map `threatPins` use pixel coordinates bounded by grid width/height × cell size. Pins are DM-only
and are not part of the player map projection.

### Example Trap

```json
{
  "key": "trap-spike-pit",
  "sourceKey": "custom_spike-pit",
  "name": "Spike Pit",
  "description": "A covered pit lined with iron spikes.",
  "severity": "DANGEROUS",
  "detectionCheck": {"mode": "CHECK", "ability": "WIS", "skill": "Perception", "dc": 15},
  "disarmMethods": [
    {"key": "jam-cover", "label": "Jam the cover shut", "ability": "DEX", "dc": 14, "sortOrder": 0}
  ],
  "attackBonus": 7,
  "damage": {"expression": "2d10", "types": ["PIERCING"]},
  "resetMode": "MANUAL",
  "conditionRefs": [
    {"scope": "CATALOG", "type": "CONDITION", "ruleset": "SRD_5_2", "sourceKey": "poisoned"}
  ]
}
```

### Export Closure

`ThreatSectionAdapter` (order 250) exports campaign-owned traps/hazards plus threats referenced by
campaign scenes, combatants, and map pins. User-global custom library dependencies required by those
threats are seeded into library export (order 200). Catalog SRD refs remain catalog refs. Transient
editor/roller open state is not exported.

### V1 Compatibility

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

## Audio Cues

The manifest carries an optional top-level `audioCues` array. Each entry defines a reusable audio
cue — a reference to an external track or playlist that can be played during a session. Cues are
assigned to campaigns, scenes, encounters, and world locations via typed content references.

### AudioCueDto

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Package key |
| `name` | string | Display name |
| `referenceKind` | string | `VIDEO` or `PLAYLIST` |
| `providerReference` | string | Provider-specific track/playlist identifier |
| `category` | string | `AMBIENT`, `EXPLORATION`, `TENSION`, `COMBAT`, `TRIUMPH`, `SORROW`, or `CUSTOM` |
| `transitionPreference` | string | `CROSSFADE` or `CUT` |
| `providerId` | string \| null | Provider identifier (e.g. `"FAKE"`, `"SPOTIFY"`, `"YOUTUBE"`); unknown providers import with a WARNING and the cue shows as unavailable |
| `cachedTitle` | string \| null | Cached display title from the provider |
| `artistOrOwner` | string \| null | Cached artist or owner display name |
| `artworkUrl` | string \| null | Cached artwork URL |
| `durationSeconds` | int \| null | Approximate duration in seconds |
| `volumeHint` | int \| null | Suggested volume 0–100 |
| `notes` | string \| null | DM-only notes |
| `createdAt` | string (ISO-8601) \| null | Creation timestamp; server-assigned when absent, then preserved across round-trip |

### Cue-Reference Assignment

Cue references are typed `ContentReference` objects with `"scope": "PACKAGE"`,
`"type": "AUDIO_CUE"`, and a `"key"` matching an entry in the `audioCues` array. They are
carried on four entity types:

| Entity | Field | Description |
|--------|-------|-------------|
| `campaign` | `defaultCueRef` | Default ambient cue for the campaign (played when no scene/encounter cue overrides) |
| `scene` | `sceneCueRef` | Ambient cue for a scene |
| `encounter` | `combatCueRef` | Combat music cue |
| `encounter` | `victoryCueRef` | Victory sting or post-combat music |
| `encounter` | `victoryCueDurationSeconds` | How long the victory cue plays before returning to the ambient cue |
| `worldLocation` | `locationCueRef` | Ambient cue for a world location |

### Export and Round-Trip Rules

Cues export as keys + provider references + cached display metadata only. Audio content
and provider credentials are never included. Playback state (current position, volume,
playing/paused) is intentionally transient and not exported.

### Semantic Validation

- Cue references must resolve to an entry in `audioCues`; unresolved references produce
  `UNRESOLVED_REFERENCE` validation errors.
- `category` and `transitionPreference` must be valid enum values.
- `volumeHint` must be 0–100 when present.
- An unknown `providerId` imports with a WARNING; the cue is preserved in the manifest
  but rendered as unavailable in the UI.

### Example

```json
{
  "key": "cue-hall-ambience",
  "name": "Great Hall Ambience",
  "providerId": "FAKE",
  "referenceKind": "PLAYLIST",
  "providerReference": "fake-playlist-ambient-hall",
  "cachedTitle": "Great Hall Ambience",
  "artistOrOwner": "DMHelper Fixture Library",
  "artworkUrl": "https://example.invalid/art/hall.png",
  "durationSeconds": 3600,
  "category": "AMBIENT",
  "volumeHint": 45,
  "transitionPreference": "CROSSFADE",
  "notes": "Default campaign ambience. Synthetic fixture reference — not a real track."
}
```

```json
{
  "defaultCueRef": null
}
```

The `defaultCueRef` is `null` when no campaign-wide default is set. Encounter cue references
use the same `ContentReference` pattern:

```json
{
  "combatCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "cue-crypt-combat" },
  "victoryCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "cue-crypt-victory" },
  "victoryCueDurationSeconds": 15
}
```

### Module Adapter

Audio cues are managed by their own adapter at a position before scenes and encounters:

```text
AudioCueSectionAdapter      (140) — audio cues
RollableTableSectionAdapter (150) — rollable tables, entries, references
```

This ensures cues are registered in the key registry before scenes and encounters that
reference them are exported.

## mapRegionKey Note

`mapRegionKey` is stored as **unvalidated free-text**. It is not resolved against any map region registry during import or export. A future delivery item (item 9) may introduce a proper typed reference. Until then, importing a package with `mapRegionKey` set simply preserves the string value; no referential integrity check is performed.

## Map Document Semantics

- Token coordinates: **pixels** from the top-left origin of the map canvas.
- Token sizes: **cell counts**.
- Region/primitive grid fields: **column/row** indices when the schema says so (see `map-document-v2.schema.json`).
- IMAGE layers may include `calibration` (two-point grid calibration), `rotationDeg`, `locked`, and `playerVisible`.
- REGION primitives require stable `key` + `label` for scene/encounter placement references.
- Layers/primitives with `playerVisible: false` are DM-only; player projection strips them. Tokens are not duplicated per presentation layer.

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
  WorldSectionAdapter          (850) — world NPCs, locations, factions, relationships, faction clocks
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

Five flagship fixtures verify the round-trip contract:

| Fixture | Path | Purpose |
|---------|------|---------|
| Minimal v2 | `src/test/resources/campaigns/v2/minimal.dmcampaign.json` | Asset-free, single entity — validates structural schema |
| Feature-complete v2 | `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` | Exercises every current section, relationship, history, and asset type |
| Published-adventure-shaped v2 | `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` | Exercises larger ordered adventure content and repeated references |
| Structured-adventure-quest v2 | `src/test/resources/campaigns/v2/structured-adventure-quest.dmcampaign/manifest.json` | Exercises structured scenes (sections, checks, participants, transitions, links), quests with objectives and dependencies, source annotations, and session objective changes |
| World-graph v2 | `src/test/resources/campaigns/v2/world-graph.dmcampaign/manifest.json` | Exercises world NPCs, locations, factions, relationships, and faction clocks |

The feature-complete fixture covers one table in every category (ENCOUNTER, TREASURE, WEATHER,
RUMOR, EVENT, GENERIC) with nested tables, weighted entries, and statblock/item references. The
published-adventure fixture adds a d100 random-encounter table linked to a scene and world location
with a nested sub-table and `2d4` quantity expressions.

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

## Validation Error Catalog

Machine-readable: `GET /api/v1/validation-errors` and
`src/main/resources/agent/validation-error-catalog.json`.

Human index: [validation-errors.md](authoring/validation-errors.md).

## Documentation Examples

| Example | Path | Expected |
|---------|------|----------|
| Minimal valid | `src/test/resources/docs-examples/minimal-valid.dmcampaign.json` | dry-run OK |
| Schema error | `src/test/resources/docs-examples/schema-error.dmcampaign.json` | SCHEMA_VIOLATION |
| Semantic error | `src/test/resources/docs-examples/semantic-error.dmcampaign.json` | UNRESOLVED_* |
