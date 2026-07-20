# DM-Only Readiness — Release Verification Record

**Date:** 2026-07-20
**Plan:** docs/superpowers/plans/2026-07-20-dm-readiness-release-verification.md
**Decision:** PENDING

## 1. Full automated suite (master §21)
- Command: `./mvnw -q clean test -Duser.home=/tmp/dmhelper-release-verify`
- Result: 239 suites / 1994 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS
- Smoke caveat: not triggered

## 2. Contract and round-trip gate (§21.1, §21.2)

### §21.1 Contract-test gate
- Command: `./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='CampaignManifestV2ContractTest,CampaignManifestV2SemanticValidatorTest,SchemaControllerTest,ContentDestinationRouteContractTest,ValidationErrorCatalogTest,FlywayMigrationTest,DocumentationExampleValidationTest'`
- Suites: CampaignManifestV2ContractTest (27), CampaignManifestV2SemanticValidatorTest (43), SchemaControllerTest (6), ContentDestinationRouteContractTest (1), ValidationErrorCatalogTest (2), FlywayMigrationTest (30), DocumentationExampleValidationTest (4)
- Result: 7 suites / 113 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; `-q` suppresses grepable “Tests run:” / “BUILD SUCCESS” banners — counts from Surefire / non-quiet confirmation run)
- Covers: schema examples validate, DTO/schema compatibility, catalog resolvability, destination routes, content-type coverage, v1→v2 deterministic migration

### §21.2 Three-flagship round-trip gate
- Command: `./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='CampaignCompleteRoundTripTest,CampaignImportExportRoundTripTest,CustomCompendiumPackageRoundTripTest,InventoryStateRoundTripTest'`
- Suites: CampaignCompleteRoundTripTest (8), CampaignImportExportRoundTripTest (16), CustomCompendiumPackageRoundTripTest (1), InventoryStateRoundTripTest (11)
- Result: 4 suites / 36 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; same `-q` banner note as §21.1)
- `CampaignCompleteRoundTripTest` parametrizes all five v2 fixtures (minimal, feature-complete, published-adventure-shaped, structured-adventure-quest, world-graph) through schema-validate → dry-run → import → export → re-import → semantic deep compare, including `audioCues` under history selection

### Audio-cue fixture non-vacuity
- Command: `grep -c "audioCue" src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json`
- `grep -c` line matches: feature-complete **1**, published-adventure-shaped **1** (both > 0; matches the top-level `audioCues` key)
- Actual `audioCues` array lengths: feature-complete **4** (default/scene/combat/victory — keys `cue-hall-ambience`, `cue-crypt-exploration`, `cue-crypt-combat`, `cue-crypt-victory`); published-adventure-shaped **3** (location/scene/combat — keys `pa-cue-village-calm`, `pa-cue-depths-tension`, `pa-cue-depths-combat`)
- Proves round-trip evidence is not vacuous for music

## 3. Browser gate (§21.3)

### Browser smoke (CoreSessionLoopSmokeTest)
- Command: `./mvnw -q test -Dtest=CoreSessionLoopSmokeTest -Duser.home=/tmp/dmhelper-release-verify`
- Result: 1 suite / 25 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; `-q` suppresses grepable “Tests run:” / “BUILD SUCCESS” banners — counts from Surefire)
- Covers: §21.3 quick-note create/promote, palette result types, cockpit resume from current scene and active encounter, encounter activate/end + session-log draft, curtain/map/handout presentation to the player view; music closeout offline fake-provider cue switching, victory expiry, override, confirm, mute, bounded retry, and player isolation

### Browser-failure guard (BrowserFailureCollector)
- Command: `./mvnw -q test -Dtest=BrowserFailureCollectorTest -Duser.home=/tmp/dmhelper-release-verify`
- Result: 1 suite / 3 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; same `-q` banner note as above)
- Collector source evidence (`src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java`):
  - L60–61: `page.onConsoleMessage` / `page.onPageError` record into the failure list
  - L62–65: `page.onRequestFailed` records non-`net::ERR_ABORTED` request failures
  - L87–97: console errors and HTTP status ≥ 400 (unexpected 4xx/5xx) recorded as failures
  - L99–103: `assertNoFailures()` asserts the failure list is empty (and declared expected HTTP failures were seen)
- Unit tests prove unexpected console/resource errors *fail* the guard (`unexpectedResourceConsoleErrorsStillFailTheSmokeGuard`, `declaredFailureDoesNotHideAResourceErrorFromAnotherUrl`); declared failures only suppress their matching resource console error

### Player-payload isolation
- Command: `./mvnw -q test -Dtest='PlayerViewSecurityContractTest,AudioPlayerSafetyTest' -Duser.home=/tmp/dmhelper-release-verify`
- Suites: PlayerViewSecurityContractTest (6), AudioPlayerSafetyTest (17)
- Result: 2 suites / 23 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; same `-q` banner note as above)
- Confirms DM-only scene/token/note/encounter/asset/audio data is absent from player network payloads (§21.3 final bullet; excluded at the projection boundary, not via CSS — roadmap §1)

## 4. Security gate (§21.4)
## 5. Documentation consistency audit (§19, §20)
## 6. Real-provider music exercise
## 7. Manual acceptance session (§21.5)
## 8. Observation triage
## 9. §23 readiness condition checklist
## 10. Release decision
