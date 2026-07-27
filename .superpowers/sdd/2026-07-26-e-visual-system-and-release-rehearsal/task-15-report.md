# Task 15 report: second campaign shape

## RED evidence

- Added `BRANCHED_TWO_MAPS` fixture tests first and ran `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest`.
- The run failed at the intended missing-feature boundary: `BRANCHED_TWO_MAPS` and `mapFreeHostileSceneCount` were unresolved.

## GREEN evidence

- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest` passed after implementing the second shape and exact graph/objective/wave assertions.
- `./mvnw -q test-compile` passed.
- `./mvnw -q test -Dtest=ReleaseRehearsalTest` was retried with local-socket permission. Tomcat started, but Playwright stalled after reporting unavailable Chromium host libraries; browser assertions could not execute.

## Changed paths

- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java` — added `BRANCHED_TWO_MAPS`, two chapters, three-way branching, a 48px second map, map-free hostile scene, two-wave encounter, third quest objective, and transactional map-free scene count.
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java` — added RED tests and exact branched fixture assertions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java` — moved the ten ordered steps into reusable nested linear/branched shape runs and added second-branch/wave assertions.

No production schema, entity, or controller files changed. Linear fixture behavior remains unchanged.

## Environment warnings

- Playwright reports missing `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`.
- The ordinary sandbox also rejected Tomcat’s random socket with `java.net.SocketException: Operation not permitted`; the escalated retry passed that boundary.
- Maven emits the existing Flyway/H2 compatibility warning and JVM sharing warning.

## Review round 1 evidence

### RED

- Added exact BRANCHED_TWO_MAPS assertions before implementation: two chapter identities and their scene associations, all three transition labels/target IDs, exactly one hostile map-free scene, no pre-linked ambush encounter, and a separate two-wave reserve encounter.
- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest` failed with `NoSuchElementException` while looking for the not-yet-implemented `Lantern Vault Reserve` wave encounter.

### GREEN / focused verification

- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest` passed after moving the wave encounter out of the scene link and adding the hostile-disposition filter.
- `./mvnw -q test-compile` passed.
- Exact outer command: `./mvnw -q test -Dtest=ReleaseRehearsalTest`. The run reached the Linear Spring/Playwright launch, emitted the missing Chromium-library warning, and was stopped before a test summary; no 20-test green claim is made because browser execution is unavailable here.
- Explicit selector: `./mvnw -q test -Dtest='ReleaseRehearsalTest$Branched'`. The run reached the Branched Spring/Playwright launch and the same missing-library boundary, then was stopped; no browser assertion claim is made.

### Round-1 changes

- Branched fixture now leaves `Lantern Vault Ambush` unlinked so the existing scene-to-encounter action is required. The rehearsal performs that action, then creates the reserve wave as its second allowed action and asserts the two-action limit.
- The reserve encounter remains synthetic and campaign-scoped with `main=ACTIVE` and `vault-reinforcements=PENDING`; it is intentionally not linked to the scene before runtime seeding.
- `mapFreeHostileSceneCount` now requires at least one `HOSTILE` participant and the fixture test requires exactly one result.
- Added deterministic outer/nested test-class ordering and PER_CLASS lifecycle metadata for the two shape runs.

### Environment limits

- Playwright reports missing `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`; browser assertions and a trustworthy 20-test rehearsal summary require those host dependencies.
- No production schema, entity, controller, or linear fixture behavior changed.

## Review round 2 evidence

### RED

- Strengthened `BRANCHED_TWO_MAPS` fixture assertions before removing the stale reserve fixture: the exact campaign encounter set had to be `Undercroft Alarm` only, while the old fixture still produced `Lantern Vault Reserve` as a second encounter.
- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest` failed at that assertion with the actual names `[Lantern Vault Reserve, Undercroft Alarm]` versus the expected `[Undercroft Alarm]`.

### GREEN / focused verification

- `./mvnw -q test-compile` passed.
- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest` passed after removing the separate branched reserve encounter. The fixture now proves exact chapter identities/associations, exact three-way target IDs/labels, exactly one hostile map-free scene, and no pre-linked ambush encounter or out-of-scope reserve encounter.
- Branched rehearsal step 4 now uses the scene seed action, then the existing encounter wave and library-combatant APIs to create `vault-reinforcements` with the named `Vault reinforcements` combatant on `Encounter: Lantern Vault Ambush`; this is counted as the second action.
- Branched step 5 fetches that same campaign encounter, defeats every main-wave combatant, waits for `main=DEPLETED`, activates the real wave-banner spawn control, and asserts `vault-reinforcements=ACTIVE` with exactly one visible named combatant.

### Browser checks and environment limit

- Exact outer command: `./mvnw -q test -Dtest=ReleaseRehearsalTest`. Spring/Tomcat started and the run entered the Linear nested class, but Playwright stopped at host validation; it did not produce a test summary or a green 20-test result.
- Explicit branched command: `./mvnw -q test -Dtest='ReleaseRehearsalTest$Branched'`. Spring/Tomcat started and the run entered the Branched nested class, then hit the same Playwright host validation boundary; no branched browser result is claimed.
- Chromium remains blocked by missing `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`.
- No production schema, entity, or controller files changed; linear rehearsal behavior was preserved.

## Review round 3 evidence

### RED

- The review identified a Linear-path regression in step 5: the shared browser evaluation resolved `vault-reinforcements` and dereferenced `pending.id` before the shape conditional. The Linear fixture contains only `main`, so this path fails before Linear’s existing damage/condition/turn assertions.
- The focused outer selector, `./mvnw -q test -Dtest=ReleaseRehearsalTest`, reached the `Linear` nested run but could not execute the browser assertions because Chromium host validation stopped the run. The reported source path was therefore fixed directly rather than claiming a browser RED/green result that this environment cannot provide.

### GREEN / focused verification

- Common step 5 now fetches only `main` combatant IDs. The pending `vault-reinforcements` lookup, missing-wave guard, spawn, and spawned-wave assertions are all inside the `BRANCHED_TWO_MAPS` branch.
- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest` passed.
- `./mvnw -q test-compile` passed.
- Outer selector: `./mvnw -q test -Dtest=ReleaseRehearsalTest` reached the `Linear` nested shape, then stopped at Chromium host validation; no false green claim is made.
- Explicit selector: `./mvnw -q test -Dtest='ReleaseRehearsalTest$Branched'` reached the `Branched` nested shape, then stopped at the same Chromium host validation boundary.
- The outer class still statically contains both ordered nested shape runs, `Linear` and `Branched`, each inheriting all ten ordered rehearsal steps.

### Environment warning

- Playwright cannot launch Chromium because the host is missing `libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`.
- No production, schema, entity, controller, or linear fixture files were changed.
