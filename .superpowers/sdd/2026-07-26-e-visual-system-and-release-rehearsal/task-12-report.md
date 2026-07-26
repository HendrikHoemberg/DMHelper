# Task 12 Report: Session-ready synthetic campaign

**Status:** DONE

## TDD evidence

### RED

```bash
./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest
```

Result: exit 1 during test compilation because `ReleaseRehearsalFixture` did not exist.

### GREEN

```bash
./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest
```

Result: exit 0; all five fixture tests passed.

### Shape coverage

```bash
./mvnw -q test -Dtest=FixtureShapeCoverageTest
```

Result: exit 0; all shape coverage tests passed.

## Changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-12-report.md`

Production sources/entities/controllers were not changed.

## Concerns

- The current `StatBlock` API has no initiative-modifier field; the fixture preserves initiative-related data through dexterity scores and encounter-ready statblock links without changing production schema.
- The fixture uses one-pixel synthetic PNGs and writes handout storage through the existing service, as required by the current API.

## Review fix round 1

### RED

Expanded `ReleaseRehearsalFixtureTest` first failed with two genuine contract failures:
campaign-scoped statblock lookup returned zero because the fixture had not assigned its
statblocks to the campaign, and provenance text omitted the persisted scene, quest,
handout, map, statblock, and party fields.

### GREEN

```bash
./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest
./mvnw -q test -Dtest=FixtureShapeCoverageTest
```

Both commands passed sequentially after the fix. The release test now covers exact hostile
participant/map/transition/quest/derivative contracts, complete scoped provenance text,
campaign-scoped IDs, and repeatability. The shape test preserves the populated-fixture
profile checks and adds campaign-scoped assertions for `ReleaseRehearsalFixture`.

### Round-1 changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-12-report.md`

### Design note

The release fixture intentionally keeps a direct synthetic content spine rather than
consuming `PopulatedCampaignFixture`: composing it would import published-shaped names
and prose into the campaign being checked by the provenance gate. The shape gate now
executes both fixtures independently, preserving the existing populated-fixture coverage
while verifying the release fixture against its own campaign ID.
