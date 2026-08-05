# DM Readiness Music Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close roadmap row 6 ("Music-focused readiness closeout") by extending the flagship fixtures with audio cues and assignments, and by making the capability matrix, capability manifest, DM manual, authoring reference, and agent playbook agree that the atmosphere/music subsystem exists — without touching any runtime feature code.

**Architecture:** The music runtime (rows 2 and 5) is already implemented, wired, and tested: the `AudioCue` model, provider SPI, cockpit widget, cue resolver, section adapter, `AUDIO_CUE` content type, and `audioCues` package section all exist and pass their focused gate. What is missing is the *closeout layer* — fixtures that exercise the audio persistent fields end-to-end, and documentation surfaces that currently describe tables and traps but are silent on music. This plan adds only fixture data (JSON) and documentation (Markdown + one hand-maintained JSON manifest); it changes no Java, no schema, no templates.

**Tech Stack:** Java 21 + Spring Boot, Maven (`./mvnw`), JUnit 5 / AssertJ, Jackson, JSON Schema (campaign format v2), exploded `.dmcampaign/manifest.json` fixture packages, Markdown docs.

## Global Constraints

Copied verbatim from the authoritative sources (master §§19–23; atmosphere design §§3.1, 6, 7, 8; roadmap row 6):

- **DM-only surface.** Audio is DM-only and MUST be absent from every player payload and player projection. Do not add any player-facing audio behavior. (design §4.8, §6.6)
- **Referential audio only.** Fixtures and docs reference audio by provider id + provider URI/ID and cached display metadata only. Never embed, cache, or export audio content, and never place provider credentials/tokens in any fixture, export, or package. (design §3.1, §6.5, §8.3)
- **No invented facts.** Do not invent rules, DCs, ranges, damage, real track titles, real artist names, or real provider URIs. Fixtures use clearly synthetic placeholder references (e.g. provider id `FAKE`, reference `fake-ambient-001`); docs describe behavior without fabricating provider specifics. (product boundary §1; design §7 non-invention; agent playbook rule)
- **Stable keys.** Every audio cue `key` matches `^[a-z0-9][a-z0-9._-]{0,99}$`. References use `{ "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "<cue-key>" }`. (design §4.3, §7)
- **Documentation examples are executable fixtures.** Any doc example that claims to be a package fragment must correspond to real fixture content that validates; the build fails otherwise. (master §19)
- **Serial roadmap discipline.** Only row 6 may be active. Row 6 → `COMPLETE` and row 7 → `READY` are flipped together in the final commit, only after the exit gate is demonstrated with real test output. (roadmap §3, §6)
- **Preserve unrelated changes.** Do not modify travel/fog artifacts; do not touch runtime audio Java. Fixture edits must keep every existing fixture field byte-stable except the audio additions.

---

## Verified baseline and gap analysis (2026-07-20)

Established by code inspection at HEAD `01b96c8` before this plan:

**Rows 1–5 are genuinely implemented in code (not just labelled COMPLETE):**

- Row 3 tables: `rollableTables` section present in both flagship fixtures (6 tables in feature-complete), `ROLLABLE_TABLE` content type registered.
- Row 4 traps/hazards: `traps` and `hazards` sections present in both fixtures; `TRAP`/`HAZARD` content types registered.
- Row 5 audio: full subsystem exists — `audio/data`, `audio/provider` (YouTube + Unsupported + registry), `audio/service` (validator, resolver, assignment, dependency, session state), `audio/web`, `audio/packagev2/AudioCueSectionAdapter`. `AUDIO_CUE` is registered in `ContentDestinationRegistry`, `CampaignContentType`, and the schema `contentType` enum. The `audioCues` array and `defaultCueRef`/`sceneCueRef`/`combatCueRef`/`victoryCueRef`/`victoryCueDurationSeconds`/`locationCueRef` reference fields already exist in `campaign-format-v2.schema.json`.

**Row 6 (this plan) is genuinely NOT done. Confirmed gaps:**

1. **Fixtures lack music.** `feature-complete.dmcampaign/manifest.json` and `published-adventure-shaped.dmcampaign/manifest.json` have `rollableTables`, `traps`, `hazards` but **zero** `audioCues` and **zero** cue-assignment refs (`grep` counts 0 for `audioCues`, `sceneCue`, `combatCue`, `defaultCue`, `locationCue`, `victoryCue`).
2. **Capability manifest lacks music.** `src/main/resources/agent/capability-manifest.json` has capabilities `tables.rollable` and `threats.traps_hazards` but no audio/music capability; its `contentTypes` array lists `ROLLABLE_TABLE`, `TRAP`, `HAZARD` but **not** `AUDIO_CUE`.
3. **Capability matrix markdown lacks music.** `docs/campaign-capabilities.md` has 0 occurrences of audio/music/atmosphere as a capability row.
4. **DM manual lacks a music chapter.** `docs/dm-manual/` has `07-rollable-tables.md` and `08-traps-and-hazards.md` but no atmosphere/music chapter.
5. **Authoring reference lacks the audio schema section.** `docs/campaign-format-v2.md` has 0 occurrences of audio/music/cue.
6. **Agent playbook lacks audio mapping.** `docs/agent/conversion-playbook.md`, `mapping-rules.md`, and `verification-checklist.md` have 0 occurrences of audio/music/cue; §7 of the design requires audio cue mapping rules and the non-invention note.

The round-trip test (`CampaignCompleteRoundTripTest`) self-compares (import → export → re-import → semantic deep compare) with **no golden snapshot file**, so fixture edits are validated by the import pipeline and deep compare alone — there is no expected-output file to regenerate.

---

## File Structure

Files created or modified by this plan (no runtime code changes):

- **Modify:** `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` — add `audioCues` array (one cue per category-relevant role) + `defaultCueRef`, `sceneCueRef`, `combatCueRef`/`victoryCueRef` assignments.
- **Modify:** `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` — add `audioCues` + a scene cue, a combat/victory cue on the module encounter, and a `locationCueRef`.
- **Modify:** `src/main/resources/agent/capability-manifest.json` — add `atmosphere.music` capability + `AUDIO_CUE` content type.
- **Modify:** `docs/campaign-capabilities.md` — add the music capability row (name+status must match the manifest for the sync test).
- **Create:** `docs/dm-manual/09-atmosphere-and-music.md`; **Modify:** `docs/dm-manual/README.md` index.
- **Modify:** `docs/campaign-format-v2.md` — add the `audioCues` + cue-reference schema section.
- **Modify:** `docs/agent/conversion-playbook.md`, `docs/agent/mapping-rules.md`, `docs/agent/verification-checklist.md` — add audio cue mapping + non-invention + verification steps.
- **Modify:** `docs/superpowers/dm-only-readiness-roadmap.md` — flip row 6 → `COMPLETE`, row 7 → `READY`, add recovery note (final commit only).

---

## Task 0: Confirm the row-6 baseline is green before editing

Per roadmap handoff protocol step 3: verify the active row's stated baseline with its focused tests before editing. This proves rows 1–5 are green at this HEAD so any later failure is attributable to this plan's edits.

**Files:** none (verification only).

- [ ] **Step 1: Confirm clean tree and HEAD**

Run: `git status --short && git log -1 --oneline`
Expected: clean working tree; HEAD is `01b96c8` (or the current tip of `Main`).

- [ ] **Step 2: Run the focused closeout gate (fixtures + capability contracts)**

Run:
```bash
./mvnw -q test -Dtest=CampaignCompleteRoundTripTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,CampaignDtoSchemaCompatibilityTest
```
Expected: `BUILD SUCCESS`, 0 failures / 0 errors. This is the pre-edit baseline for the fixtures and capability docs this plan changes.

- [ ] **Step 3: Run the focused audio gate**

Run:
```bash
./mvnw -q test -Dtest='dev.hendrikhoemberg.dmhelper.audio.**'
```
Expected: `BUILD SUCCESS`, 0 failures / 0 errors. Confirms the row-5 audio runtime this plan documents is actually green.

- [ ] **Step 4: Record the baseline**

Note the two suite counts in your working notes. If either step fails, STOP: the row-5/baseline is not green and this plan cannot proceed (do not start a later row to work around it — roadmap §6.5).

---

## Task 1: Add audio cues and assignments to the feature-complete fixture

The feature-complete fixture must "exercise every persistent field" (master §21.2). Add audio cues covering the persistent `AudioCue` fields and every assignment role: campaign `defaultCueRef`, scene `sceneCueRef`, encounter `combatCueRef` + `victoryCueRef` (+ `victoryCueDurationSeconds`).

**Files:**
- Modify: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java` (existing; run, do not edit)

**Interfaces:**
- Consumes: schema `$defs.audioCue` (required `key`, `name`, `referenceKind` ∈ {VIDEO, PLAYLIST}, `providerReference`, `category` ∈ {AMBIENT, EXPLORATION, TENSION, COMBAT, TRIUMPH, SORROW, CUSTOM}, `transitionPreference` ∈ {CROSSFADE, CUT}; optional `providerId`, `cachedTitle`, `artistOrOwner`, `artworkUrl`, `durationSeconds`, `volumeHint` 0–100, `notes`); assignment refs are `packageReference` `{scope:"PACKAGE", type:"AUDIO_CUE", key:<key>}`.
- Produces: cue keys `cue-hall-ambience`, `cue-crypt-exploration`, `cue-crypt-combat`, `cue-crypt-victory` referenced by later docs examples (Task 5) — keep these keys stable.

- [ ] **Step 1: Read the fixture's current top-level shape**

Run:
```bash
python3 -c "import json;d=json.load(open('src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json'));print(list(d['campaign'].keys()));print('encounters',len(d['encounters']),d['encounters'][0]['key']);import itertools; a=d['adventures'][0]; sc=a['chapters'][0]['scenes'][0]; print('first scene key',sc['key'])"
```
Expected: prints the campaign keys (includes `currentSceneRef`, `settings`), the first encounter key, and the first scene key. Record the encounter key and first scene key — you will attach cues to them.

- [ ] **Step 2: Add the `audioCues` array to the manifest**

Add a top-level `"audioCues"` array (place it adjacent to `"rollableTables"` for readability — key order is not semantically significant). Use exactly these four cues (synthetic references only — Global Constraints "No invented facts"):

```json
"audioCues": [
  {
    "key": "cue-hall-ambience",
    "name": "Great Hall Ambience",
    "providerId": "FAKE",
    "referenceKind": "PLAYLIST",
    "providerReference": "fake-playlist-ambient-hall",
    "cachedTitle": "Great Hall Ambience",
    "artistOrOwner": "DMHelper Fixture Library",
    "artworkUrl": "https://example.invalid/art/hall.png",
    "durationSeconds": 3600,
    "category": "AMBIENT",
    "volumeHint": 45,
    "transitionPreference": "CROSSFADE",
    "notes": "Default campaign ambience. Synthetic fixture reference — not a real track."
  },
  {
    "key": "cue-crypt-exploration",
    "name": "Crypt Exploration",
    "providerId": "FAKE",
    "referenceKind": "PLAYLIST",
    "providerReference": "fake-playlist-exploration-crypt",
    "cachedTitle": "Crypt Exploration",
    "artistOrOwner": "DMHelper Fixture Library",
    "durationSeconds": 2700,
    "category": "EXPLORATION",
    "volumeHint": 50,
    "transitionPreference": "CROSSFADE",
    "notes": "Scene cue. Synthetic fixture reference."
  },
  {
    "key": "cue-crypt-combat",
    "name": "Crypt Combat",
    "providerId": "FAKE",
    "referenceKind": "PLAYLIST",
    "providerReference": "fake-playlist-combat-crypt",
    "cachedTitle": "Crypt Combat",
    "artistOrOwner": "DMHelper Fixture Library",
    "durationSeconds": 1800,
    "category": "COMBAT",
    "volumeHint": 65,
    "transitionPreference": "CUT",
    "notes": "Encounter combat cue. Synthetic fixture reference."
  },
  {
    "key": "cue-crypt-victory",
    "name": "Crypt Victory Sting",
    "providerId": "FAKE",
    "referenceKind": "VIDEO",
    "providerReference": "fake-video-victory-sting",
    "cachedTitle": "Victory Sting",
    "artistOrOwner": "DMHelper Fixture Library",
    "durationSeconds": 20,
    "category": "TRIUMPH",
    "transitionPreference": "CUT",
    "notes": "Post-combat victory cue. Synthetic fixture reference."
  }
]
```

- [ ] **Step 3: Attach the campaign default cue**

In the `"campaign"` object, add:
```json
"defaultCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "cue-hall-ambience" }
```

- [ ] **Step 4: Attach the scene cue**

On the first scene object (the key printed in Step 1), add:
```json
"sceneCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "cue-crypt-exploration" }
```

- [ ] **Step 5: Attach the encounter combat + victory cues**

On the encounter object (the key printed in Step 1), add:
```json
"combatCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "cue-crypt-combat" },
"victoryCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "cue-crypt-victory" },
"victoryCueDurationSeconds": 15
```

- [ ] **Step 6: Validate the JSON parses**

Run:
```bash
python3 -c "import json;json.load(open('src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json'));print('parses OK')"
```
Expected: `parses OK`. If it errors, fix the comma/brace placement.

- [ ] **Step 7: Run the round-trip test for this fixture**

Run:
```bash
./mvnw -q test -Dtest='CampaignCompleteRoundTripTest#fixtureSurvivesImportExportImportWithoutSemanticLoss'
```
Expected: `BUILD SUCCESS`; the `[feature-complete]` parameterized case passes (schema validate → dry-run → import → export → re-import → deep compare, now including the four cues and three assignment roles). If the deep compare reports a missing audio field, confirm the field name matches the schema `$defs.audioCue` exactly.

- [ ] **Step 8: Commit**

```bash
git add src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json
git commit -m "test(fixtures): add audio cues and assignments to feature-complete fixture

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Add an audio cue and assignment to the published-adventure fixture

The published-adventure fixture models a real module. The design §7 says it "gains a random-encounter table and a structured trap"; row 6 extends coverage to music. Add a scene cue, a combat/victory cue on the module's encounter, and (this fixture has `worldLocations`) a `locationCueRef` so the location branch of the priority stack is exercised.

**Files:**
- Modify: `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json`
- Test: `CampaignCompleteRoundTripTest` (existing; run, do not edit)

**Interfaces:**
- Consumes: same `$defs.audioCue` + `packageReference` shapes as Task 1.
- Produces: cue keys `pa-cue-village-calm`, `pa-cue-depths-tension`, `pa-cue-depths-combat` (stable; not referenced elsewhere but keep consistent).

- [ ] **Step 1: Read the fixture's cue attachment points**

Run:
```bash
python3 -c "import json;d=json.load(open('src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json'));print('encounters',[e['key'] for e in d['encounters']]);print('worldLocations',[w['key'] for w in d.get('worldLocations',[])]);a=d['adventures'][0];print('first scene',a['chapters'][0]['scenes'][0]['key'])"
```
Expected: prints encounter keys, world location keys, and the first scene key. Record one encounter key, one world-location key, and the first scene key.

- [ ] **Step 2: Add the `audioCues` array**

Add a top-level `"audioCues"` array with three synthetic cues:
```json
"audioCues": [
  {
    "key": "pa-cue-village-calm",
    "name": "Village Calm",
    "providerId": "FAKE",
    "referenceKind": "PLAYLIST",
    "providerReference": "fake-playlist-village-calm",
    "cachedTitle": "Village Calm",
    "artistOrOwner": "DMHelper Fixture Library",
    "durationSeconds": 3000,
    "category": "AMBIENT",
    "transitionPreference": "CROSSFADE",
    "notes": "Location cue for the starting village. Synthetic fixture reference."
  },
  {
    "key": "pa-cue-depths-tension",
    "name": "The Depths",
    "providerId": "FAKE",
    "referenceKind": "PLAYLIST",
    "providerReference": "fake-playlist-depths-tension",
    "cachedTitle": "The Depths",
    "artistOrOwner": "DMHelper Fixture Library",
    "durationSeconds": 2400,
    "category": "TENSION",
    "transitionPreference": "CROSSFADE",
    "notes": "Scene cue for the dungeon depths. Synthetic fixture reference."
  },
  {
    "key": "pa-cue-depths-combat",
    "name": "Depths Combat",
    "providerId": "FAKE",
    "referenceKind": "PLAYLIST",
    "providerReference": "fake-playlist-depths-combat",
    "cachedTitle": "Depths Combat",
    "artistOrOwner": "DMHelper Fixture Library",
    "durationSeconds": 1500,
    "category": "COMBAT",
    "transitionPreference": "CUT",
    "notes": "Combat cue for the boss encounter. Synthetic fixture reference."
  }
]
```

- [ ] **Step 3: Attach the location cue**

On the world-location object recorded in Step 1, add:
```json
"locationCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "pa-cue-village-calm" }
```

- [ ] **Step 4: Attach the scene cue**

On the first scene object recorded in Step 1, add:
```json
"sceneCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "pa-cue-depths-tension" }
```

- [ ] **Step 5: Attach the combat + victory cues**

On the encounter object recorded in Step 1, add:
```json
"combatCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "pa-cue-depths-combat" },
"victoryCueRef": { "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "pa-cue-depths-tension" },
"victoryCueDurationSeconds": 10
```

- [ ] **Step 6: Validate the JSON parses**

Run:
```bash
python3 -c "import json;json.load(open('src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json'));print('parses OK')"
```
Expected: `parses OK`.

- [ ] **Step 7: Run the round-trip test**

Run:
```bash
./mvnw -q test -Dtest='CampaignCompleteRoundTripTest'
```
Expected: `BUILD SUCCESS`; both `[feature-complete]` and `[published-adventure]` cases pass.

- [ ] **Step 8: Commit**

```bash
git add src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json
git commit -m "test(fixtures): add audio cues and assignments to published-adventure fixture

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Register the music subsystem in the capability manifest and matrix

Design §7 requires the capability matrix and the machine-readable manifest to gain music entries "including the network-dependency note." The `CapabilityMatrixMarkdownSyncTest` matches markdown rows to manifest entries by capability **name** and asserts equal **status**, so both files must be edited together with an identical name+status.

**Files:**
- Modify: `src/main/resources/agent/capability-manifest.json`
- Modify: `docs/campaign-capabilities.md`
- Test: `CapabilityManifestContractTest`, `CapabilityMatrixMarkdownSyncTest` (existing; run, do not edit)

**Interfaces:**
- Consumes: `CapabilityManifest.Capability` shape (`id`, `name`, `status`, plus the existing fields used by neighbours — copy the exact field set from an existing entry such as `tables.rollable`).
- Produces: capability id `atmosphere.music` (SUPPORTED), name reused verbatim in the markdown row; content type `AUDIO_CUE` in the manifest `contentTypes` array.

- [ ] **Step 1: Read an existing capability entry to copy its exact field shape**

Run:
```bash
python3 -c "import json;d=json.load(open('src/main/resources/agent/capability-manifest.json'));print(json.dumps([c for c in d['capabilities'] if c.get('id')=='tables.rollable'][0],indent=1))"
```
Expected: prints the full `tables.rollable` object. Mirror its exact keys (do not add or drop fields) for the new entry.

- [ ] **Step 2: Add the `atmosphere.music` capability to the manifest**

Add a new object to the `capabilities` array, immediately after `threats.traps_hazards`, using the same field set as Step 1's entry. Set:
- `id`: `atmosphere.music`
- `name`: `Atmosphere & music` (this exact string must appear as the markdown row's first column in Step 4)
- `status`: `SUPPORTED`
- the human-readable description/notes field (use whatever field name Step 1 showed, e.g. `notes` or `description`): `DM-side streaming music behind a provider SPI with scene/encounter/location cue switching. Requires internet and may require a provider account; the only approved runtime network dependency. Provider failure degrades only the audio widget and never blocks a session. Audio is DM-only and absent from player payloads.`

- [ ] **Step 3: Add `AUDIO_CUE` to the manifest `contentTypes`**

Append `"AUDIO_CUE"` to the `contentTypes` array (after `"HAZARD"` to group with the other P3 content types). This matches the registered `CampaignContentType.AUDIO_CUE` and `ContentDestinationRegistry` entry.

- [ ] **Step 4: Add the matching row to the capability matrix markdown**

In `docs/campaign-capabilities.md`, add a table row whose first column is exactly `Atmosphere & music` and whose second column is exactly `SUPPORTED` (matching Step 2 so `CapabilityMatrixMarkdownSyncTest` passes). Use a description consistent with the manifest, e.g.:
```
| Atmosphere & music | `SUPPORTED` | DM-side streaming music behind a provider SPI; scene/encounter/location cue switching, manual override, per-campaign confirm and per-session mute. Requires internet and possibly a provider account — the only approved runtime network dependency; provider failure never blocks a session. DM-only, absent from player payloads. |
```
Match the existing rows' column formatting exactly (the sync test reads the status column verbatim; if other rows wrap status in backticks, keep backticks — verify by matching an existing row like `tables.rollable`/`Rollable tables`).

- [ ] **Step 5: Validate the manifest JSON parses**

Run:
```bash
python3 -c "import json;json.load(open('src/main/resources/agent/capability-manifest.json'));print('parses OK')"
```
Expected: `parses OK`.

- [ ] **Step 6: Run the capability contract + sync tests**

Run:
```bash
./mvnw -q test -Dtest=CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest
```
Expected: `BUILD SUCCESS`. `statusesAreClosedEnum` still passes (SUPPORTED is in the enum); the sync test finds the new `Atmosphere & music` row and matches its status to the manifest.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/agent/capability-manifest.json docs/campaign-capabilities.md
git commit -m "docs(capabilities): register atmosphere and music subsystem

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Add the DM manual atmosphere & music chapter

Master §19.2 requires the DM manual to cover the session workflow; the atmosphere design §6.4 defines the cockpit audio widget. Add chapter 09 and index it.

**Files:**
- Create: `docs/dm-manual/09-atmosphere-and-music.md`
- Modify: `docs/dm-manual/README.md`

- [ ] **Step 1: Read the existing chapter 08 for tone/structure to mirror**

Run: `sed -n '1,40p' docs/dm-manual/08-traps-and-hazards.md`
Expected: shows the heading style, intro sentence, and section conventions to match.

- [ ] **Step 2: Write chapter 09**

Create `docs/dm-manual/09-atmosphere-and-music.md` mirroring chapter 08's structure. It MUST cover (from design §6):
- **What it is / boundary:** DM-side streaming music; the only feature that needs the internet; a provider account may be required; player devices stay silent and receive no audio data.
- **Configuring a provider:** where the provider setting lives, the local OAuth flow, that tokens are stored locally, never exported, and are removable with one visible action; the unconfigured/offline state message.
- **Building a cue library:** what an `AudioCue` is (a reference to a provider playlist or track + cached display metadata), categories (AMBIENT, EXPLORATION, TENSION, COMBAT, TRIUMPH, SORROW, CUSTOM), transition preference (CROSSFADE/CUT), volume hint.
- **Assigning cues:** campaign default, scene cue, encounter combat + optional victory cue (+ duration), location cue.
- **Runtime switching:** the priority stack (manual override > combat > scene > location > campaign default > silence), automatic switching on cockpit scene/encounter transitions, the per-campaign confirm-mode setting, and the per-session mute.
- **The cockpit widget:** now-playing, play/pause, skip, volume (where supported), active cue source label, manual override picker, mute; keyboard operability; that a provider-required visible player stays at/above the provider minimum during playback.
- **Troubleshooting / graceful degradation:** provider outage, no active device, expired auth, rate limit → visible actionable widget errors with retry; nothing else in the session is blocked.
- **What never leaves the app:** no audio content stored/cached/exported; no credentials in packages; audio absent from player view.

Do not invent provider names, real track titles, real URIs, or OAuth specifics; describe behavior generically (the reference provider is documented in `docs/architecture/music-provider-feasibility.md` — link to it rather than restating time-sensitive provider details).

- [ ] **Step 3: Add the chapter to the manual index**

In `docs/dm-manual/README.md`, add a row after the chapter 08 row:
```
| 09 | [Atmosphere & Music](09-atmosphere-and-music.md) | DM-side streaming music: providers, cue library, assignments, priority switching, cockpit widget, graceful degradation |
```

- [ ] **Step 4: Verify links resolve**

Run:
```bash
test -f docs/dm-manual/09-atmosphere-and-music.md && grep -q "09-atmosphere-and-music.md" docs/dm-manual/README.md && echo "chapter + index OK"
```
Expected: `chapter + index OK`.

- [ ] **Step 5: Commit**

```bash
git add docs/dm-manual/09-atmosphere-and-music.md docs/dm-manual/README.md
git commit -m "docs(manual): add atmosphere and music chapter

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Add the audio schema section to the authoring reference

Master §19.3 requires a complete schema reference. `docs/campaign-format-v2.md` documents tables and traps but not audio. Add the `audioCues` section + the cue-reference fields, using the fixture cues from Task 1 as the executable example (Global Constraints: doc examples are executable fixtures).

**Files:**
- Modify: `docs/campaign-format-v2.md`

- [ ] **Step 1: Locate the traps/tables section to place the audio section beside**

Run: `grep -n "rollableTables\|## \|### \|traps" docs/campaign-format-v2.md | head -40`
Expected: shows the section headings and where `rollableTables`/`traps` are documented. Add the audio section adjacent, following the same heading depth and format.

- [ ] **Step 2: Write the `audioCues` schema section**

Document, matching the surrounding style:
- The `audioCues` manifest array and the `audioCue` object fields, copied from the schema `$defs.audioCue`: required `key`, `name`, `referenceKind` (`VIDEO` | `PLAYLIST`), `providerReference`, `category` (`AMBIENT` | `EXPLORATION` | `TENSION` | `COMBAT` | `TRIUMPH` | `SORROW` | `CUSTOM`), `transitionPreference` (`CROSSFADE` | `CUT`); optional `providerId`, `cachedTitle`, `artistOrOwner`, `artworkUrl`, `durationSeconds`, `volumeHint` (0–100), `notes`.
- The cue-reference assignment fields and where they live: `campaign.defaultCueRef`, `scene.sceneCueRef`, `encounter.combatCueRef`, `encounter.victoryCueRef` + `encounter.victoryCueDurationSeconds`, `worldLocation.locationCueRef` — each a `contentReference` with `{ "scope": "PACKAGE", "type": "AUDIO_CUE", "key": "<cue-key>" }`.
- Export/round-trip rule: cues export as keys + provider references + cached display metadata only — never audio content, never credentials; playback state is intentionally transient.
- Semantic validation notes: cue references must resolve; category/transition enums must be valid; an unknown provider id imports with a WARNING and the cue shows as unavailable.
- A short example fragment copied verbatim from the feature-complete fixture (the `cue-hall-ambience` cue object + the `defaultCueRef`), so the example is backed by a real validating fixture.

- [ ] **Step 3: Verify the documented example matches the fixture byte-for-byte on the shown fields**

Run:
```bash
python3 -c "import json;d=json.load(open('src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json'));c=[x for x in d['audioCues'] if x['key']=='cue-hall-ambience'][0];print(json.dumps(c,indent=2))"
```
Expected: prints the cue object; confirm every field you documented in the example matches this output exactly (no invented fields).

- [ ] **Step 4: Commit**

```bash
git add docs/campaign-format-v2.md
git commit -m "docs(authoring): document audioCues schema and cue references

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Add audio mapping to the agent playbook

Design §7 requires the conversion playbook and mapping rules to cover the new subsystems and to reaffirm the non-invention rule. Tables and traps are covered; audio is not.

**Files:**
- Modify: `docs/agent/conversion-playbook.md`
- Modify: `docs/agent/mapping-rules.md`
- Modify: `docs/agent/verification-checklist.md`

- [ ] **Step 1: Read how tables/traps are covered to mirror the pattern**

Run:
```bash
grep -n "table\|trap\|## \|### " docs/agent/mapping-rules.md | head -40
```
Expected: shows the mapping-rule headings for tables and traps to mirror.

- [ ] **Step 2: Add an audio mapping rule to `mapping-rules.md`**

Add a section mirroring the table/trap sections, stating:
- Source material rarely names music; a converter maps an explicit "suggested soundtrack"/"ambience" note to an `AudioCue` **only when the source states it**. It MUST NOT invent tracks, artists, providers, or provider URIs (non-invention rule).
- When the source names a mood but no track, emit a cue with `providerId: "UNKNOWN"` (or leave the provider reference for the DM to fill) and a descriptive `name`/`category`; mark it unavailable rather than fabricating a `providerReference`.
- Map source mood words to `category`: ambient/background → AMBIENT; travel/exploration → EXPLORATION; suspense/dread → TENSION; battle → COMBAT; victory/triumph → TRIUMPH; grief/loss → SORROW; anything else → CUSTOM.
- Assign cues by structure: a whole-region ambience → `worldLocation.locationCueRef`; a scene's stated ambience → `scene.sceneCueRef`; an encounter's battle music → `encounter.combatCueRef`; the campaign-wide default → `campaign.defaultCueRef`.

- [ ] **Step 3: Add an audio note to `conversion-playbook.md`**

Add a short subsection (mirroring the tables/traps subsections) pointing to the mapping rule and restating: audio is DM-only, referential only, and the converter never invents provider references; unresolved music becomes an `UNKNOWN`/unavailable cue, not a fabricated one.

- [ ] **Step 4: Add audio checks to `verification-checklist.md`**

Add deterministic checklist items:
- Every `audioCue` has a valid `key`, `category`, and `transitionPreference`.
- Every cue reference (`defaultCueRef`/`sceneCueRef`/`combatCueRef`/`victoryCueRef`/`locationCueRef`) resolves to an existing cue key.
- No cue contains credentials or embedded audio; `providerReference` is an opaque string only.
- No player-facing artifact references a cue.

- [ ] **Step 5: Verify all three files mention audio now**

Run:
```bash
for f in docs/agent/conversion-playbook.md docs/agent/mapping-rules.md docs/agent/verification-checklist.md; do echo "$f: $(grep -ic 'audio\|cue' $f)"; done
```
Expected: each file reports a non-zero count.

- [ ] **Step 6: Commit**

```bash
git add docs/agent/conversion-playbook.md docs/agent/mapping-rules.md docs/agent/verification-checklist.md
git commit -m "docs(agent): add audio cue mapping and non-invention rules

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Full closeout verification and roadmap status flip

Row 6's exit gate: "Feature-complete and published-adventure fixtures cover tables, traps, and music; schemas/catalogs/playbook/manual/capability matrix agree; full round-trip and player-safety suites pass." Prove it with the full suite, then flip the roadmap in the same commit (roadmap §6.6–6.7).

**Files:**
- Modify: `docs/superpowers/dm-only-readiness-roadmap.md`

- [ ] **Step 1: Run the full round-trip + contract + capability gate**

Run:
```bash
./mvnw -q test -Dtest=CampaignCompleteRoundTripTest,CampaignDtoSchemaCompatibilityTest,CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest
```
Expected: `BUILD SUCCESS`, 0 failures / 0 errors.

- [ ] **Step 2: Run the player-safety / security + audio isolation gate**

Run:
```bash
./mvnw -q test -Dtest='dev.hendrikhoemberg.dmhelper.audio.**,*Security*,*PlayerProjection*,*Projection*'
```
Expected: `BUILD SUCCESS`. Confirms audio remains absent from player payloads and no security regression. (If a name pattern matches nothing, adjust to the actual security/projection test class names found via `grep -rl "player" src/test/java | grep -i projection`.)

- [ ] **Step 3: Run the complete Maven suite**

Run:
```bash
./mvnw -q test
```
Expected: `BUILD SUCCESS`. Record the suite/test counts. Known environmental caveat: `CoreSessionLoopSmokeTest` may intermittently time out (documented in the roadmap recovery note); if it does, re-run it in isolation (`./mvnw -q test -Dtest=CoreSessionLoopSmokeTest`) and record that it passes standalone. Any non-environmental failure blocks the status flip.

- [ ] **Step 4: Update the roadmap**

In `docs/superpowers/dm-only-readiness-roadmap.md`:
- Change the row-6 `Status` cell from `READY` to `COMPLETE`, and replace the "Create a dated ... plan" cell with a link to this plan: `[Completed plan](plans/2026-07-20-dm-readiness-music-closeout.md)`.
- Change the row-7 `Status` cell from `BLOCKED` to `READY`.
- Update **Current NEXT item** (top of file) from `6 — Music-focused readiness closeout` to `7 — Final readiness verification and release decision`.
- Update **Last updated** to `2026-07-20`.
- Add a §7 recovery-note bullet recording: fixtures now carry audio cues + assignments in both flagship packages; capability matrix/manifest, DM manual chapter 09, authoring reference, and agent playbook now cover music; and paste the actual suite/test counts from Steps 1–3.

- [ ] **Step 5: Verify the roadmap invariant**

Run:
```bash
grep -nE "^\| [67] \|" docs/superpowers/dm-only-readiness-roadmap.md
```
Expected: row 6 shows `COMPLETE`, row 7 shows `READY`, and no other row is `READY`/`PLANNING`/`IN_PROGRESS`/`VERIFYING` (roadmap §3 single-active invariant).

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/dm-only-readiness-roadmap.md
git commit -m "docs(roadmap): close music-focused readiness closeout

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage (design §7 cross-cutting + roadmap row 6 exit gate):**
- Feature-complete fixture covers music → Task 1. ✅
- Published-adventure fixture covers music → Task 2. ✅
- Capability matrix + machine-readable manifest gain music entry with network note → Task 3. ✅
- Content-type registry identifier `AUDIO_CUE` reflected in manifest → Task 3. ✅ (code registry already has it; baseline note documents this.)
- DM manual gains audio section → Task 4. ✅
- Authoring reference gains audio schema section → Task 5. ✅
- Agent playbook gains audio mapping + non-invention → Task 6. ✅
- Full round-trip + player-safety suites pass → Task 7. ✅
- Roadmap status flip with single-active invariant → Task 7. ✅

**Placeholder scan:** All JSON snippets are concrete and copy-ready; doc tasks enumerate exact required content and cite the schema/design section each fact comes from rather than saying "add appropriate content." No "TBD"/"handle edge cases"/"similar to Task N".

**Type consistency:** Cue keys are stable across tasks (`cue-hall-ambience` etc. defined in Task 1, reused in Task 5). Reference shape `{scope:"PACKAGE", type:"AUDIO_CUE", key:...}` is identical everywhere. Field names (`referenceKind`, `providerReference`, `transitionPreference`, `victoryCueDurationSeconds`, `defaultCueRef`/`sceneCueRef`/`combatCueRef`/`victoryCueRef`/`locationCueRef`) match the schema `$defs.audioCue` and the fixture reference fields verified against `campaign-format-v2.schema.json`.

**Note for the executor:** This is a documentation/fixture closeout. If any step reveals a *runtime* audio gap (a schema field the fixture needs that doesn't exist, a resolver that drops a cue on round-trip), STOP — that is a row-5 regression, not row-6 scope. Do not patch runtime code under this plan; report it so row 5 can be reopened per roadmap §2.
