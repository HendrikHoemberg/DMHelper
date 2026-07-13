# DMHelper UI/UX Overhaul — Design Spec

**Date:** 2026-07-13 · **Status:** Approved design, pre-implementation
**Scope:** Full visual identity replacement + structural UX (navigation, app shell, session mode)

## 1. Problem

The current UI is an undesigned default: navy/purple "AI dark mode" palette (`#1a1a2e` /
`#7b68ee`, Segoe UI) with no connection to the product; ~15 inline `style=` attributes in the
navbar alone and dozens more across ~50 templates; a flat 10-link top navbar mixing prep-time
and at-the-table tools; and no visual identity anywhere — statblocks, one of the most iconic
print designs in gaming, render as generic admin cards.

## 2. Design direction: "Selective ornament" dark grimoire

**Aesthetic:** sourcebook/grimoire, dark theme only ("candlelit study"). The app chrome —
sidebar, forms, buttons, tables — stays quiet and flat. Content that represents an in-world
artifact (statblocks, handouts, adventure text) gets the full sourcebook treatment. The
contrast is the point: quiet tool, rich artifacts.

**Ornament budget (hard rule):** gold hairlines, tapered rules, and drop caps in book content.
Nothing else. No textures, no corner filigree, no decorative borders on chrome. Any future
addition of ornament must amend this spec first.

### 2.1 Palette

Every color moves from cool to warm. The navy/purple palette dies entirely.

| Role | Value (approx — tune on screen) | Notes |
|---|---|---|
| Background | `#181410` | Dark leather/walnut, not "dark mode blue" |
| Surface | `#221c15` | Cards, sidebar, panels |
| Surface raised/hover | `#2a231a` | Separated by hairlines, not glow shadows |
| Text | `#e9dfc9` | Warm parchment |
| Text muted | `#9d8f76` | Dusty tan |
| Accent (primary) | `#c9a35c` | Aged gold — links, active states, focus rings, hairlines |
| Accent (secondary) | `#a83a32` | Ember red — statblock headers, drop caps, danger. Reserved so it stays meaningful |
| Success / HP | moss green (tune) | Status colors re-tuned warm |
| Warning | amber (tune) | |
| Condition colors | fixed small palette | Existing tracker condition colors re-tuned to warm palette, recognizable at a glance |

### 2.2 Typography (all vendored — offline at the table is a hard requirement)

1. **Display — Cinzel** (SIL OFL): page titles, statblock names, section headers, small doses.
2. **Book text — Alegreya** (SIL OFL): notes, wiki, adventure text, handouts, statblock
   bodies, spell descriptions. Real italics.
3. **UI — system sans stack**: forms, tables, buttons, sidebar, tracker rows. Dense
   operational UI stays sans.
4. **Mono** (existing stack): dice expressions and tabular numbers (HP, initiative, gold)
   with `font-variant-numeric: tabular-nums`.

Fonts ship as latin woff2 subsets in `static/fonts/` with `@font-face` in `tokens.css`,
`font-display: swap`. License note in `VENDOR.md`.

### 2.3 Component kit

One consistent kit in CSS: cards with hairline borders (no glow shadows); buttons in three
weights (gold-filled primary is rare, ghost is default); warm form inputs; **the tapered
rule** (thin line swelling to a point — classic sourcebook divider) as a reusable element for
statblocks and page headers. All values come from CSS custom properties.

## 3. App shell & navigation

Replaces the 10-link top navbar.

**Slim top bar** — global/live things only: campaign name (breadcrumb to dashboard), dice
roller toggle, Player View + QR, DM Mode switch, PIN. DM Mode gets a strong visual
treatment: when OFF (screen is player-safe) the top bar shows an unmistakable state change
(gold hairline switches to a distinct "shielded" tint). Readable from across the room —
this is a safety feature.

**Grouped left sidebar** — all navigation. Inside a campaign:

- **(Campaign home)** — dashboard, top item
- **Prep** — Adventures, Encounters, Maps, Handouts, Notes & Wiki
- **Party** — Roster, Sheets, Treasury, Ledger
- **World** — Calendar, Library

Outside a campaign: Campaigns / Library / About. Section labels in small Cinzel caps with a
gold hairline (full extent of sidebar ornament). Collapsible to icons (small screens, map
editor). **Run Session** is a single prominent gold action in the sidebar footer — the one
"mode" button in the app.

The current global right-hand 280px column (`app-layout`) stops being a global fixture;
panels like quick-notes become per-page concerns.

The command palette (Ctrl+K) stays everywhere, including session mode.

## 4. Session cockpit ("Run Session")

An evolution of the existing battle screen (`maps/battle.html`), promoted to the campaign's
single live surface. Sidebar collapses away entirely; a persistent "End Session" affordance
in the top bar is the only way back to prep screens.

- **Center stage:** battle map; when no map is active, the current scene's read-out (works
  for pure roleplay scenes). Map toolbar rebuilt with the component kit: grouped icon
  buttons, one row.
- **Right rail:** initiative tracker as the flagship component — compact combatant rows,
  tabular-numeral HP, condition dots, gold "active turn" marker. Tabs behind it: scene panel
  (exists) and a quick statblock viewer (clicking a monster in the tracker shows its
  statblock in the rail, no navigation).
- **Bottom strip:** party bar — persistent thin strip with AC, passive perception,
  HP-at-a-glance for every PC.
- **Top bar (session):** scene/encounter name in Cinzel, round & turn counter, dice toggle,
  DM Mode switch, End Session. Nothing else.

**DM Mode in the cockpit:** flipping it off visibly transforms the screen — monster HP
becomes descriptors ("bloodied"), the right rail collapses, player-safe state obvious at a
glance.

**Ornament level: near zero.** Cinzel for the scene name, tapered rule under the tracker
header, and that's it. Sourcebook flavor lives in the statblock viewer.

## 5. Content treatments

### 5.1 Library & statblocks

`_statblock-renderer.html` becomes the crown jewel: classic Monster Manual anatomy adapted
to dark — creature name in Cinzel small-caps, ember-red section headings (Traits, Actions,
Legendary Actions), tapered rules between sections, six-ability table with mod/save pairs,
Alegreya body with italic flavor text. Spell cards (school + level header line), items,
feats, conditions are small variations on the same statblock grammar — one grammar, not
eleven separately-styled cards. Library browsing chrome stays quiet: filter bar + dense
results grid.

### 5.2 Notes, wiki & prep reading

Anywhere rendered markdown appears (notes, wiki, adventure/chapter/scene text, handouts)
becomes a "book surface": Alegreya at ~68ch measure, heading hierarchy in Cinzel, drop cap
on the first paragraph of adventure chapters and handouts only, wiki links as gold ink.
Editing stays functional — book styling applies to preview/read views, not inputs. The
handout presentation overlay gets the most theatrical version (vignette backdrop, generous
type) — it is literally shown to players.

### 5.3 Campaign dashboard & list

Campaign detail becomes a home screen: title in Cinzel with tapered rule; "continue where
you left off" (last-touched scene/encounter/note); party at a glance (party bar component
reused); upcoming calendar events; pinned notes; Run Session call-to-action mirrored here.
Campaign list cards get a typographic "book cover" treatment — title, hairline frame,
optional per-campaign accent color. No fake leather textures.

## 6. Technical execution

### 6.1 CSS architecture

`app.css` splits into plain static files (no build step; `<link>`s in the head fragment):

- `tokens.css` — the design system; the only file with raw values; includes `@font-face`
- `base.css` — reset, typography, shell
- `components.css` — buttons, cards, forms, tables, tracker rows, tapered rule
- `book.css` — statblock + reading surfaces
- screen-specific files where needed (cockpit, map editor)

No raw hex outside `tokens.css`.

### 6.2 Strategy: re-theme first, restructure second

Existing templates already consume `--color-bg`, `--color-surface`, etc. Step one redefines
those same variable names with the warm values + new fonts — the whole app flips to grimoire
in one commit before any template surgery. Structural work then proceeds screen-by-screen;
the app stays fully usable between every step.

Inline styles are purged per-template as each screen gets its structural pass — each
`style="..."` becomes a component class or dies. No separate big-bang sweep.

### 6.3 Rollout order (each step ships a working app)

1. Tokens + fonts + base re-theme
2. App shell: sidebar, top bar, DM-Mode treatment
3. Statblock + library
4. Book surfaces: notes, wiki, handouts (incl. presentation overlay)
5. Session cockpit
6. Dashboard + campaign list
7. Final consistency sweep (remaining inline styles, stragglers)

### 6.4 Verification

- Existing tests (form rendering, player-safe projection, Playwright smoke) stay green
  throughout — they pin behavior while the skin changes.
- Each screen pass ends with a real-browser screenshot eyeball check.
- DM-Mode hiding behavior explicitly re-verified after steps 2 and 5 — restyling must not
  break a safety feature.

## 7. Out of scope

- Light/parchment theme (dark grimoire only in this overhaul)
- Any new features or data-model changes — this is UI/UX only
- Player-view redesign beyond what falls out of shared components
- Textures, illustrations, or any ornament beyond the budget in §2
