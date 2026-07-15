# Task 3 report: Keyboard focus and muted-text contrast

## Changed files

- `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`
  - Added the exact focus-visible selector contract and numeric WCAG AA contrast
    checks for `#b3a88f` on `#17120c` and `#211a12`.
- `src/main/resources/static/css/base.css`
  - Replaced the broad `:focus-visible` rule with the required interactive-only
    selector and exact tokenized two-pixel ring declarations.
- `.superpowers/sdd/task-3-report.md`
  - This report.

`components.css`, templates, and `tokens.css` were not changed. The contrast
contract proves the muted token meets AA on both specified app backgrounds.

## RED evidence

After adding the two tests, ran:

```text
./mvnw test -Dtest=UiPolishContractTest
```

Result: 5 tests run, 1 failure, 0 errors. The expected failure was
`focusVisibleTargetsInteractiveElementsWithTokenizedRing`, because `base.css`
still contained the broad `:focus-visible` selector. The contrast test passed.

## GREEN evidence

Implemented only the required replacement rule:

```css
:where(a, button, input, select, textarea, [tabindex]):focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
  border-radius: var(--radius);
  transition: outline-offset var(--duration-micro) var(--ease-out);
}
```

The switch-specific adjacent-sibling rule remains unchanged:

```css
.switch-control input:focus-visible + .switch-track { outline: 2px solid var(--color-accent); outline-offset: 2px; }
```

Re-ran the focused contract: 5 tests run, 0 failures, 0 errors, 0 skipped.

## Opacity audit

Audited `components.css`, `base.css`, `book.css`, `cockpit.css`, and all
templates using:

```text
rg -n "opacity:\s*0\.[0-9]+|text-muted|color-text-muted" \
  src/main/resources/static/css \
  src/main/resources/templates
```

Reviewed targets `.book-meta`, `.card-meta`, `.statblock-meta`,
`.empty-state__desc`, `.appnav-label`, and `.appnav-link`: each already renders
with its direct color (or inherits `.text-muted`) and has no parent opacity.
No readable metadata required cleanup.

Retained intentional non-metadata opacity: inactive party members, defeated and
hidden combatants, animation pulse, the statblock decorative rule, disabled map
editor controls, collapsed-nav tooltip transition, loading/overlay/view
transitions, and DM-mode state effects.

## Final verification

- `./mvnw test -Dtest=UiPolishContractTest`: 5 tests, 0 failures/errors/skips.
- `git diff --check`: clean.
- `./mvnw test`: 394 tests, 0 failures/errors/skips.

The full suite emitted existing environment warnings: Flyway's H2 version note,
Mockito dynamic-agent warnings, and Playwright missing host browser libraries.
They did not cause test failures.

## Self-review

- Scoped the global focus selector to actual interactive elements exactly as
  required; no duplicate broad rule remains.
- Preserved the visible switch-track focus rule, preventing a double visible
  ring on the DM Mode control.
- Used only existing tokens and introduced no raw colors, dependencies,
  responsive changes, or unrelated refactors.
- Preserved Task 1 (`fbe21df`) and Task 2 (`0fb5428`) changes.

## Concerns

Manual browser keyboard checks for Campaign Home, Library filters/cards, and
the Add Member modal were not performed because the host is missing Playwright
browser libraries (`libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`,
`libxml2.so.2`, `libwebp.so.6`, and `libffi.so.7`). The browser smoke tests in
the full suite still passed.
