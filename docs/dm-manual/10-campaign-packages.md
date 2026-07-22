# Campaign Packages

Campaign packages (`.dmcampaign` files) are the portable format for moving campaigns between DMHelper instances. They bundle campaign data, handouts, maps, and other assets into a single file.

## Export

Export from the campaign detail page or via the API:

```
GET /campaigns/{campaignId}/package?includeCombatLog=true&includeDiceHistory=true
```

The export includes:
- Campaign metadata
- Adventures, chapters, scenes
- Maps with tokens, pins, and calibration
- Encounters with combatants
- Handouts with safety classification and derivative provenance
- Party members and sheets
- Notes, quick notes, ledger, timeline
- Rollable tables, traps, hazards
- Audio cues and assignments
- Session state (if running)

## Import

Import via the preview-confirm flow:

1. **Preview**: `POST /campaigns/package-imports/previews` — validates structure, reports warnings/blockers.
2. **Confirm**: `POST /campaigns/package-imports/{previewId}/confirm?acceptWarnings=true` — applies the import.
3. **Redirect**: The `Location` header contains the restored campaign URL.

### Safety Classification on Import

When importing a campaign from an **old package** (one that does not include the `safetyClassification` field per entry), the adapter applies a conservative default:

- If `dmOnly` is `true` → `DM_SOURCE`
- If `dmOnly` is `false` → `UNREVIEWED`

This means old packages that set `dmOnly=false` (intending to share a handout with players) are **not** blindly trusted. The DM must review each handout's classification before it can be presented. This prevents accidentally exposing content that was never classified.

New packages that include the `safetyClassification` field use it directly.

### Derivative Round-Trip

Derivatives export with:
- `safetyClassification: PLAYER_DERIVATIVE`
- `sourceRef` pointing to the source handout's package key
- `derivativeRecipe` preserving the crop/redaction recipe

On import, the adapter:
1. Creates all handouts first.
2. Resolves `sourceRef` references to wire derivative → source links.
3. Restores `derivativeRecipe` on each derivative.

This ensures the provenance chain survives export and re-import.

## Additive Import

Importing into an existing campaign is additive: new content is merged, existing content is never overwritten. Party members, encounters, and notes from the imported package are appended.
