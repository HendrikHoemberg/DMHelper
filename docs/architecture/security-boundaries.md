# Security Boundaries

## PIN-Gated Routes

DMHelper uses a per-session PIN to protect DM routes from untrusted LAN devices. The PIN is a 6-character alphanumeric string (charset: `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`) generated at startup and printed to the terminal.

The `PinInterceptor` (registered in `WebMvcConfig`) blocks all requests **except** these excluded paths:

- `/player`, `/player/**` — Player-safe view pages
- `/ws/table`, `/ws/table/**` — Player-safe WebSocket table state
- `/dm/authenticate` — PIN entry endpoint
- `/css/**`, `/js/**`, `/vendor/**`, `/fonts/**` — Static resources
- `/api/v1/schemas/**` — JSON Schema downloads (for external generators)
- `/api/v1/table/state` — Current table state (player-safe)
- `/api/v1/catalog/**` — SRD catalog lookup
- `/api/v1/capabilities` — Capability manifest
- `/api/v1/validation-errors` — Validation error catalog
- `/error`, `/favicon.ico`

PIN is cookie-backed (`dm_pin` cookie, 365-day expiry). The interceptor implements rate limiting: slowdown after 5 failures, 30-second block after 10 failures (HTTP 429).

## Player-Safe Endpoints

The excluded paths above are deliberately kept open — they serve only player-safe projections (see [session-and-player-projection.md](session-and-player-projection.md)). This invariant is enforced by:

- Server-side stripping of DM-only fields before data leaves the application
- PlayerSafeProjectionService filtering hidden tokens, annotation layers, and DM-only entities
- Read-only WebSocket handler that accepts no client input

## WebSocket Table State Channel

The `/ws/table` WebSocket endpoint:

- Broadcasts `LiveTableState` JSON to all connected player view sessions
- Accepts no client messages (player view is read-only)
- Tokens, combatants, and map documents are projected through `PlayerSafeProjectionService` before broadcast
- DM-only fields (exact HP, hidden tokens, condition details) are never included in the serialized state

## Data Protection

- Editor documents (`MapDocument`) carry a `schemaVersion` field for forward migration on load
- Optimistic locking on game map edits (409 Conflict on stale versions)
- Rotating database backups on every app start
- Campaign export/import as escape hatch for data recovery
