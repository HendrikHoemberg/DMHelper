# DMHelper DM Travel and Exploration — Design Specification

**Date:** 2026-07-18
**Status:** Approved design
**Parent specification:** `2026-07-15-all-in-one-dm-readiness-design.md` (master), delivery item 11
**Companion specification:** `2026-07-17-table-fidelity-and-atmosphere-design.md`
**Product premise:** A DM can prepare and run overland or regional travel entirely in DMHelper,
including routes, pace, watches, weather, navigation, supplies, random encounters, calendar
advancement, arrival, and session records, without consulting a separate worksheet or campaign
reference.

## 1. Purpose and relationship to DM-only readiness

This specification defines the second remaining required DM-only P3 slice. The companion table-
fidelity and atmosphere specification supplies rollable tables, structured traps and hazards,
manual fog, and music. Together with the implemented world graph and faction clocks, the two
specifications close the feature portion of master delivery item 11.

DMHelper is operated by the DM. The existing player surface remains anonymous and read-only.
Player accounts, player-controlled tokens, player rolling, and player sheet editing are outside
this design and outside the all-in-one readiness goal.

Travel is a structured DM assistant, not an autonomous simulation. It calculates and proposes
changes, but the DM confirms every consequential calendar, resource, encounter, location, and
session-log mutation.

## 2. Current capability baseline

| Area | Current strength | Missing capability |
|---|---|---|
| World geography | World locations, parent hierarchy, maps, encounter links, adjacent travel-location links | No route distance, terrain, risk, direction, duration, or source contract |
| Calendar | Custom calendars, current date, timeline events | No reviewed travel-time advancement or arrival workflow |
| Party | Active members, sheets, inventory, batch operations | No journey attendance, watch roles, supply plan, or travel-state projection |
| Tables and dice | Dice engine exists; rollable tables are designed by the companion spec | No travel-aware weather, navigation, or encounter result flow |
| Encounters | Prepared encounters, waves, rewards, completion summaries | No journey encounter draft or resume-after-encounter workflow |
| Treasury and ledger | Items, assignments, currency, append-only transactions | No reviewable journey consumption draft |
| Session cockpit | Durable session lifecycle and deterministic log draft | No journey panel, travel progress, watch resolution, or arrival action |
| Package v2 | Stable keys, schemas, validation, preview, atomic round-trip | No travel entities or runtime state |

Existing `WorldLocation.travelLocationRefs` remain valid adjacency hints. They are not silently
converted into routes because they contain no authoritative distance, direction, duration, or
source. The route editor may offer them as suggestions and requires DM confirmation before
creating structured routes.

## 3. Product principles

### 3.1 DM confirmation is the mutation boundary

The app may calculate likely time, resource consumption, weather effects, encounter results, and
arrival consequences. It never silently advances the calendar, consumes supplies, starts an
encounter, changes the party location, reveals a map, completes a scene, or applies exhaustion.

Every such consequence is represented as a reviewable draft. The DM may confirm all changes,
confirm selected changes, edit values with a visible reason, defer them, or discard them.

### 3.2 Structured where the app acts

Distance, units, pace, duration, watches, roles, weather effects, navigation results, resource
quantities, progress, and draft mutations use typed fields. Markdown remains appropriate for route
descriptions, narration, landmarks, advice, and unresolved source annotations.

### 3.3 No invented exploration rules

Navigation DCs, route distances, weather modifiers, encounter frequencies, resource rates, and
travel speeds must come from installed rules data, imported source data, campaign configuration,
or an explicit DM value. Missing information stays unset and visible. The app never guesses merely
to produce an estimate.

### 3.4 Stable, round-trippable state

Every route, leg, journey, and watch has a stable package key. Weather and change drafts are stable
value records owned by their journey or watch. Active, paused, and unresolved state is persistent-
exported and survives refresh, restart, export, import, and resume.

### 3.5 Existing modules remain authoritative

Travel reuses world locations, campaign dates, party members, sheets, inventory, rollable tables,
encounters, maps, scenes, notes, the ledger, session lifecycle, music cues, and package-v2 adapters.
It does not introduce duplicate calendars, party rosters, encounter trackers, dice engines, or
session logs.

## 4. Domain model

### 4.1 Travel route

A `TravelRoute` is a reusable, campaign-scoped connection between world locations.

Required fields:

- `key`, `name`, `originLocationRef`, and `destinationLocationRef`;
- `direction`: `ONE_WAY` or `BIDIRECTIONAL`;
- `distance` and `distanceUnit`;
- source/provenance and creation timestamp.

Optional fields:

- summary, DM notes, public description, tags, and source locator;
- terrain and route-type tags;
- base risk and navigation difficulty labels;
- default navigation check and encounter-check cadence;
- allowed or discouraged travel modes;
- linked route map, named map region, scenes, handouts, notes, rules, and rollable tables;
- ordered landmarks and optional ordered legs;
- default music cue for travel on this route.

Distance units are `MILE`, `KILOMETER`, `HEX`, `TRAVEL_HOUR`, and `TRAVEL_DAY`. A route using
`HEX` must declare the scale of one hex. Time-based distance units describe a source-provided
duration and are not reinterpreted as physical distance.

Origin and destination must differ. A bidirectional route represents the same path in either
direction but may define direction-specific notes, DCs, or modifiers. When those differ materially,
the DM creates two one-way routes instead.

### 4.2 Travel leg

A `TravelLeg` is an optional ordered portion of a route used when terrain, risk, distance, or
destination changes during travel.

Fields:

- `key`, label, sort order, distance, and unit;
- optional start/end landmark or location references;
- terrain, navigation, encounter, weather, and pace modifiers;
- linked map/region, scene, table, and DM notes;
- source locator.

Leg distances must use the route unit or declare an exact conversion supported by campaign travel
configuration. The sum of physical leg distances must equal the route distance. A route without
legs is valid and is treated as one implicit leg.

### 4.3 Journey

A `Journey` is campaign runtime state for a particular traversal.

Fields:

- `key`, title, `routeRef` or an embedded ad-hoc route snapshot;
- origin, destination, direction, and participating party-member references;
- status: `DRAFT`, `ACTIVE`, `PAUSED`, `COMPLETED`, or `CANCELLED`;
- selected pace and travel mode;
- departure campaign date/time, expected arrival, and actual arrival;
- current leg, completed distance, remaining distance, and elapsed travel time;
- current weather state and active watch;
- linked campaign session and optional arrival scene;
- pending change drafts, notes, source annotations, created/updated timestamps, and version.

Only one journey may be `ACTIVE` for a campaign. Paused journeys remain resumable. A cancelled
journey preserves its history and approved ledger/calendar effects; cancellation never rolls back
already confirmed changes.

An ad-hoc journey stores the values used to calculate it and may later be promoted to a reusable
route. Promotion creates a new route and links the journey to it without changing historical
values.

### 4.4 Travel watch

A `TravelWatch` is an ordered period within a journey.

Fields:

- `key`, sequence, status, start/end campaign time, and duration;
- planned and actual distance;
- pace and travel mode snapshot;
- typed role assignments;
- weather state and weather source;
- navigation check, entered or rolled result, outcome, and source;
- encounter check, entered or rolled result, and linked encounter draft;
- resource-consumption draft;
- narration, DM notes, resolved event references, and timestamps.

Watch status is `PLANNED`, `IN_PROGRESS`, `REVIEW`, `CONFIRMED`, or `SKIPPED`. A watch cannot be
confirmed while it contains an invalid date, unresolved required reference, or malformed quantity.

### 4.5 Watch roles

Role assignments use:

- `NAVIGATOR`;
- `LOOKOUT`;
- `FORAGER`;
- `GUIDE`;
- `DRIVER`;
- `ANIMAL_HANDLER`;
- `CARTOGRAPHER`;
- `OTHER` with a required display label.

A role assignment references a participating party member, optionally a relevant skill/tool and
rule reference, and any DM-entered modifier. Campaign configuration declares whether one member may
hold multiple simultaneous roles. The app displays conflicts but does not make party decisions.

### 4.6 Weather profile and state

A `WeatherProfile` is reusable campaign or user-global content with scope and provenance.

Fields:

- `key`, name, public description, and optional DM description;
- category and severity;
- default duration expression;
- visibility, pace, navigation, encounter, rest, and exposure modifiers;
- optional damage/resource expressions and condition/rule references;
- tags and provenance.

All mechanical fields are optional. If a field affects calculation, it must be typed and sourced.

A `WeatherState` is a journey/watch snapshot containing the selected profile reference, resolved
duration, active typed modifiers, public description, DM notes, source table roll, overrides, and
override reasons. Changing a reusable profile never changes historical watch snapshots.

The rollable-table content registry accepts `WEATHER_PROFILE` as a typed result reference. Rolling
a `WEATHER` table creates a weather-state draft; the DM confirms or edits it before it becomes
active.

### 4.7 Pace and travel configuration

`CampaignTravelSettings` is typed campaign configuration containing:

- default distance display unit;
- default watch duration and watches per travel day;
- pace presets;
- calendar time conversion rules;
- role-conflict policy;
- default supply rates and rounding rules;
- default encounter-check cadence;
- ruleset/source references.

A pace preset contains a key, label, multiplier or sourced fixed rate, permitted travel modes,
navigation/awareness/foraging modifiers, and provenance. Installed rules data may provide presets;
campaign presets may add or override them with a visible reason.

### 4.8 Travel mode and resources

A journey selects a travel mode: `FOOT`, `MOUNT`, `VEHICLE`, `SHIP`, `MAGICAL`, or `OTHER`.
It may reference equipment, a generic rule, a world entity, or campaign-scoped custom content for
the actual mount or vehicle. Travel configuration supplies its speed/capacity rules when those are
used in calculations.

A `TravelResourceRequirement` identifies:

- the sheet resource, inventory item, treasury assignment, currency, or plain-text fallback;
- consumption rate and basis (`PER_MEMBER`, `PER_MOUNT`, `PER_VEHICLE`, `PER_WATCH`, or `PER_DAY`);
- quantity unit, rounding behavior, and source;
- eligible holders and preferred depletion order.

Plain-text fallback is descriptive only and cannot be decremented automatically. Typed resources
produce a reviewable draft that names each holder and exact before/after quantity.

### 4.9 Travel change draft

A `TravelChangeDraft` groups proposed consequences from a watch or arrival:

- campaign-calendar advancement;
- timeline events;
- resource and ledger changes;
- party conditions or exhaustion recommendations;
- encounter creation/activation;
- journey progress;
- campaign current-location change;
- scene arrival/current-scene change;
- session-log facts and unresolved annotations.

Each child change has `PENDING`, `CONFIRMED`, `DEFERRED`, or `DISCARDED` status. Confirmation is
atomic for the selected child set. A failed mutation rolls back every selected child and leaves the
draft reviewable.

## 5. Route authoring and preparation

World-location detail pages show outgoing and incoming routes, typical travel information, linked
tables, and recent journeys. The route editor supports:

- selecting world-location endpoints and direction;
- entering sourced distance/unit or explicit travel time;
- adding/reordering legs and landmarks;
- selecting terrain, pace, navigation, weather, and encounter rules;
- linking maps/regions, scenes, notes, handouts, rules, tables, and music cues;
- previewing duration and supply calculations for the active party;
- creating direction-specific overrides;
- recording provenance and unresolved source annotations.

Existing adjacency links are shown as route suggestions. Creating a structured route from a
suggestion always requires the DM to supply or explicitly leave blank every value the adjacency
does not contain.

The DM may create an ad-hoc journey directly from the cockpit. Only origin, destination, and an
explicit distance or duration are required. Advanced route mechanics remain optional.

## 6. Journey runtime workflow

### 6.1 Start

From a world location or cockpit quick action, the DM:

1. selects a prepared route or creates an ad-hoc journey;
2. confirms direction, participants, pace, and travel mode;
3. reviews expected duration, watches, supplies, navigation, encounter cadence, and warnings;
4. selects or rolls starting weather;
5. starts the journey.

Starting records the origin and departure state. It does not immediately change the campaign's
current location or advance the calendar.

### 6.2 Watch resolution

The cockpit journey panel shows destination, route/leg progress, pace, current watch, weather,
roles, supplies, linked tables, and unresolved drafts. Resolving a watch follows this order:

```text
confirm watch setup
  → assign roles
  → retain/select/roll weather
  → enter/roll navigation
  → enter/roll encounter or travel event
  → calculate time, distance, and resources
  → review one grouped change draft
  → confirm, edit, defer, or discard child changes
```

Digital rolling and physical-roll entry produce the same typed result. Nested table rolls remain
grouped in the dice log. A generated encounter is prepared by default and becomes active only after
DM confirmation. When combat starts, the journey pauses at a precise watch/progress boundary and
resumes after the encounter without duplicating time or resource effects.

### 6.3 Pause, reroute, and cancel

The DM may pause at any time. Refresh, reconnect, and application restart restore the exact state.

Rerouting closes the current leg at a DM-confirmed progress point, records the reason, and selects
a new prepared or ad-hoc continuation. Historical route snapshots do not change.

Cancellation preserves confirmed history and marks pending drafts for explicit discard or manual
resolution.

### 6.4 Arrival

Reaching the calculated endpoint creates an arrival draft. It may propose:

- final time/resource changes;
- setting the campaign's current world location;
- selecting an arrival scene;
- creating a timeline event;
- changing music from route cue to location/scene cue;
- adding journey facts to the session-log draft.

The journey becomes `COMPLETED` only after the DM reviews arrival. Fog state and map presentation
never change automatically.

## 7. Session cockpit and music integration

The cockpit gains a journey panel without duplicating calendar, party, encounter, map, or dice
state. Core travel actions remain within two deliberate actions:

- start/resume/pause journey;
- advance or review a watch;
- assign roles;
- select or roll weather;
- enter navigation and encounter rolls;
- inspect supplies;
- open the current route map/location/scene;
- review pending changes.

Music cue priority extends the companion specification without changing its existing order:

1. manual override;
2. active encounter combat cue;
3. current scene cue;
4. active journey route/exploration cue;
5. current location cue;
6. campaign default cue;
7. silence.

Starting, resuming, pausing, rerouting, and arriving re-evaluate this stack. Provider failure remains
isolated to the audio widget and never blocks travel actions.

## 8. Calendar, party, inventory, and log integration

### 8.1 Calendar

Travel calculations use the campaign's configured calendar and explicit time units. A confirmed
watch advances the date/time through the calendar service. Leap/intercalary behavior and custom
month lengths remain authoritative. Invalid dates are rejected before draft confirmation.

### 8.2 Party and attendance

Journey participation is journey-specific and independent of the character-level active flag.
Watch roles reference participants. Rest, exhaustion, conditions, and other member effects remain
recommendations until separately confirmed through existing party operations.

### 8.3 Supplies and ledger

Typed consumption produces append-only ledger entries and updates the authoritative holder state
only after confirmation. Insufficient supply is a warning, not an invented purchase or forced
negative quantity. Depletion order is visible and editable.

### 8.4 Encounters and tables

Routes and legs may link `ENCOUNTER`, `WEATHER`, and `EVENT` category rollable tables. Results may
prefill encounters or weather/event drafts. Travel never automatically starts combat or grants
rewards.

### 8.5 Session log

The deterministic session-log draft includes:

- origin/destination and route;
- participants, pace, mode, watches, and elapsed time;
- weather changes and navigation outcomes;
- confirmed resources consumed;
- travel encounters and outcomes;
- reroutes, unresolved drafts, arrival, and next hooks;
- optional music cue timeline.

## 9. Player-safe presentation

The player surface remains anonymous and read-only. Travel data is absent by default. When the DM
explicitly presents travel information, the server may project only:

- public origin/destination labels;
- public route or journey description;
- public weather description;
- presented map/image and explicitly revealed route information;
- high-level progress chosen by the DM.

Player payloads never include secret destinations, unrevealed route legs, alternate routes,
navigation DCs, encounter chances/tables, planned events, supply totals, DM notes, source
annotations, or pending drafts. CSS hiding is not an acceptable protection boundary.

## 10. Package-v2 contract

Campaign format v2 gains optional sections:

- `travelRoutes`;
- `journeys`;
- `weatherProfiles`.

Typed campaign settings gain `travelSettings` and `currentWorldLocationRef`. The current-location
reference changes only through an explicit DM action or a confirmed journey-arrival draft.

Travel legs, watches, weather-state snapshots, role assignments, resource requirements, and change
drafts are nested under their owning route/journey unless independently addressable. Addressable
nested records have stable keys.

New content types include:

- `TRAVEL_ROUTE`;
- `TRAVEL_LEG`;
- `JOURNEY`;
- `TRAVEL_WATCH`;
- `WEATHER_PROFILE`.

The package preserves travel settings, current location, active/paused journey state, confirmed and
unresolved drafts, historical snapshots, source/provenance, and all typed references. Intentionally
transient fields are limited to open UI panels, unsaved form input, in-flight requests, and derived
display caches.

The travel section adapter imports after world locations, party, calendar configuration, tables,
and content dependencies are registered, using deferred resolution where the established adapter
order requires it. Import remains additive and atomic.

## 11. Schema and semantic validation

JSON Schema draft 2020-12 definitions are closed and include units, defaults applied by the same
runtime code, constraints, descriptions, and examples.

Semantic validation covers:

- unique keys and all typed references;
- endpoints belonging to the same campaign and not referencing themselves;
- route direction and direction-specific overrides;
- positive distance/duration and declared units;
- hex scale when required;
- leg order, continuity, unit conversion, and distance totals;
- valid journey transitions and at most one active journey;
- progress within route/leg bounds;
- participating-member and role-holder consistency;
- role conflicts under campaign policy;
- pace and travel-mode compatibility;
- dice/quantity expression parsing;
- weather-profile and table-result reference types;
- calendar dates and time advancement;
- resource holders, units, non-negative confirmed quantities, and depletion order;
- encounter, scene, map/region, location, music, note, rule, and table references;
- change-draft state and duplicate-application identifiers.

Unknown optional mechanics may degrade to plain text only when the target field documents that
fallback. Preview shows the lost automation explicitly. Nothing degrades silently.

## 12. Error handling and data safety

- All user-initiated travel mutations use visible correlated error handling and retry behavior.
- Optimistic failures retain entered rolls, assignments, notes, and the pending draft.
- Watch and arrival confirmation are idempotent and protected against double submission.
- Selected child changes commit atomically; failure leaves the complete selected set unapplied.
- Encounter interruption records an exact journey boundary before encounter activation.
- Deleting a referenced route, table, location, resource, or weather profile lists dependencies and
  requires confirmation according to existing reference rules.
- Import stages all travel data before commit and leaves no partial routes/journeys on failure.
- Campaign deletion cascades travel-owned data through reviewed Flyway constraints.
- Startup backups and package recovery include all travel state.

## 13. Accessibility and performance

Core journey actions are keyboard-operable. Focus is visible and restored after dialogs and HTMX
swaps. Weather, progress, warning, and supply state never rely on color alone.

Performance budgets on reference hardware:

- cockpit journey panel meaningful render: under 250 ms after cockpit data is available;
- watch calculation excluding user/provider latency: under 150 ms;
- draft confirmation acknowledgement: under 200 ms locally;
- route search first results: under 150 ms after debounce;
- large journey history remains paginated and does not inflate initial cockpit payloads.

## 14. Verification strategy

### 14.1 Contract and unit tests

- schemas and examples validate;
- Java DTO/schema compatibility;
- route/leg unit and continuity validation;
- pace, watch, weather, navigation, progress, and supply calculations;
- journey state-machine transitions;
- draft idempotency and atomicity;
- typed-reference and registry coverage.

### 14.2 Round-trip fixtures

Extend the flagship fixtures with:

1. a minimal prepared route;
2. an active multi-leg journey with roles, weather, and pending changes;
3. a published-adventure-shaped journey with sourced distance, weather table, navigation failure,
   random encounter, resource consumption, reroute, fogged arrival map, and music cues.

Each passes schema validate → dry-run → import → export → re-import → semantic deep compare.

### 14.3 Browser tests

- author a route from a world location;
- start an ad-hoc and a prepared journey;
- assign roles and enter physical roll results;
- roll weather and random encounters through the table UI;
- review partial and full draft confirmation;
- pause, refresh, restart, and resume;
- interrupt with an encounter and resume without duplicate changes;
- arrive, select a scene, and save the session-log draft;
- verify music transitions with the fake provider;
- assert console errors, malformed requests, and unexpected HTTP failures fail the suite.

### 14.4 Security tests

- player payload absence for secret routes, DCs, tables, resources, and drafts;
- PIN enforcement on every travel authoring/runtime API;
- hostile Markdown/HTML in route, weather, and event text;
- malformed dates, quantities, references, and duplicate confirmations;
- import rollback at every adapter stage.

### 14.5 Manual acceptance

The master four-hour acceptance session includes a representative multi-day journey:

- choose a route and pace;
- assign watches and roles;
- roll weather and navigation;
- consume supplies through a reviewed ledger draft;
- trigger and resolve a random encounter;
- resume travel;
- arrive on a fogged map;
- observe exploration, combat, and arrival music transitions through a real provider;
- end the session, export, restore, and resume without external campaign or tracking tools.

## 15. Delivery decomposition

| # | Delivery item | Depends on | Status |
|---|---|---|---|
| 1 | Travel contracts, settings, migrations, routes, and legs | World graph | `PLANNED` |
| 2 | Route authoring UI, destinations, search, and dependency rules | 1 | `PLANNED` |
| 3 | Journey/watch runtime model and state machine | 1 | `PLANNED` |
| 4 | Weather profiles and rollable-table integration | Companion table items 1–2, 3 | `PLANNED` |
| 5 | Pace, navigation, roles, travel modes, and supply drafts | 3 | `PLANNED` |
| 6 | Calendar, party, ledger, encounter, scene, and log integration | 3–5 | `PLANNED` |
| 7 | Session cockpit journey panel and music integration | Companion audio items 7–8, 3–6 | `PLANNED` |
| 8 | Package adapters, semantic snapshot, fixtures, and docs | 1–7 | `PLANNED` |
| 9 | Browser, security, performance, and manual acceptance gates | 1–8 | `PLANNED` |

Items 1–3 establish the independent travel core. Item 4 may proceed alongside item 5 once the
companion rollable-table contract exists. Items 6–9 integrate and close the slice.

## 16. Explicit non-goals

- autonomous selection of routes, roles, DCs, encounters, consequences, or story outcomes;
- real-world GIS routing or online map services;
- continuous tactical movement simulation between world locations;
- automatic application of damage, exhaustion, conditions, rests, or inventory purchases;
- a comprehensive vehicle combat subsystem;
- real-time meteorological simulation beyond campaign-defined weather profiles and transitions;
- player accounts, player travel voting, player-controlled tokens, player rolling, or player sheet
  editing;
- automatic fog reveal during travel;
- requiring a music provider for non-audio travel operation.

## 17. Readiness relationship

Implementing this specification does not by itself authorize the all-in-one readiness claim. The
claim additionally requires:

- all companion table/trap/fog/music delivery items complete;
- world graph and faction clocks remaining supported;
- the full automated suite green, including existing runtime reliability smoke coverage;
- capability manifest, master status, release notes, and known limitations in agreement;
- no remaining partial capability forcing consultation of external campaign references;
- player-safe projection and package security gates passing;
- the master manual acceptance session passing with a real music provider.

## 18. Decisions captured by this specification

1. DM-only readiness includes travel and weather but excludes interactive player systems.
2. Travel is a review-and-confirm workflow, never an autonomous simulation.
3. Routes are reusable world-graph connections; journeys and watches preserve runtime history.
4. Weather has reusable typed profiles and immutable runtime snapshots.
5. Consequential calendar, resource, encounter, location, and scene changes use atomic drafts.
6. Rules values are sourced or DM-entered; missing values remain explicit rather than guessed.
7. Travel reuses existing calendars, parties, ledgers, tables, encounters, sessions, and music.
8. Music is part of the required acceptance journey, while provider failure remains isolated.
9. The player surface remains anonymous, optional, read-only presentation.
10. This specification and the companion table-fidelity and atmosphere specification together
    close the required feature portion of master delivery item 11.
