# Rollable Tables Corrective Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing rollable-table slice functionally and evidentially faithful to its approved specification.

**Architecture:** Preserve the working table aggregate and resolver, introduce explicit web DTO boundaries, centralize campaign-aware validation, reuse one roll/draft UI component across detail and cockpit, and make package validation enforce the same structural invariants as runtime authoring. Every reported defect receives a failing regression test before its production fix.

**Tech Stack:** Java 25+, Spring Boot 4.1, Spring MVC, Spring Data JPA, Flyway, Jackson 3, Thymeleaf, Alpine.js, Playwright, Maven, JUnit 5, AssertJ, MockMvc, JSON Schema draft 2020-12.

## Global Constraints

- The authority order remains the canonical roadmap, approved atmosphere design, corrective design, and original implementation plan.
- Table definitions and scene/location links are persistent-exported; roll logs and draft state remain persistent-local and package-excluded.
- Player projections must contain no table definitions, references, results, or drafts.
- Consequences remain explicit and atomic; rolling never mutates encounter, treasury, scene, calendar, or player state.
- Campaign package format remains version 2 and `rollableTables` remains optional for older packages.
- Stable keys match `^[a-z0-9][a-z0-9._-]{0,99}$`.
- Use `-DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables-corrective` for verification.
- Preserve unrelated user changes and use tests before every production change.

---

### Task 0: Reopen the work package honestly

**Files:**
- Modify: `docs/superpowers/dm-only-readiness-roadmap.md`
- Modify: `docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md`

**Interfaces:**
- Consumes: corrective design approval.
- Produces: row 3 `IN_PROGRESS`, row 4 `BLOCKED`, atmosphere items 1–2 `IN_PROGRESS`.

- [ ] Change only the status fields and recovery note; preserve the prior evidence as historical context and list the corrective gate.
- [ ] Run `git diff --check` and inspect the roadmap diff.
- [ ] Commit with `docs(roadmap): reopen rollable-table correction`.

### Task 1: Stable API DTO boundary

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableResponse.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableWebMapper.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableApiController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableApiControllerTest.java`

**Interfaces:**
- Produces: `RollableTableResponse from(RollableTable)` with immutable ordered entry/reference DTOs.

- [ ] Add a MockMvc regression creating a table with one entry/reference and assert `201`, finite JSON, `id`, and ordered `entries`; verify RED with `RollableTableApiControllerTest`.
- [ ] Add mapper records and return them from create/update/clone/promote instead of entities.
- [ ] Run the web test GREEN and add a direct Jackson serialization regression.
- [ ] Commit with `fix(tables): return stable web DTOs`.

### Task 2: Campaign-aware authoring validation

**Files:**
- Modify: `RollableTableValidator.java`, `RollableTableService.java`, `TableReferenceResolver.java`
- Modify: `RollableTableRepository.java`
- Modify tests: `RollableTableValidatorTest.java`, `RollableTableServiceTest.java`

**Interfaces:**
- Produces: `validate(write,currentTableId,campaignIdOrNull)` and content-type-aware `isVisibleToScope`.

- [ ] Add RED tests for missing/blank required fields, invalid stable keys, duplicate entry keys, empty entries, range quantity errors, exact lower/upper bounds, negative/zero dice bounds, weighted supplied-expression normalization, total overflow, cross-campaign refs, missing campaign ID, and source-key collisions.
- [ ] Pass campaign scope from create/update; require the campaign when supplied; invoke `CustomContentSupport.assertAvailableSourceKey` for create/update/clone.
- [ ] Validate all entry invariants in one shared path and canonicalize weighted expressions unconditionally during persistence.
- [ ] Replace table-only promotion visibility with type-aware resolution for every ENTITY reference.
- [ ] Run service tests GREEN and commit `fix(tables): enforce authoring and scope invariants`.

### Task 3: Visible library and complete authoring controls

**Files:**
- Modify controllers/templates/scripts under `rollabletable/web`, `templates/rollable-table`, and `static/js/rollable-table-editor.js`
- Modify tests: controller/template/API tests.

**Interfaces:**
- Produces: visible-table queries, reference-option DTOs with safe destinations, functional reference picker, management controls.

- [ ] Add RED tests asserting campaign lists contain same-campaign + global/SRD but not other-campaign tables; reference options follow the same rule; list/detail contain create/clone/promote/delete controls.
- [ ] Add a JavaScript contract test that rejects an empty `showReferencePicker` and asserts option fetch/add behavior.
- [ ] Implement visible repository query/service, allowed target type selector, search field, option fetch, add/remove reference state, and per-path validation rendering.
- [ ] Add dependency-impact confirmation before delete; wire clone and promote controls with visible success/error states.
- [ ] Run web/template tests GREEN and commit `fix(tables): complete DM authoring workflows`.

### Task 4: Shared standalone and cockpit roll/draft UI

**Files:**
- Modify: `rollable-table-roll.js`, `_roll-panel.html`, draft fragments, `detail.html`
- Modify: `session/cockpit.html`, `_linked-tables.html`, `session-cockpit.js`, shared dice history templates
- Modify tests: template contracts and `CoreSessionLoopSmokeTest`.

**Interfaces:**
- Produces: one roll component initialized with `{tableId,campaignId,compact}`, recursive result rendering, retained `logId`, `table-history-refresh` event.

- [ ] Add RED template/browser assertions for campaign context, visible grouped outcome, nested result, retained log ID, history refresh, and cockpit pending draft.
- [ ] Refactor component state so roll and draft share one Alpine scope; include the component on both detail and cockpit.
- [ ] Make story-rail direct Roll execute once and show result; make top-bar picker select/open the configurable panel.
- [ ] Render reference URLs by content type rather than routing every reference to tables.
- [ ] Extend dice history to react to `table-history-refresh` and show explicit unavailable rows.
- [ ] Run template/controller tests GREEN and commit `fix(tables): wire rolling and drafts across DM surfaces`.

### Task 5: Correct atomic consequence transitions

**Files:**
- Modify: `RollableTableRollService.java`, `TableConsequenceService.java`, `TableRollLog.java`
- Modify tests: roll service, consequence service, management controller.

**Interfaces:**
- Produces: recursive outcome flattening, typed pending decision, matching transition guards, resolution audit fields.

- [ ] Add RED tests: category without applicable refs yields `NONE`; nested refs yield a draft; wrong confirm endpoint returns conflict; empty/foreign reviewed rows are rejected; confirm/discard set `resolvedAt`; confirm records created target IDs.
- [ ] Build drafts from the recursively flattened outcome tree and return null when applicable reference sets are empty.
- [ ] Require `draftType` to match each confirm method; preserve pessimistic locking and transaction rollback.
- [ ] Store stable JSON target IDs and timestamp for terminal transitions.
- [ ] Run consequence tests GREEN and commit `fix(tables): enforce consequence state machine`.

### Task 6: Package schema, dry-run, and runnable round-trip

**Files:**
- Modify: campaign v2 schema, semantic validator, rollable-table adapter/DTO tests, fixtures.
- Modify tests: manifest contract, DTO compatibility, semantic validator, adapter, complete round-trip.

**Interfaces:**
- Produces: shared structural validator mapping package DTOs to `/rollableTables/<n>` paths; canonical weighted import.

- [ ] Add RED schema tests for exact enums, required identity/entries, stable keys, and mutually exclusive RANGE/WEIGHTED fields.
- [ ] Add RED semantic tests for first/last dice bounds, inclusive overlap, original-index paths, missing result, bad ref type, depth, and a weighted fixture that can roll after import.
- [ ] Strengthen schema `$defs` and semantic validation without making `rollableTables` root-required.
- [ ] Normalize weighted import expression to `1d<sum>` and preserve referenced global dependency closure/provenance.
- [ ] Add behavioral post-import rolls to round-trip tests and run package gates GREEN.
- [ ] Commit `fix(tables): enforce runnable package fidelity`.

### Task 7: Search isolation, deletion safety, and documentation truth

**Files:**
- Modify: `CommandPaletteService.java`, location-link deletion handling, roll history DTO/UI.
- Modify: DM manual, package reference, capabilities, release notes, related tests.

**Interfaces:**
- Produces: deduplicated visible palette results and accurate operational documentation.

- [ ] Add RED palette tests excluding other-campaign tables while ranking same-campaign before global/SRD without duplicates.
- [ ] Add RED location-delete test proving a link from another campaign cannot be deleted.
- [ ] Implement visible queries and ownership checks; make unreadable history explicitly unavailable.
- [ ] Correct docs: referenced globals export through closure; logs/drafts survive table deletion; describe actual cockpit/editor behavior.
- [ ] Run search/docs/security tests GREEN and commit `fix(tables): align search safety and documentation`.

### Task 8: Real browser acceptance and closeout

**Files:**
- Modify: `CoreSessionLoopSmokeTest.java`
- Modify: roadmap and atmosphere specification only after gates pass.

**Interfaces:**
- Produces: real HTTP/DOM evidence for the accepted workflow and final roadmap handoff.

- [ ] Replace service-only table smoke steps with browser/API authoring through the same requests used by the editor, cockpit clicks, visible outcomes, discard/confirm dialogs, edited quantities, repository assertions, and failure-collector checks.
- [ ] Run focused corrective tests from the isolated home.
- [ ] Run independent `CoreSessionLoopSmokeTest` and inspect its XML for zero failures/skips.
- [ ] Run the complete non-browser Maven suite and count fresh reports.
- [ ] Perform bounded manual browser acceptance and record exact actions/results in the roadmap recovery note.
- [ ] Run `git status --short`, `git diff --check`, and inspect all documentation claims.
- [ ] Set row 3 `COMPLETE`, row 4 `READY`, and atmosphere items 1–2 `IMPLEMENTED` only now.
- [ ] Commit `docs(roadmap): close corrected rollable-table package`.

## Self-review checklist

- Every prior finding maps to Tasks 1–8.
- API DTO names, campaign-aware validator signature, roll component state, and consequence transition fields are consistent across tasks.
- No traps/fog/music/travel scope is introduced.
- Completion requires behavior, not merely structural round-trip or service-direct smoke tests.
