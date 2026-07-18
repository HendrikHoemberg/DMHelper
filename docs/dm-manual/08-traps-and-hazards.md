# Traps and Hazards

Traps and hazards are reusable, provenance-aware DM compendium threats. They are **DM-only** —
players never see definitions, mechanics, provenance, scene story cards, tracker cards, or map
pins.

## Scope: User-global vs Campaign

Threats can be created at two levels:

- **Campaign-scoped** — visible only within one campaign and included in v2 export/import.
- **User-global custom library** — visible across campaigns. A global threat is included when a
  campaign scene, encounter combatant, or map pin references it; unrelated global threats stay out
  of the package.

Campaign-scoped custom content is the primary authoring target. SRD catalog content may be
referenced (conditions, equipment, magic items, statblocks) but traps/hazards themselves are always
custom (there is no SRD trap/hazard seed).

## Authoring Routes

| Action | Route |
|--------|-------|
| List traps | `/library/traps?campaignId={id}` |
| List hazards | `/library/hazards?campaignId={id}` |
| Create trap form | `/library/traps/new?campaignId={id}` |
| Create hazard form | `/library/hazards/new?campaignId={id}` |
| Detail | `/library/traps/{id}` or `/library/hazards/{id}` |
| Edit custom | `/library/traps/{id}/edit` or `/library/hazards/{id}/edit` |
| Create API | `POST /api/v1/traps?campaignId=` / `POST /api/v1/hazards?campaignId=` |
| Update API | `PUT /api/v1/traps/{id}` / `PUT /api/v1/hazards/{id}` |
| Clone / promote / delete | `POST …/clone`, `POST …/promote`, `DELETE …?confirmed=` |
| Search (encounter / pin pickers) | `GET /api/v1/traps/search` / `GET /api/v1/hazards/search` |

The shared editor supports severity, level band, typed detection/check/save/damage, disarm methods
(traps), exposure mode (hazards), condition and salvage-item references, and provenance display.
Descriptions are sanitized Markdown; mechanic labels, triggers, failures, and effects are escaped
typed text.

## Scene References

Attach a trap or hazard to a scene as a structured section with kind `TRAP` or `HAZARD` and a
`threatId`:

```text
POST /campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/sections
  kind=TRAP&label=Spike Pit&threatId={trapId}&sortOrder=0
```

One trap definition may be attached to many scenes without duplication — each section stores only a
reference. Prose-only `TRAP`/`HAZARD` sections (body without `threatId`) remain valid for legacy
notes.

In the session cockpit story rail, sections with a resolved threat render a mechanics card.

## Encounter Tracker

Add a non-creature combatant from the encounter rail:

```text
POST /api/v1/encounters/{id}/combatants/from-threat
  { "threatKind": "TRAP"|"HAZARD", "threatId": "…", "name": optional, "initiative": optional }
```

Threat combatants use kind `TRAP`/`HAZARD`, have zero HP, and carry a `threatCard` for the active
turn. **Resolution is advisory:** the card can prefill the shared dice roller; it does not roll and
does not apply damage, conditions, treasury, or story mutations. Use the existing tracker damage
and condition controls on creatures when you resolve effects manually.

## Dice Prefill

Mechanics cards dispatch `dice-roller-prefill` with an expression and label. The shared roller
opens with the expression filled, advantage/disadvantage cleared, and no roll submitted. Click
**Roll** yourself when ready.

## DM-Only Map Pins

Place threat markers on the workspace map:

```text
GET/POST /api/v1/maps/{mapId}/pins
PUT/DELETE /api/v1/maps/{mapId}/pins/{pinId}
```

Pins store pixel coordinates bounded by grid width/height × cell size. They appear only on the DM
map surface. Player table state, map projection, and `/player` never include pin identity, labels,
or threat content.

## Ownership, Provenance, and Management

- **CUSTOM campaign** — editable, cloneable, promotable to global, deletable with dependency impact.
- **CUSTOM global** — editable; campaign content may reference it.
- **Provenance** — source title, locator, license, converter, and extraction confidence display on
  custom detail pages.
- **Deletion** — shows dependent scene sections, combatants, and map pins; confirmed delete clears
  those references.

## Import / Export

Package v2 carries optional top-level `traps` and `hazards` arrays plus:

- `scene.sections[].threatRef` for typed scene links
- `combatant.threatRef` with kind `TRAP`/`HAZARD`
- `map.threatPins[]` with `threatRef`

Export includes campaign-owned threats and the transitive library closure needed by scene,
combatant, and pin references. Unrelated user-global threats are not exported. Condition,
equipment, magic-item, and statblock references resolve to package or catalog refs.

## Player Safety Non-Goals

Threats do **not**:

- appear on `/player` or table WebSocket state
- auto-roll dice or auto-apply HP/conditions
- invent missing DCs, attack bonuses, or saves on import
- use autonomous resolution engines

Unresolved source values stay absent and may be recorded as `SOURCE_ANNOTATION` entries.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| Story card missing | Section has no `threatId`, or threat is not visible to the campaign |
| Tracker card empty | Combatant is not kind TRAP/HAZARD, or threat was deleted |
| Prefill does nothing | Dice roller script not loaded; open the Dice panel once |
| Pin not on player map | Expected — pins are DM-only |
| Import `TRAP_EFFECT_MODE_CONFLICT` | Trap declares both `attackBonus` and `save` |
| Import `TRAP_RESET_TIMING_REQUIRED` | `resetMode` is `AUTOMATIC` without `resetTiming` |
| Import `THREAT_PIN_OUT_OF_BOUNDS` | Pin x/y exceeds map pixel bounds |
