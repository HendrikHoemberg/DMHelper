# Workstream E — Visual-System Refinement and Final Release Rehearsal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make task hierarchy stronger than decoration across every DMHelper surface — one display face per view, a type floor that survives arm's length, gold reserved for focus and primary action, one elevation model, consistent controls and status — and then prove the all-in-one premise by executing spec §11.2 and §11.3 as automated gates.

**Architecture:** No database change, no new dependency, no new stylesheet architecture. Workstream E works in three layers. **Layer 1 (Tasks 1–2)** turns the design tokens into an enforced contract: every `var(--token)` resolves, and no stylesheet outside `tokens.css` carries an absolute font size. **Layer 2 (Tasks 3–11)** applies spec §10's five rules — typography roles, combat legibility, gold discipline, surface grouping, one elevation ladder, control/status/motion consistency — each landing as a CSS+template change guarded by either a text-level contract test (the codebase's existing `UiPolishContractTest` idiom) or a Playwright computed-style gate that reads what the browser actually painted. **Layer 3 (Tasks 12–16)** builds a session-ready synthetic fixture and runs the release rehearsal and viewport/accessibility gate against it end to end, then indexes every §11 requirement against the test that proves it.

**Tech Stack:** Java 21, Spring Boot MVC, Thymeleaf, htmx, Alpine, plain CSS; JUnit 5 + AssertJ + Playwright (Java); Maven (`./mvnw`).

## Global Constraints

- **No database migration and no entity change in E.** Latest Flyway migration stays `V25`. If a task appears to need a schema change, stop — it is out of scope. Rollback for every task is `git revert`; no data is transformed.
- **Server-rendered architecture is fixed** (spec §13): no SPA, no frontend package manager, no third-party UI or docking framework. New behavior is Thymeleaf + htmx + small vanilla JS or existing Alpine components only.
- **`tokens.css` is the only stylesheet allowed to contain raw values.** This is already written at the top of that file; Tasks 1, 2 and 7 make it true and then enforce it.
- **The candlelit identity is retained** (spec §4). E subordinates it to runtime hierarchy; it does not replace the palette, remove the textures, or drop the fonts.
- **Semantic colors are stable and must not be re-mapped:** `--color-danger`, `--color-warning`, `--color-success`, `--color-concentration`, `--color-shield` (Screen safety). No task may repurpose them.
- Production sets `spring.jpa.open-in-view=false`. Templates must not trigger lazy loading during render.
- DM-sensitive blocks carry `data-screen-sensitive`. Any block moved or re-rendered in `session/**` keeps that attribute, and no task may add a new runtime block without deciding its sensitivity.
- Fixture content must stay synthetic. Never commit names, prose or numbers derived from a published campaign (spec §1, §13).
- Test command: `./mvnw -q test -Dtest=<ClassName>` (single method: `-Dtest=<ClassName>#<method>`). Full build: `./mvnw -q verify`.
- Playwright tests need a browser; `./mvnw test` downloads it on first run. They are `@SpringBootTest(webEnvironment = RANDOM_PORT)` and must attach `BrowserFailureCollector` to every page (spec §11.1.8).
- Commit after every task. Never mark a task complete with failing tests.

---

## Interpretation decisions

Three spec sentences in §10 admit more than one reading. These are the readings this plan implements; they are encoded in test allowlists so a reviewer can see and change them in one place.

1. **"Cinzel for at most the primary campaign, adventure or scene title in a view" (§10.1).** Enforced as: on any rendered page, every element whose computed `font-family` contains `Cinzel` must carry `data-display-title`, and at most one such element may exist per page — with two named exceptions, both of which *are* campaign titles or the product wordmark: the app wordmark (`.navbar-brand`, `.appnav-home`) and the campaign-shelf book covers (`.book-cover h3`), where each cover is one campaign's title. All other chrome — badges, nav labels, table headers, panel and modal headers, card titles, empty states, chapter titles, error codes, scale tiles — loses Cinzel.

2. **"No required runtime information below the existing `--text-sm`" (§10.1).** Enforced only where it says *runtime*: `cockpit.css`, `cockpit-layout.css`, `cockpit-modules.css`, and the encounter-tracker rules of `components.css`. In those files a `font-size` below `--text-sm` requires the selector to appear in a reviewed `SECONDARY_METADATA` allowlist. Preparation surfaces are bound by the weaker rule that all font sizes are tokens.

3. **"Numeric combat values use tabular figures and remain legible at arm's length" (§10.1).** The tracker row currently shows initiative at `0.75rem` and shows *no* numeric HP at all — only a bar. Task 4 raises initiative to `--text-base` and adds a numeric `current/max` HP readout to the row. Adding the readout is a small scope addition beyond pure restyling; it is included because without it the sentence has no subject in the primary combat surface.

---

## File Structure

**Modified CSS** (no new stylesheets; E refines the existing ones)
- `src/main/resources/static/css/tokens.css` — adds `--text-3xl`, `--color-border-strong` and the `--z-*` elevation ladder; keeps every existing token and every existing value.
- `src/main/resources/static/css/base.css` — global `h1, h2` display-font rule removed; wordmark/appnav/badge typography; z-index tokens; reduced-motion completion.
- `src/main/resources/static/css/components.css` — the bulk: type-scale mapping, Cinzel removal from chrome, gold discipline, nested-card rule, elevation tokens, readiness-panel styles, status cluster, destructive grouping.
- `src/main/resources/static/css/cockpit.css`, `cockpit-modules.css`, `cockpit-layout.css` — runtime type floor, combat legibility, elevation tokens.
- `src/main/resources/static/css/book.css` — statblock type scale (prose stays Alegreya).
- `src/main/resources/static/css/surfaces.css` — `--space-xxs` fix.

**New templates**
- `common/_field-error.html` — fragment `field-error(field)`: the one place a validation error is rendered.

**Modified templates**
- `session/cockpit.html` — inline `font-family` removed from the title; runtime status cluster added to the topbar.
- `encounter/_tracker.html` — numeric HP readout; group-count inline style removed.
- `campaigns/_readiness.html` — controls get button/select classes and a grouped action row.
- `sheet/_rest-preview.html` — `var(--text-muted)` → `var(--color-text-muted)`.
- `fragments/navbar.html`, `fragments/_appnav.html` — `data-display-title` on the wordmark.
- `campaigns/_card.html` — `data-display-title` on book covers.

**New Java (main)**
- `live/web/TableConnectionStatusController.java` — `GET /api/table/status` → `{"connected": n}`.
- `live/TableStateWebSocketHandler.java` (modified) — `connectedCount()`.

**New JS**
- `src/main/resources/static/js/runtime-status.js` — drives the cockpit status cluster from existing htmx events plus the table-status poll.

**New tests**
- `config/CssRules.java` — shared flat-CSS reader used by the contract tests below.
- `config/DesignTokenContractTest.java` (T1)
- `config/TypeScaleContractTest.java` (T2)
- `config/TypographyRoleContractTest.java` (T3)
- `visual/TypographyRenderGateTest.java` (T3)
- `config/CombatLegibilityContractTest.java` (T4)
- `config/GoldAccentContractTest.java` (T5)
- `visual/SurfaceNestingGateTest.java` (T6)
- `config/ElevationModelContractTest.java` (T7)
- `config/ControlConsistencyContractTest.java` (T8)
- `config/DestructiveActionContractTest.java` (T9)
- `session/RuntimeStatusSurfaceTest.java` (T10)
- `config/MotionBudgetContractTest.java` (T11)
- `support/ReleaseRehearsalFixture.java` + `support/ReleaseRehearsalFixtureTest.java` (T12, T15)
- `gate/ViewportAccessibilityGateTest.java` (T13)
- `gate/ReleaseRehearsalTest.java` (T14, T15)
- `gate/ReleaseGateIndexContractTest.java` (T16)

**New docs**
- `docs/product/all-in-one-release-gate.md` (T16), linked from `docs/product/README.md`.

---

## Task 1: Design tokens resolve

Four `var(--token)` references in shipped CSS and templates point at tokens that do not exist. They silently fall back to inherited or initial values, which is exactly the invisible hierarchy drift §10 is about. Fix them, then make the class of bug impossible.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java`
- Modify: `src/main/resources/static/css/cockpit-modules.css:75`, `:373`
- Modify: `src/main/resources/static/css/components.css:2652`
- Modify: `src/main/resources/static/css/surfaces.css:295`
- Modify: `src/main/resources/templates/sheet/_rest-preview.html:24`

**Interfaces:**
- Produces: `CssRules.of(String... files)` → `List<CssRules.Rule>`, where `Rule` is `record Rule(String file, String selector, String body)` with `boolean declares(String property)` and `String value(String property)`. `@media`/`@supports`/`@layer` bodies are flattened into their inner rules; `@keyframes` and `@font-face` are returned as single rules whose selector is the at-prelude. Tasks 2, 5, 7, 11 consume it.
- Produces: `CssRules.definedTokens()` → `Set<String>` of every `--name` declared anywhere under `static/css/`.

- [ ] **Step 1: Write the shared CSS reader**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small flat reader for the app stylesheets. The design-system contract
 * tests need to ask "which selector declares this?", which grepping cannot answer and a
 * real CSS parser would be a new dependency for (spec 2026-07-22 section 13).
 */
final class CssRules {

    static final Path CSS_DIR = Path.of("src/main/resources/static/css");

    static final List<String> ALL_FILES = List.of(
            "tokens.css", "base.css", "components.css", "book.css",
            "cockpit.css", "cockpit-layout.css", "cockpit-modules.css",
            "surfaces.css", "player-projection.css");

    /** Stylesheets that paint the live table surfaces (spec section 10.1 "runtime"). */
    static final List<String> RUNTIME_FILES = List.of(
            "cockpit.css", "cockpit-layout.css", "cockpit-modules.css");

    record Rule(String file, String selector, String body) {
        boolean declares(String property) {
            return matcher(property).find();
        }

        /** The last declared value of the property, or null. */
        String value(String property) {
            Matcher m = matcher(property);
            String found = null;
            while (m.find()) found = m.group(1).trim();
            return found;
        }

        List<String> values(String property) {
            Matcher m = matcher(property);
            List<String> found = new ArrayList<>();
            while (m.find()) found.add(m.group(1).trim());
            return found;
        }

        private Matcher matcher(String property) {
            return Pattern.compile("(?:^|[;{\\s])" + Pattern.quote(property) + "\\s*:([^;}]*)")
                    .matcher(body);
        }

        String where() {
            return file + " { " + selector + " }";
        }
    }

    static List<Rule> of(String... files) {
        List<Rule> rules = new ArrayList<>();
        for (String file : files) collect(file, read(file), rules);
        return rules;
    }

    static List<Rule> of(List<String> files) {
        return of(files.toArray(new String[0]));
    }

    static String read(String file) {
        try {
            return stripComments(Files.readString(CSS_DIR.resolve(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Set<String> definedTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(--[a-z0-9-]+)\\s*:").matcher(String.join("\n",
                ALL_FILES.stream().map(CssRules::read).toList()));
        while (m.find()) tokens.add(m.group(1));
        return tokens;
    }

    static Set<String> referencedTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("var\\(\\s*(--[a-z0-9-]+)").matcher(text);
        while (m.find()) tokens.add(m.group(1));
        return tokens;
    }

    private static void collect(String file, String css, List<Rule> out) {
        int i = 0;
        while (i < css.length()) {
            int brace = css.indexOf('{', i);
            if (brace < 0) return;
            String prelude = css.substring(i, brace).trim();
            int end = matchingBrace(css, brace);
            if (end < 0) return;
            String body = css.substring(brace + 1, end);
            if (prelude.startsWith("@media") || prelude.startsWith("@supports")
                    || prelude.startsWith("@layer")) {
                collect(file, body, out);
            } else {
                out.add(new Rule(file, prelude.replaceAll("\\s+", " "), body));
            }
            i = end + 1;
        }
    }

    private static int matchingBrace(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private CssRules() {
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10: hierarchy is expressed through tokens. A var() that resolves
 * to nothing silently inherits, so the surface looks styled while carrying no decision.
 */
class DesignTokenContractTest {

    @Test
    void everyTokenReferencedByAStylesheetIsDefined() {
        Set<String> defined = CssRules.definedTokens();
        List<String> dangling = new ArrayList<>();

        for (String file : CssRules.ALL_FILES) {
            for (String token : CssRules.referencedTokens(CssRules.read(file))) {
                if (!defined.contains(token)) dangling.add(file + " → " + token);
            }
        }

        assertThat(dangling).as("var() references with no definition").isEmpty();
    }

    @Test
    void everyTokenReferencedByATemplateIsDefined() throws IOException {
        Set<String> defined = CssRules.definedTokens();
        List<String> dangling = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                for (String token : CssRules.referencedTokens(html)) {
                    if (!defined.contains(token)) dangling.add(template + " → " + token);
                }
            }
        }

        assertThat(dangling).as("var() references with no definition").isEmpty();
    }

    /**
     * Spec section 10.2: "Danger, warning, success, concentration and Screen safety retain
     * stable semantic colors." Workstream E may re-scope where colors appear; it may not
     * change what they mean.
     */
    @Test
    void semanticColorsKeepTheirValues() {
        String tokens = CssRules.read("tokens.css");

        assertThat(tokens)
                .contains("--color-danger: #a83a32")
                .contains("--color-success: #7fa05f")
                .contains("--color-warning: #d9993d")
                .contains("--color-concentration: #966a9e")
                .contains("--color-shield: #8a9aa5");
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=DesignTokenContractTest`
Expected: FAIL — four dangling references: `cockpit-modules.css → --text-md`, `components.css → --text-md`, `surfaces.css → --space-xxs`, `templates/sheet/_rest-preview.html → --text-muted`.

- [ ] **Step 4: Fix the four references**

`cockpit-modules.css:75` — `.story-module__header h3`:

```css
.story-module__header h3 {
  margin: 0;
  font-size: var(--text-base);
}
```

`cockpit-modules.css:373` — `.runtime-story .scene-card h4`:

```css
.runtime-story .scene-card h4 {
  margin: 0 0 var(--space-xs);
  font-size: var(--text-base);
}
```

`components.css:2652` — `.roll-result-text`:

```css
.roll-result-text {
  font-size: var(--text-base);
  font-weight: 500;
}
```

`surfaces.css:295` — inside `.organize-panel__row`, replace `padding: var(--space-xxs) 0;` with:

```css
    padding: var(--space-xs) 0;
```

`templates/sheet/_rest-preview.html:24` — replace `color: var(--text-muted);` inside the inline style with `color: var(--color-text-muted);`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=DesignTokenContractTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java \
        src/main/resources/static/css/cockpit-modules.css \
        src/main/resources/static/css/components.css \
        src/main/resources/static/css/surfaces.css \
        src/main/resources/templates/sheet/_rest-preview.html
git commit -m "fix(visual): resolve dangling design tokens and guard the token contract"
```

---

## Task 2: One type scale, with a floor at the table

38 `font-size` declarations outside `tokens.css` carry absolute values, six of them below `--text-sm` on live runtime surfaces. This task maps every one onto the scale and installs the two rules from §10.1.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypeScaleContractTest.java`
- Modify: `src/main/resources/static/css/tokens.css`
- Modify: `src/main/resources/static/css/cockpit.css`, `components.css`, `book.css`
- Modify: `src/main/resources/templates/encounter/_tracker.html:322`

**Interfaces:**
- Consumes: `CssRules` from Task 1.
- Produces: `--text-3xl: 4rem` in `tokens.css`. Produces the invariant that no stylesheet other than `tokens.css` contains an absolute (`px`/`rem`) font size, and that runtime stylesheets only go below `--text-sm` for allowlisted selectors — Tasks 3, 4 and 13 depend on it.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypeScaleContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1. Two rules:
 *   1. the type scale lives in tokens.css and nowhere else;
 *   2. on runtime surfaces nothing required drops below --text-sm.
 *
 * The allowlist below is the reviewed answer to "truly secondary metadata". Adding to it is
 * a deliberate act; that is the point.
 */
class TypeScaleContractTest {

    private static final Set<String> SECONDARY_METADATA = Set.of(
            ".runtime-party .chip-detail",
            ".runtime-party .chip-status",
            ".runtime-session-log .session-log-status",
            ".runtime-session-log .session-log-time",
            ".runtime-session-log .session-log-section-title",
            ".runtime-session-log .log-event-time",
            ".runtime-session-log .log-event-detail",
            ".runtime-session-log .session-log-unresolved-badge",
            ".runtime-session-log .empty-state-subtle",
            ".session-plan-module-inner .beat-broken-label",
            "[data-module-remove], [data-module-retry]",
            ".scene-status-badge",
            ".beat-type",
            ".audio-source, .audio-owner");

    @Test
    void onlyTokensCssCarriesAnAbsoluteFontSize() {
        List<String> offenders = new ArrayList<>();

        for (String file : CssRules.ALL_FILES) {
            if (file.equals("tokens.css")) continue;
            for (CssRules.Rule rule : CssRules.of(file)) {
                for (String value : rule.values("font-size")) {
                    if (value.matches(".*\\d\\s*(px|rem)\\b.*")) {
                        offenders.add(rule.where() + " → font-size: " + value);
                    }
                }
            }
        }

        assertThat(offenders).as("absolute font sizes outside tokens.css").isEmpty();
    }

    @Test
    void runtimeSurfacesStayAtOrAboveTextSmUnlessAllowlisted() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.RUNTIME_FILES)) {
            String value = rule.value("font-size");
            if (value == null || !value.contains("var(--text-xs)")) continue;
            if (!SECONDARY_METADATA.contains(rule.selector())) {
                offenders.add(rule.where());
            }
        }

        assertThat(offenders)
                .as("runtime text below --text-sm that is not reviewed secondary metadata")
                .isEmpty();
    }

    @Test
    void noTemplateSetsAFontSizeInline() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                if (Files.readString(template).matches("(?s).*style=\"[^\"]*font-size[^\"]*\".*")) {
                    offenders.add(template.toString());
                }
            }
        }

        assertThat(offenders).as("inline font-size defeats the scale").isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=TypeScaleContractTest`
Expected: FAIL on all three methods — 38 absolute sizes, several unallowlisted runtime `--text-xs` rules, and templates with inline `font-size`.

- [ ] **Step 3: Add the one missing scale step**

In `tokens.css`, extend the type scale block (after `--text-2xl`):

```css
  --text-2xl: 2rem;
  --text-3xl: 4rem;   /* error-page status numeral only */
```

- [ ] **Step 4: Apply the mapping in `cockpit.css`**

| Line | Selector | Was | Becomes |
|---|---|---|---|
| 128 | `.aoepresets button` | `0.7rem` | `var(--text-sm)` |
| 165 | `.sb-result-item .sb-cr` | `0.7rem` | `var(--text-sm)` |
| 167 | `.aoepresets-label` | `0.7rem` | `var(--text-sm)` |
| 208 | `.scene-status-badge` | `0.65rem` | `var(--text-xs)` |
| 289 | `.beat-type` | `0.65rem` | `var(--text-xs)` |
| 330 | `.audio-source, .audio-owner` | `0.75rem` | `var(--text-xs)` |

- [ ] **Step 5: Apply the mapping in `components.css`**

| Line | Selector | Was | Becomes |
|---|---|---|---|
| 431 | `.party-member-chip .chip-stats` | `0.7rem` | `var(--text-sm)` |
| 552 | `.ability-grid .form-group label` | `0.7rem` | `var(--text-xs)` |
| 742 | `.init-badge` | `0.75rem` | `var(--text-base)` |
| 803 | `.cond-icon` | `0.625rem` | `var(--text-xs)` |
| 823 | `.concentration-icon` | `0.625rem` | `var(--text-xs)` |
| 842 | `.combatant-detail label` | `0.75rem` | `var(--text-xs)` |
| 858 | `.hp-flag` | `0.75rem` | `var(--text-sm)` |
| 1081 | `.presenting-indicator` | `0.7rem` | `var(--text-sm)` |
| 1138 | `.quicknote-time` | `0.7rem` | `var(--text-xs)` |
| 1140 | `.btn-2xs` | `0.7rem` | `var(--text-sm)` |
| 1201 | `.badge-tag` | `0.7rem` | `var(--text-xs)` |
| 1302 | `.sheet-attack-table` | `0.95rem` | `var(--text-base)` |
| 1313 | `.sheet-attack-table th` | `0.85rem` | `var(--text-sm)` |
| 1328 | `.sheet-attack-range` | `0.9rem` | `var(--text-sm)` |
| 1336 | `.sheet-feature-group` | `0.9rem` | `var(--text-sm)` |
| 1357 | `.sheet-feature-source` | `0.85rem` | `var(--text-sm)` |
| 1362 | `.sheet-feature-body` | `0.9rem` | `var(--text-base)` |
| 1572 | `.dice-result-total` | `2rem` | `var(--text-2xl)` |
| 1648 | (24px icon button) | `14px` | `var(--text-sm)` |
| 1723 | `.command-palette-icon` | `1.2rem` | `var(--text-lg)` |
| 1729 | `.command-palette-input` | `1.1rem` | `var(--text-base)` |
| 2286 | `.error-page-status` | `4rem` | `var(--text-3xl)` |
| 2364 | `.modal-close` | `1.5rem` | `var(--text-xl)` |
| 2484 | `.override-table th` | `0.85rem` | `var(--text-sm)` |
| 2542 | `.skill-check-row .exp-label` | `0.7rem` | `var(--text-xs)` |
| 2694 | `.stat-grid dt` | `0.7rem` | `var(--text-xs)` |

- [ ] **Step 6: Apply the mapping in `book.css`**

| Line | Selector | Was | Becomes |
|---|---|---|---|
| 18 | `.statblock-render h2` | `1.4rem` | `var(--text-xl)` |
| 50 | `.statblock-render .sb-abilities .score` | `1rem` | `var(--text-base)` |
| 56 | `.statblock-render h3` | `1rem` | `var(--text-base)` |
| 69 | `.note-body` | `1.0625rem` | `var(--text-base)` |

Leave `.note-body code` at `0.85em` and the drop cap at `3.1em`: both are relative to the prose they sit in, which is what the scale wants.

- [ ] **Step 7: Remove the inline font sizes from templates**

`session/cockpit.html:48` — drop the whole `style` attribute from the title (Task 3 gives it a class):

```html
    <h1 class="cockpit-topbar__title" th:text="${workspace.campaign.name}">Campaign</h1>
```

`encounter/_tracker.html:322` — the group-count span loses its inline style:

```html
                            <template x-if="c.groupId && c.groupLeader">
                                <span class="group-count" x-text="'(' + groupCount(c.groupId) + ')'"></span>
                            </template>
```

`encounter/_tracker.html:95` — the active-combatant label loses its inline style:

```html
                <span class="tracker-turns__active">
                    Active: <strong x-text="activeCombatant?.name || '—'"></strong>
                </span>
```

Add the two replacement rules to `components.css` next to `.combatant-name`:

```css
.group-count {
    font-size: var(--text-xs);
    color: var(--color-text-muted);
    margin-left: 2px;
}

.tracker-turns__active {
    font-size: var(--text-sm);
}
```

Then run the failing-template list and repeat the same move for every remaining hit:

Run: `grep -rln 'style="[^"]*font-size' src/main/resources/templates/`

For each file, delete the `font-size` declaration from the inline style and add a class rule to the stylesheet that owns that component (`components.css` for shared chrome, `cockpit*.css` for runtime, `surfaces.css` for Read/Run/Edit/Admin surfaces), choosing the scale step nearest the value it replaces and never going below `--text-sm` inside `templates/session/`.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=TypeScaleContractTest`
Expected: PASS

- [ ] **Step 9: Run the neighbouring suites that read these files**

Run: `./mvnw -q test -Dtest=UiPolishContractTest,EncounterTemplateContractTest,CockpitRuntimeModuleContractTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/TypeScaleContractTest.java
git commit -m "feat(visual): put every font size on the scale and raise the runtime floor"
```

---

## Task 3: One display face per view

Cinzel currently sets 24 rules — table headers, badges, panel headers, empty states, modal titles, chapter titles. That is decoration competing with hierarchy. This task gives Cinzel exactly one job and proves it in a real browser.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/visual/TypographyRenderGateTest.java`
- Modify: `src/main/resources/static/css/base.css`, `components.css`, `cockpit.css`, `book.css`
- Modify: `src/main/resources/templates/fragments/navbar.html`, `fragments/_appnav.html`, `campaigns/_card.html`, `session/cockpit.html`

**Interfaces:**
- Consumes: `CssRules` (Task 1).
- Produces: the `data-display-title` attribute contract and the CSS rule `[data-display-title] { font-family: var(--font-display); }`. Task 13's viewport gate and Task 14's rehearsal both assert against `[data-display-title]`.

- [ ] **Step 1: Write the failing CSS-level test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1: system UI for chrome, Alegreya for prose, Cinzel for at most
 * the one primary title in a view. Everything else reads as decoration.
 */
class TypographyRoleContractTest {

    /** The only selectors allowed to reach for Cinzel. See the plan's interpretation note. */
    private static final Set<String> DISPLAY_SELECTORS = Set.of(
            "[data-display-title]",
            ".page-header h1",
            ".cockpit-topbar__title",
            ".navbar-brand",
            ".appnav-home",
            ".book-cover h3");

    /** Alegreya is for narrative prose and read-aloud, not for chrome. */
    private static final Set<String> BOOK_SELECTORS = Set.of(
            ".statblock-render",
            ".note-body",
            ".read-aloud",
            ".structured-read-aloud",
            ".scene-body",
            ".handout-prose");

    @Test
    void cinzelIsReservedForPrimaryTitles() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            String value = rule.value("font-family");
            if (value == null || !value.contains("var(--font-display)")) continue;
            if (!DISPLAY_SELECTORS.contains(rule.selector())) offenders.add(rule.where());
        }

        assertThat(offenders).as("Cinzel outside the primary-title role").isEmpty();
    }

    @Test
    void alegreyaIsReservedForProse() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            String value = rule.value("font-family");
            if (value == null || !value.contains("var(--font-book)")) continue;
            if (!BOOK_SELECTORS.contains(rule.selector())) offenders.add(rule.where());
        }

        assertThat(offenders).as("Alegreya outside narrative prose").isEmpty();
    }

    @Test
    void theDisplayTitleContractExists() {
        assertThat(CssRules.read("base.css"))
                .contains("[data-display-title] {")
                .contains("font-family: var(--font-display)");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=TypographyRoleContractTest`
Expected: FAIL — roughly 20 Cinzel rules outside the allowlist, `.dash-desc` using Alegreya, and no `[data-display-title]` rule.

- [ ] **Step 3: Install the display-title contract in `base.css`**

Replace the global heading rule at `base.css:31-35`:

```css
/* Headings carry weight and rhythm; the display face is a separate, scarcer decision
   (spec 2026-07-22 §10.1) and is opted into with [data-display-title]. */
h1, h2 {
  font-weight: 600;
  letter-spacing: 0.01em;
}

[data-display-title] {
  font-family: var(--font-display);
  letter-spacing: 0.02em;
}
```

Then, in the same file:

- `.navbar-brand` (line 100) — keep `font-family: var(--font-display);` (wordmark exception).
- `.appnav-home` (line 283) — keep `font-family: var(--font-display);` (wordmark exception).
- `.screen-safety-badge` (line 202) — delete the `font-family` declaration; the shield color and letterspacing already carry it.
- `.appnav-label` (line 255) — delete the `font-family` declaration.
- `.shortcut-panel h2` (line 462) — delete the `font-family` declaration.

- [ ] **Step 4: Strip Cinzel from chrome in `components.css`**

Delete the `font-family: var(--font-display);` declaration from each of these rules, leaving their other declarations untouched:

`.library-card__title` (331), `.library-table__name` (375), the table-header rule (619), the panel-header rule (658), `.empty-state__icon` (1884), `.empty-state .empty-state__title` (1892), `.error-page-status` (2285), `.modal-header h3` (2357), `.scale-tile__value` (2929), `.chapter-title` (3020), `.dash-grid .card h3` (1802), `.campaign-cover h3` (1814).

Keep `.book-cover h3` (1995) — a shelf cover *is* a campaign title.

Where a rule loses its display face and would now read flat, compensate with weight rather than a second face:

```css
.library-card__title { font-weight: 700; letter-spacing: 0.01em; }
.empty-state .empty-state__title { font-weight: 600; }
.modal-header h3 { font-weight: 600; letter-spacing: 0.01em; }
.chapter-title { font-weight: 700; }
```

- [ ] **Step 5: Strip Cinzel from chrome in `cockpit.css` and `book.css`**

`cockpit.css:169` becomes:

```css
.cockpit-title { font-size: var(--text-lg); color: var(--color-text); }
```

`book.css` — delete `font-family: var(--font-display);` from `.statblock-render h2` (17), `.statblock-render h3` (55), and the two remaining display rules at 74 and 96. The statblock is prose in a book face; its headings gain weight instead:

```css
.statblock-render h2 { font-size: var(--text-xl); font-weight: 700; margin-bottom: 2px; }
.statblock-render h3 { font-size: var(--text-base); font-weight: 700; }
```

- [ ] **Step 6: Pull Alegreya off `.dash-desc`**

`components.css:1799` — delete `font-family: var(--font-book);`. A dashboard description is chrome, not narrative:

```css
.dash-desc { color: var(--color-text-muted); max-width: 68ch; margin-bottom: var(--space-md); }
```

`components.css:1030` and `2832` sit on handout prose and structured read-aloud respectively; confirm their selectors are `.handout-prose` and `.structured-read-aloud` and add them to `BOOK_SELECTORS` if the selector text differs, rather than removing the font.

- [ ] **Step 7: Mark the display titles in templates**

`fragments/navbar.html` — add the attribute to the brand:

```html
<a class="navbar-brand" data-display-title th:href="@{/}">DMHelper</a>
```

`fragments/_appnav.html` — add `data-display-title` to the `.appnav-home` link.

`campaigns/_card.html` — add `data-display-title` to the `<h3>` inside `.book-cover`.

`session/cockpit.html:47` — the cockpit title (its inline style was removed in Task 2):

```html
    <h1 class="cockpit-topbar__title" data-display-title th:text="${workspace.campaign.name}">Campaign</h1>
```

Add the class rule to `cockpit-layout.css`:

```css
.cockpit-topbar__title {
  font-size: var(--text-lg);
  margin: 0;
}
```

Then add `data-display-title` to the `<h1>` inside `.page-header` on every governed page template — the six declared in `SurfaceModeContractTest` plus the list pages in `PageHeaderContractTest`:

Run: `grep -rln 'class="page-header"' src/main/resources/templates/`

and for each file, change its `<h1>` to `<h1 data-display-title>`.

- [ ] **Step 8: Run the CSS test to verify it passes**

Run: `./mvnw -q test -Dtest=TypographyRoleContractTest`
Expected: PASS

- [ ] **Step 9: Write the failing browser gate**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/visual/TypographyRenderGateTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.visual;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1, enforced against what the browser actually painted rather
 * than against what the stylesheet says. Cascade order, inline styles and fragment reuse
 * all get a vote here; the CSS-level test cannot see any of them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypographyRenderGateTest {

    @LocalServerPort private int port;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private PreparationSurfaceFixture prepFixture;

    private static Playwright playwright;
    private static Browser browser;
    private PopulatedCampaignFixture.Seeded seeded;
    private PreparationSurfaceFixture.Seeded prepared;

    @BeforeAll
    void seedAndLaunch() {
        seeded = fixture.seed();
        prepared = prepFixture.seed();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    List<String> pages() {
        String c = "/campaigns/" + seeded.campaignId();
        String p = "/campaigns/" + prepared.campaignId();
        return List.of(
                c,
                c + "/adventures",
                c + "/party",
                c + "/encounters",
                c + "/maps",
                c + "/quests",
                c + "/session",
                p + "/encounters/" + prepared.encounterId());
    }

    @ParameterizedTest
    @MethodSource("pages")
    void everyCinzelElementIsADeclaredDisplayTitle(String path) {
        try (Page page = browser.newPage()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            @SuppressWarnings("unchecked")
            List<String> undeclared = (List<String>) page.evaluate("""
                    () => Array.from(document.querySelectorAll('body *'))
                        .filter(el => getComputedStyle(el).fontFamily.includes('Cinzel'))
                        .filter(el => !el.hasAttribute('data-display-title'))
                        .map(el => el.tagName.toLowerCase() + '.' + (el.className || '(no class)'))
                    """);

            assertThat(undeclared).as("Cinzel without data-display-title on %s", path).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("pages")
    void atMostOneContentTitleUsesTheDisplayFace(String path) {
        try (Page page = browser.newPage()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            Object count = page.evaluate("""
                    () => Array.from(document.querySelectorAll('[data-display-title]'))
                        .filter(el => !el.closest('.navbar, .appnav, .book-cover'))
                        .length
                    """);

            assertThat(((Number) count).intValue())
                    .as("content titles in the display face on %s", path)
                    .isLessThanOrEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("pages")
    void controlsAndChromeRenderInTheUiFace(String path) {
        try (Page page = browser.newPage()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> serifControls = (List<Map<String, String>>) page.evaluate("""
                    () => Array.from(document.querySelectorAll('button, .btn, label, th, .badge'))
                        .filter(el => {
                          const f = getComputedStyle(el).fontFamily;
                          return f.includes('Cinzel') || f.includes('Alegreya');
                        })
                        .map(el => ({ tag: el.tagName.toLowerCase(), cls: el.className || '' }))
                    """);

            assertThat(serifControls).as("controls rendered in a book face on %s", path).isEmpty();
        }
    }
}
```

- [ ] **Step 10: Run the browser gate**

Run: `./mvnw -q test -Dtest=TypographyRenderGateTest`
Expected: PASS. If it fails, the report names the offending element's tag and class — remove the display/book face from that component's rule or, if it genuinely is the view's primary title, give it `data-display-title` and remove the competing one.

- [ ] **Step 11: Run the existing UI suites**

Run: `./mvnw -q test -Dtest=UiPolishContractTest,PageHeaderContractTest,SurfaceModeContractTest`
Expected: PASS

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/visual/TypographyRenderGateTest.java
git commit -m "feat(visual): give Cinzel one job and prove it in the browser"
```

---

## Task 4: Combat values legible at arm's length

The initiative badge renders at 12px and the tracker row shows no HP number at all. Both are required combat information read from a metre away.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java`
- Modify: `src/main/resources/static/css/components.css:733-760`
- Modify: `src/main/resources/templates/encounter/_tracker.html:309-336`

**Interfaces:**
- Consumes: `CssRules` (Task 1), the `--text-base` mapping of `.init-badge` (Task 2).
- Produces: `.combatant-hp` on every tracker row, bound to `c.currentHp` / `c.maxHp` from `combat-tracker.js`. Task 14's rehearsal reads `.combatant-hp` to verify the log matches the table.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1: "Numeric combat values use tabular figures and remain
 * legible at arm's length." The tracker is where that sentence is either true or false.
 */
class CombatLegibilityContractTest {

    private static final List<String> COMBAT_NUMERALS =
            List.of(".init-badge", ".combatant-hp", ".hp-display", ".hp-delta-input, .detail-hp input");

    @Test
    void everyCombatNumeralIsTabularAndAtLeastTextBase() {
        for (String selector : COMBAT_NUMERALS) {
            CssRules.Rule rule = CssRules.of("components.css").stream()
                    .filter(r -> r.selector().equals(selector))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing rule: " + selector));

            assertThat(rule.body())
                    .as("%s tabular figures", selector)
                    .contains("font-variant-numeric: tabular-nums");
            assertThat(rule.value("font-size"))
                    .as("%s size", selector)
                    .isIn("var(--text-base)", "var(--text-lg)");
        }
    }

    @Test
    void theTrackerRowShowsANumericHpReadout() throws IOException {
        String tracker = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"));

        assertThat(tracker)
                .contains("class=\"combatant-hp u-num\"")
                .contains("x-text=\"hpLabel(c)\"")
                .contains("x-show=\"!tableSafe\"");
    }

    @Test
    void hpLabelHandlesMissingMaximumsWithoutPrintingNull() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/combat-tracker.js"));

        assertThat(js)
                .contains("hpLabel(c)")
                .contains("if (!c || c.currentHp == null) return '—';");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=CombatLegibilityContractTest`
Expected: FAIL — `.combatant-hp` has no rule, the tracker has no readout, `hpLabel` does not exist.

- [ ] **Step 3: Enlarge the initiative badge and add the HP readout style**

Replace `.init-badge` in `components.css` (line 733) and add `.combatant-hp` after `.combatant-name`:

```css
.init-badge {
    min-width: 34px;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--color-initiative-badge);
    color: var(--color-accent);
    border-radius: 4px;
    font-size: var(--text-base);
    font-weight: 700;
    flex-shrink: 0;
    font-family: var(--font-mono);
    font-variant-numeric: tabular-nums;
    border: 1px solid var(--color-border);
}

/* Read from a metre away, next to the bar rather than instead of it. */
.combatant-hp {
    font-family: var(--font-mono);
    font-size: var(--text-base);
    font-variant-numeric: tabular-nums;
    color: var(--color-text);
    min-width: 4.5em;
    text-align: right;
    flex-shrink: 0;
}

.combatant-row.bloodied .combatant-hp { color: var(--color-warning); }
.combatant-row.defeated .combatant-hp { color: var(--color-danger); }
```

Also raise `.hp-display` to the same floor:

```css
.hp-display {
  font-family: var(--font-mono);
  font-size: var(--text-base);
  font-variant-numeric: tabular-nums;
  min-width: 2.5em;
  text-align: center;
  color: var(--color-text);
}
```

and give the delta input the explicit size the contract asks for:

```css
.hp-delta-input, .detail-hp input {
  font-family: var(--font-mono);
  font-size: var(--text-base);
  font-variant-numeric: tabular-nums;
}
```

- [ ] **Step 4: Add `hpLabel` to the tracker component**

In `src/main/resources/static/js/combat-tracker.js`, next to `hpPercent(c)` (line 627):

```javascript
            hpLabel(c) {
                if (!c || c.currentHp == null) return '—';
                if (!c.maxHp) return String(c.currentHp);
                return c.currentHp + '/' + c.maxHp;
            },
```

- [ ] **Step 5: Render the readout in the row**

In `encounter/_tracker.html`, insert the readout between `.combatant-name` and `.hp-mini`:

```html
                        <!-- Numeric HP — DM only, and the number the log will agree with -->
                        <span class="combatant-hp u-num" x-show="!tableSafe" x-text="hpLabel(c)"></span>
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=CombatLegibilityContractTest`
Expected: PASS

- [ ] **Step 7: Run the tracker suites**

Run: `./mvnw -q test -Dtest=EncounterTemplateContractTest,TypeScaleContractTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/css/components.css \
        src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_tracker.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java
git commit -m "feat(visual): make combat numerals readable from across the table"
```

---

## Task 5: Gold means focus, selection or primary action

Gold currently borders every card on hover, every ghost button on hover, and every generic button on hover — so it means nothing when it appears on the one control that matters.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java`
- Modify: `src/main/resources/static/css/components.css`, `base.css`

**Interfaces:**
- Consumes: `CssRules` (Task 1).
- Produces: `--color-border-strong` in `tokens.css` — the neutral hover hairline that replaces gold on non-primary surfaces.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Gold denotes focus, selection and primary actions rather
 * than bordering every card." A gold border everywhere is a gold border nowhere.
 */
class GoldAccentContractTest {

    /** Selection, focus and primary-action states may border in gold. Nothing else may. */
    private static final List<String> EARNED = List.of(
            ":focus", "[aria-current", "[aria-selected", ".is-selected", ".active",
            ".btn-primary", "::selection", "[data-display-title]");

    /** Reviewed exceptions: in-world surfaces whose gold edge is the identity itself. */
    private static final Set<String> IDENTITY_SURFACES = Set.of(
            ".book-cover",
            ".statblock-render",
            ".read-aloud",
            ".rule-taper--gold");

    @Test
    void goldBordersOnlyWhereItIsEarned() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            List<String> borderValues = new ArrayList<>();
            borderValues.addAll(rule.values("border"));
            borderValues.addAll(rule.values("border-color"));
            borderValues.addAll(rule.values("border-left"));
            borderValues.addAll(rule.values("border-bottom"));

            boolean gold = borderValues.stream().anyMatch(v ->
                    v.contains("--color-accent") || v.contains("--color-gold-soft")
                            || v.contains("gold-sheen"));
            if (!gold) continue;

            boolean earned = EARNED.stream().anyMatch(token -> rule.selector().contains(token))
                    || IDENTITY_SURFACES.stream().anyMatch(s -> rule.selector().contains(s));
            if (!earned) offenders.add(rule.where());
        }

        assertThat(offenders).as("gold borders that mean nothing").isEmpty();
    }

    @Test
    void aNeutralHoverHairlineExists() {
        assertThat(CssRules.read("tokens.css")).contains("--color-border-strong:");
    }

    @Test
    void cardHoverUsesTheNeutralHairline() {
        CssRules.Rule hover = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".card:hover"))
                .findFirst()
                .orElseThrow();

        assertThat(hover.value("border-color")).isEqualTo("var(--color-border-strong)");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=GoldAccentContractTest`
Expected: FAIL — `.card:hover`, `.btn:hover`, `.btn-ghost:hover` and the toast border are unearned gold; `--color-border-strong` is undefined.

- [ ] **Step 3: Add the neutral hairline token**

In `tokens.css`, next to `--color-border`:

```css
  --color-border: #3a3125;          /* warm hairline */
  --color-border-strong: #564936;   /* the same hairline, awake — hover and grouping */
```

- [ ] **Step 4: Retire gold from ambient hover states**

`components.css` — `.card:hover` and `.btn:hover`:

```css
.card:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--highlight-candle), var(--shadow-warm-sm);
  transform: translateY(-2px);
}

.btn:hover {
  background: var(--color-surface-hover);
  border-color: var(--color-border-strong);
}

.btn-ghost:hover {
  color: var(--color-text);
  background: var(--elevation-floating-bg);
  border-color: var(--color-border-strong);
}
```

`.toast` — the left rule already carries the semantic color; the surrounding border goes neutral:

```css
.toast {
  background: var(--elevation-floating-bg);
  border: 1px solid var(--color-border-strong);
  border-left: 3px solid var(--color-accent);
  ...
}
```

- [ ] **Step 5: Make selection gold explicit**

Add, after `.card:hover` in `components.css`:

```css
/* Gold is the answer to "which one is chosen" and "which one is the action"
   (spec 2026-07-22 §10.2) — so it must be unmistakable where it does appear. */
.card[aria-current="true"],
.card.is-selected {
  border-color: var(--color-accent);
  box-shadow: var(--highlight-candle), 0 0 0 1px var(--color-gold-soft);
}
```

- [ ] **Step 6: Fix every remaining offender the test names**

Run: `./mvnw -q test -Dtest=GoldAccentContractTest`

For each remaining `file { selector }` in the failure report, decide which of three it is and act:
- it marks focus, selection or the primary action → add the matching state token to the selector (`:focus-visible`, `[aria-current="true"]`, `.is-selected`, `.btn-primary`) so it is self-describing;
- it is an in-world identity surface → add its selector to `IDENTITY_SURFACES` in the test with a one-line comment saying why;
- it is neither → swap `--color-accent` / `--color-gold-soft` for `--color-border-strong`.

- [ ] **Step 7: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=GoldAccentContractTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/css \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java
git commit -m "feat(visual): reserve gold for focus, selection and primary actions"
```

---

## Task 6: Grouping by spacing and hairlines, not by another slab

§10.2 asks for fewer nested same-color cards. This is the one rule that cannot be checked in the stylesheet — nesting is a property of the rendered tree — so it gets a browser gate that names every offender.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/visual/SurfaceNestingGateTest.java`
- Modify: `src/main/resources/static/css/components.css`

**Interfaces:**
- Consumes: `PopulatedCampaignFixture`, `PreparationSurfaceFixture`.
- Produces: the invariant "no bordered surface sits directly inside a bordered surface of exactly the same fill", relied on by Task 13's viewport gate for module legibility.

- [ ] **Step 1: Write the failing gate**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/visual/SurfaceNestingGateTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.visual;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Reduce nested same-color cards; use spacing, rules and
 * surface elevation to express grouping." A slab on an identical slab reads as noise, and
 * only the rendered tree knows it happened.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurfaceNestingGateTest {

    @LocalServerPort private int port;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private PreparationSurfaceFixture prepFixture;

    private static Playwright playwright;
    private static Browser browser;
    private PopulatedCampaignFixture.Seeded seeded;
    private PreparationSurfaceFixture.Seeded prepared;

    @BeforeAll
    void seedAndLaunch() {
        seeded = fixture.seed();
        prepared = prepFixture.seed();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    List<String> pages() {
        String c = "/campaigns/" + seeded.campaignId();
        String p = "/campaigns/" + prepared.campaignId();
        return List.of(c, c + "/adventures", c + "/party", c + "/encounters",
                c + "/session", p + "/encounters/" + prepared.encounterId());
    }

    @ParameterizedTest
    @MethodSource("pages")
    void noBorderedSurfaceSitsOnAnIdenticalBorderedSurface(String path) {
        try (Page page = browser.newPage()) {
            page.setViewportSize(1366, 768);
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            @SuppressWarnings("unchecked")
            List<String> offenders = (List<String>) page.evaluate("""
                    () => {
                      const painted = (el) => {
                        const s = getComputedStyle(el);
                        const hasFill = s.backgroundColor !== 'rgba(0, 0, 0, 0)'
                          && s.backgroundColor !== 'transparent';
                        const hasEdge = parseFloat(s.borderTopWidth) > 0
                          && s.borderTopColor !== 'rgba(0, 0, 0, 0)';
                        return hasFill && hasEdge ? s.backgroundColor : null;
                      };
                      const describe = (el) =>
                        el.tagName.toLowerCase() + (el.className ? '.' + String(el.className).trim().split(/\\s+/).join('.') : '');
                      const out = [];
                      for (const el of document.querySelectorAll('body *')) {
                        const fill = painted(el);
                        if (!fill) continue;
                        for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
                          const parentFill = painted(p);
                          if (!parentFill) continue;
                          if (parentFill === fill) out.push(describe(p) + ' > … > ' + describe(el));
                          break;
                        }
                      }
                      return [...new Set(out)];
                    }
                    """);

            assertThat(offenders)
                    .as("same-fill bordered surfaces nested on %s — flatten the inner one", path)
                    .isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run the gate to verify it fails**

Run: `./mvnw -q test -Dtest=SurfaceNestingGateTest`
Expected: FAIL, listing each nested pair as `outer > … > inner`.

- [ ] **Step 3: Flatten nested cards structurally**

Add to `components.css`, immediately after the `.card:active` rule:

```css
/* Grouping inside a card is spacing and a hairline, not a second slab of the same colour
   (spec 2026-07-22 §10.2). The inner surface keeps its content and loses its walls. */
.card .card,
.card .readiness-panel,
.card .prep-summary {
  background: transparent;
  border: none;
  box-shadow: none;
  border-radius: 0;
  padding-inline: 0;
  border-top: 1px solid var(--color-border);
  padding-block: var(--space-sm);
}

.card .card:first-child,
.card .readiness-panel:first-child,
.card .prep-summary:first-child {
  border-top: none;
  padding-top: 0;
}

.card .card:hover {
  background: transparent;
  border-color: var(--color-border);
  box-shadow: none;
  transform: none;
}
```

- [ ] **Step 4: Fix every remaining pair the gate names**

Run: `./mvnw -q test -Dtest=SurfaceNestingGateTest`

For each `outer > … > inner` pair still reported, apply exactly one of:
1. **flatten the inner surface** — drop its `background` and `border`, and express the grouping with `border-top: 1px solid var(--color-border)` plus block padding (preferred; this is what §10.2 asks for);
2. **raise the inner surface** — set `background: var(--elevation-floating-bg)` so it is a genuinely different elevation rather than a repeat;
3. **flatten the outer surface** — when the outer element is a layout wrapper that never needed to be a card, remove its `.card` class in the template.

Do not add the pair to an exception list; there is deliberately no exception list on this gate.

- [ ] **Step 5: Run the gate to verify it passes**

Run: `./mvnw -q test -Dtest=SurfaceNestingGateTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates \
        src/test/java/dev/hendrikhoemberg/dmhelper/visual/SurfaceNestingGateTest.java
git commit -m "feat(visual): express grouping with spacing and hairlines, not stacked slabs"
```

---

## Task 7: One elevation ladder

27 raw `z-index` values across six stylesheets, ranging from `1` to `10020`, encode an ordering nobody can see. §10.3 asks for one elevation model shared by modals, popovers, toasts and focused layers.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/ElevationModelContractTest.java`
- Modify: `src/main/resources/static/css/tokens.css`, `base.css`, `components.css`, `cockpit.css`, `cockpit-layout.css`

**Interfaces:**
- Consumes: `CssRules` (Task 1).
- Produces: the `--z-*` ladder in `tokens.css`. Every later task and every future overlay uses a rung; no new raw value above `2` is permitted anywhere.

The ladder preserves the current relative order exactly, so nothing changes stacking:

| Token | Value | Owners today |
|---|---|---|
| `--z-workspace-chrome` | `30` | `.cockpit-topbar__overflow-panel`, `.cockpit-preset-overflow__panel` |
| `--z-workspace-focus` | `40` | `.cockpit-focus-layer` |
| `--z-workspace-menu` | `50` | `.cockpit-arrange-menu`, `.roll-tooltip` |
| `--z-nav` | `100` | `.navbar`, `.detail-panel` |
| `--z-popover` | `200` | `.handout-quick-panel` |
| `--z-tooltip` | `300` | the hover tooltip in `base.css` |
| `--z-ambient` | `400` | `body::before` vignette |
| `--z-sheet-scrim` | `800` | `.sheet-backdrop` |
| `--z-sheet` | `810` | `.side-sheet` |
| `--z-overlay` | `900` | `.handout-overlay` |
| `--z-modal` | `1000` | `.modal`, `.modal-overlay`, `.command-palette-overlay` |
| `--z-shortcut` | `1100` | `#shortcut-overlay` |
| `--z-safety-sweep` | `1200` | `#screen-safety-sweep` (was 9998) |
| `--z-toast` | `1300` | `#toast-container` (was 10000) |
| `--z-filament` | `1400` | `#loading-filament` (was 10001) |
| `--z-presentation-preview` | `1500` | `.presentation-preview` (was 10020) |

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/ElevationModelContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.3: "Modals, popovers, toasts and focused detail layers follow one
 * elevation and focus model." A ladder you can read beats 27 numbers you cannot.
 */
class ElevationModelContractTest {

    private static final List<String> LADDER = List.of(
            "--z-workspace-chrome", "--z-workspace-focus", "--z-workspace-menu",
            "--z-nav", "--z-popover", "--z-tooltip", "--z-ambient",
            "--z-sheet-scrim", "--z-sheet", "--z-overlay", "--z-modal",
            "--z-shortcut", "--z-safety-sweep", "--z-toast", "--z-filament",
            "--z-presentation-preview");

    @Test
    void theLadderIsDefinedInAscendingOrder() {
        String tokens = CssRules.read("tokens.css");
        int previousValue = Integer.MIN_VALUE;

        for (String rung : LADDER) {
            var matcher = java.util.regex.Pattern
                    .compile(java.util.regex.Pattern.quote(rung) + "\\s*:\\s*(\\d+)")
                    .matcher(tokens);
            assertThat(matcher.find()).as("%s defined", rung).isTrue();
            int value = Integer.parseInt(matcher.group(1));
            assertThat(value).as("%s above the rung below it", rung).isGreaterThan(previousValue);
            previousValue = value;
        }
    }

    @Test
    void noStylesheetInventsItsOwnElevation() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            for (String value : rule.values("z-index")) {
                String v = value.trim();
                if (v.startsWith("var(--z-")) continue;
                // Local stacking inside one component is fine; anything that competes
                // with another component's layer must name a rung.
                if (v.matches("-?[0-2]|auto|inherit")) continue;
                offenders.add(rule.where() + " → z-index: " + v);
            }
        }

        assertThat(offenders).as("raw elevations outside the ladder").isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=ElevationModelContractTest`
Expected: FAIL — no ladder defined, ~16 raw elevations.

- [ ] **Step 3: Define the ladder in `tokens.css`**

Append to the `:root` block, after the motion tokens:

```css
  /* elevation ladder — one ordering, readable end to end (spec 2026-07-22 §10.3).
     Native <dialog> lives in the browser top layer and outranks all of these. */
  --z-workspace-chrome: 30;
  --z-workspace-focus: 40;
  --z-workspace-menu: 50;
  --z-nav: 100;
  --z-popover: 200;
  --z-tooltip: 300;
  --z-ambient: 400;
  --z-sheet-scrim: 800;
  --z-sheet: 810;
  --z-overlay: 900;
  --z-modal: 1000;
  --z-shortcut: 1100;
  --z-safety-sweep: 1200;
  --z-toast: 1300;
  --z-filament: 1400;
  --z-presentation-preview: 1500;
```

- [ ] **Step 4: Replace every raw elevation with its rung**

`base.css`: `body::before` `400` → `var(--z-ambient)`; `.navbar` `100` → `var(--z-nav)`; the tooltip at 353 `300` → `var(--z-tooltip)`; `#loading-filament` `10001` → `var(--z-filament)`; `#shortcut-overlay` `1100` → `var(--z-shortcut)`; `#screen-safety-sweep` `9998` → `var(--z-safety-sweep)`.

`components.css`: `.modal` (523) `1000` → `var(--z-modal)`; `.handout-overlay` `900` → `var(--z-overlay)`; the handout quick panel (1063) `200` → `var(--z-popover)`; `.detail-panel` (1475) `100` → `var(--z-nav)`; `.roll-tooltip` `50` → `var(--z-workspace-menu)`; `.command-palette-overlay` `1000` → `var(--z-modal)`; `#toast-container` `10000` → `var(--z-toast)`; `.sheet-backdrop` `800` → `var(--z-sheet-scrim)`; `.side-sheet` (2242) `801` → `var(--z-sheet)`; `.modal-overlay` (2308) `1000` → `var(--z-modal)`.

`cockpit.css`: `.presentation-preview` `10020` → `var(--z-presentation-preview)`.

`cockpit-layout.css`: the overflow panels `30` → `var(--z-workspace-chrome)`; `.cockpit-focus-layer` `40` → `var(--z-workspace-focus)`; `.cockpit-arrange-menu` `50` → `var(--z-workspace-menu)`.

Leave the `z-index: 1` and `z-index: 2` values in `.book-cover` children, `.campaign-sigil`, the card badge and the preview panel — those are local stacking inside one component and the test allows them.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=ElevationModelContractTest`
Expected: PASS

- [ ] **Step 6: Verify the ordering still holds in a browser**

Run: `./mvnw -q test -Dtest=CoreSessionLoopSmokeTest#lifecycleDialogGeometryAndFocusAtMultipleViewports`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/css \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/ElevationModelContractTest.java
git commit -m "feat(visual): replace 27 ad-hoc z-indexes with one readable elevation ladder"
```

---

## Task 8: Controls that look like the app they are in

The readiness report shipped in workstream C has **no stylesheet at all** — it renders as browser-default markup on the campaign home, the first surface a DM sees. Its two submit buttons carry no class. This task gives it the app's control language and installs the guard that stops the next surface shipping bare.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/ControlConsistencyContractTest.java`
- Create: `src/main/resources/templates/common/_field-error.html`
- Modify: `src/main/resources/static/css/surfaces.css`, `components.css`
- Modify: `src/main/resources/templates/campaigns/_readiness.html`

**Interfaces:**
- Consumes: the `.btn`, `.btn-primary`, `.btn-ghost`, `.btn-danger`, `.form-input` vocabulary from `components.css`.
- Produces: `.readiness-panel` and its children as styled components; Task 14's rehearsal step 1 reads `.readiness-item--blocker` and `.readiness-badge`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/ControlConsistencyContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.3: consistent dark controls, labels and focus rings. A surface
 * that ships without a stylesheet is not a styling omission — it is a surface that looks
 * like a different application.
 */
class ControlConsistencyContractTest {

    @Test
    void everyButtonInAGovernedTemplateDeclaresItsRole() throws IOException {
        Pattern button = Pattern.compile("<button(?![^>]*\\bclass=)[^>]*>");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                Matcher m = button.matcher(Files.readString(template));
                while (m.find()) offenders.add(template + " → " + m.group());
            }
        }

        assertThat(offenders).as("buttons with no btn class").isEmpty();
    }

    @Test
    void theReadinessReportIsStyled() {
        String surfaces = CssRules.read("surfaces.css");

        assertThat(surfaces).contains(
                ".readiness-panel {",
                ".readiness-panel.is-blocked",
                ".readiness-panel.is-ready",
                ".readiness-badge {",
                ".readiness-item {",
                ".readiness-item--blocker",
                ".readiness-item__title",
                ".readiness-item__detail",
                ".readiness-item__actions");
    }

    @Test
    void readinessControlsUseTheAppVocabulary() throws IOException {
        String readiness = Files.readString(
                Path.of("src/main/resources/templates/campaigns/_readiness.html"));

        assertThat(readiness)
                .contains("class=\"btn btn-primary btn-xs\">Accept<")
                .contains("class=\"btn btn-xs\">Set kind<")
                .contains("class=\"form-input form-input--xs\"")
                .contains("class=\"btn btn-ghost btn-xs readiness-item__repair\"");
    }

    @Test
    void aCompactFormInputVariantExists() {
        assertThat(CssRules.read("components.css")).contains(".form-input--xs {");
    }

    /** Spec section 10.3: "Primary actions are singular and obvious within each module." */
    @Test
    void noSurfaceOffersTwoPrimaryActions() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                int count = html.split("btn-primary", -1).length - 1;
                if (count > 1) offenders.add(template + " → " + count + " primary actions");
            }
        }

        assertThat(offenders).as("more than one primary action in one template").isEmpty();
    }

    /** Spec section 10.3: consistent error placement — one fragment, used everywhere. */
    @Test
    void fieldErrorsUseTheSharedFragment() throws IOException {
        assertThat(Files.readString(Path.of("src/main/resources/templates/common/_field-error.html")))
                .contains("th:fragment=\"field-error(field)\"")
                .contains("class=\"field-error\"")
                .contains("role=\"alert\"");

        assertThat(CssRules.read("components.css"))
                .contains(".field-error {")
                .contains("color: var(--color-danger)");

        Pattern adHoc = Pattern.compile("th:errors=\"\\*\\{[^}]+}\"");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                if (adHoc.matcher(html).find() && !html.contains("common/_field-error")) {
                    offenders.add(template.toString());
                }
            }
        }

        assertThat(offenders).as("errors rendered outside the shared fragment").isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=ControlConsistencyContractTest`
Expected: FAIL on all four methods.

- [ ] **Step 3: Add the compact input variant**

In `components.css`, after the `.btn-xs` rule:

```css
.form-input--xs {
  padding: 2px 8px;
  font-size: var(--text-sm);
  height: auto;
}
```

- [ ] **Step 4: Give errors one place to appear**

Create `src/main/resources/templates/common/_field-error.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<!-- One error, directly under the control that caused it (spec 2026-07-22 §10.3).
     Callers pass the bound field name: ~{common/_field-error :: field-error('name')} -->
<p th:fragment="field-error(field)" class="field-error" role="alert"
   th:if="${#fields.hasErrors(field)}" th:errors="${'*{' + field + '}'}">Error</p>
</html>
```

Add to `components.css`, after `.form-group`:

```css
.field-error {
  margin: var(--space-xs) 0 0;
  font-size: var(--text-sm);
  color: var(--color-danger);
}
```

Then replace each ad-hoc error render the test names:

Run: `grep -rln 'th:errors' src/main/resources/templates/`

For each hit, swap the inline element for the fragment, keeping the field name:

```html
<th:block th:replace="~{common/_field-error :: field-error('name')}"></th:block>
```

- [ ] **Step 5: Style the readiness report**

Append to `surfaces.css`:

```css
/* ── Operational readiness (spec 2026-07-22 §8.1) ──
   The first thing a DM reads on the campaign home, and until now the only surface in the
   app with no stylesheet. Blocked and ready are told apart by a semantic edge, not by gold. */
.readiness-panel {
    border: 1px solid var(--color-border);
    border-left: 3px solid var(--color-border-strong);
    border-radius: var(--radius);
    background: var(--elevation-raised-bg);
    padding: var(--space-md);
    margin-bottom: var(--space-lg);
}

.readiness-panel.is-blocked { border-left-color: var(--color-warning); }
.readiness-panel.is-ready { border-left-color: var(--color-success); }

.readiness-panel__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--space-md);
    margin-bottom: var(--space-sm);
}

.readiness-panel__head h3 { margin: 0; font-size: var(--text-base); font-weight: 600; }

.readiness-badge {
    font-size: var(--text-sm);
    text-transform: uppercase;
    letter-spacing: 0.1em;
    padding: 2px 10px;
    border-radius: var(--radius);
    border: 1px solid currentColor;
}

.readiness-panel.is-blocked .readiness-badge { color: var(--color-warning); }
.readiness-panel.is-ready .readiness-badge { color: var(--color-success); }

.readiness-empty { margin: 0; color: var(--color-text-muted); font-size: var(--text-sm); }

.readiness-list { list-style: none; margin: 0; padding: 0; }

.readiness-item {
    display: grid;
    grid-template-columns: minmax(12rem, 1fr) 2fr auto;
    align-items: baseline;
    gap: var(--space-sm) var(--space-md);
    padding: var(--space-sm) 0;
    border-top: 1px solid var(--color-border);
}

.readiness-item:first-child { border-top: none; }

.readiness-item--blocker .readiness-item__title { color: var(--color-text); font-weight: 600; }
.readiness-item--accepted .readiness-item__title { color: var(--color-text-muted); }

.readiness-item__detail { color: var(--color-text-muted); font-size: var(--text-sm); }

.readiness-item__actions {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    justify-self: end;
}

.readiness-accepted > summary,
.readiness-advisories > summary {
    cursor: pointer;
    color: var(--color-text-muted);
    font-size: var(--text-sm);
    padding: var(--space-sm) 0 0;
}

@media (max-width: 900px) {
    .readiness-item { grid-template-columns: 1fr; }
    .readiness-item__actions { justify-self: start; }
}
```

- [ ] **Step 6: Give the readiness controls the app's vocabulary**

In `campaigns/_readiness.html`, replace the three bare controls:

```html
        <a th:with="href=${repairService.hrefFor(campaignId, item.repairKind(), item.targetId())}"
           th:if="${href != null}" th:href="${href}"
           class="btn btn-ghost btn-xs readiness-item__repair">Fix in preparation</a>
```

```html
          <input type="hidden" name="itemKey" th:value="${item.key()}"/>
          <button type="submit" class="btn btn-primary btn-xs">Accept</button>
```

```html
          <select name="kind" class="form-input form-input--xs">
            <option th:each="k : ${T(dev.hendrikhoemberg.dmhelper.handout.data.Handout.AssetKind).values()}"
                    th:value="${k}" th:text="${k}"></option>
          </select>
          <button type="submit" class="btn btn-xs">Set kind</button>
```

and the undo control:

```html
            <button type="button" class="btn btn-ghost btn-xs readiness-item__undo"
                    th:hx-delete="@{/campaigns/{cid}/readiness/accept(cid=${campaignId},itemKey=${item.key()})}"
                    hx-target="closest .readiness-panel" hx-swap="outerHTML">Undo</button>
```

- [ ] **Step 7: Fix every other classless button, and every second primary action, the test names**

Run: `./mvnw -q test -Dtest=ControlConsistencyContractTest`

For each classless button, add the class that states its role: `btn btn-primary` for the one primary action in that surface, `btn` for secondary, `btn-ghost` for tertiary/inline, `btn-danger` for destructive.

For each template with more than one `btn-primary`, decide which single action is the surface's primary — the one the DM came to the page to perform — and demote the rest to `btn`. Where a template genuinely hosts two independent modules (a list page with both a create action and an import action), split it so each module owns its own primary, rather than weakening the rule.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=ControlConsistencyContractTest`
Expected: PASS

- [ ] **Step 9: Run the readiness and form suites**

Run: `./mvnw -q test -Dtest=CampaignAdminSurfaceTest,SurfaceSeparationContractTest,SurfaceNestingGateTest,InteractionFailureContractTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/ControlConsistencyContractTest.java
git commit -m "feat(visual): style the readiness report and give every control a declared role"
```

---

## Task 9: Destructive actions kept at a distance

§10.3: destructive actions are visually distinct and never adjacent to the most common runtime action without separation. Today `btn-danger` appears in 43 templates with no rule about where it may sit.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DestructiveActionContractTest.java`
- Modify: `src/main/resources/static/css/components.css`
- Modify: the templates the test names

**Interfaces:**
- Consumes: the `.btn-danger` variant.
- Produces: `.action-row` and `.action-row__destructive` — the separator contract every surface with a delete uses.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/DestructiveActionContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.3: "Destructive actions are visually distinct and never adjacent
 * to the most common runtime action without separation." At the table, a misclick is a
 * deleted encounter mid-combat.
 */
class DestructiveActionContractTest {

    /** btn-primary and btn-danger touching, with nothing between them. */
    private static final Pattern ADJACENT = Pattern.compile(
            "(?s)btn-primary[^>]*>.{0,400}?btn-danger|btn-danger[^>]*>.{0,400}?btn-primary");

    @Test
    void noPrimaryActionSitsBesideADestructiveOne() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                Matcher m = ADJACENT.matcher(html);
                while (m.find()) {
                    String span = m.group();
                    // A declared separator is exactly what makes this safe.
                    if (span.contains("action-row__destructive")) continue;
                    offenders.add(template.toString());
                    break;
                }
            }
        }

        assertThat(offenders)
                .as("primary and destructive actions adjacent without a declared separator")
                .isEmpty();
    }

    @Test
    void theSeparatorIsARealVisualGap() {
        CssRules.Rule rule = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".action-row__destructive"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".action-row__destructive is not defined"));

        assertThat(rule.body())
                .contains("margin-left: auto")
                .contains("padding-left: var(--space-lg)")
                .contains("border-left: 1px solid var(--color-border)");
    }

    @Test
    void destructiveButtonsAreDistinctFromEverythingElse() {
        CssRules.Rule danger = CssRules.of("components.css").stream()
                .filter(r -> r.selector().equals(".btn-danger"))
                .findFirst()
                .orElseThrow();

        assertThat(danger.body()).contains("color: var(--color-danger)");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=DestructiveActionContractTest`
Expected: FAIL — `.action-row__destructive` is undefined, and the templates that pair a delete with a primary action are listed.

- [ ] **Step 3: Define the separator**

In `components.css`, after `.btn-warning:hover`:

```css
/* A destructive action is reachable but never neighbourly (spec 2026-07-22 §10.3):
   it sits after a gap and a rule, on the far side of the row from the primary action. */
.action-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.action-row__destructive {
  margin-left: auto;
  padding-left: var(--space-lg);
  border-left: 1px solid var(--color-border);
}
```

Replace the ad-hoc rule at `components.css:2800`:

```css
.dash-data-tools .btn-danger { margin-left: auto; }
```

with nothing — the surface uses `.action-row__destructive` instead (next step).

- [ ] **Step 4: Apply the separator in every template the test names**

For each offending template, wrap the destructive control:

```html
<div class="action-row">
    <button class="btn btn-primary" ...>Save</button>
    <a class="btn btn-ghost" ...>Cancel</a>
    <span class="action-row__destructive">
        <button class="btn btn-danger" ...>Delete</button>
    </span>
</div>
```

Start with `campaigns/settings.html` (the data-tools row that used `.dash-data-tools .btn-danger`), then work through the list the failure report prints.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=DestructiveActionContractTest`
Expected: PASS

- [ ] **Step 6: Confirm no Run surface gained a destructive action**

Run: `./mvnw -q test -Dtest=SurfaceSeparationContractTest`
Expected: PASS — workstream D already forbids destructive actions in the cockpit; this confirms Task 9 did not smuggle one in.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/css/components.css src/main/resources/templates \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/DestructiveActionContractTest.java
git commit -m "feat(visual): separate destructive actions from the primary action"
```

---

## Task 10: Save, loading, connection and error state in one quiet cluster

The cockpit tells a DM the scene, the turn, the encounter and the presentation mode. It does not tell them whether the last action saved, or whether the table display is still connected — and §11.3 fails the gate if the DM cannot identify save/error state without scrolling. The loading filament and toast system already exist; this task adds the persistent cluster they lack, and the server-side signal it needs.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/TableStateWebSocketHandler.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/TableConnectionStatusController.java`
- Create: `src/main/resources/static/js/runtime-status.js`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/resources/static/css/cockpit-layout.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusSurfaceTest.java`

**Interfaces:**
- Consumes: the existing `htmx:beforeRequest` / `htmx:afterRequest` / `htmx:responseError` / `htmx:sendError` events wired in `ui-elevation.js`.
- Produces: `GET /api/table/status` → `{"connected": <int>}`; the DOM contract `#runtimeStatus` containing `[data-status-save]` and `[data-status-table]`, each carrying `data-state` ∈ `idle | busy | saved | error` and `connected | disconnected`. Task 13 and Task 14 both assert on these.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusSurfaceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.live.TableStateWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 2026-07-22 section 10.3 and section 11.3: save, loading, connection and error status
 * are visible without dominating the command bar — and without scrolling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RuntimeStatusSurfaceTest {

    @Autowired private MockMvc mvc;
    @Autowired private TableStateWebSocketHandler handler;

    @Test
    void tableStatusEndpointReportsTheConnectedCount() throws Exception {
        mvc.perform(get("/api/table/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(handler.connectedCount()));
    }

    @Test
    void theCockpitDeclaresTheStatusCluster() throws IOException {
        String cockpit = Files.readString(
                Path.of("src/main/resources/templates/session/cockpit.html"));

        assertThat(cockpit)
                .contains("id=\"runtimeStatus\"")
                .contains("data-status-save")
                .contains("data-status-table")
                .contains("role=\"status\"")
                .contains("aria-live=\"polite\"");
    }

    @Test
    void theStatusScriptCoversAllFourStates() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/runtime-status.js"));

        assertThat(js)
                .contains("htmx:beforeRequest")
                .contains("htmx:afterRequest")
                .contains("htmx:responseError")
                .contains("htmx:sendError")
                .contains("'busy'")
                .contains("'saved'")
                .contains("'error'")
                .contains("/api/table/status");
    }

    @Test
    void theStatusClusterStaysQuietUntilItHasSomethingToSay() throws IOException {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/cockpit-layout.css"));

        assertThat(css)
                .contains("#runtimeStatus")
                .contains("[data-status-save][data-state=\"idle\"]")
                .contains("[data-status-save][data-state=\"error\"]");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=RuntimeStatusSurfaceTest`
Expected: FAIL — no endpoint, no cluster, no script.

- [ ] **Step 3: Expose the connected count**

In `TableStateWebSocketHandler`, next to `afterConnectionClosed`:

```java
    /** How many table displays are currently attached. Read by the cockpit status cluster. */
    public int connectedCount() {
        return sessions.size();
    }
```

- [ ] **Step 4: Add the status endpoint**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/TableConnectionStatusController.java`:

```java
package dev.hendrikhoemberg.dmhelper.live.web;

import dev.hendrikhoemberg.dmhelper.live.TableStateWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The DM's answer to "is the table screen still listening?". Deliberately a poll rather than
 * a second socket: a socket opened to observe the socket would count itself.
 */
@RestController
public class TableConnectionStatusController {

    private final TableStateWebSocketHandler handler;

    public TableConnectionStatusController(TableStateWebSocketHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/api/table/status")
    public Map<String, Integer> status() {
        return Map.of("connected", handler.connectedCount());
    }
}
```

- [ ] **Step 5: Write the status script**

Create `src/main/resources/static/js/runtime-status.js`:

```javascript
(function () {
  'use strict';

  const POLL_MS = 5000;

  function init() {
    const cluster = document.getElementById('runtimeStatus');
    if (!cluster) return;

    const save = cluster.querySelector('[data-status-save]');
    const table = cluster.querySelector('[data-status-table]');

    let inFlight = 0;
    let settleTimer = null;

    function setSave(state, label) {
      save.dataset.state = state;
      save.textContent = label;
    }

    document.body.addEventListener('htmx:beforeRequest', (evt) => {
      const verb = (evt.detail.requestConfig?.verb || '').toLowerCase();
      if (verb === 'get') return;
      inFlight++;
      clearTimeout(settleTimer);
      setSave('busy', 'Saving…');
    });

    document.body.addEventListener('htmx:afterRequest', (evt) => {
      const verb = (evt.detail.requestConfig?.verb || '').toLowerCase();
      if (verb === 'get') return;
      inFlight = Math.max(0, inFlight - 1);
      const xhr = evt.detail.xhr;
      if (!xhr || xhr.status < 200 || xhr.status >= 300) return;
      if (inFlight > 0) return;
      setSave('saved', 'Saved');
      // The cluster is a status line, not a log: it returns to quiet on its own.
      settleTimer = setTimeout(() => setSave('idle', 'Up to date'), 4000);
    });

    function failed() {
      inFlight = 0;
      clearTimeout(settleTimer);
      setSave('error', 'Not saved');
    }

    document.body.addEventListener('htmx:responseError', failed);
    document.body.addEventListener('htmx:sendError', failed);

    async function pollTable() {
      try {
        const response = await fetch('/api/table/status', { headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(response.status);
        const { connected } = await response.json();
        table.dataset.state = connected > 0 ? 'connected' : 'disconnected';
        table.textContent = connected > 0
          ? (connected === 1 ? 'Table connected' : connected + ' tables connected')
          : 'No table screen';
      } catch (e) {
        table.dataset.state = 'disconnected';
        table.textContent = 'Table unreachable';
      }
    }

    pollTable();
    setInterval(pollTable, POLL_MS);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
```

- [ ] **Step 6: Render the cluster in the command bar**

In `session/cockpit.html`, immediately after the presentation-mode badge and before the preset picker label:

```html
    <span id="runtimeStatus" class="cockpit-status" role="status" aria-live="polite">
      <span class="cockpit-status__atom" data-status-save data-state="idle">Up to date</span>
      <span class="cockpit-status__atom" data-status-table data-state="disconnected">No table screen</span>
    </span>
```

Load the script alongside the other cockpit scripts in `fragments/head.html`:

```html
    <script th:src="@{/js/runtime-status.js}" defer></script>
```

- [ ] **Step 7: Style the cluster so it stays quiet**

Append to `cockpit-layout.css`:

```css
/* Status you can find without hunting and ignore without effort (spec §10.3).
   Quiet by default; only the failure state raises its voice. */
#runtimeStatus {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.cockpit-status__atom {
  color: var(--color-text-muted);
}

[data-status-save][data-state="idle"] { opacity: 0.6; }
[data-status-save][data-state="busy"] { color: var(--color-text); }
[data-status-save][data-state="saved"] { color: var(--color-success); }
[data-status-save][data-state="error"] {
  color: var(--color-danger);
  font-weight: 600;
  opacity: 1;
}

[data-status-table][data-state="connected"] { color: var(--color-success); }
[data-status-table][data-state="disconnected"] { color: var(--color-text-muted); }
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=RuntimeStatusSurfaceTest`
Expected: PASS

- [ ] **Step 9: Confirm the cockpit still fits and stays safe**

Run: `./mvnw -q test -Dtest=CockpitWorkbenchTemplateContractTest,RuntimeModuleSafetyContractTest`
Expected: PASS. The status cluster reports application state, not campaign content, so it is **not** `data-screen-sensitive` — if `RuntimeModuleSafetyContractTest` demands a classification for new topbar children, declare it non-sensitive there.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/live \
        src/main/resources/static/js/runtime-status.js \
        src/main/resources/static/css/cockpit-layout.css \
        src/main/resources/templates/session/cockpit.html \
        src/main/resources/templates/fragments/head.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusSurfaceTest.java
git commit -m "feat(cockpit): show save, error and table-connection state in the command bar"
```

---

## Task 11: Motion confirms, never delays

§10.2: "Motion confirms state changes but does not delay table-time actions." The primary button carries a 600 ms sheen, HP tints run 700 ms, and the reduced-motion block does not cover every animation the app plays.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/MotionBudgetContractTest.java`
- Modify: `src/main/resources/static/css/components.css`, `base.css`

**Interfaces:**
- Consumes: `CssRules` (Task 1), the existing `--duration-*` tokens.
- Produces: the invariant that runtime and control feedback never exceeds `--duration-structural` (320 ms), and that a single global reduced-motion block neutralises every animation.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/MotionBudgetContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2 and section 11.2: motion confirms a state change and then gets
 * out of the way, and a DM who asked the OS for less motion gets less motion everywhere.
 */
class MotionBudgetContractTest {

    private static final int TABLE_BUDGET_MS = 320;

    /** Theatrical by design and never in the path of a table-time action. */
    private static final Set<String> THEATRICAL = Set.of(
            ".handout-overlay .handout-frame",
            ".handout-overlay.closing .handout-frame",
            ".page-header .rule-taper--gold");

    private static final Pattern MS = Pattern.compile("(\\d+)ms");

    @Test
    void interactionFeedbackStaysInsideTheTableBudget() {
        List<String> offenders = new ArrayList<>();

        for (CssRules.Rule rule : CssRules.of(CssRules.ALL_FILES)) {
            if (rule.file().equals("tokens.css")) continue;
            if (THEATRICAL.contains(rule.selector())) continue;
            if (rule.selector().startsWith("@keyframes")) continue;

            List<String> declarations = new ArrayList<>();
            declarations.addAll(rule.values("transition"));
            declarations.addAll(rule.values("animation"));
            declarations.addAll(rule.values("animation-duration"));
            declarations.addAll(rule.values("transition-duration"));

            for (String declaration : declarations) {
                if (declaration.contains("--duration-theatrical")) {
                    offenders.add(rule.where() + " → theatrical duration on a control");
                    continue;
                }
                Matcher m = MS.matcher(declaration);
                while (m.find()) {
                    if (Integer.parseInt(m.group(1)) > TABLE_BUDGET_MS) {
                        offenders.add(rule.where() + " → " + m.group() + " (raw)");
                    }
                }
            }
        }

        assertThat(offenders).as("motion that delays a table-time action").isEmpty();
    }

    @Test
    void reducedMotionNeutralisesEveryAnimationGlobally() {
        String base = CssRules.read("base.css");

        assertThat(base).contains("@media (prefers-reduced-motion: reduce)");
        assertThat(base).contains("""
                  *, *::before, *::after {
                    animation-duration: 1ms !important;
                    animation-iteration-count: 1 !important;
                    transition-duration: 1ms !important;
                    scroll-behavior: auto !important;
                  }""");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=MotionBudgetContractTest`
Expected: FAIL — the `.btn-primary` 600 ms sheen, the 700 ms HP tints, the 400 ms round pulse, the `dismissHandout` 600 ms, plus a missing global reduced-motion block.

- [ ] **Step 3: Bring control feedback inside the budget**

`components.css` — `.btn-primary`:

```css
.btn-primary {
  background: var(--gold-sheen);
  background-size: 200% 100%;
  border-color: var(--color-accent);
  color: var(--color-bg);
  transition: background-position var(--duration-structural) var(--ease-out),
              transform var(--duration-micro) var(--ease-out),
              box-shadow var(--duration-micro) var(--ease-out);
}
```

`components.css` — the HP tints and the round pulse:

```css
.combatant-row.hp-damage { animation: hp-tint-damage var(--duration-structural) var(--ease-out); }
.combatant-row.hp-heal { animation: hp-tint-heal var(--duration-structural) var(--ease-out); }
```

```css
.round-counter.pulse { animation: round-pulse var(--duration-structural) var(--ease-spring); }
```

`components.css` — the turn marker (`250ms` raw) becomes:

```css
    transition: transform var(--duration-standard) var(--ease-spring),
                opacity var(--duration-micro) var(--ease-out);
```

- [ ] **Step 4: Fix the remaining raw durations the test names**

For each `file { selector } → NNNms (raw)` in the report, swap the literal for the nearest token: ≤120 ms → `var(--duration-micro)`, ≤200 ms → `var(--duration-standard)`, ≤320 ms → `var(--duration-structural)`. Anything above 320 ms that is genuinely theatrical (a handout unfolding onto the table screen) keeps `var(--duration-theatrical)` **and** gets its selector added to `THEATRICAL` with a comment naming why it is not in a table-time path.

Also align the JS timeout with the CSS in `ui-elevation.js`:

```javascript
  window.dismissHandout = function(overlay) {
    if (overlay.classList.contains('closing')) return;
    overlay.classList.add('closing');
    setTimeout(() => overlay.remove(), 550);
  };
```

- [ ] **Step 5: Install the global reduced-motion block**

Replace the two partial blocks at `base.css:540` and `:544` with one:

```css
/* A DM who asked the OS for less motion gets less motion everywhere — including the
   surfaces added after this rule was written (spec 2026-07-22 §11.2). */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 1ms !important;
    scroll-behavior: auto !important;
  }
}
```

Keep the component-level reduced-motion blocks in `cockpit.css`, `cockpit-layout.css` and `components.css` — they suppress `transform` and `opacity` end states that a duration override alone cannot fix.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=MotionBudgetContractTest`
Expected: PASS

- [ ] **Step 7: Confirm the animated flows still work**

Run: `./mvnw -q test -Dtest=CoreSessionLoopSmokeTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/css src/main/resources/static/js/ui-elevation.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/MotionBudgetContractTest.java
git commit -m "feat(visual): keep motion inside the table budget and honour reduced motion"
```

---

## Task 12: A session-ready synthetic campaign

The rehearsal in §11.3 needs a campaign shaped like the imported Phandelver package — hostile scene with statblock-linked participants, a calibrated playable map, a DM-only source page and a reviewed derivative, quests, a party of four — and it must be entirely synthetic (§1). `PopulatedCampaignFixture` covers content richness but not operational readiness; this fixture covers readiness.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`

**Interfaces:**
- Consumes: `PopulatedCampaignFixture` (for the content spine), `GameMapService`, `HandoutService`, `StatBlockRepository`, `PartyMemberRepository`, `SceneStructuredContentService`.
- Produces:

```java
public record Seeded(
        UUID campaignId,
        UUID adventureId,
        UUID hostileSceneId,       // participants resolve to statblocks; seeds an encounter
        UUID branchSceneId,        // a transition target, for rehearsal step 3
        UUID playableMapId,        // grid + scale metadata, linked to hostileSceneId
        UUID playerSafeHandoutId,  // PLAYER_SAFE, presentable as stored
        UUID dmSourceHandoutId,    // DM_SOURCE, must never present
        UUID derivativeHandoutId,  // PLAYER_DERIVATIVE from dmSourceHandoutId
        UUID questId,
        List<UUID> partyMemberIds) // exactly four
```
and `Seeded seed(Shape shape)` plus `Seeded seed()` (defaults to `Shape.LINEAR_ONE_MAP`). `Shape` is added in Task 15; Task 12 defines the enum with the single `LINEAR_ONE_MAP` constant so Task 15 only adds a branch.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 8.3 and section 11.3: the rehearsal needs a campaign that is
 * session-ready, Phandelver-shaped and entirely synthetic.
 */
@SpringBootTest
class ReleaseRehearsalFixtureTest {

    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private CampaignReadinessFacade readiness;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private GameMapRepository mapRepository;
    @Autowired private PartyMemberRepository partyMemberRepository;

    @Test
    void theSeededCampaignIsSessionReady() {
        var seeded = fixture.seed();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).sessionReady())
                .as("a rehearsal that starts blocked proves nothing about the rehearsal")
                .isTrue();
    }

    @Test
    void theHostileSceneCanSeedAnEncounterFromResolvedParticipants() {
        var seeded = fixture.seed();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).byState(ReadinessState.BLOCKER))
                .isEmpty();
        assertThat(mapRepository.findById(seeded.playableMapId()).orElseThrow().getGridWidth())
                .isPositive();
    }

    @Test
    void theAssetSetCoversAllThreeClassifications() {
        var seeded = fixture.seed();

        assertThat(handoutRepository.findById(seeded.playerSafeHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.PLAYER_SAFE);
        assertThat(handoutRepository.findById(seeded.dmSourceHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.DM_SOURCE);
        assertThat(handoutRepository.findById(seeded.derivativeHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.PLAYER_DERIVATIVE);
    }

    @Test
    void thePartyHasFourMembers() {
        var seeded = fixture.seed();

        assertThat(seeded.partyMemberIds()).hasSize(4);
        assertThat(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(seeded.campaignId()))
                .hasSize(4);
    }

    @Test
    void nothingInTheFixtureCameFromAPublishedCampaign() {
        var seeded = fixture.seed();
        String everything = fixture.textualContentOf(seeded);

        assertThat(everything.toLowerCase())
                .doesNotContain("phandelver", "klarg", "cragmaw", "wave echo", "sildar",
                        "gundren", "rockseeker", "neverwinter", "tresendar");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest`
Expected: FAIL — `ReleaseRehearsalFixture` does not exist.

- [ ] **Step 3: Write the fixture**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`. Model it on `PopulatedCampaignFixture` — a `@Component` with `@Transactional` seed methods, autowired services rather than raw repositories wherever a service exists, and a `Seeded` record. Structure:

```java
package dev.hendrikhoemberg.dmhelper.support;

// imports mirror PopulatedCampaignFixture

/**
 * A synthetic campaign with the operational shape of a converted published module: hostile
 * scenes whose participants resolve to statblocks, one calibrated playable map, a reviewed
 * player-safe derivative cropped from a DM-only source page, quests, and a party of four.
 *
 * Every name here is invented. Spec section 1 forbids committing anything derived from a
 * published campaign, and ReleaseRehearsalFixtureTest enforces it.
 */
@Component
public class ReleaseRehearsalFixture {

    public enum Shape {
        /** One chapter, one map, one hostile scene. The baseline rehearsal. */
        LINEAR_ONE_MAP
    }

    public record Seeded(
            UUID campaignId,
            UUID adventureId,
            UUID hostileSceneId,
            UUID branchSceneId,
            UUID playableMapId,
            UUID playerSafeHandoutId,
            UUID dmSourceHandoutId,
            UUID derivativeHandoutId,
            UUID questId,
            List<UUID> partyMemberIds) {
    }

    // ... constructor injection of CampaignRepository, AdventureService,
    //     SceneStructuredContentService, StatBlockRepository, GameMapService,
    //     HandoutService, QuestService, PartyMemberRepository, EncounterService

    @Transactional
    public Seeded seed() {
        return seed(Shape.LINEAR_ONE_MAP);
    }

    @Transactional
    public Seeded seed(Shape shape) {
        // 1. Campaign "The Hollow Beacon <uuid-suffix>" — suffix keeps the shared in-mem DB
        //    from colliding across test classes, exactly as CoreSessionLoopSmokeTest does.
        // 2. Adventure + one chapter + three scenes:
        //      - approachScene   (exploration, read-aloud, transition -> hostileScene)
        //      - hostileScene    (4 participants, each linked to a seeded StatBlock,
        //                         setMapRequirement(SceneMapRequirement.REQUIRED),
        //                         setMap(playableMap))
        //      - branchScene     (transition target from approachScene, for rehearsal step 3)
        // 3. Four StatBlocks via StatBlockRepository with ContentSource.CUSTOM:
        //      "Bog Sentinel" CR 1/2, "Bog Skirmisher" x2 CR 1/4, "Marsh Warden" CR 2.
        //      Give each an initiative modifier so Task 14 can roll and compare.
        // 4. One GameMap "Beacon Undercroft" via GameMapService: gridWidth 20, gridHeight 15,
        //    cellSizePx 64, showGrid true — linked to hostileScene.
        // 5. Three handouts via HandoutService.createImported, each then classified with
        //    Handout.setSafetyClassification(...) and Handout.setAssetKind(...):
        //      playerSafe -> SafetyClassification.PLAYER_SAFE,       AssetKind.REGIONAL_MAP
        //                    ("Beacon Approach (player map)")
        //      dmSource   -> SafetyClassification.DM_SOURCE,         AssetKind.SOURCE_PAGE
        //                    ("Undercroft reference page")
        //      derivative -> SafetyClassification.PLAYER_DERIVATIVE, AssetKind.PLAYER_HANDOUT,
        //                    sourceHandout = dmSource (the provenance link the entity keeps)
        // 6. One quest with two objectives, one already complete.
        // 7. Four PartyMembers: "Ilsa Fenwright" (AC 18, HP 34, passive perception 14),
        //    "Ordo Brack" (AC 14, HP 27, passive 11), "Nesh Vell" (AC 12, HP 21, passive 16),
        //    "Tamsin Aroe" (AC 15, HP 25, passive 13).
        // 8. Accept nothing on the readiness report: the fixture must be ready on its merits,
        //    because rehearsal step 1 asks the DM to *resolve* blockers, not to accept them.
    }

    /** Everything the provenance test needs to scan, in one string. */
    @Transactional(readOnly = true)
    public String textualContentOf(Seeded seeded) {
        // Concatenate campaign name, adventure name, every scene title and body, every
        // statblock name, map name, quest title and handout title for the seeded ids.
        // Read-only and transactional: several of these are lazy associations and production
        // runs with open-in-view=false.
    }
}
```

Inject `SceneRepository`, `HandoutRepository`, `StatBlockRepository` and `GameMapRepository` alongside the services so the two read helpers have what they need.

Write out each numbered step as real code. The service calls each step needs, verified against the current sources:

| Step | Call |
|---|---|
| 1 | `campaignRepository.save(new Campaign(...))` — as `PopulatedCampaignFixture` does |
| 2 | `adventureRepository.save(...)`, `chapterRepository.save(...)`, `sceneRepository.save(...)`; then `scene.setMapRequirement(SceneMapRequirement.REQUIRED)` and `scene.setMap(map)` |
| 2 | `structuredContentService.addSection(campaignId, sceneId, new SceneSectionCommand(...))` for read-aloud, and `addParticipant(campaignId, sceneId, new SceneParticipantCommand(...))` for each combatant, passing the seeded statblock id so the participant resolves |
| 2 | transitions via `structuredContentService.addTransition(campaignId, sceneId, new SceneTransitionCommand(...))` |
| 3 | `statBlockRepository.save(...)` with `ContentSource.CUSTOM` |
| 4 | `gameMapService.create(campaignId, "Beacon Undercroft", 20, 15, 64)` then `gameMapService.updateMode(mapId, movementMode, true)` |
| 5 | `handoutService.createImported(campaignId, title, fileName, contentType, bytes)` (use the overload `PopulatedCampaignFixture` already calls), then `handoutService.classify(campaignId, handoutId, Handout.SafetyClassification.PLAYER_SAFE \| DM_SOURCE)`; the derivative comes from `handoutService.createDerivative(campaignId, dmSourceId, title, ...)`, which sets `PLAYER_DERIVATIVE` and the provenance link itself |
| 6 | `questService` — mirror the quest+objectives block in `PopulatedCampaignFixture` |
| 7 | `partyMemberRepository.save(...)` ×4 |

Where a step needs image bytes, reuse the in-memory PNG helper pattern from `CoreSessionLoopSmokeTest` (`ImageIO.write` of a small `BufferedImage` into a `ByteArrayOutputStream`).

Read `PopulatedCampaignFixture.java` before writing this — it demonstrates every one of these calls with its exact argument list, and copying its shape keeps the two fixtures maintainable together.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest`
Expected: PASS

- [ ] **Step 5: Confirm the fixture shape matches the private package's shape profile**

Run: `./mvnw -q test -Dtest=FixtureShapeCoverageTest`
Expected: PASS. If the shape profile requires a field this fixture lacks (unlinked participants, threats), add it to the fixture — spec §8.3 requires the synthetic fixture to cover the private package's field-population shape.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java
git commit -m "test(gate): add a synthetic session-ready campaign for the release rehearsal"
```

---

## Task 13: The viewport and accessibility gate

§11.2 lists seven conditions at two viewports. Parts are covered incidentally inside `CoreSessionLoopSmokeTest`; this task makes the gate a named, complete, reviewable artefact.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java`

**Interfaces:**
- Consumes: `ReleaseRehearsalFixture` (Task 12), `BrowserFailureCollector`, `CockpitModuleRegistry` minimum dimensions, `#runtimeStatus` (Task 10), `data-display-title` (Task 3).
- Produces: nothing consumed by later tasks; Task 16 indexes it.

- [ ] **Step 1: Write the failing gate**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 11.2, in full, at both required viewports. This is a gate, not a
 * smoke test: every assertion here corresponds to one bullet in the spec.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ViewportAccessibilityGateTest {

    private static final int[][] VIEWPORTS = {{1366, 768}, {1920, 1080}};

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private SessionLifecycleService lifecycleService;
    @Autowired private CockpitModuleRegistry moduleRegistry;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
        lifecycleService.start(seeded.campaignId(), seeded.playableMapId());
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures.clear();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void openCockpit(int width, int height) {
        page.setViewportSize(width, height);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
    }

    @Test
    void theCockpitNeverScrollsTheDocument() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            Object overflow = page.evaluate("""
                    () => ({
                      v: document.documentElement.scrollHeight - document.documentElement.clientHeight,
                      h: document.documentElement.scrollWidth - document.documentElement.clientWidth
                    })
                    """);
            @SuppressWarnings("unchecked")
            Map<String, Object> o = (Map<String, Object>) overflow;

            assertThat(((Number) o.get("v")).intValue())
                    .as("vertical document overflow at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
            assertThat(((Number) o.get("h")).intValue())
                    .as("horizontal document overflow at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void modulesNeitherOverlapNorClip() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            @SuppressWarnings("unchecked")
            List<String> problems = (List<String>) page.evaluate("""
                    (bounds) => {
                      const modules = [...document.querySelectorAll('[data-module-key]')]
                        .filter(m => m.offsetParent !== null);
                      const rects = modules.map(m => ({ key: m.dataset.moduleKey, r: m.getBoundingClientRect() }));
                      const out = [];
                      for (const { key, r } of rects) {
                        if (r.right > bounds.w + 1 || r.bottom > bounds.h + 1 || r.left < -1 || r.top < -1) {
                          out.push(key + ' clips the viewport');
                        }
                      }
                      for (let i = 0; i < rects.length; i++) {
                        for (let j = i + 1; j < rects.length; j++) {
                          const a = rects[i].r, b = rects[j].r;
                          const overlap = a.left < b.right - 1 && b.left < a.right - 1
                                       && a.top < b.bottom - 1 && b.top < a.bottom - 1;
                          if (overlap) out.push(rects[i].key + ' overlaps ' + rects[j].key);
                        }
                      }
                      return out;
                    }
                    """, Map.of("w", viewport[0], "h", viewport[1]));

            assertThat(problems).as("layout at %dx%d", viewport[0], viewport[1]).isEmpty();
        }
    }

    @Test
    void everyVisibleModuleRespectsItsDeclaredMinimum() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            for (var definition : moduleRegistry.all()) {
                Locator module = page.locator("[data-module-key='" + definition.key() + "']");
                if (module.count() == 0 || !module.first().isVisible()) continue;

                BoundingBox box = module.first().boundingBox();
                assertThat(box.width)
                        .as("%s width at %dx%d", definition.key(), viewport[0], viewport[1])
                        .isGreaterThanOrEqualTo(definition.minWidthPx());
                assertThat(box.height)
                        .as("%s height at %dx%d", definition.key(), viewport[0], viewport[1])
                        .isGreaterThanOrEqualTo(definition.minHeightPx());
            }
        }
    }

    @Test
    void theCommandBarStaysFullyReachable() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            for (String selector : List.of("#cockpitLayoutModeButton", "#screenSafetyCheckbox",
                    "#runtimeStatus", "[data-display-title]", "button[x-ref='sessionButton']")) {
                Locator control = page.locator(selector).first();
                control.scrollIntoViewIfNeeded();
                BoundingBox box = control.boundingBox();
                assertThat(box).as("%s present at %dx%d", selector, viewport[0], viewport[1])
                        .isNotNull();
                assertThat(box.y + box.height)
                        .as("%s inside the viewport at %dx%d", selector, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(viewport[1]);
            }
        }
    }

    @Test
    void keyboardUsersCanDriveTheWorkspace() {
        openCockpit(1366, 768);

        // enter edit mode
        page.locator("#cockpitLayoutModeButton").focus();
        page.keyboard().press("Enter");
        page.waitForFunction(
                "() => document.querySelector('[data-cockpit-workbench]').dataset.layoutMode === 'edit'");

        // operate a splitter with the keyboard — the workbench expresses ratios as custom
        // properties, so that is what a real resize has to move.
        Locator splitter = page.locator("[role='separator']").first();
        splitter.focus();
        String sizeBefore = (String) page.evaluate("""
                () => getComputedStyle(document.querySelector('[data-cockpit-workbench]'))
                        .getPropertyValue('--primary-size')
                """);
        page.keyboard().press("ArrowRight");
        assertThat((String) page.evaluate("""
                () => getComputedStyle(document.querySelector('[data-cockpit-workbench]'))
                        .getPropertyValue('--primary-size')
                """))
                .as("arrow key resizes the adjacent zones")
                .isNotEqualTo(sizeBefore);
        assertThat(splitter.getAttribute("aria-valuenow"))
                .as("the splitter reports its current value")
                .isNotNull();
        page.keyboard().press("ArrowLeft");

        // leave edit mode
        page.locator("#cockpitLayoutModeButton").focus();
        page.keyboard().press("Enter");
        page.waitForFunction(
                "() => document.querySelector('[data-cockpit-workbench]').dataset.layoutMode === 'locked'");

        // choose a tab with the keyboard
        Locator tab = page.locator("[role='tab']").first();
        tab.focus();
        page.keyboard().press("ArrowRight");
        assertThat((boolean) page.evaluate(
                "() => document.activeElement?.getAttribute('role') === 'tab'"))
                .as("tab strip keeps roving focus")
                .isTrue();
    }

    /**
     * Spec section 10.2: "Empty space belongs to the primary task, not to empty modules."
     * The 2026-07-22 walkthrough measured the map holding 672x944 of an empty workspace;
     * this is the assertion that the correction did not swing the other way.
     */
    @Test
    void thePrimaryZoneOwnsTheSpace() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            Object share = page.evaluate("""
                    () => {
                      const zone = (name) => document.querySelector(`[data-cockpit-zone="${name}"]`);
                      const area = (el) => {
                        if (!el || el.offsetParent === null) return 0;
                        const r = el.getBoundingClientRect();
                        return r.width * r.height;
                      };
                      const primary = area(zone('PRIMARY'));
                      const total = ['PRIMARY', 'LEFT_SUPPORT', 'RIGHT_SUPPORT', 'BOTTOM_UTILITY']
                        .map(zone).map(area).reduce((a, b) => a + b, 0);
                      return total > 0 ? primary / total : 0;
                    }
                    """);

            assertThat(((Number) share).doubleValue())
                    .as("primary zone share at %dx%d (spec §7.2: 50-65%%)", viewport[0], viewport[1])
                    .isBetween(0.45, 0.75);
        }
    }

    /**
     * Spec section 10.3: "Modals, popovers, toasts and focused detail layers follow one
     * elevation and focus model." Task 7 proved the elevation half in the stylesheet; this
     * proves the focus half in the browser, for every layer type the cockpit can open.
     */
    @Test
    void everyFocusedLayerTrapsAndRestoresFocus() {
        openCockpit(1366, 768);

        record Layer(String opener, String container) {
        }

        for (Layer layer : List.of(
                new Layer("button[x-ref='sessionButton']", "#sessionLifecycleDialog"),
                new Layer("[data-module-key='story'] [data-module-focus]", ".cockpit-focus-layer"))) {

            Locator opener = page.locator(layer.opener()).first();
            opener.focus();
            page.keyboard().press("Enter");
            page.locator(layer.container()).waitFor();

            assertThat((boolean) page.evaluate(
                    "sel => document.activeElement?.closest(sel) !== null", layer.container()))
                    .as("%s takes initial focus", layer.container())
                    .isTrue();

            page.keyboard().press("Tab");
            assertThat((boolean) page.evaluate(
                    "sel => document.activeElement?.closest(sel) !== null", layer.container()))
                    .as("%s traps Tab", layer.container())
                    .isTrue();

            page.keyboard().press("Escape");
            assertThat((boolean) page.evaluate(
                    "sel => document.activeElement === document.querySelector(sel)", layer.opener()))
                    .as("%s restores focus to its opener", layer.container())
                    .isTrue();
        }
    }

    @Test
    void focusIsVisibleAndRestoredAfterAFocusedLayer() {
        openCockpit(1366, 768);

        page.locator("button[x-ref='sessionButton']").focus();
        assertThat((boolean) page.evaluate("""
                () => {
                  const s = getComputedStyle(document.activeElement);
                  return s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0;
                }
                """)).as("focus ring is painted").isTrue();

        page.keyboard().press("Enter");
        page.locator("#sessionLifecycleDialog").waitFor();
        page.keyboard().press("Escape");

        assertThat((boolean) page.evaluate(
                "() => document.activeElement === document.querySelector(\"button[x-ref='sessionButton']\")"))
                .as("focus returns to the control that opened the layer")
                .isTrue();
    }

    @Test
    void reducedMotionIsRespected() {
        try (BrowserContext reduced = browser.newContext(new Browser.NewContextOptions()
                .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE))) {
            Page quietPage = reduced.newPage();
            failures.attach(quietPage);
            quietPage.setViewportSize(1366, 768);
            quietPage.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
            quietPage.waitForLoadState(LoadState.NETWORKIDLE);

            @SuppressWarnings("unchecked")
            List<String> animated = (List<String>) quietPage.evaluate("""
                    () => [...document.querySelectorAll('body *')]
                      .filter(el => {
                        const s = getComputedStyle(el);
                        const d = (v) => Math.max(...v.split(',').map(x => parseFloat(x) * (x.includes('ms') ? 1 : 1000) || 0));
                        return d(s.animationDuration) > 10 || d(s.transitionDuration) > 10;
                      })
                      .map(el => el.tagName.toLowerCase() + '.' + (el.className || ''))
                      .slice(0, 20)
                    """);

            assertThat(animated).as("elements still animating under prefers-reduced-motion").isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run the gate**

Run: `./mvnw -q test -Dtest=ViewportAccessibilityGateTest`
Expected: FAIL on first run for at least one of: a module below its declared minimum at 1366×768, a splitter without keyboard resize, or an element still animating under reduced motion.

- [ ] **Step 3: Fix each failure at its source**

- **Module below its minimum** → the layout engine's clamp is wrong for that zone, or the module's declared minimum in `CockpitModuleRegistry` is unachievable at 1366×768. Adjust the clamp in `cockpit-layout.js`; only lower a declared minimum if the module genuinely reads at the smaller size.
- **Splitter not keyboard-operable** → add `ArrowLeft`/`ArrowRight`/`ArrowUp`/`ArrowDown` (small step) and `PageUp`/`PageDown` (large step) handling to the splitter in `cockpit-layout.js`, updating `aria-valuenow` on each change.
- **Still animating under reduced motion** → the component-level reduced-motion block in that stylesheet is missing an end-state override; add `transform: none; opacity: 1;` for that selector inside its `@media (prefers-reduced-motion: reduce)` block.
- **Command bar control outside the viewport** → the topbar is wrapping; move the control into the existing `details.cockpit-topbar__overflow` panel rather than shrinking the type.

- [ ] **Step 4: Run the gate to verify it passes**

Run: `./mvnw -q test -Dtest=ViewportAccessibilityGateTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportAccessibilityGateTest.java \
        src/main/resources/static/js src/main/resources/static/css
git commit -m "test(gate): assert the full section 11.2 viewport and accessibility gate"
```

---

## Task 14: The release rehearsal

§11.3's ten steps, executed from a fresh session against the synthetic fixture, with the seven gate-fail conditions asserted as they arise.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java`

**Interfaces:**
- Consumes: `ReleaseRehearsalFixture.Seeded` (Task 12), `.readiness-item--blocker` / `.readiness-badge` (Task 8), `.combatant-hp` / `hpLabel` (Task 4), `#runtimeStatus` (Task 10), `data-display-title` (Task 3).
- Produces: nothing consumed by later tasks; Task 15 parametrizes it and Task 16 indexes it.

- [ ] **Step 1: Write the failing rehearsal**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 11.3 — the representative release rehearsal, executed end to end
 * without leaving DMHelper. Each @Order corresponds to one numbered rehearsal step; the
 * seven gate-fail conditions are asserted at the point where each becomes observable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseRehearsalTest {

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

    private ReleaseRehearsalFixture.Seeded seeded;
    private String base;

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
        failures.clear();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
        page.setViewportSize(1366, 768);
        base = "http://localhost:" + port;
    }

    @AfterEach
    void closePage() {
        try {
            // Gate condition: "a request fails or partially commits behind an error response"
            // and "no console errors, unhandled rejections or silent non-2xx actions".
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void openCockpit() {
        page.navigate(base + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
    }

    /** Gate condition: the DM must not have to scroll to read the runtime state. */
    private void assertVisibleWithoutScrolling(String description, String selector) {
        Locator locator = page.locator(selector).first();
        locator.waitFor();
        BoundingBox box = locator.boundingBox();
        assertThat(box).as("%s is present", description).isNotNull();
        assertThat(box.y).as("%s starts inside the viewport", description).isGreaterThanOrEqualTo(0);
        assertThat(box.y + box.height).as("%s ends inside the viewport", description)
                .isLessThanOrEqualTo(768.0);
        assertThat((int) page.evaluate("() => window.scrollY"))
                .as("%s required no scrolling", description).isZero();
    }

    @Test
    @Order(1)
    void step1_readinessReportIsInspectedAndClear() {
        page.navigate(base + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator(".readiness-panel").count())
                .as("the readiness report is on the campaign home").isEqualTo(1);
        assertThat(page.locator(".readiness-panel.is-ready").count())
                .as("no blocker survives into the rehearsal").isEqualTo(1);
        assertThat(page.locator(".readiness-item--blocker").count()).isZero();
    }

    @Test
    @Order(2)
    void step2_sessionStartsAtTheSelectedScene() {
        page.navigate(base + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator("a[href$='/session'], button[data-run-session]").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");

        assertThat(page.url()).endsWith("/session");
        assertVisibleWithoutScrolling("current scene", "[data-runtime-module='story'] [data-current-scene]");
    }

    @Test
    @Order(3)
    void step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit() {
        openCockpit();
        String before = page.textContent("[data-runtime-module='story'] [data-current-scene]");

        page.locator("[data-runtime-module='story'] [data-scene-transition]").first().click();
        page.waitForFunction("""
                previous => document.querySelector("[data-runtime-module='story'] [data-current-scene]")
                    ?.textContent !== previous
                """, before);

        // Gate condition: no critical runtime action may leave the Run surface.
        assertThat(page.url()).endsWith("/session");
        assertThat(page.locator("main[data-surface='run']").count()).isEqualTo(1);
    }

    @Test
    @Order(4)
    void step4_theEncounterIsCreatedFromTheSceneInAtMostTwoActions() {
        openCockpit();

        // Gate condition: "a prepared element requires more than two deliberate actions".
        int actions = 0;
        page.locator("[data-runtime-module='story'] [data-seed-scene-encounter]").first().click();
        actions++;
        page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");

        assertThat(actions).as("deliberate actions to reach a runnable encounter")
                .isLessThanOrEqualTo(2);
        assertVisibleWithoutScrolling("encounter identity",
                "[data-runtime-module='encounter'] [data-encounter-name]");
    }

    @Test
    @Order(5)
    void step5_initiativeDamageConditionsDefeatAndTurnsResolve() {
        openCockpit();
        Locator setup = page.locator("[data-runtime-module='encounter'] [data-initiative-setup]");
        setup.waitFor();

        // Party initiatives entered without opening a character editor (spec §6.2).
        List<Locator> inputs = setup.locator("input[data-initiative-input]").all();
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).fill(String.valueOf(20 - i));
        }
        setup.locator("[data-roll-unset-initiative]").click();
        page.waitForFunction("""
                () => !document.querySelector('[data-initiative-setup] .initiative-setup__row--unset')
                """);

        setup.locator("button[data-start-combat]").click();
        page.waitForSelector("[data-running-turn-controls]");
        assertVisibleWithoutScrolling("current turn", "[data-running-turn-controls]");

        // Damage, condition, defeat, two turns.
        Locator firstRow = page.locator(".combatant-row").first();
        String hpBefore = firstRow.locator(".combatant-hp").textContent();
        firstRow.locator(".hp-delta-input").fill("-999");
        firstRow.locator(".hp-delta-input").press("Enter");
        page.waitForFunction("""
                previous => document.querySelector('.combatant-row .combatant-hp')?.textContent !== previous
                """, hpBefore);
        assertThat(page.locator(".combatant-row.defeated").count())
                .as("a creature reduced past zero reads as defeated").isGreaterThan(0);

        page.locator("[data-action='next-turn']").click();
        page.locator("[data-action='next-turn']").click();
    }

    @Test
    @Order(6)
    void step6_statblocksAndRulesAreConsultedInsideDmhelper() {
        openCockpit();

        page.locator(".combatant-row").first().click();
        assertThat(page.locator(".detail-focused-statblock, .statblock-render").first().isVisible())
                .as("the statblock is readable without leaving the cockpit").isTrue();

        page.locator("[data-runtime-module='reference'] input[type='search']").first().fill("grapple");
        page.waitForSelector("[data-runtime-module='reference'] [data-reference-result]");
        assertThat(page.locator("[data-runtime-module='reference'] [data-reference-result]").count())
                .as("rules search answers inside the cockpit").isGreaterThan(0);
        assertThat(page.url()).endsWith("/session");
    }

    @Test
    @Order(7)
    void step7_notesAreCapturedAndThePlanIsUpdated() {
        openCockpit();

        page.locator("[data-runtime-module='quick-notes'] textarea, "
                + "[data-runtime-module='quick-notes'] input[type='text']").first()
                .fill("The warden fled through the sluice gate.");
        page.locator("[data-runtime-module='quick-notes'] button[type='submit']").first().click();
        page.waitForSelector("[data-runtime-module='quick-notes'] .quicknote-row");

        // Gate condition: save state is identifiable without scrolling.
        assertVisibleWithoutScrolling("save status", "#runtimeStatus [data-status-save]");
        page.waitForFunction("""
                () => ['saved', 'idle'].includes(
                    document.querySelector('#runtimeStatus [data-status-save]').dataset.state)
                """);
    }

    @Test
    @Order(8)
    void step8_aReviewedPlayerSafeAssetIsPresentedAndTheDisplayAgrees() {
        openCockpit();

        Locator presentation = page.locator("[data-runtime-module='presentation']");
        presentation.locator("[data-present-handout='" + seeded.playerSafeHandoutId() + "']").click();
        page.waitForFunction("""
                () => document.querySelector('[data-presentation-mode]')?.dataset.presentationMode === 'HANDOUT'
                """);

        assertVisibleWithoutScrolling("presentation state", "[data-presentation-mode]");

        // Gate condition: player-visible output contains no unreviewed or DM-only information.
        Page playerPage = context.newPage();
        failures.attach(playerPage);
        playerPage.navigate(base + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(playerPage.locator("[data-screen-sensitive]").count())
                .as("the player endpoint carries no DM-sensitive markup").isZero();
        assertThat(playerPage.content())
                .doesNotContain(String.valueOf(seeded.dmSourceHandoutId()));
        playerPage.close();
    }

    @Test
    @Order(9)
    void step9_theEncounterAndSessionAreCompleted() {
        openCockpit();

        page.locator("[data-runtime-module='encounter'] [data-end-encounter]").click();
        page.waitForFunction("""
                () => !document.querySelector('[data-running-turn-controls]')
                    || document.querySelector('[data-running-turn-controls]').hidden
                """);

        page.locator("button[x-ref='sessionButton']").click();
        Locator lifecycle = page.locator("#sessionLifecycleDialog");
        lifecycle.waitFor();

        // Spec §6.5: a modal overlay, never in normal document flow.
        BoundingBox box = lifecycle.boundingBox();
        assertThat(box.y + box.height).isLessThanOrEqualTo(768.0);

        lifecycle.locator("button[data-complete-session]").click();
        page.waitForFunction("() => !document.querySelector('#sessionLifecycleDialog[open]')");
    }

    @Test
    @Order(10)
    void step10_theGeneratedLogAgreesWithWhatHappened() {
        page.navigate(base + "/campaigns/" + seeded.campaignId() + "/notes");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(".note-body, [data-session-log]").first().waitFor();
        String log = page.textContent("body");

        // Gate condition: "the session log disagrees with the actions performed".
        // Spec §6.5: the configured timezone is Europe/Berlin and must be labelled.
        assertThat(log).as("session time with a timezone label")
                .containsPattern("\\d{1,2}:\\d{2}")
                .containsAnyOf("CET", "CEST", "Europe/Berlin");
        assertThat(log).as("the scene the session ran")
                .contains(sceneTitleVisitedDuringRehearsal);
        assertThat(log).as("the defeated creature").contains(defeatedCombatantName);
        assertThat(log).as("the unresolved note").contains("sluice gate");
        assertThat(log).as("the encounter outcome").contains(encounterName);
    }
}
```

Three fields carry the observed values forward from the earlier steps, so step 10 compares the
log against what the rehearsal actually did rather than against a hard-coded string. Declare
them beside `seeded` and assign them where each becomes known:

```java
    private String sceneTitleVisitedDuringRehearsal;
    private String defeatedCombatantName;
    private String encounterName;
```

In `step3_...`, after the transition settles:

```java
        sceneTitleVisitedDuringRehearsal =
                page.textContent("[data-runtime-module='story'] [data-current-scene]").trim();
```

In `step4_...`, once the encounter module has rendered:

```java
        encounterName =
                page.textContent("[data-runtime-module='encounter'] [data-encounter-name]").trim();
```

In `step5_...`, before applying the killing delta:

```java
        defeatedCombatantName = firstRow.locator(".combatant-name").textContent().trim();
```

- [ ] **Step 2: Run the rehearsal to verify it fails**

Run: `./mvnw -q test -Dtest=ReleaseRehearsalTest`
Expected: FAIL — several selectors asserted here (`[data-current-scene]`, `[data-scene-transition]`, `[data-seed-scene-encounter]`, `[data-encounter-name]`, `[data-initiative-input]`, `[data-roll-unset-initiative]`, `[data-start-combat]`, `[data-end-encounter]`, `[data-present-handout]`, `[data-reference-result]`, `[data-complete-session]`, `[data-run-session]`) are the rehearsal's contract with the runtime modules and do not all exist yet.

- [ ] **Step 3: Add the missing test hooks to the runtime templates**

For each selector the run reports as missing, add the attribute to the element that already performs that job — do **not** add new controls. The mapping:

| Attribute | Element |
|---|---|
| `data-run-session` | the campaign home's Run entry point (workstream D) |
| `data-current-scene` | the story module's current-scene title |
| `data-scene-transition` | each transition link in the story module |
| `data-seed-scene-encounter` | the story module's "Start encounter from this scene" button (`session/_story-rail.html:105`) |
| `data-encounter-name` | the encounter module's heading |
| `data-initiative-input` | the per-combatant initiative input (`encounter/_tracker.html:60`) |
| `data-roll-unset-initiative` | the roll-unset action in `.initiative-setup__header` |
| `data-start-combat` | the "Start combat" button (`encounter/_tracker.html:85`) |
| `data-end-encounter` | the encounter module's end action |
| `data-present-handout` | each presentable handout control in the presentation module, valued with the handout id |
| `data-reference-result` | each result row in the reference module |
| `data-complete-session` | the lifecycle dialog's complete action |

- [ ] **Step 4: Fix the real defects the rehearsal surfaces**

Every remaining failure after Step 3 is a genuine gate failure, not a test problem. Triage each against §11.3's fail list and fix at the source:
- required information absent → the module is missing content the DM needs; add it to that module's view service;
- an action leaves the Run surface → the control links to an Edit page; give the module its own runtime action (spec §6.1 already requires this for scene seeding);
- more than two actions to reach a prepared element → collapse the intermediate step in that module;
- request fails or partially commits → a workstream A regression; fix transactionally, do not retry in the test;
- state not identifiable without scrolling → the topbar or module header is wrapping at 1366×768; move a lower-value control into the overflow menu;
- player-visible output carries DM content → a workstream A2 regression in the server projection;
- log disagrees with the table → a `SessionLogService` fidelity defect (spec §6.5).

- [ ] **Step 5: Run the rehearsal to verify it passes**

Run: `./mvnw -q test -Dtest=ReleaseRehearsalTest`
Expected: PASS, all ten steps.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java \
        src/main/resources/templates src/main/java
git commit -m "test(gate): run the section 11.3 release rehearsal end to end"
```

---

## Task 15: A second campaign shape

§11.3's closing paragraph: "At least one additional synthetic campaign with different branching, map and encounter shapes must pass before a general all-in-one readiness claim."

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java`

**Interfaces:**
- Consumes: `ReleaseRehearsalFixture.Shape` (Task 12).
- Produces: `Shape.BRANCHED_TWO_MAPS` — two chapters, a three-way branch, two playable maps of different grid scales, one theatre-of-mind hostile scene with no map and a wave-based encounter. `ReleaseRehearsalTest` becomes an abstract base with two concrete nested classes, one per shape.

- [ ] **Step 1: Write the failing test**

Add to `ReleaseRehearsalFixtureTest`:

```java
    @Test
    void theSecondShapeBranchesAndCarriesTwoMapScales() {
        var seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);

        assertThat(readinessReportService.report(seeded.campaignId()).sessionReady()).isTrue();

        var maps = mapRepository.findByCampaignIdOrderBySortOrderAsc(seeded.campaignId());
        assertThat(maps).as("two playable maps").hasSize(2);
        assertThat(maps.stream().map(m -> m.getCellSizePx()).distinct().count())
                .as("different grid scales, so calibration is genuinely exercised")
                .isEqualTo(2);
    }

    @Test
    void theSecondShapeHasATheatreOfMindEncounter() {
        var seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);

        assertThat(fixture.mapFreeHostileSceneCount(seeded))
                .as("a hostile scene that runs without a map")
                .isGreaterThan(0);
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest`
Expected: FAIL — `Shape.BRANCHED_TWO_MAPS` does not exist.

- [ ] **Step 3: Add the shape**

In `ReleaseRehearsalFixture`:

```java
    public enum Shape {
        /** One chapter, one map, one hostile scene. The baseline rehearsal. */
        LINEAR_ONE_MAP,
        /**
         * Two chapters, a three-way branch, two playable maps at different grid scales, and a
         * map-free hostile scene with a wave-based encounter — the shapes the linear fixture
         * cannot exercise (spec section 11.3, closing paragraph).
         */
        BRANCHED_TWO_MAPS
    }
```

and branch inside `seed(Shape)`:
- `LINEAR_ONE_MAP` — unchanged.
- `BRANCHED_TWO_MAPS` — two chapters; `approachScene` gains three transitions instead of one; a second `GameMap` with `cellSizePx 48` and a different grid; a fourth scene `ambushScene` with `mapRequirement = NONE`, four participants and an encounter seeded with two waves; the quest gains a third objective on the second branch.

Add the helper:

```java
    /** Hostile scenes that declare no map requirement — theatre of mind. */
    @Transactional(readOnly = true)
    public long mapFreeHostileSceneCount(Seeded seeded) {
        return sceneRepository.findByChapterAdventureCampaignId(seeded.campaignId()).stream()
                .filter(s -> !s.getParticipants().isEmpty())
                .filter(s -> s.getMapRequirement() == null
                        || s.getMapRequirement() == SceneMapRequirement.NONE)
                .count();
    }
```

It is an instance method rather than a static one because the participant collection is lazy
and this call has to happen inside a transaction (`spring.jpa.open-in-view=false`).

- [ ] **Step 4: Parametrize the rehearsal over both shapes**

Restructure `ReleaseRehearsalTest` so the ten steps run once per shape. Move every `@Test`, every field and the helpers into a `static abstract class RehearsalSteps` inside the outer class, declaring:

```java
    static abstract class RehearsalSteps {

        protected abstract ReleaseRehearsalFixture.Shape shape();

        @LocalServerPort protected int port;
        @Autowired protected ReleaseRehearsalFixture fixture;

        // ... the Playwright fields, @BeforeAll/@AfterAll/@BeforeEach/@AfterEach and the ten
        //     @Order steps move here verbatim, with `fixture.seed()` becoming
        //     `fixture.seed(shape())`.
    }
```

then declare one `@Nested` subclass per shape:

```java
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Linear campaign, one map")
    class Linear extends RehearsalSteps {
        @Override protected ReleaseRehearsalFixture.Shape shape() {
            return ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP;
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Branched campaign, two map scales, theatre of mind")
    class Branched extends RehearsalSteps {
        @Override protected ReleaseRehearsalFixture.Shape shape() {
            return ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS;
        }
    }
```

The outer class keeps `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `@ActiveProfiles("playwright")` and holds no tests of its own; `@Nested` classes inherit that configuration by default, and Spring injects `port` and `fixture` into each nested instance through the inherited fields. `@TestMethodOrder` and `@TestInstance(PER_CLASS)` go on the nested classes because that is where the tests now live.

In `step3_...`, follow **two** transitions when the shape is branched, asserting the story module reaches a different scene each time.

In `step5_...`, when the shape is branched, assert the wave control appears after the first wave is cleared:

```java
        if (shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
            assertThat(page.locator("[data-runtime-module='encounter'] [data-next-wave]").count())
                    .as("a wave-based encounter offers its next wave from the cockpit")
                    .isGreaterThan(0);
        }
```

- [ ] **Step 5: Run both shapes**

Run: `./mvnw -q test -Dtest=ReleaseRehearsalTest`
Expected: PASS — twenty steps, ten per shape.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support \
        src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java
git commit -m "test(gate): run the rehearsal against a second campaign shape"
```

---

## Task 16: The gate index

§11 lists fifteen requirements across three subsections. Right now each is proved somewhere, by tests scattered across five workstreams. Without an index, "the gate passes" is a claim nobody can check.

**Files:**
- Create: `docs/product/all-in-one-release-gate.md`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseGateIndexContractTest.java`
- Modify: `docs/product/README.md`, `docs/README.md`

**Interfaces:**
- Consumes: every test class named in the table.
- Produces: the release gate document. `ReleaseGateIndexContractTest` fails if a named test class stops existing, so the index cannot rot into a marketing page.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseGateIndexContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 11: "No plan may claim the overall premise until section 11 passes."
 * The index is how that claim is checked, so every test class it names must exist.
 */
class ReleaseGateIndexContractTest {

    private static final Path INDEX = Path.of("docs/product/all-in-one-release-gate.md");

    @Test
    void everyRequirementInSection11HasARow() throws IOException {
        String index = Files.readString(INDEX);

        for (String requirement : List.of(
                "11.1.1", "11.1.2", "11.1.3", "11.1.4", "11.1.5", "11.1.6", "11.1.7", "11.1.8",
                "11.2", "11.3")) {
            assertThat(index).as("%s indexed", requirement).contains(requirement);
        }
    }

    @Test
    void everyTestClassTheIndexNamesExists() throws IOException {
        String index = Files.readString(INDEX);
        Matcher m = Pattern.compile("`([A-Z][A-Za-z0-9]+Test)`").matcher(index);

        List<String> named = new ArrayList<>();
        while (m.find()) named.add(m.group(1));
        assertThat(named).as("the index names test classes").isNotEmpty();

        List<String> missing = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/test/java"))) {
            List<String> present = sources
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString().replace(".java", ""))
                    .toList();
            for (String name : named) {
                if (!present.contains(name)) missing.add(name);
            }
        }

        assertThat(missing).as("indexed tests that no longer exist").isEmpty();
    }

    @Test
    void theIndexIsLinkedFromTheProductDocs() throws IOException {
        assertThat(Files.readString(Path.of("docs/product/README.md")))
                .contains("all-in-one-release-gate.md");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -Dtest=ReleaseGateIndexContractTest`
Expected: FAIL — the index does not exist.

- [ ] **Step 3: Write the index**

Create `docs/product/all-in-one-release-gate.md`:

```markdown
# All-in-One Release Gate

The executable gate defined in
`docs/superpowers/specs/2026-07-22-phandelver-all-in-one-corrective-design.md` §11.
Every row names the test that proves it. `ReleaseGateIndexContractTest` fails if a named
class disappears, so this table cannot drift away from the suite.

Run the whole gate:

    ./mvnw -q test -Dtest='ReleaseRehearsalTest,ViewportAccessibilityGateTest,TypographyRenderGateTest,SurfaceNestingGateTest,CoreSessionLoopSmokeTest'

## §11.1 Automated coverage

| Req | Requirement | Proved by |
|---|---|---|
| 11.1.1 | Production-parity integration; `open-in-view=false`; fails on lazy access after a service boundary | `SceneEncounterSeedProductionParityTest`, `FullPageRenderSmokeTest` |
| 11.1.2 | Every cockpit module renders empty, populated, loading, error, compact, focused, Private and Table-safe | `CockpitRuntimeModuleContractTest`, `RuntimeModuleSafetyContractTest` |
| 11.1.3 | Layout schema migration, constraints, docking, serialization, invalid recovery, preset reset | `CockpitLayoutPresetServiceTest`, `CockpitWorkbenchTemplateContractTest` |
| 11.1.4 | Browser interaction: edit lock, dividers, docking, tabs, focus, keyboard, persistence, retry, resize | `CoreSessionLoopSmokeTest`, `ViewportAccessibilityGateTest` |
| 11.1.5 | Every built-in preset in Table-safe: sensitive content neither visible nor focusable; player endpoint carries only its projection | `SessionCockpitSecurityTest`, `PlayerViewSecurityContractTest`, `ReleaseRehearsalTest` |
| 11.1.6 | Unsafe assets blocked; a reviewed derivative matches the player preview | `HandoutDerivativeTemplateContractTest`, `ReleaseRehearsalTest` |
| 11.1.7 | Tracker-driven defeat/revive and a cross-midnight session produce a faithful log | `SessionEncounterEvidenceIntegrationTest`, `ReleaseRehearsalTest` |
| 11.1.8 | No console errors, unhandled rejections, malformed requests or silent non-2xx actions | `BrowserFailureCollector` attached in `ReleaseRehearsalTest`, `ViewportAccessibilityGateTest`, `CoreSessionLoopSmokeTest` |

## §11.2 Viewport and accessibility gate — 1366×768 and 1920×1080

| Requirement | Proved by |
|---|---|
| No document-level scrolling | `ViewportAccessibilityGateTest#theCockpitNeverScrollsTheDocument` |
| Modules do not overlap or clip | `ViewportAccessibilityGateTest#modulesNeitherOverlapNorClip` |
| Command bar fully reachable | `ViewportAccessibilityGateTest#theCommandBarStaysFullyReachable` |
| Minimum module sizes respected | `ViewportAccessibilityGateTest#everyVisibleModuleRespectsItsDeclaredMinimum` |
| Keyboard: edit mode, tabs, focus/restore, splitters | `ViewportAccessibilityGateTest#keyboardUsersCanDriveTheWorkspace` |
| Focus visible and restored after dialogs | `ViewportAccessibilityGateTest#focusIsVisibleAndRestoredAfterAFocusedLayer` |
| Reduced motion respected | `ViewportAccessibilityGateTest#reducedMotionIsRespected`, `MotionBudgetContractTest` |

## §11.3 Representative release rehearsal

All ten steps, run against two synthetic campaign shapes, in `ReleaseRehearsalTest`
(`Linear` and `Branched` nested classes). The seven gate-fail conditions are asserted at the
point each becomes observable; see the method comments.

The fixtures are `ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP` and `BRANCHED_TWO_MAPS`.
Both are entirely synthetic — `ReleaseRehearsalFixtureTest` fails the build if any published
campaign's vocabulary appears in one.

## §10 Visual system

The visual rules are enforced continuously rather than at gate time:
`DesignTokenContractTest`, `TypeScaleContractTest`, `TypographyRoleContractTest`,
`TypographyRenderGateTest`, `CombatLegibilityContractTest`, `GoldAccentContractTest`,
`SurfaceNestingGateTest`, `ElevationModelContractTest`, `ControlConsistencyContractTest`,
`DestructiveActionContractTest`, `MotionBudgetContractTest`, `RuntimeStatusSurfaceTest`.

## Scope of the claim

Passing this gate supports the all-in-one premise **for the two rehearsed campaign shapes**.
It is not a claim about every published campaign (spec §11.3, closing paragraph).
```

- [ ] **Step 4: Correct any class name the test rejects**

Run: `./mvnw -q test -Dtest=ReleaseGateIndexContractTest`

For each name in the "indexed tests that no longer exist" list, find the class that actually proves that requirement:

Run: `grep -rl "<requirement keyword>" src/test/java | head`

and replace the name in the table. Never delete a row to make the test pass — if nothing proves a requirement, that is a gate failure to fix, not an index to trim.

- [ ] **Step 5: Link it from the docs index**

In `docs/product/README.md`, add to the file list:

```markdown
- [All-in-One Release Gate](all-in-one-release-gate.md) — §11 requirements mapped to the tests that prove them.
```

In `docs/README.md`, under **Key Reference Files**:

```markdown
- [All-in-One Release Gate](product/all-in-one-release-gate.md) — Executable release gate for the all-in-one premise.
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=ReleaseGateIndexContractTest`
Expected: PASS

- [ ] **Step 7: Run the docs contracts**

Run: `./mvnw -q test -Dtest=DocsIndexContractTest,DmManualContractTest`
Expected: PASS

- [ ] **Step 8: Run the full build**

Run: `./mvnw -q verify`
Expected: PASS. This is the point at which workstream E is complete and the premise may be claimed for the two rehearsed shapes — and no earlier.

- [ ] **Step 9: Commit**

```bash
git add docs/product/all-in-one-release-gate.md docs/product/README.md docs/README.md \
        src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseGateIndexContractTest.java
git commit -m "docs(gate): index every section 11 requirement against the test that proves it"
```

---

## Manual acceptance checkpoint

Automated gates cannot judge whether hierarchy now beats decoration. After Task 16, run the app and look:

```bash
./mvnw spring-boot:run
```

At 1366×768, on the seeded rehearsal campaign:

1. **Campaign home** — the readiness report reads as part of the application, one Cinzel title, one obvious primary action.
2. **Scene detail** — narrative dominates; the eye lands on prose, not on chrome.
3. **Cockpit, Exploration preset** — scene, turn, encounter, presentation state and save state are all findable without scrolling; nothing gold except the focused control and the primary action.
4. **Cockpit, Combat preset** — stand two metres back from the screen. Initiative and HP must still be readable. This is the one check no test performs.
5. **Table-safe** — toggle Screen safety and confirm the shift is recognizable from across the room.
6. **Session review** — the lifecycle modal is centered, bounded and focus-trapped.

Record anything that fails as a follow-up finding against §10; do not fix it by relaxing a contract test.

## Rollback

Every task is a single commit with no schema change and no data transformation. `git revert <sha>` restores the previous appearance exactly. Reverting a Layer 2 task also reverts its contract test, so the build stays green at any point in the history.
