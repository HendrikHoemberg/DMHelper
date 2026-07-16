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

## Open Session State

The manifest carries the current open session when one is active (nullable — `null` means IDLE):

| Field | Type | Description |
|-------|------|-------------|
| `session` | object \| null | `null` when no session is open; otherwise contains the fields below |
| `session.status` | `"RUNNING"` \| `"PAUSED"` \| `"REVIEW"` | Session lifecycle status |
| `session.presentationMode` | `"CURTAIN"` \| `"MAP"` \| `"HANDOUT"` | What is shown on the player view |
| `session.startDate` | string (ISO-8601) | Real-world timestamp when the session started |
| `session.planNoteRef` | ContentReference \| null | Typed ref to a session-plan note |
| `session.workspaceMapRef` | ContentReference \| null | Typed ref to the current workspace map |
| `session.attendeeRefs` | ContentReference[] | Typed refs to attending party members |
| `session.presentedRef` | ContentReference \| null | Typed ref to the presented map or handout |
| `session.sceneVisits` | object[] | Ordered visit records: `{sceneRef, visitedAt}` timestamps |
| `session.draftBody` | string \| null | Session-log draft body; only present when `status` is `REVIEW` |

### Invariant checks

- `presentationMode` must be consistent with `presentedRef`: `CURTAIN` → `null`, `MAP` → map ref, `HANDOUT` → handout ref.
- `draftBody` must be `null` unless `status` is `REVIEW`.
- `attendeeRefs` must reference active party members in the campaign.
- `sceneVisits` timestamps must be monotonic within a session.

### Deterministic recovery on import

When a session is open at export time, the import recreates it in the `IDLE` state. The session-plan note is imported normally (with its full body preserved). Runtime state (presentation mode, PIN, workspace map, drafts, visit timestamps) is discarded on import — it is intentionally transient state that must be re-established by the DM.

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
  NotesSectionAdapter         (1000) — notes, links, quick notes
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

Three flagship fixtures verify the round-trip contract:

| Fixture | Path | Purpose |
|---------|------|---------|
| Minimal v2 | `src/test/resources/campaigns/v2/minimal.dmcampaign.json` | Asset-free, single entity — validates structural schema |
| Feature-complete v2 | `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` | Exercises every current section, relationship, history, and asset type |
| Published-adventure-shaped v2 | `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` | Exercises larger ordered adventure content and repeated references |

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
