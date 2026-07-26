# Task 4 report: Combat values legible at arm's length

## Status

Implemented the combat legibility contract, tokenized tabular numeral rules, null-safe `hpLabel(c)`, and the DM-only current/max HP tracker readout.

## RED evidence

Command:

```text
./mvnw -q test -Dtest=CombatLegibilityContractTest
```

Result: failed as expected with 3 failures and 0 errors. The failures identified the missing `.combatant-hp` rule, missing tracker readout, and missing `hpLabel(c)` implementation.

## GREEN evidence

Command:

```text
./mvnw -q test -Dtest=CombatLegibilityContractTest
```

Result: exit 0.

## Neighboring suite evidence

Commands:

```text
./mvnw -q test -Dtest=EncounterTemplateContractTest,TypeScaleContractTest
./mvnw -q test -Dtest=ScreenSafetyCoverageTest,DmSensitiveFieldCoverageTest
```

Result: both commands exited 0. Screen safety remains enforced by `x-show="!tableSafe"` on the new numeric readout, while existing semantic warning/danger colors are preserved.

## Changes

- Enlarged `.init-badge` to the specified dimensions and retained its existing semantic colors.
- Added `.combatant-hp` with `--text-base`, mono/tabular numerals, and bloodied/defeated semantic colors.
- Raised `.hp-display` and `.hp-delta-input, .detail-hp input` to the `--text-base` floor with tabular numerals.
- Added `hpLabel(c)` returning `—` for missing current HP, current-only values without a maximum, and `current/max` otherwise.
- Added the DM-only `.combatant-hp u-num` tracker readout bound to `hpLabel(c)`.
- Added no migration, entity, or dependency changes.

## Concerns

- The contract test intentionally verifies source/template contracts rather than browser pixel distance; Task 14's rehearsal remains the runtime visual check.
- The focused Spring test emitted existing framework/dependency warnings, but completed successfully.
