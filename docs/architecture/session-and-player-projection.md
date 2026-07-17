# Session and Player Projection

## Session Workspace Selection Order

When loading a session workspace (`SessionWorkspaceService.load()`), the active map is selected in this priority:

1. **Stored session** — If the session is open, restore the last workspace map.
2. **Active encounter map** — If there is an active (in-progress) encounter with a map, use it.
3. **Current scene map** — If the campaign has a current scene with a map, use it.
4. **Session plan** — If a session plan exists, use the map from the first resolved plan beat.
5. **Explicit/Chooser** — If a `requestedMapId` parameter is provided, use that map.
6. **Empty** — No map selected (`SelectionSource.NONE`).

The `SelectionSource` enum values: `ACTIVE_ENCOUNTER`, `CURRENT_SCENE`, `SESSION_PLAN`, `EXPLICIT_MAP`, `STORED_SESSION`, `NONE`.

## Player-Safe Projection Rules

The `PlayerSafeProjectionService` creates server-side projections that strip DM-only data before it reaches players. The key rules:

- **Tokens**: Hidden tokens (`token.isHidden()`) are filtered out entirely. Projected snapshots include only name, kind, color, position, size, dead state, and bloodied flag (computed from HP threshold — exact HP is never sent).
- **Map document**: Annotation layers (`LayerType.ANNOTATIONS`) are removed. Non-player-visible layers (`playerVisible == false`) are removed. Layers set to invisible are sent as hidden. Non-player-visible primitives are removed.
- **Combatants**: Hidden combatants are filtered out. Projected snapshots include only name, initiative, defeated state, active-turn flag, and condition names. Monster HP is never sent.
- **Handouts**: Only presented handouts are broadcast. DM-only handouts are never sent.

All filtering happens **server-side** before data enters the WebSocket (`TableStateWebSocketHandler`) or the player-safe REST endpoints. The player view is a pure read-only projection with no state of its own.
