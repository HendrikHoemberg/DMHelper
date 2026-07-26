# Task 10 report

## RED evidence

Command:

```text
./mvnw -q test -Dtest=RuntimeStatusSurfaceTest
```

The first run stopped at test compilation because the brief used the pre-Spring-Boot-4
`org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` import. The
repository's existing tests use `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`,
so the test was corrected to the local convention and rerun.

The corrected RED then failed at compilation as expected because `TableStateWebSocketHandler`
did not yet expose `connectedCount()`.

## GREEN evidence

The focused test report recorded 4 tests, 0 failures, 0 errors:

```text
./mvnw -q test -Dtest=RuntimeStatusSurfaceTest
```

The required contract tests were run sequentially and completed with zero failures/errors:

```text
./mvnw -q test -Dtest=CockpitWorkbenchTemplateContractTest
./mvnw -q test -Dtest=RuntimeModuleSafetyContractTest
git diff --check
```

## Changed paths

- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-10-report.md`
- `src/main/java/dev/hendrikhoemberg/dmhelper/live/TableStateWebSocketHandler.java`
- `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/TableConnectionStatusController.java`
- `src/main/resources/static/js/runtime-status.js`
- `src/main/resources/static/css/cockpit-layout.css`
- `src/main/resources/templates/session/cockpit.html`
- `src/main/resources/templates/fragments/head.html`
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusSurfaceTest.java`

## Concerns

- The cockpit owns its own `<head>` rather than consuming `fragments/head.html`, so the status
  script is included in both locations. The shared fragment inclusion supports pages that use it;
  the direct cockpit inclusion is required for the implemented cluster to run.
- The cluster reports application state only and intentionally has no `data-screen-sensitive`
  classification. Existing HTMX events and non-GET save filtering remain unchanged.
- No database, entity, or unrelated controller behavior was added; `/api/table/status` only
  exposes the current WebSocket session count.
