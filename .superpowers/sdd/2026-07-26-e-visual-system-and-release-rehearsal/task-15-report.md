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
