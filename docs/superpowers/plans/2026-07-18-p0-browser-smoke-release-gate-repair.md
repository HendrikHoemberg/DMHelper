# P0 Browser-Smoke Release-Gate Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a green automated release gate by preventing a closed party-member dialog from intercepting clicks and by running character-sheet smoke-test API setup from the application's browser origin.

**Architecture:** Keep both repairs at their actual ownership boundaries. CSS must preserve the native `<dialog>` closed-state invariant even though the legacy `.modal` class declares `display: flex`; the browser test must establish an application origin before issuing relative API requests instead of weakening server CORS. Add explicit regression assertions so future failures identify the broken invariant directly.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, HTMX 2.0.4, native `<dialog>`, CSS, JUnit 5, AssertJ, Playwright 1.54, Maven Wrapper.

## Global Constraints

- This is a release-gate repair, not a new feature slice.
- Do not add CORS configuration. The sheet API is same-origin application functionality; the failing cross-origin request originates only from test setup on `about:blank`.
- Do not change the party controller, HTMX response, or `hx-on::after-request` handler. Diagnostics confirmed that HTMX emits a successful `htmx:afterRequest` event and removes the dialog's `open` attribute.
- A closed native `dialog.modal` must be absent from layout and pointer hit testing.
- Preserve the legacy sheet overview `<div id="batch-dialog" class="modal">` behavior; scope the closed-state rule to `dialog.modal`.
- Do not add dependencies, database migrations, frontend build tooling, production API routes, or production authentication changes.
- Keep the browser failure collector strict: console errors, page errors, failed requests, and unexpected HTTP failures still fail the smoke suite.
- Replace arbitrary sleeps only where the awaited `page.evaluate(...)` promise already proves the API request completed.
- Run Maven tests with an isolated home directory under `/tmp`.

---

## Audit Basis and Root Causes

The clean-tree reproduction command was:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-plan \
  -Dtest=CoreSessionLoopSmokeTest test
```

It ran 21 tests with exactly two errors.

| Failure | Verified cause | Repair boundary |
|---|---|---|
| `quickNotesWorkOnAFirstPartyMemberInsertedByHtmx` | The HTMX form request succeeds and `dialog.close()` makes `open == false`, but the earlier `.modal { display: flex; position: fixed; inset: 0; ... }` rule overrides the browser's hidden rendering for a closed native dialog. The rendered dialog continues intercepting pointer events over the new party card. | Add a native-dialog closed-state CSS invariant and assert the locator is hidden after successful creation. |
| `sheetDetailAndLiveStateEditing` | `@BeforeEach` creates a fresh page at `about:blank`. The first absolute `fetch(http://localhost:.../sheet)` therefore has origin `null` and is correctly rejected by the browser's CORS policy. The production endpoint is not the defect. | Navigate to an application page first, then use relative same-origin API URLs for both sheet mutations. |

No database schema or production Java change is required.

## Files

### Files modified

- `src/main/resources/static/css/components.css` — ensure closed native dialogs using the shared modal class do not render or intercept input.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java` — lock the native-dialog CSS invariant.
- `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` — assert the party dialog is actually hidden and make sheet setup same-origin and promise-driven.
- `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` — replace the stale two-error release-verification note after the full suite is green.

### Files deliberately not modified

- `src/main/resources/templates/party/_form.html` — its successful HTMX close handler is working.
- `src/main/resources/templates/party/list.html` — its dialog opens correctly after the form fragment swap.
- `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java` — the create response is successful and inserts the correct card.
- Spring MVC/security configuration — no cross-origin production access is required.

---

### Task 1: Make the native party dialog truly hidden after close

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java:322`
- Modify: `src/main/resources/static/css/components.css:2222`

**Invariant:** `dialog.modal:not([open])` is `display: none`, while non-dialog elements using `.modal` retain their current behavior.

**Interfaces:**
- Consumes: the existing `hx-on::after-request` close handler on `.pm-form` and the shared `.modal` CSS class.
- Produces: the CSS selector contract `dialog.modal:not([open]) { display: none; }` and a browser assertion that `#party-form-modal` is hidden before the inserted card is used.

- [ ] **Step 1: Add a failing CSS contract test**

Add this test to `UiPolishContractTest`:

```java
@Test
void closedNativeModalsStayOutOfLayoutAndHitTesting() throws IOException {
    assertThat(read("static/css/components.css"))
            .contains("dialog.modal:not([open]) {\n  display: none;\n}");
}
```

- [ ] **Step 2: Run the focused contract and verify RED**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-dialog-red \
  -Dtest=UiPolishContractTest#closedNativeModalsStayOutOfLayoutAndHitTesting test
```

Expected: one assertion failure because `components.css` does not contain the scoped closed-dialog rule.

- [ ] **Step 3: Add the behavioral assertion to the existing browser regression**

In `quickNotesWorkOnAFirstPartyMemberInsertedByHtmx`, immediately after `card.waitFor()`, add:

```java
assertThat(dmPage.locator("#party-form-modal").isHidden())
        .as("closed party modal must leave layout and pointer hit testing")
        .isTrue();
```

Keep the subsequent quick-note fill, click, row wait, and content assertion. They remain the end-to-end proof that the inserted card is usable.

- [ ] **Step 4: Add the scoped native-dialog CSS rule**

Immediately after the later native-dialog `.modal` block in `components.css`, add:

```css
dialog.modal:not([open]) {
  display: none;
}
```

Do not delete or rewrite the earlier legacy `.modal` block in this repair. The scoped rule has sufficient specificity and does not affect the sheet overview's `<div class="modal">`.

- [ ] **Step 5: Run the CSS contract and verify GREEN**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-dialog-green \
  -Dtest=UiPolishContractTest test
```

Expected: all `UiPolishContractTest` tests pass.

- [ ] **Step 6: Run the browser class and verify the modal failure is gone**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-dialog-browser \
  -Dtest=CoreSessionLoopSmokeTest test
```

Expected at this intermediate checkpoint: `quickNotesWorkOnAFirstPartyMemberInsertedByHtmx` passes. The class still exits non-zero only because `sheetDetailAndLiveStateEditing` performs its first request from `about:blank`.

- [ ] **Step 7: Commit the native-dialog repair**

```bash
git add src/main/resources/static/css/components.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "fix: hide closed native party dialog"
```

---

### Task 2: Make sheet smoke setup same-origin and deterministic

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java:842`

**Invariant:** Browser-side API setup runs from a loaded DMHelper page and addresses application APIs with relative paths. Production CORS remains unchanged.

**Interfaces:**
- Consumes: `PUT /api/v1/campaigns/{campaignId}/party/{memberId}/sheet`, `PUT /api/v1/campaigns/{campaignId}/party/{memberId}/live-state`, and the existing DM Playwright browser context.
- Produces: a same-origin setup sequence whose returned JavaScript promises prove both mutations completed before the test proceeds.

- [ ] **Step 1: Preserve the existing RED evidence**

Before editing the method, confirm the prior Task 1 browser report contains all three signatures:

```text
from origin 'null' has been blocked by CORS policy
request failed: PUT http://localhost:
CoreSessionLoopSmokeTest.sheetDetailAndLiveStateEditing
```

This is the failing end-to-end test for the origin invariant; do not add an artificial CORS unit test.

- [ ] **Step 2: Establish the application origin before the first sheet API call**

After calculating `memberId`, navigate to the party page:

```java
dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/party");
dmPage.waitForLoadState(LoadState.NETWORKIDLE);
```

This page is campaign-scoped, authenticated through the same DM browser context, and on the same origin as the API.

- [ ] **Step 3: Convert the sheet-creation request to a relative URL**

Replace the first evaluation with:

```java
dmPage.evaluate("""
    async ([cid, mid]) => {
        const resp = await fetch('/api/v1/campaigns/' + cid + '/party/' + mid + '/sheet', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                partyMemberId: mid,
                abilityScores: {str:15,dex:14,con:13,int:10,wis:12,cha:8},
                classLevels: [{classSourceKey:'srd-2024_fighter',level:1,hitDieRolls:[]}],
                proficiencies: {skills:[],expertise:[],tools:[],languages:[]},
                xp: 0
            })
        });
        if (!resp.ok) throw new Error('Sheet creation failed: ' + await resp.text());
    }
""", Arrays.asList(campaignId.toString(), memberId.toString()));
```

Delete the following `dmPage.waitForTimeout(500);`. Playwright waits for the promise returned by the async evaluation, so the API mutation has completed when `evaluate` returns.

- [ ] **Step 4: Convert the live-state request to the same relative-URL contract**

Keep the existing navigation to the character sheet and its visible-content assertions. Replace the second evaluation with:

```java
dmPage.evaluate("""
    async ([cid, mid]) => {
        const resp = await fetch('/api/v1/campaigns/' + cid + '/party/' + mid + '/live-state', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                tempHp: 7, inspiration: false, exhaustion: 0,
                deathSaveSuccesses: 0, deathSaveFailures: 0,
                concentratingOn: null, conditionsJson: '[]'
            })
        });
        if (!resp.ok) throw new Error('Live state update failed: ' + await resp.text());
    }
""", Arrays.asList(campaignId.toString(), memberId.toString()));
```

Delete the following `dmPage.waitForTimeout(300);` for the same promise-completion reason. Keep the short-rest preview interaction unchanged.

- [ ] **Step 5: Run the browser smoke class and verify GREEN**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-sheet-green \
  -Dtest=CoreSessionLoopSmokeTest test
```

Expected: 21 tests pass with zero failures and zero errors. The browser failure collector reports no CORS console error, failed sheet request, page error, or unexpected response.

- [ ] **Step 6: Check that the repair did not introduce CORS configuration**

Run:

```bash
git diff -- src/main src/test | rg -n "Cors|CORS|allowedOrigin|Access-Control-Allow-Origin"
```

Expected: no output. A non-zero `rg` exit status is correct when no matches are found.

- [ ] **Step 7: Commit the same-origin smoke repair**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "test: run sheet smoke setup from app origin"
```

---

### Task 3: Re-open the automated readiness gate and update its evidence

**Files:**
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md:170`

**Interfaces:**
- Consumes: green focused and full Maven verification results from Tasks 1 and 2.
- Produces: an evidence-based master-spec status note without changing delivery item 11 from `IN_PROGRESS`.

- [ ] **Step 1: Run the combined focused gate from a fresh isolated home**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-focused-gate \
  -Dtest=UiPolishContractTest,CoreSessionLoopSmokeTest test
```

Expected: both test classes pass; the smoke class still reports 21 passing tests.

- [ ] **Step 2: Run the complete Maven test suite**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p0-full-gate test
```

Expected: exit code 0 with no Maven test failures or errors. Warnings about the newer H2 version, Playwright host libraries, Mockito self-attachment, or the sheet proficiency fallback are existing non-failing warnings and do not replace the exit-code requirement.

- [ ] **Step 3: Replace the stale Workstream A status note**

In master-spec §6, replace the four-line note that says the full suite has two browser-smoke errors with:

```markdown
> **Implementation status (verified 2026-07-18):** Delivery items 1–10 have shipped implementation,
> and the world-graph/faction-clock slice of item 11 is implemented. The full automated Maven suite
> is green after repairing the party native-dialog visibility regression and making sheet smoke setup
> same-origin. The remaining required P3 slices are tracked in §22.
```

Do not change delivery item 11 from `IN_PROGRESS`; tables, traps/hazards, fog, music, travel, weather, watches, navigation, and supplies are still governed by the two approved P3 designs.

- [ ] **Step 4: Run repository hygiene checks**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` exits 0. Status lists the master-spec update and, if the plan was not committed before execution began, this plan file; no source or test files remain uncommitted.

- [ ] **Step 5: Commit the verified status update**

```bash
git add docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md \
  docs/superpowers/plans/2026-07-18-p0-browser-smoke-release-gate-repair.md
git commit -m "docs: record green p0 browser gate"
```

- [ ] **Step 6: Verify the final commit set and clean tree**

Run:

```bash
git log -3 --oneline
git status --short
```

Expected: the three commits from this plan are the newest commits and `git status --short` prints nothing.

---

## Completion Criteria

- `quickNotesWorkOnAFirstPartyMemberInsertedByHtmx` proves the newly inserted party card is usable after the native dialog closes.
- `sheetDetailAndLiveStateEditing` performs both API mutations from the application origin with relative URLs and no arbitrary post-request sleeps.
- No production CORS policy, API controller, party controller, or HTMX handler changes.
- `UiPolishContractTest` protects the scoped native-dialog CSS rule.
- `CoreSessionLoopSmokeTest` passes all 21 tests with the browser failure collector clean.
- The complete Maven test suite exits 0 from a fresh isolated home.
- The master readiness spec no longer reports the repaired two-error blocker, while P3 remains honestly `IN_PROGRESS`.
- `git diff --check` passes and the worktree is clean.
