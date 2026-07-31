# Test suite triage: the 50 source-reading test files

**Status: executed 2026-07-31.** Full suite green afterwards — 2,445 tests, 0 failures,
0 errors, 6 skipped, 3:03 min.

| Measure | Before | After |
|---|---|---|
| Test files | 376 | 349 |
| Test LOC | 68,341 | 64,121 |
| Declared `@Test` methods | 2,702 | 2,389 |
| Files reading `src/main/…` off disk | 50 | 21 |
| Substring assertions in those files | 1,054 | 81 |
| Substring assertions, whole suite | 1,720 | 748 |
| Share of all substring assertions concentrated in source-readers | **61%** | **11%** |

## Revisions made during execution

Five verdicts changed once the code was in front of me. Recorded here because the per-file
sections below describe the original proposal.

1. **`session/RuntimeStatusSurfaceTest` — proposed trim, actually deleted.** I expected
   MockMvc assertions based on its `jsonPath`/`status` imports. Those imports are dead: the
   class boots a full `@SpringBootTest` context, never touches the autowired `mvc`, and both
   its tests are file greps.
2. **`config/InteractionFailureContractTest` — cut 1 method, not 2.** Its tail contains
   `errorSurfacesNeverLeakAccessCredentials` and `errorSurfacesNeverContainRawProviderResponse`,
   which scan the audio JS for leaked `accessToken`/`refreshToken`/`deviceId`. Those are
   absence-of-bug scans and stay. Only the exact-error-string test was removed.
3. **`config/ControlConsistencyContractTest` — kept more than planned.** Its
   `noSurfaceOffersTwoPrimaryActions` rule is real and survives; `fieldErrorsUseTheSharedFragment`
   was trimmed to its structural half (ad-hoc `th:errors` outside the shared fragment) rather
   than deleted.
4. **`DestructiveActionContractTest.theSeparatorIsARealVisualGap` — rewritten, not deleted.**
   It now asserts that `.action-row__destructive` creates horizontal separation at all,
   instead of pinning exact `margin-left`/`padding-left`/`border-left` values.
5. **`config/ElevationModelContractTest` — kept.** It was on the first cut list before I read
   it; it is a sound structural test.

## The rewrite found a real bug

`SurfaceSeparationContractTest.readAndRunSurfacesCarryNoDestructiveActionInTheirHeader` was
verified by injecting a `btn-danger` into `adventure/detail.html`'s page header, behind a
nested `<div>`:

- **Old algorithm: PASS.** Its slice ran from `page-header-actions` to the *first* `</div>`,
  which was the nested div's — 56 characters, ending before the danger button.
- **New algorithm: FAIL**, reporting
  `html > body > div.app-shell > main.app-main > div.page-header > div.page-header-actions > button.btn.btn-danger`.

A destructive control could sit in a read/run page header with this gate green. The
injection was reverted after the check.

## Why this document exists

50 test files read `src/main/...` off disk and assert on the text. They hold **409 test
methods and 1,054 `contains`/`doesNotContain` calls — 61% of every substring assertion in
the suite**, in 15% of the files.

That concentration is where the "every change fights the tests" friction comes from. It is
not a volume problem. The suite runs in ~3 minutes and the test:source ratio is 1.21:1,
both unremarkable. The problem is that a large minority of these assertions are a
*transcript of the current markup*, re-imposed as a requirement. They fail on every
legitimate change and cannot fail when the page is actually broken, because they never
render anything.

Reading all 50 changed the picture from my first estimate. They are not uniform. Some are
the best tests in the repository.

## Verdicts

| Bucket | Files | Meaning |
|---|---|---|
| **Keep** | 13 | Real invariant, sound mechanism. Do not touch. |
| **Trim** | 9 | Real invariant plus brittle extras. Delete named methods only. |
| **Rewrite** | 2 | Real invariant, broken mechanism. Same intent, parse instead of grep. |
| **Delete** | 26 | 3,931 LOC, 295 test methods. |

The discriminator that matched my per-file reading almost exactly: **does the file use any
analysis technique at all?** Files that parse (Jsoup), model CSS (`CssRules`), drive a
browser (Playwright), or apply structural regex are nearly all worth keeping. Files that
only call `Files.readString` and `.contains(...)` are nearly all worth deleting.

---

# Keep (13)

### `config/CssRules.java` — infrastructure, not a test
A 130-line flat CSS reader that answers "which selector declares this property?" — which
grepping cannot answer, and which would otherwise need a new dependency. Every good design
test in the repo is built on it. This is the asset that makes "rewrite" cheap.

### `config/HtmxTemplateExpressionTest.java`
The highest-value test in this entire set. Catches `hx-get="@{...}"` missing the `th:`
prefix — Thymeleaf never evaluates it, the raw template string leaks into the HTML, and
htmx issues it verbatim as a URL, producing a runtime 404. Also catches literal `[[${...}]]`
and `/*[[${...}]]*/` without `th:inline="javascript"`. Three whole classes of silent
production bug, found structurally, with no coupling to any specific page.

### `common/TemplateFragmentPrecedenceContractTest.java`
`th:replace` (precedence 100) runs before `th:if` (precedence 300), so a conditional on the
same element is silently dead and the fragment always renders. One regex, whole-tree scan,
catches a footgun that is invisible in review.

### `config/ElevationModelContractTest.java`
Asserts the `--z-*` ladder is defined in strictly ascending order, and that no stylesheet or
template invents a raw `z-index` outside it (local `0..2` stacking allowed). Structural,
zero markup coupling. *I had this on my cut list before reading it — that was wrong.*

### `campaign/service/validation/ImportProblemCodesCoverageTest.java`
Scans all of `src/main/java` for emitted problem-code literals and asserts they are a subset
of the registry. A real coverage invariant that nothing else can enforce.

### `common/config/PersistenceProfileParityTest.java`
27 lines asserting test config sets `spring.jpa.open-in-view=false` like production, so
integration tests actually expose detached-entity failures. Prevents a whole category of
"passes in test, fails in prod".

### `common/config/FlywayLegacyUpgradeTest.java`
Real Spring/DB migration test. Reads migration files incidentally.

### `campaign/packagev2/catalog/CampaignCatalogServiceTest.java`
Real Spring service test. In this list only incidentally.

### `session/FailureSignallingBrowserTest.java`
Playwright. Measures real behaviour.

### `session/RuntimeStatusJavascriptContractTest.java`
Playwright. Measures real behaviour.

### `party/web/PartyRosterTest.java`
Renders through MockMvc and asserts on **rendered output**, not template source. This is the
correct pattern — the assertions are about what a DM sees on a roster row (AC, HP, passives,
conditions), which survives a restyle. The `substring`-based row extraction is clumsy but not
load-bearing.

### `session/ConditionDefaultsTest.java`
Asserts all 15 catalogue conditions default to indefinite duration, and that the loader does
not coerce a server-supplied duration back to 1 round. Real data-integrity rule about combat
behaviour, extracted by structural regex.

### `support/PackageShapeProfileExtractor.java`
Test-support helper for package shape profiling. Not a test.

---

# Trim (9) — keep the file, delete the named methods

### `config/DesignTokenContractTest.java`
- **Keep** `everyTokenReferencedByAStylesheetIsDefined`, `everyTokenReferencedByATemplateIsDefined` — a `var()` that resolves to nothing silently inherits, so the surface looks styled while carrying no decision. Excellent.
- **Delete** `semanticColorsKeepTheirValues` — hardcodes `#a83a32`, `#7fa05f`, `#d9993d`, `#966a9e`, `#8a9aa5`. The redesign changes these by design.
- **Delete** `warningWaveTitleContrastsWithItsWarningSurface` — greps for an exact inline `style="..."` string in one template.

### `config/TypeScaleContractTest.java`
- **Keep** all four structural tests: no absolute `font-size` outside `tokens.css`; runtime surfaces stay at or above `--text-sm` unless allowlisted; no inline `font-size`; no raw `font-size` in embedded `<style>`. The reviewed `SECONDARY_METADATA` allowlist is a legitimate maintenance point — updating it is a deliberate act, which is the point.
- **Delete** `replacementClassesKeepReviewedTemplateTypography` — pins three named selectors to three specific tokens.

### `config/DestructiveActionContractTest.java`
- **Keep** the Jsoup adjacency check and its **two self-tests on fixtures**. Testing the checker against a crafted fixture is good design and rare here. A misclick mid-combat is a deleted encounter.
- **Keep** `destructiveButtonsAreDistinctFromEverythingElse` (asserts a semantic token, not a hex).
- **Delete** `theSeparatorIsARealVisualGap` — pins exact `margin-left`/`padding-left`/`border-left` values.

### `config/CombatLegibilityContractTest.java`
- **Keep** `everyCombatNumeralIsTabularAndAtLeastTextBase` — via `CssRules`; tabular figures on HP and initiative is a real legibility rule.
- **Delete** `theTrackerRowShowsANumericHpReadout` (greps `class="combatant-hp u-num"`) and `hpLabelHandlesMissingMaximumsWithoutPrintingNull` (greps an exact JS line). The second guards real behaviour — move it to a JS-level or rendered assertion rather than a source grep.

### `config/ControlConsistencyContractTest.java`
- **Keep** `everyButtonInAGovernedTemplateDeclaresItsRole` plus its self-test `roleGuardRejectsMadeUpControlClasses`. Every `<button>` carrying a known control class is what stops a surface shipping unstyled.
- **Delete** the `theReadinessReportIsStyled`-style methods that grep `surfaces.css` for specific declarations.

### `config/InteractionFailureContractTest.java`
- **Keep**, rewritten off substrings: mutation paths must use `window.dmRequest` / `window.reportActionFailure` rather than bare `fetch`, and must not swallow errors in empty catches. That is a genuine architectural invariant about failure signalling.
- **Delete** `audioCueEditorHandlesServerErrors` — pins exact user-facing error strings.
- **Delete** `audioWidgetMutationPathsHaveNoEmptyCatches` — "parses" JS with `indexOf("},")`, which is wrong as soon as an object literal nests.

### `session/RuntimeModuleShellContractTest.java`
- **Keep** the Jsoup + registry tests: exactly one root per registry key in the rendered cockpit, and registry keys matching `data-runtime-module` one-for-one. Cross-checking rendered DOM against the Java registry is exactly right.
- **Delete** the `_cockpit-module-shell.html` attribute greps and `noDmModeTerminologyRemains` (a tombstone for a finished rename).

### `session/TrackerContractTest.java`
- **Keep** `everyStateKeyIsReadSomewhere` — finds Alpine component state that is written but never read. Genuinely clever; nothing else finds dead UI state.
- **Delete** `theRemovedConditionMenuStateDoesNotComeBack` — tombstone.

### `session/RuntimeStatusSurfaceTest.java`
- **Keep** the MockMvc/JSON endpoint assertions.
- **Delete** `theCockpitDeclaresTheStatusCluster` and `theStatusScriptCoversAllFourStates` — template and JS source greps. The class already has a Spring context; assert on rendered output instead.

### `campaign/packagev2/service/CampaignPackageArchitectureTest.java`
Listed under Delete below — included here only to note it was considered as a trim. Both its
tests are archaeology of a completed migration.

---

# Rewrite (2) — same intent, parse instead of grep

### `web/SurfaceSeparationContractTest.java`
The intent is a **safety rule**: read/run surfaces must never embed edit/admin tooling. Worth
keeping. The mechanism is broken:

- `readAndRunSurfacesCarryNoDestructiveActionInTheirHeader` finds `page-header-actions`, then
  takes everything up to the **first** `</div>`. Any nested element ends the "header" early,
  so the check silently inspects a fragment of what it claims to. It can pass while a
  `btn-danger` sits in the header.
- `everyEditAndAdminSurfaceIsReachableFromItsReadSurface` greps for `"/settings"`, `"/structure"`,
  `"/setup"` anywhere in the file — a comment satisfies it.

Rewrite with Jsoup against rendered pages: select `.page-header-actions .btn-danger`, select
real anchors and check their `href`.

### `web/SurfaceModeContractTest.java`
Real invariant: every governed page declares exactly one `data-surface` mode, on its `<main>`.
But `theDeclarationSitsOnTheRootMainElement` asserts the attribute appears within **200
characters** of `<main`. A character-distance heuristic; adding one attribute to `<main>`
breaks it. Rewrite as `document.selectFirst("main").hasAttr("data-surface")`.

---

# Delete (26 files, 3,931 LOC, 295 test methods)

**Pure markup transcripts** — assert that templates contain the strings they currently
contain. Every one fails on any rewording, restyle, or restructure, and none can fail when
the page is broken.

| File | LOC | Tests | Note |
|---|---|---|---|
| `session/CockpitRuntimeModuleContractTest` | 700 | 66 | 200 substring assertions. Largest single source of redesign friction. |
| `session/SessionCockpitTemplateContractTest` | 408 | 25 | 135 substring assertions; **modified 24 times in the last 300 commits**. |
| `session/SessionCockpitMapContractTest` | 274 | 25 | |
| `audio/web/AudioWidgetTemplateContractTest` | 225 | 25 | |
| `encounter/web/EncounterTemplateContractTest` | 217 | 17 | Method names are a list of the markup as it stood. |
| `config/UiPolishContractTest` | 209 | 14 | See below — actively blocks the redesign. |
| `audio/AudioHostileContentTest` | 193 | 19 | |
| `adventure/web/SceneStructuredTemplateContractTest` | 155 | 17 | |
| `rollabletable/RollableTableTemplateContractTest` | 123 | 13 | |
| `audio/AudioFailureIsolationTest` | 104 | 10 | |
| `threat/ThreatTemplateContractTest` | 101 | 8 | 45 assertions in 101 lines. |
| `quest/web/QuestTemplateContractTest` | 82 | 9 | |
| `audio/web/AudioCueTemplateContractTest` | 72 | 6 | |
| `threat/ThreatDicePrefillContractTest` | 64 | 4 | |
| `sheet/SheetTemplateContractTest` | 37 | 3 | |
| `session/CockpitModuleClientContractTest` | 35 | 1 | |
| `session/InitiativeSetupPartyContractTest` | 34 | 2 | |
| `library/web/LibraryShowMoreTest` | 31 | 2 | |
| `session/StoryRailSceneBodyContractTest` | 30 | 1 | Real intent (unclamped scene body) but the Playwright reachability gate covers it. |
| `audio/web/AudioNavContractTest` | 28 | 1 | |
| `party/PartyStatAbbreviationTest` | 27 | 1 | |
| `session/DiceQuickRollContractTest` | 24 | 1 | |

**Documentation greps** — assert that prose files contain or omit specific phrases.

| File | LOC | Tests | Note |
|---|---|---|---|
| `threat/ThreatDocumentationContractTest` | 130 | 8 | Asserts six markdown files mention "trap", "hazard", "DC", "never invent". |
| `docs/DmManualCockpitAccuracyTest` | 65 | 1 | 24 assertions, mostly `doesNotContain` tombstones for a past cut: `"Player preview"`, `"Screen Safety"`, `"in B1"`, `"Five immutable built-ins"`. Documents what the manual *used* to say. |

**Meta-tests and archaeology**

| File | LOC | Tests | Note |
|---|---|---|---|
| `gate/ReleaseGateIndexContractTest` | 514 | 14 | Validates that `docs/product/all-in-one-release-gate.md` names test classes and methods that exist. Well built, with self-tests — but it is a test that tests that a document describes tests. It creates a **third** place to update on every rename, and its value is entirely downstream of whether that document is used. Highest overhead-to-value ratio in the suite. |
| `campaign/packagev2/service/CampaignPackageArchitectureTest` | 49 | 2 | Asserts four already-deleted files still do not exist, and that no file references two already-deleted classes. A completed migration's tombstone. |

---

## The concrete blocker

`config/UiPolishContractTest.primaryEmptySectionsExposeSpecifiedActions` **requires** the
emoji `⚔ ◇ ✦ ⌖ ▧ ♜` in six templates. Part 1 of the UI redesign adds
`applicationChromeCarriesNoEmojiPictograms`, which forbids literal emoji in chrome in favour
of the SVG icon sprite.

These cannot both pass. The redesign cannot proceed without deleting that assertion. It is
also the file that best illustrates the category:

```java
assertThat(read("static/css/components.css"))
        .contains("repeat(auto-fill, minmax(300px, 360px))");
```

A grid changing from `360px` to `380px` is not a defect.

## What this does not touch

The behavioural core stays untouched and is genuinely good: 66 `@DataJpaTest` repository
tests, the Flyway migration tests, the whole package-v2 import/export suite (round-trip,
atomicity, semantic validation, section adapters — the code that protects user data), the
service tests, and all 20 Playwright browser tests.

## Suggested order

1. **`UiPolishContractTest`** — unblocks the redesign immediately.
2. **The 21 other markup transcripts** — mechanical, no judgement needed.
3. **`ReleaseGateIndexContractTest` + the 2 documentation greps + the migration tombstone** — decide first whether `all-in-one-release-gate.md` is still a live document.
4. **The 10 trims** — surgical, per named method.
5. **The 2 rewrites** — real work; both currently pass while not checking what they claim.

## Standing rule worth adopting

> A test may assert on rendered output, parsed CSS rules, a Java model, or measured browser
> geometry. A test may not assert that a template or stylesheet *source file* contains a
> particular string, unless the string is a structural marker with no visual or editorial
> meaning.

The four `Files.readString` + `.contains` files that survive this rule survive it because
they scan for *absence of a bug pattern* across the whole tree, never for presence of
specific content in a specific file.
