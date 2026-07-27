# Correction B Release Rehearsal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the branched map-free wave encounter in the fixture and drive both wave spawning and session-plan promotion through visible cockpit controls.

**Architecture:** Keep fixture persistence behind `ReleaseRehearsalFixture` and `EncounterService`, exposing only stable seeded IDs needed by focused assertions. Add a cockpit-specific quick-note action in the existing Alpine module that uses the existing typed promotion endpoint and module refresh events; leave the generic quick-note strip action untouched. Update the Playwright rehearsal to branch on fixture-prepared state rather than creating runtime data.

**Tech Stack:** Spring Boot, JPA/H2 test fixtures, Thymeleaf, Alpine.js, Playwright, JUnit 5, AssertJ, MockMvc.

## Global Constraints

- `BRANCHED_TWO_MAPS` receives the intentional prepared encounter; `LINEAR_ONE_MAP` remains unchanged.
- The branched encounter is map-free, linked to `Lantern Vault Ambush`, and has an active main wave plus pending manual reserve wave and prepared reserve combatant.
- Branched rehearsal wave and session-plan mutations use visible cockpit controls; no raw-fetch creation of waves or combatants is allowed.
- The quick-note action uses title `Release rehearsal plan` and type `SESSION_PLAN`; generic promotion remains available elsewhere.
- Readiness, synthetic provenance, and campaign isolation remain proven.
- Run focused tests only; do not run broad verify.

---

### Task 1: Extend the fixture contract and write branched topology tests

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`

**Interfaces:**
- Produces `Seeded.branchedEncounterId()`, `Seeded.branchedMainWaveId()`, `Seeded.branchedReserveWaveId()`, and `Seeded.branchedReserveCombatantIds()` for later rehearsal/assertion code.
- Uses `EncounterService.create`, `createWave`, and `addCombatant` for fixture setup.

- [ ] **Step 1: Add the failing branched topology assertions.**

  In `ReleaseRehearsalFixtureTest`, add a branched-only test that finds `Lantern Vault Ambush` by `seeded.branchedSceneId()`/the returned scene graph and asserts the scene encounter is linked, the encounter is named `Encounter: Lantern Vault Ambush`, has no map, and has `PLANNED`/`SETUP` state. Load waves and assert `main` is `ACTIVE`, reserve key `vault-reinforcements` is `PENDING` and `MANUAL`, and the reserve wave has one combatant named `Vault reinforcements`. Assert the IDs exposed by `Seeded` match the persisted objects.

  Add branched assertions to readiness and provenance/isolated seeding coverage: the branched campaign remains ready; textual content includes the ambush encounter, reserve wave, and reserve combatant; two branched seeds have different encounter keys/notes and each campaign owns only its own topology. Keep existing linear tests unchanged.

- [ ] **Step 2: Run the focused fixture test to verify RED.**

  Run:

  ```bash
  ./mvnw -q -Dtest=ReleaseRehearsalFixtureTest test
  ```

  Expected: compilation/test failure because the new seeded IDs/topology do not yet exist and the ambush scene remains unlinked.

- [ ] **Step 3: Implement the minimal fixture topology.**

  Extend the `Seeded` record with branched encounter/wave/combatant IDs and expose the ambush scene ID as a stable field if the test needs it. Create the branched encounter with `new EncounterService.CreateRequest("Encounter: Lantern Vault Ambush", null)`, assign a suffix-based encounter key, persist it, and call `adventures.linkEncounter(ambush.getId(), encounter.id())`.

  Create the main combatant through `encounters.addCombatant` so `ensureMainWave` creates the active main wave, then create the pending reserve wave with `new EncounterService.CreateWaveRequest("vault-reinforcements", "Vault reinforcements", WaveTriggerKind.MANUAL, null, "Synthetic reserve wave.")`. Add a library combatant with the first fixture stat block, quantity one, group name `Vault reinforcements`, and the reserve wave ID using the existing `AddLibraryCombatantsRequest` signature. Capture IDs from the returned DTOs. Do this only inside the branched shape branch; keep the linear encounter and its four combatants as-is.

  Include encounter waves and each combatant's wave key/status in `textualContentOf` so provenance tests observe the complete topology.

- [ ] **Step 4: Run focused fixture tests to verify GREEN.**

  Run:

  ```bash
  ./mvnw -q -Dtest=ReleaseRehearsalFixtureTest test
  ```

  Expected: all tests in the fixture class pass.

- [ ] **Step 5: Commit the fixture slice.**

  ```bash
  git add src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java
  git commit -m "test: prepare branched rehearsal encounter fixture"
  ```

### Task 2: Add typed session-plan quick-note cockpit contract

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/main/resources/templates/session/modules/_quick-notes.html`
- Modify: `src/main/resources/static/js/quicknotes.js`

**Interfaces:**
- Produces `data-promote-session-plan` and accessible label `Promote to session plan` in the cockpit module.
- Calls `POST /api/v1/campaigns/{campaignId}/quicknotes/{id}/promote?title=Release%20rehearsal%20plan&type=SESSION_PLAN` and then invalidates/refreshes `session-plan` plus `quick-notes`.

- [ ] **Step 1: Add the failing endpoint contract test.**

  In `QuickNoteApiControllerTest`, stub `quickNoteService.promoteToNote(campaignId, noteId, "Release rehearsal plan", NoteType.SESSION_PLAN)` and post to the promote endpoint with `title` and `type` query parameters. Assert HTTP 200 and verify the typed overload with exactly those values.

- [ ] **Step 2: Add the failing cockpit template/JavaScript contract assertions.**

  In `CockpitRuntimeModuleContractTest`, require the cockpit module source to contain the `data-promote-session-plan` hook, the accessible label, and a call to a distinct `promoteSessionPlan` method. Require `quicknotes.js` to contain `type=SESSION_PLAN` and the deterministic title. Also assert the generic `@click="promote(n)"` action remains.

- [ ] **Step 3: Run focused contract tests to verify RED.**

  Run:

  ```bash
  ./mvnw -q -Dtest=QuickNoteApiControllerTest,CockpitRuntimeModuleContractTest test
  ```

  Expected: endpoint test fails because typed parameters are not asserted/implemented in the existing contract, and template assertions fail because the cockpit action is absent.

- [ ] **Step 4: Implement the minimal cockpit action.**

  Add a second button beside the existing generic Promote button in `session/modules/_quick-notes.html` with `data-promote-session-plan`, `@click="promoteSessionPlan(n)"`, and `aria-label="Promote to session plan"`.

  Add `promoteSessionPlan(quicknote)` to `quicknotes.js`. Build `URLSearchParams({title: 'Release rehearsal plan', type: 'SESSION_PLAN'})`, call the existing promote URL with those parameters and `POST`, remove the item, notify quick-notes mutation, and dispatch a `cockpit:module-invalidate` event for `session-plan` so the normal cockpit runtime refreshes it. Reuse the existing failure reporter and retry the same method on failure. Leave `promote(quicknote)` unchanged.

- [ ] **Step 5: Run focused contract tests to verify GREEN.**

  Run the same focused command from Step 3. Expected: all selected endpoint/template tests pass.

- [ ] **Step 6: Commit the quick-note slice.**

  ```bash
  git add src/test/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiControllerTest.java src/test/java/dev/hendrikhoemberg/dmhelper/session src/main/resources/templates/session/modules/_quick-notes.html src/main/resources/static/js/quicknotes.js
  git commit -m "feat(cockpit): promote quick notes to session plans"
  ```

### Task 3: Update the rehearsal to use prepared and visible workflows

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java` only if a focused rehearsal assertion exposes a fixture gap

**Interfaces:**
- Consumes the branched IDs from `ReleaseRehearsalFixture.Seeded`.
- Produces an honest at-most-two-actions assertion and visible-control coverage for reserve spawning and session-plan promotion.

- [ ] **Step 1: Write the failing rehearsal assertions/change.**

  Remove the branched `page.evaluate` block in Step 4 that lists encounters and posts to `/waves` and `/combatants/from-library`. Branch Step 4 so a prepared branched encounter is accepted without a scene seed click, while linear retains its existing scene-to-encounter click. Assert the branched encounter name and `actions <= 2`.

  In Step 5 use the fixture reserve wave ID to locate the visible pending-wave Spawn button, click it, assert the POST response, and verify the UI/API state reports the reserve wave active with `Vault reinforcements` present. Do not create or look up the reserve wave by name through a creation fetch.

  In Step 7 replace the `page.evaluate` promotion fetch with a click on `[data-promote-session-plan]`, wait for the quick-note mutation/normal module refresh, and assert the session-plan module heading equals `Release rehearsal plan`.

- [ ] **Step 2: Run the focused rehearsal test to verify RED.**

  Run:

  ```bash
  ./mvnw -q -Dtest=ReleaseRehearsalTest test
  ```

  Expected: the test fails until the fixture-prepared IDs and visible promotion action are wired into the rehearsal.

- [ ] **Step 3: Implement the minimal rehearsal changes.**

  Use `seeded.branchedEncounterId()`/`seeded.branchedReserveWaveId()` directly in selectors and assertions. Preserve the linear seed-button path and the existing step ordering/state dependencies. Wait for the cockpit runtime’s normal refresh after the promotion click rather than navigating directly to the note endpoint.

- [ ] **Step 4: Run focused GREEN rehearsal coverage.**

  Run:

  ```bash
  ./mvnw -q -Dtest=ReleaseRehearsalTest test
  ```

  Expected: both nested linear and branched rehearsal shapes pass without console/network failures.

- [ ] **Step 5: Commit the rehearsal slice.**

  ```bash
  git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java
  git commit -m "test: drive correction B through cockpit controls"
  ```

### Task 4: Final focused verification and handoff

**Files:**
- No new production files; inspect all scoped commits and the working tree.

- [ ] **Step 1: Run the complete focused test set only.**

  ```bash
  ./mvnw -q -Dtest=ReleaseRehearsalFixtureTest,QuickNoteApiControllerTest,CockpitRuntimeModuleContractTest,ReleaseRehearsalTest test
  ```

- [ ] **Step 2: Inspect the final diff and status.**

  ```bash
  git diff --check HEAD~3..HEAD
  git status --short
  git log -4 --oneline
  ```

  Confirm no broad verify command was run, no unrelated files changed, generic quick-note promotion remains present, and the final commit SHA is recorded.

- [ ] **Step 3: Report files, focused tests, and commit SHA.**

  Report the exact changed files, each focused command and result, and the final implementation commit SHA (plus the design/plan commits if useful).
