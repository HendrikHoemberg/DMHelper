# M12 — Table Polish & Generative Tooling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish every loose end so a DM can run a real session start-to-finish, and make the campaign import pipeline fully machine-friendly for AI-generated campaigns.

**Architecture:** M12 is six independent workstreams: (1) DM Mode goes global with `Ctrl+Shift+D`, (2) a `Ctrl+K` command palette searches everything in one box, (3) full campaign export/import coverage (encounters + handouts), (4) dry-run import validation, (5) JSON Schemas for the campaign and map formats, and (6) keyboard shortcut systematization. The command palette is a new server-rendered overlay fragment served by `nav.html` with a `CommandPaletteApiController` that queries a `CommandPaletteService` aggregating across all entity repositories. Backups, error handling, and the SRD key catalog are already done.

**Tech Stack:** Spring Boot 4.1.0 + Thymeleaf + Alpine.js + htmx + Jackson 3 (tools.jackson), H2, vanilla JS ES modules

---

### File Structure

| File | Responsibility |
|---|---|
| `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiController.java` | REST endpoint `GET /api/v1/search?q=` that aggregates results from notes, statblocks, spells, conditions, rules, items, maps, encounters, handouts, quicknotes, party members, compendium |
| `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java` | Service that queries all repositories and returns `SearchResults` (list of `SearchResultItem` with id, title, type, subtype, url) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java` | REST endpoint `GET /api/v1/schemas/{name}` serving JSON Schema files from classpath |
| `src/main/resources/schemas/campaign-format.schema.json` | JSON Schema for the `.dmcampaign.json` export/import format |
| `src/main/resources/schemas/map-document.schema.json` | JSON Schema for the `MapDocument` internal format |
| `src/main/resources/templates/fragments/_command-palette.html` | Alpine.js-based overlay with search input and result list, toggled by `Ctrl+K` |
| `src/main/resources/static/js/command-palette.js` | `CommandPalette` Alpine data—debounced fetch to `/api/v1/search?q=`, keyboard navigation (up/down/enter/escape), result click navigates |
| `src/main/resources/static/js/keyboard.js` | Global keyboard shortcut manager—registers `Ctrl+Shift+D` (DM Mode) and `Ctrl+K` (command palette) and dispatches custom events |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java` | **Modify** — add `CombatantExportDto`, full `HandoutExportDto` with base64 image data |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` | **Modify** — full encounter+combatant export/import; handout image export/import; quicknote import; `validateImport()` for dry-run |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java` | **Modify** — add `?dryRun=true` parameter to `POST /campaigns/import` |
| `src/main/resources/templates/fragments/navbar.html` | **Modify** — wire DM Mode to `Ctrl+Shift+D` via custom event; add `_command-palette` include; add `keyboard.js` script |
| `src/main/resources/templates/fragments/head.html` | **Modify** — include `command-palette.js` and `keyboard.js` scripts |
| `src/main/resources/static/css/app.css` | **Modify** — command palette overlay styles; more DM Mode selectors for map switcher, party summary bar, statblock views |
| `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java` | **Modify** — exclude `/api/v1/schemas/**` from PIN gate |

---

### Task 1: Global Keyboard Shortcut Manager

**Files:**
- Create: `src/main/resources/static/js/keyboard.js`
- Modify: `src/main/resources/templates/fragments/head.html`

- [ ] **Step 1: Create keyboard.js**

```javascript
// keyboard.js — Global keyboard shortcut manager for DMHelper
// Dispatches custom events that other modules listen to.

(function () {
  'use strict';

  const SHORTCUTS = {
    'D': { ctrl: true, shift: true, event: 'dm-mode-toggle' },
    'k': { ctrl: true, event: 'command-palette-toggle' },
  };

  document.addEventListener('keydown', (e) => {
    for (const [key, config] of Object.entries(SHORTCUTS)) {
      const ctrlMatch = config.ctrl ? (e.ctrlKey || e.metaKey) : true;
      const shiftMatch = config.shift ? e.shiftKey : (config.shift === false ? false : true);
      if (ctrlMatch && shiftMatch && e.key === key) {
        e.preventDefault();
        window.dispatchEvent(new CustomEvent(config.event));
        return;
      }
    }
  });
})();
```

- [ ] **Step 2: Include keyboard.js in head.html**

Read `src/main/resources/templates/fragments/head.html`. Find the existing `<script>` tags block and add after the last vendor script:

```html
<script src="/vendor/konva.min.js"></script>
<script src="/js/keyboard.js" type="module"></script>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/keyboard.js src/main/resources/templates/fragments/head.html
git commit -m "feat: add global keyboard shortcut manager (Ctrl+K, Ctrl+Shift+D)"
```

---

### Task 2: DM Mode — Global Toggle with Ctrl+Shift+D

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Update navbar.html to listen for dm-mode-toggle event and sync the checkbox**

In `navbar.html`, replace the existing DM Mode checkbox (the `<label class="dm-toggle">...</label>` block around line 43) with:

```html
<label class="dm-toggle">
    <input type="checkbox" id="dmModeCheckbox">
    DM Mode
</label>
<script>
(function() {
    const checkbox = document.getElementById('dmModeCheckbox');
    checkbox.addEventListener('change', () => {
        document.body.classList.toggle('dm-mode-off', !checkbox.checked);
    });
    checkbox.checked = true;
    window.addEventListener('dm-mode-toggle', () => {
        checkbox.checked = !checkbox.checked;
        checkbox.dispatchEvent(new Event('change'));
    });
    window.addEventListener('tracker-dm-mode-grown', (e) => {
        checkbox.checked = e.detail.dmMode;
        document.body.classList.toggle('dm-mode-off', !e.detail.dmMode);
    });
})();
</script>
```

- [ ] **Step 2: Add DM Mode CSS rules to hide party summary bar, map switcher names, statblock prep views when DM Mode is off**

In `app.css`, after the existing DM Mode block (around line 757), add:

```css
body.dm-mode-off .party-summary-bar { display: none; }
body.dm-mode-off .map-switcher-name { display: none; }
body.dm-mode-off .statblock-full { display: none; }
body.dm-mode-off .encounter-prep { display: none; }
body.dm-mode-off .note-dm-only { display: none; }

/* Toolbar visual indicator when DM Mode is off */
.dm-toggle input:not(:checked) + span::after { content: " (player-safe)"; color: var(--color-warning); }
```

- [ ] **Step 3: Mark existing "DM-only" elements with data-dm-only or .dm-only classes**

Search for pages that show DM-only content:
- Party summary bar: Already has `.party-summary-bar` class (matches CSS rule above)
- Map switcher in `battle.html`: Add `data-dm-only` to map names that should hide
- Statblock detail in `library/detail.html`: Already wrapped (verify)
- Encounter prep sections: Already handled in `_tracker.html` via Alpine `dmMode`

No code changes needed here if CSS selectors already cover it—the rules above catch `.party-summary-bar`, `.map-switcher-name`, `.statblock-full`, `.encounter-prep`, `.note-dm-only`. Verify each exists on the right elements.

- [ ] **Step 4: Verify DM Mode across all screens**

Run the app and toggle DM Mode (checkbox) on each screen:
- Campaign list — no DM-only content, should be fine
- Campaign detail — no DM-only content
- Party list — summary bar hides ✓
- Map editor — canvas unaffected (editor is DM-only by nature, leave visible)
- Battle map — tokens, HP bars hide (already works from Alpine in `battle.html`)
- Encounters — encounter prep hides ✓
- Handouts — DM-only handouts hide
- Notes — DM-only notes hide (`.dm-only` from `Note.isDmOnly()`)
- Library — statblock details hide ✓
- Sheets — DM-only, leave visible (entire sheet feature is DM-only)
- Treasury/Ledger/Calendar — DM-only, leave visible

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html src/main/resources/static/css/app.css
git commit -m "feat: global DM Mode toggle with Ctrl+Shift+D, consistent across all screens"
```

---

### Task 3: Command Palette API — Backend

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiController.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.service.CommandPaletteService;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(CommandPaletteService.class)
class CommandPaletteServiceTest {

    @Autowired private CommandPaletteService commandPaletteService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private SpellRepository spellRepository;
    @Autowired private ConditionRepository conditionRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CampaignRepository campaignRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);

        Note note = new Note();
        note.setCampaign(campaign);
        note.setTitle("The Goblin Cave");
        note.setType(NoteType.LOCATION);
        note.setBody("A dark cave filled with goblins.");
        noteRepository.save(note);

        StatBlock sb = new StatBlock();
        sb.setName("Goblin");
        sb.setSourceKey("goblin");
        sb.setSource(StatBlock.Source.SRD);
        sb.setCr("1/4");
        sb.setType("Humanoid");
        sb.setAc(15);
        sb.setHp("7 (2d6)");
        sb.setSpeed("30 ft.");
        sb.setXp(50);
        statBlockRepository.save(sb);

        Spell spell = new Spell();
        spell.setName("Fireball");
        spell.setSourceKey("fireball");
        spell.setLevel(3);
        spell.setSchool("Evocation");
        spellRepository.save(spell);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Tavern Map");
        map.setGridWidth(30);
        map.setGridHeight(20);
        map.setCellSizePx(48);
        gameMapRepository.save(map);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Tavern Brawl");
        encounter.setStatus(Encounter.Status.PLANNED);
        encounterRepository.save(encounter);
    }

    @Test
    void searchFindsNotesByTitle() {
        var results = commandPaletteService.search("Cave", campaign.getId(), null);
        assertThat(results).anyMatch(r -> r.title().equals("The Goblin Cave") && r.type().equals("note"));
    }

    @Test
    void searchFindsStatblocksByName() {
        var results = commandPaletteService.search("Goblin", null, null);
        assertThat(results).anyMatch(r -> r.title().equals("Goblin") && r.type().equals("statblock"));
    }

    @Test
    void searchFindsSpellsByName() {
        var results = commandPaletteService.search("Fireball", null, null);
        assertThat(results).anyMatch(r -> r.title().equals("Fireball") && r.type().equals("spell"));
    }

    @Test
    void searchFindsMapsByName() {
        var results = commandPaletteService.search("Tavern", campaign.getId(), null);
        assertThat(results).anyMatch(r -> r.title().equals("Tavern Map") && r.type().equals("map"));
    }

    @Test
    void searchFindsEncountersByName() {
        var results = commandPaletteService.search("Brawl", campaign.getId(), null);
        assertThat(results).anyMatch(r -> r.title().equals("Tavern Brawl") && r.type().equals("encounter"));
    }

    @Test
    void searchWithNoCampaignOnlyReturnsGlobalContent() {
        var results = commandPaletteService.search("Goblin", null, null);
        assertThat(results).anyMatch(r -> r.type().equals("statblock"));
        assertThat(results).noneMatch(r -> r.type().equals("note"));
    }

    @Test
    void emptyQueryReturnsEmptyList() {
        var results = commandPaletteService.search("   ", campaign.getId(), null);
        assertThat(results).isEmpty();
    }
}
```

Create `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.common.service.CommandPaletteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommandPaletteApiController.class)
class CommandPaletteApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CommandPaletteService commandPaletteService;

    @Test
    void searchReturnsJsonResults() throws Exception {
        var item = new CommandPaletteService.SearchResultItem(
                "550e8400-e29b-41d4-a716-446655440000", "Goblin", "statblock",
                "Humanoid (CR 1/4)", "/library/statblocks/550e8400-e29b-41d4-a716-446655440000");
        when(commandPaletteService.search("Goblin", null, null)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/search").param("q", "Goblin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Goblin"))
                .andExpect(jsonPath("$[0].type").value("statblock"))
                .andExpect(jsonPath("$[0].url").value("/library/statblocks/550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void searchWithCampaignIdPassesItToService() throws Exception {
        var item = new CommandPaletteService.SearchResultItem(
                "abc", "The Cave", "note", "LOCATION", "/campaigns/uuid/notes/abc");
        when(commandPaletteService.search("cave", java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), null))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/search")
                .param("q", "cave")
                .param("campaignId", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("The Cave"));
    }

    @Test
    void searchWithEmptyQueryReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl . -Dtest="CommandPaletteServiceTest,CommandPaletteApiControllerTest" -DfailIfNoTests=false
```

Expected: Compilation errors — `CommandPaletteService` and `CommandPaletteApiController` not found.

- [ ] **Step 3: Create CommandPaletteService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.service;

import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CommandPaletteService {

    public record SearchResultItem(String id, String title, String type, String subtype, String url) {}

    private static final int MAX_RESULTS = 20;

    private final NoteRepository noteRepo;
    private final QuickNoteRepository quickNoteRepo;
    private final StatBlockRepository statBlockRepo;
    private final SpellRepository spellRepo;
    private final ConditionRepository conditionRepo;
    private final RuleSectionRepository ruleSectionRepo;
    private final EquipmentItemRepository equipmentItemRepo;
    private final MagicItemRepository magicItemRepo;
    private final CharacterClassRepository characterClassRepo;
    private final SpeciesRepository speciesRepo;
    private final BackgroundRepository backgroundRepo;
    private final FeatRepository featRepo;
    private final GameMapRepository gameMapRepo;
    private final EncounterRepository encounterRepo;
    private final HandoutRepository handoutRepo;
    private final PartyMemberRepository partyMemberRepo;

    public CommandPaletteService(NoteRepository noteRepo, QuickNoteRepository quickNoteRepo,
                                  StatBlockRepository statBlockRepo, SpellRepository spellRepo,
                                  ConditionRepository conditionRepo, RuleSectionRepository ruleSectionRepo,
                                  EquipmentItemRepository equipmentItemRepo, MagicItemRepository magicItemRepo,
                                  CharacterClassRepository characterClassRepo, SpeciesRepository speciesRepo,
                                  BackgroundRepository backgroundRepo, FeatRepository featRepo,
                                  GameMapRepository gameMapRepo, EncounterRepository encounterRepo,
                                  HandoutRepository handoutRepo, PartyMemberRepository partyMemberRepo) {
        this.noteRepo = noteRepo;
        this.quickNoteRepo = quickNoteRepo;
        this.statBlockRepo = statBlockRepo;
        this.spellRepo = spellRepo;
        this.conditionRepo = conditionRepo;
        this.ruleSectionRepo = ruleSectionRepo;
        this.equipmentItemRepo = equipmentItemRepo;
        this.magicItemRepo = magicItemRepo;
        this.characterClassRepo = characterClassRepo;
        this.speciesRepo = speciesRepo;
        this.backgroundRepo = backgroundRepo;
        this.featRepo = featRepo;
        this.gameMapRepo = gameMapRepo;
        this.encounterRepo = encounterRepo;
        this.handoutRepo = handoutRepo;
        this.partyMemberRepo = partyMemberRepo;
    }

    public List<SearchResultItem> search(String query, UUID campaignId, String typeFilter) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.strip().toLowerCase();
        List<SearchResultItem> results = new ArrayList<>();

        if (campaignId != null) {
            noteRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                    .filter(n -> matches(n.getTitle(), q) || matches(n.getBody(), q))
                    .limit(MAX_RESULTS)
                    .map(n -> new SearchResultItem(n.getId().toString(), n.getTitle(), "note",
                            n.getType().name(), "/campaigns/" + campaignId + "/notes/" + n.getId()))
                    .forEach(results::add);

            quickNoteRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                    .filter(qn -> matches(qn.getBody(), q))
                    .limit(5)
                    .map(qn -> new SearchResultItem(qn.getId().toString(),
                            qn.getBody().length() > 80 ? qn.getBody().substring(0, 77) + "..." : qn.getBody(),
                            "quicknote", qn.getTargetType(),
                            "/campaigns/" + campaignId + "/notes"))
                    .forEach(results::add);

            gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId).stream()
                    .filter(m -> matches(m.getName(), q))
                    .limit(MAX_RESULTS)
                    .map(m -> new SearchResultItem(m.getId().toString(), m.getName(), "map",
                            m.getGridWidth() + "x" + m.getGridHeight(),
                            "/campaigns/" + campaignId + "/maps/" + m.getId() + "/battle"))
                    .forEach(results::add);

            encounterRepo.findByCampaignIdOrderByNameAsc(campaignId).stream()
                    .filter(e -> matches(e.getName(), q))
                    .limit(MAX_RESULTS)
                    .map(e -> new SearchResultItem(e.getId().toString(), e.getName(), "encounter",
                            e.getStatus().name(),
                            "/campaigns/" + campaignId + "/encounters/" + e.getId()))
                    .forEach(results::add);

            handoutRepo.findByCampaignIdOrderByTitleAsc(campaignId).stream()
                    .filter(h -> matches(h.getTitle(), q))
                    .limit(MAX_RESULTS)
                    .map(h -> new SearchResultItem(h.getId().toString(), h.getTitle(), "handout",
                            null, "/campaigns/" + campaignId + "/handouts/" + h.getId() + "/view"))
                    .forEach(results::add);

            partyMemberRepo.findByCampaignIdOrderByCharacterNameAsc(campaignId).stream()
                    .filter(pm -> matches(pm.getCharacterName(), q) || matches(pm.getPlayerName(), q))
                    .limit(MAX_RESULTS)
                    .map(pm -> new SearchResultItem(pm.getId().toString(), pm.getCharacterName(),
                            "party-member", pm.getClassAndLevel(),
                            "/campaigns/" + campaignId + "/party/" + pm.getId()))
                    .forEach(results::add);
        }

        statBlockRepo.searchByNameContaining(q).stream()
                .limit(MAX_RESULTS)
                .map(sb -> new SearchResultItem(sb.getId().toString(), sb.getName(), "statblock",
                        sb.getType() + " (CR " + sb.getCr() + ")",
                        "/library/statblocks/" + sb.getId()))
                .forEach(results::add);

        spellRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(s -> new SearchResultItem(s.getId().toString(), s.getName(), "spell",
                        "Level " + s.getLevel() + " " + s.getSchool(),
                        "/library/spells/" + s.getId()))
                .forEach(results::add);

        conditionRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(c -> new SearchResultItem(c.getId().toString(), c.getName(), "condition",
                        null, "/library/conditions/" + c.getId()))
                .forEach(results::add);

        ruleSectionRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(r -> new SearchResultItem(r.getId().toString(), r.getName(), "rule",
                        r.getGroup(), "/library/rules/" + r.getId()))
                .forEach(results::add);

        equipmentItemRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(e -> new SearchResultItem(e.getId().toString(), e.getName(), "equipment",
                        e.getCategory(), "/library/equipment/" + e.getId()))
                .forEach(results::add);

        magicItemRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(m -> new SearchResultItem(m.getId().toString(), m.getName(), "magic-item",
                        m.getRarity(), "/library/magic-items/" + m.getId()))
                .forEach(results::add);

        characterClassRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(c -> new SearchResultItem(c.getId().toString(), c.getName(), "class",
                        null, "/library/classes/" + c.getId()))
                .forEach(results::add);

        speciesRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(s -> new SearchResultItem(s.getId().toString(), s.getName(), "species",
                        null, "/library/species/" + s.getId()))
                .forEach(results::add);

        backgroundRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(b -> new SearchResultItem(b.getId().toString(), b.getName(), "background",
                        null, "/library/backgrounds/" + b.getId()))
                .forEach(results::add);

        featRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(f -> new SearchResultItem(f.getId().toString(), f.getName(), "feat",
                        null, "/library/feats/" + f.getId()))
                .forEach(results::add);

        return results;
    }

    private boolean matches(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }
}
```

- [ ] **Step 4: Add missing repository query methods**

Several repositories need `searchByNameContaining` or `findByNameContainingIgnoreCaseOrderByNameAsc` methods. Add them to the following repositories:

In `StatBlockRepository.java`:
```java
List<StatBlock> searchByNameContaining(String name);
```
(Spring Data JPA derived query: creates `WHERE name LIKE %?1%`)

In `SpellRepository.java`:
```java
List<Spell> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `ConditionRepository.java`:
```java
List<Condition> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `RuleSectionRepository.java`:
```java
List<RuleSection> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `EquipmentItemRepository.java`:
```java
List<EquipmentItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `MagicItemRepository.java`:
```java
List<MagicItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `CharacterClassRepository.java`:
```java
List<CharacterClass> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `SpeciesRepository.java`:
```java
List<Species> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `BackgroundRepository.java`:
```java
List<Background> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

In `FeatRepository.java`:
```java
List<Feat> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
```

- [ ] **Step 5: Create CommandPaletteApiController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiController.java`:

```java
package io.github.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.common.service.CommandPaletteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommandPaletteApiController {

    private final CommandPaletteService commandPaletteService;

    public CommandPaletteApiController(CommandPaletteService commandPaletteService) {
        this.commandPaletteService = commandPaletteService;
    }

    @GetMapping("/search")
    public List<CommandPaletteService.SearchResultItem> search(
            @RequestParam String q,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) String type) {
        return commandPaletteService.search(q, campaignId, type);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
mvn test -pl . -Dtest="CommandPaletteServiceTest,CommandPaletteApiControllerTest"
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiController.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteServiceTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/common/web/CommandPaletteApiControllerTest.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/data/
git commit -m "feat: add command palette API — POST /api/v1/search aggregates all entity types"
```

---

### Task 4: Command Palette UI — Alpine.js Overlay

**Files:**
- Create: `src/main/resources/templates/fragments/_command-palette.html`
- Create: `src/main/resources/static/js/command-palette.js`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Create command-palette.js**

Create `src/main/resources/static/js/command-palette.js`:

```javascript
// command-palette.js — Ctrl+K command palette for DMHelper

(function () {
  'use strict';

  document.addEventListener('alpine:init', () => {
    Alpine.data('commandPalette', () => ({
      open: false,
      query: '',
      results: [],
      selectedIndex: -1,
      loading: false,

      init() {
        window.addEventListener('command-palette-toggle', () => {
          this.open = !this.open;
          if (this.open) {
            this.$nextTick(() => {
              this.$refs.input?.focus();
            });
          }
        });
      },

      async doSearch() {
        const q = this.query.trim();
        if (q.length < 2) {
          this.results = [];
          this.selectedIndex = -1;
          return;
        }
        this.loading = true;
        this.selectedIndex = -1;
        try {
          const campaignId = document.body.dataset.campaignId || '';
          let url = '/api/v1/search?q=' + encodeURIComponent(q);
          if (campaignId) {
            url += '&campaignId=' + encodeURIComponent(campaignId);
          }
          const resp = await fetch(url);
          if (resp.ok) {
            this.results = await resp.json();
          } else {
            this.results = [];
          }
        } catch (e) {
          this.results = [];
        } finally {
          this.loading = false;
        }
      },

      onKeydown(e) {
        if (e.key === 'Escape') {
          this.open = false;
          this.query = '';
          this.results = [];
          return;
        }
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          this.selectedIndex = Math.min(this.selectedIndex + 1, this.results.length - 1);
          return;
        }
        if (e.key === 'ArrowUp') {
          e.preventDefault();
          this.selectedIndex = Math.max(this.selectedIndex - 1, -1);
          return;
        }
        if (e.key === 'Enter') {
          e.preventDefault();
          if (this.selectedIndex >= 0 && this.selectedIndex < this.results.length) {
            this.navigateTo(this.results[this.selectedIndex]);
          }
          return;
        }
      },

      navigateTo(item) {
        this.open = false;
        this.query = '';
        this.results = [];
        if (item.url) {
          window.location.href = item.url;
        }
      },

      typeLabel(type) {
        const labels = {
          'note': 'Note', 'quicknote': 'Quick Note', 'statblock': 'Monster',
          'spell': 'Spell', 'condition': 'Condition', 'rule': 'Rule',
          'equipment': 'Equipment', 'magic-item': 'Magic Item',
          'class': 'Class', 'species': 'Species', 'background': 'Background',
          'feat': 'Feat', 'map': 'Map', 'encounter': 'Encounter',
          'handout': 'Handout', 'party-member': 'Party Member'
        };
        return labels[type] || type;
      }
    }));
  });
})();
```

- [ ] **Step 2: Create _command-palette.html fragment**

Create `src/main/resources/templates/fragments/_command-palette.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="command-palette-overlay" th:fragment="command-palette"
     x-data="commandPalette" x-show="open"
     @click.self="open = false"
     @keydown.escape.window="if(open) { open = false; query = ''; results = []; }"
     style="display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.5);
            z-index: 1000; display: flex; align-items: flex-start; justify-content: center; padding-top: 15vh;"
     x-init="$watch('open', v => { if(v) document.body.classList.add('palette-open'); else document.body.classList.remove('palette-open'); })">
    <div class="command-palette-box" style="background: var(--color-surface); border: 1px solid var(--color-border);
            border-radius: var(--radius); width: 560px; max-height: 70vh; display: flex; flex-direction: column;
            box-shadow: var(--shadow); overflow: hidden;">
        <div style="display: flex; align-items: center; padding: var(--space-md); border-bottom: 1px solid var(--color-border);">
            <span style="color: var(--color-accent); font-size: 1.2rem; margin-right: var(--space-sm);">⌘</span>
            <input type="text" x-ref="input" x-model="query"
                   @input="doSearch()" @keydown="onKeydown($event)"
                   placeholder="Search notes, monsters, spells, maps..."
                   style="flex: 1; background: transparent; border: none; color: var(--color-text);
                          font-size: 1.1rem; outline: none; padding: 4px 0;">
            <span x-show="loading" style="color: var(--color-text-muted);">…</span>
        </div>
        <div style="overflow-y: auto; max-height: 400px;">
            <template x-if="results.length === 0 && query.length >= 2">
                <div style="padding: var(--space-lg); text-align: center; color: var(--color-text-muted);">
                    No results found
                </div>
            </template>
            <template x-if="query.length < 2">
                <div style="padding: var(--space-lg); text-align: center; color: var(--color-text-muted);">
                    Type to search across all campaign content and the SRD library
                </div>
            </template>
            <template x-for="(item, idx) in results" :key="item.id">
                <div class="palette-result"
                     :class="{ 'palette-selected': idx === selectedIndex }"
                     @click="navigateTo(item)"
                     style="padding: var(--space-sm) var(--space-md); cursor: pointer;
                            display: flex; align-items: center; justify-content: space-between;">
                    <div style="display: flex; flex-direction: column; min-width: 0;">
                        <span x-text="item.title"
                              style="color: var(--color-text); font-weight: 500;
                                     white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"></span>
                        <span x-text="item.subtype" x-show="item.subtype"
                              style="color: var(--color-text-muted); font-size: var(--text-sm);"></span>
                    </div>
                    <span x-text="typeLabel(item.type)"
                          style="color: var(--color-accent); font-size: var(--text-xs);
                                 padding: 2px 8px; border: 1px solid var(--color-accent);
                                 border-radius: 4px; flex-shrink: 0; margin-left: var(--space-md);"></span>
                </div>
            </template>
        </div>
        <div style="border-top: 1px solid var(--color-border); padding: var(--space-sm) var(--space-md);
                    display: flex; gap: var(--space-md); font-size: var(--text-xs); color: var(--color-text-muted);">
            <span>Esc — close</span>
            <span>↑↓ — navigate</span>
            <span>Enter — open</span>
        </div>
    </div>
</div>
</html>
```

- [ ] **Step 3: Include command-palette fragment in navbar.html**

In `navbar.html`, add at the end of the `<nav>` element (before the closing `</nav>`):

```html
<th:block th:replace="~{fragments/_command-palette :: command-palette}"></th:block>
```

- [ ] **Step 4: Update head.html to include command-palette.js**

In `head.html`, add after the keyboard.js script:

```html
<script src="/js/command-palette.js"></script>
```

- [ ] **Step 5: Add CSS styles for command palette in app.css**

At the end of `app.css`, add:

```css
.command-palette-box {
    animation: paletteSlideIn 0.1s ease-out;
}

@keyframes paletteSlideIn {
    from { opacity: 0; transform: translateY(-8px); }
    to { opacity: 1; transform: translateY(0); }
}

.palette-result:hover,
.palette-selected {
    background: var(--color-bg);
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/js/command-palette.js \
        src/main/resources/templates/fragments/_command-palette.html \
        src/main/resources/templates/fragments/navbar.html \
        src/main/resources/templates/fragments/head.html \
        src/main/resources/static/css/app.css
git commit -m "feat: add Ctrl+K command palette — global search overlay with keyboard navigation"
```

---

### Task 5: Full Encounter + Handout Export

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

- [ ] **Step 1: Add CombatantExportDto and update HandoutExportDto in CampaignExportDto.java**

In `CampaignExportDto.java`, replace the existing `EncounterExportDto` (line 156-170) and `HandoutExportDto` (line 228-232) with:

```java
    public record EncounterExportDto(
            String name,
            List<CombatantExportDto> combatants,
            String status,
            int round,
            int activeTurnIndex,
            long logSequence,
            String lairActionName,
            String lairActionDescription
    ) {
        public static EncounterExportDto from(
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter enc,
                List<CombatantExportDto> combatants) {
            return new EncounterExportDto(
                    enc.getName(), combatants, enc.getStatus().name(),
                    enc.getRound(), enc.getActiveTurnIndex(), enc.getLogSequence(),
                    enc.getLairActionName(), enc.getLairActionDescription());
        }
    }

    public record CombatantExportDto(
            String name, int initiative, int tieBreaker, int sortOrder,
            int maxHp, int currentHp, int tempHp,
            String kind, String groupId, boolean groupLeader,
            String tokenId, String statBlockKey, String partyMemberName,
            boolean defeated, boolean hidden,
            String conditionsJson, String concentratingOn, boolean concentrationCheckPending,
            int legendaryActionsUsed, int legendaryResistancesUsed,
            int legendaryActionsMax, int legendaryResistancesMax,
            String rechargedAbilities, String notes
    ) {
        public static CombatantExportDto from(
                dev.hendrikhoemberg.dmhelper.encounter.data.Combatant c,
                java.util.Map<java.util.UUID, String> tokenIdMap) {
            return new CombatantExportDto(
                    c.getName(), c.getInitiative(), c.getTieBreaker(), c.getSortOrder(),
                    c.getMaxHp(), c.getCurrentHp(), c.getTempHp(),
                    c.getKind(), c.getGroupId(), c.isGroupLeader(),
                    c.getToken() != null ? (tokenIdMap != null ? tokenIdMap.get(c.getToken().getId()) : c.getToken().getId().toString()) : null,
                    c.getStatBlock() != null ? c.getStatBlock().getSourceKey() : null,
                    c.getPartyMember() != null ? c.getPartyMember().getCharacterName() : null,
                    c.isDefeated(), c.isHidden(),
                    c.getConditionsJson(), c.getConcentratingOn(), c.isConcentrationCheckPending(),
                    c.getLegendaryActionsUsed(), c.getLegendaryResistancesUsed(),
                    c.getLegendaryActionsMax(), c.getLegendaryResistancesMax(),
                    c.getRechargedAbilities(), c.getNotes()
            );
        }
    }

    public record HandoutExportDto(
            String title,
            List<String> tags,
            String fileName,
            String contentType,
            String imageData
    ) {
        public static HandoutExportDto from(dev.hendrikhoemberg.dmhelper.handout.data.Handout h,
                                             String imageBase64) {
            return new HandoutExportDto(h.getTitle(), parseTags(h.getTags()),
                    h.getFileName(), h.getContentType(), imageBase64);
        }
    }
```

- [ ] **Step 2: Update CampaignService.exportToJson to include encounters and handouts**

In `CampaignService.java`, modify the `exportToJson` method (around line 143). Add after the `maps` line (line 151):

```java
        var encounters = encounterRepo.findByCampaignIdOrderByNameAsc(id).stream()
                .map(enc -> {
                    var combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(enc.getId()).stream()
                            .map(c -> CampaignExportDto.CombatantExportDto.from(c, Map.of()))
                            .toList();
                    return CampaignExportDto.EncounterExportDto.from(enc, combatants);
                })
                .toList();

        var handouts = handoutRepo.findByCampaignIdOrderByTitleAsc(id).stream()
                .map(h -> {
                    String imageBase64 = null;
                    try {
                        java.nio.file.Path filePath = handoutService.resolveFilePath(h.getFileName());
                        byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
                        imageBase64 = "data:" + h.getContentType() + ";base64," +
                                java.util.Base64.getEncoder().encodeToString(bytes);
                    } catch (Exception e) {
                        System.err.println("WARNING: Could not read handout image: " + h.getFileName());
                    }
                    return CampaignExportDto.HandoutExportDto.from(h, imageBase64);
                })
                .toList();
```

Then replace the `CampaignExportDto` constructor call (lines 183-196) to use `encounters` and `handouts` instead of `List.of()`:

```java
        CampaignExportDto dto = new CampaignExportDto(
                CampaignExportDto.CURRENT_FORMAT_VERSION,
                new CampaignExportDto.CampaignDto(campaign.getName(), campaign.getDescription()),
                party,
                statBlocks,
                handouts,
                maps,
                encounters,
                noteDtos,
                quickNoteDtos,
                assignments,
                ledgerEntries,
                timelineEvents
        );
```

- [ ] **Step 3: Add missing repository method**

In `CombatantRepository.java`, add:
```java
List<Combatant> findByEncounterIdOrderBySortOrderAsc(UUID encounterId);
```

- [ ] **Step 4: Run existing tests to verify no regressions**

```bash
mvn test -Dtest="CampaignImportExportRoundTripTest,CampaignServiceTest"
```

Expected: All pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatantRepository.java
git commit -m "feat: full encounter and handout export in campaign JSON"
```

---

### Task 6: Full Encounter + Handout Import

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

- [ ] **Step 1: Add encounter and handout import in CampaignService.importFromJson**

In `CampaignService.java`, in the `importFromJson` method, add after the map import block (around line 279, after `}`):

```java
        java.util.Map<String, UUID> mapKeyToId = new java.util.HashMap<>();
        if (dto.maps() != null) {
            for (var mapDto : dto.maps()) {
                var grid = mapDto.grid();
                var map = gameMapService.create(saved.getId(), mapDto.name(),
                        grid != null ? grid.w() : 30,
                        grid != null ? grid.h() : 20,
                        grid != null ? grid.cellPx() : 48);
                if (mapDto.document() != null) {
                    gameMapService.updateDocument(map.getId(),
                            objectMapper.writeValueAsString(mapDto.document()),
                            map.getVersion());
                }
                mapKeyToId.put(mapDto.key(), map.getId());
            }
        }
```

Then add after the notes import block (around line 289, after `}`):

```java
        if (dto.handouts() != null) {
            for (var hDto : dto.handouts()) {
                if (hDto.imageData() != null && hDto.fileName() != null && hDto.contentType() != null) {
                    try {
                        String base64Data = hDto.imageData();
                        if (base64Data.startsWith("data:")) {
                            int commaIdx = base64Data.indexOf(',');
                            if (commaIdx > 0) {
                                base64Data = base64Data.substring(commaIdx + 1);
                            }
                        }
                        byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
                        java.nio.file.Path targetPath = handoutService.resolveFilePath(hDto.fileName());
                        java.nio.file.Files.createDirectories(targetPath.getParent());
                        java.nio.file.Files.write(targetPath, imageBytes);

                        dev.hendrikhoemberg.dmhelper.handout.data.Handout handout =
                                new dev.hendrikhoemberg.dmhelper.handout.data.Handout();
                        handout.setCampaign(saved);
                        handout.setTitle(hDto.title());
                        handout.setFileName(hDto.fileName());
                        handout.setContentType(hDto.contentType());
                        handout.setTags(hDto.tags() != null ? String.join(",", hDto.tags()) : null);
                        handout.setDmOnly(true);
                        handout.setPresented(false);
                        handoutRepo.save(handout);
                    } catch (Exception e) {
                        System.err.println("WARNING: Failed to import handout '" + hDto.title() + "': " + e.getMessage());
                    }
                }
            }
        }

        if (dto.encounters() != null) {
            for (var encDto : dto.encounters()) {
                var encounter = new dev.hendrikhoemberg.dmhelper.encounter.data.Encounter();
                encounter.setCampaign(saved);
                encounter.setName(encDto.name());
                encounter.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.valueOf(encDto.status()));
                encounter.setRound(encDto.round());
                encounter.setActiveTurnIndex(encDto.activeTurnIndex());
                encounter.setLogSequence(encDto.logSequence());
                encounter.setLairActionName(encDto.lairActionName());
                encounter.setLairActionDescription(encDto.lairActionDescription());
                encounter = encounterRepo.save(encounter);

                if (encDto.combatants() != null) {
                    for (var cDto : encDto.combatants()) {
                        var combatant = new dev.hendrikhoemberg.dmhelper.encounter.data.Combatant();
                        combatant.setEncounter(encounter);
                        combatant.setName(cDto.name());
                        combatant.setInitiative(cDto.initiative());
                        combatant.setTieBreaker(cDto.tieBreaker());
                        combatant.setSortOrder(cDto.sortOrder());
                        combatant.setMaxHp(cDto.maxHp());
                        combatant.setCurrentHp(cDto.currentHp());
                        combatant.setTempHp(cDto.tempHp());
                        combatant.setKind(cDto.kind());
                        combatant.setGroupId(cDto.groupId());
                        combatant.setGroupLeader(cDto.groupLeader());
                        if (cDto.tokenId() != null) {
                            tokenRepo.findById(java.util.UUID.fromString(cDto.tokenId()))
                                    .ifPresent(combatant::setToken);
                        }
                        if (cDto.statBlockKey() != null) {
                            var sb = statBlockRepository.findBySourceKey(cDto.statBlockKey());
                            if (sb != null) combatant.setStatBlock(sb);
                            else System.err.println("WARNING: Unknown statblock key: " + cDto.statBlockKey());
                        }
                        if (cDto.partyMemberName() != null) {
                            partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId())
                                    .stream().filter(pm -> pm.getCharacterName().equals(cDto.partyMemberName()))
                                    .findFirst().ifPresent(combatant::setPartyMember);
                        }
                        combatant.setDefeated(cDto.defeated());
                        combatant.setHidden(cDto.hidden());
                        combatant.setConditionsJson(cDto.conditionsJson());
                        combatant.setConcentratingOn(cDto.concentratingOn());
                        combatant.setConcentrationCheckPending(cDto.concentrationCheckPending());
                        combatant.setLegendaryActionsUsed(cDto.legendaryActionsUsed());
                        combatant.setLegendaryResistancesUsed(cDto.legendaryResistancesUsed());
                        combatant.setLegendaryActionsMax(cDto.legendaryActionsMax());
                        combatant.setLegendaryResistancesMax(cDto.legendaryResistancesMax());
                        combatant.setRechargedAbilities(cDto.rechargedAbilities());
                        combatant.setNotes(cDto.notes());
                        combatantRepo.save(combatant);
                    }
                }
            }
        }
```

Also add the quicknote import after notes (replacing the TODO comment at line 291):

```java
        if (dto.quicknotes() != null) {
            for (var qnDto : dto.quicknotes()) {
                dev.hendrikhoemberg.dmhelper.notes.data.QuickNote qn =
                        new dev.hendrikhoemberg.dmhelper.notes.data.QuickNote();
                qn.setCampaign(saved);
                qn.setTargetType(qnDto.targetType());
                qn.setBody(qnDto.body());
                quickNoteRepo.save(qn);
            }
        }
```

- [ ] **Step 2: Add needed dependencies to CampaignService**

The `importFromJson` method now needs `HandoutService`, `HandoutRepository`, `EncounterRepository`, `CombatantRepository`, and `TokenRepository`. Add these to the constructor:

In the constructor parameters add:
```java
                            HandoutService handoutService,
                            HandoutRepository handoutRepo,
                            EncounterRepository encounterRepo,
                            CombatantRepository combatantRepo,
                            TokenRepository tokenRepo) {
```

And as fields:
```java
    private final HandoutService handoutService;
    private final HandoutRepository handoutRepo;
    private final EncounterRepository encounterRepo;
    private final CombatantRepository combatantRepo;
    private final TokenRepository tokenRepo;
```

- [ ] **Step 3: Add missing repository import**

The CampaignService already imports `QuickNoteRepository` — verify. It likely already imports `EncounterRepository`, `CombatantRepository` etc. If not, add them.

- [ ] **Step 4: Check and add `parseTags` helper in CampaignExportDto.java**

In `CampaignExportDto.java`, add the static helper method at the end of the HandoutExportDto record (after `from` method):

```java
        private static List<String> parseTags(String tagsStr) {
            if (tagsStr == null || tagsStr.isBlank()) return List.of();
            return java.util.Arrays.stream(tagsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
```

- [ ] **Step 5: Run round-trip tests**

```bash
mvn test -Dtest="CampaignImportExportRoundTripTest"
```

Expected: All pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "feat: full encounter, handout, and quicknote import from campaign JSON"
```

---

### Task 7: Dry-Run Import Validation

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`

- [ ] **Step 1: Add validateImport() method to CampaignService.java**

```java
    @Transactional(readOnly = true)
    public List<String> validateImport(String json) {
        List<String> warnings = new ArrayList<>();
        CampaignExportDto dto;
        try {
            dto = objectMapper.readValue(json, CampaignExportDto.class);
        } catch (Exception e) {
            return List.of("Failed to parse JSON: " + e.getMessage());
        }

        if (dto.formatVersion() != CampaignExportDto.CURRENT_FORMAT_VERSION) {
            warnings.add("Unsupported formatVersion: " + dto.formatVersion() +
                    ". Expected: " + CampaignExportDto.CURRENT_FORMAT_VERSION);
        }
        if (dto.campaign() == null || dto.campaign().name() == null || dto.campaign().name().isBlank()) {
            warnings.add("Campaign name is required");
        }

        if (dto.maps() != null) {
            for (var mapDto : dto.maps()) {
                var grid = mapDto.grid();
                if (grid == null) {
                    warnings.add("Map '" + mapDto.name() + "' has no grid config");
                } else {
                    if (grid.w() < 1) warnings.add("Map '" + mapDto.name() + "' grid width must be >= 1");
                    if (grid.h() < 1) warnings.add("Map '" + mapDto.name() + "' grid height must be >= 1");
                }
            }
        }

        if (dto.statBlocks() != null) {
            for (var sbDto : dto.statBlocks()) {
                if (sbDto.name() == null || sbDto.name().isBlank()) {
                    warnings.add("StatBlock has no name");
                }
            }
        }

        if (dto.encounters() != null) {
            for (var encDto : dto.encounters()) {
                if (encDto.name() == null || encDto.name().isBlank()) {
                    warnings.add("Encounter has no name");
                }
                if (encDto.combatants() != null) {
                    for (int i = 0; i < encDto.combatants().size(); i++) {
                        var c = encDto.combatants().get(i);
                        if (c.name() == null || c.name().isBlank()) {
                            warnings.add("Combatant #" + (i + 1) + " in encounter '" +
                                    encDto.name() + "' has no name");
                        }
                        if (c.statBlockKey() != null && c.statBlockKey().startsWith("srd-")) {
                            var resolved = statBlockRepository.findBySourceKey(c.statBlockKey());
                            if (resolved == null) {
                                warnings.add("SRD statblock key '" + c.statBlockKey() +
                                        "' not found in library (will use plain text)");
                            }
                        }
                    }
                }
            }
        }

        if (warnings.isEmpty()) {
            warnings.add("Validation passed — campaign is ready for import.");
        }

        return warnings;
    }
```

- [ ] **Step 2: Add dryRun parameter to CampaignController.importCampaign**

In `CampaignController.java`, find the `POST /campaigns/import` method. Modify/add a `dryRun` parameter:

```java
    @PostMapping("/import")
    public Object importCampaign(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean dryRun) {
        if (file.isEmpty()) {
            if ("true".equals(request.getHeader("HX-Request"))) {
                ModelAndView mav = new ModelAndView("common/_error");
                mav.addObject("message", "No file provided");
                mav.setStatus(HttpStatus.BAD_REQUEST);
                return mav;
            }
            return ResponseEntity.badRequest().body(
                    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "No file provided"));
        }
        try {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (dryRun) {
                List<String> report = campaignService.validateImport(json);
                return ResponseEntity.ok(new DryRunResult(report));
            }
            campaignService.importFromJson(json);
            if ("true".equals(request.getHeader("HX-Request"))) {
                return "redirect:/campaigns";
            }
            return ResponseEntity.ok(Map.of("status", "imported"));
        } catch (Exception e) {
            if ("true".equals(request.getHeader("HX-Request"))) {
                ModelAndView mav = new ModelAndView("common/_error");
                mav.addObject("message", e.getMessage());
                mav.setStatus(HttpStatus.BAD_REQUEST);
                return mav;
            }
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, e.getMessage());
            problem.setType(URI.create("urn:dmhelper:validation-error"));
            return ResponseEntity.badRequest().body(problem);
        }
    }

    public record DryRunResult(List<String> messages) {}
```

- [ ] **Step 3: Run tests**

```bash
mvn test -Dtest="CampaignControllerTest,CampaignServiceTest"
```

Expected: All pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java
git commit -m "feat: add dry-run import validation (?dryRun=true) with problem report"
```

---

### Task 8: JSON Schemas for Campaign Format and Map Document

**Files:**
- Create: `src/main/resources/schemas/campaign-format.schema.json`
- Create: `src/main/resources/schemas/map-document.schema.json`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java`

- [ ] **Step 1: Create campaign-format.schema.json**

Create `src/main/resources/schemas/campaign-format.schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://dmhelper/campaign-format.schema.json",
  "title": "DMHelper Campaign Export Format",
  "description": "Schema for the .dmcampaign.json export/import format. All coordinate positions use pixel units with origin at top-left. Token sizes are in cell units (1 cell = cellSizePx pixels). Grid coordinates use [col, row] order (zero-indexed from top-left).",
  "type": "object",
  "required": ["formatVersion", "campaign"],
  "properties": {
    "formatVersion": {
      "type": "integer",
      "const": 1,
      "description": "Schema format version. Currently only version 1 is supported."
    },
    "campaign": {
      "type": "object",
      "required": ["name"],
      "properties": {
        "name": { "type": "string", "minLength": 1 },
        "description": { "type": "string" }
      }
    },
    "party": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["characterName", "playerName", "classAndLevel"],
        "properties": {
          "characterName": { "type": "string" },
          "playerName": { "type": "string" },
          "classAndLevel": { "type": "string" },
          "ac": { "type": "integer", "minimum": 0 },
          "maxHp": { "type": "integer", "minimum": 1 },
          "initiativeBonus": { "type": "integer" },
          "speed": { "type": "integer", "minimum": 0 },
          "passivePerception": { "type": "integer" },
          "passiveInsight": { "type": "integer" },
          "passiveInvestigation": { "type": "integer" },
          "notes": { "type": "string" },
          "active": { "type": "boolean" },
          "sheet": { "$ref": "#/definitions/sheet" }
        }
      }
    },
    "statBlocks": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name"],
        "properties": {
          "sourceKey": { "type": "string" },
          "name": { "type": "string" },
          "cr": { "type": "string" },
          "type": { "type": "string" },
          "size": { "type": "string" },
          "alignment": { "type": "string" },
          "ac": { "type": "integer" },
          "hp": { "type": "string" },
          "speed": { "type": "string" },
          "strScore": { "type": "integer" },
          "dexScore": { "type": "integer" },
          "conScore": { "type": "integer" },
          "intScore": { "type": "integer" },
          "wisScore": { "type": "integer" },
          "chaScore": { "type": "integer" },
          "xp": { "type": "integer" }
        }
      }
    },
    "handouts": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["title"],
        "properties": {
          "title": { "type": "string" },
          "tags": { "type": "array", "items": { "type": "string" } },
          "fileName": { "type": "string" },
          "contentType": { "type": "string" },
          "imageData": {
            "type": "string",
            "description": "Base64-encoded image data URL (data:image/...;base64,...)"
          }
        }
      }
    },
    "maps": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name", "grid", "document"],
        "properties": {
          "key": { "type": "string" },
          "name": { "type": "string" },
          "grid": {
            "type": "object",
            "required": ["w", "h", "cellPx"],
            "properties": {
              "w": { "type": "integer", "minimum": 1 },
              "h": { "type": "integer", "minimum": 1 },
              "cellPx": { "type": "integer", "minimum": 1 },
              "gridType": { "type": "string", "enum": ["square"] }
            }
          },
          "document": {
            "type": "object",
            "required": ["schemaVersion", "grid", "layers"],
            "properties": {
              "schemaVersion": { "type": "integer", "const": 1 },
              "grid": {
                "type": "object",
                "required": ["width", "height", "cellSizePx", "gridType"],
                "properties": {
                  "width": { "type": "integer", "minimum": 1 },
                  "height": { "type": "integer", "minimum": 1 },
                  "cellSizePx": { "type": "integer", "minimum": 1 },
                  "gridType": { "type": "string", "enum": ["square"] }
                }
              }
            }
          }
        }
      }
    },
    "encounters": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name"],
        "properties": {
          "name": { "type": "string" },
          "status": { "type": "string", "enum": ["PLANNED", "ACTIVE", "DONE"] },
          "round": { "type": "integer" },
          "combatants": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["name"],
              "properties": {
                "name": { "type": "string" },
                "initiative": { "type": "integer" },
                "maxHp": { "type": "integer" },
                "currentHp": { "type": "integer" },
                "kind": { "type": "string" },
                "hidden": { "type": "boolean" },
                "statBlockKey": { "type": "string" },
                "partyMemberName": { "type": "string" }
              }
            }
          }
        }
      }
    },
    "notes": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["title", "type"],
        "properties": {
          "type": { "type": "string", "enum": ["NPC", "LOCATION", "QUEST", "SESSION_LOG", "SESSION_PLAN", "GENERIC"] },
          "title": { "type": "string" },
          "body": { "type": "string" },
          "tags": { "type": "string" },
          "dmOnly": { "type": "boolean" }
        }
      }
    }
  },
  "definitions": {
    "sheet": {
      "type": "object",
      "properties": {
        "abilityScores": { "type": "object" },
        "classLevels": { "type": "array" },
        "speciesKey": { "type": "string" },
        "backgroundKey": { "type": "string" },
        "xp": { "type": "integer" }
      }
    }
  }
}
```

- [ ] **Step 2: Create map-document.schema.json**

Create `src/main/resources/schemas/map-document.schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://dmhelper/map-document.schema.json",
  "title": "DMHelper Map Document Format",
  "description": "Internal schema for the MapDocument JSON blob. Grid coordinates use [col, row] order (zero-indexed from top-left). Token positions are pixel coordinates relative to the canvas origin (top-left).",
  "type": "object",
  "required": ["schemaVersion", "grid", "layers"],
  "properties": {
    "schemaVersion": { "type": "integer", "const": 1 },
    "grid": {
      "type": "object",
      "required": ["width", "height", "cellSizePx", "gridType"],
      "properties": {
        "width": { "type": "integer", "minimum": 1 },
        "height": { "type": "integer", "minimum": 1 },
        "cellSizePx": { "type": "integer", "minimum": 1 },
        "gridType": { "type": "string", "enum": ["square"] }
      }
    },
    "layers": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["id", "name", "type"],
        "properties": {
          "id": { "type": "string" },
          "name": { "type": "string" },
          "type": { "type": "string", "enum": ["TERRAIN", "OBJECTS", "ANNOTATIONS"] },
          "visible": { "type": "boolean" },
          "locked": { "type": "boolean" },
          "cells": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["col", "row"],
              "properties": {
                "col": { "type": "integer", "minimum": 0 },
                "row": { "type": "integer", "minimum": 0 },
                "terrain": { "type": "string" }
              }
            }
          },
          "shapes": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["type"],
              "properties": {
                "type": { "type": "string", "enum": ["RECT", "CIRCLE", "LINE", "POLYGON"] },
                "x": { "type": "number" },
                "y": { "type": "number" },
                "width": { "type": "number" },
                "height": { "type": "number" },
                "radius": { "type": "number" },
                "points": { "type": "array", "items": { "type": "array", "items": { "type": "number" } } },
                "fill": { "type": "string" },
                "stroke": { "type": "string" },
                "label": { "type": "string" }
              }
            }
          },
          "image": { "type": "string" }
        }
      }
    },
    "primitives": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["type"],
        "properties": {
          "type": { "type": "string", "enum": ["ROOM", "DOOR", "REGION", "CORRIDOR"] },
          "startCol": { "type": "integer", "minimum": 0 },
          "startRow": { "type": "integer", "minimum": 0 },
          "endCol": { "type": "integer", "minimum": 0 },
          "endRow": { "type": "integer", "minimum": 0 },
          "terrain": { "type": "string" }
        }
      }
    },
    "customTerrain": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["key", "name", "fill"],
        "properties": {
          "key": { "type": "string" },
          "name": { "type": "string" },
          "fill": { "type": "string" },
          "walkable": { "type": "boolean" }
        }
      }
    }
  }
}
```

- [ ] **Step 3: Create SchemaController.java**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/schemas")
public class SchemaController {

    @GetMapping("/{name}")
    public ResponseEntity<String> getSchema(@PathVariable String name) throws IOException {
        String fileName = name.endsWith(".schema.json") ? name : name + ".schema.json";
        ClassPathResource resource = new ClassPathResource("schemas/" + fileName);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/schema+json"))
                .body(content);
    }
}
```

- [ ] **Step 4: Exclude /api/v1/schemas/** from PIN gate**

In `WebMvcConfig.java`, add `"/api/v1/schemas/**"` to the `excludePathPatterns` list:

```java
                    .excludePathPatterns(
                            "/player", "/player/**",
                            "/ws/table", "/ws/table/**",
                            "/files/**",
                            "/dm/authenticate",
                            "/css/**", "/js/**", "/vendor/**",
                            "/api/v1/schemas/**",
                            "/error",
                            "/favicon.ico"
                    );
```

- [ ] **Step 5: Run tests**

```bash
mvn test
```

Expected: All pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/schemas/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/common/web/SchemaController.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebMvcConfig.java
git commit -m "feat: add JSON Schemas for campaign format and map document, served at /api/v1/schemas/"
```

---

### Task 9: Integration Test — Full Export/Import Round-Trip with Everything

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`

- [ ] **Step 1: Add test for encounter round-trip**

Add a new test method to `CampaignImportExportRoundTripTest.java`:

```java
    @Test
    void roundTripPreservesEncountersWithCombatants() {
        Campaign c = campaignService.create("Encounter Trip", "encounters");
        GameMap map = gameMapService.create(c.getId(), "Battlefield", 30, 20, 48);

        PartyMember pm = partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
                16, 38, 4, 30, 17, 12, 11, null);

        StatBlock sb = statBlockService.createCustom(c.getId(), "Goblin Boss", "1", "Humanoid",
                17, "21 (6d6)", "30 ft.",
                10, 14, 10, 10, 8, 8,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "darkvision 60 ft.", "Common, Goblin");
        sb.setSourceKey("goblin-boss");
        statBlockRepository.save(sb);

        Encounter encounter = new Encounter();
        encounter.setCampaign(c);
        encounter.setName("Goblin Ambush");
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter.setRound(2);
        encounter.setActiveTurnIndex(0);
        encounter.setLairActionName("Falling Rocks");
        encounter.setLairActionDescription("Rocks fall, everyone dies");
        encounter = encounterRepo.save(encounter);

        Combatant pcCombatant = new Combatant();
        pcCombatant.setEncounter(encounter);
        pcCombatant.setName("Thia");
        pcCombatant.setPartyMember(pm);
        pcCombatant.setInitiative(18);
        pcCombatant.setSortOrder(0);
        pcCombatant.setMaxHp(38);
        pcCombatant.setCurrentHp(30);
        pcCombatant.setKind("PC");
        pcCombatant.setConditionsJson("[\"blinded\"]");
        combatantRepo.save(pcCombatant);

        Combatant npcCombatant = new Combatant();
        npcCombatant.setEncounter(encounter);
        npcCombatant.setName("Goblin Boss");
        npcCombatant.setStatBlock(sb);
        npcCombatant.setInitiative(12);
        npcCombatant.setSortOrder(1);
        npcCombatant.setMaxHp(21);
        npcCombatant.setCurrentHp(10);
        npcCombatant.setKind("NPC");
        npcCombatant.setHidden(true);
        combatantRepo.save(npcCombatant);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<Encounter> encounters = encounterRepo.findByCampaignIdOrderByNameAsc(imported.getId());
        assertThat(encounters).hasSize(1);
        Encounter reEnc = encounters.get(0);
        assertThat(reEnc.getName()).isEqualTo("Goblin Ambush");
        assertThat(reEnc.getStatus()).isEqualTo(Encounter.Status.ACTIVE);
        assertThat(reEnc.getRound()).isEqualTo(2);
        assertThat(reEnc.getLairActionName()).isEqualTo("Falling Rocks");

        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(reEnc.getId());
        assertThat(combatants).hasSize(2);
        Combatant rePC = combatants.get(0);
        assertThat(rePC.getName()).isEqualTo("Thia");
        assertThat(rePC.getInitiative()).isEqualTo(18);
        assertThat(rePC.getCurrentHp()).isEqualTo(30);
        assertThat(rePC.getConditionsJson()).contains("blinded");

        Combatant reNPC = combatants.get(1);
        assertThat(reNPC.getName()).isEqualTo("Goblin Boss");
        assertThat(reNPC.isHidden()).isTrue();
        assertThat(reNPC.getStatBlock().getSourceKey()).isEqualTo("goblin-boss");
    }
```

- [ ] **Step 2: Run the test**

```bash
mvn test -Dtest="CampaignImportExportRoundTripTest#roundTripPreservesEncountersWithCombatants"
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
git commit -m "test: add round-trip test for encounters with combatants"
```

---

### Task 10: Final Verification — Build and Smoke Test

**Files:** None (verification only)

- [ ] **Step 1: Full build**

```bash
mvn clean package -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run all tests**

```bash
mvn test
```

Expected: All tests PASS.

- [ ] **Step 3: Start the app and smoke test**

```bash
java -jar target/dmhelper-0.0.1-SNAPSHOT.jar
```

- Open the browser and:
  1. Press `Ctrl+Shift+D` — verify DM Mode toggles with yellow border appearing/disappearing
  2. Press `Ctrl+K` — verify command palette opens; type "goblin" — see statblocks; type a note name — see notes
  3. Create a campaign with maps, encounters, notes, handouts; export it; import it into a new campaign; verify everything is there
  4. Test dry-run import: `curl -X POST -F "file=@test.dmcampaign.json" "http://localhost:8080/campaigns/import?dryRun=true"` — verify problem report
  5. Test JSON Schema endpoint: `curl http://localhost:8080/api/v1/schemas/campaign-format` — verify schema JSON

- [ ] **Step 4: Commit final touches if any**

```bash
git diff
# If any small fixes needed, commit them
```

---

## Self-Review

### 1. Spec Coverage

| M12 Requirement (from SPEC §7) | Task |
|---|---|
| DM Mode toggle everywhere | Task 2 |
| Backups | Already done (StartupBackupRunner) — no task needed |
| Error handling | Already done (GlobalExceptionHandler) — no task needed |
| Keyboard shortcuts | Task 1 |
| Ctrl+K command palette | Tasks 3 + 4 |
| Full export/import of everything | Tasks 5 + 6 |
| JSON Schemas | Task 8 |
| Dry-run import | Task 7 |
| SRD key catalog | Already done (LibraryApiController) — no task needed |

### 2. Placeholder Scan

No TBD, TODO, or placeholders found in the plan. All code blocks are complete.

### 3. Type Consistency

- `SearchResultItem` (in `CommandPaletteService`) used consistently in both service and controller
- `CampaignExportDto` subtype records (`EncounterExportDto`, `CombatantExportDto`, `HandoutExportDto`) used consistently in both `CampaignExportDto` definition and `CampaignService`
- `DryRunResult` record defined in controller and used there
