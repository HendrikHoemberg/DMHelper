# Task 2 report — One type scale, with a floor at the table

## Scope

Implemented Task 2 on HEAD 082be097 without changing database/entities or adding dependencies.

- Added --text-3xl: 4rem to tokens.css.
- Replaced absolute font sizes in cockpit.css, components.css, and book.css with scale tokens.
- Raised the runtime module control rule from --text-xs to --text-sm; reviewed secondary metadata remains the only runtime use of --text-xs.
- Added .group-count and .tracker-turns__active replacement rules in components.css.
- Removed font-size declarations from all template style attributes found by the contract scan.
- Added TypeScaleContractTest consuming the Task 1 CssRules interface.

## RED evidence

Command:

    ./mvnw -q test -Dtest=TypeScaleContractTest

Result: exit code 1; all three tests failed as intended:

- onlyTokensCssCarriesAnAbsoluteFontSize reported the absolute sizes in components.css, book.css, and cockpit.css.
- runtimeSurfacesStayAtOrAboveTextSmUnlessAllowlisted reported the unallowlisted runtime module action selector using --text-xs.
- noTemplateSetsAFontSizeInline reported the template files containing inline font sizes.

The failure was a contract failure, not a compilation or test-discovery error: Tests run: 3, Failures: 3, Errors: 0.

## GREEN evidence

Contract test:

    ./mvnw -q test -Dtest=TypeScaleContractTest

Result: exit code 0.

Neighboring suites required by the brief:

    ./mvnw -q test -Dtest=UiPolishContractTest,EncounterTemplateContractTest,CockpitRuntimeModuleContractTest

Result: exit code 0.

Additional self-review:

    git diff --check

Result: exit code 0.

The template inline-font scan is empty, the stylesheet absolute-size contract is green, and no unallowlisted runtime --text-xs declaration remains.

## Concerns

None identified within the task scope. The relative 0.85em note-code size and 3.1em drop cap remain intentionally relative as specified by the brief.

## Fix round 1 — review findings

### RED evidence

Updated TypeScaleContractTest with:

- an embedded <style> scan for raw px/rem/em font sizes;
- replacement-class assertions for calendar, player projection, tracker, and map-module typography.

Command:

    ./mvnw -q test -Dtest=TypeScaleContractTest

Before the fix, the command exited 1 with Tests run: 5, Failures: 2, Errors: 0:

- embedded raw sizes were found in templates/maps/editor.html and templates/player/view.html;
- the replacement-class assertion failed because the reviewed classes/rules did not yet exist.

### Fix

- Moved player embedded raw sizes to tokenized rules in player-projection.css, including the waiting icon’s --text-3xl.
- Added tokenized owning stylesheet rules and named classes for calendar, map editor/module, session chrome, encounter tracker, adventure, notes, sheet, and treasury typography.
- Removed the full inline style attribute from session/cockpit.html’s title.
- Removed the full inline style attribute from encounter tracker .group-count.
- Kept the prior relative note-code/drop-cap exceptions unchanged.

### GREEN evidence

Contract suite:

    ./mvnw -q test -Dtest=TypeScaleContractTest

Result: exit code 0.

Neighboring suites:

    ./mvnw -q test -Dtest=UiPolishContractTest,EncounterTemplateContractTest,CockpitRuntimeModuleContractTest

Result: exit code 0.

Additional checks:

    git diff --check
    rg -n 'style="[^"\\n]*font-size[^"\\n]*"' src/main/resources/templates
    rg -n 'font-size:\\s*(0\\.[0-9]+|[0-9]+\\.[0-9]+rem|[0-9]+px)' src/main/resources/templates/player/view.html src/main/resources/templates/maps/editor.html

All exited 0; the two scans returned no matches.

## Fix round 2 — duplicate tracker class attribute

### RED evidence

Added EncounterTemplateContractTest.trackerPrefillButtonHasOneMergedClassAttribute to reject duplicate literal class attributes and require the Prefill button’s merged class list.

Command:

    ./mvnw -q test -Dtest=EncounterTemplateContractTest#trackerPrefillButtonHasOneMergedClassAttribute

Before the fix: exit code 1, Tests run: 1, Failures: 1, Errors: 0. The duplicate-class assertion detected the two class attributes on the Prefill button.

### Fix

Merged the Prefill button classes into one valid attribute:

    class="btn btn-sm btn-ghost tracker-statblock-badge"

The assertion distinguishes literal class attributes from Alpine :class bindings.

### GREEN evidence

    ./mvnw -q test -Dtest=TypeScaleContractTest

Result: exit code 0.

    ./mvnw -q test -Dtest=EncounterTemplateContractTest

Result: exit code 0; Tests run: 15, Failures: 0, Errors: 0.

Direct template validation:

    if rg --pcre2 -n -U '<[^>]*(?<!:)class="[^"]*"[^>]*(?<!:)class="[^"]*"[^>]*>' src/main/resources/templates/encounter/_tracker.html; then exit 1; else echo 'no duplicate class attributes in tracker'; fi
    rg -n 'class="btn btn-sm btn-ghost tracker-statblock-badge"' src/main/resources/templates/encounter/_tracker.html
    git diff --check

Result: exit code 0; no duplicate class attributes, the merged Prefill class is present at line 509, and the diff is clean.
