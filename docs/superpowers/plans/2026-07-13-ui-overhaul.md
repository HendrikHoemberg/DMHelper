# DMHelper UI/UX Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic navy/purple UI with the approved "dark grimoire, selective ornament" design system and restructure navigation (grouped left sidebar + session cockpit), per `docs/superpowers/specs/2026-07-13-ui-overhaul-design.md`.

**Architecture:** Re-theme first (swap CSS custom property values under their existing names so the whole app flips in one commit), then restructure screen-by-screen. CSS splits into `tokens.css` / `base.css` / `components.css` / `book.css` / `cockpit.css` — plain static files linked from the head fragment, no build step. Templates migrate from the navbar+right-sidebar layout to a top-bar + grouped-left-nav shell.

**Tech Stack:** Spring Boot 4.1 (Java 25), Thymeleaf, htmx, Alpine.js, Konva (all vendored), plain CSS. Tests: JUnit 5 + `@WebMvcTest`/MockMvc + Playwright smoke test, all via `./mvnw test`.

## Global Constraints

- **Spring Boot 4.1**: test imports are `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` and `org.springframework.test.context.bean.override.mockito.MockitoBean` (NOT the Boot-3 packages your training data suggests). Copy imports from `CampaignControllerTest.java` when in doubt.
- **No build step, no runtime CDN**: all assets (including fonts) are static files under `src/main/resources/static/`. The app must work fully offline.
- **Ornament budget (from spec §2)**: gold hairlines, tapered rules, drop caps in book content. Nothing else — no textures, no filigree. Do not add decoration beyond what a task specifies.
- **Token discipline**: no raw hex colors outside `static/css/tokens.css` in any CSS you write. (Existing raw hex gets removed as each file is touched; Task 16 sweeps stragglers.)
- **Keep existing CSS variable names** (`--color-bg`, `--color-surface`, …) — ~50 templates consume them.
- **Every task ends with `./mvnw test` green** and a commit. The app must be usable after every task.
- **DM Mode is a safety feature**: `body.dm-mode-off` hiding rules (`app.css` lines under "DM Mode") must keep working after every task that touches the navbar, tracker, or battle screen.
- Run the app for eyeball checks with `./mvnw spring-boot:run`, then open `http://localhost:8080/campaigns`.

## File Structure (end state)

```
static/css/tokens.css        design tokens + @font-face (only file with raw values)
static/css/base.css          reset, body/typography defaults, app shell layout, top bar
static/css/components.css    buttons, cards, forms, badges, tables, tracker, dice, palette, utilities
static/css/book.css          statblock renderer, .note-body book surfaces, read-aloud, wiki links
static/css/cockpit.css       battle/session screen layout (extracted from battle.html inline <style>)
static/fonts/                cinzel + alegreya woff2 (vendored)
templates/fragments/navbar.html    slim top bar (rewritten in place, fragment name stays "navbar")
templates/fragments/_appnav.html   grouped left nav (new)
templates/fragments/sidebar.html   DELETED in Task 6
gamemap/web/SessionController.java  GET /campaigns/{id}/session redirect (new)
```

---

### Task 1: Vendor Cinzel + Alegreya fonts

**Files:**
- Create: `src/main/resources/static/fonts/cinzel-latin-wght-normal.woff2`
- Create: `src/main/resources/static/fonts/alegreya-latin-wght-normal.woff2`
- Create: `src/main/resources/static/fonts/alegreya-latin-wght-italic.woff2`
- Modify: `VENDOR.md`

**Interfaces:**
- Produces: the three woff2 files at the exact paths above; Task 2's `@font-face` rules reference them as `/fonts/<name>.woff2`.

- [ ] **Step 1: Download the variable-font woff2 files (SIL OFL, latin subset)**

```bash
mkdir -p src/main/resources/static/fonts
curl -fL -o src/main/resources/static/fonts/cinzel-latin-wght-normal.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/cinzel:vf@latest/latin-wght-normal.woff2"
curl -fL -o src/main/resources/static/fonts/alegreya-latin-wght-normal.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/alegreya:vf@latest/latin-wght-normal.woff2"
curl -fL -o src/main/resources/static/fonts/alegreya-latin-wght-italic.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/alegreya:vf@latest/latin-wght-italic.woff2"
```

If a URL 404s, get the equivalent latin variable woff2 from https://gwfh.mranftl.com (google-webfonts-helper) instead — same target filenames.

- [ ] **Step 2: Verify the files are real WOFF2**

Run: `file src/main/resources/static/fonts/*.woff2`
Expected: each line says `Web Open Font Format (Version 2)`. Also check each file is > 20 KB (`ls -la`).

- [ ] **Step 3: Record in VENDOR.md**

Compute hashes: `sha256sum src/main/resources/static/fonts/*.woff2`, then append to the table in `VENDOR.md`:

```markdown
| `fonts/cinzel-latin-wght-normal.woff2` | (fontsource latest) | https://cdn.jsdelivr.net/fontsource/fonts/cinzel:vf@latest/latin-wght-normal.woff2 | <sha256> |
| `fonts/alegreya-latin-wght-normal.woff2` | (fontsource latest) | https://cdn.jsdelivr.net/fontsource/fonts/alegreya:vf@latest/latin-wght-normal.woff2 | <sha256> |
| `fonts/alegreya-latin-wght-italic.woff2` | (fontsource latest) | https://cdn.jsdelivr.net/fontsource/fonts/alegreya:vf@latest/latin-wght-italic.woff2 | <sha256> |
```

And add below the table: `Cinzel and Alegreya are licensed under the SIL Open Font License 1.1.`

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/fonts VENDOR.md
git commit -m "feat: vendor Cinzel and Alegreya fonts (SIL OFL, latin woff2)"
```

---

### Task 2: tokens.css — the grimoire flip

**Files:**
- Create: `src/main/resources/static/css/tokens.css`
- Modify: `src/main/resources/static/css/app.css` (delete lines 1–35, the `:root` block)
- Modify: `src/main/resources/templates/fragments/head.html`

**Interfaces:**
- Produces: every token below. Later tasks reference `--font-display`, `--font-book`, `--font-ui`, `--font-mono`, `--color-ember`, `--color-gold-soft`, `--text-xs` in addition to the legacy names.

- [ ] **Step 1: Create `tokens.css` with exactly this content**

```css
/* DMHelper design tokens — the ONLY css file allowed to contain raw values.
   Spec: docs/superpowers/specs/2026-07-13-ui-overhaul-design.md */

@font-face {
  font-family: 'Cinzel';
  src: url('/fonts/cinzel-latin-wght-normal.woff2') format('woff2-variations');
  font-weight: 400 900;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'Alegreya';
  src: url('/fonts/alegreya-latin-wght-normal.woff2') format('woff2-variations');
  font-weight: 400 900;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'Alegreya';
  src: url('/fonts/alegreya-latin-wght-italic.woff2') format('woff2-variations');
  font-weight: 400 900;
  font-style: italic;
  font-display: swap;
}

:root {
  /* palette — "candlelit study" (legacy names kept; ~50 templates use them) */
  --color-bg: #17120c;
  --color-surface: #211a12;
  --color-surface-hover: #2b2318;
  --color-text: #e9dfc9;
  --color-text-muted: #9c8e74;
  --color-accent: #c9a35c;          /* aged gold */
  --color-accent-hover: #ddb977;
  --color-danger: #a83a32;          /* ember red */
  --color-danger-hover: #c2564a;
  --color-success: #7fa05f;         /* moss */
  --color-warning: #d9993d;         /* amber */
  --color-border: #3a3125;          /* warm hairline */

  /* new tokens */
  --color-ember: #a83a32;
  --color-gold-soft: rgba(201, 163, 92, 0.28);
  --color-overlay: rgba(10, 7, 4, 0.88);

  --font-display: 'Cinzel', 'Georgia', serif;
  --font-book: 'Alegreya', 'Georgia', serif;
  --font-ui: system-ui, 'Segoe UI', sans-serif;
  --font-mono: 'Cascadia Code', 'Fira Code', ui-monospace, monospace;

  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;

  --text-xs: 0.75rem;
  --text-sm: 0.875rem;
  --text-base: 1rem;
  --text-lg: 1.25rem;
  --text-xl: 1.5rem;
  --text-2xl: 2rem;

  --radius: 6px;
  --shadow: 0 8px 28px rgba(0, 0, 0, 0.5);   /* overlays/popovers only — not cards */
  --transition: 150ms ease;

  font-family: var(--font-ui);
  font-size: 16px;
  color: var(--color-text);
  background-color: var(--color-bg);
}
```

Note `--text-xs` fixes a live bug: `.palette-result-type` and `.command-palette-footer` already use `var(--text-xs)`, which was never defined.

- [ ] **Step 2: Delete the old `:root` block from app.css**

Remove `app.css` lines 1–35 (the entire `:root { … }` block, up to and including its closing `}`). Nothing else.

- [ ] **Step 3: Link tokens.css first in the head fragment**

In `fragments/head.html`, before the app.css link, add:

```html
    <link rel="stylesheet" th:href="@{/css/tokens.css}">
```

- [ ] **Step 4: Verify tests and eyeball**

Run: `./mvnw test` → BUILD SUCCESS.
Run the app; every page should now be warm brown/gold instead of navy/purple, with no purple remnants in chrome (statblock parchment card and condition dot colors will still be off — later tasks).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css src/main/resources/templates/fragments/head.html
git commit -m "feat: grimoire token layer — whole-app warm re-theme"
```

---

### Task 3: Split app.css into base / components / book

**Files:**
- Create: `src/main/resources/static/css/base.css`, `components.css`, `book.css`
- Delete: `src/main/resources/static/css/app.css`
- Modify: `src/main/resources/templates/fragments/head.html`

**Interfaces:**
- Produces: the three files; all later CSS tasks name which file they edit. This task is a **pure move — zero rule changes**.

- [ ] **Step 1: Distribute app.css rule blocks verbatim**

| Goes to | Sections of current app.css (by their comments/selectors) |
|---|---|
| `base.css` | universal reset (`*, *::before…`), `body`, `.navbar*`, `.dm-toggle`, `.pin-display`, `.app-layout`, `.sidebar`, `.sidebar-title`, `.page-header`, `.empty-state`, "DM Mode — player-safe indicator (M7)" block, `.hidden`, `[x-cloak]` |
| `components.css` | `.card*`, `.btn*`, `.form-group`, `.form-actions`, `.inline-form`, `.badge*`, `.alert*`, `.search-bar`, `.statblock-card`, `.statblock-meta`, `.party-summary-bar`, `.party-member-*`, `.pm-inactive`, `.form-tabs`, `.ability-grid`, `.equipment-table`, entire "Combat Tracker Panel (M6)" block, "Handout Gallery (M7)", "QR Popover (M7)", "Player View send-to-table indicator (M7)", `.note-type-bar`, `.note-type-chip`, `.quicknotes-strip`, `.quicknote-row`, sheet styles (`.sheet-*`, `.badge-success`, `.small-input`, `.text-muted`), all dice styles (`.dice-*`, `.roll-btn`, `.roll-tooltip`), command palette block (`.command-palette-*`, `.palette-*`), `.detail-section`, `.detail-*`, `.compendium-section` |
| `book.css` | "Statblock Renderer (5.5e parchment style)" block (`.statblock-render*`), "Notes & Wiki (M8)" block (`.note-body*`, `.read-aloud`, `.wiki-link*`) |

Every line of app.css must land in exactly one file; delete app.css when empty.

- [ ] **Step 2: Update head fragment**

Replace the app.css link in `fragments/head.html` with:

```html
    <link rel="stylesheet" th:href="@{/css/base.css}">
    <link rel="stylesheet" th:href="@{/css/components.css}">
    <link rel="stylesheet" th:href="@{/css/book.css}">
```

(tokens.css stays first.)

- [ ] **Step 3: Verify no rule was lost**

Run: `cat src/main/resources/static/css/{base,components,book}.css | grep -c '{'` and compare with the pre-split count from git: `git show HEAD:src/main/resources/static/css/app.css | grep -c '{'` — numbers must match. Then `./mvnw test` → green; eyeball one page — pixel-identical to Task 2.

- [ ] **Step 4: Commit**

```bash
git add -A src/main/resources/static/css src/main/resources/templates/fragments/head.html
git commit -m "refactor: split app.css into tokens/base/components/book (pure move)"
```

---

### Task 4: Component kit retune + tapered rule

**Files:**
- Modify: `src/main/resources/static/css/base.css`, `components.css`

**Interfaces:**
- Produces: `.rule-taper` and `.rule-taper--gold` (block-level divider, no markup args) — used by Tasks 8, 13, 15. Heading font behavior: `h1/h2` render in `var(--font-display)` globally.

- [ ] **Step 1: base.css — typography defaults**

Add after the `body` rule:

```css
h1, h2 {
  font-family: var(--font-display);
  font-weight: 600;
  letter-spacing: 0.02em;
}

::selection { background: var(--color-gold-soft); }

:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}
```

And in `.navbar-brand`, add `font-family: var(--font-display);`.

- [ ] **Step 2: components.css — retune cards, buttons, forms; add the tapered rule**

Apply these **edits to existing rules** (find each selector and change only the listed properties):

- `.card:hover` → replace whole body with `border-color: var(--color-gold-soft);` (kill the glow shadow).
- `.statblock-card:hover` → same replacement.
- `.btn-primary` → `background: var(--color-accent); border-color: var(--color-accent); color: var(--color-bg);` (dark text on gold, not white).
- `.btn-primary:hover` → `background: var(--color-accent-hover); border-color: var(--color-accent-hover); color: var(--color-bg);`
- `.badge` → change `color: #fff` to `color: var(--color-bg);`
- `.btn-danger:hover` → change the rgba background to `background: color-mix(in srgb, var(--color-danger) 15%, transparent);`
- `.alert-error` background → `color-mix(in srgb, var(--color-danger) 15%, transparent)`; `.alert-success` background → `color-mix(in srgb, var(--color-success) 15%, transparent)`.

Add at the end of components.css:

```css
/* Tapered rule — the sourcebook divider (spec §2.3). Usage: <div class="rule-taper"></div> */
.rule-taper {
  height: 5px;
  border: none;
  background: var(--color-ember);
  clip-path: polygon(0 0, 100% 50%, 0 100%);
  margin: var(--space-sm) 0;
}
.rule-taper--gold { background: var(--color-accent); }

/* Minimal utility classes — replacement targets for inline styles */
.u-mb-lg { margin-bottom: var(--space-lg); }
.u-mb-md { margin-bottom: var(--space-md); }
.u-flex { display: flex; align-items: center; gap: var(--space-sm); }
.u-text-sm { font-size: var(--text-sm); }
.u-num { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
```

- [ ] **Step 3: Verify**

`./mvnw test` → green. Run app: buttons show dark-on-gold primary, cards no longer glow, page titles render in Cinzel.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css
git commit -m "feat: retune component kit for grimoire system; add tapered rule + utilities"
```

---

### Task 5: App shell pieces — left nav fragment, shell CSS, session route

**Files:**
- Create: `src/main/resources/templates/fragments/_appnav.html`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionController.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionControllerTest.java`
- Modify: `src/main/resources/static/css/base.css`

This task only **builds** the new pieces; nothing uses them yet, so the old layout keeps working. Task 6 wires them in.

**Interfaces:**
- Consumes: `GameMapService.findByCampaignId(UUID) → List<GameMap>` (exists), `GameMap.getId()`.
- Produces: fragment `~{fragments/_appnav :: appnav}` (expects `campaignId` model attr, nullable); CSS classes `.app-shell`, `.app-main`, `.appnav*`; route `GET /campaigns/{campaignId}/session`.

- [ ] **Step 1: Write the failing controller test**

`SessionControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameMapService gameMapService;

    @Test
    void redirectsToFirstMapPlayWhenMapsExist() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        GameMap map = new GameMap();
        map.setId(mapId);
        when(gameMapService.findByCampaignId(campaignId)).thenReturn(List.of(map));

        mockMvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/maps/" + mapId + "/play"));
    }

    @Test
    void redirectsToMapsListWhenNoMaps() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(gameMapService.findByCampaignId(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/maps"));
    }
}
```

If `GameMap` has no public `setId` (check the entity — it may use reflection-only ids), set the id the way other tests in `gamemap`/`encounter` packages do; mirror their pattern. If a `PinInterceptor`/config import is needed for `@WebMvcTest` to boot (check how `CampaignControllerTest` handles it), copy that too.

- [ ] **Step 2: Run to verify failure**

Run: `./mvnw test -Dtest=SessionControllerTest`
Expected: COMPILATION ERROR — `SessionController` does not exist.

- [ ] **Step 3: Implement `SessionController`**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * "Run Session" entry point: jumps to the campaign's live table.
 * v1 heuristic: first map's play screen, or the maps page when the campaign has no maps yet.
 */
@Controller
public class SessionController {

    private final GameMapService gameMapService;

    public SessionController(GameMapService gameMapService) {
        this.gameMapService = gameMapService;
    }

    @GetMapping("/campaigns/{campaignId}/session")
    public String runSession(@PathVariable UUID campaignId) {
        List<GameMap> maps = gameMapService.findByCampaignId(campaignId);
        if (maps.isEmpty()) {
            return "redirect:/campaigns/" + campaignId + "/maps";
        }
        return "redirect:/campaigns/" + campaignId + "/maps/" + maps.get(0).getId() + "/play";
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=SessionControllerTest` → 2 tests PASS. Then full `./mvnw test` → green.

- [ ] **Step 5: Create `fragments/_appnav.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<aside class="appnav" th:fragment="appnav" id="appnav">
    <th:block th:if="${campaignId != null}">
        <a class="appnav-link appnav-home" th:href="@{/campaigns/{id}(id=${campaignId})}">Campaign Home</a>

        <div class="appnav-group">
            <div class="appnav-label">Prep</div>
            <a class="appnav-link" th:href="@{/campaigns/{id}/adventures(id=${campaignId})}">Adventures</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/encounters(id=${campaignId})}">Encounters</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/maps(id=${campaignId})}">Maps</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/handouts(id=${campaignId})}">Handouts</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/notes(id=${campaignId})}">Notes &amp; Wiki</a>
        </div>
        <div class="appnav-group">
            <div class="appnav-label">Party</div>
            <a class="appnav-link" th:href="@{/campaigns/{id}/party(id=${campaignId})}">Roster</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/sheets(id=${campaignId})}">Sheets</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/treasury(id=${campaignId})}">Treasury</a>
            <a class="appnav-link" th:href="@{/campaigns/{id}/ledger(id=${campaignId})}">Ledger</a>
        </div>
        <div class="appnav-group">
            <div class="appnav-label">World</div>
            <a class="appnav-link" th:href="@{/campaigns/{id}/calendar(id=${campaignId})}">Calendar</a>
            <a class="appnav-link" href="/library">Library</a>
        </div>

        <div class="appnav-footer">
            <a class="btn btn-primary appnav-run" th:href="@{/campaigns/{id}/session(id=${campaignId})}">
                &#9876; Run Session
            </a>
        </div>
    </th:block>

    <th:block th:unless="${campaignId != null}">
        <div class="appnav-group">
            <a class="appnav-link" href="/campaigns">Campaigns</a>
            <a class="appnav-link" href="/library">Library</a>
            <a class="appnav-link" href="/library/about">About</a>
        </div>
    </th:block>

    <script>
    (function () {
        const path = location.pathname;
        document.querySelectorAll('#appnav .appnav-link').forEach(a => {
            const href = a.getAttribute('href');
            if (href !== '/' && path.startsWith(href)) a.setAttribute('aria-current', 'page');
        });
    })();
    </script>
</aside>
</html>
```

- [ ] **Step 6: Add shell CSS to base.css**

```css
/* ── App shell: top bar + grouped left nav (replaces .app-layout as pages migrate) ── */
.app-shell {
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: calc(100vh - 49px);
}

.app-shell > .app-main {
  padding: var(--space-lg);
  overflow-y: auto;
  min-width: 0;
}

.appnav {
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: var(--space-md) var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  position: sticky;
  top: 49px;
  height: calc(100vh - 49px);
  overflow-y: auto;
}

.appnav-label {
  font-family: var(--font-display);
  font-size: var(--text-xs);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent);
  padding: 0 var(--space-sm);
  margin-bottom: var(--space-xs);
  border-bottom: 1px solid var(--color-gold-soft);
  padding-bottom: var(--space-xs);
}

.appnav-link {
  display: block;
  padding: 6px var(--space-sm);
  border-radius: var(--radius);
  color: var(--color-text-muted);
  text-decoration: none;
  font-size: var(--text-sm);
  border-left: 2px solid transparent;
}
.appnav-link:hover { color: var(--color-text); background: var(--color-surface-hover); }
.appnav-link[aria-current="page"] {
  color: var(--color-accent);
  border-left-color: var(--color-accent);
  background: var(--color-surface-hover);
}
.appnav-home { font-family: var(--font-display); color: var(--color-text); }

.appnav-footer { margin-top: auto; padding: var(--space-sm); }
.appnav-run { width: 100%; }

@media (max-width: 900px) {
  .app-shell { grid-template-columns: 1fr; }
  .appnav { position: static; height: auto; flex-direction: row; flex-wrap: wrap; border-right: none; border-bottom: 1px solid var(--color-border); }
  .appnav-footer { margin-top: 0; }
}
```

- [ ] **Step 7: Full test run + commit**

`./mvnw test` → green (nothing uses the fragment yet; app unchanged visually).

```bash
git add src/main/java src/test/java src/main/resources
git commit -m "feat: app shell pieces — grouped left nav fragment, shell CSS, Run Session route"
```

---

### Task 6: Migrate all pages to the shell; slim the top bar

**Files:**
- Modify: all 22 page templates listed below
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Delete: `src/main/resources/templates/fragments/sidebar.html`
- Modify: `src/main/resources/static/css/base.css`

**Interfaces:**
- Consumes: `~{fragments/_appnav :: appnav}`, `.app-shell` / `.app-main` from Task 5.
- Produces: no page uses `.app-layout` or the `sidebar` fragment anymore.

- [ ] **Step 1: Apply the identical transformation to every page template**

The 22 files: `about.html`, `adventure/detail.html`, `adventure/list.html`, `adventure/scene-detail.html`, `calendar/overview.html`, `campaigns/detail.html`, `campaigns/list.html`, `encounter/detail.html`, `encounter/list.html`, `handout/list.html`, `ledger/list.html`, `library/class-detail.html`, `library/detail.html`, `library/list.html`, `maps/list.html`, `notes/detail.html`, `notes/list.html`, `party/list.html`, `sheet/create.html`, `sheet/detail.html`, `sheet/overview.html`, `treasury/list.html`.

Transformation (worked example — `campaigns/list.html`):

Before:
```html
    <div class="app-layout">
        <main>
            …page content…
        </main>

        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
```

After:
```html
    <div class="app-shell">
        <th:block th:replace="~{fragments/_appnav :: appnav}"></th:block>
        <main class="app-main">
            …page content…
        </main>
    </div>
```

Rules: appnav comes **before** main; `<main>` gains class `app-main`; the sidebar include is deleted. A few templates may lack the sidebar include or use slightly different whitespace — the end state is what matters. Do not touch `maps/editor.html`, `maps/battle.html`, or anything under `player/` (they have their own layouts).

- [ ] **Step 2: Rewrite `fragments/navbar.html` nav links away**

In `navbar.html`, delete the two inner `<nav …>` blocks (the 10 campaign links and the 3 global links) and the wrapping `<div style="display: flex; …">`, leaving the brand link directly in the navbar:

```html
    <a href="/campaigns" class="navbar-brand">DMHelper</a>
```

Keep everything in `navbar-right` (dice toggle, Player View, QR, DM Mode, PIN, scripts) exactly as-is — Task 7 restyles it. Remove the now-dead inline `style=` attributes that belonged to the deleted navs.

- [ ] **Step 3: Delete dead pieces**

- Delete `fragments/sidebar.html`.
- In `base.css`, delete the `.app-layout`, `.app-layout > main`, `.sidebar`, and `.sidebar-title` rules.
- Grep guard: `grep -rn "app-layout\|fragments/sidebar" src/main/resources/templates/` → no hits.

- [ ] **Step 4: Verify**

`./mvnw test` → green (controller tests render these templates; failures here mean a malformed edit — fix before proceeding). Run the app and click through: campaigns list → campaign → each left-nav entry; confirm grouped nav shows on every page, active item highlights, Run Session button appears inside a campaign.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/resources/templates src/main/resources/static/css
git commit -m "feat: migrate all pages to top-bar + grouped left nav shell"
```

---

### Task 7: DM Mode shielded treatment

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/static/css/base.css`

**Interfaces:**
- Consumes: existing `body.dm-mode-off` class mechanics (unchanged).
- Produces: `.dm-shield-badge` element in the top bar; stronger off-state styling. **No behavioral change to what gets hidden.**

- [ ] **Step 1: Add the shield badge to navbar.html**

Inside `navbar-right`, immediately before the `dm-toggle` label:

```html
        <span class="dm-shield-badge">PLAYER-SAFE</span>
```

- [ ] **Step 2: Add the state CSS to base.css**

```css
/* DM Mode off = screen is player-safe. Must be readable from across the room. */
.dm-shield-badge {
  display: none;
  font-family: var(--font-display);
  font-size: var(--text-xs);
  letter-spacing: 0.15em;
  padding: 2px 10px;
  border: 1px solid var(--color-warning);
  border-radius: var(--radius);
  color: var(--color-warning);
}
body.dm-mode-off .dm-shield-badge { display: inline-block; }
body.dm-mode-off .navbar {
  border-bottom: 3px solid var(--color-warning);
  background: color-mix(in srgb, var(--color-warning) 6%, var(--color-surface));
}
```

Then delete the old weaker rule `body.dm-mode-off .navbar { border-bottom-color: var(--color-warning); }` from base.css (superseded).

- [ ] **Step 3: Safety re-verification**

Run: `./mvnw test -Dtest='PlayerSafeProjectionServiceTest,CoreSessionLoopSmokeTest'` → PASS, then full `./mvnw test` → green.
Run the app, toggle DM Mode off: amber badge + amber top-bar edge + body frame appear; dice panel, party bar, and `.dm-only` content vanish. Toggle back on: all restored.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources
git commit -m "feat: unmistakable player-safe state for DM Mode toggle"
```

---

### Task 8: Statblock grimoire treatment

**Files:**
- Modify: `src/main/resources/static/css/book.css` (replace the whole `.statblock-render` block)

**Interfaces:**
- Consumes: existing markup classes in `library/_statblock-renderer.html` (`.statblock-render`, `.sb-subtitle`, `.sb-rule`, `.sb-rule-thin`, `.sb-stat-row`, `.sb-abilities`, `.sb-props`, `.sb-ability`, `.sb-ability-name`, `.sb-ability-text`) — **CSS-only task, no template edits.**
- Produces: the dark statblock look reused by Task 13's rail viewer (same classes, no extra work).

- [ ] **Step 1: Replace the "Statblock Renderer" block in book.css with:**

```css
/* Statblock — Monster Manual anatomy, dark grimoire (spec §5.1) */
.statblock-render {
  max-width: 500px;
  margin: 0 auto;
  background: color-mix(in srgb, var(--color-surface) 85%, var(--color-accent));
  color: var(--color-text);
  border: 1px solid var(--color-gold-soft);
  border-radius: var(--radius);
  padding: var(--space-lg);
  font-family: var(--font-book);
}
.statblock-render h2 {
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-weight: 700;
  margin-bottom: 2px;
  color: var(--color-accent);
}
.statblock-render .sb-subtitle {
  font-style: italic;
  font-size: var(--text-sm);
  margin-bottom: var(--space-md);
  color: var(--color-text-muted);
}
.statblock-render .sb-rule {
  height: 5px; border: none;
  background: var(--color-ember);
  clip-path: polygon(0 0, 100% 50%, 0 100%);
  margin: var(--space-sm) 0;
}
.statblock-render .sb-rule-thin {
  border: none; border-top: 1px solid var(--color-ember);
  opacity: 0.6; margin: var(--space-xs) 0;
}
.statblock-render .sb-stat-row {
  display: grid; grid-template-columns: 1fr 1fr 1fr;
  gap: var(--space-xs); font-size: var(--text-sm); margin-bottom: var(--space-sm);
}
.statblock-render .sb-stat-row span { font-weight: 600; }
.statblock-render .sb-abilities {
  display: grid; grid-template-columns: repeat(6, 1fr);
  gap: var(--space-xs); text-align: center; margin-bottom: var(--space-sm);
  font-variant-numeric: tabular-nums;
}
.statblock-render .sb-abilities div { font-size: var(--text-xs); color: var(--color-text-muted); }
.statblock-render .sb-abilities .score { font-size: 1rem; font-weight: 700; color: var(--color-text); }
.statblock-render .sb-props { font-size: var(--text-sm); margin-bottom: var(--space-sm); }
.statblock-render .sb-props dt { font-weight: 700; display: inline; }
.statblock-render .sb-props dd { display: inline; margin-right: 1em; }
.statblock-render h3 {
  font-family: var(--font-display);
  font-size: 1rem; font-weight: 600;
  color: var(--color-ember);
  letter-spacing: 0.04em;
  margin-top: var(--space-md); margin-bottom: var(--space-xs);
  border-bottom: 1px solid var(--color-ember);
  padding-bottom: 2px;
}
.statblock-render .sb-ability { margin-bottom: var(--space-sm); }
.statblock-render .sb-ability .sb-ability-name { font-weight: 700; font-style: italic; }
.statblock-render .sb-ability .sb-ability-text { font-size: var(--text-sm); line-height: 1.55; }
```

- [ ] **Step 2: Verify**

`./mvnw test` → green. Run app → Library → open a monster: dark statblock with gold Cinzel name, ember tapered rule, ember section heads. Check a spell/condition detail too (same renderer classes).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/book.css
git commit -m "feat: Monster Manual statblock anatomy, dark grimoire edition"
```

---

### Task 9: Library grammar — cards and browse chrome

**Files:**
- Modify: `src/main/resources/static/css/components.css`

**Interfaces:**
- Consumes: existing classes `.statblock-card`, `.statblock-meta`, `.badge-*`, `.equipment-table`, `.search-bar` used by the 11 `library/_*-card.html` partials — CSS-only task.

- [ ] **Step 1: Retune library CSS in components.css**

Edits to existing rules:

- `.statblock-card h3` → add `font-family: var(--font-display); font-weight: 600;`
- `.badge-srd` → `background: transparent; color: var(--color-accent); border: 1px solid var(--color-gold-soft);` (quiet outline badge)
- `.badge-custom` → `background: transparent; color: var(--color-success); border: 1px solid var(--color-success);`
- Rarity badges — replace the raw-hex set with:

```css
.badge-common    { background: var(--color-text-muted); color: var(--color-bg); }
.badge-uncommon  { background: var(--color-success); color: var(--color-bg); }
.badge-rare      { background: color-mix(in srgb, var(--color-accent) 60%, var(--color-text)); color: var(--color-bg); }
.badge-very-rare { background: color-mix(in srgb, var(--color-ember) 70%, var(--color-accent)); color: var(--color-text); }
.badge-legendary { background: var(--color-accent); color: var(--color-bg); }
.badge-artifact  { background: var(--color-ember); color: var(--color-text); }
.badge-attune    { background: transparent; color: var(--color-accent); border: 1px solid var(--color-gold-soft); }
```

- `.equipment-table th` → change `color` to `var(--color-accent)` (already is) and add `font-family: var(--font-display); letter-spacing: 0.06em;`

- [ ] **Step 2: Verify + commit**

`./mvnw test` green; eyeball `/library` across a few type tabs (monsters, items with rarity badges, equipment table).

```bash
git add src/main/resources/static/css/components.css
git commit -m "feat: one card grammar for the library; warm rarity badges"
```

---

### Task 10: Book surfaces — notes, wiki, adventure text

**Files:**
- Modify: `src/main/resources/static/css/book.css` (replace `.note-body`, `.read-aloud`, `.wiki-link` blocks)
- Modify: `src/main/resources/templates/adventure/scene-detail.html` (one class)

**Interfaces:**
- Produces: `.note-body` is the app-wide book surface; `.note-body.drop-cap` opts a container into the drop cap (adventure scene text now; handouts in Task 11).

- [ ] **Step 1: Replace the notes/wiki block in book.css with:**

```css
/* Book surfaces — anywhere rendered markdown appears (spec §5.2) */
.note-body {
  font-family: var(--font-book);
  font-size: 1.0625rem;
  line-height: 1.75;
  max-width: 68ch;
}
.note-body h1, .note-body h2, .note-body h3 {
  font-family: var(--font-display);
  color: var(--color-text);
  margin-top: var(--space-lg);
  margin-bottom: var(--space-sm);
}
.note-body h1 { font-size: var(--text-2xl); }
.note-body h2 { font-size: var(--text-xl); border-bottom: 1px solid var(--color-gold-soft); padding-bottom: var(--space-xs); }
.note-body h3 { font-size: var(--text-lg); }
.note-body p { margin-bottom: var(--space-md); }
.note-body ul, .note-body ol { margin-bottom: var(--space-md); padding-left: var(--space-lg); }
.note-body li { margin-bottom: var(--space-xs); }
.note-body code { background: var(--color-bg); padding: 2px 6px; border-radius: 4px; font-size: 0.85em; font-family: var(--font-mono); }
.note-body pre { background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius); padding: var(--space-md); overflow-x: auto; margin-bottom: var(--space-md); }
.note-body pre code { background: none; padding: 0; }
.note-body blockquote { border-left: 3px solid var(--color-accent); padding-left: var(--space-md); color: var(--color-text-muted); margin-bottom: var(--space-md); font-style: italic; }
.note-body table { width: 100%; border-collapse: collapse; margin-bottom: var(--space-md); font-size: var(--text-sm); font-family: var(--font-ui); }
.note-body th, .note-body td { padding: var(--space-xs) var(--space-sm); border: 1px solid var(--color-border); text-align: left; }
.note-body th { background: var(--color-surface-hover); font-weight: 600; }
.note-body img { max-width: 100%; border-radius: var(--radius); }

/* Drop cap — adventure chapters & handouts ONLY (ornament budget) */
.note-body.drop-cap > p:first-of-type::first-letter {
  font-family: var(--font-display);
  font-size: 3.1em;
  line-height: 0.85;
  float: left;
  padding-right: 0.08em;
  color: var(--color-ember);
}

/* Read-aloud (boxed) text */
.read-aloud {
  margin: var(--space-md) 0;
  padding: var(--space-md) var(--space-lg);
  border-left: 3px solid var(--color-accent);
  background: color-mix(in srgb, var(--color-accent) 8%, transparent);
  border-radius: var(--radius);
  font-style: italic;
}
.read-aloud p { margin: 0 0 var(--space-sm) 0; }
.read-aloud p:last-child { margin-bottom: 0; }

/* Wiki links — gold ink */
.wiki-link { color: var(--color-accent); text-decoration: none; border-bottom: 1px dashed var(--color-gold-soft); }
.wiki-link:hover { color: var(--color-accent-hover); border-bottom-style: solid; }
.wiki-link-broken { color: var(--color-danger); border-bottom-color: var(--color-danger); cursor: help; }
```

(Keep the empty `.wiki-link-resolved` rule out — it was a no-op.)

- [ ] **Step 2: Opt scene text into the drop cap**

In `adventure/scene-detail.html`, find the element carrying class `note-body` that renders the scene's main markdown body and change it to `class="note-body drop-cap"`. If multiple `note-body` blocks exist (e.g. DM notes vs. read text), apply only to the primary scene description.

- [ ] **Step 3: Verify + commit**

`./mvnw test` green; eyeball a note with headings/lists/table, a wiki link (resolved + broken), and a scene detail page (drop cap on first paragraph, read-aloud box gold).

```bash
git add src/main/resources
git commit -m "feat: book reading surfaces — Alegreya measure, Cinzel headings, gold ink links"
```

---

### Task 11: Handout presentation overlay

**Files:**
- Modify: `src/main/resources/templates/handout/_present-overlay.html`
- Modify: `src/main/resources/static/css/components.css` (the "Handout Gallery (M7)" section)

**Interfaces:**
- Consumes: existing `.handout-overlay` class and overlay markup (read the fragment first — it likely uses inline styles; move them into the classes below).

- [ ] **Step 1: Add/replace overlay CSS**

```css
.handout-overlay {
  position: fixed; inset: 0; z-index: 900;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: var(--space-md);
  background: radial-gradient(ellipse at center, var(--color-overlay) 60%, rgba(0,0,0,0.97) 100%);
  animation: fadeIn 0.2s ease;
}
.handout-overlay img {
  max-width: 88vw; max-height: 80vh;
  border: 1px solid var(--color-gold-soft);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.handout-overlay .handout-caption {
  font-family: var(--font-book);
  font-size: var(--text-lg);
  color: var(--color-text);
  max-width: 68ch; text-align: center;
}
```

- [ ] **Step 2: Align the fragment markup**

In `_present-overlay.html`: ensure the root uses `handout-overlay`, the caption/text element uses `handout-caption` (add the class; if the fragment renders markdown text handouts, give that container `note-body drop-cap handout-caption` — text handouts get the theatrical book treatment). Replace inline positioning/background styles that duplicate the CSS above; keep all htmx/Alpine wiring untouched.

- [ ] **Step 3: Verify + commit**

`./mvnw test` green; run app, present a handout (image and text if both types exist): vignette backdrop, framed image, book caption. Close works.

```bash
git add src/main/resources
git commit -m "feat: theatrical handout presentation overlay"
```

---

### Task 12: Cockpit shell — battle screen structure

**Files:**
- Create: `src/main/resources/static/css/cockpit.css`
- Modify: `src/main/resources/templates/maps/battle.html`

**Interfaces:**
- Consumes: existing battle.html structure (`.battle-container`, `.battle-toolbar`, `.battle-body`, `.battle-canvas-wrap`, `.battle-sidebar`, tool groups, tracker/scene panels) and all its JS wiring — **do not touch any JS logic, Konva code, htmx calls, or Alpine state.**
- Produces: `.cockpit-topbar`, `.cockpit-title`, restyled toolbar; "End Session" link; cockpit.css linked from battle.html (and editor if shared styles help — editor is otherwise out of scope).

- [ ] **Step 1: Extract battle.html's `<style>` block into cockpit.css**

Move the entire inline `<style>` content from battle.html into `static/css/cockpit.css` verbatim, and replace the style block with `<link rel="stylesheet" th:href="@{/css/cockpit.css}">` placed after the head include. Fix the one raw hex while moving: `.battle-canvas-wrap` background `#0a0a1a` → `var(--color-bg)`.

Run `./mvnw test` + eyeball the battle page before continuing — must be visually unchanged (except canvas backdrop now warm).

- [ ] **Step 2: Session top bar**

In battle.html, at the top of `.battle-toolbar` (or as a new bar above it if the toolbar is crowded — prefer a new `<div class="cockpit-topbar">` before `.battle-toolbar`), add:

```html
<div class="cockpit-topbar">
    <span class="cockpit-title" th:text="${map.name}">Map name</span>
    <span style="flex:1"></span>
    <a class="btn btn-ghost" th:href="@{/campaigns/{id}(id=${campaignId})}">End Session</a>
</div>
```

(The dice toggle and DM Mode switch already exist in the battle toolbar area — if battle.html includes the navbar fragment, leave it; if it has its own controls, leave those. Only ADD title + End Session; replace `style="flex:1"` with a `.cockpit-spacer { flex: 1; }` class in cockpit.css.)

Add to cockpit.css:

```css
.cockpit-topbar {
  display: flex; align-items: center; gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-gold-soft);
}
.cockpit-title { font-family: var(--font-display); font-size: var(--text-lg); color: var(--color-text); }
.cockpit-spacer { flex: 1; }
```

- [ ] **Step 3: Toolbar retune (CSS-only)**

In cockpit.css, retune the moved rules: `.battle-toolbar .tool-btn.active` → `background: var(--color-accent); border-color: var(--color-accent); color: var(--color-bg);`. Tool group dividers stay hairlines. No markup churn beyond what Step 2 added.

- [ ] **Step 4: Verify + commit**

`./mvnw test` green (includes `CoreSessionLoopSmokeTest`). Run app → open a map's play view: title in Cinzel, End Session returns to campaign home, tokens still drag, tracker/scene panels still toggle.

```bash
git add src/main/resources
git commit -m "feat: session cockpit shell — extracted cockpit.css, session top bar, End Session"
```

---

### Task 13: Initiative tracker — flagship component

**Files:**
- Modify: `src/main/resources/static/css/components.css` (tracker section)
- Modify: `src/main/resources/templates/encounter/_tracker.html` (CONDITION_COLORS map only)

**Interfaces:**
- Consumes: existing tracker markup classes and Alpine state — **restyle only; zero behavior change.**

- [ ] **Step 1: Retune tracker CSS**

Edits to the tracker section in components.css:

- `.tracker-panel` custom-prop block → replace with:

```css
.tracker-panel {
    --color-hp-bar: var(--color-success);
    --color-hp-bloodied: var(--color-warning);
    --color-hp-dead: var(--color-danger);
    --color-condition-active: var(--color-accent);
    --color-concentration: #966a9e;
    --color-legendary: var(--color-accent);
    --color-tracker-bg: var(--color-surface);
    --color-combatant-active: color-mix(in srgb, var(--color-accent) 12%, transparent);
    --color-combatant-hover: rgba(255, 255, 255, 0.04);
    --color-initiative-badge: var(--color-surface-hover);
}
```

(`#966a9e` is the one deliberate non-token hex here — concentration purple has table-wide recognition; hoist it: add `--color-concentration: #966a9e;` to tokens.css instead and reference it, keeping the no-raw-hex rule intact.)

- `.combatant-row.active` → `background: var(--color-combatant-active); border-left-color: var(--color-accent);` and add `box-shadow: inset 0 0 0 1px var(--color-gold-soft);`
- `.init-badge` → `background: var(--color-initiative-badge); color: var(--color-accent); font-family: var(--font-mono); font-variant-numeric: tabular-nums; border: 1px solid var(--color-border);`
- `.hp-mini-fill.dead` → `background: var(--color-hp-dead);` (fixes an existing bug — dead currently shows the bloodied color).
- `.tracker-header` → add `font-family: var(--font-display); letter-spacing: 0.04em;` and change `border-bottom` to use a tapered rule instead: keep the border, and in `_tracker.html` add `<div class="rule-taper rule-taper--gold"></div>` immediately after the tracker header element.
- Add: `.hp-delta-input, .detail-hp input { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }`

- [ ] **Step 2: Warm the condition colors**

In `_tracker.html`, replace the `CONDITION_COLORS` map values (keys unchanged):

```js
const CONDITION_COLORS = {
    blinded: '#8a8578', charmed: '#c25a7c', deafened: '#6e7a80',
    exhaustion: '#7a5b48', frightened: '#8a5fa0', grappled: '#b06038',
    incapacitated: '#85796a', invisible: '#5f9ea0', paralyzed: '#c98a3d',
    petrified: '#8d7a6b', poisoned: '#6f9a4f', prone: '#5a7fa8',
    restrained: '#c9b03d', stunned: '#b06038', unconscious: '#a83a32',
    concentration: '#966a9e',
};
```

(JS constants are outside the CSS token rule; hues stay recognizable, saturation warmed.)

- [ ] **Step 3: Verify + commit**

`./mvnw test` green (smoke test covers the tracker). Run an encounter: active turn shows gold marker, HP numerals tabular, condition dots readable, defeated row struck through, DM Mode off still hides what it hid before.

```bash
git add src/main/resources
git commit -m "feat: initiative tracker as flagship component — gold turn marker, tabular numerals, warm condition palette"
```

---

### Task 14: Party bar strip in the cockpit

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java` (play method)
- Test: existing `GameMapController` test if present, else add to `SessionControllerTest`'s package a `GameMapControllerPlayTest`
- Modify: `src/main/resources/templates/maps/battle.html`
- Modify: `src/main/resources/static/css/cockpit.css`

**Interfaces:**
- Consumes: `PartyMemberService.findActiveByCampaignId(UUID) → List<PartyMember>` (exists); fragment `~{party/_summary-bar :: summary-bar(members)}` (exists).
- Produces: model attribute `partyMembers` on the play view.

- [ ] **Step 1: Write the failing test**

If `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/` has a controller test for `GameMapController`, add this test method to it (with `@MockitoBean PartyMemberService partyMemberService;` added). Otherwise create `GameMapControllerPlayTest` following the `@WebMvcTest(GameMapController.class)` pattern from Task 5, mocking `GameMapService` and `PartyMemberService`:

```java
@Test
void playAddsPartyMembersToModel() throws Exception {
    UUID campaignId = UUID.randomUUID();
    UUID mapId = UUID.randomUUID();
    GameMap map = new GameMap();
    map.setId(mapId);
    when(gameMapService.findById(mapId)).thenReturn(map);
    when(partyMemberService.findActiveByCampaignId(campaignId)).thenReturn(List.of());

    mockMvc.perform(get("/campaigns/{cid}/maps/{mid}/play", campaignId, mapId))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("partyMembers"));
}
```

- [ ] **Step 2: Run to verify failure**

`./mvnw test -Dtest='GameMapController*'` → the new test FAILS (attribute missing / unknown bean).

- [ ] **Step 3: Implement**

In `GameMapController`: inject `PartyMemberService partyMemberService` via constructor; in `play(…)` add:

```java
model.addAttribute("partyMembers", partyMemberService.findActiveByCampaignId(campaignId));
```

- [ ] **Step 4: Wire the strip into battle.html**

At the bottom of `.battle-container` (after `.battle-body`):

```html
<div class="cockpit-partybar">
    <th:block th:replace="~{party/_summary-bar :: summary-bar(${partyMembers})}"></th:block>
</div>
```

cockpit.css:

```css
.cockpit-partybar { flex-shrink: 0; border-top: 1px solid var(--color-gold-soft); }
.cockpit-partybar .party-summary-bar { margin-bottom: 0; border: none; border-radius: 0; }
.cockpit-partybar .chip-stats { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
```

Note: `body.dm-mode-off .party-summary-bar { display: none; }` already exists and stays — the strip disappears in player-safe mode, consistent with current behavior.

- [ ] **Step 5: Verify + commit**

`./mvnw test` → all green. Run the play view: party strip pinned at bottom with AC/PP/Init per PC; disappears when DM Mode off.

```bash
git add src/main/java src/test/java src/main/resources
git commit -m "feat: persistent party bar strip in the session cockpit"
```

---

### Task 15: Campaign dashboard + book-cover campaign cards

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java` (detail method)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignControllerTest.java`
- Rewrite: `src/main/resources/templates/campaigns/detail.html`
- Modify: `src/main/resources/templates/campaigns/_card.html`, `components.css`

**Interfaces:**
- Consumes: `PartyMemberService.findActiveByCampaignId`, `NoteService.findByCampaignId` (exists; `Note` has `createdAt` only — sort by that), existing `sessionPlan` model attr, `~{party/_summary-bar :: summary-bar(members)}`.
- Produces: model attrs `partyMembers` (List<PartyMember>) and `recentNotes` (List<Note>, newest 5) on `campaigns/detail`.

- [ ] **Step 1: Failing test**

In `CampaignControllerTest`, add `@MockitoBean private PartyMemberService partyMemberService;` (import from `dev.hendrikhoemberg.dmhelper.party.service`) and a test:

```java
@Test
void detailProvidesDashboardModel() throws Exception {
    Campaign c = sampleCampaign();
    when(service.findById(c.getId())).thenReturn(c);
    when(noteService.findByCampaignIdAndType(eq(c.getId()), any())).thenReturn(List.of());
    when(noteService.findByCampaignId(c.getId())).thenReturn(List.of());
    when(partyMemberService.findActiveByCampaignId(c.getId())).thenReturn(List.of());

    mockMvc.perform(get("/campaigns/{id}", c.getId()))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("partyMembers"))
            .andExpect(model().attributeExists("recentNotes"));
}
```

Run `./mvnw test -Dtest=CampaignControllerTest` → new test FAILS. **Existing tests in this class may also now fail to boot until Step 2 adds the constructor param — that's expected; fix forward.**

- [ ] **Step 2: Implement controller changes**

Add `PartyMemberService` as third constructor dependency. In `detail(…)`, after the sessionPlan block:

```java
model.addAttribute("partyMembers", partyMemberService.findActiveByCampaignId(id));
model.addAttribute("recentNotes", noteService.findByCampaignId(id).stream()
        .sorted(java.util.Comparator.comparing(dev.hendrikhoemberg.dmhelper.notes.data.Note::getCreatedAt).reversed())
        .limit(5)
        .toList());
```

Run `./mvnw test -Dtest=CampaignControllerTest` → ALL pass (add the mock to any older test methods that now NPE).

- [ ] **Step 3: Rewrite `campaigns/detail.html` body content**

Keep head/navbar/app-shell wrapper from Task 6. Replace everything inside `<main class="app-main">` with:

```html
<div class="dash-header">
    <h1 th:text="${campaign.name}">Campaign Name</h1>
    <div class="rule-taper rule-taper--gold"></div>
    <p class="dash-desc" th:if="${campaign.description != null and !campaign.description.isBlank()}"
       th:text="${campaign.description}">Description</p>
</div>

<div class="dash-actions u-mb-lg">
    <a class="btn btn-primary" th:href="@{/campaigns/{id}/session(id=${campaign.id})}">&#9876; Run Session</a>
    <a class="btn" th:href="@{/campaigns/{id}/adventures(id=${campaign.id})}">Plan Adventures</a>
</div>

<th:block th:replace="~{party/_summary-bar :: summary-bar(${partyMembers})}"></th:block>

<div class="dash-grid">
    <section class="card">
        <h3>Session Plan</h3>
        <th:block th:if="${sessionPlan != null}">
            <a th:href="@{/campaigns/{cid}/notes/{nid}(cid=${campaign.id}, nid=${sessionPlan.id})}"
               th:text="${sessionPlan.title}" class="dash-link">Plan title</a>
            <div class="note-body dash-plan-preview"
                 th:utext="${#markdown.toHtml(#strings.abbreviate(sessionPlan.body != null ? sessionPlan.body : '', 400))}"></div>
        </th:block>
        <p th:unless="${sessionPlan != null}" class="text-muted u-text-sm">
            No session plan yet — create a SESSION_PLAN note.
        </p>
    </section>

    <section class="card">
        <h3>Recent Notes</h3>
        <ul class="dash-list">
            <li th:each="n : ${recentNotes}">
                <a class="dash-link" th:href="@{/campaigns/{cid}/notes/{nid}(cid=${campaign.id}, nid=${n.id})}"
                   th:text="${n.title}">Note</a>
                <span class="text-muted u-text-sm" th:text="${n.type}">TYPE</span>
            </li>
            <li th:if="${recentNotes.isEmpty()}" class="text-muted u-text-sm">Nothing written yet.</li>
        </ul>
    </section>
</div>
```

(The old wall of "Manage X" sections dies — the left nav from Task 6 owns navigation now. Keep any export/import/archive actions the old template had: move those buttons into `dash-actions`, do not drop functionality — read the full old template before deleting.)

- [ ] **Step 4: Dashboard + cover CSS in components.css**

```css
/* Campaign dashboard */
.dash-header h1 { font-size: var(--text-2xl); margin-bottom: var(--space-xs); }
.dash-header .rule-taper--gold { max-width: 320px; margin-bottom: var(--space-md); }
.dash-desc { color: var(--color-text-muted); max-width: 68ch; margin-bottom: var(--space-md); font-family: var(--font-book); }
.dash-actions { display: flex; gap: var(--space-sm); flex-wrap: wrap; }
.dash-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: var(--space-md); margin-top: var(--space-lg); }
.dash-grid .card h3 { font-family: var(--font-display); font-size: var(--text-base); margin-bottom: var(--space-sm); }
.dash-link { color: var(--color-accent); text-decoration: none; }
.dash-link:hover { color: var(--color-accent-hover); }
.dash-list { list-style: none; display: flex; flex-direction: column; gap: var(--space-xs); }
.dash-plan-preview { font-size: var(--text-sm); max-height: 180px; overflow-y: auto; }

/* Campaign list — book cover treatment */
.campaign-cover { text-align: center; padding: var(--space-xl) var(--space-md); position: relative; }
.campaign-cover::before {
  content: ''; position: absolute; inset: 6px;
  border: 1px solid var(--color-gold-soft); border-radius: 4px; pointer-events: none;
}
.campaign-cover h3 { font-family: var(--font-display); font-size: var(--text-lg); }
```

- [ ] **Step 5: Apply cover class**

In `campaigns/_card.html`, add `campaign-cover` to the root card element's class list (keep all existing classes, actions, and htmx attributes).

- [ ] **Step 6: Verify + commit**

`./mvnw test` → green. Eyeball: campaigns list shows framed covers; campaign home shows title + gold taper, Run Session CTA, party bar, plan + recent notes cards. Confirm export/import still reachable.

```bash
git add src/main/java src/test/java src/main/resources
git commit -m "feat: campaign dashboard home screen and book-cover campaign cards"
```

---

### Task 16: Final consistency sweep

**Files:**
- Modify: remaining templates with inline styles (hotspots: `encounter/detail.html`, `adventure/_action-rail.html`, `treasury/_card.html`, `notes/_quicknotes-strip.html`, `calendar/overview.html`, `ledger/_card.html`, `handout/_card.html`, `library/list.html`, `ledger/_form.html`, `calendar/_event-card.html`, `fragments/navbar.html`, `fragments/_dice-roller.html`, `fragments/_command-palette.html`, `maps/editor.html`)
- Modify: all CSS files

- [ ] **Step 1: Inline style purge**

For each file: `grep -n 'style="' <file>`, replace each with an existing component/utility class (`.u-mb-lg`, `.u-flex`, `.u-text-sm`, `.text-muted`, `.u-num`, card/btn classes). If a style is genuinely unique and needed, promote it to a named class in the appropriate CSS file. Acceptable survivors: dynamic styles bound with `th:style`/Alpine `:style` (widths of HP bars etc.) and Konva-container sizing.

Guard: `grep -rn 'style="' src/main/resources/templates --include='*.html' | grep -v 'th:style' | wc -l` — drive to as close to 0 as practical; anything left must be a documented dynamic case.

- [ ] **Step 2: Raw-hex sweep**

Run: `grep -rn '#[0-9a-fA-F]\{3\}' src/main/resources/static/css/ | grep -v tokens.css`
Replace every hit with a token or `color-mix()` of tokens. Expected end state: zero hits.

- [ ] **Step 3: Full verification pass**

- `./mvnw test` → BUILD SUCCESS, zero failures.
- Run the app and walk every screen: campaigns list, dashboard, party, sheets (overview + detail), maps list, map editor, battle/cockpit, encounters (list + detail + tracker), library (list + monster + spell + item + class), notes (list + detail), adventures (list + detail + scene), handouts (+ present), treasury, ledger, calendar, about, player view (`/player` — must remain functional and player-safe).
- Toggle DM Mode off on the cockpit and a notes page: shield badge + hiding correct.
- Ctrl+K command palette, dice roller, QR popover all styled and functional.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: final grimoire consistency sweep — inline styles and raw hex purged"
```

---

## Self-Review Notes

- **Spec coverage:** §2 palette/type/kit → Tasks 1–4; §3 shell/nav/DM-mode → Tasks 5–7; §4 cockpit → Tasks 12–14; §5.1 statblocks/library → Tasks 8–9; §5.2 book surfaces → Tasks 10–11; §5.3 dashboard/covers → Task 15; §6.2 inline purge → per-task + Task 16; §6.4 verification → every task's test step + Task 16.
- **Deliberate scope note:** the spec's "cockpit with no active map shows the scene read-out" is satisfied minimally — the battle screen's existing scene panel covers in-session scene reading, and Run Session with zero maps lands on the maps page rather than a bespoke mapless cockpit (which would be a new screen, i.e. a new feature). Likewise the "statblock viewer tab in the cockpit rail" is satisfied minimally — the existing combatant detail panel plus the restyled `.statblock-render` classes (Task 8) cover the lookup need; a dedicated rail tab is a follow-up feature, not part of this reskin (spec §7 forbids new features).
- **Sequencing:** every task leaves `./mvnw test` green and the app usable; the nav flip (Task 6) happens in one commit so navigation is never half-moved.
