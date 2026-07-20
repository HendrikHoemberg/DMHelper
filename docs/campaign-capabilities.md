# Campaign Capabilities

| Capability | Status | Notes |
|---|---|---|
| P0 interaction reliability | `SUPPORTED` | Quick notes, destinations, browser guards, secure package assets, visible/retryable session mutations, correlated failures, and honest difficulty estimates are covered. |
| Campaign contract v1 | `SUPPORTED` | Schema, DTO, dry-run, import, references, and checked fixtures share one contract. |
| Campaign package v2 foundation | `SUPPORTED` | ZIP/JSON containers, stable keys, typed catalog, preview, migrations, staged assets, and atomic import are implemented. |
| Current persisted campaign state recovery | `SUPPORTED` | Default export/import preserves all currently persisted campaign meaning. |
| Combat log/dice recovery | `SUPPORTED` | Included by default and explicitly excludable. |
| Session cockpit | `SUPPORTED` | Durable start/resume/pause/end coordination across existing modules; deterministic reviewed session logs and v2 recovery. |
| Structured scene transitions | `SUPPORTED` | Delivery item 6; CHOICE/ENTRANCE/EXIT transitions with typed scene references, mutual exclusion rules, and deferred resolution on import. |
| Structured quests/objectives | `SUPPORTED` | Delivery item 6; quests with status lifecycle, objectives with ALL/ANY completion mode, prerequisite dependency DAG, and session objective-change history. |
| Campaign-scoped non-statblock custom content | `SUPPORTED` | Delivery item 7; package PACKAGE refs for sheets/treasury, provenance, and create/clone/promote across library types. |
| Character creation choices (class/subclass/skills) | `SUPPORTED` | Create/level-up capture class, optional subclass, skill/tool/language choices, and hit-die roll or average. |
| At-table attacks/features | `SUPPORTED` | Sheet attacks and feature actions with roll expressions; package-exported. |
| Live HP/temp/death saves/conditions on party | `SUPPORTED` | PartyMember live combat state is editable, package-round-tripped, and synced with linked combatants. |
| Sheet inventory states | `SUPPORTED` | EQUIPPED/CARRIED/STASHED/CONSUMED/LOST with attunement constrained to EQUIPPED/CARRIED. |
| Rest preview and apply | `SUPPORTED` | Preview is pure; apply heals HP from hit dice (short), restores full HP, clears temp HP/death saves, and reduces exhaustion (long). |
| Multi-member rest/XP/condition/loot ops | `SUPPORTED` | Batch bar on party and sheets overview with rest preview confirmation. |
| Full automated ASI/subclass feature schedule from all class JSON | `PARTIAL` | ASI/subclass prompts use common schedules and class data when present; incomplete class feature tables remain manual. |
| Character sheet package round-trip | `SUPPORTED` | Ability scores, class levels (hit die rolls + subclassRef), proficiencies, overrides `_meta`, resources, spells/slots, attacks, features, and party live state. |
| Encounter library multi-add / groups | `SUPPORTED` | Quantity + group leader |
| Encounter waves / reserves / spawn | `SUPPORTED` | Manual + round prompts |
| Encounter prep notes + structured rewards | `SUPPORTED` | DM-confirmed apply |
| Encounter completion summary | `SUPPORTED` | Deterministic from log |
| Undo lifecycle boundaries | `SUPPORTED` | Activate/end/wave/reward |
| Published map calibrate/crop/rotate/lock | `SUPPORTED` | Image-first workflow |
| Named map regions with keys | `SUPPORTED` | Scene/encounter placement |
| DM/player map layer split (shared tokens) | `SUPPORTED` | playerVisible flags |
| Authoritative 2024 encounter difficulty | `PARTIAL` | Still labeled estimate |
| Agent SDK | `SUPPORTED` | PIN-free capability manifest, validation error catalog, typed catalog snapshot, schemas, conversion playbook, and executable documentation examples |
| World graph | `SUPPORTED` | NPCs, locations, factions, relationships, faction clocks, and simple location adjacency; music remains the active readiness slice. |
| Rollable tables | `SUPPORTED` | DM-only ranged/weighted authoring, typed references, nested/grouped rolls, scene/location cockpit access, explicit encounter/reward draft transitions, dependency-aware deletion with preserved history, and package-v2 dependency-closure round-trip |
| Traps and hazards | `SUPPORTED` | DM-only trap/hazard definitions with provenance, scene sections, encounter tracker cards, DM-only map pins, dice prefill (no auto-resolution), and package-v2 dependency-closure round-trip |
| Atmosphere & music | `SUPPORTED` | DM-side streaming music behind a provider SPI; scene/encounter/location cue switching, manual override, per-campaign confirm and per-session mute. Requires internet and possibly a provider account — the only approved runtime network dependency; provider failure never blocks a session. DM-only, absent from player payloads. |
| Structured travel/weather automation | `UNSUPPORTED` | Optional future capability; outside the readiness release. Manual workflows use locations, adjacency, notes, calendars, and rollable tables. |
| Player interaction | `UNSUPPORTED` | P3 - Deferred |
| Full fog of war gameplay | `UNSUPPORTED` | Optional future capability; outside the readiness release. Existing server-filtered map presentation remains supported. |
