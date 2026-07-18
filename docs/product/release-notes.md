# Release Notes — Readiness Program

Numbering matches master design §22
(`docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`).

## Delivery Items 1–10

- **Item 1 (P0 runtime reliability):** Quick notes, destinations, browser guards, secure package assets, visible/retryable session mutations, correlated failures, and honest difficulty estimates are implemented.
- **Item 2 (Campaign contract v1 repair):** Schema, DTO, dry-run, import, references, and checked fixtures share one contract.
- **Item 3 (Package v2 foundation):** ZIP/JSON containers, stable keys, typed catalog, preview, migrations, staged assets, and atomic import are implemented.
- **Item 4 (Complete round-trip):** Default export/import preserves all currently persisted campaign meaning. Combat log and dice history are included by default and explicitly excludable.
- **Item 5 (Session cockpit):** Real `/campaigns/{id}/session` workspace with story/table/encounter/plan rails, lifecycle start/resume/pause/end, and deterministic session-log drafts.
- **Item 6 (Structured adventure/quest model):** Scene sections, checks, participants, CHOICE/ENTRANCE/EXIT transitions with typed references, quests with objectives (ALL/ANY), dependency DAG, source annotations, and session objective-change history.
- **Item 7 (Custom compendium expansion):** Campaign-scoped custom content beyond statblocks, provenance, package PACKAGE refs for sheets/treasury, and create/clone/promote across library types.
- **Item 8 (Character-sheet completion):** Character creation/level-up choices, at-table attacks/features, live HP/temp/death saves/conditions, inventory states, rest preview/apply, multi-member batch ops, and package sheet fidelity.
- **Item 9 (Encounter and map depth):** Library multi-add/groups, waves/reserves/spawn, prep notes and structured rewards, completion summaries, undo lifecycle boundaries, published map calibrate/crop/rotate/lock, named map regions, and DM/player layer split (shared tokens).
- **Item 10 (Documentation/agent SDK):** Audience-split docs, capability manifest API, validation error catalog, typed catalog snapshot fidelity, executable documentation examples, agent conversion playbook, and contract tests.

## Delivery Item 11

- **Item 11 (Rollable tables):** DM-only ranged/weighted tables with complete editor/reference management, nested grouped rolls (max depth 5), shared detail/cockpit rolling, scene and world-location links, explicit encounter/reward draft confirmation or discard, dependency-aware deletion with preserved roll evidence, and campaign package v2 dependency-closure round-trip. Includes flagship fixtures, capability manifest, DM manual chapter, agent mapping rules, performance and player-safety gates, and browser acceptance tests.
- **Item 11 (Traps and hazards):** DM-only trap/hazard definitions with provenance, scene story cards, encounter tracker cards, DM-only map pins, dice prefill without automatic resolution, player-safe projections, package-v2 dependency-closure round-trip, flagship fixtures, DM manual chapter, agent non-invention rules, hostile-content and PIN gates, and browser acceptance.
- **Item 11 (World graph):** NPCs, locations, factions, relationships, faction clocks — campaign-scoped cross-referenced world-building state.

## Not started

- Travel/weather, fog gameplay, audio, optional player interaction — deferred.
