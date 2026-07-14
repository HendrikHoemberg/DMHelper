# The Living Grimoire — UI Elevation, Phase 2

**Date:** 2026-07-14 · **Status:** Draft for review
**Scope:** Whole app, tiered. Visual/interaction elevation only — no new features, no data-model changes.
**Prerequisite:** The 2026-07-13 UI overhaul (grimoire re-theme, app shell, cockpit, statblocks) is implemented and is the baseline this spec builds on.

---

## 1. Problem

Phase 1 gave the app a correct, coherent identity: warm palette, Cinzel/Alegreya, quiet chrome,
rich artifacts. But the result still reads as *basic*. Diagnosis, from screenshots of the current
build:

- **Everything is flat.** One background, hairline borders, no depth, no light. Surfaces don't
  layer; nothing feels touchable.
- **Nothing moves.** Pages swap instantly and statically. No screen has an entrance, no element
  acknowledges the pointer, no state change is choreographed. Modern apps feel alive because
  they respond *physically*; this one feels like documents.
- **Interaction states are missing or minimal.** Cards don't signal clickability; the only
  visible action on a campaign card is red *Delete*; hover/focus/loading/empty states are
  defaults or absent.
- **Hierarchy is monotone.** Small-caps Cinzel is used for page titles *and* every card title
  in a 60-card grid, so nothing stands out; secondary text sits below comfortable contrast.

"Wow" in a modern webapp is not one hero effect. It is the compound interest of material depth,
motion, and craft applied consistently — plus one signature moment people remember.

## 2. Design thesis

**A living grimoire: modern product physics wearing candlelit material.**

The app should feel like Linear-grade software — instant, fluid, layered, keyboard-first — whose
surfaces happen to be made of paper, leather, and gold ink instead of glass and neon. Three
pillars, in priority order:

1. **Material** — depth, light, and texture make surfaces feel physical.
2. **Motion** — choreographed, purposeful animation makes the app feel alive.
3. **Craft** — states, hierarchy, and detail make it feel expensive.

### Signature: "Opening the book"

The one element this app will be remembered by: **navigation into content is spatial, not a
page swap.** Opening a campaign, a statblock, a handout, or a note feels like opening a book —
the thing you clicked grows into the surface you land on (cross-document View Transitions;
the clicked card's cover morphs into the destination's header). Its hero expression is the
campaign shelf (§5.1); a quieter echo appears everywhere a card opens into a detail view.
Everything else stays disciplined so this one idea carries the identity.

### Amendment to the Phase-1 ornament budget

Phase 1 banned textures outright. This spec amends that: **quiet procedural texture is now in
budget** under the rules in §3.2. The ban on corner filigree, decorative borders on chrome, and
photographic/skeuomorphic material stays.

---

## 3. Tier 0 — Foundations (global systems)

These are design-system changes in `tokens.css` / `base.css` / `components.css` that every
screen inherits. They do the bulk of the "modern" work.

### 3.1 Depth & light

The current UI has one lighting model: none. Introduce a **candlelight model** — a warm key
light from the upper left, expressed as a small set of elevation tokens:

- **Elevation scale (3 steps):** base (page), raised (cards, sidebar), floating (popovers,
  dialogs, command palette). Each step combines: slightly lighter warm surface, a soft
  warm-black shadow (never pure black, never blue-gray), and a 1px inner top-edge highlight —
  the "candle catch" that makes a surface read as lit rather than outlined.
- **Hairlines stay** as the structural line language; shadows are added for *lift*, not drawn
  on everything. Resting cards keep hairline-only; hover and floating layers get the shadow.
- **Gold gains a sheen.** Interactive gold (buttons, active nav, turn marker) carries a subtle
  gradient rather than flat fill, and a slow light-sweep on hover — gold that behaves like
  metal, not paint. This is the palette's only "shiny" element; ember red and parchment stay matte.
- **Vignette:** the app frame gets an extremely subtle darkened edge (strongest in the cockpit
  and handout overlay, barely present on prep screens) — the candlelit-study feel without any
  literal candle imagery.

### 3.2 Texture

Rules first, then uses. **Rules:** procedural only (tiling noise / CSS gradients as data URIs —
no photos, no external requests, a few hundred bytes); opacity so low it disappears in a
squint test (≈2–4%); never behind dense body text at reading sizes; chrome gets at most grain,
artifacts may get more.

- **App background:** fine paper grain over the walnut base. Kills the "flat hex color" feel
  at nearly zero cost.
- **Artifact surfaces** (statblocks, handouts, book covers, adventure pages): a slightly
  stronger parchment-fiber texture plus a faint radial tone variation, so in-world documents
  feel like *material* while tool chrome stays smooth.
- **Book covers** (campaign cards, §5.1): CSS-gradient "leather tone" — deep tonal variation
  and an embossed hairline frame, explicitly not a photographic leather image.

### 3.3 Motion system

One vocabulary for the whole app, defined as tokens (durations, easings, stagger step) and a
small set of named patterns. Rules:

- **Durations:** ~120ms (micro: hover, press), ~200ms (standard: swaps, reveals), ~320ms
  (structural: panels, dialogs), ~500–700ms reserved for theatrical moments (shelf opening,
  handout reveal, DM-mode sweep). Nothing slower, ever.
- **Physics:** entrances decelerate, exits accelerate, nothing moves linearly. The gold turn
  marker and dialogs get a slight overshoot (spring feel); documents and pages never bounce.
- **Only transform and opacity animate.** No layout-property animation. 60fps on integrated
  graphics is a hard gate; any effect that can't hold it is cut, not throttled.
- **Choreography, not chaos:** at most one orchestrated moment per screen — a staggered
  entrance of its primary content (30–40ms stagger, first ~6 elements, then the rest appear
  together). Runs on navigation, never on htmx partial updates.
- **View transitions** (§2 signature) between documents; htmx swap/settle classes drive
  entrance animation of swapped fragments so filtered lists *settle* rather than blink.
- **`prefers-reduced-motion` collapses everything** to fast opacity fades. Non-negotiable.

### 3.4 Interaction states — the craft floor

Every interactive element in the app gets the full set; this is the largest single lever for
"modern" feel:

- **Hover:** clickable cards lift (2–3px translate + shadow step + hairline warms toward gold);
  buttons brighten; list rows tint. Nothing clickable is hover-silent.
- **Press:** buttons and cards compress slightly (~0.98 scale) — the tactile click.
- **Focus:** visible gold focus ring everywhere, keyboard-navigable throughout.
- **Loading:** a thin gold progress filament under the top bar for any htmx request over
  ~150ms; skeleton shimmer (warm, not gray) for slow-loading panels. No spinners.
- **Empty states:** every list surface gets a designed invitation — one line of in-world copy,
  one primary action. ("No campaigns yet. Every saga starts with a blank page." + *Create
  campaign*.) Errors state what happened and what to do, in the same voice, no apologies.
- **Feedback:** toast system (bottom-right, floating elevation, gold hairline) for
  confirmations; destructive actions get a confirm step and are *demoted* visually (kebab menu
  or hover-reveal, never a primary red link on a resting card — fixes the campaign card).

### 3.5 Hierarchy & typography corrections

- **Reserve small-caps Cinzel** for page titles, section headers, and artifact names
  (statblock headers, book covers). Dense grids (library cards) switch to sentence-case
  semibold UI sans for the name line — scannability first; the grimoire voice lives in the
  page header and the statblock, not sixty times per screen.
- **Contrast lift:** muted text moves up until it passes WCAG AA on its actual backgrounds;
  the moody palette survives a two-step lightening of `--color-text-muted`.
- **Page header anatomy**, one pattern everywhere: small eyebrow line for context (campaign
  name / section), Cinzel title, tapered rule that *draws in* once on page load (400ms, the
  one permitted flourish of chrome), actions aligned right on the same baseline.

### 3.6 Navigation & chrome unification

- **One sidebar, not two.** The icon rail and text sidebar merge into a single grouped sidebar
  (per Phase-1 information architecture) that collapses to labeled-on-hover icons. Active item
  carries a gold left edge + warm fill; transitions between sections use the standard motion tokens.
- **Top bar controls become real controls:** DM Mode's checkbox becomes a proper switch with
  the shielded-state treatment (§6.3); Player View and QR become recognizable ghost buttons.
- **Command palette becomes a first-class citizen:** floating elevation, warm glass backdrop
  blur, instant fuzzy results, and a visible hint in the top bar ("⌘K — search everything").
  Modern-app feel is keyboard-first feel; this is the cheapest big win in the app.
- **A `?` shortcut overlay** listing keyboard shortcuts, same floating treatment.
- **Warm scrollbars, gold text-selection color, themed native inputs** — the details that leak
  "unstyled" feeling get swept.

---

## 4. Tier system

| Tier | Surfaces | Treatment |
|---|---|---|
| 0 | Design system (above) | Everything inherits it |
| 1 | Campaign shelf, dashboard, library, session cockpit, handout overlay, dice roller | Foundations **plus** a designed signature moment each (§5–6) |
| 2 | Everything else: forms, roster, treasury, ledger, calendar, notes/wiki editing, maps list, settings | Foundations only — states, motion, header anatomy, empty states. No bespoke effects |

Tier 2 is deliberately unglamorous: consistency *is* its wow. A form that lifts, focuses, and
confirms beautifully reads as modern without a single bespoke effect.

---

## 5. Tier 1 — Prep surfaces

### 5.1 Campaign shelf (the front door, and the signature's hero)

The campaign list becomes a **bookshelf**. Each campaign is a book cover: portrait aspect,
leather-tone gradient in the campaign's accent color, embossed hairline frame, title in Cinzel
gold, author-line ("14 sessions · last played July 8"). Covers stand on a subtle shelf line.

- **Hover:** the book tilts up a few degrees and lifts toward the light (perspective transform
  + shadow), gold sheen sweeps the title. Unmistakably clickable, unmistakably *this app*.
- **Open:** the signature moment — the cover expands into the campaign dashboard via view
  transition: cover art becomes the dashboard header, title glides into the page-title slot.
  One continuous gesture from shelf to open book, ~500ms.
- **Create:** a ghost book ("blank tome" — dashed hairline, plus sign) at the end of the
  shelf, replacing the orphaned corner button. Delete lives in a kebab on the cover, confirm
  required.
- The empty shelf (zero campaigns) is the app's best empty state: a single lit blank tome
  centered, one line of copy.

### 5.2 Campaign dashboard

Already specced structurally in Phase 1; Phase 2 gives it the arrival moment. After the
open-book transition lands, the dashboard's modules (continue-where-you-left-off, party bar,
calendar, pinned notes) stagger in — the one orchestrated entrance of the prep area. *Run
Session* is the page's single gold-filled action, positioned as the destination of the eye's
natural path.

### 5.3 Library

The library's wow is **speed and focus**, not decoration:

- **Instant search:** debounced htmx as-you-type filtering; results settle in with swap
  animation; a result count ("312 monsters") makes the speed *visible*.
- **Card grammar refined:** sentence-case semibold names (§3.5), six columns with equal card
  heights, CR/type/size line unchanged. SRD badges disappear when the source filter makes them
  redundant (or mute to hairline chips); *Clone & Edit* appears on hover/focus only. Resting
  state: pure information. Hover: lift + actions. Result: half the visual noise at zero
  information loss.
- **Statblock as overlay:** clicking a card opens the statblock in a floating side sheet
  (view transition from card; browse position preserved, Esc closes). The crown-jewel
  statblock stops being a separate page you navigate to and becomes the payoff of every click.
  Sheet backdrop uses warm glass blur — floating elevation's showcase.

### 5.4 Notes, wiki, adventure reading

Book surfaces get the material pass (parchment fiber, §3.2) and one micro-signature: wiki
links underline with an ink-stroke animation on hover. Otherwise reading stays sacred and still.

---

## 6. Tier 1 — Table surfaces

At the table, motion must *convey state*, never entertain. Cockpit choreography is strictly
functional.

### 6.1 Initiative tracker (flagship component, now animated)

- **Turn advance:** the gold marker *slides* to the next combatant (spring easing, ~250ms);
  the active row lifts one elevation step and warms; the previous row settles back. Round
  rollover pulses the round counter once.
- **HP changes tick numerically** (count from old to new, ~300ms) with a brief red/green tint
  that fades — damage feels dealt, healing feels granted.
- **Condition dots pop in** with a micro-scale entrance; reorder on initiative change animates
  via FLIP-style row movement rather than teleporting.
- **Bloodied threshold:** the row's hairline shifts to ember — readable at a glance from
  across the table.

### 6.2 Party bar

HP bars animate width on change; a PC dropping below half pulses once. Otherwise still.

### 6.3 DM Mode — the safety feature as theater

Flipping DM Mode off (screen becomes player-safe) triggers a **full-screen shield sweep**: a
600ms wash crosses the viewport while DM-only elements fade behind it; the chrome's gold accent
shifts to a cooler "shielded steel" tint and a persistent *Shielded* chip appears in the top
bar. The transition makes the state change unmissable; the tint makes the state *legible from
across the room* even after the animation ends. Flipping back reverses it (candlelight
returns). This is the app's second most theatrical moment, and it's justified: it's a safety
feature that must never be ambiguous. Reduced-motion: instant swap, tint still applies.

### 6.4 Handout presentation overlay

The most theatrical surface — it is literally shown to players. Handout reveal: the vignette
deepens, the handout unfolds/scales up from center with the parchment texture at its
strongest, edges catching the light. A subtle, slow ambient light drift (the candle) may play
*only here* — the single ambient animation in the app. Dismissal reverses it.

### 6.5 Dice roller

Physicality: the roll button's die icon tumbles while rolling; results land with a single
bounce and a brief gold flash for nat 20 / ember for nat 1. Roll history entries slide in.
Total ≤400ms — at the table, dice are workflow, not fireworks.

### 6.6 Battle map (Konva island)

Excluded from CSS view transitions (canvas). Phase 2 asks only: token movement eases rather
than teleports, and the selected-token ring pulses subtly. Anything more is a future map spec.

---

## 7. Technical constraints

- **No build step, no new dependencies.** Everything above is achievable with CSS (view
  transitions, `@starting-style`, scroll/settle animations, gradients, data-URI noise),
  existing htmx/Alpine, and small vanilla-JS behaviors (number tick, FLIP reorder). If an
  effect seems to need a library, redesign the effect.
- **Local-first, offline at the table:** all assets stay vendored; textures are inline data
  URIs; zero network requests added.
- **Browser floor:** the DM controls the browser (local app) — target current Chrome/Firefox;
  every effect must *degrade silently* (no broken layout, just less motion) on anything older.
- **Player view** inherits foundations passively; no bespoke player-view work beyond shared components.
- **Sound is explicitly out of scope** — the table has its own soundtrack.

## 8. Rollout order (each step ships a working app)

1. **Foundations I — material:** depth/light tokens, textures, vignette, sheen, scrollbars/selection.
2. **Foundations II — motion & states:** motion tokens, view-transition wiring, hover/press/focus
   sweep, loading filament, toasts, empty states, contrast lift, typography corrections.
3. **Chrome:** sidebar unification, top-bar controls, command palette elevation, shortcut overlay.
4. **Campaign shelf + dashboard** (signature moment).
5. **Library** (instant search, card grammar, statblock side sheet).
6. **Cockpit choreography + DM-mode sweep** (re-verify player-safe hiding after this step — safety feature).
7. **Handout overlay + dice roller theatrics.**
8. **Tier-2 sweep** (headers, empty states, states audit on every remaining screen).

## 9. Verification & quality gates

- Existing tests (player-safe projection, Playwright smoke) stay green throughout.
- Every step ends with a real-browser screenshot eyeball check; steps 4–7 also get a
  screen-recording eyeball for motion feel.
- Reduced-motion pass after steps 2 and 7; contrast audit (AA) after step 2.
- Performance gate: interaction stays 60fps on integrated graphics; htmx round-trip UX never
  blocked by animation.
- DM Mode hiding re-verified after step 6 — theater must not compromise the safety feature.

## 10. Out of scope

- New features, data-model or backend changes; battle-map/editor redesign beyond §6.6.
- Light theme; sound; player interaction; mobile-first layouts (responsive degradation only).
- Any ornament beyond this spec's budget — further additions must amend this spec first.
