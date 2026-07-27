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
