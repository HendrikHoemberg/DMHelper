# DMHelper All-in-One DM Readiness — Master Design Specification

**Date:** 2026-07-15
**Status:** Approved master design
**Product premise:** A D&D DM can prepare and run a complete campaign using DMHelper alone,
without consulting PDFs, books, spreadsheets, or separate map, initiative, notes, handout, and
character-management tools.
**Secondary premise:** The application and its campaign format are documented precisely enough
that an agent can translate a legally supplied campaign source into a package that validates,
previews, imports, and runs correctly in DMHelper.

## 1. Purpose and relationship to the existing specification

`SPEC.md` remains the original product vision and architectural baseline. This document records
the gap between that vision and the application inspected on 2026-07-15, then defines the target
behavior required to close it.

This is a **master specification**, not a single implementation unit. It deliberately decomposes
the work into separately testable workstreams. Each workstream must receive its own focused design
and implementation plan before code changes begin. This document is authoritative for priority,
cross-module contracts, release gates, and acceptance criteria shared between those workstreams.

This document does not authorize importing or distributing copyrighted campaign content. Source
material handling must respect the user's rights, the source's license, and applicable law. The app
must preserve provenance and keep non-redistributable imported content local by default.

## 2. Executive assessment

DMHelper already has a broad and technically credible foundation:

- local-first Spring Boot application with embedded persistence;
- campaigns, adventures, chapters, and scenes;
- map editor, battle map, tokens, and live player projection;
- encounters, initiative, conditions, concentration, legendary/lair actions, and combat undo;
- party roster and partially rules-aware character sheets;
- notes, wiki links, quick notes, handouts, treasury, ledger, calendar, and dice;
- a bundled SRD 5.2 compendium;
- startup backups, schema migrations, PIN-gated DM routes, and player-safe server filtering;
- 405 automated tests passing at assessment time.

The application is not yet dependable as the sole tool for arbitrary campaigns. Its main gaps are:

1. Existing features are not joined by a real session cockpit.
2. The published campaign schema and the importer disagree.
3. Export/import does not preserve the full campaign state.
4. Several time-critical UI paths are broken despite the green test suite.
5. Adventure content is insufficiently structured for faithful source conversion.
6. Character sheets and custom rules content are not complete enough to replace books.
7. Documentation describes intended behavior without clearly distinguishing implemented behavior.

The recommended strategy is **contract first, session workflow second, depth third, expansion
fourth**. Adding more isolated modules before repairing those foundations would increase surface
area without fulfilling the all-in-one premise.

## 3. Current capability baseline

The following table is a product assessment, not a code-coverage report.

| Area | Current strength | Primary limitation | Readiness |
|---|---|---|---|
| Campaign management | CRUD, import/export entry points, backups | Incomplete round-trip; validator/schema drift | Partial |
| Adventures | Adventure/chapter/scene hierarchy and run cursor | Mostly linear Markdown blobs; weak branching and structured challenges | Partial |
| Session running | Battle map and encounter tracker work together | “Run Session” opens the first map instead of a session workspace | Not ready |
| Maps | Rich grid editor, backgrounds, tokens, measurement, AoE | Published-map calibration, reveal state, semantic runtime terrain missing | Strong partial |
| Encounters | Strong live combat state and action log | Prep, waves, rewards, session-log integration, exact difficulty rules incomplete | Strong partial |
| Party roster | Useful combat projection and passives | Not a complete party/character state source | Strong partial |
| Character sheets | Derived abilities, skills, rests, spells, resources | Creation and management omit many first-class character decisions | Partial |
| Compendium | Broad bundled SRD 5.2 reference set | Custom content exists properly only for statblocks | Strong partial |
| Notes/wiki | Typed Markdown notes, links, backlinks, search | Links use ambiguous names; world entities remain unstructured | Partial |
| Quick notes | Good intended capture/promote workflow | Current template emits literal Thymeleaf placeholders | Broken path |
| Handouts | Image storage and player presentation | Image-only, state not fully exported, unsafe imported filenames | Partial |
| Search | Global command palette concept | Multiple generated destinations do not exist; no shared route contract | Broken path |
| Treasury/ledger | Assignments, attunement, transactions | Weak connection to encounters, shops, consumables, encumbrance | Partial |
| Calendar/timeline | Custom month lengths/names and dated events | Campaign settings are not exported; travel/weather not modeled | Partial |
| Player view | Server-filtered read-only live projection | Presentation controls and reveal semantics can go deeper | Strong partial |
| Documentation | Large vision spec and feature design history | Aspirational and implemented behavior are mixed | Partial |

## 4. Product principles

All workstreams must follow these rules.

### 4.1 Session speed is the primary UX metric

The DM must be able to reach or operate any prepared element in no more than two deliberate
actions from the session cockpit. Search, quick capture, handout presentation, scene advancement,
and encounter activation must never require navigating through administrative screens.

### 4.2 Structured where the app acts; prose where the DM reads

Information the app must query, validate, filter, reveal, calculate, or transition must be stored
in typed fields. Markdown remains appropriate for summaries, narration, advice, and open-ended DM
notes. Markdown conventions alone must not carry required game semantics.

### 4.3 Stable identifiers over display names

Every addressable object in a campaign package must have an immutable, package-local string key.
References must use keys, never mutable names or titles. Display-name fallback is allowed only when
importing legacy format-version-1 files and must produce a warning.

### 4.4 Schema and runtime behavior are one contract

Input accepted by the published schema must be accepted by dry-run and import. Input rejected by
the schema must not be imported. DTOs, schemas, examples, catalogs, and documentation must be
verified against each other automatically.

### 4.5 Complete round-trip or explicit transience

Every campaign-owned field must be classified as persistent-exported or intentionally transient.
Export followed by import must preserve all persistent-exported meaning. No state may disappear
merely because it lives in settings JSON, an action log, or an asset flag.

### 4.6 Offline and local ownership remain hard requirements

Runtime features must work without internet access. User-created and imported content remains
local unless the user explicitly exports or shares it. No new frontend build chain or runtime CDN
is introduced.

### 4.7 Rules provenance is visible

Calculated rules must come from authoritative data. Approximations must never be labeled as exact
rules. Imported material records its source and locator so the DM and conversion agent can audit it.

### 4.8 Player safety is enforced at the server boundary

DM-only content must never be sent to player clients. New scene, map, asset, and presentation
features must extend the existing server-side projection rather than rely on CSS hiding.

## 5. Release priorities and gates

### P0 — Reliability and safety gate

These issues are release blockers because they affect existing advertised workflows:

- repair quick-note parameter rendering and add an end-to-end regression test;
- replace invalid command-palette destinations with a shared destination registry;
- remove path traversal and filename collision risk from campaign asset import;
- label or replace the approximate encounter difficulty calculation;
- add tests that fail on browser console errors and malformed network requests;
- ensure every time-critical action surfaces failure rather than silently swallowing it.

### P1 — Authoring and session-readiness gate

- make campaign validation schema-driven and semantically complete;
- make export/import a complete round-trip;
- introduce stable package-local keys and typed catalogs;
- replace “Run Session” with a unified session cockpit;
- separate implemented capabilities from roadmap documentation.

### P2 — Feature-depth gate

- structure adventure scenes for faithful conversion and branching play;
- complete character management and custom compendium content;
- deepen encounter preparation and published-map workflows;
- connect rewards, logs, calendar, and notes to the session lifecycle.

### P3 — New all-in-one subsystems

- structured world entities and relationships;
- quest objectives and campaign clocks;
- traps, hazards, travel, weather, and rollable tables;
- optional advanced player interaction, fog of war, and audio.

No P3 work should displace an unfinished P0 or P1 release gate.

## 6. Workstream A — Existing-path reliability

> **Implementation status (2026-07-15):** Package v2 foundation complete. Container safety, key registry,
> current-surface schema, typed catalog, v1 migration, preview/confirmation, atomic staged assets, and
> complete round-trip are implemented. Delivery items 3 and 4 (Package v2 foundation, Complete round-trip)
> are done. Session cockpit and all deeper features remain open.

### 6.1 Quick notes

The quick-note fragment must receive real campaign and target values in its Alpine component. The
rendered HTML must never contain unresolved `[[${...}]]` expressions.

Acceptance criteria:

- quick notes load on campaign, party-member, map, encounter, note, handout, statblock, and scene
  targets;
- add, delete, and promote succeed from each supported target;
- failed network operations show a non-destructive toast and retain unsaved input;
- browser tests assert request URLs contain valid UUIDs/keys and no template syntax;
- the smoke suite fails on HTTP request-parsing errors.

### 6.2 Command palette and link destinations

Destination construction must move out of `CommandPaletteService` string concatenation into a
shared destination registry used by search, wiki links, cards, and tests. Each content type declares
whether it opens a full page, side sheet, filtered library tab, or parent collection.

Acceptance criteria:

- every returned result has a destination that responds successfully;
- maps open `/play`, not an undefined `/battle` route;
- handouts use a valid gallery/presentation destination;
- party members open their sheet when present, otherwise the roster/edit surface;
- compendium entries without dedicated detail pages open the correct filtered tab or gain a detail
  route;
- route-contract tests enumerate every palette result type;
- results are ranked globally and capped globally, rather than capped independently per category.

### 6.3 Visible error handling

Interactive map, tracker, quick-note, dice, and presentation code must not use empty `catch` blocks
for user-initiated operations.

Acceptance criteria:

- failures display actionable messages without leaking internal details;
- optimistic update failures restore or retain local state;
- retryable actions offer retry;
- server logs carry a correlation identifier also shown in the user-facing error details;
- no failed action appears successful in the UI.

## 7. Workstream B — Campaign package version 2 and authoring SDK

> **Implementation status:** The campaign-contract-v1 checkpoint made the published v1 campaign
> and map schemas executable, closed them against unknown fields, unified dry-run and import
> validation, blocked unresolved v1 references before persistence, and added checked-in contract
> fixtures. ZIP packaging, stable version-2 keys, preview confirmation, migrations, and complete
> persistent-state round-trip (delivery items 3 and 4) are finished.

### 7.1 Package container

The canonical external format becomes a `.dmcampaign` ZIP package:

```text
campaign.dmcampaign
├── manifest.json
└── assets/
    ├── maps/
    ├── handouts/
    ├── portraits/
    └── audio/
```

Plain `.dmcampaign.json` remains supported for small, asset-free packages and legacy version 1.
Version 2 must not embed large binary assets as base64 in the manifest. Export chooses ZIP whenever
assets are present.

### 7.2 Manifest identity and references

Every package-owned entity has a required `key` matching `^[a-z0-9][a-z0-9._-]{0,99}$`.
Keys are unique within their entity type. References use `{ "type": "ENCOUNTER", "key":
"crypt-guardians" }` or a field whose type is unambiguous. Names are presentation only.

Required keyed types include:

- adventures, chapters, scenes, transitions, quests, and objectives;
- maps, tokens, encounters, combatants, and encounter waves;
- party members and character sheets;
- notes, handouts, timeline events, assignments, and ledger entries;
- custom statblocks, spells, rules, items, classes, subclasses, species, backgrounds, and feats.

SRD references use a typed catalog identifier containing content type, ruleset, and source key.

### 7.3 JSON Schema requirements

Schemas use JSON Schema draft 2020-12 and must:

- set `additionalProperties: false` on closed contract objects;
- declare every runtime-required field;
- provide explicit defaults only where the importer applies the same default;
- use `$defs` rather than duplicate the map-document contract inline;
- define nested action, condition, sheet, resource, map primitive, and provenance shapes completely;
- specify units for every coordinate, distance, duration, and currency;
- use conditional requirements for union types;
- constrain enums, string lengths, numeric ranges, MIME types, and asset paths;
- include descriptions and at least one valid example for non-obvious structures.

The Java DTO model and published schemas must have an automated compatibility test.

### 7.4 Validation pipeline

Dry-run and import share one validation pipeline:

```text
container safety
  → JSON parse
  → schema validation
  → uniqueness and reference validation
  → rules-catalog resolution
  → spatial and asset validation
  → compatibility migrations
  → preview model
  → atomic persistence
```

Validation returns structured problems:

```json
{
  "severity": "ERROR",
  "code": "UNRESOLVED_REFERENCE",
  "path": "/adventures/0/chapters/2/scenes/4/encounter",
  "message": "Encounter key 'crypt-guards' does not exist.",
  "suggestion": "Use 'crypt-guardians' or define the missing encounter."
}
```

Severities are `ERROR`, `WARNING`, and `INFO`. Import is blocked by errors. Warnings require an
explicit user confirmation in the preview UI. A success message is status metadata, not a fake
warning entry.

### 7.5 Semantic validation

The validator must check at least:

- key uniqueness and all cross-references;
- exact typed SRD key resolution;
- token pixel bounds against `gridWidth * cellSizePx` and `gridHeight * cellSizePx`;
- map primitive and scene-pin bounds;
- map document grid agreement with the containing map;
- encounter status, active encounter uniqueness, initiative indices, and token ownership;
- party-member/sheet consistency and class-level totals;
- spell, item, feat, species, background, and class reference types;
- handout and asset MIME type, size, extension, and existence;
- note/wiki targets and duplicate ambiguous titles;
- timeline dates against the configured calendar;
- attunement warnings and assignment-holder references;
- scene transitions and quest-objective targets.

Unknown optional catalog content may degrade only when the target field has a documented
plain-text fallback. The fallback and data loss must be shown in the preview. Nothing may degrade
silently to a server log line.

### 7.6 Atomic import and preview

Import must not persist anything until the complete package and all assets have passed validation
and the DM confirms the preview. Persistence and asset installation behave atomically: either the
campaign and all accepted assets exist, or neither does.

The preview summarizes:

- entity and asset counts;
- source/provenance coverage;
- unresolved or degraded references;
- package size and estimated installed size;
- unsupported features;
- conflicts, migrations, and compatibility warnings.

Import remains additive. It never overwrites an existing campaign unless a future explicit update
workflow is separately designed.

### 7.7 Asset safety

Imported filenames are metadata only. DMHelper generates internal UUID-based storage names and
never resolves an untrusted package path outside the extraction directory.

Required controls:

- reject absolute paths, `..`, symlinks, duplicate normalized paths, and archive bombs;
- cap file count, per-file expanded size, total expanded size, and compression ratio;
- validate file signatures as well as declared MIME types;
- permit only explicitly supported types;
- sanitize original display names;
- do not overwrite an existing stored file;
- clean temporary extraction data on both success and failure.

### 7.8 Complete round-trip contract

Version 2 export/import must preserve:

- campaign description, settings, leveling mode, current date, and current scene;
- party current/max HP, active state, complete sheets, resources, slots, and inventory;
- maps, documents, ordering, tokens, icons, HP, hidden/dead state, and asset references;
- encounters, combatants, active state, complete combat log, and optional dice history;
- notes, links, visibility, tags, and quick-note targets/timestamps;
- handout visibility, presented/given state, tags, content type, and asset;
- treasury assignments, attunement, ledger references, and currencies;
- calendar configuration and timeline references;
- adventures, chapters, structured scenes, transitions, status, and linked resources;
- custom compendium content and provenance.

Dice history and combat logs may be optionally excluded only through an explicit export option.
The export manifest records exclusions.

### 7.9 Typed catalog endpoint

The agent-facing catalog returns records rather than an unlabeled key list:

```json
{
  "type": "STATBLOCK",
  "sourceKey": "srd-2024_goblin",
  "name": "Goblin",
  "ruleset": "SRD_5_2",
  "source": "SRD",
  "aliases": []
}
```

Catalog snapshots ship with the repository and are also available from the running app. A snapshot
contains a version/hash so an agent can declare which catalog it used.

## 8. Workstream C — Unified session cockpit

`/campaigns/{campaignId}/session` becomes a real page rather than a redirect to the first map.

### 8.1 Layout and responsibilities

The cockpit contains:

- **story rail:** current scene, previous/next transitions, scene status, read-aloud blocks, checks,
  secrets, and linked NPCs;
- **table surface:** currently presented map or curtain, token controls, and map switcher;
- **encounter rail:** active encounter, initiative, HP/effects, planned encounters, and activation;
- **session plan:** ordered prepared beats and links;
- **quick access:** search, dice, rules, handouts, party passives, calendar date, and quick notes;
- **session lifecycle:** start/resume, pause, and end-session actions.

The layout may reuse the existing battle-map island and tracker. It must not duplicate their state
or introduce a second encounter implementation.

### 8.2 Session start and resume

Opening the cockpit selects content in this order:

1. active encounter and its map;
2. current scene and its map;
3. latest session plan's first linked scene/map;
4. campaign map chooser;
5. a useful empty state when no map exists.

The app never chooses the first map merely because it sorts first.

### 8.3 End-session workflow

Ending a session offers a generated draft containing:

- real-world and in-game date;
- attendance;
- scenes visited/completed;
- encounter outcomes and casualties;
- loot and ledger changes;
- quest/objective changes;
- unresolved quick notes;
- free-form recap and next-session hooks.

The DM reviews the draft before saving it as a `SESSION_LOG` note. No generative network service is
required; the baseline draft is deterministic from local state.

### 8.4 Acceptance criteria

- the DM can run a prepared scene and encounter without leaving the cockpit;
- browsing private material does not change the player presentation;
- current scene, active encounter, presented map/handout, and session plan remain independent but
  visibly coordinated;
- refresh/reconnect restores the same session state;
- cockpit actions work with no player device connected;
- keyboard operation covers next turn, scene navigation, quick note, search, dice, and presentation;
- all DM-only data remains absent from player payloads.

## 9. Workstream D — Structured adventures and source translation

### 9.1 Adventure graph

The display hierarchy remains Adventure → Chapter → Scene, but navigation is no longer assumed to
be linear. Scenes may declare typed transitions:

```text
Scene ──* Transition(targetSceneKey, label, condition?, dmNote?)
```

Previous/next remains available as editorial order. Runtime choices use transitions. The DM is
never blocked from jumping manually.

### 9.2 Structured scene model

A scene supports:

- title, key, summary, source locator, status, and tags;
- ordered read-aloud blocks;
- ordered DM exposition/advice blocks;
- entrances and exits/transitions;
- discoverable secrets;
- checks with ability/skill, DC, visibility, success, failure, and partial outcomes;
- creatures/NPCs with quantity, disposition, and placement hints;
- encounter and map links, including map regions/pins;
- traps, hazards, environmental effects, and puzzles;
- treasure/rewards;
- developments and consequences;
- scaling guidance;
- linked handouts, rules, notes, quests, and timeline events.

All sections are optional. A scene may still consist only of Markdown body content.

### 9.3 Conversion fidelity

The format must represent a published campaign without forcing an agent to:

- flatten read-aloud, DM secrets, checks, and outcomes into indistinguishable prose;
- invent map coordinates when the source supplies no usable map;
- duplicate a creature statblock for every appearance;
- encode branching choices as prose-only notes;
- discard source page references;
- invent missing rules values.

When source material is unclear, the converter records an unresolved annotation with confidence and
source locator rather than guessing.

### 9.4 Quest model

Quests become structured campaign entities while existing `QUEST` notes remain valid descriptive
documents.

A quest has key, title, status, giver, summary, ordered/branching objectives, rewards, related
scenes/NPCs/locations, prerequisites, and outcome notes. Objective state changes appear in the
session log and may trigger timeline or campaign-clock updates only after DM confirmation.

## 10. Workstream E — Compendium and custom rules content

### 10.1 Unified content ownership

Every compendium type supports:

- bundled read-only SRD content;
- reusable user-global custom content;
- campaign-scoped imported/custom content.

This applies to statblocks, spells, mundane and magic items, classes, subclasses, species,
backgrounds, feats, rules, conditions, traps, hazards, diseases, curses, vehicles, and optional
rollable tables.

### 10.2 Provenance

Every non-original imported entry stores:

- source title and optional edition/version;
- page, section, or other locator;
- content owner/license classification;
- import timestamp and converter identity/version;
- source hash when available;
- extraction confidence and unresolved annotations.

Provenance is visible in the DM UI and exportable. Player presentation excludes provenance unless
explicitly included in a handout.

### 10.3 Reference behavior

Campaign content may reference bundled catalog entries without copying them. Non-SRD imported
content remains campaign-scoped unless the DM explicitly promotes it to their reusable local
library. Promotion never changes source attribution.

### 10.4 Acceptance criteria

- each content type has list, search, view, create/clone, edit, scope, and provenance behavior;
- campaign export includes every campaign-scoped custom dependency;
- deleting or promoting content follows documented reference rules;
- sheets, encounters, treasury, notes, scenes, and search share the same content identifiers;
- unsupported rules content can be stored as a typed generic rule entry without inventing fields.

## 11. Workstream F — Complete character and party management

### 11.1 Character creation and advancement

The sheet workflow must support:

- class, subclass, multiclass levels, species, background, feats, and choices;
- ability scores and improvements;
- saving throw, skill, tool, armor, weapon, and language proficiencies;
- expertise and mastery choices;
- hit points and hit-die history;
- class/species/background features and limited-use resources;
- spell lists, known/prepared choices, slots, pact-style resources, and rituals;
- configurable overrides with a visible reason/source;
- XP and milestone advancement.

The engine derives only what its installed rules data supports. Missing or custom mechanics remain
explicit, editable resources/features rather than silently guessed calculations.

### 11.2 At-table sheet

The main sheet must expose usable actions, not only derived totals:

- attacks with roll expression, damage, range, properties, and ammunition;
- actions, bonus actions, reactions, and relevant feature text;
- saves, skills, initiative, movement, senses, and passive scores;
- current/max/temp HP, death saves, exhaustion, conditions, concentration, and inspiration;
- inventory, currency, equipped/attuned items, consumables, weight, and optional encumbrance;
- spells with searchable full text and prepared/slot/resource controls;
- rest preview showing exactly what will recover.

Digital rolling remains optional. Every rollable value remains readable for physical dice.

### 11.3 Party operations

- attendance and active/inactive state are session-specific as well as character-level;
- rest, XP, milestone, loot, and condition operations may target one or multiple members;
- encounter and map projections stay synchronized with sheet HP/state according to an explicit
  ownership rule;
- party import/export preserves current state, not only maximum/derived values.

## 12. Workstream G — Encounter preparation and resolution

### 12.1 Preparation

Encounter building adds:

- library creature selection with quantity and grouping;
- party-based scaling variants;
- waves, reinforcements, triggers, and reserves;
- starting map positions or placement regions;
- tactics, morale, surrender/flee conditions, and environment notes;
- traps/hazards and non-creature initiative entries;
- rewards, loot, XP, and quest consequences;
- source locator and scene linkage.

### 12.2 Runtime

The existing tracker remains the core. Improvements must preserve:

- optional physical initiative/roll entry;
- group initiative with per-creature HP;
- concentration and effect durations;
- legendary/lair/recharge behavior;
- action log and undo;
- server-safe player projection.

New runtime requirements:

- visible failure handling for every mutation;
- combat log export/import;
- clear undo scope and non-undoable boundary markers;
- waves and trigger prompts;
- deterministic encounter summary at completion;
- DM-confirmed reward and quest updates.

### 12.3 Difficulty calculation

The calculator must use authoritative rules data for the selected ruleset. Until that data exists,
the feature must be labeled “estimate” and show its source/assumptions. A 2014 table plus heuristic
must not be presented as an exact 2024 calculation.

## 13. Workstream H — Map and spatial workflow

### 13.1 Published map import

The map editor adds an image-first workflow:

- crop and rotate source images;
- set two grid reference points and calibrate cell size/offset;
- align, scale, and lock the source layer;
- optionally hide or mask printed labels and secrets;
- create separate DM and player variants without duplicating token state;
- define named regions for scene and encounter references.

An imported map may remain image-only. The user or agent is not forced to repaint it as terrain.

### 13.2 Runtime semantics

Maps may optionally model doors, secret doors, difficult terrain, hazards, cover, elevation,
lighting, walls, and reveal regions. These semantics remain advisory unless a separately approved
automation feature consumes them.

### 13.3 Fog and reveal

Fog of war is a post-readiness feature, but version-2 map documents must not preclude reveal regions
or a persistent revealed/hidden state. Player-safe projection remains the enforcement boundary.

### 13.4 Spatial contract

- token and pin coordinates are pixels from the top-left origin;
- token sizes are cell counts;
- semantic primitive coordinates are grid columns/rows;
- all schema descriptions and validators use the same units;
- grid and freeform movement never reinterpret stored coordinates.

## 14. Workstream I — Notes, search, and structured world knowledge

### 14.1 Notes and wiki

Notes remain the flexible authoring surface. Improvements include:

- immutable note keys alongside titles;
- key-based links with title-oriented authoring/autocomplete;
- explicit ambiguous-link resolution;
- automatic link repair after renaming;
- saved filters, pinning, and session favorites;
- note templates with typed front matter generated by the UI;
- conversion annotations that can be resolved or dismissed.

### 14.2 Search

Global search indexes titles, aliases, structured fields, and relevant bodies across campaign and
compendium data. Results are permission-filtered, ranked, grouped, and globally capped.

Search acceptance criteria:

- current campaign results rank above global library results when relevance is equal;
- exact title/key and prefix matches rank above body matches;
- every result destination is contract-tested;
- search remains responsive with a feature-complete imported campaign;
- the DM can restrict search by type without leaving the palette.

### 14.3 Structured world entities

After notes and references are stable, add first-class optional entities for NPCs, locations,
factions, settlements, and relationships. They complement rather than replace linked notes.

Minimum useful structures:

- NPC: role, disposition, faction, location, appearance, voice, motivation, secret, statblock,
  inventory, status;
- Location: parent location, map/region, occupants, services, encounters, secrets, travel links;
- Faction: goals, resources, allies/enemies, reputation, clocks, members;
- Relationship: typed edge, direction, public/secret knowledge, current status.

## 15. Workstream J — Treasure, calendar, and campaign logistics

### 15.1 Treasury and ledger

- encounter/quest rewards create reviewable transaction drafts;
- items distinguish equipped, carried, stashed, consumed, and lost states;
- stackable consumables and ammunition have direct decrement controls;
- currency conversion and party/individual ownership are explicit;
- optional weight and encumbrance derive from inventory;
- shops and service inventories can be linked to locations and dates;
- every automated change is represented in the append-only ledger.

### 15.2 Calendar and timeline

- campaign package preserves full calendar configuration and current date;
- calendars support eras and optional leap/intercalary rules without assuming Gregorian months;
- scene, quest, faction-clock, travel, and session events may create reviewable timeline drafts;
- date validation uses the selected campaign calendar;
- timeline links use stable keys, not titles.

### 15.3 Travel and exploration

As a P3 subsystem, travel may add routes, pace, watches, weather, navigation checks, supplies, and
random encounter tables. It must integrate with the calendar, party resources, maps, scenes, and
session log rather than become another isolated calculator.

## 16. Workstream K — Player presentation

The read-only player view remains a valid baseline. Improve it through:

- explicit curtain, map, handout, initiative, and optional read-aloud presentation types;
- presentation preview before sending;
- persistent “given to players” handout library;
- reconnect/resume without leaking previously hidden content;
- DM-visible connected-device count and last-update status;
- accessible scaling for TV, tablet, and phone displays.

Future player interaction, identity, token movement, rolling, and character editing require a
separate permissions design. They are not prerequisites for the DM-only all-in-one goal.

## 17. Workstream L — Architecture and maintainability

### 17.1 Import/export decomposition

The current campaign service coordinates too many repositories and responsibilities. Replace the
monolithic flow with module contracts:

- `CampaignPackageReader` — container and manifest reading;
- `CampaignSchemaValidator` — JSON Schema validation;
- `CampaignSemanticValidator` — cross-reference and domain checks;
- `CatalogResolver` — typed global/SRD reference resolution;
- `AssetInstaller` — safe staged asset handling;
- per-module `CampaignSectionImporter` and `CampaignSectionExporter` implementations;
- `CampaignImportCoordinator` — ordering and atomic transaction;
- `CampaignExportCoordinator` — dependency closure and manifest creation;
- `FormatMigrationRegistry` — deterministic version upgrades;
- `CampaignPreviewBuilder` — user/agent-readable import summary.

Each module owns its DTO mapping and section validation. The coordinator owns order and transaction
boundaries, not entity details.

### 17.2 Typed internal JSON

Existing CLOB/JSON fields may remain storage implementation details, but application code and the
external contract must use typed records for campaign settings, class levels, proficiencies,
statblock actions, conditions, map documents, resources, and overrides. Raw `Map<String,Object>`
must not cross module APIs where a stable shape is known.

### 17.3 Destination and reference registries

Search, wiki, quick notes, scenes, and import/export share a content-type registry defining:

- stable type identifier;
- key format;
- destination behavior;
- display label/icon;
- player visibility eligibility;
- supported reference scopes;
- import/export adapter.

This removes independent switch statements and route strings that drift over time.

### 17.4 Schema migrations

All persistent schema changes use reviewed Flyway migrations. Hibernate validates the schema in
production rather than evolving it implicitly. Migrations include upgrade tests from supported
released versions and preserve user data by default.

## 18. Agent conversion playbook

The documented conversion flow is:

1. **Inventory the source:** title, edition, ruleset, pages, maps, appendices, and asset rights.
2. **Extract without invention:** capture text and tables with page locators and confidence.
3. **Build an intermediate source model:** headings, boxed text, keyed locations, creatures,
   checks, treasure, transitions, and assets before mapping to DMHelper.
4. **Resolve catalog content:** use the exact typed catalog snapshot and record its hash.
5. **Create custom content:** include source-scoped definitions for material not in the catalog.
6. **Build the campaign graph:** stable keys first, then references.
7. **Attach assets:** preserve source names as metadata but use safe package paths.
8. **Validate locally:** schema, references, spatial checks, and capability checks.
9. **Run app dry-run:** consume structured errors and iterate until no errors remain.
10. **Review warnings:** do not suppress ambiguity or unsupported content.
11. **Import preview:** verify counts, provenance, degraded fields, and sample scenes.
12. **Post-import smoke:** open the cockpit, follow representative branches, activate an
    encounter, present a handout, and export/re-import the result.

The agent must never invent a DC, stat, map coordinate, source key, or missing rule merely to make
validation pass. It records an unresolved annotation or uses an explicitly documented generic
fallback.

## 19. Documentation deliverables

Documentation must be split by audience and generated from contracts where practical.

### 19.1 Product and status

- product vision;
- capability matrix with `SUPPORTED`, `PARTIAL`, `UNSUPPORTED`, and `EXPERIMENTAL` states;
- release notes and format compatibility matrix;
- known limitations.

### 19.2 DM manual

- first-run and backup/restore;
- campaign preparation;
- session cockpit workflow;
- maps, encounters, party, sheets, notes, handouts, and player view;
- import preview and warning interpretation;
- offline operation and troubleshooting.

### 19.3 Campaign authoring reference

- package/container rules;
- complete schema reference;
- stable keys and reference resolution;
- map units and coordinate examples;
- asset rules;
- provenance rules;
- validation error catalog;
- migration/versioning rules;
- minimal, feature-complete, and published-adventure-shaped examples.

### 19.4 Architecture reference

- module boundaries and dependencies;
- domain model and ownership;
- campaign import/export data flow;
- session/player presentation state flow;
- security boundaries;
- database and asset lifecycle;
- testing strategy.

### 19.5 Agent guide

- conversion playbook from section 18;
- machine-readable capability manifest;
- typed catalog snapshot;
- prompt-independent mapping rules;
- ambiguity and non-invention policy;
- dry-run repair examples;
- deterministic verification checklist.

Documentation examples are executable test fixtures. If an example ceases to validate or import,
the build fails.

## 20. Quality requirements

### 20.1 Correctness

- no schema-valid package fails because of DTO drift;
- no invalid reference is silently discarded;
- no rules result is presented as authoritative without a recorded source;
- round-trip semantic comparison passes for every persistent-exported field;
- current session state survives refresh, restart, export, and import as documented.

### 20.2 Performance

Performance budgets on reference hardware:

- cockpit initial meaningful render: under 2 seconds for a large local campaign;
- palette first results: under 150 ms after debounce;
- scene switch without map change: under 250 ms;
- map/token mutation acknowledgement: under 200 ms locally;
- dry-run reports incremental progress for operations longer than 2 seconds;
- a feature-complete campaign package may exceed the old 50 MB upload limit through a streaming
  package-import path rather than raising the global request limit without bounds.

### 20.3 Accessibility

- all core prep and session actions are keyboard-operable;
- focus is visible and restored after dialogs/HTMX swaps;
- color is not the only carrier of encounter, health, visibility, or validation status;
- player view supports large-display contrast and scalable text;
- canvas actions have equivalent named controls where practical.

### 20.4 Security and privacy

- DM route PIN checks cover every new DM API and asset endpoint;
- imported packages are treated as untrusted input;
- player clients never receive hidden scene, token, note, encounter, or asset data;
- error responses do not expose local paths, stack traces, PINs, or source content;
- generated exports disclose what content and provenance they contain before saving.

### 20.5 Data safety

- startup backup behavior remains intact and is covered by isolated tests;
- imports stage before commit and leave no partial campaigns/assets;
- destructive operations explain dependency effects and require confirmation;
- format migrations are deterministic and never modify the original import file;
- backup restore and export/import recovery are documented and tested.

## 21. Verification strategy

### 21.1 Contract tests

- schema examples validate;
- Java DTO/schema compatibility;
- typed catalog uniqueness and resolvability;
- destination registry routes all return success;
- content-type registry covers every supported entity;
- v1 fixtures migrate to v2 deterministically.

### 21.2 Round-trip tests

Maintain three flagship fixtures:

1. minimal asset-free campaign;
2. feature-complete synthetic campaign exercising every persistent field;
3. published-adventure-shaped campaign with branches, maps, handouts, custom rules content, and
   provenance.

For each fixture: schema validate → dry-run → import → export → new import → semantic deep compare.

### 21.3 Browser tests

- browser console errors, page errors, malformed requests, and unexpected 4xx/5xx responses fail
  the test;
- create and promote quick notes;
- exercise every palette result type;
- resume the cockpit from current scene and active encounter;
- activate/end an encounter and save its session-log draft;
- send curtain, map, and handout presentations to a player view;
- verify DM-only data is absent from player network payloads.

### 21.4 Security tests

- path traversal, absolute paths, symlinks, duplicate paths, archive bombs, MIME spoofing, and
  oversized packages;
- PIN bypass attempts for new routes;
- player asset access before and after presentation;
- malformed references and hostile Markdown/HTML;
- atomic rollback on failures at each import stage.

### 21.5 Manual acceptance session

Before an all-in-one readiness release, run a representative four-hour session from a converted
synthetic adventure without opening another campaign reference tool. Record every forced context
switch, missing datum, broken link, and manual duplication. All blocking observations become release
issues; convenience observations are triaged separately.

## 22. Delivery decomposition

This program must not be implemented as one branch or one plan.

Recommended sequence:

1. **P0 runtime reliability:** quick notes, destinations, errors, asset security, difficulty label.
2. **Campaign contract v1 repair:** make current schema/import/export internally consistent before
   introducing version 2.
3. **Package v2 foundation:** container, keys, schemas, validator, preview, assets, migration.
4. **Complete round-trip:** module exporters/importers and flagship fixtures.
5. **Session cockpit:** coordinate existing map, tracker, scene, plan, and presentation features.
6. **Structured adventure/quest model:** conversion fidelity and branching.
7. **Custom compendium expansion:** all rules content types and provenance.
8. **Character-sheet completion:** creation, choices, actions, inventory, spells, and rest state.
9. **Encounter and map depth:** prep waves/rewards and published-map workflow.
10. **Documentation/agent SDK release:** generated references, catalogs, fixtures, and playbook.
11. **P3 expansion:** world graph, travel, tables, clocks, fog, audio, optional players.

| # | Delivery Item | Status |
|---|--------------|--------|
| 1 | P0 runtime reliability | `IMPLEMENTED` |
| 2 | Campaign contract v1 repair | `IMPLEMENTED` |
| 3 | Package v2 foundation | `IMPLEMENTED` |
| 4 | Complete round-trip | `IMPLEMENTED` |
| 5 | Session cockpit | `IMPLEMENTED` |
| 6 | Structured adventure/quest model | `PLANNED` |
| 7 | Custom compendium expansion | `PLANNED` |
| 8 | Character-sheet completion | `PLANNED` |
| 9 | Encounter and map depth | `PLANNED` |
| 10 | Documentation/agent SDK release | `PLANNED` |
| 11 | P3 expansion | `PLANNED` |

Each item receives a separate design, implementation plan, migration analysis, and verification
report. Items 1–5 form the minimum coherent readiness program. Items 6–11 are post-readiness
but tracked in the same delivery structure.

## 23. Definition of all-in-one readiness

DMHelper may claim all-in-one DM readiness only when all conditions below are met:

- P0 and P1 release gates are complete;
- the session cockpit is the normal runtime entry point;
- a feature-complete campaign round-trips without unreported semantic loss;
- a source-conversion agent can author against versioned schemas, catalogs, examples, and structured
  validation errors;
- arbitrary non-SRD campaign dependencies can be represented as local custom content with
  provenance;
- a representative campaign can be prepared, run, logged, exported, restored, and resumed without
  consulting external campaign references;
- the capability matrix honestly identifies remaining optional/unsupported subsystems;
- player-safe projection and import-package security tests pass;
- the full automated suite and the manual acceptance session pass their release criteria.

“All-in-one” does not mean every optional tabletop preference exists. It means the app can faithfully
hold and operate all information required by the selected campaign and rules content, and it makes
unsupported information explicit instead of losing or inventing it.

## 24. Explicit non-goals of the readiness program

- bundling proprietary D&D books or campaigns with the application;
- automatic redistribution of imported copyrighted content;
- cloud accounts, multi-tenant hosting, or internet-required services;
- autonomous DM decisions or automatic execution of story transitions;
- mandatory digital dice;
- replacing human review of ambiguous source conversion;
- completing every P3 feature before the readiness claim;
- rewriting the existing application in another stack.

## 25. Decisions captured by this master specification

1. Preserve `SPEC.md`; add this as the gap-closing master specification.
2. Repair existing reliability before expanding breadth.
3. Make campaign contracts and validation authoritative before agent documentation.
4. Adopt a version-2 ZIP package for asset-bearing campaigns while retaining legacy JSON support.
5. Require stable typed keys and prohibit mutable-name references in version 2.
6. Build the session cockpit from existing modules rather than a parallel runtime system.
7. Keep Markdown, but add structured scene, quest, and rules data where the app must act.
8. Support custom content and provenance for every campaign dependency, not only statblocks.
9. Decompose import/export into module-owned adapters and a small coordinator.
10. Treat documentation examples, schemas, catalogs, routes, and fixtures as executable contracts.
