# Correction B: Prepared branched rehearsal and visible session-plan promotion

## Goal

Make the branched release rehearsal exercise a fixture-prepared, map-free wave encounter entirely through visible cockpit controls, and make Step 7 promote a quick note to a session plan through a visible cockpit action.

## Scope and constraints

- `BRANCHED_TWO_MAPS` gets one intentional additional encounter; `LINEAR_ONE_MAP` keeps its existing seed shape and scene-to-encounter creation flow.
- The branched encounter is named `Encounter: Lantern Vault Ambush`, has no map, is linked to `Lantern Vault Ambush`, and is seeded with an active main wave, a pending manual reserve wave, and reserve combatants.
- Fixture setup uses `EncounterService` APIs for wave and combatant creation rather than duplicating persistence logic.
- The rehearsal may use a pre-linked branched encounter, but must not raw-fetch to create waves or combatants. Its wave mutation must be the visible Spawn control.
- The quick-notes cockpit module adds a user-facing action with a stable accessible label/data hook. It calls the existing quick-note promotion endpoint with `title=Release rehearsal plan` and `type=SESSION_PLAN`, then refreshes the cockpit session-plan module.
- Existing generic quick-note promotion remains unchanged in the generic quick-note strip and other non-cockpit surfaces.
- Synthetic fixture provenance and campaign isolation must include the new branched encounter, waves, and combatants. Readiness must remain true.
- Verification is focused only; no broad release verification is run.

## Design

### Fixture topology

`ReleaseRehearsalFixture.Seeded` exposes the branched encounter ID, main-wave ID, reserve-wave ID, and reserve combatant IDs as nullable stable fixture outputs. The values are populated only for `BRANCHED_TWO_MAPS`; linear seeding returns null/empty values without changing its existing encounter topology.

After the shared stat blocks exist, branched seeding creates the map-free encounter with `EncounterService.CreateRequest("Encounter: Lantern Vault Ambush", null)`, assigns a campaign-unique encounter key, and links it to the ambush scene. The service's main-wave helper is invoked by adding the first main combatant. A pending manual reserve wave is created with `createWave`, and a named reserve combatant is added with the library API request pointing at that wave. The reserve wave remains pending until the rehearsal clicks Spawn.

The fixture read model and textual provenance traversal include waves and wave assignments so the fixture test can prove the complete topology and isolation. Existing linear encounter assertions remain unchanged.

### Cockpit interaction

The branched rehearsal's scene step sees the prepared encounter and does not click a scene seed action. The encounter setup step still records an at-most-two-actions bound: linear uses the scene-to-encounter button; branched uses the prepared encounter as zero setup actions. Step 5 starts combat through the existing visible setup controls, defeats main-wave combatants, then clicks the rendered pending-wave Spawn button. Assertions verify the endpoint succeeds and the prepared reserve wave becomes active with its pre-seeded combatant.

Step 7 creates the quick note through the existing visible form. Each cockpit quick-note row renders both the existing generic Promote action and a new `data-promote-session-plan` action with an accessible label. The latter calls the same `/promote` endpoint with the deterministic title and `SESSION_PLAN` type, removes the quick note, and dispatches module invalidation/refresh events. The test clicks this action, waits for the normal cockpit refresh, and asserts the session-plan module title.

### Error handling

The new JavaScript action follows the existing `request`/`reportFailure` convention, preserving the note body in the existing module state if promotion fails. No new endpoint or persistence path is introduced; the existing controller contract already accepts the optional title and `NoteType` query parameters.

## Tests

- Extend `ReleaseRehearsalFixtureTest` with branched scene linkage, encounter/wave/combatant topology, readiness, provenance, and cross-campaign isolation assertions.
- Add a quick-note API contract test proving `title` and `type=SESSION_PLAN` reach the typed promotion service overload.
- Extend the cockpit template contract test to require the session-plan action's label/data hook and typed endpoint parameters while preserving the generic action.
- Update `ReleaseRehearsalTest` to use only visible branched wave and quick-note controls, with no raw creation fetches; run it as a focused browser gate.

