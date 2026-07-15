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

**`GET /campaigns/{campaignId}/export`** — legacy v1 JSON export.

## V1 Migration

Legacy v1 packages are migrated with:
- Deterministic package key generation
- Reference rewriting (name-based to key-based)
- Embedded image extraction to package assets
- Warning `LEGACY_REFERENCE_MIGRATED` per migrated reference
- Label `MIGRATED_FROM_V1`

## Foundation Exclusions

The following persistent fields are not yet represented in the v2 export:

```
CAMPAIGN_SETTINGS
CURRENT_SCENE
PARTY_CURRENT_HP
HANDOUT_PRESENTATION_STATE
COMBAT_LOG
DICE_HISTORY
CALENDAR_CONFIGURATION
CALENDAR_CURRENT_DATE
CUSTOM_COMPENDIUM_NON_STATBLOCK
STRUCTURED_SCENE_TRANSITIONS
QUESTS_AND_OBJECTIVES
```

These are declared in `metadata.exclusions`. The next **Complete Round-trip** milestone will remove them.

## Atomicity

Import persists nothing until validation passes and the DM confirms. Campaign persistence, key binding,
and asset installation share one transaction. All package keys, including nested sheet/resource, token,
combatant, chapter, and scene keys, bind through persistence receipts in that transaction. On rollback,
no campaign row, key row, or installed asset survives.

## Fixture Locations

- Minimal v2: `src/test/resources/campaigns/v2/minimal.dmcampaign.json`
- Current-surface manifest: `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json`
- Feature-complete v1: `src/test/resources/campaigns/v1/feature-complete.dmcampaign.json`

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
