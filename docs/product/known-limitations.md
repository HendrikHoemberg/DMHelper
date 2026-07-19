# Known Limitations

Based on the [capability matrix](../campaign-capabilities.md):

## PARTIAL

- **Full automated ASI/subclass feature schedule from all class JSON** — ASI/subclass prompts use common schedules and class data when present; incomplete class feature tables remain manual.
- **Authoritative 2024 encounter difficulty** — Still labeled as an estimate.
- **Agent SDK** — Capability manifest API and validation error catalog are implemented; remaining agent endpoints are deferred.

## UNSUPPORTED

- **World graph extensions** — NPCs, locations, factions, relationships, faction clocks, and simple location adjacency are SUPPORTED; structured travel and player-facing world views are not.
- **Structured travel/weather automation** — Optional future capability outside the readiness release. Use locations, adjacency, notes, calendars, and rollable tables for manual travel workflows.
- **Player interaction** — P3, deferred (players cannot move tokens or edit sheets directly).
- **Full fog of war gameplay** — Optional future capability outside the readiness release. Existing server-filtered map presentation remains supported.
- **Atmosphere/music** — Required readiness work; provider feasibility is complete, but the production cue and playback subsystem is not implemented yet.
