# Music-Provider Feasibility Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Commit an evidence-backed provider decision that proves YouTube can supply the baseline DM-device music path and classifies Spotify as supported, conditional, or rejected without building the production music subsystem.

**Architecture:** This is a documentation-and-proof spike, not atmosphere feature implementation. It evaluates only the public YouTube IFrame Player API and Spotify Web API, performs disposable real-provider proofs outside the repository, and commits sanitized evidence plus the exact downstream provider contract. YouTube is the required baseline because it has public playback controls without a paid account; Spotify is an optional enhanced adapter whose policy, Premium, OAuth, device, and development-mode constraints must not become readiness prerequisites.

**Tech Stack:** Official Spotify and YouTube developer documentation, Markdown architecture records, JUnit 5, AssertJ, YouTube IFrame Player API, Spotify Web API/OAuth 2.0 PKCE, Maven Wrapper, browser developer tools, curl.

## Global Constraints

- The only candidates are YouTube and Spotify.
- Amazon Music remains out of scope: its official playback API is closed beta and Amazon states that it has no public SDK (https://developer.amazon.com/docs/music/get_started_program-overview.html).
- This spike must not add production Java, templates, JavaScript, CSS, routes, database fields, dependencies, migrations, frontend tooling, provider credentials, or campaign-package fields.
- Use current official provider documentation during execution. Record the check date and direct source URLs in the architecture decision.
- YouTube is the required readiness baseline. Row 2 cannot become COMPLETE unless a real browser on the DM device plays and switches audible YouTube content through the official IFrame API.
- An authless public provider path is permitted. OAuth and local token storage apply only when the selected provider requires authentication.
- Spotify is optional for this gate. Missing Premium, credentials, an active device, or policy clearance records a limitation; it does not invalidate a successful YouTube proof.
- Do not claim Spotify support while its prohibition on synchronizing recordings with visual media remains incompatible or unresolved for automatic scene and encounter cues.
- The YouTube proof must keep the official player visible, unobscured, correctly attributed, at least 200 by 200 CSS pixels, and more than half visible whenever scripted playback starts.
- The first audible browser playback must follow an explicit DM gesture. The downstream design must surface autoplay blocking rather than bypassing browser policy.
- Cue references are identifiers or URLs only. Do not download, proxy, cache, transcode, redistribute, or commit audio/video content.
- The player view remains silent and receives no provider references, playback state, credentials, or provider scripts.
- Provider failure may disable audio only; no non-audio DMHelper action may depend on this subsystem.
- Never print, screenshot, record, or commit client secrets, authorization codes, access tokens, refresh tokens, cookies, account identifiers, device identifiers, or private playlist metadata.
- Store disposable proof files and any transient credentials only beneath /tmp with owner-only permissions; remove them after recording sanitized results.
- Run Maven with an isolated home directory under /tmp.

---

## Evidence Basis to Recheck at Execution Time

These official pages were current when this plan was written on 2026-07-18. Re-open them before recording the decision because provider capabilities and policies are time-sensitive.

### YouTube

- IFrame API controls and events: https://developers.google.com/youtube/iframe_api_reference
- Player parameters and playlist embeds: https://developers.google.com/youtube/player_parameters
- Required minimum functionality: https://developers.google.com/youtube/terms/required-minimum-functionality
- Developer policies: https://developers.google.com/youtube/terms/developer-policies
- Terms revision history: https://developers.google.com/youtube/terms/revision-history

Known planning facts:

- the IFrame API exposes play, pause, stop, playlist queueing, skip, volume, mute, state, error, and autoplay-blocked events;
- public video and playlist identifiers can be played without OAuth;
- the embedded player must remain visible and at least 200 by 200 pixels;
- scripted playback is subject to visibility and browser autoplay policy;
- YouTube branding, controls, ads, and attribution may not be obscured or altered.

### Spotify

- Developer policy: https://developer.spotify.com/policy
- February 2026 development-mode migration guide: https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide
- Authorization Code with PKCE: https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow
- Redirect URI requirements: https://developer.spotify.com/documentation/web-api/concepts/redirect_uri
- Refresh-token behavior: https://developer.spotify.com/documentation/web-api/tutorials/refreshing-tokens
- OAuth scopes: https://developer.spotify.com/documentation/web-api/concepts/scopes
- Start/resume playback: https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback
- Current playback state: https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback
- Rate limits: https://developer.spotify.com/documentation/web-api/concepts/rate-limits

Known planning facts:

- playback control requires Spotify Premium and the user-modify-playback-state scope;
- state/device discovery requires user-read-playback-state;
- local public clients should use Authorization Code with PKCE and an explicit 127.0.0.1 loopback redirect, not localhost;
- access tokens expire after one hour and current dashboard refresh tokens expire after six months;
- 2026 development mode requires a Premium app owner and limits new developers to one client ID and five users per app;
- Spotify policy forbids commercial streaming integrations, public broadcast, and synchronization of recordings with visual media;
- playback commands target an active official Spotify client or Connect device rather than making DMHelper an audio player.

---

## Files

### Files created during execution

- docs/architecture/music-provider-feasibility.md — official-source comparison, sanitized proof evidence, decision, credentials approach, capability limits, failure modes, prerequisites, and downstream contract.

### Files modified during execution

- docs/architecture/README.md — link the new architecture decision.
- src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java — make the decision record and required sections executable documentation contracts.
- docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md — clarify that an official provider-hosted playback client is inside the narrowly bounded music-network exception, not a general-purpose runtime CDN.
- docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md — specialize authentication and cockpit presentation for an authless provider whose official player must remain visible.
- docs/superpowers/dm-only-readiness-roadmap.md — record the completed or blocked outcome and the next legal work package.
- docs/superpowers/plans/2026-07-18-music-provider-feasibility.md — check completed execution steps and record final status.

### Files deliberately not modified

- src/main/** — no provider SPI, OAuth callback, token store, cue model, or cockpit widget belongs in this spike.
- src/main/resources/agent/capability-manifest.json and docs/campaign-capabilities.md — feasibility is not shipped capability.
- docs/dm-manual/** — users cannot operate a feature that has not been implemented.
- campaign schemas, DTOs, fixtures, and package adapters — audio cues arrive only in roadmap row 7.

---

### Task 1: Make the provider decision an executable architecture contract

**Files:**
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java
- Create: docs/architecture/music-provider-feasibility.md
- Modify: docs/architecture/README.md

**Interfaces:**
- Consumes: the documentation indexing pattern in DocsIndexContractTest and the approved requirements in atmosphere §§7.1, 7.6, 9.2, 9.3, 10, and 12.
- Produces: the indexed decision record and the headings later proof tasks must fill with final evidence.

- [ ] **Step 1: Add the failing documentation contract**

Add this test to DocsIndexContractTest:

~~~java
@Test
void musicProviderFeasibilityDecisionIsIndexedAndComplete() throws Exception {
    String architectureIndex = Files.readString(Path.of("docs/architecture/README.md"));
    Path decisionPath = Path.of("docs/architecture/music-provider-feasibility.md");

    assertThat(architectureIndex).contains("music-provider-feasibility.md");
    assertThat(decisionPath).exists();

    String decision = Files.readString(decisionPath);
    assertThat(decision)
            .contains("# Music-Provider Feasibility Decision")
            .contains("**Official sources checked:**")
            .containsPattern("\\*\\*Official sources checked:\\*\\* 20\\d{2}-\\d{2}-\\d{2}")
            .contains("## Decision")
            .contains("**Readiness provider:**")
            .contains("**YouTube status:**")
            .contains("**Spotify status:**")
            .contains("## Candidate Matrix")
            .contains("## DM-Device Playback Proof")
            .contains("## OAuth and Credential Storage")
            .contains("## Capability Limits")
            .contains("## Failure Modes")
            .contains("## Account and Subscription Prerequisites")
            .contains("## Downstream Implementation Contract")
            .contains("## Roadmap Outcome")
            .contains("https://developers.google.com/youtube/iframe_api_reference")
            .contains("https://developer.spotify.com/policy");
}
~~~

The ISO-date assertion keeps the evidence date machine-readable without making the committed test
expire on the next calendar day. The execution steps separately require the executor to refresh
the date after reopening the official sources.

- [ ] **Step 2: Run the contract and verify RED**

Run:

~~~bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-doc-red -Dtest=DocsIndexContractTest#musicProviderFeasibilityDecisionIsIndexedAndComplete test
~~~

Expected: one failure because the architecture index does not yet link music-provider-feasibility.md and the decision file does not exist.

- [ ] **Step 3: Create the paper-assessment decision record**

Create docs/architecture/music-provider-feasibility.md with this initial structure and factual status:

~~~markdown
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
- https://developer.spotify.com/documentation/web-api/concepts/redirect_uri
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
~~~

If execution occurs after 2026-07-18, replace the checked date with the actual date after reopening every official source.

- [ ] **Step 4: Link the decision from the architecture index**

Add this bullet to docs/architecture/README.md:

~~~markdown
- [Music-Provider Feasibility](music-provider-feasibility.md) — Spotify/YouTube evidence, DM-device playback proof, credentials boundary, capability limits, and the selected readiness provider.
~~~

- [ ] **Step 5: Run the documentation contract and verify GREEN**

Run:

~~~bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-doc-green -Dtest=DocsIndexContractTest test
~~~

Expected: all DocsIndexContractTest tests pass.

- [ ] **Step 6: Commit the executable paper assessment**

~~~bash
git add docs/architecture/README.md docs/architecture/music-provider-feasibility.md src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java
git commit -m "docs: assess music provider candidates"
~~~

---

### Task 2: Prove YouTube as the baseline DM-device provider

**Files:**
- Modify: docs/architecture/music-provider-feasibility.md
- Disposable only: /tmp/dmhelper-youtube-proof/index.html

**Interfaces:**
- Consumes: the public IFrame API, the official sample video ID M7lc1UVf-VE, the official sample playlist ID PLC77007E23FF423C6, and a real DM browser with audible output.
- Produces: a sanitized proof that the official visible player can play, pause, change volume, advance, and switch cue context after a DM gesture.

- [ ] **Step 1: Recheck the official YouTube contract**

Open all five YouTube sources listed in the decision record. Confirm and record any changes to:

- the 200 by 200 minimum player viewport;
- the more-than-half-visible rule for scripted playback;
- Referer/client identity requirements;
- branding, controls, ads, and overlay restrictions;
- play, pause, skip, volume, playlist, state, error, and onAutoplayBlocked APIs.

If current official documentation removes public IFrame playback or prohibits this DM-operated use, record YouTube as REJECTED, skip to Task 4's blocked branch, and do not substitute an unofficial API.

- [ ] **Step 2: Create a disposable visible-player proof page**

Create /tmp/dmhelper-youtube-proof/index.html with exactly this content:

~~~html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="referrer" content="strict-origin-when-cross-origin">
  <title>DMHelper YouTube feasibility proof</title>
  <style>
    body { font: 16px system-ui; margin: 2rem; background: #17120c; color: #f5ead7; }
    #player { width: 480px; height: 270px; }
    button { margin: .75rem .5rem 0 0; padding: .6rem .9rem; }
    #events { white-space: pre-wrap; max-width: 60rem; }
  </style>
</head>
<body>
  <h1>DMHelper YouTube feasibility proof</h1>
  <p>The official player stays visible and unobscured throughout scripted playback.</p>
  <div id="player"></div>
  <div>
    <button id="enable" type="button">Enable audio</button>
    <button id="pause" type="button">Pause</button>
    <button id="resume" type="button">Resume</button>
    <button id="next" type="button">Next</button>
    <button id="quiet" type="button">Volume 25%</button>
    <button id="switch" type="button">Switch cue context</button>
  </div>
  <pre id="events" aria-live="polite"></pre>
  <script>
    let player;
    const events = document.getElementById('events');
    const record = message => {
      events.textContent += new Date().toISOString() + ' ' + message + '\n';
    };

    window.onYouTubeIframeAPIReady = () => {
      player = new YT.Player('player', {
        width: 480,
        height: 270,
        videoId: 'M7lc1UVf-VE',
        playerVars: { playsinline: 1, origin: window.location.origin },
        events: {
          onReady: () => record('ready'),
          onStateChange: event => record('state=' + event.data),
          onError: event => record('error=' + event.data),
          onAutoplayBlocked: () => record('autoplay-blocked')
        }
      });
    };

    document.getElementById('enable').onclick = () => player.playVideo();
    document.getElementById('pause').onclick = () => player.pauseVideo();
    document.getElementById('resume').onclick = () => player.playVideo();
    document.getElementById('next').onclick = () => player.nextVideo();
    document.getElementById('quiet').onclick = () => player.setVolume(25);
    document.getElementById('switch').onclick = () => player.loadPlaylist({
      listType: 'playlist',
      list: 'PLC77007E23FF423C6',
      index: 0
    });
  </script>
  <script src="https://www.youtube.com/iframe_api"></script>
</body>
</html>
~~~

Do not add this page to the repository.

- [ ] **Step 3: Serve the proof only on loopback**

Run:

~~~bash
python3 -m http.server 8765 --bind 127.0.0.1 --directory /tmp/dmhelper-youtube-proof
~~~

Expected: a long-running local server announces port 8765. Open http://127.0.0.1:8765 in the DM's normal browser. Browser audio cannot be accepted through a headless run.

- [ ] **Step 4: Execute the audible control proof**

Keep the entire 480 by 270 player visible and perform these actions in order:

1. click Enable audio and confirm sound comes from the DM device;
2. click Pause and confirm audio stops;
3. click Resume and confirm audio continues;
4. click Volume 25% and confirm the player reports 25 through player.getVolume() in developer tools;
5. click Switch cue context and confirm the official sample playlist replaces the initial context without page navigation;
6. click Next and confirm the playlist advances;
7. inspect the event log and browser console for autoplay-blocked, player error, CSP, Referer, or mixed-content failures.

Pass requires all six controls to work after the explicit DM gesture, an unobscured official player, audible DM-device output, and no player error. An initial autoplay-blocked event before Enable audio is expected behavior and must be documented, not bypassed.

- [ ] **Step 5: Record sanitized YouTube evidence and freeze its capability declaration**

Update the decision record:

- set Readiness provider to YOUTUBE and YouTube status to VIABLE only if Step 4 passed;
- record the execution timestamp, browser family/version, operating system, official sample identifiers, visible dimensions, control results, and whether autoplay was initially blocked;
- do not record account, cookie, IP, device, or content-personalization data;
- declare these capabilities:

~~~text
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
~~~

The future implementation may add YouTube Data API search separately, but baseline readiness uses pasted video/playlist URLs and does not require an API key.

- [ ] **Step 6: Remove disposable proof material**

Stop the loopback server and remove /tmp/dmhelper-youtube-proof. Verify:

~~~bash
test ! -e /tmp/dmhelper-youtube-proof
~~~

Expected: exit code 0.

- [ ] **Step 7: Run the documentation contract and commit the proof**

Run:

~~~bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-youtube-green -Dtest=DocsIndexContractTest test
~~~

Expected: all DocsIndexContractTest tests pass.

Then commit:

~~~bash
git add docs/architecture/music-provider-feasibility.md
git commit -m "docs: prove youtube dm playback"
~~~

---

### Task 3: Classify Spotify without making it a readiness dependency

**Files:**
- Modify: docs/architecture/music-provider-feasibility.md
- Disposable only when credentials are available: /tmp/dmhelper-spotify-proof/

**Interfaces:**
- Consumes: current Spotify policy, development-mode rules, OAuth PKCE requirements, Player API reference, an optional Premium account, and an active official Spotify device.
- Produces: Spotify status SUPPORTED, CONDITIONAL, REJECTED, or NOT_EXERCISED plus an exact credential and error-handling design. Only SUPPORTED authorizes the later row 7 plan to include the Spotify adapter.

- [ ] **Step 1: Perform the Spotify policy gate before OAuth**

Re-open every Spotify source in the decision record. Compare the intended DMHelper behavior—automatic scene and encounter cue changes while maps and story content are visible—with Spotify's current restrictions on:

- synchronization with visual media;
- non-interactive broadcast;
- personal/non-commercial use;
- Streaming SDA monetization;
- metadata, artwork, and attribution;
- development-mode distribution.

Use this conservative rule:

- mark SUPPORTED only when current official language clearly permits the intended automatic tabletop use;
- mark CONDITIONAL when the synchronization classification remains ambiguous;
- mark REJECTED when official language directly prohibits it.

Do not infer permission from a technically successful API call. If status is CONDITIONAL or REJECTED, row 7 must not ship Spotify without later written clearance; continue documenting the technical path but do not make it part of readiness.

- [ ] **Step 2: Record the exact Spotify authentication and storage approach**

Document this downstream contract:

~~~text
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
~~~

Also document platform handling: on non-POSIX systems, use an owner-only application-data location and fail closed with an actionable settings error if owner-only storage cannot be established.

- [ ] **Step 3: Execute the optional functional proof only when prerequisites exist**

Prerequisites:

- a Spotify developer application owned by an active Premium account;
- a 127.0.0.1 loopback redirect registered exactly;
- the owner or one of the five permitted development-mode users;
- an active official Spotify client or Connect device on the DM machine;
- a non-sensitive playlist or context selected by the user.

Use Spotify's official PKCE tutorial or interactive Web API console. Request only user-read-playback-state and user-modify-playback-state. Exercise:

~~~text
GET /v1/me/player
PUT /v1/me/player/pause
PUT /v1/me/player/play
POST /v1/me/player/next
PUT /v1/me/player/volume?volume_percent=25
GET /v1/me/player
~~~

Expected: state reads return 200, successful control commands return 204, and sound changes on the active DM device. A 401, 403, 404/no-active-device, or 429 is recorded as a capability/failure-path result; do not weaken scopes or copy tokens into the repository.

If credentials or Premium are unavailable, record Spotify as NOT_EXERCISED unless the policy gate already requires CONDITIONAL or REJECTED. Do not ask the user to paste secrets into chat.

- [ ] **Step 4: Record Spotify capabilities and failure modes**

Record:

~~~text
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
~~~

Map provider failures to future user-facing categories:

- AUTH_REQUIRED for expired/revoked authorization or six-month refresh-token expiry;
- PREMIUM_REQUIRED for product/account rejection;
- NO_ACTIVE_DEVICE for absent/restricted playback devices;
- CONTENT_UNAVAILABLE for deleted, market-restricted, or non-playable references;
- RATE_LIMITED with Retry-After;
- PROVIDER_OFFLINE for network, 5xx, or timeout;
- POLICY_DISABLED when Spotify is not authorized for this product behavior.

- [ ] **Step 5: Remove disposable Spotify credentials**

Delete every transient proof file under /tmp/dmhelper-spotify-proof and revoke the proof authorization if it is no longer needed. Do not delete the user's developer application or Spotify account.

Verify:

~~~bash
test ! -e /tmp/dmhelper-spotify-proof
~~~

Expected: exit code 0.

- [ ] **Step 6: Verify and commit the Spotify classification**

Run:

~~~bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-spotify-green -Dtest=DocsIndexContractTest test
~~~

Expected: all DocsIndexContractTest tests pass.

Then commit:

~~~bash
git add docs/architecture/music-provider-feasibility.md
git commit -m "docs: classify spotify integration"
~~~

---

### Task 4: Freeze the downstream provider contract

**Files:**
- Modify: docs/architecture/music-provider-feasibility.md
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java
- Modify: docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md
- Modify: docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md

**Interfaces:**
- Consumes: the YouTube proof and Spotify classification.
- Produces: the exact boundaries the future atmosphere/music implementation plan must follow.

- [ ] **Step 1: Record the final decision**

When YouTube passed, the Decision section must state:

~~~markdown
**Readiness provider:** YOUTUBE
**YouTube status:** VIABLE
**Spotify status:** SUPPORTED, CONDITIONAL, REJECTED, or NOT_EXERCISED
~~~

Use exactly one observed Spotify status, not the list above.

When YouTube failed, use:

~~~markdown
**Readiness provider:** NONE
**YouTube status:** REJECTED
**Spotify status:** SUPPORTED, CONDITIONAL, REJECTED, or NOT_EXERCISED
~~~

Spotify remains optional even when its observed status is SUPPORTED. If YouTube fails, record NONE
and block the roadmap instead of silently changing the approved readiness baseline.

If YouTube is REJECTED, skip Steps 2–7 below: do not amend the approved design for a provider that
failed proof. In Step 8, commit only the finalized architecture decision before applying Task 5's
blocked-roadmap branch.

- [ ] **Step 2: Add a failing contract for the necessary design clarifications**

Add this test to DocsIndexContractTest:

~~~java
@Test
void approvedMusicDesignCoversAuthlessAndProviderHostedPlaybackClients() throws Exception {
    String master = Files.readString(Path.of(
            "docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md"));
    String atmosphere = Files.readString(Path.of(
            "docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md"));

    assertThat(master)
            .contains("general-purpose runtime CDN")
            .contains("official provider-hosted playback client or script");
    assertThat(atmosphere)
            .contains("may require a provider account")
            .contains("An authless public provider path may declare `AudioAuthMode.NONE`")
            .contains("must not collapse or hide that player while audio continues");
}
~~~

Run:

~~~bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-design-red -Dtest=DocsIndexContractTest#approvedMusicDesignCoversAuthlessAndProviderHostedPlaybackClients test
~~~

Expected: one failure because the two approved design documents do not yet contain these provider-specific boundary clarifications.

- [ ] **Step 3: Align the approved design with the proven provider contract**

Make only these narrow design changes after YouTube has passed its real-browser proof:

1. In master §4.6, replace the final sentence with:

~~~markdown
No new frontend build chain or general-purpose runtime CDN is introduced. An official
provider-hosted playback client or script required solely by the approved streaming-music
subsystem falls within this exception and must not be used by non-audio features.
~~~

2. In atmosphere §3.1, change “requires internet access and a provider account” to “requires internet access and may require a provider account”, then add this boundary after the existing exception rules:

~~~markdown
The exception includes an official provider-hosted playback client or script when the selected
provider technically requires it. It does not authorize unrelated runtime CDN assets or a new
frontend build chain.
~~~

3. In atmosphere §7.1, replace the unconditional authentication bullet with:

~~~markdown
- providers that require authentication use their standard local OAuth flow; tokens are stored
  in local app data, never in the database export, never in campaign packages, and are clearable
  from settings. An authless public provider path may declare `AudioAuthMode.NONE` and must not
  invent credentials or request unrelated API scopes;
~~~

4. In atmosphere §7.4, add this provider-capability rule:

~~~markdown
- when a provider requires its official player to remain visible, the cockpit renders that
  player at or above the provider minimum throughout playback. The quick-access widget may use
  compact controls while idle, but it must not collapse or hide that player while audio
  continues; scripted playback is blocked whenever the provider's visibility threshold is not
  met;
~~~

Do not weaken the DM-only projection boundary, offline degradation behavior, provider attribution, or no-audio-caching rules.

- [ ] **Step 4: Specify the future provider-neutral interfaces**

Document these planned interfaces without adding Java code:

~~~java
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
~~~

The future implementation may refine return records during its own design review, but it may not erase the declared capability, credential, DM-device, or failure-isolation boundaries.

- [ ] **Step 5: Specify YouTube's mandatory implementation constraints**

Record all of these:

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

- [ ] **Step 6: Specify Spotify's conditional implementation constraints**

Record that the later row 7 plan:

- includes Spotify only when status is SUPPORTED;
- must not hide policy risk behind a feature flag when status is CONDITIONAL;
- uses PKCE with no client secret and a loopback callback;
- treats the client ID as local configuration because 2026 development mode cannot support a universal zero-setup distribution;
- stores only refresh credentials on disk with owner-only permissions;
- keeps access tokens and device IDs transient;
- requires Premium and an active official device;
- honors Retry-After and bounds provider timeouts away from session mutations;
- displays metadata/artwork and provider attribution whenever Spotify content is controlled.

- [ ] **Step 7: Run decision-record and approved-design checks**

Run:

~~~bash
rg -n "Readiness provider|YouTube status|Spotify status|authMode|refreshTokenPath|requiresVisiblePlayer|POLICY_DISABLED" docs/architecture/music-provider-feasibility.md
rg -n "provider-hosted playback client|AudioAuthMode.NONE|must not collapse or hide" docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-contract-green -Dtest=DocsIndexContractTest test
git diff --check
~~~

Expected: rg finds every contract marker, DocsIndexContractTest passes, and git diff --check exits 0.

- [ ] **Step 8: Commit the downstream contract and aligned design**

~~~bash
git add docs/architecture/music-provider-feasibility.md src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md
git commit -m "docs: freeze music provider contract"
~~~

---

### Task 5: Advance or block the canonical roadmap from evidence

**Files:**
- Modify: docs/superpowers/dm-only-readiness-roadmap.md
- Modify: docs/superpowers/plans/2026-07-18-music-provider-feasibility.md

**Interfaces:**
- Consumes: the final architecture decision and green documentation contract.
- Produces: exactly one honest next roadmap state.

- [ ] **Step 1: Apply the successful-roadmap branch only when a provider is viable**

If the decision names YOUTUBE:

- change Current NEXT item to 3 — Rollable tables and integrations;
- change row 2 from READY to COMPLETE;
- link this completed plan and docs/architecture/music-provider-feasibility.md from row 2;
- change row 3 from BLOCKED to READY;
- keep rows 4–11 BLOCKED;
- update the recovery note to state the chosen readiness provider, Spotify's observed status, and that the next action is to create the p3-rollable-tables plan;
- explicitly state that the spike did not implement or advertise a music feature.

- [ ] **Step 2: Apply the blocked-roadmap branch when no provider is viable**

If the decision names NONE:

- change row 2 from READY to BLOCKED;
- keep rows 3–11 BLOCKED;
- set Current NEXT item to “Blocked — music-provider design decision”;
- link this plan and the architecture decision from row 2;
- record the exact failed hard requirement in the recovery note;
- do not create the rollable-tables plan until the product design or provider choice is explicitly revised.

- [ ] **Step 3: Mark execution tracking honestly**

Check only steps actually performed. Set the plan header to one of:

~~~markdown
> **Implementation status (verified DATE):** Complete. YouTube is the viable baseline provider; Spotify is recorded with its observed status. This spike shipped no provider feature.
~~~

or:

~~~markdown
> **Implementation status (verified DATE):** Blocked. No evaluated provider met the hard readiness requirements; see the architecture decision and roadmap recovery note.
~~~

Replace DATE with the execution date. Do not mark unheard audio, unperformed Spotify checks, or skipped cleanup as complete.

- [ ] **Step 4: Run the focused and full verification gates**

Run:

~~~bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-focused-final -Dtest=DocsIndexContractTest test
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-music-full-final test
~~~

Expected: both commands exit 0. Provider warnings or outages are not allowed in automated tests because no production provider code exists yet.

- [ ] **Step 5: Run repository and secret hygiene checks**

Run:

~~~bash
git diff --check
git status --short
git diff -- src/main src/main/resources
find /tmp -maxdepth 1 -type d -name 'dmhelper-*-proof' -print
~~~

Expected:

- git diff --check exits 0;
- status lists only the roadmap and this plan after the earlier focused commits, with no unrelated files;
- the src/main diff is empty;
- find prints nothing;
- no proof credential or provider response file is staged.

- [ ] **Step 6: Commit the evidence-based roadmap transition**

~~~bash
git add docs/superpowers/dm-only-readiness-roadmap.md docs/superpowers/plans/2026-07-18-music-provider-feasibility.md
git commit -m "docs: close music provider feasibility spike"
~~~

- [ ] **Step 7: Verify final history and clean tree**

Run:

~~~bash
git log -5 --oneline
git status --short
~~~

Expected: the spike's focused commits are the newest history and git status prints nothing.

---

## Completion Criteria

- Only Spotify and YouTube were evaluated; Amazon and unofficial provider APIs were not substituted.
- Every time-sensitive claim cites current official provider documentation and carries the actual check date.
- A visible official YouTube player produced audible DM-device playback and passed play, pause, resume, volume, context-switch, and skip proof after a DM gesture, or the roadmap is explicitly blocked.
- The decision records YOUTUBE or NONE; it never promotes optional Spotify or a paper-only candidate to the readiness baseline.
- Spotify has one observed status with policy reasoning, technical prerequisites, and optional live-proof evidence.
- YouTube's visibility, branding, attribution, Referer, autoplay, and error-event constraints are explicit.
- The approved design explicitly permits authless providers, confines the official provider-hosted player script to the music exception, and forbids hiding a required visible player during playback.
- OAuth/token storage is exact: YouTube baseline has no credentials; Spotify uses PKCE, memory-only access tokens, and owner-only local refresh-token storage.
- Capability limits and actionable failure categories are documented for both providers.
- The future row 7 implementation boundary names the provider SPI, fake-provider automated tests, DM-only projection boundary, and bounded provider outages.
- No production code, provider dependency, credential, audio/video content, capability claim, or package schema was added by the spike.
- DocsIndexContractTest, the full Maven suite, and git diff --check pass.
- The roadmap advances to row 3 only when a real provider proof satisfies the hard gate; otherwise it records a blocker.
- The worktree is clean after the final commit.
