# Cockpit DM-Run Review Fixes Design

## Goal

Close the three defects found while reviewing the cockpit DM-run fixes:

1. Never expose a server-rendered module body whose mode differs from the active preset.
2. Verify the runtime workspace-map picker in a real browser.
3. Prevent concurrent quick-roll submissions and verify that behavior in a real browser.

The changes remain local to the cockpit module controller, layout state rendering, dice roller,
and focused browser tests. No persistence model or schema changes are required.

## Module Mode Safety

### State

`[data-module-content]` will record:

- `data-module-mode`: the mode represented by its current body.
- `data-module-mode-mismatch`: `true` only while the body is known to represent a different
  mode from the one currently required.

During initial discovery, the controller copies the server-rendered fragment's
`data-module-mode` onto the content container. If the rendered and required modes differ, it
marks the mismatch and hides the module body immediately.

The same mismatch preparation runs when a later `cockpit:module-mode` event requests a
different mode. Modules whose content is deliberately preserved continue to follow their
existing preservation rule.

### Loading and failure behavior

Loading and error chrome remain visible outside `.cockpit-module__body`. While
`data-module-mode-mismatch="true"`:

- `loading` keeps the stale body hidden;
- `error` keeps the stale body hidden and exposes the existing error/Retry control;
- a successful matching response records the requested mode, clears the mismatch, and lets the
  normal `ready` state reveal the body;
- an empty matching response clears the mismatch and uses the existing empty-state chrome.

This preserves retry information without showing content that is structurally wrong for the
active preset.

## Runtime Map Picker Verification

Add a focused Playwright test using `CockpitInitialLoadFixtures`:

1. Create a campaign with a running session and workspace map.
2. Open the cockpit and switch to Combat so the Map module is visible.
3. Wait for the runtime Map module to finish loading.
4. Assert that `#runtimeMapPicker` has the workspace map ID as its value and the workspace map
   name as its selected text.

The test attaches `BrowserFailureCollector` through a local `guardedPage(context)` helper and
calls `assertNoFailures()` before closing its context.

## Quick-Roll Re-entrancy

`diceRoller.roll()` will return immediately when `loading` is already true. Every quick-roll
button will also bind `:disabled="loading"` so the UI communicates the same state enforced by
the component.

A focused browser test will retrieve the live Alpine dice component, set an expression, and
invoke `roll()` twice without waiting between calls. After both promises settle, exactly one
new history entry must exist for the campaign. This verifies the defensive method guard rather
than relying only on button markup or browser scheduling.

The existing source contract will also require the quick-roll controls to expose their loading
disable binding.

## Testing Strategy

Follow three independent red-green cycles:

1. Extend `CockpitModuleInitialLoadBrowserTest` to pause the inactive Party module request,
   activate its tab, and assert the stale Standard body remains hidden until the Compact response
   is released.
2. Add the focused runtime map-picker browser test and verify it passes against the real Alpine
   option rendering.
3. Add the concurrent-roll browser test, verify it creates two entries before the guard, then
   add the guard and disabled button bindings so it creates one.

After focused tests pass, run:

```bash
./mvnw test -Dtest='CockpitModuleInitialLoadBrowserTest,CockpitRuntimeMapPickerBrowserTest,DiceQuickRoll*Test'
./mvnw test
git diff --check
```

The six skips that predate the reviewed commit range are not changed by this work.

## Scope Boundaries

- Do not change the database schema.
- Do not prefetch every inactive cockpit module.
- Do not clear stale module content; retain it for retry bookkeeping while keeping it hidden.
- Do not revive or rewrite the disabled legacy complete-cockpit smoke flow.
- Do not change dice history semantics beyond rejecting a submission while one is already in
  flight.
