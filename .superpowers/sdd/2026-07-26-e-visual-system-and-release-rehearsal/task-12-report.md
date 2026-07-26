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

## Review fix round 2

### RED

The strengthened focused test failed before the implementation change: exact participant
statblock mappings read as null from persisted scene participants, and the provenance scan
could not find the persisted encounter name and combatant kind. This demonstrated both the
association defect and the encounter-content omission.

### GREEN

```bash
./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest
./mvnw -q test -Dtest=FixtureShapeCoverageTest
```

Both commands passed sequentially. The release test now asserts exact participant rows and
statblock mappings, map and encounter metadata, exact transition labels/targets, exact quest
objectives, and campaign-scoped encounter/combatants. `textualContentOf` now includes all
campaign-scoped encounter and combatant fields alongside the existing scene, statblock,
map, handout, quest, and party surfaces.

### Round-2 changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-12-report.md`

Production sources/entities/controllers/schema were not changed.
