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
