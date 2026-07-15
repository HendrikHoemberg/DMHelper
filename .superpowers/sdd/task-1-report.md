# Task 1 Implementation Report

## Changed files

- `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`
  - Added the global text-like form-control selector contract.
- `src/main/resources/static/css/base.css`
  - Added zero-specificity `:where()` fallback styling and focus border styling for text-like inputs, `select`, and `textarea`.

## TDD RED

Command:

```text
./mvnw test -Dtest=UiPolishContractTest
```

Output:

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

The expected failure was that `base.css` did not contain the new `:where()` fallback block.

## TDD GREEN

Command:

```text
./mvnw test -Dtest=UiPolishContractTest
```

Output:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Final test

Command:

```text
./mvnw test
```

Output:

```text
Tests run: 390, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Self-review

- `git diff --check` passed.
- Only the task-owned CSS and contract test were changed before this report.
- The selector list excludes checkbox, radio, range, file, and color controls.
- The fallback contains no padding, width, or font-size declarations.
- `:where()` keeps specificity at zero, allowing component rules to override it.
- No dependencies, tokens, browser work, or unrelated files were changed.

## Concerns

- The supplied contract extracts from the fallback comment to the first `.navbar`; placing the block at the brief’s “before `::selection`” location would include pre-existing `fieldset` and global rules containing forbidden `padding:` and `width:` declarations. The unchanged contract therefore requires the fallback to be immediately before `.navbar` to isolate it.
- The full suite emitted pre-existing environment/tooling warnings, including Playwright missing host libraries and a surefire corrupted-channel warning; the suite still exited successfully.
- Browser inspection was not performed, as requested by the instruction to limit browser work to what is feasible here.

## Fix Report

### Review findings addressed

- Changed the fallback focus selector from `):focus` to `):where(:focus)`, keeping the focus border while giving the selector zero specificity so the existing `:focus-visible` keyboard ring is not overridden.
- Updated the contract to extract from the fallback comment directly through the adjacent `::selection` marker, and moved the fallback immediately after the `h1, h2` rule as specified by the plan.

### TDD RED

Command:

```text
./mvnw test -Dtest=UiPolishContractTest
```

Relevant output:

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expecting actual: 953 to be greater than: 1699
BUILD FAILURE
```

The contract failed because the existing fallback was still after `::selection`.

### Passing verification

Command:

```text
./mvnw test -Dtest=UiPolishContractTest
```

Relevant output:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
