# Campaign Readiness Panel UX Design

## Goal

Turn the campaign readiness report from a long validation log into a compact preparation
workflow. A GM should be able to see how many blockers remain, understand what kind of work is
needed, open the relevant preparation surface, and distinguish a real repair from an explicitly
accepted risk.

The report evaluates the whole campaign, not only the next planned session. The heading therefore
uses **Campaign readiness**. Changing readiness computation to a planned-session subset is outside
this change.

## Information hierarchy

The blocked header reads **Not ready — N blockers**. Supporting copy explains that the GM can
resolve blockers or explicitly continue without the missing preparation. A ready report reads
**Ready** and retains a short explanation.

Blockers are grouped by `ReadinessCategory`. Each category is an expandable group with:

- a human-readable category label and blocker count;
- a short category-specific summary;
- individual blocker rows inside the group.

Groups are expanded by default so the existing actions remain discoverable, but category grouping
removes repeated issue wording from the top-level scan and gives the list a clear hierarchy.
Accepted choices and advisories remain in secondary disclosure sections and are collapsed by
default.

## Actions and language

Repair is the preferred action whenever a repair link exists. Its label describes the operation:

- map: **Add map**;
- encounter: **Prepare encounter**;
- statblock: **Review participants**;
- asset: **Review handout**;
- fallback: **Open preparation**.

Acceptance is always secondary and never receives primary styling merely because it is the first
row. Its label states the consequence:

- map: **Run without map**;
- encounter: **Run without encounter**;
- statblock: **Run without statblocks**;
- asset: **Accept safety risk**;
- fallback: **Accept blocker**.

Acceptance remains persisted and undoable through the existing readiness acknowledgement flow.
The action title and nearby explanatory text make clear that acceptance marks the campaign ready
without repairing the underlying omission.

## Layout and responsive behavior

The panel retains the existing semantic warning/success edge. Category headers use a compact
summary row, while individual items use a two-column content/action layout instead of reserving
separate columns for repetitive title and detail text. At narrow widths, actions wrap below the
content.

The badge is sentence case rather than uppercase status jargon. Counts are exposed in visible text,
not color alone. Native `details` and `summary` elements retain keyboard accessibility.

## Scope and compatibility

This change is limited to the readiness report view, small report grouping helpers, styles, and
their tests. It does not change blocker computation, acknowledgement persistence, import behavior,
or repair URL resolution. Existing HTMX replacement continues to replace the whole panel after an
action.

## Verification

Tests prove:

- blocked and ready summaries show the correct counts and language;
- blockers render in category groups;
- every blocker retains a repair action when available and an acceptance form;
- no arbitrary acceptance button has primary styling;
- category-specific action labels are present;
- accepted choices remain undoable;
- the focused campaign web tests and UI contract tests pass.
