# M8: Notes, Wiki & Quicknotes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the campaign spreadsheet with typed Markdown notes, wiki-links with backlinks, full-text search, a session-plan sidebar, and entity-attached quicknotes with promote-to-note.

**Architecture:** New `notes` package (following the existing `campaign`/`gamemap`/`encounter` pattern) with `Note` and `QuickNote` JPA entities, a `NoteLink` join entity for resolving `[[wiki links]]` and computing backlinks, a `WikiLinkParser` service that pre-processes Markdown before commonmark rendering, server-rendered Thymeleaf pages for note CRUD, and REST endpoints for quicknotes referenced via Alpine.js strips on entity pages. The shared sidebar is replaced with a campaign-aware session plan panel. Campaign export/import is extended to include notes and quicknotes.

**Tech Stack:** Spring Boot 4.1.0 (Jakarta EE 11), Spring Data JPA + H2, Thymeleaf + htmx, Alpine.js, commonmark-java, Jackson 3 (`tools.jackson`)

---

## File Structure

### New files (create)

| File | Responsibility |
|------|---------------|
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteType.java` | Enum: NPC, LOCATION, QUEST, SESSION_LOG, SESSION_PLAN, GENERIC |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/Note.java` | JPA entity: typed Markdown note scoped to a campaign |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteRepository.java` | Spring Data repository with search queries |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNote.java` | JPA entity: plain-text jot attached to any entity |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java` | Spring Data repository with target-qualified queries |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLink.java` | JPA entity: records `[[wiki-link]]` source→target relationships |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java` | Spring Data repository for backlink lookups |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParser.java` | Extracts `[[...]]` references from Markdown, resolves to entities, builds NoteLinks |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java` | CRUD + search + wiki-link lifecycle |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java` | CRUD + promote-to-note |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/NoteController.java` | Thymeleaf controller: note list, detail, create/edit form, delete |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/NoteApiController.java` | REST `/api/v1` controller for note CRUD, search, wiki-link autocomplete |
| `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiController.java` | REST `/api/v1` controller for quicknote CRUD + promote |
| `src/main/resources/templates/notes/list.html` | Thymeleaf page: note list with type filter + search |
| `src/main/resources/templates/notes/_card.html` | Thymeleaf fragment: note card (used in list + search results) |
| `src/main/resources/templates/notes/detail.html` | Thymeleaf page: full note view with rendered body, backlinks, quicknotes strip |
| `src/main/resources/templates/notes/_form.html` | Thymeleaf fragment: create/edit form with type selector and Markdown textarea |
| `src/main/resources/templates/notes/_quicknotes-strip.html` | Thymeleaf fragment: inline quicknotes strip (reusable across entity pages) |
| `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteServiceTest.java` | Unit tests for Note CRUD + wiki-link parsing + backlinks |
| `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteServiceTest.java` | Unit tests for QuickNote CRUD + promote |
| `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParserTest.java` | Unit tests for `[[...]]` extraction and resolution |

### Modified files

| File | Change |
|------|--------|
| `src/main/resources/templates/fragments/navbar.html` | Add "Notes" link to campaign nav |
| `src/main/resources/templates/fragments/sidebar.html` | Replace placeholder with campaign-aware session plan panel |
| `src/main/resources/templates/campaigns/detail.html` | Add Notes section and Handouts section links |
| `src/main/resources/templates/maps/battle.html` | Add quicknotes strip to battle sidebar |
| `src/main/resources/templates/encounter/detail.html` | Add quicknotes strip |
| `src/main/resources/templates/library/detail.html` | Add quicknotes strip for statblock detail |
| `src/main/resources/templates/party/list.html` | Add quicknotes strip for each party member? No — quicknotes only on detail entity pages (statblock detail, encounter detail, map battle) |
| `src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java` | Integrate wiki-link pre-processing |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java` | Add NoteExportDto, QuickNoteExportDto; replace `List<Object>` |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` | Wire notes + quicknotes into export/import |
| `src/main/resources/static/css/app.css` | Add note-specific styles (wiki-link rendering, quicknotes strip, note detail, session plan sidebar) |

---

### Task 1: NoteType enum + Note entity + NoteRepository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteType.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/Note.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteRepository.java`

- [ ] **Step 1: Create the NoteType enum**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

public enum NoteType {
    NPC,
    LOCATION,
    QUEST,
    SESSION_LOG,
    SESSION_PLAN,
    GENERIC
}
```

- [ ] **Step 2: Create the Note JPA entity**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteType type;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String body;

    @Column(length = 1000)
    private String tags;

    @Column(nullable = false)
    private boolean dmOnly = true;

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

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public NoteType getType() { return type; }
    public void setType(NoteType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isDmOnly() { return dmOnly; }
    public void setDmOnly(boolean dmOnly) { this.dmOnly = dmOnly; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Create the NoteRepository**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<Note> findByCampaignIdAndTypeOrderByCreatedAtDesc(UUID campaignId, NoteType type);

    @Query("SELECT n FROM Note n WHERE n.campaign.id = :campaignId " +
           "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(n.body) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY n.createdAt DESC")
    List<Note> searchByCampaignId(@Param("campaignId") UUID campaignId,
                                  @Param("search") String search);

    @Query("SELECT n FROM Note n WHERE n.campaign.id = :campaignId " +
           "AND LOWER(n.title) = LOWER(:title)")
    List<Note> findByCampaignIdAndTitle(@Param("campaignId") UUID campaignId,
                                        @Param("title") String title);
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteType.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/Note.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteRepository.java
git commit -m "feat(m8): add Note entity, NoteType enum, and NoteRepository"
```

---

### Task 2: QuickNote entity + QuickNoteRepository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNote.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java`

- [ ] **Step 1: Create the QuickNote JPA entity**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quick_note")
public class QuickNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 50)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String body;

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

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Create the QuickNoteRepository**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuickNoteRepository extends JpaRepository<QuickNote, UUID> {

    List<QuickNote> findByCampaignIdAndTargetTypeAndTargetIdOrderByCreatedAtAsc(
            UUID campaignId, String targetType, UUID targetId);

    List<QuickNote> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    @Query("SELECT qn FROM QuickNote qn WHERE qn.campaign.id = :campaignId " +
           "AND LOWER(qn.body) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY qn.createdAt DESC")
    List<QuickNote> searchByCampaignId(@Param("campaignId") UUID campaignId,
                                       @Param("search") String search);

    void deleteByTargetTypeAndTargetId(String targetType, UUID targetId);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNote.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java
git commit -m "feat(m8): add QuickNote entity and repository"
```

---

### Task 3: NoteLink entity + NoteLinkRepository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLink.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java`

- [ ] **Step 1: Create the NoteLink JPA entity**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "note_link")
public class NoteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_note_id", nullable = false)
    private Note sourceNote;

    @Column(nullable = false, length = 50)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 500)
    private String displayText;

    @Column(nullable = false)
    private boolean resolved = false;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Note getSourceNote() { return sourceNote; }
    public void setSourceNote(Note sourceNote) { this.sourceNote = sourceNote; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}
```

- [ ] **Step 2: Create the NoteLinkRepository**

```java
package dev.hendrikhoemberg.dmhelper.notes.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteLinkRepository extends JpaRepository<NoteLink, UUID> {

    List<NoteLink> findBySourceNoteId(UUID sourceNoteId);

    List<NoteLink> findByTargetTypeAndTargetId(String targetType, UUID targetId);

    void deleteBySourceNoteId(UUID sourceNoteId);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLink.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java
git commit -m "feat(m8): add NoteLink entity and repository for wiki-link backlinks"
```

---

### Task 4: WikiLinkParser service

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParser.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParserTest.java`

- [ ] **Step 1: Write the failing test for WikiLinkParser**

```java
package dev.hendrikhoemberg.dmhelper.notes.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikiLinkParserTest {

    private final WikiLinkParser parser = new WikiLinkParser();

    @Test
    void extractsSimpleWikiLink() {
        var refs = parser.extractReferences("See [[Goblin Cave]] for details.");
        assertEquals(1, refs.size());
        assertEquals("NOTE", refs.get(0).targetType());
        assertEquals("Goblin Cave", refs.get(0).title());
    }

    @Test
    void extractsPrefixedLinks() {
        var refs = parser.extractReferences("[[handout:The Regent's Letter]] [[map:Throne Room]] [[statblock:Goblin]]");
        assertEquals(3, refs.size());
        assertEquals("HANDOUT", refs.get(0).targetType());
        assertEquals("The Regent's Letter", refs.get(0).title());
        assertEquals("MAP", refs.get(1).targetType());
        assertEquals("Throne Room", refs.get(1).title());
        assertEquals("STATBLOCK", refs.get(2).targetType());
        assertEquals("Goblin", refs.get(2).title());
    }

    @Test
    void returnsEmptyForNoLinks() {
        var refs = parser.extractReferences("Just plain text.");
        assertTrue(refs.isEmpty());
    }

    @Test
    void handlesNull() {
        var refs = parser.extractReferences(null);
        assertTrue(refs.isEmpty());
    }

    @Test
    void convertsLinksToHtml() {
        var refs = List.of(
            new WikiLinkParser.WikiLinkReference("NOTE", "Goblin Cave", "/campaigns/x/notes/y", true),
            new WikiLinkParser.WikiLinkReference("NOTE", "Unknown", null, false)
        );
        String result = parser.replaceLinks(
            "See [[Goblin Cave]] and [[Unknown]].",
            refs
        );
        assertTrue(result.contains("<a href=\"/campaigns/x/notes/y\" class=\"wiki-link wiki-link-resolved\">Goblin Cave</a>"));
        assertTrue(result.contains("<span class=\"wiki-link wiki-link-broken\" title=\"Not found\">Unknown</span>"));
    }
}
```

- [ ] **Step 2: Run the test to verify failure**

```bash
mvn test -pl . -Dtest=WikiLinkParserTest
```
Expected: compilation failure — class does not exist.

- [ ] **Step 3: Implement WikiLinkParser**

```java
package dev.hendrikhoemberg.dmhelper.notes.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiLinkParser {

    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    public record WikiLinkReference(String prefix, String title, String url, boolean resolved) {}

    public List<WikiLinkTarget> extractReferences(String markdown) {
        var targets = new ArrayList<WikiLinkTarget>();
        if (markdown == null || markdown.isBlank()) return targets;

        var matcher = WIKI_LINK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            String prefix = "NOTE";
            String title = raw;

            int colonIdx = raw.indexOf(':');
            if (colonIdx > 0) {
                String maybePrefix = raw.substring(0, colonIdx).toUpperCase();
                if (maybePrefix.equals("HANDOUT") || maybePrefix.equals("MAP")
                        || maybePrefix.equals("STATBLOCK") || maybePrefix.equals("ENCOUNTER")) {
                    prefix = maybePrefix;
                    title = raw.substring(colonIdx + 1).trim();
                }
            }

            if (!title.isBlank()) {
                targets.add(new WikiLinkTarget(prefix, title));
            }
        }
        return targets;
    }

    public record WikiLinkTarget(String targetType, String title) {}

    public String replaceLinks(String markdown, List<WikiLinkReference> refs) {
        if (markdown == null) return "";

        String result = markdown;
        for (var ref : refs) {
            String search;
            if (ref.prefix().equals("NOTE")) {
                search = "(?i)\\[\\[" + Pattern.quote(ref.title()) + "\\]\\]";
            } else {
                search = "(?i)\\[\\[" + Pattern.quote(ref.prefix() + ":" + ref.title()) + "\\]\\]";
            }
            String replacement;
            if (ref.url() != null && ref.resolved()) {
                replacement = "<a href=\"" + ref.url() + "\" class=\"wiki-link wiki-link-resolved\">" + ref.title() + "</a>";
            } else {
                replacement = "<span class=\"wiki-link wiki-link-broken\" title=\"Not found\">" + ref.title() + "</span>";
            }
            result = result.replaceAll(search, Matcher.quoteReplacement(replacement));
        }
        return result;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn test -pl . -Dtest=WikiLinkParserTest
```
Expected: PASS — green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParser.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParserTest.java
git commit -m "feat(m8): add WikiLinkParser for [[wiki-link]] extraction and HTML replacement"
```

---

### Task 5: NoteService with wiki-link resolution + backlinks

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteServiceTest.java`

- [ ] **Step 1: Write the failing test for NoteService**

```java
package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({NoteService.class, WikiLinkParser.class, StatBlockService.class})
class NoteServiceTest {

    @Autowired private NoteRepository noteRepository;
    @Autowired private NoteLinkRepository noteLinkRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private NoteService noteService;

    private Campaign campaign;
    private StatBlock goblin;
    private GameMap dungeon;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaignRepository.save(campaign);

        goblin = new StatBlock();
        goblin.setName("Goblin");
        goblin.setSource(StatBlock.Source.SRD);
        goblin.setSourceKey("goblin");
        statBlockRepository.save(goblin);

        dungeon = new GameMap();
        dungeon.setCampaign(campaign);
        dungeon.setName("Dungeon Level 1");
        dungeon.setGridWidth(20);
        dungeon.setGridHeight(20);
        dungeon.setCellSizePx(48);
        gameMapRepository.save(dungeon);
    }

    @Test
    void createsNoteAndResolvesWikiLinks() {
        Note note = noteService.create(
            campaign.getId(),
            NoteType.LOCATION,
            "The Dungeon",
            "Guarded by [[statblock:Goblin]] and connects to [[Dungeon Level 1]].",
            "dungeon, level-1"
        );

        assertNotNull(note.getId());
        assertEquals("The Dungeon", note.getTitle());
        assertEquals(NoteType.LOCATION, note.getType());

        List<NoteLink> links = noteLinkRepository.findBySourceNoteId(note.getId());
        assertEquals(2, links.size());

        var statblockLink = links.stream()
            .filter(l -> "STATBLOCK".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(statblockLink.isResolved());
        assertEquals(goblin.getId(), statblockLink.getTargetId());

        var noteLink = links.stream()
            .filter(l -> "NOTE".equals(l.getTargetType()) && "Dungeon Level 1".equals(l.getDisplayText()))
            .findFirst().orElseThrow();
        assertFalse(noteLink.isResolved()); // no note titled "Dungeon Level 1" exists yet
    }

    @Test
    void findsBacklinks() {
        Note source = noteService.create(campaign.getId(), NoteType.GENERIC, "Source", "Links to [[Target]].", "");
        Note target = noteService.create(campaign.getId(), NoteType.GENERIC, "Target", "I am the target.", "");

        List<Note> backlinks = noteService.findBacklinks(target.getId());
        assertEquals(1, backlinks.size());
        assertEquals("Source", backlinks.get(0).getTitle());
    }

    @Test
    void searchFindsByTitleAndBody() {
        noteService.create(campaign.getId(), NoteType.NPC, "Aldric", "A wise wizard.", "");
        noteService.create(campaign.getId(), NoteType.LOCATION, "Forest", "Dense woods.", "");

        var results = noteService.search(campaign.getId(), "wizard");
        assertEquals(1, results.size());
        assertEquals("Aldric", results.get(0).getTitle());
    }

    @Test
    void updatesNoteAndReResolvesLinks() {
        Note note = noteService.create(campaign.getId(), NoteType.GENERIC, "Test", "Links to [[Old]].", "");
        assertEquals(1, noteLinkRepository.findBySourceNoteId(note.getId()).size());

        Note updated = noteService.update(note.getId(), note.getType(), "Test Updated",
            "Links to [[New]] instead.", "updated");
        var links = noteLinkRepository.findBySourceNoteId(updated.getId());
        assertEquals(1, links.size());
        assertEquals("New", links.get(0).getDisplayText());
    }

    @Test
    void deletesNoteAndRemovesLinks() {
        Note note = noteService.create(campaign.getId(), NoteType.GENERIC, "Test", "Links to [[Target]].", "");
        assertFalse(noteLinkRepository.findBySourceNoteId(note.getId()).isEmpty());

        noteService.delete(note.getId());
        assertTrue(noteLinkRepository.findBySourceNoteId(note.getId()).isEmpty());
        assertTrue(noteRepository.findById(note.getId()).isEmpty());
    }

    @Test
    void resolvesHandoutAndMapPrefixes() {
        Handout letter = new Handout();
        letter.setCampaign(campaign);
        letter.setTitle("The Letter");
        handoutRepository.save(letter);

        Note note = noteService.create(campaign.getId(), NoteType.QUEST, "Quest",
            "Read [[handout:The Letter]] and explore [[map:Dungeon Level 1]].", "");

        var links = noteLinkRepository.findBySourceNoteId(note.getId());
        var handoutLink = links.stream().filter(l -> "HANDOUT".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(handoutLink.isResolved());
        assertEquals(letter.getId(), handoutLink.getTargetId());

        var mapLink = links.stream().filter(l -> "MAP".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(mapLink.isResolved());
        assertEquals(dungeon.getId(), mapLink.getTargetId());
    }
}
```

- [ ] **Step 2: Run test to verify failure**

```bash
mvn test -pl . -Dtest=NoteServiceTest
```
Expected: compilation failure — NoteService does not exist.

- [ ] **Step 3: Implement NoteService**

```java
package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteLinkRepository noteLinkRepository;
    private final CampaignRepository campaignRepository;
    private final StatBlockRepository statBlockRepository;
    private final StatBlockService statBlockService;
    private final GameMapRepository gameMapRepository;
    private final HandoutRepository handoutRepository;
    private final WikiLinkParser wikiLinkParser;

    public NoteService(NoteRepository noteRepository,
                       NoteLinkRepository noteLinkRepository,
                       CampaignRepository campaignRepository,
                       StatBlockRepository statBlockRepository,
                       StatBlockService statBlockService,
                       GameMapRepository gameMapRepository,
                       HandoutRepository handoutRepository,
                       WikiLinkParser wikiLinkParser) {
        this.noteRepository = noteRepository;
        this.noteLinkRepository = noteLinkRepository;
        this.campaignRepository = campaignRepository;
        this.statBlockRepository = statBlockRepository;
        this.statBlockService = statBlockService;
        this.gameMapRepository = gameMapRepository;
        this.handoutRepository = handoutRepository;
        this.wikiLinkParser = wikiLinkParser;
    }

    public Note create(UUID campaignId, NoteType type, String title, String body, String tags) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));

        Note note = new Note();
        note.setCampaign(campaign);
        note.setType(type);
        note.setTitle(title);
        note.setBody(body);
        note.setTags(tags);
        note = noteRepository.save(note);

        rebuildLinks(note);
        return note;
    }

    @Transactional(readOnly = true)
    public Note findById(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note not found"));
    }

    @Transactional(readOnly = true)
    public List<Note> findByCampaignId(UUID campaignId) {
        return noteRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    }

    @Transactional(readOnly = true)
    public List<Note> findByCampaignIdAndType(UUID campaignId, NoteType type) {
        return noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, type);
    }

    @Transactional(readOnly = true)
    public List<Note> search(UUID campaignId, String search) {
        if (search == null || search.isBlank()) {
            return findByCampaignId(campaignId);
        }
        return noteRepository.searchByCampaignId(campaignId, search);
    }

    public Note update(UUID id, NoteType type, String title, String body, String tags) {
        Note note = findById(id);
        note.setType(type);
        note.setTitle(title);
        note.setBody(body);
        note.setTags(tags);
        note = noteRepository.save(note);

        rebuildLinks(note);
        return note;
    }

    public void delete(UUID id) {
        Note note = findById(id);
        noteLinkRepository.deleteBySourceNoteId(id);
        noteRepository.delete(note);
    }

    @Transactional(readOnly = true)
    public List<Note> findBacklinks(UUID noteId) {
        List<NoteLink> incomingLinks = noteLinkRepository.findByTargetTypeAndTargetId("NOTE", noteId);
        return incomingLinks.stream()
                .map(NoteLink::getSourceNote)
                .distinct()
                .toList();
    }

    public String renderBody(Note note) {
        String body = note.getBody();
        if (body == null || body.isBlank()) return "";

        var targets = wikiLinkParser.extractReferences(body);
        var refs = new ArrayList<WikiLinkParser.WikiLinkReference>();

        for (var target : targets) {
            String url = null;
            boolean resolved = false;

            switch (target.targetType()) {
                case "NOTE" -> {
                    var notes = noteRepository.findByCampaignIdAndTitle(
                            note.getCampaign().getId(), target.title());
                    if (!notes.isEmpty()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/notes/" + notes.get(0).getId();
                        resolved = true;
                    }
                }
                case "STATBLOCK" -> {
                    var hits = statBlockService.search(null, null, null, target.title());
                    if (!hits.isEmpty()) {
                        url = "/library/statblocks/" + hits.get(0).getId();
                        resolved = true;
                    }
                }
                case "HANDOUT" -> {
                    var handouts = handoutRepository.findByCampaignIdOrderByTitleAsc(
                            note.getCampaign().getId());
                    var match = handouts.stream()
                            .filter(h -> h.getTitle().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/handouts";
                        resolved = true;
                    }
                }
                case "MAP" -> {
                    var maps = gameMapRepository.findByCampaignIdOrderBySortOrderAsc(
                            note.getCampaign().getId());
                    var match = maps.stream()
                            .filter(m -> m.getName().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/maps/" + match.get().getId() + "/battle";
                        resolved = true;
                    }
                }
            }

            refs.add(new WikiLinkParser.WikiLinkReference(
                    target.targetType(),
                    target.title(),
                    url, resolved));
        }

        if (refs.isEmpty()) return body;
        return wikiLinkParser.replaceLinks(body, refs);
    }

    private void rebuildLinks(Note note) {
        noteLinkRepository.deleteBySourceNoteId(note.getId());

        var targets = wikiLinkParser.extractReferences(note.getBody());
        for (var target : targets) {
            NoteLink link = new NoteLink();
            link.setSourceNote(note);
            link.setTargetType(target.targetType());
            link.setDisplayText(target.title());

            boolean found = switch (target.targetType()) {
                case "NOTE" -> {
                    var notes = noteRepository.findByCampaignIdAndTitle(
                            note.getCampaign().getId(), target.title());
                    if (!notes.isEmpty()) {
                        link.setTargetId(notes.get(0).getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID()); // placeholder for unresolved
                    yield false;
                }
                case "STATBLOCK" -> {
                    var hits = statBlockService.search(null, null, null, target.title());
                    if (!hits.isEmpty()) {
                        link.setTargetId(hits.get(0).getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "HANDOUT" -> {
                    var handouts = handoutRepository.findByCampaignIdOrderByTitleAsc(
                            note.getCampaign().getId());
                    var match = handouts.stream()
                            .filter(h -> h.getTitle().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        link.setTargetId(match.get().getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "MAP" -> {
                    var maps = gameMapRepository.findByCampaignIdOrderBySortOrderAsc(
                            note.getCampaign().getId());
                    var match = maps.stream()
                            .filter(m -> m.getName().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        link.setTargetId(match.get().getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                default -> {
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
            };
            link.setResolved(found);
            noteLinkRepository.save(link);
        }
    }
}
```

**Implementation notes for this task:**
- `StatBlockRepository` does NOT have a `search(...)` method — it uses `JpaSpecificationExecutor`. The code above correctly uses `statBlockService.search(null, null, null, title)` (via `StatBlockService` injection) — never `statBlockRepository.search()`.
- `StatBlockRepository.findBySourceAndSourceKey(Source, String)` **exists** and may be used as a fallback if desired, but `statBlockService.search()` handles both SRD and CUSTOM sources.
- `HandoutRepository.findByCampaignIdOrderByTitleAsc(UUID)` exists.
- `GameMapRepository.findByCampaignIdOrderBySortOrderAsc(UUID)` exists.
- When looking up statblocks by name for wiki-link resolution, use `statBlockService.search(null, null, null, title)` which handles both SRD and CUSTOM sources.

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -pl . -Dtest=NoteServiceTest
```
Expected: PASS — green, all 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteServiceTest.java
git commit -m "feat(m8): add NoteService with wiki-link resolution, backlinks, CRUD, and search"
```

---

### Task 6: QuickNoteService with promote-to-note

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteServiceTest.java`

- [ ] **Step 1: Write the failing test for QuickNoteService**

```java
package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({QuickNoteService.class, NoteService.class, WikiLinkParser.class, StatBlockService.class})
class QuickNoteServiceTest {

    @Autowired private QuickNoteRepository quickNoteRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private QuickNoteService quickNoteService;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaignRepository.save(campaign);
    }

    @Test
    void createsQuickNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", UUID.randomUUID(), "Watch for traps.");
        assertNotNull(qn.getId());
        assertEquals("Watch for traps.", qn.getBody());
        assertEquals("ENCOUNTER", qn.getTargetType());
    }

    @Test
    void findsByTarget() {
        UUID targetId = UUID.randomUUID();
        quickNoteService.create(campaign.getId(), "MAP", targetId, "First note.");
        quickNoteService.create(campaign.getId(), "MAP", targetId, "Second note.");

        var notes = quickNoteService.findByTarget(campaign.getId(), "MAP", targetId);
        assertEquals(2, notes.size());
    }

    @Test
    void promotesQuickNoteToNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "STATBLOCK", UUID.randomUUID(), "This goblin has a secret lair.");
        Note note = quickNoteService.promoteToNote(qn.getId());

        assertNotNull(note.getId());
        assertEquals("This goblin has a secret lair.", note.getBody());
        assertEquals(NoteType.GENERIC, note.getType());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void promotesQuickNoteWithTitle() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "The party must find the crystal.");
        Note note = quickNoteService.promoteToNote(qn.getId(), "The Crystal Quest", NoteType.QUEST);

        assertEquals("The Crystal Quest", note.getTitle());
        assertEquals(NoteType.QUEST, note.getType());
    }

    @Test
    void deletesQuickNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", UUID.randomUUID(), "Temp note.");
        quickNoteService.delete(qn.getId());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void promotesQuickNoteWithPrefixedLink() {
        // Create a real statblock and attach a quicknote — promotion should pre-link
        var sb = new dev.hendrikhoemberg.dmhelper.library.data.StatBlock();
        sb.setName("Goblin");
        sb.setSource(dev.hendrikhoemberg.dmhelper.library.data.StatBlock.Source.SRD);
        sb.setSourceKey("goblin");
        statBlockRepository.save(sb);

        QuickNote qn = quickNoteService.create(campaign.getId(), "STATBLOCK", sb.getId(), "This goblin has a secret lair.");
        Note note = quickNoteService.promoteToNote(qn.getId(), "Goblin Secrets", NoteType.LOCATION);

        assertTrue(note.getBody().startsWith("[[statblock:Goblin]]"));
        assertTrue(note.getBody().contains("This goblin has a secret lair."));
    }
}
```

- [ ] **Step 2: Run test to verify failure**

```bash
mvn test -pl . -Dtest=QuickNoteServiceTest
```
Expected: compilation failure — QuickNoteService does not exist.

- [ ] **Step 3: Implement QuickNoteService**

```java
package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class QuickNoteService {

    private final QuickNoteRepository quickNoteRepository;
    private final CampaignRepository campaignRepository;
    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final StatBlockRepository statBlockRepository;
    private final GameMapRepository gameMapRepository;
    private final HandoutRepository handoutRepository;

    public QuickNoteService(QuickNoteRepository quickNoteRepository,
                            CampaignRepository campaignRepository,
                            NoteService noteService,
                            NoteRepository noteRepository,
                            StatBlockRepository statBlockRepository,
                            GameMapRepository gameMapRepository,
                            HandoutRepository handoutRepository) {
        this.quickNoteRepository = quickNoteRepository;
        this.campaignRepository = campaignRepository;
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.statBlockRepository = statBlockRepository;
        this.gameMapRepository = gameMapRepository;
        this.handoutRepository = handoutRepository;
    }

    public QuickNote create(UUID campaignId, String targetType, UUID targetId, String body) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));

        QuickNote qn = new QuickNote();
        qn.setCampaign(campaign);
        qn.setTargetType(targetType);
        qn.setTargetId(targetId);
        qn.setBody(body);
        return quickNoteRepository.save(qn);
    }

    @Transactional(readOnly = true)
    public List<QuickNote> findByTarget(UUID campaignId, String targetType, UUID targetId) {
        return quickNoteRepository.findByCampaignIdAndTargetTypeAndTargetIdOrderByCreatedAtAsc(
                campaignId, targetType, targetId);
    }

    @Transactional(readOnly = true)
    public QuickNote findById(UUID id) {
        return quickNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("QuickNote not found"));
    }

    public Note promoteToNote(UUID quickNoteId) {
        QuickNote qn = findById(quickNoteId);
        String targetLink = resolveTargetLink(qn);
        Note note = noteService.create(qn.getCampaign().getId(),
            NoteType.GENERIC,
            qn.getBody().length() > 80 ? qn.getBody().substring(0, 77) + "..." : qn.getBody(),
            targetLink + qn.getBody(),
            "");
        quickNoteRepository.delete(qn);
        return note;
    }

    public Note promoteToNote(UUID quickNoteId, String title, NoteType type) {
        QuickNote qn = findById(quickNoteId);
        String targetLink = resolveTargetLink(qn);
        Note note = noteService.create(qn.getCampaign().getId(), type, title,
            targetLink + qn.getBody(), "");
        quickNoteRepository.delete(qn);
        return note;
    }

    public void delete(UUID id) {
        QuickNote qn = findById(id);
        quickNoteRepository.delete(qn);
    }

    /**
     * Resolves the quicknote's target entity to a wiki-link for promote-to-note.
     * Returns the link string (with trailing newline) or empty string if unresolvable.
     */
    private String resolveTargetLink(QuickNote qn) {
        UUID targetId = qn.getTargetId();
        String link = switch (qn.getTargetType()) {
            case "STATBLOCK" -> {
                var sb = statBlockRepository.findById(targetId);
                yield sb.map(s -> "[[statblock:" + s.getName() + "]]\n\n").orElse("");
            }
            case "MAP" -> {
                var map = gameMapRepository.findById(targetId);
                yield map.map(m -> "[[map:" + m.getName() + "]]\n\n").orElse("");
            }
            case "HANDOUT" -> {
                var handout = handoutRepository.findById(targetId);
                yield handout.map(h -> "[[handout:" + h.getTitle() + "]]\n\n").orElse("");
            }
            case "NOTE" -> {
                var note = noteRepository.findById(targetId);
                yield note.map(n -> "[[" + n.getTitle() + "]]\n\n").orElse("");
            }
            // ENCOUNTER, PARTYMEMBER, CAMPAIGN — no wiki-link prefix; body copied as-is
            default -> "";
        };
        return link;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -pl . -Dtest=QuickNoteServiceTest
```
Expected: PASS — green, all 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteServiceTest.java
git commit -m "feat(m8): add QuickNoteService with CRUD and promote-to-note"
```

---

### Task 7: Note Thymeleaf controller (NoteController)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/NoteController.java`

- [ ] **Step 1: Create NoteController**

```java
package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/notes")
public class NoteController {

    private final NoteService noteService;
    private final QuickNoteService quickNoteService;
    private final CampaignRepository campaignRepository;
    private final MarkdownUtil markdownUtil;

    public NoteController(NoteService noteService,
                          QuickNoteService quickNoteService,
                          CampaignRepository campaignRepository,
                          MarkdownUtil markdownUtil) {
        this.noteService = noteService;
        this.quickNoteService = quickNoteService;
        this.campaignRepository = campaignRepository;
        this.markdownUtil = markdownUtil;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId,
                       @RequestParam(required = false) NoteType type,
                       @RequestParam(required = false, defaultValue = "") String search,
                       Model model) {
        var notes = (search != null && !search.isBlank())
                ? noteService.search(campaignId, search)
                : (type != null)
                    ? noteService.findByCampaignIdAndType(campaignId, type)
                    : noteService.findByCampaignId(campaignId);
        model.addAttribute("notes", notes);
        model.addAttribute("noteTypes", NoteType.values());
        model.addAttribute("selectedType", type);
        model.addAttribute("search", search);
        return "notes/list";
    }

    @GetMapping("/{noteId}")
    public String detail(@PathVariable UUID campaignId,
                         @PathVariable UUID noteId,
                         Model model) {
        Note note = noteService.findById(noteId);
        model.addAttribute("note", note);

        String renderedBody = noteService.renderBody(note);
        model.addAttribute("renderedBody", markdownUtil.toHtml(renderedBody));

        var backlinks = noteService.findBacklinks(noteId);
        model.addAttribute("backlinks", backlinks);

        var quicknotes = quickNoteService.findByTarget(campaignId, "NOTE", noteId);
        model.addAttribute("quicknotes", quicknotes);

        return "notes/detail";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("noteTypes", NoteType.values());
        return "notes/_form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam NoteType type,
                         @RequestParam String title,
                         @RequestParam(required = false) String body,
                         @RequestParam(required = false) String tags) {
        Note note = noteService.create(campaignId, type, title, body, tags);
        return "redirect:/campaigns/%s/notes/%s".formatted(campaignId, note.getId());
    }

    @GetMapping("/{noteId}/edit")
    public String editForm(@PathVariable UUID campaignId,
                           @PathVariable UUID noteId,
                           Model model) {
        Note note = noteService.findById(noteId);
        model.addAttribute("note", note);
        model.addAttribute("noteTypes", NoteType.values());
        return "notes/_form";
    }

    @PutMapping("/{noteId}")
    public String update(@PathVariable UUID campaignId,
                         @PathVariable UUID noteId,
                         @RequestParam NoteType type,
                         @RequestParam String title,
                         @RequestParam(required = false) String body,
                         @RequestParam(required = false) String tags) {
        noteService.update(noteId, type, title, body, tags);
        return "redirect:/campaigns/%s/notes/%s".formatted(campaignId, noteId);
    }

    @DeleteMapping("/{noteId}")
    public String delete(@PathVariable UUID campaignId,
                         @PathVariable UUID noteId) {
        noteService.delete(noteId);
        return "redirect:/campaigns/%s/notes".formatted(campaignId);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/NoteController.java
git commit -m "feat(m8): add NoteController with CRUD, search, and detail views"
```

---

### Task 8: Note REST API controller (NoteApiController)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/NoteApiController.java`

- [ ] **Step 1: Create NoteApiController**

```java
package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/notes")
public class NoteApiController {

    private final NoteService noteService;

    public NoteApiController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<NoteSummaryDto> list(@PathVariable UUID campaignId,
                                     @RequestParam(required = false) NoteType type,
                                     @RequestParam(required = false, defaultValue = "") String search) {
        var notes = (search != null && !search.isBlank())
                ? noteService.search(campaignId, search)
                : (type != null)
                    ? noteService.findByCampaignIdAndType(campaignId, type)
                    : noteService.findByCampaignId(campaignId);
        return notes.stream().map(NoteSummaryDto::from).toList();
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<NoteDetailDto> get(@PathVariable UUID campaignId,
                                             @PathVariable UUID noteId) {
        Note note = noteService.findById(noteId);
        return ResponseEntity.ok(NoteDetailDto.from(note, noteService.renderBody(note),
                noteService.findBacklinks(noteId)));
    }

    @GetMapping("/autocomplete")
    public List<Map<String, String>> autocomplete(@PathVariable UUID campaignId,
                                                  @RequestParam String q) {
        return noteService.search(campaignId, q).stream()
                .map(n -> Map.of("id", n.getId().toString(), "title", n.getTitle(),
                        "type", n.getType().name()))
                .limit(10)
                .toList();
    }

    public record NoteSummaryDto(UUID id, String title, NoteType type,
                                  String snippet, String createdAt) {
        static NoteSummaryDto from(Note n) {
            String snippet = n.getBody() != null && n.getBody().length() > 120
                    ? n.getBody().substring(0, 117) + "..." : n.getBody();
            return new NoteSummaryDto(n.getId(), n.getTitle(), n.getType(),
                    snippet, n.getCreatedAt().toString());
        }
    }

    public record NoteDetailDto(UUID id, String title, NoteType type,
                                 String body, String renderedBody, String tags,
                                 boolean dmOnly, String createdAt,
                                 List<BacklinkDto> backlinks) {
        static NoteDetailDto from(Note n, String renderedBody, List<Note> backlinks) {
            return new NoteDetailDto(n.getId(), n.getTitle(), n.getType(),
                    n.getBody(), renderedBody, n.getTags(), n.isDmOnly(),
                    n.getCreatedAt().toString(),
                    backlinks.stream().map(BacklinkDto::from).toList());
        }

        record BacklinkDto(UUID id, String title, NoteType type) {
            static BacklinkDto from(Note n) {
                return new BacklinkDto(n.getId(), n.getTitle(), n.getType());
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/NoteApiController.java
git commit -m "feat(m8): add NoteApiController with REST endpoints for notes and autocomplete"
```

---

### Task 9: QuickNote REST API controller (QuickNoteApiController)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiController.java`

- [ ] **Step 1: Create QuickNoteApiController**

```java
package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/quicknotes")
public class QuickNoteApiController {

    private final QuickNoteService quickNoteService;

    public QuickNoteApiController(QuickNoteService quickNoteService) {
        this.quickNoteService = quickNoteService;
    }

    @GetMapping
    public List<QuickNoteDto> list(@PathVariable UUID campaignId,
                                   @RequestParam String targetType,
                                   @RequestParam UUID targetId) {
        return quickNoteService.findByTarget(campaignId, targetType, targetId)
                .stream().map(QuickNoteDto::from).toList();
    }

    @PostMapping
    public QuickNoteDto create(@PathVariable UUID campaignId,
                               @RequestParam String targetType,
                               @RequestParam UUID targetId,
                               @RequestParam String body) {
        QuickNote qn = quickNoteService.create(campaignId, targetType, targetId, body);
        return QuickNoteDto.from(qn);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId,
                                       @PathVariable UUID id) {
        quickNoteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/promote")
    public PromoteResultDto promote(@PathVariable UUID campaignId,
                                    @PathVariable UUID id,
                                    @RequestParam(required = false) String title,
                                    @RequestParam(required = false) NoteType type) {
        Note note = (title != null && type != null)
                ? quickNoteService.promoteToNote(id, title, type)
                : quickNoteService.promoteToNote(id);
        return PromoteResultDto.from(note, campaignId);
    }

    public record QuickNoteDto(UUID id, UUID campaignId, String targetType,
                                UUID targetId, String body, Instant createdAt) {
        static QuickNoteDto from(QuickNote qn) {
            return new QuickNoteDto(qn.getId(), qn.getCampaign().getId(),
                    qn.getTargetType(), qn.getTargetId(), qn.getBody(), qn.getCreatedAt());
        }
    }

    public record PromoteResultDto(UUID noteId, String url) {
        static PromoteResultDto from(Note n, UUID campaignId) {
            return new PromoteResultDto(n.getId(),
                    "/campaigns/%s/notes/%s".formatted(campaignId, n.getId()));
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/web/QuickNoteApiController.java
git commit -m "feat(m8): add QuickNoteApiController with REST endpoints for quicknotes"
```

---

### Task 10: Thymeleaf templates for notes

**Files:**
- Create: `src/main/resources/templates/notes/list.html`
- Create: `src/main/resources/templates/notes/_card.html`
- Create: `src/main/resources/templates/notes/detail.html`
- Create: `src/main/resources/templates/notes/_form.html`

- [ ] **Step 1: Create the note card fragment**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="card(note)">
<div class="card">
    <h3>
        <a th:href="@{/campaigns/{cid}/notes/{nid}(cid=${note.campaign.id}, nid=${note.id})}"
           th:text="${note.title}">Title</a>
    </h3>
    <div class="statblock-meta">
        <span class="badge" th:text="${note.type}">TYPE</span>
    </div>
    <p th:if="${note.body != null and !note.body.isBlank()}"
       th:text="${#strings.abbreviate(note.body, 150)}">Snippet</p>
    <div class="card-time" th:text="'Created ' + ${#temporals.format(note.createdAt, 'yyyy-MM-dd HH:mm')}">Time</div>
    <div class="card-actions">
        <a th:href="@{/campaigns/{cid}/notes/{nid}/edit(cid=${note.campaign.id}, nid=${note.id})}"
           class="btn btn-ghost">Edit</a>
        <button class="btn btn-danger"
                hx-delete="@{/campaigns/{cid}/notes/{nid}(cid=${note.campaign.id}, nid=${note.id})}"
                hx-target="#notes-list" hx-swap="outerHTML"
                hx-confirm="Delete this note?">
            Delete
        </button>
    </div>
</div>
</th:block>
</html>
```

- [ ] **Step 2: Create the note list page**

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — ' + ${campaign.name} + ' — Notes'">DMHelper — Notes</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-layout">
        <main>
            <div class="page-header">
                <h1>Notes</h1>
                <a th:href="@{/campaigns/{id}(id=${campaignId})}" class="btn btn-ghost">&larr; Campaign</a>
            </div>

            <div class="search-bar">
                <div class="form-group">
                    <label for="noteSearch">Search</label>
                    <input type="text" id="noteSearch" name="search"
                           th:value="${search}"
                           hx-get="@{/campaigns/{id}/notes(id=${campaignId})}"
                           hx-trigger="keyup changed delay:200ms"
                           hx-include="#noteTypeFilter"
                           hx-target="#notes-list"
                           hx-swap="outerHTML"
                           placeholder="Search notes...">
                </div>
                <div class="form-group">
                    <label for="noteTypeFilter">Type</label>
                    <select id="noteTypeFilter" name="type"
                            hx-get="@{/campaigns/{id}/notes(id=${campaignId})}"
                            hx-trigger="change"
                            hx-include="#noteSearch"
                            hx-target="#notes-list"
                            hx-swap="outerHTML">
                        <option value="" th:selected="${selectedType == null}">All Types</option>
                        <option th:each="t : ${noteTypes}"
                                th:value="${t.name()}"
                                th:text="${t.name()}"
                                th:selected="${selectedType != null and selectedType.name() == t.name()}">Type</option>
                    </select>
                </div>
            </div>

            <div style="margin-bottom: var(--space-md);">
                <a th:href="@{/campaigns/{id}/notes/new(id=${campaignId})}"
                   class="btn btn-primary">+ New Note</a>
            </div>

            <div id="notes-list" class="card-grid">
                <th:block th:if="${notes.isEmpty()}">
                    <div class="empty-state">
                        <p>No notes yet</p>
                        <a th:href="@{/campaigns/{id}/notes/new(id=${campaignId})}" class="btn btn-primary">Create your first note</a>
                    </div>
                </th:block>
                <th:block th:each="note : ${notes}">
                    <th:block th:replace="~{notes/_card :: card(note=${note})}"></th:block>
                </th:block>
            </div>
        </main>

        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 3: Create the note detail page**

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — ' + ${note.title}">DMHelper — Note</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-layout">
        <main>
            <div class="page-header">
                <h1 th:text="${note.title}">Note Title</h1>
                <a th:href="@{/campaigns/{id}/notes(id=${campaignId})}" class="btn btn-ghost">&larr; Notes</a>
            </div>

            <div class="detail-section">
                <div class="detail-meta">
                    <span class="badge" th:text="${note.type}">TYPE</span>
                    <span style="margin-left: var(--space-md);"
                          th:text="'Created ' + ${#temporals.format(note.createdAt, 'yyyy-MM-dd HH:mm')}">Date</span>
                    <span th:if="${note.tags != null and !note.tags.isBlank()}"
                          style="margin-left: var(--space-md); color: var(--color-text-muted);"
                          th:text="'Tags: ' + ${note.tags}">Tags</span>
                </div>

                <div class="note-body" th:utext="${renderedBody}">
                    Rendered body here
                </div>

                <div class="detail-actions" style="margin-top: var(--space-lg);">
                    <a th:href="@{/campaigns/{cid}/notes/{nid}/edit(cid=${campaignId}, nid=${note.id})}"
                       class="btn">Edit</a>
                    <button class="btn btn-danger"
                            hx-delete="@{/campaigns/{cid}/notes/{nid}(cid=${campaignId}, nid=${note.id})}"
                            hx-confirm="Delete this note?"
                            hx-target="body" hx-swap="outerHTML">
                        Delete
                    </button>
                </div>
            </div>

            <div th:if="${not #lists.isEmpty(backlinks)}" class="detail-section" style="margin-top: var(--space-lg);">
                <h2>Referenced By</h2>
                <ul style="list-style: none; padding: 0;">
                    <li th:each="bl : ${backlinks}" style="padding: var(--space-xs) 0;">
                        <a th:href="@{/campaigns/{cid}/notes/{nid}(cid=${campaignId}, nid=${bl.id})}"
                           th:text="${bl.title}">Backlink</a>
                        <span class="badge" th:text="${bl.type}" style="margin-left: var(--space-sm); font-size: 0.7rem;">TYPE</span>
                    </li>
                </ul>
            </div>

            <div class="detail-section" style="margin-top: var(--space-lg);">
                <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='NOTE', targetId=${note.id})}"></th:block>
            </div>
        </main>

        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 4: Create the note form fragment**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="form(note, noteTypes, campaignId)">
<form th:action="${note != null}"
          ? @{/campaigns/{cid}/notes/{nid}(cid=${campaignId}, nid=${note.id})}
          : @{/campaigns/{cid}/notes(cid=${campaignId})}"
      th:method="${note != null} ? 'put' : 'post'"
      hx-target="body" hx-swap="outerHTML"
      style="padding: var(--space-lg); max-width: 800px; margin: 0 auto;">

    <h1 th:text="${note != null} ? 'Edit Note' : 'New Note'"
        style="margin-bottom: var(--space-lg);">New Note</h1>

    <div class="form-group">
        <label for="noteTitle">Title</label>
        <input type="text" id="noteTitle" name="title" required
               th:value="${note != null ? note.title : ''}"
               placeholder="Note title">
    </div>

    <div class="form-group">
        <label for="noteType">Type</label>
        <select id="noteType" name="type" required>
            <option th:each="t : ${noteTypes}"
                    th:value="${t.name()}"
                    th:text="${t.name()}"
                    th:selected="${note != null and note.type == t}">TYPE</option>
        </select>
        <style>
            #noteType {
                width: 100%; padding: var(--space-sm) var(--space-md);
                font-size: var(--text-base); font-family: inherit;
                color: var(--color-text); background: var(--color-bg);
                border: 1px solid var(--color-border); border-radius: var(--radius);
            }
        </style>
    </div>

    <div class="form-group">
        <label for="noteBody">Body (Markdown)</label>
        <textarea id="noteBody" name="body" rows="20"
                  th:text="${note != null ? note.body : ''}"
                  placeholder="Write in Markdown. Use [[Note Title]] for wiki-links, [[statblock:Name]] for statblocks..."></textarea>
    </div>

    <div class="form-group">
        <label for="noteTags">Tags (comma-separated)</label>
        <input type="text" id="noteTags" name="tags"
               th:value="${note != null ? note.tags : ''}"
               placeholder="e.g. dungeon, level-1, goblins">
    </div>

    <div class="form-actions">
        <a th:href="${note != null}
                ? @{/campaigns/{cid}/notes/{nid}(cid=${campaignId}, nid=${note.id})}
                : @{/campaigns/{cid}/notes(cid=${campaignId})}"
           class="btn btn-ghost">Cancel</a>
        <button type="submit" class="btn btn-primary">Save</button>
    </div>
</form>
</th:block>
</html>
```

- [ ] **Step 5: Create the quicknotes strip fragment**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="strip(campaignId, targetType, targetId)">
<div x-data="quicknotes('__${campaignId}__', '__${targetType}__', '__${targetId}__')"
     class="quicknotes-strip">
    <h3 style="font-size: var(--text-sm); color: var(--color-text-muted);
               text-transform: uppercase; letter-spacing: 1px; margin-bottom: var(--space-sm);">
        Quick Notes
    </h3>

    <form @submit.prevent="add()" style="display: flex; gap: var(--space-xs); margin-bottom: var(--space-sm);">
        <input type="text" x-model="newBody" placeholder="Jot a quick note..."
               style="flex: 1; padding: var(--space-xs) var(--space-sm);
                      background: var(--color-bg); border: 1px solid var(--color-border);
                      border-radius: var(--radius); color: var(--color-text);
                      font-size: var(--text-sm);">
        <button type="submit" class="btn btn-primary" style="font-size: var(--text-sm); padding: 2px 8px;">
            Add
        </button>
    </form>

    <div style="max-height: 200px; overflow-y: auto;">
        <template x-for="qn in items" :key="qn.id">
            <div class="quicknote-row" style="padding: var(--space-xs) 0;
                     border-bottom: 1px solid var(--color-border);
                     display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-xs);">
                <div>
                    <span x-text="qn.body" style="font-size: var(--text-sm); white-space: pre-wrap;"></span>
                    <div style="font-size: 0.7rem; color: var(--color-text-muted);" x-text="formatTime(qn.createdAt)"></div>
                </div>
                <div style="display: flex; gap: 4px; flex-shrink: 0;">
                    <button @click="promote(qn)" class="btn btn-ghost"
                            style="font-size: 0.7rem; padding: 1px 4px;"
                            title="Promote to full note">&#8593;</button>
                    <button @click="remove(qn.id)" class="btn btn-ghost"
                            style="font-size: 0.7rem; padding: 1px 4px; color: var(--color-danger);"
                            title="Delete">&times;</button>
                </div>
            </div>
        </template>
        <div x-show="items.length === 0" style="font-size: var(--text-sm); color: var(--color-text-muted); padding: var(--space-sm) 0;">
            No quick notes yet.
        </div>
    </div>
</div>

<script>
document.addEventListener('alpine:init', () => {
    Alpine.data('quicknotes', (campaignId, targetType, targetId) => ({
        items: [],
        newBody: '',
        campaignId: campaignId,
        targetType: targetType,
        targetId: targetId,

        async init() {
            await this.load();
        },

        async load() {
            try {
                const resp = await fetch(
                    '/api/v1/campaigns/' + this.campaignId + '/quicknotes?targetType='
                    + this.targetType + '&targetId=' + this.targetId);
                this.items = await resp.json();
            } catch (e) {
                console.error('Failed to load quicknotes', e);
            }
        },

        async add() {
            if (!this.newBody.trim()) return;
            const body = this.newBody.trim();
            this.newBody = '';
            try {
                const formData = new FormData();
                formData.append('targetType', this.targetType);
                formData.append('targetId', this.targetId);
                formData.append('body', body);
                const resp = await fetch(
                    '/api/v1/campaigns/' + this.campaignId + '/quicknotes',
                    { method: 'POST', body: formData });
                const qn = await resp.json();
                this.items.push(qn);
            } catch (e) {
                console.error('Failed to create quicknote', e);
            }
        },

        async remove(id) {
            try {
                await fetch('/api/v1/campaigns/' + this.campaignId + '/quicknotes/' + id,
                    { method: 'DELETE' });
                this.items = this.items.filter(i => i.id !== id);
            } catch (e) {
                console.error('Failed to delete quicknote', e);
            }
        },

        async promote(qn) {
            try {
                const resp = await fetch(
                    '/api/v1/campaigns/' + this.campaignId + '/quicknotes/' + qn.id + '/promote',
                    { method: 'POST' });
                const result = await resp.json();
                this.items = this.items.filter(i => i.id !== qn.id);
                window.location = result.url;
            } catch (e) {
                console.error('Failed to promote quicknote', e);
            }
        },

        formatTime(iso) {
            if (!iso) return '';
            const d = new Date(iso);
            return d.toLocaleString();
        }
    }));
});
</script>
</th:block>
</html>
```

**Thymeleaf+Alpine.js interpolation note:** For passing Thymeleaf variables into Alpine.js `x-data`, use Thymeleaf inline expressions:
```html
<div x-data="quicknotes('[[${campaignId}]]', '[[${targetType}]]', '[[${targetId}]]')"
```
The `[[...]]` syntax is Thymeleaf's inline expression that evaluates server-side before Alpine.js sees the HTML. The `'[[${value}]]'` wrapping with quotes ensures valid JavaScript string literals. UUID values are safe; string values with quotes would need escaping — use `#strings.replace()` if needed.

- [ ] **Step 6: Create the session log template fragment**

**Spec requirement:** Session log template (date, attendance, summary, loot, XP) to encourage consistent records (§4.7).

Create `src/main/resources/templates/notes/_session-log-template.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="sessionLogTemplate">
## Session Date: [date]

## Attendance
- 

## Summary



## Loot & Rewards
- 

## XP Awarded

| Character | XP |
|-----------|----|
| | |

## Notes



</th:block>
</html>
```

When creating a new note of type `SESSION_LOG`, the form's body textarea should be pre-filled with this template. Update the `_form.html` body textarea:

```html
<textarea id="noteBody" name="body" rows="20"
          th:text="${note != null ? note.body : (param.type != null and param.type[0] == 'SESSION_LOG' ? ~{notes/_session-log-template :: sessionLogTemplate} : '')}"
          placeholder="Write in Markdown. Use [[Note Title]] for wiki-links, [[statblock:Name]] for statblocks..."></textarea>
```

**Implementation note:** Because calling a fragment from `th:text` is unusual in Thymeleaf, a cleaner approach is to use a JavaScript snippet in the form page that detects the `SESSION_LOG` type selection and inserts the template text into the textarea via an `hx-get` or inline script. For v1, add a small inline `<script>` block in `_form.html`:

```html
<script>
document.addEventListener('DOMContentLoaded', function() {
    const typeSelect = document.getElementById('noteType');
    const bodyArea = document.getElementById('noteBody');
    if (typeSelect && bodyArea && !bodyArea.value) {
        if (typeSelect.value === 'SESSION_LOG') {
            bodyArea.value = "## Session Date: [date]\n\n## Attendance\n- \n\n## Summary\n\n\n\n## Loot & Rewards\n- \n\n## XP Awarded\n\n| Character | XP |\n|-----------|----|\n| | |\n\n## Notes\n\n";
        }
        typeSelect.addEventListener('change', function() {
            if (this.value === 'SESSION_LOG' && !bodyArea.value.trim()) {
                bodyArea.value = "## Session Date: [date]\n\n## Attendance\n- \n\n## Summary\n\n\n\n## Loot & Rewards\n- \n\n## XP Awarded\n\n| Character | XP |\n|-----------|----|\n| | |\n\n## Notes\n\n";
            }
        });
    }
});
</script>
```

Add this `<script>` block at the end of `_form.html`, inside the `<th:block th:fragment="form(...)">`.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/notes/
git commit -m "feat(m8): add note list, detail, card, form templates, quicknotes strip, and session log template"
```

---

### Task 11: Sidebar integration — session plan panel

**Files:**
- Modify: `src/main/resources/templates/fragments/sidebar.html`

- [ ] **Step 1: Replace the sidebar placeholder with a campaign-aware session plan panel**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<aside class="sidebar" th:fragment="sidebar">
    <div class="sidebar-title">Quick Access</div>

    <th:block th:if="${campaignId != null}">
        <th:block th:if="${sessionPlan != null}">
            <div class="sidebar-section">
                <h4 class="sidebar-section-title">Session Plan</h4>
                <a th:href="@{/campaigns/{cid}/notes/{nid}(cid=${campaignId}, nid=${sessionPlan.id})}"
                   th:text="${sessionPlan.title}"
                   style="color: var(--color-accent); font-size: var(--text-sm); display: block; margin-bottom: var(--space-xs);">
                    Session Plan Title
                </a>
                <div style="font-size: var(--text-sm); color: var(--color-text-muted); max-height: 150px; overflow-y: auto;"
                     th:utext="${@markdownUtil.toHtml(#strings.abbreviate(sessionPlan.body != null ? sessionPlan.body : '', 300))}">
                    Session plan preview
                </div>
            </div>
        </th:block>
        <th:block th:unless="${sessionPlan != null}">
            <p style="color: var(--color-text-muted); font-size: var(--text-sm); margin-bottom: var(--space-md);">
                No session plan yet. Create a <code>SESSION_PLAN</code> note to see it here.
            </p>
        </th:block>

        <div class="sidebar-section">
            <h4 class="sidebar-section-title">Quick Links</h4>
            <a th:href="@{/campaigns/{id}/notes(id=${campaignId})}"
               class="sidebar-link">
                &#9998; All Notes
            </a>
            <a th:href="@{/campaigns/{id}/notes?type=SESSION_PLAN(id=${campaignId})}"
               class="sidebar-link">
                &#128197; Session Plans
            </a>
            <a th:href="@{/campaigns/{id}/notes?type=QUEST(id=${campaignId})}"
               class="sidebar-link">
                &#9872; Quests
            </a>
        </div>
    </th:block>

    <th:block th:unless="${campaignId != null}">
        <p style="color: var(--color-text-muted); font-size: var(--text-sm);">
            Open a campaign to see session plans and quick links here.
        </p>
    </th:block>
</aside>

<style>
    .sidebar-section {
        margin-bottom: var(--space-md);
        padding-bottom: var(--space-md);
        border-bottom: 1px solid var(--color-border);
    }
    .sidebar-section-title {
        font-size: var(--text-sm);
        font-weight: 600;
        color: var(--color-text-muted);
        margin-bottom: var(--space-xs);
    }
    .sidebar-link {
        display: block;
        padding: var(--space-xs) var(--space-sm);
        font-size: var(--text-sm);
        color: var(--color-text-muted);
        text-decoration: none;
        border-radius: 4px;
    }
    .sidebar-link:hover {
        background: var(--color-surface-hover);
        color: var(--color-text);
    }
</style>
</aside>
</html>
```

The sidebar now depends on a `sessionPlan` model attribute. This must be populated by controllers. The simplest approach: add a `@ControllerAdvice` or update each controller. For v1, add the session plan lookup in the `NoteController`'s `@ModelAttribute` and in any other controller that renders with the sidebar. Better: create a `@ControllerAdvice`:

**Additional file:** Modify `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/HomeController.java` or create a new `SidebarAdvice.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.UUID;

@ControllerAdvice
public class SidebarAdvice {

    private final NoteService noteService;

    public SidebarAdvice(NoteService noteService) {
        this.noteService = noteService;
    }

    @ModelAttribute("sessionPlan")
    public Object sessionPlan(@ModelAttribute("campaignId") UUID campaignId) {
        if (campaignId == null) return null;
        var plans = noteService.findByCampaignIdAndType(campaignId, NoteType.SESSION_PLAN);
        return plans.isEmpty() ? null : plans.get(0); // most recent session plan
    }
}
```

Note: `@ModelAttribute("campaignId")` gets the campaignId from the model (set by controllers that have `addCampaign`). Controllers that DON'T set `campaignId` in model won't trigger this — the `UUID` will be treated as a request parameter, not found, and will be null. Actually `@ModelAttribute` without any binding source will look in the model, then request parameters. Since `campaignId` may be a path variable, we need to make sure it's in the model. A safer approach: have the `@ControllerAdvice` itself pick up the campaign context. But that's complex. For now, keep the sidebar template check `th:if="${campaignId != null}"` and the advice is optional — controllers that want a session plan in the sidebar should add it to the model explicitly.

Simpler approach: don't use a controller advice. Instead, have the NoteController's `@ModelAttribute` also add the session plan. Other controllers can add it as needed. For M8, start with the sidebar requiring explicit `sessionPlan` model attribute from controllers. The sidebar gracefully degrades when it's absent.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/fragments/sidebar.html
git commit -m "feat(m8): update sidebar with session plan panel and quick links"
```

---

### Task 12: Quicknotes strips on entity pages

**Files:**
- Modify: `src/main/resources/templates/maps/battle.html`
- Modify: `src/main/resources/templates/encounter/detail.html`
- Modify: `src/main/resources/templates/library/detail.html`
- Modify: `src/main/resources/templates/party/list.html` (optional — campaign-level quicknotes)

- [ ] **Step 1: Add quicknotes to the battle map sidebar (MAP + active ENCOUNTER)**

The spec (§4.13) requires showing both the current map's **and** the active encounter's quicknotes. In `maps/battle.html`, add quicknotes sections below the token list and above the tracker:

```html
<!-- Map quicknotes -->
<div class="battle-sidebar-section" style="padding: var(--space-sm); border-bottom: 1px solid var(--color-border);">
    <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='MAP', targetId=${map.id})}"></th:block>
</div>

<!-- Active encounter quicknotes (if an encounter is currently active) -->
<th:block th:if="${activeEncounter != null}">
<div class="battle-sidebar-section" style="padding: var(--space-sm); border-bottom: 1px solid var(--color-border);">
    <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='ENCOUNTER', targetId=${activeEncounter.id})}"></th:block>
</div>
</th:block>
```

The battle controller must add `activeEncounter` to the model. If the battle page currently uses a `@ModelAttribute` or similar, add the encounter lookup there. If no active encounter context exists yet, the `th:if` check ensures the strip gracefully hides.

- [ ] **Step 2: Add quicknotes to encounter detail**

In `encounter/detail.html`, at the bottom of the main content area:

```html
<div style="margin-top: var(--space-lg);">
    <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='ENCOUNTER', targetId=${encounter.id})}"></th:block>
</div>
```

- [ ] **Step 3: Add quicknotes to statblock detail**

In `library/detail.html`, at the bottom:

```html
<div style="margin-top: var(--space-lg);">
    <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='STATBLOCK', targetId=${statBlock.id})}"></th:block>
</div>
```

Conditionally show only when `campaignId` is present (statblock detail is also accessible without a campaign context).

Note: The statblock detail page (`library/detail.html`) may not have `campaignId` in the model — add it conditionally. If `campaignId` is not present, hide the quicknotes strip.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/maps/battle.html \
        src/main/resources/templates/encounter/detail.html \
        src/main/resources/templates/library/detail.html
git commit -m "feat(m8): add quicknotes strips to battle map, encounter detail, and statblock detail"
```

---

### Task 13: Navbar + navigation integration

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/templates/campaigns/detail.html`

- [ ] **Step 1: Add "Notes" link to campaign navbar**

In `fragments/navbar.html`, add the Notes link after the Handouts link:

```html
<a th:href="@{/campaigns/{id}/notes(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Notes</a>
```

after line 11 (the Handouts link).

- [ ] **Step 2: Add Notes section to campaign detail page**

In `campaigns/detail.html`, add after the Handouts section (or after the Encounters section), before the Edit section:

```html
<h2>Notes</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/notes(id=${campaign.id})}" class="btn btn-primary">
        Manage Notes
    </a>
</div>
```

- [ ] **Step 3: Add session plan to campaign detail sidebar by updating the controller**

Modify `CampaignController`'s detail method to add the session plan:

```java
@GetMapping("/{campaignId}")
public String detail(@PathVariable UUID campaignId, Model model) {
    Campaign campaign = campaignService.findById(campaignId);
    model.addAttribute("campaign", campaign);
    model.addAttribute("campaignId", campaignId);

    var plans = noteService.findByCampaignIdAndType(campaignId, NoteType.SESSION_PLAN);
    if (!plans.isEmpty()) {
        model.addAttribute("sessionPlan", plans.get(0));
    }

    return "campaigns/detail";
}
```

Note: This requires injecting `NoteService` into `CampaignController`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html \
        src/main/resources/templates/campaigns/detail.html \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java
git commit -m "feat(m8): integrate Notes into navbar, campaign detail, and sidebar"
```

---

### Task 14: Campaign export/import — include notes + quicknotes

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

- [ ] **Step 1: Add NoteExportDto and QuickNoteExportDto to CampaignExportDto**

In `CampaignExportDto.java`, replace `List<Object> notes` with a typed record:

```java
public record NoteExportDto(
        String type,
        String title,
        String body,
        String tags,
        boolean dmOnly
) {
    public static NoteExportDto from(dev.hendrikhoemberg.dmhelper.notes.data.Note note) {
        return new NoteExportDto(
                note.getType().name(),
                note.getTitle(),
                note.getBody(),
                note.getTags(),
                note.isDmOnly()
        );
    }
}
```

Add a second record for quicknotes:

```java
public record QuickNoteExportDto(
        String targetType,
        String targetId,
        String body,
        String createdAt
) {
    public static QuickNoteExportDto from(
            dev.hendrikhoemberg.dmhelper.notes.data.QuickNote qn,
            java.util.Map<UUID, String> idMappings) {
        String mappedTargetId = idMappings.getOrDefault(qn.getTargetId(), qn.getTargetId().toString());
        return new QuickNoteExportDto(
                qn.getTargetType(),
                mappedTargetId,
                qn.getBody(),
                qn.getCreatedAt().toString()
        );
    }
}
```

Update the `CampaignExportDto` record to use typed lists:

```java
public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<PartyMemberExportDto> party,
        List<StatBlockExportDto> statBlocks,
        List<HandoutExportDto> handouts,
        List<MapExportDto> maps,
        List<EncounterExportDto> encounters,
        List<NoteExportDto> notes,
        List<QuickNoteExportDto> quicknotes
) {
```

Note: The `handouts` field needs its own export DTO too (left as exercise — currently `List<Object>`). Create `HandoutExportDto` in the same style.

- [ ] **Step 2: Update CampaignService to include notes in export**

In `CampaignService`, inject `NoteRepository` and `QuickNoteRepository`. In the `exportToJson` method:

```java
List<CampaignExportDto.NoteExportDto> noteDtos = noteRepository
        .findByCampaignIdOrderByCreatedAtDesc(campaign.getId())
        .stream().map(CampaignExportDto.NoteExportDto::from).toList();

List<CampaignExportDto.QuickNoteExportDto> quickNoteDtos = quickNoteRepository
        .findByCampaignIdOrderByCreatedAtDesc(campaign.getId())
        .stream().map(qn -> CampaignExportDto.QuickNoteExportDto.from(
                qn, Map.of())) // no ID mapping needed for export (raw IDs)
        .toList();
```

Add these to the `CampaignExportDto` constructor call.

- [ ] **Step 3: Update CampaignService to import notes**

In the `importFromJson` method, after importing the campaign and before returning, iterate over `dto.notes()` and create notes:

```java
if (dto.notes() != null) {
    for (var noteDto : dto.notes()) {
        noteService.create(imported.getId(),
                NoteType.valueOf(noteDto.type()),
                noteDto.title(),
                noteDto.body(),
                noteDto.tags());
    }
}
```

For quicknotes during import, entity IDs are re-generated so the `targetId` references need mapping. Handle quicknotes import with a UUID mapping table (old→new) for the entities that can be targets (notes, encounters, maps, etc.).

- [ ] **Step 4: Update export/import round-trip test**

Update the existing `CampaignService` integration test (or `CampaignExportDto` test) to include notes and quicknotes in the round-trip:

```java
// In the round-trip test, after creating a campaign with notes:
Note note = noteService.create(campaign.getId(), NoteType.NPC, "Aldric", "A wizard.", "wizard");
QuickNote qn = quickNoteService.create(campaign.getId(), "NOTE", note.getId(), "Has a secret.");

// Export then import
String exportedJson = campaignService.exportToJson(campaign.getId());
Campaign imported = campaignService.importFromJson(exportedJson);

// Verify notes were imported
List<Note> importedNotes = noteRepository.findByCampaignIdOrderByCreatedAtDesc(imported.getId());
assertEquals(1, importedNotes.size());
assertEquals("Aldric", importedNotes.get(0).getTitle());
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "feat(m8): include notes and quicknotes in campaign export/import"
```

---

### Task 15: CSS styling for notes

**Files:**
- Modify: `src/main/resources/static/css/app.css`

- [ ] **Step 1: Add note-specific styles to app.css**

Append to `app.css`:

```css
/* ==============================
   Notes & Wiki (M8)
   ============================== */

.note-body {
    line-height: 1.8;
    font-size: var(--text-base);
}

.note-body h1, .note-body h2, .note-body h3 {
    color: var(--color-text);
    margin-top: var(--space-lg);
    margin-bottom: var(--space-sm);
}

.note-body h1 { font-size: var(--text-2xl); }
.note-body h2 { font-size: var(--text-xl); border-bottom: 1px solid var(--color-border); padding-bottom: var(--space-xs); }
.note-body h3 { font-size: var(--text-lg); }

.note-body p {
    margin-bottom: var(--space-md);
}

.note-body ul, .note-body ol {
    margin-bottom: var(--space-md);
    padding-left: var(--space-lg);
}

.note-body li {
    margin-bottom: var(--space-xs);
}

.note-body code {
    background: var(--color-bg);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
}

.note-body pre {
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    padding: var(--space-md);
    overflow-x: auto;
    margin-bottom: var(--space-md);
}

.note-body pre code {
    background: none;
    padding: 0;
}

.note-body blockquote {
    border-left: 3px solid var(--color-accent);
    padding-left: var(--space-md);
    color: var(--color-text-muted);
    margin-bottom: var(--space-md);
}

.note-body table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: var(--space-md);
}

.note-body th, .note-body td {
    padding: var(--space-xs) var(--space-sm);
    border: 1px solid var(--color-border);
    text-align: left;
}

.note-body th {
    background: var(--color-surface-hover);
    font-weight: 600;
}

.note-body img {
    max-width: 100%;
    border-radius: var(--radius);
}

/* Wiki-link styles */
.wiki-link {
    color: var(--color-accent);
    text-decoration: none;
    border-bottom: 1px dashed var(--color-accent);
}

.wiki-link:hover {
    color: var(--color-accent-hover);
    border-bottom-style: solid;
}

.wiki-link-resolved {
    /* default */
}

.wiki-link-broken {
    color: var(--color-danger);
    border-bottom-color: var(--color-danger);
    cursor: help;
}

/* Quicknotes strip */
.quicknotes-strip {
    padding: 0;
}

.quicknote-row:hover {
    background: var(--color-surface-hover);
}

/* Note type filter bar */
.note-type-bar {
    display: flex;
    gap: var(--space-xs);
    flex-wrap: wrap;
    margin-bottom: var(--space-md);
}

.note-type-chip {
    padding: var(--space-xs) var(--space-sm);
    font-size: var(--text-sm);
    border: 1px solid var(--color-border);
    border-radius: 20px;
    background: var(--color-surface);
    color: var(--color-text-muted);
    cursor: pointer;
    text-decoration: none;
    transition: all var(--transition);
}

.note-type-chip:hover {
    border-color: var(--color-accent);
    color: var(--color-text);
}

.note-type-chip.active {
    background: var(--color-accent);
    border-color: var(--color-accent);
    color: #fff;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(m8): add note, wiki-link, and quicknote CSS styles"
```

---

### Task 16: Integration tests and smoke test

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/notes/NoteIntegrationTest.java`

- [ ] **Step 1: Write integration test for the notes flow**

```java
package dev.hendrikhoemberg.dmhelper.notes;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class NoteIntegrationTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NoteService noteService;
    @Autowired private QuickNoteService quickNoteService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private NoteLinkRepository noteLinkRepository;
    @Autowired private QuickNoteRepository quickNoteRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Integration Test Campaign");
        campaignRepository.save(campaign);
    }

    @Test
    void fullNoteLifecycle() {
        Note note = noteService.create(campaign.getId(), NoteType.LOCATION, "The Crypt",
                "A dark crypt with [[statblock:Skeleton]] guards. See also [[The Altar]].", "dungeon");

        assertNotNull(note.getId());

        String rendered = noteService.renderBody(note);
        assertTrue(rendered.contains("wiki-link"));

        var found = noteService.search(campaign.getId(), "crypt");
        assertEquals(1, found.size());
        assertEquals("The Crypt", found.get(0).getTitle());

        noteService.update(note.getId(), NoteType.LOCATION, "The Catacombs", "Updated body.", "catacombs");
        Note updated = noteService.findById(note.getId());
        assertEquals("The Catacombs", updated.getTitle());
    }

    @Test
    void quicknotePromoteFlow() {
        UUID targetId = UUID.randomUUID();
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", targetId, "Secret door in the north wall.");

        Note promoted = quickNoteService.promoteToNote(qn.getId(), "Secret Door Discovery", NoteType.LOCATION);
        assertNotNull(promoted.getId());
        assertEquals("Secret Door Discovery", promoted.getTitle());
        assertEquals("Secret door in the north wall.", promoted.getBody());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void wikiLinksCreateBacklinks() {
        Note target = noteService.create(campaign.getId(), NoteType.NPC, "Gundren", "A dwarf.", "");
        Note source = noteService.create(campaign.getId(), NoteType.QUEST, "Find Gundren",
                "The party must find [[Gundren]].", "quest");

        var backlinks = noteService.findBacklinks(target.getId());
        assertEquals(1, backlinks.size());
        assertEquals("Find Gundren", backlinks.get(0).getTitle());
    }

    @Test
    void searchAcrossNotesAndQuicknotes() {
        noteService.create(campaign.getId(), NoteType.NPC, "Thalia", "Elven ranger.", "");
        noteService.create(campaign.getId(), NoteType.LOCATION, "Forest of Whispers", "Ancient woods.", "");
        quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "Thalia suspects something.");

        var noteResults = noteService.search(campaign.getId(), "Thalia");
        assertEquals(1, noteResults.size());
        assertEquals("Thalia", noteResults.get(0).getTitle());

        var qnResults = quickNoteRepository.searchByCampaignId(campaign.getId(), "Thalia");
        assertEquals(1, qnResults.size());
    }
}
```

- [ ] **Step 2: Run integration tests**

```bash
mvn test -pl . -Dtest=NoteIntegrationTest
```
Expected: PASS — all 4 integration tests green.

- [ ] **Step 3: Build the full application and run a manual smoke test**

```bash
mvn clean package -DskipTests
java -jar target/dmhelper-*.jar
```

Smoke test checklist:
1. Open browser, create a campaign
2. Navigate to Notes via the campaign detail page or navbar
3. Create a note with type QUEST, Markdown body containing `[[statblock:Goblin]]` and `[[Another Note]]`
4. Verify wiki-links render as links on the detail page
5. Create "Another Note" — verify the backlink appears on the first note
6. Add a quicknote on the note detail page — verify it appears
7. Promote the quicknote — verify it becomes a full note
8. Create a SESSION_PLAN note — verify it appears in the sidebar
9. Export the campaign — verify notes and quicknotes are in the JSON
10. Import the JSON into a new campaign — verify notes are present

- [ ] **Step 4: Commit any final fixes**

```bash
git add -A
git diff --cached
git commit -m "feat(m8): add integration tests for notes, wiki-links, and quicknotes"
```

---

## Cross-Cutting Concerns

### PIN gate (security)
All note routes (`/campaigns/{campaignId}/notes/**`) are under a campaign context. The PIN interceptor already protects all campaign routes (§2.3.7). Verify: `PinInterceptor` intercepts all `/**(**` paths — notes routes are automatically protected. No additional work needed.

The REST API endpoints `/api/v1/campaigns/{campaignId}/notes/**` and `/api/v1/campaigns/{campaignId}/quicknotes/**` are also under campaign paths. The PIN interceptor must also protect `/api/v1/campaigns/**`. Verify and fix if needed.

### DM Mode (player-safe)
Notes and quicknotes are inherently DM-only (`dmOnly = true` by default on Note; QuickNote is always DM-only per spec). No special player-safe projection work needed — notes never leave the DM side.

### Backups
The startup backup runner (`StartupBackupRunner`) already backs up the H2 database. No additional work needed for M8 — new tables (note, quick_note, note_link) are automatically included in the backup.

### Search across all entity types (Ctrl+K)
The global `Ctrl+K` command palette is deferred to M12. However, the REST autocomplete endpoint (`/api/v1/campaigns/{campaignId}/notes/autocomplete`) is built now so M12 can aggregate it.

### Markdown inline HTML (wiki-link rendering)
commonmark's `HtmlRenderer` by default may escape inline HTML tags. The wiki-link preprocessing inserts raw `<a href="...">` tags into markdown text before passing to `MarkdownUtil.toHtml()`. To prevent wiki-links from being rendered as escaped text, ensure `MarkdownUtil` is configured with `HtmlRenderer.builder().escapeHtml(false).build()`:

```java
@Component("markdownUtil")
public class MarkdownUtil {
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(false).build();

    public String toHtml(String markdown) {
        if (markdown == null) return "";
        return renderer.render(parser.parse(markdown));
    }
}
```

### CSS variables
The CSS additions in Task 15 reference `var(--color-accent-hover)` and `var(--transition)`. Before adding them, verify these CSS custom properties exist in the current `app.css`. If they don't, define them:

```css
:root {
    --color-accent-hover: color-mix(in srgb, var(--color-accent) 80%, white);
    --transition: 150ms ease;
}
```

### HandoutExportDto
Task 14 creates `NoteExportDto` and `QuickNoteExportDto` inside `CampaignExportDto`. The existing `List<Object> handouts` field should also be replaced with a typed `HandoutExportDto` record:

```java
public record HandoutExportDto(String title, List<String> tags) {
    public static HandoutExportDto from(dev.hendrikhoemberg.dmhelper.handout.data.Handout h) {
        return new HandoutExportDto(h.getTitle(), List.of());
    }
}
```

Wire it into `CampaignService.exportToJson()` alongside the notes DTOs.

### Lazy backlink resolution
When Note A is created with `[[Note B]]` and Note B doesn't exist yet, the NoteLink is created with `resolved=false`. When Note B is later created, Note A's link is NOT automatically updated — the backlink won't appear until Note A is manually edited and re-saved. This is a known v1 limitation. Future enhancement: on note creation/update, scan existing notes for unresolved links to the new note and mark them resolved.

---

## Dependencies Between Tasks

```
Task 1 (Note entity)
  └─> Task 5 (NoteService) ──> Task 7 (NoteController) ──> Task 10 (templates)
  └─> Task 4 (WikiLinkParser) ──> Task 5
  └─> Task 3 (NoteLink entity) ──> Task 5

Task 2 (QuickNote entity)
  └─> Task 6 (QuickNoteService) ──> Task 9 (QuickNoteApiController)
  └─> Task 10 (quicknotes strip template uses API)

Task 7 + Task 8 + Task 9 + Task 10
  └─> Task 11 (sidebar) + Task 12 (entity page strips) + Task 13 (navbar)

Task 5 + Task 6
  └─> Task 14 (export/import)

Task 10
  └─> Task 15 (CSS)

Task 1–15
  └─> Task 16 (integration tests)
```

Tasks 1–4 can run in parallel. Task 5 depends on 1+3+4. Task 6 depends on 2+5. Task 7–10 depend on 5+6. Task 11–13 depend on 7+10. Task 14 depends on 5+6. Task 15 depends on 10. Task 16 is last.
