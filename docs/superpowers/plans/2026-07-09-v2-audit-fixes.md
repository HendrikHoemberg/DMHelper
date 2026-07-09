# DMHelper v2 Audit Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 30+ bugs from comprehensive SPEC.md audit covering live-sync, statblock resolution, turn order, map primitives, character sheet engine, recharge abilities, data scoping, and cross-entity integrity.

**Architecture:** Eight independent modules ordered by dependency: (1) Live Sync, (2) Map Primitives, (3) Turn Order & Round Logic, (4) StatBlock Resolution, (5) Import/Export Integrity, (6) Sheet Engine & Milestones, (7) Recharge Detection, (8) Polish & Validation. Each module is a self-contained PR with tests.

**Tech Stack:** Spring Boot 4.1, Java 25, JPA/Hibernate, H2, Thymeleaf, Konva.js, JUnit 5 + Mockito.

> **Verification pass (2026-07-09):** every task in this plan was cross-checked against the real source before handoff. Diagnoses were accurate almost everywhere, but several proposed fixes referenced methods/fields/schemas that don't actually exist, or would not have compiled, or (Task 3.2) would have introduced a worse bug than the one being fixed. The following tasks were rewritten in place and are flagged inline with a "Revised after source verification" note where they occur: **1.1, 1.2, 2.1, 2.2, 3.2, 3.4 (bonus fix), 8.1, 8.3 (merged into 8.1), 8.6, 8.9.1, 8.9.6**. All other tasks were verified correct as originally written (or plausible-but-unverified for a few of the smaller quick wins) and are unchanged.

---

## Module 1: Live Table Sync

> **Revised after source verification:** the original drafts of Task 1.1 and 1.2 referenced JS methods (`onTokenDragEnd`, `onTokenMove`, `addTokenAt`, `onHideSelected`, `deleteSelected`, `handleTokenupdateMessage`) and a `broadcast(LiveTableState)` overload that do not exist in the real codebase. Rewritten below against the actual `battle-map.js` and `TablePresentationService.java`.

### Task 1.1: Fire `battle-state-changed` on every real token mutation

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js`

**Problem:** `emitState()` (which fires `state-changed` → `battle-state-changed`, consumed by `battle.html:428-430` to POST `/api/v1/table/refresh`) is only called once, in `load()` (line 116). The real token-mutating methods are `saveTokenMove()` (357), `createToken()` (374), `deleteToken()` (386), `duplicateToken()` (396), `markDead()` (406), `updateToken()` (481), and `addPartyToMap()` (494) — each already does `this.emit('tokenupdate', { tokens: this.tokens })` (which repaints only the DM's own canvas) but never emits `state-changed`.

**Fix:** Add `this.emit('state-changed');` immediately after the existing `this.emit('tokenupdate', ...)` call in each of those methods, plus in `switchToMap()` (917) so presenting continuity survives a map switch.

- [ ] **Step 1: Add the emit call to each real mutation method**

```javascript
// e.g. in saveTokenMove(), right after:
this.emit('tokenupdate', { tokens: this.tokens });
this.emit('state-changed');
```

Repeat identically in `createToken()`, `deleteToken()`, `duplicateToken()`, `markDead()`, `updateToken()`, `addPartyToMap()`, and `switchToMap()` — each already has a `this.emit('tokenupdate', ...)` call; add `this.emit('state-changed');` directly after it.

- [ ] **Step 2: Manual verification**

Present a map (`Send to Table`). In the DM view: drag a token, add one from a statblock, mark one dead, delete one, switch maps. Confirm `/player` (open in a second tab) updates within ~1s each time. No new automated test — a real Playwright E2E test covering this path is deferred to Task 8.9.14.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js
git commit -m "fix: fire battle-state-changed on every real token mutation so player view stays in sync"
```

---

### Task 1.2: Push a table refresh when the turn advances

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

**Problem:** Task 1.1 fixes token mutations by relying on the *existing* `battle-state-changed` → `POST /api/v1/table/refresh` → `TablePresentationService.broadcastCurrentState()` path (`TablePresentationController.java:35-38`), which already correctly re-reads tokens/combatants/turn index from the DB and re-broadcasts. Turn advancement never goes through the browser at all — `EncounterApiController` calls `EncounterService.nextTurn()`/`previousTurn()` directly, and nothing in that path touches `TablePresentationService` (confirmed by grep: zero references to it anywhere under `encounter/`). So "Next Turn" still never reaches the player view even after Task 1.1.

`LiveTableState.tokenMoved(MapSnapshot)` and `turnChanged(List<CombatantSnapshot>, int)` (`LiveTableState.java:26-32`) really do exist unused, as the audit says — but wiring them in means building a second, parallel incremental-message path. `TablePresentationService.broadcast()` (line 156) is `private`, takes **zero arguments**, and just replays the `onStateChange` callback against the `currentState` field — it has no support today for dispatching a distinct `TOKEN_MOVED`/`TURN_CHANGED` payload type, and the client side has no handler for one either. Building that out is real, separable work. It is not required to fix this bug: the already-correct `broadcastCurrentState()` full-refresh is enough.

**Fix:** Inject `TablePresentationService` into `EncounterService` and call `broadcastCurrentState()` at the end of `nextTurn()` and `previousTurn()`. No circular dependency: `TablePresentationService`'s constructor depends only on `PlayerSafeProjectionService`, `GameMapRepository`, `EncounterRepository`, `CombatantRepository`, `HandoutRepository` — not `EncounterService`.

- [ ] **Step 1: Inject TablePresentationService into EncounterService**

```java
private final TablePresentationService tablePresentationService;
// add as the last constructor parameter and assign it in the body
```

- [ ] **Step 2: Call it at the end of nextTurn() and previousTurn()**

```java
// nextTurn(), just before `return new EncounterDto(...)`:
tablePresentationService.broadcastCurrentState();

// previousTurn() (post Task 3.1's restructuring), just before the final
// `return toDto(encounter);` that follows an actual index/round change:
tablePresentationService.broadcastCurrentState();
```

`broadcastCurrentState()` already no-ops safely when nothing is presented or the presented mode isn't `MAP` (`TablePresentationService.java:112-114`), so it's safe to call unconditionally.

- [ ] **Step 3: Note the remaining gap (not fixed here)**

HP changes, condition add/remove, and `undo()` have the identical staleness problem. Out of scope for this task, but the fix is the same one-line call added to `applyDamage()`, `addCondition()`, `removeCondition()`, and `undo()`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
git commit -m "fix: broadcast table state after turn advancement so player view reflects Next/Previous Turn"
```

---

## Module 2: Map Primitive Rendering Outside Editor

> **Revised after source verification:** the original Task 2.1 invented a primitive schema (`lowercase types`, `col/row/w/h/layerId`) that doesn't match the real one, and proposed baking primitives into saved cells, which conflicts with the codebase's existing, deliberate design (primitives stay as editable source-of-truth, expanded only at render time — see `map-editor.js:413`, "expanded to cells on render, never serialized"). Rewritten below to extend that existing pattern instead of replacing it.

### Task 2.1: Expand primitives into visible cells outside the editor

**Files:**
- Modify: `src/main/resources/static/js/map/shared.js`
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/static/js/player/player-view.js`

**Problem:** The real primitive schema (`map-editor.js:979,992,1007`) is `{type: 'ROOM'|'DOOR'|'REGION'|'CORRIDOR', startCol, startRow, endCol, endRow, terrain?}` — uppercase types, `start*/end*` fields, no `layerId`. `map-editor.js` already has a correctly-shaped expansion function, `expandPrimitives()` (lines 416-450), used *only* to feed the editor's own live preview (`addCellRect(kl, cell, {primitive:true})`, line 309-310). Primitives are deliberately never baked into `cells` on save — `buildDocumentFromCanvas()` explicitly skips `_primitive`-tagged nodes (line 1574: "`_primitive` nodes ... carry neither attr → skipped"). That's intentional, not a bug: primitives stay editable, re-expanded at render time. The actual bug is that `battle-map.js`'s `renderTerrain()` and `player-view.js`'s terrain loop (`player-view.js:121-126`) only ever read `layers[...].cells` directly and never call any primitive-expansion logic — so a primitive-only map renders blank everywhere except inside the editor.

**Fix:** Move `expandPrimitives()` into `shared.js` as a plain exported function, and call it from all three renderers (editor, battle-map, player-view). No server-side change needed: `MapDocumentDto.primitives()` (Java) already passes primitives through untouched on both the raw `/maps/{id}/document` endpoint and the player-projected path (`PlayerSafeProjectionService.projectMapDocument()`, line 55).

- [ ] **Step 1: Move `expandPrimitives()` into `shared.js`**

```javascript
// shared.js
export function expandPrimitives(document) {
    const cells = [];
    for (const p of (document?.primitives || [])) {
        const c0 = Math.min(p.startCol, p.endCol), c1 = Math.max(p.startCol, p.endCol);
        const r0 = Math.min(p.startRow, p.endRow), r1 = Math.max(p.startRow, p.endRow);
        switch (p.type) {
            case 'ROOM':
                for (let r = r0; r <= r1; r++) {
                    for (let c = c0; c <= c1; c++) {
                        const edge = r === r0 || r === r1 || c === c0 || c === c1;
                        cells.push({ col: c, row: r, terrain: edge ? 'wall' : 'floor' });
                    }
                }
                break;
            case 'CORRIDOR':
                break; // open floor; nothing to paint
            case 'DOOR':
                cells.push({ col: p.startCol, row: p.startRow, terrain: 'door' });
                break;
            case 'REGION': {
                const terrain = p.terrain || 'floor';
                for (let r = r0; r <= r1; r++) {
                    for (let c = c0; c <= c1; c++) {
                        cells.push({ col: c, row: r, terrain });
                    }
                }
                break;
            }
        }
    }
    return cells;
}
```

Note the one behavior change from the editor's current copy: `ROOM` now explicitly pushes `terrain: 'floor'` for interior cells instead of pushing nothing for them — this folds in Task 2.2 (below).

- [ ] **Step 2: Make `map-editor.js` delegate to the shared function**

```javascript
import { expandPrimitives } from './shared.js';
// ...
expandPrimitives() {
    return expandPrimitives(this.document);
}
```
Every existing call site in `map-editor.js` (e.g. line 309) keeps working unchanged.

- [ ] **Step 3: Use it in `battle-map.js`'s `renderTerrain()`**

```javascript
import { drawGrid, setupPanAndZoom, cellPos, snapPixel, pixelToCell, expandPrimitives } from './shared.js';
// ...
renderTerrain(doc) {
    this.terrainLayer.destroyChildren();
    if (!doc || !doc.layers) return;
    const terrainLayer = doc.layers.find(l => l.id === 'terrain');
    const explicitCells = terrainLayer?.cells || [];
    const explicitKeys = new Set(explicitCells.map(c => `${c.col},${c.row}`));
    const primitiveCells = expandPrimitives(doc).filter(c => !explicitKeys.has(`${c.col},${c.row}`));
    const s = this.cellSizePx;
    for (const cell of [...primitiveCells, ...explicitCells]) {
        const color = TERRAIN_COLORS[cell.terrain] || '#2a2a3e';
        this.terrainLayer.add(new Konva.Rect({
            x: cell.col * s, y: cell.row * s, width: s, height: s,
            fill: color, stroke: '#222', strokeWidth: 0.5,
        }));
    }
    this.terrainLayer.batchDraw();
}
```
Explicitly-painted cells win over primitive-implied ones, matching how the editor already lets you paint over a primitive.

- [ ] **Step 4: Apply the same merge in `player-view.js` (terrain loop around lines 121-126)**

Import `expandPrimitives` from `shared.js` (add the import if `player-view.js` doesn't already have one) and merge primitive cells behind the existing `layer.cells` the same way as Step 3.

- [ ] **Step 5: Manual verification**

In the editor, draw a Room primitive and a Door primitive, save, open Play mode and `/player` — confirm walls, floor, and the door render identically to the editor's own preview.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/js/map/shared.js src/main/resources/static/js/map/map-editor.js src/main/resources/static/js/map/battle-map.js src/main/resources/static/js/player/player-view.js
git commit -m "fix: expand map primitives into terrain cells in battle-map and player-view, not just the editor"
```

---

### Task 2.2: Room primitive floor cells

Folded into Task 2.1's `expandPrimitives()` rewrite above (the `edge ? 'wall' : 'floor'` branch) — no separate change needed. This also fixes the same bug in the editor's own live preview, which previously pushed only wall cells and left the interior "floored" purely by coincidence of the blank canvas background.

---

## Module 3: Turn Order, Round Logic & Groups

### Task 3.1: Fix `previousTurn` round-decrement-before-bailout bug

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

**Problem:** In `previousTurn()` (line ~694-708), the `do-while` loop decrements `encounter.setRound(encounter.getRound() - 1)` inside the loop body before the bail-out check `loopCount >= combatants.size()`. If all combatants except the current one are defeated, the loop decrements the round and then returns without updating `activeTurnIndex`, leaving the round counter silently corrupted.

**Fix:** Reorder to check bail-out first, and move the round decrement outside the loop.

- [ ] **Step 1: Restructure the loop in previousTurn**

Read `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java` at lines 679-718. Replace the `do-while`:

```java
public EncounterDto previousTurn(UUID encounterId) {
    Encounter encounter = findEntityById(encounterId);
    List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

    if (combatants.isEmpty() || encounter.getActiveTurnIndex() < 0) {
        return toDto(encounter);
    }

    int oldIdx = encounter.getActiveTurnIndex();
    if (oldIdx >= combatants.size()) {
        oldIdx = combatants.size() - 1;
    }

    int idx = oldIdx;
    int checked = 0;
    boolean crossedBoundary = false;

    while (checked < combatants.size()) {
        if (idx == 0) {
            idx = combatants.size() - 1;
            if (encounter.getRound() > 1) {
                crossedBoundary = true;
            }
        } else {
            idx--;
        }
        checked++;
        if (!combatants.get(idx).isDefeated()) {
            break;
        }
    }

    if (checked >= combatants.size()) {
        return toDto(encounter);
    }

    if (crossedBoundary) {
        encounter.setRound(encounter.getRound() - 1);
        logEntry(encounterId, CombatLogEntry.EntryType.TURN_END, "", "");
    }

    encounter.setActiveTurnIndex(idx);
    encounterRepo.save(encounter);
    return toDto(encounter);
}
```

- [ ] **Step 2: Run existing tests**

```bash
mvn test -pl . -Dtest=EncounterServiceTest -Dsurefire.useFile=false
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
git commit -m "fix: restructure previousTurn to check bailout before decrementing round counter"
```

---

### Task 3.2: Monster groups share a turn slot

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java`

> **Revised after source verification:** the original draft's "redirect to the group leader" logic was traced against its own example scenario and found to deadlock the turn cursor on the leader (and spuriously increment the round every call) instead of advancing past the group. Rewritten below with a different, verified-correct approach.

**Problem:** `Combatant.groupId`/`isGroupLeader()` already exist (`Combatant.java:46,48,118-122`) and are unused by `nextTurn()`/`previousTurn()` (`EncounterService.java:631-677`) — confirmed by reading both methods: pure `sortOrder` index walks with no group awareness. "The 4 goblins" take 4 separate turns.

**Fix:** Change the walk's stop condition so a non-defeated combatant is a valid stopping point only when it's ungrouped or is its group's leader — non-leader group members get silently skipped over during the walk, the same way defeated combatants already are. The group leader's slot becomes the only place the cursor stops for that group.

- [ ] **Step 1: Update the walk condition in `nextTurn()`**

```java
// OLD:
while (checked < combatants.size()) {
    idx = (idx + 1) % combatants.size();
    if (!combatants.get(idx).isDefeated()) {
        break;
    }
    checked++;
}

// NEW:
while (checked < combatants.size()) {
    idx = (idx + 1) % combatants.size();
    Combatant candidate = combatants.get(idx);
    boolean stoppable = !candidate.isDefeated()
            && (candidate.getGroupId() == null || candidate.isGroupLeader());
    if (stoppable) {
        break;
    }
    checked++;
}
```

Trace on `[Leader@0(G,leader), Member1@1(G), Member2@2(G), OtherPC@3]`, turn starting on the Leader (`oldIdx=0`): idx=1 (Member1, grouped non-leader → skip) → idx=2 (Member2, same → skip) → idx=3 (OtherPC, ungrouped → stop). One `nextTurn()` call correctly advances past the whole group. Next call from `oldIdx=3`: idx=0 (Leader, grouped leader → stop); `idx(0) <= oldIdx(3)` so the round correctly advances, once per full lap through the group.

- [ ] **Step 2: Mirror the same condition in `previousTurn()`** (post-Task-3.1 version)

```java
// In the while-loop from Task 3.1, change the break condition to:
if (!combatants.get(idx).isDefeated()
        && (combatants.get(idx).getGroupId() == null || combatants.get(idx).isGroupLeader())) {
    break;
}
```

- [ ] **Step 3: Known v1 limitation**

If the group leader is defeated but other members survive, the walk skips that group's slot entirely rather than promoting a member to leader. The DM can still act for a specific surviving member via the existing `setActiveTurn(encounterId, combatantId)` endpoint — document this as a known limitation instead of solving leader succession here.

- [ ] **Step 4: Write test**

```java
@Test
void groupMembersShareTurnSlot() {
    Encounter encounter = createActiveEncounter();
    String groupId = UUID.randomUUID().toString();
    Combatant leader = createCombatant(encounter, "Goblin Leader");
    leader.setGroupId(groupId); leader.setGroupLeader(true); leader.setSortOrder(0);
    Combatant member1 = createCombatant(encounter, "Goblin 1");
    member1.setGroupId(groupId); member1.setGroupLeader(false); member1.setSortOrder(1);
    Combatant member2 = createCombatant(encounter, "Goblin 2");
    member2.setGroupId(groupId); member2.setGroupLeader(false); member2.setSortOrder(2);
    Combatant other = createCombatant(encounter, "Fighter");
    other.setSortOrder(3);
    combatantRepo.saveAll(List.of(leader, member1, member2, other));

    encounter.setActiveTurnIndex(0);
    encounterRepo.save(encounter);

    EncounterDto dto = encounterService.nextTurn(encounter.getId());
    assertThat(dto.activeTurnIndex()).isEqualTo(3); // skipped straight past the group to the Fighter

    EncounterDto dto2 = encounterService.nextTurn(encounter.getId());
    assertThat(dto2.activeTurnIndex()).isEqualTo(0); // back to the leader
    assertThat(dto2.round()).isEqualTo(2); // round advanced exactly once per lap
}
```

- [ ] **Step 5: Run test and commit**

```bash
mvn test -pl . -Dtest=EncounterServiceTest#groupMembersShareTurnSlot -Dsurefire.useFile=false
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java
git commit -m "fix: skip non-leader group members in turn walk so monster groups share one turn slot"
```

---

### Task 3.3: Fix undo discarding manual initiative reordering

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java`

**Problem:** `undo()` unconditionally calls `rebuildSortOrderForUndo()` which rebuilds `sortOrder` from `initiative`/`tieBreaker`/`name` after replaying the log. Any manual drag-to-reorder done between the undone action and a prior action is silently reverted.

**Fix:** Instead of rebuilding sort order from initiative, only rebuild for combatants whose `sortOrder` was modified by the replayed entries. For v1, track original `sortOrder` at undo start and detect whether an entry would have changed it. If not, don't rebuild.

Simplest correct approach: log `SORT_ORDER` changes as a separate log entry when `resortCombatants` is called. During undo replay, restore sort orders from the log.

- [ ] **Step 1: Log sort order changes in resortCombatants()**

Read `resortCombatants()` method. After sorting, log the new order:

```java
// At the end of resortCombatants:
Map<String, Integer> orderMap = new HashMap<>();
for (int i = 0; i < combatants.size(); i++) {
    combatants.get(i).setSortOrder(i);
    orderMap.put(combatants.get(i).getId().toString(), i);
}
logEntry(encounterId, CombatLogEntry.EntryType.SORT_ORDER, "",
        JSON_MAPPER.writeValueAsString(orderMap));
```

- [ ] **Step 2: Handle SORT_ORDER in replayEntry()**

```java
case SORT_ORDER -> {
    try {
        var node = JSON_MAPPER.readTree(entry.getPayload());
        node.fieldNames().forEachRemaining(combatantIdStr -> {
            try {
                UUID cid = UUID.fromString(combatantIdStr);
                Combatant c = combatants.get(cid);
                if (c != null) {
                    c.setSortOrder(node.get(combatantIdStr).asInt());
                }
            } catch (Exception e) { /* ignore */ }
        });
    } catch (Exception e) { /* ignore */ }
}
```

- [ ] **Step 3: Remove unconditional rebuildSortOrderForUndo call**

In `undo()`, change:
```java
// OLD:
rebuildSortOrderForUndo(combatants, encounter);

// NEW:
// Sort order is now restored via SORT_ORDER log entries during replay
// Don't rebuild from initiative
```

- [ ] **Step 4: Write test**

```java
@Test
void undoDoesNotDiscardManualReordering() {
    Encounter encounter = createActiveEncounter();
    Combatant a = createCombatant(encounter, "A"); a.setInitiative(20); a.setSortOrder(0);
    Combatant b = createCombatant(encounter, "B"); b.setInitiative(10); b.setSortOrder(1);
    combatantRepo.saveAll(List.of(a, b));

    // Manually reorder: swap A and B positions
    a.setSortOrder(1);
    b.setSortOrder(0);
    combatantRepo.save(a);
    combatantRepo.save(b);
    encounterService.logSortOrder(encounter.getId(), List.of(b, a)); // or equivalent

    // Do some other action
    encounterService.applyDamage(encounter.getId(), a.getId(), -5);

    // Undo damage
    encounterService.undoLastAction(encounter.getId());

    // Verify manual reorder preserved
    Combatant aAfter = combatantRepo.findById(a.getId()).get();
    Combatant bAfter = combatantRepo.findById(b.getId()).get();
    assertEquals(1, aAfter.getSortOrder());
    assertEquals(0, bAfter.getSortOrder());
}
```

- [ ] **Step 5: Run tests and commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java
git commit -m "fix: log sort order changes so undo doesn't revert manual initiative reordering"
```

---

### Task 3.4: Add lair action to turn order as initiative-20 slot

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/main/resources/templates/encounter/_tracker.html`

**Problem:** Lair action is rendered as a free-floating button after the combatant list, not a slot at initiative 20 the turn cursor passes through.

**Fix:** Treat lair actions as a pseudo-combatant in the turn order. When `nextTurn()` advances past initiative 20 relative to the last combatant, activate the lair action. Add a visual indicator in the tracker template.

> **Bonus fix found during verification (not one of the 7 originally requested, but a hard compile-blocker with a trivial fix):** `Encounter.java` currently only has `lairActionName`/`lairActionDescription` (lines 46-49, 75-79) — no `lairActionTriggered` field. The steps below call `encounter.setLairActionTriggered(true)` and expect `encounter.lairActionAvailable` in the template; neither exists yet. Add this first:
> ```java
> // Encounter.java
> @Column(nullable = false)
> private boolean lairActionTriggered = false;
>
> public boolean isLairActionTriggered() { return lairActionTriggered; }
> public void setLairActionTriggered(boolean lairActionTriggered) { this.lairActionTriggered = lairActionTriggered; }
> ```
> Then add `boolean lairActionAvailable` to `EncounterDto` (already listed in this plan's own "Summary of New Entities/Fields" table below), computed as `encounter.getLairActionName() != null && !encounter.isLairActionTriggered()` wherever the DTO is built.

- [ ] **Step 1: Add lair-action slot to nextTurn()**

In `EncounterService.nextTurn()`, after determining the next combatant `idx`, check if the lair action should fire:

```java
// After finding idx and before saving:
// Lair action check: fires at initiative 20, once per round
Combatant oldCombatant = combatants.get(oldIdx);
Combatant newCombatant = combatants.get(idx);
if (encounter.getLairActionName() != null &&
    oldCombatant.getInitiative() >= 20 && newCombatant.getInitiative() < 20) {
    // Lair action fires here — include in response
    encounter.setLairActionTriggered(true);
}

// Reset lair action flag on round wrap
if (idx <= oldIdx) {
    encounter.setLairActionTriggered(false);
    // ... existing round-advance logic
}
```

Add `boolean lairActionAvailable` to `EncounterDto` record.

- [ ] **Step 2: Update _tracker.html to show lair-action slot**

Read `src/main/resources/templates/encounter/_tracker.html`. Insert between the last combatant with initiative > 20 and the first with initiative < 20:

```html
<!-- LAIR ACTION SLOT -->
<th:block th:if="${encounter.lairActionName != null && encounter.lairActionAvailable}">
    <div class="combatant-row lair-action" th:data-lair="true">
        <span class="initiative-badge">20</span>
        <span th:text="${encounter.lairActionName}" class="combatant-name"></span>
        <button hx-post="@{/api/v1/encounters/{id}/lair-action(id=${encounter.id})}"
                class="btn-sm">Activate</button>
    </div>
</th:block>
```

- [ ] **Step 3: Add lair-action endpoint**

Add to `EncounterApiController`:
```java
@PostMapping("/{id}/lair-action")
public String triggerLairAction(@PathVariable UUID id) {
    encounterService.triggerLairAction(id);
    // Log to session log
    return "redirect:/encounters/" + id;
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java src/main/resources/templates/encounter/_tracker.html src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java
git commit -m "feat: integrate lair action into turn order at initiative 20"
```

---

### Task 3.5: Fix Combatant.kind set to creature type instead of PC/NPC/MONSTER/OBJECT

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

**Problem:** When creating a combatant from a statblock, `kind` is set to `sb.getType()` (e.g., "Aberration") instead of "MONSTER". The field's contract is PC/NPC/MONSTER/OBJECT.

**Fix:** At line 274 of `EncounterService.java`, change:
```java
// OLD:
kind = sb.getType();

// NEW:
kind = "MONSTER";
```

- [ ] **Step 1: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
git commit -m "fix: set combatant kind to 'MONSTER' instead of creature type string from statblock"
```

---

## Module 4: StatBlock Resolution & Source Key

### Task 4.1: Fix SRD statblock resolution on import (campaign=null)

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java`

**Problem:** `CampaignService.java:547` calls `findByCampaignIdAndSourceKey(saved.getId(), cDto.statBlockKey())`. SRD statblocks always have `campaign = null`, so this never matches. `StatBlockRepository.java:30` has `findByCampaignIdAndSourceKey` that requires campaign join, but `findBySourceKey(String)` at line 28 exists without campaign.

**Fix:** Change import resolution to a two-step lookup: first try campaign-scoped, then fall back to global (SRD).

- [ ] **Step 1: Fix statblock lookup in import**

In `CampaignService.java` line ~546:

```java
// OLD:
if (cDto.statBlockKey() != null) {
    statBlockRepository.findByCampaignIdAndSourceKey(saved.getId(), cDto.statBlockKey())
            .ifPresentOrElse(combatant::setStatBlock,
                    () -> System.err.println("WARNING: Unknown statblock key: " + cDto.statBlockKey()));
}

// NEW:
if (cDto.statBlockKey() != null) {
    var resolved = statBlockRepository.findByCampaignIdAndSourceKey(saved.getId(), cDto.statBlockKey())
            .or(() -> statBlockRepository.findBySourceKey(cDto.statBlockKey()));
    resolved.ifPresentOrElse(combatant::setStatBlock,
            () -> System.err.println("WARNING: Unknown statblock key: " + cDto.statBlockKey()));
}
```

- [ ] **Step 2: Write test**

```java
@Test
void importResolvesSrdStatBlock() {
    Campaign campaign = campaignService.create("SRD Test", "");
    // Ensure SRD monster "goblin" exists in DB (seeded)
    StatBlock goblin = statBlockRepo.findBySourceKey("goblin")
            .orElseGet(() -> {
                StatBlock sb = new StatBlock();
                sb.setSource(StatBlock.Source.SRD);
                sb.setSourceKey("goblin");
                sb.setName("Goblin");
                sb.setCr("1/4");
                sb.setType("Humanoid");
                sb.setAc(15);
                sb.setHp("7 (2d6)");
                return statBlockRepo.save(sb);
            });

    String json = """
            {"formatVersion":1,"campaign":{"name":"SRD Test","description":""},
             "maps":[],"statBlocks":[],"encounters":[
               {"name":"Test Enc","status":"PLANNING","round":1,"activeTurnIndex":-1,
                "logSequence":0,"combatants":[
                  {"name":"Goblin","initiative":15,"tieBreaker":0,"sortOrder":0,
                   "maxHp":7,"currentHp":7,"tempHp":0,"kind":"MONSTER",
                   "statBlockKey":"goblin","defeated":false,"hidden":false}]}
             ],"notes":[],"quicknotes":[],"assignments":[],"ledger":[],"timeline":[]}
            """;

    Campaign imported = campaignService.importFromJson(json);
    List<Encounter> encounters = encounterRepo.findByCampaignId(imported.getId());
    Combatant c = combatantRepo.findByEncounterId(encounters.get(0).getId()).get(0);
    assertThat(c.getStatBlock()).isNotNull();
    assertThat(c.getStatBlock().getSourceKey()).isEqualTo("goblin");
}
```

- [ ] **Step 3: Run test**

```bash
mvn test -pl . -Dtest=CampaignServiceTest#importResolvesSrdStatBlock -Dsurefire.useFile=false
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java
git commit -m "fix: resolve SRD statblocks (campaign=null) on import via global findBySourceKey fallback"
```

---

### Task 4.2: Set sourceKey and xp in createCustom()

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java`

**Problem:** `createCustom()` never calls `setSourceKey(...)` or `setXp(...)`. Homebrew monsters have no sourceKey for import round-tripping and contribute 0 XP to the difficulty calculator.

**Fix:** Add `sourceKey` and `xp` parameters to `createCustom()`. If `sourceKey` is null, auto-generate one from the name (slugified).

- [ ] **Step 1: Add parameters to createCustom()**

Read `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java`. Add to the method signature (after `senses` and `languages`):

```java
public StatBlock createCustom(UUID campaignId, String name, String cr, String type,
                              int ac, String hp, String speed,
                              int str, int dex, int con, int intel, int wis, int cha,
                              Integer strSave, Integer dexSave, Integer conSave,
                              Integer intSave, Integer wisSave, Integer chaSave,
                              String skills, String damageVuln, String damageRes,
                              String damageImm, String condImm,
                              String senses, String languages,
                              String sourceKey, Integer xp) {  // NEW params
```

In the method body:
```java
// Auto-generate sourceKey if not provided
if (sourceKey != null && !sourceKey.isBlank()) {
    sb.setSourceKey(sourceKey);
} else {
    sb.setSourceKey(name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
}
// Set XP
if (xp != null) {
    sb.setXp(xp);
} else {
    sb.setXp(calculateXpFromCr(cr));
}
```

- [ ] **Step 2: Add calculateXpFromCr helper**

```java
private int calculateXpFromCr(String cr) {
    return switch (cr) {
        case "0" -> 10;
        case "1/8" -> 25;
        case "1/4" -> 50;
        case "1/2" -> 100;
        case "1" -> 200;
        case "2" -> 450;
        case "3" -> 700;
        case "4" -> 1100;
        case "5" -> 1800;
        case "6" -> 2300;
        case "7" -> 2900;
        case "8" -> 3900;
        case "9" -> 5000;
        case "10" -> 5900;
        case "11" -> 7200;
        case "12" -> 8400;
        case "13" -> 10000;
        case "14" -> 11500;
        case "15" -> 13000;
        case "16" -> 15000;
        case "17" -> 18000;
        case "18" -> 20000;
        case "19" -> 22000;
        case "20" -> 25000;
        case "21" -> 33000;
        case "22" -> 41000;
        case "23" -> 50000;
        case "24" -> 62000;
        case "25" -> 75000;
        case "26" -> 90000;
        case "27" -> 105000;
        case "28" -> 120000;
        case "29" -> 135000;
        case "30" -> 155000;
        default -> {
            try { yield Integer.parseInt(cr) * 200; }
            catch (NumberFormatException e) { yield 0; }
        }
    };
}
```

- [ ] **Step 3: Update callers**

Read `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java` and `StatBlockServiceTest` to update calls that pass to `createCustom()`. Add `null, null` for `sourceKey` and `xp` where they're not provided by the caller (backward compatible).

- [ ] **Step 4: Write test**

```java
@Test
void createCustomSetsSourceKeyAndXp() {
    StatBlock sb = statBlockService.createCustom(campaignId,
            "Homebrew Monster", "5", "Monstrosity",
            16, "85 (10d10+30)", "30 ft.",
            18, 14, 16, 6, 12, 8,
            4, 2, 3, -1, 1, -1,
            "Perception +4", null, null, null, null,
            "Darkvision 60 ft.", "—",
            null, null); // sourceKey=null, xp=null -> auto-created
    assertThat(sb.getSourceKey()).isEqualTo("homebrew-monster");
    assertThat(sb.getXp()).isEqualTo(1800);
}
```

- [ ] **Step 5: Run test and commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java
git commit -m "fix: auto-generate sourceKey and xp for custom statblocks so they survive import and contribute to difficulty"
```

---

### Task 4.3: Fix dry-run import SRD-key check

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

**Problem:** `validateImport()` at line 340 checks `key.startsWith("srd-")` but real seeded keys are bare slugs ("goblin"). The check never fires on genuine content. Also missing spatial validation (tokens-in-bounds, primitives-in-dimensions).

**Fix:** Remove the `startsWith` guard — always resolve the key. Add spatial validation.

- [ ] **Step 1: Fix the SRD key check**

```java
// OLD:
if (c.statBlockKey() != null && c.statBlockKey().startsWith("srd-")) {
    var resolved = statBlockRepository.findBySourceKey(c.statBlockKey());
    if (resolved.isEmpty()) {
        warnings.add("SRD statblock key '" + c.statBlockKey() + "' not found...");
    }
}

// NEW:
if (c.statBlockKey() != null) {
    var resolved = statBlockRepository.findByCampaignIdAndSourceKey(null, c.statBlockKey())
            .or(() -> statBlockRepository.findBySourceKey(c.statBlockKey()));
    if (resolved.isEmpty()) {
        warnings.add("Statblock key '" + c.statBlockKey() + "' not found in library");
    }
}
```

- [ ] **Step 2: Add spatial validation**

In `validateImport()`, after the map grid validation:

```java
if (mapDto.tokens() != null && mapDto.grid() != null) {
    for (var tDto : mapDto.tokens()) {
        if (tDto.positionX() < 0 || tDto.positionX() >= grid.w() ||
            tDto.positionY() < 0 || tDto.positionY() >= grid.h()) {
            warnings.add("Map '" + mapDto.name() + "': token '" + tDto.name() +
                    "' at (" + tDto.positionX() + "," + tDto.positionY() + ") is outside grid bounds");
        }
    }
}
if (mapDto.document() != null) {
    try {
        var docNode = objectMapper.readTree(objectMapper.writeValueAsString(mapDto.document()));
        if (docNode.has("primitives")) {
            for (var p : docNode.get("primitives")) {
                int col = p.get("col").asInt(0);
                int row = p.get("row").asInt(0);
                int w = p.get("w").asInt(0);
                int h = p.get("h").asInt(0);
                if (col < 0 || row < 0 || col + w > grid.w() || row + h > grid.h()) {
                    warnings.add("Map '" + mapDto.name() + "': primitive '" +
                            p.get("type").asText() + "' exceeds grid bounds");
                }
            }
        }
    } catch (Exception e) { /* ignore parse errors */ }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "fix: dry-run validates all statblock keys (not just srd- prefix) and checks spatial bounds"
```

---

## Module 5: Import/Export Integrity

### Task 5.1: Restore token statBlock/partyMember links on import

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`

**Problem:** Token import loop (lines 444-465) creates tokens with `name`, `kind`, `color`, position, HP, etc. but never calls `token.setStatBlock(...)` or `token.setPartyMember(...)`, even though the export DTO carries `statBlockKey` and `partyMemberName`.

**Fix:** After creating each token, resolve and set the statBlock and partyMember from the DTO.

- [ ] **Step 1: Check CampaignExportDto.TokenExportDto**

Read `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`. Verify the `TokenExportDto` record has `String statBlockKey` and `String partyMemberName` fields. If not, add them.

- [ ] **Step 2: Add resolution in the import loop**

In `CampaignService.importFromJson()`, inside the token creation loop (line ~446):

```java
if (tDto.statBlockKey() != null) {
    statBlockRepository.findByCampaignIdAndSourceKey(saved.getId(), tDto.statBlockKey())
            .or(() -> statBlockRepository.findBySourceKey(tDto.statBlockKey()))
            .ifPresent(token::setStatBlock);
}
if (tDto.partyMemberName() != null) {
    partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId()).stream()
            .filter(pm -> tDto.partyMemberName().equals(pm.getCharacterName()))
            .findFirst().ifPresent(token::setPartyMember);
}
```

- [ ] **Step 3: Verify export side includes these fields**

In `CampaignService.exportToJson()`, verify that when building `TokenExportDto.from(token, ...)`, the `statBlockKey` and `partyMemberName` are populated from `token.getStatBlock()` and `token.getPartyMember()`.

- [ ] **Step 4: Write round-trip test**

Add to `CampaignImportExportRoundTripTest`:
```java
@Test
void tokenStatBlockSurvivesRoundTrip() {
    // Create campaign, map, statblock, token with statblock
    // Export, import, verify token.getStatBlock() is not null
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java
git commit -m "fix: restore token statBlock and partyMember links during import"
```

---

### Task 5.2: Fix LedgerEntry itemAssignmentRef dropped on export/import

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

**Problem:** `LedgerEntry` has a `@ManyToOne ItemAssignment itemAssignmentRef` field but it's omitted from both export and import.

**Fix:** Add `String itemAssignmentRef` to `LedgerExportDto`. On export, serialize as the assignment's UUID (or a stable reference key). On import, resolve.

- [ ] **Step 1: Add field to DTO**

```java
public record LedgerExportDto(
        String id, String date, String description,
        String type, long amount, long balanceAfter,
        String itemAssignmentRef   // NEW
) {
    public static LedgerExportDto from(LedgerEntry entry) {
        return new LedgerExportDto(
                entry.getId().toString(), entry.getDate().toString(),
                entry.getDescription(), entry.getType(),
                entry.getAmount(), entry.getBalanceAfter(),
                entry.getItemAssignmentRef() != null ?
                        entry.getItemAssignmentRef().getId().toString() : null
        );
    }
}
```

- [ ] **Step 2: Resolve on import**

Build an `assignmentIdMap` (old-to-new UUID) during the assignment import phase, then use it when creating ledger entries.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "fix: preserve LedgerEntry.itemAssignmentRef during export and import"
```

---

### Task 5.3: Fix QuickNote ID remapping for ENCOUNTER/PARTY_MEMBER/CAMPAIGN targets

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

**Problem:** QuickNote import `switch` (line ~602) handles MAP, STATBLOCK, NOTE, HANDOUT but not ENCOUNTER, PARTY_MEMBER, or CAMPAIGN. These fall through to `default -> UUID.fromString(...)` which resolves the old UUID from the export file — a stale reference.

**Fix:** Add `ENCOUNTER`, `PARTY_MEMBER`, and `CAMPAIGN` cases using maps built during earlier import phases (encounter name→ID, party member name→ID, campaign is always `saved.getId()`).

- [ ] **Step 1: Add missing cases**

```java
resolvedId = switch (qnDto.targetType()) {
    case "MAP" -> mapKeyToId.get(qnDto.targetRef());
    case "STATBLOCK" -> statblockKeyToId.get(qnDto.targetRef());
    case "NOTE" -> { /* ... existing ... */ }
    case "HANDOUT" -> { /* ... existing ... */ }
    case "ENCOUNTER" -> encounterNameToId.get(qnDto.targetRef());
    case "PARTY_MEMBER" -> partyMemberNameToId.get(qnDto.targetRef());
    case "CAMPAIGN" -> saved.getId();  // always maps to the new campaign
    default -> { /* existing fallback */ }
};
```

- [ ] **Step 2: Build the encounter and party member maps during import**

In `importFromJson()`, after creating encounters:
```java
Map<String, UUID> encounterNameToId = new HashMap<>();
for (var encDto : dto.encounters()) {
    // ... create encounter ...
    encounterNameToId.put(encDto.name(), encounter.getId());
}
```

After creating party members:
```java
Map<String, UUID> partyMemberNameToId = new HashMap<>();
for (var pmDto : dto.party()) {
    // ... create party member ...
    partyMemberNameToId.put(pmDto.characterName(), member.getId());
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "fix: remap quicknote target IDs for ENCOUNTER, PARTY_MEMBER, and CAMPAIGN targets on import"
```

---

## Module 6: Character Sheet Engine & Milestones

### Task 6.1: Fix feat/ASI bonus parser to handle real ASI feat text

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngine.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngineTest.java`

**Problem:** The seeded "Ability Score Improvement" feat text is prose: "Increase one ability score of your choice by 2, or increase two ability scores of your choice by 1..." — no `+N to X` substring. The regex `\+(\d+)\s+to\s+(\w+)` never matches.

**Fix:** Add a structural `asiBonus` field to the `Feat` entity (or parse the standardized format from the seed data). For v1, recognize the ASI feat by its sourceKey and apply a "points pool" approach: `featRefs` containing "asi" means the character has that many ASI points available. The DM manually assigns them through the sheet UI.

Better v1 approach: Add a `Map<String, Integer> abilityScoreAdjustments` field to `Feat`. On seed import, for the ASI feat specifically, set it to allow manual assignment. Update `SheetEngine.derive()` to check for this field.

Simplest v1: Hard-code the ASI feat key check and surface it as a manual override.

```java
// In SheetEngine.derive(), feat processing:
for (Feat feat : feats) {
    if ("ability-score-improvement".equals(feat.getSourceKey())) {
        // ASI is manual — surface as a reminder in derived values
        featsRequiringManualAssignment.add(feat.getName() + " (ASI)");
        continue;
    }
    // Parse benefit text for bonuses as before (may match other feats)
    String benefit = feat.getBenefit();
    if (benefit != null) {
        // existing regex logic
    }
}
```

- [ ] **Step 1: Add manual-ASI detection**

Read `SheetEngine.java` at line 448. Add early return for ASI:

```java
List<String> featsRequiringManual = new ArrayList<>();

for (Feat feat : feats) {
    if ("ability-score-improvement".equals(feat.getSourceKey())) {
        featsRequiringManual.add("Ability Score Improvement");
        continue;
    }
    if (feat.getBenefit() != null) {
        Matcher m = Pattern.compile("\\+(\\d+)\\s+to\\s+(\\w+)", Pattern.CASE_INSENSITIVE)
                .matcher(feat.getBenefit());
        while (m.find()) {
            // existing case logic
        }
    }
}
```

Add `featsRequiringManual` to the `DerivedValues` record so the UI can display a warning/reminder.

- [ ] **Step 2: Update DerivedValues record**

```java
public record DerivedValues(
        // ... existing fields ...
        List<String> featsRequiringManualAssignment
) {}
```

- [ ] **Step 3: Write test**

```java
@Test
void asiFeatIsFlaggedForManualAssignment() {
    DerivationInput input = new DerivationInput(
            Map.of("str", 15, "dex", 14, "con", 13, "int", 10, "wis", 12, "cha", 8),
            List.of(Map.of("className", "Fighter", "level", 4)),
            "Hill Dwarf", "Soldier",
            List.of("ability-score-improvement"),
            "{}", List.of(), List.of(), List.of(), List.of(),
            List.of(), "{}", null
    );
    DerivedValues result = sheetEngine.derive(input);
    assertThat(result.featsRequiringManualAssignment()).contains("Ability Score Improvement");
    // Ability scores unchanged (manual override)
    assertThat(result.abilityScores().get("str")).isEqualTo(15);
}
```

- [ ] **Step 4: Run test and commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngine.java src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngineTest.java
git commit -m "fix: flag ASI feat for manual assignment instead of silently ignoring prose-based benefit text"
```

---

### Task 6.2: Implement milestone leveling mode

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java`

**Problem:** `checkLevelUp()` runs unconditionally. No per-campaign milestone mode exists. Milestone mode is referenced in §3 and §4.12 but zero code backs it.

**Fix:** Add `private boolean milestoneLeveling` field to `Campaign`. In `SheetService.checkLevelUp()`, when the campaign uses milestone mode, skip XP-based level-up and instead expose an endpoint `POST /api/v1/sheets/{id}/level-up` that a DM clicks manually.

- [ ] **Step 1: Add milestoneLeveling to Campaign**

Read `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java`. Add:

```java
@Column(nullable = false)
private boolean milestoneLeveling = false;

public boolean isMilestoneLeveling() { return milestoneLeveling; }
public void setMilestoneLeveling(boolean milestoneLeveling) { this.milestoneLeveling = milestoneLeveling; }
```

- [ ] **Step 2: Add toggle to campaign settings API**

In `CampaignController` or `CampaignApiController`, add:
```java
@PostMapping("/{id}/milestone")
public String toggleMilestone(@PathVariable UUID id, @RequestParam boolean enabled) {
    campaignService.setMilestoneMode(id, enabled);
    return "redirect:/campaigns/" + id;
}
```

- [ ] **Step 3: Gate checkLevelUp on mode**

Read `SheetService.checkLevelUp()`. At the top:
```java
Campaign campaign = sheet.getPartyMember().getCampaign();
if (campaign.isMilestoneLeveling()) {
    return; // Level-ups are manual in milestone mode
}
// ... existing XP logic
```

- [ ] **Step 4: Add manual level-up endpoint**

```java
@PostMapping("/{id}/manual-level-up")
public String manualLevelUp(@PathVariable UUID id) {
    sheetService.incrementLevel(id);
    return "redirect:/sheet/" + id;
}
```

- [ ] **Step 5: Write test**

```java
@Test
void milestoneModeSkipsAutomaticLevelUp() {
    Campaign campaign = campaignService.create("Milestone Campaign", "");
    campaignService.setMilestoneMode(campaign.getId(), true);
    PartyMember pm = partyMemberService.create(campaign.getId(), "Test", "...", "Wizard 1", 12, 8, 2, 30, 10, 10, 10, "");
    CharacterSheet sheet = sheetService.create(pm.getId(), "...");
    sheet.setXp(9999); // Would trigger level up normally
    sheetService.checkLevelUp(sheet.getId());
    CharacterSheet after = sheetRepo.findById(sheet.getId()).get();
    assertThat(after.getClassLevels()).isEqualTo("""[{"className":"Wizard","level":1}]""");
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "feat: add milestone leveling mode — skips automatic XP-based level-ups"
```

---

## Module 7: Recharge Abilities

### Task 7.1: Parse recharge from action names (parenthetical text) + scan bonusActions

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

**Problem:** `checkRechargeAbilities()` only scans `actions` and `legendaryActions` for a structured `"recharge"` JSON key. No real seed monster has this — recharge is embedded as parenthetical text in action names: `"Petrifying Gaze (Recharge 4-6)"`. Also, `bonusActions` is never scanned.

**Fix:** Add a `parseRechargeFromName()` pass that scans action names for `(Recharge N-M)` patterns. Also scan `bonusActions` JSON.

- [ ] **Step 1: Add parseRechargeFromName helper**

```java
private List<RechargePrompt> parseRechargeFromNames(String json, List<String> recharged) {
    List<RechargePrompt> prompts = new ArrayList<>();
    try {
        var node = JSON_MAPPER.readTree(json);
        if (node.isArray()) {
            for (var item : node) {
                if (item.has("name")) {
                    String name = item.get("name").asText();
                    Matcher m = Pattern.compile("\\(Recharge\\s+(\\d+)-(\\d+)\\)", Pattern.CASE_INSENSITIVE)
                            .matcher(name);
                    if (m.find() && !recharged.contains(name)) {
                        int min = Integer.parseInt(m.group(1));
                        int max = Integer.parseInt(m.group(2));
                        prompts.add(new RechargePrompt(name, min, max));
                    }
                }
            }
        }
    } catch (Exception e) { /* ignore */ }
    return prompts;
}
```

- [ ] **Step 2: Add bonusActions scan to checkRechargeAbilities()**

```java
// After existing actions and legendaryActions scans:
String bonusJson = sb.getBonusActions();
if (bonusJson != null && !bonusJson.isEmpty()) {
    prompts.addAll(parseRechargePatterns(bonusJson, recharged));
    prompts.addAll(parseRechargeFromNames(bonusJson, recharged));
}
```

Also apply `parseRechargeFromNames` to `actions` and `legendaryActions` in addition to `parseRechargePatterns`:
```java
if (actionsJson != null && !actionsJson.isEmpty()) {
    prompts.addAll(parseRechargePatterns(actionsJson, recharged));
    prompts.addAll(parseRechargeFromNames(actionsJson, recharged));
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
git commit -m "fix: detect recharge abilities from parenthetical names and scan bonusActions"
```

---

### Task 7.2: Fix recharge Roll button to actually roll dice

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`

**Problem:** The UI's "Roll" button for recharge doesn't call the dice engine — it just submits whatever number the DM typed, silently no-ops if blank.

**Fix:** Add `hx-post` to hit a new endpoint that auto-rolls a d6, or make the roll happen client-side with JS and submit the result.

- [ ] **Step 1: Find the recharge roll button in _tracker.html**

Read `src/main/resources/templates/encounter/_tracker.html`. Locate the recharge prompt section with the roll button and number input.

- [ ] **Step 2: Replace manual input with auto-roll**

```html
<!-- OLD: -->
<input type="number" name="rollResult" min="1" max="6" />
<button type="submit">Roll</button>

<!-- NEW: -->
<button type="button"
        hx-post="@{/api/v1/encounters/{encounterId}/combatants/{id}/recharge(id=${combatant.id}, encounterId=${encounter.id})}"
        hx-vals='js:{"abilityName":"the-ability-name","rollResult":Math.floor(Math.random()*6)+1}'
        hx-swap="none">Roll d6</button>
```

Alternatively, add a server-side endpoint that rolls the d6 for the DM:
```java
@PostMapping("/{id}/combatants/{combatantId}/recharge-roll/{abilityName}")
public String rechargeRoll(@PathVariable UUID id, @PathVariable UUID combatantId,
                            @PathVariable String abilityName) {
    int roll = ThreadLocalRandom.current().nextInt(1, 7);
    encounterService.resolveRecharge(combatantId, abilityName, roll);
    // build updated tracker response
    return buildTrackerFragment(id);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html
git commit -m "fix: auto-roll d6 for recharge check instead of requiring DM to type a number"
```

---

## Module 8: Polish & Validation

### Task 8.1 + 8.3: Scope DiceRoll to Campaign, and only log to ACTIVE encounters

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRoll.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRollRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/DiceService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiController.java`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/service/DiceServiceTest.java`

> **Revised after source verification:** this was originally two separate tasks (8.1 here, 8.3 further down). Both rewrite the same `DiceService.roll()` method with incompatible signatures — 8.1's draft dropped the `encounterId` parameter and the `encounterService.logDiceRoll(...)` call that already exists in the real code, which would be a regression, and 8.3 assumed `roll()` still takes `encounterId`. Merged into one task so the method only gets rewritten once, correctly.

**Problem:** `DiceRoll` has no `campaign` FK; `DiceRollRepository.findTop20ByOrderByCreatedAtDesc()` is global, so a DM running two campaigns sees Campaign A's rolls in Campaign B's history panel. Separately (Medium finding, Task 8.3 originally), the real `DiceService.roll(String expression, UUID encounterId)` already threads `encounterId` through to `encounterService.logDiceRoll(...)` with no check that the encounter is actually `ACTIVE`.

- [ ] **Step 1: Add campaign to DiceRoll**

```java
// DiceRoll.java — import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "campaign_id", nullable = false)
private Campaign campaign;

public Campaign getCampaign() { return campaign; }
public void setCampaign(Campaign campaign) { this.campaign = campaign; }
```

- [ ] **Step 2: Add campaign-scoped query**

```java
// DiceRollRepository.java
List<DiceRoll> findTop20ByCampaignIdOrderByCreatedAtDesc(UUID campaignId);
```

- [ ] **Step 3: Update `DiceService.roll()` — add campaignId, keep encounterId, gate the encounter log on ACTIVE**

```java
private final CampaignRepository campaignRepo; // new constructor param

public DiceResult roll(String expression, UUID campaignId, UUID encounterId) {
    DiceResult result = diceEngine.roll(expression);

    DiceRoll roll = new DiceRoll();
    roll.setExpression(result.expression());
    roll.setModifier(result.modifier());
    roll.setTotal(result.total());
    roll.setAdvantage(result.advantage());
    roll.setDisadvantage(result.disadvantage());
    roll.setCampaign(campaignRepo.getReferenceById(campaignId));

    boolean encounterActive = false;
    if (encounterId != null) {
        encounterActive = encounterService.findActiveByCampaignId(campaignId)
                .map(e -> e.id().equals(encounterId))
                .orElse(false);
        roll.setEncounterId(encounterActive ? encounterId.toString() : null);
    }

    String rollsJson = serializeRolls(result);
    roll.setRolls(rollsJson);
    diceRollRepo.save(roll);

    if (encounterActive) {
        encounterService.logDiceRoll(encounterId, result.expression(), result.total(), rollsJson);
    }
    return result;
}

@Transactional(readOnly = true)
public List<DiceRoll> getHistory(UUID campaignId) {
    return diceRollRepo.findTop20ByCampaignIdOrderByCreatedAtDesc(campaignId);
}
```
`EncounterService.findActiveByCampaignId(UUID)` already exists (`EncounterService.java:221-224`) and returns the currently-`ACTIVE` encounter for a campaign, if any — reuse it instead of adding a new query.

- [ ] **Step 4: Update `DiceApiController`**

```java
@PostMapping
public ResponseEntity<?> roll(@RequestBody Map<String, Object> body) {
    // ... existing expression validation unchanged ...
    UUID campaignId = UUID.fromString((String) body.get("campaignId"));
    Object encounterIdObj = body.get("encounterId");
    UUID encounterId = encounterIdObj != null ? UUID.fromString(encounterIdObj.toString()) : null;
    DiceResult result = diceService.roll(expression, campaignId, encounterId);
    return ResponseEntity.ok(result);
}

@GetMapping("/history")
public ResponseEntity<List<DiceRoll>> history(@RequestParam UUID campaignId) {
    return ResponseEntity.ok(diceService.getHistory(campaignId));
}
```

- [ ] **Step 5: Pass campaignId from the client**

`campaignId` is already a Thymeleaf model attribute on every campaign-scoped page (`navbar.html:6`: `th:if="${campaignId != null}"`). Add near the top of the existing `<script>` block in `navbar.html`:
```html
<script th:inline="javascript">
const CURRENT_CAMPAIGN_ID = /*[[${campaignId}]]*/ null;
</script>
```
Then in both request bodies (the `data-roll` click handler around line 91, and the `diceRoller` Alpine component's `roll()` around line 149), add:
```javascript
if (CURRENT_CAMPAIGN_ID) body.campaignId = CURRENT_CAMPAIGN_ID;
```
and append `?campaignId=${CURRENT_CAMPAIGN_ID}` to the `/api/v1/roll/history` fetch in `loadHistory()`.

- [ ] **Step 6: Update tests, run, commit**

```bash
mvn test -pl . -Dtest=DiceServiceTest -Dsurefire.useFile=false
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRoll.java src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRollRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/DiceService.java src/main/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiController.java src/main/resources/templates/fragments/navbar.html src/test/java/dev/hendrikhoemberg/dmhelper/dice/service/DiceServiceTest.java
git commit -m "fix: scope DiceRoll to Campaign and only log rolls to the ACTIVE encounter"
```

---

### Task 8.2: Fix dice engine d0 validation and NumberFormatException

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngine.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngineTest.java`

- [ ] **Step 1: Add validation for d0**

```java
// After parsing sides (line 44):
if (sides == 0) {
    throw new IllegalArgumentException("Invalid dice expression: sides cannot be 0 in " + expression);
}
```

- [ ] **Step 2: Guard against huge dice counts**

```java
// After parsing count (line 42):
if (countStr != null && !countStr.isEmpty()) {
    try {
        count = Integer.parseInt(countStr);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid dice expression: " + expression);
    }
}
if (count > 1000) {
    throw new IllegalArgumentException("Dice count exceeds maximum (1000): " + expression);
}
```

- [ ] **Step 3: Write tests**

```java
@Test
void d0ThrowsValidationError() {
    assertThrows(IllegalArgumentException.class, () -> engine.roll("1d0"));
}

@Test
void hugeCountThrowsValidationError() {
    assertThrows(IllegalArgumentException.class, () -> engine.roll("99999d6"));
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngine.java src/test/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngineTest.java
git commit -m "fix: validate d0 and huge dice counts with clean validation errors"
```

---

### Task 8.3: Validate that dice rolls log into an active encounter

**Merged into Task 8.1 above** (see that task's header note) — implementing 8.1 already implements this. No separate work here.

---

### Task 8.4: Append session-log summary line on encounter end

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

In the method that transitions encounter status to `DONE`:

```java
encounter.setStatus(Encounter.Status.DONE);
logEntry(encounterId, CombatLogEntry.EntryType.SESSION_END, "",
        "{\"endedAt\":\"" + Instant.now().toString() + "\"}");
```

- [ ] **Step 1: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
git commit -m "fix: append session-log summary line when encounter ends"
```

---

### Task 8.5: Long-rest hit-dice recovery minimum of 1

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java`

**Problem:** `totalLevel / 2` with integer division means level-1 character recovers 0 hit dice.

**Fix:**
```java
int recoveredHD = Math.max(1, totalLevel / 2);
```

- [ ] **Step 1: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java
git commit -m "fix: ensure long-rest hit-dice recovery has a minimum of 1"
```

---

### Task 8.6: Auto-clear AoE templates on encounter end

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

> **Revised after source verification:** `TablePresentationService.broadcast()` (line 156) is `private` and takes **zero arguments** — the original draft's `broadcast(LiveTableState.full(...))` doesn't compile against the real signature. AoEs live in a separate `currentAoEs` field (`TablePresentationService.java:34`), already mutated by the existing public `updateAoEs(List<AoeTemplateSnapshot>)` (lines 57-59) — the same method `TablePresentationController.updateAoEs()` already calls before triggering a broadcast. No new method needed on `TablePresentationService` at all.

**Problem:** AoE templates are never cleared when an encounter ends.

**Fix:** In `EncounterService.endEncounter()` (lines 241-247 — the real DONE-transition method; there's no separate one), call the existing `updateAoEs(List.of())` then `broadcastCurrentState()`.

- [ ] **Step 1: Inject TablePresentationService** (same injection as Task 1.2 — do it once if both tasks land)

- [ ] **Step 2: Clear AoEs in endEncounter()**

```java
public EncounterDto endEncounter(UUID id) {
    Encounter e = findEntityById(id);
    e.setStatus(Encounter.Status.DONE);
    EncounterDto dto = toDto(encounterRepo.save(e));
    logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ENDED, "", "");
    tablePresentationService.updateAoEs(List.of());
    tablePresentationService.broadcastCurrentState();
    return dto;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
git commit -m "fix: clear AoE templates on the live table when an encounter ends"
```

---

### Task 8.7: Fix condition tooltips in combat tracker

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`

**Problem:** Hardcoded fallback text for Grappled/Incapacitated/Prone is missing real clauses from the app's own `srd-5.2-conditions.json`.

**Fix:** Load condition definitions from the SRD data (or a server-side endpoint) and use them for tooltip content instead of hardcoded strings. For v1, fix the hardcoded text:

```html
<!-- OLD (hardcoded): -->
<div title="Grappled: A grappled creature's speed becomes 0, and it can't benefit from any bonus to its speed.">

<!-- NEW (correct from SRD): -->
<div title="Grappled: A grappled creature's speed becomes 0, and it can't benefit from any bonus to its speed. The condition ends if the grappler is incapacitated. The condition also ends if an effect removes the grappled creature from the reach of the grappler.">
```

Update all three: Grappled (add grappler-incapacitated and removal clauses), Incapacitated (add "An incapacitated creature can't take actions or reactions" and note about surprise concentration), Prone (add attack-roll advantage/disadvantage clause).

Better approach: Add a server endpoint `GET /api/v1/library/conditions` that returns `Map<String, String>` of condition key → description, and use Alpine.js to look up tooltip text dynamically.

- [ ] **Step 1: Add condition descriptions endpoint**

```java
@GetMapping("/api/v1/library/conditions")
@ResponseBody
public Map<String, String> getConditionDescriptions() {
    return conditionRepo.findAll().stream()
            .collect(Collectors.toMap(Condition::getSourceKey, Condition::getDescription));
}
```

- [ ] **Step 2: Update template to use server data**

In `_tracker.html`, use `x-data` with Alpine.js to fetch conditions on load and populate tooltip attributes.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java
git commit -m "fix: load condition tooltip text from SRD data instead of truncated hardcoded fallback"
```

---

### Task 8.8: Quicknotes for missing target types (PARTY_MEMBER, HANDOUT, CAMPAIGN)

**Files:**
- Modify: Party member screen template
- Modify: Handout gallery template  
- Modify: Campaign dashboard template

**Problem:** Only 4 of 7 target types (§4.13) have quicknote UI. Missing: party member screen, handout gallery, campaign dashboard. Promoting an encounter-targeted quicknote to a note silently drops the "pre-linked to its target" guarantee.

**Fix:** Add `_quicknotes-strip.html` include to the three missing screens, wired with the correct `targetType` and `targetId`.

- [ ] **Step 1: Add quicknotes strip to party member detail**

Read `src/main/resources/templates/party/list.html` (or _card.html). Add:
```html
<div th:replace="~{notes/_quicknotes-strip :: strip(targetType='PARTY_MEMBER', targetId=${member.id})}"></div>
```

- [ ] **Step 2: Add to handout gallery**

```html
<div th:replace="~{notes/_quicknotes-strip :: strip(targetType='HANDOUT', targetId=${handout.id})}"></div>
```

- [ ] **Step 3: Add to campaign dashboard**

```html
<div th:replace="~{notes/_quicknotes-strip :: strip(targetType='CAMPAIGN', targetId=${campaign.id})}"></div>
```

- [ ] **Step 4: Fix quicknote-to-note promotion**

In `QuickNoteService.promoteToNote()`, when creating the note, ensure the note's back-link targets the same entity as the original quicknote.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/party/ src/main/resources/templates/handout/ src/main/resources/templates/campaigns/ src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java
git commit -m "fix: add quicknote UI to party member, handout gallery, and campaign dashboard screens"
```

---

### Task 8.9 (Low Priority): Quick win fixes

**These are single-line-of-code fixes — one commit each:**

#### 8.9.1: Add provenance header to monster/spell seed files (revised — original fix is invalid JSON)
`srd-5.2-monsters.json`/`srd-5.2-spells.json` are bare top-level JSON **arrays**, parsed via `objectMapper.readValue(is, new TypeReference<List<X>>(){})` in `SrdSeedService.java`/`SpellSeedService.java`. You cannot add a `"_provenance"` key to a bare array. Restructure both files the way `srd-5.2-conditions.json` already does it (`{"_source": ..., "_fetched": ..., "results": [...]}`, parsed via `readTree()` + `root.get("results")` in `ConditionSeedService.java`):
```bash
for f in src/main/resources/srd/srd-5.2-monsters.json src/main/resources/srd/srd-5.2-spells.json; do
  jq '{"_source": "SRD 5.2, CC-BY-4.0", "_fetched": "2026-07-09", "results": .}' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
done
```
Then update `SrdSeedService.seedIfEmpty()` and the equivalent in `SpellSeedService.java` to parse the wrapper (mirroring `ConditionSeedService`):
```java
JsonNode root = objectMapper.readTree(is);
List<SrdMonsterEntry> entries = objectMapper.convertValue(root.get("results"),
        new TypeReference<List<SrdMonsterEntry>>() {});
```
Run `mvn test` afterward — the seed-service tests seed directly from these files and will catch any format regression immediately.

#### 8.9.2: Fix status bar color count
Reduce to 2 colors as spec describes (currently 3).

#### 8.9.3: Remove stale "post-v1" comment about background-image layer
Find the comment in the stylesheet and remove or update.

#### 8.9.4: Display PC roster initiative bonus in tracker
Add `${member.initiativeBonus}` to the party summary bar template.

#### 8.9.5: Clamp bounds in setHp
```java
c.setCurrentHp(Math.max(0, Math.min(c.getMaxHp(), currentHp)));
```

#### 8.9.6: Fix wiki-link parser to resolve [[Encounter:...]] prefix (revised — wrong file identified)
`WikiLinkParser.java:31` **already** recognizes the `ENCOUNTER` prefix — no change needed there. The real gap is in `NoteService.java`: both `renderBody()` (switch at line 129) and `rebuildLinks()` (switch at line 189) handle only `NOTE`/`STATBLOCK`/`HANDOUT`/`MAP`, with no `ENCOUNTER` case in either, so an `[[Encounter:...]]` link is extracted correctly but always renders as unresolved/broken. Fix: inject `EncounterRepository` into `NoteService` (it already injects `GameMapRepository`/`HandoutRepository` the same way for the `MAP`/`HANDOUT` cases), and add an `ENCOUNTER` case to both switches, resolving by name within the note's campaign — same pattern as the existing `MAP` case, reusing `EncounterRepository.findByCampaignIdOrderByNameAsc(UUID)` (already used by `EncounterService.list()`):
```java
case "ENCOUNTER" -> {
    var encounters = encounterRepository.findByCampaignIdOrderByNameAsc(note.getCampaign().getId());
    var match = encounters.stream()
            .filter(enc -> enc.getName().equalsIgnoreCase(target.title()))
            .findFirst();
    if (match.isPresent()) {
        url = "/campaigns/" + note.getCampaign().getId() + "/encounters/" + match.get().getId();
        resolved = true;
    }
}
```
(and the analogous case in `rebuildLinks()`, setting `link.setTargetId(...)` / `yield true|false` following the existing `MAP` case's shape).

#### 8.9.7: Map switcher hides unvisited maps' names
Add a `visited` flag to `GameMap` or track visited maps in the session. Filter the switcher list.

#### 8.9.8: StatBlock name search index-friendly
Replace `LOWER(sb.name)` with exact case-sensitive match (or add a function index). For H2 compatibility, skip the `LOWER()` wrapper since H2 is typically case-insensitive by default.

#### 8.9.9: Fix CASTER_TYPES to not hardcode all Fighters/Rogues as third-casters
Make the caster type lookup check the specific subclass key (not class key). Currently inert with SRD-only content but would misfire on homebrew.

#### 8.9.10: Ensure ItemAssignment validates mutual exclusivity
Add validation in `TreasuryService` that rejects assignments with both `magicItemId` + `equipmentItemId` set.

#### 8.9.11: Decouple Handout `presented` from `dmOnly`
Change `setPresented(true)` to stop implicitly calling `setDmOnly(false)`. Add a separate "given to players" flag.

#### 8.9.12: Attunement warning — refresh all cards on change
When attunement status changes, return all attunable cards in the response (not just the toggled one) so the warning banner on other cards updates.

#### 8.9.13: Expand PIN access control tests
Add tests for all controller endpoints that mix PIN-required and public paths.

#### 8.9.14: Add real browser-driven Playwright E2E test
Replace repository/service mock steps in the smoke test with actual UI interactions (click, drag, type) so broken paths like finding #1 get caught.

---

## Execution Order

1. **Module 1** (Live Sync) — no dependencies, highest impact
2. **Module 3** (Turn Order) — core gameplay logic
3. **Module 2** (Map Primitives) — rendering outside editor
4. **Module 4** (StatBlock Resolution) — data integrity
5. **Module 5** (Import/Export) — cross-entity integrity
6. **Module 6** (Sheet Engine) — character logic
7. **Module 7** (Recharge) — feature fix
8. **Module 8** (Polish) — validation and quick wins (can be parallelized)

After each module: run `mvn test` to verify no regressions.

---

## Summary of New Entities/Fields

| Entity | New Field | Type |
|--------|-----------|------|
| `Campaign` | `milestoneLeveling` | `boolean` |
| `DiceRoll` | `campaign` | `@ManyToOne Campaign` |
| `CombatLogEntry` | `SORT_ORDER` | new EntryType enum value |
| `EncounterDto` | `lairActionAvailable` | `boolean` |
| `Encounter` | `lairActionTriggered` | `boolean` (required by Task 3.4; missing from the original draft) |
| `StatBlock` | `sourceKey` auto-generated in `createCustom()` | existing field |
| `LedgerExportDto` | `itemAssignmentRef` | `String` (new DTO field, Task 5.2) |
| `DerivedValues` | `featsRequiringManualAssignment` | `List<String>` (new record field, Task 6.1) |
