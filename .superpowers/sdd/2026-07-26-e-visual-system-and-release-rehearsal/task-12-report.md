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

## Review fix round 3

### RED

The strengthened `ReleaseRehearsalFixtureTest` failed before the fixture change: it
referenced a missing deterministic statblock-ID accessor, and the two-campaign provenance
test found null encounter keys and combatant notes. The RED run exited 1.

### GREEN

```bash
./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest
```

Result: exit 0 after adding campaign-local statblock source keys and exact participant and
combatant ID assertions, plus unique persisted encounter/combatant markers used to prove
campaign isolation.

```bash
./mvnw -q test -Dtest=FixtureShapeCoverageTest
```

Result: exit 0 when run sequentially after the focused release test.

The provenance helper scans the relevant persisted campaign-scoped encounter and combatant
fields; this report does not claim exhaustive serialization of every entity field.

### Round-3 changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-12-report.md`

Production sources/entities/controllers/schema were not changed.
