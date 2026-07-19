# DMHelper Table Fidelity and Atmosphere Expansion — Design Specification

**Date:** 2026-07-17
**Status:** Approved design, scope amended 2026-07-19
**Parent specification:** `2026-07-15-all-in-one-dm-readiness-design.md` (master), delivery item 11 (P3 expansion)
**Product premise:** Close the remaining gaps that force a DM running a published or homebrew
campaign to reach for a book or a music app: rollable tables, reusable trap and hazard content,
and in-app atmospheric music.

## 1. Purpose and relationship to the master specification

The master specification defined all-in-one DM readiness and delivered items 1–10. This document
defines the remaining required DM-only P3 feature slice from item 11. Interactive player accounts
and player-controlled gameplay are explicit non-goals.

This slice contains three subsystems that a DM running a published module today may otherwise handle
outside the app:

- **Workstream M — Rollable tables:** random encounters, loot, rumors, weather, and other
  die-driven tables as first-class content instead of flattened prose.
- **Workstream N — Traps and hazards:** reusable, structured compendium entries instead of
  per-scene prose sections.
- **Workstream P — Atmosphere and music:** streaming-based background music with scene-linked
  dynamic switching.

The 2026-07-19 scope amendment removed progressive fog of war and structured travel/hexcrawl
automation from the readiness program. Existing DM/player map projection, maps, named regions,
world-location adjacency, notes, calendars, and rollable tables remain supported; specialized fog
and travel automation may be proposed later as optional features.

All master-specification principles (§4), quality requirements (§20), and verification strategy
(§21) apply unless explicitly amended here. This document introduces exactly one principle
amendment (§3.1, network dependency for music). Each workstream must receive its own
implementation plan before code changes begin; this document is authoritative for contracts,
priorities, and acceptance criteria shared between them.

## 2. Current capability baseline

| Area | Current strength | Primary limitation | Readiness |
|---|---|---|---|
| Rollable tables | Dice engine, roll log, and treasury reward drafts exist | No table entity anywhere; imported tables flatten to prose; DM rolls against text | Not present |
| Traps/hazards | Typed prose scene sections (`TRAP`, `HAZARD`); non-creature initiative entries | No reusable entry, no structured DCs/damage/effects, no compendium scope or provenance | Prose only |
| Music | None | No audio subsystem at all | Not present |

## 3. Product principles applied

### 3.1 Amendment — network dependency for the music subsystem

Master §4.6 requires runtime features to work without internet access. The music subsystem is
**the first and only approved exception**: it is streaming-first by explicit product decision and
requires internet access and may require a provider account.

The exception is bounded by these rules:

- no other feature may depend on the music subsystem or its connectivity;
- loss of connectivity or provider failure degrades only the audio widget, never the session:
  scene navigation, encounters, and presentation continue unaffected;
- the app never stores, caches, re-hosts, or exports audio content — only provider references
  and display metadata;
- provider credentials are stored locally, are never exported in a campaign package, and are
  removable with a single visible action;
- when the subsystem is unconfigured or offline, its UI states that plainly instead of failing
  silently or blocking.

The exception includes an official provider-hosted playback client or script when the selected
provider technically requires it. It does not authorize unrelated runtime CDN assets or a new
frontend build chain.

### 3.2 Unchanged principles with specific consequences here

- **§4.2 Structured where the app acts:** table entries, trap DCs, and audio cue
  assignments are typed fields, never Markdown conventions.
- **§4.3 Stable identifiers:** every new entity type (tables, table entries where addressable,
  traps, hazards, and audio cues) has a package-local `key` matching
  `^[a-z0-9][a-z0-9._-]{0,99}$`; references use keys.
- **§4.5 Complete round-trip:** every new persistent field is classified persistent-exported or
  intentionally transient in the same commit that introduces it.
- **§4.7 Rules provenance:** imported tables, traps, and hazards carry the standard
  `ContentProvenance` record.
- **§4.8 Player safety at the server boundary:** audio is DM-only and absent from player payloads
  entirely. Existing map and presentation projections retain their standing server-side safety
  requirements without adding progressive-fog semantics.
- **Master §24 non-goal "no autonomous DM decisions":** automatic music switching is a
  presentation behavior, not a story decision, and is therefore permitted; it remains
  DM-configurable per campaign (§6.4).

## 4. Workstream M — Rollable tables

### 4.1 Content model

A rollable table is a compendium entity following the unified ownership model (master §10.1):
bundled read-only where the SRD provides tables, reusable user-global, and campaign-scoped.

Required fields:

- `key`, `name`, optional description (Markdown allowed for description only);
- `rollExpression` — a dice expression valid in the existing dice engine (e.g. `1d100`, `2d6`);
- ordered entries, each with:
  - `rangeStart`/`rangeEnd` (inclusive) against the roll result, or `weight` for weighted tables
    (one addressing mode per table, declared explicitly);
  - result payload: rich text **and/or** typed references (statblock, item, magic item, note,
    another table, encounter, handout) using standard typed references;
  - optional quantity expression (e.g. `2d4` goblins) evaluated with the same dice engine;
- optional tags and category (ENCOUNTER, TREASURE, WEATHER, RUMOR, EVENT, GENERIC);
- scope and provenance per the existing compendium contract.

Nested tables: an entry may reference another table (“roll again on…”). Nesting resolves
recursively at roll time with a hard depth limit (default 5) and cycle detection at validation
time. A table must not reference itself directly or transitively.

### 4.2 Rolling behavior

- rolling uses the existing dice engine and appends to the existing roll log; a table roll
  records table key, raw roll, matched entry, and resolved nested rolls as one grouped log item;
- rolled results are display-only by default; when an entry references content with a
  consequence (loot → treasury, creatures → encounter), the app offers a **reviewable draft**
  (reward draft, encounter prefill) that the DM explicitly confirms — never automatic
  application, consistent with master §15.1 and §12.2;
- physical dice remain first-class: the DM may type a manually rolled number instead of digital
  rolling and get the same resolution;
- a “roll N times” control supports generating multiple results with duplicate handling
  (allow duplicates / reroll duplicates, chosen at roll time).

### 4.3 Integration points

- **Scenes:** scenes may link tables through the existing scene-link mechanism with a role
  (e.g. RANDOM_ENCOUNTERS); the cockpit story rail shows linked tables with a one-click roll.
- **Locations:** world locations may link tables the same way (regional encounter tables).
- **Encounters:** an ENCOUNTER-category entry can prefill a new prepared encounter (creatures,
  quantities) as a draft.
- **Treasury:** a TREASURE-category result can open a pre-filled reward draft.
- **Command palette and search:** tables are indexed, ranked, and routed through the shared
  destination registry and content-type registry (master §17.3).
- **Quick access:** the cockpit quick-access strip offers “roll on a linked table” within two
  deliberate actions (master §4.1).

### 4.4 Package, schema, and validation

- new manifest section `rollableTables` in campaign format v2 with full JSON Schema
  (`$defs`, closed objects, examples) and DTO compatibility tests;
- semantic validation:
  - range tables: ranges are contiguous, non-overlapping, and exactly cover the min–max of the
    declared roll expression (gaps and overlaps are errors);
  - weighted tables: weights are positive integers;
  - all entry references resolve (typed catalog or package keys);
  - nested reference cycle detection and depth limit;
  - roll and quantity expressions parse in the dice engine;
- export includes campaign-scoped tables and the campaign’s dependency closure; user-global
  tables referenced by a campaign are copied into the package as campaign-scoped entries with
  preserved provenance (consistent with master §10.3).

### 4.5 Acceptance criteria

- a DM can create, clone, edit, tag, search, and roll on a table without leaving the app;
- an imported published-adventure table (ranges, nested rolls, quantities) round-trips without
  flattening to prose and without semantic loss;
- rolling a loot table can produce a treasury reward draft that the DM confirms or discards;
- rolling an encounter table can prefill a prepared encounter draft;
- invalid tables (gap, overlap, cycle, unresolvable reference, bad expression) are rejected at
  dry-run with structured problems including the entry path;
- table rolls appear in the roll log and, during a session, in the session-log draft;
- the feature-complete flagship fixture gains at least one table of each category and passes the
  round-trip deep compare;
- palette/search route contract tests cover the table destination.

## 5. Workstream N — Traps and hazards compendium

### 5.1 Positioning: structured but advisory

Traps and hazards become reusable structured content. The app **displays** structured mechanics
and pre-fills the dice roller; it does **not** auto-roll or auto-apply damage or conditions.
This matches the master specification’s stance that map/scene semantics remain advisory
(master §13.2) until a separately approved automation feature consumes them.

### 5.2 Content model

Two entity types sharing one editor pattern, following the unified compendium ownership and
provenance model:

**Trap** required/optional fields:

- `key`, `name`, description (Markdown), severity band (SETBACK, DANGEROUS, DEADLY) and
  intended level range;
- trigger description (typed text) and optional trigger area hint;
- detection: passive threshold and/or active check (ability/skill + DC), using the same check
  structure as scene checks;
- disarm/avoid: one or more methods, each with ability/skill or tool, DC, and failure
  consequence text;
- effect: attack bonus **or** save (ability + DC), damage expression + type(s), conditions
  applied (typed condition references), additional effect text;
- reset behavior (NONE, MANUAL, AUTOMATIC + timing text);
- optional linked statblock (for creature-like traps) and countermeasure notes.

**Hazard** required/optional fields:

- `key`, `name`, description, severity/level band;
- exposure model: on-enter, start-of-turn, per-round, or continuous (typed enum + text);
- area/extent hint;
- save/check structure, damage expression, conditions, and escalation text;
- ending/removal conditions.

All numeric mechanics fields are optional individually: a prose-only trap remains valid
(master §9.2 “all sections are optional” applies analogously), but whatever the app is expected
to display as mechanics must be typed.

### 5.3 Scene and encounter integration

- the existing `TRAP` and `HAZARD` scene sections remain valid prose and are unchanged on
  migration; they gain an **optional typed reference** to a trap/hazard entry; when present, the
  cockpit story rail renders the structured card inline;
- encounters: the existing non-creature initiative entry can reference a trap/hazard entry; the
  tracker shows its card (trigger, DCs, effect) on its turn without automating resolution;
- maps: a map pin may reference a trap/hazard entry as a DM-only marker; player projection
  never includes it;
- dice roller: every roll expression on a trap/hazard card (check DC advantage rolls, attack,
  damage) is clickable and pre-fills the dice roller; results are never auto-applied;
- treasury/rewards: disarming or salvaging text may link items, but no automatic transactions.

### 5.4 Package, schema, and validation

- new manifest sections `traps` and `hazards` with schemas, DTO compatibility tests, and
  provenance;
- semantic validation: damage expressions parse; condition references resolve; DCs within
  configured bounds; severity/level bands from the declared enum; scene-section and encounter
  references resolve;
- migration: existing campaigns and v2 packages without these sections import unchanged; a
  scene section with only prose stays prose.

### 5.5 Acceptance criteria

- each type has list, search, view, create/clone, edit, scope, and provenance behavior
  (master §10.4 applies verbatim);
- a trap authored once can be referenced from multiple scenes and an encounter without
  duplication;
- the cockpit shows a referenced trap’s full mechanics within two deliberate actions from the
  story rail;
- a trap on its initiative turn shows its card; resolution stays manual and the action log
  records what the DM applied through existing tracker controls;
- prose-only trap sections continue to work and round-trip byte-identically;
- imported entries display provenance; the feature-complete fixture gains at least one fully
  structured trap and hazard and passes round-trip deep compare;
- search/palette destinations are contract-tested.

## 6. Workstream P — Atmosphere and music

### 6.1 Architecture: provider abstraction, streaming-first

Music is streaming-first per explicit product decision (§3.1). The design isolates all
provider-specific code behind a single internal SPI so the product contract does not name a
vendor:

- `AudioProviderClient` — authenticate, search playlists/tracks, read display metadata, start /
  stop / transition playback on the DM device, report player state;
- the reference implementation targets a mainstream streaming service with a documented
  playback-control API; additional providers are additive;
- provider capabilities are declared (e.g. `supportsCrossfade`, `supportsVolume`,
  `supportsQueue`); the UI adapts to declared capabilities instead of assuming them;
- playback happens **on the DM device** (the machine running the DM browser/its linked provider
  app); the player view remains silent and receives no audio-related data;
- providers that require authentication use their standard local OAuth flow; tokens are stored
  in local app data, never in the database export, never in campaign packages, and are clearable
  from settings. An authless public provider path may declare `AudioAuthMode.NONE` and must not
  invent credentials or request unrelated API scopes;

### 6.2 Content model

Audio content is referential metadata only:

- **AudioCue**: `key`, `name`, provider reference (provider id + provider URI/ID), cached
  display metadata (title, artist/owner, artwork URL, duration), category (AMBIENT, EXPLORATION,
  TENSION, COMBAT, TRIUMPH, SORROW, CUSTOM), optional volume hint and transition preference
  (CROSSFADE, CUT), notes;
- a cue references a playlist or a single track; playlists are preferred for length;
- **Assignments:** scenes, encounters, world locations, and the campaign itself may reference
  cues by key with a role:
  - campaign: `defaultCue`;
  - scene: `sceneCue`;
  - encounter: `combatCue` and optional `victoryCue`;
  - location: `locationCue`;
- cached metadata is display-only and refreshable; a cue whose provider content was deleted
  remains valid data and surfaces a visible “unavailable at provider” state.

### 6.3 Runtime behavior and dynamic switching

Cue selection follows a priority stack, resolved top-down, at most one active cue:

1. manual DM override (always wins until cleared);
2. active encounter `combatCue`;
3. current scene `sceneCue`;
4. current location `locationCue` (when the scene declares a location);
5. campaign `defaultCue`;
6. silence.

Switching rules:

- scene change, encounter activation, and encounter end re-evaluate the stack and switch
  automatically (product decision); transition uses crossfade when the provider supports it,
  otherwise cut;
- a per-campaign setting downgrades automatic switching to a **confirm prompt** in the cockpit
  (“Scene changed — switch to ‘Sunless Citadel Depths’?”), and a per-session toggle mutes the
  subsystem entirely;
- ending an encounter returns to the underlying stack result (optionally via `victoryCue` for a
  DM-configured duration);
- browsing prep screens never changes playback; only cockpit-driven scene/encounter transitions
  do (consistent with master §8.4: browsing must not change the table presentation).

### 6.4 Cockpit integration

- a quick-access audio widget shows now playing (title, artist, artwork), play/pause, skip,
  volume (where supported), the active cue source (“from scene: The Ossuary”), manual override
  picker, and mute;
- every control is reachable within two deliberate actions (master §4.1) and keyboard-operable;
- provider errors (expired auth, rate limit, no active device, network loss) surface as visible,
  actionable messages with retry per master §6.3 — e.g. “No active playback device. Open your
  provider app, then retry.”;
- the session-log draft may record the cue timeline as an optional section.
- when a provider requires its official player to remain visible, the cockpit renders that player
  at or above the provider minimum throughout playback. The quick-access widget may use compact
  controls while idle, but it must not collapse or hide that player while audio continues;
  scripted playback is blocked whenever the provider's visibility threshold is not met;

### 6.5 Package, schema, and validation

- new manifest section `audioCues` plus cue-reference fields on campaign, scenes, encounters,
  and locations; schemas with `$defs`, closed objects, and examples; DTO compatibility tests;
- exports contain cue keys, provider references, and cached display metadata — never audio
  content, never credentials;
- semantic validation: cue references resolve; category/transition enums valid; provider id is
  one of the declared providers or `UNKNOWN` (imported packages may reference providers this
  installation lacks — that imports with a WARNING and the cue shows as unavailable);
- round-trip: assignments and cues survive deep compare; playback state is intentionally
  transient and documented as such.

### 6.6 Acceptance criteria

- with a configured provider, a DM assigns a cue to a scene and hears playback switch on the DM
  device when entering the scene from the cockpit;
- activating an encounter switches to its combat cue; ending it returns to the scene cue;
- manual override always wins and is clearly indicated;
- with no provider configured, or offline, the widget states the condition, everything else in
  the app works unchanged, and no session action is blocked;
- no audio data, provider reference, or credential appears in any player payload or exported
  package credentials section — verified by contract tests;
- provider outage mid-session degrades to a visible widget error without interrupting scene or
  encounter operations;
- cue assignments round-trip in the flagship fixtures.

## 7. Cross-cutting contracts

- **Content-type and destination registries:** rollable tables, traps, hazards, and audio cues
  register stable type identifiers, key formats, destinations, labels, player-visibility
  eligibility (tables/traps/hazards/cues are all DM-only), and import/export adapters
  (master §17.3);
- **Capability matrix and manifest:** `docs/campaign-capabilities.md` and the machine-readable
  capability manifest gain entries for all three subsystems at introduction time, including the
  network-dependency note for music;
- **Documentation:** the DM manual gains sections for tables, traps/hazards, and audio;
  the authoring reference and agent guide gain the new schema sections; documentation examples
  remain executable fixtures (master §19);
- **Flagship fixtures:** the feature-complete fixture is extended with all three subsystems; the
  published-adventure-shaped fixture gains a random-encounter table and a structured trap;
- **Agent conversion:** the conversion playbook gains mapping rules for source tables
  (die column parsing, range normalization) and trap statblocks; the non-invention rule applies —
  a converter must not invent DCs, ranges, or damage to satisfy validation.

## 8. Quality requirements

### 8.1 Correctness

- table range validation is exhaustive: any gap or overlap is an ERROR with the entry path;
- audio cue resolution is deterministic from the priority stack; two clients observing the same
  session state derive the same active cue.

### 8.2 Performance

- table roll with nested resolution: under 100 ms locally;
- audio switch request issued within 500 ms of the triggering cockpit action (provider latency
  is outside the budget and shown as pending state).

### 8.3 Security and privacy

- provider tokens live only in local app data with owner-only file permissions; they never
  appear in exports, logs, or error responses;
- imported packages containing audio sections are untrusted input like everything else; provider
  URIs are treated as opaque strings and never fetched during import.

### 8.4 Data safety

- deleting a table/trap/hazard/cue follows the documented reference rules (master §10.4):
  dependents are listed and the DM confirms.

## 9. Verification strategy

- **Contract tests:** schemas ↔ DTOs for all new sections; registry coverage; fixture
  migrations from current v2 packages produce no warnings;
- **Round-trip tests:** extended flagship fixtures pass schema validate → dry-run → import →
  export → re-import → semantic deep compare;
- **Browser tests:** roll a table from the cockpit and confirm a reward draft; open a trap card
  from the story rail; exercise the simulated-provider audio widget flow (a fake provider backs
  browser tests so
  they stay offline and deterministic);
- **Security tests:** token absence from exports and error responses; audio absence from player
  payloads; hostile table/trap content (Markdown/HTML) rendering safely;
- **Manual acceptance:** the master §21.5 acceptance session is re-run after workstreams M, N, and
  P land, including a random-encounter roll and trap resolution; the music subsystem is exercised
  with a real configured provider in the same release-acceptance
  session. Provider-independent automated coverage continues to use a deterministic fake provider.

## 10. Delivery decomposition

Recommended sequence — tables first (pure content, unblocks conversion fidelity), then traps
(reuses the compendium patterns tables re-validated), then music (new external dependency,
isolated last):

| # | Delivery Item | Depends on | Status |
|---|--------------|------------|--------|
| 1 | Rollable tables: model, editor, roll flow, package section | — | `IMPLEMENTED` |
| 2 | Table integrations: scene/location links, encounter prefill, reward drafts | 1 | `IMPLEMENTED` |
| 3 | Traps/hazards: model, editor, package section, provenance | — | `IMPLEMENTED` |
| 4 | Traps/hazards integration: scene sections, tracker cards, map pins | 3 | `IMPLEMENTED` |
| 5 | Audio: provider SPI, auth, cue library, cockpit widget | — | `PLANNED` |
| 6 | Audio: scene/encounter assignments and dynamic switching | 5 | `PLANNED` |
| 7 | Fixtures, docs, capability matrix, agent playbook updates | 1–6 | `PLANNED` |

Items 1–2 and 3–4 are implemented. Items 5–6 form the remaining music package; item 7 closes this
specification. Progressive fog and structured travel/hexcrawl automation are not prerequisites.

## 11. Explicit non-goals

- progressive fog of war, line-of-sight, token vision, and lighting computation;
- structured travel/hexcrawl automation, including routes, journeys, watches, weather state,
  navigation, pace, and supply calculation;
- automatic application of trap/hazard damage or conditions to combatants;
- hosting, caching, transcoding, or redistributing audio files;
- synchronized audio playback on player devices;
- player accounts, player-controlled tokens, player rolling, or player sheet editing;
- bundling copyrighted tables, traps, or music with the application;
- stateful weather simulation; rollable tables may still represent weather results;
- circumventing any provider’s licensing, DRM, or terms of service.

## 12. Decisions captured by this specification

1. Rollable tables become first-class compendium content with exhaustive range validation and
   draft-based consequence application.
2. Traps and hazards become reusable structured compendium entries; resolution remains advisory
   and manual.
3. The music subsystem is streaming-first behind a provider SPI — the single approved exception
   to the offline principle — with playback on the DM device.
4. Music switching is automatic on cockpit scene/encounter transitions by default, with a
   per-campaign confirmation mode and a session mute.
5. Audio content is referenced, never stored; credentials never leave local app data.
6. Tables, traps/hazards, and music join the v2 package, registries, capability matrix, flagship
   fixtures, and security suite in the same delivery program.
7. Music is required for the DM-only readiness claim; an
   individual campaign may intentionally use silence, but the released product must provide and
   acceptance-test at least one working real provider integration.
8. Progressive fog and structured travel/hexcrawl automation are optional future features and do
   not block readiness.
9. This specification closes the required feature portion of delivery item 11. Reliability,
   documentation consistency, and the master manual acceptance gate remain separate
   release-verification obligations.
