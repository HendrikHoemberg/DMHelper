# UI Redesign Visual Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the brown-on-brown visual foundation with the approved charcoal, ivory, scarce-gold semantic system and enforce the color, typography, icon, focus, motion, and elevation rules that every later redesign stage will consume.

**Architecture:** `tokens.css` becomes the single raw-color authority and publishes semantic role tokens; existing `--color-*` names temporarily alias those roles under a non-increasing migration budget. Existing Java CSS-contract helpers gain automatic stylesheet discovery and color inspection, while focused JUnit and Playwright gates verify source rules, measured contrast, browser-computed hierarchy, and reusable inline-SVG icons without changing routes or persisted data.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, repository-owned CSS and SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Global Constraints

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.
- Support desktop and laptop browsers only, with a minimum viewport of 1280x720.
- Preserve Spring MVC, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, existing routes, persisted semantics, campaign-package formats, player safety, and the customizable four-zone cockpit with saved presets.
- Do not introduce a SPA, frontend package manager, CSS framework, icon font, or third-party component system.
- Keep `tokens.css` as the only source of raw UI color values; persisted terrain/drawing colors are domain data, not UI chrome.
- Use system UI for chrome, controls, labels, tables, values, metadata, editor chrome, and cockpit chrome; Alegreya for narrative and document content; Cinzel only for the product wordmark and at most one principal title per view.
- Use aged gold only for the primary action, selection, and visible focus. Secondary controls and ordinary boundaries stay neutral.
- Keep semantic state meanings stable and combine color with text, icon, border, or shape.
- Keep required runtime information at or above `--text-sm`.
- Preserve reduced-motion behavior and the existing elevation order.
- This stage may change global paint and foundation primitives; structural shell and feature-template migrations belong to later plans.
- No database migration or domain-model change is permitted in this stage.

---

## File structure

### Create

- `src/main/resources/static/icons/ui.svg` — repository-owned outline icon sprite; all
  symbols use `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`,
  `stroke-width="1.75"`, `stroke-linecap="round"`, and `stroke-linejoin="round"`.
- `src/main/resources/templates/common/_icon.html` — the only Thymeleaf interface for
  inserting application-chrome icons.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssInventoryContractTest.java` —
  proves the CSS test inventory cannot omit a shipped stylesheet.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrast.java` — focused
  package-private WCAG contrast utility for design-contract tests.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrastTest.java` — verifies
  parsing and ratio calculations independently of palette assertions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/RawVisualValueContractTest.java` —
  confines raw UI color literals to `tokens.css`.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java` —
  freezes the exact compatibility vocabulary and prevents reference counts increasing.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/IconSystemContractTest.java` —
  verifies the sprite, fragment, sizing roles, and icon accessibility contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/visual/VisualFoundationRenderGateTest.java` —
  verifies computed palette, hierarchy, semantic states, focus, browser health, and
  representative screenshots.

### Modify

- `src/main/resources/static/css/tokens.css` — semantic palette, derived state surfaces,
  shadow/scrim/glow tokens, and temporary compatibility aliases.
- `src/main/resources/static/css/base.css` — neutral document paint, texture containment,
  selection, form-control boundaries, and global focus.
- `src/main/resources/static/css/components.css` — replace raw colors and align shared
  button/card/banner paint with semantic roles.
- `src/main/resources/static/css/cockpit.css` — replace raw overlay/fallback colors.
- `src/main/resources/static/css/cockpit-modules.css` — replace raw neutral/danger tints.
- `src/main/resources/static/css/surfaces.css` — remove raw danger fallbacks.
- `src/main/resources/templates/encounter/_tracker.html` — replace the raw concentration
  background with a semantic class.
- `src/main/resources/templates/encounter/_summary-modal.html` — remove duplicated inline
  modal paint/layout in favor of the existing modal contract.
- `src/main/resources/templates/treasury/_attunement-warn.html` — remove raw fallback and
  spacing from inline style.
- `src/main/resources/templates/maps/editor.html` — replace the two raw UI shadow values
  with a token; leave persisted terrain/drawing palette values intact.
- `src/main/resources/templates/common/_empty-state.html` — render named SVG icons instead
  of application-authored Unicode pictograms while preserving its fragment signatures.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java` — automatic CSS
  discovery, application-source aggregation, and raw-color/reference helpers.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java` —
  normative semantic role/value and contrast assertions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java` —
  semantic gold-role enforcement rather than legacy-token enforcement.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java` —
  approved type-role selector contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java` — update
  assertions that intentionally change with the neutral foundation and named icons.

## Public interfaces established by this stage

### Semantic CSS roles

```css
--surface-canvas
--surface-navigation
--surface-workspace
--surface-panel
--surface-raised
--surface-inset
--surface-overlay
--border-subtle
--border-strong
--text-primary
--text-secondary
--text-tertiary
--action-primary
--action-primary-hover
--action-on-primary
--selection-accent
--selection-surface
--focus-ring
--state-success
--state-success-surface
--state-success-border
--state-warning
--state-warning-surface
--state-warning-border
--state-danger
--state-danger-surface
--state-danger-border
--state-info
--state-info-surface
--state-info-border
--state-shield
--state-shield-surface
--state-shield-border
--state-concentration
--state-concentration-surface
--state-concentration-border
--scrim-standard
--scrim-strong
--shadow-floating
--shadow-overlay
--glow-primary
```

### Icon fragment

```html
<th:block th:replace="~{common/_icon :: icon(name='search')}"></th:block>
```

The fragment returns an `aria-hidden="true"`, `focusable="false"` SVG. Accessibility
belongs to the enclosing link/button through visible text, `aria-label`, or
`aria-labelledby`; the icon never becomes a second accessible name.

### Java test helpers

```java
static List<String> CssRules.ALL_FILES
static List<String> CssRules.discoverCssFiles()
static String CssRules.allApplicationCss()
static String CssRules.allTemplateMarkup()
static List<String> CssRules.rawColorLiterals(String source)
static long CssRules.tokenReferenceCount(String token)
static double ColorContrast.ratio(String foregroundHex, String backgroundHex)
```

## Task 1: Make the stylesheet inventory authoritative

**Files:**

- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssInventoryContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssInventoryContractTest.java`

**Interfaces:**

- Consumes: `CssRules.CSS_DIR`.
- Produces: `CssRules.discoverCssFiles()` and a dynamically initialized immutable
  `CssRules.ALL_FILES`.

- [ ] **Step 1: Write the failing inventory contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CssInventoryContractTest {

    @Test
    void everyShippedStylesheetParticipatesInDesignContracts() {
        assertThat(CssRules.ALL_FILES).containsExactly(
                "base.css",
                "book.css",
                "cockpit-layout.css",
                "cockpit-modules.css",
                "cockpit.css",
                "components.css",
                "encounter.css",
                "surfaces.css",
                "tokens.css");
    }

    @Test
    void discoveryIsStableAndHasNoDuplicates() {
        assertThat(CssRules.discoverCssFiles())
                .containsExactlyElementsOf(CssRules.ALL_FILES)
                .doesNotHaveDuplicates();
    }
}
```

- [ ] **Step 2: Run the contract and confirm the current manual list fails**

Run:

```bash
./mvnw -Dtest=CssInventoryContractTest test
```

Expected: FAIL because `encounter.css` is absent and the files are not directory-sorted.

- [ ] **Step 3: Replace the manual list with sorted directory discovery**

Add `java.util.Comparator` and replace `ALL_FILES` in `CssRules` with:

```java
static final List<String> ALL_FILES = discoverCssFiles();

static List<String> discoverCssFiles() {
    try (var files = Files.list(CSS_DIR)) {
        return files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".css"))
                .sorted(Comparator.naturalOrder())
                .toList();
    } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
}
```

Keep `RUNTIME_FILES` explicit because it describes a semantic subset, not the physical
directory.

- [ ] **Step 4: Run the inventory and all existing CSS contracts**

Run:

```bash
./mvnw -Dtest='CssInventoryContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest' test
```

Expected: PASS, including `encounter.css` in every general contract.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java src/test/java/dev/hendrikhoemberg/dmhelper/config/CssInventoryContractTest.java
git commit -m "test: discover every shipped stylesheet"
```

## Task 2: Add a measured WCAG contrast utility

**Files:**

- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrast.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrastTest.java`

**Interfaces:**

- Consumes: six-digit `#rrggbb` strings.
- Produces: `ColorContrast.ratio(String, String)` returning the WCAG relative-luminance
  ratio from `1.0` through `21.0`.

- [ ] **Step 1: Write the failing utility tests**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorContrastTest {

    @Test
    void blackAndWhiteHaveMaximumContrast() {
        assertThat(ColorContrast.ratio("#000000", "#ffffff")).isEqualTo(21.0);
    }

    @Test
    void ratioIsIndependentOfArgumentOrder() {
        assertThat(ColorContrast.ratio("#eee8dc", "#101113"))
                .isEqualTo(ColorContrast.ratio("#101113", "#eee8dc"));
    }

    @Test
    void rejectsValuesOutsideTheSixDigitTokenFormat() {
        assertThatThrownBy(() -> ColorContrast.ratio("#fff", "#000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("#rrggbb");
    }
}
```

- [ ] **Step 2: Run the tests and verify the helper is missing**

Run:

```bash
./mvnw -Dtest=ColorContrastTest test
```

Expected: compilation FAIL because `ColorContrast` does not exist.

- [ ] **Step 3: Implement the utility**

```java
package dev.hendrikhoemberg.dmhelper.config;

final class ColorContrast {

    static double ratio(String foregroundHex, String backgroundHex) {
        double foreground = luminance(parse(foregroundHex));
        double background = luminance(parse(backgroundHex));
        return (Math.max(foreground, background) + 0.05)
                / (Math.min(foreground, background) + 0.05);
    }

    private static int parse(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Expected a six-digit #rrggbb color");
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static double luminance(int rgb) {
        return 0.2126 * channel((rgb >> 16) & 0xff)
                + 0.7152 * channel((rgb >> 8) & 0xff)
                + 0.0722 * channel(rgb & 0xff);
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.04045
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private ColorContrast() {
    }
}
```

- [ ] **Step 4: Run and commit**

```bash
./mvnw -Dtest=ColorContrastTest test
git add src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrast.java src/test/java/dev/hendrikhoemberg/dmhelper/config/ColorContrastTest.java
git commit -m "test: measure design token contrast"
```

Expected: PASS.

## Task 3: Publish the semantic charcoal palette

**Files:**

- Modify: `src/main/resources/static/css/tokens.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java`

**Interfaces:**

- Consumes: `ColorContrast.ratio(String, String)`.
- Produces: every semantic CSS role listed in this plan's Public Interfaces section.
- Compatibility: all currently defined global `--color-*` tokens remain defined, but
  resolve through semantic roles.

- [ ] **Step 1: Replace the old hard-coded semantic-color assertion with the normative palette contract**

Add this helper and test to `DesignTokenContractTest`:

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
    for (String state : List.of(
            "#89ad69", "#e0a34d", "#d36a61", "#74a3c1", "#8fa8b8", "#b58ac1")) {
        assertThat(ColorContrast.ratio(state, "#1d1f24")).isGreaterThanOrEqualTo(4.5);
    }
}

@Test
void strongInteractiveBoundaryMeetsThreeToOne() {
    assertThat(ColorContrast.ratio("#686c75", "#111216")).isGreaterThanOrEqualTo(3.0);
}
```

Delete the old `semanticColorsKeepTheirValues()` and
`warningWaveTitleContrastsWithItsWarningSurface()` assertions because the former checks
superseded values and the latter couples a global token test to inline encounter markup.

- [ ] **Step 2: Run the contract and verify the semantic roles are absent**

Run:

```bash
./mvnw -Dtest='ColorContrastTest,DesignTokenContractTest' test
```

Expected: FAIL with `--surface-canvas is defined` and the other new role assertions.

- [ ] **Step 3: Replace the palette section in `tokens.css`**

Use this exact semantic block at the start of `:root`:

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

  /* Temporary migration aliases. LegacyVisualAliasContractTest prevents growth. */
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

Retain the existing non-color spacing, type, radius, duration, z-index, texture, and
cockpit-width tokens. Change the existing elevation aliases to:

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

- [ ] **Step 4: Run the palette contracts**

Run:

```bash
./mvnw -Dtest='ColorContrastTest,DesignTokenContractTest,ElevationModelContractTest' test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/tokens.css src/test/java/dev/hendrikhoemberg/dmhelper/config/DesignTokenContractTest.java
git commit -m "feat: establish semantic charcoal palette"
```

## Task 4: Confine raw UI colors to the token layer

**Files:**

- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/RawVisualValueContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`
- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/main/resources/static/css/surfaces.css`
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/templates/encounter/_summary-modal.html`
- Modify: `src/main/resources/templates/treasury/_attunement-warn.html`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**

- Consumes: semantic color, scrim, shadow, tint, and glow tokens from Task 3.
- Produces: `CssRules.allApplicationCss()`, `CssRules.allTemplateMarkup()`, and
  `CssRules.rawColorLiterals(String)`.
- Excludes: JavaScript string values representing user-editable/persisted terrain,
  drawing fill, and drawing stroke data in `maps/editor.html`.

- [ ] **Step 1: Add source aggregation and literal detection to `CssRules`**

```java
private static final Pattern RAW_COLOR = Pattern.compile(
        "(?i)(?<![%\\w-])#[0-9a-f]{3,8}\\b|\\b(?:rgb|rgba|hsl|hsla)\\s*\\([^)]*\\)");

static String allApplicationCss() {
    return ALL_FILES.stream()
            .filter(file -> !file.equals("tokens.css"))
            .map(file -> "/* " + file + " */\n" + read(file))
            .collect(java.util.stream.Collectors.joining("\n"));
}

static String allTemplateMarkup() {
    try (var files = Files.walk(Path.of("src/main/resources/templates"))) {
        return files.filter(path -> path.toString().endsWith(".html"))
                .sorted()
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

static List<String> rawColorLiterals(String source) {
    List<String> values = new ArrayList<>();
    Matcher matcher = RAW_COLOR.matcher(source);
    while (matcher.find()) values.add(matcher.group());
    return values;
}
```

- [ ] **Step 2: Write the failing raw-color contracts**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RawVisualValueContractTest {

    private static final Pattern STYLE_ATTRIBUTE =
            Pattern.compile("(?is)\\sstyle\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern STYLE_BLOCK =
            Pattern.compile("(?is)<style\\b[^>]*>(.*?)</style>");

    @Test
    void rawUiColorsExistOnlyInTokensCss() {
        assertThat(CssRules.rawColorLiterals(CssRules.allApplicationCss()))
                .as("raw colors in application stylesheets")
                .isEmpty();
    }

    @Test
    void inlineAndEmbeddedTemplateCssUsesTokens() {
        String markup = CssRules.allTemplateMarkup();
        List<String> offenders = new ArrayList<>();
        for (Pattern pattern : List.of(STYLE_ATTRIBUTE, STYLE_BLOCK)) {
            var matcher = pattern.matcher(markup);
            while (matcher.find()) {
                offenders.addAll(CssRules.rawColorLiterals(matcher.group(1)));
            }
        }
        assertThat(offenders).as("raw colors in template-owned CSS").isEmpty();
    }

    @Test
    void persistedMapPaletteValuesAreNotMistakenForUiChrome() {
        String domainData = "fill: '#2a2a3e'; stroke: '#000000'";
        assertThat(CssRules.rawColorLiterals(domainData))
                .containsExactly("#2a2a3e", "#000000");
        assertThat(STYLE_ATTRIBUTE.matcher(domainData).find()).isFalse();
        assertThat(STYLE_BLOCK.matcher(domainData).find()).isFalse();
    }
}
```

- [ ] **Step 3: Run the contract and inspect every reported literal**

Run:

```bash
./mvnw -Dtest=RawVisualValueContractTest test
```

Expected: FAIL with literals from `base.css`, `components.css`, `cockpit.css`,
`cockpit-modules.css`, `surfaces.css`, the three inline template styles, and the two map
editor embedded-style shadows.

- [ ] **Step 4: Add derived UI tokens for the reviewed exceptional paint**

Add these to `tokens.css` beside the other derived visual tokens:

```css
  --selection-foreground: var(--text-primary);
  --vignette-color: rgba(8, 9, 11, var(--vignette-intensity));
  --spotlight-scrim: radial-gradient(ellipse at center,
      rgba(16, 17, 19, 0.94) 45%, rgba(0, 0, 0, 0.98) 100%);
  --parchment-deep-surface: #2a2218;
  --parchment-sheen: linear-gradient(105deg,
      rgba(30, 24, 17, 1) 0%, rgba(48, 38, 27, 1) 45%,
      rgba(24, 19, 14, 1) 55%, rgba(30, 24, 17, 1) 100%);
  --parchment-highlight: #fff3d2;
  --combatant-pulse-high: 0 0 18px rgba(227, 198, 139, 0.9);
  --combatant-pulse-danger: 0 0 18px rgba(211, 106, 97, 0.9);
  --combatant-hover-surface: rgba(238, 232, 220, 0.04);
  --danger-soft-surface: rgba(211, 106, 97, 0.10);
  --danger-faint-surface: rgba(211, 106, 97, 0.05);
```

- [ ] **Step 5: Replace every UI literal with its exact semantic owner**

Apply this mapping:

| Current source | Replacement |
|---|---|
| `base.css` vignette gradient | `var(--vignette-color)` inside the gradient |
| `base.css` selection background/foreground | `var(--selection-surface)` / `var(--selection-foreground)` |
| `base.css` shortcut backdrop | `var(--scrim-standard)` |
| `components.css` gold glow | `var(--glow-primary)` |
| `components.css` modal/overlay/backdrop black paint | `var(--scrim-standard)` |
| `components.css` combatant hover literal | `var(--combatant-hover-surface)` |
| `components.css` spotlight radial gradient | `var(--spotlight-scrim)` |
| `components.css` deep parchment surface | `var(--parchment-deep-surface)` |
| `components.css` side-sheet shadow | `var(--shadow-floating)` |
| `components.css` candle overlay values | `var(--scrim-standard)` |
| `components.css` hairline shadow | `var(--shadow-hairline)` |
| `components.css` parchment gradient | `var(--parchment-sheen)` |
| `components.css` parchment/card shadows | `var(--shadow-floating)` |
| `components.css` sheen midpoint | `var(--parchment-highlight)` |
| `components.css` high/danger pulse keyframes | `text-shadow: none` at 0%/100%, plus `var(--combatant-pulse-high)` / `var(--combatant-pulse-danger)` at 35% |
| `components.css` source-filter tint | `var(--selection-surface)` |
| `cockpit.css` overlay | `var(--scrim-standard)` |
| `cockpit.css` warning fallbacks | `var(--state-warning-surface)` / `var(--state-warning)` |
| `cockpit-modules.css` neutral tint | `var(--neutral-hover-surface)` |
| `cockpit-modules.css` two danger tints | `var(--danger-faint-surface)` / `var(--danger-soft-surface)` |
| `surfaces.css` danger fallbacks | `var(--state-danger)` |
| map editor embedded-style shadows | `var(--shadow-floating)` |

Move the encounter concentration inline paint to a `.conc-check` rule in
`encounter.css`:

```css
.conc-check {
  margin-bottom: var(--space-xs);
  padding: var(--space-xs);
  border: 1px solid var(--state-concentration-border);
  border-radius: var(--radius);
  background: var(--state-concentration-surface);
}
```

Remove the inline `style` from `_summary-modal.html` and use the existing
`.modal-overlay`/`.modal-panel` structure. Add to `components.css`:

```css
.attunement-warning {
  margin-top: var(--space-xs);
  color: var(--state-danger);
}
```

Then remove the inline style from `_attunement-warn.html`.

- [ ] **Step 6: Run raw-value, token, and visual-role contracts**

Run:

```bash
./mvnw -Dtest='RawVisualValueContractTest,DesignTokenContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest' test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates/encounter/_tracker.html src/main/resources/templates/encounter/_summary-modal.html src/main/resources/templates/treasury/_attunement-warn.html src/main/resources/templates/maps/editor.html src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java src/test/java/dev/hendrikhoemberg/dmhelper/config/RawVisualValueContractTest.java
git commit -m "refactor: confine ui colors to semantic tokens"
```

## Task 5: Freeze legacy aliases under non-increasing budgets

**Files:**

- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java`

**Interfaces:**

- Consumes: application CSS and template source, excluding `tokens.css`.
- Produces: `CssRules.tokenReferenceCount(String token)`.
- Rule: a migrated stage reduces the affected numbers; no stage may raise one or add a
  new alias.

- [ ] **Step 1: Add exact reference counting**

```java
static long tokenReferenceCount(String token) {
    String source = allApplicationCss() + "\n" + allTemplateMarkup();
    return Pattern.compile("var\\(\\s*" + Pattern.quote(token) + "(?=\\s*[,)]\\s*)")
            .matcher(source)
            .results()
            .count();
}
```

- [ ] **Step 2: Write the migration budget contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyVisualAliasContractTest {

    private static final Map<String, Long> MAX_REFERENCES = budgets();

    @Test
    void legacyVisualReferencesNeverIncrease() {
        MAX_REFERENCES.forEach((token, maximum) ->
                assertThat(CssRules.tokenReferenceCount(token))
                        .as("%s migration budget", token)
                        .isLessThanOrEqualTo(maximum));
    }

    @Test
    void compatibilitySectionDefinesOnlyReviewedAliases() {
        String tokens = CssRules.read("tokens.css");
        int start = tokens.indexOf("/* Temporary migration aliases.");
        int end = tokens.indexOf("/* End temporary migration aliases. */");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String compatibility = tokens.substring(start, end);
        var matcher = java.util.regex.Pattern.compile("(--color-[a-z0-9-]+)\\s*:")
                .matcher(compatibility);
        var defined = new java.util.LinkedHashSet<String>();
        while (matcher.find()) defined.add(matcher.group(1));
        assertThat(defined).containsExactlyInAnyOrderElementsOf(MAX_REFERENCES.keySet());
    }

    private static Map<String, Long> budgets() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("--color-text-muted", 182L);
        values.put("--color-border", 173L);
        values.put("--color-text", 113L);
        values.put("--color-bg", 82L);
        values.put("--color-accent", 67L);
        values.put("--color-surface", 46L);
        values.put("--color-border-strong", 40L);
        values.put("--color-danger", 33L);
        values.put("--color-warning", 32L);
        values.put("--color-surface-hover", 24L);
        values.put("--color-success", 23L);
        values.put("--color-gold-soft", 12L);
        values.put("--color-ember", 9L);
        values.put("--color-overlay", 3L);
        values.put("--color-text-secondary", 2L);
        values.put("--color-surface-muted", 2L);
        values.put("--color-shield", 2L);
        values.put("--color-bg-elevated", 2L);
        values.put("--color-warning-text", 0L);
        values.put("--color-warning-bg", 0L);
        values.put("--color-shield-soft", 1L);
        values.put("--color-info", 1L);
        values.put("--color-concentration", 1L);
        values.put("--color-border-subtle", 1L);
        values.put("--color-attack-bonus", 0L);
        values.put("--color-accent-hover", 0L);
        values.put("--color-danger-hover", 0L);
        values.put("--color-shield-hover", 0L);
        return Map.copyOf(values);
    }
}
```

Wrap the compatibility declarations in `tokens.css` with the exact opening comment used
by the test and this closing marker:

```css
  /* End temporary migration aliases. */
```

- [ ] **Step 3: Run the contract against the post-Task-4 source**

Run:

```bash
./mvnw -Dtest=LegacyVisualAliasContractTest test
```

Expected: PASS. When Task 4 lowered a count, lower that map entry to the observed value;
the exact post-migration limits are already printed in this task and must not be raised.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/tokens.css src/test/java/dev/hendrikhoemberg/dmhelper/config/CssRules.java src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java
git commit -m "test: bound legacy visual aliases"
```

## Task 6: Establish the repository-owned SVG icon system

**Files:**

- Create: `src/main/resources/static/icons/ui.svg`
- Create: `src/main/resources/templates/common/_icon.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/IconSystemContractTest.java`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/resources/templates/common/_empty-state.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`

**Interfaces:**

- Consumes: `--text-primary`, `--text-secondary`, and semantic state tokens through
  `currentColor`.
- Produces: `icon(name)` fragment and `.ui-icon`, `.ui-icon--sm`, `.ui-icon--lg`.
- Symbol IDs: `campaign`, `session`, `adventure`, `encounter`, `map`, `handout`, `audio`,
  `party`, `quest`, `npc`, `location`, `faction`, `calendar`, `notes`, `treasury`,
  `ledger`, `library`, `table`, `trap`, `hazard`, `search`, `dice`, `more`,
  `chevron-left`, `chevron-right`, `chevron-down`, `plus`, `edit`, `trash`, `save`,
  `close`, `warning`, `check`, `info`, `shield`, `lock`, `eye`, `undo`, `redo`,
  `upload`, `download`, and `help`.

- [ ] **Step 1: Write the failing sprite and fragment contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IconSystemContractTest {

    private static final Path SPRITE =
            Path.of("src/main/resources/static/icons/ui.svg");
    private static final Path FRAGMENT =
            Path.of("src/main/resources/templates/common/_icon.html");
    private static final Set<String> NAMES = Set.of(
            "campaign", "session", "adventure", "encounter", "map", "handout",
            "audio", "party", "quest", "npc", "location", "faction", "calendar",
            "notes", "treasury", "ledger", "library", "table", "trap", "hazard",
            "search", "dice", "more", "chevron-left", "chevron-right",
            "chevron-down", "plus", "edit", "trash", "save", "close", "warning",
            "check", "info", "shield", "lock", "eye", "undo", "redo", "upload",
            "download", "help");

    @Test
    void spritePublishesTheReviewedCatalog() throws Exception {
        var document = Jsoup.parse(Files.readString(SPRITE));
        var ids = document.select("symbol[id]").eachAttr("id");
        assertThat(ids).containsExactlyInAnyOrderElementsOf(
                NAMES.stream().map(name -> "icon-" + name).toList());
        assertThat(document.select("symbol:not([viewBox='0 0 24 24'])")).isEmpty();
    }

    @Test
    void iconFragmentIsDecorativeAndUsesTheNamedSymbol() throws Exception {
        String fragment = Files.readString(FRAGMENT);
        assertThat(fragment)
                .contains("th:fragment=\"icon(name)\"")
                .contains("class=\"ui-icon\"")
                .contains("aria-hidden=\"true\"")
                .contains("focusable=\"false\"")
                .contains("@{/icons/ui.svg} + '#icon-' + ${name}");
    }

    @Test
    void cssProvidesOnlyTheThreeApprovedSizeRoles() {
        String css = CssRules.read("components.css");
        assertThat(css)
                .contains(".ui-icon {")
                .contains("width: 20px", "height: 20px")
                .contains(".ui-icon--sm { width: 16px; height: 16px; }")
                .contains(".ui-icon--lg { width: 24px; height: 24px; }");
    }
}
```

- [ ] **Step 2: Run the test and verify both files are missing**

Run:

```bash
./mvnw -Dtest=IconSystemContractTest test
```

Expected: ERROR/FAIL because `ui.svg` and `_icon.html` do not exist.

- [ ] **Step 3: Create the sprite with the exact catalog and visual grammar**

Create `ui.svg` with this complete catalog:

```svg
<svg xmlns="http://www.w3.org/2000/svg">
  <defs>
    <symbol id="icon-campaign" viewBox="0 0 24 24">
      <path d="M4 21V9l3 2V7l3 2V5l2-2 2 2v4l3-2v4l3-2v12"/>
      <path d="M2 21h20M9 21v-5h6v5"/>
    </symbol>
    <symbol id="icon-session" viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4Z"/>
    </symbol>
    <symbol id="icon-adventure" viewBox="0 0 24 24">
      <path d="M3 5.5A4.5 4.5 0 0 1 7.5 4H11v16H7.5A4.5 4.5 0 0 0 3 21.5Z"/>
      <path d="M21 5.5A4.5 4.5 0 0 0 16.5 4H13v16h3.5a4.5 4.5 0 0 1 4.5 1.5Z"/>
    </symbol>
    <symbol id="icon-encounter" viewBox="0 0 24 24">
      <path d="m5 3 7 7M3 5l4-2 2 4-6 6M19 3l-7 7M21 5l-4-2-2 4 6 6"/>
      <path d="m8 14-4 7M16 14l4 7"/>
    </symbol>
    <symbol id="icon-map" viewBox="0 0 24 24">
      <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z"/>
      <path d="M9 3v15M15 6v15"/>
    </symbol>
    <symbol id="icon-handout" viewBox="0 0 24 24">
      <rect x="4" y="3" width="16" height="18" rx="2"/>
      <path d="M8 8h8M8 12h8M8 16h5"/>
    </symbol>
    <symbol id="icon-audio" viewBox="0 0 24 24">
      <path d="M9 18V6l10-2v12"/><circle cx="6" cy="18" r="3"/><circle cx="16" cy="16" r="3"/>
    </symbol>
    <symbol id="icon-party" viewBox="0 0 24 24">
      <circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.5"/>
      <path d="M3 20v-2a6 6 0 0 1 12 0v2M15 14a5 5 0 0 1 6 4.9V20"/>
    </symbol>
    <symbol id="icon-quest" viewBox="0 0 24 24">
      <path d="M5 21V3M6 4h12l-3 4 3 4H6"/>
    </symbol>
    <symbol id="icon-npc" viewBox="0 0 24 24">
      <circle cx="12" cy="8" r="4"/><path d="M5 21a7 7 0 0 1 14 0"/>
    </symbol>
    <symbol id="icon-location" viewBox="0 0 24 24">
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/>
      <circle cx="12" cy="10" r="2.5"/>
    </symbol>
    <symbol id="icon-faction" viewBox="0 0 24 24">
      <path d="M5 21V3M6 4h12l-2 4 2 4H6"/><path d="M9 16h8"/>
    </symbol>
    <symbol id="icon-calendar" viewBox="0 0 24 24">
      <rect x="3" y="5" width="18" height="16" rx="2"/>
      <path d="M7 3v4M17 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01"/>
    </symbol>
    <symbol id="icon-notes" viewBox="0 0 24 24">
      <path d="M5 3h12a2 2 0 0 1 2 2v16H7a2 2 0 0 1-2-2Z"/>
      <path d="M8 3v18M11 8h5M11 12h5M11 16h3"/>
    </symbol>
    <symbol id="icon-treasury" viewBox="0 0 24 24">
      <path d="m12 3 7 6-7 12L5 9Z"/><path d="M5 9h14M9 9l3 12 3-12M8 4l1 5M16 4l-1 5"/>
    </symbol>
    <symbol id="icon-ledger" viewBox="0 0 24 24">
      <rect x="4" y="3" width="16" height="18" rx="2"/>
      <path d="M8 7h8M8 11h8M8 15h4M16 15h.01"/>
    </symbol>
    <symbol id="icon-library" viewBox="0 0 24 24">
      <path d="M4 4h4v16H4ZM10 4h4v16h-4ZM16 5l3-1 4 15-4 1Z"/>
    </symbol>
    <symbol id="icon-table" viewBox="0 0 24 24">
      <rect x="3" y="4" width="18" height="16" rx="2"/>
      <path d="M3 10h18M9 4v16M15 4v16"/>
    </symbol>
    <symbol id="icon-trap" viewBox="0 0 24 24">
      <path d="M4 18h16L12 4Z"/><path d="m8 15 2-3 2 3 2-3 2 3"/>
    </symbol>
    <symbol id="icon-hazard" viewBox="0 0 24 24">
      <path d="M3 18 9 8l3 5 3-8 6 13Z"/><path d="M3 21c2-2 4 2 6 0s4 2 6 0 4 2 6 0"/>
    </symbol>
    <symbol id="icon-search" viewBox="0 0 24 24">
      <circle cx="11" cy="11" r="7"/>
      <path d="m20 20-4-4"/>
    </symbol>
    <symbol id="icon-dice" viewBox="0 0 24 24">
      <path d="m12 2 9 7-3 11H6L3 9Z"/><path d="m3 9 9 3 9-3M12 12v8"/>
      <circle cx="12" cy="7" r=".5"/>
    </symbol>
    <symbol id="icon-more" viewBox="0 0 24 24">
      <circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>
    </symbol>
    <symbol id="icon-plus" viewBox="0 0 24 24">
      <path d="M12 5v14M5 12h14"/>
    </symbol>
    <symbol id="icon-edit" viewBox="0 0 24 24">
      <path d="M12 20H5a1 1 0 0 1-1-1v-7L15 1l4 4L8 16l-4 1"/>
      <path d="m13 3 4 4"/>
    </symbol>
    <symbol id="icon-trash" viewBox="0 0 24 24">
      <path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14M10 11v6M14 11v6"/>
    </symbol>
    <symbol id="icon-save" viewBox="0 0 24 24">
      <path d="M4 3h13l3 3v15H4Z"/><path d="M8 3v6h8V3M8 21v-7h8v7"/>
    </symbol>
    <symbol id="icon-close" viewBox="0 0 24 24">
      <path d="m6 6 12 12M18 6 6 18"/>
    </symbol>
    <symbol id="icon-warning" viewBox="0 0 24 24">
      <path d="M12 3 2 21h20Z"/><path d="M12 9v5M12 18h.01"/>
    </symbol>
    <symbol id="icon-check" viewBox="0 0 24 24">
      <path d="m5 12 4 4L19 6"/>
    </symbol>
    <symbol id="icon-info" viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>
    </symbol>
    <symbol id="icon-shield" viewBox="0 0 24 24">
      <path d="M12 3 4 6v6c0 5 3.4 8 8 10 4.6-2 8-5 8-10V6Z"/>
    </symbol>
    <symbol id="icon-lock" viewBox="0 0 24 24">
      <rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>
    </symbol>
    <symbol id="icon-eye" viewBox="0 0 24 24">
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/>
      <circle cx="12" cy="12" r="3"/>
    </symbol>
    <symbol id="icon-undo" viewBox="0 0 24 24">
      <path d="m9 7-5 5 5 5M5 12h8a6 6 0 0 1 6 6"/>
    </symbol>
    <symbol id="icon-redo" viewBox="0 0 24 24">
      <path d="m15 7 5 5-5 5M19 12h-8a6 6 0 0 0-6 6"/>
    </symbol>
    <symbol id="icon-upload" viewBox="0 0 24 24">
      <path d="M12 16V3M7 8l5-5 5 5M4 14v7h16v-7"/>
    </symbol>
    <symbol id="icon-download" viewBox="0 0 24 24">
      <path d="M12 3v13M7 11l5 5 5-5M4 14v7h16v-7"/>
    </symbol>
    <symbol id="icon-help" viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 3.4 2.3c-.9.4-.9 1.1-.9 2.2M12 17.5h.01"/>
    </symbol>
    <symbol id="icon-chevron-left" viewBox="0 0 24 24">
      <path d="m15 18-6-6 6-6"/>
    </symbol>
    <symbol id="icon-chevron-right" viewBox="0 0 24 24">
      <path d="m9 18 6-6-6-6"/>
    </symbol>
    <symbol id="icon-chevron-down" viewBox="0 0 24 24">
      <path d="m6 9 6 6 6-6"/>
    </symbol>
  </defs>
</svg>
```

Do not add embedded styles, text, masks, raster images, or per-icon stroke widths.

- [ ] **Step 4: Create the fragment and sizing CSS**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<svg th:fragment="icon(name)" class="ui-icon"
     aria-hidden="true" focusable="false">
    <use th:href="@{/icons/ui.svg} + '#icon-' + ${name}"></use>
</svg>
</html>
```

```css
.ui-icon {
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.75;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.ui-icon--sm { width: 16px; height: 16px; }
.ui-icon--lg { width: 24px; height: 24px; }
```

- [ ] **Step 5: Preserve empty-state signatures while replacing Unicode icon content**

In `common/_empty-state.html`, keep all existing fragment signatures. Replace
`th:text="${icon}"` with:

```html
<div th:if="${icon != null}" class="empty-state__icon" aria-hidden="true">
    <th:block th:replace="~{common/_icon :: icon(name=${icon})}"></th:block>
</div>
```

Update `UiPolishContractTest` to assert named values (`encounter`, `treasury`,
`calendar`, `map`, `handout`, `party`) rather than the existing Unicode glyphs. Updating
the six calling templates to those names belongs in the same commit because the old
strings are no longer valid sprite IDs.

- [ ] **Step 6: Run the icon and empty-state contracts**

Run:

```bash
./mvnw -Dtest='IconSystemContractTest,UiPolishContractTest' test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/icons/ui.svg src/main/resources/templates/common/_icon.html src/main/resources/templates/common/_empty-state.html src/main/resources/static/css/components.css src/main/resources/templates/encounter/list.html src/main/resources/templates/treasury/list.html src/main/resources/templates/calendar/_timeline-list.html src/main/resources/templates/maps/list.html src/main/resources/templates/handout/list.html src/main/resources/templates/party/_roster.html src/test/java/dev/hendrikhoemberg/dmhelper/config/IconSystemContractTest.java src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java
git commit -m "feat: add shared application icon system"
```

## Task 7: Apply neutral document, control, focus, typography, and gold roles

**Files:**

- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`

**Interfaces:**

- Consumes: semantic palette and derived tokens from Tasks 3-4.
- Produces: neutral default canvas/workspace, inset control surface, strong interactive
  boundary, gold focus, one primary-button paint, and contained immersive texture.

- [ ] **Step 1: Rewrite the gold reference vocabulary before changing component CSS**

In `GoldAccentContractTest`, replace `GOLD_REFERENCES` with:

```java
private static final List<String> GOLD_REFERENCES = List.of(
        "--action-primary", "--action-primary-hover", "--selection-accent",
        "--selection-surface", "--focus-ring", "--glow-primary",
        "--gold-sheen", "--gold-sweep",
        "--color-accent", "--color-accent-hover", "--color-gold-soft",
        "--color-attack-bonus");
```

Keep the earned state/identity model, but remove `.badge-info` from
`IDENTITY_SURFACES`: information now owns blue. Keep the truly in-world book, cover,
handout, statblock, read-aloud, campaign-sigil, and critical-result exceptions. Update
neutral assertions to expect `var(--text-primary)` and `var(--border-strong)`.

- [ ] **Step 2: Tighten typography selector ownership**

Use these exact role sets in `TypographyRoleContractTest`:

```java
private static final Set<String> DISPLAY_SELECTORS = Set.of(
        "[data-display-title]",
        ".navbar-brand",
        ".book-cover h3");

private static final Set<String> BOOK_SELECTORS = Set.of(
        ".statblock-render",
        ".note-body",
        ".read-aloud",
        ".structured-read-aloud > p",
        ".scene-body",
        ".handout-prose");
```

The later shell plan will remove the redundant `.navbar-brand` exception when the
wordmark receives its permanent class. Remove `.page-header h1`,
`.cockpit-topbar__title`, and `.appnav-home`; all principal titles must opt in through
`data-display-title` and the rail is system UI.

- [ ] **Step 3: Run the focused contracts and verify current selectors/paint fail**

Run:

```bash
./mvnw -Dtest='GoldAccentContractTest,TypographyRoleContractTest,UiPolishContractTest' test
```

Expected: FAIL on legacy gold references, legacy values, and display-face rail/cockpit
selectors until CSS is migrated.

- [ ] **Step 4: Apply the semantic base paint**

Use these declarations in `base.css`:

```css
body {
  min-height: 100vh;
  color: var(--text-primary);
  background: var(--surface-canvas);
}

body:has(.cockpit-topbar),
body:has(.battle-container),
body:has(.editor-container) {
  background-image: var(--texture-paper);
}

body::before {
  background: none;
}

body:has(.cockpit-topbar)::before,
body:has(.battle-container)::before,
body:has(.editor-container)::before {
  background: radial-gradient(
      ellipse at center,
      transparent 55%,
      var(--vignette-color) 100%);
}

:where(
  input[type="text"], input[type="number"], input[type="search"],
  input[type="email"], input[type="password"], input[type="url"],
  input[type="tel"], input[type="date"], input[type="time"],
  select, textarea
) {
  color: var(--text-primary);
  background: var(--surface-inset);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
}

:where(
  input[type="text"], input[type="number"], input[type="search"],
  input[type="email"], input[type="password"], input[type="url"],
  input[type="tel"], input[type="date"], input[type="time"],
  select, textarea
):where(:focus) {
  border-color: var(--focus-ring);
}

:where(a, button, input, select, textarea, [tabindex]):focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
  border-radius: var(--radius);
  transition: outline-offset var(--duration-micro) var(--ease-out);
}
```

Do not set `outline: none` on focused controls. Let `:focus-visible` supply the keyboard
ring and use border color for pointer/programmatic focus.

- [ ] **Step 5: Apply the semantic shared-component paint**

The core component declarations must resolve as follows:

```css
.card {
  color: var(--text-primary);
  background: var(--surface-panel);
  border: 1px solid var(--border-subtle);
}

.card:hover {
  background: var(--surface-raised);
  border-color: var(--border-strong);
}

.card[aria-current="true"],
.card.is-selected {
  border-color: var(--selection-accent);
  box-shadow: inset 3px 0 0 var(--selection-accent);
}

.btn {
  color: var(--text-primary);
  background: var(--surface-raised);
  border: 1px solid var(--border-strong);
}

.btn-primary {
  color: var(--action-on-primary);
  background: var(--action-primary);
  border-color: var(--action-primary);
}

.btn-primary:hover {
  color: var(--action-on-primary);
  background: var(--action-primary-hover);
  border-color: var(--action-primary-hover);
}

.btn-danger {
  color: var(--surface-canvas);
  background: var(--state-danger);
  border-color: var(--state-danger);
}

.btn-ghost {
  color: var(--text-secondary);
  background: transparent;
  border-color: transparent;
}

.btn-ghost:hover {
  color: var(--text-primary);
  background: var(--neutral-hover-surface);
  border-color: var(--border-subtle);
}
```

Migrate badges, banners, toasts, combat states, and status controls to the matching
`--state-*-surface`, `--state-*-border`, and `--state-*` foreground. Information uses
`--state-info`, shield/player-safe uses `--state-shield`, and concentration uses only
`--state-concentration`. Remove generic decorative gold borders.

- [ ] **Step 6: Update affected source-level assertions**

In `UiPolishContractTest`, expect:

```java
.contains("background: var(--surface-inset)")
.contains("border: 1px solid var(--border-strong)")
.contains("border-color: var(--focus-ring)")
```

Retain all behavioral assertions about control types, shared empty states, bounded grids,
card hierarchy, field focus, and skill keys.

- [ ] **Step 7: Run the complete foundation contract set**

Run:

```bash
./mvnw -Dtest='DesignTokenContractTest,RawVisualValueContractTest,LegacyVisualAliasContractTest,GoldAccentContractTest,TypographyRoleContractTest,TypeScaleContractTest,ElevationModelContractTest,MotionBudgetContractTest,ControlConsistencyContractTest,UiPolishContractTest' test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/css/base.css src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/config/GoldAccentContractTest.java src/test/java/dev/hendrikhoemberg/dmhelper/config/TypographyRoleContractTest.java src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java
git commit -m "feat: apply neutral hierarchy and scarce gold"
```

## Task 8: Verify computed hierarchy and capture the foundation review set

**Files:**

- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/visual/VisualFoundationRenderGateTest.java`
- Modify only if a real browser defect is exposed: the CSS/template files owned by Tasks
  3-7.

**Interfaces:**

- Consumes: `PreparationSurfaceFixture`, `PopulatedCampaignFixture`,
  `BrowserFailureCollector`, and the semantic CSS roles.
- Produces: computed-style and focus assertions plus PNG evidence under
  `target/visual-foundations/`.

- [ ] **Step 1: Create a browser gate with representative routes**

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
`@TestInstance(PER_CLASS)`, one shared headless Chromium browser, a fresh page per route,
and the existing `BrowserFailureCollector`. Seed both fixtures and expose these paths:

```java
record ReviewPage(String name, String path) {}

List<ReviewPage> reviewPages() {
    String campaign = "/campaigns/" + populated.campaignId();
    String preparedCampaign = "/campaigns/" + prepared.campaignId();
    return List.of(
            new ReviewPage("campaign-home", campaign),
            new ReviewPage("encounter-index", campaign + "/encounters"),
            new ReviewPage("scene-detail",
                    preparedCampaign + "/adventures/" + prepared.adventureId()
                            + "/scenes/" + prepared.sceneId()),
            new ReviewPage("encounter-form", campaign + "/encounters/new"),
            new ReviewPage("party-operational", campaign + "/party"),
            new ReviewPage("session-cockpit", campaign + "/session"),
            new ReviewPage("map-editor",
                    preparedCampaign + "/maps/" + prepared.mapId() + "/edit"));
}
```

The fixture accessors above are `campaignId()`, `adventureId()`, `sceneId()`, and
`mapId()`; do not add production model data for this gate.

- [ ] **Step 2: Assert computed role separation**

For each route at 1440x900, evaluate:

```javascript
() => {
  const style = getComputedStyle(document.documentElement);
  return {
    canvas: style.getPropertyValue('--surface-canvas').trim(),
    panel: style.getPropertyValue('--surface-panel').trim(),
    raised: style.getPropertyValue('--surface-raised').trim(),
    text: style.getPropertyValue('--text-primary').trim(),
    gold: style.getPropertyValue('--action-primary').trim(),
    body: getComputedStyle(document.body).backgroundColor
  };
}
```

Assert the five token values are `#101113`, `#1d1f24`, `#24262c`, `#eee8dc`, and
`#c9a35c`, and the body background computes to `rgb(16, 17, 19)`. Also assert:

```javascript
() => {
  const card = document.querySelector('.card');
  const primary = document.querySelector('.btn-primary');
  return {
    cardBorder: card ? getComputedStyle(card).borderColor : null,
    primaryBackground: primary ? getComputedStyle(primary).backgroundColor : null
  };
}
```

When present, card borders must not equal gold and the primary background must equal
`rgb(201, 163, 92)`.

- [ ] **Step 3: Assert visible keyboard focus**

On every route, focus the first enabled visible interactive control, then evaluate its
computed `outlineStyle`, `outlineWidth`, and `outlineColor`. Assert solid, at least 2px,
and `rgb(201, 163, 92)`. Use the same visible-control iteration approach as
`ViewportAccessibilityGateTest` so hidden htmx/Alpine controls are skipped.

- [ ] **Step 4: Assert texture is contained**

For campaign/index/detail/form/party pages, assert the computed body
`backgroundImage` is `none`. For cockpit and editor, permit either `none` or the
repository paper texture; assert no standard `.card` uses the texture. This protects the
approved rule that warmth and material texture belong to selected in-world moments.

- [ ] **Step 5: Capture review screenshots**

Create the output directory in `@BeforeAll`:

```java
Files.createDirectories(Path.of("target/visual-foundations"));
```

After assertions, capture:

```java
page.screenshot(new Page.ScreenshotOptions()
        .setPath(Path.of("target/visual-foundations/" + reviewPage.name() + ".png"))
        .setFullPage(true));
```

Do not approve screenshots in code. The human review asks: Does charcoal chrome separate
from content? Is the primary action obvious? Is gold scarce? Is required text clear? Do
semantic states remain identifiable without relying only on color?

- [ ] **Step 6: Run the browser gate**

Run:

```bash
./mvnw -Dtest=VisualFoundationRenderGateTest test
```

Expected: PASS with seven PNGs and no console error, unhandled rejection, or failed
application request.

- [ ] **Step 7: Inspect every screenshot**

Open the generated files in `target/visual-foundations/` and compare them with the
corresponding images in `artifacts/ui-review/`. Fix only foundation defects in this task:
incorrect palette resolution, illegible text, accidental gold, raw brown chrome,
invisible focus, or texture leaking onto standard pages. Record structural concerns for
the shell/feature stages instead of folding them into this stage.

- [ ] **Step 8: Re-run and commit**

```bash
./mvnw -Dtest='VisualFoundationRenderGateTest,TypographyRenderGateTest,SurfaceNestingGateTest' test
git add src/test/java/dev/hendrikhoemberg/dmhelper/visual/VisualFoundationRenderGateTest.java src/main/resources/static/css src/main/resources/templates
git commit -m "test: gate visual foundation rendering"
```

Expected: PASS.

## Task 9: Run the stage release gate and hand off the shell plan

**Files:**

- Modify: `docs/superpowers/plans/2026-07-31-whole-product-ui-redesign-program.md`
  only to append completion evidence and final observed legacy budgets.

**Interfaces:**

- Consumes: every commit and test from Tasks 1-8.
- Produces: a verified foundation baseline for the shell/archetypes plan.

- [ ] **Step 1: Run the complete focused gate**

```bash
./mvnw -Dtest='CssInventoryContractTest,ColorContrastTest,DesignTokenContractTest,RawVisualValueContractTest,LegacyVisualAliasContractTest,IconSystemContractTest,GoldAccentContractTest,TypographyRoleContractTest,TypeScaleContractTest,ElevationModelContractTest,MotionBudgetContractTest,ControlConsistencyContractTest,UiPolishContractTest,VisualFoundationRenderGateTest,TypographyRenderGateTest,SurfaceNestingGateTest' test
```

Expected: PASS.

- [ ] **Step 2: Run full regression**

```bash
./mvnw test
```

Expected: BUILD SUCCESS with no failed test.

- [ ] **Step 3: Check repository hygiene**

```bash
git diff --check
git status --short
```

Expected: no whitespace error; only intentional uncommitted documentation evidence, if
it has not yet been committed.

- [ ] **Step 4: Record evidence in the delivery program**

Under Stage 1, append:

- the focused-gate result and test count;
- the full-suite result and test count;
- paths to the seven `target/visual-foundations/` images;
- the final `LegacyVisualAliasContractTest` budgets;
- any structural observations explicitly deferred to Stage 2.

- [ ] **Step 5: Commit the evidence**

```bash
git add docs/superpowers/plans/2026-07-31-whole-product-ui-redesign-program.md
git commit -m "docs: record visual foundation gate"
```

- [ ] **Step 6: Begin the next stage from the verified baseline**

Use `superpowers:writing-plans` to create
`docs/superpowers/plans/2026-08-01-ui-redesign-shell-and-archetypes.md`, inventorying the
finished foundation interfaces before defining its TDD tasks. Do not rewrite or bypass
the token, icon, focus, motion, elevation, or migration-budget contracts established
here.
