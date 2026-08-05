# Session Encounter UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore authored-map parity in session mode and make encounter/token workflows accessible and predictable.

**Architecture:** Add one shared Konva runtime renderer consumed by the DM battle map and player projection. Keep cockpit interaction state in the existing Alpine component, add native `<dialog>` surfaces, and enforce party-token idempotency in the service.

**Tech Stack:** Spring Boot 4.1, Java 26, Thymeleaf, Alpine.js, Konva, JUnit 5, MockMvc, Playwright.

## Global Constraints

- Leaving the cockpit must not pause, review, or complete the running session.
- Player rendering must never include layers with `playerVisible == false`.
- Cancelling any dialog must perform no mutation.
- Runtime map rendering must not mutate the persisted map document.

---

### Task 1: Shared runtime document renderer

**Files:**
- Create: `src/main/resources/static/js/map/runtime-renderer.js`
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/static/js/player/player-view.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java`

**Interfaces:**
- Produces: `renderRuntimeDocument({Konva, document, targetLayer, gridWidth, gridHeight, cellSizePx, playerView})`
- Consumes: persisted `MapDocumentDto` JSON.

- [ ] Add a browser regression with an image, custom terrain, shapes, and a DM-only layer; verify DM nodes include the image/shapes and player filtering excludes the private layer.
- [ ] Run the regression and verify it fails because the runtime renderers only draw cells.
- [ ] Implement filtered, clipped image/terrain/shape rendering in `runtime-renderer.js` and call it from both runtime surfaces.
- [ ] Run the focused browser and session map contract tests.

### Task 2: Accessible token and confirmation dialogs

**Files:**
- Modify: `src/main/resources/templates/session/_map-module.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit.css`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`

**Interfaces:**
- Produces: `openTokenDialog()`, `submitTokenDialog()`, `requestTokenDelete(id)`, `confirmTokenDelete()`, and encounter-end confirmation events.

- [ ] Add failing contracts asserting no `prompt()`/`confirm()` remains in session token/annotation workflows and that accessible dialogs exist.
- [ ] Implement the token form with validated enum choices, bounded snapped placement, busy/error feedback, and focus restoration.
- [ ] Implement reusable token-delete and encounter-end confirmation dialogs.
- [ ] Run session cockpit contracts.

### Task 3: Party token idempotency

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenServiceTest.java`

**Interfaces:**
- Produces: `addPartyToMap(UUID)` that creates tokens only for missing active party members.

- [ ] Add a failing service test that calls `addPartyToMap` twice and expects one token per active party member.
- [ ] Filter active members by existing token `partyMemberId` before insertion.
- [ ] Run `TokenServiceTest` and `TokenApiControllerTest`.

### Task 4: Encounter-linked map activation and cockpit exit

**Files:**
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`

**Interfaces:**
- Produces: `activateEncounter(id, mapId)` and a campaign dashboard link.

- [ ] Add failing contracts for linked-map activation and **Leave cockpit** navigation.
- [ ] Pass the planned encounter map ID to `activateEncounter`; after activation, call `switchMap(mapId)` before refreshing modules.
- [ ] Add a normal anchor to `/campaigns/{campaignId}` labelled **Leave cockpit**.
- [ ] Run session cockpit and workspace-selection tests.

### Task 5: Final verification

**Files:**
- Verify all files above.

- [ ] Run focused map, token, encounter, session, accessibility, and release-contract suites.
- [ ] Run `git diff --check`.
- [ ] Review the final diff against every requirement in the design.

