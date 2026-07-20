# Live Music Provider Exercise — YouTube (DM surface)

**Environment:** Firefox/Linux, official YouTube IFrame API, DM cockpit only.
**Precondition:** app running via the normal cockpit entry point; a campaign with ≥2 audio cues whose `providerReference` points at real, playable YouTube content.

## Controls (all must pass)
- [ ] Assign a cue and start playback from the cockpit widget; official player visible at 480×270.
- [ ] Pause and resume.
- [ ] Switch to a second cue (scene/combat change) and confirm the track changes.
- [ ] Volume/mute from the widget.
- [ ] Victory/override cue takes priority, then expires back to the prior cue.
- [ ] Per-session mute silences playback without unassigning cues.

## Runtime-policy checks
- [ ] Scripted playback is gated on document visibility and >50% intersection (no hidden autoplay).
- [ ] Playback is user-gesture initiated.
- [ ] The IFrame API is loaded only on the DM surface (open the player view; confirm no player/embed request).

## Provider-failure isolation
- [ ] Disconnect network mid-session: the rest of the cockpit (scene, tracker, notes, presentation) stays fully usable; the widget shows a bounded-retry/degraded state and never blocks the session.
- [ ] Reconnect: playback can be resumed manually.

## Result
- Date/browser/OS:
- All controls: PASS / FAIL (detail)
- Policy checks: PASS / FAIL (detail)
- Failure isolation: PASS / FAIL (detail)
- Blocking observations:
