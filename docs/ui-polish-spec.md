# DMHelper — UI/UX Polish Specification

> **Superseded:** The whole-product redesign in
> `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`
> replaces this draft, including its existing-palette and desktop-adaptability constraints.

**Status:** Draft
**Author:** UX review (2026-07-15)
**Scope:** Visual and interaction polish on the existing DM-facing web app. No new
feature modules, no data-model changes, no new dependencies.

This spec is written to feed the SDD task-brief pipeline. Each section is
independently implementable and independently reviewable. Sections are ordered
by effort-to-impact; implement top-to-bottom unless noted.

---

## 0. Context & Constraints

### 0.1 Design tokens (already defined — reuse, do not add new colors)

From `src/main/resources/static/css/tokens.css`:

| Token | Value | Role |
|---|---|---|
| `--color-bg` | `#17120c` | page / input fill |
| `--color-surface` | `#211a12` | cards |
| `--color-surface-hover` | `#2b2318` | card hover |
| `--color-text` | `#e9dfc9` | body text |
| `--color-text-muted` | `#b3a88f` | secondary text |
| `--color-accent` | `#c9a35c` | aged gold — CTAs, focus |
| `--color-border` | `#3a3125` | warm hairline |
| `--font-display` | Cinzel | headers |
| `--font-book` | Alegreya | prose |
| `--radius` | `6px` | — |
| `--space-*` | 4/8/16/24/32 | spacing scale |

### 0.2 Global constraints

- **No new npm/Maven dependencies.** Everything below is CSS + Thymeleaf template edits.
- **Reuse existing tokens.** No new hex values except the single muted-text adjustment in §5.
- **No specificity wars.** New global CSS rules use `:where()` (specificity 0) so
  existing component rules keep winning.
- **Server-rendered.** All markup is Thymeleaf; there is no client-side framework to
  re-render. Verify by loading the page, not by unit-testing a component.

### 0.3 Explicit non-goals

- **Mobile / responsive layout is out of scope.** The app is desktop-only by design;
  do not add breakpoints, hamburger nav, or touch affordances.
- No dark/light theme toggle (app is dark-only).
- No copy rewrites beyond the empty-state and label strings named below.

---

## 1. Global form-control base style (fixes white inputs)

### 1.1 Problem

The Calendar "Advance by [ ] days" field renders as a **stark white box**. The
"Set Date" (year/month/day) fields and the event-form fields have the same defect.

### 1.2 Root cause

There is **no global `input` style**. Inputs are themed only through *scoped*
selectors — `.form-group input` (`components.css:174`), `.form-group select`
(`components.css:1930`), `.dice-input-row input[type="text"]` (`components.css:1127`).
Any input rendered **outside** those containers inherits the browser default (white
fill, system border). The calendar fields use bare utility classes
(`.calendar-days-input` = width only, `components.css:437`; `.u-w-80` = width only,
`components.css:1456`) and sit outside any `.form-group`, so they are unstyled.

This is a *class of bug*, not one field — the fix is a global fallback, not a
per-field patch.

### 1.3 Requirements

1. Add a global base rule that themes text-like inputs, `select`, and `textarea` to
   match the app: dark fill, warm border, gold focus ring. **Visual properties only**
   (color / background / border / border-radius / focus) — **do not** set global
   `padding`, `width`, or `font-size`, because component classes rely on their own
   sizing (e.g. the 70px calendar field, centered `.pm-num`).
2. Use `:where()` so specificity stays 0 and every existing scoped rule
   (`.form-group input`, `.dice-input-row input`, `.form-group select`) still overrides.
3. Scope to text-like types only. **Must not** affect `checkbox`, `radio`, `range`,
   `file`, `color` — notably the `.switch-control input` (`base.css:371`) and the file
   input at `campaigns/detail.html:30`.

### 1.4 Implementation

Add to `src/main/resources/static/css/base.css` (near the top-level element styles):

```css
/* Global fallback theme for any form control not covered by a component rule.
   :where() keeps specificity 0 so .form-group/.dice-input-row rules still win. */
:where(
  input[type="text"], input[type="number"], input[type="search"],
  input[type="email"], input[type="password"], input[type="url"],
  input[type="tel"], input[type="date"], input[type="time"],
  select, textarea
) {
  color: var(--color-text);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-family: inherit;
}
:where(
  input[type="text"], input[type="number"], input[type="search"],
  input[type="email"], input[type="password"], input[type="url"],
  input[type="tel"], input[type="date"], input[type="time"],
  select, textarea
):focus {
  outline: none;
  border-color: var(--color-accent);
}
```

### 1.5 Files

- Modify: `src/main/resources/static/css/base.css`

### 1.6 Acceptance criteria

- [ ] Calendar page `/campaigns/{id}/calendar`: "Advance by" field has dark fill + warm
      border, not white.
- [ ] Expand "Set Date": year/month/day fields are dark. Expand an event form: dark.
- [ ] The Add Party Member modal and Edit Campaign form look **unchanged** (their
      `.form-group` rules still win).
- [ ] The DM-Mode toggle switch and file-upload input are visually unchanged.
- [ ] Grep confirms no other bare-utility inputs remain white: load Ledger `_form`,
      Treasury `_form`, Sheet create — spot-check every visible input is dark.

---

## 2. Empty states with inline actions

### 2.1 Problem

Empty sections render as a single line of muted centered text and, in several
sections, offer **no action** at all — the user must find the top-right button. For a
new user this is most of the first-run experience and it neither teaches nor invites.

### 2.2 Current state

A shared fragment already exists:
`src/main/resources/templates/common/_empty-state.html`
with signature `empty-state(message, actionText, actionHref, description)`. It renders
a `<p>`, optional description `<p>`, and an optional `.btn.btn-primary` anchor.

Call sites that pass **no CTA** (the gap):

| Section | File | Current call |
|---|---|---|
| Encounters | `encounter/list.html:30` | `('No encounters yet. Create your first encounter!', null, null, null)` |
| Treasury | `treasury/list.html` (stash empty) | plain text "Party stash is empty" |
| Calendar timeline | `calendar/overview.html` | plain text "No timeline events" |
| Maps | `maps/*` list | plain text "No maps yet" (verify) |
| Handouts | `handout/list.html` | verify |
| Party roster | `party/*` | plain text "No party members yet…" |

Sections that already pass a CTA and just inherit the visual upgrade: Campaigns
(`campaigns/list.html:24`), Notes (`notes/list.html:55`).

### 2.3 Requirements

1. **Upgrade the fragment visually** into a proper centered empty state: optional
   decorative icon/glyph slot, a display-font headline (the `message`), a muted
   description line, and the CTA rendered as a prominent primary button. Add an
   optional `icon` parameter (a glyph string or inline SVG name); when null, render
   no icon. Keep the existing 4-arg signature working by adding `icon` as a **new
   optional 5th parameter** (or a Thymeleaf fragment overload) so current call sites
   don't break.
2. **Pass CTAs at the gap call sites** in §2.2. Each CTA points at that section's
   existing create route and uses action copy consistent with the top-right button
   (e.g. Encounters → "New encounter" → the same href as the "+ New Encounter" button).
3. The empty state must be **vertically centered in the content area** and use the
   real estate (see §3), not hug the top.
4. Copy: keep it short, one sentence of "why this matters," in the app's voice
   (the campaigns string "Every saga starts with a blank page." is the reference tone).

### 2.4 Implementation notes

- Extend `common/_empty-state.html`. Suggested structure:

```html
<div class="empty-state" th:fragment="empty-state(message, actionText, actionHref, description, icon)">
  <div th:if="${icon != null}" class="empty-state__icon" th:text="${icon}"></div>
  <p class="empty-state__title" th:text="${message}">No items yet.</p>
  <p th:if="${description != null}" class="empty-state__desc text-muted" th:text="${description}"></p>
  <a th:if="${actionHref != null}" th:href="${actionHref}" th:text="${actionText}"
     class="btn btn-primary empty-state__cta">Create</a>
</div>
```

- Add CSS to `components.css` for `.empty-state` (currently only has a transition at
  `components.css:1478`): center vertically + horizontally, `.empty-state__title` in
  `--font-display` at `--text-lg`, icon at large size with `--color-text-muted`,
  generous `--space-lg` gaps. Keep the existing `:has()` hide rules
  (`components.css:9,14`) intact.
- Suggested per-section copy (final, no placeholders):
  - Encounters: title "No encounters yet", desc "Build one ahead of the session so
    initiative is one click away.", CTA "New encounter".
  - Treasury: title "The party stash is empty", desc "Track coin, gems, and magic
    items the party is carrying.", CTA "Add item".
  - Calendar timeline: title "No events on the timeline", desc "Log festivals,
    deadlines, and things that happen while the party travels.", CTA "Add event".
  - Party roster: title "No heroes yet", desc "Add the player characters so combat,
    passive senses, and difficulty math work.", CTA "Add member".
  - Maps: title "No maps yet", desc "Draw a battle map or import an image to run
    encounters on the grid.", CTA "New map".
  - Handouts: title "No handouts yet", desc "Upload images or documents to reveal to
    players during a session.", CTA "Upload handout".

### 2.5 Files

- Modify: `src/main/resources/templates/common/_empty-state.html`
- Modify: `src/main/resources/static/css/components.css` (`.empty-state*` block)
- Modify each call site in §2.2 to pass icon + CTA args.

### 2.6 Acceptance criteria

- [ ] Every section in §2.2 shows an icon, a display-font headline, a description, and
      a working primary-button CTA that navigates to the correct create flow.
- [ ] Empty states are centered in the content area (not top-hugging).
- [ ] Existing Campaigns and Notes empty states still render and their CTAs still work.
- [ ] Appending a real card still hides the empty state (the `:has()` rules unbroken).

---

## 3. List-page density / vertical space

### 3.1 Problem

Maps, Encounters, and Treasury put one short card or bar at the top and leave ~70% of
the viewport empty and dark, so populated pages feel top-weighted and abandoned.

### 3.2 Requirements

1. Content lists use a **card grid** that fills width before flowing down (Maps
   already uses card-ish tiles — extend the same grid to Encounters and Treasury
   items).
2. When a list is **empty**, the empty state (from §2) occupies the vertical center of
   the content area rather than sitting at the top.
3. Do not stretch a single card to full width; cap card width (reuse the campaign
   `.book-cover` sizing or a `--card-min` track) and let the grid wrap.

### 3.3 Implementation notes

- Prefer a shared grid utility if one exists (grep `card-grid` in `components.css` —
  the campaigns list and party already use `.card-grid`). Apply the same class to the
  Encounters and Treasury lists rather than inventing a new grid.
- For vertical centering of the empty state, give the content wrapper a `min-height`
  and `display:flex; align-items:center; justify-content:center` **only when empty**
  (a `:has(.empty-state)` rule on the wrapper avoids affecting populated pages).

### 3.4 Files

- Modify: `src/main/resources/templates/encounter/list.html`
- Modify: `src/main/resources/templates/treasury/list.html`
- Modify: `src/main/resources/templates/maps/*` list template (confirm exact file)
- Modify: `src/main/resources/static/css/components.css`

### 3.5 Acceptance criteria

- [ ] With ≥3 items, Encounters/Treasury render a wrapping grid, not a single stacked
      column of full-width bars.
- [ ] With 0 items, the empty state is vertically centered.
- [ ] No horizontal overflow at 1440px.

---

## 4. Library scannability

### 4.1 Problem

The Monster Library is ~5 columns of near-identical low-contrast rows with tiny tag
chips — a wall of text that is hard to scan for a specific creature or CR.

### 4.2 Requirements

1. **Typographic hierarchy** inside each card: creature name larger / higher-contrast
   (`--color-text`, `--font-display` or heavier weight) vs. metadata in
   `--color-text-muted`.
2. **CR as a consistent, aligned element** (e.g. a right-aligned badge in a fixed slot)
   so the eye can scan CR down a column.
3. **Category color-coding on tags** using only existing semantic tokens
   (`--color-accent`, `--color-success`, `--color-danger`, `--color-warning`,
   `--color-shield`) — no new hues. Map a small fixed set (e.g. type or source) to
   these tokens.
4. This applies to the bestiary card `library/_card.html`; the same hierarchy pattern
   should be mirrored in the sibling cards (`_spell-card`, `_magic-item-card`,
   `_equipment-card`, `_class-card`) for consistency, but the bestiary is the priority.

### 4.3 Files

- Modify: `src/main/resources/templates/library/_card.html` (+ CSS in `components.css`)
- Optional follow-up: sibling `library/_*-card.html` files.

### 4.4 Acceptance criteria

- [ ] Creature name is visibly the dominant element in each card.
- [ ] CR appears in the same aligned position across every card.
- [ ] Tag chips carry category color; contrast of chip text meets §5.
- [ ] The floating statblock popover (click-to-open, already implemented) still works.

---

## 5. Accessibility: contrast + focus

### 5.1 Problem

- Secondary text (`--color-text-muted: #b3a88f` on `--color-bg: #17120c`) and the
  smaller muted metadata are low-contrast; several instances likely fail WCAG AA for
  small text.
- Keyboard focus is only guaranteed on the switch control
  (`base.css:394` has `:focus-visible`); links / buttons / inputs have no global
  visible focus ring.

### 5.2 Requirements

1. **Contrast:** raise the muted foreground until small text over `--color-bg` and over
   `--color-surface` reaches **WCAG AA (≥4.5:1)**. `#b3a88f` on `#17120c` is ~7:1
   already for the base pairing, but verify the *smaller / dimmed* uses (card metadata,
   empty-state description, nav section labels) — where opacity or a dimmer color is
   layered on top, those are the failing cases. Fix by using the token directly instead
   of stacking opacity, or introduce **one** slightly brighter step if needed.
2. **Focus:** add a global `:focus-visible` outline for interactive elements using the
   accent color, matching the existing switch pattern
   (`outline: 2px solid var(--color-accent); outline-offset: 2px;`).

### 5.3 Implementation

Add to `base.css`:

```css
:where(a, button, input, select, textarea, [tabindex]):focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
  border-radius: var(--radius);
}
```

Audit muted-text usages: grep for `opacity:` and `text-muted` in templates/CSS; where a
muted color is dimmed *further* by opacity, drop the opacity and rely on the token.

### 5.4 Files

- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/static/css/tokens.css` (only if a brighter muted step is
  needed after measuring)

### 5.5 Acceptance criteria

- [ ] Tab through Campaign Home, Library, Add-Member modal: every focused control shows
      a visible gold ring.
- [ ] Measured contrast of card metadata, empty-state description, and nav section
      labels is ≥4.5:1 (use a contrast checker on the rendered colors).
- [ ] No regression to the existing switch-control focus style.

---

## 6. Polish

### 6.1 Command-palette discoverability

**Problem:** the header trigger is a bare `⌘K` glyph
(`fragments/navbar.html:10`), cryptic to users who won't guess it.

**Requirement:** render it as a search affordance — a magnifier glyph + the word
"Search" + the `⌘K` shortcut hint — while keeping the same
`command-palette-toggle` event dispatch. Keep it compact; it stays in the top bar.

**Files:** `src/main/resources/templates/fragments/navbar.html` (+ minor CSS).

**Acceptance:** the header control reads as "Search" with a visible shortcut hint;
clicking it still opens the palette; `⌘K` still opens it.

### 6.2 Campaign tile identity

**Problem:** the flagship Campaigns screen shows placeholder-feeling tiles ("AWDAD",
"0 heroes") with only the light-sweep sheen for interest.

**Requirement:** give each campaign tile a visual anchor — either an optional
cover-image slot on the campaign, or a **generated deterministic sigil** (e.g. a
seeded geometric/rune motif derived from the campaign id) rendered as inline SVG so no
asset upload or dependency is required. Non-goal: an upload/CDN pipeline.

**Files:** `src/main/resources/templates/campaigns/_card.html` (+ CSS; + a small
Thymeleaf/util helper if generating the sigil server-side).

**Acceptance:** every campaign tile shows a distinct visual mark; tiles remain visually
consistent with the grimoire theme; no new dependency; the "+ Blank Tome" create tile
is unchanged.

---

## 7. Sequencing & review boundaries

Implement and review in this order (each is independently shippable):

1. **§1 Global form-control base** — smallest change, kills the white-input bug class.
2. **§2 Empty states** — highest first-run impact; depends on nothing.
3. **§5 Accessibility** — broad, low-risk correctness; do before visual polish so
    contrast fixes inform §4.
4. **§3 List density** — layout; pairs naturally with §2's centering.
5. **§4 Library scannability** — most involved template work.
6. **§6 Polish** — §6.1 quick; §6.2 larger (sigil generation) — do last.

## 8. Verification

There are no pure-CSS unit tests; verify by driving the running app
(`./mvnw spring-boot:run`, port 8081) with the existing Playwright setup and
re-capturing the review screenshots for: Calendar (§1), Encounters / Treasury / Party /
Maps / Handouts empty states (§2, §3), Library (§4), and a keyboard-focus pass (§5).
Compare against the pre-change screenshots. The app already depends on Playwright, so no
new tooling is required.
