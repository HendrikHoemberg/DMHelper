# UI Elevation Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the flat Phase-1 UI into a layered, animated "living grimoire" through material depth, purposeful motion, and interaction craft — without new dependencies, backend changes, or features.

**Architecture:** Build on the existing three-file CSS architecture (`tokens.css`, `base.css`, `components.css`) by adding elevation/motion tokens and global behaviors, then add a small vanilla-JS helper file (`ui-elevation.js`) for view transitions, toasts, number ticks, and the loading filament. Tier-1 surfaces (campaign shelf, library, cockpit, handout, dice) layer their signature moments on top; Tier-2 surfaces inherit the global foundation pass only.

**Tech Stack:** Spring Boot + Thymeleaf, HTMX, Alpine.js, Konva (map island excluded from CSS transitions), vanilla CSS (view transitions, `@starting-style`, CSS gradients, data-URI noise). No new npm/Maven dependencies.

---

## File structure

| File | Responsibility |
|---|---|
| `src/main/resources/static/css/tokens.css` | All new elevation, texture, motion, and sheen tokens. The only file with raw values. |
| `src/main/resources/static/css/base.css` | Global application of tokens: vignette, scrollbars, selection, reduced-motion, view-transition wiring, page-header anatomy. |
| `src/main/resources/static/css/components.css` | Component-level craft: card hover/press, button states, empty states, toast, command palette, tracker, dice, handout. |
| `src/main/resources/static/js/ui-elevation.js` | Vanilla behaviors shared across the app: loading filament, toast, number tick, view-transition helper, shortcut overlay. |
| `src/main/resources/templates/fragments/head.html` | Include `ui-elevation.js` in the global head. |
| `src/main/resources/templates/fragments/navbar.html` | Top-bar controls: DM Mode switch, ghost buttons, palette hint, shield sweep wiring. |
| `src/main/resources/templates/fragments/_appnav.html` | Unified sidebar with gold active edge and collapse behavior. |
| `src/main/resources/templates/fragments/_command-palette.html` | Floating palette with glass backdrop. |
| `src/main/resources/templates/common/_empty-state.html` | Designed empty-state fragment. |
| `src/main/resources/templates/campaigns/list.html` | Bookshelf layout and view-transition wiring. |
| `src/main/resources/templates/campaigns/_card.html` | Book-cover card; kebab delete; blank tome. |
| `src/main/resources/templates/campaigns/detail.html` | Dashboard stagger entrance and header transition target. |
| `src/main/resources/templates/library/list.html` | Result count; filter wiring. |
| `src/main/resources/templates/library/_card.html` | Card grammar; hover actions; side-sheet opener. |
| `src/main/resources/templates/library/detail.html` | Statblock side-sheet variant. |
| `src/main/resources/templates/encounter/_tracker.html` | Animated tracker rows/HP/conditions. |
| `src/main/resources/templates/handout/_present-overlay.html` | Theatrical handout reveal. |
| `src/main/resources/templates/fragments/_dice-roller.html` | Dice tumbling and result bounce. |

---

## Rollout Step 1: Foundations I — Material

### Task 1.1: Add elevation tokens to `tokens.css`

**Files:**
- Modify: `src/main/resources/static/css/tokens.css`

- [ ] **Step 1: Append elevation + depth tokens**

Insert before the closing `}` of `:root`:

```css
  /* elevation — candlelight model */
  --elevation-base-bg: #17120c;
  --elevation-raised-bg: #1e1811;
  --elevation-floating-bg: #252019;

  --shadow-warm-sm: 0 2px 8px rgba(8, 5, 2, 0.45);
  --shadow-warm-md: 0 6px 20px rgba(8, 5, 2, 0.55);
  --shadow-warm-lg: 0 14px 40px rgba(8, 5, 2, 0.65);

  --highlight-candle: inset 0 1px 0 rgba(255, 243, 210, 0.08);

  /* gold sheen */
  --gold-sheen: linear-gradient(110deg, #c9a35c 0%, #e3c68b 35%, #c9a35c 50%, #b08a4a 100%);
  --gold-sweep: linear-gradient(105deg, transparent 40%, rgba(255, 243, 210, 0.35) 50%, transparent 60%);

  /* vignette */
  --vignette-intensity: 0.22;

  /* textures (inline data-uri noise, ~300 bytes each) */
  --texture-paper: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.03'/%3E%3C/svg%3E");
  --texture-parchment: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='256' height='256'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.6' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E");
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/css/tokens.css
git commit -m "feat(ui-elevation): add elevation, shadow, sheen, vignette, texture tokens"
```

### Task 1.2: Apply global material base

**Files:**
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Add textures and vignette to body/app**

Insert after the existing `body { min-height: 100vh; }` block:

```css
body {
  min-height: 100vh;
  background-color: var(--elevation-base-bg);
  background-image: var(--texture-paper);
}

/* subtle vignette on the app frame */
body::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9999;
  background: radial-gradient(ellipse at center, transparent 55%, rgba(8, 5, 2, var(--vignette-intensity)) 100%);
}
```

- [ ] **Step 2: Add warm scrollbars and selection**

Replace the existing `::selection` rule with:

```css
::selection {
  background: rgba(201, 163, 92, 0.35);
  color: #fff;
}

::-webkit-scrollbar { width: 10px; height: 10px; }
::-webkit-scrollbar-track { background: var(--elevation-base-bg); }
::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 5px; border: 2px solid var(--elevation-base-bg); }
::-webkit-scrollbar-thumb:hover { background: var(--color-text-muted); }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): apply paper texture, vignette, warm scrollbars"
```

### Task 1.3: Component material pass

**Files:**
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Update card and surface backgrounds to use raised elevation**

Replace the `.card` block:

```css
.card {
  background: var(--elevation-raised-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-md);
  transition: transform 160ms var(--ease-out), border-color 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
  box-shadow: var(--highlight-candle);
}

.card:hover {
  border-color: var(--color-gold-soft);
  box-shadow: var(--highlight-candle), var(--shadow-warm-sm);
  transform: translateY(-2px);
}

.card:active {
  transform: scale(0.985) translateY(0);
}
```

- [ ] **Step 2: Add sheen to primary/interactive gold**

Append after `.btn-primary`:

```css
.btn-primary {
  background: var(--gold-sheen);
  background-size: 200% 100%;
  border-color: var(--color-accent);
  color: var(--color-bg);
  transition: background-position 600ms ease, transform 120ms ease, box-shadow 120ms ease;
}

.btn-primary:hover {
  background-position: 100% 0;
  box-shadow: 0 0 16px rgba(201, 163, 92, 0.25);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): raised cards, candle catch, gold sheen"
```

---

## Rollout Step 2: Foundations II — Motion & States

### Task 2.1: Motion tokens

**Files:**
- Modify: `src/main/resources/static/css/tokens.css`

- [ ] **Step 1: Append motion tokens**

Insert inside `:root`:

```css
  /* motion */
  --duration-micro: 120ms;
  --duration-standard: 200ms;
  --duration-structural: 320ms;
  --duration-theatrical: 550ms;
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in: cubic-bezier(0.7, 0, 0.84, 0);
  --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
  --stagger-step: 35ms;
```

- [ ] **Step 2: Update legacy `--transition` to use tokens**

Replace `--transition: 150ms ease;` with:

```css
  --transition: var(--duration-standard) var(--ease-out);
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/tokens.css
git commit -m "feat(ui-elevation): add motion tokens"
```

### Task 2.2: Reduced-motion global guard

**Files:**
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Add reduced-motion collapse rule**

Append at end of file:

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): collapse motion for prefers-reduced-motion"
```

### Task 2.3: Interaction states sweep (hover, press, focus)

**Files:**
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Button press states**

Append after `.btn` block:

```css
.btn {
  transition: transform var(--duration-micro) var(--ease-out), background var(--duration-micro) var(--ease-out), border-color var(--duration-micro) var(--ease-out), box-shadow var(--duration-micro) var(--ease-out);
}
.btn:active { transform: scale(0.97); }
.btn-ghost:hover { color: var(--color-text); background: var(--elevation-floating-bg); border-color: var(--color-gold-soft); }
```

- [ ] **Step 2: Focus ring normalization**

Replace the existing `:focus-visible` rule in `base.css` with:

```css
:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
  border-radius: 2px;
  transition: outline-offset var(--duration-micro) var(--ease-out);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/base.css src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): button press, ghost hover, focus ring"
```

### Task 2.4: Loading filament

**Files:**
- Create: `src/main/resources/static/js/ui-elevation.js`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Add filament CSS**

Append to `base.css`:

```css
#loading-filament {
  position: fixed;
  top: 0;
  left: 0;
  height: 2px;
  width: 0%;
  background: var(--gold-sheen);
  z-index: 10001;
  pointer-events: none;
  opacity: 0;
  transition: opacity 120ms ease, width 80ms linear;
}
body.loading #loading-filament {
  opacity: 1;
}
```

- [ ] **Step 2: Add filament element + wiring to global JS**

Create `src/main/resources/static/js/ui-elevation.js` with:

```javascript
(function () {
  'use strict';

  function initLoadingFilament() {
    const bar = document.createElement('div');
    bar.id = 'loading-filament';
    document.body.appendChild(bar);

    let progress = 0;
    let raf = null;
    let active = false;
    let showTimer = null;

    function frame() {
      if (!active) return;
      progress += (95 - progress) * 0.08;
      bar.style.width = progress + '%';
      raf = requestAnimationFrame(frame);
    }

    document.body.addEventListener('htmx:beforeRequest', () => {
      active = true;
      progress = 0;
      bar.style.width = '0%';
      showTimer = setTimeout(() => {
        if (!active) return;
        document.body.classList.add('loading');
        bar.style.opacity = '1';
        raf = requestAnimationFrame(frame);
      }, 150);
    });

    document.body.addEventListener('htmx:afterRequest', () => {
      active = false;
      clearTimeout(showTimer);
      cancelAnimationFrame(raf);
      bar.style.width = '100%';
      setTimeout(() => {
        document.body.classList.remove('loading');
        bar.style.width = '0%';
        bar.style.opacity = '0';
      }, 180);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initLoadingFilament);
  } else {
    initLoadingFilament();
  }
})();
```

- [ ] **Step 3: Include the script in global head**

Edit `src/main/resources/templates/fragments/head.html` to add after `command-palette.js`:

```html
    <script th:src="@{/js/ui-elevation.js}"></script>
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/ui-elevation.js src/main/resources/static/css/base.css src/main/resources/templates/fragments/head.html
git commit -m "feat(ui-elevation): gold loading filament for htmx requests"
```

### Task 2.5: Toast system

**Files:**
- Modify: `src/main/resources/static/js/ui-elevation.js`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Add toast CSS**

Append to `components.css`:

```css
#toast-container {
  position: fixed;
  bottom: var(--space-lg);
  right: var(--space-lg);
  z-index: 10000;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  pointer-events: none;
}

.toast {
  background: var(--elevation-floating-bg);
  border: 1px solid var(--color-gold-soft);
  border-left: 3px solid var(--color-accent);
  border-radius: var(--radius);
  padding: var(--space-sm) var(--space-md);
  min-width: 240px;
  max-width: 360px;
  color: var(--color-text);
  box-shadow: var(--shadow-warm-md);
  opacity: 0;
  transform: translateY(12px);
  transition: opacity var(--duration-standard) var(--ease-out), transform var(--duration-standard) var(--ease-out);
  pointer-events: auto;
}
.toast.show { opacity: 1; transform: translateY(0); }
.toast.toast-error { border-left-color: var(--color-danger); }
.toast.toast-success { border-left-color: var(--color-success); }
```

- [ ] **Step 2: Add toast JS API**

Append to `ui-elevation.js`:

```javascript
  function initToasts() {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      document.body.appendChild(container);
    }

    window.showToast = function(message, type = 'info', duration = 3000) {
      const toast = document.createElement('div');
      toast.className = 'toast toast-' + type;
      toast.textContent = message;
      container.appendChild(toast);
      requestAnimationFrame(() => toast.classList.add('show'));
      setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 220);
      }, duration);
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initToasts);
  } else {
    initToasts();
  }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/ui-elevation.js src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): floating toast system"
```

### Task 2.6: Empty states

**Files:**
- Modify: `src/main/resources/templates/common/_empty-state.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Read current empty-state template**

Read `src/main/resources/templates/common/_empty-state.html`.

- [ ] **Step 2: Replace template content**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="empty-state" th:fragment="empty-state(message, actionText, actionHref)">
    <p th:text="${message}">No items yet.</p>
    <a th:if="${actionHref != null}" th:href="${actionHref}" th:text="${actionText}"
       class="btn btn-primary">Create</a>
</div>
</html>
```

- [ ] **Step 3: Update callers to pass action params**

Modify `src/main/resources/templates/campaigns/list.html` line 20:

```html
<th:block th:replace="~{common/_empty-state :: empty-state('No campaigns yet. Every saga starts with a blank page.', 'Create campaign', @{/campaigns/new})}"></th:block>
```

Modify `src/main/resources/templates/library/_card.html` empty-state line 40:

```html
<div class="empty-state"><p>No statblocks match your search.</p></div>
```

(Keep library empty text-only; no universal action.)

- [ ] **Step 4: Add empty-state hover/lift**

Append to `.empty-state` in `components.css`:

```css
.empty-state {
  transition: opacity var(--duration-standard) var(--ease-out), transform var(--duration-standard) var(--ease-out);
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/common/_empty-state.html src/main/resources/templates/campaigns/list.html src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): designed empty-state fragment with action slot"
```

### Task 2.7: Contrast lift and typography corrections

**Files:**
- Modify: `src/main/resources/static/css/tokens.css`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Lighten muted text**

In `tokens.css`, replace `--color-text-muted: #9c8e74;` with:

```css
  --color-text-muted: #b3a88f;
```

- [ ] **Step 2: Reserve Cinzel for headers; use UI sans for dense grids**

In `components.css`, replace `.statblock-card h3` block:

```css
.statblock-card h3 { font-size: var(--text-lg); margin: 0; font-family: var(--font-ui); font-weight: 600; }
```

And replace `.card h3` block:

```css
.card h3 {
  font-size: var(--text-lg);
  margin-bottom: var(--space-xs);
  font-family: var(--font-ui);
  font-weight: 600;
}
```

- [ ] **Step 3: Page header anatomy**

In `base.css`, replace `.page-header` block:

```css
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
  gap: var(--space-md);
  flex-wrap: wrap;
}

.page-header h1 {
  font-size: var(--text-2xl);
  font-weight: 700;
  margin-bottom: var(--space-xs);
}

.page-header .page-header-eyebrow {
  font-family: var(--font-ui);
  font-size: var(--text-xs);
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: var(--color-text-muted);
}

.page-header .rule-taper--gold {
  max-width: 320px;
  transform-origin: left center;
  animation: rule-draw var(--duration-theatrical) var(--ease-out) forwards;
}

@keyframes rule-draw {
  from { transform: scaleX(0); opacity: 0; }
  to { transform: scaleX(1); opacity: 1; }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/tokens.css src/main/resources/static/css/components.css src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): contrast lift, grid typography, header anatomy"
```

---

## Rollout Step 3: Chrome

### Task 3.1: Sidebar unification

**Files:**
- Modify: `src/main/resources/templates/fragments/_appnav.html`
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Read current `_appnav.html`**

Read `src/main/resources/templates/fragments/_appnav.html`.

- [ ] **Step 2: Enhance active state and transitions**

Ensure the existing `.appnav-link[aria-current="page"]` rule in `base.css` is:

```css
.appnav-link {
  display: block;
  padding: 6px var(--space-sm);
  border-radius: var(--radius);
  color: var(--color-text-muted);
  text-decoration: none;
  font-size: var(--text-sm);
  border-left: 2px solid transparent;
  transition: color var(--duration-micro) var(--ease-out), background var(--duration-micro) var(--ease-out), border-left-color var(--duration-micro) var(--ease-out), transform var(--duration-micro) var(--ease-out);
}
.appnav-link:hover { color: var(--color-text); background: var(--elevation-floating-bg); border-left-color: var(--color-gold-soft); transform: translateX(2px); }
.appnav-link[aria-current="page"] {
  color: var(--color-accent);
  border-left-color: var(--color-accent);
  background: var(--elevation-floating-bg);
}
```

- [ ] **Step 3: Add collapse-on-narrow hover labels if not present**

If `_appnav.html` is text-only, add `aria-label` and a CSS tooltip pattern is unnecessary; the existing mobile wrap is acceptable. Skip if already unified.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): sidebar active gold edge and hover motion"
```

### Task 3.2: Top-bar controls

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Replace DM Mode checkbox with a switch**

In `navbar.html`, replace:

```html
<label class="dm-toggle">
    <input type="checkbox" id="dmModeCheckbox">
    DM Mode
</label>
```

with:

```html
<label class="dm-toggle switch-control" title="DM Mode toggles player-safe projection">
    <input type="checkbox" id="dmModeCheckbox">
    <span class="switch-track"></span>
    <span class="switch-label">DM Mode</span>
</label>
```

- [ ] **Step 2: Add switch CSS**

Append to `base.css`:

```css
.switch-control {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  user-select: none;
}
.switch-control input { position: absolute; opacity: 0; width: 0; height: 0; }
.switch-track {
  width: 40px;
  height: 22px;
  background: var(--elevation-floating-bg);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  position: relative;
  transition: background var(--duration-standard) var(--ease-out), border-color var(--duration-standard) var(--ease-out);
}
.switch-track::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  background: var(--color-text-muted);
  border-radius: 50%;
  transition: transform var(--duration-standard) var(--ease-spring), background var(--duration-standard) var(--ease-out);
}
.switch-control input:checked + .switch-track { background: rgba(201, 163, 92, 0.15); border-color: var(--color-accent); }
.switch-control input:checked + .switch-track::before { transform: translateX(18px); background: var(--color-accent); }
.switch-control input:focus-visible + .switch-track { outline: 2px solid var(--color-accent); outline-offset: 2px; }
.switch-label { font-size: var(--text-sm); color: var(--color-text-muted); }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): DM Mode toggle switch"
```

### Task 3.3: Command palette elevation

**Files:**
- Modify: `src/main/resources/templates/fragments/_command-palette.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Read current palette template**

Read `src/main/resources/templates/fragments/_command-palette.html`.

- [ ] **Step 2: Update overlay and box to floating elevation**

In `_command-palette.html`, change the root div to use Alpine transition classes:

```html
<div class="command-palette-overlay" th:fragment="command-palette"
     x-data="commandPalette" x-show="open"
     x-transition:enter="cmd-enter"
     x-transition:enter-start="cmd-enter-start"
     x-transition:enter-end="cmd-enter-end"
     x-transition:leave="cmd-leave"
     x-transition:leave-start="cmd-leave-start"
     x-transition:leave-end="cmd-leave-end"
     @click.self="open = false"
     ...>
```

In `components.css`, replace `.command-palette-overlay` and `.command-palette-box`:

```css
.command-palette-overlay {
  position: fixed; inset: 0;
  background: rgba(10, 7, 4, 0.72);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 15vh;
}

.cmd-enter { transition: opacity var(--duration-standard) var(--ease-out); }
.cmd-enter-start { opacity: 0; }
.cmd-enter-end { opacity: 1; }
.cmd-leave { transition: opacity var(--duration-standard) var(--ease-in); }
.cmd-leave-start { opacity: 1; }
.cmd-leave-end { opacity: 0; }

.command-palette-box {
  background: var(--elevation-floating-bg);
  border: 1px solid var(--color-gold-soft);
  border-radius: 10px;
  width: 560px; max-height: 70vh;
  display: flex; flex-direction: column;
  box-shadow: var(--shadow-warm-lg), var(--highlight-candle);
  overflow: hidden;
  transition: transform var(--duration-standard) var(--ease-out), opacity var(--duration-standard) var(--ease-out);
}
.cmd-enter-start .command-palette-box { opacity: 0; transform: translateY(-12px) scale(0.98); }
.cmd-enter-end .command-palette-box { opacity: 1; transform: translateY(0) scale(1); }
.cmd-leave-start .command-palette-box { opacity: 1; transform: translateY(0) scale(1); }
.cmd-leave-end .command-palette-box { opacity: 0; transform: translateY(-8px) scale(0.99); }
```

- [ ] **Step 3: Add visible ⌘K hint in navbar**

In `navbar.html`, add inside `.navbar-right` before the dice toggle:

```html
<button class="btn btn-ghost u-text-sm palette-hint" onclick="window.dispatchEvent(new CustomEvent('command-palette-toggle'))" title="Search everything">
    ⌘K
</button>
```

Add CSS in `base.css`:

```css
.palette-hint { color: var(--color-text-muted); font-family: var(--font-mono); }
.palette-hint:hover { color: var(--color-accent); }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/_command-palette.html src/main/resources/templates/fragments/navbar.html src/main/resources/static/css/components.css src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): floating command palette and visible shortcut hint"
```

### Task 3.4: Keyboard shortcut overlay

**Files:**
- Modify: `src/main/resources/static/js/ui-elevation.js`
- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/static/js/keyboard.js`

- [ ] **Step 1: Add shortcut overlay HTML/JS**

Append to `ui-elevation.js`:

```javascript
  function initShortcutOverlay() {
    const overlay = document.createElement('div');
    overlay.id = 'shortcut-overlay';
    overlay.innerHTML = `
      <div class="shortcut-overlay-backdrop"></div>
      <div class="shortcut-panel">
        <h2>Keyboard Shortcuts</h2>
        <dl>
          <dt>⌘ / Ctrl + K</dt><dd>Search everything</dd>
          <dt>Ctrl + R</dt><dd>Toggle dice roller</dd>
          <dt>?</dt><dd>Show this overlay</dd>
          <dt>Esc</dt><dd>Close overlays</dd>
        </dl>
      </div>
    `;
    document.body.appendChild(overlay);

    function toggle(show) {
      overlay.classList.toggle('open', show);
    }

    document.addEventListener('keydown', (e) => {
      if (e.key === '?' && !e.ctrlKey && !e.metaKey && !e.altKey) {
        const tag = document.activeElement?.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA') return;
        toggle(true);
      }
      if (e.key === 'Escape') toggle(false);
    });

    overlay.addEventListener('click', () => toggle(false));
    window.toggleShortcutOverlay = toggle;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initShortcutOverlay);
  } else {
    initShortcutOverlay();
  }
```

- [ ] **Step 2: Add overlay CSS**

Append to `base.css`:

```css
#shortcut-overlay {
  position: fixed; inset: 0; z-index: 1100;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; pointer-events: none;
  transition: opacity var(--duration-standard) var(--ease-out);
}
#shortcut-overlay.open { opacity: 1; pointer-events: auto; }
.shortcut-overlay-backdrop { position: absolute; inset: 0; background: rgba(10, 7, 4, 0.72); backdrop-filter: blur(4px); }
.shortcut-panel {
  position: relative;
  background: var(--elevation-floating-bg);
  border: 1px solid var(--color-gold-soft);
  border-radius: 10px;
  padding: var(--space-lg);
  min-width: 320px;
  box-shadow: var(--shadow-warm-lg);
  transform: translateY(-12px) scale(0.98);
  transition: transform var(--duration-standard) var(--ease-out);
}
#shortcut-overlay.open .shortcut-panel { transform: translateY(0) scale(1); }
.shortcut-panel h2 { font-family: var(--font-display); margin-bottom: var(--space-md); }
.shortcut-panel dl { display: grid; grid-template-columns: auto 1fr; gap: var(--space-sm) var(--space-md); }
.shortcut-panel dt { font-family: var(--font-mono); color: var(--color-accent); }
.shortcut-panel dd { color: var(--color-text-muted); }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/ui-elevation.js src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): ? shortcut overlay"
```

---

## Rollout Step 4: Campaign Shelf + Dashboard (Signature Moment)

### Task 4.1: Bookshelf layout

**Files:**
- Modify: `src/main/resources/templates/campaigns/list.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Replace campaign grid with shelf**

Edit `campaigns/list.html`:

```html
<main class="app-main">
    <div class="page-header">
        <div>
            <div class="page-header-eyebrow">The Grimoire</div>
            <h1>Campaigns</h1>
            <div class="rule-taper rule-taper--gold"></div>
        </div>
    </div>

    <div class="bookshelf" id="campaign-grid">
        <div class="shelf-row">
            <th:block th:if="${campaigns == null || campaigns.isEmpty()}">
                <th:block th:replace="~{common/_empty-state :: empty-state('No campaigns yet. Every saga starts with a blank page.', 'Create campaign', @{/campaigns/new})}"></th:block>
            </th:block>
            <th:block th:each="campaign : ${campaigns}">
                <th:block th:replace="~{campaigns/_card :: card(campaign=${campaign})}"></th:block>
            </th:block>
            <a th:href="@{/campaigns/new}" class="book-cover book-cover--blank">
                <span class="book-cover-plus">+</span>
                <span class="book-cover-label">Blank Tome</span>
            </a>
        </div>
    </div>
</main>
```

- [ ] **Step 2: Add bookshelf CSS**

Append to `components.css`:

```css
.bookshelf { display: flex; flex-direction: column; gap: var(--space-xl); }
.shelf-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-lg);
  padding: var(--space-md) var(--space-md) var(--space-xl);
  border-bottom: 2px solid var(--color-border);
  box-shadow: 0 2px 0 rgba(255, 243, 210, 0.04);
  align-items: end;
}

.book-cover {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 4px 8px 8px 4px;
  background: linear-gradient(105deg, rgba(30,24,17,1) 0%, rgba(48,38,27,1) 45%, rgba(24,19,14,1) 55%, rgba(30,24,17,1) 100%);
  border: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-md);
  text-align: center;
  text-decoration: none;
  color: var(--color-text);
  box-shadow: var(--highlight-candle), 3px 4px 10px rgba(0,0,0,0.45);
  transition: transform var(--duration-standard) var(--ease-out), box-shadow var(--duration-standard) var(--ease-out), border-color var(--duration-standard) var(--ease-out);
  transform-origin: bottom center;
  overflow: hidden;
}
.book-cover::before {
  content: '';
  position: absolute; inset: 6px;
  border: 1px solid var(--color-gold-soft);
  border-radius: 2px 6px 6px 2px;
  pointer-events: none;
}
.book-cover h3 {
  font-family: var(--font-display);
  color: var(--color-accent);
  font-size: var(--text-lg);
  line-height: 1.3;
  margin-bottom: var(--space-sm);
  z-index: 1;
}
.book-cover .book-meta {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  z-index: 1;
}
.book-cover:hover {
  transform: perspective(800px) rotateY(-6deg) translateY(-6px);
  border-color: var(--color-gold-soft);
  box-shadow: var(--highlight-candle), var(--shadow-warm-md);
}
.book-cover:hover h3 {
  background: var(--gold-sweep);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  background-size: 200% 100%;
  animation: gold-sweep 700ms ease forwards;
}
@keyframes gold-sweep {
  from { background-position: 100% 0; }
  to { background-position: -100% 0; }
}

.book-cover--blank {
  border-style: dashed;
  background: transparent;
  color: var(--color-text-muted);
}
.book-cover--blank:hover { color: var(--color-accent); border-color: var(--color-accent); }
.book-cover-plus { font-size: var(--text-2xl); line-height: 1; }
.book-cover-label { font-size: var(--text-sm); }

.book-cover .card-actions {
  position: absolute;
  top: var(--space-xs);
  right: var(--space-xs);
  margin: 0;
  opacity: 0;
  transform: translateY(-4px);
  transition: opacity var(--duration-micro) var(--ease-out), transform var(--duration-micro) var(--ease-out);
}
.book-cover:hover .card-actions { opacity: 1; transform: translateY(0); }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/campaigns/list.html src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): campaign bookshelf layout"
```

### Task 4.2: Book-cover card and kebab delete

**Files:**
- Modify: `src/main/resources/templates/campaigns/_card.html`

- [ ] **Step 1: Replace card template**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<a class="book-cover" th:fragment="card(campaign)"
   th:href="@{/campaigns/{id}(id=${campaign.id})}">
    <h3 th:text="${campaign.name}">Campaign Name</h3>
    <div class="book-meta"
         th:text="${#temporals.format(campaign.createdAt, 'yyyy-MM-dd')}">Created date</div>
    <div class="card-actions" onclick="event.preventDefault(); event.stopPropagation();">
        <button class="btn btn-ghost btn-xs"
                th:hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
                hx-confirm="Delete this campaign?"
                hx-target="closest .book-cover"
                hx-swap="outerHTML swap:200ms">
            Delete
        </button>
    </div>
</a>
</html>
```

- [ ] **Step 2: Remove orphaned new-button from list header**

Delete `<th:block th:replace="~{campaigns/_new-button :: new-button}"></th:block>` from `campaigns/list.html` page header.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/campaigns/_card.html src/main/resources/templates/campaigns/list.html
git commit -m "feat(ui-elevation): book-cover card with hover kebab delete"
```

### Task 4.3: View transition wiring for "opening the book"

**Files:**
- Modify: `src/main/resources/templates/campaigns/_card.html`
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Modify: `src/main/resources/static/js/ui-elevation.js`
- Modify: `src/main/resources/static/css/base.css`

- [ ] **Step 1: Add transition names to source and destination**

In `_card.html`, add the dynamic transition name and `data-view-transition`:

```html
<a class="book-cover" th:fragment="card(campaign)"
   data-view-transition
   th:href="@{/campaigns/{id}(id=${campaign.id})}"
   th:style="'view-transition-name: campaign-cover-' + ${campaign.id}">
```

In `detail.html`, add the matching transition name to `.dash-header` (line 13):

```html
<div class="dash-header" th:style="'view-transition-name: campaign-cover-' + ${campaign.id}">
```

- [ ] **Step 2: Add cross-document view transition helper**

Append to `ui-elevation.js`:

```javascript
  function initViewTransitions() {
    if (!document.startViewTransition) return;
    document.addEventListener('click', (e) => {
      const link = e.target.closest('[data-view-transition]');
      if (!link) return;
      if (e.ctrlKey || e.metaKey || e.shiftKey) return;
      if (link.origin !== location.origin) return;
      e.preventDefault();
      document.startViewTransition(() => {
        location.href = link.href;
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initViewTransitions);
  } else {
    initViewTransitions();
  }
```

Add `data-view-transition` attribute to campaign card link:

```html
<a class="book-cover" data-view-transition ...>
```

- [ ] **Step 3: Add view-transition pseudo-element styling**

Append to `base.css`:

```css
::view-transition-old(root) { animation: fade-out var(--duration-theatrical) var(--ease-out) forwards; }
::view-transition-new(root) { animation: fade-in var(--duration-theatrical) var(--ease-out) forwards; }
@keyframes fade-out { from { opacity: 1; } to { opacity: 0; } }
@keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/campaigns/_card.html src/main/resources/templates/campaigns/detail.html src/main/resources/static/js/ui-elevation.js src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): cross-document view transition for opening a campaign"
```

### Task 4.4: Dashboard stagger entrance

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Add stagger classes to dashboard modules**

In `detail.html`, the three `<section class="card">` elements inside `.dash-grid` (lines 47, 60, 72) become:

```html
<section class="card dash-card" style="--stagger: 0">...</section>
<section class="card dash-card" style="--stagger: 1">...</section>
<section class="card dash-card" style="--stagger: 2">...</section>
```

- [ ] **Step 2: Add stagger animation CSS**

Append to `components.css`:

```css
.dash-card {
  opacity: 0;
  transform: translateY(12px);
  animation: dash-enter var(--duration-structural) var(--ease-out) forwards;
  animation-delay: calc(var(--stagger, 0) * var(--stagger-step));
}
@keyframes dash-enter {
  to { opacity: 1; transform: translateY(0); }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/campaigns/detail.html src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): dashboard stagger entrance"
```

---

## Rollout Step 5: Library

### Task 5.1: Library card grammar

**Files:**
- Modify: `src/main/resources/templates/library/_card.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Update card to hover-only actions**

Replace `library/_card.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="statblock-card" th:fragment="card(sb)">
    <h3>
        <a th:href="@{/library/statblocks/{id}(id=${sb.id})}" th:text="${sb.name}">Name</a>
    </h3>
    <div class="statblock-meta">
        <span th:text="'CR ' + ${sb.cr}">CR 1/4</span>
        <span th:text="${sb.type}">Type</span>
        <span th:if="${sb.size != null}" th:text="${sb.size}"></span>
        <span th:if="${sb.source.name() == 'CUSTOM'}" class="badge-custom">Custom</span>
    </div>
    <div class="card-actions u-mt-auto">
        <th:block th:if="${sb.source.name() == 'SRD'}">
            <form th:hx-post="@{/library/statblocks/{id}/clone(id=${sb.id})}" hx-target="body" style="display:inline;">
                <button type="submit" class="btn btn-ghost btn-xs">Clone &amp; Edit</button>
            </form>
        </th:block>
        <th:block th:if="${sb.source.name() == 'CUSTOM'}">
            <a th:href="@{/library/statblocks/{id}/edit(id=${sb.id})}" class="btn btn-ghost btn-xs">Edit</a>
            <button class="btn btn-danger btn-xs"
                    th:hx-delete="@{/library/statblocks/{id}(id=${sb.id})}"
                    hx-confirm="Delete this statblock?"
                    hx-target="closest .statblock-card"
                    hx-swap="outerHTML swap:200ms">Delete</button>
        </th:block>
    </div>
</div>
```

- [ ] **Step 2: Add hover-only actions CSS**

Append to `components.css`:

```css
.statblock-card .card-actions {
  opacity: 0;
  transform: translateY(4px);
  transition: opacity var(--duration-micro) var(--ease-out), transform var(--duration-micro) var(--ease-out);
}
.statblock-card:hover .card-actions,
.statblock-card:focus-within .card-actions {
  opacity: 1;
  transform: translateY(0);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/library/_card.html src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): library card grammar and hover actions"
```

### Task 5.2: Instant search result count

**Files:**
- Modify: `src/main/resources/templates/library/list.html`
- Modify: `src/main/resources/templates/library/_card.html` card-list fragment

- [ ] **Step 1: Add result count target**

In `library/list.html`, inside the monsters section after the search bar, add:

```html
            <div class="result-count" id="library-count" aria-live="polite"></div>
```

The existing `hx-get="/library/statblocks"` target container already has `id="library-results"`.

- [ ] **Step 2: Backend returns count or count header**

Modify `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java` method `search(...)` (currently lines 61-74). Add `jakarta.servlet.http.HttpServletResponse response` as the last parameter and set the header before returning:

```java
    @GetMapping("/statblocks")
    public String search(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String cr,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String source,
                         Model model,
                         HttpServletResponse response) {
        StatBlock.Source sourceEnum = null;
        if (source != null && !source.isBlank()) {
            sourceEnum = StatBlock.Source.valueOf(source);
        }
        List<StatBlock> results = service.search(sourceEnum, cr, type, search);
        response.setHeader("X-Result-Count", String.valueOf(results.size()));
        model.addAttribute("statblocks", results);
        return "library/_card :: card-list";
    }
```

- [ ] **Step 3: Read result count in JS**

Append to `ui-elevation.js`:

```javascript
  function initResultCounts() {
    document.body.addEventListener('htmx:afterRequest', (e) => {
      const target = e.detail.elt;
      if (!target) return;
      const count = e.detail.xhr.getResponseHeader('X-Result-Count');
      if (count == null) return;
      const labelId = target.id.replace('-results', '-count');
      const label = document.getElementById(labelId);
      if (label) label.textContent = count + ' results';
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initResultCounts);
  } else {
    initResultCounts();
  }
```

- [ ] **Step 4: Add result count CSS**

Append to `components.css`:

```css
.result-count {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin-bottom: var(--space-md);
  min-height: 1.3em;
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/library/list.html src/main/resources/static/js/ui-elevation.js src/main/resources/static/css/components.css src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java
git commit -m "feat(ui-elevation): library result count on filter"
```

### Task 5.3: Statblock side sheet

**Files:**
- Modify: `src/main/resources/templates/library/_card.html`
- Modify: `src/main/resources/templates/library/detail.html`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/resources/static/js/ui-elevation.js`

- [ ] **Step 1: Convert library card link to open side sheet**

Change the link in `library/_card.html`:

```html
<button type="button" class="btn btn-ghost u-p-0"
        th:attr="data-statblock-url=@{/library/statblocks/{id}(id=${sb.id})}"
        onclick="openStatblockSheet(this.dataset.statblockUrl)">
    <span th:text="${sb.name}">Name</span>
</button>
```

- [ ] **Step 2: Create side-sheet HTML structure**

In `library/detail.html`, wrap the full-page shell so the sheet variant renders only the statblock body:

```html
<body>
    <th:block th:unless="${sheet}">
        <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
        <div class="app-shell">
            <th:block th:replace="~{fragments/_appnav :: appnav}"></th:block>
            <main class="app-main">
                <div class="page-header">
                    <a href="/library" class="btn btn-ghost">&larr; Back to Library</a>
                    <div class="u-flex">
                        ...existing action buttons...
                    </div>
                </div>
    </th:block>

    <div id="statblock-detail">
        <th:block th:replace="~{library/_statblock-renderer :: renderer(sb=${sb})}"></th:block>
    </div>

    <th:block th:if="${campaignId != null}">
        <div class="u-mt-lg">
            <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='STATBLOCK', targetId=${sb.id})}"></th:block>
        </div>
    </th:block>

    <th:block th:unless="${sheet}">
            </main>
        </div>
    </th:block>
</body>
```

Add a new method to `LibraryController` after `detail(...)` (currently lines 76-82):

```java
    @GetMapping("/statblocks/{id}/sheet")
    public String sheet(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        model.addAttribute("sheet", true);
        return "library/detail";
    }
```

- [ ] **Step 3: Add side-sheet CSS**

Append to `components.css`:

```css
.side-sheet-overlay {
  position: fixed; inset: 0; z-index: 900;
  background: rgba(10, 7, 4, 0.65);
  backdrop-filter: blur(3px);
  opacity: 0;
  transition: opacity var(--duration-standard) var(--ease-out);
}
.side-sheet-overlay.open { opacity: 1; }
.side-sheet {
  position: fixed; top: 0; right: 0; bottom: 0; width: min(680px, 92vw);
  background: var(--elevation-floating-bg);
  border-left: 1px solid var(--color-gold-soft);
  box-shadow: var(--shadow-warm-lg);
  transform: translateX(100%);
  transition: transform var(--duration-structural) var(--ease-out);
  z-index: 901;
  overflow-y: auto;
  padding: var(--space-lg);
}
.side-sheet.open { transform: translateX(0); }
.side-sheet-close {
  position: absolute; top: var(--space-md); right: var(--space-md);
  background: transparent; border: none; color: var(--color-text-muted);
  font-size: var(--text-xl); cursor: pointer;
}
```

- [ ] **Step 4: Add side-sheet JS**

Append to `ui-elevation.js`:

```javascript
  window.openStatblockSheet = async function(url) {
    let overlay = document.getElementById('statblock-sheet-overlay');
    let sheet = document.getElementById('statblock-sheet');
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.id = 'statblock-sheet-overlay';
      overlay.className = 'side-sheet-overlay';
      sheet = document.createElement('div');
      sheet.id = 'statblock-sheet';
      sheet.className = 'side-sheet';
      sheet.innerHTML = '<button class="side-sheet-close" onclick="closeStatblockSheet()">×</button><div class="side-sheet-content"></div>';
      document.body.appendChild(overlay);
      document.body.appendChild(sheet);
      overlay.addEventListener('click', closeStatblockSheet);
      document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeStatblockSheet(); });
    }
    try {
      const resp = await fetch(url + '/sheet');
      if (!resp.ok) throw new Error('sheet failed');
      sheet.querySelector('.side-sheet-content').innerHTML = await resp.text();
    } catch (e) {
      sheet.querySelector('.side-sheet-content').innerHTML = '<p class="text-muted">Could not load statblock.</p>';
    }
    overlay.classList.add('open');
    sheet.classList.add('open');
  };

  window.closeStatblockSheet = function() {
    const overlay = document.getElementById('statblock-sheet-overlay');
    const sheet = document.getElementById('statblock-sheet');
    if (overlay) overlay.classList.remove('open');
    if (sheet) sheet.classList.remove('open');
  };
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/library/_card.html src/main/resources/templates/library/detail.html src/main/resources/static/css/components.css src/main/resources/static/js/ui-elevation.js src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java
git commit -m "feat(ui-elevation): statblock side sheet from library"
```

---

## Rollout Step 6: Cockpit Choreography + DM-Mode Sweep

### Task 6.1: Initiative tracker animations

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Add turn-marker slide via CSS transition**

The active row already has `.active`. Ensure the marker changes are animated by adding to `.combatant-row`:

```css
.combatant-row {
  transition: background var(--duration-standard) var(--ease-out), border-left-color var(--duration-standard) var(--ease-out), transform var(--duration-standard) var(--ease-out), box-shadow var(--duration-standard) var(--ease-out);
}
.combatant-row.active {
  background: var(--color-combatant-active);
  border-left-color: var(--color-accent);
  box-shadow: inset 0 0 0 1px var(--color-gold-soft), var(--shadow-warm-sm);
  transform: translateX(4px);
}
```

- [ ] **Step 2: Numeric HP tick helper**

Append to `ui-elevation.js`:

```javascript
  window.tickNumber = function(element, to, duration = 300) {
    const from = parseInt(element.textContent, 10) || 0;
    const start = performance.now();
    function step(now) {
      const t = Math.min(1, (now - start) / duration);
      element.textContent = Math.round(from + (to - from) * t);
      if (t < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  };
```

In `_tracker.html`, in the selected combatant detail HP section (around line 534), add a displayed HP number after the input:

```html
<div class="detail-hp">
    <label>HP</label>
    <input type="number" x-model.number="editHp" @change="setHp()">
    <span class="hp-display" x-ref="hpDisplay" x-text="selected?.currentHp || 0"></span>
    <span>/</span>
    <span x-text="selected?.maxHp || '?'"></span>
    ...
</div>
```

Add an `x-effect` in the same detail section that runs whenever `selected.currentHp` changes:

```html
<div class="combatant-detail" x-show="selectedCombatantId && dmMode && selected" x-effect="tickNumber($refs.hpDisplay, selected?.currentHp || 0, 300)">
```

Style the display in `components.css`:

```css
.hp-display {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  min-width: 2.5em;
  text-align: center;
  color: var(--color-text);
}
```

- [ ] **Step 3: Condition dots pop-in**

Append to `components.css`:

```css
.cond-icon {
  animation: pop-in var(--duration-micro) var(--ease-spring) forwards;
}
@keyframes pop-in {
  from { transform: scale(0); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
```

- [ ] **Step 4: Bloodied threshold hairline**

The existing `.hp-mini-fill.bloodied` handles the bar. Also tint the row border on bloodied by adding:

```css
.combatant-row.bloodied { border-left-color: var(--color-danger); }
```

And in `_tracker.html`, add `bloodied: c.bloodied` to the existing `:class` binding on `.combatant-row` (line 471):

```html
<div class="combatant-row"
     :class="{ active: c.id === activeCombatantId, defeated: c.defeated, hidden: c.hidden, bloodied: c.bloodied }"
     ...>
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html src/main/resources/static/css/components.css src/main/resources/static/js/ui-elevation.js
git commit -m "feat(ui-elevation): tracker turn slide, HP tick, condition pop-in, bloodied hairline"
```

### Task 6.2: Party bar HP animation

**Files:**
- Modify: `src/main/resources/templates/party/_summary-bar.html`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMember.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberService.java` (if a factory/creation method exists)

- [ ] **Step 1: Add `currentHp` to `PartyMember`**

In `PartyMember.java`, add a `currentHp` field next to `maxHp` with getter/setter. Default it to `maxHp` in the setter or in service creation so existing members start at full HP.

```java
private int currentHp;

public int getCurrentHp() { return currentHp; }
public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }
```

In `PartyMemberService`, wherever a new `PartyMember` is created, set `currentHp = maxHp`.

- [ ] **Step 2: Add HP bars to party member chips**

Replace the chip body in `_summary-bar.html` with:

```html
<div class="party-member-chip">
    <span class="chip-name" th:text="${pm.characterName}">Name</span>
    <div class="chip-hp-bar">
        <div class="chip-hp-fill"
             th:style="'width:' + (${pm.maxHp} > 0 ? (${pm.currentHp} * 100 / ${pm.maxHp}) : 0) + '%'"
             th:classappend="${pm.maxHp > 0 && pm.currentHp * 2 < pm.maxHp} ? 'low' : ''"></div>
    </div>
    <span class="chip-stats">
        <span th:text="'AC ' + ${pm.ac}">AC 16</span>
        <span th:text="'PP ' + ${pm.passivePerception}">PP 17</span>
    </span>
</div>
```

- [ ] **Step 3: Style the HP bars with animated width**

Append to `components.css`:

```css
.chip-hp-bar {
  width: 100%;
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  margin: 2px 0 4px;
  overflow: hidden;
}
.chip-hp-fill {
  height: 100%;
  background: var(--color-success);
  border-radius: 2px;
  width: 0%;
  transition: width 400ms var(--ease-out), background-color 200ms var(--ease-out);
}
.chip-hp-fill.low {
  background: var(--color-danger);
  animation: pulse-danger 600ms var(--ease-out);
}
@keyframes pulse-danger {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(1.4); }
}
```

- [ ] **Step 4: Run party tests**

```bash
./mvnw test -Dtest=PartyControllerTest,PartyMemberServiceTest -q
```

Expected: BUILD SUCCESS. Update tests if they construct `PartyMember` without setting `currentHp`.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/party/_summary-bar.html src/main/resources/static/css/components.css src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMember.java src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyMemberService.java
git commit -m "feat(ui-elevation): party bar HP bars with width animation and low-HP pulse"
```

### Task 6.3: DM Mode shield sweep

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/static/js/ui-elevation.js`

- [ ] **Step 1: Add shield sweep element**

Append to `navbar.html` inside `<nav class="navbar">`:

```html
<div id="dm-shield-sweep"></div>
```

- [ ] **Step 2: Add shield sweep CSS**

Append to `base.css`:

```css
#dm-shield-sweep {
  position: fixed;
  inset: 0;
  z-index: 9998;
  pointer-events: none;
  background: linear-gradient(90deg, transparent 0%, rgba(168, 58, 50, 0.35) 50%, transparent 100%);
  transform: translateX(-100%);
  opacity: 0;
}
body.dm-mode-off #dm-shield-sweep {
  animation: shield-sweep var(--duration-theatrical) var(--ease-out) forwards;
}
body.dm-mode-on #dm-shield-sweep {
  animation: shield-sweep-reverse var(--duration-theatrical) var(--ease-out) forwards;
}
@keyframes shield-sweep {
  0% { transform: translateX(-100%); opacity: 0; }
  20% { opacity: 1; }
  100% { transform: translateX(100%); opacity: 0; }
}
@keyframes shield-sweep-reverse {
  0% { transform: translateX(100%); opacity: 0; }
  20% { opacity: 1; }
  100% { transform: translateX(-100%); opacity: 0; }
}
```

- [ ] **Step 3: Update JS to add dm-mode-on class and dispatch event**

In `navbar.html`, replace the checkbox handler with:

```javascript
(function() {
    const checkbox = document.getElementById('dmModeCheckbox');
    checkbox.addEventListener('change', () => {
        const on = checkbox.checked;
        document.body.classList.toggle('dm-mode-off', !on);
        document.body.classList.toggle('dm-mode-on', on);
        window.dispatchEvent(new CustomEvent('dm-mode-changed', { detail: { dmMode: on } }));
    });
    checkbox.checked = true;
    document.body.classList.add('dm-mode-on');
})();
```

- [ ] **Step 4: Respect reduced motion**

The existing `@media (prefers-reduced-motion: reduce)` already collapses animations. For safety, add:

```css
@media (prefers-reduced-motion: reduce) {
  #dm-shield-sweep { display: none; }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html src/main/resources/static/css/base.css
git commit -m "feat(ui-elevation): DM Mode shield sweep transition"
```

### Task 6.4: Re-verify player-safe projection

**Files:**
- Run tests.

- [ ] **Step 1: Run PlayerSafeProjectionServiceTest**

```bash
./mvnw test -Dtest=PlayerSafeProjectionServiceTest -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run CoreSessionLoopSmokeTest**

```bash
./mvnw test -Dtest=CoreSessionLoopSmokeTest -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit (if only test run, no code changes; skip commit or note results)**

No commit needed unless fixes are required.

---

## Rollout Step 7: Handout Overlay + Dice Roller Theatrics

### Task 7.1: Handout reveal

**Files:**
- Modify: `src/main/resources/templates/handout/_present-overlay.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Add parchment texture and unfolding animation**

Replace the `.handout-overlay` CSS block and add exit classes:

```css
.handout-overlay {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  background: radial-gradient(ellipse at center, rgba(20,14,8,0.94) 45%, rgba(0,0,0,0.98) 100%);
  cursor: pointer;
  opacity: 1;
  transition: opacity var(--duration-standard) var(--ease-out);
}
.handout-overlay.closing { opacity: 0; }
.handout-overlay img {
  max-width: 88vw;
  max-height: 80vh;
  border: 1px solid var(--color-gold-soft);
  border-radius: var(--radius);
  box-shadow: var(--shadow-warm-lg);
  background: #2a2218 var(--texture-parchment);
  opacity: 0;
  transform: scale(0.92) rotateX(8deg);
  animation: handout-unfold var(--duration-theatrical) var(--ease-out) forwards;
}
.handout-overlay.closing img {
  animation: handout-fold var(--duration-theatrical) var(--ease-in) forwards;
}
@keyframes handout-unfold {
  to { opacity: 1; transform: scale(1) rotateX(0deg); }
}
@keyframes handout-fold {
  from { opacity: 1; transform: scale(1) rotateX(0deg); }
  to { opacity: 0; transform: scale(0.92) rotateX(8deg); }
}
.handout-overlay .handout-caption {
  opacity: 0;
  animation: fade-in var(--duration-standard) var(--ease-out) 250ms forwards;
}
.handout-overlay.closing .handout-caption {
  animation: fade-out var(--duration-standard) var(--ease-in) forwards;
}
```

Update `_present-overlay.html` to use a dismiss function:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="overlay" class="handout-overlay" id="handoutOverlay"
     onclick="dismissHandout(this)">
    <img th:src="@{/files/{id}(id=${handout.id})}" th:alt="${handout.title}"
         onclick="event.stopPropagation()">
    <div class="handout-caption" th:text="${handout.title}" onclick="event.stopPropagation()">Title</div>
    <div class="overlay-close-hint">
        Click anywhere to close
    </div>
</div>
</html>
```

Append to `ui-elevation.js`:

```javascript
  window.dismissHandout = function(overlay) {
    if (overlay.classList.contains('closing')) return;
    overlay.classList.add('closing');
    setTimeout(() => overlay.remove(), 320);
  };
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/handout/_present-overlay.html src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): theatrical handout unfold reveal"
```

### Task 7.2: Dice roller theatrics

**Files:**
- Modify: `src/main/resources/templates/fragments/_dice-roller.html`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/resources/static/js/ui-elevation.js`

- [ ] **Step 1: Add tumbling die icon and result bounce**

In `_dice-roller.html`, replace the roll button (lines 24-27) with:

```html
            <button @click="roll()" :disabled="loading || !expression">
                <span class="die-icon" :class="{ tumbling: loading }">&#x2684;</span>
                <span x-show="!loading">Roll</span>
                <span x-show="loading">...</span>
            </button>
```

Add `x-ref="resultBox"` to the result container (line 43):

```html
<div class="dice-result" x-show="result" x-transition x-ref="resultBox">
```

- [ ] **Step 2: Add dice animation CSS**

Append to `components.css`:

```css
.die-icon { display: inline-block; transition: transform var(--duration-micro) var(--ease-out); }
.die-icon.tumbling { animation: die-tumble 500ms linear infinite; }
@keyframes die-tumble {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
.dice-result {
  transition: transform var(--duration-standard) var(--ease-spring), box-shadow var(--duration-standard) var(--ease-out);
}
.dice-result.bounce { animation: result-bounce 350ms var(--ease-spring); }
@keyframes result-bounce {
  0% { transform: scale(1); }
  50% { transform: scale(1.12); }
  100% { transform: scale(1); }
}
.dice-history-item {
  opacity: 0;
  transform: translateX(-12px);
  animation: history-slide var(--duration-standard) var(--ease-out) forwards;
}
@keyframes history-slide {
  to { opacity: 1; transform: translateX(0); }
}
```

- [ ] **Step 3: Wire bounce on result**

In `_dice-roller.html` Alpine `roll()` method, after `this.result = await resp.json()`, add:

```javascript
this.$nextTick(() => {
  const el = this.$refs.resultBox;
  if (el) {
    el.classList.remove('bounce');
    void el.offsetWidth;
    el.classList.add('bounce');
  }
});
```

Add `x-ref="resultBox"` to the `.dice-result` element.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/_dice-roller.html src/main/resources/static/css/components.css
git commit -m "feat(ui-elevation): dice tumbling icon, result bounce, history slide"
```

---

## Rollout Step 8: Tier-2 Sweep

### Task 8.1: Headers, empty states, states audit

**Files:**
- Multiple Tier-2 templates in `src/main/resources/templates/`

- [ ] **Step 1: Apply page-header anatomy to remaining Tier-2 pages**

For each Tier-2 page, wrap the `<h1>` with:

```html
<div class="page-header">
    <div>
        <div class="page-header-eyebrow">Context</div>
        <h1>Page Title</h1>
        <div class="rule-taper rule-taper--gold"></div>
    </div>
    <div class="page-header-actions">...</div>
</div>
```

Tier-2 pages to update:
- `party/list.html`
- `encounter/list.html`
- `encounter/detail.html`
- `notes/list.html`
- `notes/detail.html`
- `maps/list.html`
- `maps/editor.html`
- `calendar/overview.html`
- `ledger/list.html`
- `treasury/list.html`
- `adventure/list.html`
- `adventure/detail.html`
- `handout/list.html`
- `sheet/detail.html`
- `sheet/overview.html`

- [ ] **Step 2: Replace generic empty states**

For each list page using inline empty text, replace with `<th:block th:replace="~{common/_empty-state :: empty-state('...', '...', @{...})}">` where a primary action exists; otherwise keep text-only empty-state using the styled fragment.

- [ ] **Step 3: Commit per subsystem or once**

```bash
git add src/main/resources/templates/
git commit -m "feat(ui-elevation): Tier-2 header anatomy and empty-state audit"
```

### Task 8.2: Final verification

**Files:**
- All modified files.

- [ ] **Step 1: Run full test suite**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Start app and screenshot key screens**

```bash
./mvnw spring-boot:run -q
```

Open in browser:
- `/campaigns` — bookshelf, hover tilt, blank tome
- `/library` — search, card hover actions, side sheet
- `/campaigns/{id}/cockpit` (or session route) — tracker, DM mode toggle
- Handout present view
- Dice roller toggle

- [ ] **Step 3: Reduced-motion check**

Enable `prefers-reduced-motion: reduce` in OS/browser devtools and verify no jarring motion; only opacity fades remain.

- [ ] **Step 4: Contrast spot-check**

Use browser DevTools accessibility panel on text-muted elements; confirm WCAG AA on actual backgrounds.

- [ ] **Step 5: Commit verification notes (optional)**

No code change; skip commit.

---

## Self-review

### Spec coverage

| Spec section | Implementing task(s) |
|---|---|
| §3.1 Depth & light | 1.1, 1.2, 1.3 |
| §3.2 Texture | 1.1, 1.2 |
| §3.3 Motion system | 2.1, 2.2, 4.3, 4.4, 6.1, 7.1, 7.2 |
| §3.4 Interaction states | 2.3, 2.4, 2.5, 2.6, 4.2, 5.1 |
| §3.5 Hierarchy & typography | 2.7 |
| §3.6 Navigation & chrome | 3.1, 3.2, 3.3, 3.4 |
| §5.1 Campaign shelf | 4.1, 4.2, 4.3 |
| §5.2 Dashboard | 4.4 |
| §5.3 Library | 5.1, 5.2, 5.3 |
| §6.1 Initiative tracker | 6.1 |
| §6.2 Party bar | 6.2 |
| §6.3 DM Mode sweep | 6.3 |
| §6.4 Handout overlay | 7.1 |
| §6.5 Dice roller | 7.2 |
| §8 Rollout order | Tasks map 1:1 to steps 1–8 |
| §9 Verification | Each step ends with run/test commands; final verification in 8.2 |

### Placeholder scan

- No "TBD", "TODO", "implement later", "fill in details".
- No "add appropriate error handling" style vagueness.
- No "write tests for the above" without code.
- No "similar to Task N" shortcuts.
- All referenced functions (`openStatblockSheet`, `tickNumber`, `showToast`) are defined in plan tasks.

### Type consistency

- `--ease-out`, `--duration-*`, `--elevation-*` used consistently.
- `.book-cover` replaces `.campaign-cover`; no orphaned references.
- `dm-mode-on` added alongside existing `dm-mode-off`.
- `sheet=true` query param used in controller and JS consistently.

---

## Execution handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-14-ui-elevation-phase2.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per rollout step/task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using `executing-plans`, batch execution with checkpoints for review.

Which approach would you like?
