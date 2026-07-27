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
