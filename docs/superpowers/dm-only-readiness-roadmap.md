# DM-Only All-in-One Readiness — Canonical Execution Roadmap

**Last updated:** 2026-07-19

**Roadmap established after:** commit `36f7c3c`; use the status table together with current Git history

**Current NEXT item:** 5 — Atmosphere and music completion

**Terminal goal:** DMHelper can prepare, run, record, export, restore, and resume a representative
campaign as an all-in-one **DM-operated** tool, including the intended DM-side music experience.

This file is the durable execution handoff for the remaining readiness work. It exists so a new
agent can recover the intended sequence from the repository without relying on chat history.

## 1. Product boundary that must not drift

- DM-only all-in-one readiness is the goal.
- The player surface remains anonymous, read-only, and presentation-only.
- Player accounts, player-controlled tokens, player rolling, player voting, and player sheet
  editing are not part of this program.
- Music is required for readiness. Playback is DM-side and streaming-first behind a provider
  abstraction; provider failure must never block the rest of a session.
- Progressive fog of war and structured travel/hexcrawl automation are optional future features,
  not readiness requirements. Existing maps, presentation controls, world-location adjacency,
  notes, calendars, and rollable tables remain available for manual campaign workflows.
- The application must not invent rules, campaign facts, or source details to fill missing data.
- DM-only data is excluded at server projection boundaries, not hidden with CSS.

## 2. Authority and conflict rules

Use these sources in this order:

1. The [master readiness specification](specs/2026-07-15-all-in-one-dm-readiness-design.md)
   defines the product goal, release gates, and non-goals.
2. The approved [table fidelity and atmosphere design](specs/2026-07-17-table-fidelity-and-atmosphere-design.md)
   defines the remaining subsystem requirements and acceptance criteria.
3. This roadmap defines the serial execution order and current handoff state.
4. Each implementation plan defines the exact files, tests, and commits for its work package.

If sources disagree, requirements from the authoritative design win over roadmap shorthand. This
roadmap wins over older chat summaries for execution order. Fresh code inspection and test evidence
win over a stale status label; correct the label before continuing.

The former travel-and-exploration design and travel-core implementation plan were intentionally
removed on 2026-07-19. Historical Git commits are not active authority and must not be used to
resume travel or fog work.

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
intentionally serial to minimize integration churn and keep the release gate attributable.

## 4. Ordered implementation queue

| # | Work package | Status | Depends on | Design scope | Implementation plan | Exit gate |
|---|---|---|---|---|---|---|
| 1 | P0 browser-smoke release-gate repair | `COMPLETE` | — | Master §§5–6, 21, 23 | [Completed plan](plans/2026-07-18-p0-browser-smoke-release-gate-repair.md) | Native party dialog regression and same-origin sheet setup fixed; `CoreSessionLoopSmokeTest` and full Maven suite green; master blocker note updated. |
| 2 | Music-provider feasibility spike | `COMPLETE` | 1 | Atmosphere §6.1 and §6.6 | [Completed plan](plans/2026-07-18-music-provider-feasibility.md) | YouTube proven as viable baseline provider on the DM device (Firefox/Linux, 480x270 visible player, all six controls passed). Spotify classified CONDITIONAL (Policy III.6 synchronization). Provider contract frozen. See [decision record](../architecture/music-provider-feasibility.md). |
| 3 | Rollable tables and integrations | `COMPLETE` | 2 | Atmosphere delivery items 1–2 | [Completed corrective plan](plans/2026-07-18-rollable-tables-corrective-implementation.md) | Correct API DTOs, campaign-aware validation, complete authoring/rolling UI, consequence state machine, runnable package fidelity, real browser acceptance, and focused/full tests pass. |
| 4 | Traps and hazards | `COMPLETE` | 3 | Atmosphere delivery items 3–4 | [Completed plan](plans/2026-07-18-p3-traps-and-hazards.md) | Structured and prose-compatible traps/hazards, provenance, scene/tracker/map integrations, package round-trip, player-safety coverage, and focused/full tests pass. |
| 5 | Atmosphere and music completion | `IN_PROGRESS` | 2, 4 | Atmosphere delivery items 5–6 | [Implementation plan](plans/2026-07-19-p3-atmosphere-music.md) | Provider SPI/reference adapter, local credentials/token clearing where required, cue library, cockpit widget, assignments, priority switching, deterministic fake-provider tests, package safety, and bounded outage behavior pass. |
| 6 | Music-focused readiness closeout | `BLOCKED` | 5 | Atmosphere item 7; master §§19–23 | Create a dated `dm-readiness-music-closeout` plan | Feature-complete and published-adventure fixtures cover tables, traps, and music; schemas/catalogs/playbook/manual/capability matrix agree; full round-trip and player-safety suites pass. |
| 7 | Final readiness verification and release decision | `BLOCKED` | 6 | Master §§21 and 23 | Create a dated `dm-readiness-release-verification` plan | Full automated suite, security gates, documentation audit, real-provider music exercise, and recorded representative manual acceptance session pass. Only then may master item 11 and the DM-only readiness claim be marked complete. |

## 5. Why this order is fixed

1. The known red browser gate was repaired before adding breadth.
2. Provider feasibility was checked early because music is required and is the only approved
   runtime internet dependency.
3. Tables landed before traps so traps could reuse typed results, provenance, package, and
   reference patterns.
4. Music now builds on the stable campaign, scene, encounter, cockpit, package, and player-safety
   contracts established by completed work.
5. Documentation and fixtures close only after music behavior is stable.
6. The release decision is evidence-driven and last; implementation status alone is insufficient.

## 6. Future-agent handoff protocol

At the start of every continuation session:

1. Read this roadmap, the master specification, and the design/plan linked by the first active row.
2. Run `git status --short` and `git log -5 --oneline`; preserve unrelated user changes.
3. Verify the active row's stated baseline with its focused test before editing.
4. If its plan exists, execute it. If it does not exist, write a plan against the already approved
   design scope named in the row.
5. Do not begin a later row to work around a failure in the active row.
6. Update this roadmap in the same commit that closes or changes a work-package status.
7. Mark a row `COMPLETE` only after its exit gate is demonstrated. Change the immediately following
   row from `BLOCKED` to `READY` in that same status-update commit.
8. Do not reintroduce progressive fog or structured travel as a readiness dependency. Any future
   proposal for either requires a new product decision and a separate optional-feature design.

For music-provider implementation and final real-provider verification, browse current **official
provider documentation** because API capabilities, OAuth requirements, subscription rules, and
platform policies are time-sensitive. For other packages, prefer repository contracts and the
approved specifications; browse only where the task independently requires current external facts.

## 7. Current recovery note

As of the scope re-baseline on 2026-07-19:

- master delivery items 1–10 are implemented;
- world graph and faction clocks are implemented and remain in scope;
- the P0 browser-smoke plan is implemented, audited, and linked above;
- the music-provider feasibility spike is complete: YouTube is the viable baseline provider
  (Firefox/Linux, all six controls passed with visible official player); Spotify is CONDITIONAL
  (Policy III.6 synchronization prohibition);
- the corrected P3 rollable-tables package is implemented and verified;
- the P3 traps-and-hazards package is implemented and verified, including package round-trip,
  browser workflow, hostile-content, and player-safety coverage;
- the partial travel-core implementation, its V15 migration, tests, UI, execution plan, and
  authoritative travel design were removed before journey/watch work began;
- progressive fog and structured travel/hexcrawl automation are optional future capabilities and
  no longer block DM-only readiness;
- **Scope-rebaseline verification (2026-07-19):** clean focused settings/package/world/migration/
  documentation gate **10 suites / 119 tests / 0 failures / 0 errors / 0 skips**; fresh complete
  Maven run **213 suites / 1627 tests / 0 failures / 0 errors / 0 skips**. Fresh Flyway startup
  validates 14 migrations and ends at V14; the normal persistent database predates the removed V15.
- **Atmosphere and music verification (2026-07-19):** focused audio gate
  **38 suites / 442 tests / 0 failures / 0 errors / 0 skips**; clean complete Maven run
  **237 suites / 1971 tests / 0 failures / 2 errors / 0 skips** (2 CoreSessionLoopSmokeTest
  timeouts — environmental); fresh Flyway startup validates 16 migrations and ends at V16;
  V14-to-V16 upgrade preserves data.
- **Corrective audit (2026-07-19):** row 5 returned to `IN_PROGRESS` after fresh browser evidence
  reproduced 2 `CoreSessionLoopSmokeTest` errors and code review found incomplete ownership,
  dependency deletion, confirm/victory state, provider routing, visibility, and fake-provider
  acceptance behavior. Row 6 is blocked until the row-5 exit gate is genuinely green.

When conversation context is missing or compacted, resume from the first non-`COMPLETE` row in this
file and validate its status against the repository before acting.
