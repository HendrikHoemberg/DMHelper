# UI Redesign Part 2: Application Shell and Shared Archetypes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every hand-rolled page skeleton with one top bar, one navigation rail, one shell fragment, one page-header contract, five page archetypes, and one implementation each of the toolbar, badge, feedback, and overlay primitives — then migrate all 59 page templates onto them.

**Architecture:** A set of shared Thymeleaf fragments becomes the only implementation of the app shell, header, toolbar, feedback, and overlay contracts, and a static JUnit contract fails the build if any feature template hand-rolls one. Pages become a `th:replace` on their own `<html>` element that hands the shell three markup slots — header, content, and (Detail only) rail. Playwright gates assert computed geometry across the supported viewports rather than pixel equality.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, repository-owned CSS and inline SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Position in the program

This is **Part 2 of 4** of the whole-product UI redesign. The program index is
`docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`; the design authority is
`docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.

| Part | Stages | Tasks | Status |
|---|---|---|---|
| 1. Visual foundations | 1 | 1–9 | **must be complete before this part** |
| **2. Shell and shared archetypes (this plan)** | 2 | 10–19 | consumes Part 1 |
| 3. Feature surfaces | 3–5 | 20–37 | consumes this part |
| 4. Editors, cockpit, and release | 6–8 | 38–53 | consumes this part and Part 3 |

Task numbers are global across the four parts, so every cross-reference in the program
(`Task 49 removes them`, `Task 30 owns that`) means the same task everywhere.

**Working product after this part:** one top bar, one navigation rail, one page-header
contract, five archetypes, and one implementation each of the toolbar, feedback, badge, and
overlay primitives. Every standard page renders through `fragments/_shell :: page`; no
template duplicates app-shell markup. Feature *content* is unchanged in substance — Part 3
restructures it.

### Task order

The tasks appear here in dependency order, which is not numeric order: **10, 11, 12, 13, 15,
16, 17, 18, 14, 19**. Task 14 builds the shell, and the shell renders the page header
(Task 15) and the toast region (Task 18) and loads `ui-overlay.js` (Task 18), so it has to
land after them. Execute them top to bottom as written.

## Global Constraints

Every task's requirements implicitly include this section.

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`. This program supersedes the deleted `2026-07-31-ui-redesign-visual-foundations.md` and `2026-07-31-whole-product-ui-redesign-program.md`; both are absorbed here.
- `docs/ui-polish-spec.md` is superseded, including its brown-palette-reuse and no-responsive-work requirements.
- Desktop and laptop browsers only. Minimum supported viewport **1280x720**; primary range **1440x900**–**1920x1080**; large-screen gate **2560x1440**; zoom gates **125%** and **150%**.
- Preserve Spring MVC, Thymeleaf, htmx, Alpine.js, and vanilla JavaScript. No SPA, no frontend package manager, no CSS framework, no icon font, no third-party component system.
- Preserve existing routes, form contracts, persisted entities, campaign-package formats, player-safety filtering, cockpit preset storage, and the customizable four-zone cockpit.
- `tokens.css` is the only file allowed to contain raw UI color values. Persisted terrain, drawing, and campaign-sigil colors are domain data, not UI chrome, and are outside that rule.
- Type roles: system UI for chrome, controls, labels, tables, values, metadata, editor and cockpit chrome; Alegreya for narrative and document content; Cinzel only for the product wordmark and at most one principal title per view; monospace for identifiers, dice expressions, and source keys.
- Aged gold is only ever a primary action, current selection, or visible focus. Secondary controls and ordinary boundaries stay neutral. At most one filled-gold primary action per action region.
- Every semantic state combines at least two of: foreground color, tinted background or border, icon, explicit text, row or shape emphasis. Never color alone.
- Required runtime information never renders below `--text-sm`. Combat numbers, dates, quantities, currency, and resources use tabular figures.
- WCAG AA text contrast; 3:1 for interactive boundaries and focus indicators; complete keyboard operation; reduced-motion support.
- No database migration. View-only DTO or controller-model additions are permitted where a summary, state cluster, or relationship rail cannot be rendered safely from the existing view model; they must not change persisted semantics.
- A stage is complete only when every page in its scope is fully migrated. A visibly hybrid page fails review.
- Keep every existing controller, template, htmx, package, player-safety, encounter, map, cockpit, and accessibility test green. `./mvnw test` must pass at the end of this part.
- **How tests may assert.** A test may assert on rendered output, parsed CSS rules, a Java
  model, or measured browser geometry. A test may not assert that a template or stylesheet
  *source file* contains a particular string, unless that string is a structural marker with
  no visual or editorial meaning — a `th:fragment` signature, a `data-*` hook, a CSS selector
  resolved through `CssRules`. Never slice source at a character offset (`indexOf` +
  `substring`) and assert on the slice; parse it with Jsoup instead. A scan that can match
  nothing must assert it matched something before asserting what it found.
  `docs/test-suite-triage.md` records why: 27 test classes were deleted in July 2026 for
  failing this rule, and one offset-slice guard was passing while a destructive control sat
  in a page header.

## Shared task protocol

Applies to every task in this plan.

- Red first: write or extend the failing test, run it, confirm the failure message names the
  missing thing, then implement.
- One task, one commit. Use `feat:`, `refactor:`, `test:`, or `fix:` prefixes.
- Run the task's focused test command after each red/green cycle; run `./mvnw test` at the
  end of the part before the review step.
- Never add a new legacy alias. Never raise a migration budget. Budgets only ratchet down.
- When a template moves onto a shared fragment, delete the markup it replaced in the same
  commit. Leaving both is what produces a hybrid page.
- Screenshots go to `target/ui-redesign/<stage-slug>/`; they are human-review evidence, not
  assertions. Automated tests assert behavior, computed styles, and geometry.

## File structure

### Created by this part

| Path | Responsibility |
|---|---|
| `src/main/resources/templates/fragments/_shell.html` | Whole-document layout for every standard page |
| `src/main/resources/templates/fragments/_topbar.html` | Global top bar, search, dice, session state, overflow |
| `src/main/resources/templates/fragments/_rail.html` | Campaign and global navigation rail |
| `src/main/resources/templates/fragments/_page-header.html` | Breadcrumb, title, summary, action groups |
| `src/main/resources/templates/fragments/_toolbar.html` | Search/filter toolbar and table toolbar |
| `src/main/resources/templates/fragments/_states.html` | Empty, loading, unavailable, error, skeleton |
| `src/main/resources/templates/fragments/_banner.html` | Semantic inline banner |
| `src/main/resources/templates/fragments/_badge.html` | Status, provenance, scope badges |
| `src/main/resources/templates/fragments/_overlay.html` | Dialog, side sheet, popover, toast region |
| `src/main/resources/templates/fragments/_context-rail.html` | Detail archetype contextual rail |
| `src/main/resources/templates/fragments/_status.html` | Save, loading, connection indicators |
| `src/main/resources/static/js/topbar.js` | Palette hint key, roll delegate, dice shortcut |
| `src/main/resources/static/js/rail.js` | Current-page marking and persisted collapse |
| `src/main/resources/static/js/ui-overlay.js` | Focus trap, restore, Escape, toast lifecycle |
| `src/main/resources/static/icons/favicon.svg` | Token-coloured favicon |

Test sources created by this part: `config/TemplateRules.java`, `AppShellContractTest`,
`PageArchetypeContractTest`, `TopBarContractTest`, `NavigationRailContractTest`,
`PageHeaderContractTest`, `SharedComponentContractTest`, `AsyncStateContractTest`,
`OverlayContractTest`, and `gate/OverlayBehaviorGateTest`, `gate/ShellRenderGateTest`.

### Deleted by this part

| Path | Replaced by |
|---|---|
| `src/main/resources/templates/fragments/_appnav.html` | `fragments/_rail.html` |
| `src/main/resources/templates/fragments/navbar.html` | `fragments/_topbar.html` |
| the `head` fragment inside `fragments/head.html` | `document-head(pageTitle)` |

## Public interfaces

### Consumed from Part 1

Every semantic role token (`--surface-*`, `--border-*`, `--text-*`, `--action-*`,
`--selection-*`, `--focus-ring`, `--state-*`, `--scrim-*`, `--shadow-*`, `--glow-primary`,
`--neutral-hover-surface`), the icon fragments

```html
~{common/_icon :: icon(name='search')}
~{common/_icon :: icon-sized(name='search', size='20')}
```

and the test helpers `CssRules.ALL_FILES`, `CssRules.of(...)`, `CssRules.allApplicationCss()`,
`CssRules.allTemplateMarkup()`, `CssRules.rawColorLiterals(...)`,
`CssRules.tokenReferenceCount(...)`, `ColorContrast.ratio(...)`.

### Produced for Parts 3 and 4 — layout roles

```css
--topbar-height        --rail-width-expanded  --rail-width-collapsed
--page-gap             --page-pad             --context-rail-width
--measure-prose        --page-max-index       --page-max-detail       --page-max-form
```

Archetype classes `.page--index`, `.page--detail`, `.page--form`, `.page--operational`,
`.page--editor`, and the three slot classes `.page-header-slot`, `.page-content`,
`.page-rail-slot`.

### Produced for Parts 3 and 4 — Thymeleaf fragment contracts

```html
~{fragments/head :: document-head(pageTitle='Encounters')}
~{fragments/_shell :: page(pageTitle=…, archetype=…, surface=…, header=~{::…}, content=~{::…}, rail=~{::…})}
~{fragments/_topbar :: topbar}
~{fragments/_rail :: rail}
~{fragments/_page-header :: page-header(title=…, summary=…, breadcrumb=~{}, primary=~{}, secondary=~{})}
~{fragments/_toolbar :: toolbar(action=…, searchValue=…, searchPlaceholder=…, filters=~{}, actions=~{})}
~{fragments/_toolbar :: table-toolbar(selectionLabel=…, actions=~{})}
~{fragments/_states :: empty(icon=…, title=…, description=…, cta=~{})}
~{fragments/_states :: loading(label=…)}
~{fragments/_states :: skeleton(count=…)}
~{fragments/_states :: unavailable(title=…, description=…, retry=~{})}
~{fragments/_states :: failed(title=…, description=…, retry=~{})}
~{fragments/_banner :: banner(tone=…, title=…, body=…, actions=~{})}
~{fragments/_badge :: badge(tone=…, icon=…, label=…)}
~{fragments/_context-rail :: rail(body=~{::…})}
~{fragments/_context-rail :: rail-section(title=…, body=~{::…})}
~{fragments/_overlay :: dialog(id=…, title=…, body=~{::…}, actions=~{::…})}
~{fragments/_overlay :: side-sheet(id=…, title=…, body=~{::…})}
~{fragments/_overlay :: popover(id=…, label=…, body=~{::…})}
~{fragments/_overlay :: toast-region}
~{fragments/_status :: save-status(id=…)}
```

`tone` is one of `success`, `warning`, `danger`, `info`, `shield`, `neutral`.
`archetype` is one of `index`, `detail`, `form`, `operational`, `editor`.
Omitted markup parameters are passed as the empty fragment `~{}`.

### Produced for Parts 3 and 4 — Java test helpers and JavaScript globals

```java
static List<Path>    TemplateRules.allTemplates()
static List<Path>    TemplateRules.pageTemplates()
static String        TemplateRules.read(Path template)
static Document      TemplateRules.parse(Path template)
static final Path    TemplateRules.ROOT
```

```js
window.dmOverlay.open(element)      // shows, traps focus, remembers trigger
window.dmOverlay.close(element)     // hides, restores focus to trigger
window.dmToast.show(message, tone)  // transient confirmation only
```

---

# Stage 2: Application shell and shared archetypes

**Working product after this stage:** one top bar, one navigation rail, one page-header
contract, five archetypes, and one implementation each of toolbar, feedback, badge, and
overlay primitives. Every standard page renders through `fragments/_shell :: page`; no
template duplicates app-shell markup. Feature content is unchanged in substance — Stages
3–5 restructure it.

## Task 10: Add the template inventory helper and the shell duplication contract

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/TemplateRules.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/AppShellContractTest.java`

**Interfaces:**
- Produces: `TemplateRules.allTemplates()`, `TemplateRules.pageTemplates()`,
  `TemplateRules.read(Path)`, `TemplateRules.parse(Path)`.

`jsoup` is already a test dependency (used by the existing template contract tests).

- [ ] **Step 1: Write the helper**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Flat reader for the Thymeleaf templates, mirroring CssRules for markup contracts. */
final class TemplateRules {

    static final Path ROOT = Path.of("src/main/resources/templates");

    private TemplateRules() {
    }

    static List<Path> allTemplates() {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(path -> path.toString().endsWith(".html")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Templates that render a whole document rather than a fragment. A page template is one
     * whose file name does not start with "_" and that declares a body or delegates to the
     * shell.
     */
    static List<Path> pageTemplates() {
        return allTemplates().stream()
                .filter(path -> !path.getFileName().toString().startsWith("_"))
                .filter(path -> {
                    String markup = read(path);
                    return markup.contains("<body") || markup.contains("fragments/_shell");
                })
                .toList();
    }

    static String read(Path template) {
        try {
            return Files.readString(template);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parsed with the XML parser so Thymeleaf attributes survive intact. */
    static Document parse(Path template) {
        return Jsoup.parse(read(template), "", Parser.xmlParser());
    }
}
```

- [ ] **Step 2: Write the failing shell contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 18.2: no feature template may duplicate global app-shell markup. */
class AppShellContractTest {

    /** Standalone documents that legitimately do not use the campaign shell. */
    private static final List<String> EXEMPT = List.of("error.html");

    private static boolean exempt(Path template) {
        return EXEMPT.contains(template.getFileName().toString());
    }

    @Test
    void everyPageTemplateDelegatesToTheSharedShell() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (exempt(template)) continue;
            assertThat(TemplateRules.read(template))
                    .as("%s must render through fragments/_shell :: page", template)
                    .contains("fragments/_shell :: page");
        }
    }

    @Test
    void noPageTemplateHandRollsTheShell() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (exempt(template)) continue;
            String markup = TemplateRules.read(template);
            assertThat(markup)
                    .as("%s hand-rolls shell markup", template)
                    .doesNotContain("class=\"app-shell\"")
                    .doesNotContain("fragments/navbar")
                    .doesNotContain("fragments/_appnav")
                    .doesNotContain("~{fragments/head :: head}");
        }
    }

    @Test
    void theShellIsImplementedExactlyOnce() {
        long implementations = TemplateRules.allTemplates().stream()
                .filter(path -> TemplateRules.read(path).contains("class=\"app-shell\""))
                .count();
        assertThat(implementations).as("app-shell implementations").isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

```bash
./mvnw -Dtest='AppShellContractTest' test
```

Expected: FAIL for 59 templates. That failure list is the Task 19 worklist.

- [ ] **Step 4: Commit the red contract**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/config/TemplateRules.java
git commit -m "test: add the template inventory helper"
```

Commit only the helper. `AppShellContractTest` lands green in Task 19; keep it in the
working tree until then.

## Task 11: Define layout tokens and the five archetypes

**Files:**
- Modify: `src/main/resources/static/css/tokens.css`
- Modify: `src/main/resources/static/css/base.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/PageArchetypeContractTest.java`

**Interfaces:**
- Produces: the layout tokens and the `.page--*` archetype classes.

- [ ] **Step 1: Write the failing archetype contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 9: every standard page is one of five archetypes. */
class PageArchetypeContractTest {

    static final List<String> ARCHETYPES =
            List.of("index", "detail", "form", "operational", "editor");

    @Test
    void layoutTokensExist() {
        String tokens = CssRules.read("tokens.css");
        for (String token : List.of("--topbar-height", "--rail-width-expanded",
                "--rail-width-collapsed", "--page-gap", "--page-pad", "--context-rail-width",
                "--measure-prose", "--page-max-index", "--page-max-detail", "--page-max-form")) {
            assertThat(tokens).as("%s is defined", token).contains(token + ":");
        }
    }

    @Test
    void everyArchetypeHasALayoutRule() {
        String base = CssRules.read("base.css");
        for (String archetype : ARCHETYPES) {
            assertThat(base).as("layout rule for %s", archetype).contains(".page--" + archetype);
        }
    }

    @Test
    void readingSurfacesAreBoundedAndOperationalSurfacesAreNot() {
        var rules = CssRules.of("base.css");
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).contains(".page--form");
            assertThat(rule.value("max-width")).contains("--page-max-form");
        });
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).contains(".page--detail");
            assertThat(rule.value("max-width")).contains("--page-max-detail");
        });
        var operational = rules.stream()
                .filter(rule -> rule.selector().equals(".page--operational"))
                .findFirst()
                .orElseThrow();
        assertThat(operational.value("max-width")).isNull();
    }

    @Test
    void theEditorArchetypeOwnsTheViewportAndDoesNotScrollTheDocument() {
        var editor = CssRules.of("base.css").stream()
                .filter(rule -> rule.selector().equals(".page--editor"))
                .findFirst()
                .orElseThrow();
        assertThat(editor.value("height")).contains("100");
        assertThat(editor.value("overflow")).isEqualTo("hidden");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='PageArchetypeContractTest' test
```

Expected: FAIL — `--topbar-height is defined` is false.

- [ ] **Step 3: Add the layout tokens to `tokens.css`**

```css
  /* Layout roles (spec section 9). */
  --topbar-height: 52px;
  --rail-width-expanded: 232px;
  --rail-width-collapsed: 56px;
  --page-gap: 24px;
  --page-pad: 32px;
  --context-rail-width: 320px;
  --measure-prose: 74ch;
  --page-max-index: 1680px;
  --page-max-detail: 1440px;
  --page-max-form: 780px;
```

- [ ] **Step 4: Add the shell frame and archetypes to `base.css`**

Replace the existing `.app-shell` / `.app-main` block with:

```css
.app-shell {
  display: grid;
  grid-template-columns: var(--rail-width-expanded) 1fr;
  min-height: calc(100vh - var(--topbar-height));
  background: var(--surface-canvas);
}

.app-shell--rail-collapsed { grid-template-columns: var(--rail-width-collapsed) 1fr; }

.app-main {
  background: var(--surface-workspace);
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* Archetypes ------------------------------------------------------------- */

.page {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--page-gap);
  padding: var(--page-pad);
  width: 100%;
  margin-inline: auto;
}

.page--index { max-width: var(--page-max-index); }

.page--detail {
  max-width: var(--page-max-detail);
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--context-rail-width);
  grid-template-areas: "header header" "content rail";
  align-content: start;
}
/* The grid children are the page's own slot wrappers, not the fragments inside them:
   the shell replaces its th:block with the page's `<div id="page-header">` element, and
   `.page-header` lives on the <header> nested one level deeper inside that wrapper. */
.page--detail > .page-header-slot { grid-area: header; min-width: 0; }
.page--detail > .page-content { grid-area: content; min-width: 0; }
.page--detail > .page-rail-slot { grid-area: rail; min-width: 0; }
.page--detail .prose { max-width: var(--measure-prose); }

/* A detail page with no contextual rail must not reserve an empty 320px column. */
.page--detail:not(:has(> .page-rail-slot)) {
  grid-template-columns: minmax(0, 1fr);
  grid-template-areas: "header" "content";
}

.page--form { max-width: var(--page-max-form); }

.page--operational { padding-inline: var(--page-gap); }

.page--editor {
  height: calc(100vh - var(--topbar-height));
  overflow: hidden;
  padding: 0;
  gap: 0;
  max-width: none;
}

/* Below 1440 the contextual rail would squeeze the primary column past use;
   spec section 9.2 allows it to become an inline lower section there. */
@media (max-width: 1439px) {
  .page--detail {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: "header" "content" "rail";
  }
}

/* Ultrawide: grow the margins, never the measure (spec section 9.6). */
@media (min-width: 2200px) {
  .page--index { max-width: var(--page-max-index); }
  .page--operational { padding-inline: calc(var(--page-pad) * 2); }
}
```

Set `body { overflow-x: hidden; }` only on `body[data-archetype="editor"]`; standard pages
must genuinely not overflow rather than hide it.

- [ ] **Step 5: Run it and watch it pass**

```bash
./mvnw -Dtest='PageArchetypeContractTest,RawVisualValueContractTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css/tokens.css src/main/resources/static/css/base.css src/test/java/dev/hendrikhoemberg/dmhelper/config/PageArchetypeContractTest.java
git commit -m "feat: define layout tokens and the five page archetypes"
```

## Task 12: Build the global top bar

**Files:**
- Create: `src/main/resources/templates/fragments/_topbar.html`
- Modify: `src/main/resources/static/css/base.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/TopBarContractTest.java`

**Interfaces:**
- Produces: `~{fragments/_topbar :: topbar}`.
- Consumes: `~{common/_icon :: icon}`, `~{fragments/_command-palette :: command-palette}`,
  `~{fragments/_dice-roller :: dice-roller}`.

The top bar carries global or session-wide utilities only: identity, global search, dice,
session state, and an overflow menu. Everything the old `navbar.html` did — the campaign-id
body attribute, the roll-tooltip delegate, the dice keyboard shortcut, the palette hint
key — moves here unchanged.

- [ ] **Step 1: Write the failing contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 8.1: the top bar carries global utilities only. */
class TopBarContractTest {

    private static final Path TOPBAR =
            Path.of("src/main/resources/templates/fragments/_topbar.html");

    @Test
    void theTopBarCarriesOnlyGlobalUtilities() {
        String markup = TemplateRules.read(TOPBAR);
        assertThat(markup).contains("data-topbar-search")
                .contains("data-topbar-dice")
                .contains("data-topbar-session")
                .contains("data-topbar-overflow");
    }

    @Test
    void thePageCreationAndFilterActionsDoNotLiveInTheTopBar() {
        String markup = TemplateRules.read(TOPBAR).toLowerCase();
        for (String forbidden : java.util.List.of(">new ", ">create", ">filter", ">edit", ">delete")) {
            assertThat(markup).as("page action %s belongs to the page header", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @Test
    void everyIconOnlyControlHasAnAccessibleName() {
        var document = TemplateRules.parse(TOPBAR);
        for (var control : document.select("button, a")) {
            boolean hasText = !control.ownText().isBlank()
                    || !control.select("span:not([aria-hidden])").isEmpty();
            boolean hasLabel = control.hasAttr("aria-label") || control.hasAttr("aria-labelledby");
            assertThat(hasText || hasLabel)
                    .as("accessible name for %s", control.outerHtml())
                    .isTrue();
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='TopBarContractTest' test
```

Expected: FAIL — the file does not exist.

- [ ] **Step 3: Write `_topbar.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="topbar">
<header class="app-topbar">
    <a class="app-brand" data-display-title th:href="@{/}">DMHelper</a>

    <span class="app-topbar__campaign" th:if="${campaignName != null}"
          th:text="${campaignName}">Campaign</span>

    <div class="app-topbar__spacer"></div>

    <button type="button" class="topbar-btn" data-topbar-search
            aria-label="Search everything" title="Search everything"
            onclick="window.dispatchEvent(new CustomEvent('command-palette-toggle'))">
        <th:block th:replace="~{common/_icon :: icon(name='search')}"></th:block>
        <span class="topbar-btn__label">Search</span>
        <kbd class="topbar-btn__key" id="paletteHintKey">&#8984;K</kbd>
    </button>

    <button type="button" class="topbar-btn" id="diceToggle" data-topbar-dice
            th:if="${campaignId != null}" aria-label="Toggle dice roller"
            title="Toggle dice roller (Ctrl+R)"
            onclick="window.dispatchEvent(new CustomEvent('dice-roller-toggle'))">
        <th:block th:replace="~{common/_icon :: icon(name='dice')}"></th:block>
    </button>

    <div class="app-topbar__session" data-topbar-session
         th:if="${sessionState != null}">
        <span class="badge badge--info" th:text="${sessionState}">Session state</span>
    </div>

    <div class="app-topbar__overflow" data-topbar-overflow x-data="{ open: false }">
        <button type="button" class="topbar-btn" aria-label="More" title="More"
                :aria-expanded="open" @click="open = !open">
            <th:block th:replace="~{common/_icon :: icon(name='menu')}"></th:block>
        </button>
        <div class="popover popover--end" x-show="open" x-cloak @click.outside="open = false"
             role="menu" aria-label="More">
            <a class="popover__item" role="menuitem" href="/library/about">About</a>
            <a class="popover__item" role="menuitem"
               th:if="${campaignId != null}"
               th:href="@{/campaigns/{id}/settings(id=${campaignId})}">Campaign settings</a>
        </div>
    </div>

    <th:block th:replace="~{fragments/_command-palette :: command-palette}"></th:block>
</header>

<th:block th:if="${campaignId != null}">
    <th:block th:replace="~{fragments/_dice-roller :: dice-roller}"></th:block>
</th:block>

<script th:inline="javascript">
/*<![CDATA[*/
document.body.dataset.campaignId = /*[[${campaignId}]]*/ '';
/*]]>*/
</script>
<script th:src="@{/js/dice-roller.js}"></script>
<script th:src="@{/js/topbar.js}"></script>
</th:block>
</html>
```

- [ ] **Step 4: Move the inline behavior into `static/js/topbar.js`**

Move verbatim from the old `navbar.html`: the platform check that rewrites `#paletteHintKey`
to `Ctrl K`, the `[data-roll]` click delegate that posts to `/api/v1/roll` and shows the roll
tooltip, and the `Ctrl+R` dice shortcut. Behavior must not change; only its home does.

- [ ] **Step 5: Style the top bar in `base.css`**

```css
.app-topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-navigation);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  height: var(--topbar-height);
  padding-inline: var(--space-md);
  background: var(--surface-navigation);
  border-bottom: 1px solid var(--border-subtle);
}
.app-topbar__spacer { flex: 1; }
.app-brand { font-family: var(--font-display); color: var(--text-primary); text-decoration: none; }
.app-topbar__campaign { color: var(--text-secondary); font-size: var(--text-sm); }
.topbar-btn {
  display: inline-flex; align-items: center; gap: var(--space-2xs);
  padding: var(--space-2xs) var(--space-xs);
  background: transparent; border: 1px solid transparent; border-radius: var(--radius);
  color: var(--text-secondary); cursor: pointer;
}
.topbar-btn:hover { color: var(--text-primary); background: var(--neutral-hover-surface); }
.topbar-btn__label { font-size: var(--text-sm); }
.topbar-btn__key { color: var(--text-tertiary); font-size: var(--text-xs); }
```

- [ ] **Step 6: Run it and watch it pass**

```bash
./mvnw -Dtest='TopBarContractTest,IconSystemContractTest' test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/fragments/_topbar.html src/main/resources/static/js/topbar.js src/main/resources/static/css/base.css src/test/java/dev/hendrikhoemberg/dmhelper/config/TopBarContractTest.java
git commit -m "feat: build the global top bar"
```

## Task 13: Rebuild the navigation rail on the approved groups

**Files:**
- Create: `src/main/resources/templates/fragments/_rail.html`
- Delete: `src/main/resources/templates/fragments/_appnav.html`
- Modify: `src/main/resources/static/css/base.css`
- Create: `src/main/resources/static/js/rail.js`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/NavigationRailContractTest.java`

**Interfaces:**
- Produces: `~{fragments/_rail :: rail}`.

Two deliberate deviations from today's rail, both required by spec section 8.2:

1. `Run Session` moves into the **Campaign** group and the permanent footer button is
   deleted. Campaign Home keeps its own contextual Start/Resume action.
2. The rail lists **Party**, not **Party** and **Sheets**. Character sheets stay reachable:
   the Party page header gains a `Character sheets` secondary action to `/campaigns/{id}/sheets`
   and roster rows keep linking to individual sheets. Task 30 owns that.

- [ ] **Step 1: Write the failing rail contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec sections 8.2 and 8.3. */
class NavigationRailContractTest {

    private static final Path RAIL =
            Path.of("src/main/resources/templates/fragments/_rail.html");

    /**
     * One rail branch, resolved through the tree rather than by slicing source at an offset.
     * `data-rail-branch` exists purely so this test can name a branch; it carries no styling.
     */
    private static Element branch(String name) {
        Element branch = TemplateRules.parse(RAIL).selectFirst("[data-rail-branch=" + name + "]");
        assertThat(branch).as("the %s rail branch exists", name).isNotNull();
        return branch;
    }

    private static List<String> destinationsOf(String branchName) {
        return branch(branchName).select("a.rail__link").stream()
                .map(link -> link.attr("data-label"))
                .toList();
    }

    @Test
    void theCampaignRailUsesTheApprovedGroupsInOrder() {
        List<String> groups = branch("campaign").select(".rail__group > .rail__label").stream()
                .map(Element::text)
                .toList();

        assertThat(groups)
                .as("spec 8.2: the five campaign groups, in order")
                .containsExactly("Campaign", "Prepare", "Party & World", "Records", "Reference");
    }

    /**
     * Scoped to the campaign branch on purpose: Library, Tables, Traps, and Hazards are
     * deliberately reachable from both branches, so a whole-file count would always be 2.
     *
     * <p>`containsExactlyInAnyOrder` is deliberate — it fails both on a missing destination
     * and on an unapproved extra one, which a per-label occurrence count cannot do.
     */
    @Test
    void everyApprovedDestinationIsPresentExactlyOnceInTheCampaignRail() {
        assertThat(destinationsOf("campaign"))
                .as("spec 8.2: the campaign rail's destinations, each exactly once")
                .containsExactlyInAnyOrder("Campaign Home", "Run Session", "Adventures",
                        "Encounters", "Maps", "Handouts", "Audio", "Party", "Quests", "NPCs",
                        "Locations", "Factions", "Calendar", "Notes", "Treasury", "Ledger",
                        "Library", "Tables", "Traps", "Hazards");
    }

    @Test
    void runSessionIsNotDuplicatedAsAFooterButton() {
        assertThat(TemplateRules.read(RAIL))
                .as("spec 8.2: Run Session is a Campaign group entry, not a footer button")
                .doesNotContain("appnav-footer")
                .doesNotContain("rail__footer");
    }

    @Test
    void theGlobalRailDoesNotPretendToBeInsideACampaign() {
        assertThat(destinationsOf("global"))
                .as("spec 8.3: the global rail offers only campaign-independent destinations")
                .containsExactlyInAnyOrder("Campaigns", "Library", "Tables", "Traps", "Hazards",
                        "About");
    }

    @Test
    void collapsedLabelsRemainAccessible() {
        var document = TemplateRules.parse(RAIL);
        for (var link : document.select("a.rail__link")) {
            assertThat(link.hasAttr("data-label"))
                    .as("collapsed label for %s", link.outerHtml()).isTrue();
            assertThat(link.hasAttr("title"))
                    .as("collapsed affordance for %s", link.outerHtml()).isTrue();
            assertThat(link.select("span.rail__text").isEmpty())
                    .as("%s must keep its text node as the accessible name", link.outerHtml())
                    .isFalse();
        }
    }

    /**
     * The collapsed rail must clip its labels visually, not remove them from the
     * accessibility tree — display:none would leave every collapsed link unnamed.
     */
    @Test
    void theCollapsedRailDoesNotHideLabelsFromScreenReaders() {
        var collapsed = CssRules.of("base.css").stream()
                .filter(rule -> rule.selector().contains(".app-shell--rail-collapsed")
                        && rule.selector().contains(".rail__text"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".rail__text has no collapsed rule"));
        assertThat(collapsed.value("display"))
                .as("display:none strips the accessible name; clip the label instead")
                .isNotEqualTo("none");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='NavigationRailContractTest' test
```

Expected: FAIL — the file does not exist.

- [ ] **Step 3: Write `_rail.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<nav class="rail" th:fragment="rail" id="rail" aria-label="Primary">
    <button type="button" class="rail__collapse" id="railCollapse"
            aria-label="Collapse navigation" aria-expanded="true" title="Collapse navigation">
        <th:block th:replace="~{common/_icon :: icon-sized(name='chevron-right', size='16')}"></th:block>
    </button>

    <th:block th:if="${campaignId != null}" data-rail-branch="campaign">
        <div class="rail__group">
            <div class="rail__label">Campaign</div>
            <a class="rail__link" data-label="Campaign Home"
               th:href="@{/campaigns/{id}(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='castle')}"></th:block>
                <span class="rail__text">Campaign Home</span></a>
            <a class="rail__link" data-label="Run Session"
               th:href="@{/campaigns/{id}/session(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='play')}"></th:block>
                <span class="rail__text">Run Session</span></a>
        </div>

        <div class="rail__group">
            <div class="rail__label">Prepare</div>
            <a class="rail__link" data-label="Adventures"
               th:href="@{/campaigns/{id}/adventures(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='book')}"></th:block>
                <span class="rail__text">Adventures</span></a>
            <a class="rail__link" data-label="Encounters"
               th:href="@{/campaigns/{id}/encounters(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='swords')}"></th:block>
                <span class="rail__text">Encounters</span></a>
            <a class="rail__link" data-label="Maps"
               th:href="@{/campaigns/{id}/maps(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='map')}"></th:block>
                <span class="rail__text">Maps</span></a>
            <a class="rail__link" data-label="Handouts"
               th:href="@{/campaigns/{id}/handouts(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='scroll')}"></th:block>
                <span class="rail__text">Handouts</span></a>
            <a class="rail__link" data-label="Audio"
               th:href="@{/campaigns/{id}/audio/cues(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='music')}"></th:block>
                <span class="rail__text">Audio</span></a>
        </div>

        <div class="rail__group">
            <div class="rail__label">Party &amp; World</div>
            <a class="rail__link" data-label="Party"
               th:href="@{/campaigns/{id}/party(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='users')}"></th:block>
                <span class="rail__text">Party</span></a>
            <a class="rail__link" data-label="Quests"
               th:href="@{/campaigns/{id}/quests(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='star')}"></th:block>
                <span class="rail__text">Quests</span></a>
            <a class="rail__link" data-label="NPCs"
               th:href="@{/campaigns/{id}/world/npcs(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='user')}"></th:block>
                <span class="rail__text">NPCs</span></a>
            <a class="rail__link" data-label="Locations"
               th:href="@{/campaigns/{id}/world/locations(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='location')}"></th:block>
                <span class="rail__text">Locations</span></a>
            <a class="rail__link" data-label="Factions"
               th:href="@{/campaigns/{id}/world/factions(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='flag')}"></th:block>
                <span class="rail__text">Factions</span></a>
            <a class="rail__link" data-label="Calendar"
               th:href="@{/campaigns/{id}/calendar(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='calendar')}"></th:block>
                <span class="rail__text">Calendar</span></a>
        </div>

        <div class="rail__group">
            <div class="rail__label">Records</div>
            <a class="rail__link" data-label="Notes"
               th:href="@{/campaigns/{id}/notes(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='note')}"></th:block>
                <span class="rail__text">Notes</span></a>
            <a class="rail__link" data-label="Treasury"
               th:href="@{/campaigns/{id}/treasury(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='gem')}"></th:block>
                <span class="rail__text">Treasury</span></a>
            <a class="rail__link" data-label="Ledger"
               th:href="@{/campaigns/{id}/ledger(id=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='coins')}"></th:block>
                <span class="rail__text">Ledger</span></a>
        </div>

        <div class="rail__group">
            <div class="rail__label">Reference</div>
            <!-- Carry campaignId: these routes list global content only when it is absent,
                 which hides everything the current campaign imported. -->
            <a class="rail__link" data-label="Library"
               th:href="@{/library(campaignId=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='book')}"></th:block>
                <span class="rail__text">Library</span></a>
            <a class="rail__link" data-label="Tables"
               th:href="@{/library/tables(campaignId=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='dice')}"></th:block>
                <span class="rail__text">Tables</span></a>
            <a class="rail__link" data-label="Traps"
               th:href="@{/library/traps(campaignId=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='alert-triangle')}"></th:block>
                <span class="rail__text">Traps</span></a>
            <a class="rail__link" data-label="Hazards"
               th:href="@{/library/hazards(campaignId=${campaignId})}">
                <th:block th:replace="~{common/_icon :: icon(name='skull')}"></th:block>
                <span class="rail__text">Hazards</span></a>
        </div>
    </th:block>

    <th:block th:unless="${campaignId != null}" data-rail-branch="global">
        <div class="rail__group">
            <a class="rail__link" data-label="Campaigns" href="/campaigns">
                <th:block th:replace="~{common/_icon :: icon(name='castle')}"></th:block>
                <span class="rail__text">Campaigns</span></a>
            <a class="rail__link" data-label="Library" href="/library">
                <th:block th:replace="~{common/_icon :: icon(name='book')}"></th:block>
                <span class="rail__text">Library</span></a>
            <a class="rail__link" data-label="Tables" href="/library/tables">
                <th:block th:replace="~{common/_icon :: icon(name='dice')}"></th:block>
                <span class="rail__text">Tables</span></a>
            <a class="rail__link" data-label="Traps" href="/library/traps">
                <th:block th:replace="~{common/_icon :: icon(name='alert-triangle')}"></th:block>
                <span class="rail__text">Traps</span></a>
            <a class="rail__link" data-label="Hazards" href="/library/hazards">
                <th:block th:replace="~{common/_icon :: icon(name='skull')}"></th:block>
                <span class="rail__text">Hazards</span></a>
            <a class="rail__link" data-label="About" href="/library/about">
                <th:block th:replace="~{common/_icon :: icon(name='help')}"></th:block>
                <span class="rail__text">About</span></a>
        </div>
    </th:block>

    <script th:src="@{/js/rail.js}"></script>
</nav>
</html>
```

- [ ] **Step 4: Move the rail behavior into `static/js/rail.js`**

Port the `_appnav.html` script unchanged in behavior: longest-prefix `aria-current="page"`
marking, and the persisted collapse preference. Rename the storage key from
`dmhelper.navCollapsed` to `dmhelper.railCollapsed` and read the old key once as a fallback
so an existing preference is not lost:

```js
(function () {
    const path = location.pathname;
    let best = null;
    document.querySelectorAll('#rail .rail__link').forEach(link => {
        const href = link.getAttribute('href').split('?')[0];
        if (href === '/' || !(path === href || path.startsWith(href + '/'))) return;
        if (!best || href.length > best.getAttribute('href').split('?')[0].length) best = link;
    });
    if (best) best.setAttribute('aria-current', 'page');

    const shell = document.querySelector('.app-shell');
    const toggle = document.getElementById('railCollapse');
    if (!shell || !toggle) return;

    function apply(collapsed) {
        shell.classList.toggle('app-shell--rail-collapsed', collapsed);
        toggle.setAttribute('aria-expanded', String(!collapsed));
        toggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
        toggle.title = collapsed ? 'Expand navigation' : 'Collapse navigation';
    }

    const stored = localStorage.getItem('dmhelper.railCollapsed')
        ?? localStorage.getItem('dmhelper.navCollapsed');
    apply(stored === '1');

    toggle.addEventListener('click', () => {
        const collapsed = !shell.classList.contains('app-shell--rail-collapsed');
        localStorage.setItem('dmhelper.railCollapsed', collapsed ? '1' : '0');
        apply(collapsed);
    });
})();
```

- [ ] **Step 5: Style the rail in `base.css`**

Replace every `.appnav*` rule with `.rail*` equivalents:

```css
.rail {
  background: var(--surface-navigation);
  border-right: 1px solid var(--border-subtle);
  padding: var(--space-sm) 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  overflow-y: auto;
}
.rail__group { display: flex; flex-direction: column; padding-block: var(--space-2xs); }
.rail__group + .rail__group { border-top: 1px solid var(--border-subtle); margin-top: var(--space-2xs); }
.rail__label {
  padding: var(--space-2xs) var(--space-sm);
  font-size: var(--text-xs);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-tertiary);
}
.rail__link {
  display: flex; align-items: center; gap: var(--space-xs);
  padding: var(--space-2xs) var(--space-sm);
  color: var(--text-secondary); text-decoration: none;
  border-left: 2px solid transparent;
}
.rail__link:hover { color: var(--text-primary); background: var(--neutral-hover-surface); }
.rail__link[aria-current="page"] {
  color: var(--text-primary);
  border-left-color: var(--selection-accent);
  background: var(--selection-surface);
}
/* Collapsed labels are clipped from view, never from the accessibility tree. display:none
   would strip the link's accessible name entirely, and a CSS tooltip positioned outside the
   rail would be clipped anyway because overflow-y:auto makes .rail a scroll container in
   both axes. The visual affordance is the native title attribute on the link. */
.app-shell--rail-collapsed .rail__text {
  position: absolute;
  width: 1px; height: 1px;
  margin: -1px; padding: 0; border: 0;
  clip-path: inset(50%);
  overflow: hidden; white-space: nowrap;
}
.app-shell--rail-collapsed .rail__label { display: none; }
.app-shell--rail-collapsed .rail__link { justify-content: center; padding-inline: 0; }
```

Add `th:title` alongside `data-label` on every `a.rail__link` so the collapsed rail keeps a
visible affordance:

```html
<a class="rail__link" data-label="Campaign Home" title="Campaign Home"
   th:href="@{/campaigns/{id}(id=${campaignId})}">
```

- [ ] **Step 6: Delete `_appnav.html` and re-enable the emoji check**

```bash
git rm src/main/resources/templates/fragments/_appnav.html
```

Remove the `@Disabled` marker from `IconSystemContractTest.applicationChromeCarriesNoEmojiPictograms`
if Task 6 added one.

- [ ] **Step 7: Run the contracts**

```bash
./mvnw -Dtest='NavigationRailContractTest,IconSystemContractTest,TopBarContractTest' test
```

Expected: PASS. Page templates still reference the deleted fragment and will fail their own
render tests until Task 19; that is expected mid-stage.

- [ ] **Step 8: Commit**

```bash
git add -A src/main/resources/templates/fragments src/main/resources/static/js/rail.js src/main/resources/static/css/base.css src/test/java/dev/hendrikhoemberg/dmhelper/config/NavigationRailContractTest.java
git commit -m "feat: rebuild the navigation rail on the approved task groups"
```

## Task 15: Build the page header contract

**Files:**
- Create: `src/main/resources/templates/fragments/_page-header.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/PageHeaderContractTest.java`

**Interfaces:**
- Produces: `~{fragments/_page-header :: page-header(title, summary, breadcrumb, primary, secondary)}`.

**This retires `page-header-actions`,** the ad-hoc class in 35 templates, in favour of
`.page-header__actions` inside this fragment. One test depends on the old name:
`SurfaceSeparationContractTest` scans `.page-header-actions` for `.btn-danger` to keep
destructive controls out of read/run headers. It is a *selector* dependency, so it does not
break loudly — it simply matches nothing and passes. Task 19 Step 4 re-points it at the
`#page-header` slot and adds a non-vacuity guard; do not delete the old class before then.

- [ ] **Step 1: Write the failing contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 8.4: one header contract, one principal title, one primary action. */
class PageHeaderContractTest {

    private static final Path HEADER =
            Path.of("src/main/resources/templates/fragments/_page-header.html");

    @Test
    void theHeaderExposesTheApprovedFiveSlots() {
        assertThat(TemplateRules.read(HEADER))
                .contains("th:fragment=\"page-header(title, summary, breadcrumb, primary, secondary)\"");
    }

    @Test
    void theHeaderRendersExactlyOneHeadingElement() {
        assertThat(TemplateRules.parse(HEADER).select("h1")).hasSize(1);
    }

    @Test
    void onlyTheHeaderTitleCarriesTheDisplayTypeface() {
        var document = TemplateRules.parse(HEADER);
        assertThat(document.select("[data-display-title]")).hasSize(1);
        assertThat(document.select("h1").first().hasAttr("data-display-title")).isTrue();
    }

    @Test
    void everyMigratedPageHasOneHeaderAndAtMostOnePrimaryAction() {
        for (Path template : TemplateRules.pageTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("fragments/_shell :: page")) continue;
            assertThat(markup.split("_page-header :: page-header", -1).length - 1)
                    .as("page headers in %s", template).isEqualTo(1);
            assertThat(markup.split("btn-primary", -1).length - 1)
                    .as("filled primary actions in %s (spec 6.3)", template)
                    .isLessThanOrEqualTo(1);
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='PageHeaderContractTest' test
```

Expected: FAIL — the fragment does not exist.

- [ ] **Step 3: Write the fragment**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<header class="page-header" data-region="page-header"
        th:fragment="page-header(title, summary, breadcrumb, primary, secondary)">
    <nav class="page-header__breadcrumb" aria-label="Breadcrumb">
        <th:block th:replace="${breadcrumb}"></th:block>
    </nav>
    <div class="page-header__row">
        <div class="page-header__identity">
            <h1 class="page-header__title" data-display-title th:text="${title}">Title</h1>
            <p class="page-header__summary" th:if="${summary != null}" th:text="${summary}">Summary</p>
        </div>
        <div class="page-header__actions" data-action-region>
            <div class="page-header__secondary"><th:block th:replace="${secondary}"></th:block></div>
            <div class="page-header__primary"><th:block th:replace="${primary}"></th:block></div>
        </div>
    </div>
</header>
</html>
```

- [ ] **Step 4: Style it in `components.css`**

```css
.page-header { display: flex; flex-direction: column; gap: var(--space-2xs); }
.page-header__row {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: var(--page-gap); flex-wrap: wrap;
}
.page-header__title {
  font-family: var(--font-display);
  font-size: var(--text-2xl); line-height: 1.2; color: var(--text-primary); margin: 0;
}
.page-header__summary { color: var(--text-secondary); font-size: var(--text-sm); margin: 0; }
.page-header__breadcrumb { font-size: var(--text-xs); color: var(--text-tertiary); }
.page-header__breadcrumb:empty { display: none; }
.page-header__actions { display: flex; align-items: center; gap: var(--space-sm); }
.page-header__secondary { display: flex; align-items: center; gap: var(--space-2xs); }
```

- [ ] **Step 5: Run it**

```bash
./mvnw -Dtest='PageHeaderContractTest,TypographyRoleContractTest,GoldAccentContractTest' test
```

Expected: the three fragment-level tests PASS.
`everyMigratedPageHasOneHeaderAndAtMostOnePrimaryAction` passes trivially now (only
`about.html` is migrated) and stays green through Task 19.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/fragments/_page-header.html src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/config/PageHeaderContractTest.java
git commit -m "feat: add the shared page header contract"
```

## Task 16: Build the toolbar, badge, and contextual rail contracts

**Files:**
- Create: `src/main/resources/templates/fragments/_toolbar.html`
- Create: `src/main/resources/templates/fragments/_badge.html`
- Create: `src/main/resources/templates/fragments/_context-rail.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/SharedComponentContractTest.java`

**Interfaces:**
- Produces: `toolbar(action, searchValue, searchPlaceholder, filters, actions)`,
  `table-toolbar(selectionLabel, actions)`, `badge(tone, icon, label)`,
  `rail(body)`, `rail-section(title, body)`.

- [ ] **Step 1: Write the failing shared-component contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 10: features specialize content, never recreate the primitives. */
class SharedComponentContractTest {

    private static final List<String> REQUIRED_FRAGMENTS = List.of(
            "fragments/_page-header.html|page-header(title, summary, breadcrumb, primary, secondary)",
            "fragments/_toolbar.html|toolbar(action, searchValue, searchPlaceholder, filters, actions)",
            "fragments/_toolbar.html|table-toolbar(selectionLabel, actions)",
            "fragments/_badge.html|badge(tone, icon, label)",
            "fragments/_context-rail.html|rail(body)",
            "fragments/_context-rail.html|rail-section(title, body)",
            "fragments/_states.html|empty(icon, title, description, cta)",
            "fragments/_states.html|loading(label)",
            "fragments/_states.html|skeleton(count)",
            "fragments/_states.html|unavailable(title, description, retry)",
            "fragments/_states.html|failed(title, description, retry)",
            "fragments/_banner.html|banner(tone, title, body, actions)",
            "fragments/_status.html|save-status(id)",
            "fragments/_overlay.html|dialog(id, title, body, actions)",
            "fragments/_overlay.html|side-sheet(id, title, body)",
            "fragments/_overlay.html|popover(id, label, body)",
            "fragments/_overlay.html|toast-region");

    @Test
    void everySharedContractExistsWithItsApprovedSignature() {
        for (String required : REQUIRED_FRAGMENTS) {
            String[] parts = required.split("\\|", 2);
            Path file = TemplateRules.ROOT.resolve(parts[0]);
            assertThat(file).as("%s exists", parts[0]).exists();
            assertThat(TemplateRules.read(file))
                    .as("fragment %s in %s", parts[1], parts[0])
                    .contains("th:fragment=\"" + parts[1] + "\"");
        }
    }

    @Test
    void featureTemplatesDoNotRecreateThePrimitives() {
        for (Path template : TemplateRules.allTemplates()) {
            if (template.toString().contains("/fragments/")) continue;
            String markup = TemplateRules.read(template);
            for (String privateCopy : List.of("class=\"toolbar\"", "class=\"page-rail\"",
                    "class=\"toast\"", "class=\"side-sheet\"", "class=\"page-header\"")) {
                assertThat(markup)
                        .as("%s reimplements %s — use the shared fragment", template, privateCopy)
                        .doesNotContain(privateCopy);
            }
        }
    }

    @Test
    void badgeTonesAreTheStableSemanticSet() {
        String css = CssRules.allApplicationCss();
        for (String tone : List.of("success", "warning", "danger", "info", "shield", "neutral")) {
            assertThat(css).as("badge tone %s", tone).contains(".badge--" + tone);
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='SharedComponentContractTest' test
```

Expected: FAIL — `fragments/_toolbar.html exists` is false.

- [ ] **Step 3: Write `_toolbar.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="toolbar" data-region="toolbar"
     th:fragment="toolbar(action, searchValue, searchPlaceholder, filters, actions)">
    <form class="toolbar__search" role="search" method="get" th:action="${action}">
        <label class="sr-only" for="toolbarSearch">Search</label>
        <th:block th:replace="~{common/_icon :: icon-sized(name='search', size='16')}"></th:block>
        <input type="search" id="toolbarSearch" name="q" class="toolbar__input"
               th:value="${searchValue}" th:placeholder="${searchPlaceholder}">
    </form>
    <div class="toolbar__filters"><th:block th:replace="${filters}"></th:block></div>
    <div class="toolbar__spacer"></div>
    <div class="toolbar__actions" data-action-region>
        <th:block th:replace="${actions}"></th:block>
    </div>
</div>

<div class="toolbar toolbar--table" data-region="table-toolbar" role="toolbar"
     th:fragment="table-toolbar(selectionLabel, actions)">
    <span class="toolbar__selection" aria-live="polite" th:text="${selectionLabel}">0 selected</span>
    <div class="toolbar__spacer"></div>
    <div class="toolbar__actions" data-action-region>
        <th:block th:replace="${actions}"></th:block>
    </div>
</div>
</html>
```

- [ ] **Step 4: Write `_badge.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<span th:fragment="badge(tone, icon, label)"
      th:class="'badge badge--' + ${tone}">
    <th:block th:if="${icon != null}"
              th:replace="~{common/_icon :: icon-sized(name=${icon}, size='16')}"></th:block>
    <span class="badge__label" th:text="${label}">Label</span>
</span>
</html>
```

Every badge therefore carries a label as well as a tone, satisfying the "never color alone"
rule structurally.

- [ ] **Step 5: Write `_context-rail.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<aside class="page-rail" data-region="rail" th:fragment="rail(body)"
       aria-label="Related information">
    <th:block th:replace="${body}"></th:block>
</aside>

<section class="rail-section" th:fragment="rail-section(title, body)">
    <h2 class="rail-section__title" th:text="${title}">Section</h2>
    <div class="rail-section__body"><th:block th:replace="${body}"></th:block></div>
</section>
</html>
```

- [ ] **Step 6: Style all three in `components.css`**

```css
.toolbar {
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  background: var(--surface-panel);
  border: 1px solid var(--border-subtle); border-radius: var(--radius);
}
.toolbar__spacer { flex: 1; }
.toolbar__search { display: flex; align-items: center; gap: var(--space-2xs); }
.toolbar__input {
  min-width: 18rem; background: var(--surface-inset);
  border: 1px solid var(--border-strong); border-radius: var(--radius);
  color: var(--text-primary); padding: var(--space-2xs) var(--space-xs);
}
.toolbar__filters { display: flex; align-items: center; gap: var(--space-2xs); flex-wrap: wrap; }
.toolbar__selection { color: var(--text-secondary); font-size: var(--text-sm); }

.badge {
  display: inline-flex; align-items: center; gap: 0.25em;
  padding: 0.1em 0.5em; border-radius: var(--radius);
  font-size: var(--text-sm); line-height: 1.5;
  border: 1px solid var(--border-subtle); background: var(--surface-raised);
  color: var(--text-secondary);
}
.badge--neutral { }
.badge--success { color: var(--state-success); background: var(--state-success-surface); border-color: var(--state-success-border); }
.badge--warning { color: var(--state-warning); background: var(--state-warning-surface); border-color: var(--state-warning-border); }
.badge--danger  { color: var(--state-danger);  background: var(--state-danger-surface);  border-color: var(--state-danger-border); }
.badge--info    { color: var(--state-info);    background: var(--state-info-surface);    border-color: var(--state-info-border); }
.badge--shield  { color: var(--state-shield);  background: var(--state-shield-surface);  border-color: var(--state-shield-border); }

.page-rail { display: flex; flex-direction: column; gap: var(--space-md); min-width: 0; }
.rail-section {
  background: var(--surface-panel); border: 1px solid var(--border-subtle);
  border-radius: var(--radius); padding: var(--space-sm);
}
.rail-section__title {
  font-size: var(--text-sm); font-weight: 600; color: var(--text-secondary);
  margin: 0 0 var(--space-xs);
}
```

Delete the superseded `.badge-success`, `.badge-warning`, `.badge-danger`, `.badge-info`
rules and repoint their callers in Stages 3–5; keep the domain-specific rarity and item-state
badges, which are content, not chrome.

- [ ] **Step 7: Run the contract**

```bash
./mvnw -Dtest='SharedComponentContractTest' test
```

Expected: FAIL only on the fragments Tasks 17 and 18 create. The toolbar, badge, and rail
assertions pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/fragments src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/config/SharedComponentContractTest.java
git commit -m "feat: add toolbar, badge, and contextual rail contracts"
```

## Task 17: Build the feedback and state contracts

**Files:**
- Create: `src/main/resources/templates/fragments/_states.html`
- Create: `src/main/resources/templates/fragments/_banner.html`
- Create: `src/main/resources/templates/fragments/_status.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/AsyncStateContractTest.java`

**Interfaces:**
- Produces: `empty`, `loading`, `skeleton`, `unavailable`, `failed`, `banner`, `save-status`.

- [ ] **Step 1: Write the failing async-state contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 16: every asynchronous surface defines its full state set. */
class AsyncStateContractTest {

    @Test
    void loadingAndLiveRegionsAreAnnounced() {
        String states = TemplateRules.read(TemplateRules.ROOT.resolve("fragments/_states.html"));
        assertThat(states).contains("aria-live=\"polite\"").contains("role=\"status\"");
        assertThat(TemplateRules.read(TemplateRules.ROOT.resolve("fragments/_banner.html")))
                .contains("role=\"alert\"");
    }

    @Test
    void recoverableFailuresOfferRetry() {
        String states = TemplateRules.read(TemplateRules.ROOT.resolve("fragments/_states.html"));
        assertThat(states).contains("th:fragment=\"failed(title, description, retry)\"")
                .contains("th:fragment=\"unavailable(title, description, retry)\"");
    }

    @Test
    void skeletonsReserveLayoutRatherThanCollapseIt() {
        var skeleton = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().contains(".skeleton"))
                .toList();
        assertThat(skeleton).as("skeleton rules").isNotEmpty();
        assertThat(skeleton).anySatisfy(rule -> assertThat(rule.value("min-height")).isNotNull());
    }

    @Test
    void everyHtmxTargetDeclaresAnIndicatorOrSwapPreservingRegion() {
        for (Path template : TemplateRules.allTemplates()) {
            var document = TemplateRules.parse(template);
            for (var trigger : document.select("[hx-get], [hx-post], [hx-put], [hx-delete]")) {
                boolean declared = trigger.hasAttr("hx-indicator")
                        || trigger.hasAttr("hx-disabled-elt")
                        || trigger.hasAttr("hx-sync")
                        || trigger.hasAttr("data-no-indicator");
                assertThat(declared)
                        .as("%s: %s needs hx-indicator, hx-disabled-elt, hx-sync, or an explicit "
                                + "data-no-indicator opt-out", template, trigger.tagName())
                        .isTrue();
            }
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='AsyncStateContractTest' test
```

Expected: FAIL — `fragments/_states.html` does not exist, and the htmx assertion lists every
trigger lacking a declaration.

- [ ] **Step 3: Write `_states.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="state state--empty" th:fragment="empty(icon, title, description, cta)">
    <th:block th:replace="~{common/_icon :: icon-sized(name=${icon}, size='24')}"></th:block>
    <p class="state__title" th:text="${title}">Nothing here yet</p>
    <p class="state__description" th:if="${description != null}" th:text="${description}">Why</p>
    <div class="state__cta"><th:block th:replace="${cta}"></th:block></div>
</div>

<div class="state state--loading" role="status" aria-live="polite"
     th:fragment="loading(label)">
    <span class="spinner" aria-hidden="true"></span>
    <span class="state__title" th:text="${label}">Loading…</span>
</div>

<div class="skeleton-group" aria-hidden="true" th:fragment="skeleton(count)">
    <div class="skeleton" th:each="i : ${#numbers.sequence(1, count)}"></div>
</div>

<div class="state state--unavailable" role="status" aria-live="polite"
     th:fragment="unavailable(title, description, retry)">
    <th:block th:replace="~{common/_icon :: icon-sized(name='alert-circle', size='24')}"></th:block>
    <p class="state__title" th:text="${title}">Unavailable</p>
    <p class="state__description" th:if="${description != null}" th:text="${description}">Why</p>
    <div class="state__cta"><th:block th:replace="${retry}"></th:block></div>
</div>

<div class="state state--failed" role="status" aria-live="polite"
     th:fragment="failed(title, description, retry)">
    <th:block th:replace="~{common/_icon :: icon-sized(name='alert-triangle', size='24')}"></th:block>
    <p class="state__title" th:text="${title}">That did not work</p>
    <p class="state__description" th:if="${description != null}" th:text="${description}">Why</p>
    <div class="state__cta"><th:block th:replace="${retry}"></th:block></div>
</div>
</html>
```

- [ ] **Step 4: Write `_banner.html` and `_status.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="banner(tone, title, body, actions)" role="alert"
     th:class="'banner banner--' + ${tone}">
    <th:block th:replace="~{common/_icon :: icon-sized(
        name=${tone == 'danger' ? 'alert-triangle' : (tone == 'warning' ? 'alert-circle' :
             (tone == 'success' ? 'check' : (tone == 'shield' ? 'shield' : 'info')))},
        size='20')}"></th:block>
    <div class="banner__text">
        <p class="banner__title" th:text="${title}">Title</p>
        <p class="banner__body" th:if="${body != null}" th:text="${body}">Body</p>
    </div>
    <div class="banner__actions"><th:block th:replace="${actions}"></th:block></div>
</div>
</html>
```

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<span th:fragment="save-status(id)" th:id="${id}" class="save-status"
      role="status" aria-live="polite" data-save-status="idle">
    <span class="save-status__dot" aria-hidden="true"></span>
    <span class="save-status__label">Saved</span>
</span>
</html>
```

- [ ] **Step 5: Style them in `components.css`**

```css
.state {
  display: flex; flex-direction: column; align-items: center; gap: var(--space-xs);
  padding: var(--space-xl) var(--space-md); text-align: center;
  color: var(--text-secondary);
  background: var(--surface-panel); border: 1px solid var(--border-subtle);
  border-radius: var(--radius);
}
.state__title { color: var(--text-primary); font-weight: 600; margin: 0; }
.state__description { margin: 0; max-width: 46ch; }
.state--failed { border-color: var(--state-danger-border); background: var(--state-danger-surface); }
.state--unavailable { border-color: var(--state-warning-border); background: var(--state-warning-surface); }

.skeleton-group { display: flex; flex-direction: column; gap: var(--space-xs); }
.skeleton {
  min-height: 2.5rem; border-radius: var(--radius);
  background: var(--surface-raised);
}
@media (prefers-reduced-motion: no-preference) {
  .skeleton { animation: skeleton-pulse 1.4s ease-in-out infinite; }
  @keyframes skeleton-pulse { 50% { opacity: 0.55; } }
}

.banner {
  display: flex; align-items: flex-start; gap: var(--space-xs);
  padding: var(--space-sm); border-radius: var(--radius);
  border: 1px solid var(--border-subtle); background: var(--surface-panel);
}
.banner__text { flex: 1; }
.banner__title { margin: 0; font-weight: 600; color: var(--text-primary); }
.banner__body { margin: 0; color: var(--text-secondary); font-size: var(--text-sm); }
.banner--success { border-color: var(--state-success-border); background: var(--state-success-surface); color: var(--state-success); }
.banner--warning { border-color: var(--state-warning-border); background: var(--state-warning-surface); color: var(--state-warning); }
.banner--danger  { border-color: var(--state-danger-border);  background: var(--state-danger-surface);  color: var(--state-danger); }
.banner--info    { border-color: var(--state-info-border);    background: var(--state-info-surface);    color: var(--state-info); }
.banner--shield  { border-color: var(--state-shield-border);  background: var(--state-shield-surface);  color: var(--state-shield); }

.save-status { display: inline-flex; align-items: center; gap: var(--space-2xs);
  color: var(--text-tertiary); font-size: var(--text-sm); }
.save-status__dot { width: 0.5em; height: 0.5em; border-radius: 50%; background: var(--text-tertiary); }
.save-status[data-save-status="saving"] .save-status__dot { background: var(--state-info); }
.save-status[data-save-status="saved"] .save-status__dot { background: var(--state-success); }
.save-status[data-save-status="error"] .save-status__dot { background: var(--state-danger); }
```

- [ ] **Step 6: Declare htmx indicators on every trigger the test named**

For each reported element add `hx-indicator` pointing at the region that shows the loading
state, or `hx-disabled-elt="this"` for a button that disables itself, or
`data-no-indicator` with a one-line comment when the swap is genuinely instantaneous and
local. Do not blanket-apply `data-no-indicator`.

- [ ] **Step 7: Run it and watch it pass**

```bash
./mvnw -Dtest='AsyncStateContractTest,SharedComponentContractTest' test
```

Expected: `AsyncStateContractTest` PASSES. `SharedComponentContractTest` still fails on the
overlay fragments (Task 18).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/config/AsyncStateContractTest.java
git commit -m "feat: add feedback, state, and banner contracts"
```

## Task 18: Build the overlay elevation model

**Files:**
- Create: `src/main/resources/templates/fragments/_overlay.html`
- Create: `src/main/resources/static/js/ui-overlay.js`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/OverlayContractTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/OverlayBehaviorGateTest.java`

**Interfaces:**
- Produces: `dialog`, `side-sheet`, `popover`, `toast-region`, `window.dmOverlay`,
  `window.dmToast`.

- [ ] **Step 1: Write the failing static overlay contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 15: five levels, shared accessibility rules. */
class OverlayContractTest {

    private static final Path OVERLAY =
            Path.of("src/main/resources/templates/fragments/_overlay.html");

    @Test
    void blockingOverlaysDeclareRoleAndAccessibleName() {
        var document = TemplateRules.parse(OVERLAY);
        var dialog = document.selectFirst("[th\\:fragment^=dialog]");
        assertThat(dialog).isNotNull();
        assertThat(dialog.attr("role")).isEqualTo("dialog");
        assertThat(dialog.attr("aria-modal")).isEqualTo("true");
        assertThat(dialog.hasAttr("aria-labelledby")).isTrue();
    }

    @Test
    void everyOverlayLevelHasItsOwnElevationAndScrimRule() {
        String css = CssRules.allApplicationCss();
        for (String level : List.of(".popover", ".side-sheet", ".dialog", ".toast")) {
            assertThat(css).as("style for %s", level).contains(level);
        }
        var dialog = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().equals(".dialog__backdrop"))
                .findFirst().orElseThrow();
        assertThat(dialog.value("background")).contains("--scrim");
    }

    @Test
    void aPersistentErrorIsNeverOnlyAToast() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("dmToast.show")) continue;
            assertThat(markup)
                    .as("%s raises a toast; persistent failure also needs a banner or state "
                            + "region (spec 15)", template)
                    .containsAnyOf("_banner :: banner", "_states :: failed", "_states :: unavailable");
        }
    }

    /**
     * .dialog sets `display: grid` and .side-sheet sets `display: flex`; both outrank the
     * UA stylesheet's `[hidden] { display: none }`, so the hidden attribute is inert unless
     * the sheet resets it explicitly.
     */
    @Test
    void theHiddenAttributeActuallyHidesEveryOverlayLevel() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        for (String level : List.of(".dialog", ".side-sheet", ".popover")) {
            assertThat(rules)
                    .as("%s[hidden] must reset display", level)
                    .anySatisfy(rule -> {
                        assertThat(rule.selector()).contains(level + "[hidden]");
                        assertThat(rule.value("display")).isEqualTo("none");
                    });
        }
    }

    @Test
    void overlaysBoundThemselvesToTheViewport() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        for (String level : List.of(".dialog__panel", ".side-sheet", ".popover")) {
            var rule = rules.stream().filter(r -> r.selector().equals(level)).findFirst().orElseThrow();
            assertThat(rule.value("max-height")).as("%s max-height", level).isNotNull();
            assertThat(rule.value("overflow")).as("%s overflow", level).isNotNull();
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='OverlayContractTest' test
```

Expected: FAIL — the fragment does not exist.

- [ ] **Step 3: Write `_overlay.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="dialog(id, title, body, actions)" th:id="${id}"
     class="dialog" role="dialog" aria-modal="true"
     th:attr="aria-labelledby=${id} + 'Title'" hidden>
    <div class="dialog__backdrop" data-overlay-dismiss></div>
    <div class="dialog__panel">
        <div class="dialog__header">
            <h2 class="dialog__title" th:id="${id} + 'Title'" th:text="${title}">Title</h2>
            <button type="button" class="icon-btn" data-overlay-dismiss aria-label="Close">
                <th:block th:replace="~{common/_icon :: icon(name='close')}"></th:block>
            </button>
        </div>
        <div class="dialog__body"><th:block th:replace="${body}"></th:block></div>
        <div class="dialog__actions" data-action-region>
            <th:block th:replace="${actions}"></th:block>
        </div>
    </div>
</div>

<aside th:fragment="side-sheet(id, title, body)" th:id="${id}"
       class="side-sheet" role="complementary"
       th:attr="aria-labelledby=${id} + 'Title'" hidden>
    <div class="side-sheet__header">
        <h2 class="side-sheet__title" th:id="${id} + 'Title'" th:text="${title}">Title</h2>
        <button type="button" class="icon-btn" data-overlay-dismiss aria-label="Close">
            <th:block th:replace="~{common/_icon :: icon(name='close')}"></th:block>
        </button>
    </div>
    <div class="side-sheet__body"><th:block th:replace="${body}"></th:block></div>
</aside>

<div th:fragment="popover(id, label, body)" th:id="${id}" class="popover"
     role="menu" th:attr="aria-label=${label}" hidden>
    <th:block th:replace="${body}"></th:block>
</div>

<div th:fragment="toast-region" class="toast-region" id="toastRegion"
     role="status" aria-live="polite"></div>
</html>
```

- [ ] **Step 4: Write `ui-overlay.js`**

```js
(function () {
    const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), '
        + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const triggers = new WeakMap();
    let trapped = null;

    function focusables(root) {
        return [...root.querySelectorAll(FOCUSABLE)].filter(el => el.offsetParent !== null);
    }

    function open(element) {
        triggers.set(element, document.activeElement);
        element.hidden = false;
        const blocking = element.getAttribute('aria-modal') === 'true';
        if (blocking) trapped = element;
        const first = focusables(element)[0];
        if (first) first.focus();
    }

    function close(element) {
        element.hidden = true;
        if (trapped === element) trapped = null;
        const trigger = triggers.get(element);
        if (trigger && document.contains(trigger)) trigger.focus();
        triggers.delete(element);
    }

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            const openOverlay = document.querySelector(
                '.dialog:not([hidden]), .side-sheet:not([hidden]), .popover:not([hidden])');
            if (openOverlay) {
                event.preventDefault();
                close(openOverlay);
            }
            return;
        }
        if (event.key !== 'Tab' || !trapped) return;
        const items = focusables(trapped);
        if (items.length === 0) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });

    document.addEventListener('click', event => {
        const dismiss = event.target.closest('[data-overlay-dismiss]');
        if (!dismiss) return;
        const overlay = dismiss.closest('.dialog, .side-sheet, .popover');
        if (overlay) close(overlay);
    });

    window.dmOverlay = { open, close };

    window.dmToast = {
        show(message, tone = 'neutral') {
            const region = document.getElementById('toastRegion');
            if (!region) return;
            const toast = document.createElement('div');
            toast.className = 'toast toast--' + tone;
            toast.textContent = message;
            region.appendChild(toast);
            setTimeout(() => toast.remove(), 5000);
        }
    };
})();
```

- [ ] **Step 5: Style the four levels in `components.css`**

```css
/* MUST come first. A class rule that sets `display` outranks the UA `[hidden] {display:none}`
   on specificity, so without this every dialog and side sheet renders open on page load. */
.dialog[hidden],
.side-sheet[hidden],
.popover[hidden] { display: none; }

.dialog { position: fixed; inset: 0; z-index: var(--z-modal); display: grid; place-items: center; }
.dialog__backdrop { position: absolute; inset: 0; background: var(--scrim-standard); }
.dialog__panel {
  position: relative; width: min(640px, calc(100vw - 4rem));
  max-height: calc(100vh - 4rem); overflow: auto;
  background: var(--surface-overlay); border: 1px solid var(--border-strong);
  border-radius: var(--radius); box-shadow: var(--shadow-overlay);
  display: flex; flex-direction: column;
}
.dialog__header, .side-sheet__header {
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-sm); padding: var(--space-sm);
  border-bottom: 1px solid var(--border-subtle);
}
.dialog__body, .side-sheet__body { padding: var(--space-md); overflow: auto; }
.dialog__actions {
  display: flex; justify-content: flex-end; gap: var(--space-sm);
  padding: var(--space-sm); border-top: 1px solid var(--border-subtle);
}

.side-sheet {
  position: fixed; inset-block: var(--topbar-height) 0; inset-inline-end: 0;
  width: min(480px, 40vw); max-height: calc(100vh - var(--topbar-height)); overflow: auto;
  z-index: var(--z-floating);
  background: var(--surface-panel); border-left: 1px solid var(--border-strong);
  box-shadow: var(--shadow-floating);
  display: flex; flex-direction: column;
}

.popover {
  position: absolute; z-index: var(--z-floating);
  min-width: 12rem; max-height: 60vh; overflow: auto;
  background: var(--surface-overlay); border: 1px solid var(--border-strong);
  border-radius: var(--radius); box-shadow: var(--shadow-floating);
  padding: var(--space-2xs);
}
.popover--end { inset-inline-end: 0; }
.popover__item {
  display: block; padding: var(--space-2xs) var(--space-xs);
  color: var(--text-secondary); text-decoration: none; border-radius: var(--radius);
}
.popover__item:hover { background: var(--neutral-hover-surface); color: var(--text-primary); }

.toast-region {
  position: fixed; inset-block-end: var(--space-md); inset-inline-end: var(--space-md);
  z-index: var(--z-toast); display: flex; flex-direction: column; gap: var(--space-2xs);
}
.toast {
  padding: var(--space-xs) var(--space-sm);
  background: var(--surface-overlay); color: var(--text-primary);
  border: 1px solid var(--border-strong); border-radius: var(--radius);
  box-shadow: var(--shadow-floating);
}
.toast--success { border-color: var(--state-success-border); }
.toast--danger  { border-color: var(--state-danger-border); }
```

Add `--z-toast` to `tokens.css` above `--z-modal` if the elevation ladder does not already
define it, and update `ElevationModelContractTest` to include it.

- [ ] **Step 6: Write the failing overlay behavior gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverlayBehaviorGateTest {

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures = new BrowserFailureCollector();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
        page = context.newPage();
        failures.attach(page);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void escapeClosesADialogAndRestoresFocusToItsTrigger() {
        page.evaluate("""
                () => {
                  const t = document.createElement('button');
                  t.id = 'overlayTrigger';
                  t.textContent = 'Open';
                  document.querySelector('main').prepend(t);
                  const d = document.createElement('div');
                  d.className = 'dialog';
                  d.id = 'probeDialog';
                  d.setAttribute('role', 'dialog');
                  d.setAttribute('aria-modal', 'true');
                  d.hidden = true;
                  d.innerHTML = '<div class="dialog__panel"><button id="inside">Inside</button></div>';
                  document.body.appendChild(d);
                  t.addEventListener('click', () => window.dmOverlay.open(d));
                }
                """);
        page.click("#overlayTrigger");
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("inside");
        page.keyboard().press("Escape");
        assertThat(page.evaluate("() => document.getElementById('probeDialog').hidden")).isEqualTo(true);
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("overlayTrigger");
    }

    @Test
    void tabWrapsInsideABlockingDialog() {
        page.evaluate("""
                () => {
                  const d = document.createElement('div');
                  d.className = 'dialog';
                  d.setAttribute('role', 'dialog');
                  d.setAttribute('aria-modal', 'true');
                  d.hidden = true;
                  d.innerHTML = '<div class="dialog__panel">'
                    + '<button id="a">A</button><button id="b">B</button></div>';
                  document.body.appendChild(d);
                  window.dmOverlay.open(d);
                }
                """);
        page.keyboard().press("Tab");
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("b");
        page.keyboard().press("Tab");
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("a");
    }
}
```

- [ ] **Step 7: Run both**

```bash
./mvnw -Dtest='OverlayContractTest,OverlayBehaviorGateTest,SharedComponentContractTest' test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/fragments/_overlay.html src/main/resources/static/js/ui-overlay.js src/main/resources/static/css src/test/java/dev/hendrikhoemberg/dmhelper
git commit -m "feat: add the overlay elevation model with focus trap and restore"
```

## Task 14: Build the shell layout fragment

**Files:**
- Create: `src/main/resources/templates/fragments/_shell.html`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/resources/static/css/base.css`

**Interfaces:**
- Produces: `~{fragments/_shell :: page(pageTitle, archetype, surface, header, content, rail)}` and
  `~{fragments/head :: document-head(pageTitle)}`.
- Consumes: `~{fragments/_topbar :: topbar}`, `~{fragments/_rail :: rail}`,
  `~{fragments/_page-header :: page-header}`, `~{fragments/_overlay :: toast-region}`.

The shell takes a third markup slot, `rail`, because the Detail archetype's grid needs the
contextual rail as a **direct** child of `.page`. A rail nested inside the content slot can
never match `.page--detail > .page-rail-slot`. Non-detail pages pass `rail=~{}`.

Run this task **after** Tasks 15–18: the shell renders the page header and the toast region,
and `document-head` loads `ui-overlay.js`, all of which those tasks create.

This uses Thymeleaf's fragment-expression parameters, so a page template becomes a
`th:replace` on its own `<html>` element and supplies its header and content as markup
selectors into itself.

- [ ] **Step 1: Add the parameterized head fragment**

Add to `fragments/head.html`, keeping the existing `head` fragment for now:

```html
<head th:fragment="document-head(pageTitle)">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="${pageTitle} + ' · DMHelper'">DMHelper</title>
    <link rel="icon" th:href="@{/icons/favicon.svg}">
    <link rel="stylesheet" th:href="@{/css/tokens.css}">
    <link rel="stylesheet" th:href="@{/css/base.css}">
    <link rel="stylesheet" th:href="@{/css/components.css}">
    <link rel="stylesheet" th:href="@{/css/book.css}">
    <link rel="stylesheet" th:href="@{/css/surfaces.css}">
    <script th:src="@{/vendor/htmx.min.js}"></script>
    <script th:src="@{/js/dm-request.js}"></script>
    <script th:src="@{/js/keyboard.js}"></script>
    <script th:src="@{/js/command-palette.js}"></script>
    <script th:src="@{/js/quicknotes.js}"></script>
    <script th:src="@{/js/ui-elevation.js}"></script>
    <script th:src="@{/js/ui-overlay.js}"></script>
    <script th:src="@{/js/runtime-status.js}" defer></script>
    <script th:src="@{/js/campaign-import.js}" defer></script>
    <script th:src="@{/vendor/alpine.min.js}" defer></script>
</head>
```

`fragments/head.html` currently ships no favicon link at all, so there is nothing to move —
author `src/main/resources/static/icons/favicon.svg` fresh, drawn with the `--surface-canvas`
and `--action-primary` hex values so no raw color literal ever enters markup. Keep the
existing `head` fragment alongside `document-head` until Task 19 Step 4 deletes it.

- [ ] **Step 2: Write `_shell.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      th:fragment="page(pageTitle, archetype, surface, header, content, rail)">
<head th:replace="~{fragments/head :: document-head(pageTitle=${pageTitle})}"></head>
<body th:attr="data-archetype=${archetype}">
    <a class="skip-link" href="#main-content">Skip to content</a>
    <th:block th:replace="~{fragments/_topbar :: topbar}"></th:block>
    <div class="app-shell">
        <th:block th:replace="~{fragments/_rail :: rail}"></th:block>
        <main class="app-main" id="main-content" th:attr="data-surface=${surface}">
            <div th:class="'page page--' + ${archetype}">
                <th:block th:replace="${header}"></th:block>
                <th:block th:replace="${content}"></th:block>
                <th:block th:replace="${rail}"></th:block>
            </div>
        </main>
    </div>
    <th:block th:replace="~{fragments/_overlay :: toast-region}"></th:block>
</body>
</html>
```

**Two classifications, deliberately kept apart.** `archetype` is a *layout* decision — it
selects `.page--index|detail|form|editor|operational` and nothing else. `surface` is a
*safety* decision from spec 2026-07-24 §D: `read`, `run`, `edit`, or `admin`. They do not
map onto each other — `campaigns/settings.html` is `surface='admin'` but `archetype='form'`,
and `session/cockpit.html` is `surface='run'` but `archetype='editor'`. `SurfaceModeContractTest`
and `SurfaceSeparationContractTest` key off `data-surface`; dropping it during this migration
would silently retire the guard that keeps destructive tooling out of pages a DM uses
mid-session.

Pages outside the governed set pass no surface at all. `th:attr` omits an attribute whose
expression is null, so `surface=null` renders `<main class="app-main" id="main-content">`
with no `data-surface` — which is what `about.html` and the other ungoverned pages want.

- [ ] **Step 3: Add the skip link to `base.css`**

```css
.skip-link {
  position: absolute; left: var(--space-sm); top: calc(var(--space-sm) * -4);
  z-index: var(--z-overlay);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--surface-raised); color: var(--text-primary);
  border: 1px solid var(--border-strong); border-radius: var(--radius);
  transition: top var(--duration-fast) ease;
}
.skip-link:focus-visible { top: var(--space-sm); }
```

- [ ] **Step 4: Prove the shell renders by migrating one page**

Convert `src/main/resources/templates/about.html` first — it is the smallest page and has
no htmx. The markup selectors are element ids in the page's own body (`~{::#page-header}`),
which is the pattern every later migration copies:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/_shell :: page(
          pageTitle='About',
          archetype='detail',
          surface=null,
          header=~{::#page-header},
          content=~{::#page-content},
          rail=~{})}">
<body>
<div id="page-header" class="page-header-slot">
    <th:block th:replace="~{fragments/_page-header :: page-header(
        title='About DMHelper', summary='Attribution and licences.',
        breadcrumb=~{}, primary=~{}, secondary=~{})}"></th:block>
</div>

<div id="page-content" class="page-content prose">
    <!-- existing about content, unchanged -->
</div>
</body>
</html>
```

The `<body>` wrapper exists only so the selectors resolve; the shell fragment replaces the
whole `<html>` element, so nothing from this file's own skeleton reaches the response.

- [ ] **Step 5: Run the page in a browser**

```bash
./mvnw -Dtest='FullPageRenderSmokeTest' test
```

Expected: PASS for `/library/about`. If Thymeleaf reports an unresolved fragment, the
selector syntax is wrong — check that the `id` exists in the same template.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/fragments src/main/resources/templates/about.html src/main/resources/static/css/base.css src/main/resources/static/icons/favicon.svg
git commit -m "feat: add the shared shell layout fragment"
```

## Task 19: Migrate every page onto the shell and run the Stage 2 gate

**Files:**
- Modify: all 59 templates listed below
- Delete: `src/main/resources/templates/fragments/navbar.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ShellRenderGateTest.java`

**Interfaces:**
- Consumes: every fragment built in Tasks 12–18.

### Archetype assignment

| Template | Archetype |
|---|---|
| `about.html` | detail |
| `adventure/list.html` | index |
| `adventure/detail.html` | detail |
| `adventure/scene-detail.html` | detail |
| `adventure/scene-structure.html` | operational |
| `audio/list.html` | index |
| `audio/detail.html` | detail |
| `audio/form.html` | form |
| `calendar/overview.html` | operational |
| `campaigns/list.html` | index |
| `campaigns/detail.html` | operational |
| `campaigns/new.html` | form |
| `campaigns/settings.html` | form |
| `encounter/list.html` | index |
| `encounter/detail.html` | detail |
| `encounter/new.html` | form |
| `encounter/setup.html` | operational |
| `handout/list.html` | index |
| `ledger/list.html` | operational |
| `library/list.html` | index |
| `library/form.html` | form |
| `library/detail.html` and every `library/*-detail.html` | detail |
| `maps/list.html` | index |
| `maps/new.html` | form |
| `maps/editor.html` | editor |
| `notes/list.html` | index |
| `notes/detail.html` | detail |
| `notes/form.html` | form |
| `party/list.html` | operational |
| `quest/list.html` | index |
| `quest/detail.html` | detail |
| `rollable-table/list.html` | index |
| `rollable-table/detail.html` | detail |
| `rollable-table/form.html` | form |
| `session/cockpit.html` | editor |
| `sheet/overview.html` | operational |
| `sheet/detail.html` | operational |
| `sheet/create.html` | form |
| `threat/list.html` | index |
| `threat/detail.html` | detail |
| `threat/form.html` | form |
| `treasury/list.html` | operational |
| `world/*-list.html` | index |
| `world/*-detail.html` | detail |
| `world/*-form.html` | form |
| `error.html` | standalone, exempt |

- [ ] **Step 1: Migrate one template and lock the pattern**

Take `encounter/list.html` as the worked exemplar:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/_shell :: page(
          pageTitle='Encounters',
          archetype='index',
          surface=null,
          header=~{::#page-header},
          content=~{::#page-content},
          rail=~{})}">
<body>
<div id="page-header" class="page-header-slot">
    <th:block th:replace="~{fragments/_page-header :: page-header(
        title='Encounters',
        summary=${encounters.size() + ' prepared'},
        breadcrumb=~{},
        primary=~{::#primaryAction},
        secondary=~{})}"></th:block>
    <a id="primaryAction" class="btn btn-primary"
       th:href="@{/campaigns/{id}/encounters/new(id=${campaignId})}">New encounter</a>
</div>

<div id="page-content" class="page-content">
    <!-- existing toolbar/grid/empty-state content, with the hand-rolled header,
         navbar include, and app-shell wrapper deleted -->
</div>
</body>
</html>
```

Rules for every migration:

1. Delete the `<head th:replace="~{fragments/head :: head}">`, the navbar include, the
   `_appnav` include, and the `.app-shell`/`.app-main` wrapper.
2. Replace the hand-rolled header block with the `page-header` fragment.
3. Keep exactly one `btn-primary` on the page; demote the rest to neutral `btn`.
4. Move `Back`, `Edit`, `Settings`, and `Delete` out of scattered corners into the header's
   secondary slot, an overflow popover, or a labelled danger region.
5. Replace `common/_empty-state` icon arguments with icon names from the sprite.
6. Leave htmx targets, ids, form names, and route references untouched.
7. If the template's old `<main>` carried `data-surface`, pass that exact value as the
   shell's `surface` argument. The nine governed pages and their modes are
   `campaigns/detail`=read, `campaigns/settings`=admin, `adventure/detail`=read,
   `adventure/scene-detail`=read, `adventure/scene-structure`=edit, `encounter/detail`=read,
   `encounter/setup`=edit, `party/list`=read, `session/cockpit`=run. Every other page passes
   `surface=null`.
8. The three slot wrappers carry the classes the archetype grid keys off:
   `<div id="page-header" class="page-header-slot">`,
   `<div id="page-content" class="page-content">`, and — Detail pages only —
   `<div id="page-rail" class="page-rail-slot">` passed as `rail=~{::#page-rail}`.
   Do not put `class="page-header"` or `class="page-rail"` on a wrapper; those belong to the
   shared fragments nested inside, and `SharedComponentContractTest` rejects them in feature
   templates. Every non-Detail page passes `rail=~{}`.

- [ ] **Step 2: Verify the exemplar in a browser before doing the other 58**

```bash
./mvnw -Dtest='FullPageRenderSmokeTest,EncounterControllerTest' test
```

Expected: PASS. Fix the pattern here, not 59 times later.

- [ ] **Step 3: Migrate the remaining templates in family batches**

Work family by family — `campaigns`, `adventure`, `encounter`, `party`/`sheet`,
`treasury`/`ledger`, `handout`/`audio`, `notes`/`quest`/`world`/`calendar`, `library`,
`threat`/`rollable-table`, `maps`, `session`. Run
`./mvnw -Dtest='AppShellContractTest,PageHeaderContractTest,FullPageRenderSmokeTest' test`
after each family and commit that family.

For `maps/editor.html` and `session/cockpit.html`, use `archetype='editor'` and pass
`header=~{}` and `rail=~{}` — those two own their own command bars, restructured in Part 4.
The cockpit still passes `surface='run'`; the map editor passes `surface=null`.

- [ ] **Step 4: Re-point the surface guards at the rendered page**

`SurfaceModeContractTest` parses page templates off disk. After this migration the `<main>`
element comes from `_shell.html`, so parsing `campaigns/detail.html` finds no `<main>` and no
`data-surface`, and both of its tests fail. Replace the file wholesale:

```java
package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Spec 2026-07-22 section 9.1: every governed page states which of the four surface modes it
 * serves. Asserted against the rendered response, because since Task 19 the {@code <main>}
 * element comes from {@code fragments/_shell} and never appears in the page's own source.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurfaceModeContractTest {

    @Autowired MockMvc mvc;
    @Autowired ReleaseRehearsalFixture fixture;

    private ReleaseRehearsalFixture.Seeded seeded;

    @BeforeAll
    void seedOnce() throws Exception {
        seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
    }

    /** Governed route -> declared surface mode. */
    private Map<String, String> governedSurfaces() {
        String campaign = "/campaigns/" + seeded.campaignId();
        String adventure = campaign + "/adventures/" + seeded.adventureId();
        String scene = adventure + "/scenes/" + seeded.hostileSceneId();
        String encounter = campaign + "/encounters/" + seeded.branchedEncounterId();

        Map<String, String> map = new LinkedHashMap<>();
        map.put(campaign, "read");
        map.put(campaign + "/settings", "admin");
        map.put(adventure, "read");
        map.put(scene, "read");
        map.put(scene + "/structure", "edit");
        map.put(encounter, "read");
        map.put(encounter + "/setup", "edit");
        map.put(campaign + "/party", "read");
        map.put(campaign + "/session", "run");
        return map;
    }

    @Test
    void everyGovernedPageDeclaresItsSurfaceOnTheMainElement() throws Exception {
        for (Map.Entry<String, String> entry : governedSurfaces().entrySet()) {
            String route = entry.getKey();
            Document page = Jsoup.parse(mvc.perform(get(route)).andReturn()
                    .getResponse().getContentAsString());

            Elements declarations = page.select("[data-surface]");
            assertThat(declarations)
                    .as("%s must declare exactly one data-surface", route).hasSize(1);
            assertThat(declarations.first().tagName())
                    .as("%s must declare the surface on <main>", route).isEqualTo("main");
            assertThat(declarations.first().attr("data-surface"))
                    .as("%s declares the wrong surface mode", route).isEqualTo(entry.getValue());
        }
    }
}
```

Then fix `SurfaceSeparationContractTest`, whose header scan selects `.page-header-actions` —
a class this migration retires. Left alone it would select nothing and pass vacuously, which
is worse than failing: that test is the one that caught a real `btn-danger` in a page header.
Replace `readAndRunSurfacesCarryNoDestructiveActionInTheirHeader` with a version that scans
the header slot and refuses to pass on an empty scan:

```java
    @Test
    void readAndRunSurfacesCarryNoDestructiveActionInTheirHeader() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (String page : readRunSurfaces().keySet()) {
            for (Element header : parse(page).select("#page-header, .page-header-slot")) {
                scanned++;
                for (Element danger : header.select(".btn-danger")) {
                    offenders.add(page + " -> " + danger.cssSelector());
                }
            }
        }

        assertThat(scanned)
                .as("no header slot was found on any read/run surface — this test has gone blind")
                .isGreaterThanOrEqualTo(4);
        assertThat(offenders)
                .as("a destructive control in a read/run page header is one misclick from data loss")
                .isEmpty();
    }
```

- [ ] **Step 5: Run both guards against the migrated pages**

```bash
./mvnw -Dtest='SurfaceModeContractTest,SurfaceSeparationContractTest' test
```

Expected: PASS. A failure naming a route that declares no `data-surface` means that page was
migrated without carrying its surface across — fix the call site, not the test.

- [ ] **Step 6: Delete `navbar.html`**

```bash
git rm src/main/resources/templates/fragments/navbar.html
```

Then remove the now-unused `head` fragment from `fragments/head.html`, leaving only
`document-head(pageTitle)`.

- [ ] **Step 7: Write the Stage 2 render gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShellRenderGateTest {

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}, {2560, 1440}};

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        failures = new BrowserFailureCollector();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closeContext() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private List<String> standardPages() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of("/campaigns", c, c + "/adventures", c + "/encounters", c + "/maps",
                c + "/handouts", c + "/audio/cues", c + "/notes", c + "/party", c + "/sheets",
                c + "/treasury", c + "/ledger", c + "/quests", c + "/world/npcs",
                c + "/world/locations", c + "/world/factions", c + "/calendar",
                "/library", "/library/tables", "/library/traps", "/library/hazards",
                "/library/about");
    }

    @Test
    void noStandardPageScrollsHorizontallyAtAnySupportedViewport() {
        for (int[] viewport : VIEWPORTS) {
            page.setViewportSize(viewport[0], viewport[1]);
            for (String path : standardPages()) {
                page.navigate("http://localhost:" + port + path);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                int overflow = ((Number) page.evaluate(
                        "() => document.documentElement.scrollWidth - document.documentElement.clientWidth"))
                        .intValue();
                assertThat(overflow)
                        .as("horizontal overflow on %s at %dx%d", path, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    void everyStandardPageHasExactlyOneShellOneHeadingAndAtMostOnePrimaryAction() {
        page.setViewportSize(1440, 900);
        for (String path : standardPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) page.evaluate("""
                    () => ({
                      shells: document.querySelectorAll('.app-shell').length,
                      rails: document.querySelectorAll('nav.rail').length,
                      h1: document.querySelectorAll('h1').length,
                      primary: document.querySelectorAll('.btn-primary').length,
                      archetype: document.body.dataset.archetype
                    })
                    """);
            assertThat(((Number) counts.get("shells")).intValue()).as("shells on %s", path).isEqualTo(1);
            assertThat(((Number) counts.get("rails")).intValue()).as("rails on %s", path).isEqualTo(1);
            assertThat(((Number) counts.get("h1")).intValue()).as("h1 on %s", path).isEqualTo(1);
            assertThat(((Number) counts.get("primary")).intValue())
                    .as("filled primary actions on %s", path).isLessThanOrEqualTo(1);
            assertThat((String) counts.get("archetype"))
                    .as("archetype on %s", path)
                    .isIn("index", "detail", "form", "operational", "editor");
        }
    }

    @Test
    void theRailStaysUsableExpandedAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator("nav.rail").isVisible()).isTrue();
        assertThat(page.locator("nav.rail a.rail__link").count()).isGreaterThanOrEqualTo(20);
        int mainWidth = ((Number) page.evaluate(
                "() => document.querySelector('.app-main').getBoundingClientRect().width")).intValue();
        assertThat(mainWidth).as("main column width at 1280").isGreaterThanOrEqualTo(960);
    }
}
```

- [ ] **Step 8: Run the Stage 2 gate**

```bash
./mvnw -Dtest='AppShellContractTest,PageArchetypeContractTest,PageHeaderContractTest,NavigationRailContractTest,TopBarContractTest,SharedComponentContractTest,AsyncStateContractTest,OverlayContractTest,OverlayBehaviorGateTest,ShellRenderGateTest' test
./mvnw test
```

Expected: PASS.

- [ ] **Step 9: Review the stage screenshots**

Re-run `VisualFoundationRenderGateTest` and compare `target/ui-redesign/visual-foundations/`
against the Stage 1 set. Every page must now show one top bar, one grouped rail, one header,
and one primary action. Content is still unrestructured — that is Stages 3–5.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: migrate every page onto the shared shell and archetypes"
```

---

# Appendix A: Spec coverage for this part

| Spec section | Covered by |
|---|---|
| 4 Supported environment | Tasks 11, 19 (completed by Tasks 41, 45, 51) |
| 6.3 Gold discipline | Task 15 |
| 6.4 Semantic state | Task 16 |
| 7.1–7.2 Type roles | Task 15 |
| 7.3 Icons | Tasks 12, 13 |
| 8.1 Global top bar | Task 12 |
| 8.2–8.3 Navigation | Task 13 |
| 8.4 Page header | Tasks 15, 19 |
| 9.1–9.6 Archetypes and width | Tasks 11, 19 |
| 10 Shared components | Tasks 15, 16, 17, 18 |
| 15 Overlays | Task 18 |
| 16 Loading and feedback | Task 17 |
| 17 Accessibility | Tasks 14, 18 |
| 18.2 Template ownership | Tasks 10, 16, 19 |
| 18.3 JavaScript | Tasks 12, 13, 18 |
| 20.1 Static contracts | Tasks 10–18 |
| 20.2 Browser gates | Tasks 18, 19 |
| 20.4 Functional regression | Task 19 |

# Appendix B: Standing rules for every task

- Never widen a gate to make a page pass. Fix the page.
- Never add a `--color-*` token. The vocabulary is frozen at Task 5 and deleted at Task 49.
- Never leave a page half-migrated across a commit boundary within a stage's own scope.
- Never change a route, form field name, htmx target id, persisted field, or package format.
  If a redesign appears to require one, stop and raise it — that is a functional change and
  needs its own spec.
- When a test name in this plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.

# Handoff

Part 2 is done when Task 19's `./mvnw test` is green, `navbar.html` and `_appnav.html` are
deleted, and `AppShellContractTest` reports exactly one app-shell implementation. Continue
with `docs/superpowers/plans/2026-07-31-ui-redesign-3-feature-surfaces.md`.
