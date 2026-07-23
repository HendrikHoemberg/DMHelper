# B1 Cockpit Layout Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the scrolling fixed cockpit grid with a locked-by-default, four-zone dock workbench whose modules, built-in and custom presets, resize rules, focus mode, accessibility behavior, persistence, and failure recovery form a stable foundation for B2 runtime-module migration.

**Architecture:** Add a typed server-side module registry and versioned layout document, persist only named custom presets as application-local data, and expose them through a small REST boundary. A separate dependency-free `cockpit-layout.js` controller moves each existing server-rendered module root between CSS Grid zones without recreating it, so Alpine, htmx, Konva, and server services remain authoritative for game state. B1 owns shells and layout mechanics; B2 owns extracting and improving the Story, Encounter, Map, Party, Presentation, and utility module bodies.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Thymeleaf, plain JavaScript, CSS Grid, existing Alpine.js/htmx/Konva integrations, JUnit 5, MockMvc, AssertJ, jsoup, Playwright 1.54.

## Global Constraints

- Workstream A must be complete and green before B1 changes the cockpit structure.
- Preserve `spring.jpa.open-in-view=false` in production and tests.
- Preserve the existing server-rendered Spring/Thymeleaf/htmx/Alpine architecture.
- Add no npm setup, SPA framework, third-party docking library, or new Maven dependency.
- Use four curated zones only: `PRIMARY`, `LEFT_SUPPORT`, `RIGHT_SUPPORT`, and `BOTTOM_UTILITY`.
- Do not add floating windows, overlapping modules, arbitrary recursive splits, or a snap-to-grid dashboard.
- The document never scrolls in the cockpit; every module owns its internal scroll region.
- Primary remains dominant at 50–65% of horizontal workspace width after ratio clamping.
- Bottom utility may collapse completely; Primary may never collapse.
- A module appears at most once in a workspace.
- Layout is fully locked on every page load, including splitters; entry to and exit from edit mode each require one action.
- Preset switching is always manual. Scene, encounter, turn, and presentation changes never switch presets.
- Built-in presets are immutable. Custom presets may be duplicated, renamed, updated, and deleted.
- Durable custom presets are application-local and excluded from both legacy JSON and package-v2 campaign exports.
- Browser storage contains only the last preset per campaign, last active tabs per campaign, and an unfinished edit draft per campaign.
- Preset operations must never mutate current scene, encounter, session lifecycle, map position, notes, presentation, party, or combat state.
- Existing module DOM roots are moved, not cloned or re-rendered, during docking and preset changes.
- Screen Safety remains authoritative and every registered module declares the existing `FILTER`,
  `HIDE`, or `PLAYER_PROJECTION` behavior.
- Heavy components initialize only while visible and suspend hidden rendering without losing viewport state.
- Missing module keys are omitted with a visible, non-blocking warning; an unrecoverable document falls back to the nearest built-in.
- A failed preset save keeps edit mode active and retains the recoverable browser draft.
- The hard viewport gate is 1366×768; also test 1920×1080 and 80%, 100%, and 125% zoom.
- Preset chrome must settle within 100 ms; a lazy module must show loading chrome inside that interval and retain the existing two-second render budget.
- Respect `prefers-reduced-motion: reduce`, visible focus, and minimum 32×32 px interactive targets.
- Commit no private campaign package, PDF, source-page capture, or `artifacts/` content.
- Follow TDD: observe every specified red test before adding the corresponding production code.

## Scope Boundary

B1 delivers the layout system as a working vertical slice. It wraps the six existing top-level runtime roots—Story, Map, Encounter, Session plan, Party, and Audio—and supplies honest shell states for Quick notes, Presentation, Reference, and Session log where their current content is not yet an independently mountable runtime fragment. B2 replaces those transitional bodies and relocates the map-, encounter-, handout-, audio-, and presentation-specific command-bar actions into their modules.

B1 may make the minimum adapter changes needed to preserve current module state and suspend hidden map rendering. It must not redesign encounter behavior, rewrite scene content, duplicate quick-note state, or embed preparation/Edit pages.

## Stable Identifiers and Data Contract

Use these module keys everywhere—in Java, JSON, `data-module-key`, browser storage, tests, and documentation:

| Key | Title | Minimum px | Allowed zones | Compact | Focus | Screen safety |
|---|---|---:|---|---|---|---|
| `story` | Story | 240×220 | Primary, Left | yes | yes | `FILTER` |
| `map` | Map | 420×300 | Primary | no | yes | `FILTER` |
| `encounter` | Encounter | 280×260 | Primary, Right | yes | yes | `HIDE` |
| `session-plan` | Session plan | 220×180 | Primary, Left | yes | yes | `HIDE` |
| `party` | Party | 220×140 | Left, Right, Bottom | yes | yes | `FILTER` |
| `quick-notes` | Quick notes | 220×140 | Right, Bottom | yes | yes | `HIDE` |
| `presentation` | Presentation | 280×220 | Primary, Right | yes | yes | `FILTER` |
| `reference` | Reference | 260×220 | Primary, Left, Right | yes | yes | `HIDE` |
| `audio` | Audio | 220×112 | Left, Right, Bottom | yes | yes | `FILTER` |
| `session-log` | Session log | 280×180 | Primary, Bottom | yes | yes | `HIDE` |

Use these immutable built-in keys:

- `builtin:exploration`
- `builtin:combat`
- `builtin:theatre-of-mind`
- `builtin:presentation`
- `builtin:session-review`

Custom preset API keys are `custom:<uuid>`. Browser-storage keys are:

```text
dmhelper.cockpit.last-preset.v1.<campaignId>
dmhelper.cockpit.active-tabs.v1.<campaignId>
dmhelper.cockpit.edit-draft.v1.<campaignId>
```

Layout schema version `1` is:

```json
{
  "schemaVersion": 1,
  "name": "Exploration",
  "zones": {
    "PRIMARY": {"moduleKeys": ["story"], "activeModuleKey": "story", "collapsed": false},
    "LEFT_SUPPORT": {"moduleKeys": ["session-plan"], "activeModuleKey": "session-plan", "collapsed": false},
    "RIGHT_SUPPORT": {"moduleKeys": ["party", "quick-notes"], "activeModuleKey": "party", "collapsed": false},
    "BOTTOM_UTILITY": {"moduleKeys": ["audio", "session-log"], "activeModuleKey": "audio", "collapsed": true}
  },
  "ratios": {"left": 0.20, "primary": 0.56, "right": 0.24, "bottom": 0.24},
  "compactModuleKeys": ["party", "quick-notes", "audio", "session-log"]
}
```

Horizontal ratios must sum to `1.0 ± 0.001`; `primary` must remain in `[0.50, 0.65]`; `bottom` must remain in `[0.16, 0.40]`. The client additionally clamps against the visible modules' pixel minima and current workbench dimensions.

---

## File Structure

### Layout model and registry

- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitZone.java`
  - Defines the four stable zone names and their recovery order.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitScreenSafetyBehavior.java`
  - Defines the existing `FILTER`, `HIDE`, and `PLAYER_PROJECTION` contract values.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleSource.java`
  - Declares a Thymeleaf fragment or HTTP endpoint.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleStateContract.java`
  - Declares exact empty/loading/error text and attention support.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleDefinition.java`
  - Holds immutable module metadata.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistry.java`
  - Owns the complete ten-module catalog and rejects duplicate keys.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutDocument.java`
  - Defines schema-v1 zones, ratios, active tabs, collapsed state, and compact preferences.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java`
  - Owns the five immutable defaults.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutValidator.java`
  - Strictly validates save requests.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutResolver.java`
  - Repairs readable schema-v1 documents and chooses the nearest built-in fallback.

### Persistence and API

- Create `src/main/resources/db/migration/V22__add_cockpit_layout_presets.sql`
  - Adds the application-local custom preset table with no campaign foreign key.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPreset.java`
  - Stores name, normalized name, schema version, JSON, timestamps, and optimistic version.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPresetRepository.java`
  - Lists custom presets deterministically and enforces case-insensitive uniqueness through `normalized_name`.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodec.java`
  - Serializes/deserializes schema-v1 JSON through the application `ObjectMapper`.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetService.java`
  - Merges built-ins and custom presets, validates writes, performs repair on reads, and maps DTOs.
- Create `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiController.java`
  - Exposes registry and preset CRUD without campaign-state mutations.

### Workbench UI

- Create `src/main/resources/templates/session/_cockpit-workbench.html`
  - Renders the four zones, tabs, splitters, focus layer, module depot, Add module dialog, and save/discard dialog.
- Create `src/main/resources/templates/session/_cockpit-module-shell.html`
  - Renders one accessible shell and standardized empty/loading/error/attention chrome.
- Create `src/main/resources/templates/session/_cockpit-deferred-modules.html`
  - Renders honest transitional Quick notes, Presentation, Reference, and Session log actions without embedding Edit pages.
- Create `src/main/resources/static/js/cockpit-layout.js`
  - Owns only layout mode, docking, tab selection, ratio changes, focus, preset switching, transient storage, warning/error chrome, and module-visibility events.
- Create `src/main/resources/static/css/cockpit-layout.css`
  - Owns viewport, CSS Grid zones, tabs, splitters, edit targets, focus layer, dialogs, responsive clamping, and reduced motion.
- Modify `src/main/resources/templates/session/cockpit.html`
  - Adds bootstrap data and the compact layout controls, renders existing module roots once in the depot, and installs the workbench.
- Modify `src/main/resources/static/css/cockpit.css`
  - Removes the legacy three-column/document-scroll structure while retaining module-specific styles.
- Modify `src/main/resources/static/js/session-cockpit.js`
  - Reacts to visibility events and resizes the existing map after it becomes visible.
- Modify `src/main/resources/static/js/map/battle-map.js`
  - Adds an idempotent render-active adapter without changing map state.

### Tests and documentation

- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutValidatorTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutResolverTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodecTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPresetRepositoryTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetServiceTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiControllerTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitWorkbenchTemplateContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeModuleSafetyContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`
- Modify `docs/dm-manual/03-session-cockpit.md`

---

### Task 1: Define the Module Registry and Versioned Layout Types

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitZone.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitScreenSafetyBehavior.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleSource.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleStateContract.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleDefinition.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistry.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutDocument.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java`

**Interfaces:**
- Produces: `CockpitModuleRegistry.all(): List<CockpitModuleDefinition>`.
- Produces: `CockpitModuleRegistry.require(String): CockpitModuleDefinition`.
- Produces: `CockpitLayoutDocument.CURRENT_SCHEMA_VERSION == 1`.
- Produces: immutable `ZoneLayout`, `SplitRatios`, and module metadata consumed by every later task.

- [ ] **Step 1: Write the failing registry contract**

```java
package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CockpitModuleRegistryTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();

    @Test
    void registersTheStableCatalogExactlyOnce() {
        assertThat(registry.all()).extracting(CockpitModuleDefinition::key)
                .containsExactly("story", "map", "encounter", "session-plan", "party",
                        "quick-notes", "presentation", "reference", "audio", "session-log");
        assertThat(registry.all()).extracting(CockpitModuleDefinition::key)
                .doesNotHaveDuplicates();
    }

    @Test
    void mapIsPrimaryOnlyAndEncounterIsDmSensitive() {
        assertThat(registry.require("map").allowedZones())
                .isEqualTo(Set.of(CockpitZone.PRIMARY));
        assertThat(registry.require("encounter").screenSafetyBehavior())
                .isEqualTo(CockpitScreenSafetyBehavior.HIDE);
    }

    @Test
    void everyModuleHasUsableStateCopyAndDimensions() {
        assertThat(registry.all()).allSatisfy(module -> {
            assertThat(module.minWidthPx()).isGreaterThanOrEqualTo(220);
            assertThat(module.minHeightPx()).isGreaterThanOrEqualTo(112);
            assertThat(module.states().emptyMessage()).isNotBlank();
            assertThat(module.states().loadingMessage()).isNotBlank();
            assertThat(module.states().errorMessage()).isNotBlank();
        });
    }

    @Test
    void unknownModuleCannotBeRequired() {
        assertThatThrownBy(() -> registry.require("floating-pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown cockpit module: floating-pdf");
    }

    @Test
    void duplicateRegistryKeysFailAtStartup() {
        CockpitModuleDefinition story = registry.require("story");
        assertThatThrownBy(() -> new CockpitModuleRegistry(List.of(story, story)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate cockpit module: story");
    }
}
```

- [ ] **Step 2: Run the focused test and observe the missing-type failure**

Run:

```bash
./mvnw -Dtest=CockpitModuleRegistryTest test
```

Expected: compilation fails because `CockpitModuleRegistry` and its value types do not exist.

- [ ] **Step 3: Add the immutable model**

```java
// CockpitZone.java
package dev.hendrikhoemberg.dmhelper.session.layout;

public enum CockpitZone {
    PRIMARY, LEFT_SUPPORT, RIGHT_SUPPORT, BOTTOM_UTILITY
}
```

```java
// CockpitScreenSafetyBehavior.java
package dev.hendrikhoemberg.dmhelper.session.layout;

public enum CockpitScreenSafetyBehavior {
    FILTER, HIDE, PLAYER_PROJECTION
}
```

```java
// CockpitModuleSource.java
package dev.hendrikhoemberg.dmhelper.session.layout;

public record CockpitModuleSource(Kind kind, String value) {
    public enum Kind { THYMELEAF_FRAGMENT, ENDPOINT }

    public CockpitModuleSource {
        if (kind == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("Module source requires kind and value.");
        }
    }
}
```

```java
// CockpitModuleStateContract.java
package dev.hendrikhoemberg.dmhelper.session.layout;

public record CockpitModuleStateContract(
        String emptyMessage,
        String loadingMessage,
        String errorMessage,
        boolean attentionSupported) {
}
```

```java
// CockpitModuleDefinition.java
package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.Set;

public record CockpitModuleDefinition(
        String key,
        String title,
        CockpitModuleSource source,
        int minWidthPx,
        int minHeightPx,
        Set<CockpitZone> allowedZones,
        boolean compactSupported,
        boolean focusSupported,
        CockpitScreenSafetyBehavior screenSafetyBehavior,
        CockpitModuleStateContract states) {

    public CockpitModuleDefinition {
        allowedZones = Set.copyOf(allowedZones);
        if (key == null || !key.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid cockpit module key.");
        }
        if (title == null || title.isBlank() || source == null || allowedZones.isEmpty()
                || screenSafetyBehavior == null || states == null
                || minWidthPx < 1 || minHeightPx < 1) {
            throw new IllegalArgumentException("Cockpit module metadata is incomplete.");
        }
    }
}
```

```java
// CockpitLayoutDocument.java
package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CockpitLayoutDocument(
        int schemaVersion,
        String name,
        Map<CockpitZone, ZoneLayout> zones,
        SplitRatios ratios,
        Set<String> compactModuleKeys) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CockpitLayoutDocument {
        zones = zones == null ? Map.of() : Map.copyOf(zones);
        compactModuleKeys = compactModuleKeys == null ? Set.of() : Set.copyOf(compactModuleKeys);
    }

    public record ZoneLayout(
            List<String> moduleKeys,
            String activeModuleKey,
            boolean collapsed) {
        public ZoneLayout {
            moduleKeys = moduleKeys == null ? List.of() : List.copyOf(moduleKeys);
        }
    }

    public record SplitRatios(double left, double primary, double right, double bottom) {
    }
}
```

- [ ] **Step 4: Add the complete standard registry**

Build `CockpitModuleRegistry.standard()` from a `LinkedHashMap` so API and test order are deterministic. Use the exact catalog table above. Use existing sources for the six mountable modules and the B1 deferred fragment for the remaining four:

```java
package dev.hendrikhoemberg.dmhelper.session.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitModuleRegistry {
    private final Map<String, CockpitModuleDefinition> modules;

    public CockpitModuleRegistry() {
        this(standardDefinitions());
    }

    CockpitModuleRegistry(List<CockpitModuleDefinition> definitions) {
        LinkedHashMap<String, CockpitModuleDefinition> indexed = new LinkedHashMap<>();
        for (CockpitModuleDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate cockpit module: " + definition.key());
            }
        }
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    public static CockpitModuleRegistry standard() {
        return new CockpitModuleRegistry(standardDefinitions());
    }

    private static List<CockpitModuleDefinition> standardDefinitions() {
        return List.of(
                module("story", "Story", "session/_story-rail :: story", 240, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.FILTER, "Select a scene to begin."),
                module("map", "Map", "session/cockpit :: map-module", 420, 300,
                        Set.of(CockpitZone.PRIMARY), false, true,
                        CockpitScreenSafetyBehavior.FILTER, "Choose or create a workspace map."),
                module("encounter", "Encounter", "session/_encounter-rail :: encounters", 280, 260,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.RIGHT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Link or create an encounter for this scene."),
                module("session-plan", "Session plan", "session/_session-plan :: plan", 220, 180,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Create a session plan when the session starts."),
                module("party", "Party", "party/_summary-bar :: summary-bar", 220, 140,
                        Set.of(CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY),
                        true, true, CockpitScreenSafetyBehavior.FILTER, "Add party members to this campaign."),
                deferred("quick-notes", "Quick notes", 220, 140,
                        Set.of(CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Capture a note from the command action."),
                deferred("presentation", "Presentation", 280, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.RIGHT_SUPPORT), true, true,
                        CockpitScreenSafetyBehavior.FILTER, "Nothing is currently presented."),
                deferred("reference", "Reference", 260, 220,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT),
                        true, true, CockpitScreenSafetyBehavior.HIDE, "Search rules and compendium content."),
                module("audio", "Audio", "audio/_cockpit-widget :: cockpit-widget", 220, 112,
                        Set.of(CockpitZone.LEFT_SUPPORT, CockpitZone.RIGHT_SUPPORT, CockpitZone.BOTTOM_UTILITY),
                        true, true, CockpitScreenSafetyBehavior.FILTER, "No audio cue is selected."),
                deferred("session-log", "Session log", 280, 180,
                        Set.of(CockpitZone.PRIMARY, CockpitZone.BOTTOM_UTILITY), true, true,
                        CockpitScreenSafetyBehavior.HIDE, "Session events will appear after play begins.")
        );
    }

    public List<CockpitModuleDefinition> all() {
        return List.copyOf(modules.values());
    }

    public CockpitModuleDefinition require(String key) {
        CockpitModuleDefinition definition = modules.get(key);
        if (definition == null) throw new IllegalArgumentException("Unknown cockpit module: " + key);
        return definition;
    }

    public boolean contains(String key) {
        return modules.containsKey(key);
    }

    private static CockpitModuleDefinition module(
            String key, String title, String fragment, int minWidth, int minHeight,
            Set<CockpitZone> zones, boolean compact, boolean focus,
            CockpitScreenSafetyBehavior safety, String empty) {
        return new CockpitModuleDefinition(key, title,
                new CockpitModuleSource(CockpitModuleSource.Kind.THYMELEAF_FRAGMENT, fragment),
                minWidth, minHeight, zones, compact, focus, safety,
                new CockpitModuleStateContract(empty, "Loading " + title + "…",
                        title + " could not refresh. Existing content was kept.", true));
    }

    private static CockpitModuleDefinition deferred(
            String key, String title, int minWidth, int minHeight, Set<CockpitZone> zones,
            boolean compact, boolean focus, CockpitScreenSafetyBehavior safety, String empty) {
        return module(key, title, "session/_cockpit-deferred-modules :: " + key,
                minWidth, minHeight, zones, compact, focus, safety, empty);
    }
}
```

The no-argument constructor is the Spring component path. `standard()` gives plain unit tests the
same catalog without starting an application context; both paths call `standardDefinitions()`.

- [ ] **Step 5: Run the registry test**

Run:

```bash
./mvnw -Dtest=CockpitModuleRegistryTest test
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit the typed contract**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/layout \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java
git commit -m "feat(cockpit): define layout and module contracts"
```

---

### Task 2: Add Immutable Built-ins, Strict Validation, and Recovery

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutValidator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutResolver.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutValidatorTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutResolverTest.java`

**Interfaces:**
- Consumes: the Task 1 registry and layout records.
- Produces: `CockpitBuiltInPresetCatalog.all()` and `require(String)`.
- Produces: `CockpitLayoutValidator.validateForSave(CockpitLayoutDocument): void`.
- Produces: `CockpitLayoutResolver.resolve(CockpitLayoutDocument): Resolution`.
- Produces: `Resolution(document, warnings, fallbackKey)` where `fallbackKey` is null unless a built-in replacement was required.

- [ ] **Step 1: Write failing built-in and validation tests**

```java
class CockpitBuiltInPresetCatalogTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
    private final CockpitLayoutValidator validator = new CockpitLayoutValidator(registry);
    private final CockpitBuiltInPresetCatalog catalog = new CockpitBuiltInPresetCatalog();

    @Test
    void exposesTheFiveApprovedPresetsInOrder() {
        assertThat(catalog.all()).extracting(CockpitBuiltInPresetCatalog.BuiltInPreset::key)
                .containsExactly("builtin:exploration", "builtin:combat",
                        "builtin:theatre-of-mind", "builtin:presentation",
                        "builtin:session-review");
    }

    @Test
    void everyBuiltInPassesTheSameValidatorAsCustomPresets() {
        assertThatCode(() -> catalog.all().forEach(p -> validator.validateForSave(p.layout())))
                .doesNotThrowAnyException();
    }

    @Test
    void combatPresetHasTheApprovedModulePlacement() {
        CockpitLayoutDocument layout = catalog.require("builtin:combat").layout();
        assertThat(layout.zones().get(CockpitZone.PRIMARY).moduleKeys()).containsExactly("map");
        assertThat(layout.zones().get(CockpitZone.LEFT_SUPPORT).moduleKeys())
                .containsExactly("story", "party");
        assertThat(layout.zones().get(CockpitZone.RIGHT_SUPPORT).moduleKeys())
                .containsExactly("encounter");
        assertThat(layout.zones().get(CockpitZone.BOTTOM_UTILITY).moduleKeys())
                .containsExactly("quick-notes", "audio");
    }
}
```

```java
class CockpitLayoutValidatorTest {
    private final CockpitLayoutValidator validator =
            new CockpitLayoutValidator(CockpitModuleRegistry.standard());
    private final CockpitLayoutDocument valid =
            new CockpitBuiltInPresetCatalog().require("builtin:exploration").layout();

    @Test
    void rejectsDuplicateUnknownDisallowedAndEmptyPrimaryModules() {
        assertThatThrownBy(() -> validator.validateForSave(withPrimary(List.of())))
                .hasMessageContaining("Primary requires a module");
        assertThatThrownBy(() -> validator.validateForSave(withPrimary(List.of("map", "map"))))
                .hasMessageContaining("appears more than once");
        assertThatThrownBy(() -> validator.validateForSave(withPrimary(List.of("pdf-reader"))))
                .hasMessageContaining("Unknown cockpit module");
        assertThatThrownBy(() -> validator.validateForSave(withRight(List.of("map"))))
                .hasMessageContaining("map is not allowed in RIGHT_SUPPORT");
    }

    @Test
    void rejectsInvalidRatiosCollapseAndActiveTab() {
        assertThatThrownBy(() -> validator.validateForSave(withRatios(0.1, 0.7, 0.2, 0.24)))
                .hasMessageContaining("Primary ratio");
        assertThatThrownBy(() -> validator.validateForSave(withCollapsed(CockpitZone.PRIMARY)))
                .hasMessageContaining("Only Bottom utility may collapse");
        assertThatThrownBy(() -> validator.validateForSave(withActive(CockpitZone.RIGHT_SUPPORT, "audio")))
                .hasMessageContaining("active tab");
    }
}
```

```java
class CockpitLayoutResolverTest {
    private final CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
    private final CockpitBuiltInPresetCatalog builtIns = new CockpitBuiltInPresetCatalog();
    private final CockpitLayoutResolver resolver = new CockpitLayoutResolver(registry, builtIns);

    @Test
    void omitsMissingKeysRepairsActiveTabsAndClampsRatios() {
        CockpitLayoutDocument damaged = damagedExploration(
                List.of("party", "removed-module"), "removed-module",
                new CockpitLayoutDocument.SplitRatios(.05, .80, .15, .90));

        CockpitLayoutResolver.Resolution result = resolver.resolve(damaged);

        assertThat(result.document().zones().get(CockpitZone.RIGHT_SUPPORT).moduleKeys())
                .containsExactly("party");
        assertThat(result.document().zones().get(CockpitZone.RIGHT_SUPPORT).activeModuleKey())
                .isEqualTo("party");
        assertThat(result.document().ratios().primary()).isBetween(.50, .65);
        assertThat(result.document().ratios().bottom()).isEqualTo(.40);
        assertThat(result.warnings()).anyMatch(w -> w.contains("removed-module"));
        assertThat(result.fallbackKey()).isNull();
    }

    @Test
    void unreadableOrFutureDocumentsFallBackWithoutThrowing() {
        CockpitLayoutDocument future = new CockpitLayoutDocument(
                99, "Future", Map.of(), new CockpitLayoutDocument.SplitRatios(0, 0, 0, 0), Set.of());
        CockpitLayoutResolver.Resolution result = resolver.resolve(future);
        assertThat(result.fallbackKey()).isEqualTo("builtin:exploration");
        assertThat(result.warnings()).contains("This layout uses an unsupported schema. Exploration was restored.");
    }

    @Test
    void invalidPlacementChoosesTheNearestBuiltIn() {
        CockpitLayoutDocument combatLike = invalidCombatWithEmptyPrimary();
        assertThat(resolver.resolve(combatLike).fallbackKey()).isEqualTo("builtin:combat");
    }
}
```

Add these helpers to `CockpitLayoutValidatorTest`:

```java
private CockpitLayoutDocument withPrimary(List<String> keys) {
    return withZone(CockpitZone.PRIMARY,
            new CockpitLayoutDocument.ZoneLayout(
                    keys, keys.isEmpty() ? null : keys.getFirst(), false));
}

private CockpitLayoutDocument withRight(List<String> keys) {
    return withZone(CockpitZone.RIGHT_SUPPORT,
            new CockpitLayoutDocument.ZoneLayout(
                    keys, keys.isEmpty() ? null : keys.getFirst(), false));
}

private CockpitLayoutDocument withCollapsed(CockpitZone zone) {
    CockpitLayoutDocument.ZoneLayout old = valid.zones().get(zone);
    return withZone(zone, new CockpitLayoutDocument.ZoneLayout(
            old.moduleKeys(), old.activeModuleKey(), true));
}

private CockpitLayoutDocument withActive(CockpitZone zone, String key) {
    CockpitLayoutDocument.ZoneLayout old = valid.zones().get(zone);
    return withZone(zone, new CockpitLayoutDocument.ZoneLayout(
            old.moduleKeys(), key, old.collapsed()));
}

private CockpitLayoutDocument withRatios(double left, double primary, double right, double bottom) {
    return new CockpitLayoutDocument(valid.schemaVersion(), valid.name(), valid.zones(),
            new CockpitLayoutDocument.SplitRatios(left, primary, right, bottom),
            valid.compactModuleKeys());
}

private CockpitLayoutDocument withZone(
        CockpitZone zone, CockpitLayoutDocument.ZoneLayout replacement) {
    EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> zones =
            new EnumMap<>(valid.zones());
    zones.put(zone, replacement);
    return new CockpitLayoutDocument(valid.schemaVersion(), valid.name(), zones,
            valid.ratios(), valid.compactModuleKeys());
}
```

Add these helpers to `CockpitLayoutResolverTest`:

```java
private CockpitLayoutDocument damagedExploration(
        List<String> rightKeys,
        String rightActive,
        CockpitLayoutDocument.SplitRatios ratios) {
    CockpitLayoutDocument source = builtIns.require("builtin:exploration").layout();
    EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> zones =
            new EnumMap<>(source.zones());
    zones.put(CockpitZone.RIGHT_SUPPORT,
            new CockpitLayoutDocument.ZoneLayout(rightKeys, rightActive, false));
    return new CockpitLayoutDocument(1, source.name(), zones, ratios,
            source.compactModuleKeys());
}

private CockpitLayoutDocument invalidCombatWithEmptyPrimary() {
    CockpitLayoutDocument source = builtIns.require("builtin:combat").layout();
    EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> zones =
            new EnumMap<>(source.zones());
    zones.put(CockpitZone.PRIMARY,
            new CockpitLayoutDocument.ZoneLayout(List.of(), null, false));
    return new CockpitLayoutDocument(1, source.name(), zones, source.ratios(),
            source.compactModuleKeys());
}
```

Import `java.util.EnumMap`. These helpers always return a new document and never mutate a catalog
document.

- [ ] **Step 2: Run the three tests and observe missing implementations**

Run:

```bash
./mvnw -Dtest=CockpitBuiltInPresetCatalogTest,CockpitLayoutValidatorTest,CockpitLayoutResolverTest test
```

Expected: test compilation fails on the three missing production types.

- [ ] **Step 3: Implement the five exact built-ins**

Annotate `CockpitBuiltInPresetCatalog`, `CockpitLayoutValidator`, and `CockpitLayoutResolver` with
`@org.springframework.stereotype.Component` and use constructor injection for the registry/catalog
dependencies. Unit tests continue to instantiate them directly.

`CockpitBuiltInPresetCatalog` must build documents with default ratios `.20/.56/.24/.24`, the placements below, the first module active in every zone, and compact preferences for every support/utility module that supports compact mode:

```java
private static final List<BuiltInPreset> PRESETS = List.of(
    preset("builtin:exploration", "Exploration",
        zone("story"), zone("session-plan"), zone("party", "quick-notes"),
        collapsedZone("audio", "session-log")),
    preset("builtin:combat", "Combat",
        zone("map"), zone("story", "party"), zone("encounter"),
        zone("quick-notes", "audio")),
    preset("builtin:theatre-of-mind", "Theatre of Mind",
        zone("encounter"), zone("story"), zone("party", "quick-notes"),
        collapsedZone("audio", "session-log")),
    preset("builtin:presentation", "Presentation",
        zone("map"), zone("story"), zone("presentation"), collapsedZone()),
    preset("builtin:session-review", "Session Review",
        zone("session-log"), zone("session-plan"), zone("quick-notes", "party"),
        collapsedZone())
);
```

Use `Map.of(PRIMARY, primary, LEFT_SUPPORT, left, RIGHT_SUPPORT, right, BOTTOM_UTILITY, bottom)` and `Set.of(...)`. `BuiltInPreset` is:

```java
public record BuiltInPreset(String key, String name, CockpitLayoutDocument layout) {
}
```

The exact `compactModuleKeys` sets are:

```java
// Exploration
Set.of("session-plan", "party", "quick-notes", "audio", "session-log")
// Combat
Set.of("story", "party", "encounter", "quick-notes", "audio")
// Theatre of Mind
Set.of("story", "party", "quick-notes", "audio", "session-log")
// Presentation
Set.of("story", "presentation")
// Session Review
Set.of("session-plan", "quick-notes", "party")
```

`all()` returns `PRESETS`; `require(key)` throws `IllegalArgumentException("Unknown built-in cockpit preset: " + key)`.

- [ ] **Step 4: Implement strict save validation**

`CockpitLayoutValidator.validateForSave` must accumulate deterministic messages and throw one `IllegalArgumentException` containing `"; "`-joined problems. Validate:

```java
if (layout == null) problem("Layout is required");
if (layout.schemaVersion() != 1) problem("Layout schema version must be 1");
if (layout.name() == null || layout.name().isBlank() || layout.name().trim().length() > 80)
    problem("Preset name must contain 1 to 80 characters");
if (!layout.zones().keySet().equals(EnumSet.allOf(CockpitZone.class)))
    problem("Layout must contain all four cockpit zones");
```

Then, in enum order:

1. require non-null `ZoneLayout`;
2. allow `collapsed=true` only for `BOTTOM_UTILITY`;
3. require a non-empty Primary;
4. reject duplicate module keys globally;
5. reject unknown module keys;
6. reject modules outside `allowedZones`;
7. require `activeModuleKey` to be null for an empty zone and a member for a populated zone;
8. reject compact keys that are absent or whose module does not support compact;
9. require finite ratios, sum `1.0 ± .001`, Primary `[.50,.65]`, Bottom `[.16,.40]`, and positive Left/Right.

- [ ] **Step 5: Implement repair and nearest-built-in resolution**

`CockpitLayoutResolver.resolve` follows this exact order:

1. null or schema other than `1` → Exploration plus one warning;
2. visit zones in `PRIMARY`, `LEFT_SUPPORT`, `RIGHT_SUPPORT`, `BOTTOM_UTILITY` order;
3. omit unknown, duplicate, and disallowed module keys and add one warning per omitted key;
4. replace an invalid active key with the first surviving key;
5. clear collapse on every zone except Bottom;
6. remove invalid compact keys;
7. normalize horizontal ratios to sum to one, clamp Primary to `[.50,.65]`, distribute the remainder between positive Left/Right proportionally, and clamp Bottom to `[.16,.40]`;
8. strict-validate the repaired document;
9. if strict validation still fails, select the built-in with the highest placement score and add a fallback warning.

The placement score is deterministic:

```java
private int score(CockpitLayoutDocument candidate, CockpitLayoutDocument builtIn) {
    int score = 0;
    for (CockpitZone zone : CockpitZone.values()) {
        List<String> actual = candidate.zones().getOrDefault(
                zone, new CockpitLayoutDocument.ZoneLayout(List.of(), null, false)).moduleKeys();
        List<String> expected = builtIn.zones().get(zone).moduleKeys();
        for (String key : actual) {
            if (expected.contains(key)) score += 3;
            else if (builtIn.zones().values().stream().anyMatch(z -> z.moduleKeys().contains(key))) score += 1;
        }
        if (!actual.isEmpty() && !expected.isEmpty() && actual.getFirst().equals(expected.getFirst())) score += 2;
    }
    return score;
}
```

Resolve ties by catalog order. `Resolution` is:

```java
public record Resolution(
        CockpitLayoutDocument document,
        List<String> warnings,
        String fallbackKey) {
    public Resolution {
        warnings = List.copyOf(warnings);
    }
}
```

- [ ] **Step 6: Run the layout-model suite**

Run:

```bash
./mvnw -Dtest=CockpitModuleRegistryTest,CockpitBuiltInPresetCatalogTest,CockpitLayoutValidatorTest,CockpitLayoutResolverTest test
```

Expected: all tests pass.

- [ ] **Step 7: Commit built-ins and recovery**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/layout \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/layout
git commit -m "feat(cockpit): validate and recover layout presets"
```

---

### Task 3: Persist Application-Local Custom Presets

**Files:**
- Create: `src/main/resources/db/migration/V22__add_cockpit_layout_presets.sql`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPreset.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPresetRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodec.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodecTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/data/CockpitLayoutPresetRepositoryTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java`

**Interfaces:**
- Consumes: schema-v1 `CockpitLayoutDocument`.
- Produces: migration version `22`.
- Produces: `CockpitLayoutCodec.write(document): String` and `read(json): CockpitLayoutDocument`.
- Produces: repository methods `findAllByOrderByNormalizedNameAsc`, `existsByNormalizedName`, and `existsByNormalizedNameAndIdNot`.

- [ ] **Step 1: Write failing migration, codec, and repository tests**

Add to `FlywayMigrationTest`:

```java
@Test
void v22CreatesApplicationLocalCockpitPresetStorage() {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '22' AND \"success\" = TRUE",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'COCKPIT_LAYOUT_PRESET'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'COCKPIT_LAYOUT_PRESET' AND column_name = 'CAMPAIGN_ID'
            """, Integer.class)).isZero();
}
```

Add to `FlywayLegacyUpgradeTest`:

```java
@Test
void appliesV22WithoutChangingTheLegacyCampaign() {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '22' AND \"success\" = TRUE",
            Integer.class)).isEqualTo(1);
    assertThat(campaigns.findAll()).extracting(Campaign::getName).contains("Curse of Strahd");
}
```

Change `ThreatMigrationTest.flywayReportsTwentyOneMigrations` to `flywayReportsTwentyTwoMigrations`, expect `22`, and expect current version `"22"`.

Create codec test:

```java
class CockpitLayoutCodecTest {
    private final CockpitLayoutCodec codec =
            new CockpitLayoutCodec(tools.jackson.databind.json.JsonMapper.builder().build());

    @Test
    void roundTripsTheVersionedDocumentWithoutLosingOrder() {
        CockpitLayoutDocument source =
                new CockpitBuiltInPresetCatalog().require("builtin:combat").layout();
        assertThat(codec.read(codec.write(source))).isEqualTo(source);
        assertThat(codec.write(source)).contains("\"schemaVersion\":1", "\"moduleKeys\":[\"story\",\"party\"]");
    }

    @Test
    void rejectsUnreadableJsonWithAStableMessage() {
        assertThatThrownBy(() -> codec.read("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cockpit layout JSON could not be read.");
    }
}
```

Create this `@DataJpaTest` repository coverage:

```java
@DataJpaTest
class CockpitLayoutPresetRepositoryTest {
    @Autowired CockpitLayoutPresetRepository repository;

    @Test
    void ordersByNormalizedNameAndAdvancesTheOptimisticVersion() {
        CockpitLayoutPreset zed = preset("Zed", "zed");
        CockpitLayoutPreset alpha = preset("Alpha", "alpha");
        repository.saveAndFlush(zed);
        repository.saveAndFlush(alpha);

        assertThat(repository.findAllByOrderByNormalizedNameAsc())
                .extracting(CockpitLayoutPreset::getName)
                .containsExactly("Alpha", "Zed");

        long before = zed.getVersion();
        zed.setName("Zed table");
        repository.saveAndFlush(zed);
        assertThat(zed.getVersion()).isEqualTo(before + 1);
    }

    @Test
    void normalizedNameIsUnique() {
        repository.saveAndFlush(preset("Combat", "combat"));
        assertThatThrownBy(() -> repository.saveAndFlush(preset("COMBAT", "combat")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CockpitLayoutPreset preset(String name, String normalized) {
        CockpitLayoutPreset entity = new CockpitLayoutPreset();
        entity.setName(name);
        entity.setNormalizedName(normalized);
        entity.setLayoutSchemaVersion(1);
        entity.setLayoutJson("{\"schemaVersion\":1}");
        return entity;
    }
}
```

The service-level stale-version test in Task 4 proves the user-visible optimistic conflict path
without relying on two persistence contexts inside one `@DataJpaTest`.

- [ ] **Step 2: Run the red persistence tests**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,ThreatMigrationTest,CockpitLayoutCodecTest,CockpitLayoutPresetRepositoryTest test
```

Expected: failures report missing V22, missing persistence types, and migration count `21`.

- [ ] **Step 3: Add migration V22**

```sql
create table cockpit_layout_preset (
    id uuid not null,
    name varchar(80) not null,
    normalized_name varchar(80) not null,
    layout_schema_version integer not null,
    layout_json clob not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    primary key (id),
    constraint uq_cockpit_layout_preset_name unique (normalized_name),
    constraint ck_cockpit_layout_preset_name
        check (char_length(trim(name)) between 1 and 80),
    constraint ck_cockpit_layout_preset_schema
        check (layout_schema_version = 1)
);
```

Do not add `campaign_id`, cascade rules, package keys, or a seed row for built-ins.

- [ ] **Step 4: Add the entity and repository**

```java
@Getter
@Setter
@Entity
@Table(name = "cockpit_layout_preset")
public class CockpitLayoutPreset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 80)
    private String normalizedName;

    @Column(name = "layout_schema_version", nullable = false)
    private int layoutSchemaVersion;

    @Column(name = "layout_json", nullable = false, columnDefinition = "CLOB")
    private String layoutJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void createTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
```

```java
public interface CockpitLayoutPresetRepository extends JpaRepository<CockpitLayoutPreset, UUID> {
    List<CockpitLayoutPreset> findAllByOrderByNormalizedNameAsc();
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);
}
```

- [ ] **Step 5: Add the JSON codec**

```java
@Component
public final class CockpitLayoutCodec {
    private final ObjectMapper mapper;

    public CockpitLayoutCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(CockpitLayoutDocument document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalArgumentException("Cockpit layout JSON could not be written.", ex);
        }
    }

    public CockpitLayoutDocument read(String json) {
        try {
            return mapper.readValue(json, CockpitLayoutDocument.class);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalArgumentException("Cockpit layout JSON could not be read.", ex);
        }
    }
}
```

- [ ] **Step 6: Run persistence tests**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,ThreatMigrationTest,CockpitLayoutCodecTest,CockpitLayoutPresetRepositoryTest test
```

Expected: all selected tests pass and Flyway reports version `22`.

- [ ] **Step 7: Commit persistence**

```bash
git add src/main/resources/db/migration/V22__add_cockpit_layout_presets.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/data \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodec.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/data \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitLayoutCodecTest.java
git commit -m "feat(cockpit): persist custom layout presets"
```

---

### Task 4: Expose Registry and Preset CRUD Without Campaign Mutations

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiController.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetServiceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiControllerTest.java`

**Interfaces:**
- Produces: `PresetDto(key, id, name, builtIn, version, layout, warnings)`.
- Produces: `GET /api/v1/cockpit-layout/modules`.
- Produces: `GET /api/v1/cockpit-layout/presets`.
- Produces: `POST /api/v1/cockpit-layout/presets`.
- Produces: `PUT /api/v1/cockpit-layout/presets/{id}`.
- Produces: `DELETE /api/v1/cockpit-layout/presets/{id}`.
- Consumes: global `OptimisticLockingFailureException` mapping to HTTP 409.

Annotate `CockpitLayoutPresetService` with `@Service`; inject the repository, codec, built-in
catalog, validator, and resolver through its only constructor.

- [ ] **Step 1: Write failing service tests**

Cover these exact cases:

```java
@Test
void listPlacesImmutableBuiltInsBeforeNamedCustomPresets() {
    when(repository.findAllByOrderByNormalizedNameAsc()).thenReturn(List.of(customEntity()));
    assertThat(service.list()).extracting(CockpitLayoutPresetService.PresetDto::key)
            .containsExactly("builtin:exploration", "builtin:combat",
                    "builtin:theatre-of-mind", "builtin:presentation",
                    "builtin:session-review", "custom:" + CUSTOM_ID);
}

@Test
void createTrimsNameValidatesLayoutAndStoresNormalizedName() {
    CockpitLayoutPresetService.PresetDto created =
            service.create(new SavePresetRequest("  My Table  ", explorationNamed("My Table")));
    ArgumentCaptor<CockpitLayoutPreset> saved = ArgumentCaptor.forClass(CockpitLayoutPreset.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getName()).isEqualTo("My Table");
    assertThat(saved.getValue().getNormalizedName()).isEqualTo("my table");
    assertThat(created.builtIn()).isFalse();
}

@Test
void duplicateNameAndMismatchedDocumentNameAreRejected() {
    when(repository.existsByNormalizedName("combat copy")).thenReturn(true);
    assertThatThrownBy(() -> service.create(
            new SavePresetRequest("Combat Copy", explorationNamed("Combat Copy"))))
            .hasMessage("A cockpit preset named 'Combat Copy' already exists.");
    assertThatThrownBy(() -> service.create(
            new SavePresetRequest("A", explorationNamed("B"))))
            .hasMessage("Preset name and layout name must match.");
}

@Test
void unreadableStoredJsonFallsBackAndReturnsAWarning() {
    CockpitLayoutPreset broken = customEntity();
    broken.setLayoutJson("{broken");
    when(repository.findAllByOrderByNormalizedNameAsc()).thenReturn(List.of(broken));
    assertThat(service.list().getLast()).satisfies(dto -> {
        assertThat(dto.name()).isEqualTo("My Table");
        assertThat(dto.layout().name()).isEqualTo("My Table");
        assertThat(dto.warnings()).contains(
                "The saved preset could not be read. Exploration was used as its recoverable layout.");
    });
}

@Test
void updateRejectsAStaleRequestVersionBeforeWriting() {
    CockpitLayoutPreset existing = customEntity();
    existing.setVersion(7);
    when(repository.findById(CUSTOM_ID)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.update(CUSTOM_ID,
            new UpdatePresetRequest("My Table", 6, explorationNamed("My Table"))))
            .isInstanceOf(OptimisticLockingFailureException.class);

    verify(repository, never()).saveAndFlush(any());
}

@Test
void deleteTouchesOnlyTheApplicationLocalPresetRepository() {
    when(repository.existsById(CUSTOM_ID)).thenReturn(true);
    service.delete(CUSTOM_ID);
    verify(repository).existsById(CUSTOM_ID);
    verify(repository).deleteById(CUSTOM_ID);
    verifyNoMoreInteractions(repository);
}
```

Use this fixture in the same test class:

```java
@ExtendWith(MockitoExtension.class)
class CockpitLayoutPresetServiceTest {
    private static final UUID CUSTOM_ID =
            UUID.fromString("31ae49e1-0182-44aa-bdb5-127dc75197c9");
    @Mock CockpitLayoutPresetRepository repository;
    @Mock CockpitLayoutCodec codec;
    CockpitBuiltInPresetCatalog builtIns;
    CockpitLayoutPresetService service;

    @BeforeEach
    void setUp() {
        CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
        builtIns = new CockpitBuiltInPresetCatalog();
        service = new CockpitLayoutPresetService(repository, codec, builtIns,
                new CockpitLayoutValidator(registry),
                new CockpitLayoutResolver(registry, builtIns));
        lenient().when(codec.write(any())).thenReturn("{\"schemaVersion\":1}");
        lenient().when(codec.read(any())).thenReturn(explorationNamed("My Table"));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            CockpitLayoutPreset entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(CUSTOM_ID);
            return entity;
        });
    }

    private CockpitLayoutPreset customEntity() {
        CockpitLayoutPreset entity = new CockpitLayoutPreset();
        entity.setId(CUSTOM_ID);
        entity.setName("My Table");
        entity.setNormalizedName("my table");
        entity.setLayoutSchemaVersion(1);
        entity.setLayoutJson("{\"schemaVersion\":1}");
        return entity;
    }

    private CockpitLayoutDocument explorationNamed(String name) {
        CockpitLayoutDocument source =
                builtIns.require("builtin:exploration").layout();
        return new CockpitLayoutDocument(1, name, source.zones(),
                source.ratios(), source.compactModuleKeys());
    }
}
```

Place the six test methods inside that class. In the unreadable-JSON test, override the lenient
codec stub with:

```java
when(codec.read("{broken")).thenThrow(
        new IllegalArgumentException("Cockpit layout JSON could not be read."));
```

- [ ] **Step 2: Run the service test**

Run:

```bash
./mvnw -Dtest=CockpitLayoutPresetServiceTest test
```

Expected: compilation fails because service DTOs and methods do not exist.

- [ ] **Step 3: Implement the transactional service**

Use these records and signatures:

```java
public record PresetDto(
        String key,
        UUID id,
        String name,
        boolean builtIn,
        long version,
        CockpitLayoutDocument layout,
        List<String> warnings) {
}

public record SavePresetRequest(String name, CockpitLayoutDocument layout) {
}

public record UpdatePresetRequest(String name, long version, CockpitLayoutDocument layout) {
}

@Transactional(readOnly = true)
public List<PresetDto> list()

@Transactional
public PresetDto create(SavePresetRequest request)

@Transactional
public PresetDto update(UUID id, UpdatePresetRequest request)

@Transactional
public void delete(UUID id)
```

Normalization is:

```java
private String normalize(String name) {
    return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
}
```

For `list()`, map built-ins first with `version=0`, then custom rows. A stored custom layout is
decoded and passed through `resolver.resolve`. Always copy the custom row's name into the resolved
document, including when a built-in structure was used as fallback, so the recovered document can
be saved back through the normal name-equality rule. Include all resolver warnings. If JSON cannot
be decoded at all, use Exploration's structure, rename the document to the row name, and return the
exact warning asserted above.

For create/update:

1. trim and validate the request name;
2. require `request.layout().name().trim().equals(trimmedName)`;
3. run strict validation;
4. check normalized uniqueness;
5. write JSON and schema version `1`;
6. `saveAndFlush`;
7. return no warnings.

For update, load or throw `NotFoundException("Cockpit preset not found")`, compare
`entity.getVersion()` with `request.version()`, and throw
`new OptimisticLockingFailureException("Cockpit preset changed")` when they differ. Leave the
managed entity's version untouched so Hibernate performs its normal optimistic update. Delete
custom UUID rows only; built-ins have no UUID endpoint.

- [ ] **Step 4: Write failing MockMvc API tests**

```java
@WebMvcTest(CockpitLayoutApiController.class)
@Import(GlobalExceptionHandler.class)
class CockpitLayoutApiControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CockpitLayoutPresetService presets;
    @MockitoBean CockpitModuleRegistry modules;
    private static final UUID CUSTOM_ID =
            UUID.fromString("31ae49e1-0182-44aa-bdb5-127dc75197c9");

    @Test
    void listsModulesAndPresets() throws Exception {
        var layout = new CockpitBuiltInPresetCatalog()
                .require("builtin:exploration").layout();
        when(modules.all()).thenReturn(List.of(
                CockpitModuleRegistry.standard().require("story")));
        when(presets.list()).thenReturn(List.of(
                new PresetDto("builtin:exploration", null, "Exploration",
                        true, 0, layout, List.of())));
        mvc.perform(get("/api/v1/cockpit-layout/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("story"));
        mvc.perform(get("/api/v1/cockpit-layout/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("builtin:exploration"));
    }

    @Test
    void createsUpdatesAndDeletesCustomPresets() throws Exception {
        var source = new CockpitBuiltInPresetCatalog()
                .require("builtin:exploration").layout();
        var layout = new CockpitLayoutDocument(1, "My Table", source.zones(),
                source.ratios(), source.compactModuleKeys());
        var dto = new PresetDto("custom:" + CUSTOM_ID, CUSTOM_ID, "My Table",
                false, 0, layout, List.of());
        when(presets.create(any())).thenReturn(dto);
        when(presets.update(eq(CUSTOM_ID), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/cockpit-layout/presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new SavePresetRequest("My Table", layout))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/cockpit-layout/presets/")));
        mvc.perform(put("/api/v1/cockpit-layout/presets/{id}", CUSTOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new UpdatePresetRequest("My Table", 0, layout))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/cockpit-layout/presets/{id}", CUSTOM_ID))
                .andExpect(status().isNoContent());

        verify(presets).create(any());
        verify(presets).update(eq(CUSTOM_ID), any());
        verify(presets).delete(CUSTOM_ID);
    }
}
```

Use static imports for the three nested service records, Mockito matchers/verifiers, MockMvc
builders/results, and Hamcrest `containsString`.

- [ ] **Step 5: Implement the REST controller**

```java
@RestController
@RequestMapping("/api/v1/cockpit-layout")
public class CockpitLayoutApiController {
    private final CockpitModuleRegistry modules;
    private final CockpitLayoutPresetService presets;

    public CockpitLayoutApiController(
            CockpitModuleRegistry modules,
            CockpitLayoutPresetService presets) {
        this.modules = modules;
        this.presets = presets;
    }

    @GetMapping("/modules")
    List<CockpitModuleDefinition> modules() {
        return modules.all();
    }

    @GetMapping("/presets")
    List<CockpitLayoutPresetService.PresetDto> presets() {
        return presets.list();
    }

    @PostMapping("/presets")
    ResponseEntity<CockpitLayoutPresetService.PresetDto> create(
            @RequestBody CockpitLayoutPresetService.SavePresetRequest request) {
        var created = presets.create(request);
        return ResponseEntity.created(URI.create(
                "/api/v1/cockpit-layout/presets/" + created.id())).body(created);
    }

    @PutMapping("/presets/{id}")
    CockpitLayoutPresetService.PresetDto update(
            @PathVariable UUID id,
            @RequestBody CockpitLayoutPresetService.UpdatePresetRequest request) {
        return presets.update(id, request);
    }

    @DeleteMapping("/presets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        presets.delete(id);
    }
}
```

- [ ] **Step 6: Run service and API tests**

Run:

```bash
./mvnw -Dtest=CockpitLayoutPresetServiceTest,CockpitLayoutApiControllerTest test
```

Expected: all selected tests pass, including 400 validation, 404 missing row, and 409 stale version cases.

- [ ] **Step 7: Commit the server boundary**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiController.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitLayoutApiControllerTest.java
git commit -m "feat(cockpit): expose layout preset API"
```

---

### Task 5: Render Accessible Module Shells and the Four-Zone Workbench

**Files:**
- Create: `src/main/resources/templates/session/_cockpit-module-shell.html`
- Create: `src/main/resources/templates/session/_cockpit-deferred-modules.html`
- Create: `src/main/resources/templates/session/_cockpit-workbench.html`
- Create: `src/main/resources/static/css/cockpit-layout.css`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitWorkbenchTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeModuleSafetyContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java`

**Interfaces:**
- Consumes: module metadata and preset DTOs from Tasks 1–4.
- Produces: one `[data-cockpit-workbench]`, four `[data-cockpit-zone]`, three adjacent splitters, and one unique shell per registered module.
- Produces: `window.cockpitLayoutConfig = {campaignId, modules, presets, defaultPresetKey}` before `cockpit-layout.js` executes.
- Produces: command controls with IDs used by the client: `cockpitPresetPicker`, `cockpitLayoutModeButton`, `cockpitFocusReturn`, `cockpitAddModuleButton`.

- [ ] **Step 1: Write the failing rendered-template contract**

Use `@SpringBootTest`, `SpringTemplateEngine`, the standard registry, and built-in DTOs to render the workbench fragment. Parse with jsoup and assert:

```java
assertThat(document.select("[data-cockpit-workbench]")).hasSize(1);
assertThat(document.select("[data-cockpit-zone]")).extracting(e -> e.attr("data-cockpit-zone"))
        .containsExactlyInAnyOrder("PRIMARY", "LEFT_SUPPORT", "RIGHT_SUPPORT", "BOTTOM_UTILITY");
assertThat(document.select("[role=separator][aria-orientation]")).hasSize(3);
assertThat(document.select(".cockpit-module[data-module-key]")).extracting(e -> e.attr("data-module-key"))
        .containsExactlyInAnyOrderElementsOf(registry.all().stream()
                .map(CockpitModuleDefinition::key).toList());
assertThat(document.select(".cockpit-module[data-module-key]")).extracting(e -> e.attr("data-module-key"))
        .doesNotHaveDuplicates();
assertThat(document.select("#cockpitLayoutModeButton")).singleElement()
        .satisfies(button -> assertThat(button.text()).contains("Edit layout"));
assertThat(document.select("[data-layout-edit-only]")).allSatisfy(
        node -> assertThat(node.hasAttr("hidden")).isTrue());
```

Extend `RuntimeModuleSafetyContractTest` so every registry key has exactly one rendered module root and its `data-table-safe-behavior` equals the registry classification.

- [ ] **Step 2: Run template contracts and observe missing workbench markup**

Run:

```bash
./mvnw -Dtest=CockpitWorkbenchTemplateContractTest,SessionCockpitTemplateContractTest,RuntimeModuleSafetyContractTest test
```

Expected: failures report no workbench, zones, shells, splitters, or layout controls.

- [ ] **Step 3: Add the module shell fragment**

Use this semantic shape for every module:

```html
<article th:fragment="shell(module, body)"
         class="cockpit-module"
         th:id="|cockpitModule-${module.key}|"
         th:attr="data-module-key=${module.key},
                  data-runtime-module=${module.key},
                  data-min-width=${module.minWidthPx},
                  data-min-height=${module.minHeightPx},
                  data-table-safe-behavior=${module.screenSafetyBehavior}"
         tabindex="-1">
  <header class="cockpit-module__header">
    <h2 th:id="|cockpitModuleTitle-${module.key}|" th:text="${module.title}">Module</h2>
    <span class="cockpit-module__attention" hidden aria-label="0 updates"></span>
    <div class="cockpit-module__actions">
      <button type="button" data-module-focus
              th:if="${module.focusSupported}"
              th:attr="aria-label=|Focus ${module.title}|">Focus</button>
      <button type="button" data-module-menu data-layout-edit-only hidden
              th:attr="aria-label=|Arrange ${module.title}|">Arrange</button>
      <button type="button" data-module-remove data-layout-edit-only hidden
              th:attr="aria-label=|Remove ${module.title}|">Remove</button>
    </div>
  </header>
  <div class="cockpit-module__status" data-module-status aria-live="polite" hidden></div>
  <div class="cockpit-module__body" data-module-body
       th:attr="aria-labelledby=|cockpitModuleTitle-${module.key}|">
    <th:block th:replace="${body}"></th:block>
  </div>
  <div class="cockpit-module__error" data-module-error role="alert" hidden>
    <span th:text="${module.states.errorMessage}">Refresh failed.</span>
    <button type="button" data-module-retry>Retry</button>
  </div>
</article>
```

The concrete workbench may use a `th:switch` rather than passing fragment expressions dynamically if Thymeleaf rejects dynamic `th:replace`; preserve this rendered HTML contract.

- [ ] **Step 4: Add honest deferred module bodies**

The four fragments must provide useful runtime exits without pretending the B2 bodies exist:

```html
<th:block th:fragment="quick-notes">
  <p>Capture a quick note without leaving the session.</p>
  <button type="button" data-open-quick-notes>Open quick-note capture</button>
</th:block>

<th:block th:fragment="presentation">
  <p data-presentation-summary>Table output is controlled by the current presentation status.</p>
  <button type="button" data-open-presentation-preview>Preview table output</button>
</th:block>

<th:block th:fragment="reference">
  <p>Search rules and compendium entries from the cockpit.</p>
  <button type="button" data-open-reference-search>Open search</button>
</th:block>

<th:block th:fragment="session-log">
  <p>Review the current session timeline and completed-session note.</p>
  <button type="button" data-open-session-review>Open session review</button>
</th:block>
```

Wire those buttons to existing `sessionCockpit`/global events only. Do not duplicate notes, presentation, search, or lifecycle state in the layout controller.

- [ ] **Step 5: Add workbench zones, splitters, dialogs, and depot**

The workbench fragment must render:

```html
<section class="cockpit-workbench"
         data-cockpit-workbench
         data-layout-mode="locked"
         aria-label="Session workspace">
  <div class="cockpit-zone cockpit-zone--left" data-cockpit-zone="LEFT_SUPPORT"></div>
  <button type="button" class="cockpit-splitter cockpit-splitter--left"
          data-cockpit-splitter="LEFT_PRIMARY" role="separator"
          aria-orientation="vertical" aria-valuemin="10" aria-valuemax="35"
          aria-valuenow="20" aria-label="Resize left support" tabindex="-1"></button>
  <div class="cockpit-zone cockpit-zone--primary" data-cockpit-zone="PRIMARY"></div>
  <button type="button" class="cockpit-splitter cockpit-splitter--right"
          data-cockpit-splitter="PRIMARY_RIGHT" role="separator"
          aria-orientation="vertical" aria-valuemin="15" aria-valuemax="35"
          aria-valuenow="24" aria-label="Resize right support" tabindex="-1"></button>
  <div class="cockpit-zone cockpit-zone--right" data-cockpit-zone="RIGHT_SUPPORT"></div>
  <button type="button" class="cockpit-splitter cockpit-splitter--bottom"
          data-cockpit-splitter="BOTTOM_UTILITY" role="separator"
          aria-orientation="horizontal" aria-valuemin="16" aria-valuemax="40"
          aria-valuenow="24" aria-label="Resize bottom utility" tabindex="-1"></button>
  <div class="cockpit-zone cockpit-zone--bottom" data-cockpit-zone="BOTTOM_UTILITY"></div>
</section>
```

Each zone receives a `role="tablist"` header and a `data-zone-panels` container. Across the four
zones and the hidden depot, render all ten shells exactly once. The depot exists outside the grid
but inside the `sessionCockpit` Alpine root and contains only modules absent from the current
server-rendered preset. Add:

- Add module `<dialog id="cockpitAddModuleDialog">`;
- arrange menu with allowed-zone commands and Move earlier/Move later;
- save/discard `<dialog id="cockpitLayoutExitDialog">`;
- custom-name `<dialog id="cockpitPresetNameDialog">`;
- non-modal `<div id="cockpitLayoutNotice" role="status" aria-live="polite">`;
- focus layer with `#cockpitFocusReturn`.

- [ ] **Step 6: Render a usable no-JavaScript Exploration layout**

Server-render the default Exploration placement into the four zone panel containers: Story in
Primary; Session plan in Left; Party and Quick notes in Right; Audio and Session log in collapsed
Bottom. Render Map, Encounter, Presentation, and Reference shells in the hidden depot. Tabs use the
first key as active and mark inactive panels `hidden inert`.

Create `cockpit-layout.css` now with this structural baseline:

```css
html:has(.session-cockpit),
body:has(.session-cockpit) {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.session-cockpit {
  height: 100dvh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cockpit-workbench {
  --left-size: 20fr;
  --primary-size: 56fr;
  --right-size: 24fr;
  --top-size: 76fr;
  --bottom-size: 24fr;
  --splitter-size: 6px;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns:
    minmax(0, var(--left-size)) var(--splitter-size)
    minmax(0, var(--primary-size)) var(--splitter-size)
    minmax(0, var(--right-size));
  grid-template-rows:
    minmax(0, var(--top-size))
    var(--splitter-size) minmax(0, var(--bottom-size));
  overflow: hidden;
}

.cockpit-zone--left { grid-column: 1; grid-row: 1; }
.cockpit-splitter--left { grid-column: 2; grid-row: 1; }
.cockpit-zone--primary { grid-column: 3; grid-row: 1; }
.cockpit-splitter--right { grid-column: 4; grid-row: 1; }
.cockpit-zone--right { grid-column: 5; grid-row: 1; }
.cockpit-splitter--bottom { grid-column: 1 / -1; grid-row: 2; }
.cockpit-zone--bottom { grid-column: 1 / -1; grid-row: 3; }

.cockpit-zone,
.cockpit-module,
.cockpit-module__body,
[data-zone-panels],
[role="tabpanel"] {
  min-width: 0;
  min-height: 0;
}

.cockpit-zone,
.cockpit-module {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cockpit-module {
  height: 100%;
}

.cockpit-module__body {
  flex: 1 1 auto;
  overflow: auto;
  overscroll-behavior: contain;
}

.cockpit-workbench [hidden],
.cockpit-module-depot[hidden] {
  display: none !important;
}

.cockpit-zone[data-collapsed="true"],
.cockpit-workbench[data-bottom-collapsed="true"] .cockpit-splitter--bottom {
  display: none;
}

.cockpit-workbench[data-bottom-collapsed="true"] {
  --top-size: 1fr;
  --bottom-size: 0fr;
  grid-template-rows: minmax(0, 1fr) 0 minmax(0, 0fr);
}
```

This prevents an empty or scrolling cockpit between the server-rendered-shell commit and the
client-controller commit. Task 8 expands the file with edit, zoom, focus, target-size, and
reduced-motion rules.

Render preset/edit controls as `disabled aria-disabled="true"` in this no-JavaScript state. Module
content and tab links still work; layout mutation never appears to succeed before the controller
is installed.

- [ ] **Step 7: Compact the command bar and bootstrap the page**

Keep campaign/session identity, date, Screen Safety, presentation status, current preset, one-click layout control, Search, Dice, Session, and one overflow menu. Preserve map/handout/audio actions in the overflow only until B2 relocates them.

In `SessionController.cockpit`, add:

```java
model.addAttribute("cockpitModules", cockpitModules.all());
model.addAttribute("cockpitPresets", cockpitPresets.list());
model.addAttribute("cockpitDefaultPresetKey", "builtin:exploration");
```

Inject `CockpitModuleRegistry` and `CockpitLayoutPresetService` through the constructor. In
`cockpit.html`, load `cockpit-layout.css` immediately after the legacy `cockpit.css` so structural
rules win, and define `window.cockpitLayoutConfig` through
`th:inline="javascript"`. Task 6 adds `cockpit-layout.js` before Alpine. The existing
map, Story, Encounter, plan, Party, and Audio bodies must retain their IDs, Alpine directives,
`data-screen-sensitive` descendants, and server-rendered content. Move their
`data-runtime-module` and `data-table-safe-behavior` attributes to the unique outer module shell so
Screen Safety sees exactly one authoritative boundary per registered key.

Add matching `@MockitoBean CockpitModuleRegistry` and
`@MockitoBean CockpitLayoutPresetService` fields to `SessionControllerTest`; return the standard
catalog and five built-in DTOs in the cockpit-rendering cases so the MVC slice renders the same
bootstrap shape as production.

- [ ] **Step 8: Run template and existing session contracts**

Run:

```bash
./mvnw -Dtest=CockpitWorkbenchTemplateContractTest,SessionCockpitTemplateContractTest,SessionCockpitMapContractTest,RuntimeModuleSafetyContractTest,SessionControllerTest test
```

Expected: all selected tests pass; static inspection finds ten unique modules and no removed current action.

- [ ] **Step 9: Commit the server-rendered workbench**

```bash
git add src/main/resources/templates/session \
  src/main/resources/templates/session/cockpit.html \
  src/main/resources/static/css/cockpit-layout.css \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session
git commit -m "feat(cockpit): render four-zone module workbench"
```

---

### Task 6: Implement Locked Preset Switching and Transient Recovery

**Files:**
- Create: `src/main/resources/static/js/cockpit-layout.js`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/templates/session/_cockpit-workbench.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Consumes: `window.cockpitLayoutConfig`.
- Produces: `window.cockpitLayout`, an instance of `CockpitLayoutController`.
- Produces: `applyPreset(key)`, `enterEditMode()`, `requestExitEditMode()`, `saveEdit()`, `discardEdit()`, `selectTab(zone,key)`, and `setModuleState(key,state,detail)`.
- Produces events: `cockpit:layout-applied`, `cockpit:module-visibility`, and `cockpit:layout-warning`.

- [ ] **Step 1: Add a failing ordered browser test for locked startup and manual presets**

First add this helper to the existing ordered smoke class:

```java
private void selectCockpitPreset(String key) {
    dmPage.waitForFunction("window.cockpitLayout?.mounted === true");
    Locator picker = dmPage.locator("#cockpitPresetPicker");
    if (!key.equals(picker.inputValue())) picker.selectOption(key);
    dmPage.waitForFunction("key => window.cockpitLayout.currentPresetKey === key", key);
}
```

Call `selectCockpitPreset("builtin:combat")` immediately after cockpit navigation in the existing
methods that operate Map or Encounter controls:

```text
screenSafetyToggleHidesAndDisablesSensitiveContent
runsTheCompleteCockpitFlowThroughVisibleControls
failedTokenMoveRollsBackAndRetryPersists
failedNextTurnKeepsTrackerStateAndRetryAdvances
failedPresentationKeepsCurtainAndRetryShowsMap
failedDefeatedToggleRestoresThePersistedAndVisibleState
failedStatblockTokenCreationRetainsTheSearchForRetry
threatWorkflowProvesDmSurfacesAndPackageFidelity
audioCockpitUsesFakeProviderAcrossTheRealSessionFlow
crossMidnightSessionWithDefeatSequence
```

This is an explicit manual preset choice in the test scenario, not an application-side automatic
switch. Leave Story-only and lifecycle tests on Exploration.

Append one ordered method after the current A3 browser flow:

```java
@Test
@Order(29)
void cockpitLayoutStartsLockedAndSwitchesOnlyOnExplicitPresetChoice() {
    dmPage.setViewportSize(1366, 768);
    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/session");
    dmPage.waitForLoadState(LoadState.NETWORKIDLE);
    dmPage.waitForFunction("window.cockpitLayout?.mounted === true");

    assertThat(dmPage.locator("[data-cockpit-workbench]").getAttribute("data-layout-mode"))
            .isEqualTo("locked");
    assertThat(dmPage.locator("[data-cockpit-splitter]").all())
            .allSatisfy(splitter -> assertThat(splitter.getAttribute("tabindex")).isEqualTo("-1"));
    assertThat(dmPage.locator("[data-layout-edit-only]:visible").count()).isZero();

    String beforeSceneChange = dmPage.locator("#cockpitPresetPicker").inputValue();
    dmPage.evaluate("window.dispatchEvent(new CustomEvent('session-scene-step', {detail:{direction:1}}))");
    assertThat(dmPage.locator("#cockpitPresetPicker").inputValue()).isEqualTo(beforeSceneChange);

    long started = System.nanoTime();
    dmPage.locator("#cockpitPresetPicker").selectOption("builtin:combat");
    dmPage.waitForFunction("document.querySelector('[data-cockpit-zone=\"PRIMARY\"] [data-module-key=\"map\"]')");
    assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1000);
    assertThat(dmPage.locator("[data-cockpit-zone='RIGHT_SUPPORT'] [data-module-key='encounter']")).hasCount(1);
}
```

The Java wall-clock threshold is intentionally loose for CI. Task 10 adds an in-browser `performance.now()` assertion for the 100 ms product budget.

- [ ] **Step 2: Run the ordered browser test and observe missing controller behavior**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test
```

Expected: Order 29 fails because `window.cockpitLayout` is absent or modules remain in the depot.

- [ ] **Step 3: Implement controller construction and immutable state copies**

Load `/js/cockpit-layout.js` with `defer` immediately before the existing deferred Alpine script.
The inline bootstrap from Task 5 appears earlier in the parsed document. On successful `mount()`,
remove `disabled`/`aria-disabled` from the preset and layout-mode controls.

Use a class, not Alpine component state:

```javascript
(function () {
  'use strict';

  const ZONES = ['PRIMARY', 'LEFT_SUPPORT', 'RIGHT_SUPPORT', 'BOTTOM_UTILITY'];
  const DEFAULT_KEY = 'builtin:exploration';

  class CockpitLayoutController {
    constructor(config) {
      this.config = config;
      this.campaignId = String(config.campaignId);
      this.modules = new Map(config.modules.map(module => [module.key, module]));
      this.presets = new Map(config.presets.map(preset => [preset.key, preset]));
      this.workbench = document.querySelector('[data-cockpit-workbench]');
      this.currentPresetKey = DEFAULT_KEY;
      this.current = null;
      this.editSnapshot = null;
      this.focusedModuleKey = null;
      this.attention = new Map();
      this.metrics = {firstMeaningfulMs: null, lastApplyMs: null};
      this.mounted = false;
    }

    clone(value) {
      return window.structuredClone
        ? window.structuredClone(value)
        : JSON.parse(JSON.stringify(value));
    }

    storageKey(kind) {
      return `dmhelper.cockpit.${kind}.v1.${this.campaignId}`;
    }
  }

  window.CockpitLayoutController = CockpitLayoutController;
  window.cockpitLayout = new CockpitLayoutController(window.cockpitLayoutConfig);
  window.cockpitLayout.mount();
})();
```

- [ ] **Step 4: Implement mount, preset application, tabs, and locked behavior**

`mount()` must:

1. verify all ten module shell keys are unique;
2. bind controls and event listeners;
3. read `last-preset`, using Exploration when absent or unknown;
4. overlay only valid stored active tabs;
5. apply the selected preset by moving the existing module nodes;
6. force locked mode;
7. expose `mounted=true`.

`applyPreset(key)` must refuse while edit mode is dirty, clone the preset document, restore valid active tabs, call `renderLayout`, set picker value, store only the key, emit visibility for all ten modules, and dispatch:

```javascript
const durationMs = performance.now() - started;
this.metrics.lastApplyMs = durationMs;
if (this.metrics.firstMeaningfulMs === null) {
  this.metrics.firstMeaningfulMs = performance.now();
}
window.dispatchEvent(new CustomEvent('cockpit:layout-applied', {
  detail: {presetKey: key, durationMs}
}));
```

`renderLayout` must never use `innerHTML` for module content. For each zone:

- create/update one tab button per assigned module with `role="tab"`, `aria-selected`, and `aria-controls`;
- append the original module node to a stable panel with `role="tabpanel"`;
- make only the active panel visible and remove its `inert`;
- set all inactive panels `hidden` and `inert`;
- collapse Bottom through `data-collapsed`;
- set CSS variables `--left-size`, `--primary-size`, `--right-size`, `--top-size`, and
  `--bottom-size` as `fr` tracks so fixed splitter pixels cannot overflow the grid;
- set `data-compact` only on compact-capable selected modules.

`selectTab` works in locked mode, persists only the active-tab map, clears that module's attention badge, and emits visibility changes. It does not mutate or save the named preset.

- [ ] **Step 5: Implement one-click edit entry and exit-state recovery**

`enterEditMode()` takes an immutable snapshot, sets `data-layout-mode="edit"`, shows edit-only controls, removes `tabindex=-1` from splitters, and writes the initial draft. `requestExitEditMode()` performs exactly one action:

- unchanged document → lock immediately;
- changed document → open save/discard dialog.

`discardEdit()` restores the snapshot, clears the draft key, locks, and leaves all session/game state untouched.

`saveEdit()`:

- duplicates a built-in through `POST`;
- updates a custom through `PUT` with its optimistic version;
- on success updates the local preset map, clears draft, applies returned key, and locks;
- on failure stays in edit, leaves the draft, and shows the returned safe message plus correlation reference in `#cockpitLayoutNotice`.

The preset overflow uses the same API boundary:

- **Duplicate preset** opens the name dialog and POSTs a copy of the current document;
- **Rename preset** is enabled only for custom presets and PUTs the same document with its new
  `name` plus current `version`;
- **Delete preset** is enabled only for custom presets, DELETEs it, removes its browser
  last-preset reference, and applies Exploration;
- **Restore built-in** is enabled only for built-ins, replaces the in-memory document with the
  catalog copy, clears that campaign's active-tab override and edit draft, and applies it without a
  server write.

None of those controls call a campaign or session endpoint.

All requests use `window.dmRequest` if available; otherwise use `fetch` with JSON and explicit non-2xx handling.

- [ ] **Step 6: Implement corrupted storage and unfinished-draft recovery**

Wrap every `localStorage` read/write in `try/catch`. On malformed JSON, remove only that key and show:

```text
The saved device layout state was invalid and has been ignored.
```

If a valid edit draft exists, do not silently apply it. Show a non-modal notice with **Resume edit** and **Discard draft**. Resume applies the draft and enters edit mode; discard removes it and leaves the selected named preset locked.

- [ ] **Step 7: Run the browser flow**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test
```

Expected: all ordered methods pass, the cockpit starts locked, scene changes do not change presets, and manual preset switching moves unique module roots.

- [ ] **Step 8: Commit preset switching and lock state**

```bash
git add src/main/resources/static/js/cockpit-layout.js \
  src/main/resources/templates/session/cockpit.html \
  src/main/resources/templates/session/_cockpit-workbench.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): add locked preset controller"
```

---

### Task 7: Add Docking, Tab Reorder, Splitters, Add/Remove, and Focus

**Files:**
- Modify: `src/main/resources/static/js/cockpit-layout.js`
- Modify: `src/main/resources/templates/session/_cockpit-workbench.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Consumes: Task 6 controller state.
- Produces: `moveModule(key, zone, index)`, `removeModule(key)`, `addModule(key, zone)`, `resize(splitter, delta)`, `focusModule(key)`, and `restoreFocus()`.
- Produces: accessible pointer and keyboard parity for every edit operation.

- [ ] **Step 1: Add failing browser coverage for edit mechanics**

Add an ordered method that performs:

1. click `#cockpitLayoutModeButton` once and assert edit mode;
2. assert all three splitters become focusable;
3. focus left separator; press `ArrowRight`, then `Shift+ArrowRight`; assert `aria-valuenow` rises by `2` then `10` percentage points or stops at the calculated clamp;
4. use Encounter's Arrange menu to move it from Right to Primary; assert exactly one Encounter shell remains and becomes a Primary tab;
5. move Encounter earlier/later by keyboard menu actions and assert tab DOM order;
6. remove Audio; assert its shell returns to depot and appears in Add module;
7. add Audio to Bottom; assert it appears exactly once;
8. drag Party onto Right's docking target and assert it moves;
9. focus Story, assert other zones are hidden/inert, choose Return, and assert focus returns to Story's Focus button;
10. exit once, choose Discard, and assert the original preset is restored.

- [ ] **Step 2: Run the browser test and observe missing edit operations**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test
```

Expected: the new ordered method fails on the first missing arrange or separator behavior.

- [ ] **Step 3: Implement move, reorder, remove, and add invariants**

Every mutation first calls:

```javascript
assertEditing() {
  if (this.workbench.dataset.layoutMode !== 'edit') {
    throw new Error('Layout changes require edit mode.');
  }
}
```

`moveModule(key, targetZone, index)` must:

- require a known key and allowed zone;
- remove the key from every zone before inserting it once;
- clamp index into `[0,target.moduleKeys.length]`;
- repair prior and new active tabs;
- reject leaving Primary empty;
- render and persist the draft.

`removeModule` rejects the only Primary module, removes compact preference, moves the shell back to depot, and makes it available in Add module. `addModule` lists only absent modules, lists only allowed zones, inserts once, and closes the dialog with focus restored to Add module.

Pointer docking uses native drag events only in edit mode:

```javascript
header.draggable = editing;
header.addEventListener('dragstart', event => {
  event.dataTransfer.setData('text/x-dmhelper-module', key);
});
target.addEventListener('drop', event => {
  event.preventDefault();
  this.moveModule(event.dataTransfer.getData('text/x-dmhelper-module'), zone, index);
});
```

The keyboard Arrange menu calls the same methods; there is no drag-only state transition.

- [ ] **Step 4: Implement adjacent splitter pointer and keyboard behavior**

Small increment is `0.02`; Shift increment is `0.10`. Vertical separators use Left/Right arrows; the horizontal separator uses Up/Down. Home/End move to the calculated minimum/maximum.

On pointer drag:

```javascript
splitter.setPointerCapture(event.pointerId);
const frame = () => {
  this.applyPendingResize();
  this.resizeFrame = null;
};
if (!this.resizeFrame) this.resizeFrame = requestAnimationFrame(frame);
```

Calculate pixel minima from the active module definitions. Clamp Left and Right so Primary remains 50–65% and every visible column meets its active module's declared minimum. Clamp Bottom against its active module minimum and `[.16,.40]`. Update `aria-valuenow` after every accepted change. Ignore every pointer/keyboard resize while locked.

- [ ] **Step 5: Implement one-action Focus and focus restoration**

`focusModule(key)`:

- works in locked and edit modes;
- does not mutate `current`;
- records `document.activeElement`;
- moves the original module shell into the focus layer;
- sets the workbench `inert` and shows `#cockpitFocusReturn`;
- dispatches visibility changes.

`restoreFocus()` returns the shell to its exact zone/panel, removes `inert`, hides the layer, and restores focus to the initiating control. Escape restores focus unless another modal is open. Screen Safety remains applied to the focused shell.

- [ ] **Step 6: Implement attention badges instead of automatic layout**

Listen for:

```javascript
window.addEventListener('cockpit:module-attention', event => {
  this.setAttention(event.detail.moduleKey, event.detail.count ?? 1);
});
```

If the module is not the active visible tab, show the count on its tab and shell badge. Never activate the tab, move the module, or switch presets. Selecting/focusing the module clears its badge.

- [ ] **Step 7: Run the browser edit test**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test
```

Expected: pointer and keyboard operations pass, locked mode rejects mutation, focus restores correctly, and each module remains unique.

- [ ] **Step 8: Commit layout editing**

```bash
git add src/main/resources/static/js/cockpit-layout.js \
  src/main/resources/templates/session/_cockpit-workbench.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): add accessible layout editing"
```

---

### Task 8: Enforce the Viewport, Grid, Zoom, and Reduced-Motion Contract

**Files:**
- Modify: `src/main/resources/static/css/cockpit-layout.css`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Consumes: Task 5 markup and Task 7 CSS variables.
- Produces: zero document scroll, no overlap, internal module scrolling, visible command bar, and at least 32×32 px controls at the acceptance viewports/zoom levels.

- [ ] **Step 1: Add failing geometry assertions**

For viewport sizes 1366×768 and 1920×1080, and zoom factors `.8`, `1.0`, `1.25`, evaluate:

```javascript
() => {
  const root = document.documentElement;
  const modules = [...document.querySelectorAll(
    '[data-cockpit-zone]:not([data-collapsed="true"]) [role="tabpanel"]:not([hidden]) .cockpit-module'
  )];
  const rectangles = modules.map(node => node.getBoundingClientRect());
  const overlaps = rectangles.some((a, i) => rectangles.some((b, j) =>
    i < j && a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
  ));
  return {
    documentScrolls: root.scrollHeight > root.clientHeight + 1 || root.scrollWidth > root.clientWidth + 1,
    overlaps,
    clipped: rectangles.some(r => r.left < 0 || r.top < 0 ||
      r.right > window.innerWidth + 1 || r.bottom > window.innerHeight + 1),
    smallestTarget: Math.min(...[...document.querySelectorAll(
      '.cockpit-commandbar button:visible, .cockpit-tab:visible, [data-module-focus]:visible'
    )].map(node => Math.min(node.getBoundingClientRect().width, node.getBoundingClientRect().height)))
  };
}
```

Because `:visible` is not a DOM selector, implement target selection by filtering `node.offsetParent !== null`. Apply zoom in Chromium with `document.documentElement.style.zoom = String(factor)` and reset it after each assertion. The manual checkpoint in Task 10 uses actual browser zoom.

- [ ] **Step 2: Run the geometry test and observe legacy scroll/overlap**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test
```

Expected: the new geometry method fails because legacy `.session-cockpit`/`.cockpit-grid` rules still permit document scrolling or fixed strips.

- [ ] **Step 3: Add the structural CSS Grid**

Use:

```css
html:has(.session-cockpit),
body:has(.session-cockpit) {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.session-cockpit {
  height: 100dvh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cockpit-commandbar {
  min-height: 3rem;
  max-height: 3rem;
  flex: 0 0 3rem;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  overflow: visible;
}

.cockpit-workbench {
  --left-size: 20fr;
  --primary-size: 56fr;
  --right-size: 24fr;
  --top-size: 76fr;
  --bottom-size: 24fr;
  --splitter-size: 6px;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns:
    minmax(0, var(--left-size)) var(--splitter-size)
    minmax(0, var(--primary-size)) var(--splitter-size)
    minmax(0, var(--right-size));
  grid-template-rows:
    minmax(0, var(--top-size))
    var(--splitter-size) minmax(0, var(--bottom-size));
  overflow: hidden;
}

.cockpit-zone--left {
  grid-column: 1;
  grid-row: 1;
}

.cockpit-splitter--left {
  grid-column: 2;
  grid-row: 1;
}

.cockpit-zone--primary {
  grid-column: 3;
  grid-row: 1;
}

.cockpit-splitter--right {
  grid-column: 4;
  grid-row: 1;
}

.cockpit-zone--right {
  grid-column: 5;
  grid-row: 1;
}

.cockpit-splitter--bottom {
  grid-column: 1 / -1;
  grid-row: 2;
}

.cockpit-zone {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cockpit-zone--bottom {
  grid-column: 1 / -1;
  grid-row: 3;
}

.cockpit-zone[data-collapsed="true"] {
  display: none;
}

.cockpit-workbench[data-bottom-collapsed="true"] {
  --top-size: 1fr;
  --bottom-size: 0fr;
  grid-template-rows: minmax(0, 1fr) 0 minmax(0, 0fr);
}

.cockpit-workbench[data-bottom-collapsed="true"] .cockpit-splitter--bottom {
  display: none;
}

.cockpit-module,
.cockpit-module__body,
[data-zone-panels],
[role="tabpanel"] {
  min-width: 0;
  min-height: 0;
}

.cockpit-module {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cockpit-module__body {
  flex: 1 1 auto;
  overflow: auto;
  overscroll-behavior: contain;
}
```

When Bottom is collapsed, set `--top-size: 1fr` and `--bottom-size: 0fr`, hide the bottom splitter,
and set the middle track to `0` through `data-bottom-collapsed`; row one then fills the remaining
height without an invisible gap. JavaScript writes horizontal ratios as `${ratio * 100}fr` and
vertical ratios as `${(1 - bottom) * 100}fr` / `${bottom * 100}fr`.

- [ ] **Step 4: Style edit mode, tabs, focus layer, and controls**

Required behavior:

- splitters are visually absent and pointer-inert while locked;
- edit mode gives splitters a visible 6 px line with a 32 px transparent hit target;
- docking targets use the existing steel/gold focus palette;
- active tabs have visible state without relying only on color;
- tab lists scroll horizontally within their zone and never widen the document;
- module header controls and command controls have `min-width/min-height: 32px`;
- focus layer fills exactly the workbench rectangle, not the document;
- `:focus-visible` uses the existing 3 px accent outline;
- dialogs use the existing UI elevation/focus model.

Add:

```css
@media (prefers-reduced-motion: reduce) {
  .cockpit-workbench *,
  .cockpit-focus-layer *,
  .cockpit-layout-dialog * {
    scroll-behavior: auto !important;
    transition-duration: 0.001ms !important;
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
  }
}
```

Remove the structural `.cockpit-grid`, `.cockpit-plan`, `.cockpit-partybar`, and page-scroll assumptions from `cockpit.css`; keep battle-map, story-card, tracker, party chip, and lifecycle component styling there.

- [ ] **Step 5: Run geometry and accessibility assertions**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest,CockpitWorkbenchTemplateContractTest test
```

Expected: all six viewport/zoom combinations report no document scroll, overlap, or clipping; command bar stays reachable; visible targets are at least 32 px in both dimensions.

- [ ] **Step 6: Commit viewport layout**

```bash
git add src/main/resources/static/css/cockpit-layout.css \
  src/main/resources/static/css/cockpit.css \
  src/main/resources/static/css/components.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(cockpit): enforce viewport grid contract"
```

---

### Task 9: Preserve Runtime State, Screen Safety, and Module Failure Isolation

**Files:**
- Modify: `src/main/resources/static/js/cockpit-layout.js`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/templates/session/_cockpit-module-shell.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeModuleSafetyContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Produces: `cockpit:module-visibility` detail `{moduleKey, visible}`.
- Consumes: `cockpit:module-state` detail `{moduleKey, state, message, retry}`.
- Produces: `BattleMap.setRenderingActive(boolean)` and `BattleMap.isRenderingActive()`.
- Preserves: map transform, current scene/encounter, quick-note draft, active combatant, presentation mode, and server session state.

- [ ] **Step 1: Add failing state-preservation and failure tests**

In the browser flow:

1. set a distinctive map scale/position;
2. put text in the existing quick-note/session draft input without submitting;
3. record selected scene, active encounter, presentation mode, and active combatant;
4. switch Exploration → Combat → Theatre of Mind → Combat;
5. assert the same DOM node identities by assigning `dataset.identity = crypto.randomUUID()` before switching;
6. assert map transform, draft input, scene, encounter, presentation, and active combatant remain unchanged;
7. dispatch `cockpit:module-state` error for Story and assert Story's existing body remains visible with an inline Retry action;
8. dispatch module attention for a hidden tab and assert a badge appears without tab/preset activation;
9. enter Table-safe in every built-in and assert every `HIDE` shell is hidden/inert and every `FILTER` shell retains only its filtered safe content;
10. hide Map and assert `window.battleMap.isRenderingActive() === false`; show it and assert the transform is unchanged.

Route one preset save to HTTP 503. Assert edit mode remains active, the draft key exists, the notice includes the correlation reference, and no success message appears.

- [ ] **Step 2: Run focused tests and observe missing visibility/failure adapters**

Run:

```bash
./mvnw -Dtest=SessionCockpitMapContractTest,RuntimeModuleSafetyContractTest,CoreSessionLoopSmokeTest test
```

Expected: failures identify absent render-active methods, missing state events, or unsafe focused/hidden module behavior.

- [ ] **Step 3: Add standardized module-state handling**

`cockpit-layout.js` listens for `cockpit:module-state` and accepts only:

- `loading`: show live loading text; keep existing body when it already has content;
- `ready`: clear loading/error and reveal body;
- `empty`: show the registry empty message;
- `error`: keep body visible, show inline registry error text and Retry;
- `attention`: increment badge without rearranging.

Store Retry callbacks in memory only; never serialize functions. Clicking Retry sets loading and invokes the callback. A rejected retry returns to error and retains prior content.

- [ ] **Step 4: Add visibility events and the map adapter**

After every render, tab selection, Bottom collapse, focus change, and Screen Safety change, dispatch one visibility event per module. Visibility means its panel is active, its zone is not collapsed, it is not hidden by safety, and either the workbench or focus layer exposes it.

In `session-cockpit.js`:

```javascript
window.addEventListener('cockpit:module-visibility', event => {
  if (event.detail?.moduleKey !== 'map' || !window.battleMap) return;
  window.battleMap.setRenderingActive(Boolean(event.detail.visible));
  if (event.detail.visible) {
    requestAnimationFrame(() => window.battleMap.resizeToContainer());
  }
});
```

In `battle-map.js`, add:

```javascript
setRenderingActive(active) {
  const next = Boolean(active);
  if (this.renderingActive === next) return;
  this.renderingActive = next;
  this.stage.listening(next);
  for (const layer of this.stage.getLayers()) {
    layer.listening(next);
    layer.visible(next);
  }
  if (next) this.stage.batchDraw();
}

isRenderingActive() {
  return this.renderingActive !== false;
}

resizeToContainer() {
  if (!this.isRenderingActive() || !this.container?.isConnected) return;
  const width = this.container.clientWidth;
  const height = this.container.clientHeight;
  if (width < 1 || height < 1) return;
  this.stage.size({width, height});
  this.stage.batchDraw();
}
```

Initialize `renderingActive=true`. Keep all world position, stage scale, token, fog, annotation, and selection state untouched.

- [ ] **Step 5: Make heavy initialization visibility-aware**

Before constructing `BattleMap`, ask:

```javascript
const mapVisible = window.cockpitLayout?.isModuleVisible('map') ?? true;
```

If hidden, defer construction until the first visible event. Store one pending initializer and remove the listener after successful construction. If already constructed, use the render-active adapter. Do not initialize a second map on repeated visibility events.

- [ ] **Step 6: Re-apply Screen Safety after DOM moves**

The layout controller must not implement a second safety policy. After moving or focusing modules,
call `window.setScreenSafety(document.body.dataset.screenSafety || 'PRIVATE', {animate:false})`;
that existing function dispatches `screen-safety-changed`. Ensure hidden sensitive shells and
inactive tab panels are `inert`, and focused modules cannot bypass their registry behavior.

- [ ] **Step 7: Run safety, map, and browser tests**

Run:

```bash
./mvnw -Dtest=SessionCockpitMapContractTest,RuntimeModuleSafetyContractTest,SessionCockpitSecurityTest,CoreSessionLoopSmokeTest test
```

Expected: all selected tests pass; map initialization occurs at most once; preset switches preserve all recorded runtime state; failed module/save actions remain visible and recoverable.

- [ ] **Step 8: Commit runtime isolation**

```bash
git add src/main/resources/static/js/cockpit-layout.js \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/js/map/battle-map.js \
  src/main/resources/templates/session/_cockpit-module-shell.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "fix(cockpit): preserve module state across layouts"
```

---

### Task 10: Prove Export Exclusion, Performance, Accessibility, and Manual Acceptance

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `docs/dm-manual/03-session-cockpit.md`

**Interfaces:**
- Consumes: the complete B1 implementation.
- Produces: a focused automated release gate and manual acceptance record.
- Produces: a clean dependency handoff to B2 Runtime Module Migration.

- [ ] **Step 1: Add explicit campaign-export exclusion tests**

Persist a custom preset named `PRIVATE_LAYOUT_SENTINEL_9D4F`, export both formats, and assert:

```java
assertThat(campaignService.exportToJson(campaignId))
        .doesNotContain("PRIVATE_LAYOUT_SENTINEL_9D4F", "cockpitLayout", "layoutPreset");
```

For package v2, serialize the manifest and inspect zip entry names:

```java
assertThat(JsonMapper.builder().build().writeValueAsString(artifact.manifest()))
        .doesNotContain("PRIVATE_LAYOUT_SENTINEL_9D4F", "cockpitLayout", "layoutPreset");
assertThat(zipEntryNames).noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("layout"));
```

Then import each export and assert the existing global custom preset row still exists exactly once; campaign import must neither create nor delete application-local presets.

- [ ] **Step 2: Add in-browser performance and keyboard assertions**

Listen for `cockpit:layout-applied` and assert:

```java
Number duration = (Number) dmPage.evaluate("""
    async () => {
      const done = new Promise(resolve =>
        window.addEventListener('cockpit:layout-applied',
          event => resolve(event.detail.durationMs), {once: true}));
      document.querySelector('#cockpitPresetPicker').value = 'builtin:combat';
      document.querySelector('#cockpitPresetPicker').dispatchEvent(
        new Event('change', {bubbles: true}));
      return await done;
    }
    """);
assertThat(duration.doubleValue()).isLessThan(100.0);
```

Also prove:

- `window.cockpitLayout.metrics.firstMeaningfulMs < 2000`;
- dispatching a `loading` state updates the target shell within 100 ms; B2 must retain that
  assertion and add the existing two-second endpoint-render assertion when it replaces the
  transitional bodies with lazy endpoints;
- Alt+Shift+1 through Alt+Shift+5 selects the five built-ins only when no modal/input owns the keystroke;
- Tab/Shift+Tab remain trapped in Add, name, save/discard, and focus layers as appropriate;
- Escape closes the topmost layer and restores its trigger;
- separators report updated `aria-valuenow`;
- tabs expose `role=tab`, selected state, owned panels, and arrow-key selection;
- locked mode makes no layout edit control or splitter keyboard reachable;
- reduced-motion media emulation removes meaningful transition duration;
- no console error, page error, unhandled rejection, malformed request, or unapproved non-2xx response occurs.

- [ ] **Step 3: Run the focused B1 test gate**

Run:

```bash
./mvnw -Dtest='Cockpit*Test,SessionCockpit*Test,RuntimeModuleSafetyContractTest,SessionControllerTest,CoreSessionLoopSmokeTest,FlywayMigrationTest,FlywayLegacyUpgradeTest,ThreatMigrationTest,CampaignPackageV2IntegrationTest,CampaignImportExportRoundTripTest' test
```

Expected: all B1-focused unit, persistence, MVC, template, migration, export, safety, map, and browser tests pass.

- [ ] **Step 4: Document the DM workflow**

Update `docs/dm-manual/03-session-cockpit.md` with:

- the four zones and internal scrolling;
- locked default and the one-click Edit layout toggle;
- Save preset versus Discard changes;
- duplicate/rename/delete/restore behavior;
- built-in preset placements and Alt+Shift+1…5 shortcuts;
- Add module, keyboard Arrange commands, pointer docking, and separators;
- Focus and Return;
- device-only transient recovery;
- why combat/scene changes create attention badges instead of changing layouts;
- the B1/B2 boundary for transitional module bodies.

- [ ] **Step 5: Run a clean full build and patch hygiene checks**

Run:

```bash
./mvnw clean test
git diff --check
git status --short
git ls-files artifacts
```

Expected: the full suite passes from a clean compile; no whitespace errors; only intended B1 changes remain; no private artifact is tracked.

- [ ] **Step 6: Perform the manual 1366×768 checkpoint**

Use the populated synthetic browser campaign:

1. Load the cockpit and confirm the document itself cannot scroll.
2. Confirm Exploration is visible and the workspace is locked.
3. Select tabs and operate Story/Encounter content while locked.
4. Confirm dividers, Add, Remove, docking, and reorder cannot operate while locked.
5. Click **Edit layout** once; resize both horizontal boundaries and Bottom.
6. Drag one allowed module, then perform the same move through its keyboard Arrange menu.
7. Attempt to move Map outside Primary and confirm the action is unavailable.
8. Remove and re-add Audio; confirm no duplicate can be created.
9. Exit edit once, Discard, and confirm the exact prior layout returns.
10. Edit again, save a named custom preset, reload, and confirm it persists.
11. Duplicate a built-in, rename the duplicate, delete it, and restore the immutable built-in.
12. Switch presets and confirm map viewport, selected scene, encounter setup/turn, presentation, notes, and session state survive.
13. Focus and return each of Story, Map, Encounter, and Party.
14. Enter Table-safe in every built-in; verify sensitive content is invisible and not focusable.
15. Simulate a failed preset save and confirm edit mode plus recoverable draft remain.
16. Corrupt one browser storage value and confirm a non-blocking recovery explanation.
17. Confirm missing/unavailable content shows an explanation and next action, not a blank panel.
18. Confirm command bar identity, preset, lock, Screen Safety, presentation, Search, Dice, and Session remain reachable.

- [ ] **Step 7: Perform the 1920×1080 and actual browser-zoom checkpoint**

At 80%, 100%, and 125% browser zoom:

- confirm no document scroll, overlap, clipped command, or unreachable tab;
- confirm Primary remains visually dominant;
- confirm module internals scroll independently;
- confirm all edit targets and focus outlines remain usable;
- confirm pointer splitters remain responsive;
- confirm hidden Map causes no repeated Konva draws and reappears at the same transform;
- confirm reduced-motion operating-system preference removes layout animation.

- [ ] **Step 8: Record rollback**

Flyway migrations are forward-only. Before deployment, rely on the application's startup backup. A development-only schema rollback is:

```sql
drop table cockpit_layout_preset;
```

Then restore the pre-B1 application binary and remove only these device keys if desired:

```javascript
for (const key of Object.keys(localStorage)) {
  if (key.startsWith('dmhelper.cockpit.')) localStorage.removeItem(key);
}
```

A production rollback should restore the pre-V22 backup instead of editing Flyway history. Layout preset rollback never requires or authorizes changes to campaign/session tables.

- [ ] **Step 9: Confirm the dependency handoff**

Review B1 against corrective-spec sections 7.1–7.8, 11.1–11.2, 12, 13, and 14:

- curated four-zone tiling with no overlap;
- viewport-owned layout and internal module scroll;
- full lock and one-click edit transitions;
- registry metadata and ten unique modules;
- immutable built-ins plus durable custom presets;
- browser-only transient state;
- manual switching and hidden-tab attention;
- accessible docking, tabs, splitters, dialogs, and focus;
- layout-only client ownership;
- invalid recovery and failure isolation;
- state preservation and Screen Safety;
- 1366×768/1920×1080/zoom/performance gates;
- no campaign export leakage;
- no new framework or docking dependency.

Expected: every item maps to a green automated test or the completed manual checkpoint. The next dependency-ordered plan is **B2 — Runtime module migration**, beginning with Story, Encounter, Map, Party, and Presentation.

- [ ] **Step 10: Commit verification and documentation**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java \
  docs/dm-manual/03-session-cockpit.md
git commit -m "test(cockpit): verify B1 layout foundation"
```

## Final Acceptance Gate

B1 is complete only when:

- schema-v1 documents round-trip and invalid documents recover predictably;
- the ten-module registry is the single source of layout metadata and safety behavior;
- all five built-ins validate and remain immutable;
- custom presets persist globally in local application data with optimistic concurrency;
- neither campaign export format contains custom presets;
- every page load starts fully locked;
- edit entry and exit are each one action, with save/discard only when dirty;
- every pointer docking operation has a keyboard equivalent;
- splitters resize adjacent zones only and clamp to ratio plus module minima;
- every module appears at most once;
- focus never mutates a preset and restores focus correctly;
- preset switching is manual and settles its layout chrome within 100 ms;
- module failure, preset-save failure, missing keys, and corrupt storage are visible and recoverable;
- current scene, encounter, combat, map viewport, note draft, presentation, party, and session state survive every layout operation;
- hidden Konva content suspends without losing transform;
- every built-in is safe in Table-safe mode;
- 1366×768 and 1920×1080 at 80%, 100%, and 125% zoom have no document scroll, overlap, or clipping;
- `./mvnw clean test` and `git diff --check` pass;
- the remaining content-shell migration is explicitly handed to B2 rather than hidden inside B1.
