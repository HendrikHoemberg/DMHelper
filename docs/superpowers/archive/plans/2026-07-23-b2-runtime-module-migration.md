# B2 Runtime Module Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace B1's transitional and page-coupled cockpit bodies with ten honest, independently renderable runtime modules, prioritizing Story, Encounter, Map, Party, and Presentation, then completing Session plan, Quick notes, Reference, Audio, and Session log without weakening layout, safety, or live-session state.

**Architecture:** Keep `cockpit-layout.js` as the sole owner of zones, tabs, presets, focus, compact flags, and visibility. Add a separate endpoint-backed runtime-module controller that loads only visible module bodies, refreshes modules independently, keeps prior DOM on failure, and converts hidden updates into attention badges. Server-side module view services return detached, typed view records assembled inside read transactions; templates do not traverse lazy entities. The Exploration no-JavaScript baseline remains server rendered, while modules outside that baseline load on first visibility. Map/Konva, encounter tracker state, Alpine components, player projection, and server services retain their current ownership.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Thymeleaf, Alpine.js, htmx, dependency-free browser JavaScript, Konva, H2, JUnit 5, MockMvc, Mockito, AssertJ, and Playwright.

## Global Constraints

- B1 is a hard dependency. Do not rewrite its layout document, persistence schema, preset semantics, docking model, or locked-by-default behavior.
- The existing ten stable module keys remain unchanged: `story`, `map`, `encounter`, `session-plan`, `party`, `quick-notes`, `presentation`, `reference`, `audio`, and `session-log`.
- A module appears at most once. Module refresh replaces only `[data-module-content]`; it never replaces or recreates the B1 shell.
- `cockpit-layout.js` owns layout only. `cockpit-modules.js` owns module loading and refresh only. `session-cockpit.js` owns consequential session actions and dispatches semantic refresh events.
- Production and test execution both use `spring.jpa.open-in-view=false`. Every module endpoint must finish view assembly inside an explicit read transaction and return records/immutable collections rather than lazy entity graphs.
- The default Exploration page remains useful without JavaScript: Story, Session plan, Party, and the Quick notes capture action are server rendered.
- Hidden modules may load only when first shown. A hidden update creates attention; it does not switch tabs, presets, or zones.
- Existing module content remains visible when refresh fails. Retry repeats only that module request.
- Each request has one active `AbortController` per module; stale or aborted responses may not overwrite newer content or show an error.
- Module endpoint loading state appears within 100 ms and settles within the existing two-second module budget on the populated synthetic campaign.
- Map is initialized at most once per page. Routine module refresh must never replace `#battleCanvasWrap` after Konva has mounted.
- Presentation preview uses the same `/player` document and `/api/v1/table/state` projection as the external player display. No DM DOM approximation is allowed.
- Table-safe behavior remains registry-driven: `HIDE` modules are absent from visibility and focus; `FILTER` modules retain only explicitly player-safe descendants.
- Compact and Focus modes change information density, not domain state. Entering or leaving either mode must not reset map transforms, encounter setup, current turn, note drafts, or presentation state.
- Deep adventure, map, encounter, party, and handout editing remains outside the cockpit.
- Remove map-, handout-, audio-, encounter-, and presentation-specific command-bar actions once their owning module provides the runtime action. Search, Dice, Session, Screen safety, layout, identity, and presentation status remain global.
- Do not add a frontend package manager, SPA framework, third-party docking system, or client-side template library.
- No private Phandelver package, source PDF, screenshot evidence, or `artifacts/` content may be committed.
- Follow TDD: add and run the stated red test before each production change.

## Chosen Runtime Model

Use progressively enhanced endpoint-backed modules.

- **Chosen — stable shell plus replaceable content root:** B1 chrome, focus targets, attention, safety metadata, and layout identity survive every module refresh.
- **Chosen — server-rendered Exploration baseline:** the page remains usable if module JavaScript fails, and the first meaningful render is not delayed behind five requests.
- **Chosen — lazy first load for non-baseline modules:** Map, Encounter, Presentation, Reference, and Session log do not inflate the initial page or initialize heavy clients while hidden.
- **Rejected — one full-workspace refresh:** it couples unrelated failure domains, repeats expensive data loading, and can destroy Konva/Alpine state.
- **Rejected — client-side JSON templates for all modules:** it duplicates existing Thymeleaf rendering and expands the player-safety boundary.
- **Rejected — iframe per module:** only Presentation deliberately embeds `/player`; using frames elsewhere would fragment keyboard, styling, and event ownership.
- **Rejected — automatic refresh polling:** semantic domain events and explicit user actions are sufficient; polling adds load and race conditions during play.

## Runtime Module Contract

### Endpoint

Each registry source is an endpoint template:

```text
/campaigns/{campaignId}/session/modules/{module-key}?mode={STANDARD|COMPACT|FOCUSED}
```

Map additionally accepts the current workspace selection:

```text
/campaigns/{campaignId}/session/modules/map?mode=STANDARD&mapId={uuid}
```

Every successful response contains exactly one root:

```html
<section data-cockpit-module-fragment="story"
         data-module-mode="STANDARD"
         data-module-empty="false">
  <div data-module-content-root>…</div>
</section>
```

Rules:

- unknown module key: HTTP 404;
- unsupported `COMPACT` or `FOCUSED`: HTTP 400;
- campaign/content ownership mismatch: HTTP 404;
- empty data: HTTP 200 with `data-module-empty="true"` and a useful next action;
- a render/service failure uses the existing global error boundary and must not return a partial 200 fragment;
- module endpoints are PIN-protected like the cockpit.

### Client events

`cockpit-modules.js` consumes:

```text
cockpit:layout-applied
cockpit:module-visibility
cockpit:module-mode
cockpit:module-refresh
cockpit:module-invalidate
screen-safety-changed
```

It produces:

```text
cockpit:module-state
cockpit:module-loaded
cockpit:module-load-failed
cockpit:module-content-ready
```

Event payloads are:

```javascript
{ moduleKey: 'story', mode: 'STANDARD', reason: 'scene-changed' }
{ moduleKey: 'story', state: 'loading' }
{ moduleKey: 'story', state: 'ready', durationMs: 83.4, revision: 4 }
{ moduleKey: 'story', state: 'error', message: 'Story could not refresh.', retry: Function }
```

`cockpit:module-refresh` fetches immediately when visible. If hidden, it records one attention
increment and marks the module stale. `cockpit:module-invalidate` only marks stale/attention; the
next visibility or explicit retry fetches it.

### Mode rules

| Module | Compact | Standard | Focused |
|---|---|---|---|
| Story | scene identity, read-aloud, navigation | full scene runtime content and actions | standard plus expanded checks, participants, links, and threat cards |
| Map | unsupported | map canvas plus runtime controls | same mounted canvas with expanded side panel; no rerender |
| Encounter | active/setup order and essential HP/turn actions | complete tracker and planned encounter actions | standard with expanded statblocks/mechanics |
| Session plan | next beats and progress summary | full ordered beats and quest progress | expanded beat context |
| Party | names, HP, AC, passive perception | all passive scores, conditions, death saves, sheet link | expanded per-member runtime state |
| Quick notes | capture plus three newest | capture, unresolved list, promote/delete | expanded list |
| Presentation | status and Curtain | exact player preview plus map/handout controls | larger exact preview |
| Reference | search field plus top results | grouped inline results | expanded result detail links |
| Audio | existing minimal widget | existing widget | expanded cue provenance where available |
| Session log | recent three events | current-session timeline and recent logs | full current-session evidence list |

## File Structure

### Runtime contract and controller

- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitModuleMode.java`
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java`
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/SessionLogModuleService.java`
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeModuleController.java`
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistry.java`
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java`

### Templates and browser controllers

- Create `src/main/resources/static/js/cockpit-modules.js`
- Create `src/main/resources/static/js/cockpit-reference.js`
- Create `src/main/resources/static/css/cockpit-modules.css`
- Create `src/main/resources/templates/session/modules/_story.html`
- Create `src/main/resources/templates/session/modules/_map.html`
- Create `src/main/resources/templates/session/modules/_encounter.html`
- Create `src/main/resources/templates/session/modules/_session-plan.html`
- Create `src/main/resources/templates/session/modules/_party.html`
- Create `src/main/resources/templates/session/modules/_quick-notes.html`
- Create `src/main/resources/templates/session/modules/_presentation.html`
- Create `src/main/resources/templates/session/modules/_reference.html`
- Create `src/main/resources/templates/session/modules/_audio.html`
- Create `src/main/resources/templates/session/modules/_session-log.html`
- Modify `src/main/resources/templates/session/cockpit.html`
- Modify `src/main/resources/templates/session/_cockpit-workbench.html`
- Modify `src/main/resources/templates/session/_cockpit-module-shell.html`
- Delete `src/main/resources/templates/session/_cockpit-deferred-modules.html`
- Modify `src/main/resources/templates/session/_story-rail.html`
- Modify `src/main/resources/templates/session/_encounter-rail.html`
- Modify `src/main/resources/templates/session/_map-module.html`
- Modify `src/main/resources/templates/session/_session-plan.html`
- Modify `src/main/resources/templates/party/_summary-bar.html`
- Modify `src/main/resources/templates/player/view.html`
- Modify `src/main/resources/static/js/cockpit-layout.js`
- Modify `src/main/resources/static/js/session-cockpit.js`
- Modify `src/main/resources/static/js/quicknotes.js`
- Modify `src/main/resources/static/css/cockpit-layout.css`

### Tests and documentation

- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewServiceTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/SessionLogModuleServiceTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeModuleControllerTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeModuleSafetyContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerViewSecurityContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify `docs/dm-manual/03-session-cockpit.md`

---

### Task 1: Freeze the Endpoint and Mode Contract

**Files:**

- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitModuleMode.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistry.java`
- Modify: `src/main/resources/templates/session/_cockpit-module-shell.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`

**Interfaces:**

- Produces `CockpitModuleMode { COMPACT, STANDARD, FOCUSED }`.
- Changes every registry `source.kind` to `ENDPOINT`.
- Endpoint source values use `/campaigns/{campaignId}/session/modules/{key}`.
- Adds `data-module-endpoint`, `data-module-loaded`, `data-module-stale`, and stable `[data-module-content]` to the B1 shell.

- [ ] **Step 1: Add red registry and shell tests**

In `CockpitModuleRegistryTest`, assert all ten sources:

```java
assertThat(registry.all())
        .allSatisfy(module -> {
            assertThat(module.source().kind())
                    .isEqualTo(CockpitModuleSource.Kind.ENDPOINT);
            assertThat(module.source().value())
                    .isEqualTo("/campaigns/{campaignId}/session/modules/" + module.key());
        });
```

In `CockpitRuntimeModuleContractTest`, read `_cockpit-module-shell.html` and assert:

```java
assertThat(shell).contains(
        "data-module-endpoint",
        "data-module-loaded",
        "data-module-stale",
        "data-module-content");
assertThat(count(shell, "data-module-content")).isEqualTo(1);
```

- [ ] **Step 2: Run the red tests**

```bash
./mvnw -Dtest=CockpitModuleRegistryTest,CockpitRuntimeModuleContractTest test
```

Expected: registry sources are still Thymeleaf fragments and the shell lacks endpoint state.

- [ ] **Step 3: Add the enum and endpoint metadata**

Create:

```java
package dev.hendrikhoemberg.dmhelper.session.runtime;

public enum CockpitModuleMode {
    COMPACT, STANDARD, FOCUSED
}
```

Replace registry fragment/deferred helpers with:

```java
private static CockpitModuleDefinition endpoint(
        String key, String title, int minWidth, int minHeight,
        Set<CockpitZone> zones, boolean compact, boolean focus,
        CockpitScreenSafetyBehavior safety, String empty) {
    return new CockpitModuleDefinition(key, title,
            new CockpitModuleSource(CockpitModuleSource.Kind.ENDPOINT,
                    "/campaigns/{campaignId}/session/modules/" + key),
            minWidth, minHeight, zones, compact, focus, safety,
            new CockpitModuleStateContract(empty, "Loading " + title + "…",
                    title + " could not refresh. Existing content was kept.", true));
}
```

Keep the existing keys, titles, dimensions, zones, capabilities, safety behavior, and state copy.

In the shell, render:

```html
<div class="cockpit-module__body"
     data-module-body
     th:attr="aria-labelledby=|cockpitModuleTitle-${module.key}|">
  <div data-module-content
       th:attr="data-module-endpoint=${module.source.value},
                data-module-loaded=${initiallyRendered},
                data-module-stale='false'">
    <th:block th:if="${initiallyRendered}">
      <!-- Existing B1 switch remains temporarily and is removed in Task 10. -->
    </th:block>
  </div>
</div>
```

Add `initiallyRendered` to the fragment signature. `_cockpit-workbench.html` passes `true` only for
Story, Session plan, Party, and Quick notes; all other shells pass `false`.

- [ ] **Step 4: Run the focused contract tests**

```bash
./mvnw -Dtest=CockpitModuleRegistryTest,CockpitRuntimeModuleContractTest,SessionCockpitTemplateContractTest test
```

Expected: green, with all existing B1 shell uniqueness assertions retained.

- [ ] **Step 5: Commit the runtime contract**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistry.java \
  src/main/resources/templates/session/_cockpit-module-shell.html \
  src/main/resources/templates/session/_cockpit-workbench.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/session
git commit -m "refactor(cockpit): define endpoint-backed module contract"
```

---

### Task 2: Build the Failure-Isolated Module Loader

**Files:**

- Create: `src/main/resources/static/js/cockpit-modules.js`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/js/cockpit-layout.js`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java`

**Interfaces:**

- Produces `window.cockpitModules`.
- Produces `load(moduleKey, options)`, `refresh(moduleKey, reason)`, `invalidate(moduleKey, reason)`, `modeFor(moduleKey)`, and `isLoaded(moduleKey)`.
- Adds `cockpit:module-mode` events from the layout controller without transferring mode ownership.
- Consumes `window.cockpitLayoutConfig.runtimeModulesEnabled`; it is deliberately `false` until
  Task 3 has installed all ten server endpoints and templates.

- [ ] **Step 1: Add the red client contract test**

The static test must require:

```java
assertThat(js).contains(
        "class CockpitModuleController",
        "new AbortController()",
        "cockpit:module-visibility",
        "cockpit:module-refresh",
        "cockpit:module-invalidate",
        "cockpit:module-state",
        "cockpit:module-loaded",
        "data-cockpit-module-fragment",
        "replaceChildren");
assertThat(js).doesNotContain("outerHTML =", "window.location.reload()");
```

- [ ] **Step 2: Run the red tests**

```bash
./mvnw -Dtest=CockpitModuleClientContractTest test
```

Expected: failure because the runtime module controller and lazy load behavior do not exist.

- [ ] **Step 3: Implement the controller**

The controller constructor discovers all `[data-module-key]` shells and stores:

```javascript
this.requests = new Map();
this.revisions = new Map();
this.stale = new Set();
this.loaded = new Set();
```

The `load` algorithm is exact:

1. reject unknown keys;
2. skip hidden non-forced loads;
3. skip a loaded, non-stale module when mode is unchanged;
4. abort the previous request for that key;
5. increment the key revision;
6. dispatch loading;
7. replace `{campaignId}` in the endpoint;
8. append `mode` and Map's current `mapId`;
9. fetch with `Accept: text/html` and `X-Cockpit-Module: key`;
10. parse through a `<template>`;
11. require exactly one `[data-cockpit-module-fragment="key"]`;
12. ignore the response if its revision is stale;
13. preserve focused element identity where the replacement contains the same `id`;
14. call `replaceChildren(fragment)`;
15. mark loaded/not stale and dispatch ready/content-ready;
16. on non-abort failure, retain prior content and dispatch error with `retry`.

Use `window.dmRequest` rather than raw `fetch` so PIN/error handling remains consistent. Extend
`dmRequest` only if it cannot accept an `AbortSignal`; do not create a second request wrapper.

- [ ] **Step 4: Add mode emission to B1 without moving ownership**

After `renderLayout`, `focusModule`, and `restoreFocus`, dispatch:

```javascript
window.dispatchEvent(new CustomEvent('cockpit:module-mode', {
  detail: { moduleKey: key, mode: this.moduleMode(key) }
}));
```

`moduleMode(key)` returns `FOCUSED` for the focused module, `COMPACT` when its shell has
`data-compact="true"`, and `STANDARD` otherwise.

- [ ] **Step 5: Load the controller before Alpine**

Add `/js/cockpit-modules.js` after `/js/cockpit-layout.js` and before Alpine. Bootstrap with:

```javascript
window.cockpitLayoutConfig.runtimeModulesEnabled = false;
window.cockpitModules = new CockpitModuleController(window.cockpitLayoutConfig);
window.cockpitModules.mount();
```

If the B1 layout controller has not mounted, wait for `cockpit:layout-applied`; do not poll.
`mount()` must leave the B1 server-rendered bodies untouched and bind no fetch-triggering listeners
while `runtimeModulesEnabled !== true`.

- [ ] **Step 6: Run the loader gate**

```bash
./mvnw -Dtest=CockpitModuleClientContractTest,RuntimeModuleSafetyContractTest,SessionCockpitTemplateContractTest test
node --check src/main/resources/static/js/cockpit-modules.js
node --check src/main/resources/static/js/cockpit-layout.js
```

Expected: green; the dormant controller changes no current B1 runtime behavior.

- [ ] **Step 7: Commit the loader**

```bash
git add src/main/resources/static/js/cockpit-modules.js \
  src/main/resources/static/js/cockpit-layout.js \
  src/main/resources/templates/session/cockpit.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/session
git commit -m "feat(cockpit): load runtime modules independently"
```

---

### Task 3: Add Transaction-Safe Module Endpoints and Typed Views

**Files:**

- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeModuleController.java`
- Create: `src/main/resources/templates/session/modules/_story.html`
- Create: `src/main/resources/templates/session/modules/_map.html`
- Create: `src/main/resources/templates/session/modules/_encounter.html`
- Create: `src/main/resources/templates/session/modules/_session-plan.html`
- Create: `src/main/resources/templates/session/modules/_party.html`
- Create: `src/main/resources/templates/session/modules/_quick-notes.html`
- Create: `src/main/resources/templates/session/modules/_presentation.html`
- Create: `src/main/resources/templates/session/modules/_reference.html`
- Create: `src/main/resources/templates/session/modules/_audio.html`
- Create: `src/main/resources/templates/session/modules/_session-log.html`
- Create: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewServiceTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeModuleControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**

`CockpitRuntimeModuleViewService` exposes:

```java
StoryView story(UUID campaignId);
MapView map(UUID campaignId, UUID requestedMapId);
EncounterView encounter(UUID campaignId);
SessionPlanView sessionPlan(UUID campaignId);
PartyView party(UUID campaignId);
QuickNotesView quickNotes(UUID campaignId);
PresentationView presentation(UUID campaignId);
ReferenceView reference(UUID campaignId);
AudioView audio(UUID campaignId);
SessionLogView sessionLog(UUID campaignId);
```

The service returns records containing scalar values, nested records, and immutable lists. Entity
types are forbidden in these public record components.

- [ ] **Step 1: Add red transaction and endpoint tests**

For every method, build data using `PopulatedCampaignFixture`, call the service, clear the
persistence context, and access every record component. Assert no lazy access and no entity class:

```java
assertThatThrownBy(() -> entityManager.getReference(
        Scene.class, view.sceneId()).getTitle())
        .isInstanceOf(LazyInitializationException.class);
assertThat(view.title()).isEqualTo("Cragmaw Hideout");
assertThat(allRecordComponentTypes(view.getClass()))
        .noneMatch(type -> type.isAnnotationPresent(Entity.class));
```

The MVC test hits all ten routes with `mode=STANDARD` and asserts HTTP 200, the exact module marker,
and one content root. Also test unknown key, unsupported mode, foreign `mapId`, and PIN interception.

Add the Playwright sequence that Task 2 intentionally deferred:

1. start in Exploration;
2. confirm Encounter is not loaded;
3. select Theatre of Mind;
4. observe a loading state within 100 ms;
5. receive exactly one Encounter fragment;
6. inject a 503 for the next Story request;
7. refresh Story and confirm its previous title remains visible with Retry;
8. retry successfully;
9. start two Story refreshes and delay the first;
10. confirm the second response wins and the aborted request creates no error.

- [ ] **Step 2: Run the red service/controller tests**

```bash
./mvnw -Dtest=CockpitRuntimeModuleViewServiceTest,CockpitRuntimeModuleControllerTest test
```

Expected: compilation failure because the service/controller do not exist.

- [ ] **Step 3: Extract reusable selection and view assembly**

Move map-selection logic from the private `SessionWorkspaceService.select` method into a
package-visible read-only helper used by both the legacy full workspace and `MapView`. Do not
duplicate precedence:

```text
explicit request → stored open-session map → active encounter map
→ current scene map → first plan map → none
```

Each module method is `@Transactional(readOnly = true)`. Copy JPA collections with `List.copyOf`
and map entities to records before returning.

- [ ] **Step 4: Implement explicit controller methods**

Use one route per module rather than a dynamic fragment expression. Example:

```java
@GetMapping("/story")
public String story(@PathVariable UUID campaignId,
                    @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                    Model model) {
    requireSupported("story", mode);
    model.addAttribute("view", views.story(campaignId));
    model.addAttribute("mode", mode);
    return "session/modules/_story :: body";
}
```

All methods call `registry.require(key)` and reject unsupported modes before loading data.

- [ ] **Step 5: Add all ten minimal typed endpoint bodies**

Create every `session/modules/_*.html` file now so enabling the loader cannot expose a 404 or
missing-template failure. Each fragment has the exact `body(view, mode)` signature and required
root/content markers. At this checkpoint:

- Story, Map, Encounter, Session plan, Party, and Audio render their current runtime information
  from typed records.
- Quick notes, Presentation, Reference, and Session log render useful minimal bodies equivalent to
  their B1 exits plus their authoritative status/empty information.
- No endpoint template receives a `SessionWorkspace` or JPA entity.

The later vertical-slice tasks enrich these bodies and their mode behavior; they do not create the
endpoint contract for the first time.

- [ ] **Step 6: Enable endpoint loading while retaining the B1 baseline**

Set:

```javascript
window.cockpitLayoutConfig.runtimeModulesEnabled = true;
```

The four initial bodies remain server rendered through the B1 switch until Task 11 replaces that
last compatibility path with typed initial fragments. The other six remain unloaded until first
visibility. Do not enable the flag until all ten endpoint MVC tests are green.

- [ ] **Step 7: Run production-parity and browser tests**

```bash
./mvnw -Dtest=CockpitRuntimeModuleViewServiceTest,CockpitRuntimeModuleControllerTest,SessionWorkspaceMapSelectionTest,SessionControllerTest,CockpitModuleClientContractTest,CoreSessionLoopSmokeTest test
```

Expected: green with `spring.jpa.open-in-view=false`; an isolated refresh failure does not destroy
content or affect another module.

- [ ] **Step 8: Commit the server boundary and enablement**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session \
  src/main/resources/templates/session/modules \
  src/main/resources/static/css/cockpit-modules.css \
  src/main/resources/templates/session/cockpit.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): expose transaction-safe module endpoints"
```

---

### Task 4: Migrate Story as the First Vertical Slice

**Files:**

- Modify: `src/main/resources/templates/session/modules/_story.html`
- Modify: `src/main/resources/templates/session/_story-rail.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Required populated state:**

- scene picker and current scene identity;
- complete scene body and structured sections;
- read-aloud visibly distinct and never screen-sensitive;
- checks, participants, transitions, links, threat cards, linked tables, and scene actions;
- previous/next editorial navigation;
- scene encounter seeding without leaving the cockpit.

- [ ] **Step 1: Add red mode and interaction tests**

Assert all three Story modes render:

- Compact excludes `.scene-checks`, `.scene-participants`, and full DM section bodies but includes
  current title, read-aloud, previous/next, and `Start encounter from this scene`.
- Standard contains all existing B1 runtime content.
- Focused contains expanded checks/participants/links.
- Empty state says `No current scene yet` and includes the scene picker.
- Table-safe leaves read-aloud visible while DM summary/body/checks/participants/transitions are
  hidden and unfocusable.

Browser-test scene selection, previous/next, branch transition, linked table roll, quick-note
capture, and encounter seeding through the new module endpoint.

- [ ] **Step 2: Run the red Story tests**

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,CockpitRuntimeModuleControllerTest,CoreSessionLoopSmokeTest test
```

Expected: the endpoint template and mode-specific markup do not exist.

- [ ] **Step 3: Render Story from `StoryView`**

Move entity-dependent expressions out of `_story-rail.html`. The new module fragment wraps the
refactored rail:

```html
<section th:fragment="body"
         data-cockpit-module-fragment="story"
         th:attr="data-module-mode=${mode},
                  data-module-empty=${view.sceneId == null}">
  <div data-module-content-root
       class="runtime-story"
       th:classappend="| runtime-story--${#strings.toLowerCase(mode.name())}|">
    <th:block th:replace="~{session/_story-rail :: story(view=${view}, mode=${mode})}"></th:block>
  </div>
</section>
```

Do not use CSS line clamping on read-aloud or current action labels. Compact mode removes secondary
detail at render time so hidden DM content is not merely clipped.

- [ ] **Step 4: Replace `refreshRails()` with semantic module refresh**

Add:

```javascript
refreshModules(keys, reason) {
  for (const moduleKey of keys) {
    window.dispatchEvent(new CustomEvent('cockpit:module-refresh', {
      detail: { moduleKey, reason }
    }));
  }
}
```

Scene selection, stepping, transition following, and seeding refresh Story. Seeding and encounter
activation also refresh Encounter. Remove direct `.innerHTML` writes for `.cockpit-story`.

- [ ] **Step 5: Run and commit the Story slice**

```bash
./mvnw -Dtest=CockpitRuntimeModuleViewServiceTest,CockpitRuntimeModuleControllerTest,CockpitRuntimeModuleContractTest,SessionCockpitTemplateContractTest,CoreSessionLoopSmokeTest test
node --check src/main/resources/static/js/session-cockpit.js
git add src/main/resources/templates/session/modules/_story.html \
  src/main/resources/templates/session/_story-rail.html \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): migrate Story runtime module"
```

---

### Task 5: Migrate Encounter Without Resetting Combat State

**Files:**

- Modify: `src/main/resources/templates/session/modules/_encounter.html`
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Required populated state:**

- explicit initiative setup, including zero/negative values and Start combat;
- current round, active turn, HP, temp HP, conditions, defeat/revive, recharge, concentration,
  waves, lair/legendary actions, threat mechanics, and encounter completion;
- planned encounter list and activate/map actions;
- full statblocks in Standard/Focused, compact turn essentials in Compact.

- [ ] **Step 1: Add red mode and state-preservation tests**

Browser sequence:

1. load Theatre of Mind;
2. enter one manual initiative and leave another unset;
3. focus and return Encounter;
4. switch to Combat and back;
5. confirm manual input and server setup values survive;
6. start combat, damage a creature, add a condition, advance turn;
7. trigger a hidden Encounter invalidation;
8. confirm attention without tab/preset switching;
9. reveal Encounter and confirm refreshed authoritative values.

Also assert a failed Encounter endpoint refresh leaves the live tracker DOM and retry action intact.

- [ ] **Step 2: Run the red Encounter tests**

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,EncounterTemplateContractTest,CoreSessionLoopSmokeTest test
```

- [ ] **Step 3: Render the tracker by mode**

Wrap the existing tracker rather than creating a second encounter controller:

```html
<section th:fragment="body"
         data-cockpit-module-fragment="encounter"
         th:attr="data-module-mode=${mode},
                  data-module-empty=${view.activeEncounter == null and view.plannedEncounters.isEmpty()}">
  <div data-module-content-root class="runtime-encounter"
       th:attr="data-encounter-id=${view.activeEncounter != null ? view.activeEncounter.id : null}">
    <th:block th:replace="~{session/_encounter-rail :: encounters(view=${view}, mode=${mode})}"></th:block>
  </div>
</section>
```

Add mode classes/data attributes to `_tracker.html`; keep its existing Alpine/API behavior. Compact
mode omits statblock expansion and secondary log/history controls but never hides initiative setup,
active turn, HP, conditions, or End encounter.

- [ ] **Step 4: Route semantic encounter events**

Encounter activation, seeding, completion, and wave changes dispatch refresh/invalidate for
Encounter and Story. HP/turn/setup mutations that already update the tracker in place must not
force an endpoint swap. Dispatch `cockpit:module-invalidate` only when another hidden copy of
server-rendered summary data is stale.

- [ ] **Step 5: Run and commit the Encounter slice**

```bash
./mvnw -Dtest=EncounterTemplateContractTest,EncounterApiControllerTest,CockpitRuntimeModuleViewServiceTest,CockpitRuntimeModuleControllerTest,CockpitRuntimeModuleContractTest,CoreSessionLoopSmokeTest test
git add src/main/resources/templates/session/modules/_encounter.html \
  src/main/resources/templates/session/_encounter-rail.html \
  src/main/resources/templates/encounter/_tracker.html \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): migrate Encounter runtime module"
```

---

### Task 6: Migrate Map While Preserving the Single Konva Instance

**Files:**

- Modify: `src/main/resources/templates/session/modules/_map.html`
- Modify: `src/main/resources/templates/session/_map-module.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

- [ ] **Step 1: Add red lazy-init and transform-preservation tests**

At 1366×768:

1. start in Exploration and assert `window.battleMap` is absent;
2. choose Combat and assert the Map endpoint loads once and one `BattleMap` is constructed;
3. pan/zoom and select a token;
4. tab away, focus/return, switch presets, and toggle Table-safe;
5. confirm the same JS instance, stage transform, selected token, and server map remain;
6. switch workspace maps and confirm no full page navigation;
7. hide Map and assert no repeated Konva draws;
8. reveal it and assert one resize after resume.

- [ ] **Step 2: Run the red map tests**

```bash
./mvnw -Dtest=SessionCockpitMapContractTest,CockpitRuntimeModuleContractTest,CoreSessionLoopSmokeTest test
```

- [ ] **Step 3: Implement first-load-only Map content**

The Map module endpoint supplies the canvas and controls only before `window.battleMap` exists.
`cockpit-modules.js` marks Map `preserveAfterLoad`; later mode/refresh events call:

```javascript
window.battleMap?.resizeToContainer();
window.battleMap?.setRenderingActive(window.cockpitLayout.isModuleVisible('map'));
```

They never replace Map content. Focused Map expands its sidebar through shell/mode classes, not an
endpoint rerender. Remove presentation controls from `_map-module.html`; retain map picker, select,
measure, AoE, token, grid/movement, threat-pin, and save-status controls.

- [ ] **Step 4: Keep workspace selection authoritative**

Map switching continues to persist through `/session/workspace-map`. The module endpoint accepts
`mapId` only as an explicit initial selection and validates campaign ownership. It must not mutate
the stored session merely because a module rendered.

- [ ] **Step 5: Run and commit the Map slice**

```bash
./mvnw -Dtest=SessionCockpitMapContractTest,GameMapApiControllerTest,PlayerSafeMapProjectionTest,CockpitRuntimeModuleControllerTest,CoreSessionLoopSmokeTest test
node --check src/main/resources/static/js/map/battle-map.js
git add src/main/resources/templates/session/modules/_map.html \
  src/main/resources/templates/session/_map-module.html \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/js/map/battle-map.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): migrate Map runtime module"
```

---

### Task 7: Migrate Party to a Runtime Decision Surface

**Files:**

- Modify: `src/main/resources/templates/session/modules/_party.html`
- Modify: `src/main/resources/templates/party/_summary-bar.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Required state per active member:**

- character/player identity;
- current/max/temp HP and clear low/down state;
- AC, speed, passive Perception, Insight, and Investigation;
- conditions and death-save counts;
- read-only link to the sheet.

- [ ] **Step 1: Add red Party state tests**

Use a party containing healthy, bloodied, unconscious, conditioned, and no-sheet members. Assert:

- compact mode has name, HP, AC, and passive Perception;
- standard/focused expose all passive scores, conditions, and death saves;
- no member edit/delete/toggle-active controls appear;
- Table-safe filters sensitive mechanics but preserves safe identity/HP treatment defined by the
  registry;
- empty state links to party preparation rather than rendering a blank bar.

- [ ] **Step 2: Run the red Party tests**

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,CockpitRuntimeModuleViewServiceTest,CoreSessionLoopSmokeTest test
```

- [ ] **Step 3: Render records rather than Party entities**

Change `_summary-bar.html` to accept `PartyView` rows or create an internal compatible fragment;
do not pass `PartyMember` entities from the module endpoint. Preserve the existing fragment
signature for non-cockpit callers until their tests are migrated.

Parse `conditionsJson` server-side into safe labels. Malformed condition JSON produces an empty
condition list plus a logged warning; it may not fail the Party module.

- [ ] **Step 4: Refresh on actual party-state events**

After sheet live-state, rest, condition, death-save, loot, XP, and milestone operations succeed,
dispatch `party-runtime-changed`. The cockpit translates it into Party refresh/attention. Do not
poll.

- [ ] **Step 5: Run and commit the Party slice**

```bash
./mvnw -Dtest=CockpitRuntimeModuleViewServiceTest,CockpitRuntimeModuleControllerTest,CockpitRuntimeModuleContractTest,PartyBatchOperationsTest,CoreSessionLoopSmokeTest test
git add src/main/resources/templates/session/modules/_party.html \
  src/main/resources/templates/party/_summary-bar.html \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): migrate Party runtime module"
```

---

### Task 8: Build the Exact Presentation Module and Clean the Command Bar

**Files:**

- Modify: `src/main/resources/templates/session/modules/_presentation.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/templates/session/_presentation-preview.html`
- Modify: `src/main/resources/templates/player/view.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/PlayerViewController.java`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerViewSecurityContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Required controls:**

- current presentation mode and current map/handout identity;
- exact embedded player preview;
- Curtain;
- present selected reviewed map;
- preview selected handout, then present;
- two-step emergency override only inside exact preview;
- open external player display.

- [ ] **Step 1: Add red projection-parity and ownership tests**

Assert the embedded preview is `/player?embedded=true`, uses the same player script/state endpoint,
and contains no DM-only controls. Browser-test:

1. Presentation preset;
2. exact curtain preview;
3. reviewed map presentation;
4. safe handout preview/presentation;
5. blocked `DM_SOURCE`/`UNREVIEWED`;
6. two explicit emergency confirmation steps and audit;
7. failed presentation keeps the prior preview/status and offers retry;
8. external `/player` and embedded preview reach the same mode/ref.

- [ ] **Step 2: Run the red Presentation tests**

```bash
./mvnw -Dtest=PlayerViewSecurityContractTest,CockpitRuntimeModuleContractTest,HandoutPreviewParityTest,CoreSessionLoopSmokeTest test
```

- [ ] **Step 3: Add embedded player rendering**

`PlayerViewController` accepts `embedded` and adds only a presentation hint:

```java
@GetMapping("/player")
public String playerView(@RequestParam(defaultValue = "false") boolean embedded,
                         Model model) {
    model.addAttribute("embedded", embedded);
    return "player/view";
}
```

The same `player/view.html`, `player-view.js`, WebSocket/state endpoint, and projection CSS render
both views. Embedded mode removes outer page margin/chrome only; it does not use a different data
model.

- [ ] **Step 4: Move presentation controls into the module**

Remove the handout picker and Handouts action from the command-bar overflow. Remove Curtain,
Present current map, and Preview table from Map. Keep only the compact global presentation status
badge in the command bar.

The Presentation module owns the picker and existing `_presentation-preview` confirmation layer.
On success, update Alpine presentation state and invalidate Presentation; the embedded player
updates from the authoritative projection channel.

- [ ] **Step 5: Run and commit the Presentation slice**

```bash
./mvnw -Dtest=PlayerViewSecurityContractTest,PlayerSafeProjectionServiceTest,HandoutPreviewParityTest,RuntimeModuleSafetyContractTest,CockpitRuntimeModuleControllerTest,CockpitRuntimeModuleContractTest,CoreSessionLoopSmokeTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/live/web/PlayerViewController.java \
  src/main/resources/templates/session/modules/_presentation.html \
  src/main/resources/templates/session/cockpit.html \
  src/main/resources/templates/session/_presentation-preview.html \
  src/main/resources/templates/player/view.html \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/live \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): add exact Presentation runtime module"
```

---

### Task 9: Complete Session Plan, Quick Notes, Reference, and Audio Utilities

**Files:**

- Modify: `src/main/resources/templates/session/modules/_session-plan.html`
- Modify: `src/main/resources/templates/session/modules/_quick-notes.html`
- Modify: `src/main/resources/templates/session/modules/_reference.html`
- Modify: `src/main/resources/templates/session/modules/_audio.html`
- Create: `src/main/resources/static/js/cockpit-reference.js`
- Modify: `src/main/resources/templates/session/_session-plan.html`
- Modify: `src/main/resources/static/js/quicknotes.js`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

- [ ] **Step 1: Add red utility contracts**

Assert:

- Session plan shows ordered beats, broken-link state, current/upcoming emphasis, quest progress,
  and empty-state next action.
- Quick notes provides immediate campaign-scoped capture while idle or running, lists unresolved
  notes, preserves an unsent input across tab/preset/focus changes, and promotes/deletes visibly.
- Reference searches existing `/api/v1/search`, groups campaign/rules/statblocks/conditions, keeps
  results inside the module, and opens deep details only on explicit selection.
- Audio renders one authoritative widget instance, current cue/source, play/pause/stop/volume, and
  failure state; command bar contains no audio-specific action.

- [ ] **Step 2: Run the red utility tests**

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,QuickNoteApiControllerTest,CommandPaletteApiControllerTest,AudioCockpitSecurityTest,CoreSessionLoopSmokeTest test
```

- [ ] **Step 3: Implement the four endpoint bodies**

Quick notes uses `targetType=CAMPAIGN` and `targetId=campaignId` for the standalone module so it
works before a persisted session exists. Scene-scoped capture stays in Story. Give every
quick-notes Alpine instance a unique DOM id derived from target type/id.

Reference uses a dedicated Alpine/browser controller with a 250 ms debounce, minimum two
characters, an abortable request, grouped results, visible empty/error state, and no HTML injection.
Render result text with `x-text`.

Audio endpoint rendering reuses `audio/_cockpit-widget`; move its config beside the Audio module
or retain the one global config fragment, but assert exactly one interactive widget body.

- [ ] **Step 4: Route utility refresh events**

- objective mutation → refresh Session plan and invalidate Session log;
- quick-note add/promote/delete → refresh/invalidate Quick notes and Session log;
- scene change → refresh Story and Session plan only when plan emphasis changes;
- audio cue/source/status events update Audio in place and invalidate Session log if recorded.

- [ ] **Step 5: Run and commit the utility tranche**

```bash
./mvnw -Dtest=CockpitRuntimeModuleViewServiceTest,CockpitRuntimeModuleControllerTest,CockpitRuntimeModuleContractTest,QuickNoteApiControllerTest,CommandPaletteApiControllerTest,AudioCockpitSecurityTest,CoreSessionLoopSmokeTest test
node --check src/main/resources/static/js/cockpit-reference.js
node --check src/main/resources/static/js/quicknotes.js
git add src/main/resources/templates/session/modules \
  src/main/resources/templates/session/_session-plan.html \
  src/main/resources/templates/session/cockpit.html \
  src/main/resources/static/js/cockpit-reference.js \
  src/main/resources/static/js/quicknotes.js \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): migrate runtime utility modules"
```

---

### Task 10: Build the Session Log Runtime Module

**Files:**

- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/SessionLogModuleService.java`
- Modify: `src/main/resources/templates/session/modules/_session-log.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/SessionLogModuleServiceTest.java`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**

`SessionLogModuleService.current(UUID campaignId)` returns:

```java
record SessionLogView(
        String sessionStatus,
        String timeRange,
        List<LogEventView> currentEvents,
        List<SavedLogView> recentSavedLogs,
        int unresolvedQuickNoteCount,
        String reviewDraft) {}

record LogEventView(
        Instant occurredAt,
        String kind,
        String title,
        String detail,
        boolean warning) {}

record SavedLogView(UUID noteId, String title, Instant createdAt, String url) {}
```

- [ ] **Step 1: Add red aggregation tests**

Construct a running session with visits, objective changes, encounter defeat/revive evidence,
quick notes, audio/presentation audit where available, and a previous `SESSION_LOG`. Assert stable
chronological ordering by timestamp then ID and no invented events.

Also test:

- idle campaign with prior logs;
- running session with no events;
- review state exposes the persisted draft;
- malformed presentation audit details become a warning row rather than failing the module;
- final defeated state matches `SessionDraftService`.

- [ ] **Step 2: Run the red log tests**

```bash
./mvnw -Dtest=SessionLogModuleServiceTest,CockpitRuntimeModuleContractTest test
```

- [ ] **Step 3: Aggregate existing evidence without a new event store**

Use existing repositories and the same final-state rules as `SessionDraftService`. Extract a
shared package-private evidence assembler if necessary; do not copy defeat/revive replay logic.
The module is read-only and adds no audit side effects.

Render:

- Compact: last three events plus unresolved-note count.
- Standard: full current-session timeline, current time range/status, unresolved notes, recent
  saved logs, and Review action.
- Focused: Standard plus persisted review draft when status is `REVIEW`.

Every row carries a readable kind label; raw JSON and internal IDs are never shown.

- [ ] **Step 4: Invalidate from semantic actions**

Scene, objective, encounter end, quick-note, presentation override, and lifecycle state changes
invalidate Session log. Hidden updates create attention. Do not refresh on every HP or turn request.

- [ ] **Step 5: Run and commit Session log**

```bash
./mvnw -Dtest=SessionLogModuleServiceTest,SessionDraftServiceTest,SessionEncounterEvidenceIntegrationTest,CockpitRuntimeModuleControllerTest,CockpitRuntimeModuleContractTest,CoreSessionLoopSmokeTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime \
  src/main/resources/templates/session/modules/_session-log.html \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): add live Session log module"
```

---

### Task 11: Remove Transitional Rendering and Prove the Ten-State Matrix

**Files:**

- Modify: `src/main/resources/templates/session/_cockpit-module-shell.html`
- Modify: `src/main/resources/templates/session/_cockpit-workbench.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Delete: `src/main/resources/templates/session/_cockpit-deferred-modules.html`
- Modify: `src/main/resources/static/css/cockpit-layout.css`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeModuleSafetyContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

- [ ] **Step 1: Add a red matrix test**

For all ten registry definitions, prove:

```text
empty
populated
loading chrome
error + retry chrome
attention while hidden
compact when supported
focused when supported
Private
Table-safe
refresh without shell replacement
```

Static assertions also require:

```java
assertThat(shell).doesNotContain("th:switch=\"${module.key}\"");
assertThat(workbench).doesNotContain("_cockpit-deferred-modules");
assertThat(Files.exists(DEFERRED_MODULES)).isFalse();
```

- [ ] **Step 2: Run the red matrix**

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,RuntimeModuleSafetyContractTest,SessionCockpitTemplateContractTest,CoreSessionLoopSmokeTest test
```

- [ ] **Step 3: Remove the B1 switch and transitional file**

Change the shell signature to `shell(module, initialBody)`. It retains only status/error chrome and
`[data-module-content]`, inserting initial content through a Thymeleaf fragment parameter:

```html
<div data-module-content
     th:attr="data-module-endpoint=${module.source.value},
              data-module-loaded=${initialBody != null},
              data-module-stale='false'">
  <th:block th:if="${initialBody != null}">
    <th:block th:insert="${initialBody}"></th:block>
  </th:block>
</div>
```

`SessionController` now adds `initialStoryView`, `initialSessionPlanView`, `initialPartyView`, and
`initialQuickNotesView`. `_cockpit-workbench.html` passes these explicit fragments:

```text
Story        → modules/_story :: body(initialStoryView, STANDARD)
Session plan → modules/_session-plan :: body(initialSessionPlanView, STANDARD)
Party        → modules/_party :: body(initialPartyView, STANDARD)
Quick notes  → modules/_quick-notes :: body(initialQuickNotesView, STANDARD)
```

Every hidden module passes `null` and starts with an empty content root. No module-key switch
remains in shared chrome.

Move all module-inner CSS from `cockpit-layout.css`/legacy `cockpit.css` to
`cockpit-modules.css`. Leave B1 grid, zone, tab, splitter, focus, dialog, and edit CSS untouched.

- [ ] **Step 4: Verify all built-ins and mode transitions**

Browser-test each built-in at 1366×768:

- expected modules load when first visible;
- hidden modules do not initialize;
- active tab and preset never change from a domain event;
- compact/focus transitions retain input/server state;
- each Table-safe preset has no visible/focusable sensitive content;
- command bar has no clipped or module-specific overflow actions.

- [ ] **Step 5: Run and commit cleanup**

```bash
./mvnw -Dtest='Cockpit*Test,SessionCockpit*Test,RuntimeModuleSafetyContractTest,CoreSessionLoopSmokeTest' test
git add src/main/resources/templates/session \
  src/main/resources/static/css/cockpit-layout.css \
  src/main/resources/static/css/cockpit-modules.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "refactor(cockpit): remove transitional module bodies"
```

---

### Task 12: Performance, Accessibility, Documentation, Rollback, and Final Gate

**Files:**

- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `docs/dm-manual/03-session-cockpit.md`

- [ ] **Step 1: Add automated performance assertions**

Measure on the populated synthetic campaign:

```java
assertThat(firstMeaningfulMs).isLessThan(2000.0);
assertThat(loadingChromeMs).isLessThan(100.0);
assertThat(moduleLoadedMs).isLessThan(2000.0);
```

Also assert:

- initial Exploration makes no Map/Encounter/Presentation/Reference/Session-log module request;
- revealing each makes one request;
- revisiting a fresh module makes no request;
- invalidating then revealing makes one request;
- no request duplicates after rapid preset switching;
- hidden Map produces no repeated draw loop;
- no console error, page error, unhandled rejection, malformed request, or unapproved non-2xx
  response occurs.

- [ ] **Step 2: Add accessibility assertions**

At 1366×768 and 1920×1080:

- every loaded fragment has one labelled module landmark;
- loading uses the shell live region without moving focus;
- Retry is keyboard reachable and restores content;
- tab/focus/splitter behavior from B1 is unchanged;
- focused mode returns to its Focus trigger;
- embedded player preview has an accessible title;
- Reference results and Party/Encounter rows have meaningful accessible names;
- Table-safe removes sensitive descendants from both visibility and sequential focus;
- reduced motion removes module transition animation.

- [ ] **Step 3: Update the DM manual**

Replace the `transitional B1 bodies` section with:

- the ten final module responsibilities;
- lazy loading, attention, and retry behavior;
- compact versus Focus behavior;
- where map, encounter, handout, presentation, reference, audio, quick-note, and session-log
  actions now live;
- exact player preview guarantees;
- no automatic preset switching;
- runtime versus Edit/Admin boundary;
- keyboard workflow for each critical module.

- [ ] **Step 4: Run the focused B2 gate**

```bash
./mvnw -Dtest='CockpitRuntimeModule*Test,CockpitModuleClientContractTest,CockpitModuleRegistryTest,RuntimeModuleSafetyContractTest,SessionCockpit*Test,SessionControllerTest,Encounter*Test,GameMap*Test,PlayerSafe*Test,PlayerViewSecurityContractTest,HandoutPreviewParityTest,QuickNoteApiControllerTest,CommandPaletteApiControllerTest,AudioCockpitSecurityTest,SessionLogModuleServiceTest,SessionDraftServiceTest,CoreSessionLoopSmokeTest' test
```

Expected: all endpoint, view assembly, runtime UI, safety, projection, state-preservation, and browser tests pass.

- [ ] **Step 5: Run a clean full build and patch hygiene**

```bash
./mvnw clean test
node --check src/main/resources/static/js/cockpit-layout.js
node --check src/main/resources/static/js/cockpit-modules.js
node --check src/main/resources/static/js/cockpit-reference.js
node --check src/main/resources/static/js/session-cockpit.js
git diff --check
git status --short
git ls-files artifacts
```

Expected: full suite green, JavaScript syntax green, no whitespace errors, only intended B2 files
changed, and no private artifact tracked.

- [ ] **Step 6: Perform the manual viewport checkpoint**

At 1366×768 and 1920×1080, each at 80%, 100%, and 125% browser zoom:

1. load Exploration and confirm useful Story/Plan/Party content before lazy modules;
2. operate scene navigation, branch, read-aloud, linked table, and encounter seed;
3. switch manually to Theatre of Mind and complete initiative setup/turn actions;
4. switch to Combat and operate map pan/zoom/token/tools without state loss;
5. focus/return Story, Encounter, Map, Party, and Presentation;
6. capture/promote a Quick note and use Reference without leaving the cockpit;
7. operate Audio and confirm its source/status;
8. present Curtain, map, safe handout, and verify exact embedded/external parity;
9. trigger one module failure and confirm every other module remains operable;
10. hide an updated module and confirm attention rather than auto-navigation;
11. open Session Review and inspect live evidence in Session log;
12. enter Table-safe in all five built-ins and keyboard-tab through every visible control;
13. confirm no document scroll, overlap, clipped command, unusable sliver, or duplicate module;
14. confirm module internals scroll independently and primary information is visually dominant.

- [ ] **Step 7: Record database migration and rollback**

B2 requires **no database migration**. It adds read-only module views, endpoints, templates, and
browser orchestration over existing authoritative data.

Rollback:

1. restore the pre-B2 application binary;
2. leave campaign/session data untouched;
3. keep B1 layout presets because module keys and schema remain compatible;
4. clear no browser keys unless diagnosing unrelated B1 transient-state corruption.

There is no Flyway rollback and no package-format change.

- [ ] **Step 8: Confirm dependency handoff**

Review B2 against corrective-spec sections 7.4, 7.7, 7.8, 11.1–11.2, 12, 13, and 14:

- ten runtime-only modules with honest responsibilities;
- typed endpoint/fragment contract and mode support;
- no lazy entity access after transaction boundaries;
- independent loading, error, retry, and attention;
- prior content retained on refresh failure;
- Story/Encounter/Map/Party/Presentation complete before utilities;
- exact server-authoritative player preview;
- heavy Map initialization only while visible and only once;
- manual presets and B1 state remain untouched;
- module-specific actions removed from global chrome;
- every module covers empty/populated/loading/error/attention/compact/focus/safety;
- viewport, keyboard, reduced-motion, and performance gates pass;
- no deep Edit/Admin embedding, new frontend framework, or private artifact.

Expected: every item maps to a green automated test or completed manual checkpoint. The next
dependency-ordered plan is **C — Operational-readiness report and campaign conversion fidelity**.

- [ ] **Step 9: Commit verification and documentation**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java \
  docs/dm-manual/03-session-cockpit.md
git commit -m "test(cockpit): verify B2 runtime modules"
```

## Final Acceptance Gate

B2 is complete only when:

- all ten registered module sources are endpoint-backed and render one valid fragment root;
- the server-rendered Exploration baseline remains useful without JavaScript;
- non-baseline modules load only on first visibility;
- one module request or render failure cannot damage the workbench or another module;
- Retry is local, preserves prior content, and cannot be overwritten by a stale request;
- Story exposes complete runtime scene content and encounter seeding;
- Encounter exposes setup, turns, HP, conditions, statblocks, and completion without state reset;
- Map creates one Konva instance and preserves map, transform, token selection, and rendering suspension;
- Party exposes compact decision state and all passive scores without Edit/Admin controls;
- Presentation shows the exact player projection and owns safe map/handout/Curtain controls;
- Session plan, Quick notes, Reference, Audio, and Session log are real modules rather than exits;
- hidden domain updates produce attention and never rearrange the workspace;
- compact and focused rendering meet the module matrix without altering consequential state;
- every built-in passes Private and Table-safe browser checks;
- first meaningful render is under two seconds, loading appears under 100 ms, and each module
  settles within two seconds on the populated synthetic campaign;
- 1366×768 and 1920×1080 at 80%, 100%, and 125% zoom have no document scroll, overlap, clipping,
  inaccessible controls, or broken module minima;
- the command bar contains only global actions plus presentation status;
- no module template depends on detached JPA entities;
- `./mvnw clean test`, JavaScript syntax checks, and `git diff --check` pass;
- no database, package format, layout schema, or private campaign artifact changed;
- the implementation is handed to Workstream C without claiming the overall all-in-one premise.
