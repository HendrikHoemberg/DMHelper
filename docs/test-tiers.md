# Test tiers: what `./mvnw test` runs, and what it doesn't

**Status: introduced 2026-08-02.**

The suite is split in two. The default command runs the JVM tier only; the browser gates are
opt-in behind a profile.

| Command | Runs | Wall | Tests |
|---|---|---|---|
| `./mvnw test` | JVM tier | 77s | 2,331 |
| `./mvnw test -P gates` | everything | 193s | 2,574 |

Both green as of the change that introduced this document (2026-08-02). The full suite
was 257s / 2,518 before it; the extra test is net of two moved off the browser tier and
one merged into it, so no assertion was dropped at that time.

**Updated 2026-08-04.** The appearance gates were consolidated into a single
overflow gate (`gate/ViewportAccessibilityGateTest`); 14 style and render gates
were deleted and `CockpitPresetSwitchingBrowserTest` was added, taking the browser
tier from 30 classes to 17. Visual design requirements remain in force as design
intent — they are no longer machine-enforced.

## Why

The 17 Playwright classes are 9% of the tests and 60% of the wall clock — 193s full
against 77s without. That cost is paid on every task in a multi-task plan, where the browser
gates almost never have anything to say about the template that just changed. Splitting the
tiers makes the edit loop cheap without deleting a single assertion (the 2026-08-04
consolidation above is a separate, deliberate reduction).

## The rules

- **Every class that calls `Playwright.create()` carries `@Tag("browser")`.** `excludedGroups`
  only excludes what is tagged, so a gate that forgets the tag does not go missing — it runs
  in *both* tiers, and the edit loop quietly gets its browser cost back. The symptom is a
  slower `./mvnw test`, not a silent gap in coverage. There is no guard for this yet; it is on
  the reviewer.
- **The exclusion also applies to `-Dtest=`.** Selecting a browser class by name still needs
  the profile:

  ```sh
  ./mvnw -P gates -Dtest='ShellRenderGateTest' test    # runs
  ./mvnw -Dtest='ShellRenderGateTest' test             # selects it, then the tag excludes it
  ```

- **Run `-P gates` at stage boundaries, before any merge, and in CI.** The JVM tier is for the
  edit loop, not for signing anything off.

## What belongs in which tier

A browser earns its place when the assertion needs a layout engine or real event dispatch:
horizontal overflow, element geometry, computed styles, focus traps, `Escape` handling,
canvas. Everything else — element counts, attribute values, rendered text, archetype and
surface wiring — is in the server's response body, and MockMvc plus jsoup asserts it in
milliseconds. `web/ShellCompositionContractTest` is the worked example: it took two tests off
`gate/ShellRenderGateTest` that between them cost 38 page loads to read markup no script ever
touches.

Note the one asymmetry. `/library` leaves ten result panes empty and fills them from
`hx-trigger="load"`, so its composed DOM does not exist in the response body — that page keeps
a browser assertion (`theLibraryPageStillComposesCleanlyOnceItsPanesHaveLoaded`).

## Navigation in browser gates

Use `support.PageReady.open(page, base, path)`. Do not call `waitForLoadState(NETWORKIDLE)`
directly.

`NETWORKIDLE` is defined as half a second of network silence, so it bills 500ms to every
navigation whether or not the page has anything left to fetch. `ShellRenderGateTest` makes 90
navigations; that wait was essentially its entire runtime. `PageReady` waits for `LOAD` —
still after the document and its subresources, which is what geometry and computed-style
measurements need — and spends the extra half-second only on the two route families that
genuinely are not finished when the document is:

- `/library`, which defers ten panes over `hx-trigger="load"`
- `**/session`, where the cockpit composes module geometry in JavaScript after
  `DOMContentLoaded`

Measured effect, all classes green across repeated runs:

| Class | Before | After |
|---|---|---|
| `gate/ShellRenderGateTest` | 66.3s | 10.4s |
| `visual/TypographyRenderGateTest` | 13.6s | 2.9s |
| `gate/VisualFoundationRenderGateTest` | 9.6s | 2.7s |
| `visual/SurfaceNestingGateTest` | 6.2s | 2.3s |

`CoreSessionLoopSmokeTest`, `ReleaseRehearsalTest`, `MapEditorBrowserTest`,
`ViewportAccessibilityGateTest`, `OverlayBehaviorGateTest` and the cockpit browser tests were
deliberately left on `NETWORKIDLE`. They drive the cockpit and the map editor through
interactions, where the fixed wait may be doing real settling work that nobody wrote down; a
speedup there is not worth reintroducing flake into the gates that guard the session loop.
