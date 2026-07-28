# Session Encounter UX Design

## Goal

Make the session encounter view faithfully display authored maps and replace fragile
browser-native interactions with predictable, accessible application UI.

## Runtime map parity

The DM battle map and player projection must render the same persisted map document:
background image geometry, custom terrain, generated primitives, and authored shapes.
The DM view respects layer `visible`; the player view additionally excludes layers
whose `playerVisible` is false. Background overflow is clipped to the authoritative
map rectangle. Tokens and DM-only threat pins remain runtime overlays.

Shared, dependency-light drawing helpers in `map/runtime-renderer.js` own document
layer filtering and Konva node creation so DM and player rendering cannot drift.

## Token workflow

`+ Token` opens an application dialog rather than browser prompts. It captures name,
kind, size, HP, color, hidden state, and placement. Placement defaults to the visible
map center, snaps in grid mode, and is clamped inside map bounds. Cancelling creates
nothing. Submission shows a busy state and keeps the dialog open on failure.

Token deletion and encounter ending use application dialogs. Adding the party is
idempotent: party members already represented by a token on the map are preserved,
and only missing active members are added.

## Encounter and cockpit navigation

Activating a planned encounter also switches the session workspace to its linked map.
Encounters without a map leave the current workspace map unchanged.

The cockpit top bar exposes **Leave cockpit**, returning to the campaign dashboard
without changing the session lifecycle. **Review & Complete** remains the only normal
way to finish a running session.

## Verification

Browser/controller/service tests cover runtime image rendering, player visibility,
token-dialog cancellation/submission, party deduplication, linked-map activation,
destructive confirmations, and leave navigation.

