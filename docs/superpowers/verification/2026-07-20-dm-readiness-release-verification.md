# DM-Only Readiness — Release Verification Record

**Date:** 2026-07-20
**Plan:** docs/superpowers/archive/plans/2026-07-20-dm-readiness-release-verification.md
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

No OWASP/dependency-scanner plugin in the build; security gate = security test suites + manual review of this plan’s diff.

### §21.4 Package / asset attack-surface
- Command: `./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='AudioPackageSecurityTest,FileServeControllerSecurityTest,AudioHostileContentTest,ThreatHostileContentTest'`
- Suites: AudioPackageSecurityTest (6), FileServeControllerSecurityTest (4), AudioHostileContentTest (19), ThreatHostileContentTest (1)
- Result: 4 suites / 30 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; `-q` suppresses grepable “Tests run:” / “BUILD SUCCESS” banners — counts from Surefire)
- Covers: path traversal, absolute paths, symlinks, duplicate paths, archive bombs, MIME spoofing, oversized packages, and hostile Markdown/HTML

### §21.4 PIN / route-authorization
- Command: `./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='PinInterceptorTest,PinInterceptorRateLimitTest,SessionCockpitSecurityTest,AudioCockpitSecurityTest,MapPinAccessControlTest,AudioCredentialBoundaryTest'`
- Suites (run sequentially as separate Maven invocations so in-process PIN rate-limit state does not cross-contaminate suites): PinInterceptorTest (8), PinInterceptorRateLimitTest (3), SessionCockpitSecurityTest (5), AudioCockpitSecurityTest (7), MapPinAccessControlTest (2), AudioCredentialBoundaryTest (3)
- Result: 6 suites / 28 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS per suite (Maven exit 0 each; same `-q` banner note as above)
- Note: a single multi-class `-Dtest=` JVM can return **429** instead of **403** on later PIN-gate suites (shared rate-limit window after `PinInterceptorTest` / rate-limit tests). Isolation re-run of each suite is green; Task 1 full suite was also green. Not a product security gap.
- Covers: PIN-bypass attempts for cockpit/presentation/quest/map/audio routes, player asset access before/after presentation, and no provider credentials in cues (`AudioCredentialBoundaryTest`)

### §21.4 Atomic rollback on import failure
- Grep hits under `src/test/java/.../campaign/packagev2` for `rollback|atomic|staged|no partial|leaves no`: includes `CampaignImportAtomicityTest`, `CampaignImportPreviewStoreTest`, `CampaignImportCoordinatorTest`, `CampaignPackageReaderTest`, `CampaignPackageValidationPipelineTest`, etc.
- Brief wildcards `*ImportRollback*,*AtomicImport*,*PackageImportPreview*` match **no** Surefire class names; real class used: **`CampaignImportAtomicityTest`** (plus related preview/coordinator coverage)
- Command: `./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='CampaignImportAtomicityTest,CampaignImportPreviewStoreTest,CampaignImportCoordinatorTest'`
- Suites: CampaignImportAtomicityTest (2), CampaignImportPreviewStoreTest (2), CampaignImportCoordinatorTest (1)
- Result: 3 suites / 5 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS (Maven exit 0; same `-q` banner note)
- `CampaignImportAtomicityTest` forces failure after every section-adapter boundary and on deferred setters; asserts campaign rows, package keys, domain table counts, and handout files roll back, and that the import preview remains retryable

### Manual boundary review of this plan’s diff
- Command: `git diff --stat Main...HEAD` (default branch is `Main`, capital M)
- Stat at Task 4 recording:
  - `docs/superpowers/dm-only-readiness-roadmap.md` (M) — status/NEXT bookkeeping only
  - `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (A) — verification record only
- **Confirmation:** plan branch introduces **docs only** — no new runtime route, no new asset endpoint, no player-payload change, no production Java/HTML/JS/CSS. (Plan will later add a docs-only contract test and more docs in subsequent tasks; none of those are production attack surface.) Code security-review skill not required for a verification-only docs diff.

## 5. Documentation consistency audit (§19, §20.1)

### Baseline doc-contract gate (pre-fix)
- Command: `./mvnw test -Duser.home=/tmp/dmhelper-release-verify -Dtest='DmManualContractTest,DocsIndexContractTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,AgentGuideContractTest,DocumentationExampleValidationTest,ThreatDocumentationContractTest'`
- Suites: AgentGuideContractTest (3), CapabilityManifestContractTest (5), CapabilityMatrixMarkdownSyncTest (1), DmManualContractTest (3), DocsIndexContractTest (5), DocumentationExampleValidationTest (4), ThreatDocumentationContractTest (8)
- Result: 7 suites / 29 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS

### Manual drift scan
- Command: `grep -niE "not started|remaining required|planned|todo|in progress|coming soon" docs/product/release-notes.md docs/campaign-capabilities.md docs/README.md`
- Hits:
  - `docs/product/release-notes.md:25` `## Not started` — section heading (kept; still has optional deferred items)
  - `docs/product/release-notes.md:27` `Atmosphere/music is the remaining required P3 feature package.` — **stale** (roadmap row 6 shipped atmosphere/music)
- Additional audit hit (broader scan of capability notes): `docs/campaign-capabilities.md` World graph notes still said “music remains the active readiness slice” — **stale**
- `docs/README.md`: no matching stale hits

### Regression guard (TDD)
- Added `src/test/java/dev/hendrikhoemberg/dmhelper/agent/ReleaseNotesConsistencyTest.java`
- RED (before doc fix): `./mvnw test -Dtest=ReleaseNotesConsistencyTest -Duser.home=/tmp/dmhelper-release-verify` → **Tests run: 2, Failures: 1** — `atmosphereMusicIsNoLongerDescribedAsUnstarted` failed; BUILD FAILURE
- GREEN (after doc fix): same command → **Tests run: 2, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

### Docs edited
- `docs/product/release-notes.md` — added Delivery Item 11 atmosphere/music bullet; removed stale “remaining required P3” Not started line
- `docs/campaign-capabilities.md` — removed “music remains the active readiness slice” from World graph notes (status unchanged: `SUPPORTED`)
- No changes required to `docs/agent/verification-checklist.md` or `docs/README.md`

### Full doc-contract gate (post-fix, includes guard)
- Command: `./mvnw test -Duser.home=/tmp/dmhelper-release-verify -Dtest='DmManualContractTest,DocsIndexContractTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,AgentGuideContractTest,DocumentationExampleValidationTest,ReleaseNotesConsistencyTest'`
- Suites: ReleaseNotesConsistencyTest (2), AgentGuideContractTest (3), CapabilityManifestContractTest (5), CapabilityMatrixMarkdownSyncTest (1), DmManualContractTest (3), DocsIndexContractTest (5), DocumentationExampleValidationTest (4)
- Result: 7 suites / 23 tests / 0 failures / 0 errors / 0 skips — BUILD SUCCESS

### Residual notes (out of Task 5 edit list; not blocking this gate)
- `docs/product/known-limitations.md` still lists Atmosphere/music under UNSUPPORTED as “not implemented yet”
- `src/main/resources/agent/capability-manifest.json` World graph notes still say “music remains required readiness work” (status already `SUPPORTED` for Atmosphere & music; matrix status sync is green)

## 6. Real-provider music exercise

### Exercise script
- Path: [`docs/superpowers/verification/live-music-provider-exercise.md`](live-music-provider-exercise.md)
- Scope: six provider controls, runtime-policy checks (visibility/intersection, user-gesture, DM-surface-only IFrame), and provider-failure isolation against the real YouTube IFrame API on the DM cockpit (Firefox/Linux).

### YouTube policy recheck (official docs)
- **Checked date:** 2026-07-20
- **URLs checked:**
  - https://developers.google.com/youtube/iframe_api_reference
  - https://developers.google.com/youtube/terms/required-minimum-functionality (page last updated 2026-04-28 UTC)
  - https://developers.google.com/youtube/terms/api-services-terms-of-service (page last updated 2026-04-28 UTC)
- **Findings (not blocking):**
  - **Player size:** embedded players must be ≥ 200×200 CSS px; 16:9 recommended minimum remains **480×270**. A visible official player at 480×270 stays compliant.
  - **Visibility / autoplay:** required-minimum-functionality still forbids initiating automatic/scripted playback until the player is visible and **more than half** of the player is on-screen; at most one autoplaying player per page/screen; no overlays obscuring the player or controls.
  - **Gesture / browser autoplay:** IFrame API still surfaces `onAutoplayBlocked` when the browser blocks autoplay or scripted playback (e.g. unmuted playback without user interaction). Intended design (user-gesture enable, then scripted control while visible) remains aligned.
  - **Client identity:** RMF requires a non-suppressed `HTTP Referer` (or equivalent client identification). IFrame API `onError` code **153** (documented 2025-07-09) covers missing Referer/client identity — relevant for WebView-style hosts; normal Firefox/HTTP(S) cockpit origin continues to send Referer.
  - **Terms:** API Services ToS still permits official embedded-player use under the Agreement / RMF / developer policies. **No policy change found that prohibits** DM-cockpit-only, gesture-gated, visible official IFrame use at 480×270.
- **Policy gate status:** **CLEAR** (not blocking). Does not substitute for the live DM-device exercise.

### Live exercise result
- **Status:** **AWAITING_DM_RUN**
- Agent cannot exercise a real streaming provider on the DM's device (human-in-the-loop). DM must execute `live-music-provider-exercise.md` and return the filled Result block for transcription here.
- Date/browser/OS: _(pending DM run)_
- All controls: **AWAITING_DM_RUN**
- Policy checks: **AWAITING_DM_RUN**
- Failure isolation: **AWAITING_DM_RUN**
- Blocking observations: none from policy recheck; live run not yet performed.

## 7. Manual acceptance session (§21.5)

### Template
- Path: [`docs/superpowers/verification/manual-acceptance-session-template.md`](manual-acceptance-session-template.md)
- Scope: representative ~4-hour session from a converted synthetic adventure; DMHelper as the only campaign tool; log every forced context switch, missing datum, broken link, and manual duplication.

### Setup notes (for the DM)
- Import `feature-complete.dmcampaign` (or an equivalent converted synthetic adventure) via package import; dry-run must show zero ERROR.
- Enter through the session cockpit (the normal runtime entry point).
- During play: prep → run → record loop (activate scenes, ≥2 encounters, ≥1 rollable table, ≥1 trap/hazard, curtain/map/handout to player view); music cue switches; party HP/conditions/rest; ≥3 quick notes + one promote + palette find; mid/late export → restore → resume.

### Session result
- **Status:** **AWAITING_DM_SESSION**
- Agent cannot run a real four-hour DM session (human-in-the-loop). DM must execute the template and return the filled observation log and Result block for transcription here.
- Session length: _(pending DM session)_
- Had to open another campaign tool? _(pending DM session)_
- Blocking observations count: _(pending DM session)_
- Convenience observations count: _(pending DM session)_
- Observation log: none recorded yet (session not run).

## 8. Observation triage

### Pending observations
| Time | Type | Description | Blocking? | Disposition |
|------|------|-------------|-----------|-------------|
| _(none)_ | — | Zero observations recorded so far; session not yet run. | — | — |

### Blocking vs convenience (§21.5 rule)
- **Blocking** (forced context switch to another campaign tool, missing datum that stalled play, broken link, or lost/duplicated state): **0** recorded. Any blocking observation from the completed session withholds the release and must be filed as a release issue (`gh issue create` or listed here if `gh` is unavailable) before Task 8.
- **Convenience** (non-blocking UX friction): **0** recorded. Listed separately when returned; triaged for later, not release-blocking.
- No issues invented; no `gh` issues filed from this gate yet (nothing to file until the DM returns the filled log).

## 9. §23 readiness condition checklist
## 10. Release decision
