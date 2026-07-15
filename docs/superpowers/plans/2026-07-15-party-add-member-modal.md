# Party "Add / Edit Member" Modal + Class Dropdown — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the cramped inline "Add Party Member" form into a roomy centered modal, drive the class field from a dropdown of base SRD classes (subclasses removed), and split class/level while keeping the existing `classAndLevel` storage.

**Architecture:** Reuse the codebase's native `<dialog>` idiom. A persistent empty `<dialog id="party-form-modal">` in `list.html` receives the `_form` fragment via HTMX and opens with `showModal()`. Class options come from `CharacterClassRepository.findBySubclassOfIsNullOrderByNameAsc()`; class + level are combined client-side into the existing `classAndLevel` string so no entity/schema change is needed. Subclasses are removed from the seed JSON and purged from already-seeded databases via a Flyway migration.

**Tech Stack:** Spring Boot (MVC + Thymeleaf), HTMX, native HTML `<dialog>`, Flyway, JUnit 5 + MockMvc + Mockito, Maven (`./mvnw`).

## Global Constraints

- Build/test command: `./mvnw` (wrapper in repo root). Run a single test class with `./mvnw test -Dtest=ClassName`.
- Jackson is the **Jackson 3** distribution: import `tools.jackson.databind.ObjectMapper` / `tools.jackson.databind.JsonNode` (NOT `com.fasterxml.jackson`). Match existing code in `CharacterClassSeedService`.
- Design tokens (verified in `tokens.css`): `--space-xs/sm/md/lg` (4/8/16/24px), `--text-sm/base/lg` (0.875/1/1.25rem), `--radius` (6px), `--color-surface`, `--color-bg`, `--color-border`, `--color-text`, `--color-text-muted`, `--color-accent`, `--color-overlay`, `--shadow`. Use these — do not hardcode values.
- The existing `classAndLevel` DB column and `PartyMember` entity MUST NOT change. Class+Level combine happens client-side into a hidden `name="classAndLevel"` input.
- The 12 base SRD classes (all that remain after subclass removal): Barbarian, Bard, Cleric, Druid, Fighter, Monk, Paladin, Ranger, Rogue, Sorcerer, Warlock, Wizard.
- HTMX executes inline `<script>` tags in swapped content; the party form is injected via `innerHTML`, so its init script MUST be an immediately-invoked function (IIFE) — do NOT wrap it in `DOMContentLoaded` (the document is already loaded when the fragment is swapped in).

---

## File Structure

- `src/main/resources/srd/srd-5.2-classes.json` — remove 12 subclass entries (24 → 12).
- `src/main/resources/db/migration/V2__remove_class_subclasses.sql` — **new**; purge subclasses from seeded DBs.
- `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassSeedDataTest.java` — **new**; guards the JSON has only base classes.
- `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java` — inject `CharacterClassRepository`; add `classNames` to model for new/edit; make `update` return the card fragment.
- `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java` — add `CharacterClassRepository` mock + new assertions.
- `src/main/resources/templates/party/_form.html` — restructure into modal panel: class dropdown + level + hidden `classAndLevel`, group fieldsets, IIFE combine/parse script.
- `src/main/resources/static/css/components.css` — modal styles, `.form-row-4`, fieldset/legend, `select` styling, number-spinner reset.
- `src/main/resources/templates/party/list.html` — persistent `<dialog>` container + point "+ Add Member" at it.
- `src/main/resources/templates/party/_card.html` — add stable card id; convert Edit `<a>` to HTMX modal trigger.

---

## Task 1: Remove subclasses from SRD class data + migration

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassSeedDataTest.java`
- Modify: `src/main/resources/srd/srd-5.2-classes.json`
- Create: `src/main/resources/db/migration/V2__remove_class_subclasses.sql`

**Interfaces:**
- Consumes: nothing.
- Produces: a `srd-5.2-classes.json` whose `results` array contains exactly the 12 base classes (each with `subclass_of: null`); a `V2` migration that deletes subclass rows.

- [ ] **Step 1: Write the failing guard test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassSeedDataTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CharacterClassSeedDataTest {

    @Test
    void jsonContainsOnlyBaseClasses() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource("srd/srd-5.2-classes.json").getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode results = root.get("results");
            assertEquals(12, results.size(), "expected only the 12 base classes");
            for (JsonNode entry : results) {
                // Subclass entries carry an object under "subclass_of"; base classes are null.
                assertFalse(entry.path("subclass_of").isObject(),
                        "subclass leaked into class list: " + entry.path("name").asText());
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=CharacterClassSeedDataTest`
Expected: FAIL — `expected only the 12 base classes` (actual size is 24).

- [ ] **Step 3: Strip subclasses from the JSON**

Run this from the repo root (filters out any entry with a `subclass_of`, rewrites the file):

```bash
python3 - <<'EOF'
import json
p = 'src/main/resources/srd/srd-5.2-classes.json'
with open(p) as f:
    d = json.load(f)
before = len(d['results'])
d['results'] = [c for c in d['results'] if not c.get('subclass_of')]
with open(p, 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
    f.write('\n')
print(f"results: {before} -> {len(d['results'])}")
EOF
```

Expected output: `results: 24 -> 12`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=CharacterClassSeedDataTest`
Expected: PASS.

- [ ] **Step 5: Create the Flyway migration**

The seed only runs when `character_class` is empty, so already-seeded databases still contain subclasses. Create `src/main/resources/db/migration/V2__remove_class_subclasses.sql`:

```sql
-- Subclasses were removed from the SRD class list; drop them from already-seeded databases.
-- Table/column verified against V1__baseline.sql: character_class(subclass_of).
DELETE FROM character_class WHERE subclass_of IS NOT NULL;
```

- [ ] **Step 6: Verify the app boots and Flyway applies V2**

Run: `./mvnw spring-boot:run` (stop with Ctrl-C once started).
Expected: startup log shows Flyway migrating to version `2` with no errors; app reaches "Started" line. If startup is impractical in your environment, instead run `./mvnw test` and confirm the existing suite (which boots the Spring context) passes.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/srd/srd-5.2-classes.json \
        src/main/resources/db/migration/V2__remove_class_subclasses.sql \
        src/test/java/dev/hendrikhoemberg/dmhelper/library/service/CharacterClassSeedDataTest.java
git commit -m "feat(party): remove SRD subclasses from class list and seeded DB

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: PartyController — class options + card-returning update

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java`

**Interfaces:**
- Consumes: `CharacterClassRepository.findBySubclassOfIsNullOrderByNameAsc()` → `List<CharacterClass>`; `CharacterClass.getName()` → `String`; `PartyMemberService.update(...)` → `PartyMember` (already returns the saved entity).
- Produces: model attribute `classNames` (`List<String>`) on the `/new` and `/{id}/edit` responses; `PUT /{id}` now returns the `party/_card :: card` fragment (HTTP 200 with the card HTML) instead of a redirect, with model attribute `pm`.

- [ ] **Step 1: Update the controller test (write the new expectations first)**

Edit `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java`.

Add imports near the existing ones:

```java
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
```

Add a mocked bean next to the existing `@MockitoBean` fields:

```java
    @MockitoBean
    private CharacterClassRepository classRepository;
```

Add this stub helper to the existing `/new` and `/edit` tests (so the template's class loop has data). In `shouldRenderNewForm`, before the `mockMvc.perform(...)` call, add:

```java
        CharacterClass rogue = new CharacterClass();
        rogue.setName("Rogue");
        when(classRepository.findBySubclassOfIsNullOrderByNameAsc()).thenReturn(List.of(rogue));
```

and append to its expectations:

```java
                .andExpect(model().attributeExists("classNames"));
```

Do the same stub in `shouldRenderEditForm` (add the stub before `mockMvc.perform`, and add `.andExpect(model().attributeExists("classNames"))`).

Then add a new test that pins the `update` behavior:

```java
    @Test
    void shouldUpdateReturnCardFragment() throws Exception {
        UUID pid = UUID.randomUUID();
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        PartyMember pm = new PartyMember();
        pm.setId(pid);
        pm.setCampaign(c);
        pm.setCharacterName("Thia");
        pm.setActive(true);
        when(partyService.update(eq(pid), eq("Thia"), any(), any(), eq(16), eq(38), eq(4), eq(30),
                eq(17), eq(12), eq(14), any())).thenReturn(pm);

        mockMvc.perform(put("/campaigns/{cid}/party/{pid}", campaignId, pid)
                        .param("characterName", "Thia")
                        .param("classAndLevel", "Rogue 5")
                        .param("ac", "16").param("maxHp", "38")
                        .param("initiativeBonus", "4").param("speed", "30")
                        .param("passivePerception", "17").param("passiveInsight", "12")
                        .param("passiveInvestigation", "14")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thia")));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=PartyControllerTest`
Expected: FAIL — `shouldUpdateReturnCardFragment` fails (current `update` returns a 3xx redirect, not 200 card HTML), and the `model().attributeExists("classNames")` expectations fail (controller doesn't add it yet).

- [ ] **Step 3: Implement the controller changes**

Edit `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java`.

Add imports:

```java
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import java.util.List;
```

Add the repository as a constructor-injected field:

```java
    private final CampaignService campaignService;
    private final PartyMemberService partyService;
    private final CharacterClassRepository classRepository;

    public PartyController(CampaignService campaignService, PartyMemberService partyService,
                           CharacterClassRepository classRepository) {
        this.campaignService = campaignService;
        this.partyService = partyService;
        this.classRepository = classRepository;
    }
```

Add a private helper and use it in both form handlers:

```java
    private List<String> baseClassNames() {
        return classRepository.findBySubclassOfIsNullOrderByNameAsc()
                .stream().map(CharacterClass::getName).toList();
    }
```

`newForm`:

```java
    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("pm", null);
        model.addAttribute("classNames", baseClassNames());
        return "party/_form :: form";
    }
```

`editForm`:

```java
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("pm", partyService.findById(id));
        model.addAttribute("classNames", baseClassNames());
        return "party/_form :: form";
    }
```

Change `update` to return the card fragment (add `Model model`, capture the returned entity):

```java
    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId, @PathVariable UUID id,
                         @RequestParam String characterName,
                         @RequestParam(required = false) String playerName,
                         @RequestParam(required = false) String classAndLevel,
                         @RequestParam int ac, @RequestParam int maxHp,
                         @RequestParam int initiativeBonus, @RequestParam int speed,
                         @RequestParam int passivePerception, @RequestParam int passiveInsight,
                         @RequestParam int passiveInvestigation,
                         @RequestParam(required = false) String notes,
                         Model model) {
        PartyMember pm = partyService.update(id, characterName, playerName, classAndLevel,
                ac, maxHp, initiativeBonus, speed,
                passivePerception, passiveInsight, passiveInvestigation, notes);
        model.addAttribute("pm", pm);
        return "party/_card :: card";
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=PartyControllerTest`
Expected: PASS (all tests, including `shouldUpdateReturnCardFragment` and the `classNames` expectations).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java
git commit -m "feat(party): supply class options and return card fragment on update

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Modal form fragment + CSS

**Files:**
- Modify: `src/main/resources/templates/party/_form.html`
- Modify: `src/main/resources/static/css/components.css`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java` (add a render assertion)

**Interfaces:**
- Consumes: model attributes `campaignId` (`UUID`), `pm` (`PartyMember` or null), `classNames` (`List<String>`).
- Produces: a `<div th:fragment="form(campaignId, pm, classNames)" class="modal-panel">` whose submit posts/puts only `characterName`, `playerName`, `classAndLevel` (hidden, combined client-side), `ac`, `maxHp`, `initiativeBonus`, `speed`, `passivePerception`, `passiveInsight`, `passiveInvestigation`, `notes`. The `<select>`/level inputs are unnamed and do not submit. Add form target for edit is `#party-grid`/`beforeend`; edit target is `#pm-card-<id>`/`outerHTML` (the matching id is added in Task 4).

- [ ] **Step 1: Add a render assertion to the controller test (fails until template updated)**

Edit `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java`. Add a new test:

```java
    @Test
    void shouldRenderClassDropdownOptions() throws Exception {
        CharacterClass rogue = new CharacterClass();
        rogue.setName("Rogue");
        CharacterClass wizard = new CharacterClass();
        wizard.setName("Wizard");
        when(classRepository.findBySubclassOfIsNullOrderByNameAsc())
                .thenReturn(List.of(rogue, wizard));

        mockMvc.perform(get("/campaigns/{cid}/party/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString("Rogue")))
                .andExpect(content().string(containsString("Wizard")));
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=PartyControllerTest#shouldRenderClassDropdownOptions`
Expected: FAIL — no `<select` in the current template.

- [ ] **Step 3: Rewrite the form fragment**

Replace the entire contents of `src/main/resources/templates/party/_form.html` with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="form(campaignId, pm, classNames)" class="modal-panel">
    <div class="modal-header">
        <h3 th:text="${pm == null ? 'Add Party Member' : 'Edit ' + pm.characterName}">Add Party Member</h3>
        <button type="button" class="modal-close" aria-label="Close"
                onclick="this.closest('dialog').close()">&times;</button>
    </div>
    <form class="pm-form"
          th:action="${pm} == null ? @{/campaigns/{cid}/party(cid=${campaignId})} : @{/campaigns/{cid}/party/{pid}(cid=${campaignId}, pid=${pm.id})}"
          th:method="${pm == null ? 'post' : 'put'}"
          th:hx-target="${pm == null ? '#party-grid' : '#pm-card-' + pm.id}"
          th:hx-swap="${pm == null ? 'beforeend' : 'outerHTML'}"
          hx-on::after-request="if(event.detail.successful){ this.closest('dialog').close(); }">
        <div class="modal-body">
            <div class="form-group">
                <label>Character Name *</label>
                <input type="text" name="characterName" required th:value="${pm?.characterName}" placeholder="e.g. Thia">
            </div>
            <div class="form-row-3">
                <div class="form-group">
                    <label>Player Name</label>
                    <input type="text" name="playerName" th:value="${pm?.playerName}" placeholder="Anna">
                </div>
                <div class="form-group">
                    <label>Class</label>
                    <select id="pm-class" onchange="pmSyncClassLevel()">
                        <option value="">&mdash;</option>
                        <option th:each="cn : ${classNames}" th:value="${cn}" th:text="${cn}">Rogue</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Level</label>
                    <input type="number" id="pm-level" min="1" class="pm-num" placeholder="5" oninput="pmSyncClassLevel()">
                </div>
            </div>
            <input type="hidden" name="classAndLevel" id="pm-classlevel" th:value="${pm?.classAndLevel}">
            <fieldset class="form-fieldset">
                <legend class="form-legend">Combat</legend>
                <div class="form-row-4">
                    <div class="form-group"><label>AC *</label><input type="number" name="ac" required class="pm-num" th:value="${pm?.ac}" placeholder="16"></div>
                    <div class="form-group"><label>Max HP *</label><input type="number" name="maxHp" required class="pm-num" th:value="${pm?.maxHp}" placeholder="38"></div>
                    <div class="form-group"><label>Init *</label><input type="number" name="initiativeBonus" required class="pm-num" th:value="${pm?.initiativeBonus}" placeholder="4"></div>
                    <div class="form-group"><label>Speed *</label><input type="number" name="speed" required class="pm-num" th:value="${pm?.speed}" placeholder="30"></div>
                </div>
            </fieldset>
            <fieldset class="form-fieldset">
                <legend class="form-legend">Passive Senses</legend>
                <div class="form-row-3">
                    <div class="form-group"><label>Perception *</label><input type="number" name="passivePerception" required class="pm-num" th:value="${pm?.passivePerception}" placeholder="17"></div>
                    <div class="form-group"><label>Insight *</label><input type="number" name="passiveInsight" required class="pm-num" th:value="${pm?.passiveInsight}" placeholder="12"></div>
                    <div class="form-group"><label>Investigation *</label><input type="number" name="passiveInvestigation" required class="pm-num" th:value="${pm?.passiveInvestigation}" placeholder="14"></div>
                </div>
            </fieldset>
            <div class="form-group">
                <label>Notes</label>
                <textarea name="notes" rows="3" th:text="${pm?.notes}" placeholder="e.g. darkvision, fey ancestry"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-ghost" onclick="this.closest('dialog').close()">Cancel</button>
            <button type="submit" class="btn btn-primary" th:text="${pm == null ? 'Add' : 'Save'}">Add</button>
        </div>
    </form>
    <script>
    (function () {
        var sel = document.getElementById('pm-class');
        var lvl = document.getElementById('pm-level');
        var hidden = document.getElementById('pm-classlevel');
        if (!sel || !lvl || !hidden) return;

        // Combine class + level into the hidden field that actually submits.
        window.pmSyncClassLevel = function () {
            var c = sel.value.trim();
            var l = lvl.value.trim();
            hidden.value = c ? (l ? c + ' ' + l : c) : (l ? l : '');
        };

        // Prefill on edit: split a "Class Level" string into the dropdown + level.
        var raw = (hidden.value || '').trim();
        if (raw) {
            var m = raw.match(/^(.*?)(?:\s+(\d+))?$/);
            var cls = m ? m[1].trim() : raw;
            var num = m ? m[2] : null;
            var matched = false;
            for (var i = 0; i < sel.options.length; i++) {
                if (sel.options[i].value.toLowerCase() === cls.toLowerCase()) {
                    sel.selectedIndex = i;
                    matched = true;
                    break;
                }
            }
            if (num) lvl.value = num;
            // If the class didn't match a known option (legacy/freeform), leave the
            // hidden field as the original string so an untouched save preserves it.
            if (matched) window.pmSyncClassLevel();
        }
    })();
    </script>
</div>
</html>
```

- [ ] **Step 4: Add the CSS**

Append to `src/main/resources/static/css/components.css`:

```css
/* ── Modal dialog (native <dialog>) ── */
.modal {
  padding: 0;
  border: none;
  background: transparent;
  max-width: 620px;
  width: calc(100vw - 2 * var(--space-lg));
}
.modal::backdrop {
  background: var(--color-overlay);
  backdrop-filter: blur(4px);
}
.modal-panel {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 6rem);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}
.modal-header h3 {
  font-size: var(--text-lg);
  margin: 0;
}
.modal-close {
  background: none;
  border: none;
  color: var(--color-text-muted);
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
  padding: 0 var(--space-xs);
}
.modal-close:hover { color: var(--color-text); }
.pm-form {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.modal-body {
  padding: var(--space-lg);
  overflow-y: auto;
}
.modal-footer {
  display: flex;
  gap: var(--space-sm);
  justify-content: flex-end;
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}

/* Grouped numeric rows */
.form-row-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-md); }
.form-fieldset { border: none; padding: 0; margin: 0 0 var(--space-md); min-width: 0; }
.form-legend {
  font-size: var(--text-sm);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-accent);
  margin-bottom: var(--space-xs);
  padding: 0;
}

/* Select styled like inputs */
.form-group select {
  width: 100%;
  padding: var(--space-sm) var(--space-md);
  font-size: var(--text-base);
  font-family: inherit;
  color: var(--color-text);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}
.form-group select:focus { outline: none; border-color: var(--color-accent); }

/* Remove native number spinners that overlap the value */
.pm-num::-webkit-outer-spin-button,
.pm-num::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.pm-num { -moz-appearance: textfield; appearance: textfield; text-align: center; }
```

- [ ] **Step 5: Run the render test to verify it passes**

Run: `./mvnw test -Dtest=PartyControllerTest`
Expected: PASS (including `shouldRenderClassDropdownOptions`).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/party/_form.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java
git commit -m "feat(party): modal form with class dropdown and level split

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Wire the dialog container and triggers

**Files:**
- Modify: `src/main/resources/templates/party/list.html`
- Modify: `src/main/resources/templates/party/_card.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java` (extend the list render test)

**Interfaces:**
- Consumes: the `_form` fragment (loaded via HTMX into `#party-form-modal`) and its post-submit close behavior from Task 3.
- Produces: `#party-form-modal` `<dialog>` that opens on `htmx:afterSwap` and closes on backdrop click; "+ Add Member" and each card's "Edit" load the form into it; each card has id `pm-card-<id>` so edit can target it.

- [ ] **Step 1: Extend the list render test (dialog present)**

Edit `shouldRenderPartyList` in `PartyControllerTest.java`, appending an expectation:

```java
                .andExpect(content().string(containsString("id=\"party-form-modal\"")));
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=PartyControllerTest#shouldRenderPartyList`
Expected: FAIL — no `party-form-modal` in the current `list.html`.

- [ ] **Step 3: Add the dialog + point the Add trigger at it**

In `src/main/resources/templates/party/list.html`, change the "+ Add Member" button (currently targeting `#party-grid`/`beforeend`) to load into the modal:

```html
                    <button class="btn btn-primary"
                            th:hx-get="@{/campaigns/{cid}/party/new(cid=${campaign.id})}"
                            hx-target="#party-form-modal" hx-swap="innerHTML">+ Add Member</button>
```

Then add the dialog element just before the closing `</main>` (after the `#party-grid` div):

```html
            <dialog id="party-form-modal" class="modal"
                    hx-on::after-swap="this.showModal()"
                    onclick="if (event.target === this) this.close();"></dialog>
```

- [ ] **Step 4: Add a stable id to the card and convert Edit to a modal trigger**

In `src/main/resources/templates/party/_card.html`, add an id to the fragment root:

```html
<div class="party-member-card" th:fragment="card(pm)"
     th:id="'pm-card-' + ${pm.id}"
     th:classappend="${!pm.active} ? 'pm-inactive'">
```

Replace the Edit anchor:

```html
        <a th:href="@{/campaigns/{cid}/party/{pid}/edit(cid=${pm.campaign.id}, pid=${pm.id})}" class="btn btn-ghost">Edit</a>
```

with an HTMX button that loads into the modal:

```html
        <button class="btn btn-ghost"
                th:hx-get="@{/campaigns/{cid}/party/{pid}/edit(cid=${pm.campaign.id}, pid=${pm.id})}"
                hx-target="#party-form-modal" hx-swap="innerHTML">Edit</button>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PartyControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/party/list.html \
        src/main/resources/templates/party/_card.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyControllerTest.java
git commit -m "feat(party): open add/edit form in a centered modal dialog

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: End-to-end manual verification

**Files:** none (verification only).

**Interfaces:** exercises the full add/edit flow in a running app.

- [ ] **Step 1: Run the full test suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 2: Start the app**

Run: `./mvnw spring-boot:run`
Expected: Flyway migrates to version 2; app starts. Open the app and navigate to a campaign's Party page.

- [ ] **Step 3: Verify the add flow (empty party)**

- Click "+ Add Member". Expected: a centered modal opens over a dimmed/blurred backdrop — NOT a stranded ~300px card in the corner.
- Confirm numeric fields (AC/Max HP/Init/Speed, the three passive senses) are roomy, labels do NOT wrap to two lines, and there are no native spinner arrows overlapping values.
- Confirm the Class field is a dropdown listing exactly the 12 base classes (Barbarian … Wizard) with no subclasses (no Champion, Thief, Life Domain, etc.).
- Fill in name + Class "Rogue" + Level "5" + required numbers, click Add. Expected: modal closes, a new card appears, the "No party members yet" empty state disappears, and the card's class line reads "Rogue 5".

- [ ] **Step 4: Verify the edit flow**

- On the new card, click "Edit". Expected: the same modal opens, prefilled; the Class dropdown shows "Rogue" selected and Level shows "5".
- Change Level to "6", click Save. Expected: modal closes, the SAME card updates in place (no full-page reload, no bare form page), class line now "Rogue 6".

- [ ] **Step 5: Verify dismissal + legacy tolerance**

- Open the modal and dismiss via each of: Cancel button, the × button, Esc key, and clicking the dimmed backdrop. All should close it.
- (If you have a member whose stored `classAndLevel` is freeform like "Rogue 5 (multiclass)": open Edit, leave the class field untouched, Save — the original string must be preserved on the card.)

- [ ] **Step 6: Verify subclasses are gone from the Library**

- Navigate to the Library's classes view. Expected: only the 12 base classes; subclasses (Champion, Thief, Evoker, Life Domain, …) are absent.

- [ ] **Step 7: Final confirmation**

If all checks pass, the feature is complete. If any check fails, use superpowers:systematic-debugging before patching.

---

## Self-Review Notes

- **Spec coverage:** A=modal (Tasks 3,4); B=layout/groups/spinner fix (Task 3); C=class dropdown + level combine + edit round-trip + legacy fallback (Tasks 2,3); D=subclass removal JSON + migration (Task 1); E=CSS (Task 3). Bare-page Edit fix = Task 4 (Edit → HTMX modal) + Task 2 (update returns card). All covered.
- **No placeholders:** every code/step is concrete.
- **Type consistency:** `classNames` (`List<String>`) produced by controller (Task 2) and consumed by fragment (Task 3); `findBySubclassOfIsNullOrderByNameAsc()` and `CharacterClass.getName()` verified to exist; `PartyMemberService.update(...)` verified to return `PartyMember`; card id `pm-card-<id>` produced in Task 4 and referenced by the edit form target defined in Task 3.
