# Music-Provider Feasibility Decision

**Official sources checked:** 2026-07-18
**Decision owner:** DMHelper DM-only readiness program

## Decision

**Readiness provider:** YOUTUBE
**YouTube status:** VIABLE
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
- https://developer.spotify.com/documentation/web-api/concepts/redirect_uri
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

### YouTube: VIABLE

**Manual proof confirmation:** The user confirmed on 2026-07-18 that the audible DM-device proof
was approved immediately before the follow-up audit. The original executor did not preserve a
reliable clock time.
**Browser:** Firefox on Linux (exact browser and OS versions were not recorded by the original executor)
**Player dimensions:** 480 by 270 CSS pixels, visible and unobscured
**Official sample video:** M7lc1UVf-VE
**Official sample playlist:** PLC77007E23FF423C6

All six controls passed after an explicit DM gesture (Enable audio):

1. Enable audio — audible output from the DM device
2. Pause — audio stopped
3. Resume — audio continued from paused position
4. Volume 25% — volume reduced and persisted
5. Switch cue context — official sample playlist replaced initial video context without page navigation
6. Next — playlist advanced to the next track

Autoplay was initially blocked as expected before the Enable audio gesture. Browser console
contained YouTube-internal warnings (feature policy, SameSite cookie, Firefox fingerprinting
protection, unreachable code in YouTube scripts, CORS-blocked ad tracking) — none were player
errors or CSP/Referer failures.

The official player remained visible, unobscured, with intact branding and controls throughout
scripted playback. No YouTube reference, state, or script was exposed outside the proof page.

### YouTube Provider Capabilities

providerId=YOUTUBE
authMode=NONE
supportsKnownVideo=true
supportsKnownPlaylist=true
supportsSearch=false
supportsPlayPause=true
supportsSkip=true
supportsVolume=true
supportsQueue=true
supportsCrossfade=false
requiresVisiblePlayer=true
requiresInitialUserGesture=true
playsOnDmDevice=true

### Spotify Functional Proof: NOT EXECUTED

Functional proof was not executed. Prerequisites (Spotify Premium account, developer application
with registered 127.0.0.1 loopback redirect, active official client or Connect device) are not
available in this environment. The policy gate in the Policy Compliance Assessment above
determines the final Spotify status, which remains CONDITIONAL.

## OAuth and Credential Storage

YouTube baseline playback of known public IDs uses no OAuth token or API key and stores no
credentials. Spotify would use Authorization Code with PKCE, a 127.0.0.1 loopback callback,
least-privilege playback scopes, an in-memory access token, and an owner-only local refresh-token
file as specified below.

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

## Capability Limits

The future provider SPI must declare capabilities instead of presenting unsupported controls.
YouTube requires its official player to remain visible and an initial DM gesture; it does not
provide baseline search or crossfade. Spotify requires Premium and an active official playback
device; it does not provide crossfade. The detailed Spotify declaration is:

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

## Failure Modes

Audio failures remain bounded to the audio widget. They must be visible and retryable without
interrupting scene navigation, encounters, presentation, or any other session action.

| Failure category | YouTube trigger | Spotify trigger |
|---|---|---|
| AUTOPLAY_BLOCKED | Browser rejects scripted playback before a DM gesture; show the enable-audio prompt | Not applicable |
| AUTH_REQUIRED | Not applicable for public-ID baseline playback | Expired or revoked authorization; six-month refresh-token expiry requiring reauthorization |
| PREMIUM_REQUIRED | Not applicable | Product or account rejection without an active Premium subscription |
| NO_ACTIVE_DEVICE | Not applicable; playback occurs in the visible browser player | No active client or Connect device available for playback commands |
| CONTENT_UNAVAILABLE | Deleted, embedding-disabled, age-restricted, region-restricted, or otherwise unplayable video/playlist | Deleted, market-restricted, or non-playable track or episode reference |
| RATE_LIMITED | Provider throttling or temporary embed-service rejection | 429 response; honor Retry-After |
| PROVIDER_OFFLINE | IFrame API load failure, network error, player error, or timeout | Network error, 5xx response, or request timeout |
| POLICY_DISABLED | Current YouTube terms no longer permit the required visible-player behavior | Spotify remains unauthorized while its status is CONDITIONAL |

## Provider Interfaces (Planned)

These are the planned provider-neutral interfaces for the roadmap row 5 atmosphere/music implementation.
No Java code is committed by this spike.

enum AudioProviderId { YOUTUBE, SPOTIFY }
enum AudioAuthMode { NONE, OAUTH_PKCE }

record AudioProviderCapabilities(
        boolean supportsSearch,
        boolean supportsPlayPause,
        boolean supportsSkip,
        boolean supportsVolume,
        boolean supportsQueue,
        boolean supportsCrossfade,
        boolean requiresVisiblePlayer,
        boolean requiresInitialUserGesture,
        boolean requiresActiveProviderDevice) {}

interface AudioProviderClient {
    AudioProviderId id();
    AudioAuthMode authMode();
    AudioProviderCapabilities capabilities();
    ProviderReference parseReference(String input);
    ProviderMetadata resolveMetadata(ProviderReference reference);
    PlaybackState currentState();
    PlaybackResult play(ProviderReference reference);
    PlaybackResult pause();
    PlaybackResult resume();
    PlaybackResult skip();
    PlaybackResult setVolume(int percent);
    void clearCredentials();
}

## YouTube Implementation Constraints

The roadmap row 5 implementation must observe all of these:

- the official IFrame player is rendered only in the PIN-gated DM cockpit;
- no YouTube script, reference, state, iframe, thumbnail, or error reaches /player or /ws/table;
- the player is at least 480 by 270 when controls are shown, remains unobscured, and retains required branding;
- while YouTube audio plays, its official player stays rendered in the cockpit and cannot be collapsed or hidden;
- an IntersectionObserver or equivalent visibility check prevents scripted playback below the provider's visibility threshold;
- the first audible action is a clearly labeled DM gesture;
- onAutoplayBlocked becomes a visible prompt;
- URL parsing accepts only documented YouTube video/playlist URL shapes and stores opaque IDs;
- baseline supports pasted references, not search, so no API key is required;
- the official provider-hosted IFrame script loads only when YouTube is enabled in the DM widget and is the sole runtime-script exception authorized by the amended design;
- browser tests use a deterministic fake provider and never call YouTube.

## Spotify Implementation Constraints

The roadmap row 5 plan includes Spotify only when its status is SUPPORTED. The implementation:

- is included only when status is SUPPORTED;
- must not hide policy risk behind a feature flag when status is CONDITIONAL;
- uses PKCE with no client secret and a loopback callback;
- treats the client ID as local configuration because 2026 development mode cannot support a universal zero-setup distribution;
- stores only refresh credentials on disk with owner-only permissions;
- keeps access tokens and device IDs transient;
- requires Premium and an active official device;
- honors Retry-After and bounds provider timeouts away from session mutations;
- displays metadata/artwork and provider attribution whenever Spotify content is controlled.

## Account and Subscription Prerequisites

YouTube public embeds require no account for known public IDs. Spotify playback requires the
following:
- Owner must have an active Premium subscription
- Development mode limits: 1 client ID per developer, 5 users per app
- Spotify client or Connect device must be active on the DM machine

## Downstream Implementation Contract

No production contract is authorized until the real-provider proof is recorded. The future row 5
plan must use a provider SPI, deterministic fake-provider tests, local credential clearing, and a
strictly DM-only projection boundary.

## Roadmap Outcome

YouTube is proven on the DM device as a viable baseline provider. Row 2 can advance to COMPLETE
when the contract test passes and the roadmap is updated. This proof alone does not advance row 3;
see the roadmap for the next legal work package.
