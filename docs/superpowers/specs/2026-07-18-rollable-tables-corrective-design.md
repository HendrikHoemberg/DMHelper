# Rollable Tables Corrective Design

**Date:** 2026-07-18
**Status:** Approved
**Corrects:** `2026-07-18-p3-rollable-tables.md`
**Authority:** `2026-07-17-table-fidelity-and-atmosphere-design.md` and the canonical readiness roadmap

## 1. Purpose

Repair the implemented rollable-table slice so its actual DM workflows, validation boundaries,
package behavior, documentation, and acceptance evidence match the approved specification. Preserve
the working persistence, nested resolver, grouped evidence, scene/location model, package registry,
fixtures, player-safety gate, and performance gate.

## 2. Architecture

### 2.1 API boundary

Controllers return explicit immutable DTOs, never JPA entities. A table response contains table
metadata, ordered entry DTOs, ordered reference DTOs, ownership/provenance metadata needed by the
editor, and safe reference destinations. Bidirectional entity relationships remain persistence-only.

### 2.2 Authoring and visibility

The service validates with the destination campaign scope. Campaign-scoped content may reference
SRD, user-global, and same-campaign entities only. User-global tables may reference only SRD or
user-global entities. Missing campaign IDs fail instead of silently creating global content.
Source keys use the existing stable-key grammar and collision checks.

Range tables require a valid roll expression and exact contiguous inclusive coverage from its parsed
minimum through maximum. Weighted tables reject range fields, require positive weights, and always
store `1d<sum>` regardless of browser or package input. Every entry requires text or a reference;
quantity expressions validate in both modes.

### 2.3 DM UI

The library list provides create controls and visible scope filtering. Detail/editor pages provide
clone, promote, dependency-aware delete, and a functional reference picker constrained by campaign
visibility and allowed reference types.

Standalone and cockpit rolling use one reusable Alpine component. Both surfaces show ordered grouped
outcomes and nested results, refresh shared history, retain the roll log ID, and render pending
encounter/reward drafts. Cockpit direct roll remains one deliberate action; the top-bar picker opens
the roll panel so physical values, roll count, and duplicate policy remain available within two.

### 2.4 Consequences

A draft is `PENDING` only when the complete resolved outcome tree contains applicable typed
references. Confirmation requires the matching draft type, validates reviewed rows against the
stored result, applies one atomic transaction, and records resolved target IDs and timestamp.
Discard records resolution time without applying state. Nested consequence references participate in
preview and confirmation.

### 2.5 Package contract

The v2 schema uses closed mode-specific table-entry unions, exact category/address enums, stable keys,
and required identity/entry fields. Semantic dry-run shares table structural rules with runtime
authoring, preserves original JSON paths, validates allowed reference types, exact dice bounds,
cycles, and depth.

Imported weighted tables are normalized to their canonical expression. Campaign tables and referenced
user-global dependency closure remain exported; unrelated global content remains excluded. Roll logs
and draft state remain local operational evidence and survive table deletion through snapshots.

### 2.6 Documentation and roadmap

Documentation states that referenced user-global tables are copied into campaign packages with
preserved provenance, and that deletion preserves roll/draft snapshots. Roadmap row 3 is reopened
during correction and is closed only after all automated gates and a recorded bounded manual flow.

## 3. Error handling

- Validation failures return RFC 9457 problem details with stable `{code,path,message}` rows.
- Missing or cross-campaign targets return 404/structured validation failures as appropriate.
- Wrong draft transitions return 409 without mutation.
- Roll/UI failures remain visible and retryable through the shared action-failure mechanism.
- Unreadable historical roll rows render an explicit unavailable-history item.

## 4. Verification

Each correction begins with a failing regression test. Required closing evidence:

1. focused service, web, package, documentation, security, and performance tests;
2. a real Playwright flow covering editor reference selection, standalone/cockpit rolls, discard,
   encounter/reward confirmation, and visible grouped history;
3. independent `CoreSessionLoopSmokeTest`;
4. the complete non-browser Maven suite;
5. clean diff/worktree checks;
6. recorded bounded manual acceptance for create/edit/clone, both roll modes, physical and N rolls,
   linked cockpit access, consequence transitions, deletion impact, and export/reimport.

## 5. Scope guard

This correction does not add traps, hazards, fog, music, travel, player-controlled behavior, automatic
damage, automatic currency parsing, or copyrighted bundled table content.
