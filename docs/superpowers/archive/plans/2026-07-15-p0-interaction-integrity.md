# P0 Interaction Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make quick notes and content navigation dependable under real browser use, with tests that fail on unresolved template expressions, browser errors, malformed requests, or invalid generated destinations.

**Architecture:** Introduce a small `ContentDestinationRegistry` as the single owner of application URLs, then make command-palette and wiki-link services consume it. Keep quick-note state in the existing Alpine component, but bind fragment arguments through HTML data attributes and centralize response/error handling inside that component. Add a reusable Playwright failure collector so browser smoke tests enforce the same no-silent-failure contract on every page they open.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA, Thymeleaf, Alpine.js, HTMX, JUnit 5, AssertJ, Mockito, Playwright 1.54, Maven Wrapper.

## Global Constraints

- Runtime features must work without internet access.
- No new frontend build chain or runtime CDN is introduced.
- DM-only content must never be sent to player clients.
- Interactive operations must surface failure and must not discard unsaved user input.
- Search, wiki links, cards, and tests must share destination behavior rather than concatenate route strings independently.
- Results are ranked and capped globally at 20 entries.
- Map destinations end in `/play`; there is no `/battle` controller route.
- Handouts open the campaign handout gallery at the matching card.
- Party members open their sheet when one exists and the roster card otherwise.
- Compendium types without a full detail route open the correct filtered library tab.
- Tests must use an isolated home directory through `-DargLine=-Duser.home=/tmp/dmhelper-p0`.

## File Structure

### Files created

- `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistry.java` — typed construction of campaign and library destinations.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistryTest.java` — exhaustive destination contract for every supported result type.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRouteContractTest.java` — verifies generated paths match registered Spring MVC routes.
- `src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java` — Playwright listener bundle that records page, console, request, and HTTP failures.
- `src/test/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiControllerTest.java` — campaign ownership and controller delegation tests.

### Files modified

- `src/main/resources/templates/notes/_quicknotes-strip.html` — safe fragment argument binding and visible mutation errors.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java` — campaign-scoped lookup.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java` — supported target validation, campaign ownership, and encounter promotion links.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiController.java` — campaign-scoped mutations.
- `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteServiceTest.java` — invalid-target, ownership, and encounter-link coverage.
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/HtmxTemplateExpressionTest.java` — guard against literal Thymeleaf inline syntax in rendered JavaScript attributes.
- `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` — guarded pages plus quick-note and palette navigation flows.
- `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java` — registry-backed URLs and global relevance ordering.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteServiceTest.java` — exact URL, ranking, and global-cap coverage.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java` — registry-backed wiki destinations.
- `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteServiceTest.java` — exact map and handout link destinations.
- `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java` — accepts initial tab and search query.
- `src/main/resources/templates/library/list.html` — activates and filters a library tab from its URL.
- `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java` — deep-link model contract.
- `src/main/resources/templates/handout/_card.html` — stable handout anchor already used by generated destinations; retain `handout-{id}`.
- `src/main/resources/templates/party/_card.html` — stable party anchor already used by generated destinations; retain `pm-card-{id}`.

### Explicitly deferred to the next P0 plans

- Imported handout filename isolation and collision-proof storage.
- Shared mutation-error handling for the encounter tracker and battle-map controls.
- Honest encounter difficulty estimate metadata and UI labeling.
- Correlation identifiers for server-side failures.

---

### Task 1: Guard and repair quick-note browser interactions

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/HtmxTemplateExpressionTest.java`
- Modify: `src/main/resources/templates/notes/_quicknotes-strip.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteServiceTest.java`

**Interfaces:**
- Consumes: Playwright `Page` events.
- Produces: `BrowserFailureCollector.attach(Page)`, `BrowserFailureCollector.assertNoFailures()`, `BrowserFailureCollector.clear()`, `QuickNoteRepository.findByIdAndCampaignId(UUID, UUID)`, and campaign-scoped `delete`/`promoteToNote` service methods.

- [ ] **Step 1: Add the failing static template contract**

Add this test to `HtmxTemplateExpressionTest`:

```java
@Test
void noTemplateContainsLiteralThymeleafInlinePlaceholder() throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(TEMPLATES)) {
        for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".html"))::iterator) {
            String content = Files.readString(file);
            if (content.contains("[[${")) {
                violations.add(TEMPLATES.relativize(file).toString());
            }
        }
    }

    assertThat(violations)
            .as("fragment parameters must use th:* attributes or data attributes, not literal [[${...}]] syntax")
            .isEmpty();
}
```

- [ ] **Step 2: Run the contract and verify the current fragment fails it**

Run:

```bash
./mvnw -Dtest=HtmxTemplateExpressionTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: FAIL naming `notes/_quicknotes-strip.html`.

- [ ] **Step 3: Add the reusable Playwright collector**

Create `BrowserFailureCollector.java`:

```java
package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserFailureCollector {
    private final List<String> failures = new CopyOnWriteArrayList<>();

    void attach(Page page) {
        page.onConsoleMessage(this::recordConsoleError);
        page.onPageError(message -> failures.add("page error: " + message));
        page.onRequestFailed(request -> {
            String failure = request.failure();
            if (!"net::ERR_ABORTED".equals(failure)) {
                failures.add("request failed: " + request.method() + " " + request.url() + " — " + failure);
            }
        });
        page.onResponse(response -> {
            if (response.status() >= 400) {
                failures.add("HTTP " + response.status() + ": "
                        + response.request().method() + " " + response.url());
            }
        });
    }

    private void recordConsoleError(ConsoleMessage message) {
        if ("error".equals(message.type())) {
            failures.add("console error: " + message.text());
        }
    }

    void assertNoFailures() {
        assertThat(failures).as("unexpected browser failures").isEmpty();
    }

    void clear() {
        failures.clear();
    }
}
```

- [ ] **Step 4: Guard every page used by the smoke test**

Add a field and helper to `CoreSessionLoopSmokeTest`:

```java
private final BrowserFailureCollector browserFailures = new BrowserFailureCollector();

private Page guardedPage(BrowserContext context) {
    Page page = context.newPage();
    browserFailures.attach(page);
    return page;
}
```

Change `setUp()` to clear prior state and create the DM page through the helper:

```java
@BeforeEach
void setUp() {
    browserFailures.clear();
    dmContext = browser.newContext();
    dmPage = guardedPage(dmContext);
}
```

Change `tearDown()` so the assertion runs before the context closes:

```java
@AfterEach
void tearDown() {
    try {
        browserFailures.assertNoFailures();
    } finally {
        if (dmContext != null) dmContext.close();
    }
}
```

Replace both player-page constructions with:

```java
BrowserContext playerContext = browser.newContext();
Page playerPage = guardedPage(playerContext);
```

Close `playerContext` directly at each test's end.

- [ ] **Step 5: Run the smoke test and confirm the collector exposes the malformed quick-note request**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: FAIL with an unexpected HTTP 400, request failure, or console error originating from the quick-note fragment.

- [ ] **Step 6: Write failing service tests for target validation, ownership, and encounter promotion**

Add `EncounterRepository` to `QuickNoteServiceTest`, create an encounter owned by `campaign`, and add these tests:

```java
@Test
void rejectsUnknownTargetType() {
    assertThrows(IllegalArgumentException.class,
            () -> quickNoteService.create(campaign.getId(), "UNKNOWN", UUID.randomUUID(), "No target"));
}

@Test
void refusesMutationThroughAnotherCampaign() {
    Campaign other = new Campaign();
    other.setName("Other Campaign");
    campaignRepository.save(other);
    QuickNote note = quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "Private note");

    assertThrows(dev.hendrikhoemberg.dmhelper.common.NotFoundException.class,
            () -> quickNoteService.delete(other.getId(), note.getId()));
    assertTrue(quickNoteRepository.findById(note.getId()).isPresent());
}

@Test
void promotesQuickNoteWithEncounterLink() {
    dev.hendrikhoemberg.dmhelper.encounter.data.Encounter encounter =
            new dev.hendrikhoemberg.dmhelper.encounter.data.Encounter();
    encounter.setCampaign(campaign);
    encounter.setName("Crypt Ambush");
    encounter.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.PLANNED);
    encounterRepository.save(encounter);

    QuickNote qn = quickNoteService.create(
            campaign.getId(), "ENCOUNTER", encounter.getId(), "The ghouls arrive in round two.");
    Note promoted = quickNoteService.promoteToNote(campaign.getId(), qn.getId());

    assertTrue(promoted.getBody().startsWith("[[encounter:Crypt Ambush]]"));
}
```

- [ ] **Step 7: Run the service tests and verify the new method signatures and behavior fail**

Run:

```bash
./mvnw -Dtest=QuickNoteServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: test compilation fails because campaign-scoped mutation methods do not exist.

- [ ] **Step 8: Add campaign-scoped repository lookup and target parsing**

Add to `QuickNoteRepository`:

```java
Optional<QuickNote> findByIdAndCampaignId(UUID id, UUID campaignId);
```

Add this enum and lookup method inside `QuickNoteService`:

```java
private enum TargetType {
    CAMPAIGN, PARTY_MEMBER, MAP, ENCOUNTER, NOTE, HANDOUT, STATBLOCK, SCENE
}

private TargetType parseTargetType(String raw) {
    try {
        return TargetType.valueOf(raw == null ? "" : raw.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Unsupported quick-note target type: " + raw);
    }
}

private QuickNote findByCampaignAndId(UUID campaignId, UUID id) {
    return quickNoteRepository.findByIdAndCampaignId(id, campaignId)
            .orElseThrow(() -> new NotFoundException("QuickNote not found"));
}
```

In `create`, persist `parseTargetType(targetType).name()` and reject a blank body:

```java
if (body == null || body.isBlank()) {
    throw new IllegalArgumentException("Quick-note body is required");
}
qn.setTargetType(parseTargetType(targetType).name());
qn.setBody(body.strip());
```

Update every existing `QuickNoteServiceTest` mutation call to pass `campaign.getId()` as the first
argument. This includes both promotion overloads and `delete`.

- [ ] **Step 9: Scope mutations to the campaign and add encounter links**

Replace mutation methods with these signatures and bodies:

```java
public Note promoteToNote(UUID campaignId, UUID quickNoteId) {
    QuickNote qn = findByCampaignAndId(campaignId, quickNoteId);
    String targetLink = resolveTargetLink(qn);
    String title = qn.getBody().length() > 80
            ? qn.getBody().substring(0, 77) + "..."
            : qn.getBody();
    Note note = noteService.create(campaignId, NoteType.GENERIC, title, targetLink + qn.getBody(), "");
    quickNoteRepository.delete(qn);
    return note;
}

public Note promoteToNote(UUID campaignId, UUID quickNoteId, String title, NoteType type) {
    QuickNote qn = findByCampaignAndId(campaignId, quickNoteId);
    Note note = noteService.create(campaignId, type, title, resolveTargetLink(qn) + qn.getBody(), "");
    quickNoteRepository.delete(qn);
    return note;
}

public void delete(UUID campaignId, UUID id) {
    quickNoteRepository.delete(findByCampaignAndId(campaignId, id));
}
```

Add this branch to `resolveTargetLink`:

```java
case "ENCOUNTER" -> encounterRepository.findById(targetId)
        .map(encounter -> "[[encounter:" + encounter.getName() + "]]\n\n")
        .orElse("");
```

Inject `EncounterRepository` into the service constructor.

- [ ] **Step 10: Change the controller to use campaign-scoped mutations**

Use these calls in `QuickNoteApiController`:

```java
quickNoteService.delete(campaignId, id);
```

```java
Note note = (title != null && type != null)
        ? quickNoteService.promoteToNote(campaignId, id, title, type)
        : quickNoteService.promoteToNote(campaignId, id);
```

- [ ] **Step 11: Add controller delegation tests**

Create `QuickNoteApiControllerTest` with `@WebMvcTest(QuickNoteApiController.class)`, a mocked `QuickNoteService`, and these tests:

```java
@Test
void deleteScopesTheMutationToThePathCampaign() throws Exception {
    UUID campaignId = UUID.randomUUID();
    UUID noteId = UUID.randomUUID();

    mockMvc.perform(delete("/api/v1/campaigns/{campaignId}/quicknotes/{id}", campaignId, noteId))
            .andExpect(status().isNoContent());

    verify(quickNoteService).delete(campaignId, noteId);
}

@Test
void promoteScopesTheMutationToThePathCampaign() throws Exception {
    UUID campaignId = UUID.randomUUID();
    UUID noteId = UUID.randomUUID();
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    Note promoted = new Note();
    promoted.setId(UUID.randomUUID());
    promoted.setCampaign(campaign);
    when(quickNoteService.promoteToNote(campaignId, noteId)).thenReturn(promoted);

    mockMvc.perform(post("/api/v1/campaigns/{campaignId}/quicknotes/{id}/promote", campaignId, noteId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noteId").value(promoted.getId().toString()));

    verify(quickNoteService).promoteToNote(campaignId, noteId);
}
```

Include the exact static imports for `delete`, `post`, `status`, `jsonPath`, Mockito `verify`/`when`, and autowire `MockMvc`.

- [ ] **Step 12: Bind quick-note parameters through data attributes**

Replace the fragment root with:

```html
<div x-data="quicknotes"
     th:attr="data-campaign-id=${campaignId},data-target-type=${targetType},data-target-id=${targetId}"
     class="quicknotes-strip">
```

Change the Alpine registration to a parameterless `quicknotes` factory. Initialize identifiers from
the root element:

```javascript
campaignId: '',
targetType: '',
targetId: '',

async init() {
    this.campaignId = this.$el.dataset.campaignId;
    this.targetType = this.$el.dataset.targetType;
    this.targetId = this.$el.dataset.targetId;
    await this.load();
},

async request(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        throw new Error('HTTP ' + response.status);
    }
    return response;
},

reportFailure(message, error) {
    console.error(message, error);
    window.showToast?.(message, 'error', 5000);
}
```

Use `request` for all four operations. In `add`, do not clear `newBody` until the POST has succeeded:

```javascript
async add() {
    const body = this.newBody.trim();
    if (!body) return;
    try {
        const formData = new FormData();
        formData.append('targetType', this.targetType);
        formData.append('targetId', this.targetId);
        formData.append('body', body);
        const response = await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes',
            { method: 'POST', body: formData });
        this.items.push(await response.json());
        this.newBody = '';
    } catch (error) {
        this.newBody = body;
        this.reportFailure('Could not save the quick note. Your text has been kept.', error);
    }
},
```

For delete and promote, update `items` or navigate only after `request` succeeds. Report
`Could not delete the quick note. Nothing was changed.` and
`Could not promote the quick note. Nothing was changed.` respectively. Load failure leaves the
existing `items` array intact and reports `Could not load quick notes.`.

- [ ] **Step 13: Run the focused quick-note and template tests**

Run:

```bash
./mvnw -Dtest=QuickNoteServiceTest,QuickNoteApiControllerTest,HtmxTemplateExpressionTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 14: Re-run the guarded browser flow**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: PASS with no browser failure entries.

- [ ] **Step 15: Commit quick-note integrity and its browser guards**

```bash
git add src/main/resources/templates/notes/_quicknotes-strip.html src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiController.java src/test/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiControllerTest.java src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteServiceTest.java src/test/java/dev/hendrikhoemberg/dmhelper/config/HtmxTemplateExpressionTest.java src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "fix: make quick notes reliable and campaign scoped"
```

### Task 2: Introduce the shared content destination registry

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistry.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistryTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRouteContractTest.java`

**Interfaces:**
- Consumes: content type, campaign ID, entity ID, optional parent/source key, and display name.
- Produces: `campaign(...)` and `library(...)` URL methods.

- [ ] **Step 1: Write the exhaustive failing destination contract**

Create `ContentDestinationRegistryTest` and assert these exact results:

```java
class ContentDestinationRegistryTest {
    private final ContentDestinationRegistry registry = new ContentDestinationRegistry();
    private final UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID entityId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adventureId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void campaignDestinationsMatchControllerRoutes() {
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.NOTE,
                campaignId, entityId, null)).isEqualTo(
                "/campaigns/11111111-1111-1111-1111-111111111111/notes/22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.MAP,
                campaignId, entityId, null)).endsWith("/maps/22222222-2222-2222-2222-222222222222/play");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.ENCOUNTER,
                campaignId, entityId, null)).endsWith("/encounters/22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.HANDOUT,
                campaignId, entityId, null)).endsWith("/handouts#handout-22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.PARTY_MEMBER,
                campaignId, entityId, null)).endsWith("/party#pm-card-22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.PARTY_MEMBER_SHEET,
                campaignId, entityId, null)).endsWith("/party/22222222-2222-2222-2222-222222222222/sheet");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.SCENE,
                campaignId, entityId, adventureId)).endsWith(
                "/adventures/33333333-3333-3333-3333-333333333333/scenes/22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.QUICK_NOTE,
                campaignId, entityId, null)).endsWith("/notes");
    }

    @Test
    void libraryDestinationsUseDetailsOrFilteredTabs() {
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.STATBLOCK,
                entityId, "goblin", "Goblin")).isEqualTo("/library/statblocks/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.CLASS,
                entityId, "srd-2024_fighter", "Fighter")).isEqualTo("/library/classes/srd-2024_fighter");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.SPELL,
                entityId, "fireball", "Fireball")).isEqualTo("/library?tab=spells&search=Fireball");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.MAGIC_ITEM,
                entityId, "bag-of-holding", "Bag of Holding")).isEqualTo(
                "/library?tab=magic-items&search=Bag%20of%20Holding");
    }

    @Test
    void everyEnumValueHasADestination() {
        assertThat(Arrays.stream(ContentDestinationRegistry.CampaignType.values())
                .map(type -> registry.campaign(type, campaignId, entityId, adventureId)))
                .allMatch(url -> url.startsWith("/"));
        assertThat(Arrays.stream(ContentDestinationRegistry.LibraryType.values())
                .map(type -> registry.library(type, entityId, "source-key", "Display Name")))
                .allMatch(url -> url.startsWith("/library"));
    }
}
```

- [ ] **Step 2: Run the registry test and verify it fails to compile**

Run:

```bash
./mvnw -Dtest=ContentDestinationRegistryTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: compilation failure because `ContentDestinationRegistry` does not exist.

- [ ] **Step 3: Implement the registry**

Create `ContentDestinationRegistry.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class ContentDestinationRegistry {
    public enum CampaignType {
        NOTE, QUICK_NOTE, MAP, ENCOUNTER, HANDOUT, PARTY_MEMBER, PARTY_MEMBER_SHEET, SCENE
    }

    public enum LibraryType {
        STATBLOCK(null), SPELL("spells"), CONDITION("conditions"), RULE("rules"),
        EQUIPMENT("equipment"), MAGIC_ITEM("magic-items"), CLASS(null),
        SPECIES("species"), BACKGROUND("backgrounds"), FEAT("feats");

        private final String tab;

        LibraryType(String tab) {
            this.tab = tab;
        }
    }

    public String campaign(CampaignType type, UUID campaignId, UUID entityId, UUID parentId) {
        String root = "/campaigns/" + campaignId;
        return switch (type) {
            case NOTE -> root + "/notes/" + entityId;
            case QUICK_NOTE -> root + "/notes";
            case MAP -> root + "/maps/" + entityId + "/play";
            case ENCOUNTER -> root + "/encounters/" + entityId;
            case HANDOUT -> root + "/handouts#handout-" + entityId;
            case PARTY_MEMBER -> root + "/party#pm-card-" + entityId;
            case PARTY_MEMBER_SHEET -> root + "/party/" + entityId + "/sheet";
            case SCENE -> {
                if (parentId == null) throw new IllegalArgumentException("Scene destination requires adventure ID");
                yield root + "/adventures/" + parentId + "/scenes/" + entityId;
            }
        };
    }

    public String library(LibraryType type, UUID entityId, String sourceKey, String displayName) {
        return switch (type) {
            case STATBLOCK -> "/library/statblocks/" + entityId;
            case CLASS -> {
                if (sourceKey == null || sourceKey.isBlank()) {
                    yield filtered("classes", displayName);
                }
                yield "/library/classes/" + encodePathSegment(sourceKey);
            }
            default -> filtered(type.tab, displayName);
        };
    }

    private String filtered(String tab, String displayName) {
        return "/library?tab=" + encodeQuery(tab) + "&search=" + encodeQuery(displayName);
    }

    private String encodeQuery(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Run the registry test**

Run:

```bash
./mvnw -Dtest=ContentDestinationRegistryTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: PASS.

- [ ] **Step 5: Add the route-registration contract**

Create `ContentDestinationRouteContractTest.java` so the registry is checked against Spring's
actual handler mappings:

```java
package dev.hendrikhoemberg.dmhelper.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContentDestinationRouteContractTest {
    @Autowired private ContentDestinationRegistry registry;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private final UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID entityId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adventureId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void everyGeneratedDestinationMatchesARegisteredControllerRoute() {
        Set<String> registered = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());
        PathPatternParser parser = new PathPatternParser();

        var campaignUrls = Arrays.stream(ContentDestinationRegistry.CampaignType.values())
                .map(type -> registry.campaign(type, campaignId, entityId, adventureId));
        var libraryUrls = Arrays.stream(ContentDestinationRegistry.LibraryType.values())
                .map(type -> registry.library(type, entityId, "source-key", "Display Name"));

        java.util.stream.Stream.concat(campaignUrls, libraryUrls).forEach(url -> {
            String path = URI.create(url).getPath();
            boolean matched = registered.stream()
                    .map(parser::parse)
                    .anyMatch(pattern -> pattern.matches(PathContainer.parsePath(path)));
            assertThat(matched).as("registered controller route for %s", url).isTrue();
        });
    }
}
```

Run:

```bash
./mvnw -Dtest=ContentDestinationRegistryTest,ContentDestinationRouteContractTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: both tests PASS and every generated URL path matches a registered controller mapping.

- [ ] **Step 6: Commit the destination contract**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistry.java src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistryTest.java src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRouteContractTest.java
git commit -m "feat: centralize content destinations"
```

### Task 3: Rewire command-palette results and library deep links

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`
- Modify: `src/main/resources/templates/library/list.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java`

**Interfaces:**
- Consumes: `ContentDestinationRegistry`, `CharacterSheetRepository.findByPartyMemberId(UUID)`, and URL parameters `tab`/`search`.
- Produces: globally ranked `List<SearchResultItem>` with at most 20 entries and valid destinations.

- [ ] **Step 1: Write failing palette URL, ordering, and cap tests**

Extend the `@Import` in `CommandPaletteServiceTest` to include
`ContentDestinationRegistry.class`. Add imports for `Handout`, `HandoutRepository`, `PartyMember`,
`PartyMemberRepository`, `CharacterSheet`, and `CharacterSheetRepository`, then add these fields:

```java
@Autowired private HandoutRepository handoutRepository;
@Autowired private PartyMemberRepository partyMemberRepository;
@Autowired private CharacterSheetRepository characterSheetRepository;
```

Add these assertions:

```java
@Test
void mapResultUsesPlayRoute() {
    var result = commandPaletteService.search("Tavern Map", campaign.getId()).stream()
            .filter(item -> item.type().equals("map"))
            .findFirst().orElseThrow();
    assertThat(result.url()).endsWith("/play");
    assertThat(result.url()).doesNotContain("/battle");
}

@Test
void spellResultUsesFilteredLibraryTab() {
    var result = commandPaletteService.search("Fireball", null).stream()
            .filter(item -> item.type().equals("spell"))
            .findFirst().orElseThrow();
    assertThat(result.url()).isEqualTo("/library?tab=spells&search=Fireball");
}

@Test
void handoutResultUsesTheGalleryCardAnchor() {
    Handout handout = new Handout();
    handout.setCampaign(campaign);
    handout.setTitle("Royal Invitation");
    handout.setFileName("invitation.png");
    handout = handoutRepository.save(handout);

    var result = commandPaletteService.search("Royal Invitation", campaign.getId()).stream()
            .filter(item -> item.type().equals("handout"))
            .findFirst().orElseThrow();
    assertThat(result.url()).isEqualTo(
            "/campaigns/" + campaign.getId() + "/handouts#handout-" + handout.getId());
}

@Test
void partyMemberUsesRosterUntilASheetExists() {
    PartyMember member = new PartyMember();
    member.setCampaign(campaign);
    member.setCharacterName("Arannis");
    member = partyMemberRepository.save(member);

    var rosterResult = commandPaletteService.search("Arannis", campaign.getId()).stream()
            .filter(item -> item.type().equals("party-member"))
            .findFirst().orElseThrow();
    assertThat(rosterResult.url()).isEqualTo(
            "/campaigns/" + campaign.getId() + "/party#pm-card-" + member.getId());

    CharacterSheet sheet = new CharacterSheet();
    sheet.setPartyMember(member);
    characterSheetRepository.save(sheet);

    var sheetResult = commandPaletteService.search("Arannis", campaign.getId()).stream()
            .filter(item -> item.type().equals("party-member"))
            .findFirst().orElseThrow();
    assertThat(sheetResult.url()).isEqualTo(
            "/campaigns/" + campaign.getId() + "/party/" + member.getId() + "/sheet");
}

@Test
void resultsAreGloballyCapped() {
    for (int index = 0; index < 30; index++) {
        Note note = new Note();
        note.setCampaign(campaign);
        note.setTitle("Shared Result " + index);
        note.setType(NoteType.GENERIC);
        note.setBody("shared result body");
        noteRepository.save(note);
    }
    assertThat(commandPaletteService.search("shared result", campaign.getId())).hasSize(20);
}

@Test
void exactCampaignTitleRanksAheadOfEqualGlobalTitle() {
    Note note = new Note();
    note.setCampaign(campaign);
    note.setTitle("Goblin");
    note.setType(NoteType.NPC);
    note.setBody("");
    noteRepository.save(note);

    var results = commandPaletteService.search("Goblin", campaign.getId());
    assertThat(results.getFirst().type()).isEqualTo("note");
}
```

- [ ] **Step 2: Run palette tests and verify route/cap failures**

Run:

```bash
./mvnw -Dtest=CommandPaletteServiceTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: FAIL because the map and spell URLs are invalid and the service applies per-category limits.

- [ ] **Step 3: Inject the destination registry and sheet repository**

Add fields and constructor parameters:

```java
private final ContentDestinationRegistry destinations;
private final CharacterSheetRepository characterSheetRepo;
```

Replace every URL literal in result mapping with a registry call. Use these mappings:

```java
destinations.campaign(ContentDestinationRegistry.CampaignType.NOTE, campaignId, n.getId(), null)
destinations.campaign(ContentDestinationRegistry.CampaignType.QUICK_NOTE, campaignId, qn.getId(), null)
destinations.campaign(ContentDestinationRegistry.CampaignType.MAP, campaignId, m.getId(), null)
destinations.campaign(ContentDestinationRegistry.CampaignType.ENCOUNTER, campaignId, e.getId(), null)
destinations.campaign(ContentDestinationRegistry.CampaignType.HANDOUT, campaignId, h.getId(), null)
destinations.campaign(
        characterSheetRepo.findByPartyMemberId(pm.getId()).isPresent()
                ? ContentDestinationRegistry.CampaignType.PARTY_MEMBER_SHEET
                : ContentDestinationRegistry.CampaignType.PARTY_MEMBER,
        campaignId, pm.getId(), null)
destinations.campaign(ContentDestinationRegistry.CampaignType.SCENE, campaignId, s.getId(),
        s.getChapter().getAdventure().getId())
```

Map library entities to the matching `LibraryType`; classes pass `getSourceKey()`, while the other entities pass their available source key or `null`.

- [ ] **Step 4: Replace per-category limiting with one relevance pipeline**

Add this internal record and helpers:

```java
private record RankedResult(SearchResultItem item, int relevance) {}

private int relevance(String title, String body, String query, boolean campaignOwned) {
    String normalizedTitle = title == null ? "" : title.toLowerCase();
    int base;
    if (normalizedTitle.equals(query)) base = 0;
    else if (normalizedTitle.startsWith(query)) base = 10;
    else if (normalizedTitle.contains(query)) base = 20;
    else if (body != null && body.toLowerCase().contains(query)) base = 30;
    else base = 40;
    return base + (campaignOwned ? 0 : 1);
}

private void add(List<RankedResult> results, SearchResultItem item,
                 String searchableBody, String query, boolean campaignOwned) {
    results.add(new RankedResult(item,
            relevance(item.title(), searchableBody, query, campaignOwned)));
}
```

Change the accumulator to `List<RankedResult>`. Remove every intermediate `.limit(...)`. Finish `search` with:

```java
return results.stream()
        .sorted(java.util.Comparator.comparingInt(RankedResult::relevance)
                .thenComparing(result -> result.item().title(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(result -> result.item().type()))
        .limit(MAX_RESULTS)
        .map(RankedResult::item)
        .toList();
```

Each repository mapping must call `add(...)` with the entity body for notes/quick notes and `null` for title-only types.

- [ ] **Step 5: Write the failing library deep-link controller test**

Add to `LibraryControllerTest`:

```java
@Test
void listExposesRequestedInitialTabAndSearch() throws Exception {
    mockMvc.perform(get("/library")
                    .param("tab", "spells")
                    .param("search", "Fireball"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("initialTab", "spells"))
            .andExpect(model().attribute("initialSearch", "Fireball"));
}
```

- [ ] **Step 6: Add the library deep-link model**

Replace the no-argument library list handler with:

```java
@GetMapping
public String list(@RequestParam(defaultValue = "monsters") String tab,
                   @RequestParam(required = false, defaultValue = "") String search,
                   Model model) {
    Set<String> tabs = Set.of("monsters", "spells", "conditions", "rules", "equipment",
            "magic-items", "classes", "species", "backgrounds", "feats");
    model.addAttribute("initialTab", tabs.contains(tab) ? tab : "monsters");
    model.addAttribute("initialSearch", search);
    return "library/list";
}
```

On the library `<main>` element, add:

```html
th:attr="data-initial-tab=${initialTab},data-initial-search=${initialSearch}"
id="library-root"
```

Replace the tab function and add initialization:

```javascript
function switchCompendiumTab(tab) {
    document.querySelectorAll('.form-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.compendium-section').forEach(s => s.classList.add('hidden'));
    document.getElementById('tab-' + tab)?.classList.add('active');
    document.getElementById('section-' + tab)?.classList.remove('hidden');
}

document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('library-root');
    const tab = root?.dataset.initialTab || 'monsters';
    const search = root?.dataset.initialSearch || '';
    switchCompendiumTab(tab);
    if (!search) return;
    const input = document.querySelector('#section-' + tab + ' input[name="search"]');
    if (!input) return;
    input.value = search;
    input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'a' }));
});
```

- [ ] **Step 7: Run palette and library tests**

Run:

```bash
./mvnw -Dtest=CommandPaletteServiceTest,LibraryControllerTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: PASS with globally capped results and exact valid destinations.

- [ ] **Step 8: Commit palette and deep-link behavior**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java src/test/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteServiceTest.java src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java src/main/resources/templates/library/list.html src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java
git commit -m "fix: route palette results through valid destinations"
```

### Task 4: Reuse destination contracts in wiki links and exercise browser flows

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Consumes: `ContentDestinationRegistry` and the guarded Playwright page from Task 1.
- Produces: consistent wiki/map/handout destinations plus browser coverage for quick-note creation and palette navigation.

- [ ] **Step 1: Strengthen wiki destination assertions**

In `NoteServiceTest.resolvesHandoutAndMapPrefixes`, render the note and assert:

```java
String rendered = noteService.renderBody(note);
assertTrue(rendered.contains("/campaigns/" + campaign.getId() + "/maps/" + dungeon.getId() + "/play"));
assertTrue(rendered.contains("/campaigns/" + campaign.getId() + "/handouts#handout-" + letter.getId()));
assertFalse(rendered.contains("/battle"));
```

- [ ] **Step 2: Run the note test and verify the map assertion fails**

Run:

```bash
./mvnw -Dtest=NoteServiceTest#resolvesHandoutAndMapPrefixes test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: FAIL because the rendered map link ends in `/battle`.

- [ ] **Step 3: Inject and use `ContentDestinationRegistry` in `NoteService`**

Add the registry to the constructor and replace resolved URL construction with:

```java
url = destinations.campaign(ContentDestinationRegistry.CampaignType.NOTE,
        note.getCampaign().getId(), notes.getFirst().getId(), null);
```

```java
url = destinations.library(ContentDestinationRegistry.LibraryType.STATBLOCK,
        hits.getFirst().getId(), hits.getFirst().getSourceKey(), hits.getFirst().getName());
```

```java
url = destinations.campaign(ContentDestinationRegistry.CampaignType.HANDOUT,
        note.getCampaign().getId(), match.get().getId(), null);
```

```java
url = destinations.campaign(ContentDestinationRegistry.CampaignType.MAP,
        note.getCampaign().getId(), match.get().getId(), null);
```

```java
url = destinations.campaign(ContentDestinationRegistry.CampaignType.ENCOUNTER,
        note.getCampaign().getId(), match.get().getId(), null);
```

```java
url = destinations.campaign(ContentDestinationRegistry.CampaignType.SCENE,
        note.getCampaign().getId(), s.getId(), s.getChapter().getAdventure().getId());
```

Update `@Import` declarations that construct `NoteService` directly to include `ContentDestinationRegistry.class`.

- [ ] **Step 4: Add the browser quick-note regression**

Append this ordered test after campaign creation and after the adventure exists:

```java
@Test
@Order(8)
void createQuickNoteWithoutTemplateOrRequestErrors() {
    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/adventures");
    dmPage.waitForLoadState(LoadState.NETWORKIDLE);
    dmPage.locator(".quicknotes-form input").first().fill("Remember the hidden stair.");
    dmPage.locator(".quicknotes-form button[type='submit']").first().click();
    dmPage.locator(".quicknote-row").first().waitFor();

    assertThat(dmPage.locator(".quicknote-body").first().textContent())
            .isEqualTo("Remember the hidden stair.");
}
```

The collector's `@AfterEach` assertion is the malformed-request and console-error assertion.

- [ ] **Step 5: Add the browser palette-navigation regression**

Append:

```java
@Test
@Order(9)
void commandPaletteOpensTheRealMapPlayRoute() {
    dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/maps");
    dmPage.waitForLoadState(LoadState.NETWORKIDLE);
    dmPage.evaluate("window.dispatchEvent(new CustomEvent('command-palette-toggle'))");
    dmPage.locator(".command-palette-input").fill("Test Battle Map");
    dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).waitFor();
    dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).click();
    dmPage.waitForURL(url -> url.endsWith("/play"));

    assertThat(dmPage.url()).endsWith("/play");
}
```

- [ ] **Step 6: Run the focused note and smoke tests**

Run:

```bash
./mvnw -Dtest=NoteServiceTest,CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: PASS with no browser failure entries.

- [ ] **Step 7: Commit shared navigation and browser regressions**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteServiceTest.java src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "test: cover quick notes and palette navigation in browser"
```

### Task 5: Full verification and implementation record

**Files:**
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`

**Interfaces:**
- Consumes: all changes from Tasks 1–4.
- Produces: verified P0 interaction-integrity checkpoint and an accurate status note in the master specification.

- [ ] **Step 1: Run focused interaction-integrity tests**

Run:

```bash
./mvnw -Dtest=HtmxTemplateExpressionTest,QuickNoteServiceTest,QuickNoteApiControllerTest,ContentDestinationRegistryTest,ContentDestinationRouteContractTest,CommandPaletteServiceTest,LibraryControllerTest,NoteServiceTest,CoreSessionLoopSmokeTest test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: all selected tests pass with zero failures and zero errors.

- [ ] **Step 2: Run the complete test suite**

Run:

```bash
./mvnw test -DargLine=-Duser.home=/tmp/dmhelper-p0
```

Expected: `BUILD SUCCESS`, zero failures, and zero errors.

- [ ] **Step 3: Verify route and placeholder invariants directly**

Run:

```bash
rg -n '\[\[\$\{|/battle|/handouts/.*/view|/party/.*\)$' src/main/resources/templates/notes/_quicknotes-strip.html src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java
```

Expected: no matches.

- [ ] **Step 4: Record the completed slice without marking the entire P0 gate complete**

Under section `6. Workstream A — Existing-path reliability` in the master specification, add:

```markdown
> **Implementation status:** The interaction-integrity checkpoint commit completed quick-note
> rendering/mutations, browser failure guards, shared content destinations, palette ranking/cap,
> library deep links, and wiki route reuse. Asset import safety, tracker/map mutation feedback,
> difficulty labeling, and correlation identifiers remain open P0 work.
```

- [ ] **Step 5: Check the final diff**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` exits 0; status lists only the intended master-spec documentation
change.

- [ ] **Step 6: Commit the verified checkpoint record**

```bash
git add docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md
git commit -m "docs: record P0 interaction integrity checkpoint"
```

## Completion Gate

This plan is complete only when:

- literal `[[${...}]]` placeholders cannot pass the static template suite;
- quick-note add/delete/promote retain state on failure and display a toast;
- quick-note mutations are scoped to the path campaign;
- encounter-target quick notes promote with a usable encounter link;
- every search result URL comes from `ContentDestinationRegistry`;
- every supported destination enum has a contract test;
- palette results are globally ranked and capped at 20;
- library tab URLs open and run the requested filter;
- wiki map links end in `/play`;
- the guarded Playwright flow creates a quick note and opens a map search result without console, page, request, or HTTP errors;
- the complete Maven suite reports zero failures and zero errors;
- the master spec records only this slice as complete, leaving the other P0 work visible.
