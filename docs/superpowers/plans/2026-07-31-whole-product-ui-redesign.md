# Whole-Product UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved whole-product desktop redesign — charcoal foundation, one application shell, five page archetypes, redesigned map editor and session cockpit — across every shipped DMHelper page without changing routes, persisted semantics, or campaign-package formats.

**Architecture:** Work flows from shared visual contracts outward. `tokens.css` becomes the only raw-value authority and publishes semantic roles; a set of shared Thymeleaf fragments becomes the only implementation of the app shell, page header, toolbar, feedback, and overlay contracts; then feature families migrate onto those contracts one complete family at a time. The map editor and cockpit are restructured last among feature work because they consume every earlier contract, and a final stage removes the temporary compatibility layer and runs the whole-product gate. Every stage is guarded by static JUnit contracts over CSS and template source plus Playwright gates over computed geometry, so a half-migrated page fails the build rather than shipping.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, repository-owned CSS and inline SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Global Constraints

Every task's requirements implicitly include this section.

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`. This plan supersedes `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md` and the deleted `2026-07-31-whole-product-ui-redesign-program.md`; both are absorbed here.
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
- Keep every existing controller, template, htmx, package, player-safety, encounter, map, cockpit, and accessibility test green. `./mvnw test` must pass at the end of every stage.

## Scope reconciliation: spec section 11.11

Spec section 11.11 describes a player-facing presentation surface. The shipped product no
longer has one: commits `e6e0a3b8` (remove table-safe screen mode) and `440a6484` (remove
player-surface leftovers) cut it, and the only presentation surface that exists today is
the DM-side full-viewport handout overlay at
`GET /campaigns/{campaignId}/handouts/{id}/present`
(`templates/handout/_present-overlay.html`), plus the `playerSafe` flag on `Handout` and
`Note`.

**Assumption this plan proceeds under:** section 11.11 applies to what exists — the
full-viewport presentation overlay and the player-safe/DM-only Shield semantics that feed
it. Task 47 redesigns that overlay to the spec's "neutral near-black canvas, no DM chrome,
explicit unavailable/error state" requirements and keeps Shield semantics stable. This plan
does **not** re-introduce a separate player projection surface; doing that is a functional
feature, not a redesign, and needs its own spec. If a player projection surface is wanted,
raise it before Stage 8 so the release gate can cover it.

## Stage map

| Stage | Tasks | Owns | Depends on |
|---|---|---|---|
| 1. Visual foundations | 1–9 | Semantic tokens, contrast, type roles, icon sprite, focus, motion, elevation, legacy budgets | Approved spec |
| 2. Shell and archetypes | 10–19 | Top bar, rail, app frame, page header, five archetypes, shared controls, feedback, overlays | Stage 1 |
| 3. Campaign and narrative | 20–26 | Campaign selection/home, adventures, scenes, quests, world, notes, calendar | Stage 2 |
| 4. Operational preparation | 27–33 | Encounters, party, sheets, treasury, ledger, handouts, audio | Stage 2 |
| 5. Reference workspace | 34–37 | Library categories, tables, traps, hazards | Stage 2 |
| 6. Map editor | 38–41 | Command bar, tool rail, contextual inspector, canvas and save feedback | Stages 2, 4 |
| 7. Session cockpit | 42–46 | Command bar, module hierarchy, four built-in presets, laptop fit, runtime state | Stages 4, 6 |
| 8. Consistency and release gate | 47–53 | System/admin/presentation pages, overlay and form sweep, legacy removal, full matrix | All |

Stages 3, 4, and 5 depend only on Stage 2 and may be executed in any order or in parallel
worktrees. Everything else is strictly sequential.

## Shared stage protocol

Applies to every task in this plan.

- Red first: write or extend the failing test, run it, confirm the failure message names the
  missing thing, then implement.
- One task, one commit. Use `feat:`, `refactor:`, `test:`, or `fix:` prefixes.
- Run the task's focused test command after each red/green cycle; run `./mvnw test` at the
  end of every stage before the stage's review step.
- Never add a new legacy alias. Never raise a migration budget. Budgets only ratchet down.
- When a template moves onto a shared fragment, delete the markup it replaced in the same
  commit. Leaving both is what produces a hybrid page.
- Screenshots go to `target/ui-redesign/<stage-slug>/`; they are human-review evidence, not
  assertions. Automated tests assert behavior, computed styles, and geometry.

## File structure

### Created by this plan

| Path | Responsibility | Stage |
|---|---|---|
| `src/main/resources/static/icons/ui.svg` | Repository-owned outline icon sprite | 1 |
| `src/main/resources/templates/common/_icon.html` | Only interface for chrome icons | 1 |
| `src/main/resources/templates/fragments/_shell.html` | Whole-document layout for every standard page | 2 |
| `src/main/resources/templates/fragments/_topbar.html` | Global top bar, search, dice, session state, overflow | 2 |
| `src/main/resources/templates/fragments/_rail.html` | Campaign and global navigation rail | 2 |
| `src/main/resources/templates/fragments/_page-header.html` | Breadcrumb, title, summary, action groups | 2 |
| `src/main/resources/templates/fragments/_toolbar.html` | Search/filter toolbar and table toolbar | 2 |
| `src/main/resources/templates/fragments/_states.html` | Empty, loading, unavailable, error, skeleton | 2 |
| `src/main/resources/templates/fragments/_banner.html` | Semantic inline banner | 2 |
| `src/main/resources/templates/fragments/_badge.html` | Status, provenance, scope badges | 2 |
| `src/main/resources/templates/fragments/_overlay.html` | Dialog, side sheet, popover, toast region | 2 |
| `src/main/resources/templates/fragments/_context-rail.html` | Detail archetype contextual rail | 2 |
| `src/main/resources/templates/fragments/_status.html` | Save, loading, connection indicators | 2 |
| `src/main/resources/static/js/ui-overlay.js` | Focus trap, restore, Escape, toast lifecycle | 2 |
| `src/main/resources/static/css/map-editor.css` | Isolated map-editor workspace styles | 6 |
| `src/main/resources/static/js/map-inspector.js` | Inspector relevance and layer controls | 6 |

New test sources are listed per task.

### Deleted by this plan

| Path | Replaced by | Stage |
|---|---|---|
| `src/main/resources/templates/fragments/_appnav.html` | `fragments/_rail.html` | 2 |
| `src/main/resources/templates/fragments/navbar.html` | `fragments/_topbar.html` | 2 |
| `src/main/resources/templates/common/_empty-state.html` | `fragments/_states.html` | 8 |
| `src/main/resources/templates/common/_skeleton.html` | `fragments/_states.html` | 8 |
| `src/main/resources/templates/common/_error.html` | `fragments/_states.html` | 8 |

## Public interfaces

### Semantic CSS roles (Stage 1)

```css
--surface-canvas      --surface-navigation  --surface-workspace   --surface-panel
--surface-raised      --surface-inset       --surface-overlay
--border-subtle       --border-strong
--text-primary        --text-secondary      --text-tertiary
--action-primary      --action-primary-hover --action-on-primary
--selection-accent    --selection-surface   --focus-ring
--state-success       --state-success-surface       --state-success-border
--state-warning       --state-warning-surface       --state-warning-border
--state-danger        --state-danger-surface        --state-danger-border
--state-info          --state-info-surface          --state-info-border
--state-shield        --state-shield-surface        --state-shield-border
--state-concentration --state-concentration-surface --state-concentration-border
--scrim-standard      --scrim-strong        --neutral-hover-surface
--shadow-floating     --shadow-overlay      --shadow-hairline     --glow-primary
```

### Layout roles (Stage 2)

```css
--topbar-height        --rail-width-expanded  --rail-width-collapsed
--page-gap             --page-pad             --context-rail-width
--measure-prose        --page-max-index       --page-max-detail       --page-max-form
```

### Thymeleaf fragment contracts

```html
<!-- Stage 1 -->
~{common/_icon :: icon(name='search')}
~{common/_icon :: icon-sized(name='search', size='20')}

<!-- Stage 2 -->
~{fragments/head :: document-head(pageTitle='Encounters')}
~{fragments/_shell :: page(pageTitle=…, archetype=…, header=~{::…}, content=~{::…})}
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

### Java test helpers

```java
// package dev.hendrikhoemberg.dmhelper.config (package-private)
static List<String>  CssRules.ALL_FILES
static List<String>  CssRules.discoverCssFiles()
static String        CssRules.allApplicationCss()
static String        CssRules.allTemplateMarkup()
static List<String>  CssRules.rawColorLiterals(String source)
static long          CssRules.tokenReferenceCount(String token)
static double        ColorContrast.ratio(String foregroundHex, String backgroundHex)
static List<Path>    TemplateRules.allTemplates()
static List<Path>    TemplateRules.pageTemplates()
static String        TemplateRules.read(Path template)
static Document      TemplateRules.parse(Path template)
```

### JavaScript globals

```js
window.dmOverlay.open(element)      // Stage 2: shows, traps focus, remembers trigger
window.dmOverlay.close(element)     // Stage 2: hides, restores focus to trigger
window.dmToast.show(message, tone)  // Stage 2: transient confirmation only
window.mapInspector.setContext(key) // Stage 6: 'tool' | 'selection' | 'layers' | 'map'
```

---

# Stage 1: Visual foundations

**Working product after this stage:** every existing page renders on the charcoal surface
ladder with ivory text and scarce gold. Structure is unchanged; later stages can rely on
stable semantic tokens, a repository-owned icon set, and measured contrast, focus, motion,
and elevation contracts.

## Task 1: Make the stylesheet inventory authoritative

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssInventoryContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`

**Interfaces:**
- Produces: `CssRules.discoverCssFiles()`, `CssRules.allApplicationCss()`,
  `CssRules.allTemplateMarkup()`.

`CssRules.ALL_FILES` is currently a hand-maintained list of eight names and already omits
`encounter.css`, so every contract test in the repo silently skips it.

- [ ] **Step 1: Write the failing inventory test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class CssInventoryContractTest {

    @Test
    void everyShippedStylesheetIsInspectedByTheContractTests() throws IOException {
        List<String> onDisk;
        try (Stream<java.nio.file.Path> files = Files.list(CssRules.CSS_DIR)) {
            onDisk = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".css"))
                    .sorted()
                    .toList();
        }
        assertThat(CssRules.ALL_FILES).containsExactlyInAnyOrderElementsOf(onDisk);
    }

    @Test
    void discoveryIsAutomaticRatherThanHandMaintained() {
        assertThat(CssRules.discoverCssFiles()).isEqualTo(CssRules.ALL_FILES);
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='CssInventoryContractTest' test
```

Expected: FAIL — `encounter.css` is on disk but missing from `ALL_FILES`, and
`discoverCssFiles` does not exist.

- [ ] **Step 3: Replace the hand-maintained list in `CssRules`**

```java
    static final List<String> ALL_FILES = discoverCssFiles();

    static List<String> discoverCssFiles() {
        try (Stream<Path> files = Files.list(CSS_DIR)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".css"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every application stylesheet except the token layer, concatenated. */
    static String allApplicationCss() {
        return ALL_FILES.stream()
                .filter(file -> !file.equals("tokens.css"))
                .map(CssRules::read)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Every Thymeleaf template, concatenated, for inline-style and markup rules. */
    static String allTemplateMarkup() {
        Path root = Path.of("src/main/resources/templates");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".html"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
```

Add `import java.util.stream.Stream;`. Leave `RUNTIME_FILES` as-is.

- [ ] **Step 4: Run the whole design-contract package**

```bash
./mvnw -Dtest='CssInventoryContractTest,DesignTokenContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest,TypeScaleContractTest,TypographyRoleContractTest,UiPolishContractTest,ControlConsistencyContractTest,CombatLegibilityContractTest,DestructiveActionContractTest,InteractionFailureContractTest' test
```

Expected: `CssInventoryContractTest` PASSES. Other tests may now fail because
`encounter.css` is inspected for the first time. Fix `encounter.css` to satisfy them —
do not narrow the inventory.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/config/ src/main/resources/static/css/encounter.css
git commit -m "test: discover stylesheets automatically so no sheet escapes the contracts"
```

## Task 2: Add a measured WCAG contrast utility

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrast.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrastTest.java`

**Interfaces:**
- Produces: `ColorContrast.ratio(String foregroundHex, String backgroundHex)`.

Contrast must be measured, not asserted by eye, because the palette section of the spec
explicitly permits value adjustment only when measured contrast requires it.

- [ ] **Step 1: Write the failing utility test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorContrastTest {

    @Test
    void blackOnWhiteIsTwentyOneToOne() {
        assertThat(ColorContrast.ratio("#000000", "#ffffff")).isCloseTo(21.0, within(0.01));
    }

    @Test
    void theRatioIsSymmetric() {
        assertThat(ColorContrast.ratio("#eee8dc", "#101113"))
                .isCloseTo(ColorContrast.ratio("#101113", "#eee8dc"), within(0.0001));
    }

    @Test
    void shortHexExpands() {
        assertThat(ColorContrast.ratio("#fff", "#000"))
                .isCloseTo(ColorContrast.ratio("#ffffff", "#000000"), within(0.0001));
    }

    @Test
    void nonHexInputIsRejectedLoudly() {
        assertThatThrownBy(() -> ColorContrast.ratio("var(--text-primary)", "#101113"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex");
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='ColorContrastTest' test
```

Expected: FAIL — `ColorContrast` does not exist.

- [ ] **Step 3: Implement the utility**

```java
package dev.hendrikhoemberg.dmhelper.config;

/** WCAG 2.1 relative-luminance contrast, for design-contract assertions only. */
final class ColorContrast {

    private ColorContrast() {
    }

    static double ratio(String foregroundHex, String backgroundHex) {
        double a = luminance(foregroundHex);
        double b = luminance(backgroundHex);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(String hex) {
        String value = hex.trim();
        if (!value.startsWith("#")) {
            throw new IllegalArgumentException("Expected a hex color, got: " + hex);
        }
        value = value.substring(1);
        if (value.length() == 3) {
            StringBuilder expanded = new StringBuilder();
            for (char c : value.toCharArray()) expanded.append(c).append(c);
            value = expanded.toString();
        }
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Expected a 3- or 6-digit hex color, got: " + hex);
        }
        double r = channel(Integer.parseInt(value.substring(0, 2), 16));
        double g = channel(Integer.parseInt(value.substring(2, 4), 16));
        double b = channel(Integer.parseInt(value.substring(4, 6), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int raw) {
        double s = raw / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

```bash
./mvnw -Dtest='ColorContrastTest' test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrast.java src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrastTest.java
git commit -m "test: measure WCAG contrast instead of asserting it by eye"
```

## Task 3: Publish the semantic charcoal palette

**Files:**
- Modify: `src/main/resources/static/css/tokens.css`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java`

**Interfaces:**
- Consumes: `ColorContrast.ratio(String, String)`.
- Produces: every semantic role in the Public Interfaces section.
- Compatibility: every `--color-*` token currently defined stays defined, resolving through
  a semantic role.

- [ ] **Step 1: Replace the palette assertions in `DesignTokenContractTest`**

Delete `semanticColorsKeepTheirValues()` (it asserts superseded brown values) and
`warningWaveTitleContrastsWithItsWarningSurface()` (it couples a global token test to
inline encounter markup). Add:

```java
    private static String tokenValue(String name) {
        var matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(name) + "\\s*:\\s*([^;]+);")
                .matcher(CssRules.read("tokens.css"));
        assertThat(matcher.find()).as("%s is defined", name).isTrue();
        return matcher.group(1).trim();
    }

    @Test
    void semanticRolesKeepTheirApprovedValues() {
        assertThat(tokenValue("--surface-canvas")).isEqualTo("#101113");
        assertThat(tokenValue("--surface-navigation")).isEqualTo("#151619");
        assertThat(tokenValue("--surface-workspace")).isEqualTo("#18191d");
        assertThat(tokenValue("--surface-panel")).isEqualTo("#1d1f24");
        assertThat(tokenValue("--surface-raised")).isEqualTo("#24262c");
        assertThat(tokenValue("--surface-inset")).isEqualTo("#111216");
        assertThat(tokenValue("--border-subtle")).isEqualTo("#30333a");
        assertThat(tokenValue("--border-strong")).isEqualTo("#686c75");
        assertThat(tokenValue("--text-primary")).isEqualTo("#eee8dc");
        assertThat(tokenValue("--text-secondary")).isEqualTo("#b8b4aa");
        assertThat(tokenValue("--text-tertiary")).isEqualTo("#929089");
        assertThat(tokenValue("--action-primary")).isEqualTo("#c9a35c");
        assertThat(tokenValue("--action-primary-hover")).isEqualTo("#ddb977");
        assertThat(tokenValue("--state-success")).isEqualTo("#89ad69");
        assertThat(tokenValue("--state-warning")).isEqualTo("#e0a34d");
        assertThat(tokenValue("--state-danger")).isEqualTo("#d36a61");
        assertThat(tokenValue("--state-info")).isEqualTo("#74a3c1");
        assertThat(tokenValue("--state-shield")).isEqualTo("#8fa8b8");
        assertThat(tokenValue("--state-concentration")).isEqualTo("#b58ac1");
    }

    @Test
    void approvedTextAndStatePairsMeetWcagAa() {
        assertThat(ColorContrast.ratio("#eee8dc", "#101113")).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorContrast.ratio("#b8b4aa", "#18191d")).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorContrast.ratio("#929089", "#1d1f24")).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorContrast.ratio("#101113", "#c9a35c")).isGreaterThanOrEqualTo(4.5);
        for (String state : java.util.List.of(
                "#89ad69", "#e0a34d", "#d36a61", "#74a3c1", "#8fa8b8", "#b58ac1")) {
            assertThat(ColorContrast.ratio(state, "#1d1f24")).isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void strongInteractiveBoundaryMeetsThreeToOne() {
        assertThat(ColorContrast.ratio("#686c75", "#111216")).isGreaterThanOrEqualTo(3.0);
    }
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='DesignTokenContractTest' test
```

Expected: FAIL — `--surface-canvas is defined` is false.

- [ ] **Step 3: Replace the palette block at the top of `:root` in `tokens.css`**

```css
  /* Semantic UI palette: components consume roles, never raw colors. */
  --surface-canvas: #101113;
  --surface-navigation: #151619;
  --surface-workspace: #18191d;
  --surface-panel: #1d1f24;
  --surface-raised: #24262c;
  --surface-inset: #111216;
  --surface-overlay: #292c33;

  --border-subtle: #30333a;
  --border-strong: #686c75;

  --text-primary: #eee8dc;
  --text-secondary: #b8b4aa;
  --text-tertiary: #929089;

  --action-primary: #c9a35c;
  --action-primary-hover: #ddb977;
  --action-on-primary: #101113;
  --selection-accent: var(--action-primary);
  --selection-surface: color-mix(in srgb, var(--selection-accent) 18%, transparent);
  --focus-ring: var(--action-primary);

  --state-success: #89ad69;
  --state-success-surface: color-mix(in srgb, var(--state-success) 14%, var(--surface-panel));
  --state-success-border: color-mix(in srgb, var(--state-success) 62%, var(--surface-panel));
  --state-warning: #e0a34d;
  --state-warning-surface: color-mix(in srgb, var(--state-warning) 14%, var(--surface-panel));
  --state-warning-border: color-mix(in srgb, var(--state-warning) 62%, var(--surface-panel));
  --state-danger: #d36a61;
  --state-danger-surface: color-mix(in srgb, var(--state-danger) 14%, var(--surface-panel));
  --state-danger-border: color-mix(in srgb, var(--state-danger) 62%, var(--surface-panel));
  --state-info: #74a3c1;
  --state-info-surface: color-mix(in srgb, var(--state-info) 14%, var(--surface-panel));
  --state-info-border: color-mix(in srgb, var(--state-info) 62%, var(--surface-panel));
  --state-shield: #8fa8b8;
  --state-shield-surface: color-mix(in srgb, var(--state-shield) 14%, var(--surface-panel));
  --state-shield-border: color-mix(in srgb, var(--state-shield) 62%, var(--surface-panel));
  --state-concentration: #b58ac1;
  --state-concentration-surface: color-mix(in srgb, var(--state-concentration) 14%, var(--surface-panel));
  --state-concentration-border: color-mix(in srgb, var(--state-concentration) 62%, var(--surface-panel));

  --scrim-standard: rgba(8, 9, 11, 0.72);
  --scrim-strong: rgba(8, 9, 11, 0.88);
  --neutral-hover-surface: rgba(238, 232, 220, 0.05);
  --shadow-floating: 0 8px 28px rgba(0, 0, 0, 0.48);
  --shadow-overlay: 0 16px 48px rgba(0, 0, 0, 0.62);
  --shadow-hairline: 0 2px 0 rgba(238, 232, 220, 0.04);
  --glow-primary: 0 0 16px rgba(201, 163, 92, 0.25);

  /* Temporary migration aliases. LegacyVisualAliasContractTest prevents growth;
     Task 49 removes them. */
  --color-bg: var(--surface-canvas);
  --color-surface: var(--surface-panel);
  --color-surface-hover: var(--surface-raised);
  --color-text: var(--text-primary);
  --color-text-muted: var(--text-secondary);
  --color-accent: var(--action-primary);
  --color-accent-hover: var(--action-primary-hover);
  --color-danger: var(--state-danger);
  --color-danger-hover: color-mix(in srgb, var(--state-danger) 82%, var(--text-primary));
  --color-success: var(--state-success);
  --color-warning: var(--state-warning);
  --color-warning-bg: var(--state-warning-surface);
  --color-warning-text: var(--state-warning);
  --color-border: var(--border-subtle);
  --color-border-strong: var(--border-strong);
  --color-concentration: var(--state-concentration);
  --color-info: var(--state-info);
  --color-ember: var(--state-danger);
  --color-gold-soft: var(--selection-surface);
  --color-overlay: var(--scrim-strong);
  --color-shield: var(--state-shield);
  --color-shield-hover: color-mix(in srgb, var(--state-shield) 82%, var(--text-primary));
  --color-shield-soft: var(--state-shield-surface);
  --color-attack-bonus: var(--action-primary);
  --color-text-secondary: var(--text-secondary);
  --color-border-subtle: var(--border-subtle);
  --color-bg-elevated: var(--surface-raised);
  --color-surface-muted: var(--surface-panel);
  /* End temporary migration aliases. */
```

Keep the existing spacing, type-scale, radius, duration, z-index, texture, and cockpit-width
tokens. Repoint the elevation aliases:

```css
  --elevation-base-bg: var(--surface-canvas);
  --elevation-raised-bg: var(--surface-panel);
  --elevation-floating-bg: var(--surface-raised);
  --elevation-sunken-bg: var(--surface-inset);
  --shadow: var(--shadow-floating);
  --shadow-warm-sm: var(--shadow-floating);
  --shadow-warm-md: var(--shadow-floating);
  --shadow-warm-lg: var(--shadow-overlay);
```

- [ ] **Step 4: Run the palette contracts and watch them pass**

```bash
./mvnw -Dtest='ColorContrastTest,DesignTokenContractTest,ElevationModelContractTest' test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/tokens.css src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java
git commit -m "feat: establish the semantic charcoal palette"
```

## Task 4: Confine raw UI colors to the token layer

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/RawVisualValueContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`
- Modify: `src/main/resources/static/css/base.css`, `components.css`, `surfaces.css`,
  `book.css`, `encounter.css`, `cockpit.css`, `cockpit-layout.css`, `cockpit-modules.css`
- Modify: `src/main/resources/templates/encounter/_tracker.html`,
  `encounter/_summary-modal.html`, `treasury/_attunement-warn.html`, `maps/editor.html`

**Interfaces:**
- Produces: `CssRules.rawColorLiterals(String source)`.

- [ ] **Step 1: Add the literal scanner to `CssRules`**

```java
    private static final Pattern RAW_COLOR = Pattern.compile(
            "#[0-9a-fA-F]{3,8}\\b|\\brgba?\\([^)]*\\)|\\bhsla?\\([^)]*\\)");

    /** Raw color literals in a CSS or markup source, in document order. */
    static List<String> rawColorLiterals(String source) {
        Matcher m = RAW_COLOR.matcher(source);
        List<String> found = new ArrayList<>();
        while (m.find()) found.add(m.group());
        return found;
    }
```

- [ ] **Step 2: Write the failing containment test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 6.1: tokens.css is the only source of raw visual values. Persisted terrain,
 * drawing, and sigil colors are domain data and are named in DOMAIN_COLOR_MARKERS.
 */
class RawVisualValueContractTest {

    /** Lines carrying these markers describe persisted domain data, not UI chrome. */
    private static final List<String> DOMAIN_COLOR_MARKERS =
            List.of("data-terrain-color", "data-domain-color", "th:style", "th:attr=\"style");

    @Test
    void noStylesheetOutsideTheTokenLayerDeclaresARawColor() {
        for (String file : CssRules.ALL_FILES) {
            if (file.equals("tokens.css")) continue;
            assertThat(CssRules.rawColorLiterals(CssRules.read(file)))
                    .as("raw color literals in %s — use a semantic token", file)
                    .isEmpty();
        }
    }

    @Test
    void templatesDoNotInlineRawUiColors() {
        for (java.nio.file.Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            for (String line : markup.split("\n")) {
                if (DOMAIN_COLOR_MARKERS.stream().anyMatch(line::contains)) continue;
                assertThat(CssRules.rawColorLiterals(line))
                        .as("raw UI color in %s: %s", template, line.trim())
                        .isEmpty();
            }
        }
    }
}
```

`TemplateRules` is created in Task 10; until then, inline the same walk used by
`CssRules.allTemplateMarkup()` and replace it in Task 10.

- [ ] **Step 3: Run it and watch it fail**

```bash
./mvnw -Dtest='RawVisualValueContractTest' test
```

Expected: FAIL, listing every remaining literal.

- [ ] **Step 4: Replace each reported literal with the nearest semantic token**

Mapping rules:

- backgrounds behind the document → `var(--surface-canvas)`
- grouped panels → `var(--surface-panel)`; cards and active modules → `var(--surface-raised)`
- inputs, wells, embedded data → `var(--surface-inset)`
- hairlines → `var(--border-subtle)`; interactive boundaries → `var(--border-strong)`
- overlay backdrops → `var(--scrim-standard)` or `var(--scrim-strong)`
- floating shadows → `var(--shadow-floating)` / `var(--shadow-overlay)`
- semantic tints → the matching `--state-*-surface` / `--state-*-border`

In `encounter/_tracker.html`, replace the inline concentration background with
`class="combatant-row--concentrating"` and add that rule to `encounter.css` using
`var(--state-concentration-surface)` and `var(--state-concentration-border)`.
In `encounter/_summary-modal.html`, delete the inline modal paint and use the existing
modal contract classes. In `treasury/_attunement-warn.html`, delete the inline style and
use `class="banner banner--warning"`. In `maps/editor.html`, replace the two UI shadow
literals with `var(--shadow-floating)` and leave every persisted terrain color untouched.

- [ ] **Step 5: Run it and watch it pass**

```bash
./mvnw -Dtest='RawVisualValueContractTest,DesignTokenContractTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/config
git commit -m "refactor: confine raw UI color values to the token layer"
```

## Task 5: Freeze the legacy aliases under non-increasing budgets

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`

**Interfaces:**
- Produces: `CssRules.tokenReferenceCount(String token)`.

- [ ] **Step 1: Add the counter to `CssRules`**

```java
    /** How many times a token is referenced across app CSS and template markup. */
    static long tokenReferenceCount(String token) {
        String haystack = allApplicationCss() + "\n" + allTemplateMarkup();
        Matcher m = Pattern.compile("var\\(\\s*" + Pattern.quote(token) + "\\s*[,)]")
                .matcher(haystack);
        return m.results().count();
    }
```

- [ ] **Step 2: Write the failing budget test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The --color-* names are a temporary migration bridge (spec section 18.1). They may only
 * ever shrink. Task 49 deletes the last of them.
 */
class LegacyVisualAliasContractTest {

    /** Fill each ceiling with the count printed by the first run of this test. */
    private static final Map<String, Long> BUDGETS = new LinkedHashMap<>();

    static {
        BUDGETS.put("--color-bg", 0L);
        BUDGETS.put("--color-surface", 0L);
        BUDGETS.put("--color-surface-hover", 0L);
        BUDGETS.put("--color-text", 0L);
        BUDGETS.put("--color-text-muted", 0L);
        BUDGETS.put("--color-accent", 0L);
        BUDGETS.put("--color-accent-hover", 0L);
        BUDGETS.put("--color-danger", 0L);
        BUDGETS.put("--color-danger-hover", 0L);
        BUDGETS.put("--color-success", 0L);
        BUDGETS.put("--color-warning", 0L);
        BUDGETS.put("--color-warning-bg", 0L);
        BUDGETS.put("--color-warning-text", 0L);
        BUDGETS.put("--color-border", 0L);
        BUDGETS.put("--color-border-strong", 0L);
        BUDGETS.put("--color-concentration", 0L);
        BUDGETS.put("--color-info", 0L);
        BUDGETS.put("--color-ember", 0L);
        BUDGETS.put("--color-gold-soft", 0L);
        BUDGETS.put("--color-overlay", 0L);
        BUDGETS.put("--color-shield", 0L);
        BUDGETS.put("--color-shield-hover", 0L);
        BUDGETS.put("--color-shield-soft", 0L);
        BUDGETS.put("--color-attack-bonus", 0L);
        BUDGETS.put("--color-text-secondary", 0L);
        BUDGETS.put("--color-border-subtle", 0L);
        BUDGETS.put("--color-bg-elevated", 0L);
        BUDGETS.put("--color-surface-muted", 0L);
    }

    @Test
    void legacyAliasUseNeverGrows() {
        BUDGETS.forEach((token, ceiling) -> assertThat(CssRules.tokenReferenceCount(token))
                .as("%s references — budgets only ratchet down", token)
                .isLessThanOrEqualTo(ceiling));
    }

    @Test
    void noAliasOutsideTheApprovedVocabularyIsIntroduced() {
        String tokens = CssRules.read("tokens.css");
        var declared = java.util.regex.Pattern.compile("(--color-[a-z0-9-]+)\\s*:")
                .matcher(tokens).results()
                .map(r -> r.group(1))
                .collect(java.util.stream.Collectors.toSet());
        assertThat(declared)
                .as("new legacy aliases are forbidden")
                .isSubsetOf(BUDGETS.keySet());
    }
}
```

- [ ] **Step 3: Run it, read the real counts, and set the ceilings**

```bash
./mvnw -Dtest='LegacyVisualAliasContractTest' test
```

Expected: FAIL. Each failure message prints the actual count. Replace every `0L` with the
exact number reported, then re-run until PASS. Do not round up.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/config
git commit -m "test: freeze legacy visual aliases under non-increasing budgets"
```

## Task 6: Establish the repository-owned SVG icon system

**Files:**
- Create: `src/main/resources/static/icons/ui.svg`
- Create: `src/main/resources/templates/common/_icon.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/IconSystemContractTest.java`
- Modify: `src/main/resources/templates/common/_empty-state.html`

**Interfaces:**
- Produces: `~{common/_icon :: icon(name=…)}` and `~{common/_icon :: icon-sized(name=…, size=…)}`.

The fragment renders an `aria-hidden="true"`, `focusable="false"` SVG. Accessibility belongs
to the enclosing control through visible text or `aria-label`; the icon never becomes a
second accessible name.

- [ ] **Step 1: Write the failing icon contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class IconSystemContractTest {

    private static final Path SPRITE = Path.of("src/main/resources/static/icons/ui.svg");

    /** Every icon the application chrome needs. Extend deliberately, never ad hoc. */
    private static final List<String> REQUIRED = List.of(
            "search", "dice", "menu", "chevron-right", "chevron-down", "close", "plus",
            "edit", "trash", "play", "pause", "check", "alert-triangle", "alert-circle",
            "info", "shield", "eye", "eye-off", "book", "scroll", "map", "swords",
            "users", "user", "castle", "coins", "gem", "note", "calendar", "flag",
            "location", "music", "sparkles", "layers", "brush", "fill", "select",
            "square", "circle", "line", "polygon", "door", "corridor", "room", "pin",
            "undo", "redo", "download", "upload", "settings", "help", "external-link",
            "grip", "filter", "sort", "star", "clock", "heart", "skull", "concentration");

    private static String sprite() throws Exception {
        return Files.readString(SPRITE);
    }

    @Test
    void everyRequiredSymbolExists() throws Exception {
        String svg = sprite();
        for (String name : REQUIRED) {
            assertThat(svg).as("symbol %s", name).contains("id=\"icon-" + name + "\"");
        }
    }

    @Test
    void everySymbolSharesOneGeometryAndStrokeContract() throws Exception {
        var symbols = java.util.regex.Pattern.compile("<symbol[^>]*>")
                .matcher(sprite()).results().map(r -> r.group()).toList();
        assertThat(symbols).hasSizeGreaterThanOrEqualTo(REQUIRED.size());
        for (String symbol : symbols) {
            assertThat(symbol).as("viewBox on %s", symbol).contains("viewBox=\"0 0 24 24\"");
        }
        assertThat(sprite()).contains("stroke=\"currentColor\"")
                .contains("stroke-width=\"1.75\"")
                .contains("stroke-linecap=\"round\"")
                .contains("stroke-linejoin=\"round\"")
                .contains("fill=\"none\"");
    }

    @Test
    void theIconFragmentIsDecorativeOnly() throws Exception {
        String fragment = Files.readString(
                Path.of("src/main/resources/templates/common/_icon.html"));
        assertThat(fragment).contains("aria-hidden=\"true\"").contains("focusable=\"false\"");
        assertThat(fragment).doesNotContain("aria-label");
    }

    @Test
    void applicationChromeCarriesNoEmojiPictograms() {
        String markup = CssRules.allTemplateMarkup();
        for (String forbidden : List.of("&#x1F", "&#x26", "&#x27", "\uD83C", "\uD83D", "\uD83E")) {
            assertThat(markup)
                    .as("emoji or Unicode pictogram in chrome — use ~{common/_icon :: icon}")
                    .doesNotContain(forbidden);
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='IconSystemContractTest' test
```

Expected: FAIL — the sprite does not exist.

- [ ] **Step 3: Author the sprite**

Create `src/main/resources/static/icons/ui.svg` as one `<svg>` containing one `<symbol>`
per required name. Shared attributes go on the root so every symbol inherits them:

```xml
<svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor"
     stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="display:none">
  <symbol id="icon-search" viewBox="0 0 24 24">
    <circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>
  </symbol>
  <symbol id="icon-close" viewBox="0 0 24 24">
    <path d="M6 6l12 12M18 6L6 18"/>
  </symbol>
  <symbol id="icon-plus" viewBox="0 0 24 24">
    <path d="M12 5v14M5 12h14"/>
  </symbol>
  <symbol id="icon-chevron-right" viewBox="0 0 24 24">
    <path d="M9 6l6 6-6 6"/>
  </symbol>
  <!-- one symbol per name in IconSystemContractTest.REQUIRED, same construction -->
</svg>
```

Draw the remaining symbols in the same 24x24 outline style. Keep every path inside a 2px
margin so 16px rendering stays legible.

- [ ] **Step 4: Write the fragment**

`src/main/resources/templates/common/_icon.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<svg th:fragment="icon(name)" class="icon icon--20" width="20" height="20"
     aria-hidden="true" focusable="false">
    <use th:attr="href=@{/icons/ui.svg} + '#icon-' + ${name}"></use>
</svg>

<svg th:fragment="icon-sized(name, size)" th:classappend="'icon icon--' + ${size}"
     th:attr="width=${size},height=${size}" aria-hidden="true" focusable="false">
    <use th:attr="href=@{/icons/ui.svg} + '#icon-' + ${name}"></use>
</svg>
</html>
```

Add to `components.css`:

```css
.icon { flex: none; display: inline-block; vertical-align: -0.125em; color: currentColor; }
.icon--16 { width: 16px; height: 16px; }
.icon--20 { width: 20px; height: 20px; }
.icon--24 { width: 24px; height: 24px; }
```

- [ ] **Step 5: Replace authored pictograms in `common/_empty-state.html`**

Keep all four fragment signatures. Replace the Unicode pictogram output with
`~{common/_icon :: icon-sized(name=${icon}, size='24')}` so callers pass an icon name.

- [ ] **Step 6: Run it and watch it pass**

```bash
./mvnw -Dtest='IconSystemContractTest' test
```

Expected: PASS. If `applicationChromeCarriesNoEmojiPictograms` still fails, the remaining
offenders are in `fragments/navbar.html` and `fragments/_appnav.html`; both are rewritten
in Task 12 and Task 13. Add a temporary `@Disabled("re-enabled in Task 13")` on that one
test method only if it blocks this task, and remove it in Task 13.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/icons src/main/resources/templates/common src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/config/IconSystemContractTest.java
git commit -m "feat: add the repository-owned inline SVG icon system"
```

## Task 7: Apply neutral document, control, focus, and type roles

**Files:**
- Modify: `src/main/resources/static/css/base.css`, `components.css`, `surfaces.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`

- [ ] **Step 1: Rewrite the type-role contract around semantic selectors**

Replace the body of `TypographyRoleContractTest` with:

```java
    @Test
    void cinzelIsReservedForTheWordmarkAndOnePrincipalTitle() {
        var displayRules = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String family = rule.value("font-family");
                    return family != null && family.contains("--font-display");
                })
                .toList();
        assertThat(displayRules)
                .as("Cinzel selectors")
                .allSatisfy(rule -> assertThat(rule.selector())
                        .as("Cinzel in %s", rule.where())
                        .containsAnyOf("data-display-title", ".app-brand", ".page-header__title"));
    }

    @Test
    void narrativeTypeIsScopedToNarrativeSurfaces() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String family = rule.value("font-family");
                    return family != null && family.contains("--font-body-serif");
                })
                .forEach(rule -> assertThat(rule.selector())
                        .as("Alegreya in %s", rule.where())
                        .containsAnyOf(".prose", ".read-aloud", ".narrative", ".statblock", ".book"));
    }

    @Test
    void tabularFiguresAreDeclaredForNumericSurfaces() {
        var tabular = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> {
                    String value = rule.value("font-variant-numeric");
                    return value != null && value.contains("tabular-nums");
                })
                .map(CssRules.Rule::selector)
                .toList();
        assertThat(String.join(" ", tabular))
                .contains(".data-table")
                .contains(".u-num");
    }
```

- [ ] **Step 2: Rewrite the gold contract around approved roles**

Replace `GoldAccentContractTest`'s assertions with:

```java
    private static final java.util.List<String> GOLD_ROLE_MARKERS = java.util.List.of(
            ".btn-primary", ":focus-visible", "[aria-current", "[aria-selected",
            ".is-selected", ".app-brand", "[data-display-title", ".page-header__title",
            "--focus-ring", "--selection-", "--action-primary");

    @Test
    void goldPaintsOnlyPrimaryActionSelectionAndFocus() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> java.util.stream.Stream
                        .of("background", "background-color", "border-color", "outline-color", "color")
                        .map(rule::value)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(value -> value.contains("--action-primary")
                                || value.contains("--selection-accent")
                                || value.contains("--focus-ring")))
                .forEach(rule -> assertThat(GOLD_ROLE_MARKERS)
                        .as("gold outside an approved role in %s", rule.where())
                        .anySatisfy(marker -> assertThat(rule.selector()).contains(marker)));
    }

    @Test
    void noGenericCardOrSeparatorIsGold() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().matches(".*\\.(card|panel|section|divider|rule)\\b.*"))
                .forEach(rule -> {
                    String border = rule.value("border");
                    String borderColor = rule.value("border-color");
                    assertThat(String.valueOf(border) + borderColor)
                            .as("decorative gold in %s", rule.where())
                            .doesNotContain("--action-primary")
                            .doesNotContain("--selection-accent");
                });
    }
```

- [ ] **Step 3: Run both and watch them fail**

```bash
./mvnw -Dtest='TypographyRoleContractTest,GoldAccentContractTest' test
```

Expected: FAIL, naming each offending selector.

- [ ] **Step 4: Repaint `base.css`, `components.css`, and `surfaces.css`**

- `html, body` → `background: var(--surface-canvas); color: var(--text-primary);`
- `.app-main` → `background: var(--surface-workspace);`
- `.card`, `.panel` → `background: var(--surface-raised); border: 1px solid var(--border-subtle);`
  and no gold border, no drop shadow.
- `input, select, textarea` → `background: var(--surface-inset); border: 1px solid var(--border-strong); color: var(--text-primary);`
- `.btn` (secondary default) → neutral: `background: var(--surface-raised); border: 1px solid var(--border-strong); color: var(--text-primary);`
- `.btn-primary` → `background: var(--action-primary); color: var(--action-on-primary); border-color: var(--action-primary);`
- `.btn-danger` → `background: var(--state-danger-surface); border-color: var(--state-danger-border); color: var(--text-primary);`
- Global focus:

```css
:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
  border-radius: inherit;
}
```

- Remove every decorative gold rule, gold hairline, and `rule-taper--gold` treatment.
- Scope `--texture-parchment` to `.parchment`, `.read-aloud`, `.book-cover`, `.campaign-sigil`.
- Add `.u-num { font-variant-numeric: tabular-nums; }` and apply `tabular-nums` to
  `.data-table td`, `.data-table th`.

- [ ] **Step 5: Update `UiPolishContractTest` for the assertions that intentionally changed**

Change only the assertions that encode the superseded brown system or the removed
pictograms. Leave every behavioral assertion untouched. Every edited assertion gets a
one-line comment naming the spec section that superseded it.

- [ ] **Step 6: Run the whole static design suite**

```bash
./mvnw -Dtest='CssInventoryContractTest,ColorContrastTest,DesignTokenContractTest,RawVisualValueContractTest,LegacyVisualAliasContractTest,TypographyRoleContractTest,TypeScaleContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest,IconSystemContractTest,UiPolishContractTest,ControlConsistencyContractTest,DestructiveActionContractTest' test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/css src/test/java/dev/hendrikhoemberg/dmhelper/config
git commit -m "feat: apply neutral document, control, focus, and type roles"
```

## Task 8: Verify computed hierarchy in a real browser

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualFoundationRenderGateTest.java`

**Interfaces:**
- Consumes: `ReleaseRehearsalFixture` (already `@Autowired`-able, see
  `ViewportAccessibilityGateTest`), `BrowserFailureCollector`.

- [ ] **Step 1: Write the failing render gate**

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisualFoundationRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/visual-foundations");

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
        Files.createDirectories(SHOTS);
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures = new BrowserFailureCollector();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
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

    private void open(String path) {
        page.navigate("http://localhost:" + port + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private String bg(String selector) {
        return (String) page.evaluate(
                "s => getComputedStyle(document.querySelector(s)).backgroundColor", selector);
    }

    @Test
    void theSurfaceLadderSeparatesChromeWorkspaceAndCards() {
        open("/campaigns/" + seeded.campaignId());
        assertThat(bg("body")).isEqualTo("rgb(16, 17, 19)");
        assertThat(bg(".app-main")).isEqualTo("rgb(24, 25, 29)");
        assertThat(bg(".card")).isEqualTo("rgb(36, 38, 44)");
    }

    @Test
    void focusIsVisibleAndGold() {
        open("/campaigns");
        page.keyboard().press("Tab");
        Object outline = page.evaluate("""
                () => {
                  const s = getComputedStyle(document.activeElement);
                  return { color: s.outlineColor, width: parseFloat(s.outlineWidth) };
                }
                """);
        @SuppressWarnings("unchecked")
        var o = (java.util.Map<String, Object>) outline;
        assertThat((String) o.get("color")).isEqualTo("rgb(201, 163, 92)");
        assertThat(((Number) o.get("width")).doubleValue()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    void iconsResolveFromTheSprite() {
        open("/campaigns");
        Object broken = page.evaluate("""
                () => [...document.querySelectorAll('svg.icon use')]
                        .filter(u => !u.getAttribute('href')?.includes('/icons/ui.svg#icon-'))
                        .length
                """);
        assertThat(((Number) broken).intValue()).isZero();
    }

    @Test
    void captureTheFoundationReviewSet() {
        record Shot(String name, String path) {
        }
        String c = "/campaigns/" + seeded.campaignId();
        List<Shot> shots = List.of(
                new Shot("campaigns", "/campaigns"),
                new Shot("campaign-home", c),
                new Shot("encounters-index", c + "/encounters"),
                new Shot("party", c + "/party"),
                new Shot("library", "/library"),
                new Shot("maps", c + "/maps"),
                new Shot("session", c + "/session"));
        for (Shot shot : shots) {
            open(shot.path());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot.name() + ".png"))
                    .setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
```

- [ ] **Step 2: Run it**

```bash
./mvnw -Dtest='VisualFoundationRenderGateTest' test
```

Expected: PASS if Task 7 landed correctly. A failing computed color means a stylesheet
still paints an old surface — fix the stylesheet, never the assertion.

- [ ] **Step 3: Look at every screenshot**

Open each file in `target/ui-redesign/visual-foundations/`. The review question is: does
charcoal chrome now separate from the workspace, do cards read as raised, and is gold rare?
Structural problems (stranded content, scattered actions) are expected here — Stage 2 fixes
them.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualFoundationRenderGateTest.java
git commit -m "test: gate the visual foundation on computed browser hierarchy"
```

## Task 9: Run the Stage 1 exit gate

- [ ] **Step 1: Run the full suite**

```bash
./mvnw test
```

Expected: PASS. Investigate every failure; do not weaken an unrelated gate to get green.

- [ ] **Step 2: Record the stage result**

Append a short "Stage 1 complete" note to this plan under the Stage 1 heading listing the
final legacy-alias budget numbers and the screenshot directory.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md
git commit -m "docs: record the visual foundations stage result"
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
.page--detail > .page-header { grid-area: header; }
.page--detail > .page-content { grid-area: content; min-width: 0; }
.page--detail > .page-rail { grid-area: rail; }
.page--detail .prose { max-width: var(--measure-prose); }

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

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec sections 8.2 and 8.3. */
class NavigationRailContractTest {

    private static final Path RAIL =
            Path.of("src/main/resources/templates/fragments/_rail.html");

    @Test
    void theCampaignRailUsesTheApprovedGroupsInOrder() {
        String markup = TemplateRules.read(RAIL);
        int campaign = markup.indexOf(">Campaign<");
        int prepare = markup.indexOf(">Prepare<");
        int partyWorld = markup.indexOf(">Party &amp; World<");
        int records = markup.indexOf(">Records<");
        int reference = markup.indexOf(">Reference<");
        assertThat(List.of(campaign, prepare, partyWorld, records, reference))
                .as("all five groups present").doesNotContain(-1);
        assertThat(campaign).isLessThan(prepare);
        assertThat(prepare).isLessThan(partyWorld);
        assertThat(partyWorld).isLessThan(records);
        assertThat(records).isLessThan(reference);
    }

    @Test
    void everyApprovedDestinationIsPresentExactlyOnce() {
        String markup = TemplateRules.read(RAIL);
        for (String label : List.of("Campaign Home", "Run Session", "Adventures", "Encounters",
                "Maps", "Handouts", "Audio", "Party", "Quests", "NPCs", "Locations", "Factions",
                "Calendar", "Notes", "Treasury", "Ledger", "Library", "Tables", "Traps",
                "Hazards")) {
            assertThat(markup.split(">" + label + "<", -1).length - 1)
                    .as("occurrences of %s", label).isEqualTo(1);
        }
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
        String markup = TemplateRules.read(RAIL);
        int unlessIndex = markup.indexOf("th:unless=\"${campaignId != null}\"");
        assertThat(unlessIndex).as("global branch exists").isNotNegative();
        String global = markup.substring(unlessIndex);
        for (String label : List.of("Campaigns", "Library", "Tables", "Traps", "Hazards", "About")) {
            assertThat(global).as("global destination %s", label).contains(">" + label + "<");
        }
        assertThat(global).doesNotContain("Run Session").doesNotContain("Encounters");
    }

    @Test
    void collapsedLabelsRemainAccessible() {
        var document = TemplateRules.parse(RAIL);
        for (var link : document.select("a.rail__link")) {
            assertThat(link.hasAttr("data-label"))
                    .as("collapsed label for %s", link.outerHtml()).isTrue();
        }
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

    <th:block th:if="${campaignId != null}">
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

    <th:block th:unless="${campaignId != null}">
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
.app-shell--rail-collapsed .rail__text,
.app-shell--rail-collapsed .rail__label { display: none; }
.app-shell--rail-collapsed .rail__link { justify-content: center; padding-inline: 0; }
.app-shell--rail-collapsed .rail__link::after {
  content: attr(data-label);
  position: absolute; left: var(--rail-width-collapsed); margin-left: var(--space-2xs);
  padding: var(--space-2xs) var(--space-xs);
  background: var(--surface-overlay); color: var(--text-primary);
  border: 1px solid var(--border-subtle); border-radius: var(--radius);
  white-space: nowrap; opacity: 0; pointer-events: none;
}
.app-shell--rail-collapsed .rail__link { position: relative; }
.app-shell--rail-collapsed .rail__link:hover::after,
.app-shell--rail-collapsed .rail__link:focus-visible::after { opacity: 1; }
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

## Task 14: Build the shell layout fragment

**Files:**
- Create: `src/main/resources/templates/fragments/_shell.html`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/resources/static/css/base.css`

**Interfaces:**
- Produces: `~{fragments/_shell :: page(pageTitle, archetype, header, content)}` and
  `~{fragments/head :: document-head(pageTitle)}`.
- Consumes: `~{fragments/_topbar :: topbar}`, `~{fragments/_rail :: rail}`.

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

Move the inline data-URI favicon into `src/main/resources/static/icons/favicon.svg` using
`--surface-canvas` and `--action-primary` values so no raw color literal remains in markup.

- [ ] **Step 2: Write `_shell.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      th:fragment="page(pageTitle, archetype, header, content)">
<head th:replace="~{fragments/head :: document-head(pageTitle=${pageTitle})}"></head>
<body th:attr="data-archetype=${archetype}">
    <a class="skip-link" href="#main-content">Skip to content</a>
    <th:block th:replace="~{fragments/_topbar :: topbar}"></th:block>
    <div class="app-shell">
        <th:block th:replace="~{fragments/_rail :: rail}"></th:block>
        <main class="app-main" id="main-content">
            <div th:class="'page page--' + ${archetype}">
                <th:block th:replace="${header}"></th:block>
                <th:block th:replace="${content}"></th:block>
            </div>
        </main>
    </div>
    <th:block th:replace="~{fragments/_overlay :: toast-region}"></th:block>
</body>
</html>
```

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
          header=~{::#page-header},
          content=~{::#page-content})}">
<body>
<div id="page-header">
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

## Task 15: Build the page header contract

**Files:**
- Create: `src/main/resources/templates/fragments/_page-header.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/PageHeaderContractTest.java`

**Interfaces:**
- Produces: `~{fragments/_page-header :: page-header(title, summary, breadcrumb, primary, secondary)}`.

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
          header=~{::#page-header},
          content=~{::#page-content})}">
<body>
<div id="page-header">
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
`header=~{}` — those two own their own command bars, restructured in Stages 6 and 7.

- [ ] **Step 4: Delete `navbar.html`**

```bash
git rm src/main/resources/templates/fragments/navbar.html
```

Then remove the now-unused `head` fragment from `fragments/head.html`, leaving only
`document-head(pageTitle)`.

- [ ] **Step 5: Write the Stage 2 render gate**

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

- [ ] **Step 6: Run the Stage 2 gate**

```bash
./mvnw -Dtest='AppShellContractTest,PageArchetypeContractTest,PageHeaderContractTest,NavigationRailContractTest,TopBarContractTest,SharedComponentContractTest,AsyncStateContractTest,OverlayContractTest,OverlayBehaviorGateTest,ShellRenderGateTest' test
./mvnw test
```

Expected: PASS.

- [ ] **Step 7: Review the stage screenshots**

Re-run `VisualFoundationRenderGateTest` and compare `target/ui-redesign/visual-foundations/`
against the Stage 1 set. Every page must now show one top bar, one grouped rail, one header,
and one primary action. Content is still unrestructured — that is Stages 3–5.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: migrate every page onto the shared shell and archetypes"
```

---

# Stage 3: Campaign and narrative preparation

**Working product after this stage:** campaign selection, Campaign Home, adventures, scenes,
quests, world records, notes, and calendar are restructured onto the archetypes with real
hierarchy — not just repainted.

Every task in this stage shares one shape: extend the feature's existing controller/template
test with the new structural assertions, run it red, restructure the templates, run it green,
commit. Feature behavior, routes, and htmx targets do not change.

## Task 20: Campaign selection

**Files:**
- Modify: `src/main/resources/templates/campaigns/list.html`, `campaigns/_card.html`,
  `campaigns/_new-button.html`, `campaigns/_import-dialog.html`, `campaigns/new.html`
- Modify: `src/main/resources/static/css/surfaces.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignSelectionLayoutTest.java`

**Interfaces:**
- Consumes: `page-header`, `badge`, `states :: empty`, `overlay :: dialog`.

- [ ] **Step 1: Write the failing layout test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.1. */
class CampaignSelectionLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void coversSitOnTheNeutralWorkspaceRatherThanADecoratedCanvas() throws Exception {
        assertThat(read("campaigns/list.html"))
                .contains("archetype='index'")
                .doesNotContain("bookshelf--ornate");
    }

    @Test
    void everyCardShowsNamePartySizeLastActivityAndReadiness() throws Exception {
        String card = read("campaigns/_card.html");
        assertThat(card).contains("data-campaign-name")
                .contains("data-campaign-party-size")
                .contains("data-campaign-last-activity")
                .contains("data-campaign-readiness");
    }

    @Test
    void campaignNamesAreNotForcedToAllCaps() throws Exception {
        var rules = Jsoup.parse(read("campaigns/_card.html"), "", Parser.xmlParser());
        assertThat(rules.select("[data-campaign-name]").attr("class"))
                .doesNotContain("u-uppercase");
    }

    @Test
    void createAndImportAreDistinctActions() throws Exception {
        String list = read("campaigns/list.html");
        assertThat(list).contains("data-action=\"create-campaign\"")
                .contains("data-action=\"import-campaign\"");
        assertThat(list.split("btn-primary", -1).length - 1)
                .as("exactly one filled primary action").isEqualTo(1);
    }

    @Test
    void theEmptyStateExplainsBothCreationPaths() throws Exception {
        assertThat(read("campaigns/list.html"))
                .contains("_states :: empty")
                .contains("Import");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='CampaignSelectionLayoutTest' test
```

- [ ] **Step 3: Restructure the templates**

- `list.html`: index archetype; page header `Campaigns` with `New campaign` as the single
  primary and `Import campaign` as a neutral secondary that opens the import dialog through
  `window.dmOverlay.open`.
- `_card.html`: keep `book-cover` and the deterministic sigil; place them on
  `var(--surface-workspace)`; add the four `data-campaign-*` hooks; render readiness with
  `~{fragments/_badge :: badge(tone=…, icon=…, label=…)}` where tone is `success` when ready,
  `warning` when unresolved, `neutral` when unstarted.
- Remove uppercase transforms from the campaign name; let it wrap naturally at two lines.
- `_import-dialog.html`: move onto `~{fragments/_overlay :: dialog}`.
- Empty state: `~{fragments/_states :: empty(icon='castle', title='No campaigns yet', description='Create one from scratch, or import a campaign package.', cta=~{::#createPaths})}`.

- [ ] **Step 4: Run it green and run the existing campaign tests**

```bash
./mvnw -Dtest='CampaignSelectionLayoutTest,CampaignControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/campaigns src/main/resources/static/css/surfaces.css src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignSelectionLayoutTest.java
git commit -m "feat: restructure campaign selection onto the index archetype"
```

## Task 21: Campaign Home hierarchy

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html`, `campaigns/_readiness.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
  (view-model additions only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignHomeHierarchyTest.java`

**Interfaces:**
- Produces: nothing consumed elsewhere; Campaign Home is the reference standard other pages
  are reviewed against.

Spec section 11.2 fixes the order: current scene and active encounter, one Start/Resume
action, compact readiness, party condition and resources, preparation counts with meaningful
warnings, session plan and recent notes, then secondary settings and package actions.

- [ ] **Step 1: Write the failing hierarchy test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.2: Campaign Home is the hierarchy reference standard. */
class CampaignHomeHierarchyTest {

    private static String home() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/campaigns/detail.html"));
    }

    @Test
    void theSectionsAppearInTheApprovedOrder() throws Exception {
        String markup = home();
        List<String> ordered = List.of(
                "data-home-section=\"current\"",
                "data-home-section=\"start\"",
                "data-home-section=\"readiness\"",
                "data-home-section=\"party\"",
                "data-home-section=\"preparation\"",
                "data-home-section=\"plan\"",
                "data-home-section=\"admin\"");
        int previous = -1;
        for (String marker : ordered) {
            int index = markup.indexOf(marker);
            assertThat(index).as("%s present", marker).isNotNegative();
            assertThat(index).as("%s follows the previous section", marker).isGreaterThan(previous);
            previous = index;
        }
    }

    @Test
    void thereIsExactlyOneStartOrResumeSessionAction() throws Exception {
        assertThat(home().split("data-action=\"start-session\"", -1).length - 1).isEqualTo(1);
    }

    @Test
    void zeroCountsDoNotBecomeEqualWeightMetricTiles() throws Exception {
        assertThat(home())
                .as("preparation counts must suppress irrelevant zeros")
                .contains("data-prep-count")
                .contains("th:if=\"${");
    }

    @Test
    void unresolvedPreparationUsesABannerNotTinyText() throws Exception {
        assertThat(home()).contains("_banner :: banner");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='CampaignHomeHierarchyTest' test
```

- [ ] **Step 3: Restructure `campaigns/detail.html`**

Use `archetype='operational'`. Emit seven sections in order, each marked with its
`data-home-section` value:

1. `current` — current scene title and active encounter as a raised panel with
   `~{fragments/_badge :: badge}` for state; if neither exists, a single line saying so with a
   link to Adventures.
2. `start` — one `btn-primary` marked `data-action="start-session"`, labelled `Start session`
   or `Resume session` from the existing session state.
3. `readiness` — compact summary reusing `_readiness.html`, rendered as a row of labelled
   badges rather than a grid of tiles.
4. `party` — condition and resource summary from the existing party view model.
5. `preparation` — counts with `data-prep-count`; render a count only when it is non-zero or
   its absence is itself a warning, and raise `~{fragments/_banner :: banner(tone='warning', …)}`
   for unresolved preparation.
6. `plan` — session plan and recent notes.
7. `admin` — settings, export, import, and package actions behind a neutral secondary group
   or overflow popover; destructive campaign actions in a labelled danger region.

Delete every duplicated Run Session control on this page.

- [ ] **Step 4: Add only the view-model fields the sections need**

If a section cannot be rendered from the current model, add a read-only record to
`CampaignController`'s model. Do not touch services, entities, or package adapters.

- [ ] **Step 5: Run it green**

```bash
./mvnw -Dtest='CampaignHomeHierarchyTest,CampaignControllerTest,CampaignReadinessControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/campaigns src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignHomeHierarchyTest.java
git commit -m "feat: give Campaign Home the approved operational hierarchy"
```

## Task 22: Adventure index and detail

**Files:**
- Modify: `src/main/resources/templates/adventure/list.html`, `_adventure-list.html`,
  `detail.html`, `_chapter-list.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureLayoutTest.java`

- [ ] **Step 1: Write the failing layout test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.3. */
class AdventureLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theIndexShowsChapterAndSceneProgressAndCurrentPosition() throws Exception {
        String card = read("adventure/_adventure-list.html");
        assertThat(card).contains("data-adventure-progress").contains("data-adventure-current");
    }

    @Test
    void theDetailPresentsAScannableChapterOutlineWithAlignedSceneRows() throws Exception {
        String detail = read("adventure/detail.html");
        assertThat(detail).contains("archetype='detail'").contains("data-chapter-outline");
        assertThat(read("adventure/_chapter-list.html")).contains("data-scene-row");
    }

    @Test
    void structureEditingStaysBehindEdit() throws Exception {
        assertThat(read("adventure/detail.html"))
                .as("editorial structure controls belong behind Edit (spec 11.3)")
                .doesNotContain("_scene-structure-editor");
    }
}
```

- [ ] **Step 2: Run it red, restructure, run it green**

```bash
./mvnw -Dtest='AdventureLayoutTest' test
```

- `list.html` → index archetype, toolbar with search, compact cards carrying
  `data-adventure-progress` (chapters/scenes complete) and `data-adventure-current`
  (current position badge).
- `detail.html` → detail archetype; primary column is the chapter outline
  (`data-chapter-outline`) with aligned scene rows (`data-scene-row`: name, status badge,
  map, encounter); contextual rail carries adventure status, current position, and the Edit
  action.
- Structure editing stays on `scene-structure.html`, reachable from the rail's Edit action.

```bash
./mvnw -Dtest='AdventureLayoutTest,AdventureControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/adventure src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureLayoutTest.java
git commit -m "feat: restructure the adventure index and detail"
```

## Task 23: Scene detail as a narrative surface

**Files:**
- Modify: `src/main/resources/templates/adventure/scene-detail.html`, `_scene-body.html`,
  `_scene-rail.html`, `_scene-sections.html`
- Modify: `src/main/resources/static/css/book.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.3: narrative is primary; state and links live in the rail. */
class SceneDetailLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void narrativeContentOwnsThePrimaryColumn() throws Exception {
        assertThat(read("adventure/scene-detail.html"))
                .contains("archetype='detail'")
                .contains("id=\"page-content\"");
        assertThat(read("adventure/_scene-body.html")).contains("class=\"prose");
    }

    @Test
    void statusMapEncounterHandoutsAndEditLiveInTheRail() throws Exception {
        String rail = read("adventure/_scene-rail.html");
        for (String section : List.of("Status", "Map", "Encounter", "Handouts", "References")) {
            assertThat(rail).as("rail section %s", section).contains(section);
        }
        assertThat(rail).contains("_context-rail :: rail-section");
    }

    @Test
    void readAloudTextGetsTheParchmentTreatment() throws Exception {
        assertThat(read("adventure/_scene-sections.html")).contains("class=\"read-aloud");
        assertThat(Files.readString(Path.of("src/main/resources/static/css/book.css")))
                .contains(".read-aloud")
                .contains("--texture-parchment");
    }

    @Test
    void narrativeProseUsesTheNarrativeTypeface() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/static/css/book.css")))
                .contains("--font-body-serif");
    }
}
```

- [ ] **Step 2: Run it red, restructure, run it green**

```bash
./mvnw -Dtest='SceneDetailLayoutTest' test
```

- Primary column: scene narrative in `.prose` bounded to `var(--measure-prose)`, read-aloud
  blocks in `.read-aloud` with the parchment texture and Alegreya.
- Rail (`_scene-rail.html`): `rail-section` per Status, Map, Encounter, Handouts, References,
  plus the Edit action. Current-scene state and transitions render as badges in the Status
  section, not as a banner over the narrative.
- Editorial metadata (participants, checks, links editing) moves behind Edit.

```bash
./mvnw -Dtest='SceneDetailLayoutTest,SceneControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/adventure src/main/resources/static/css/book.css src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailLayoutTest.java
git commit -m "feat: make scene detail a narrative-first surface"
```

## Task 24: Quests

**Files:**
- Modify: `src/main/resources/templates/quest/list.html`, `detail.html`,
  `_objective-list.html`, `_dependency-list.html`, `_link-list.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/quest/web/QuestLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.quest.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.7: Records pattern; graph mechanics stay out of the reading surface. */
class QuestLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theIndexUsesTheStandardToolbarAndStatusChips() throws Exception {
        String list = read("quest/list.html");
        assertThat(list).contains("_toolbar :: toolbar").contains("_badge :: badge");
    }

    @Test
    void objectivesShowPrerequisitesAndCompletionModeInPlainLanguage() throws Exception {
        String objectives = read("quest/_objective-list.html");
        assertThat(objectives).contains("data-objective-prerequisite")
                .contains("data-objective-completion-mode");
        assertThat(objectives)
                .as("raw graph mechanics must not surface as data (spec 11.7)")
                .doesNotContain("edgeType")
                .doesNotContain("nodeId");
    }

    @Test
    void relationshipsUseTheContextRail() throws Exception {
        assertThat(read("quest/detail.html"))
                .contains("archetype='detail'")
                .contains("_context-rail :: rail");
    }
}
```

- [ ] **Step 2: Run it red, restructure, run it green**

```bash
./mvnw -Dtest='QuestLayoutTest' test
```

- `list.html` → index archetype, `toolbar` with search plus a status filter group, dense rows
  with aligned status chip and objective progress.
- `detail.html` → detail archetype; objectives and narrative in the primary column;
  dependencies, links, and annotations in `rail-section`s.
- `_objective-list.html` → each objective renders its prerequisite as a sentence
  (`data-objective-prerequisite`) and its completion mode as a labelled badge
  (`data-objective-completion-mode`). Never print raw graph identifiers.

```bash
./mvnw -Dtest='QuestLayoutTest,QuestControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/quest src/test/java/dev/hendrikhoemberg/dmhelper/quest/web/QuestLayoutTest.java
git commit -m "feat: restructure quests onto the records pattern"
```

## Task 25: World records and notes

**Files:**
- Modify: `src/main/resources/templates/world/*.html`, `world/_relationship-row.html`,
  `world/_clock-row.html`
- Modify: `src/main/resources/templates/notes/list.html`, `detail.html`, `_card.html`,
  `_quicknotes-strip.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/world/web/WorldRecordsLayoutTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/web/NotesLayoutTest.java`

- [ ] **Step 1: Write the failing world test**

```java
package dev.hendrikhoemberg.dmhelper.world.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.7. */
class WorldRecordsLayoutTest {

    private static final List<String> INDEXES =
            List.of("world/npcs-list.html", "world/locations-list.html", "world/factions-list.html");
    private static final List<String> DETAILS =
            List.of("world/npcs-detail.html", "world/locations-detail.html",
                    "world/factions-detail.html");

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void everyWorldIndexSharesTheRecordsPattern() throws Exception {
        for (String template : INDEXES) {
            assertThat(read(template)).as(template)
                    .contains("archetype='index'")
                    .contains("_toolbar :: toolbar")
                    .contains("_states :: empty");
        }
    }

    @Test
    void everyWorldDetailUsesTheContextRail() throws Exception {
        for (String template : DETAILS) {
            assertThat(read(template)).as(template)
                    .contains("archetype='detail'")
                    .contains("_context-rail :: rail");
        }
    }

    @Test
    void relationshipsAreGroupedByRoleAndDirection() throws Exception {
        assertThat(read("world/_relationship-row.html"))
                .contains("data-relationship-role")
                .contains("data-relationship-direction");
    }

    @Test
    void factionClocksReadAsProgressNotAsBareNumbers() throws Exception {
        assertThat(read("world/_clock-row.html"))
                .contains("role=\"progressbar\"")
                .contains("aria-valuenow");
    }
}
```

- [ ] **Step 2: Write the failing notes test**

```java
package dev.hendrikhoemberg.dmhelper.notes.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.7: title, type, tags, body, backlinks; quick notes stay fast. */
class NotesLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void noteCardsLeadWithTitleTypeAndTags() throws Exception {
        String card = read("notes/_card.html");
        int title = card.indexOf("data-note-title");
        int type = card.indexOf("data-note-type");
        int tags = card.indexOf("data-note-tags");
        assertThat(title).isNotNegative();
        assertThat(title).isLessThan(type);
        assertThat(type).isLessThan(tags);
    }

    @Test
    void backlinksLiveInTheRail() throws Exception {
        assertThat(read("notes/detail.html"))
                .contains("_context-rail :: rail-section")
                .contains("Backlinks");
    }

    @Test
    void playerSafeStateUsesStableShieldSemantics() throws Exception {
        assertThat(read("notes/_card.html")).contains("tone='shield'");
    }

    @Test
    void theQuickNoteStripStaysASingleStepAction() throws Exception {
        var strip = read("notes/_quicknotes-strip.html");
        assertThat(strip).contains("hx-post");
        assertThat(strip.split("<form", -1).length - 1)
                .as("one form; quick capture must not become a wizard").isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run both red, restructure, run green**

```bash
./mvnw -Dtest='WorldRecordsLayoutTest,NotesLayoutTest' test
```

Restructure all nine world templates and the four note templates onto the Records pattern:
index archetype + toolbar + dense rows + empty state; detail archetype + context rail;
relationships grouped by role and direction; faction clocks as accessible progress bars;
note player-safe state as a Shield badge.

```bash
./mvnw -Dtest='WorldRecordsLayoutTest,NotesLayoutTest,NpcControllerTest,LocationControllerTest,FactionControllerTest,NoteControllerTest,FullPageRenderSmokeTest' test
```

Use the actual controller test names in `src/test/java/dev/hendrikhoemberg/dmhelper/world/web`
and `.../notes/web`; run the whole package if unsure:
`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.world.web.*,dev.hendrikhoemberg.dmhelper.notes.web.*' test`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/world src/main/resources/templates/notes src/test/java/dev/hendrikhoemberg/dmhelper/world src/test/java/dev/hendrikhoemberg/dmhelper/notes
git commit -m "feat: restructure world records and notes onto the records pattern"
```

## Task 26: Calendar and the Stage 3 gate

**Files:**
- Modify: `src/main/resources/templates/calendar/overview.html`, `_current-date.html`,
  `_timeline-list.html`, `_event-card.html`, `_config-form.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/web/CalendarLayoutTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/NarrativePreparationRenderGateTest.java`

- [ ] **Step 1: Write the failing calendar test**

```java
package dev.hendrikhoemberg.dmhelper.calendar.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.7: separate current date, upcoming events, and history. */
class CalendarLayoutTest {

    private static String overview() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/calendar/overview.html"));
    }

    @Test
    void theThreeRegionsAreDistinctAndOrdered() throws Exception {
        String markup = overview();
        int current = markup.indexOf("data-calendar-region=\"current\"");
        int upcoming = markup.indexOf("data-calendar-region=\"upcoming\"");
        int history = markup.indexOf("data-calendar-region=\"history\"");
        assertThat(current).isNotNegative();
        assertThat(current).isLessThan(upcoming);
        assertThat(upcoming).isLessThan(history);
    }

    @Test
    void currentDateControlsAreNotOneOfThreeEqualWeightPanels() throws Exception {
        assertThat(overview())
                .as("the current date is the operational focus, not a peer card")
                .doesNotContain("class=\"dash-grid\"");
    }

    @Test
    void datesUseTabularFigures() throws Exception {
        assertThat(Files.readString(
                Path.of("src/main/resources/templates/calendar/_current-date.html")))
                .contains("u-num");
    }
}
```

- [ ] **Step 2: Restructure the calendar and run it green**

`overview.html` → operational archetype with three marked regions: `current` (prominent
date controls and advance action), `upcoming` (events table), `history` (timeline). Calendar
configuration moves into a side sheet opened from a neutral secondary action.

```bash
./mvnw -Dtest='CalendarLayoutTest,CalendarControllerTest,CalendarFormatDateTest' test
```

- [ ] **Step 3: Write the Stage 3 render gate**

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NarrativePreparationRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/narrative");

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
        Files.createDirectories(SHOTS);
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        failures = new BrowserFailureCollector();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
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

    private void open(String path) {
        page.navigate("http://localhost:" + port + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    @Test
    void campaignHomeShowsCurrentStateAboveTheFold() {
        open("/campaigns/" + seeded.campaignId());
        double y = ((Number) page.evaluate(
                "() => document.querySelector('[data-home-section=\"start\"]')"
                        + ".getBoundingClientRect().bottom")).doubleValue();
        assertThat(y).as("Start/Resume must be visible without scrolling").isLessThan(720);
    }

    @Test
    void sceneNarrativeStaysWithinAReadableMeasure() {
        open("/campaigns/" + seeded.campaignId() + "/adventures/" + seeded.adventureId()
                + "/scenes/" + seeded.sceneId());
        double width = ((Number) page.evaluate(
                "() => document.querySelector('.prose').getBoundingClientRect().width"))
                .doubleValue();
        double fontSize = ((Number) page.evaluate(
                "() => parseFloat(getComputedStyle(document.querySelector('.prose')).fontSize)"))
                .doubleValue();
        assertThat(width / fontSize).as("characters per line ≈ width / font-size / 0.5")
                .isBetween(60.0 * 0.5, 90.0 * 0.5);
    }

    @Test
    void captureTheNarrativeReviewSet() {
        String c = "/campaigns/" + seeded.campaignId();
        List<String[]> shots = List.of(
                new String[]{"campaigns", "/campaigns"},
                new String[]{"campaign-home", c},
                new String[]{"adventures", c + "/adventures"},
                new String[]{"adventure-detail", c + "/adventures/" + seeded.adventureId()},
                new String[]{"quests", c + "/quests"},
                new String[]{"npcs", c + "/world/npcs"},
                new String[]{"notes", c + "/notes"},
                new String[]{"calendar", c + "/calendar"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
```

Use whatever accessor `ReleaseRehearsalFixture.Seeded` exposes for a scene id; if it has
none, add one that returns an existing seeded scene without changing the fixture's data.

- [ ] **Step 4: Run the Stage 3 gate**

```bash
./mvnw -Dtest='CampaignSelectionLayoutTest,CampaignHomeHierarchyTest,AdventureLayoutTest,SceneDetailLayoutTest,QuestLayoutTest,WorldRecordsLayoutTest,NotesLayoutTest,CalendarLayoutTest,NarrativePreparationRenderGateTest' test
./mvnw test
```

- [ ] **Step 5: Review `target/ui-redesign/narrative/` and commit**

```bash
git add -A
git commit -m "feat: complete the campaign and narrative preparation stage"
```

---

# Stage 4: Operational preparation

**Working product after this stage:** encounters, party, character sheets, treasury, ledger,
handouts, and audio are aligned operational surfaces with substantial runtime state.

## Task 27: Encounter index and detail

**Files:**
- Modify: `src/main/resources/templates/encounter/list.html`, `_card.html`, `detail.html`,
  `_prep-summary.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.encounter.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.4. */
class EncounterLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theIndexIsScanFriendlyAndFilterable() throws Exception {
        String list = read("encounter/list.html");
        assertThat(list).contains("archetype='index'").contains("_toolbar :: toolbar");
        assertThat(list).contains("data-filter=\"status\"");
    }

    @Test
    void everyCardCarriesTheFiveScanCriticalFields() throws Exception {
        String card = read("encounter/_card.html");
        for (String field : List.of("data-encounter-status", "data-encounter-difficulty",
                "data-encounter-combatants", "data-encounter-map", "data-encounter-readiness")) {
            assertThat(card).as(field).contains(field);
        }
    }

    @Test
    void detailOffersExactlyOneRunOrResumeAction() throws Exception {
        assertThat(read("encounter/detail.html")
                .split("data-action=\"run-encounter\"", -1).length - 1).isEqualTo(1);
    }

    @Test
    void countsAndDifficultyUseTabularFigures() throws Exception {
        assertThat(read("encounter/_card.html")).contains("u-num");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

- `list.html` → index archetype with a toolbar carrying search and a `data-filter="status"`
  group; grid of cards (or table when the campaign has more than 12 encounters — reuse the
  existing view-model count, do not add a preference).
- `_card.html` → the five `data-encounter-*` fields, status and readiness as badges, counts
  in `.u-num`.
- `detail.html` → detail archetype; readable preparation summary in the primary column;
  contextual rail with map, difficulty, readiness, and the single
  `data-action="run-encounter"` primary.

```bash
./mvnw -Dtest='EncounterLayoutTest,EncounterControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterLayoutTest.java
git commit -m "feat: restructure the encounter index and detail"
```

## Task 28: Encounter setup as a two-column operational workspace

**Files:**
- Modify: `src/main/resources/templates/encounter/setup.html`, `_combatant-list.html`,
  `_library-add.html`, `_threat-add.html`, `_waves.html`, `_placement-board.html`
- Modify: `src/main/resources/static/css/encounter.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterSetupLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.encounter.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.4: deliberate two-column setup with usable control widths. */
class EncounterSetupLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void setupDeclaresTheTwoOperationalColumns() throws Exception {
        String setup = read("encounter/setup.html");
        assertThat(setup).contains("archetype='operational'")
                .contains("data-setup-column=\"sources\"")
                .contains("data-setup-column=\"settings\"");
    }

    @Test
    void quantityWaveSearchAndGroupControlsHaveDeclaredUsableWidths() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/encounter.css"));
        for (String control : List.of(".setup-qty", ".setup-wave", ".setup-search", ".setup-group")) {
            assertThat(css).as("width rule for %s", control).contains(control);
        }
        assertThat(css)
                .as("micro-controls are forbidden (spec 11.4)")
                .doesNotContain("width: 2rem")
                .doesNotContain("width: 32px");
    }

    @Test
    void setupIsVisuallyDistinctFromLiveCombat() throws Exception {
        assertThat(read("encounter/setup.html")).contains("data-encounter-mode=\"setup\"");
        assertThat(read("encounter/_tracker.html")).contains("data-encounter-mode=\"live\"");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

- `setup.html` → operational archetype; left column `data-setup-column="sources"` holds the
  combatant list and the library/threat/party add sources; right column
  `data-setup-column="settings"` holds encounter settings, waves, and placement.
- Give `.setup-qty` a minimum of `4.5rem`, `.setup-wave` `7rem`, `.setup-search` `16rem`, and
  `.setup-group` `12rem` in `encounter.css`, each with a visible label.
- Mark the mode on both surfaces so the CSS and later gates can tell setup from live combat;
  give the live tracker a distinctly darker canvas and heavier row treatment.

```bash
./mvnw -Dtest='EncounterSetupLayoutTest,EncounterSetupControllerTest,FullPageRenderSmokeTest' test
```

Run the whole encounter web package if the setup controller test has a different name:
`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.encounter.web.*' test`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter src/main/resources/static/css/encounter.css src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterSetupLayoutTest.java
git commit -m "feat: rebuild encounter setup as a two-column operational workspace"
```

## Task 29: Live combat legibility

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`, `_combatant-list.html`
- Modify: `src/main/resources/static/css/encounter.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java`

- [ ] **Step 1: Extend the existing combat legibility contract**

```java
    @Test
    void theActiveCombatantGetsSubstantialRowTreatment() {
        var active = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().contains(".combatant-row--active"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".combatant-row--active is not styled"));
        long signals = java.util.stream.Stream
                .of(active.value("background"), active.value("border-left"),
                        active.value("box-shadow"), active.value("outline"))
                .filter(java.util.Objects::nonNull)
                .count();
        assertThat(signals)
                .as("active turn needs more than a thin colored border (spec 12.5)")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void everyRuntimeStateCombinesColorWithSomethingElse() {
        String tracker = CssRules.allTemplateMarkup();
        for (String state : java.util.List.of("defeated", "concentrating", "unresolved-initiative")) {
            assertThat(tracker)
                    .as("state %s needs an icon or explicit label, not color alone", state)
                    .contains("data-combatant-state=\"" + state + "\"");
        }
    }

    @Test
    void combatNumbersUseTabularFigures() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).containsAnyOf(".combatant-hp", ".combatant-row");
            assertThat(rule.value("font-variant-numeric")).contains("tabular-nums");
        });
    }
```

- [ ] **Step 2: Run red, implement, run green**

- Add `data-combatant-state` to each row with `defeated`, `concentrating`, or
  `unresolved-initiative`, each rendering an icon and a text label alongside its color.
- Style `.combatant-row--active` with a raised background, a 3px `--selection-accent` left
  edge, and a subtle `--glow-primary` — three signals, not one.
- Apply `font-variant-numeric: tabular-nums` to HP, AC, and initiative cells.

```bash
./mvnw -Dtest='CombatLegibilityContractTest,EncounterCombatControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter src/main/resources/static/css/encounter.css src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java
git commit -m "feat: give live combat substantial runtime state treatment"
```

## Task 30: Party roster

**Files:**
- Modify: `src/main/resources/templates/party/list.html`, `_roster.html`, `_summary-bar.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyRosterLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.party.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.5 and section 8.2's rail decision. */
class PartyRosterLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theRosterAlignsTheSevenScanColumns() throws Exception {
        String roster = read("party/_roster.html");
        for (String column : List.of("data-roster-name", "data-roster-player", "data-roster-ac",
                "data-roster-hp", "data-roster-passives", "data-roster-conditions",
                "data-roster-resources")) {
            assertThat(roster).as(column).contains(column);
        }
        assertThat(roster).contains("class=\"data-table");
    }

    @Test
    void bulkActionsUseTheStandardTableToolbar() throws Exception {
        assertThat(read("party/list.html")).contains("_toolbar :: table-toolbar");
    }

    @Test
    void characterSheetsRemainReachableFromTheParty() throws Exception {
        assertThat(read("party/list.html"))
                .as("the rail no longer lists Sheets, so Party must link to them")
                .contains("/sheets");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

- `list.html` → operational archetype; `table-toolbar` for selection and bulk actions;
  `Character sheets` as a neutral secondary action in the page header pointing at
  `/campaigns/{id}/sheets`.
- `_roster.html` → one `.data-table` with the seven aligned columns; HP and AC in tabular
  figures; conditions as badges with icon and label.

```bash
./mvnw -Dtest='PartyRosterLayoutTest,PartyControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/party src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyRosterLayoutTest.java
git commit -m "feat: rebuild the party roster as an aligned operational table"
```

## Task 31: Character sheets

**Files:**
- Modify: `src/main/resources/templates/sheet/detail.html`, `overview.html`,
  `_derived-stats.html`, `_live-state.html`, `_rest-preview.html`, `_level-up-dialog.html`,
  `_inventory.html`, `_resources.html`, `_spell-list.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.sheet.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.5. */
class SheetLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theIdentityAndDerivedStatSummaryIsPersistent() throws Exception {
        assertThat(read("sheet/detail.html")).contains("data-sheet-identity");
        var css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        assertThat(css).contains("[data-sheet-identity]").contains("position: sticky");
    }

    @Test
    void sheetSectionsUseLocalHierarchyNotAStackOfIdenticalCards() throws Exception {
        String detail = read("sheet/detail.html");
        assertThat(detail).contains("data-sheet-section");
        assertThat(detail.split("class=\"card\"", -1).length - 1)
                .as("same-color card stack (spec 11.5)").isLessThanOrEqualTo(2);
    }

    @Test
    void pendingChangesAreDistinctFromAppliedChanges() throws Exception {
        assertThat(read("sheet/_rest-preview.html")).contains("data-change-state=\"pending\"");
        assertThat(read("sheet/_live-state.html")).contains("data-change-state=\"applied\"");
    }

    @Test
    void levelUpIsABlockingDialogWithFocusManagement() throws Exception {
        assertThat(read("sheet/_level-up-dialog.html")).contains("_overlay :: dialog");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

- `detail.html` → operational archetype; a sticky `data-sheet-identity` strip carrying name,
  class/level, AC, HP, speed, and proficiency; sections marked `data-sheet-section` and
  separated by headings and rules rather than nested cards.
- Rest, level-up, inventory, spell, and resource surfaces mark
  `data-change-state="pending"` versus `"applied"` and use the warning/success tones
  accordingly.
- `_level-up-dialog.html` moves onto the shared dialog.

```bash
./mvnw -Dtest='SheetLayoutTest,SheetControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/sheet src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetLayoutTest.java
git commit -m "feat: rebuild character sheets with real local hierarchy"
```

## Task 32: Treasury and ledger

**Files:**
- Modify: `src/main/resources/templates/treasury/list.html`, `_card.html`,
  `_holder-section.html`, `_attunement-warn.html`, `_form.html`
- Modify: `src/main/resources/templates/ledger/list.html`, `_card.html`, `_balance.html`,
  `_form.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/treasury/web/TransactionalTableLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.treasury.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.6. */
class TransactionalTableLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void bothSurfacesUseTransactionalTables() throws Exception {
        for (String template : List.of("treasury/list.html", "ledger/list.html")) {
            assertThat(read(template)).as(template)
                    .contains("archetype='operational'")
                    .contains("class=\"data-table");
        }
    }

    @Test
    void treasuryColumnsAreAligned() throws Exception {
        String card = read("treasury/_card.html");
        for (String column : List.of("data-item-owner", "data-item-state", "data-item-quantity",
                "data-item-attunement")) {
            assertThat(card).as(column).contains(column);
        }
    }

    @Test
    void ledgerAmountsDirectionsAndBalancesAreAlignedAndTabular() throws Exception {
        String card = read("ledger/_card.html");
        assertThat(card).contains("data-entry-amount").contains("data-entry-direction")
                .contains("u-num");
        assertThat(read("ledger/_balance.html")).contains("u-num");
    }

    @Test
    void attunementWarningsAreBannersNotTinyAnnotations() throws Exception {
        assertThat(read("treasury/_attunement-warn.html")).contains("_banner :: banner");
    }

    @Test
    void entryFormsUseTheFormArchetypeAndSeparateDestructiveActions() throws Exception {
        for (String template : List.of("treasury/_form.html", "ledger/_form.html")) {
            assertThat(read(template)).as(template).contains("form-actions");
        }
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

```bash
./mvnw -Dtest='TransactionalTableLayoutTest,TreasuryControllerTest,LedgerControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/treasury src/main/resources/templates/ledger src/test/java/dev/hendrikhoemberg/dmhelper/treasury/web/TransactionalTableLayoutTest.java
git commit -m "feat: rebuild treasury and ledger as transactional tables"
```

## Task 33: Handouts, audio, and the Stage 4 gate

**Files:**
- Modify: `src/main/resources/templates/handout/list.html`, `_card.html`
- Modify: `src/main/resources/templates/audio/list.html`, `_card.html`, `detail.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/AssetCatalogLayoutTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/OperationalPreparationRenderGateTest.java`

- [ ] **Step 1: Write the failing catalog test**

```java
package dev.hendrikhoemberg.dmhelper.handout.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.8. */
class AssetCatalogLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void handoutSafetyStateUsesStableShieldAndDangerSemantics() throws Exception {
        String card = read("handout/_card.html");
        assertThat(card).contains("tone='shield'").contains("data-handout-visibility");
    }

    @Test
    void presentIsVisuallyDistinctFromEditAndDelete() throws Exception {
        String card = read("handout/_card.html");
        assertThat(card).contains("data-action=\"present\"");
        int present = card.indexOf("data-action=\"present\"");
        int destructive = card.indexOf("action-row__destructive");
        assertThat(destructive).as("destructive group exists").isNotNegative();
        assertThat(Math.abs(destructive - present)).as("present and delete are separated")
                .isGreaterThan(80);
    }

    @Test
    void audioCuesDistinguishSourceAssignmentAvailabilityAndRuntimeStatus() throws Exception {
        String card = read("audio/_card.html");
        for (String field : java.util.List.of("data-cue-source", "data-cue-assignment",
                "data-cue-availability", "data-cue-runtime")) {
            assertThat(card).as(field).contains(field);
        }
    }

    @Test
    void providerUnavailableKeepsAUsefulNextAction() throws Exception {
        assertThat(read("audio/list.html")).contains("_states :: unavailable");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

Both index pages become visual asset card grids on the index archetype with concise
operational metadata; present/preview separates from edit/delete; audio adds the four cue
state fields and an `unavailable` state that still offers "Configure provider".

- [ ] **Step 3: Write the Stage 4 render gate**

Copy the structure of `NarrativePreparationRenderGateTest` into
`OperationalPreparationRenderGateTest` with `SHOTS = Path.of("target/ui-redesign/operational")`
and these tests:

```java
    @Test
    void encounterSetupControlsAreNotMicroControlsAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.encounterId() + "/setup");
        double narrowest = ((Number) page.evaluate("""
                () => Math.min(...[...document.querySelectorAll(
                        '[data-setup-column] input, [data-setup-column] select')]
                        .map(el => el.getBoundingClientRect().width))
                """)).doubleValue();
        assertThat(narrowest).as("narrowest setup control").isGreaterThanOrEqualTo(64.0);
    }

    @Test
    void theActiveCombatantIsDistinguishableByMoreThanColor() {
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.encounterId());
        Object signals = page.evaluate("""
                () => {
                  const row = document.querySelector('.combatant-row--active');
                  if (!row) return -1;
                  const s = getComputedStyle(row);
                  let count = 0;
                  if (s.borderLeftWidth !== '0px') count++;
                  if (s.boxShadow !== 'none') count++;
                  if (s.backgroundColor !== getComputedStyle(row.parentElement).backgroundColor) count++;
                  return count;
                }
                """);
        assertThat(((Number) signals).intValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void captureTheOperationalReviewSet() {
        String c = "/campaigns/" + seeded.campaignId();
        java.util.List<String[]> shots = java.util.List.of(
                new String[]{"encounters", c + "/encounters"},
                new String[]{"encounter-detail", c + "/encounters/" + seeded.encounterId()},
                new String[]{"encounter-setup", c + "/encounters/" + seeded.encounterId() + "/setup"},
                new String[]{"party", c + "/party"},
                new String[]{"sheets", c + "/sheets"},
                new String[]{"treasury", c + "/treasury"},
                new String[]{"ledger", c + "/ledger"},
                new String[]{"handouts", c + "/handouts"},
                new String[]{"audio", c + "/audio/cues"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
```

- [ ] **Step 4: Run the Stage 4 gate**

```bash
./mvnw -Dtest='EncounterLayoutTest,EncounterSetupLayoutTest,CombatLegibilityContractTest,PartyRosterLayoutTest,SheetLayoutTest,TransactionalTableLayoutTest,AssetCatalogLayoutTest,OperationalPreparationRenderGateTest' test
./mvnw test
```

- [ ] **Step 5: Review `target/ui-redesign/operational/` and commit**

```bash
git add -A
git commit -m "feat: complete the operational preparation stage"
```

---

# Stage 5: Reference workspace

**Working product after this stage:** Library is a reference workspace with a stable category
navigator, scan-aligned cards, and detail surfaces; tables, traps, and hazards share the
visual language while keeping their own top-level destinations and renderers.

## Task 34: Library workspace and category navigator

**Files:**
- Modify: `src/main/resources/templates/library/list.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`
  (page title and active-category model attributes only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryWorkspaceLayoutTest.java`

**Interfaces:**
- Produces: `data-library-category` on each navigator entry; `activeCategory` model attribute.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.9: Library is a reference workspace, not a card wall. */
class LibraryWorkspaceLayoutTest {

    static final List<String> CATEGORIES = List.of("Monsters", "Spells", "Conditions", "Rules",
            "Equipment", "Magic Items", "Classes", "Species", "Backgrounds", "Feats");

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theCategoryNavigatorIsStableAndComplete() throws Exception {
        String list = read("library/list.html");
        for (String category : CATEGORIES) {
            assertThat(list).as("navigator entry %s", category)
                    .contains("data-library-category=\"" + category + "\"");
        }
    }

    @Test
    void thePageTitleMatchesTheActiveCategory() throws Exception {
        assertThat(read("library/list.html"))
                .as("the title must not be a generic 'Library'")
                .contains("title=${activeCategory}");
    }

    @Test
    void searchAndFiltersStayVisibleWhileBrowsing() throws Exception {
        assertThat(read("library/list.html"))
                .contains("_toolbar :: toolbar")
                .contains("data-filter=");
    }

    @Test
    void theCreationActionNamesTheContentType() throws Exception {
        String list = read("library/list.html");
        assertThat(list).doesNotContain("New Homebrew");
        assertThat(list).contains("'New ' + ${activeCategorySingular}");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

- `list.html` → index archetype. The page header title binds to `activeCategory`; the primary
  action label binds to `'New ' + activeCategorySingular`.
- Add a local category navigator above the toolbar: one horizontal tab strip with
  `data-library-category` per category and `aria-current="page"` on the active one. Selection
  uses a gold indicator plus high-contrast text, never a gold-filled pill.
- Add `activeCategory` and `activeCategorySingular` to the controller model for every category
  route. No service or repository change.

```bash
./mvnw -Dtest='LibraryWorkspaceLayoutTest,LibraryControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/library src/main/java/dev/hendrikhoemberg/dmhelper/library/web src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryWorkspaceLayoutTest.java
git commit -m "feat: turn Library into a reference workspace with a category navigator"
```

## Task 35: Library card alignment, clamping, and detail

**Files:**
- Modify: every `src/main/resources/templates/library/_*-card.html`
- Modify: `src/main/resources/templates/library/_scope-badge.html`,
  `library/_provenance-fields.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryCardContractTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.9: cards show scan-critical information and align across categories. */
class LibraryCardContractTest {

    private static final List<String> CARDS = List.of(
            "_card.html", "_spell-card.html", "_condition-card.html", "_rule-card.html",
            "_equipment-card.html", "_magic-item-card.html", "_class-card.html",
            "_species-card.html", "_background-card.html", "_feat-card.html");

    private static String read(String card) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/library").resolve(card));
    }

    @Test
    void everyCardUsesTheSharedScanGrid() throws Exception {
        for (String card : CARDS) {
            assertThat(read(card)).as(card).contains("class=\"ref-card");
        }
    }

    @Test
    void repeatedFieldsUseTheSameSlotAcrossCategories() throws Exception {
        for (String card : CARDS) {
            assertThat(read(card)).as("%s must mark its scan fields", card)
                    .contains("data-ref-field");
        }
    }

    @Test
    void longDescriptionsAreClamped() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        assertThat(css).contains(".ref-card__summary").contains("-webkit-line-clamp");
    }

    @Test
    void provenanceIsPresentButSecondary() throws Exception {
        var css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        assertThat(css).contains(".ref-card__provenance");
        assertThat(read("_scope-badge.html")).contains("_badge :: badge");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

- Give every card the same `.ref-card` grid: title row, one aligned metadata row of
  `data-ref-field` slots (`cr`, `level`, `school`, `rarity`, `category`, `source`), a clamped
  `.ref-card__summary`, and a `.ref-card__provenance` line in `--text-tertiary`.
- Clamp with `display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;`.
- The full description opens in the existing detail route, or in a side sheet on the index
  where the category already has one.
- `_scope-badge.html` moves onto the shared badge.

```bash
./mvnw -Dtest='LibraryCardContractTest,LibraryWorkspaceLayoutTest,LibraryControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/library src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryCardContractTest.java
git commit -m "feat: align library cards on one scan grid"
```

## Task 36: Tables, traps, and hazards

**Files:**
- Modify: `src/main/resources/templates/rollable-table/list.html`, `detail.html`, `form.html`,
  `_roll-panel.html`
- Modify: `src/main/resources/templates/threat/list.html`, `detail.html`, `form.html`,
  `_mechanics-card.html`, `_provenance.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/ReferenceSiblingLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.9: shared reference language, own destinations and renderers. */
class ReferenceSiblingLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void theIndexesShareTheReferenceLanguage() throws Exception {
        for (String template : List.of("rollable-table/list.html", "threat/list.html")) {
            assertThat(read(template)).as(template)
                    .contains("archetype='index'")
                    .contains("_toolbar :: toolbar")
                    .contains("class=\"ref-card");
        }
    }

    @Test
    void theTaskSpecificRenderersSurvive() throws Exception {
        assertThat(read("rollable-table/_roll-panel.html")).contains("data-roll");
        assertThat(read("threat/_mechanics-card.html")).contains("data-threat-mechanics");
    }

    @Test
    void trapsAndHazardsKeepDistinctTopLevelDestinations() throws Exception {
        String rail = Files.readString(
                Path.of("src/main/resources/templates/fragments/_rail.html"));
        assertThat(rail).contains("/library/traps").contains("/library/hazards");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

```bash
./mvnw -Dtest='ReferenceSiblingLayoutTest,RollableTableControllerTest,ThreatControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/rollable-table src/main/resources/templates/threat src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/ReferenceSiblingLayoutTest.java
git commit -m "feat: bring tables, traps, and hazards into the reference language"
```

## Task 37: Stage 5 gate

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReferenceWorkspaceRenderGateTest.java`

- [ ] **Step 1: Write the gate**

Copy the structure of `NarrativePreparationRenderGateTest` with
`SHOTS = Path.of("target/ui-redesign/reference")` and these tests:

```java
    @Test
    void everyLibraryCategoryRendersWithItsOwnTitleAndTheSameCardGrid() {
        for (String category : java.util.List.of("", "/spells", "/conditions", "/rules",
                "/equipment", "/magic-items", "/classes", "/species", "/backgrounds", "/feats")) {
            open("/library" + category);
            String title = page.locator("h1").innerText();
            assertThat(title).as("title for %s", category).isNotEqualTo("Library");
            assertThat(page.locator("[data-library-category][aria-current='page']").count())
                    .as("exactly one active category for %s", category).isEqualTo(1);
            assertThat(page.locator(".ref-card").count())
                    .as("cards render for %s", category).isGreaterThan(0);
        }
    }

    @Test
    void referenceCardsAlignTheirMetadataRow() {
        open("/library/spells");
        Object misaligned = page.evaluate("""
                () => {
                  const rows = [...document.querySelectorAll('.ref-card__meta')];
                  if (rows.length < 2) return 0;
                  const tops = rows.map(r => Math.round(
                      r.getBoundingClientRect().top - r.closest('.ref-card').getBoundingClientRect().top));
                  return new Set(tops).size - 1;
                }
                """);
        assertThat(((Number) misaligned).intValue())
                .as("metadata rows must sit at the same offset in every card").isZero();
    }

    @Test
    void captureTheReferenceReviewSet() {
        java.util.List<String[]> shots = java.util.List.of(
                new String[]{"library-monsters", "/library"},
                new String[]{"library-spells", "/library/spells"},
                new String[]{"library-magic-items", "/library/magic-items"},
                new String[]{"tables", "/library/tables"},
                new String[]{"traps", "/library/traps"},
                new String[]{"hazards", "/library/hazards"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
```

Adjust the category paths to the routes `LibraryController` actually maps.

- [ ] **Step 2: Run the Stage 5 gate**

```bash
./mvnw -Dtest='LibraryWorkspaceLayoutTest,LibraryCardContractTest,ReferenceSiblingLayoutTest,ReferenceWorkspaceRenderGateTest' test
./mvnw test
```

- [ ] **Step 3: Review `target/ui-redesign/reference/` and commit**

```bash
git add -A
git commit -m "feat: complete the reference workspace stage"
```

---

# Stage 6: Map editor

**Working product after this stage:** the map editor is a viewport-owned workspace with a top
command bar, a compact tool rail, a large canvas, and a contextual inspector. Every existing
layer, token, pin, calibration, import, crop, rotation, undo/redo, keyboard, and export
behavior is preserved, as is the 60fps performance target.

## Task 38: Split the editor into command bar, tool rail, canvas, and inspector

**Files:**
- Create: `src/main/resources/static/css/map-editor.css`
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/main/resources/templates/fragments/head.html` (load the new sheet on the
  editor page only, via a `th:if` on an `editorPage` model flag)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorLayoutContractTest.java`

**Interfaces:**
- Produces: `.mapedit`, `.mapedit__commandbar`, `.mapedit__rail`, `.mapedit__canvas`,
  `.mapedit__inspector`, `.mapedit__status`; `data-tool`, `data-inspector-section`.

- [ ] **Step 1: Write the failing layout contract**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 13. */
class MapEditorLayoutContractTest {

    private static final Path EDITOR = Path.of("src/main/resources/templates/maps/editor.html");

    private static String markup() throws Exception {
        return Files.readString(EDITOR);
    }

    @Test
    void theEditorHasFourNamedRegions() throws Exception {
        String editor = markup();
        for (String region : List.of("mapedit__commandbar", "mapedit__rail",
                "mapedit__canvas", "mapedit__inspector")) {
            assertThat(editor).as(region).contains(region);
        }
    }

    @Test
    void theCommandBarCarriesOnlyItsApprovedControls() throws Exception {
        var document = Jsoup.parse(markup(), "", Parser.xmlParser());
        var bar = document.selectFirst(".mapedit__commandbar");
        assertThat(bar).isNotNull();
        String text = bar.outerHtml();
        for (String required : List.of("data-command=\"back\"", "data-command=\"map-name\"",
                "data-command=\"undo\"", "data-command=\"redo\"", "data-command=\"save-state\"",
                "data-command=\"import\"", "data-command=\"export\"", "data-command=\"help\"")) {
            assertThat(text).as(required).contains(required);
        }
        assertThat(bar.select("[data-tool]"))
                .as("drawing properties belong to the inspector, not the command bar")
                .isEmpty();
    }

    @Test
    void everyToolLivesInTheToolRailWithALabelAndShortcut() throws Exception {
        var document = Jsoup.parse(markup(), "", Parser.xmlParser());
        var rail = document.selectFirst(".mapedit__rail");
        assertThat(rail).isNotNull();
        var tools = rail.select("[data-tool]");
        assertThat(tools.size()).as("tool count").isGreaterThanOrEqualTo(11);
        for (var tool : tools) {
            assertThat(tool.hasAttr("aria-label")).as("label on %s", tool.attr("data-tool")).isTrue();
            assertThat(tool.hasAttr("aria-pressed")).as("selected state on %s", tool.attr("data-tool")).isTrue();
            assertThat(tool.hasAttr("data-shortcut")).as("shortcut hint on %s", tool.attr("data-tool")).isTrue();
        }
    }

    @Test
    void theInspectorOwnsEverySixSection() throws Exception {
        String editor = markup();
        for (String section : List.of("tool", "palette", "selection", "layers", "map", "pins")) {
            assertThat(editor).as("inspector section %s", section)
                    .contains("data-inspector-section=\"" + section + "\"");
        }
    }

    @Test
    void theEditorOwnsTheViewport() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/map-editor.css"));
        assertThat(css).contains(".mapedit").contains("grid-template-areas");
        assertThat(markup()).contains("archetype='editor'");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='MapEditorLayoutContractTest' test
```

- [ ] **Step 3: Restructure `editor.html`**

Replace `editor-container` / `editor-toolbar` / `editor-body` / `editor-sidebar` /
`editor-statusbar` with:

```html
<div id="page-content" class="mapedit">
    <div class="mapedit__commandbar">
        <a class="btn btn-ghost" data-command="back"
           th:href="@{/campaigns/{id}/maps(id=${campaignId})}">
            <th:block th:replace="~{common/_icon :: icon(name='chevron-right')}"></th:block>
            Back to Maps</a>
        <span class="mapedit__name" data-command="map-name" th:text="${map.name}">Map</span>
        <button type="button" class="icon-btn" data-command="undo" aria-label="Undo"
                title="Undo (Ctrl+Z)">
            <th:block th:replace="~{common/_icon :: icon(name='undo')}"></th:block></button>
        <button type="button" class="icon-btn" data-command="redo" aria-label="Redo"
                title="Redo (Ctrl+Shift+Z)">
            <th:block th:replace="~{common/_icon :: icon(name='redo')}"></th:block></button>
        <span class="mapedit__spacer"></span>
        <th:block th:replace="~{fragments/_status :: save-status(id='mapSaveStatus')}"></th:block>
        <button type="button" class="btn" data-command="import">Import</button>
        <button type="button" class="btn" data-command="export">Export</button>
        <button type="button" class="icon-btn" data-command="help" aria-label="Keyboard help">
            <th:block th:replace="~{common/_icon :: icon(name='help')}"></th:block></button>
    </div>

    <nav class="mapedit__rail" aria-label="Tools">
        <!-- one button per tool; group with <div class="mapedit__toolgroup"> -->
        <button type="button" class="tool-btn" data-tool="brush" data-shortcut="B"
                aria-label="Brush" aria-pressed="false" title="Brush (B)">
            <th:block th:replace="~{common/_icon :: icon(name='brush')}"></th:block></button>
        <!-- fill, select, rect, circle, line, polygon, room, door, corridor, region -->
    </nav>

    <div class="mapedit__canvas" id="editorCanvasWrap"><!-- existing canvas markup --></div>

    <aside class="mapedit__inspector" aria-label="Inspector">
        <section data-inspector-section="tool"><!-- active tool options --></section>
        <section data-inspector-section="palette"><!-- terrain/color palette --></section>
        <section data-inspector-section="selection"><!-- selected object properties --></section>
        <section data-inspector-section="layers"><!-- visibility, lock, ordering --></section>
        <section data-inspector-section="map"><!-- size, grid, background --></section>
        <section data-inspector-section="pins"><!-- threat pins --></section>
    </aside>

    <div class="mapedit__status"><!-- existing status readout --></div>
</div>
```

Move every existing control into exactly one region. Nothing is deleted; the terrain panel,
resize dialog, background section, shape properties, and threat-pin list all become inspector
sections. Keep every `id`, `x-data`, `data-map-control`, and `data-image-control` hook so
`map-editor.js` and its tests keep working.

- [ ] **Step 4: Write `map-editor.css`**

```css
.mapedit {
  display: grid;
  grid-template-columns: auto 1fr auto;
  grid-template-rows: auto 1fr auto;
  grid-template-areas:
    "commandbar commandbar commandbar"
    "rail canvas inspector"
    "status status status";
  height: 100%;
  min-height: 0;
  background: var(--surface-canvas);
}
.mapedit__commandbar {
  grid-area: commandbar;
  display: flex; align-items: center; gap: var(--space-xs);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--surface-navigation);
  border-bottom: 1px solid var(--border-subtle);
}
.mapedit__spacer { flex: 1; }
.mapedit__name { font-weight: 600; color: var(--text-primary); }
.mapedit__rail {
  grid-area: rail;
  width: 56px;
  display: flex; flex-direction: column; gap: var(--space-2xs);
  padding: var(--space-2xs);
  background: var(--surface-navigation);
  border-right: 1px solid var(--border-subtle);
  overflow-y: auto;
}
.mapedit__toolgroup { display: contents; }
.mapedit__toolgroup + .mapedit__toolgroup::before {
  content: ""; height: 1px; background: var(--border-subtle); margin-block: var(--space-2xs);
}
.tool-btn {
  display: grid; place-items: center; width: 40px; height: 40px;
  background: transparent; border: 1px solid transparent; border-radius: var(--radius);
  color: var(--text-secondary); cursor: pointer;
}
.tool-btn:hover { color: var(--text-primary); background: var(--neutral-hover-surface); }
.tool-btn[aria-pressed="true"] {
  color: var(--text-primary);
  background: var(--selection-surface);
  border-color: var(--selection-accent);
}
.mapedit__canvas { grid-area: canvas; min-width: 0; min-height: 0; overflow: hidden; position: relative; }
.mapedit__inspector {
  grid-area: inspector;
  width: 320px; min-width: 0;
  padding: var(--space-sm);
  background: var(--surface-panel);
  border-left: 1px solid var(--border-subtle);
  overflow-y: auto;
  display: flex; flex-direction: column; gap: var(--space-sm);
}
.mapedit__status {
  grid-area: status;
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--surface-navigation);
  border-top: 1px solid var(--border-subtle);
  color: var(--text-tertiary); font-size: var(--text-sm);
}
/* At the minimum viewport the inspector narrows before the canvas does. */
@media (max-width: 1439px) {
  .mapedit__inspector { width: 280px; }
}
```

- [ ] **Step 5: Run it and watch it pass**

```bash
./mvnw -Dtest='MapEditorLayoutContractTest,GameMapControllerTest,MapEditorBrowserTest' test
```

`MapEditorBrowserTest` is the existing behavior gate. If it fails, a control was moved
without its hook — restore the hook, do not change the test.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/maps/editor.html src/main/resources/static/css/map-editor.css src/main/resources/templates/fragments/head.html src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorLayoutContractTest.java
git commit -m "feat: split the map editor into command bar, tool rail, canvas, and inspector"
```

## Task 39: Make the inspector contextual

**Files:**
- Create: `src/main/resources/static/js/map-inspector.js`
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/main/resources/static/css/map-editor.css`

**Interfaces:**
- Produces: `window.mapInspector.setContext(key)` where key is `tool`, `selection`, `layers`,
  or `map`.

- [ ] **Step 1: Write the failing browser assertion**

Add to `MapEditorBrowserTest`:

```java
    @Test
    void onlySectionsRelevantToTheActiveToolArePromoted() {
        openEditor();
        page.click("[data-tool='brush']");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("secondary");

        page.click("[data-tool='select']");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("secondary");
    }

    @Test
    void everySectionRemainsReachableEvenWhenSecondary() {
        openEditor();
        page.click("[data-tool='brush']");
        for (String section : java.util.List.of("tool", "palette", "selection", "layers",
                "map", "pins")) {
            assertThat(page.isVisible("[data-inspector-section='" + section + "']"))
                    .as("section %s stays reachable", section).isTrue();
        }
    }
```

Reuse whatever helper `MapEditorBrowserTest` already has for opening the editor; if it is
named differently from `openEditor()`, use that name.

- [ ] **Step 2: Run red, implement, run green**

`map-inspector.js`:

```js
(function () {
    const RELEVANCE = {
        brush: 'palette', fill: 'palette', room: 'palette', corridor: 'palette',
        region: 'palette', door: 'palette',
        rect: 'tool', circle: 'tool', line: 'tool', polygon: 'tool',
        select: 'selection'
    };

    function setContext(primary) {
        document.querySelectorAll('[data-inspector-section]').forEach(section => {
            const key = section.dataset.inspectorSection;
            section.dataset.relevance = key === primary ? 'primary' : 'secondary';
        });
    }

    document.addEventListener('click', event => {
        const tool = event.target.closest('[data-tool]');
        if (!tool) return;
        document.querySelectorAll('[data-tool]').forEach(
            button => button.setAttribute('aria-pressed', String(button === tool)));
        setContext(RELEVANCE[tool.dataset.tool] ?? 'tool');
    });

    window.mapInspector = { setContext };
    document.addEventListener('DOMContentLoaded', () => setContext('palette'));
})();
```

CSS: a `primary` section shows expanded; a `secondary` section collapses to its heading with
a disclosure the DM can open. Never `display: none` — every section stays reachable.

```css
.mapedit__inspector [data-relevance="secondary"] > :not(h2) { display: none; }
.mapedit__inspector [data-relevance="secondary"][open] > * { display: revert; }
.mapedit__inspector [data-relevance="primary"] { order: -1; }
```

Render each section as a `<details>` with a `<summary>` heading so the collapse is native and
keyboard-operable, and set `open` on the primary one from `map-inspector.js`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/map-inspector.js src/main/resources/templates/maps/editor.html src/main/resources/static/css/map-editor.css src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: make the map inspector contextual to the active tool"
```

## Task 40: Canvas legibility and quiet save state

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`, `battle-map.js`
- Modify: `src/main/resources/static/css/map-editor.css`

- [ ] **Step 1: Write the failing browser assertions**

Add to `MapEditorBrowserTest`:

```java
    @Test
    void theCanvasReceivesTheMajorityOfTheViewport() {
        page.setViewportSize(1280, 720);
        openEditor();
        double canvas = ((Number) page.evaluate(
                "() => document.querySelector('.mapedit__canvas').getBoundingClientRect().width"))
                .doubleValue();
        assertThat(canvas / 1280.0).as("canvas share of width").isGreaterThan(0.6);
    }

    @Test
    void gridAndSelectionStayLegibleOverImportedImagery() {
        openEditor();
        Object contrastPair = page.evaluate("""
                () => {
                  const s = getComputedStyle(document.documentElement);
                  return [s.getPropertyValue('--map-grid-line').trim(),
                          s.getPropertyValue('--map-selection').trim()];
                }
                """);
        assertThat((java.util.List<?>) contrastPair).doesNotContain("");
    }

    @Test
    void autosaveStateIsVisibleButQuiet() {
        openEditor();
        assertThat(page.isVisible("#mapSaveStatus")).isTrue();
        String color = (String) page.evaluate(
                "() => getComputedStyle(document.querySelector('#mapSaveStatus')).color");
        assertThat(color).as("save state must not use the primary text role")
                .isNotEqualTo("rgb(238, 232, 220)");
    }
```

- [ ] **Step 2: Run red, implement, run green**

- Add `--map-grid-line` and `--map-selection` to `tokens.css` with values that keep 3:1
  against both `--surface-canvas` and a mid-grey imported image; draw the grid with a
  1px dark stroke plus a 1px light stroke offset by 1px so it reads on any imagery.
- Point the editor's autosave writes at `#mapSaveStatus`, setting
  `data-save-status` to `saving`, `saved`, or `error`.
- Keep every existing render path; do not touch the 60fps draw loop's structure.

```bash
./mvnw -Dtest='MapEditorBrowserTest,MapEditorLayoutContractTest,RawVisualValueContractTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/map src/main/resources/static/css src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: keep the map canvas legible and its save state quiet"
```

## Task 41: Stage 6 gate

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/MapEditorRenderGateTest.java`

- [ ] **Step 1: Write the gate**

Copy the `NarrativePreparationRenderGateTest` structure with
`SHOTS = Path.of("target/ui-redesign/map-editor")` and:

```java
    @Test
    void nothingEssentialClipsAtTheMinimumViewport() {
        for (int[] viewport : new int[][]{{1280, 720}, {1440, 900}, {1920, 1080}}) {
            page.setViewportSize(viewport[0], viewport[1]);
            open("/campaigns/" + seeded.campaignId() + "/maps/" + seeded.playableMapId() + "/edit");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> geometry = (java.util.Map<String, Object>) page.evaluate("""
                    () => {
                      const clipped = [...document.querySelectorAll(
                          '.mapedit__commandbar [data-command], .mapedit__rail [data-tool]')]
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.width === 0 || r.height === 0
                              || r.right > innerWidth + 1 || r.bottom > innerHeight + 1;
                        }).map(el => el.dataset.command ?? el.dataset.tool);
                      return {
                        clipped,
                        docOverflow: document.documentElement.scrollHeight
                                   - document.documentElement.clientHeight
                      };
                    }
                    """);
            assertThat((java.util.List<?>) geometry.get("clipped"))
                    .as("clipped controls at %dx%d", viewport[0], viewport[1]).isEmpty();
            assertThat(((Number) geometry.get("docOverflow")).intValue())
                    .as("document scroll at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void captureTheEditorReviewSet() {
        for (int[] viewport : new int[][]{{1280, 720}, {1920, 1080}}) {
            page.setViewportSize(viewport[0], viewport[1]);
            open("/campaigns/" + seeded.campaignId() + "/maps/" + seeded.playableMapId() + "/edit");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve("editor-" + viewport[0] + ".png")));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(2);
    }
```

Use the route `GameMapController` actually maps for the editor.

- [ ] **Step 2: Run the Stage 6 gate**

```bash
./mvnw -Dtest='MapEditorLayoutContractTest,MapEditorBrowserTest,MapEditorRenderGateTest' test
./mvnw test
```

- [ ] **Step 3: Review `target/ui-redesign/map-editor/` and commit**

```bash
git add -A
git commit -m "feat: complete the map editor stage"
```

---

# Stage 7: Session cockpit

**Working product after this stage:** the cockpit keeps its four-zone workbench, custom
presets, Focus/Return, locked default, Edit layout mode, keyboard arrangement, and persisted
device state, while every built-in preset is useful without layout editing and the whole
surface fits 1280x720.

Preset changes stay manual. Current scene, active encounter, and workspace map stay
independent functional state. No preset is removed.

## Task 42: Cockpit command bar

**Files:**
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/css/cockpit.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitCommandBarContractTest.java`

- [ ] **Step 1: Write the failing contract**

```java
package dev.hendrikhoemberg.dmhelper.session.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 12.2. */
class CockpitCommandBarContractTest {

    private static String cockpit() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
    }

    @Test
    void theCommandBarCarriesTheEightApprovedSlots() throws Exception {
        String markup = cockpit();
        for (String slot : List.of("campaign", "session-state", "in-game-date", "active-preset",
                "search", "dice", "session", "more")) {
            assertThat(markup).as("command bar slot %s", slot)
                    .contains("data-command-slot=\"" + slot + "\"");
        }
    }

    @Test
    void rareActionsLiveBehindTheirMenus() throws Exception {
        var bar = Jsoup.parse(cockpit(), "", Parser.xmlParser())
                .selectFirst("[data-cockpit-commandbar]");
        assertThat(bar).isNotNull();
        for (String rare : List.of("Edit layout", "Reset layout", "Provider settings")) {
            assertThat(bar.select("[data-command-slot='more']").outerHtml())
                    .as("%s belongs behind More", rare).contains(rare);
        }
    }

    @Test
    void layoutChromeIsAbsentWhileLocked() throws Exception {
        assertThat(cockpit())
                .as("spec 12.5: no layout editing chrome while locked")
                .contains("th:if=\"${layoutEditing}\"");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

Rebuild the cockpit's top bar as `[data-cockpit-commandbar]` with the eight
`data-command-slot` regions in spec order. Move Edit layout, Reset layout, provider settings,
and any other rare administrative action into the `more` popover. Guard every layout-editing
control with `th:if="${layoutEditing}"`.

```bash
./mvnw -Dtest='CockpitCommandBarContractTest,SessionControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/session/cockpit.html src/main/resources/static/css/cockpit.css src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitCommandBarContractTest.java
git commit -m "feat: rebuild the cockpit command bar on the approved slots"
```

## Task 43: Redesign the four built-in presets

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`

**Interfaces:**
- Consumes: `CockpitLayoutDocument.SplitRatios(left, primary, right, bottom)`.
- Constraint from `CockpitLayoutValidator`: `left + primary + right == 1.0` (±0.001),
  `0.50 <= primary <= 0.65`, `0.16 <= bottom <= 0.40`, `left > 0`, `right > 0`.

- [ ] **Step 1: Write the failing preset composition test**

Add to `CockpitBuiltInPresetCatalogTest`:

```java
    private static java.util.List<String> zone(CockpitBuiltInPresetCatalog.BuiltInPreset preset,
                                               CockpitZone zone) {
        return preset.layout().zones().get(zone).moduleKeys();
    }

    @Test
    void explorationLeadsWithTheStoryWorkflow() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:exploration");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("story");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("session-plan");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("party");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "audio", "reference");
        assertThat(preset.layout().zones().get(CockpitZone.BOTTOM_UTILITY).collapsed())
                .as("quick notes are the default bottom module, not a collapsed drawer")
                .isFalse();
    }

    @Test
    void combatLeadsWithTheMapAndKeepsTheEncounterProminent() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:combat");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("map");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("encounter");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("story", "party");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "reference", "audio", "session-log");
        assertThat(preset.layout().compactModuleKeys()).contains("story", "party");
        assertThat(preset.layout().compactModuleKeys())
                .as("the encounter is prominent support, never compact").doesNotContain("encounter");
    }

    @Test
    void theatreOfMindLeadsWithTheEncounterAndExpandsTheStory() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:theatre-of-mind");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("encounter");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("story");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("party", "reference");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "audio", "session-log");
        assertThat(preset.layout().compactModuleKeys()).doesNotContain("story");
    }

    @Test
    void sessionReviewLeadsWithTheLogDraft() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:session-review");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("session-log");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("session-plan");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("quick-notes", "party");
    }

    @Test
    void everyPresetSatisfiesTheLayoutValidator() {
        var validator = new CockpitLayoutValidator();
        for (var preset : new CockpitBuiltInPresetCatalog().all()) {
            assertThat(validator.validate(preset.layout()))
                    .as("validation problems for %s", preset.key())
                    .isEmpty();
        }
    }

    @Test
    void noPresetLeavesTheBottomZoneEmptyAndUncollapsed() {
        for (var preset : new CockpitBuiltInPresetCatalog().all()) {
            var bottom = preset.layout().zones().get(CockpitZone.BOTTOM_UTILITY);
            assertThat(bottom.moduleKeys().isEmpty() && !bottom.collapsed())
                    .as("%s wastes bottom space", preset.key()).isFalse();
        }
    }
```

Adapt `validator.validate(...)` to the validator's actual method name and return type.

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='CockpitBuiltInPresetCatalogTest' test
```

- [ ] **Step 3: Rewrite the catalog with per-preset ratios**

```java
    private static final List<BuiltInPreset> PRESETS = List.of(
            preset("builtin:exploration", "Exploration",
                    new CockpitLayoutDocument.SplitRatios(0.22, 0.54, 0.24, 0.16),
                    zone("story"), zone("session-plan"), zone("party"),
                    zone("quick-notes", "audio", "reference"),
                    Set.of("session-plan", "party", "audio", "reference")),
            preset("builtin:combat", "Combat",
                    new CockpitLayoutDocument.SplitRatios(0.18, 0.52, 0.30, 0.20),
                    zone("map"), zone("story", "party"), zone("encounter"),
                    zone("quick-notes", "reference", "audio", "session-log"),
                    Set.of("story", "party", "quick-notes", "reference", "audio", "session-log")),
            preset("builtin:theatre-of-mind", "Theatre of Mind",
                    new CockpitLayoutDocument.SplitRatios(0.19, 0.50, 0.31, 0.16),
                    zone("encounter"), zone("party", "reference"), zone("story"),
                    zone("quick-notes", "audio", "session-log"),
                    Set.of("party", "reference", "quick-notes", "audio", "session-log")),
            preset("builtin:session-review", "Session Review",
                    new CockpitLayoutDocument.SplitRatios(0.22, 0.56, 0.22, 0.16),
                    zone("session-log"), zone("session-plan"), zone("quick-notes", "party"),
                    collapsedZone(),
                    Set.of("session-plan", "quick-notes", "party"))
    );
```

Change `preset(...)` to take the ratios as its third parameter and pass them into the
`CockpitLayoutDocument` instead of `DEFAULT_RATIOS`; delete `DEFAULT_RATIOS`. Keep the
existing `zone(...)`/`collapsedZone(...)` helpers and the `BuiltInPreset` record unchanged.

- [ ] **Step 4: Run the whole cockpit layout package**

```bash
./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.session.layout.*,CockpitLayoutApiControllerTest' test
```

Expected: PASS. Saved custom presets are untouched — this changes only the built-in
definitions, and `CockpitLayoutResolver` already falls back to Exploration for unknown keys.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/layout src/test/java/dev/hendrikhoemberg/dmhelper/session/layout
git commit -m "feat: redesign the four built-in cockpit presets around one primary task"
```

## Task 44: Module hierarchy and runtime emphasis

**Files:**
- Modify: `src/main/resources/templates/session/_cockpit-module-shell.html`
- Modify: `src/main/resources/templates/session/modules/*.html`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeEmphasisTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.session.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec sections 12.4 and 12.5. */
class CockpitRuntimeEmphasisTest {

    private static final Path MODULES = Path.of("src/main/resources/templates/session/modules");

    private static String read(String name) throws Exception {
        return Files.readString(MODULES.resolve(name));
    }

    @Test
    void everyModuleDeclaresItsRoleSoZonesCanRankThem() throws Exception {
        try (var files = Files.list(MODULES)) {
            for (Path module : files.toList()) {
                assertThat(Files.readString(module)).as(module.getFileName().toString())
                        .contains("data-module-role");
            }
        }
    }

    @Test
    void theStoryModuleCarriesTheWholeCurrentSceneWorkflow() throws Exception {
        String story = read("_story.html");
        for (String part : List.of("data-story-scene", "data-story-read-aloud",
                "data-story-transitions", "data-story-references")) {
            assertThat(story).as(part).contains(part);
        }
    }

    @Test
    void theEncounterModuleIsFullyOperableWithoutAMap() throws Exception {
        String encounter = read("_encounter.html");
        assertThat(encounter).contains("data-encounter-turn").contains("data-encounter-initiative");
        assertThat(encounter)
                .as("theatre of mind: the tracker must not require the map module")
                .doesNotContain("data-requires-map");
    }

    @Test
    void runtimeStateRolesAreDistinctAndSubstantial() throws Exception {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/cockpit-modules.css"));
        for (String role : List.of("[data-runtime-state=\"current-scene\"]",
                "[data-runtime-state=\"active-encounter\"]",
                "[data-runtime-state=\"map-mismatch\"]",
                "[data-runtime-state=\"active-turn\"]",
                "[data-runtime-state=\"session\"]",
                "[data-runtime-state=\"connection\"]")) {
            assertThat(css).as("style for %s", role).contains(role);
        }
    }

    @Test
    void emptyModulesOfferANextAction() throws Exception {
        assertThat(Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html")))
                .contains("_states :: empty");
    }
}
```

- [ ] **Step 2: Run red, implement, run green**

- Every module template declares `data-module-role` of `primary`, `support`, or `utility`;
  `_cockpit-module-shell.html` uses it to pick heading size, padding, and density.
- `_story.html` gains the complete current-scene workflow: scene title and state, read-aloud,
  transitions, and linked references — enough to fill the primary zone rather than one short
  card above empty canvas.
- `_encounter.html` renders turn order and unresolved initiative inline
  (`data-encounter-turn`, `data-encounter-initiative`) with no dependency on the map module.
- `cockpit-modules.css` gives each `data-runtime-state` a distinct treatment combining at
  least two signals; the active combatant gets a substantial panel or row, not a hairline.
- The module shell renders `~{fragments/_states :: empty}` with a next action when a module
  has no content.

```bash
./mvnw -Dtest='CockpitRuntimeEmphasisTest,CockpitRuntimeModuleControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/session src/main/resources/static/css/cockpit-modules.css src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeEmphasisTest.java
git commit -m "feat: rank cockpit modules and make runtime state substantial"
```

## Task 45: Laptop fit with container queries

**Files:**
- Modify: `src/main/resources/static/css/cockpit-layout.css`, `cockpit-modules.css`
- Modify: `src/main/resources/static/js/cockpit-layout.js` (support-zone tabbing only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/CockpitLaptopFitGateTest.java`

- [ ] **Step 1: Write the failing gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same @SpringBootTest / @ActiveProfiles("playwright") scaffolding as
// ViewportAccessibilityGateTest, including lifecycleService.start(...) in @BeforeAll

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}};

    @Test
    void everyBuiltInPresetFitsWithoutClippingOrDocumentScroll() {
        for (String preset : java.util.List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            for (int[] viewport : VIEWPORTS) {
                openCockpitWithPreset(preset, viewport[0], viewport[1]);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> report =
                        (java.util.Map<String, Object>) page.evaluate("""
                        () => ({
                          docOverflow: document.documentElement.scrollWidth
                                     - document.documentElement.clientWidth,
                          clipped: [...document.querySelectorAll('[data-cockpit-commandbar] button,'
                                   + ' [data-cockpit-commandbar] a')]
                            .filter(el => {
                              const r = el.getBoundingClientRect();
                              return r.width === 0 || r.right > innerWidth + 1;
                            }).length,
                          scrollingModules: [...document.querySelectorAll('.cockpit-module')]
                            .filter(m => getComputedStyle(m).overflow === 'visible').length
                        })
                        """);
                assertThat(((Number) report.get("docOverflow")).intValue())
                        .as("%s at %dx%d document overflow", preset, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(1);
                assertThat(((Number) report.get("clipped")).intValue())
                        .as("%s at %dx%d clipped command bar controls", preset, viewport[0], viewport[1])
                        .isZero();
                assertThat(((Number) report.get("scrollingModules")).intValue())
                        .as("%s at %dx%d modules must own their scrolling", preset, viewport[0], viewport[1])
                        .isZero();
            }
        }
    }

    @Test
    void noRequiredTextWrapsOneWordPerLine() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        Object worst = page.evaluate("""
                () => {
                  const els = [...document.querySelectorAll(
                      '.cockpit-module h2, .cockpit-module h3, .cockpit-module button')];
                  return Math.max(0, ...els.map(el => {
                    const words = el.textContent.trim().split(/\\s+/).length;
                    if (words < 2) return 0;
                    const lines = Math.round(el.getBoundingClientRect().height
                        / parseFloat(getComputedStyle(el).lineHeight));
                    return lines >= words ? words : 0;
                  }));
                }
                """);
        assertThat(((Number) worst).intValue()).as("one-word-per-line wrapping").isZero();
    }

    @Test
    void supportModulesBecomeTabsWhenTheZoneIsConstrained() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        assertThat(page.locator("[data-zone='LEFT_SUPPORT'] [role='tab']").count())
                .as("story and party share the constrained left zone as tabs")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void primaryRuntimeActionsStayVisibleWithoutScrolling() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        Object hidden = page.evaluate("""
                () => [...document.querySelectorAll('[data-runtime-action]')]
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.bottom > innerHeight + 1 || r.width === 0;
                        }).length
                """);
        assertThat(((Number) hidden).intValue()).isZero();
    }
```

Write `openCockpitWithPreset(String presetKey, int width, int height)` to set the viewport,
navigate to the session route, wait for `window.cockpitLayout?.mounted === true`, then apply
the preset through the existing preset control — the same manual path a DM uses. Do not add
an automatic preset switch.

- [ ] **Step 2: Run red, implement, run green**

- Give each module `container-type: inline-size` and move internal breakpoints to
  `@container` queries so a module adapts to its zone, not the viewport.
- Enforce zone minima in `cockpit-layout.css` before text clips; the validator's ratios set
  the target, the minima protect it.
- In `cockpit-layout.js`, when a support zone holds more than one module and its measured
  width is below the widest module's `minWidth`, render the modules as `role="tablist"` tabs
  within the zone. Preserve keyboard arrangement and Focus/Return.
- Mark primary runtime actions with `data-runtime-action` so the gate can find them.
- Every module keeps `overflow: auto`; the document never scrolls.

```bash
./mvnw -Dtest='CockpitLaptopFitGateTest,ViewportAccessibilityGateTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css src/main/resources/static/js/cockpit-layout.js src/test/java/dev/hendrikhoemberg/dmhelper/gate/CockpitLaptopFitGateTest.java
git commit -m "feat: fit every cockpit preset on a laptop with container queries"
```

## Task 46: Stage 7 gate

- [ ] **Step 1: Capture every preset in idle and active-session states**

Add to `CockpitLaptopFitGateTest`:

```java
    @Test
    void captureTheCockpitReviewSet() throws Exception {
        java.nio.file.Path shots = java.nio.file.Path.of("target/ui-redesign/cockpit");
        java.nio.file.Files.createDirectories(shots);
        for (String preset : java.util.List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            openCockpitWithPreset(preset, 1440, 900);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(shots.resolve(preset.replace(':', '-') + "-active.png")));
        }
        assertThat(shots.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(4);
    }
```

Run it once with an active session (the fixture already starts one in `@BeforeAll`) and once
with the session ended, saving the second set with an `-idle` suffix.

- [ ] **Step 2: Run the Stage 7 gate**

```bash
./mvnw -Dtest='CockpitCommandBarContractTest,CockpitBuiltInPresetCatalogTest,CockpitRuntimeEmphasisTest,CockpitLaptopFitGateTest,ViewportAccessibilityGateTest,DmManualCockpitAccuracyTest' test
./mvnw test
```

`DmManualCockpitAccuracyTest` documents the cockpit in the DM manual; update
`docs/dm-manual` where the preset compositions changed, then re-run it.

- [ ] **Step 3: Review `target/ui-redesign/cockpit/` and commit**

```bash
git add -A
git commit -m "feat: complete the session cockpit stage"
```

---

# Stage 8: Consistency and release gate

**Working product after this stage:** the redesign is complete. No page uses the former
brown-on-brown system, the compatibility layer is gone, and the accessibility, zoom, viewport,
keyboard, browser-health, and functional regression gates all pass.

## Task 47: Presentation surface and player-safe semantics

**Files:**
- Modify: `src/main/resources/templates/handout/_present-overlay.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/PresentationSurfaceTest.java`

Scope note: see "Scope reconciliation" at the top of this plan. This task redesigns the
presentation surface that exists — the full-viewport handout overlay — and keeps Shield
semantics consistent between it and the DM preview. It does not create a separate player
projection surface.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.handout.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.11, scoped to the presentation surface the product actually has. */
class PresentationSurfaceTest {

    private static String overlay() throws Exception {
        return Files.readString(
                Path.of("src/main/resources/templates/handout/_present-overlay.html"));
    }

    @Test
    void thePresentationCanvasCarriesNoDmChrome() throws Exception {
        String markup = overlay();
        for (String chrome : List.of("app-topbar", "rail", "page-header", "toolbar", "btn-danger")) {
            assertThat(markup).as("DM chrome %s must not reach the presentation surface", chrome)
                    .doesNotContain(chrome);
        }
    }

    @Test
    void thePresentedAssetReceivesTheViewportRatherThanACard() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        var rule = css.substring(css.indexOf(".handout-overlay"));
        assertThat(rule).contains("position: fixed").contains("inset: 0");
        assertThat(rule).doesNotContain("var(--surface-raised)");
    }

    @Test
    void unavailableAndErrorPresentationStatesAreExplicit() throws Exception {
        assertThat(overlay())
                .contains("data-presentation-state")
                .contains("_states :: unavailable");
    }

    @Test
    void shieldSemanticsMatchTheDmPreview() throws Exception {
        String card = Files.readString(
                Path.of("src/main/resources/templates/handout/_card.html"));
        assertThat(card).contains("tone='shield'");
        assertThat(overlay())
                .as("the overlay states its player-safe status the same way")
                .contains("data-handout-visibility");
    }
}
```

- [ ] **Step 2: Run red, implement, run green**

- The overlay becomes a fixed, inset-0 near-black canvas (`--surface-canvas`) with the asset
  centred and no card chrome, DM-only labels, or management controls.
- Add `data-presentation-state` with `ready`, `unavailable`, or `error`; render
  `~{fragments/_states :: unavailable}` when the asset cannot load, with a Retry that re-fetches
  the same route.
- Carry `data-handout-visibility` so the presented state matches the DM-side Shield badge.

```bash
./mvnw -Dtest='PresentationSurfaceTest,HandoutControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/handout src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/PresentationSurfaceTest.java
git commit -m "feat: give the presentation surface a clean canvas and explicit states"
```

## Task 48: Administration, About, and error pages

**Files:**
- Modify: `src/main/resources/templates/campaigns/settings.html`, `campaigns/new.html`,
  `campaigns/_import-dialog.html`
- Modify: `src/main/resources/templates/about.html`, `error.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/AdministrationLayoutTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 11.10. */
class AdministrationLayoutTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void campaignSettingsSeparateItsFourConcerns() throws Exception {
        String settings = read("campaigns/settings.html");
        for (String group : List.of("identity", "progression", "package", "danger")) {
            assertThat(settings).as("field group %s", group)
                    .contains("data-settings-group=\"" + group + "\"");
        }
        assertThat(settings).contains("archetype='form'");
    }

    @Test
    void importPreviewRanksErrorsWarningsContentsAndReadiness() throws Exception {
        String dialog = read("campaigns/_import-dialog.html");
        int errors = dialog.indexOf("data-import-section=\"errors\"");
        int warnings = dialog.indexOf("data-import-section=\"warnings\"");
        int contents = dialog.indexOf("data-import-section=\"contents\"");
        int readiness = dialog.indexOf("data-import-section=\"readiness\"");
        assertThat(List.of(errors, warnings, contents, readiness)).doesNotContain(-1);
        assertThat(errors).isLessThan(warnings);
        assertThat(warnings).isLessThan(contents);
        assertThat(contents).isLessThan(readiness);
    }

    @Test
    void destructiveCampaignActionsStateTheirConsequence() throws Exception {
        String settings = read("campaigns/settings.html");
        assertThat(settings).contains("data-settings-group=\"danger\"");
        assertThat(settings).contains("data-confirm-consequence");
    }

    @Test
    void aboutIsARestrainedReadingLayout() throws Exception {
        String about = read("about.html");
        assertThat(about).contains("archetype='detail'").contains("class=\"prose");
        assertThat(about).doesNotContain("data-table");
    }

    @Test
    void errorPagesNameTheFailedActionAndKeepTheCorrelationIdSecondary() throws Exception {
        String error = read("error.html");
        assertThat(error).contains("data-error-action");
        assertThat(error).contains("data-error-correlation-id");
        assertThat(error).contains("data-error-recovery");
    }
}
```

- [ ] **Step 2: Run red, implement, run green**

- `settings.html` → form archetype with four `data-settings-group` sections; the danger group
  is labelled, visually separated, and every destructive control carries
  `data-confirm-consequence` naming the entity and the consequence.
- `_import-dialog.html` → the four ranked `data-import-section` regions before the confirm
  action; validation errors block confirmation, warnings do not.
- `about.html` → detail archetype, `.prose`, no data-management chrome.
- `error.html` → plain-language `data-error-action`, a `data-error-recovery` link back to a
  safe destination, and the correlation id in `--text-tertiary` marked
  `data-error-correlation-id`.

```bash
./mvnw -Dtest='AdministrationLayoutTest,CampaignSettingsControllerTest,GlobalExceptionHandlerTest,NotFoundPageAdviceTest,RenderFailureIsCleanTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/AdministrationLayoutTest.java
git commit -m "feat: restructure administration, About, and error pages"
```

## Task 49: Remove the compatibility layer

**Files:**
- Modify: `src/main/resources/static/css/tokens.css` and every stylesheet still referencing
  a legacy alias
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasRemovalTest.java`

- [ ] **Step 1: Write the failing removal test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 18.1 and acceptance criterion 11: completed migration leaves one semantic
 * token system.
 */
class LegacyVisualAliasRemovalTest {

    @Test
    void noLegacyAliasIsStillDefined() {
        assertThat(CssRules.read("tokens.css"))
                .as("the migration bridge must be gone")
                .doesNotContain("--color-");
    }

    @Test
    void noLegacyAliasIsStillReferenced() {
        String source = CssRules.allApplicationCss() + "\n" + CssRules.allTemplateMarkup();
        var matcher = java.util.regex.Pattern.compile("var\\(\\s*(--color-[a-z0-9-]+)")
                .matcher(source);
        var offenders = matcher.results().map(r -> r.group(1))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        assertThat(offenders).as("remaining legacy references").isEmpty();
    }
}
```

- [ ] **Step 2: Run it, then replace every remaining reference**

```bash
./mvnw -Dtest='LegacyVisualAliasRemovalTest' test
```

Work through the reported list token by token using the mapping already encoded in the alias
block from Task 3 — each `--color-x` has exactly one semantic replacement. Ratchet the Task 5
budgets down to `0L` as you go, and delete the alias block from `tokens.css` last.

- [ ] **Step 3: Delete `LegacyVisualAliasContractTest`**

Its job is finished; `LegacyVisualAliasRemovalTest` is stricter and permanent.

```bash
git rm src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java
```

- [ ] **Step 4: Delete the superseded common fragments**

```bash
git rm src/main/resources/templates/common/_empty-state.html
git rm src/main/resources/templates/common/_skeleton.html
git rm src/main/resources/templates/common/_error.html
```

Repoint any remaining caller at `fragments/_states.html`. Keep
`common/_field-error.html` and `common/_form-cta.html` — Task 50 owns them.

- [ ] **Step 5: Run everything**

```bash
./mvnw -Dtest='LegacyVisualAliasRemovalTest,RawVisualValueContractTest,DesignTokenContractTest,SharedComponentContractTest' test
./mvnw test
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove the legacy visual compatibility layer"
```

## Task 50: Form, action, and destructive-behavior sweep

**Files:**
- Modify: every form template that the earlier stages did not already touch
- Modify: `src/main/resources/templates/common/_field-error.html`, `common/_form-cta.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DestructiveActionContractTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/FormContractTest.java`

- [ ] **Step 1: Write the failing form contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 14. */
class FormContractTest {

    @Test
    void everyControlHasALabelInTheSameOrder() {
        for (Path template : TemplateRules.allTemplates()) {
            var document = TemplateRules.parse(template);
            for (var control : document.select("input:not([type=hidden]), select, textarea")) {
                String id = control.attr("id");
                boolean labelled = (!id.isBlank() && !document.select("label[for=" + id + "]").isEmpty())
                        || control.hasAttr("aria-label")
                        || control.hasAttr("aria-labelledby")
                        || !control.parents().select("label").isEmpty();
                assertThat(labelled)
                        .as("%s: unlabelled control %s", template, control.outerHtml())
                        .isTrue();
            }
        }
    }

    @Test
    void fieldErrorsSitBesideTheirControlAndAreAnnounced() {
        String fragment = TemplateRules.read(TemplateRules.ROOT.resolve("common/_field-error.html"));
        assertThat(fragment).contains("role=\"alert\"").contains("aria-live");
    }

    @Test
    void saveAndCancelSitInAStableActionRegion() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("type=\"submit\"")) continue;
            assertThat(markup)
                    .as("%s: submit controls belong in a form action region", template)
                    .contains("data-action-region");
        }
    }

    @Test
    void destructiveControlsAreNotAdjacentToThePrimaryAction() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            int danger = markup.indexOf("btn-danger");
            int primary = markup.indexOf("btn-primary");
            if (danger < 0 || primary < 0) continue;
            assertThat(Math.abs(danger - primary))
                    .as("%s: destructive control sits next to the primary action", template)
                    .isGreaterThan(120);
        }
    }

    @Test
    void confirmationCopyNamesTheEntityAndConsequence() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("data-confirm")) continue;
            assertThat(markup)
                    .as("%s: confirmation must name the consequence", template)
                    .contains("data-confirm-consequence");
        }
    }
}
```

- [ ] **Step 2: Run red, fix every reported form, run green**

For each report: add the missing label, move the submit pair into a
`data-action-region` footer, separate the destructive control into a labelled danger section
or a confirmation dialog, and add `data-confirm-consequence` copy naming the entity. On
recoverable submission failure, keep entered values and render
`~{fragments/_states :: failed}` with a visible Retry — `dm-request.js` already carries the
retry callback contract.

```bash
./mvnw -Dtest='FormContractTest,DestructiveActionContractTest,InteractionFailureContractTest' test
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: bring every form onto the shared action and destructive contracts"
```

## Task 51: Accessibility, zoom, and viewport matrix

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportMatrixGateTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/KeyboardOperationGateTest.java`

- [ ] **Step 1: Write the viewport matrix gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same scaffolding as ShellRenderGateTest

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}, {2560, 1440}};
    private static final double[] ZOOMS = {1.0, 1.25, 1.5};

    @Test
    void everyReviewedPageSurvivesTheViewportAndZoomMatrix() {
        for (int[] viewport : VIEWPORTS) {
            for (double zoom : ZOOMS) {
                // Emulate zoom by shrinking the viewport: 125% of 1440x900 is 1152x720 CSS px.
                page.setViewportSize((int) (viewport[0] / zoom), (int) (viewport[1] / zoom));
                for (String path : reviewedPages()) {
                    page.navigate("http://localhost:" + port + path);
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                    int overflow = ((Number) page.evaluate(
                            "() => document.documentElement.scrollWidth"
                                    + " - document.documentElement.clientWidth")).intValue();
                    assertThat(overflow)
                            .as("%s at %dx%d @ %.0f%%", path, viewport[0], viewport[1], zoom * 100)
                            .isLessThanOrEqualTo(1);
                }
            }
        }
    }

    @Test
    void reducedMotionRemovesNonEssentialAnimation() {
        BrowserContext reduced = browser.newContext(new Browser.NewContextOptions()
                .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE)
                .setViewportSize(1440, 900));
        Page reducedPage = reduced.newPage();
        try {
            reducedPage.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
            reducedPage.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            Object animated = reducedPage.evaluate("""
                    () => [...document.querySelectorAll('*')]
                            .filter(el => {
                              const s = getComputedStyle(el);
                              return s.animationName !== 'none'
                                  && parseFloat(s.animationDuration) > 0.05;
                            }).length
                    """);
            assertThat(((Number) animated).intValue()).isZero();
        } finally {
            reduced.close();
        }
    }

    @Test
    void requiredRuntimeTextNeverFallsBelowTheMinimumSize() {
        page.setViewportSize(1280, 720);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        Object tooSmall = page.evaluate("""
                () => [...document.querySelectorAll('.cockpit-module [data-runtime-state],'
                        + ' .cockpit-module [data-runtime-action]')]
                        .filter(el => parseFloat(getComputedStyle(el).fontSize) < 14)
                        .length
                """);
        assertThat(((Number) tooSmall).intValue()).isZero();
    }
```

`reviewedPages()` returns the same list as `ShellRenderGateTest.standardPages()` plus the
map editor and the session cockpit. Adjust the 14px floor to the computed value of
`--text-sm` if the token differs.

- [ ] **Step 2: Write the keyboard gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same scaffolding

    @Test
    void everyReviewedPageIsFullyKeyboardReachableWithVisibleFocus() {
        page.setViewportSize(1440, 900);
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            Object invisible = page.evaluate("""
                    () => {
                      const focusable = [...document.querySelectorAll(
                        'button:not([disabled]), [href], input:not([disabled]), '
                        + 'select:not([disabled]), textarea:not([disabled]), '
                        + '[tabindex]:not([tabindex="-1"])')]
                        .filter(el => el.offsetParent !== null);
                      let bad = 0;
                      for (const el of focusable) {
                        el.focus();
                        const s = getComputedStyle(el);
                        if (s.outlineStyle === 'none' && s.boxShadow === 'none') bad++;
                      }
                      return bad;
                    }
                    """);
            assertThat(((Number) invisible).intValue())
                    .as("controls without a visible focus indicator on %s", path).isZero();
        }
    }

    @Test
    void everyIconOnlyControlHasAnAccessibleName() {
        page.setViewportSize(1440, 900);
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            Object unnamed = page.evaluate("""
                    () => [...document.querySelectorAll('button, a')]
                            .filter(el => el.offsetParent !== null)
                            .filter(el => !el.textContent.trim()
                                       && !el.getAttribute('aria-label')
                                       && !el.getAttribute('aria-labelledby')
                                       && !el.getAttribute('title'))
                            .length
                    """);
            assertThat(((Number) unnamed).intValue())
                    .as("unnamed icon-only controls on %s", path).isZero();
        }
    }

    @Test
    void landmarksAndHeadingOrderAreLogical() {
        page.setViewportSize(1440, 900);
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> structure =
                    (java.util.Map<String, Object>) page.evaluate("""
                    () => {
                      const levels = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')]
                        .map(h => Number(h.tagName[1]));
                      let skips = 0;
                      for (let i = 1; i < levels.length; i++) {
                        if (levels[i] - levels[i - 1] > 1) skips++;
                      }
                      return {
                        main: document.querySelectorAll('main').length,
                        nav: document.querySelectorAll('nav').length,
                        h1: levels.filter(l => l === 1).length,
                        skips
                      };
                    }
                    """);
            assertThat(((Number) structure.get("main")).intValue()).as("main on %s", path).isEqualTo(1);
            assertThat(((Number) structure.get("nav")).intValue()).as("nav on %s", path).isGreaterThanOrEqualTo(1);
            assertThat(((Number) structure.get("h1")).intValue()).as("h1 on %s", path).isEqualTo(1);
            assertThat(((Number) structure.get("skips")).intValue())
                    .as("skipped heading levels on %s", path).isZero();
        }
    }
```

- [ ] **Step 3: Run both, fix every failure at the source**

```bash
./mvnw -Dtest='ViewportMatrixGateTest,KeyboardOperationGateTest,ViewportAccessibilityGateTest' test
```

Fix the page, never the assertion. A page that cannot pass at 150% zoom needs its layout
adjusted, not its gate relaxed.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: gate the whole product on the viewport, zoom, and keyboard matrix"
```

## Task 52: Capture the visual review matrix

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualReviewMatrixGateTest.java`

**Interfaces:**
- Consumes: `ReleaseRehearsalFixture` (the feature-complete synthetic campaign).

Spec section 20.3 fixes the matrix. Capture into `target/ui-redesign/matrix/<surface>/<state>.png`.

- [ ] **Step 1: Write the capture gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same scaffolding as ShellRenderGateTest, viewport 1440x900

    private static final Path MATRIX = Path.of("target/ui-redesign/matrix");

    private record Surface(String name, String path) {
    }

    private List<Surface> surfaces() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of(
                new Surface("campaign-selection", "/campaigns"),
                new Surface("campaign-home", c),
                new Surface("adventure-index", c + "/adventures"),
                new Surface("adventure-detail", c + "/adventures/" + seeded.adventureId()),
                new Surface("scene-detail", c + "/adventures/" + seeded.adventureId()
                        + "/scenes/" + seeded.sceneId()),
                new Surface("quests", c + "/quests"),
                new Surface("npcs", c + "/world/npcs"),
                new Surface("locations", c + "/world/locations"),
                new Surface("factions", c + "/world/factions"),
                new Surface("notes", c + "/notes"),
                new Surface("calendar", c + "/calendar"),
                new Surface("encounter-index", c + "/encounters"),
                new Surface("encounter-detail", c + "/encounters/" + seeded.encounterId()),
                new Surface("encounter-setup", c + "/encounters/" + seeded.encounterId() + "/setup"),
                new Surface("party", c + "/party"),
                new Surface("sheets", c + "/sheets"),
                new Surface("treasury", c + "/treasury"),
                new Surface("ledger", c + "/ledger"),
                new Surface("handouts", c + "/handouts"),
                new Surface("audio", c + "/audio/cues"),
                new Surface("maps-index", c + "/maps"),
                new Surface("map-editor", c + "/maps/" + seeded.playableMapId() + "/edit"),
                new Surface("library-monsters", "/library"),
                new Surface("library-spells", "/library/spells"),
                new Surface("library-conditions", "/library/conditions"),
                new Surface("library-rules", "/library/rules"),
                new Surface("library-equipment", "/library/equipment"),
                new Surface("library-magic-items", "/library/magic-items"),
                new Surface("library-classes", "/library/classes"),
                new Surface("library-species", "/library/species"),
                new Surface("library-backgrounds", "/library/backgrounds"),
                new Surface("library-feats", "/library/feats"),
                new Surface("tables", "/library/tables"),
                new Surface("traps", "/library/traps"),
                new Surface("hazards", "/library/hazards"),
                new Surface("campaign-settings", c + "/settings"),
                new Surface("about", "/library/about"),
                new Surface("session-cockpit", c + "/session"));
    }

    @Test
    void capturePopulatedStatesForEverySurface() throws Exception {
        for (Surface surface : surfaces()) {
            Path directory = MATRIX.resolve(surface.name());
            Files.createDirectories(directory);
            page.navigate("http://localhost:" + port + surface.path());
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(directory.resolve("populated.png")).setFullPage(true));
        }
        assertThat(MATRIX.toFile().listFiles()).hasSize(surfaces().size());
    }

    @Test
    void captureEmptyStatesFromTheUnseededCampaign() throws Exception {
        // ReleaseRehearsalFixture seeds a second, deliberately empty campaign; if it does not,
        // add one that creates a campaign with no records and returns its id.
        String empty = "/campaigns/" + seeded.emptyCampaignId();
        for (String surface : List.of("/adventures", "/encounters", "/maps", "/handouts",
                "/audio/cues", "/notes", "/party", "/treasury", "/ledger", "/quests",
                "/world/npcs", "/world/locations", "/world/factions")) {
            Path directory = MATRIX.resolve("empty" + surface.replace('/', '-'));
            Files.createDirectories(directory);
            page.navigate("http://localhost:" + port + empty + surface);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            assertThat(page.locator(".state--empty").count())
                    .as("empty state on %s", surface).isEqualTo(1);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(directory.resolve("empty.png")).setFullPage(true));
        }
    }

    @Test
    void captureOverlayAndTransientSurfaces() throws Exception {
        Path directory = MATRIX.resolve("overlays");
        Files.createDirectories(directory);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        page.keyboard().press("Control+k");
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("command-palette.png")));
        page.keyboard().press("Escape");

        page.keyboard().press("Control+r");
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("dice-roller.png")));
        page.keyboard().press("Escape");

        page.evaluate("() => window.dmToast.show('Saved', 'success')");
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("toast.png")));

        assertThat(directory.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(3);
    }
```

- [ ] **Step 2: Run the capture and review every image**

```bash
./mvnw -Dtest='VisualReviewMatrixGateTest' test
```

Open every file under `target/ui-redesign/matrix/`. For each, answer: is the primary task
obvious, is the current state visible, is there exactly one primary action, is gold rare, is
space doing a job? Record any surface that fails and fix it before Task 53.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualReviewMatrixGateTest.java
git commit -m "test: capture the whole-product visual review matrix"
```

## Task 53: Whole-product release gate

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseGateIndexContractTest.java`
- Modify: `docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`
- Delete: `docs/ui-polish-spec.md`
- Delete: `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md`

- [ ] **Step 1: Register the new gates in the release gate index**

`ReleaseGateIndexContractTest` asserts that every gate class is listed. Add
`ShellRenderGateTest`, `OverlayBehaviorGateTest`, `NarrativePreparationRenderGateTest`,
`OperationalPreparationRenderGateTest`, `ReferenceWorkspaceRenderGateTest`,
`MapEditorRenderGateTest`, `CockpitLaptopFitGateTest`, `ViewportMatrixGateTest`,
`KeyboardOperationGateTest`, and `VisualReviewMatrixGateTest`.

- [ ] **Step 2: Write the acceptance-criteria checklist test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 21: the machine-checkable half of the acceptance criteria. */
class RedesignCoverageContractTest {

    @Test
    void everyShippedPageIsAssignedAnApprovedArchetype() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (template.getFileName().toString().equals("error.html")) continue;
            String markup = TemplateRules.read(template);
            assertThat(markup).as("%s declares an archetype", template).contains("archetype='");
            assertThat(PageArchetypeContractTest.ARCHETYPES)
                    .as("%s uses an approved archetype", template)
                    .anySatisfy(archetype -> assertThat(markup).contains("archetype='" + archetype + "'"));
        }
    }

    @Test
    void noShippedPageUsesTheFormerBrownSurfaceSystem() {
        String source = CssRules.allApplicationCss() + "\n" + CssRules.allTemplateMarkup();
        for (String brown : java.util.List.of("#17120c", "#211a12", "#2b2318", "#3a3125", "#564936")) {
            assertThat(source).as("superseded brown %s", brown).doesNotContain(brown);
        }
    }

    @Test
    void goldIsAbsentFromGenericCardBordersAndDecorativeSeparators() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> rule.selector().matches(".*\\.(card|panel|divider|rule|section)\\b.*"))
                .forEach(rule -> assertThat(
                        String.valueOf(rule.value("border")) + rule.value("border-color"))
                        .as("decorative gold in %s", rule.where())
                        .doesNotContain("--action-primary"));
    }

    @Test
    void everyDuplicateShellImplementationIsGone() {
        long shells = TemplateRules.allTemplates().stream()
                .filter(path -> TemplateRules.read(path).contains("class=\"app-shell\""))
                .count();
        assertThat(shells).isEqualTo(1);
    }
}
```

Make `PageArchetypeContractTest.ARCHETYPES` package-visible so this test can reuse it.

- [ ] **Step 3: Run the whole build**

```bash
./mvnw clean verify
```

Expected: PASS with no skipped gates.

- [ ] **Step 4: Retire the superseded documents**

```bash
git rm docs/ui-polish-spec.md
git rm docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md
```

Search for and update any reference to either path
(`grep -rn "ui-polish-spec\|ui-redesign-visual-foundations" --include='*.md' --include='*.java' .`).

- [ ] **Step 5: Record the acceptance evidence**

Append a "Release evidence" section to this plan listing, for each of the twelve acceptance
criteria in spec section 21, the test class or screenshot directory that demonstrates it, and
note any criterion that required a human judgement call with the reviewer's conclusion.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: pass the whole-product UI redesign release gate"
```

---

# Appendix A: Spec coverage map

| Spec section | Covered by |
|---|---|
| 4 Supported environment | Tasks 11, 19, 41, 45, 51 |
| 5 Design principles | Tasks 7, 11, 21, 44, 52 (review) |
| 6.1 Normative palette | Task 3 |
| 6.2 Surface ladder | Tasks 3, 7, 8 |
| 6.3 Gold discipline | Tasks 7, 15, 53 |
| 6.4 Semantic state | Tasks 3, 16, 29, 44 |
| 7.1–7.2 Typeface and type roles | Tasks 7, 15, 23 |
| 7.3 Icons | Tasks 6, 12, 13 |
| 8.1 Global top bar | Task 12 |
| 8.2–8.3 Navigation | Task 13 |
| 8.4 Page header | Tasks 15, 19 |
| 9.1–9.6 Archetypes and width | Tasks 11, 19 |
| 10 Shared components | Tasks 15, 16, 17, 18 |
| 11.1 Campaign selection | Task 20 |
| 11.2 Campaign Home | Task 21 |
| 11.3 Adventures and scenes | Tasks 22, 23 |
| 11.4 Encounters | Tasks 27, 28, 29 |
| 11.5 Party and sheets | Tasks 30, 31 |
| 11.6 Treasury and ledger | Task 32 |
| 11.7 Quests, world, notes, calendar | Tasks 24, 25, 26 |
| 11.8 Maps, handouts, audio | Tasks 33, 38 |
| 11.9 Library and reference | Tasks 34, 35, 36 |
| 11.10 Administration and system pages | Task 48 |
| 11.11 Presentation | Task 47 (see Scope reconciliation) |
| 12.1–12.5 Session cockpit | Tasks 42, 43, 44, 45, 46 |
| 13 Map editor | Tasks 38, 39, 40, 41 |
| 14 Forms and destructive behavior | Task 50 |
| 15 Overlays | Task 18 |
| 16 Loading and feedback | Tasks 17, 40 |
| 17 Accessibility | Tasks 7, 18, 50, 51 |
| 18.1 CSS ownership | Tasks 1, 3, 4, 38, 49 |
| 18.2 Template ownership | Tasks 10, 16, 19, 53 |
| 18.3 JavaScript | Tasks 12, 13, 18, 39, 45 |
| 18.4 Backend compatibility | Tasks 21, 34 |
| 19 Delivery decomposition | Stage structure of this plan |
| 20.1 Static contracts | Tasks 1–7, 10–18, 49, 53 |
| 20.2 Browser gates | Tasks 8, 19, 26, 33, 37, 41, 45, 51 |
| 20.3 Visual review matrix | Task 52 |
| 20.4 Functional regression | Every stage's `./mvnw test` step; Task 53 |
| 21 Acceptance criteria | Task 53 |

# Appendix B: Standing rules for every task

- Never widen a gate to make a page pass. Fix the page.
- Never add a `--color-*` token. The vocabulary is frozen at Task 5 and deleted at Task 49.
- Never leave a page half-migrated across a commit boundary within a stage's own scope.
- Never change a route, form field name, htmx target id, persisted field, or package format.
  If a redesign appears to require one, stop and raise it — that is a functional change and
  needs its own spec.
- When a test name in this plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.




