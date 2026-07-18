# Music-Provider Feasibility Decision

**Official sources checked:** 2026-07-18
**Decision owner:** DMHelper DM-only readiness program

## Decision

**Readiness provider:** PROOF_REQUIRED
**YouTube status:** ELIGIBLE_FOR_PROOF
**Spotify status:** CONDITIONAL

YouTube is the required baseline candidate. Spotify is an optional enhanced candidate and cannot
become a readiness dependency. No provider feature is implemented or advertised by this decision.

## Candidate Matrix

| Requirement | YouTube | Spotify |
|---|---|---|
| Public developer access | Public IFrame API | Public Web API in restricted development mode |
| Full-length DM-device playback | Embedded official player | Active official client or Connect device |
| Authentication | None for known public IDs | OAuth 2.0 Authorization Code with PKCE |
| Required account | None for public embeds | Premium owner and playback user |
| Play/pause/skip/volume/state | Supported by IFrame API | Supported by Player Web API |
| Automatic cue switch | After DM gesture, while player is sufficiently visible | API command, subject to provider policy |
| Main blocker | Visible-player and autoplay rules | Premium/dev limits and synchronization policy |

## Official Sources

### YouTube

- https://developers.google.com/youtube/iframe_api_reference
- https://developers.google.com/youtube/player_parameters
- https://developers.google.com/youtube/terms/required-minimum-functionality
- https://developers.google.com/youtube/terms/developer-policies
- https://developers.google.com/youtube/terms/revision-history

### Spotify

- https://developer.spotify.com/policy
- https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide
- https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow
- https://developer.spotify.com/documentation/web-api/tutorials/redirect_uri
- https://developer.spotify.com/documentation/web-api/tutorials/refreshing-tokens
- https://developer.spotify.com/documentation/web-api/concepts/scopes
- https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback
- https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback
- https://developer.spotify.com/documentation/web-api/concepts/rate-limits

## DM-Device Playback Proof

The paper assessment is complete. Real YouTube playback remains required before this decision can
advance the roadmap. Spotify playback is optional and cannot override a policy incompatibility.

## OAuth and Credential Storage

YouTube baseline playback of known public IDs uses no OAuth token or API key. Spotify would use
Authorization Code with PKCE, a 127.0.0.1 loopback callback, least-privilege playback scopes, an
in-memory access token, and an owner-only local refresh-token file.

## Capability Limits

The downstream SPI must declare capabilities instead of presenting every control for every
provider. Crossfade is unsupported by both baseline paths. YouTube requires a visible official
player. Spotify requires an active official device.

## Failure Modes

Audio failures remain bounded to the audio widget. Autoplay blocking, unavailable content,
provider authorization expiry, rate limiting, no active device, and network loss must be visible
and retryable without interrupting any session action.

## Account and Subscription Prerequisites

YouTube public embeds require no account for known public IDs. Spotify playback requires Premium;
development-mode ownership and user limits apply.

## Downstream Implementation Contract

No production contract is authorized until the real-provider proof is recorded. The future row 7
plan must use a provider SPI, deterministic fake-provider tests, local credential clearing, and a
strictly DM-only projection boundary.

## Roadmap Outcome

Row 2 remains READY until YouTube is proven on the DM device and this record names a viable
readiness provider. This paper assessment alone does not advance row 3.
