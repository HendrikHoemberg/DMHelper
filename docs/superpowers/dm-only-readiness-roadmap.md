# DM-Only All-in-One Readiness — Canonical Execution Roadmap

**Last updated:** 2026-07-18

**Roadmap established after:** commit `36f7c3c`; use the status table together with current Git history

**Current NEXT item:** 2 — Music-provider feasibility spike

**Terminal goal:** DMHelper can prepare, run, record, export, restore, and resume a representative campaign as an all-in-one **DM-operated** tool.

This file is the durable execution handoff for the remaining readiness work. It exists so a new
agent can recover the intended sequence from the repository without relying on chat history.

## 1. Product boundary that must not drift

- DM-only all-in-one readiness is the goal.
- The player surface remains anonymous, read-only, and presentation-only.
- Player accounts, player-controlled tokens, player rolling, player voting, and player sheet
  editing are not part of this program.
- Music is required for readiness. Playback is DM-side and streaming-first behind a provider
  abstraction; provider failure must never block the rest of a session.
- Consequential travel, encounter, calendar, supply, location, fog, and story changes remain under
  explicit DM confirmation.
- The application must not invent rules, campaign facts, or source details to fill missing data.
- DM-only data is excluded at server projection boundaries, not hidden with CSS.

## 2. Authority and conflict rules

Use these sources in this order:

1. The [master readiness specification](specs/2026-07-15-all-in-one-dm-readiness-design.md)
   defines the product goal, release gates, and non-goals.
2. The approved [table fidelity and atmosphere design](specs/2026-07-17-table-fidelity-and-atmosphere-design.md)
   and [travel and exploration design](specs/2026-07-18-dm-travel-and-exploration-design.md)
   define subsystem requirements and acceptance criteria.
3. This roadmap defines the serial execution order and current handoff state.
4. Each implementation plan defines the exact files, tests, and commits for its work package.

If sources disagree, requirements from the authoritative design win over roadmap shorthand. This
roadmap wins over older chat summaries for execution order. Fresh code inspection and test evidence
win over a stale status label; correct the label before continuing.

## 3. Status vocabulary

| Status | Meaning |
|---|---|
| `BLOCKED` | A predecessor gate is incomplete. Do not create or execute this package yet. |
| `READY` | This is the single next package. Its approved design and prerequisites are available. |
| `PLANNING` | The detailed implementation plan is being written and reviewed. |
| `IN_PROGRESS` | The approved implementation plan is being executed. |
| `VERIFYING` | Implementation is present; focused, full-suite, security, or manual gates remain. |
| `COMPLETE` | Required implementation, documentation, and exit-gate evidence are committed. |

Only one row may be `READY`, `PLANNING`, `IN_PROGRESS`, or `VERIFYING` at a time. The sequence is
intentionally serial even where the subsystem specifications permit parallel work; this minimizes
integration churn and keeps the release gate attributable.

## 4. Ordered implementation queue

| # | Work package | Status | Depends on | Design scope | Implementation plan | Exit gate |
|---|---|---|---|---|---|---|
| 1 | P0 browser-smoke release-gate repair | `COMPLETE` | — | Master §§5–6, 21, 23 | [Completed plan](plans/2026-07-18-p0-browser-smoke-release-gate-repair.md) | Native party dialog regression and same-origin sheet setup fixed; `CoreSessionLoopSmokeTest` and full Maven suite green; master blocker note updated. |
| 2 | Music-provider feasibility spike | `READY` | 1 | Atmosphere §7.1 and §7.6 | [Ready plan](plans/2026-07-18-music-provider-feasibility.md) | Using current official Spotify and YouTube documentation, commit `docs/architecture/music-provider-feasibility.md` identifying one viable provider, OAuth/token-storage approach, DM-device playback-control proof, capability limits, failure modes, and any account/subscription prerequisites. No readiness feature is marked complete by the spike. |
| 3 | Rollable tables and integrations | `BLOCKED` | 2 | Atmosphere delivery items 1–2 | Create a dated `p3-rollable-tables` plan | Table model/editor/validation, nested deterministic rolls, log integration, scene/location links, encounter/reward drafts, package round-trip, fixtures, and focused/full tests pass. |
| 4 | Traps and hazards | `BLOCKED` | 3 | Atmosphere delivery items 3–4 | Create a dated `p3-traps-and-hazards` plan | Structured and prose-compatible traps/hazards, provenance, scene/tracker/map integrations, package round-trip, player-safety coverage, and focused/full tests pass. |
| 5 | Travel core | `BLOCKED` | 4 | Travel delivery items 1–3 | Create a dated `p3-travel-core` plan | Settings, migrations, routes, legs, authoring/search/dependency rules, journey/watch state machine, persistence, package-key contracts, and focused/full tests pass. |
| 6 | Manual fog of war | `BLOCKED` | 5 | Atmosphere delivery items 5–6 | Create a dated `p3-manual-fog` plan | Mask model, DM tools, package support, server-side masked projection, reconnect behavior, cache/payload leak security tests, round-trip, and focused/full tests pass. |
| 7 | Atmosphere and music completion | `BLOCKED` | 6 and the decision from 2 | Atmosphere delivery items 7–8 | Create a dated `p3-atmosphere-music` plan | Provider SPI/reference adapter, local OAuth and token clearing, cue library, cockpit widget, assignments, priority switching, deterministic fake-provider tests, package safety, and bounded outage behavior pass. |
| 8 | Travel rules and runtime integrations | `BLOCKED` | 3, 5, 6, 7 | Travel delivery items 4–7 | Create a dated `p3-travel-integrations` plan | Weather, pace, navigation, roles, modes, supply drafts, calendar/ledger/encounter/scene/log connections, cockpit journey panel, and music transitions pass without autonomous mutations. |
| 9 | Travel packaging and acceptance gates | `BLOCKED` | 8 | Travel delivery items 8–9 | Create a dated `p3-travel-packaging-and-gates` plan | Package adapters, semantic snapshot, fixtures, documentation, browser/security/performance tests, export/restore/resume, and the travel acceptance scenario pass. |
| 10 | Cross-spec readiness closeout | `BLOCKED` | 3–9 | Atmosphere item 9; master §§19–23 | Create a dated `dm-readiness-cross-spec-closeout` plan | Feature-complete and published-adventure fixtures cover tables, traps, fog, music, and travel; schemas/catalogs/playbook/manual/capability matrix agree; full round-trip and player-safety suites pass. |
| 11 | Final readiness verification and release decision | `BLOCKED` | 10 | Master §§21 and 23 | Create a dated `dm-readiness-release-verification` plan | Full automated suite, security gates, documentation audit, real-provider music exercise, and recorded representative manual acceptance session pass. Only then may master item 11 and the DM-only readiness claim be marked complete. |

## 5. Why this order is fixed

1. The known red browser gate is repaired before adding breadth.
2. Provider feasibility is checked early because music is required and is the only approved runtime
   internet dependency; discovering an unusable control/auth model late would invalidate completed
   integration work.
3. Tables land before traps and travel because both consume typed table results and drafts.
4. Traps reuse the compendium, provenance, package, and typed-reference patterns validated by
   tables.
5. The independent travel domain model lands before UI-heavy cross-module integration.
6. Fog is completed and security-tested before travel's fogged-arrival acceptance flow.
7. Full music implementation remains isolated until its provider decision and the local content
   patterns are stable.
8. Travel integration then composes tables, world locations, encounters, fog-aware arrival, music,
   calendar, party, ledger, and session lifecycle without inventing duplicate subsystems.
9. Documentation and cross-spec fixtures close only after the domain behavior is stable.
10. The release decision is evidence-driven and last; implementation status alone is insufficient.

## 6. Future-agent handoff protocol

At the start of every continuation session:

1. Read this roadmap, the master specification, and the design/plan linked by the first active row.
2. Run `git status --short` and `git log -5 --oneline`; preserve unrelated user changes.
3. Verify the active row's stated baseline with its focused test before editing.
4. If its plan exists, execute that plan using the required execution skill. If it does not exist,
   use the writing-plans skill against the already approved design scope named in the row.
5. Do not begin a later row to work around a failure in the active row.
6. Update this roadmap in the same commit that closes or changes a work-package status.
7. Mark a row `COMPLETE` only after its exit gate is demonstrated. Change the immediately following
   row from `BLOCKED` to `READY` in that same status-update commit.
8. Record deviations in this file with the reason and dependency effect; do not silently reorder
   packages.

For the provider feasibility package, browse current **official provider documentation** because API
capabilities, OAuth requirements, subscription rules, and platform policies are time-sensitive.
For all other packages, prefer repository contracts and the approved specifications; browse only
where the task independently requires current external facts.

## 7. Current recovery note

As of the P0 post-implementation audit on 2026-07-18:

- master delivery items 1–10 are implemented;
- world graph and faction clocks are implemented;
- the remaining P3 designs are approved and committed;
- the P0 browser-smoke plan is implemented, audited, and linked above;
- the music-provider feasibility plan is written; no P3 table/trap/fog/full-music/travel implementation plan has yet been created;
- the focused browser gate and complete Maven suite are green;
- the next action is to execute the linked music-provider feasibility-spike plan for row 2.

When conversation context is missing or compacted, resume from the first non-`COMPLETE` row in this
file and validate its status against the repository before acting.
