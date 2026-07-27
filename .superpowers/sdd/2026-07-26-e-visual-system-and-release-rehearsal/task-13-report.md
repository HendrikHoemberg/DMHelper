# Task 13 Report: Viewport and accessibility gate

**Status:** DONE

## TDD evidence

### RED

```bash
./mvnw -q test -Dtest=ViewportAccessibilityGateTest
```

The first run exposed two current test APIs omitted by the brief skeleton: `BrowserFailureCollector.clear()` is package-private from the new `gate` package, and `ReleaseRehearsalFixture.seed()` declares `IOException`. After adapting the gate to those APIs, the browser RED ran 9 tests and timed out in `keyboardUsersCanDriveTheWorkspace` because dirty layout edits correctly opened the existing discard dialog instead of locking directly. A subsequent gate run exposed the missing focused-layer Tab trap.

### GREEN

```bash
./mvnw -q test -Dtest=ViewportAccessibilityGateTest
```

Result: exit 0; all 9 gate tests passed sequentially. The gate covers both required viewports, document overflow, module clipping/overlap/minimums, command-bar reachability, runtime status and display-title controls, primary-zone share, keyboard layout/splitter/tab behavior, focused-layer focus trapping/restoration, visible focus, and reduced motion.

## Changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java`
- `src/main/resources/static/js/cockpit-layout.js`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-13-report.md`

The production changes are the focused-module layer’s local Tab trap and exact-opener focus restoration. No database, entity, controller, layout clamp, splitter sizing, command-bar styling, or reduced-motion end-state behavior was changed because the gate found those behaviors already passing.

## Environment warnings

- Playwright reported missing host libraries: `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`. Chromium still launched and completed the gate.
- The Maven run emitted the existing Lombok `sun.misc.Unsafe` deprecation warning and Flyway’s existing H2-version warning.
- The full `./mvnw -q test` suite was also run; it exited 1 on unrelated existing failures in `SurfaceNestingGateTest`, two `CoreSessionLoopSmokeTest` scenarios, and `GameMapControllerTest`. The focused Task 13 gate remained green in its sequential rerun.

## Fix round 1

### RED

```bash
./mvnw -q test -Dtest=ViewportAccessibilityGateTest
```

After strengthening the gate, the fresh run was red: 9 tests ran with 2 failures. The focused-layer test failed its forward-wrap assertion at 1366x768, and the command-bar test correctly exposed that the raw Screen Safety checkbox is visually hidden at 1366x768. The visible switch label was then used for viewport reachability while the underlying checkbox retained enabled/tabbable assertions.

### GREEN

```bash
./mvnw -q test -Dtest=ViewportAccessibilityGateTest
```

Result: exit 0; all 9 gate tests passed sequentially across both required viewports. The focused-layer test now checks forward wrap from the last focusable and backward wrap from the first, and all viewport-sensitive keyboard, focus, restoration, and reduced-motion checks run at 1366x768 and 1920x1080.

## Fix round 1 changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java`
- `src/main/resources/static/js/cockpit-layout.js`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-13-report.md`

The source fix captures the exact clicked module Focus button and restores focus to it after leaving the focused layer. No database, entity, controller, schema, layout-clamp, splitter-sizing, command-bar-styling, or reduced-motion source behavior was changed.

## Fix round 1 environment warnings

- Playwright reported missing host libraries: `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`; Chromium still launched and completed the focused gate.
- The Maven run emitted the existing Lombok `sun.misc.Unsafe` deprecation warning and Flyway’s existing H2-version warning.

## Fix round 2

### RED

```bash
./mvnw -q test -Dtest=ViewportAccessibilityGateTest
```

Fresh re-review reproduction was red: 9 tests ran with 1 failure in `everyFocusedLayerTrapsAndRestoresFocus`; the focused-module layer failed its initial-focus assertion in the responsive duplicate-shell path. The gate was strengthened to choose the visible opener and mark that exact DOM element, confirming the source lookup—not the assertion’s global selector—was selecting the wrong module shell.

### GREEN

```bash
./mvnw -q test -Dtest=ViewportAccessibilityGateTest
```

Result: exit 0; all 9 tests passed sequentially. `focusModule` now resolves the shell from the actual opener element, with a visible-shell fallback for programmatic callers, and restoration is asserted against the exact still-connected opener for both the session dialog and focused-module layer at 1366x768 and 1920x1080.

## Fix round 2 changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java`
- `src/main/resources/static/js/cockpit-layout.js`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-13-report.md`

No database, entity, controller, schema, or unrelated styling changes were made.

## Fix round 2 environment warnings

- Playwright reported missing host libraries: `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`; Chromium still launched and completed the focused gate.
- The Maven run emitted the existing Lombok `sun.misc.Unsafe` deprecation warning and Flyway’s existing H2-version warning.
