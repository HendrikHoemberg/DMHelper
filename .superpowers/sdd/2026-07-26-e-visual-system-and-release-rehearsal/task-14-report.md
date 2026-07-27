# Task 14 report: release rehearsal

## RED evidence

- The first `./mvnw -q test -Dtest=ReleaseRehearsalTest` run failed during test compilation because the prescribed test is in the `...gate` package while `BrowserFailureCollector.clear()` was package-private.
- After exposing that test-fixture reset method, the test compiled and reached Spring Boot/Playwright startup. The browser run could not reach selector assertions because the environment cannot launch Chromium.

## GREEN evidence

- `./mvnw -q -DskipTests test-compile` passed.
- `./mvnw -q test -Dtest=SessionCockpitTemplateContractTest,RuntimeStatusSurfaceTest,CombatLegibilityContractTest,BrowserFailureCollectorTest` passed.
- `./mvnw -q test -Dtest=ReleaseRehearsalTest` did not complete: Playwright reported missing host libraries (`libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, `libffi.so.7`), and the available system Chromium traps with exit 133. No rehearsal assertion was weakened.

## Changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java` — ten ordered Playwright rehearsal steps with `BrowserFailureCollector` and carried-forward scene, combatant, and encounter values for final log assertions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java` — made the existing reset operation public so the gate-package test can clear its collector between steps.
- `src/main/resources/templates/campaigns/detail.html`
- `src/main/resources/templates/encounter/_tracker.html`
- `src/main/resources/templates/session/_lifecycle-dialog.html`
- `src/main/resources/templates/session/_story-rail.html`
- `src/main/resources/templates/session/modules/_presentation.html`
- `src/main/resources/templates/session/modules/_reference.html`

The template changes add only the prescribed `data-*` hooks to existing controls. No database, schema, player-safety projection, Run routing, session/encounter behavior, or HTMX action was changed. No runtime defect could be honestly confirmed or fixed without a functioning browser; the rehearsal remains the release-gate evidence needed after the host dependencies are installed.

## Environment warnings

- Playwright host dependency validation warns that the six shared libraries above are unavailable.
- `/usr/bin/chromium --headless --no-sandbox ...` terminates with a sandboxed-environment `Trace/breakpoint trap` (exit 133).
- Maven also emits the existing Lombok `sun.misc.Unsafe` deprecation warning and Flyway’s H2-version compatibility warning.

## Review round 1 evidence

### RED

- Revised `ReleaseRehearsalTest` was run before the round-1 source correction. It compiled, started Spring Boot, and then stopped at the reproducible Playwright host-dependency warning/hang.
- A sequential ordinary-sandbox run also reproduced `java.net.SocketException: Operation not permitted` while Tomcat attempted to bind the random test port. The same command was retried with escalation; Tomcat then started, but Playwright still stopped at the missing-library boundary.

### GREEN / focused verification

- `./mvnw -q -DskipTests test-compile` passed after the round-1 test changes.
- `./mvnw -q test -Dtest=SessionCockpitTemplateContractTest,RuntimeStatusSurfaceTest,CombatLegibilityContractTest,BrowserFailureCollectorTest` passed.
- The full sequential rehearsal could not reach its browser assertions in this environment; no assertion was weakened to disguise that limitation.

### Round-1 changes

- Step 2 now preserves the Run navigation semantics, starts the seeded campaign through the existing `SessionLifecycleService.start` contract, reloads the cockpit, and asserts `RUNNING` state before later ordered steps.
- Step 3 reads the actual scene title element; the existing `data-current-scene` hook moved from the card container to that title.
- Step 7 promotes the captured quick note through the existing quick-note promotion API as a `SESSION_PLAN`, reloads the cockpit, and asserts the plan title is rendered by the session-plan module.
- Step 5 checks an existing encounter condition checkbox and asserts the persisted checked state alongside damage, defeat, and turns.
- Step 8 uses `selectOption` on the owning handout picker, completes the existing preview flow, asserts the preview asset and HANDOUT mode, and verifies `/player` renders the selected safe asset with no DM source ID or screen-sensitive markup.
- Step 9 enters the existing generated review title/body before completion.
- Step 10 locates the generated `SESSION_LOG` card in the real notes list, opens its note detail, and asserts the actual `.note-body` content for time zone, visited scene title, defeated combatant, quick-note text, and encounter name.

Round 1 changed only the rehearsal test and the prescribed scene-title hook; no database/entity/schema changes were made.

## Review round 2 evidence

### RED

- Removed the injected lifecycle start and ran `./mvnw -q test -Dtest=ReleaseRehearsalTest` before the round-2 implementation. The test compiled and Spring Boot started, but Playwright could not launch because the environment lacks `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`.
- The RED test now asserts the UI-reported `RUNNING` state immediately after the existing lifecycle dialog Start action, so it will fail on a broken UI start route rather than injecting state.

### GREEN / focused verification

- `./mvnw -q -DskipTests test-compile` passed after the round-2 test changes.
- `./mvnw -q test -Dtest=SessionCockpitTemplateContractTest,RuntimeStatusSurfaceTest,CombatLegibilityContractTest,BrowserFailureCollectorTest` passed sequentially after the round-2 changes.
- The corrected `ReleaseRehearsalTest` was rerun with escalated socket permission; Tomcat started, then the run stopped at the same Playwright host-dependency/browser-launch boundary. No browser assertion was weakened.

### Round-2 changes

- Step 2 now uses only the existing Run link followed by the existing Session lifecycle dialog Start button, then asserts the rendered `data-session-status="RUNNING"`. No lifecycle service is injected or called by the rehearsal.
- Step 5 reloads the cockpit after the condition mutation, reopens the combatant detail, and asserts the condition remains checked after server-backed encounter state is reloaded.

Round 2 made no player-safety, routing, database, schema, or entity changes.
