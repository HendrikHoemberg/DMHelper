# Task 11 report — Motion confirms, never delays

## RED evidence

Command:

```text
./mvnw -q test -Dtest=MotionBudgetContractTest
```

The first run failed as intended with 2 tests, 2 failures, and 0 errors. The contract reported
the 600 ms primary-button sheen, 400/600 ms HP-chip feedback, 700 ms HP tints, 400 ms round pulse,
the raw safety sweep and dice/book feedback durations, theatrical declarations not yet allowlisted,
and the missing exact global reduced-motion block.

## GREEN evidence

```text
./mvnw -q test -Dtest=MotionBudgetContractTest
```

Result: exit 0; 2 tests, 0 failures, 0 errors.

The required smoke test was then run sequentially:

```text
./mvnw -q test -Dtest=CoreSessionLoopSmokeTest
```

Result: exit 1 on both runs; 39 tests, 1 failure, 0 errors, 5 skipped. The reproducible failure is
`cockpitViewportGeometryHoldsAcrossSizesAndZoom` at `1366x768@1.25`, where the existing command-bar
geometry reports a clipped child. The changed paths contain no cockpit layout rules. The run also
reports the host's existing missing Playwright shared libraries.

## Changes

- Tokenized control/runtime feedback in `base.css` and `components.css` at or below
  `--duration-structural`.
- Aligned `dismissHandout` removal with the theatrical CSS duration at 550 ms.
- Replaced the two partial base reduced-motion blocks with one global 1 ms block.
- Preserved the component reduced-motion end-state suppression in `cockpit.css`,
  `cockpit-layout.css`, and `components.css`.
- Allowlisted only the campaign-cover view transition and handout unfold/fold as theatrical
  exceptions, with comments explaining why they are outside table-time controls.

## Changed paths

- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-11-report.md`
- `src/main/resources/static/css/base.css`
- `src/main/resources/static/css/components.css`
- `src/main/resources/static/js/ui-elevation.js`
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/MotionBudgetContractTest.java`

## Concerns

- `CoreSessionLoopSmokeTest` remains blocked by the repeatable, unrelated command-bar geometry
  failure described above; no cockpit or database/entity logic was changed.
- Playwright emits the existing missing-library warning for `libicudata.so.66`, `libicui18n.so.66`,
  `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`.

## Fix round 1 — review findings

### RED evidence

The contract was extended before production changes with seconds-unit parsing, duration-token
resolution, and direct regression coverage for `2s`, `1.4s`, and `--duration-structural`.

```text
./mvnw -q test -Dtest=MotionBudgetContractTest
```

Result: exit 1; 3 tests, 1 failure, 0 errors. The expected offenders were:

```text
components.css { .presenting-indicator } → 2s (2000.0ms)
components.css { .skeleton-card } → 1.4s (1400.0ms)
```

### Fix

- Changed `.presenting-indicator` and `.skeleton-card` repeated animations to
  `var(--duration-structural)`; neither was theatrical and neither was allowlisted.
- Kept the duration contract’s `ms`/`s` conversion and practical `--duration-*` token resolution.

### Parent-baseline investigation

Ran the requested smoke test in a temporary isolated worktree checked out at parent
`85d253d8`:

```text
./mvnw -q test -Dtest=CoreSessionLoopSmokeTest
```

Parent result: exit 1; 39 tests, 1 failure, 0 errors, 5 skipped. It failed at the same
`cockpitViewportGeometryHoldsAcrossSizesAndZoom` assertion for `1366x768@1.25` command-bar child
reachability. The current Task 11 checkout produced the identical result. Therefore the smoke
failure predates Task 11 and no cockpit fix is required or included in this round.

### Changed paths in fix round 1

- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-11-report.md`
- `src/main/resources/static/css/components.css`
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/MotionBudgetContractTest.java`
