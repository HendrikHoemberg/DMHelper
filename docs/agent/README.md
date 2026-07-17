# Agent Guide

This directory contains documentation for the DMHelper conversion agent.

## Contents

| File | Purpose |
|------|---------|
| [conversion-playbook.md](conversion-playbook.md) | Step-by-step conversion procedure, non-invention policy, and offline mode |
| [mapping-rules.md](mapping-rules.md) | JSON mapping rules with concrete schema snippets |
| [verification-checklist.md](verification-checklist.md) | Pre-import checklist covering format, capability, schema, dry-run, smoke |
| [dry-run-repair.md](dry-run-repair.md) | Debugging guide with executable examples and field-level fix patterns |

## Related artifacts

- Schema: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Schema: `src/main/resources/schemas/map-document-v2.schema.json`
- Catalog: `src/main/resources/catalog/srd-5.2-catalog.json`
- Capability manifest: `src/main/resources/agent/capability-manifest.json`
- Validation error catalog: `src/main/resources/agent/validation-error-catalog.json`
- Executable fixtures: `src/test/resources/docs-examples/`
