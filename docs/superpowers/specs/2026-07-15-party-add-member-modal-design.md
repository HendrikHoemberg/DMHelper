# Party "Add / Edit Member" — Modal + Class Dropdown

**Date:** 2026-07-15
**Status:** Approved (pending spec review)

## Problem

The "Add Party Member" form is injected as a **grid item** into `#party-grid`, which
uses `repeat(auto-fill, minmax(300px, 1fr))`. When the party is empty, the form is
locked to a single ~300px column, stranded in the bottom-left of an otherwise empty
page. Consequences:

- 4-column (AC / Max HP / Init / Speed) and 3-column (passive senses) rows are crushed
  into ~300px → labels wrap to two lines and the native number spinners overlap the
  values.
- The panel looks cramped and randomly placed; poor UX for multi-field data entry.

Secondary issues discovered:

- The `_form` fragment is shared by **Add** (HTMX inline) and **Edit**
  (`GET /{id}/edit` also returns `party/_form :: form`). Edit is a plain `<a href>`
  full-page navigation, so today clicking **Edit** lands you on the *bare* form fragment
  with no navbar/shell — a naked form on an empty page.
- The `Class & Level` field is freeform text. The user wants Class driven by the SRD
  class list, with subclasses excluded.

## Goals

1. Present Add/Edit as a focused, roomy **centered modal dialog**.
2. Fix the crushed numeric inputs (spinner overlap, label wrapping).
3. Turn Class into a **dropdown** of base SRD classes; keep Level as a number.
4. Remove subclasses from the SRD class list — from the JSON **and** the already-seeded
   Library DB.
5. Fix the bare-page Edit as a side effect (Edit becomes a modal too).

Non-goals: no changes to the party entity/schema for class/level; no redesign of the
party cards, summary bar, or Library UI.

## Design

### A. Modal form factor (native `<dialog>`)

Reuse the existing native-`<dialog>` idiom (as in `sheet-level-up-dialog`, with
`::backdrop`).

- `list.html` gains a persistent, initially-empty
  `<dialog id="party-form-modal" class="modal">`.
- **+ Add Member** and each card's **Edit** do an HTMX `GET` that loads the `_form`
  fragment into `#party-form-modal` (innerHTML swap), then open it via `showModal()`
  (triggered from `hx-on::after-swap` on the container, or an inline open call on the
  fragment).
- Closes on: Cancel button, Esc (native dialog behavior), the × header button, and
  backdrop click (a `close()` when the click target is the `<dialog>` itself).
- On successful submit, close the modal. The submit still targets `#party-grid`:
  - Add → `hx-swap="beforeend"` appends the new card (existing behavior; the
    empty-state `:has(.card)` rule hides the placeholder automatically).
  - Edit → the returned card replaces the edited card. (Edit is being moved onto the
    HTMX/modal path; the PUT handler is adjusted to return the updated
    `party/_card :: card` fragment targeting that card instead of a full-page redirect.)

### B. Modal layout (~600px max-width)

- `Character Name` — full width, required
- Row: `Player Name` · `Class` (dropdown) · `Level` (number)
- **Combat** group (light group label): `AC` · `Max HP` · `Init` · `Speed` — 4 columns,
  all required
- **Passive Senses** group (light group label; short labels since the header carries the
  "Passive" context): `Perception` · `Insight` · `Investigation` — 3 columns, required
- `Notes` — full width textarea
- Sticky footer, right-aligned: `Cancel` (ghost) + `Add`/`Save` (primary)
- Header: title (`Add Party Member` / `Edit <name>`) + × close button, divider below.

### C. Class → dropdown, Level → number

- **Data source:** `PartyController` injects `CharacterClassRepository` and calls
  `findBySubclassOfIsNullOrderByNameAsc()` (already exists) to get the base classes.
  Their names are added to the model (e.g. `classNames`) for both `newForm` and
  `editForm`. Because the query filters `subclassOf IS NULL`, the dropdown never shows
  subclasses even independent of the JSON edit.
- **Level:** a separate small number input (`min="1"`).
- **Storage — no schema change.** Class + Level are combined into the existing
  `classAndLevel` string on submit, **client-side**:
  - A hidden input `name="classAndLevel"` is populated on form submit by concatenating
    the selected class and the level (e.g. `"Rogue 5"`; class only if level blank).
  - The real `<select>` and level input use non-submitting names (or are excluded from
    submission) so only the combined `classAndLevel` reaches the controller — the
    controller and DTO are unchanged.
- **Edit prefill / round-trip:** given a stored `classAndLevel` string, split on the
  trailing integer: the leading text is matched against the dropdown options (select it
  if it matches a known class), the trailing integer prefills Level.
- **Legacy / non-parsing fallback:** if the stored string doesn't cleanly match a known
  class (e.g. `"Rogue 5 (multiclass)"`), keep it simple: leave the dropdown on a
  neutral/placeholder option and preserve the original string so a save without touching
  the field doesn't destroy it. (Acceptable simple fallback — no fuzzy matching.)

### D. Remove subclasses from the SRD class list

1. **JSON:** delete the 12 `subclass_of != null` entries from
   `src/main/resources/srd/srd-5.2-classes.json` (24 → 12 base classes:
   Barbarian, Bard, Cleric, Druid, Fighter, Monk, Paladin, Ranger, Rogue, Sorcerer,
   Warlock, Wizard). `CharacterClassSeedService.seedIfEmpty()` then seeds only base
   classes on a fresh DB.
2. **Migration:** the seed only runs when `character_class` is empty, so already-seeded
   databases keep their subclasses. Add
   `src/main/resources/db/migration/V2__remove_class_subclasses.sql`:
   `DELETE FROM character_class WHERE subclass_of IS NOT NULL;`
   (confirm exact table/column names against `V1__baseline.sql` / the `CharacterClass`
   entity during implementation).

   Note: subclasses disappear from the Library too (accepted by the user).

### E. CSS

- Reusable modal styles: `.modal` panel (surface bg, border, radius, `max-width`,
  `var(--shadow)`), `::backdrop`, header row + divider, sticky footer.
- Light group-label styling for the "Combat" / "Passive Senses" subheadings.
- Number-spinner reset to remove the overlapping arrows:
  `input[type=number] { appearance: textfield; }` and the
  `::-webkit-inner/outer-spin-button { appearance: none; margin: 0; }` pair. Center
  numeric values.

## Files touched

- `src/main/resources/templates/party/_form.html` — restructure into modal content;
  class dropdown + level input + hidden `classAndLevel`; group labels; header/footer.
- `src/main/resources/templates/party/list.html` — persistent `<dialog>` container;
  point **+ Add Member** trigger at it.
- `src/main/resources/templates/party/_card.html` — convert **Edit** `<a href>` into an
  HTMX modal trigger.
- `src/main/java/.../party/web/PartyController.java` — inject
  `CharacterClassRepository`; add class names to model in `newForm`/`editForm`; adjust
  the edit (`PUT`) handler to return the updated card fragment for the modal flow.
- `src/main/resources/static/css/components.css` — modal, group labels, spinner reset.
- `src/main/resources/srd/srd-5.2-classes.json` — drop subclass entries.
- `src/main/resources/db/migration/V2__remove_class_subclasses.sql` — new migration.

## Testing / verification

- Empty party: **+ Add Member** opens a centered modal (not a stranded 300px card);
  numeric fields are roomy, labels don't wrap, no spinner overlap.
- Add a member → card appends, empty-state hides, modal closes.
- Class dropdown lists exactly the 12 base classes; no subclasses.
- Add with Class + Level → card shows `"Rogue 5"`.
- Edit an existing member → modal prefills all fields, class dropdown + level parsed
  from `classAndLevel`; Save updates the card in place (no bare page).
- Legacy non-parsing `classAndLevel` survives an untouched save.
- Fresh DB seeds 12 classes; existing DB has subclasses removed after `V2` runs; Library
  shows no subclasses.
- Esc / × / backdrop / Cancel all dismiss the modal.
