# Atmosphere & Music

DM-side streaming music is the only feature that requires an internet connection and, depending
on the provider, an external account. Players never receive audio data, player devices stay silent,
and the player UI carries no audio controls or indicators.

Provider specifics are documented in
[Music Provider Feasibility](../architecture/music-provider-feasibility.md) — this chapter covers
the generic behavior and configuration surface.

## Configuring a Provider

The provider is set under **Settings → Music**. The flow is:

1. Select a provider from the dropdown (or leave **None** for offline operation).
2. If the provider requires authentication, a local OAuth popup opens in the system browser.
3. On success, the refresh token is stored in the local data directory — never transmitted to
   another device, never included in packages or exports.
4. The **Disconnect** button appears; one click revokes the token and returns to the unconfigured
   state.

While unconfigured or offline the status reads **Music unavailable — configure a provider in
Settings**. No audio feature is accessible, and no network requests are made.

## Building a Cue Library

An `AudioCue` is a reference to a provider playlist or track together with cached display
metadata (label, provider icon, duration hint). Cues belong to one of seven categories:

| Category | Typical use |
|----------|-------------|
| `AMBIENT` | Environmental background (wind, rain, dungeon drip) |
| `EXPLORATION` | Light travel or social pacing |
| `TENSION` | Suspenseful build-up |
| `COMBAT` | Fight pacing |
| `TRIUMPH` | Victory or resolution |
| `SORROW` | Loss or melancholy |
| `CUSTOM` | User-defined label outside the above |

Every cue carries a **transition preference** — `CROSSFADE` (overlapping blend) or `CUT`
(instant swap) — and an optional **volume hint** (0.0–1.0) that the cockpit applies as a ceiling.

Cues are created and managed from **Library → Music Cues**. A search-as-you-type picker queries
the provider's catalogue; selecting a result creates the cue locally. Cues with a deleted or
unreachable provider reference show a stale indicator but remain editable.

## Assigning Cues

Cues are resolved hierarchically — each level overrides the one below:

- **Campaign default** — set on the campaign detail page; played when no more specific cue applies.
- **Location cue** — attached to a published map (pins or map metadata).
- **Scene cue** — attached to a scene; activated when the scene becomes active in the cockpit.
- **Encounter combat cue** — an encounter can designate a combat cue (played while initiative is
  active) and an optional **victory cue** with a configurable play duration (seconds; default 5). On
  encounter resolution the victory cue plays for its duration before the previous non-combat cue
  resumes.

All assignments are optional. Unassigned levels fall through the priority stack to silence.

## Runtime Switching

When the cockpit is live, audio follows this priority stack (highest first):

1. **Manual override** — DM picks a cue from the widget; plays until stopped or another override.
2. **Combat cue** — active encounter's combat cue while initiative runs.
3. **Victory cue** — on encounter resolve, plays for configured seconds, then drops to next live
   cue.
4. **Scene cue** — active scene's assigned cue.
5. **Location cue** — current map's assigned cue.
6. **Campaign default** — fallback cue.
7. **Silence** — no cue configured at any level.

Switching is automatic: entering a scene → scene cue (or fade-to-silence); starting an encounter
→ combat cue crossfade (respecting each cue's transition preference). Transitions also fire on
chapter, adventure, scene, and encounter rail clicks.

A per-campaign **confirm-mode** setting (default off) prompts the DM before any automatic switch.
The per-session **mute** toggle in the cockpit widget silences all output without changing assignments.

## Cockpit Widget

The atmosphere widget lives in the cockpit toolbar:

- **Now-playing** — label and provider icon of the active cue, or "Silence".
- **Play / Pause** — toggles playback without losing the active cue.
- **Skip** — jumps to the next track in a playlist cue; no-op for single-track cues.
- **Volume slider** — adjusts output (where the provider supports it; otherwise hidden).
- **Active source label** — e.g. "Scene: Dungeon Echo" or "Override: Custom Playlist".
- **Manual override picker** — opens the cue library to select a temporary cue.
- **Mute** — instant silence with a single click; unmute resumes the previous cue/state.
- **Keyboard** — all widget actions have keyboard shortcuts shown on hover / tooltip.

If the provider requires an active playback device (e.g. a speaker group or browser tab), the
widget surfaces a visible player that stays at or above the provider's minimum display size
during active playback. This player is not controllable from the player view.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| Widget shows error with Retry | Provider outage or network interruption |
| "No active device" | Provider requires a device target; open device picker or ensure a device is online |
| "Authentication expired" | Refresh token revoked or expired; use **Disconnect** then re-authorize in Settings |
| "Rate limited" | Provider API quota exceeded; wait and retry |
| Cue shows stale icon | Provider playlist / track was deleted; re-assign or remove the cue |
| Mute does not persist | Mute is per-session; it resets on next cockpit start |

All errors are surfaced as visible, actionable widget messages with a **Retry** button. No audio
error blocks any other session feature — maps, encounters, chat, dice, and scene navigation
continue unaffected.

## What Never Leaves the App

- **No audio content** is stored, cached, or exported by DMHelper. All streaming happens
  provider-side; the app holds only references and metadata.
- **No credentials** are included in campaign packages, exports, backups, or logs.
- **No audio data** is sent to player devices or included in WebSocket state.
- **No audio UI** appears on the player view or anywhere outside the DM cockpit.
