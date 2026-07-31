# UI Redesign Part 1: Visual Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the brown-on-brown visual foundation with the approved charcoal, ivory, scarce-gold semantic token system, and enforce the color, typography, icon, focus, motion, and elevation rules that Parts 2–4 consume — without changing routes, structure, or persisted data.

**Architecture:** `tokens.css` becomes the only file allowed to hold a raw UI color value and publishes semantic role tokens; the existing `--color-*` names temporarily alias those roles under a non-increasing migration budget that Part 4 deletes. The existing Java CSS-contract helpers gain automatic stylesheet discovery and measured WCAG contrast, and focused JUnit plus Playwright gates verify source rules, contrast, browser-computed hierarchy, and a repository-owned inline-SVG icon set.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, repository-owned CSS and inline SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Position in the program

This is **Part 1 of 4** of the whole-product UI redesign. The program index is
`docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`; the design authority is
`docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.

| Part | Stages | Tasks | Status |
|---|---|---|---|
| **1. Visual foundations (this plan)** | 1 | 1–9 | depends only on the approved spec |
| 2. Shell and shared archetypes | 2 | 10–19 | consumes Part 1 |
| 3. Feature surfaces | 3–5 | 20–37 | consumes Part 2 |
| 4. Editors, cockpit, and release | 6–8 | 38–53 | consumes Parts 2–3 |

Task numbers are global across the four parts, so every cross-reference in the program
(`Task 49 removes them`, `Task 13 owns that`) means the same task everywhere.

**Working product after this part:** every existing page renders on the charcoal surface
ladder with ivory text and scarce gold. Page structure is unchanged; Part 2 restructures it.

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
| `src/main/resources/static/icons/ui.svg` | Repository-owned outline icon sprite |
| `src/main/resources/templates/common/_icon.html` | Only interface for chrome icons |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssInventoryContractTest.java` | Proves no shipped stylesheet escapes the contracts |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrast.java` | Package-private WCAG contrast utility |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrastTest.java` | Verifies parsing and ratios |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/RawVisualValueContractTest.java` | Confines raw literals to `tokens.css` |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java` | Freezes the compatibility vocabulary |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/IconSystemContractTest.java` | Sprite, fragment, sizing, accessibility |
| `src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualFoundationRenderGateTest.java` | Computed palette, hierarchy, focus, screenshots |

### Modified by this part

`static/css/tokens.css`, `base.css`, `components.css`, `surfaces.css`, `book.css`,
`encounter.css`, `cockpit.css`, `cockpit-layout.css`, `cockpit-modules.css`;
`templates/encounter/_tracker.html`, `encounter/_summary-modal.html`,
`treasury/_attunement-warn.html`, `maps/editor.html`, `common/_empty-state.html`;
`config/CssRules.java`, `DesignTokenContractTest.java`, `TypographyRoleContractTest.java`,
`GoldAccentContractTest.java`.

Nothing is deleted by this part.

## Public interfaces

### Produced for Parts 2–4 — semantic CSS roles

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

### Produced for Parts 2–4 — Thymeleaf fragment contracts

```html
~{common/_icon :: icon(name='search')}
~{common/_icon :: icon-sized(name='search', size='20')}
```

### Produced for Parts 2–4 — Java test helpers

```java
// package dev.hendrikhoemberg.dmhelper.config (package-private)
static List<String>  CssRules.ALL_FILES
static List<String>  CssRules.discoverCssFiles()
static String        CssRules.allApplicationCss()
static String        CssRules.allTemplateMarkup()
static List<String>  CssRules.rawColorLiterals(String source)
static long          CssRules.tokenReferenceCount(String token)
static double        ColorContrast.ratio(String foregroundHex, String backgroundHex)
```

### Consumed from the existing repository

`CssRules.of(String...)` / `CssRules.of(List<String>)` and `CssRules.Rule`
(`selector()`, `file()`, `value(String)`, `where()`) already exist.
`ReleaseRehearsalFixture` (`support` package, `@Autowired`-able, exposes `Seeded`) and
`BrowserFailureCollector` already exist — see `ViewportAccessibilityGateTest`.

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
./mvnw -Dtest='CssInventoryContractTest,DesignTokenContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest,TypeScaleContractTest,TypographyRoleContractTest,ControlConsistencyContractTest,CombatLegibilityContractTest,DestructiveActionContractTest,InteractionFailureContractTest' test
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

- [ ] **Step 1: Add the palette assertions to `DesignTokenContractTest`**

Nothing needs deleting first. `semanticColorsKeepTheirValues()` and
`warningWaveTitleContrastsWithItsWarningSurface()` were already removed by the test-suite
triage (`docs/test-suite-triage.md`); the file now holds only
`everyTokenReferencedByAStylesheetIsDefined()` and
`everyTokenReferencedByATemplateIsDefined()`. Add:

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
    }

    /**
     * A <use> reference into an external sprite clones only the <symbol> subtree, and
     * inherited properties resolve from the <use> element's position in the referencing
     * document — not from the sprite's own root. Painting attributes declared on the
     * sprite's root <svg> are therefore dropped, and every icon renders as a black fill.
     * The stroke contract must live in components.css on .icon, where it does inherit.
     */
    @Test
    void theStrokeContractLivesWhereExternalUseCanInheritIt() {
        var icon = CssRules.of("components.css").stream()
                .filter(rule -> rule.selector().equals(".icon"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".icon is not styled in components.css"));
        assertThat(icon.value("fill")).isEqualTo("none");
        assertThat(icon.value("stroke")).isEqualTo("currentColor");
        assertThat(icon.value("stroke-width")).isEqualTo("1.75");
        assertThat(icon.value("stroke-linecap")).isEqualTo("round");
        assertThat(icon.value("stroke-linejoin")).isEqualTo("round");
    }

    @Test
    void theIconFragmentIsDecorativeOnly() throws Exception {
        String fragment = Files.readString(
                Path.of("src/main/resources/templates/common/_icon.html"));
        assertThat(fragment).contains("aria-hidden=\"true\"").contains("focusable=\"false\"");
        assertThat(fragment).doesNotContain("aria-label");
    }

    /**
     * Matched by codepoint, not by entity prefix: "&#x26" and "&#x27" also match the
     * ordinary escapes &#x26; (ampersand) and &#x27; (apostrophe), which are legitimate.
     */
    @Test
    void applicationChromeCarriesNoEmojiPictograms() {
        String markup = CssRules.allTemplateMarkup();

        var literal = java.util.regex.Pattern
                .compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}]")
                .matcher(markup);
        assertThat(literal.find())
                .as("literal emoji or pictogram in chrome — use ~{common/_icon :: icon}")
                .isFalse();

        var escaped = java.util.regex.Pattern
                .compile("&#x([0-9a-fA-F]{4,5});")
                .matcher(markup);
        while (escaped.find()) {
            int codepoint = Integer.parseInt(escaped.group(1), 16);
            boolean pictogram = (codepoint >= 0x1F300 && codepoint <= 0x1FAFF)
                    || (codepoint >= 0x2600 && codepoint <= 0x27BF)
                    || (codepoint >= 0x2B00 && codepoint <= 0x2BFF);
            assertThat(pictogram)
                    .as("escaped pictogram %s in chrome — use ~{common/_icon :: icon}",
                            escaped.group())
                    .isFalse();
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
per required name.

**Do not put the painting attributes on the sprite's root `<svg>`.** A `<use>` reference into
an external sprite clones only the `<symbol>` subtree, and inherited properties resolve from
the `<use>` element's position in the *referencing* document — so `fill`, `stroke`, and
`stroke-width` declared on the sprite root never reach the clone, and every icon renders as a
black fill. The stroke contract belongs in `components.css` on `.icon` (Step 4), where it
inherits into the shadow tree correctly.

```xml
<svg xmlns="http://www.w3.org/2000/svg" style="display:none">
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

Add to `components.css`. This rule, not the sprite root, is what actually paints the icons —
`fill`, `stroke`, and the stroke geometry inherit from here into the `<use>` shadow tree:

```css
.icon {
  flex: none;
  display: inline-block;
  vertical-align: -0.125em;
  color: currentColor;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.75;
  stroke-linecap: round;
  stroke-linejoin: round;
}
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

- [ ] **Step 5: Run the whole static design suite**

```bash
./mvnw -Dtest='CssInventoryContractTest,ColorContrastTest,DesignTokenContractTest,RawVisualValueContractTest,LegacyVisualAliasContractTest,TypographyRoleContractTest,TypeScaleContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest,IconSystemContractTest,ControlConsistencyContractTest,DestructiveActionContractTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

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

Add a Stage 1 row to the Status log in the program index,
`docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`, listing the final
legacy-alias budget numbers from Task 5 and the screenshot directory
`target/ui-redesign/visual-foundations/`.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md
git commit -m "docs: record the visual foundations stage result"
```

---

# Appendix A: Spec coverage for this part

| Spec section | Covered by |
|---|---|
| 5 Design principles | Task 7 |
| 6.1 Normative palette | Task 3 |
| 6.2 Surface ladder | Tasks 3, 7, 8 |
| 6.3 Gold discipline | Task 7 (completed by Tasks 15, 53) |
| 6.4 Semantic state | Task 3 (completed by Tasks 16, 29, 44) |
| 7.1–7.2 Typeface and type roles | Task 7 (completed by Tasks 15, 23) |
| 7.3 Icons | Task 6 (consumed by Tasks 12, 13) |
| 17 Accessibility | Task 7 (completed by Tasks 18, 50, 51) |
| 18.1 CSS ownership | Tasks 1, 3, 4 (completed by Tasks 38, 49) |
| 20.1 Static contracts | Tasks 1–7 |
| 20.2 Browser gates | Task 8 |
| 20.4 Functional regression | Task 9 |

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

Part 1 is done when Task 9's `./mvnw test` is green and the final legacy-alias budget numbers
are recorded in the program index. Continue with
`docs/superpowers/plans/2026-07-31-ui-redesign-2-shell-and-archetypes.md`.
