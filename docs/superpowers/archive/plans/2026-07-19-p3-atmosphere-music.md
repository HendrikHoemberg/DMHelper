# Atmosphere and Music Completion Implementation Plan

> **Execution model:** Work through the tasks in order. Keep the checkboxes as the durable execution
> record. Every production change starts with a failing focused test, ends with a focused green test,
> and is committed separately unless a task explicitly says otherwise.

**Goal:** Complete roadmap row 5 by delivering a DM-only, streaming-first music subsystem with a
campaign cue library, YouTube reference adapter, provider-neutral playback boundary, cockpit audio
widget, deterministic cue priority, scene/encounter/location/campaign assignments, manual override,
confirmation mode, session mute, package-v2 fidelity, and bounded provider failure.

**Architecture:** Persist only campaign-owned cue metadata and typed assignments. Resolve the desired
cue on the server from current campaign/session state, but execute playback in the DM browser through
a provider-neutral JavaScript adapter because the approved YouTube IFrame player lives on the DM
device. The reference YouTube adapter is authless and loads the official IFrame API only after the DM
selects **Enable audio**. A deterministic fake browser adapter supplies all automated playback tests.
Scene and encounter mutations commit independently of audio, then ask the audio controller to
re-evaluate; provider failure can therefore never roll back or block a game action.

**Tech stack:** Java 25+, Spring Boot 4.1, Spring MVC, Spring Data JPA, Flyway, Jackson 3,
Thymeleaf, Alpine.js, HTMX, YouTube IFrame Player API, Playwright, Maven, JUnit 5, AssertJ,
MockMvc, JSON Schema draft 2020-12.

## Scope boundary

This plan implements atmosphere design delivery items 5–6 and roadmap row 5 only.

Included:

- campaign-owned audio cues containing provider references and display metadata;
- YouTube known-video and known-playlist references without an API key or login;
- provider capability declarations and an additive browser playback SPI;
- campaign, scene, encounter, and world-location cue assignments;
- campaign default automatic/confirm switching mode;
- session-local mute, manual override, pending confirmation, and bounded victory-cue state;
- a visible, keyboard-operable DM cockpit widget;
- package-v2 schema, import/export, semantic validation, deep comparison, and player-safety tests;
- deterministic fake-provider service, browser, outage, and timing tests.

Deferred to roadmap row 6:

- flagship fixture expansion;
- DM manual, authoring reference, conversion playbook, release-note, campaign-capability, and
  machine-readable capability-manifest closeout;
- the representative end-to-end readiness rehearsal.

Deferred to roadmap row 7:

- the final real-provider release exercise and final readiness decision.

Explicitly excluded:

- Spotify implementation while its architecture status remains `CONDITIONAL`;
- OAuth, refresh-token storage, provider client secrets, and device discovery;
- search through YouTube or any other provider;
- local audio files, downloads, caching, proxying, transcoding, or redistribution;
- crossfade (YouTube declares it unsupported, so `CROSSFADE` requests degrade to `CUT`);
- audio on `/player`, player WebSockets, player devices, or player projections;
- fog, travel, weather simulation, and any other optional roadmap expansion.

## Official provider contract rechecked for this plan

**Checked:** 2026-07-19

- [YouTube IFrame API reference](https://developers.google.com/youtube/iframe_api_reference)
- [YouTube player parameters](https://developers.google.com/youtube/player_parameters)
- [YouTube required minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality)
- [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies)

The implementation assumptions from the completed feasibility decision remain valid:

- the IFrame API supports video/playlist loading, play, pause, skip, volume, state, error, and
  `onAutoplayBlocked` events;
- the viewport minimum remains 200 by 200 pixels, with 480 by 270 recommended for a controlled
  16:9 player;
- scripted playback must not start until more than half of the player is visible;
- the player may not be obscured, modified, separated into audio-only output, or used as a
  background player;
- the embed must receive a valid origin and normal HTTP Referer/client identity;
- automated tests must not contact YouTube.

If any official requirement changes during execution, stop provider implementation, update
`docs/architecture/music-provider-feasibility.md`, and return roadmap row 5 to `READY` or `BLOCKED`
with the exact reason. Do not substitute an unofficial player or extraction library.

## Architecture decisions frozen by this plan

1. **YouTube is the only shipped provider.** `SPOTIFY` references may be preserved when imported,
   but remain unavailable. `UNKNOWN` and uninstalled provider IDs never trigger a network request.
2. **The provider boundary is split by runtime location.** Java adapters parse and normalize
   references and declare capabilities. JavaScript adapters own playback because the provider
   player runs in the browser. Server services never pretend to control browser media directly.
3. **Audio is an eventual side effect of game state.** Scene/encounter mutations return success
   first; the browser then requests the newly resolved audio state and applies it. Audio errors
   never participate in the scene or encounter transaction.
4. **Cue definitions are campaign-owned.** There is no SRD/global music catalog and no bundled
   copyrighted cue. Stable cue keys are package-local.
5. **Existing typed scene-location links define location context.** The resolver uses the first
   ordered package-scoped `SceneLinkRole.LOCATION` targeting `WORLD_LOCATION`; it does not create a
   second scene-location model. Ambiguous links are deterministic by `sortOrder`, then UUID.
6. **Runtime audio state is local and package-excluded.** Mute, manual override, accepted/pending
   cue, dismissal, provider state, and victory timing never enter campaign packages.
7. **Cached metadata is non-authoritative.** Import and authoring never fetch a provider URL.
   YouTube's official visible player is the media/artwork surface; arbitrary cached artwork URLs
   are not loaded by the cockpit.
8. **The YouTube player stays mounted.** Cockpit scene/encounter actions must stop doing full-page
   reloads. They refresh only the affected rails/map/tracker state so playback and the initial DM
   gesture survive.
9. **Visibility is enforced conservatively.** Scripted playback requires an intersection ratio
   greater than 0.5 and a visible document. If either condition becomes false while playing, the
   adapter pauses and explains how to resume; the player cannot be collapsed while active.
10. **Victory is a bounded overlay.** A configured victory cue temporarily sits below manual
    override and above the underlying scene/location/default result. Expiry uses an injected
    `Clock`; zero or absent duration skips the overlay.

## Global constraints

- Authority order is the master readiness specification, atmosphere design §6, canonical roadmap,
  completed provider decision, then this plan.
- Stable cue keys match `^[a-z0-9][a-z0-9._-]{0,99}$`.
- Cue names, provider IDs, references, display metadata, and notes are untrusted input.
- Provider references are parsed as data. They are never interpolated into HTML or script source.
- YouTube URL parsing accepts only HTTPS and an explicit allowlist of YouTube hosts/shapes, extracts
  opaque video/playlist IDs, and discards every unrelated query parameter.
- New DM HTML/API routes remain behind the existing PIN boundary.
- No provider script, iframe, cue, provider reference, playback state, metadata, or error may occur
  in `/player`, `/ws/table`, `LiveTableState`, or any player-safe map/handout response.
- No credentials, authorization codes, tokens, cookies, provider response bodies, or device IDs may
  be logged, exported, returned in errors, or added to this repository.
- YouTube `AudioAuthMode.NONE` must not create a credential directory or expose fake credential UI.
  `clearCredentials()` is idempotent and has no filesystem effect for this provider.
- Package imports preserve unsupported provider IDs as opaque data, issue a warning, and never
  contact the provider.
- Existing v2 packages without `audioCues` or cue assignments remain valid and import cleanly.
- Every new list is ordered deterministically and every new JSON object remains closed in schema.
- Use `-Duser.home=/tmp/dmhelper-p3-atmosphere-music` for Maven runs; do not overwrite `argLine`.
- Preserve unrelated user changes. Do not update readiness claims before all row-5 gates pass.

---

### Task 0: Start execution and preserve the verified baseline

**Files:**

- Modify: `docs/superpowers/dm-only-readiness-roadmap.md`
- Modify: `docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md`

**Produces:** roadmap row 5 `IN_PROGRESS`, row 6 `BLOCKED`, atmosphere items 5–6 `IN_PROGRESS`.

- [ ] Inspect `git status --short` and `git log -8 --oneline`; preserve every unrelated change.
- [ ] Re-open the four official YouTube pages above and record any change before production work.
- [ ] Run the predecessor baseline:

```bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-atmosphere-music \
  -Dtest=CampaignSettingsCodecTest,AdventureServiceTest,EncounterServiceTest,WorldServiceTest,SessionApiControllerTest,SessionWorkspaceServiceTest,CampaignSectionRegistryTest,CampaignManifestV2ContractTest,CampaignCompleteRoundTripTest,PlayerViewSecurityContractTest,CoreSessionLoopSmokeTest test
```

Expected: all selected tests pass with zero skips. Diagnose a red predecessor before editing.

- [ ] Change only the active status fields and recovery note. Link this plan from roadmap row 5.
- [ ] Run `git diff --check`, inspect the two documentation diffs, and commit
  `docs(roadmap): start atmosphere and music`.

---

### Task 1: Add the cue and runtime-state persistence model with V15

**Files:**

- Create: `src/main/resources/db/migration/V15__add_audio_cues.sql`
- Create under `src/main/java/dev/hendrikhoemberg/dmhelper/audio/data/`:
  `AudioCue.java`, `AudioCueRepository.java`, `AudioCategory.java`, `AudioReferenceKind.java`,
  `AudioTransitionPreference.java`, `AudioSwitchMode.java`, `SessionAudioState.java`,
  `SessionAudioStateRepository.java`
- Modify: `campaign/data/Campaign.java`
- Modify: `adventure/data/Scene.java`
- Modify: `encounter/data/Encounter.java`
- Modify: `world/data/WorldLocation.java`
- Modify: `campaign/service/CampaignSettings.java`
- Modify: `campaign/service/CampaignSettingsCodec.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/data/AudioCuePersistenceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/data/AudioMigrationTest.java`
- Modify test: `campaign/service/CampaignSettingsCodecTest.java`
- Modify test: `common/config/FlywayMigrationTest.java`
- Modify test: `common/config/FlywayLegacyUpgradeTest.java`

**Persistent model:**

```java
enum AudioCategory { AMBIENT, EXPLORATION, TENSION, COMBAT, TRIUMPH, SORROW, CUSTOM }
enum AudioReferenceKind { VIDEO, PLAYLIST }
enum AudioTransitionPreference { CROSSFADE, CUT }
enum AudioSwitchMode { AUTOMATIC, CONFIRM }
```

`AudioCue` contains:

- `id`, required campaign, unique `(campaign_id, cue_key)`, `cueKey`, `name`;
- opaque `providerId`, normalized `referenceKind`, normalized `providerReference`;
- optional `cachedTitle`, `artistOrOwner`, `artworkUrl`, `durationSeconds`;
- required category, optional `volumeHint` from 0–100, required transition preference, notes;
- `createdAt` and `updatedAt`.

Typed assignment columns:

- `campaign.default_audio_cue_id`;
- `adventure_scene.scene_audio_cue_id`;
- `encounter.combat_audio_cue_id`, `victory_audio_cue_id`, and
  `victory_cue_duration_seconds`;
- `world_location.location_audio_cue_id`.

`SessionAudioState` is one-to-one with `CampaignSession` and contains:

- manual override cue;
- accepted automatic cue;
- pending cue and dismissed candidate cue;
- muted flag;
- temporary victory cue and `victoryUntil`;
- optimistic version and update timestamp.

It deliberately does **not** contain provider credentials, iframe state, volume observed from the
provider, a queue, or provider errors.

- [ ] Write RED persistence tests that save/reload a complete cue, every assignment role, and one
  session runtime state; assert campaign ownership and enum fidelity after clearing the entity
  manager.
- [ ] Write RED migration tests proving existing campaign/scene/encounter/location/session rows
  migrate with null assignments, automatic switch default, and no new required content.
- [ ] Extend `CampaignSettings` with `AudioSwitchMode audioSwitchMode`, defaulting missing/null legacy
  JSON to `AUTOMATIC` without rewriting unrelated settings.
- [ ] Implement additive V15 with indexes for campaign/name, campaign/key, provider/reference, and
  all assignment FKs. Use constraints for volume 0–100, non-negative duration, and one runtime row
  per session.
- [ ] Make assignment FKs restrictive for authored content. Runtime-only FKs may clear on cue
  deletion after the service confirms dependencies.
- [ ] Add repository methods for ordered campaign listing, exact key lookup, normalized reference
  collision lookup, and detailed ID lookup.
- [ ] Run:

```bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-atmosphere-music \
  -Dtest=AudioCuePersistenceTest,AudioMigrationTest,CampaignSettingsCodecTest,FlywayMigrationTest,FlywayLegacyUpgradeTest test
```

- [ ] Commit `feat(audio): add cue and session state model`.

---

### Task 2: Implement the provider-neutral reference boundary and YouTube adapter

**Files:**

- Create under `src/main/java/dev/hendrikhoemberg/dmhelper/audio/provider/`:
  `AudioProviderId.java`, `AudioAuthMode.java`, `AudioProviderCapabilities.java`,
  `AudioProviderAvailability.java`, `ParsedAudioReference.java`, `AudioProviderAdapter.java`,
  `AudioProviderRegistry.java`, `YouTubeProviderAdapter.java`, `UnsupportedAudioProviderAdapter.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/provider/YouTubeProviderAdapterTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/provider/AudioProviderRegistryTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/provider/AudioCredentialBoundaryTest.java`

**Java SPI:**

```java
interface AudioProviderAdapter {
    String id();
    AudioAuthMode authMode();
    AudioProviderCapabilities capabilities();
    ParsedAudioReference parseReference(String input);
    AudioProviderAvailability availability();
    void clearCredentials();
}
```

YouTube capabilities are fixed to known video/playlist, play/pause, skip, volume, and queue;
search/crossfade are false; visible player and initial gesture are true; auth is `NONE`.

- [ ] Add RED parser tests for raw video IDs, raw playlist IDs, watch URLs, playlist URLs, embed
  URLs, and short URLs. Assert canonical `(kind,id)` output.
- [ ] Add hostile URL tests rejecting HTTP, lookalike domains, user-info host tricks, blank IDs,
  invalid characters/length, mixed unknown shapes, and script/data URLs.
- [ ] Assert normalization drops timestamps, tracking parameters, fragments, and every field other
  than the chosen video/playlist ID.
- [ ] Add registry tests for exact case-normalized provider lookup, unavailable opaque provider IDs,
  unique adapter IDs, and capability truth.
- [ ] Prove `clearCredentials()` for YouTube neither creates `~/.dmhelper/providers` nor changes any
  file under an isolated home.
- [ ] Implement no metadata fetch, no Data API dependency, no API key, and no HTTP client.
- [ ] Run the three provider tests and commit
  `feat(audio): add provider contract and youtube references`.

---

### Task 3: Deliver safe cue CRUD, dependency handling, and the cue library

**Files:**

- Create under `audio/service/`: `AudioCueWrite.java`, `AudioCueValidator.java`,
  `AudioCueValidationProblem.java`, `AudioCueValidationException.java`, `AudioCueService.java`,
  `AudioCueDependency.java`, `AudioCueDeletionImpact.java`, `AudioCueDependencyService.java`
- Create under `audio/web/`: `AudioCueResponse.java`, `AudioCueWebMapper.java`,
  `AudioCueApiController.java`, `AudioCueController.java`
- Create templates under `templates/audio/`: `list.html`, `detail.html`, `form.html`, `_card.html`
- Create: `src/main/resources/static/js/audio-cue-editor.js`
- Modify: `common/service/ContentDestinationRegistry.java`
- Modify: `common/service/CommandPaletteService.java`
- Modify: `campaign/packagev2/key/CampaignContentType.java`
- Test under `src/test/java/dev/hendrikhoemberg/dmhelper/audio/service/`:
  `AudioCueValidatorTest.java`, `AudioCueServiceTest.java`, `AudioCueDependencyServiceTest.java`
- Test under `audio/web/`: `AudioCueApiControllerTest.java`, `AudioCueControllerTest.java`,
  `AudioCueTemplateContractTest.java`
- Modify test: `common/service/ContentDestinationRegistryTest.java`
- Modify test: `common/service/CommandPaletteServiceTest.java`

**Routes:**

- HTML: `/campaigns/{campaignId}/audio/cues`, `/new`, `/{cueId}`, `/{cueId}/edit`;
- API: `/api/v1/campaigns/{campaignId}/audio/cues` and
  `/api/v1/campaigns/{campaignId}/audio/cues/{cueId}`;
- dependency preview before confirmed deletion.

- [ ] Add RED validation tests for blank/duplicate keys, names, unknown providers during authoring,
  invalid reference shapes, duplicate normalized references, volume/duration bounds, invalid enum
  input, cross-campaign update IDs, and overlong metadata.
- [ ] Route every authoring reference through `AudioProviderAdapter.parseReference`; persist only the
  canonical provider ID, kind, and opaque provider ID.
- [ ] Return immutable response DTOs, never JPA entities. Escape typed display metadata and sanitize
  notes Markdown on rendering.
- [ ] Implement create, update, list, detail, clone, and deletion-impact/confirmed-delete. Cloning
  requires a new stable key and preserves no runtime state.
- [ ] Dependency impact lists campaign/scene/encounter/location assignments plus session override,
  accepted, pending, dismissed, and victory state. Confirmed deletion clears all listed references
  in one transaction; cancellation changes nothing.
- [ ] Add `AUDIO_CUE` to `CampaignContentType` and a campaign-scoped destination. Register cues in the
  command palette for the owning campaign only; never return another campaign's cue.
- [ ] Build a functional editor with provider capability hints, pasted-reference normalization
  preview, exact field errors, category/transition/volume controls, and clear unsupported-control
  messaging. Do not add provider search.
- [ ] Ensure `artworkUrl` is displayed as escaped text in authoring/detail views and is not assigned
  to an `<img src>` or fetched.
- [ ] Run focused service/web/search tests and commit `feat(audio): add campaign cue library`.

---

### Task 4: Add typed assignments and campaign switching configuration

**Files:**

- Create: `audio/service/AudioCueAssignmentService.java`
- Create: `audio/web/AudioCueAssignmentApiController.java`
- Modify: `campaign/web/CampaignController.java`
- Modify: `adventure/web/SceneController.java`
- Modify: `encounter/web/EncounterController.java`
- Modify: `encounter/web/EncounterApiController.java`
- Modify: `world/web/WorldController.java`
- Modify templates: `audio/detail.html`, `adventure/_scene-form.html`,
  `adventure/scene-detail.html`, `encounter/_form.html`, `encounter/detail.html`,
  `world/locations-form.html`, `world/locations-detail.html`
- Modify: `src/main/resources/static/js/audio-cue-editor.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/service/AudioCueAssignmentServiceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/web/AudioCueAssignmentApiControllerTest.java`
- Modify tests: `CampaignControllerTest`, `SceneControllerTest`, `EncounterControllerTest`,
  `EncounterApiControllerTest`, `WorldControllerTest`

**Assignment API:**

```text
PUT /api/v1/campaigns/{campaignId}/audio/assignments/campaign
PUT /api/v1/campaigns/{campaignId}/audio/assignments/scenes/{sceneId}
PUT /api/v1/campaigns/{campaignId}/audio/assignments/encounters/{encounterId}
PUT /api/v1/campaigns/{campaignId}/audio/assignments/locations/{locationId}
PUT /api/v1/campaigns/{campaignId}/audio/settings
```

- [ ] Add RED service tests proving assignments require the cue and target to belong to the same
  campaign and that null cue IDs clear assignments safely.
- [ ] Validate encounter combat/victory roles independently; accept victory duration only when a
  victory cue exists, with a documented bounded range (5–600 seconds).
- [ ] Implement campaign `AUTOMATIC`/`CONFIRM` configuration through `CampaignSettingsCodec`; retain
  calendar and leveling fields exactly.
- [ ] Populate cue pickers with ordered same-campaign cues only. Show provider/category and an
  explicit unavailable marker for opaque imported provider IDs.
- [ ] Add assignment controls to existing entity forms/details and a reverse “used by” view on cue
  detail. Every update reports a visible success or sanitized actionable error.
- [ ] Prove direct cross-campaign API attempts fail without leaking the existence/name of the
  foreign target or cue.
- [ ] Run focused tests and commit `feat(audio): add contextual cue assignments`.

---

### Task 5: Add package-v2 schema, import/export, warning, and deep-compare fidelity

**Files:**

- Modify: `campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `campaign/packagev2/section/CampaignManifestAssembler.java`
- Create: `audio/packagev2/AudioCueSectionAdapter.java`
- Modify: `campaign/packagev2/adapter/CampaignSectionAdapter.java`
- Modify: `adventure/packagev2/AdventureSectionAdapter.java`
- Modify: `encounter/packagev2/EncounterSectionAdapter.java`
- Modify: `world/packagev2/WorldSectionAdapter.java`
- Modify: `campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Modify: `campaign/packagev2/service/CampaignSemanticSnapshot.java`
- Modify: `campaign/packagev2/service/CampaignSemanticSnapshotService.java`
- Modify: `campaign/packagev2/service/CampaignSemanticComparator.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify tests: `CampaignManifestV2ContractTest`, `CampaignDtoSchemaCompatibilityTest`,
  `CampaignManifestV2SemanticValidatorTest`, `CampaignSectionRegistryTest`,
  `CampaignSectionAdapterTest`, `AdventureSectionAdapterTest`, `EncounterSectionAdapterTest`,
  `WorldSectionAdapterTest`, `CampaignCompleteRoundTripTest`,
  `CampaignSemanticComparatorTest`, `CampaignImportAtomicityTest`
- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/packagev2/AudioCueSectionAdapterTest.java`
- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/AudioPackageSecurityTest.java`

**Manifest additions:**

- optional/default-empty top-level `audioCues`;
- `campaign.defaultCueRef` and `campaign.settings.audioSwitchMode`;
- `scene.sceneCueRef`;
- `encounter.combatCueRef`, `victoryCueRef`, `victoryCueDurationSeconds`;
- `worldLocation.locationCueRef`.

`AudioCueDto` mirrors every persistent cue field and contains no runtime/provider-auth field.

- [ ] Add RED schema tests for closed cue objects, stable keys, provider/reference bounds, exact
  category/kind/transition enums, volume/duration bounds, optional root section, and typed refs.
- [ ] Add RED semantic tests for missing/cross-type cue refs, duplicate keys, duplicate normalized
  provider refs, cross-assignment references, victory duration without cue, and unsupported provider
  warning paths.
- [ ] Preserve opaque unsupported provider IDs and references exactly on import. Emit a warning and
  mark the cue unavailable; do not rewrite it to `UNKNOWN` and do not fetch it.
- [ ] Import cue definitions before deferred assignment resolution. Register package keys as
  `AUDIO_CUE`; resolve all assignments through the import context.
- [ ] Export all campaign cues and assignments, including cached metadata. Explicitly exclude
  `SessionAudioState`, player state, errors, volume observed from the provider, and all credentials.
- [ ] Extend semantic snapshot/deep comparison so cue definitions and every assignment survive
  export → import → export. Keep runtime state deliberately absent from comparisons.
- [ ] Add an adversarial package containing fields named `accessToken`, `refreshToken`,
  `authorizationCode`, and `deviceId`; schema validation must reject them. Assert exports never
  contain those names or runtime-state values.
- [ ] Use programmatic/small test manifests here. Do not edit flagship fixtures; that is row 6.
- [ ] Run all listed package tests and commit `feat(audio): round-trip cues and assignments`.

---

### Task 6: Implement deterministic cue resolution and session audio state

**Files:**

- Create under `audio/service/`: `AudioCueSource.java`, `ResolvedAudioCue.java`,
  `SceneLocationResolver.java`, `AudioCueResolver.java`, `SessionAudioStateService.java`,
  `AudioRuntimeView.java`
- Create: `audio/web/AudioRuntimeApiController.java`
- Modify: `session/service/SessionReferenceCleaner.java`
- Modify: `session/service/SessionLifecycleService.java`
- Modify: `campaign/service/CampaignService.java`
- Test: `audio/service/SceneLocationResolverTest.java`
- Test: `audio/service/AudioCueResolverTest.java`
- Test: `audio/service/SessionAudioStateServiceTest.java`
- Test: `audio/web/AudioRuntimeApiControllerTest.java`
- Modify test: `campaign/service/CampaignCascadeDeleteTest.java`

**Priority contract:**

```text
manual override
temporary victory cue (until injected Clock says expired)
active encounter combat cue
current scene cue
first ordered current-scene location cue
campaign default cue
silence
```

The runtime response names cue, source kind/id/label, provider capabilities, muted state, switching
mode, pending confirmation, and actionable availability. It contains no credentials.

- [ ] Add a complete RED priority matrix: every tier alone, every higher tier masking lower tiers,
  encounter end fallback, victory expiry, no current scene, deleted/unavailable cue, session mute,
  and manual override clear.
- [ ] Prove location resolution accepts only a package-scoped `LOCATION` link whose target type is
  `WORLD_LOCATION`, belongs to the campaign, and resolves. Multiple valid links sort by
  `sortOrder`, then UUID.
- [ ] Use an injected `Clock`; never use sleeps in resolver tests.
- [ ] Implement `AUTOMATIC`: expose the new resolved cue immediately and clear stale pending state.
- [ ] Implement `CONFIRM`: keep the accepted cue, expose a new pending candidate, support accept and
  decline, and avoid re-prompting a declined candidate until the priority result changes.
- [ ] Manual override changes are explicit DM actions and apply immediately in either mode. Clearing
  override re-enters automatic/confirm resolution normally.
- [ ] Muting pauses output but preserves the priority result and manual override. Unmuting returns
  the current desired cue for browser application.
- [ ] Create/reset `SessionAudioState` when `SessionLifecycleService.start` opens a new session,
  retain it across pause/resume/review, and clear it when the session completes. An idle campaign
  has no playable runtime state; the cockpit may still author and display cues but cannot start
  playback until the session is open.
- [ ] Encounter-end integration records a victory overlay only when configured, then returns to the
  underlying stack after expiry. Ending an encounter still succeeds if audio state creation/update
  fails; log only a sanitized local warning and let the widget retry resolution.
- [ ] Add DM-only API actions for state, mute/unmute, override/clear, confirm/decline, victory expiry,
  and playback-result acknowledgement. Require campaign ownership on every ID.
- [ ] Delete session audio state through the existing session/campaign cleanup path.
- [ ] Run focused resolver/API/cascade tests and commit
  `feat(audio): resolve session cue priority`.

---

### Task 7: Build the cockpit widget and deterministic browser playback SPI

**Files:**

- Create: `src/main/resources/static/js/audio-provider-registry.js`
- Create: `src/main/resources/static/js/audio-provider-youtube.js`
- Create: `src/main/resources/static/js/audio-provider-fake.js`
- Create: `src/main/resources/static/js/audio-widget.js`
- Create: `src/main/resources/templates/audio/_cockpit-widget.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/test/resources/application.properties`
- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/AudioWidgetTemplateContractTest.java`
- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/web/AudioCockpitSecurityTest.java`

**Browser SPI:**

```javascript
registerAudioProvider('YOUTUBE', ({ mount, onState, onError, onAutoplayBlocked }) => ({
  enable, load, play, pause, resume, skip, setVolume, destroy
}));
```

Every method resolves to a typed result or rejects with a normalized category:
`AUTOPLAY_BLOCKED`, `CONTENT_UNAVAILABLE`, `PROVIDER_OFFLINE`, `POLICY_DISABLED`, or
`UNSUPPORTED_CONTROL`. Raw provider errors never reach HTML or server logs.

- [ ] Add RED template contracts for now playing, source label, enable, play/pause, skip, supported
  volume, override picker, clear override, mute, retry, pending confirmation, error live region,
  and visible 480×270 player mount.
- [ ] Build the provider registry with duplicate-ID rejection and a deterministic fake adapter that
  records ordered commands, supports injected failures, and never uses the network.
- [ ] Load the fake adapter only under the Playwright/test profile. Production pages must not expose
  a URL/query switch that enables it.
- [ ] Build YouTube adapter injection only inside `enable()` after an actual button/keyboard event.
  Inject exactly `https://www.youtube.com/iframe_api`; set `origin` to `window.location.origin` and
  preserve normal referrer behavior.
- [ ] Render the official player at 480×270 or larger with no overlay or custom frame over it.
  Never hide/collapse/unmount it while playing.
- [ ] Before `load`, `play`, or automatic switch, require `document.visibilityState === 'visible'`
  and `IntersectionObserver` ratio greater than 0.5. Pause and explain when those conditions cease.
- [ ] Map `onAutoplayBlocked` to the enable/resume prompt. Map YouTube error codes 2, 5, 100,
  101/150, and 153 to sanitized actionable categories.
- [ ] Use `loadVideoById` or `loadPlaylist` according to the normalized reference kind. Use official
  `playVideo`, `pauseVideo`, `nextVideo`, `setVolume`, and state events only.
- [ ] Apply volume hints only when present; degrade requested crossfade to a visible/recorded `CUT`
  transition without inventing a fade.
- [ ] Show cached title/owner in compact controls, but use the official player as the only artwork/
  media surface. Never fetch `artworkUrl`.
- [ ] Make all controls keyboard-operable, labelled, and reachable within two deliberate actions.
  Errors use `aria-live` and retain a bounded Retry action.
- [ ] Verify the cockpit alone contains the audio scripts/widget. Assert `/player`, player templates,
  player JavaScript, and WebSocket payload builders contain none of them.
- [ ] Run template/security tests and commit `feat(audio): add DM cockpit playback widget`.

---

### Task 8: Switch cues on cockpit scene/encounter actions without remounting the player

**Files:**

- Modify: `session/web/SessionController.java`
- Modify: `session/web/SessionApiController.java`
- Modify: `session/service/SessionWorkspaceService.java`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/templates/session/_story-rail.html`
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/templates/encounter/_summary-modal.html`
- Modify: `src/main/resources/static/js/audio-widget.js`
- Modify tests: `SessionApiControllerTest`, `SessionControllerTest`,
  `SessionWorkspaceServiceTest`, `SessionCockpitTemplateContractTest`,
  `EncounterTemplateContractTest`, `CoreSessionLoopSmokeTest`

**Mutation flow:**

```text
DM cockpit action
  -> commit scene/encounter mutation
  -> update only affected cockpit state/fragment
  -> request /audio/state immediately
  -> automatic: issue provider command
     confirm: show prompt and wait
  -> provider success/error updates audio widget only
```

- [ ] Add RED contracts proving `setCurrentScene`, `stepScene`, `followTransition`,
  `activateEncounter`, and cockpit encounter end do not call `window.location.reload()`.
- [ ] Add a focused fragment/state endpoint for the story and encounter rails, or an equivalent
  typed workspace response. Refresh those areas without replacing the root Alpine component or the
  audio widget DOM.
- [ ] After a scene mutation, update editorial neighbors, story content, linked tables, relevant map
  selection, and audio resolution. Preserve the existing transactional scene behavior.
- [ ] After encounter activation/end, update planned/active encounter state, tracker visibility,
  relevant map selection, and audio resolution. Refactor tracker/summary-modal reloads to dispatch a
  cockpit event handled by the parent.
- [ ] Ensure prep-screen browsing and opening scene/encounter details never invokes audio
  re-evaluation. Only cockpit mutations do.
- [ ] Coalesce rapid mutations with a monotonic request sequence or `AbortController`; a late
  provider/state response must not overwrite the newest resolved cue.
- [ ] Measure from successful mutation response to fake provider `load` invocation using a
  monotonic clock. Add a browser assertion below 500 ms without including fake provider latency.
- [ ] In `CONFIRM`, prove the mutation completes and the widget prompts without switching until the
  DM accepts; decline retains current cue and the next distinct context can prompt again.
- [ ] Prove manual override remains active across scene and encounter changes, and clearing it
  immediately resolves the underlying current context.
- [ ] Prove mute prevents new provider play/load commands while every scene, map, encounter, table,
  dice, presentation, and note action remains available.
- [ ] Run focused session/encounter/template/browser tests and commit
  `feat(audio): switch cockpit cues with session context`.

---

### Task 9: Harden failure isolation, player safety, and hostile-content behavior

**Files:**

- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/AudioFailureIsolationTest.java`
- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/AudioPlayerSafetyTest.java`
- Create test: `src/test/java/dev/hendrikhoemberg/dmhelper/audio/AudioHostileContentTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerViewSecurityContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeMapProjectionTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/TableStateWebSocketHandlerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/InteractionFailureContractTest.java`
- Modify production only if a RED test identifies a leak or unbounded failure

- [ ] Assert `/player`, initial/table WebSocket messages, player map/handout payloads, player HTML,
  and player JavaScript contain no `audio`, cue IDs/names, provider IDs/references, metadata, iframe,
  provider script URL, or provider errors.
- [ ] Inject fake `PROVIDER_OFFLINE`, `CONTENT_UNAVAILABLE`, and `AUTOPLAY_BLOCKED` failures during
  scene and encounter switching. Assert the game mutation succeeds, the widget alone reports the
  normalized error, Retry is bounded, and no reload loop occurs.
- [ ] Inject malformed/unavailable imported provider references. Assert the cockpit does not create
  a script, iframe, image, fetch, navigation, or DNS-bearing URL from them.
- [ ] Use hostile cue names, metadata, notes, and provider strings containing HTML/Markdown/script
  payloads. Assert safe text/sanitized Markdown in library, assignment pickers, API errors, and the
  cockpit widget.
- [ ] Assert exception messages, structured errors, logs captured in tests, and campaign exports
  contain no credential-like data or raw provider response.
- [ ] Prove retry throttling/coalescing prevents concurrent duplicate loads for the same cue and
  ignores stale results after a newer context wins.
- [ ] Run the full audio/security focused gate and commit `test(audio): harden failure and player boundaries`.

---

### Task 10: Browser acceptance, full verification, and roadmap handoff

**Files:**

- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `docs/architecture/music-provider-feasibility.md` only if implementation reality refines
  its planned interface wording; do not alter the provider decision without new evidence
- Modify: `docs/superpowers/dm-only-readiness-roadmap.md`
- Modify: `docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md`
- Modify: this plan's execution checkboxes/evidence note

**Offline fake-provider browser flow:**

1. create default, scene, combat, victory, and override cues through real authoring requests;
2. assign them through the real assignment API/UI;
3. open the cockpit and enable the fake provider through the same widget contract;
4. enter a scene and observe the scene cue and source label;
5. activate an encounter and observe combat cue switching within 500 ms;
6. end it, observe bounded victory cue, advance the injected/test clock, and return to scene cue;
7. set manual override, change scene/encounter, prove override wins, then clear it;
8. enable confirm mode and prove explicit accept/decline behavior;
9. mute/unmute and verify commands plus non-audio session behavior;
10. inject provider outage, verify actionable widget error, and continue scene/map/encounter/table
    actions;
11. export/import and prove authored cues/assignments survive while runtime state is absent;
12. open `/player` and inspect DOM/network/WebSocket state for zero audio exposure.

- [x] Extend `CoreSessionLoopSmokeTest` with the deterministic fake provider and the complete flow
  above. Do not intercept or call YouTube.
- [x] Attach the existing browser failure collector to audio API failures. Expected injected outage
  responses must be explicitly registered; all unexpected console/page/request failures fail.
- [x] Run the focused audio gate:

```bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-atmosphere-music \
  -Dtest='Audio*Test,CampaignSettingsCodecTest,CampaignSectionAdapterTest,AdventureSectionAdapterTest,EncounterSectionAdapterTest,WorldSectionAdapterTest,CampaignManifestV2ContractTest,CampaignManifestV2SemanticValidatorTest,CampaignCompleteRoundTripTest,CampaignSemanticComparatorTest,SessionApiControllerTest,SessionControllerTest,SessionWorkspaceServiceTest,SessionCockpitTemplateContractTest,EncounterTemplateContractTest,PlayerViewSecurityContractTest,PlayerSafeMapProjectionTest,TableStateWebSocketHandlerTest' test
```

- [x] Run `CoreSessionLoopSmokeTest` separately and inspect its fresh Surefire XML for zero
  failures/errors/skips.
- [x] Run the complete Maven suite from a clean target directory:

```bash
./mvnw -q clean test -Duser.home=/tmp/dmhelper-p3-atmosphere-music-full
```

- [x] Count fresh Surefire suites/tests/failures/errors/skips and record exact values in the roadmap
  recovery note.
- [x] Start the application against a fresh H2 database and verify Flyway ends at V17. Verify a
  populated V14 database upgrades through the original V15–V16 migrations and corrective V17
  without changing its campaign row.
- [x] Inspect `git status --short`, `git diff --check`, committed source diff, generated package
  examples, and all audio/player boundary claims.
- [x] Do **not** claim final real-provider acceptance here. Record that row 7 still owns the live
  YouTube release exercise.
- [x] Only after every row-5 gate passes: set roadmap row 5 `COMPLETE`, row 6 `READY`, and atmosphere
  items 5–6 `IMPLEMENTED`. Leave atmosphere item 7 `PLANNED` for row 6.
- [x] Commit `docs(roadmap): close atmosphere and music package`.

## Completion evidence required before row 5 may be marked complete

- V15–V17 install cleanly on fresh and populated V14 databases.
- YouTube references normalize safely without an API key, OAuth, metadata fetch, or credential path.
- Cue CRUD, deletion impact, all four assignment contexts, and switching configuration work.
- Priority resolution is deterministic and fully covered, including victory, override, mute, and
  confirm mode.
- The fake-provider browser flow proves scene/encounter switching, failure isolation, and the
  under-500-ms command budget without internet access.
- The production cockpit dynamically loads only the official YouTube IFrame API after a DM gesture,
  maintains the visible 480×270 player, and enforces document/intersection visibility.
- Older v2 packages remain valid; cues and assignments round-trip; runtime state and credentials do
  not export.
- No audio information or provider code reaches any player surface or projection.
- Focused, browser, migration, and complete Maven gates pass with fresh recorded evidence.
- Documentation status advances only to row 6; no premature overall readiness claim is made.

## Self-review checklist

- Every atmosphere §6.1–6.6 requirement maps to at least one task and test.
- The server/browser provider split is explicit and does not imply server-side media control.
- No provider call occurs inside scene, encounter, import, export, or package-validation transactions.
- The no-reload cockpit refactor is limited to actions required to preserve the mounted player.
- Unsupported providers are preserved safely and never contacted.
- Cached artwork URLs are not rendered as network resources.
- Spotify, fog, and travel do not re-enter readiness scope.
- Row 6 retains fixtures/manual/capability closeout; row 7 retains final live-provider acceptance.
