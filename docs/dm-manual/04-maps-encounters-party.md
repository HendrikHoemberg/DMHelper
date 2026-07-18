# Maps, Encounters & Party

## Published Map Calibrate

In the map editor, calibrate the grid by drawing a reference line over a known distance on the image and entering the real-world length. The cell size is persisted with the map. Use the toolbar to crop, rotate (90° increments), and lock the map against edits.

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
