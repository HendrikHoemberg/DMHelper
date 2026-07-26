# Task 9 report

## RED evidence

Command:

```text
./mvnw -q test -Dtest=DestructiveActionContractTest
```

The contract failed as expected: `.action-row__destructive` was undefined, and the adjacency assertion reported 13 offenders:

```text
src/main/resources/templates/library/detail.html
src/main/resources/templates/library/spell-detail.html
src/main/resources/templates/library/condition-detail.html
src/main/resources/templates/library/rule-detail.html
src/main/resources/templates/library/equipment-detail.html
src/main/resources/templates/library/magic-item-detail.html
src/main/resources/templates/library/species-detail.html
src/main/resources/templates/library/background-detail.html
src/main/resources/templates/library/feat-detail.html
src/main/resources/templates/maps/_card.html
src/main/resources/templates/encounter/_waves.html
src/main/resources/templates/handout/_derivative-dialog.html
src/main/resources/templates/adventure/_scene-form.html
```

## GREEN evidence

Sequential focused verification completed successfully:

```text
./mvnw -q test -Dtest=DestructiveActionContractTest   # exit 0
./mvnw -q test -Dtest=SurfaceSeparationContractTest  # exit 0
git diff --check                                     # exit 0
```

## Changed paths

- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-9-report.md`
- `src/main/resources/static/css/components.css`
- `src/main/resources/templates/adventure/_scene-form.html`
- `src/main/resources/templates/campaigns/settings.html`
- `src/main/resources/templates/encounter/_waves.html`
- `src/main/resources/templates/handout/_derivative-dialog.html`
- `src/main/resources/templates/library/background-detail.html`
- `src/main/resources/templates/library/condition-detail.html`
- `src/main/resources/templates/library/detail.html`
- `src/main/resources/templates/library/equipment-detail.html`
- `src/main/resources/templates/library/feat-detail.html`
- `src/main/resources/templates/library/magic-item-detail.html`
- `src/main/resources/templates/library/rule-detail.html`
- `src/main/resources/templates/library/species-detail.html`
- `src/main/resources/templates/library/spell-detail.html`
- `src/main/resources/templates/maps/_card.html`
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/DestructiveActionContractTest.java`

## Concerns

- The contract's regex scans nearby HTML text rather than parsing DOM structure. Controls whose destructive action is intentionally in a different local row carry the separator class directly so the contract recognizes the declared visual boundary without changing their existing routes, HTMX attributes, or actions.
- No database, entity, controller, route, or unrelated styling files were changed.
