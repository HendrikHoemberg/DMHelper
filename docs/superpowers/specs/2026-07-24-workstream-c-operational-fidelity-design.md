# Workstream C — Campaign Operational Fidelity Design Specification

**Date:** 2026-07-24

**Status:** Approved design

**Parent:** `2026-07-22-phandelver-all-in-one-corrective-design.md` §8 and §12 item 6.

**Position in delivery order:** Workstreams A (A1–A3) and B (B1–B2) are implemented and
verified. C is the next unstarted unit. D (Read/Run vs Edit/Admin) and E (visual refinement
and the §11.3 release rehearsal) follow C and are out of scope here.

## 1. Purpose and authority

The master corrective spec found that a schema-valid imported campaign is not necessarily a
*runnable* campaign: the evaluated import had 90 scenes but zero maps, zero prepared encounters,
and seven source-page image captures used as generic handouts. This design defines the corrective
work for Workstream C so that operational readiness is measured, explained and repairable
distinctly from schema validity.

This document is a focused sub-specification of the master corrective design. Where the master
spec and this document agree, the master spec remains authoritative for intent; this document is
authoritative for the concrete decisions that scope Workstream C into a single implementation
plan. It adds no copyrighted campaign content; committed fixtures remain synthetic and
content-free (counts, kinds and population booleans only).

## 2. Scope decisions

Three scoping decisions were resolved during design and bound this plan:

1. **Asset/map depth — Thin.** C introduces an asset *kind* taxonomy in the domain and package,
   the import path honors it, and the readiness report warns on unsafe or unclassified assets.
   C does **not** build a map-calibration tool. A printed tactical map with no derivable geometry
   yields an explicit readiness warning, which the master spec (§8.2) explicitly permits. In-app
   crop/redaction derivative tooling already shipped in A2 and is reused, not rebuilt.

2. **Signal source — Schema-carried optional fields plus conservative inference.** The campaign
   converter is external to this repository. C adds *optional*, backward-compatible package v2
   fields the converter *may* populate (asset kind, per-scene map-requirement hint, declared
   omissions). When absent, values default conservatively and the readiness engine infers what it
   can from imported domain data. The external converter is not required to change before C ships.

3. **Repair actions — Deep-link to preparation, build only the gaps.** Each readiness item links
   directly to the preparation surface that resolves it. C reuses existing actions (A1 scene
   encounter seeding, A2 handout safety review, scene/map editing) and builds new controls only
   where none exists: assign an asset kind, mark a map non-playable, and explicitly accept an
   omission or not-session-ready item. Readiness resolution is a pre-session preparation activity
   and does not alter the runtime cockpit gate.

## 3. Architecture

Readiness is computed by a single rule engine over a normalized input abstraction, so the
pre-commit import preview and the post-import campaign home can never disagree about whether a
campaign is session-ready.

- `ReadinessInputs` — a normalized, source-agnostic view of the campaign: scenes and their
  participants, participant statblock links, encounter links, maps and map requirements, image
  assets with kind and safety, runtime links (quests, rollable tables, treasures, transitions),
  declared omissions and existing readiness acknowledgements.
- `CampaignReadinessService` — computes a `CampaignReadinessReport` from `ReadinessInputs`. Pure
  rule logic; no persistence or transport concerns.
- Two assemblers build `ReadinessInputs`:
  - a pre-commit assembler over the pending package / `CampaignImportPreview` graph;
  - a persisted assembler over the campaign's repositories.

The readiness report is a distinct structure from the existing schema-validity output. It does not
replace `CampaignImportProblem` (schema validity) or reuse the `problems` list on
`CampaignImportPreview`; a package can be schema-valid and not session-ready simultaneously.

## 4. Asset model

`AssetKind` is a new enumeration and a separate axis from A2's `SafetyClassification`
(`DM_SOURCE`, `PLAYER_SAFE`, `PLAYER_DERIVATIVE`, `UNREVIEWED`):

- `PLAYER_HANDOUT`
- `DM_REFERENCE`
- `REGIONAL_MAP`
- `TACTICAL_MAP`
- `ILLUSTRATION`
- `SOURCE_PAGE`

Rules:

- Kind and safety are orthogonal and both required for presentation decisions. Kind describes what
  an image *is*; safety describes what may reach a player.
- Unknown kind defaults to `SOURCE_PAGE`, the most conservative interpretation, mirroring A2's
  `UNREVIEWED` safety default. The converter must never have a kind field silently coerce an image
  toward presentable.
- A `TACTICAL_MAP` for which no safe geometry can be derived is never auto-converted to a playable
  map. It surfaces as a readiness warning stating that conversion could not derive safe geometry.
  Grid/scale calibration is explicitly out of scope for C.

## 5. Package v2 schema and migration

C adds only optional, backward-compatible signals and bumps the package format version:

- per-asset `kind` (`AssetKind`);
- per-scene `mapRequirement` hint (`REQUIRED`, `OPTIONAL`, `NONE`);
- package-level declared omissions, expressed through the existing `CampaignExportExclusion`
  mechanism where it fits, extended only if a content-area omission cannot be represented.

Requirements:

- Reading a package that omits these fields succeeds and applies conservative defaults
  (`kind = SOURCE_PAGE`; `mapRequirement` inferred from scene participant disposition and any
  existing map link; omissions empty).
- Import adapters honor declared fields when present.
- Export writes the fields; a round-trip preserves kind, map-requirement and omissions.
- A migration and back-compatibility test proves that a prior-version package imports with correct
  defaults and that a new-version package round-trips without loss.

## 6. Readiness model and rules

`CampaignReadinessReport` covers the seven §8.1 categories:

1. hostile scenes and whether each can seed an encounter or has a linked prepared encounter;
2. participants missing resolvable statblocks;
3. scenes requiring a map and whether a playable or reference map is linked;
4. image assets grouped by kind and safety classification;
5. runtime links with unresolved targets: encounters, quests, rollable tables, treasures,
   transitions;
6. content areas deliberately omitted (declared by the converter or accepted by the DM);
7. the post-import actions needed before the first session.

Each readiness item has one of three states:

- `RESOLVED` — the requirement is satisfied.
- `BLOCKER` — unresolved and not acknowledged.
- `ACCEPTED` — explicitly acknowledged by the DM. Acceptance is persisted as a
  `ReadinessAcknowledgement` (item identity, who accepted, when) so an absence becomes an explicit,
  explained choice rather than an invisible one.

Session-ready rule: a campaign is labeled **"Valid but not session-ready"** while any `BLOCKER`
remains. Zero maps or zero prepared encounters is not automatically an error, but it can only
reach session-ready once the corresponding items are `ACCEPTED`. Reaching session-ready therefore
requires that every readiness item is `RESOLVED` or `ACCEPTED`.

Blocker classification (default, before acknowledgement):

- **Blockers:** a hostile scene with neither a seedable encounter nor a linked encounter; a
  participant in a hostile scene with no resolvable statblock; an image linked for player
  presentation that is `UNREVIEWED` or `DM_SOURCE`; a scene whose `mapRequirement` is `REQUIRED`
  with no map linked.
- **Advisories (not blockers):** unreviewed illustrations not linked for presentation; `OPTIONAL`
  map absences; unresolved non-critical runtime links; declared omissions. Advisories still appear
  in the report and may be accepted, but do not by themselves prevent session-ready.

## 7. Surfaces and repair actions

The readiness report renders on **both** surfaces and is visually distinct from schema-validity
problems:

- **Import preview** — shown before commit over the pending package, so the DM sees readiness
  before importing.
- **Campaign home** — shown after import over persisted state, with a status summary and per-item
  detail. C adds a readiness panel here; D later refines its placement within the home's Read/Run
  information architecture.

Repair behavior:

- Each item deep-links to the preparation surface that resolves it: A1 scene encounter seeding, A2
  handout safety review, and existing scene/map editing.
- C builds only the gap controls that do not yet exist:
  - assign an `AssetKind` to an image;
  - mark a map non-playable (DM reference);
  - accept an omission or a not-session-ready item, creating a `ReadinessAcknowledgement`.
- Resolving a blocker through any of these paths flips the item's state on the next report
  computation; the report never claims success for a failed action.

## 8. Fixture and testing

Fixture (extends the existing content-free `PackageShapeProfile` and `FixtureShapeCoverageTest`):

- covers statblock-linked and unlinked participants, safe and unsafe handouts, every `AssetKind`,
  maps present and absent, `REQUIRED`/`OPTIONAL`/`NONE` map requirements, declared omissions, and
  seedable versus non-seedable hostile scenes;
- provides both a **not-session-ready** shape (blockers present) and a **ready-after-repair**
  shape (all items resolved or accepted);
- commits only counts, kinds and population booleans; no copyrighted content.

Tests:

- readiness-service unit tests for each category and for the `RESOLVED` / `BLOCKER` / `ACCEPTED`
  transitions, including the session-ready rule;
- round-trip tests for the new optional package fields;
- a migration and back-compatibility test for the format version bump;
- integration tests proving the import preview and campaign home render the report, that the two
  assemblers agree on the same fixture, and that a repair deep-link or gap control flips a
  `BLOCKER` to `RESOLVED` (or `ACCEPTED`) with the campaign reaching session-ready;
- production-parity: readiness assembly runs inside an explicit read transaction with no lazy
  access during rendering, consistent with A1's `open-in-view=false` requirement.

## 9. Scope boundaries

**In scope (C):** the `AssetKind` model; optional package v2 signals with migration; the readiness
rule engine and report; both readiness surfaces; the three gap repair controls; the fixture and
test expansion above.

**Deferred to D:** restructuring the campaign home's Read/Run/Edit/Admin information architecture
around the readiness report. C contributes the panel and its data; D owns final placement and the
surrounding surface separation.

**Deferred to E:** the full §11.3 ten-step release rehearsal, the second synthetic campaign with
different branching, and visual-system refinement of the readiness surfaces.

**Non-goals:** map grid/scale calibration or any invented geometry; changing the external
converter; inline one-click resolution inside the report; any runtime or cockpit module change;
committing copyrighted campaign material.

## 10. Acceptance criteria for this plan

1. An imported package exposes a readiness report on both import preview and campaign home,
   distinct from schema validity, driven by one rule engine over two assemblers.
2. Every image carries an `AssetKind`; unknown kinds default to `SOURCE_PAGE`; kind and safety are
   independent and both consulted before presentation.
3. New optional package fields round-trip; a prior-version package imports with conservative
   defaults; the migration test passes.
4. A campaign with unresolved blockers is labeled "Valid but not session-ready"; it reaches
   session-ready only when every item is `RESOLVED` or `ACCEPTED`, with acceptances persisted.
5. Each readiness item deep-links to a working repair path; the three gap controls exist and
   function; resolving an item updates the report truthfully.
6. The synthetic fixture exercises both not-ready and ready-after-repair shapes with no copyrighted
   content, and all listed tests pass.

This plan does not claim the overall all-in-one premise; that remains gated by §11 of the master
spec after D and E.
