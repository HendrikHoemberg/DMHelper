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

### Policy Compliance Assessment

| Requirement | Status | Rationale |
|---|---|---|
| Synchronization with visual media | CONDITIONAL | Policy III.6 prohibits synchronizing sound recordings with visual media including "slideshow, video, or similar content." DMHelper's automatic scene-triggered cue changes while maps and story content are visible could be interpreted as synchronization. The language is broad and does not clearly exclude tabletop use. |
| Non-interactive broadcast | SUPPORTED | Policy III.4 prohibits non-interactive internet webcasting to multiple simultaneous listeners. DMHelper plays on a single DM device only. |
| Personal/non-commercial use | SUPPORTED | Policy III.10 requires personal, non-commercial use. DMHelper is a personal DM tool, not a business-facing product. |
| Streaming SDA monetization | SUPPORTED | Policy IV.2 prohibits commercial streaming SDA monetization. DMHelper is non-commercial and requires Premium. |
| Metadata, artwork, attribution | CONDITIONAL | Policy II.5 requires showing relevant cover art and metadata during playback. DMHelper must implement metadata display for Spotify content. |
| Development-mode distribution | SUPPORTED | February 2026 Dev Mode limits (1 client ID, 5 users, Premium owner) are operational constraints DMHelper can satisfy. |

**Overall Policy Status: CONDITIONAL.** `Spotify status` remains CONDITIONAL because Policy III.6 (synchronization) is a blocking concern. Row 7 must not ship Spotify without later written clearance.

## DM-Device Playback Proof

The paper assessment is complete. Real YouTube playback remains required before this decision can
advance the roadmap. Spotify playback is optional and cannot override a policy incompatibility.

### Spotify: NOT_EXERCISED

Functional proof was not executed. Prerequisites (Spotify Premium account, developer application
with registered 127.0.0.1 loopback redirect, active official client or Connect device) are not
available in this environment. The policy gate in the Policy Compliance Assessment above
determines the final Spotify status.

## Spotify Provider Contract

providerId=SPOTIFY
authMode=OAUTH_PKCE
redirectUri=http://127.0.0.1:8081/settings/audio/oauth/spotify/callback
scopes=user-read-playback-state user-modify-playback-state
accessTokenStorage=memory only
refreshTokenPath=~/.dmhelper/providers/spotify/credentials.json
providerDirectoryPermissions=0700 where POSIX permissions are available
credentialFilePermissions=0600 where POSIX permissions are available
clientSecret=not used
clearCredentials=delete refresh-token file and in-memory access token
exportBehavior=exclude all credentials and transient playback state
logBehavior=never log tokens, authorization codes, provider responses containing tokens, or device ids

Platform handling: on non-POSIX systems, use an owner-only application-data location and fail
closed with an actionable settings error if owner-only storage cannot be established.

## Provider Capabilities

supportsSearch=true
supportsKnownTrack=true
supportsKnownPlaylist=true
supportsPlayPause=true
supportsSkip=true
supportsVolume=device dependent
supportsQueue=true
supportsCrossfade=false
requiresVisiblePlayer=false
requiresInitialUserGesture=false
requiresActiveProviderDevice=true
requiresPremium=true
developmentModeUserLimit=5
developmentModeClientIdsPerDeveloper=1
rateLimitHandling=honor Retry-After on 429

## Provider Failure Mapping

| Failure category | Spotify trigger |
|---|---|
| AUTH_REQUIRED | Expired or revoked authorization; six-month refresh-token expiry requiring reauthorization |
| PREMIUM_REQUIRED | Product or account rejection when the user does not have an active Premium subscription |
| NO_ACTIVE_DEVICE | No active client or Connect device available for playback commands |
| CONTENT_UNAVAILABLE | Deleted, market-restricted, or non-playable track or episode references |
| RATE_LIMITED | 429 response; honor Retry-After header |
| PROVIDER_OFFLINE | Network error, 5xx response, or request timeout |
| POLICY_DISABLED | Spotify is not authorized for this product behavior (CONDITIONAL status) |

## Account and Subscription Prerequisites

YouTube public embeds require no account for known public IDs. Spotify playback requires the
following:
- Owner must have an active Premium subscription
- Development mode limits: 1 client ID per developer, 5 users per app
- Spotify client or Connect device must be active on the DM machine

## Downstream Implementation Contract

No production contract is authorized until the real-provider proof is recorded. The future row 7
plan must use a provider SPI, deterministic fake-provider tests, local credential clearing, and a
strictly DM-only projection boundary.

## Roadmap Outcome

Row 2 remains READY until YouTube is proven on the DM device and this record names a viable
readiness provider. This paper assessment alone does not advance row 3.
