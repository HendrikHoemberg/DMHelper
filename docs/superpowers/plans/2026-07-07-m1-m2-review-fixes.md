# M1/M2 Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the six defects found reviewing M1 (walking skeleton) and M2 (statblock library & party roster) against `SPEC.md`, and resolve one spec-level API-convention ambiguity.

**Architecture:** Small, independent fixes across the existing feature modules (`campaign`, `library`, `party`, `common`) plus a build-time Python generator. Each task is TDD where a runtime behavior changes, and ends with a green `./mvnw test` and a commit.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA + H2 (file mode), Thymeleaf + htmx, Jackson 3 (`tools.jackson`), JUnit 5 + AssertJ + Mockito, Python 3 (build-time SRD generator).

## Global Constraints

- Spring Boot **4.1.0** / Java **25**. Never downgrade a managed dependency to make an example compile.
- Jackson **3** only: databind/core under `tools.jackson`; annotations remain under `com.fasterxml.jackson.annotation` (this is correct for Jackson 3 — do not "fix" it).
- Use `spring-boot-starter-webmvc` (not `-web`) and the granular Boot 4 test starters already in `pom.xml`; do not add `spring-boot-starter-test`.
- No Node/npm, no CDN, no new frontend libraries. Vendored JS stays in `static/vendor/` with `VENDOR.md`.
- Entity IDs are UUIDs everywhere. htmx routes return Thymeleaf fragments; scriptable JSON lives under `/api/v1`.
- Test runner: `./mvnw test`. Single class: `./mvnw test -Dtest=ClassName`. Single method: `./mvnw test -Dtest='ClassName#method'`.
- Data dir: `~/.dmhelper/data` (H2). Handout files (future): `~/.dmhelper/files`. Backups: `~/.dmhelper/backups`.

## Findings covered

| # | Finding | Task |
|---|---|---|
| 1 | All 339 spells have empty `school`, `components`, `higherLevel`, and `concentration` always false (generator maps wrong open5e v2 fields) | Task 1 |
| 2 | Party member `active` flag dropped on import | Task 2 |
| 3 | Custom statblock `sourceKey` dropped on import | Task 2 |
| 4 | Flagship round-trip test only covers empty campaigns (never exercises party/statblocks) | Task 2 |
| A | `/api/v1` JSON boundary undefined; `srd-keys` sits on an unprefixed htmx controller | Task 3 |
| 6 | Startup backup copies only the H2 data dir, not the handout files dir | Task 4 |
| 7a | Dead `hpValue`/`speedValue` params threaded through statblock create/update | Task 5 |
| 5 | No browser auto-open on `java -jar` startup | Task 6 |

**Explicitly deferred (documented, not fixed here):**
- `ddl-auto=update` migration strategy — an accepted, spec-documented tradeoff (§2.1/§3). Task 1's one-off migration bean exercises the prescribed `@PostConstruct` mechanism, partially validating it. No change.
- `FAIL_ON_UNKNOWN_PROPERTIES=true` on import — currently correct (strict validation). It only tensions with externally-authored JSON once JSON Schemas land in M8; revisit then.
- `server.port=8081` vs the spec's illustrative `:8080` examples — left as-is. Task 6 derives the browser URL from `server.port` so no port value is ever hardcoded, which removes the underlying risk without changing the developer's chosen port.

---

### Task 1: Fix SRD spell seed data (school / components / higherLevel / concentration)

The generator `bin/generate-srd-spells.py` reads open5e **v1** field names (`school_name`, a `components` list, `higher_level_desc`, `requires_concentration`) but hits the **v2** API, which uses `school` (nested object), boolean `verbal`/`somatic`/`material` + `material_specified`, `higher_level`, and `concentration`. Every one of those four fields is therefore empty/false across all 339 spells. Fix the generator, regenerate the JSON, add a one-off migration so already-seeded databases repopulate, and lock it with a regression test.

**Files:**
- Modify: `bin/generate-srd-spells.py:33-58` (the per-spell mapping loop)
- Regenerate: `src/main/resources/srd/srd-5.2-spells.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpellRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/SpellReseedMigration.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/SpellSeedServiceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/SpellReseedMigrationTest.java`

**Interfaces:**
- Produces: `SpellRepository.countWithSchool()` → `long` (spells with a non-blank school).
- Produces: `SpellReseedMigration.reseedIfStale()` → `void` (deletes all spells iff data is stale, i.e. `countWithSchool() == 0` while `count() > 0`).
- Consumes: existing `SpellSeedService.seedIfEmpty()` (repopulates when the spell table is empty).

- [ ] **Step 1: Write the failing regression test for seeded spell fields**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/SpellSeedServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SpellSeedService.class)
class SpellSeedServiceTest {

    @Autowired private SpellRepository repository;
    @Autowired private SpellSeedService service;

    @BeforeEach
    void seed() {
        service.seedIfEmpty();
    }

    @Test
    void shouldSeedAllSpellsWithASchool() {
        assertThat(repository.count()).isEqualTo(339);
        long blankSchool = repository.findAll().stream()
                .filter(s -> s.getSchool() == null || s.getSchool().isBlank())
                .count();
        assertThat(blankSchool).isZero();
    }

    @Test
    void shouldPopulateFireballFields() {
        Spell fireball = repository.findAll().stream()
                .filter(s -> s.getName().equals("Fireball"))
                .findFirst().orElseThrow();
        assertThat(fireball.getSchool()).isEqualTo("Evocation");
        assertThat(fireball.getComponents()).contains("V").contains("S").contains("M");
        assertThat(fireball.getHigherLevel()).isNotBlank();
        assertThat(fireball.isConcentration()).isFalse();
    }

    @Test
    void shouldHaveConcentrationSpells() {
        long concentrationCount = repository.findAll().stream()
                .filter(Spell::isConcentration)
                .count();
        assertThat(concentrationCount).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails against the stale JSON**

Run: `./mvnw test -Dtest=SpellSeedServiceTest`
Expected: FAIL — `shouldSeedAllSpellsWithASchool` reports 339 blank schools; `shouldPopulateFireballFields` gets `""` for school; `shouldHaveConcentrationSpells` gets 0.

- [ ] **Step 3: Fix the generator's field mapping**

In `bin/generate-srd-spells.py`, replace the entire per-spell loop body (lines 33-58, from `for s in results:` through the closing `})` of `entries.append`) with:

```python
        for s in results:
            # open5e v2 exposes components as booleans + a material description,
            # not a list; school is a nested object; higher-level and
            # concentration use different keys than v1.
            comp_parts = []
            if s.get("verbal"):
                comp_parts.append("V")
            if s.get("somatic"):
                comp_parts.append("S")
            if s.get("material"):
                comp_parts.append("M")
            components = ", ".join(comp_parts)
            material_desc = s.get("material_specified", "") or ""
            if components and material_desc and s.get("material"):
                components = f"{components} ({material_desc})"

            school = s.get("school") or {}
            school_name = school.get("name", "") if isinstance(school, dict) else str(school)

            entries.append({
                "sourceKey": s["key"].removeprefix("srd-2024_"),
                "name": s["name"],
                "level": s.get("level", 0),
                "school": school_name,
                "castingTime": s.get("casting_time", ""),
                "range": s.get("range_text", ""),
                "components": components,
                "duration": s.get("duration", ""),
                "description": s.get("desc", ""),
                "higherLevel": s.get("higher_level", ""),
                "ritual": s.get("ritual", False),
                "concentration": s.get("concentration", False),
            })
```

- [ ] **Step 4: Regenerate the spell JSON (requires internet to reach open5e)**

Run: `python3 bin/generate-srd-spells.py`
Expected: prints per-page counts and `Wrote 339 spells to .../srd-5.2-spells.json`.

Then verify the data is now populated:

Run:
```bash
python3 - <<'EOF'
import json
s=json.load(open("src/main/resources/srd/srd-5.2-spells.json"))
assert len(s)==339, len(s)
assert sum(1 for x in s if not x["school"])==0, "blank schools remain"
assert sum(1 for x in s if x["concentration"])>0, "no concentration spells"
fb=[x for x in s if x["name"]=="Fireball"][0]
assert fb["school"]=="Evocation", fb["school"]
assert "M" in fb["components"], fb["components"]
assert fb["higherLevel"], "empty higherLevel"
print("OK", "school sample:", fb["school"], "| components:", fb["components"])
EOF
```
Expected: `OK school sample: Evocation | components: V, S, M (...)`

- [ ] **Step 5: Run the regression test and confirm it passes**

Run: `./mvnw test -Dtest=SpellSeedServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Add the `countWithSchool` repository query**

In `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpellRepository.java`, add the import and method:

```java
import org.springframework.data.jpa.repository.Query;
```

```java
    @Query("select count(s) from Spell s where s.school is not null and s.school <> ''")
    long countWithSchool();
```

- [ ] **Step 7: Write the failing migration test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/SpellReseedMigrationTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpellReseedMigrationTest {

    @Autowired private SpellRepository repository;

    private Spell spell(String key, String school) {
        Spell s = new Spell();
        s.setSourceKey(key);
        s.setName(key);
        s.setLevel(1);
        s.setSchool(school);
        return s;
    }

    @Test
    void clearsStaleSpellsWithNoSchool() {
        repository.save(spell("a", ""));
        repository.save(spell("b", ""));
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isZero();
    }

    @Test
    void keepsHealthySpellsThatHaveSchools() {
        repository.save(spell("a", "Evocation"));
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void noOpOnEmptyDatabase() {
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isZero();
    }
}
```

- [ ] **Step 8: Run the migration test and confirm it fails to compile**

Run: `./mvnw test -Dtest=SpellReseedMigrationTest`
Expected: FAIL — compilation error, `SpellReseedMigration` does not exist.

- [ ] **Step 9: Create the one-off migration bean**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/SpellReseedMigration.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ONE-OFF DATA MIGRATION (SPEC §3). The initial spell seed shipped with empty
 * school/components/higherLevel and concentration=false due to a generator bug
 * that read open5e v1 field names against the v2 API. Clearing the stale spell
 * rows forces {@code SpellSeedService.seedIfEmpty()} (fired on ApplicationReadyEvent,
 * after this @PostConstruct) to repopulate from the corrected JSON.
 *
 * The staleness guard (no spell has a school) makes this a no-op once the
 * corrected data is in place. DELETE THIS CLASS in the following release.
 */
@Component
public class SpellReseedMigration {

    private static final Logger log = LoggerFactory.getLogger(SpellReseedMigration.class);

    private final SpellRepository spellRepository;

    public SpellReseedMigration(SpellRepository spellRepository) {
        this.spellRepository = spellRepository;
    }

    @PostConstruct
    public void reseedIfStale() {
        long total = spellRepository.count();
        if (total == 0) {
            return; // fresh DB — the seeder will populate correct data
        }
        if (spellRepository.countWithSchool() == 0) {
            spellRepository.deleteAllInBatch();
            log.info("Cleared {} stale spells (no school populated) — will reseed", total);
        }
    }
}
```

- [ ] **Step 10: Run the migration test and confirm it passes**

Run: `./mvnw test -Dtest=SpellReseedMigrationTest`
Expected: PASS (3 tests).

- [ ] **Step 11: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests pass (58 total: prior 52 + 6 new).

- [ ] **Step 12: Commit**

```bash
git add bin/generate-srd-spells.py src/main/resources/srd/srd-5.2-spells.json \
  src/main/java/dev/hendrikhoemberg/dmhelper/library/data/SpellRepository.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/common/config/SpellReseedMigration.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/library/service/SpellSeedServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/SpellReseedMigrationTest.java
git commit -m "fix: populate spell school/components/higherLevel/concentration from open5e v2

Generator read v1 field names against the v2 API, leaving all 339 spells
with empty school/components/higherLevel and concentration=false. Fix the
mapping, regenerate, and add a one-off migration to reseed stale databases.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Preserve `active` and `sourceKey` on import + flagship round-trip test

`CampaignService.importFromJson` re-creates party members via `partyMemberService.create(...)` (which hard-codes `active=true`) and custom statblocks via `statBlockService.createCustom(...)` without ever restoring `sourceKey` — both are present in the export. The existing `shouldRoundTripCampaign` test only covers name+description, so the loss is invisible. Add a real round-trip integration test (the §6 flagship) that exercises party + statblocks, watch it fail, then fix the two lines.

**Files:**
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java:111-146`

**Interfaces:**
- Consumes: `CampaignService.exportToJson(UUID)`, `CampaignService.importFromJson(String)`, `PartyMemberService.create(...)`, `PartyMemberService.setActive(UUID, boolean)`, `PartyMemberService.findByCampaignId(UUID)`, `StatBlockService.createCustom(...)`, `StatBlockService.findByCampaignId(UUID)`, `StatBlockRepository.save(...)`.

- [ ] **Step 1: Write the failing round-trip fidelity test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CampaignService.class, PartyMemberService.class, StatBlockService.class})
class CampaignImportExportRoundTripTest {

    @Autowired private CampaignService campaignService;
    @Autowired private PartyMemberService partyMemberService;
    @Autowired private StatBlockService statBlockService;
    @Autowired private StatBlockRepository statBlockRepository;

    @Test
    void roundTripPreservesPartyActiveAndStatblockSourceKey() {
        Campaign c = campaignService.create("Round Trip", "full graph");

        partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
                16, 38, 4, 30, 17, 12, 11, "darkvision");
        PartyMember retired = partyMemberService.create(c.getId(), "Borin", "Ben", "Fighter 5",
                18, 45, 1, 30, 12, 10, 10, "retired PC");
        partyMemberService.setActive(retired.getId(), false);

        StatBlock sb = statBlockService.createCustom(c.getId(), "Amber Knight", "5", "Humanoid",
                18, "75 (10d8 + 30)", "30 ft.",
                16, 12, 16, 10, 12, 14,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 12", "Common");
        sb.setSourceKey("amber-knight");
        statBlockRepository.save(sb);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<PartyMember> members = partyMemberService.findByCampaignId(imported.getId());
        assertThat(members).hasSize(2);
        PartyMember reBorin = members.stream()
                .filter(m -> m.getCharacterName().equals("Borin")).findFirst().orElseThrow();
        assertThat(reBorin.isActive()).isFalse();

        List<StatBlock> blocks = statBlockService.findByCampaignId(imported.getId());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getSourceKey()).isEqualTo("amber-knight");
        assertThat(blocks.get(0).getName()).isEqualTo("Amber Knight");
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails on both fields**

Run: `./mvnw test -Dtest=CampaignImportExportRoundTripTest`
Expected: FAIL — `reBorin.isActive()` is `true` (expected false), and/or the statblock `sourceKey` is `null` (expected `"amber-knight"`).

- [ ] **Step 3: Restore `active` on party import**

In `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`, replace the party import loop (currently lines 111-120):

```java
        if (dto.party() != null) {
            for (var pmDto : dto.party()) {
                partyMemberService.create(saved.getId(),
                        pmDto.characterName(), pmDto.playerName(),
                        pmDto.classAndLevel(), pmDto.ac(), pmDto.maxHp(),
                        pmDto.initiativeBonus(), pmDto.speed(),
                        pmDto.passivePerception(), pmDto.passiveInsight(),
                        pmDto.passiveInvestigation(), pmDto.notes());
            }
        }
```

with:

```java
        if (dto.party() != null) {
            for (var pmDto : dto.party()) {
                var member = partyMemberService.create(saved.getId(),
                        pmDto.characterName(), pmDto.playerName(),
                        pmDto.classAndLevel(), pmDto.ac(), pmDto.maxHp(),
                        pmDto.initiativeBonus(), pmDto.speed(),
                        pmDto.passivePerception(), pmDto.passiveInsight(),
                        pmDto.passiveInvestigation(), pmDto.notes());
                if (!pmDto.active()) {
                    partyMemberService.setActive(member.getId(), false);
                }
            }
        }
```

- [ ] **Step 4: Restore `sourceKey` on statblock import**

In the same file, in the statblock import loop, add a `setSourceKey` line alongside the other post-create setters (immediately after the `if (sbDto.size() != null) sb.setSize(sbDto.size());` line):

```java
                if (sbDto.sourceKey() != null) sb.setSourceKey(sbDto.sourceKey());
```

- [ ] **Step 5: Run the round-trip test and confirm it passes**

Run: `./mvnw test -Dtest=CampaignImportExportRoundTripTest`
Expected: PASS.

- [ ] **Step 6: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
git commit -m "fix: preserve party active flag and statblock sourceKey on import

Import re-created members as active and dropped custom sourceKeys. Add the
flagship export->import round-trip test (party + statblocks) that caught it.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Establish the `/api/v1` JSON boundary (relocate the SRD-key catalog)

The spec (§5, §4.1) puts scriptable JSON under `/api/v1` and lists `GET /api/v1/library/srd-keys`, but the endpoint currently lives as a `@ResponseBody` method on the htmx `LibraryController` at `/library/statblocks/srd-keys`. Move it to a dedicated `@RestController` under `/api/v1/library`, which cleanly opens the JSON-API surface the map islands (M3+) will extend, and matches the spec's canonical path.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java` (remove `srdKeys()` + its imports if now unused)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java` (remove `shouldListSrdKeys`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/library/srd-keys` → `List<String>` (JSON array of SRD source keys, sorted).
- Consumes: `StatBlockService.findAll()`, `StatBlock.getSource()`, `StatBlock.getSourceKey()`.

- [ ] **Step 1: Write the failing test for the new endpoint**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.SpellSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.SrdSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryApiController.class)
class LibraryApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService service;
    @MockitoBean private SrdSeedService srdSeedService;
    @MockitoBean private SpellSeedService spellSeedService;

    private StatBlock srd(String key) {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.SRD);
        sb.setSourceKey(key);
        return sb;
    }

    private StatBlock custom() {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.CUSTOM);
        sb.setSourceKey("should-not-appear");
        return sb;
    }

    @Test
    void listsSortedSrdKeysOnly() throws Exception {
        when(service.findAll()).thenReturn(List.of(srd("goblin"), custom(), srd("aboleth")));
        mockMvc.perform(get("/api/v1/library/srd-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("aboleth"))
                .andExpect(jsonPath("$[1]").value("goblin"))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails to compile**

Run: `./mvnw test -Dtest=LibraryApiControllerTest`
Expected: FAIL — `LibraryApiController` does not exist.

- [ ] **Step 3: Create the JSON API controller**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JSON API for the library, under the canonical {@code /api/v1} boundary (SPEC §5).
 * htmx fragment routes stay on {@link LibraryController}; scriptable JSON lives here.
 */
@RestController
@RequestMapping("/api/v1/library")
public class LibraryApiController {

    private final StatBlockService service;

    public LibraryApiController(StatBlockService service) {
        this.service = service;
    }

    @GetMapping("/srd-keys")
    public List<String> srdKeys() {
        return service.findAll().stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.SRD)
                .map(StatBlock::getSourceKey)
                .sorted()
                .toList();
    }
}
```

- [ ] **Step 4: Remove the old endpoint from `LibraryController`**

In `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`, delete the SRD-key method and its section comment (currently lines 220-230):

```java
    // ------- SRD key catalog -------

    @GetMapping("/statblocks/srd-keys")
    @ResponseBody
    public List<String> srdKeys() {
        return service.findAll().stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.SRD)
                .map(StatBlock::getSourceKey)
                .sorted()
                .toList();
    }
```

Leave the remaining imports as-is (`java.util.*` and the `@GetMapping` etc. are still used by other methods; `@ResponseBody` was only used here but comes from the wildcard `org.springframework.web.bind.annotation.*` import, so no import edit is needed).

- [ ] **Step 5: Remove the stale test from `LibraryControllerTest`**

In `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java`, delete the `shouldListSrdKeys` test (currently lines 148-153):

```java
    @Test
    void shouldListSrdKeys() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/library/statblocks/srd-keys"))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 6: Run both affected test classes**

Run: `./mvnw test -Dtest='LibraryApiControllerTest,LibraryControllerTest'`
Expected: PASS — new endpoint test green; `LibraryControllerTest` green with one fewer test.

- [ ] **Step 7: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java
git commit -m "refactor: move SRD-key catalog to /api/v1/library/srd-keys

Establishes the canonical /api/v1 JSON boundary (SPEC §5) that the map
islands will extend; htmx fragment routes stay on LibraryController.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Include the handout files directory in startup backups

`StartupBackupRunner` copies only `~/.dmhelper/data`; §6 requires the H2 DB **and** the handout files directory (`~/.dmhelper/files`). Files don't exist until M6, but the runner should copy both trees when present so the safety net is complete before handouts land. Restructure the snapshot to hold `data/` and `files/` subdirectories.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunner.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunnerTest.java`

**Interfaces:**
- Produces: `StartupBackupRunner(String userHome)` constructor (unchanged signature) and `run(ApplicationArguments)`; snapshot layout becomes `~/.dmhelper/backups/dmhelper-<ts>/{data,files}/...`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunnerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StartupBackupRunnerTest {

    @Test
    void backsUpBothDataAndFilesDirectories(@TempDir Path home) throws Exception {
        Path dmData = Files.createDirectories(home.resolve(".dmhelper/data"));
        Files.writeString(dmData.resolve("dmhelper.mv.db"), "db-bytes");
        Path dmFiles = Files.createDirectories(home.resolve(".dmhelper/files"));
        Files.writeString(dmFiles.resolve("portrait.png"), "img-bytes");

        new StartupBackupRunner(home.toString()).run(null);

        Path backups = home.resolve(".dmhelper/backups");
        try (Stream<Path> snapshots = Files.list(backups)) {
            Path snapshot = snapshots.findFirst().orElseThrow();
            assertThat(snapshot.resolve("data/dmhelper.mv.db")).exists();
            assertThat(snapshot.resolve("files/portrait.png")).exists();
        }
    }

    @Test
    void skipsMissingFilesDirectory(@TempDir Path home) throws Exception {
        Path dmData = Files.createDirectories(home.resolve(".dmhelper/data"));
        Files.writeString(dmData.resolve("dmhelper.mv.db"), "db-bytes");

        new StartupBackupRunner(home.toString()).run(null);

        Path backups = home.resolve(".dmhelper/backups");
        try (Stream<Path> snapshots = Files.list(backups)) {
            Path snapshot = snapshots.findFirst().orElseThrow();
            assertThat(snapshot.resolve("data/dmhelper.mv.db")).exists();
            assertThat(Files.exists(snapshot.resolve("files"))).isFalse();
        }
    }

    @Test
    void keepsAtMostTenBackups(@TempDir Path home) throws Exception {
        Path dmData = Files.createDirectories(home.resolve(".dmhelper/data"));
        Files.writeString(dmData.resolve("dmhelper.mv.db"), "db-bytes");
        Path backups = Files.createDirectories(home.resolve(".dmhelper/backups"));
        for (int i = 0; i < 12; i++) {
            Files.createDirectory(backups.resolve("dmhelper-2020010" + String.format("%01d", i % 10) + "-00000" + i % 10 + "-old" + i));
        }
        // Trim pre-seeded fakes to a clean known set, then run once.
        try (Stream<Path> old = Files.list(backups)) {
            List<Path> toDelete = old.toList();
            for (int i = 0; i < toDelete.size(); i++) {
                Files.delete(toDelete.get(i));
            }
        }
        for (int i = 0; i < 12; i++) {
            Files.createDirectory(backups.resolve("dmhelper-2020-" + String.format("%02d", i)));
        }

        new StartupBackupRunner(home.toString()).run(null);

        try (Stream<Path> remaining = Files.list(backups)) {
            assertThat(remaining.filter(Files::isDirectory).count()).isLessThanOrEqualTo(10);
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./mvnw test -Dtest=StartupBackupRunnerTest`
Expected: FAIL — `backsUpBothDataAndFilesDirectories` cannot find `data/dmhelper.mv.db` (current code copies data files to the snapshot root, not under `data/`) and `files/portrait.png` is never copied.

- [ ] **Step 3: Restructure the runner to back up both trees**

In `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunner.java`, add a `filesDir` field and copy both directories into named subfolders.

Replace the field declarations and constructor (currently lines 24-30):

```java
    private final Path dataDir;
    private final Path backupDir;

    public StartupBackupRunner(@Value("${user.home}") String userHome) {
        this.dataDir = Path.of(userHome, ".dmhelper", "data");
        this.backupDir = Path.of(userHome, ".dmhelper", "backups");
    }
```

with:

```java
    private final Path dataDir;
    private final Path filesDir;
    private final Path backupDir;

    public StartupBackupRunner(@Value("${user.home}") String userHome) {
        this.dataDir = Path.of(userHome, ".dmhelper", "data");
        this.filesDir = Path.of(userHome, ".dmhelper", "files");
        this.backupDir = Path.of(userHome, ".dmhelper", "backups");
    }
```

Replace the `run` method body (currently lines 33-58):

```java
    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(dataDir)) {
            log.info("No data directory at {} — skipping startup backup", dataDir);
            return;
        }

        try {
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(FMT);
            Path snapshotDir = backupDir.resolve("dmhelper-" + timestamp);
            Files.createDirectory(snapshotDir);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir)) {
                for (Path file : stream) {
                    Files.copy(file, snapshotDir.resolve(file.getFileName()));
                }
            }

            log.info("Database backup created at {}", snapshotDir);

            rotateBackups();
        } catch (IOException e) {
            log.error("Failed to create startup backup", e);
        }
    }
```

with:

```java
    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(dataDir)) {
            log.info("No data directory at {} — skipping startup backup", dataDir);
            return;
        }

        try {
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(FMT);
            Path snapshotDir = backupDir.resolve("dmhelper-" + timestamp);
            Files.createDirectory(snapshotDir);

            copyTree(dataDir, snapshotDir.resolve("data"));
            if (Files.isDirectory(filesDir)) {
                copyTree(filesDir, snapshotDir.resolve("files"));
            }

            log.info("Backup created at {}", snapshotDir);

            rotateBackups();
        } catch (IOException e) {
            log.error("Failed to create startup backup", e);
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }
```

(The `import java.nio.file.*;` and `java.util.stream.Stream` imports already present cover `Files.walk`/`Stream`; `DirectoryStream` is no longer used but the wildcard import makes removing it optional.)

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./mvnw test -Dtest=StartupBackupRunnerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunner.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/StartupBackupRunnerTest.java
git commit -m "fix: back up handout files directory alongside the H2 database

SPEC §6 requires copying both ~/.dmhelper/data and ~/.dmhelper/files;
snapshots now hold data/ and files/ subdirectories.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Remove the dead `hpValue` / `speedValue` parameters

`StatBlockService.createCustom`/`updateCustom` take `String hpValue, String speedValue` that are never read; `LibraryController` binds them from `@RequestParam` and passes them, `CampaignService` passes `null, null`, and no template submits them. Delete them for a clean signature. Pure refactor — the existing tests, updated to the new signature, are the safety net.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java` (both method signatures)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java` (remove 2 `@RequestParam` + 2 call args in both `create` and `update`)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` (drop the `null, null` args)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockServiceTest.java` (call sites)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java` (mock matcher + params)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` (drop the `null, null` hp/speed-value line from its `createCustom` call — this test was written in Task 2 against the pre-removal 28-arg signature)

**Note on arg counts:** `createCustom`/`updateCustom` currently take **28** parameters; after removing `hpValue`/`speedValue` they take **26**.

- [ ] **Step 1: Drop the params from `StatBlockService`**

In `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java`, remove the `String hpValue, String speedValue,` line from **both** `createCustom` (line 66) and `updateCustom` (line 106) signatures. The method bodies never reference them, so no body change is needed. After edit, both signatures read:

```java
    public StatBlock createCustom(UUID campaignId, String name, String cr, String type,
                                  int ac, String hp, String speed,
                                  int str, int dex, int con, int intel, int wis, int cha,
                                  Integer strSave, Integer dexSave, Integer conSave,
                                  Integer intSave, Integer wisSave, Integer chaSave,
                                  String skills, String damageVuln, String damageRes,
                                  String damageImm, String condImm,
                                  String senses, String languages) {
```

(and the identical change to `updateCustom`).

- [ ] **Step 2: Update `LibraryController` create + update**

In `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`:

In `create`, delete the two request-param lines (currently 81-82):
```java
                         @RequestParam(required = false) String hpValue,
                         @RequestParam(required = false) String speedValue,
```
and change the `createCustom` call (currently lines 107-112) to drop the `hpValue, speedValue,` argument line (currently line 109):
```java
        StatBlock sb = service.createCustom(campaignId, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages);
```

In `update`, delete the two request-param lines (currently 135-136):
```java
                         @RequestParam(required = false) String hpValue,
                         @RequestParam(required = false) String speedValue,
```
and change the `updateCustom` call (currently lines 160-165) to drop the `hpValue, speedValue,` argument line (currently line 162):
```java
        StatBlock sb = service.updateCustom(id, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages);
```

- [ ] **Step 3: Update `CampaignService` import call**

In `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`, in the statblock import loop, remove the `null, null,` argument line (currently line 128) from the `createCustom` call so it reads:

```java
                StatBlock sb = statBlockService.createCustom(saved.getId(),
                        sbDto.name(), sbDto.cr(), sbDto.type(),
                        sbDto.ac(), sbDto.hp(), sbDto.speed(),
                        sbDto.strScore(), sbDto.dexScore(), sbDto.conScore(),
                        sbDto.intScore(), sbDto.wisScore(), sbDto.chaScore(),
                        sbDto.strSave(), sbDto.dexSave(), sbDto.conSave(),
                        sbDto.intSave(), sbDto.wisSave(), sbDto.chaSave(),
                        sbDto.skills(),
                        sbDto.damageVulnerabilities(), sbDto.damageResistances(),
                        sbDto.damageImmunities(), sbDto.conditionImmunities(),
                        sbDto.senses(), sbDto.languages());
```

- [ ] **Step 4: Update `StatBlockServiceTest` call sites**

In `src/test/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockServiceTest.java`:

In the `createCustom` helper (lines 33-40), drop the `hp, "30 ft.",` line:
```java
    private StatBlock createCustom(String name, String cr, String type, int ac, String hp) {
        return service.createCustom(campaignId, name, cr, type, ac, hp, "30 ft.",
                10, 10, 10, 10, 10, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 10", null);
    }
```

In `createCustomForOtherCampaign` (lines 58-65), drop the `"30", "40 ft.",` line:
```java
    private void createCustomForOtherCampaign() {
        service.createCustom(UUID.randomUUID(), "Custom B", "2", "Giant", 14, "30", "30 ft.",
                10, 10, 10, 10, 10, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 10", "Giant");
    }
```

In `shouldUpdateCustomStatBlock` (lines 97-103), drop the `"45", "40 ft.",` line:
```java
        StatBlock updated = service.updateCustom(created.getId(), "Renamed", "3", "Beast",
                16, "45", "40 ft.",
                16, 10, 16, 12, 14, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "darkvision 60 ft.", "Common, Giant");
```

- [ ] **Step 5: Update `LibraryControllerTest`**

In `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java`, in `shouldCreateCustom`:

Change the `when(service.createCustom(...))` stub (lines 81-86) to remove one `anyString(), anyString(),` pair (the mock must match the new **26-arg** signature — currently 28 matchers). It becomes:
```java
        when(service.createCustom(any(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sb);
```
And delete the now-unbound params line (line 98):
```java
                        .param("hpValue", "").param("speedValue", "")
```

- [ ] **Step 6: Update the round-trip test's `createCustom` call**

In `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` (created in Task 2 against the 28-arg signature), delete the standalone `null, null,` line — the `hpValue`/`speedValue` pair that sits between the ability-scores line and the saves line — so the call has 26 args:

```java
        StatBlock sb = statBlockService.createCustom(c.getId(), "Amber Knight", "5", "Humanoid",
                18, "75 (10d8 + 30)", "30 ft.",
                16, 12, 16, 10, 12, 14,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 12", "Common");
        sb.setSourceKey("amber-knight");
        statBlockRepository.save(sb);
```

- [ ] **Step 7: Run the affected tests, then the full suite**

Run: `./mvnw test -Dtest='StatBlockServiceTest,LibraryControllerTest,CampaignImportExportRoundTripTest'`
Expected: PASS.

Run: `./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
git commit -m "refactor: drop unused hpValue/speedValue params from statblock create/update

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Open the browser on startup

§2.1 says `java -jar dmhelper.jar` "starts everything and opens the browser." Add a best-effort launcher that fires on `ApplicationReadyEvent`, derives the URL from `server.port` (never hard-codes a port), is gated by `dmhelper.open-browser` (default true, disabled in tests), and degrades silently on headless/unsupported environments — startup must never fail because a browser couldn't open.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/BrowserLauncher.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java` (disable in the one full-context test)

**Interfaces:**
- Consumes: `${server.port:8080}`, `${dmhelper.open-browser:true}`, `ApplicationReadyEvent`.
- Produces: `BrowserLauncher.openBrowser(ApplicationReadyEvent)` (best-effort; no return value other tasks depend on).

- [ ] **Step 1: Add the default property**

In `src/main/resources/application.properties`, add (after the existing `spring.thymeleaf.*` lines):

```properties

# Best-effort browser open on startup (SPEC §2.1); disable for headless/server runs.
dmhelper.open-browser=true
```

- [ ] **Step 2: Disable the launcher in the full-context test**

In `src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java`, add `dmhelper.open-browser=false` to the existing `@TestPropertySource` so the context-load test never spawns a browser on a developer's desktop:

```java
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "dmhelper.open-browser=false"
})
```

- [ ] **Step 3: Create the launcher**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/BrowserLauncher.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

/**
 * Best-effort: open the DM's default browser at the app URL once the server is
 * ready (SPEC §2.1). Never throws into startup; gated by {@code dmhelper.open-browser}
 * and skipped on headless environments (CI, tests, servers).
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final boolean enabled;
    private final int port;

    public BrowserLauncher(@Value("${dmhelper.open-browser:true}") boolean enabled,
                           @Value("${server.port:8080}") int port) {
        this.enabled = enabled;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser(ApplicationReadyEvent event) {
        if (!enabled || GraphicsEnvironment.isHeadless()) {
            return;
        }
        String url = "http://localhost:" + port;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("linux") && tryExec("xdg-open", url)) {
                return;
            }
            if (os.contains("mac") && tryExec("open", url)) {
                return;
            }
            if (os.contains("win") && tryExec("rundll32", "url.dll,FileProtocolHandler", url)) {
                return;
            }
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            log.info("Open DMHelper in your browser: {}", url);
        } catch (Exception e) {
            log.info("Could not auto-open a browser; open DMHelper at {} ({})", url, e.getMessage());
        }
    }

    private boolean tryExec(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Verify the context still loads with the launcher present**

Run: `./mvnw test -Dtest=DmhelperApplicationTests`
Expected: PASS — context loads, no browser opens (property disabled + headless guard).

- [ ] **Step 5: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Manually verify the real launch (developer machine, has a display)**

Run: `./mvnw spring-boot:run`
Expected: after "Started DmhelperApplication", the default browser opens at `http://localhost:8081` (the value of `server.port`). Stop with Ctrl+C.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/config/BrowserLauncher.java \
  src/main/resources/application.properties \
  src/test/java/dev/hendrikhoemberg/dmhelper/DmhelperApplicationTests.java
git commit -m "feat: open the browser on startup (SPEC §2.1)

Best-effort launcher on ApplicationReadyEvent, URL derived from server.port,
gated by dmhelper.open-browser, headless-safe.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [ ] **Run the whole suite once more**

Run: `./mvnw test`
Expected: BUILD SUCCESS; test count ≈ 62 (52 original − 1 removed srd-keys test + 11 new: SpellSeedServiceTest 3, SpellReseedMigrationTest 3, CampaignImportExportRoundTripTest 1, LibraryApiControllerTest 1, StartupBackupRunnerTest 3).

- [ ] **Sanity-check the app end-to-end**

Run: `./mvnw spring-boot:run`, then in the browser: open the Library, search "Goblin" (<100 ms), open a spell (e.g. Fireball) and confirm **School: Evocation** and **Components: V, S, M** now render; create a campaign, add an inactive party member and a custom monster, export it, re-import the file, and confirm the member is still inactive and the custom monster survives. Stop with Ctrl+C.

## Self-review notes

- **Spec coverage:** every review finding (#1–#7, spec-finding A) maps to a task per the table above; deferred items are documented with rationale.
- **Type consistency:** `reseedIfStale()`, `countWithSchool()`, `copyTree(Path,Path)`, `openBrowser(ApplicationReadyEvent)`, and the trimmed `createCustom`/`updateCustom` 28-arg signatures are used identically in their producing task and every consumer/test.
- **Ordering / arg counts:** Task 2 writes `CampaignImportExportRoundTripTest` against the current **28-arg** `createCustom` (includes `null, null` for `hpValue`/`speedValue`). Task 5 removes those two params (28 → 26 args) and therefore **must** also drop that `null, null` line from the round-trip test — this is Task 5, Step 6. All four `createCustom`/`updateCustom` call sites (StatBlockServiceTest ×3, LibraryController ×2 real calls, LibraryControllerTest mock ×1, CampaignService ×1, round-trip test ×1) are updated within Task 5. After Task 5, no call site passes `hpValue`/`speedValue`.
```
