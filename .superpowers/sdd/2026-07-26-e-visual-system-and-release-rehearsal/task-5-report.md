# Task 5 report — Gold means focus, selection or primary action

## Result

Implemented the gold-accent contract. Ambient hover and grouping borders now use `--color-border-strong`; gold remains on focus, selection, primary actions, semantic toast rails, and reviewed in-world identity surfaces. Added the explicit selected-card gold rule.

No migration, entity, dependency, or template changes were made.

## TDD evidence

### RED

Command:

```text
./mvnw -q test -Dtest=GoldAccentContractTest
```

Result: expected failure. All 3 tests failed: the neutral token was undefined, `.card:hover` still used `var(--color-gold-soft)`, and the contract reported the unearned gold-border inventory including cards, generic/ghost buttons, toast outer border, and other ambient/decorative surfaces.

### GREEN

Command:

```text
./mvnw -q test -Dtest=GoldAccentContractTest
```

Result: pass; all 3 tests passed.

Neighboring visual contracts:

```text
./mvnw -q test -Dtest=GoldAccentContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,CombatLegibilityContractTest,UiPolishContractTest,InteractionFailureContractTest
```

Result: pass.

Full suite:

```text
./mvnw -q test
```

Result: 2,565 tests, 0 failures, 1 error, 6 skipped. The error is `GameMapControllerTest.shouldRenderEditorPage`, caused by pre-existing duplicate `class` markup in `src/main/resources/templates/maps/editor.html:362`; this task did not modify that template.

## Changes

- Added `GoldAccentContractTest` using `CssRules`.
- Added `--color-border-strong: #564936` beside `--color-border`.
- Replaced ambient gold hover/decorative borders in the base, component, book, and cockpit stylesheets with the neutral hairline.
- Preserved gold on primary audio action, selected cards, focus/selection states, toast severity rail, and material identity surfaces.
- Documented the toast exception because the supplied contract scans `border-left` while the brief explicitly preserves its semantic colored rail.

## Concerns

- The full suite remains red because of the unrelated duplicate `class` attribute in `templates/maps/editor.html:362`.
- The task brief’s supplied test treats the toast’s semantic `border-left` as a gold border; `.toast` is therefore recorded as a reviewed semantic exception so the behavior-preserving toast severity rail remains intact.

## Review fix round 1

### RED

Strengthened `GoldAccentContractTest` before changing production CSS:

- selector lists are split only at top-level commas, so `:where(...)` argument lists remain intact while each real selector is evaluated independently;
- `border-top-color`, `border-right-color`, `border-bottom-color`, and `border-left-color` are now included.

Command:

```text
./mvnw -q test -Dtest=GoldAccentContractTest
```

Result: expected failure. The strengthened contract identified five actual offenders:

```text
components.css { .statblock-card:hover }
components.css { .sheet-toc a:hover }
components.css { .structured-read-aloud }
cockpit-layout.css { .cockpit-arrange-menu button:hover }
cockpit-layout.css { .cockpit-dialog [data-add-module]:hover }
```

### GREEN

Neutralized the five unearned borders, split mixed hover/focus rules so focus retains gold, and recorded structured read-aloud as a reviewed in-world identity surface.

Commands:

```text
./mvnw -q test -Dtest=GoldAccentContractTest
./mvnw -q test -Dtest=GoldAccentContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,CombatLegibilityContractTest,UiPolishContractTest,InteractionFailureContractTest
```

Result: both commands passed with exit code 0.
