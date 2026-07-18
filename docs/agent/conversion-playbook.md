# Agent Conversion Playbook

## Non-invention policy

The agent must never invent a DC, stat, map coordinate, source key, missing rule, table range,
table weight, or quantity expression merely to make validation pass. Record an unresolved
`SOURCE_ANNOTATION` or use a documented generic fallback. Prefer ERROR/WARNING visibility
over silent guesses.

### Rollable table non-invention rules

- **Ranges**: Never invent or adjust `rangeStart`/`rangeEnd` to fill gaps. If the source has
  gaps, create a `"Nothing unusual"` entry. If ranges overlap, emit a `SOURCE_ANNOTATION`.
- **Weights**: Never invent weights. If the source does not specify weights, use `RANGE` mode
  with equal ranges instead.
- **Quantity expressions**: Never invent `quantityExpression`. If the source says "2d4 goblins",
  use it verbatim. If no quantity is given, omit the field.
- **Roll expressions**: Use the source's die expression verbatim (e.g. `"1d12"`, `"1d100"`).
  Never substitute a different die.

For any invented data detected during dry-run, the agent must produce a structured repair plan
(see [dry-run-repair.md](dry-run-repair.md)) and request user approval before making changes.

## Steps

1. Inventory the source (title, edition, ruleset, pages, maps, assets, rights).
2. Extract without invention; keep page locators and confidence.
3. Build an intermediate source model (headings, boxed text, keyed locations, creatures, checks, treasure, transitions, assets).
4. Resolve catalog content:
   - Read `GET /api/v1/catalog` or `src/main/resources/catalog/srd-5.2-catalog.json`
   - Record `version` + `sha256` in converter metadata / provenance
5. Create campaign-scoped custom content for non-catalog material with provenance.
6. Assign stable package keys (`^[a-z0-9][a-z0-9._-]{0,99}$`) before wiring references.
7. Attach assets under `assets/**` with safe relative paths only.
8. Validate locally against `campaign-format-v2.schema.json` and `map-document-v2.schema.json` (also available at `GET /api/v1/schemas/{name}` when the app is running).
9. Dry-run via package preview API; iterate on structured problems until no ERROR remains.
10. Review WARNINGs; do not suppress ambiguity.
11. Import preview: verify counts, provenance, degraded fields, sample scenes.
12. Post-import smoke: open session cockpit, follow a branch, activate an encounter, present a handout, export and re-import.

## Local offline mode

When the app is not running, use classpath artifacts only:

- `src/main/resources/schemas/campaign-format-v2.schema.json`
- `src/main/resources/schemas/map-document-v2.schema.json`
- `src/main/resources/catalog/srd-5.2-catalog.json`
- `src/main/resources/agent/capability-manifest.json`
- `src/main/resources/agent/validation-error-catalog.json`
