# Campaign Capabilities

| Capability | Status | Notes |
|---|---|---|
| P0 interaction reliability | `SUPPORTED` | Quick notes, destinations, browser guards, secure package assets, visible/retryable session mutations, correlated failures, and honest difficulty estimates are covered. |
| Campaign contract v1 | `SUPPORTED` | Schema, DTO, dry-run, import, references, and checked fixtures share one contract. |
| Campaign package v2 foundation | `SUPPORTED` | ZIP/JSON containers, stable keys, typed catalog, preview, migrations, staged assets, and atomic import are implemented. |
| Current persisted campaign state recovery | `SUPPORTED` | Default export/import preserves all currently persisted campaign meaning. |
| Combat log/dice recovery | `SUPPORTED` | Included by default and explicitly excludable. |
| Session cockpit | `SUPPORTED` | Durable start/resume/pause/end coordination across existing modules; deterministic reviewed session logs and v2 recovery. |
| Structured scene transitions | `UNSUPPORTED` | Delivery item 6; cockpit previous/next remains editorial order. |
| Structured quests/objectives | `UNSUPPORTED` | Delivery item 6. |
| Campaign-scoped non-statblock custom content | `UNSUPPORTED` | Delivery item 7. |
