# DM-Only Readiness — Final Verification and Release Decision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the master §21 verification strategy end to end, record attributable evidence, and — only if every gate passes — make the §23 all-in-one DM readiness release decision by flipping roadmap row 7 and master delivery item 11 to complete.

**Architecture:** This is the terminal work package (roadmap row 7). It adds almost no product code. It runs the existing automated gates (contract, round-trip, browser, security), performs a documentation-consistency audit, drives two human-in-the-loop exercises (a real-provider music exercise and a recorded four-hour manual acceptance session), aggregates all evidence into one durable verification report, and then either makes or withholds the readiness claim strictly on the evidence. Any blocking observation stops the release decision and files issues instead.

**Tech Stack:** Java 21 / Spring Boot, Maven (`./mvnw`), Thymeleaf + HTMX templates, JUnit 5, Playwright-driven browser smoke (`CoreSessionLoopSmokeTest`), Flyway migrations, YouTube IFrame API (DM-side music provider).

## Global Constraints

- Authority order (roadmap §2): master spec §§21, 23 > this roadmap's execution order > this plan. If they disagree, the design wins; correct the label.
- **Full automated suite command:** `./mvnw -q clean test -Duser.home=/tmp/dmhelper-release-verify`. The isolated `user.home` protects the developer's persistent database and startup-backup behavior (master §20.5); never run the full suite against the real home.
- **Known environmental caveat:** `CoreSessionLoopSmokeTest` may intermittently time out under load. If and only if it times out, re-run it in isolation (`./mvnw -q test -Dtest=CoreSessionLoopSmokeTest`) and record that it passes standalone. Only a *non-environmental* failure blocks the release.
- Product boundary (roadmap §1): DM-only; player surface stays anonymous, read-only, presentation-only. Do not add player accounts, player tokens, player rolling, or player sheet editing. Do not reintroduce fog or structured travel as a readiness dependency.
- Non-invention rule (master §4, §24): do not invent rules, campaign facts, or source details. If a datum is missing, record it as a gap; never fabricate it.
- Roadmap single-active invariant (roadmap §3): only one row may be `READY`/`PLANNING`/`IN_PROGRESS`/`VERIFYING` at a time. Move row 7 to `VERIFYING` while this plan runs and to `COMPLETE` only after its exit gate is demonstrated.
- Every documentation example is an executable contract (master §19, §20.1). A doc fix is not done until the relevant contract test is green.
- Commit message trailer for every commit:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- The readiness claim (§23) may be marked complete only when **every** §23 condition, the full automated suite, the security gate, the documentation audit, the real-provider music exercise, and the manual acceptance session all pass. A single unresolved blocking observation withholds the claim.

## What "already implemented" means for this plan

Roadmap rows 1–6 are `COMPLETE` and were spot-verified before this plan was written: the audio module, migrations V15–V17, provider registry, cue/assignment services, `dm-manual` chapter 09, capability matrix (`Atmosphere & music = SUPPORTED`), and both flagship fixtures with `audioCues` are present, and a focused audio gate (`AudioProviderRegistryTest`, `AudioCueResolverTest`, `AudioPlayerSafetyTest` = 60 tests) is green. This plan does **not** re-implement those; it verifies the assembled whole and decides the release.

## File Structure

New/modified files, by responsibility:

- Create: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md`
  — the durable release-decision record: per-gate evidence, the live-music exercise log, the manual-acceptance session log, the observation triage, and the final decision. This is the artifact §21.5 requires.
- Create: `docs/superpowers/verification/manual-acceptance-session-template.md`
  — the reusable four-hour session checklist and observation-logging template (§21.5).
- Create: `docs/superpowers/verification/live-music-provider-exercise.md`
  — the scripted real-provider (YouTube) exercise checklist and evidence record.
- Modify: `docs/product/release-notes.md`
  — move atmosphere/music out of "Not started"; add the Item 11 music entry and the readiness-verified statement (documentation-audit fix).
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/ReleaseNotesConsistencyTest.java`
  — a lightweight contract guard so the release-notes drift cannot silently return.
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`
  — §22 delivery table: item 11 → `IMPLEMENTED`, P3 matrix all `IMPLEMENTED`; annotate the readiness claim as verified (only in the release-decision task).
- Modify: `docs/superpowers/dm-only-readiness-roadmap.md`
  — row 7 status transitions, `Current NEXT item`, `Last updated`, and a §7 recovery-note bullet with the recorded counts.
- Modify (only if the audit finds drift): `docs/campaign-capabilities.md`, `docs/agent/verification-checklist.md`, and/or `docs/README.md`.

Directory `docs/superpowers/verification/` is new; create it in Task 1.

---

### Task 1: Baseline confirmation and full automated suite gate (§21 overall, §20.1)

**Files:**
- Create: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: the verification report file with a "1. Full automated suite" section holding recorded suite/test counts that later tasks append to.

- [ ] **Step 1: Confirm a clean, expected baseline**

Run:
```bash
git status --short && git log -3 --oneline
```
Expected: clean working tree; HEAD at the music-closeout commits (`f4879f4` era). If the tree is dirty with unrelated user changes, stop and preserve them (roadmap §6.2) before continuing.

- [ ] **Step 2: Move roadmap row 7 to `VERIFYING`**

In `docs/superpowers/dm-only-readiness-roadmap.md`, change row 7's `Status` cell from `READY` to `VERIFYING` and set `Last updated` to `2026-07-20`. Verify the single-active invariant:
```bash
grep -nE "^\| 7 \|" docs/superpowers/dm-only-readiness-roadmap.md
grep -cE "\| (READY|PLANNING|IN_PROGRESS|VERIFYING) \|" docs/superpowers/dm-only-readiness-roadmap.md
```
Expected: row 7 shows `VERIFYING`; exactly one active-status row total.

- [ ] **Step 3: Run the complete automated suite in an isolated home**

Run:
```bash
./mvnw -q clean test -Duser.home=/tmp/dmhelper-release-verify 2>&1 | tee /tmp/dmhelper-release-verify/full-suite.log | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. Baseline reference from the closeout run was **239 suites / 1994 tests / 0 failures / 0 errors / 0 skips**; the new run must be ≥ that with 0 failures/0 errors (skips only where already documented).

- [ ] **Step 4: Handle the smoke-test environmental caveat**

If Step 3 failed *only* on `CoreSessionLoopSmokeTest` timeouts, re-run it alone:
```bash
./mvnw -q test -Dtest=CoreSessionLoopSmokeTest -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS` standalone. Record that it passed in isolation. Any other failure is a blocking release issue — stop and file it (do not proceed to Task 8).

- [ ] **Step 5: Create the verification report and record suite evidence**

Create `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` with this skeleton, filling the real counts from Steps 3–4:
```markdown
# DM-Only Readiness — Release Verification Record

**Date:** 2026-07-20
**Plan:** docs/superpowers/plans/2026-07-20-dm-readiness-release-verification.md
**Decision:** PENDING

## 1. Full automated suite (master §21)
- Command: `./mvnw -q clean test -Duser.home=/tmp/dmhelper-release-verify`
- Result: <N suites / N tests / 0 failures / 0 errors / N skips> — BUILD SUCCESS
- Smoke caveat: <not triggered | re-run standalone PASS>

## 2. Contract and round-trip gate (§21.1, §21.2)
## 3. Browser gate (§21.3)
## 4. Security gate (§21.4)
## 5. Documentation consistency audit (§19, §20)
## 6. Real-provider music exercise
## 7. Manual acceptance session (§21.5)
## 8. Observation triage
## 9. §23 readiness condition checklist
## 10. Release decision
```

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/dm-only-readiness-roadmap.md docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(verification): open release verification; full suite green

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Contract and round-trip attribution (§21.1, §21.2)

**Files:**
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (section 2)

**Interfaces:**
- Consumes: a green full suite from Task 1.
- Produces: attributable evidence that the §21.1 contract tests and the three-flagship §21.2 round-trip deep-compare pass.

- [ ] **Step 1: Run the contract-test gate**

Run:
```bash
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='CampaignManifestV2ContractTest,CampaignManifestV2SemanticValidatorTest,SchemaControllerTest,ContentDestinationRouteContractTest,ValidationErrorCatalogTest,FlywayMigrationTest,DocumentationExampleValidationTest' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. These cover §21.1: schema examples validate, DTO/schema compatibility, catalog resolvability, destination routes, content-type coverage, and v1→v2 deterministic migration.

- [ ] **Step 2: Run the three-flagship round-trip gate**

Run:
```bash
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='CampaignCompleteRoundTripTest,CampaignImportExportRoundTripTest,CustomCompendiumPackageRoundTripTest,InventoryStateRoundTripTest' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. `CampaignCompleteRoundTripTest` parametrizes all five v2 fixtures (minimal, feature-complete, published-adventure-shaped, structured-adventure-quest, world-graph) through schema-validate → dry-run → import → export → re-import → semantic deep compare, including `audioCues` under history selection — this satisfies §21.2's flagship requirement.

- [ ] **Step 3: Confirm the audio-cue fixtures actually carry music into the round-trip**

Run:
```bash
grep -c "audioCue" src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json
```
Expected: both counts > 0 (feature-complete: 4 cues default/scene/combat/victory; published-adventure: 3 cues location/scene/combat). This proves the round-trip evidence is not vacuous for music.

- [ ] **Step 4: Record and commit**

Fill section 2 of the report with the two command results and the fixture cue counts. Then:
```bash
git add docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(verification): record contract and round-trip gate

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Browser gate attribution (§21.3)

**Files:**
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (section 3)

**Interfaces:**
- Consumes: a green full suite.
- Produces: attributable evidence that the browser smoke path passes with console/page-error and unexpected-4xx/5xx guards active, and that DM-only data is absent from player payloads.

- [ ] **Step 1: Run the browser smoke gate**

Run:
```bash
./mvnw -q test -Dtest=CoreSessionLoopSmokeTest -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS` (baseline: 1 suite / 25 tests). This exercises §21.3: quick-note create/promote, palette result types, cockpit resume from current scene and active encounter, encounter activate/end + session-log draft, curtain/map/handout presentation to the player view, and — per the music closeout — offline fake-provider cue switching, victory expiry, override, confirm, mute, bounded retry, and player isolation.

- [ ] **Step 2: Confirm the browser-failure guard is real, not disabled**

Run:
```bash
./mvnw -q test -Dtest=BrowserFailureCollectorTest -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
grep -n "consoleError\|pageError\|4[0-9][0-9]\|5[0-9][0-9]\|requestfailed" src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java | head
```
Expected: `BUILD SUCCESS`, and the collector visibly fails the test on console errors, page errors, failed requests, and unexpected 4xx/5xx (§21.3 requirement that these *fail* the test).

- [ ] **Step 3: Confirm player-payload isolation**

Run:
```bash
./mvnw -q test -Dtest='PlayerViewSecurityContractTest,AudioPlayerSafetyTest' -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. Confirms DM-only scene/token/note/encounter/asset/audio data is absent from player network payloads (§21.3 final bullet; excluded at the projection boundary, not via CSS — roadmap §1).

- [ ] **Step 4: Record and commit**

Fill section 3 with the three results. Then:
```bash
git add docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(verification): record browser gate

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Security gate attribution (§21.4, §20.4)

**Files:**
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (section 4)

**Interfaces:**
- Consumes: a green full suite.
- Produces: attributable evidence for every §21.4 security dimension. (Note: the build has no OWASP/dependency-scanner plugin, so the "security gate" is the security test suite plus a manual review of any diff this plan introduces — a dependency scanner would be a separate future decision, out of scope here.)

- [ ] **Step 1: Run the package/asset attack-surface gate**

Run:
```bash
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='AudioPackageSecurityTest,FileServeControllerSecurityTest,AudioHostileContentTest,ThreatHostileContentTest' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. Covers §21.4: path traversal, absolute paths, symlinks, duplicate paths, archive bombs, MIME spoofing, oversized packages, and hostile Markdown/HTML.

- [ ] **Step 2: Run the PIN / route-authorization gate**

Run:
```bash
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='PinInterceptorTest,PinInterceptorRateLimitTest,SessionCockpitSecurityTest,AudioCockpitSecurityTest,MapPinAccessControlTest,AudioCredentialBoundaryTest' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. Covers §21.4 PIN-bypass attempts for new routes, player asset access before/after presentation, and confirms no provider credentials leak into cues (audio credential boundary).

- [ ] **Step 3: Confirm atomic-rollback coverage**

Run:
```bash
grep -rln "rollback\|atomic\|staged\|no partial\|leaves no" src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 | head
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='*ImportRollback*,*AtomicImport*,*PackageImportPreview*' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: at least one class matches and passes, demonstrating §21.4 atomic rollback on failures at each import stage. If no class name matches, use the `grep` hit to name the real class and re-run; if genuinely uncovered, record it as a blocking gap.

- [ ] **Step 4: Manual boundary review of this plan's diff**

Run:
```bash
git diff --stat main...HEAD
```
Confirm this plan introduces only docs + one test file, i.e. no new runtime route, no new asset endpoint, no player-payload change. Record the confirmation. (This substitutes for a code security review because the package is verification-only; if any production route were added, invoke the `security-review` skill on the diff before recording.)

- [ ] **Step 5: Record and commit**

Fill section 4 with all results and the diff confirmation. Then:
```bash
git add docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(verification): record security gate

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Documentation consistency audit (§19, §20.1) — with regression guard

**Files:**
- Modify: `docs/product/release-notes.md`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/ReleaseNotesConsistencyTest.java`
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (section 5)
- Modify (only if audit finds drift): `docs/campaign-capabilities.md`, `docs/agent/verification-checklist.md`, `docs/README.md`

**Interfaces:**
- Consumes: nothing beyond the repository.
- Produces: a corrected `release-notes.md` guarded by `ReleaseNotesConsistencyTest`, and a recorded audit that all doc-contract tests pass. `ReleaseNotesConsistencyTest` asserts the release notes do not still describe atmosphere/music as unstarted.

- [ ] **Step 1: Run the documentation contract tests (capture current state)**

Run:
```bash
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='DmManualContractTest,DocsIndexContractTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,AgentGuideContractTest,DocumentationExampleValidationTest,ThreatDocumentationContractTest' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS` (these are already green; they prove manual/agent/catalog/capability docs are self-consistent). Record the result.

- [ ] **Step 2: Manual cross-read for the one known and any further drift**

Confirmed drift to fix: `docs/product/release-notes.md` still lists under **Not started** — *"Atmosphere/music is the remaining required P3 feature package."* That is false after roadmap row 6. Also scan for other stale claims:
```bash
grep -niE "not started|remaining required|planned|todo|in progress|coming soon" docs/product/release-notes.md docs/campaign-capabilities.md docs/README.md
```
Record every hit and whether it is now stale.

- [ ] **Step 3: Write the failing guard test first**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/agent/ReleaseNotesConsistencyTest.java`:
```java
package dev.hendrikhoemberg.dmhelper.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseNotesConsistencyTest {

    private static final Path RELEASE_NOTES = Path.of("docs/product/release-notes.md");

    @Test
    void atmosphereMusicIsNoLongerDescribedAsUnstarted() throws Exception {
        String text = Files.readString(RELEASE_NOTES);
        assertThat(text)
                .as("atmosphere/music shipped in roadmap row 6; release notes must not list it as remaining")
                .doesNotContain("Atmosphere/music is the remaining required P3 feature package");
    }

    @Test
    void deliveryItem11RecordsTheMusicSlice() throws Exception {
        String text = Files.readString(RELEASE_NOTES);
        assertThat(text)
                .as("Item 11 must record the shipped atmosphere/music slice")
                .containsIgnoringCase("music");
    }
}
```

- [ ] **Step 4: Run the guard test to verify it fails**

Run:
```bash
./mvnw -q test -Dtest=ReleaseNotesConsistencyTest -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: FAIL on `atmosphereMusicIsNoLongerDescribedAsUnstarted` (the stale line is still present).

- [ ] **Step 5: Fix `release-notes.md`**

In `docs/product/release-notes.md`, under `## Delivery Item 11`, add:
```markdown
- **Item 11 (Atmosphere and music):** DM-side streaming music behind a provider SPI with a reference/fake adapter and a YouTube adapter; a campaign cue library; a cockpit playback widget; per-campaign cue assignments; deterministic scene/encounter/location priority switching with manual override, per-campaign confirmation, per-session mute, victory expiry, and bounded provider-outage retry; package-v2 dependency-closure round-trip; player-payload isolation; flagship-fixture cues; DM manual chapter 09; authoring reference; agent cue-mapping and non-invention rules; and hostile-content, credential-boundary, and PIN gates.
```
Then in the `## Not started` section, delete the line:
`- Atmosphere/music is the remaining required P3 feature package.`
Fix any additional stale lines found in Step 2 the same way (do not invent new claims — only reflect what the repository proves).

- [ ] **Step 6: Run the guard test to verify it passes**

Run:
```bash
./mvnw -q test -Dtest=ReleaseNotesConsistencyTest -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: PASS (2 tests).

- [ ] **Step 7: Re-run the full doc-contract gate**

Run:
```bash
./mvnw -q test -Duser.home=/tmp/dmhelper-release-verify -Dtest='DmManualContractTest,DocsIndexContractTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,AgentGuideContractTest,DocumentationExampleValidationTest,ReleaseNotesConsistencyTest' 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS`. Record the audit result (including which docs were edited) in section 5.

- [ ] **Step 8: Commit**

```bash
git add docs/product/release-notes.md src/test/java/dev/hendrikhoemberg/dmhelper/agent/ReleaseNotesConsistencyTest.java docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(release-notes): reflect shipped atmosphere/music; guard against drift

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Real-provider (YouTube) live music exercise

**Files:**
- Create: `docs/superpowers/verification/live-music-provider-exercise.md`
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (section 6)

**Interfaces:**
- Consumes: the running application and the DM device's real network/YouTube access. This gate is owned by row 7 (row 2 proved feasibility offline; row 7 owns live release acceptance).
- Produces: a recorded live-exercise log confirming all six provider controls work against the real YouTube IFrame API on the DM surface, with provider-failure isolation observed.

> **USER ACTION REQUIRED (human-in-the-loop):** an agent cannot exercise a real streaming provider on the DM's device. This task creates the scripted checklist; the DM runs it and reports results; the agent transcribes them.

- [ ] **Step 1: Write the live-exercise script**

Create `docs/superpowers/verification/live-music-provider-exercise.md`:
```markdown
# Live Music Provider Exercise — YouTube (DM surface)

**Environment:** Firefox/Linux, official YouTube IFrame API, DM cockpit only.
**Precondition:** app running via the normal cockpit entry point; a campaign with ≥2 audio cues whose `providerReference` points at real, playable YouTube content.

## Controls (all must pass)
- [ ] Assign a cue and start playback from the cockpit widget; official player visible at 480×270.
- [ ] Pause and resume.
- [ ] Switch to a second cue (scene/combat change) and confirm the track changes.
- [ ] Volume/mute from the widget.
- [ ] Victory/override cue takes priority, then expires back to the prior cue.
- [ ] Per-session mute silences playback without unassigning cues.

## Runtime-policy checks
- [ ] Scripted playback is gated on document visibility and >50% intersection (no hidden autoplay).
- [ ] Playback is user-gesture initiated.
- [ ] The IFrame API is loaded only on the DM surface (open the player view; confirm no player/embed request).

## Provider-failure isolation
- [ ] Disconnect network mid-session: the rest of the cockpit (scene, tracker, notes, presentation) stays fully usable; the widget shows a bounded-retry/degraded state and never blocks the session.
- [ ] Reconnect: playback can be resumed manually.

## Result
- Date/browser/OS:
- All controls: PASS / FAIL (detail)
- Policy checks: PASS / FAIL (detail)
- Failure isolation: PASS / FAIL (detail)
- Blocking observations:
```

- [ ] **Step 2: Re-check current official YouTube requirements**

Because provider policy is time-sensitive (roadmap §6), browse current official YouTube IFrame API documentation and confirm: gesture-gated playback, embedding terms, and that a visible official player at 480×270 remains compliant. Record the checked date and any change. If policy now prohibits the intended use, that is a blocking finding — record it and stop before Task 8.

- [ ] **Step 3: DM runs the exercise and reports**

The DM executes `live-music-provider-exercise.md` and returns the filled Result block. Transcribe it verbatim into section 6 of the verification report.

- [ ] **Step 4: Record and commit**

```bash
git add docs/superpowers/verification/live-music-provider-exercise.md docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(verification): add live music provider exercise and record results

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: Recorded representative manual acceptance session (§21.5)

**Files:**
- Create: `docs/superpowers/verification/manual-acceptance-session-template.md`
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (sections 7 and 8)

**Interfaces:**
- Consumes: a converted synthetic adventure (import the `feature-complete.dmcampaign` fixture as the representative campaign) and a real DM running a session.
- Produces: a recorded four-hour session log, an observation list, and a blocking/convenience triage. Blocking observations withhold the release.

> **USER ACTION REQUIRED (human-in-the-loop):** §21.5 is a real four-hour DM session run without opening another campaign-reference tool. The agent builds the template and triages results; the DM runs the session.

- [ ] **Step 1: Write the session template**

Create `docs/superpowers/verification/manual-acceptance-session-template.md`:
```markdown
# Manual Acceptance Session Template (master §21.5)

**Goal:** run a representative ~4-hour session from a converted synthetic adventure using DMHelper as the only campaign tool. Log every forced context switch, missing datum, broken link, and manual duplication.

**Setup**
- [ ] Import `feature-complete.dmcampaign` (or an equivalent converted synthetic adventure) via package import; dry-run shows zero ERROR.
- [ ] Enter through the session cockpit (the normal runtime entry point).

**During play — log each occurrence with timestamp**
- [ ] Prep → run → record loop: activate scenes, run at least two encounters, roll on ≥1 rollable table, trigger ≥1 trap/hazard card, present curtain/map/handout to the player view.
- [ ] Music: assign and switch cues across scene/combat/victory as the session demands.
- [ ] Party/sheets: apply HP/conditions/rest to ≥1 character.
- [ ] Notes/search: capture ≥3 quick notes and promote one; find content via the palette.
- [ ] Export mid/late session, restore from the export, and resume — confirm current state survives.

**Observation log (append rows)**
| Time | Type (context-switch / missing-datum / broken-link / manual-dup / other) | Description | Blocking? |
|------|--------------------------------------------------------------------------|-------------|-----------|

**Result**
- Session length:
- Had to open another campaign tool? (Y/N — if Y, why):
- Blocking observations count:
- Convenience observations count:
```

- [ ] **Step 2: DM runs the session and returns the filled log**

The DM runs the session and returns the completed template. Transcribe the observation log into section 7 of the verification report.

- [ ] **Step 3: Triage observations (§21.5 rule)**

In section 8, split observations: every **blocking** observation (forced context switch to another campaign tool, missing datum that stalled play, broken link, or lost/duplicated state) becomes a release issue; **convenience** observations are listed separately and triaged for later, not blocking.

For each blocking observation, file it:
```bash
# example — adapt title/body per observation
gh issue create --title "Readiness blocker: <short>" --body "From §21.5 manual acceptance session 2026-07-20: <detail>"
```
If `gh` is unavailable, record the blocker list in section 8 and stop before Task 8.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/verification/manual-acceptance-session-template.md docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(verification): record manual acceptance session and triage

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: Release decision and status finalization (§22, §23)

**Files:**
- Modify: `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` (sections 9, 10)
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` (§22 tables)
- Modify: `docs/superpowers/dm-only-readiness-roadmap.md` (row 7, header, §7)

**Interfaces:**
- Consumes: green Tasks 1–7 evidence and an empty blocking-observation list.
- Produces: the recorded release decision. Item 11 and the DM-only readiness claim flip **only** if every gate passed.

- [ ] **Step 1: Gate check — decide GO or NO-GO**

In section 9, fill the §23 checklist explicitly, one line per condition, each citing the report section that proves it:
```markdown
- [ ] P0 and P1 gates complete — full suite §1
- [ ] every required DM-only P3 slice complete — §2, §3
- [ ] session cockpit is the normal runtime entry point — §3, §7
- [ ] feature-complete campaign round-trips without unreported semantic loss — §2
- [ ] agent can author against versioned schemas/catalogs/examples/errors — §5
- [ ] non-SRD dependencies representable as custom content with provenance — §2, §5
- [ ] representative campaign prepared/run/logged/exported/restored/resumed without external references — §7
- [ ] capability matrix honestly marks optional/unsupported subsystems — §5
- [ ] player-safe projection and package-security tests pass — §3, §4
- [ ] full automated suite and manual acceptance session pass release criteria — §1, §7
```
**Decision rule:** if any box is unchecked or any blocking observation is open, set `Decision: NO-GO`, record the open items, do **not** perform Steps 2–4, leave roadmap row 7 as `VERIFYING`, and stop. Otherwise set `Decision: GO`.

- [ ] **Step 2: (GO only) Flip the master delivery status**

In `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` §22:
- Change delivery-item-11 row `IN_PROGRESS` → `IMPLEMENTED`.
- In the P3 child-slice matrix, confirm all three slices read `IMPLEMENTED` (world graph/clocks; tables + traps/hazards; atmosphere/music).
- Append one sentence after the matrix noting readiness verification is recorded in `../verification/2026-07-20-dm-readiness-release-verification.md` and the manual acceptance session passed on 2026-07-20. Do not alter the §23 definition text; it is satisfied, not rewritten.

- [ ] **Step 3: (GO only) Close roadmap row 7**

In `docs/superpowers/dm-only-readiness-roadmap.md`:
- Row 7 `Status`: `VERIFYING` → `COMPLETE`; replace the "Create a dated ... plan" cell with `[Completed plan](plans/2026-07-20-dm-readiness-release-verification.md)`.
- `Current NEXT item`: change to `— (all readiness work packages complete)`.
- `Last updated`: `2026-07-20`.
- Add a §7 recovery-note bullet dated 2026-07-20 recording the final suite counts, that the documentation audit fixed the release-notes drift, and that the live-music exercise and §21.5 manual acceptance session both passed with zero open blockers.

- [ ] **Step 4: (GO only) Verify the roadmap invariant and set the decision**

Run:
```bash
grep -cE "\| (READY|PLANNING|IN_PROGRESS|VERIFYING) \|" docs/superpowers/dm-only-readiness-roadmap.md
```
Expected: `0` active rows (every package complete). Set `Decision: GO` at the top of the verification report and fill section 10 with the one-paragraph rationale citing sections 1–9.

- [ ] **Step 5: Final full-suite re-run after the doc edits**

Run:
```bash
./mvnw -q clean test -Duser.home=/tmp/dmhelper-release-verify 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE"
```
Expected: `BUILD SUCCESS` including the new `ReleaseNotesConsistencyTest` and the doc-contract tests reading the edited files. This proves the status flip did not break a documentation contract.

- [ ] **Step 6: Commit the release decision**

```bash
git add docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md docs/superpowers/dm-only-readiness-roadmap.md docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md
git commit -m "docs(readiness): record final verification and DM-only readiness decision

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 7: Offer branch finishing**

The readiness program is complete. Invoke the `superpowers:finishing-a-development-branch` skill to present merge/PR/cleanup options for the verification branch.

---

## Self-Review

**Spec coverage (master §21, §23):**
- §21.1 contract tests → Task 2 Step 1. §21.2 three-flagship round-trip → Task 2 Steps 2–3. §21.3 browser tests → Task 3. §21.4 security tests → Task 4. §21.5 manual acceptance session → Task 7. Full automated suite → Task 1 + Task 8 Step 5. §19/§20 documentation audit → Task 5. Real-provider music exercise (roadmap row 7 exit gate) → Task 6. §22 status flip + §23 readiness claim → Task 8. All covered.

**Placeholder scan:** No "TBD/TODO/handle edge cases" left as work items. The `<...>` tokens are explicitly evidence to be transcribed from real command output and human reports, not unwritten logic. Human-in-the-loop tasks (6, 7) are marked USER ACTION REQUIRED because a real streaming provider and a four-hour session genuinely cannot be simulated by the agent — the agent's deliverable in those tasks (the scripts/templates and the triage) is fully specified.

**Type consistency:** `ReleaseNotesConsistencyTest` is defined once (Task 5) and referenced with the same name in Tasks 5, 8. The verification report path `docs/superpowers/verification/2026-07-20-dm-readiness-release-verification.md` and its section numbers (1–10) are used consistently across all tasks. Roadmap row-7 transitions are monotonic: `READY`→`VERIFYING` (Task 1) →`COMPLETE` (Task 8), never skipping.

**Guardrails honored:** Task 8's decision rule makes the readiness claim conditional on zero open blockers; a NO-GO path is specified so the plan cannot rubber-stamp a failing release. The isolated `user.home` and the smoke-test caveat appear in every full-suite command.
