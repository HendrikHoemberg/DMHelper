# M11 Dice Roller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-side dice roller with a floating UI panel accessible from every screen, clickable roll buttons on statblocks and character sheets, and combat-log integration — all respecting the optional-first principle (§2.3.9).

**Architecture:** A stateless `DiceEngine` parses expressions and rolls via `java.util.Random`. `DiceService` orchestrates engine calls, persists history in a `DiceRoll` entity, and integrates with the combat log via `EncounterService`. A `DiceApiController` exposes `POST /api/v1/roll`. The UI is an Alpine.js floating panel embedded in the navbar (available on every page) plus inline Thymeleaf roll buttons on statblocks and character sheets.

**Tech Stack:** Java 25, Spring Boot 4.1.0, tools.jackson (Jackson 3), H2, Thymeleaf, htmx, Alpine.js, vanilla JS

**Design spec:** `docs/superpowers/specs/2026-07-09-m11-dice-roller-design.md`

---

### Task 1: DiceResult and DieRoll Records

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceResult.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/package-info.java`

- [ ] **Step 1: Create the `dice` package directory structure**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/dice`

- [ ] **Step 2: Write the `DiceResult` record with inner `DieRoll` record**

```java
package dev.hendrikhoemberg.dmhelper.dice;

import java.util.List;

public record DiceResult(
        String expression,
        List<DieRoll> rolls,
        int modifier,
        int total,
        boolean advantage,
        boolean disadvantage
) {
    public record DieRoll(String die, List<Integer> values) {}
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS (or compile errors only for missing dependencies, which is fine since DiceResult has no deps)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/
git commit -m "feat(m11): add DiceResult and DieRoll records"
```

---

### Task 2: DiceEngine — Expression Parser and Roller

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngine.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngineTest.java`

- [ ] **Step 1: Write the `DiceEngineTest` (failing tests first)**

```java
package dev.hendrikhoemberg.dmhelper.dice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiceEngineTest {

    private final DiceEngine engine = new DiceEngine();

    @Test
    void shouldRollSingleDie() {
        DiceResult result = engine.roll("d20");
        assertThat(result.expression()).isEqualTo("d20");
        assertThat(result.rolls()).hasSize(1);
        assertThat(result.rolls().get(0).die()).isEqualTo("d20");
        assertThat(result.rolls().get(0).values()).hasSize(1);
        assertThat(result.rolls().get(0).values().get(0)).isBetween(1, 20);
        assertThat(result.modifier()).isZero();
        assertThat(result.total()).isBetween(1, 20);
        assertThat(result.advantage()).isFalse();
        assertThat(result.disadvantage()).isFalse();
    }

    @Test
    void shouldRollMultipleDice() {
        DiceResult result = engine.roll("2d6");
        assertThat(result.rolls()).hasSize(1);
        assertThat(result.rolls().get(0).die()).isEqualTo("d6");
        assertThat(result.rolls().get(0).values()).hasSize(2);
        assertThat(result.rolls().get(0).values().get(0)).isBetween(1, 6);
        assertThat(result.rolls().get(0).values().get(1)).isBetween(1, 6);
        assertThat(result.modifier()).isZero();
        assertThat(result.total()).isBetween(2, 12);
    }

    @Test
    void shouldRollWithPositiveModifier() {
        DiceResult result = engine.roll("2d6+4");
        assertThat(result.rolls()).hasSize(1);
        assertThat(result.rolls().get(0).die()).isEqualTo("d6");
        assertThat(result.rolls().get(0).values()).hasSize(2);
        assertThat(result.modifier()).isEqualTo(4);
        assertThat(result.total()).isBetween(6, 16);
    }

    @Test
    void shouldRollWithNegativeModifier() {
        DiceResult result = engine.roll("1d4-1");
        assertThat(result.modifier()).isEqualTo(-1);
        assertThat(result.total()).isBetween(0, 3);
    }

    @Test
    void shouldRollWithAdvantage() {
        DiceResult result = engine.roll("d20 adv");
        assertThat(result.advantage()).isTrue();
        assertThat(result.disadvantage()).isFalse();
        assertThat(result.rolls().get(0).values()).hasSize(2);
        int v1 = result.rolls().get(0).values().get(0);
        int v2 = result.rolls().get(0).values().get(1);
        assertThat(result.total()).isEqualTo(Math.max(v1, v2));
    }

    @Test
    void shouldRollWithDisadvantage() {
        DiceResult result = engine.roll("d20 dis");
        assertThat(result.advantage()).isFalse();
        assertThat(result.disadvantage()).isTrue();
        assertThat(result.rolls().get(0).values()).hasSize(2);
        int v1 = result.rolls().get(0).values().get(0);
        int v2 = result.rolls().get(0).values().get(1);
        assertThat(result.total()).isEqualTo(Math.min(v1, v2));
    }

    @Test
    void shouldRollBareDieWithoutCount() {
        DiceResult result = engine.roll("d8");
        assertThat(result.rolls().get(0).die()).isEqualTo("d8");
        assertThat(result.rolls().get(0).values()).hasSize(1);
        assertThat(result.total()).isBetween(1, 8);
    }

    @Test
    void shouldRollLargeCount() {
        DiceResult result = engine.roll("5d10+3");
        assertThat(result.rolls().get(0).values()).hasSize(5);
        assertThat(result.total()).isBetween(8, 53);
    }

    @Test
    void shouldRollPercentileDie() {
        DiceResult result = engine.roll("d100");
        assertThat(result.rolls().get(0).die()).isEqualTo("d100");
        assertThat(result.total()).isBetween(1, 100);
    }

    @Test
    void shouldRejectEmptyExpression() {
        assertThatThrownBy(() -> engine.roll(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldRejectNullExpression() {
        assertThatThrownBy(() -> engine.roll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldRejectInvalidExpression() {
        assertThatThrownBy(() -> engine.roll("notdice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldRejectDoubleSignModifier() {
        assertThatThrownBy(() -> engine.roll("2d6+-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldAcceptTypedInteger() {
        DiceResult result = engine.roll("15");
        assertThat(result.expression()).isEqualTo("typed: 15");
        assertThat(result.rolls()).isEmpty();
        assertThat(result.modifier()).isZero();
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.advantage()).isFalse();
        assertThat(result.disadvantage()).isFalse();
    }

    @Test
    void shouldAcceptTypedZero() {
        DiceResult result = engine.roll("0");
        assertThat(result.expression()).isEqualTo("typed: 0");
        assertThat(result.total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"d20", "d6", "2d6", "3d8+2", "1d4-1", "d100"})
    void shouldProduceValidResults(String expression) {
        DiceResult result = engine.roll(expression);
        assertThat(result.expression()).isEqualTo(expression);
        assertThat(result.total()).isPositive();
        for (DiceResult.DieRoll roll : result.rolls()) {
            assertThat(roll.die()).startsWith("d");
            assertThat(roll.values()).isNotEmpty();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=DiceEngineTest -q`
Expected: FAIL — `DiceEngine` class not found

- [ ] **Step 3: Write `DiceEngine.java`**

```java
package dev.hendrikhoemberg.dmhelper.dice;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiceEngine {

    private static final Pattern DICE_PATTERN = Pattern.compile(
            "^(\\d*)d(\\d+)([+-]\\d+)?(\\s+(adv|dis))?$");
    private static final Pattern TYPED_PATTERN = Pattern.compile("^\\d+$");

    private final Random random;

    public DiceEngine() {
        this.random = new Random();
    }

    public DiceResult roll(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }

        String trimmed = expression.trim();

        if (TYPED_PATTERN.matcher(trimmed).matches()) {
            int value = Integer.parseInt(trimmed);
            return new DiceResult("typed: " + value, List.of(), 0, value, false, false);
        }

        Matcher matcher = DICE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }

        String countStr = matcher.group(1);
        int count = (countStr == null || countStr.isEmpty()) ? 1 : Integer.parseInt(countStr);
        String sidesStr = matcher.group(2);
        int sides = Integer.parseInt(sidesStr);
        String modifierStr = matcher.group(3);
        int modifier = 0;
        if (modifierStr != null) {
            modifier = Integer.parseInt(modifierStr);
        }
        String advDisGroup = matcher.group(5);
        boolean advantage = "adv".equals(advDisGroup);
        boolean disadvantage = "dis".equals(advDisGroup);

        int rollsToMake = (advantage || disadvantage) ? 2 : count;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < rollsToMake; i++) {
            values.add(random.nextInt(sides) + 1);
        }

        int total;
        if (advantage) {
            total = values.stream().mapToInt(Integer::intValue).max().orElse(0) + modifier;
        } else if (disadvantage) {
            total = values.stream().mapToInt(Integer::intValue).min().orElse(0) + modifier;
        } else {
            total = values.stream().mapToInt(Integer::intValue).sum() + modifier;
        }

        List<DiceResult.DieRoll> dieRolls = List.of(new DiceResult.DieRoll("d" + sides, List.copyOf(values)));
        return new DiceResult(trimmed, dieRolls, modifier, total, advantage, disadvantage);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=DiceEngineTest -q`
Expected: PASS (all 16 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngine.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngineTest.java
git commit -m "feat(m11): add DiceEngine with expression parser and roller"
```

---

### Task 3: DiceRoll JPA Entity and Repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRoll.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRollRepository.java`

- [ ] **Step 1: Write `DiceRoll.java` entity**

```java
package dev.hendrikhoemberg.dmhelper.dice.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dice_roll")
public class DiceRoll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String expression;

    @Column(columnDefinition = "CLOB")
    private String rolls;

    private int modifier;

    @Column(nullable = false)
    private int total;

    private boolean advantage;

    private boolean disadvantage;

    @Column(length = 36)
    private String encounterId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getRolls() { return rolls; }
    public void setRolls(String rolls) { this.rolls = rolls; }

    public int getModifier() { return modifier; }
    public void setModifier(int modifier) { this.modifier = modifier; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public boolean isAdvantage() { return advantage; }
    public void setAdvantage(boolean advantage) { this.advantage = advantage; }

    public boolean isDisadvantage() { return disadvantage; }
    public void setDisadvantage(boolean disadvantage) { this.disadvantage = disadvantage; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Write `DiceRollRepository.java`**

```java
package dev.hendrikhoemberg.dmhelper.dice.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiceRollRepository extends JpaRepository<DiceRoll, UUID> {
    List<DiceRoll> findTop20ByOrderByCreatedAtDesc();
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/
git commit -m "feat(m11): add DiceRoll entity and repository"
```

---

### Task 4: Integrate DiceEngine into EncounterService, Add DICE_ROLL Combat Log Type

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatLogEntry.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`

- [ ] **Step 1: Add `DICE_ROLL` to the `EntryType` enum**

In `CombatLogEntry.java`, add `DICE_ROLL` to the enum at line 24 (before `NOTE`):

```java
    public enum EntryType {
        INITIATIVE_SET, TURN_START, TURN_END, ROUND_ADVANCE,
        DAMAGE, HEAL, TEMP_HP,
        CONDITION_ADDED, CONDITION_REMOVED, CONDITION_TICKED,
        CONCENTRATION_SET, CONCENTRATION_LOST, CONCENTRATION_CHECK,
        RECHARGE,
        LEGENDARY_ACTION, LEGENDARY_RESISTANCE,
        DEFEATED, REVIVED,
        COMBATANT_ADDED, COMBATANT_REMOVED, COMBATANT_REORDERED,
        GROUP_SPLIT, LAIR_ACTION,
        ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED,
        DICE_ROLL, NOTE
    }
```

- [ ] **Step 2: Add `DICE_ROLL` to the no-op cases in `replayEntry()`**

In `EncounterService.java`, add `DICE_ROLL` to the existing no-op case at line 1043:

From:
```java
            case CONDITION_TICKED, TURN_END, LAIR_ACTION, NOTE,
                 ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED,
                 COMBATANT_ADDED, COMBATANT_REMOVED -> {
```
To:
```java
            case CONDITION_TICKED, TURN_END, LAIR_ACTION, NOTE, DICE_ROLL,
                 ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED,
                 COMBATANT_ADDED, COMBATANT_REMOVED -> {
```

- [ ] **Step 3: Add `DiceEngine` as a dependency of `EncounterService` and update `autoRollInitiative()`**

In `EncounterService.java`, add the import:
```java
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
```

Add the field (after line 56 — after `CombatDifficultyCalculator calculator`):
```java
    private final DiceEngine diceEngine;
```

Add `DiceEngine diceEngine` as the last constructor parameter (after `CombatDifficultyCalculator calculator` on line 63) and assign it:
```java
        this.diceEngine = diceEngine;
```

In the `autoRollInitiative()` method (lines 395–416), replace the raw `new java.util.Random()` usage with `diceEngine`:

**Replace** (line 403):
```java
                int roll = new java.util.Random().nextInt(20) + 1;
```
**With**:
```java
                int roll = diceEngine.roll("d20").total();
```

- [ ] **Step 4: Add public `logDiceRoll` method to `EncounterService`**

Add immediately after the existing `logEntry` method (after line 818):

```java
    public void logDiceRoll(UUID encounterId, String expression, int total, String rollsJson) {
        String payload;
        try {
            payload = JSON_MAPPER.writeValueAsString(Map.of(
                    "expression", expression,
                    "total", total,
                    "rolls", rollsJson));
        } catch (Exception e) {
            payload = "{\"expression\":\"" + expression + "\",\"total\":" + total + "}";
        }
        logEntry(encounterId, CombatLogEntry.EntryType.DICE_ROLL, "", payload);
    }
```

- [ ] **Step 5: Verify compilation**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Run existing tests to ensure no regressions**

Run: `./mvnw test -pl . -Dtest=EncounterServiceTest -q`
Expected: PASS (existing tests still pass)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/
git commit -m "feat(m11): add DiceEngine to EncounterService, update auto-roll, add DICE_ROLL combat log entry type and logDiceRoll"
```

---

### Task 5: DiceService — Orchestrator

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/DiceService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/service/DiceServiceTest.java`

- [ ] **Step 1: Write `DiceServiceTest.java`**

```java
package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiceServiceTest {

    @Mock private DiceRollRepository diceRollRepo;
    @Mock private EncounterService encounterService;
    @Mock private DiceEngine diceEngine;

    private DiceService diceService;

    @BeforeEach
    void setUp() {
        diceService = new DiceService(diceEngine, diceRollRepo, encounterService);
    }

    @Test
    void shouldRollAndSaveHistory() {
        UUID encounterId = UUID.randomUUID();
        DiceResult expectedResult = new DiceResult("2d6+4",
                List.of(new DiceResult.DieRoll("d6", List.of(3, 5))), 4, 12, false, false);
        when(diceEngine.roll("2d6+4")).thenReturn(expectedResult);

        DiceResult result = diceService.roll("2d6+4", encounterId);

        assertThat(result).isEqualTo(expectedResult);

        ArgumentCaptor<DiceRoll> captor = ArgumentCaptor.forClass(DiceRoll.class);
        verify(diceRollRepo).save(captor.capture());
        DiceRoll saved = captor.getValue();
        assertThat(saved.getExpression()).isEqualTo("2d6+4");
        assertThat(saved.getTotal()).isEqualTo(12);
        assertThat(saved.getEncounterId()).isEqualTo(encounterId.toString());

        verify(encounterService).logDiceRoll(eq(encounterId), eq("2d6+4"), eq(12), anyString());
    }

    @Test
    void shouldRollWithoutEncounterAndNotLog() {
        DiceResult expectedResult = new DiceResult("d20",
                List.of(new DiceResult.DieRoll("d20", List.of(17))), 0, 17, false, false);
        when(diceEngine.roll("d20")).thenReturn(expectedResult);

        DiceResult result = diceService.roll("d20", null);

        assertThat(result).isEqualTo(expectedResult);
        verify(diceRollRepo).save(any());
        verify(encounterService, never()).logDiceRoll(any(), anyString(), anyInt(), anyString());
    }

    @Test
    void shouldHandleTypedInput() {
        DiceResult expectedResult = new DiceResult("typed: 15", List.of(), 0, 15, false, false);
        when(diceEngine.roll("15")).thenReturn(expectedResult);

        DiceResult result = diceService.roll("15", null);

        assertThat(result.expression()).isEqualTo("typed: 15");
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.rolls()).isEmpty();

        ArgumentCaptor<DiceRoll> captor = ArgumentCaptor.forClass(DiceRoll.class);
        verify(diceRollRepo).save(captor.capture());
        assertThat(captor.getValue().getExpression()).isEqualTo("typed: 15");
    }

    @Test
    void shouldGetHistory() {
        DiceRoll roll1 = new DiceRoll();
        roll1.setExpression("d20");
        roll1.setTotal(17);
        DiceRoll roll2 = new DiceRoll();
        roll2.setExpression("2d6+4");
        roll2.setTotal(12);
        when(diceRollRepo.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of(roll2, roll1));

        List<DiceRoll> history = diceService.getHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getExpression()).isEqualTo("2d6+4");
        assertThat(history.get(1).getExpression()).isEqualTo("d20");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=DiceServiceTest -q`
Expected: FAIL — `DiceService` class not found

- [ ] **Step 3: Write `DiceService.java`**

```java
package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DiceService {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    private final DiceEngine diceEngine;
    private final DiceRollRepository diceRollRepo;
    private final EncounterService encounterService;

    public DiceService(DiceEngine diceEngine, DiceRollRepository diceRollRepo,
                       EncounterService encounterService) {
        this.diceEngine = diceEngine;
        this.diceRollRepo = diceRollRepo;
        this.encounterService = encounterService;
    }

    public DiceResult roll(String expression, UUID encounterId) {
        DiceResult result = diceEngine.roll(expression);

        DiceRoll roll = new DiceRoll();
        roll.setExpression(result.expression());
        roll.setModifier(result.modifier());
        roll.setTotal(result.total());
        roll.setAdvantage(result.advantage());
        roll.setDisadvantage(result.disadvantage());
        roll.setEncounterId(encounterId != null ? encounterId.toString() : null);

        try {
            roll.setRolls(JSON_MAPPER.writeValueAsString(
                    result.rolls().stream().map(r -> {
                        try {
                            return JSON_MAPPER.writeValueAsString(r);
                        } catch (Exception e) {
                            return "{}";
                        }
                    }).toList()));
        } catch (Exception e) {
            roll.setRolls("[]");
        }

        diceRollRepo.save(roll);

        if (encounterId != null) {
            encounterService.logDiceRoll(encounterId, result.expression(), result.total(),
                    roll.getRolls());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<DiceRoll> getHistory() {
        return diceRollRepo.findTop20ByOrderByCreatedAtDesc();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=DiceServiceTest -q`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/dice/service/
git commit -m "feat(m11): add DiceService orchestrator"
```

---

### Task 6: DiceApiController — REST Endpoints

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiControllerTest.java`

- [ ] **Step 1: Write `DiceApiControllerTest.java`**

```java
package dev.hendrikhoemberg.dmhelper.dice.web;

import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.service.DiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiceApiController.class)
class DiceApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DiceService diceService;

    @Test
    void shouldRollValidExpression() throws Exception {
        DiceResult result = new DiceResult("2d6+4",
                List.of(new DiceResult.DieRoll("d6", List.of(3, 5))), 4, 12, false, false);
        when(diceService.roll(eq("2d6+4"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"2d6+4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression").value("2d6+4"))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.modifier").value(4))
                .andExpect(jsonPath("$.rolls[0].die").value("d6"))
                .andExpect(jsonPath("$.rolls[0].values[0]").value(3))
                .andExpect(jsonPath("$.rolls[0].values[1]").value(5));
    }

    @Test
    void shouldRollAdvantage() throws Exception {
        DiceResult result = new DiceResult("d20 adv",
                List.of(new DiceResult.DieRoll("d20", List.of(7, 14))), 0, 14, true, false);
        when(diceService.roll(eq("d20 adv"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20 adv\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advantage").value(true))
                .andExpect(jsonPath("$.total").value(14));
    }

    @Test
    void shouldRollDisadvantage() throws Exception {
        DiceResult result = new DiceResult("d20 dis",
                List.of(new DiceResult.DieRoll("d20", List.of(14, 3))), 0, 3, false, true);
        when(diceService.roll(eq("d20 dis"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20 dis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disadvantage").value(true))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void shouldRollTypedInput() throws Exception {
        DiceResult result = new DiceResult("typed: 15", List.of(), 0, 15, false, false);
        when(diceService.roll(eq("15"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression").value("typed: 15"))
                .andExpect(jsonPath("$.total").value(15))
                .andExpect(jsonPath("$.rolls").isArray())
                .andExpect(jsonPath("$.rolls").isEmpty());
    }

    @Test
    void shouldReturnBadRequestForInvalidExpression() throws Exception {
        when(diceService.roll(eq("notdice"), isNull()))
                .thenThrow(new IllegalArgumentException("Invalid dice expression: notdice"));

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"notdice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid dice expression: notdice"));
    }

    @Test
    void shouldReturnBadRequestForEmptyExpression() throws Exception {
        when(diceService.roll(eq(""), isNull()))
                .thenThrow(new IllegalArgumentException("Expression is required"));

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetHistory() throws Exception {
        DiceRoll roll = new DiceRoll();
        roll.setExpression("2d6+4");
        roll.setTotal(12);
        when(diceService.getHistory()).thenReturn(List.of(roll));

        mockMvc.perform(get("/api/v1/roll/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expression").value("2d6+4"))
                .andExpect(jsonPath("$[0].total").value(12));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=DiceApiControllerTest -q`
Expected: FAIL — `DiceApiController` not found

- [ ] **Step 3: Write `DiceApiController.java`**

```java
package dev.hendrikhoemberg.dmhelper.dice.web;

import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.service.DiceService;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roll")
public class DiceApiController {

    private final DiceService diceService;

    public DiceApiController(DiceService diceService) {
        this.diceService = diceService;
    }

    @PostMapping
    public ResponseEntity<?> roll(@RequestBody Map<String, Object> body) {
        String expression = (String) body.get("expression");
        if (expression == null || expression.isBlank()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Expression is required");
            problem.setInstance(URI.create("/api/v1/roll"));
            problem.setTitle("Invalid roll request");
            return ResponseEntity.badRequest().body(problem);
        }
        Object encounterIdObj = body.get("encounterId");
        UUID encounterId = encounterIdObj != null ? UUID.fromString(encounterIdObj.toString()) : null;

        try {
            DiceResult result = diceService.roll(expression, encounterId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
            problem.setInstance(URI.create("/api/v1/roll"));
            problem.setTitle("Invalid dice expression");
            return ResponseEntity.badRequest().body(problem);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<DiceRoll>> history() {
        return ResponseEntity.ok(diceService.getHistory());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=DiceApiControllerTest -q`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/dice/web/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/dice/web/
git commit -m "feat(m11): add DiceApiController REST endpoints"
```

---

### Task 7: CSS — Dice Panel and Roll Button Styles

**Files:**
- Modify: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Add CSS styles to `app.css`**

Append the following to the end of `src/main/resources/static/css/app.css`:

```css
/* ── Dice Roller Panel ── */
.dice-panel {
    position: fixed;
    right: 0;
    top: 0;
    bottom: 0;
    width: 320px;
    background: var(--color-surface);
    border-left: 1px solid var(--color-border);
    z-index: 100;
    display: flex;
    flex-direction: column;
    transform: translateX(0);
    transition: transform 0.2s ease;
    box-shadow: -4px 0 16px rgba(0, 0, 0, 0.4);
}
.dice-panel.closed {
    transform: translateX(100%);
}

.dice-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--space-md) var(--space-lg);
    border-bottom: 1px solid var(--color-border);
    font-size: var(--text-lg);
    font-weight: 600;
}

.dice-panel-body {
    flex: 1;
    overflow-y: auto;
    padding: var(--space-md) var(--space-lg);
}

.dice-input-row {
    display: flex;
    gap: var(--space-sm);
    margin-bottom: var(--space-md);
}

.dice-input-row input[type="text"] {
    flex: 1;
    padding: var(--space-sm) var(--space-md);
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    color: var(--color-text);
    font-family: var(--font-mono, monospace);
    font-size: var(--text-md);
}

.dice-input-row input[type="text"]:focus {
    outline: none;
    border-color: var(--color-accent);
}

.dice-input-row button {
    padding: var(--space-sm) var(--space-md);
    background: var(--color-accent);
    border: none;
    border-radius: var(--radius);
    color: #fff;
    font-weight: 600;
    cursor: pointer;
    white-space: nowrap;
}
.dice-input-row button:hover {
    background: var(--color-accent-hover);
}

.dice-options {
    display: flex;
    gap: var(--space-md);
    margin-bottom: var(--space-md);
    font-size: var(--text-sm);
}

.dice-options label {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    color: var(--color-text-muted);
    cursor: pointer;
}

.dice-result {
    padding: var(--space-md);
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    margin-bottom: var(--space-md);
    text-align: center;
}

.dice-result-total {
    font-size: 2rem;
    font-weight: 700;
    font-family: var(--font-mono, monospace);
    color: var(--color-accent);
}
.dice-result-total.high {
    color: #4ade80;
}
.dice-result-total.low {
    color: #f87171;
}

.dice-result-details {
    font-size: var(--text-sm);
    color: var(--color-text-muted);
    margin-top: var(--space-sm);
}

.dice-history-section h4 {
    font-size: var(--text-sm);
    font-weight: 600;
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: var(--space-sm);
}

.dice-history-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--space-sm) 0;
    border-bottom: 1px solid var(--color-border);
    font-size: var(--text-sm);
}
.dice-history-item:last-child {
    border-bottom: none;
}

.dice-history-item .expr {
    color: var(--color-text-muted);
    font-family: var(--font-mono, monospace);
}
.dice-history-item .value {
    font-weight: 700;
    font-family: var(--font-mono, monospace);
    min-width: 2em;
    text-align: right;
}
.dice-history-item .value.high {
    color: #4ade80;
}
.dice-history-item .value.low {
    color: #f87171;
}

/* ── Dice Roller Toggle Button ── */
.dice-toggle-btn {
    cursor: pointer;
    background: none;
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    padding: var(--space-xs) var(--space-sm);
    color: var(--color-text-muted);
    font-size: var(--text-md);
}
.dice-toggle-btn:hover {
    background: var(--color-bg);
    color: var(--color-text);
}
.dice-toggle-btn.active {
    background: var(--color-accent);
    border-color: var(--color-accent);
    color: #fff;
}

/* ── Inline Roll Buttons ── */
.roll-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    background: none;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    color: var(--color-text-muted);
    cursor: pointer;
    font-size: 14px;
    line-height: 1;
    flex-shrink: 0;
    margin-left: 4px;
    vertical-align: middle;
}
.roll-btn:hover {
    background: var(--color-accent);
    border-color: var(--color-accent);
    color: #fff;
}

/* ── Roll Result Tooltip ── */
.roll-tooltip {
    position: absolute;
    z-index: 50;
    padding: var(--space-sm) var(--space-md);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    font-size: var(--text-sm);
    font-weight: 600;
    font-family: var(--font-mono, monospace);
    color: var(--color-accent);
    box-shadow: var(--shadow);
    white-space: nowrap;
    pointer-events: none;
}

/* ── DM Mode ── */
body.dm-mode-off .dice-panel,
body.dm-mode-off .dice-toggle-btn { display: none; }
```

- [ ] **Step 2: Verify the app starts and serves static CSS**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev & sleep 8 && curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/css/app.css && kill %1 2>/dev/null`
Expected: `200`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(m11): add dice roller panel and roll button CSS styles"
```

---

### Task 8: Alpine.js Dice Roller Panel Fragment

**Files:**
- Create: `src/main/resources/templates/fragments/_dice-roller.html`

- [ ] **Step 1: Write `_dice-roller.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="dice-roller"
     x-data="diceRoller()"
     x-show="open"
     class="dice-panel"
     :class="{ closed: !open }"
     @keydown.escape.window="open = false">

    <div class="dice-panel-header">
        <span>&#x1F3B2; Dice Roller</span>
        <button class="btn btn-ghost" style="font-size: var(--text-lg); line-height: 1; padding: 0 var(--space-xs);"
                @click="open = false">&times;</button>
    </div>

    <div class="dice-panel-body">
        <div class="dice-input-row">
            <input type="text"
                   x-model="expression"
                   placeholder="e.g. 2d6+4 or typed value"
                   @keydown.enter="roll()"
                   :disabled="loading"
                   autofocus>
            <button @click="roll()" :disabled="loading || !expression">
                <span x-show="!loading">Roll</span>
                <span x-show="loading">...</span>
            </button>
        </div>

        <div class="dice-options">
            <label>
                <input type="checkbox" x-model="advantage"
                       @change="if(advantage) disadvantage = false">
                Adv
            </label>
            <label>
                <input type="checkbox" x-model="disadvantage"
                       @change="if(disadvantage) advantage = false">
                Dis
            </label>
        </div>

        <div class="dice-result" x-show="result" x-transition>
            <div class="dice-result-total"
                 :class="{ high: result && result.disadvantage == false && result.advantage == false && result.total >= 15 && result.expression && result.expression.startsWith('d20'),
                           low: result && result.disadvantage == false && result.advantage == false && result.total <= 5 && result.expression && result.expression.startsWith('d20') }"
                 x-text="result ? result.total : ''"></div>
            <div class="dice-result-details" x-show="result && result.rolls && result.rolls.length > 0">
                <template x-for="dieRoll in result.rolls" :key="dieRoll.die">
                    <div>
                        <span x-text="dieRoll.die + ': '"></span>
                        <span x-text="'[' + dieRoll.values.join('] [') + ']'"></span>
                    </div>
                </template>
                <div x-show="result.modifier != 0">
                    <span x-text="result.modifier >= 0 ? '+ ' + result.modifier : '- ' + Math.abs(result.modifier)"></span>
                </div>
                <div x-show="result.advantage" style="color: #4ade80;">(advantage — higher of 2)</div>
                <div x-show="result.disadvantage" style="color: #f87171;">(disadvantage — lower of 2)</div>
            </div>
            <div class="dice-result-details" x-show="result && (!result.rolls || result.rolls.length == 0)">
                typed input
            </div>
        </div>

        <div class="dice-history-section">
            <h4>Recent Rolls</h4>
            <template x-if="history.length === 0">
                <div style="color: var(--color-text-muted); font-size: var(--text-sm);">No rolls yet</div>
            </template>
            <template x-for="item in history" :key="item.id">
                <div class="dice-history-item">
                    <span class="expr" x-text="item.expression"></span>
                    <span class="value"
                          :class="{ high: item.total >= 15 && item.expression && item.expression.startsWith('d20') && !item.advantage && !item.disadvantage,
                                    low: item.total <= 5 && item.expression && item.expression.startsWith('d20') && !item.advantage && !item.disadvantage }"
                          x-text="item.total"></span>
                </div>
            </template>
        </div>
    </div>
</div>
</html>
```

- [ ] **Step 2: Verify compilation (Thymeleaf template — no build needed)**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/fragments/_dice-roller.html
git commit -m "feat(m11): add Alpine.js dice roller panel fragment"
```

---

### Task 9: Integrate into Navbar — Toggle Button + Alpine Component

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`

- [ ] **Step 1: Add the dice roller toggle button and embed the fragment in `navbar.html`**

Add before the `</nav>` closing tag, right after the `</div>` closing the `navbar-right` div. Also add the Alpine component registration script at the bottom.

The full modified `navbar.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<nav class="navbar" th:fragment="navbar">
    <div style="display: flex; align-items: center; gap: var(--space-lg);">
        <a href="/campaigns" class="navbar-brand">DMHelper</a>
        <nav th:if="${campaignId != null}" style="display: flex; gap: var(--space-md);">
            <a th:href="@{/campaigns/{id}(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Campaign</a>
            <a th:href="@{/campaigns/{id}/party(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Party</a>
            <a th:href="@{/campaigns/{id}/maps(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Maps</a>
            <a th:href="@{/campaigns/{id}/encounters(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Encounters</a>
            <a th:href="@{/campaigns/{id}/handouts(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Handouts</a>
            <a th:href="@{/campaigns/{id}/sheets(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Sheets</a>
            <a th:href="@{/campaigns/{id}/notes(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Notes</a>
            <a th:href="@{/campaigns/{id}/treasury(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Treasury</a>
            <a th:href="@{/campaigns/{id}/ledger(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Ledger</a>
            <a th:href="@{/campaigns/{id}/calendar(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Calendar</a>
        </nav>
        <nav th:unless="${campaignId != null}" style="display: flex; gap: var(--space-md);">
            <a href="/campaigns" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Campaigns</a>
            <a href="/library" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Library</a>
            <a href="/library/about" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">About</a>
        </nav>
    </div>
    <div class="navbar-right">
        <button class="dice-toggle-btn" id="diceToggle"
                :class="{ active: open }"
                @click="open = !open"
                title="Toggle Dice Roller (Ctrl+R)">&#x1F3B2;</button>
        <a href="/player" target="_blank" class="btn btn-ghost" style="font-size: var(--text-sm);"
           title="Open player view in a new tab">Player View</a>
        <button class="btn btn-ghost" style="font-size: var(--text-sm);" id="qrToggle"
                onclick="document.getElementById('qrPopover').classList.toggle('hidden')"
                title="Show QR code for player view">QR</button>
        <div id="qrPopover" class="hidden" style="position: absolute; top: 100%; right: 60px;
                background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
                padding: var(--space-md); z-index: 200; box-shadow: var(--shadow);">
            <img th:src="@{/qr/player-view}" alt="Player View QR" width="180" height="180"
                 style="display: block;">
            <div style="text-align: center; margin-top: var(--space-xs); font-size: var(--text-sm); color: var(--color-text-muted);">
                Scan to join
            </div>
        </div>
        <label class="dm-toggle">
            <input type="checkbox" id="dmModeCheckbox"
                   onclick="document.body.classList.toggle('dm-mode-off')">
            DM Mode
        </label>
        <span class="pin-display" id="pinDisplay"
              th:text="${pinDisplay}">PIN: -----</span>
    </div>
</nav>

<th:block th:replace="~{fragments/_dice-roller :: dice-roller}"></th:block>

<script>
document.addEventListener('alpine:init', () => {
    Alpine.data('diceRoller', () => ({
        open: false,
        expression: '',
        advantage: false,
        disadvantage: false,
        result: null,
        history: [],
        loading: false,
        _encounterId: null,

        async init() {
            await this.loadHistory();
            window.addEventListener('tracker-encounter-state', (e) => {
                const encId = e.detail?.encounter?.id || null;
                this._encounterId = encId;
                document.body.dataset.activeEncounterId = encId || '';
            });
        },

        async roll() {
            let expr = this.expression.trim();
            if (!expr) return;

            if (this.advantage && !expr.toLowerCase().includes('adv')) {
                expr = expr + ' adv';
            } else if (this.disadvantage && !expr.toLowerCase().includes('dis')) {
                expr = expr + ' dis';
            }

            this.loading = true;
            try {
                const body = { expression: expr };
                if (this._encounterId) body.encounterId = this._encounterId;

                const resp = await fetch('/api/v1/roll', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });

                if (!resp.ok) {
                    const err = await resp.json();
                    alert(err.detail || 'Roll failed');
                    return;
                }

                this.result = await resp.json();
                window.dispatchEvent(new CustomEvent('dice-roll-result', {
                    detail: this.result
                }));
                await this.loadHistory();
                this.expression = '';
                this.advantage = false;
                this.disadvantage = false;
            } catch (e) {
                alert('Roll failed: ' + e.message);
            } finally {
                this.loading = false;
            }
        },

        async loadHistory() {
            try {
                const resp = await fetch('/api/v1/roll/history');
                if (resp.ok) this.history = await resp.json();
            } catch (e) {
                // silently ignore
            }
        }
    }));
});

document.addEventListener('keydown', (e) => {
    if (e.ctrlKey && e.key === 'r') {
        e.preventDefault();
        const root = document.querySelector('[x-data="diceRoller()"]');
        if (root && root.__x) {
            const data = root.__x.getUnobservedData();
            data.open = !data.open;
            root.__x.updateElements(root);
        }
    }
});
</script>
</html>
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html
git commit -m "feat(m11): integrate dice roller toggle button and panel into navbar"
```

---

### Task 10: Statblock Roll Buttons

**Files:**
- Modify: `src/main/resources/templates/library/_statblock-renderer.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java` (add `attackBonus` and `damage` to parsed action maps)

- [ ] **Step 1: Add enrichment to `enrichStatBlock` in `LibraryController.java`**

The `enrichStatBlock` method at line 347 calls `parseJsonArray()` for each action type, which returns `List<Map<String, String>>` (deserialized JSON maps with `name` and `description` keys). We add a helper to extract `attackBonus` and `damageExpr` from action names using regex.

**Add imports** at the top of `LibraryController.java` (near the existing `java.util.*` imports):

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;
```

**Modify `enrichStatBlock`** (lines 347-354) to call the enrichment wrapper:

```java
    private void enrichStatBlock(StatBlock sb) {
        sb.setTraitsParsed(enrichWithRolls(parseJsonArray(sb.getTraits())));
        sb.setActionsParsed(enrichWithRolls(parseJsonArray(sb.getActions())));
        sb.setBonusActionsParsed(enrichWithRolls(parseJsonArray(sb.getBonusActions())));
        sb.setReactionsParsed(enrichWithRolls(parseJsonArray(sb.getReactions())));
        sb.setLegendaryActionsParsed(enrichWithRolls(parseJsonArray(sb.getLegendaryActions())));
        sb.setLairActionsParsed(enrichWithRolls(parseJsonArray(sb.getLairActions())));
    }
```

**Add the `enrichWithRolls` helper** after the existing `parseJsonArray` method (after line 363):

```java
    private static final Pattern ATK_PATTERN = Pattern.compile("\\+(\\d+) to hit");
    private static final Pattern DMG_PATTERN = Pattern.compile("\\((\\d+d\\d+[-+]?\\d*)\\)");

    private List<Map<String, String>> enrichWithRolls(List<Map<String, String>> parsed) {
        for (Map<String, String> entry : parsed) {
            String name = entry.getOrDefault("name", "");
            Matcher atkM = ATK_PATTERN.matcher(name);
            if (atkM.find()) {
                entry.put("attackBonus", atkM.group(1));
            }
            Matcher dmgM = DMG_PATTERN.matcher(name);
            if (dmgM.find()) {
                entry.put("damageExpr", dmgM.group(1));
            }
        }
        return parsed;
    }
```

- [ ] **Step 2: Add roll buttons to each action in `_statblock-renderer.html`**

Modify the action rendering blocks. For each `sb-ability` div in the actions, bonus actions, reactions, legendary actions, and lair actions sections, add roll buttons after the name. The pattern:

```html
<!-- ACTIONS -->
<th:block th:if="${sb.actions != null and !sb.actions.isBlank()}">
    <h3>Actions</h3>
    <div class="sb-ability" th:each="action : ${sb.actionsParsed}">
        <div class="sb-ability-name">
            <span th:text="${action.name}">Action Name</span>
            <th:block th:if="${action.attackBonus != null}">
                <button class="roll-btn" th:attr="data-roll='d20+${action.attackBonus}'"
                        type="button" title="Attack roll (d20+${action.attackBonus})">d20</button>
            </th:block>
            <th:block th:if="${action.damageExpr != null}">
                <button class="roll-btn" th:attr="data-roll=${action.damageExpr}"
                        type="button" title="Damage roll (${action.damageExpr})">&#x1F3B2;</button>
            </th:block>
        </div>
        <div class="sb-ability-text" th:text="${action.description}">Description.</div>
    </div>
</th:block>
```

Apply the same pattern to:
- Traits (line 38-41)
- Bonus Actions (line 52-57)
- Reactions (line 60-65)
- Legendary Actions (line 68-75)
- Lair Actions (line 77-82)

Each section adds the same `<th:block>` with `attackBonus` and `damageExpr` checks after the name.

- [ ] **Step 3: Add global click handler for `[data-roll]` buttons in the navbar script**

In `navbar.html`, add this inside the existing `<script>` tag, before the Alpine.init handler:

```javascript
document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-roll]');
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();

    const expr = btn.dataset.roll;
    const tooltip = document.createElement('div');
    tooltip.className = 'roll-tooltip';
    tooltip.textContent = '...';
    document.body.appendChild(tooltip);

    const rect = btn.getBoundingClientRect();
    tooltip.style.left = rect.left + 'px';
    tooltip.style.top = (rect.top - 32) + 'px';

    try {
        const body = { expression: expr };
        const activeEnc = document.body.dataset.activeEncounterId || null;
        if (activeEnc) body.encounterId = activeEnc;

        const resp = await fetch('/api/v1/roll', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (!resp.ok) {
            tooltip.textContent = 'Error';
            setTimeout(() => tooltip.remove(), 1500);
            return;
        }

        const result = await resp.json();
        tooltip.textContent = expr + ' = ' + result.total;
        window.dispatchEvent(new CustomEvent('dice-roll-result', { detail: result }));
    } catch (e) {
        tooltip.textContent = 'Error';
    }

    setTimeout(() => tooltip.remove(), 3000);
});
```

- [ ] **Step 4: Verify compilation and run a quick test**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/library/_statblock-renderer.html \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java \
        src/main/resources/templates/fragments/navbar.html
git commit -m "feat(m11): add roll buttons to statblock actions"
```

---

### Task 11: Character Sheet Roll Buttons

**Files:**
- Modify: `src/main/resources/templates/sheet/detail.html`
- Modify: `src/main/resources/templates/sheet/_ability-scores.html`
- Modify: `src/main/resources/templates/sheet/_derived-stats.html`

- [ ] **Step 1: Add ability check roll buttons in `_ability-scores.html`**

The existing template iterates `sheet.abilityScores()` and computes the modifier inline as `(entry.value - 10) / 2`. Add a roll button next to the mod span inside the loop:

In `_ability-scores.html`, find each `sheet-ability-mod` span inside the `th:each="entry : ${sheet.abilityScores()}"` loop (line 8). The current line:
```html
            <span class="sheet-ability-mod" th:text="${(entry.value - 10) / 2}">+4</span>
```

Replace with (adding the roll button after the span, using the same inline computation):
```html
            <span class="sheet-ability-mod" th:text="${(entry.value - 10) / 2}">+4</span>
            <button class="roll-btn" th:attr="data-roll='d20+${(entry.value - 10) / 2}'"
                    type="button" th:title="${entry.key.toUpperCase()} + ' check'">d20</button>
```

This pattern works for all six abilities since the loop naturally handles each one.

- [ ] **Step 2: Add saving throw roll buttons in `detail.html`**

In the saving throws section (around line 54), each save entry shows:
```html
<div><dt>STR</dt><dd>+7</dd></div>
```

Add a roll button after each `<dd>`:
```html
<div><dt>STR</dt><dd>+7 <button class="roll-btn" th:attr="data-roll='d20+${sheet.derivedValues().saveStr()}'" type="button" title="STR save">d20</button></dd></div>
```

Apply for all six saves (STR, DEX, CON, INT, WIS, CHA).

- [ ] **Step 3: Add skill roll buttons in `detail.html`**

In the skills section (around line 66), each skill entry shows:
```html
<div class="sheet-skill" th:each="entry : ${sheet.derivedValues().skillBonuses()}">
    <span class="sheet-skill-name" th:text="${entry.key}">perception</span>
    <span class="sheet-skill-bonus" th:text="(${entry.value} >= 0 ? '+' : '') + ${entry.value}">+4</span>
</div>
```

Add a roll button after each bonus:
```html
<div class="sheet-skill" th:each="entry : ${sheet.derivedValues().skillBonuses()}">
    <span class="sheet-skill-name" th:text="${entry.key}">perception</span>
    <span class="sheet-skill-bonus" th:text="(${entry.value} >= 0 ? '+' : '') + ${entry.value}">+4</span>
    <button class="roll-btn" th:attr="data-roll='d20+${entry.value}'" type="button" th:title="${entry.key} + ' check'">d20</button>
</div>
```

- [ ] **Step 4: Add initiative and spell attack roll buttons in `detail.html`**

In the derived stats / spellcasting sections:
```html
<dt>Init</dt><dd>+2 <button class="roll-btn" th:attr="data-roll='d20+${sheet.derivedValues().initiativeBonus()}'" type="button" title="Initiative">d20</button></dd>
```

```html
<dt>Spell Attack Bonus</dt><dd>+5 <button class="roll-btn" th:attr="data-roll='d20+${sheet.derivedValues().spellAttackBonus()}'" type="button" title="Spell attack">d20</button></dd>
```

- [ ] **Step 5: Verify compilation**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/sheet/
git commit -m "feat(m11): add roll buttons to character sheet saves, skills, abilities, initiative, and spell attack"
```

---

### Task 12: End-to-End Manual Verification

**No files changed.** Run the app and verify the full M11 feature.

- [ ] **Step 1: Start the application**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

- [ ] **Step 2: Verify the floating panel**

1. Open `http://localhost:8081` in a browser
2. Click the dice icon (&#x1F3B2;) in the navbar — panel should slide in from the right
3. Type `2d6+4` and press Enter — should show total between 6 and 16
4. Type `d20` and check the Adv box, press Enter — should roll with advantage
5. Type `d20` and check the Dis box, press Enter — should roll with disadvantage
6. Type `15` and press Enter — should show "typed input" with total 15
7. History should show all recent rolls
8. Press `Esc` — panel should close
9. Press `Ctrl+R` — panel should toggle

- [ ] **Step 3: Verify DM Mode integration**

1. Toggle DM Mode off (the DM Mode checkbox in navbar)
2. Dice toggle button should disappear
3. Dice panel should disappear (if it was open)
4. Toggle DM Mode back on — toggle button returns

- [ ] **Step 4: Verify statblock roll buttons**

1. Navigate to Library → any monster statblock
2. Actions should show `d20` buttons next to attack actions (e.g., "+4 to hit" → d20+4 button)
3. Click — tooltip should show the roll result
4. Open dice panel — click a statblock button — result should also appear in panel history

- [ ] **Step 5: Verify character sheet roll buttons**

1. Navigate to Campaign → Sheets → any character with a sheet
2. Ability scores should have `d20` buttons next to each modifier
3. Saving throws should have `d20` buttons
4. Skills should have `d20` buttons
5. Initiative should have a `d20` button
6. Spell attack bonus should have a `d20` button
7. Click each — should roll correctly with the right modifier

- [ ] **Step 6: Verify combat log integration**

1. Create and activate an encounter with some combatants
2. Open the dice panel and roll `2d6+4`
3. Check the combat log — should contain a `DICE_ROLL` entry with the expression and total
4. Undo the last action — the `DICE_ROLL` entry should be removed
5. End the encounter, then roll again — no combat log entry should appear

- [ ] **Step 7: Verify keyboard shortcut**

1. On any page, press `Ctrl+R` — the dice panel should toggle open/closed

- [ ] **Step 8: Commit any fixes if needed**

If any issues found during manual testing, fix and commit. Otherwise, mark M11 complete:

Run: `./mvnw clean test -pl .`
Expected: all tests pass

```bash
git commit -m "feat(m11): mark M11 as complete — Dice Roller"
```
