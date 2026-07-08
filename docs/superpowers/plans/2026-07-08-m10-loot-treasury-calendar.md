# M10: Loot, Treasury & Calendar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Item assignments per PC and party stash, append-only gold + item ledger with derived running balances, attunement tracking with warnings, configurable in-game calendar with current date and advance-days, timeline events with relative markers, and in-game dates on ledger entries and session logs. Per the SPEC §4.14 and §4.15, and milestone table "Distribute a hoard to the party; 'the eclipse is in 12 days' is a timeline entry."

**Architecture:** Three new feature packages — `treasury` (ItemAssignment entity + CRUD), `ledger` (LedgerEntry entity + append-only transaction history), `calendar` (TimelineEvent entity + Campaign settings JSON calendar config). Each follows the existing `web/service/data` sub-package convention with Thymeleaf/htmx controllers. Campaign settings JSON is extended with `calendarConfig` (month names/lengths, weekday names) and `currentInGameDate` (year/month/day). Calendar operations (advance date, set date) mutate the Campaign.settings CLOB. Running gold balances are derived by summing ledger entries, never stored. Item assignments are shown on the party loot overview, per-PC sheet view, and party stash view. Timeline is displayed on the campaign overview with "in N days" relative markers computed from the current date.

**Tech Stack:** Spring Boot 4.1.0 (Jakarta EE 11), Spring Data JPA + H2, Thymeleaf + htmx, Jackson 3 (`tools.jackson`)

---

## 1. Scope

### What M10 includes

| Feature | Scope |
|---------|-------|
| ItemAssignment entity (PC/stash holder) | Full — campaign, optional partyMember (null = stash), optional magicItem/equipmentItem refs, customText, quantity, attuned |
| LedgerEntry entity (append-only) | Full — campaign, timestamp, optional inGameDate, kind (GOLD/ITEM), direction (GAIN/SPEND), amount, currency, optional itemAssignmentRef, holder (string), note |
| Treasury CRUD (htmx) | Full — assign items to PCs or stash, view by holder, toggle attunement, change quantity, delete |
| Attunement warnings | Full — per-PC count, warning at >3 attuned items (warned, not enforced) |
| Party-wide loot overview | Full — show all assignments grouped by holder, with gold balances from ledger |
| Ledger CRUD (htmx) | Full — record gold/item transactions, view history, derived running balances per holder |
| Calendar config in Campaign.settings | Full — month names/lengths, weekday names, defaults (Gregorian preset), editable via campaign settings form |
| Current in-game date | Full — stored in Campaign.settings JSON, displayed on campaign page, editable |
| Advance-days action | Full — increment current in-game date by N days, with htmx partial update |
| TimelineEvent entity | Full — campaign, inGameYear/Month/Day, title, optional body, optional noteRef |
| Timeline CRUD (htmx) | Full — create/edit/delete events, chronological list with "in N days" relative markers |
| In-game dates on ledger entries | Full — optional inGameDate fields, auto-filled from campaign's current date when recording |
| Campaign detail page integration | Full — links to Treasury, Ledger, Calendar & Timeline sections |
| Navbar integration | Full — Treasury, Ledger, Calendar & Timeline links in campaign sub-nav |
| Campaign export/import | Full — ItemAssignment, LedgerEntry, TimelineEvent included in export DTOs and import round-trip |

### What M10 does NOT include

- Presenting item descriptions as handouts (handout mechanics already exist in M7 — linking is a future enhancement)
- Dice roller integration for random treasure (M11)
- Player-visible loot (player view is DM-only in v1 — all treasury/ledger/calendar data is DM-only)
- Item search/browse from compendium when creating assignments (the form uses a `<select>` or typeahead of known items; full search/browse is an enhancement)
- Calendar event reminders or notifications (post-v1)
- Combat log → session log date auto-stamping (M8 session logs are DM-authored; adding dates is a natural follow-up, but the M10 scope is the calendar system itself)

---

## 2. Architecture Decisions

### 2.1 ItemAssignment holder: PartyMember FK, nullable = party stash

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "party_member_id")
private PartyMember partyMember; // null = party stash
```

A null `partyMember` means the item is in the "party stash" (not held by any specific PC). The UI groups assignments by holder: each party member then the stash.

### 2.2 Item references: optional FKs to MagicItem / EquipmentItem + free-text fallback

Three optional fields cover all cases:
- `magicItem` FK → MagicItem compendium (for known magic items)
- `equipmentItem` FK → EquipmentItem compendium (for standard gear)
- `customText` String (for homebrew items or one-offs like "mysterious amulet")

At least one of the three must be non-null. The UI shows the compendium name if available, otherwise the custom text.

### 2.3 LedgerEntry is append-only with derived balances

No update or delete endpoints for ledger entries. Running gold balances per holder are computed by summing all GOLD-kind entries' amounts (positive for GAIN, negative for SPEND). The balance is displayed in the UI but never stored — it's always derivable from the ledger.

The `holder` field is a free-text string (not a foreign key) because:
- Gold might be split in a single transaction ("Thia gets 10gp, party stash gets 40gp")
- The holder name outlives a deleted party member (historical accuracy)

### 2.4 In-game dates: 3 integer columns, not a JSON blob

TimelineEvent and LedgerEntry store `inGameYear`, `inGameMonth`, `inGameDay` as separate `int` columns for queryability (sort by date, compute relative days). The campaign's current date is stored as a JSON object in `Campaign.settings`, parsed at runtime:

```json
{
  "calendarConfig": {
    "monthNames": ["January", ..., "December"],
    "monthLengths": [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31],
    "weekdayNames": ["Monday", ..., "Sunday"]
  },
  "currentInGameDate": {
    "year": 1492,
    "month": 0,   // 0-indexed month (0 = first month in monthNames)
    "day": 1      // 1-indexed day of month
  },
  "levelingMode": "XP"
}
```

### 2.5 Calendar defaults: Gregorian preset

When no calendar config exists, the app uses a sensible Gregorian default. The DM can customize month names, month lengths, and weekday names. This lives in the campaign settings form, not a separate entity.

### 2.6 "In N days" computation

Given a `TimelineEvent` date (year/month/day) and the campaign's `currentInGameDate`, compute the relative day offset accounting for:
1. Same year: sum days in each month between current month and event month, plus day offsets
2. Different year (future only): compute days remaining in current year + days in event year up to the event date

The result is displayed as "today", "tomorrow", "in N days", or "N days ago" for past events. Years difference is not displayed unless > 30 days away.

### 2.7 Campaign export/import integration

ItemAssignment, LedgerEntry, and TimelineEvent are included in the campaign JSON export/import. Compendium references (magicItem, equipmentItem) are exported by sourceKey. The `holder` field on LedgerEntry is exported as-is (string). In-game dates are exported as `{year, month, day}` JSON objects.

Calendar config and current in-game date are included via the campaign's `settings` JSON — no additional DTO fields needed since they're already part of the campaign data.

### 2.8 Module boundaries

`treasury` handles only `ItemAssignment`. `ledger` handles only `LedgerEntry`. `calendar` handles `TimelineEvent` + calendar config operations. The `treasury` and `ledger` controllers are separate to keep responsibilities clean, but the treasury overview page includes a cross-section of both (item assignments + gold balances).

The party stash is a conceptual grouping, not a database entity — it's just ItemAssignments with null `partyMember`.

---

## 3. Entity Design

### 3.1 `ItemAssignment` (`treasury/data/ItemAssignment.java`)

```java
package dev.hendrikhoemberg.dmhelper.treasury.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "item_assignment")
public class ItemAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_member_id")
    private PartyMember partyMember; // null = party stash

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "magic_item_id")
    private MagicItem magicItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_item_id")
    private EquipmentItem equipmentItem;

    @Column(length = 500)
    private String customText;

    private int quantity = 1;

    private boolean attuned;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }

    public MagicItem getMagicItem() { return magicItem; }
    public void setMagicItem(MagicItem magicItem) { this.magicItem = magicItem; }

    public EquipmentItem getEquipmentItem() { return equipmentItem; }
    public void setEquipmentItem(EquipmentItem equipmentItem) { this.equipmentItem = equipmentItem; }

    public String getCustomText() { return customText; }
    public void setCustomText(String customText) { this.customText = customText; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isAttuned() { return attuned; }
    public void setAttuned(boolean attuned) { this.attuned = attuned; }

    public String getItemName() {
        if (magicItem != null) return magicItem.getName();
        if (equipmentItem != null) return equipmentItem.getName();
        return customText;
    }
}
```

### 3.2 `ItemAssignmentRepository` (`treasury/data/ItemAssignmentRepository.java`)

```java
package dev.hendrikhoemberg.dmhelper.treasury.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemAssignmentRepository extends JpaRepository<ItemAssignment, UUID> {
    List<ItemAssignment> findByCampaignIdOrderByPartyMemberAsc(UUID campaignId);
    List<ItemAssignment> findByPartyMemberId(UUID partyMemberId);
    List<ItemAssignment> findByCampaignIdAndPartyMemberIsNull(UUID campaignId);
    int countByPartyMemberIdAndAttunedTrue(UUID partyMemberId);
}
```

### 3.3 `LedgerEntry` (`ledger/data/LedgerEntry.java`)

```java
package dev.hendrikhoemberg.dmhelper.ledger.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    public enum Kind { GOLD, ITEM }
    public enum Direction { GAIN, SPEND }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    private Integer inGameYear;
    private Integer inGameMonth;
    private Integer inGameDay;

    @Enumerated(EnumType.STRING)
    private Kind kind = Kind.GOLD;

    @Enumerated(EnumType.STRING)
    private Direction direction = Direction.GAIN;

    private BigDecimal amount;

    @Column(length = 10)
    private String currency; // GP, SP, CP, PP, EP

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_assignment_id")
    private ItemAssignment itemAssignmentRef;

    @Column(length = 255)
    private String holder; // free-text: PC name, "party stash", etc.

    @Column(columnDefinition = "CLOB")
    private String note;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Integer getInGameYear() { return inGameYear; }
    public void setInGameYear(Integer inGameYear) { this.inGameYear = inGameYear; }
    public Integer getInGameMonth() { return inGameMonth; }
    public void setInGameMonth(Integer inGameMonth) { this.inGameMonth = inGameMonth; }
    public Integer getInGameDay() { return inGameDay; }
    public void setInGameDay(Integer inGameDay) { this.inGameDay = inGameDay; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ItemAssignment getItemAssignmentRef() { return itemAssignmentRef; }
    public void setItemAssignmentRef(ItemAssignment itemAssignmentRef) { this.itemAssignmentRef = itemAssignmentRef; }
    public String getHolder() { return holder; }
    public void setHolder(String holder) { this.holder = holder; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
```

### 3.4 `LedgerEntryRepository` (`ledger/data/LedgerEntryRepository.java`)

```java
package dev.hendrikhoemberg.dmhelper.ledger.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByCampaignIdOrderByTimestampDesc(UUID campaignId);

    List<LedgerEntry> findByCampaignIdAndHolderOrderByTimestampDesc(UUID campaignId, String holder);

    @Query("SELECT COALESCE(SUM(CASE WHEN le.direction = 'GAIN' THEN le.amount ELSE le.amount.negate() END), 0) " +
           "FROM LedgerEntry le WHERE le.campaign.id = :campaignId AND le.kind = 'GOLD' AND le.holder = :holder " +
           "AND le.currency = :currency")
    BigDecimal computeGoldBalance(UUID campaignId, String holder, String currency);
}
```

### 3.5 `TimelineEvent` (`calendar/data/TimelineEvent.java`)

```java
package dev.hendrikhoemberg.dmhelper.calendar.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "timeline_event")
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    private int inGameYear;
    private int inGameMonth; // 0-indexed
    private int inGameDay;   // 1-indexed

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note noteRef;

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public int getInGameYear() { return inGameYear; }
    public void setInGameYear(int inGameYear) { this.inGameYear = inGameYear; }
    public int getInGameMonth() { return inGameMonth; }
    public void setInGameMonth(int inGameMonth) { this.inGameMonth = inGameMonth; }
    public int getInGameDay() { return inGameDay; }
    public void setInGameDay(int inGameDay) { this.inGameDay = inGameDay; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Note getNoteRef() { return noteRef; }
    public void setNoteRef(Note noteRef) { this.noteRef = noteRef; }
}
```

### 3.6 `TimelineEventRepository` (`calendar/data/TimelineEventRepository.java`)

```java
package dev.hendrikhoemberg.dmhelper.calendar.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TimelineEventRepository extends JpaRepository<TimelineEvent, UUID> {
    List<TimelineEvent> findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(UUID campaignId);
}
```

---

## 4. Service Layer Design

### 4.1 `TreasuryService` (`treasury/service/TreasuryService.java`)

```java
@Service
@Transactional
public class TreasuryService {

    // Dependencies: ItemAssignmentRepository, PartyMemberRepository,
    //   MagicItemRepository, EquipmentItemRepository, CampaignRepository

    public record CreateAssignmentRequest(
        UUID campaignId, UUID partyMemberId,       // partyMemberId null = party stash
        UUID magicItemId, UUID equipmentItemId,    // at least one of magicItemId, equipmentItemId, or customText
        String customText, int quantity, boolean attuned
    ) {}

    public record AssignmentDto(
        UUID id, UUID campaignId, UUID partyMemberId, String holderName,
        UUID magicItemId, String magicItemName,
        UUID equipmentItemId, String equipmentItemName,
        String customText, int quantity, boolean attuned, String itemName
    ) {}

    AssignmentDto create(CreateAssignmentRequest request);
    AssignmentDto update(UUID id, UUID partyMemberId, int quantity, boolean attuned);
    void delete(UUID id);
    AssignmentDto toggleAttunement(UUID id);

    @Transactional(readOnly = true)
    List<AssignmentDto> findByCampaignId(UUID campaignId);

    @Transactional(readOnly = true)
    List<AssignmentDto> findByPartyMemberId(UUID partyMemberId);

    @Transactional(readOnly = true)
    List<AssignmentDto> findPartyStash(UUID campaignId);

    @Transactional(readOnly = true)
    int countAttunements(UUID partyMemberId); // for warning
}
```

The `AssignmentDto` includes a `holderName` computed from `partyMember.characterName` or `"Party Stash"` if null. The `itemName` getter delegates to `magicItem.name`, `equipmentItem.name`, or `customText`.

The `toggleAttunement` method flips the boolean and returns the updated DTO. If attunement count exceeds 3 after toggling, the DTO includes a `warning` field (or the UI checks the count separately).

### 4.2 `LedgerService` (`ledger/service/LedgerService.java`)

```java
@Service
@Transactional
public class LedgerService {

    // Dependencies: LedgerEntryRepository, CampaignRepository, ledgerEntryRepository

    public record CreateLedgerEntryRequest(
        UUID campaignId, LedgerEntry.Kind kind, LedgerEntry.Direction direction,
        BigDecimal amount, String currency,
        String holder, String note,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay
    ) {}

    public record LedgerEntryDto(
        UUID id, UUID campaignId, Instant timestamp,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay,
        String kind, String direction, BigDecimal amount, String currency,
        String holder, String note
    ) {}

    public record HolderBalance(String holder, BigDecimal balance, String currency) {}

    LedgerEntryDto create(CreateLedgerEntryRequest request);

    @Transactional(readOnly = true)
    List<LedgerEntryDto> findByCampaignId(UUID campaignId);

    @Transactional(readOnly = true)
    List<LedgerEntryDto> findByCampaignAndHolder(UUID campaignId, String holder);

    @Transactional(readOnly = true)
    BigDecimal computeGoldBalance(UUID campaignId, String holder, String currency);

    @Transactional(readOnly = true)
    List<HolderBalance> computeAllGoldBalances(UUID campaignId);
}
```

`create()` auto-fills `inGameYear/Month/Day` from the campaign's current date if not provided. The `computeAllGoldBalances` method returns balances for all distinct holder+currency pairs in the ledger.

No update or delete — append-only per the spec.

### 4.3 `CalendarService` (`calendar/service/CalendarService.java`)

```java
@Service
@Transactional
public class CalendarService {

    // Dependencies: CampaignRepository, TimelineEventRepository,
    //   NoteRepository, ObjectMapper (Jackson 3)

    public record CalendarConfig(int[] monthLengths, String[] monthNames, String[] weekdayNames) {}

    public record InGameDate(int year, int month, int day) {}

    public record TimelineEventDto(
        UUID id, UUID campaignId, int inGameYear, int inGameMonth, int inGameDay,
        String title, String body, UUID noteId, String relativeLabel
    ) {}

    // --- Calendar Config ---

    CalendarConfig getCalendarConfig(UUID campaignId); // returns configured or default
    void updateCalendarConfig(UUID campaignId, CalendarConfig config);

    // --- Current Date ---

    InGameDate getCurrentDate(UUID campaignId);
    void setCurrentDate(UUID campaignId, InGameDate date);
    InGameDate advanceDays(UUID campaignId, int days); // returns new date

    // --- Timeline Events ---

    TimelineEventDto createEvent(UUID campaignId, InGameDate date, String title, String body, UUID noteId);
    TimelineEventDto updateEvent(UUID id, InGameDate date, String title, String body, UUID noteId);
    void deleteEvent(UUID id);

    @Transactional(readOnly = true)
    List<TimelineEventDto> findByCampaignId(UUID campaignId); // sorted chrono, with relative labels
}
```

The `CalendarService` reads/writes `Campaign.settings` JSON. It uses the Jackson `ObjectMapper` to parse and update the settings map, preserving existing keys (`levelingMode`, etc.).

**Default calendar config** (Gregorian, in a static constant):

```java
public static final CalendarConfig DEFAULT_CALENDAR = new CalendarConfig(
    new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
    new String[]{"January", "February", "March", "April", "May", "June",
                 "July", "August", "September", "October", "November", "December"},
    new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}
);
```

**Relative label computation** (`computeRelativeLabel(currentDate, eventDate) -> String`):

```
days = daysBetween(currentDate, eventDate)
if days < 0: "N days ago"  (where N = abs(days))
if days == 0: "today"
if days == 1: "tomorrow"
else: "in N days"
```

**Days-between calculation** converts each date to a day-of-era integer (sum of days in all prior years + days in prior months of that year + day offset). Year length is assumed as 365 days for simplicity — the calendar is configurable per campaign but the computation only needs to be internally consistent with the month lengths from the config.

### 4.4 `CalendarService` settings JSON manipulation

```java
private Map<String, Object> readSettings(UUID campaignId) {
    Campaign c = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign not found"));
    if (c.getSettings() == null || c.getSettings().isBlank()) return new HashMap<>();
    try {
        return objectMapper.readValue(c.getSettings(), Map.class);
    } catch (Exception e) {
        return new HashMap<>();
    }
}

private void writeSettings(UUID campaignId, Map<String, Object> settings) {
    Campaign c = campaignRepository.findById(campaignId).orElseThrow();
    try {
        c.setSettings(objectMapper.writeValueAsString(settings));
        campaignRepository.save(c);
    } catch (Exception e) {
        throw new RuntimeException("Failed to save settings", e);
    }
}
```

---

## 5. Template Design

### 5.1 Treasury templates (`templates/treasury/`)

```
templates/treasury/
├── list.html           Full party loot overview: grouped by holder,
│                       each with item list + gold balance + attunement count.
│                       Include the "add item" form at top.
├── _card.html          Single assignment card (item name, holder, quantity,
│                       attuned badge, action buttons: attune, edit qty, delete)
├── _form.html          Add item form: select holder (PC or stash), compendium
│                       item select or custom text, quantity, attuned checkbox
├── _attunement-warn.html  Warning banner shown when >3 attuned items
└── _holder-section.html   A grouped section for one holder: "Thia's Items" or
│                       "Party Stash" with gold balance line
```

### 5.2 Ledger templates (`templates/ledger/`)

```
templates/ledger/
├── list.html           Full transaction history with running balance per holder.
│                       "Record transaction" form at top.
├── _card.html          Single ledger entry card (timestamp, kind/direction badge,
│                       amount, holder, note, in-game date if set)
├── _form.html          Record transaction form: kind (GOLD/ITEM), direction
│                       (GAIN/SPEND), amount, currency, holder, note, optional
│                       in-game date (pre-filled from campaign current date)
└── _balance.html       Running balance display per holder (refreshable fragment)
```

### 5.3 Calendar templates (`templates/calendar/`)

```
templates/calendar/
├── overview.html       Calendar + timeline combined page: current date display,
│                       advance-days control, timeline list, calendar config
│                       button, add event form
├── _current-date.html  Current date display fragment (for htmx refresh after
│                       advance/set)
├── _config-form.html   Calendar config form (month names/lengths, weekday names)
├── _timeline-list.html Sorted timeline events list with relative labels
├── _event-card.html    Single timeline event card (date, title, body, relative
│                       label, wiki-link if noteRef, edit/delete buttons)
└── _event-form.html    Create/edit timeline event form (date, title, body, noteRef)
```

### 5.4 Integration fragments in existing templates

**Campaign detail page (`campaigns/detail.html`)** — add after the "Notes" section:

```html
<h2>Treasury & Loot</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/treasury(id=${campaign.id})}" class="btn btn-primary">
        Manage Loot
    </a>
</div>

<h2>Ledger</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/ledger(id=${campaign.id})}" class="btn btn-primary">
        View Ledger
    </a>
</div>

<h2>Calendar & Timeline</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/calendar(id=${campaign.id})}" class="btn btn-primary">
        Open Calendar
    </a>
</div>
```

**Navbar (`fragments/navbar.html`)** — add after the Notes link:

```html
<a th:href="@{/campaigns/{id}/treasury(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Treasury</a>
<a th:href="@{/campaigns/{id}/ledger(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Ledger</a>
<a th:href="@{/campaigns/{id}/calendar(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Calendar</a>
```

**Sheet detail page (`sheet/detail.html`)** — add a section for "Equipment & Loot" that htmx-loads from `/campaigns/{campaignId}/treasury/party/{memberId}` fragment:

```html
<section id="sheet-loot"
         hx-get="@{/campaigns/{campaignId}/treasury/fragment/{memberId}(campaignId=${campaignId}, memberId=${partyMember.id})}"
         hx-trigger="load">
    <div class="loading">Loading items...</div>
</section>
```

---

## 6. Controller Design

### 6.1 `TreasuryController` (`treasury/web/TreasuryController.java`)

```java
@Controller
@RequestMapping("/campaigns/{campaignId}/treasury")
public class TreasuryController {

    // Dependencies: TreasuryService, LedgerService, CampaignRepository, PartyMemberRepository

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        // adds campaign, campaignId, partyMembers (for holder select dropdown)
    }

    @GetMapping
    String overview(@PathVariable UUID campaignId, Model model);
    // Returns "treasury/list" with assignments grouped by holder + gold balances

    @GetMapping("/party/{memberId}")
    String partyMemberItems(@PathVariable UUID campaignId, @PathVariable UUID memberId, Model model);
    // Returns "treasury/list" filtered to one party member

    @GetMapping("/fragment/{memberId}")
    String partyMemberItemsFragment(@PathVariable UUID campaignId, @PathVariable UUID memberId, Model model);
    // Returns "treasury/_holder-section" fragment for htmx load from sheet page

    @GetMapping("/new")
    String newForm(@PathVariable UUID campaignId, Model model);
    // Returns "treasury/_form :: form" fragment

    @PostMapping
    String create(@PathVariable UUID campaignId, CreateAssignmentRequest request, Model model);
    // Creates assignment, returns "treasury/_holder-section" for the updated holder

    @PutMapping("/{id}/{action}")
    String action(@PathVariable UUID campaignId, @PathVariable UUID id,
                  @PathVariable String action, // "attune", "qty"
                  @RequestParam(required = false) Integer quantity,
                  Model model);
    // Toggle attunement or update quantity, returns updated card fragment

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id);
    // 200 OK (htmx removes the element via hx-target)
}
```

### 6.2 `LedgerController` (`ledger/web/LedgerController.java`)

```java
@Controller
@RequestMapping("/campaigns/{campaignId}/ledger")
public class LedgerController {

    // Dependencies: LedgerService, CalendarService, CampaignRepository

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) { ... }

    @GetMapping
    String list(@PathVariable UUID campaignId, Model model);
    // Returns "ledger/list" with entries + balances

    @GetMapping("/new")
    String newForm(@PathVariable UUID campaignId, Model model);
    // Returns "ledger/_form :: form" fragment with current date pre-filled

    @PostMapping
    String create(@PathVariable UUID campaignId, CreateLedgerEntryRequest request, Model model);
    // Creates entry, re-renders list with updated balances
}
```

### 6.3 `CalendarController` (`calendar/web/CalendarController.java`)

```java
@Controller
@RequestMapping("/campaigns/{campaignId}/calendar")
public class CalendarController {

    // Dependencies: CalendarService, CampaignRepository

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) { ... }

    @GetMapping
    String overview(@PathVariable UUID campaignId, Model model);
    // Returns "calendar/overview" with current date, timeline, config

    @PostMapping("/advance")
    String advance(@PathVariable UUID campaignId, @RequestParam(defaultValue = "1") int days, Model model);
    // Advances the date by N days, returns calendar/overview (or just _current-date fragment)

    @PostMapping("/set-date")
    String setDate(@PathVariable UUID campaignId,
                   @RequestParam int year, @RequestParam int month, @RequestParam int day,
                   Model model);
    // Sets current date directly, returns overview

    // --- Config ---

    @GetMapping("/config")
    String configForm(@PathVariable UUID campaignId, Model model);
    // Returns "calendar/_config-form" fragment

    @PostMapping("/config")
    String updateConfig(@PathVariable UUID campaignId, CalendarConfig config, Model model);
    // Saves calendar config, returns overview

    // --- Timeline Events ---

    @GetMapping("/events/new")
    String newEventForm(@PathVariable UUID campaignId, Model model);
    // Returns "calendar/_event-form :: form" fragment

    @PostMapping("/events")
    String createEvent(@PathVariable UUID campaignId,
                       @RequestParam int year, @RequestParam int month, @RequestParam int day,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) UUID noteId, Model model);
    // Creates event, returns updated _timeline-list

    @GetMapping("/events/{eventId}/edit")
    String editEventForm(@PathVariable UUID campaignId, @PathVariable UUID eventId, Model model);

    @PutMapping("/events/{eventId}")
    String updateEvent(@PathVariable UUID campaignId, @PathVariable UUID eventId,
                       @RequestParam int year, @RequestParam int month, @RequestParam int day,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) UUID noteId, Model model);

    @DeleteMapping("/events/{eventId}")
    ResponseEntity<Void> deleteEvent(@PathVariable UUID campaignId, @PathVariable UUID eventId);
}
```

---

## 7. Campaign Export/Import Integration

### 7.1 DTO additions to `CampaignExportDto`

Add three new nested records and include them in the top-level record:

```java
public record CampaignExportDto(
    int formatVersion,
    CampaignDto campaign,
    List<PartyMemberExportDto> party,
    List<StatBlockExportDto> statBlocks,
    List<HandoutExportDto> handouts,
    List<MapExportDto> maps,
    List<Object> encounters,
    List<NoteExportDto> notes,
    List<QuickNoteExportDto> quicknotes,
    List<AssignmentExportDto> assignments,      // NEW
    List<LedgerExportDto> ledger,               // NEW
    List<TimelineExportDto> timeline             // NEW
) {
    // ...

    public record AssignmentExportDto(
        UUID id, String holderName,             // null for stash
        String magicItemKey,                     // sourceKey of MagicItem, null if none
        String equipmentItemKey,                 // sourceKey of EquipmentItem, null if none
        String customText,
        int quantity,
        boolean attuned
    ) {}

    public record LedgerExportDto(
        UUID id, Instant timestamp,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay,
        String kind, String direction,
        BigDecimal amount, String currency,
        String holder, String note
    ) {}

    public record TimelineExportDto(
        UUID id, int inGameYear, int inGameMonth, int inGameDay,
        String title, String body, String noteTitle  // noteTitle for reference resolution
    ) {}

    // Update all factory methods to include empty lists for new fields
    public static CampaignExportDto from(Campaign, party, statBlocks, maps) {
        return new CampaignExportDto(..., List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
    // ... update all other from() overloads similarly
}
```

### 7.2 Export logic additions in `CampaignService.exportToJson()`

After existing export sections, add:

```java
var assignments = assignmentRepo.findByCampaignIdOrderByPartyMemberAsc(id).stream()
    .map(a -> new CampaignExportDto.AssignmentExportDto(
        a.getId(),
        a.getPartyMember() != null ? a.getPartyMember().getCharacterName() : null,
        a.getMagicItem() != null ? a.getMagicItem().getSourceKey() : null,
        a.getEquipmentItem() != null ? a.getEquipmentItem().getSourceKey() : null,
        a.getCustomText(), a.getQuantity(), a.isAttuned()))
    .toList();

var ledgerEntries = ledgerEntryRepo.findByCampaignIdOrderByTimestampDesc(id).stream()
    .map(le -> new CampaignExportDto.LedgerExportDto(
        le.getId(), le.getTimestamp(),
        le.getInGameYear(), le.getInGameMonth(), le.getInGameDay(),
        le.getKind().name(), le.getDirection().name(),
        le.getAmount(), le.getCurrency(),
        le.getHolder(), le.getNote()))
    .toList();

var timelineEvents = timelineEventRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(id).stream()
    .map(te -> new CampaignExportDto.TimelineExportDto(
        te.getId(), te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(),
        te.getTitle(), te.getBody(),
        te.getNoteRef() != null ? te.getNoteRef().getTitle() : null))
    .toList();
```

Pass these to the `CampaignExportDto` constructor.

### 7.3 Import logic additions in `CampaignService.importFromJson()`

After existing import sections, add:

```java
if (dto.assignments() != null) {
    for (var aDto : dto.assignments()) {
        ItemAssignment ia = new ItemAssignment();
        ia.setCampaign(saved);
        // Resolve party member by name from the imported party
        if (aDto.holderName() != null) {
            partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId())
                .stream().filter(pm -> pm.getCharacterName().equals(aDto.holderName()))
                .findFirst().ifPresent(ia::setPartyMember);
        }
        // Resolve magic/equipment items by sourceKey
        if (aDto.magicItemKey() != null) {
            MagicItem mi = magicItemRepo.findBySourceKey(aDto.magicItemKey()).orElse(null);
            if (mi != null) ia.setMagicItem(mi);
            else System.err.println("WARNING: Unknown magic item key: " + aDto.magicItemKey());
        }
        if (aDto.equipmentItemKey() != null) {
            EquipmentItem ei = equipmentItemRepo.findBySourceKey(aDto.equipmentItemKey()).orElse(null);
            if (ei != null) ia.setEquipmentItem(ei);
            else System.err.println("WARNING: Unknown equipment item key: " + aDto.equipmentItemKey());
        }
        ia.setCustomText(aDto.customText());
        ia.setQuantity(aDto.quantity());
        ia.setAttuned(aDto.attuned());
        assignmentRepo.save(ia);
    }
}

if (dto.ledger() != null) {
    for (var leDto : dto.ledger()) {
        LedgerEntry le = new LedgerEntry();
        le.setCampaign(saved);
        le.setTimestamp(leDto.timestamp());
        le.setInGameYear(leDto.inGameYear());
        le.setInGameMonth(leDto.inGameMonth());
        le.setInGameDay(leDto.inGameDay());
        le.setKind(LedgerEntry.Kind.valueOf(leDto.kind()));
        le.setDirection(LedgerEntry.Direction.valueOf(leDto.direction()));
        le.setAmount(leDto.amount());
        le.setCurrency(leDto.currency());
        le.setHolder(leDto.holder());
        le.setNote(leDto.note());
        ledgerEntryRepo.save(le);
    }
}

if (dto.timeline() != null) {
    for (var teDto : dto.timeline()) {
        TimelineEvent te = new TimelineEvent();
        te.setCampaign(saved);
        te.setInGameYear(teDto.inGameYear());
        te.setInGameMonth(teDto.inGameMonth());
        te.setInGameDay(teDto.inGameDay());
        te.setTitle(teDto.title());
        te.setBody(teDto.body());
        // Resolve noteRef by title from imported notes
        if (teDto.noteTitle() != null) {
            noteRepository.findByCampaignIdAndTitle(saved.getId(), teDto.noteTitle())
                .ifPresent(te::setNoteRef);
        }
        timelineEventRepo.save(te);
    }
}
```

---

## 8. Testing Strategy

| Test | Type | What it verifies |
|------|------|------------------|
| `TreasuryServiceTest` | Unit (`@DataJpaTest`) | CRUD for ItemAssignment, attunement count, stash vs PC holder |
| `LedgerServiceTest` | Unit (`@DataJpaTest`) | Create ledger entries, compute gold balances per holder |
| `CalendarServiceTest` | Unit (`@DataJpaTest`) | Read/write campaign settings JSON, advance days across month/year boundaries, relative label computation, timeline CRUD |
| `CampaignImportExportRoundTripTest` | Integration | Add assignments, ledger entries, and timeline events to the round-trip test; verify they survive export→import |
| `TreasuryControllerTest` | Web MVC | htmx form submission creates assignment, delete returns 200 |
| `LedgerControllerTest` | Web MVC | create ledger entry, list shows entries in order |
| `CalendarControllerTest` | Web MVC | advance days updates display, timeline event CRUD |

---

## 9. File Structure Summary

### New files

```
src/main/java/dev/hendrikhoemberg/dmhelper/treasury/
├── data/
│   ├── ItemAssignment.java
│   └── ItemAssignmentRepository.java
├── service/
│   └── TreasuryService.java
└── web/
    └── TreasuryController.java

src/main/java/dev/hendrikhoemberg/dmhelper/ledger/
├── data/
│   ├── LedgerEntry.java
│   └── LedgerEntryRepository.java
├── service/
│   └── LedgerService.java
└── web/
    └── LedgerController.java

src/main/java/dev/hendrikhoemberg/dmhelper/calendar/
├── data/
│   ├── TimelineEvent.java
│   └── TimelineEventRepository.java
├── service/
│   └── CalendarService.java
└── web/
    └── CalendarController.java

src/main/resources/templates/treasury/
├── list.html
├── _card.html
├── _form.html
├── _attunement-warn.html
└── _holder-section.html

src/main/resources/templates/ledger/
├── list.html
├── _card.html
├── _form.html
└── _balance.html

src/main/resources/templates/calendar/
├── overview.html
├── _current-date.html
├── _config-form.html
├── _timeline-list.html
├── _event-card.html
└── _event-form.html

src/test/java/dev/hendrikhoemberg/dmhelper/treasury/service/TreasuryServiceTest.java
src/test/java/dev/hendrikhoemberg/dmhelper/ledger/service/LedgerServiceTest.java
src/test/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarServiceTest.java
src/test/java/dev/hendrikhoemberg/dmhelper/treasury/web/TreasuryControllerTest.java
src/test/java/dev/hendrikhoemberg/dmhelper/ledger/web/LedgerControllerTest.java
src/test/java/dev/hendrikhoemberg/dmhelper/calendar/web/CalendarControllerTest.java
```

### Modified files

```
src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java
    - Add AssignmentExportDto, LedgerExportDto, TimelineExportDto records
    - Add new fields to top-level record
    - Update all factory methods

src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
    - Inject ItemAssignmentRepository, LedgerEntryRepository, TimelineEventRepository,
      MagicItemRepository, EquipmentItemRepository
    - Add export logic for assignments, ledger, timeline
    - Add import logic for assignments, ledger, timeline

src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java
    - No code changes needed (export/import work through CampaignService)

src/main/resources/templates/campaigns/detail.html
    - Add Treasury, Ledger, Calendar & Timeline sections with links

src/main/resources/templates/fragments/navbar.html
    - Add Treasury, Ledger, Calendar links in campaign sub-nav

src/main/resources/templates/sheet/detail.html
    - Add Equipment & Loot section (htmx lazy-load from treasury)

src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
    - Add round-trip test cases for assignments, ledger entries, timeline events

src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java
    - Add import/export assertions for new DTO fields
```

---

## 10. Task Breakdown

### Task 1: Treasury entities and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/data/ItemAssignment.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/data/ItemAssignmentRepository.java`

- [ ] **Step 1: Create ItemAssignment entity**

Create `treasury/data/ItemAssignment.java` with the code from §3.1.

- [ ] **Step 2: Create ItemAssignmentRepository**

Create `treasury/data/ItemAssignmentRepository.java` with the code from §3.2.

- [ ] **Step 3: Verify app compiles and starts**

```bash
mvn -q compile && timeout 10 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK" — tables created by ddl-auto, app starts.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/treasury/
git commit -m "feat(m10): add ItemAssignment entity and repository"
```

---

### Task 2: Ledger entities and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntry.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java`

- [ ] **Step 1: Create LedgerEntry entity**

Create `ledger/data/LedgerEntry.java` with the code from §3.3.

- [ ] **Step 2: Create LedgerEntryRepository**

Create `ledger/data/LedgerEntryRepository.java` with the code from §3.4.

- [ ] **Step 3: Verify app compiles and starts**

```bash
mvn -q compile && timeout 10 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK"

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/ledger/
git commit -m "feat(m10): add LedgerEntry entity and repository"
```

---

### Task 3: Calendar entities and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/data/TimelineEvent.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/data/TimelineEventRepository.java`

- [ ] **Step 1: Create TimelineEvent entity**

Create `calendar/data/TimelineEvent.java` with the code from §3.5.

- [ ] **Step 2: Create TimelineEventRepository**

Create `calendar/data/TimelineEventRepository.java` with the code from §3.6.

- [ ] **Step 3: Verify app compiles and starts**

```bash
mvn -q compile && timeout 10 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK"

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/calendar/
git commit -m "feat(m10): add TimelineEvent entity and repository"
```

---

### Task 4: TreasuryService — CRUD and attunement

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/service/TreasuryService.java`

- [ ] **Step 1: Write TreasuryService**

Create `treasury/service/TreasuryService.java` implementing the interface from §4.1. The service should:

```java
package dev.hendrikhoemberg.dmhelper.treasury.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TreasuryService {

    private final ItemAssignmentRepository repository;
    private final PartyMemberRepository partyMemberRepository;
    private final MagicItemRepository magicItemRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final CampaignRepository campaignRepository;

    public TreasuryService(ItemAssignmentRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           MagicItemRepository magicItemRepository,
                           EquipmentItemRepository equipmentItemRepository,
                           CampaignRepository campaignRepository) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.magicItemRepository = magicItemRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.campaignRepository = campaignRepository;
    }

    public record CreateAssignmentRequest(
        UUID campaignId, UUID partyMemberId, UUID magicItemId, UUID equipmentItemId,
        String customText, int quantity, boolean attuned
    ) {}

    public record AssignmentDto(
        UUID id, UUID campaignId, UUID partyMemberId, String holderName,
        UUID magicItemId, String magicItemName,
        UUID equipmentItemId, String equipmentItemName,
        String customText, int quantity, boolean attuned, String itemName
    ) {
        public static AssignmentDto from(ItemAssignment a) {
            return new AssignmentDto(
                a.getId(), a.getCampaign().getId(),
                a.getPartyMember() != null ? a.getPartyMember().getId() : null,
                a.getPartyMember() != null ? a.getPartyMember().getCharacterName() : "Party Stash",
                a.getMagicItem() != null ? a.getMagicItem().getId() : null,
                a.getMagicItem() != null ? a.getMagicItem().getName() : null,
                a.getEquipmentItem() != null ? a.getEquipmentItem().getId() : null,
                a.getEquipmentItem() != null ? a.getEquipmentItem().getName() : null,
                a.getCustomText(), a.getQuantity(), a.isAttuned(), a.getItemName()
            );
        }
    }

    public AssignmentDto create(CreateAssignmentRequest req) {
        Campaign campaign = campaignRepository.findById(req.campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        if (req.magicItemId == null && req.equipmentItemId == null
            && (req.customText == null || req.customText.isBlank())) {
            throw new IllegalArgumentException("At least one of magicItemId, equipmentItemId, or customText is required");
        }
        ItemAssignment a = new ItemAssignment();
        a.setCampaign(campaign);
        if (req.partyMemberId != null) {
            a.setPartyMember(partyMemberRepository.findById(req.partyMemberId)
                    .orElseThrow(() -> new NotFoundException("Party member not found")));
        }
        if (req.magicItemId != null) {
            a.setMagicItem(magicItemRepository.findById(req.magicItemId)
                    .orElseThrow(() -> new NotFoundException("Magic item not found")));
        }
        if (req.equipmentItemId != null) {
            a.setEquipmentItem(equipmentItemRepository.findById(req.equipmentItemId)
                    .orElseThrow(() -> new NotFoundException("Equipment item not found")));
        }
        a.setCustomText(req.customText);
        a.setQuantity(req.quantity);
        a.setAttuned(req.attuned);
        return AssignmentDto.from(repository.save(a));
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByPartyMemberAsc(campaignId).stream()
                .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findByPartyMemberId(UUID partyMemberId) {
        return repository.findByPartyMemberId(partyMemberId).stream()
                .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findPartyStash(UUID campaignId) {
        return repository.findByCampaignIdAndPartyMemberIsNull(campaignId).stream()
                .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentDto findById(UUID id) {
        return repository.findById(id)
                .map(AssignmentDto::from)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
    }

    public AssignmentDto update(UUID id, UUID partyMemberId, int quantity, boolean attuned) {
        ItemAssignment a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
        if (partyMemberId != null) {
            a.setPartyMember(partyMemberRepository.findById(partyMemberId)
                    .orElseThrow(() -> new NotFoundException("Party member not found")));
        } else {
            a.setPartyMember(null);
        }
        a.setQuantity(Math.max(1, quantity));
        a.setAttuned(attuned);
        return AssignmentDto.from(repository.save(a));
    }

    public AssignmentDto toggleAttunement(UUID id) {
        ItemAssignment a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
        a.setAttuned(!a.isAttuned());
        return AssignmentDto.from(repository.save(a));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Item assignment not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public int countAttunements(UUID partyMemberId) {
        return repository.countByPartyMemberIdAndAttunedTrue(partyMemberId);
    }
}
```

- [ ] **Step 2: Verify compiles**

```bash
mvn -q compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/treasury/service/
git commit -m "feat(m10): add TreasuryService with CRUD and attunement tracking"
```

---

### Task 5: LedgerService — append-only with balance computation

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/service/LedgerService.java`

- [ ] **Step 1: Write LedgerService**

Create `ledger/service/LedgerService.java` implementing the interface from §4.2.

```java
package dev.hendrikhoemberg.dmhelper.ledger.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LedgerService {

    private final LedgerEntryRepository repository;
    private final CampaignRepository campaignRepository;

    public LedgerService(LedgerEntryRepository repository,
                         CampaignRepository campaignRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
    }

    public record CreateLedgerEntryRequest(
        UUID campaignId, String kind, String direction,
        BigDecimal amount, String currency,
        String holder, String note,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay
    ) {}

    public record LedgerEntryDto(
        UUID id, UUID campaignId, Instant timestamp,
        Integer inGameYear, Integer inGameMonth, Integer inGameDay,
        String kind, String direction, BigDecimal amount, String currency,
        String holder, String note
    ) {
        public static LedgerEntryDto from(LedgerEntry le) {
            return new LedgerEntryDto(
                le.getId(), le.getCampaign().getId(),
                le.getTimestamp(),
                le.getInGameYear(), le.getInGameMonth(), le.getInGameDay(),
                le.getKind().name(), le.getDirection().name(),
                le.getAmount(), le.getCurrency(),
                le.getHolder(), le.getNote()
            );
        }
    }

    public record HolderBalance(String holder, BigDecimal balance, String currency) {}

    public LedgerEntryDto create(CreateLedgerEntryRequest req) {
        if (req.holder == null || req.holder.isBlank()) {
            throw new IllegalArgumentException("Holder is required");
        }
        Campaign campaign = campaignRepository.findById(req.campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        LedgerEntry le = new LedgerEntry();
        le.setCampaign(campaign);
        le.setKind(LedgerEntry.Kind.valueOf(req.kind.toUpperCase()));
        le.setDirection(LedgerEntry.Direction.valueOf(req.direction.toUpperCase()));
        le.setAmount(req.amount != null ? req.amount : BigDecimal.ZERO);
        le.setCurrency(req.currency != null ? req.currency.toUpperCase() : "GP");
        le.setHolder(req.holder.trim());
        le.setNote(req.note);
        le.setInGameYear(req.inGameYear);
        le.setInGameMonth(req.inGameMonth);
        le.setInGameDay(req.inGameDay);
        return LedgerEntryDto.from(repository.save(le));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByTimestampDesc(campaignId).stream()
                .map(LedgerEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> findByCampaignAndHolder(UUID campaignId, String holder) {
        return repository.findByCampaignIdAndHolderOrderByTimestampDesc(campaignId, holder).stream()
                .map(LedgerEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal computeGoldBalance(UUID campaignId, String holder, String currency) {
        BigDecimal balance = repository.computeGoldBalance(campaignId, holder,
                currency != null ? currency.toUpperCase() : "GP");
        return balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<HolderBalance> computeAllGoldBalances(UUID campaignId) {
        var entries = repository.findByCampaignIdOrderByTimestampDesc(campaignId);
        Map<String, Map<String, BigDecimal>> acc = new LinkedHashMap<>();
        for (var le : entries) {
            if (le.getKind() != LedgerEntry.Kind.GOLD) continue;
            String key = le.getHolder();
            String cur = le.getCurrency() != null ? le.getCurrency() : "GP";
            acc.computeIfAbsent(key, k -> new LinkedHashMap<>());
            var inner = acc.get(key);
            BigDecimal delta = le.getAmount() != null ? le.getAmount() : BigDecimal.ZERO;
            if (le.getDirection() == LedgerEntry.Direction.SPEND) {
                delta = delta.negate();
            }
            inner.merge(cur, delta, BigDecimal::add);
        }
        List<HolderBalance> result = new ArrayList<>();
        for (var outer : acc.entrySet()) {
            for (var inner : outer.getValue().entrySet()) {
                result.add(new HolderBalance(outer.getKey(),
                        inner.getValue().setScale(2, RoundingMode.HALF_UP), inner.getKey()));
            }
        }
        return result;
    }
}
```

- [ ] **Step 2: Verify compiles**

```bash
mvn -q compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/ledger/service/
git commit -m "feat(m10): add LedgerService with append-only entries and balance computation"
```

---

### Task 6: CalendarService — calendar config, date management, timeline

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java`

- [ ] **Step 1: Write CalendarService**

Create `calendar/service/CalendarService.java` implementing the interface from §4.3.

```java
package dev.hendrikhoemberg.dmhelper.calendar.service;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class CalendarService {

    public static final CalendarConfig DEFAULT_CALENDAR = new CalendarConfig(
        new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
        new String[]{"January", "February", "March", "April", "May", "June",
                     "July", "August", "September", "October", "November", "December"},
        new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}
    );

    private static final int YEAR_DAYS = 365;

    private final CampaignRepository campaignRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    public CalendarService(CampaignRepository campaignRepository,
                           TimelineEventRepository timelineEventRepository,
                           NoteRepository noteRepository,
                           ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.noteRepository = noteRepository;
        this.objectMapper = objectMapper;
    }

    public record CalendarConfig(int[] monthLengths, String[] monthNames, String[] weekdayNames) {
        @Override public boolean equals(Object o) {
            if (!(o instanceof CalendarConfig other)) return false;
            return Arrays.equals(monthLengths, other.monthLengths)
                && Arrays.equals(monthNames, other.monthNames)
                && Arrays.equals(weekdayNames, other.weekdayNames);
        }
        @Override public int hashCode() { return Objects.hash(Arrays.hashCode(monthLengths), Arrays.hashCode(monthNames), Arrays.hashCode(weekdayNames)); }
    }

    public record InGameDate(int year, int month, int day) {}

    public record TimelineEventDto(
        UUID id, UUID campaignId, int inGameYear, int inGameMonth, int inGameDay,
        String title, String body, UUID noteId, String relativeLabel
    ) {
        public static TimelineEventDto from(TimelineEvent te, String relativeLabel) {
            return new TimelineEventDto(
                te.getId(), te.getCampaign().getId(),
                te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(),
                te.getTitle(), te.getBody(),
                te.getNoteRef() != null ? te.getNoteRef().getId() : null,
                relativeLabel
            );
        }
    }

    // --- Calendar Config ---

    public CalendarConfig getCalendarConfig(UUID campaignId) {
        Map<String, Object> settings = readSettings(campaignId);
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) settings.get("calendarConfig");
        if (cfg == null) return DEFAULT_CALENDAR;

        @SuppressWarnings("unchecked")
        List<Integer> mlRaw = (List<Integer>) cfg.get("monthLengths");
        @SuppressWarnings("unchecked")
        List<String> mnRaw = (List<String>) cfg.get("monthNames");
        @SuppressWarnings("unchecked")
        List<String> wnRaw = (List<String>) cfg.get("weekdayNames");

        int[] monthLengths = mlRaw != null ? mlRaw.stream().mapToInt(i -> i).toArray() : DEFAULT_CALENDAR.monthLengths();
        String[] monthNames = mnRaw != null ? mnRaw.toArray(String[]::new) : DEFAULT_CALENDAR.monthNames();
        String[] weekdayNames = wnRaw != null ? wnRaw.toArray(String[]::new) : DEFAULT_CALENDAR.weekdayNames();
        return new CalendarConfig(monthLengths, monthNames, weekdayNames);
    }

    public void updateCalendarConfig(UUID campaignId, CalendarConfig config) {
        Map<String, Object> settings = readSettings(campaignId);
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("monthLengths", Arrays.asList(Arrays.stream(config.monthLengths()).boxed().toArray(Integer[]::new)));
        cfg.put("monthNames", List.of(config.monthNames()));
        cfg.put("weekdayNames", List.of(config.weekdayNames()));
        settings.put("calendarConfig", cfg);
        writeSettings(campaignId, settings);
    }

    // --- Current Date ---

    public InGameDate getCurrentDate(UUID campaignId) {
        Map<String, Object> settings = readSettings(campaignId);
        @SuppressWarnings("unchecked")
        Map<String, Object> date = (Map<String, Object>) settings.get("currentInGameDate");
        if (date == null) return new InGameDate(1492, 0, 1);
        return new InGameDate(
            ((Number) date.getOrDefault("year", 1492)).intValue(),
            ((Number) date.getOrDefault("month", 0)).intValue(),
            ((Number) date.getOrDefault("day", 1)).intValue()
        );
    }

    public void setCurrentDate(UUID campaignId, InGameDate date) {
        Map<String, Object> settings = readSettings(campaignId);
        Map<String, Object> dateObj = new LinkedHashMap<>();
        dateObj.put("year", date.year());
        dateObj.put("month", date.month());
        dateObj.put("day", date.day());
        settings.put("currentInGameDate", dateObj);
        writeSettings(campaignId, settings);
    }

    public InGameDate advanceDays(UUID campaignId, int days) {
        CalendarConfig cfg = getCalendarConfig(campaignId);
        InGameDate current = getCurrentDate(campaignId);
        int year = current.year();
        int month = current.month();
        int day = current.day();

        for (int i = 0; i < days; i++) {
            day++;
            if (day > cfg.monthLengths()[month]) {
                day = 1;
                month++;
                if (month >= cfg.monthLengths().length) {
                    month = 0;
                    year++;
                }
            }
        }
        InGameDate newDate = new InGameDate(year, month, day);
        setCurrentDate(campaignId, newDate);
        return newDate;
    }

    // --- Timeline Events ---

    public TimelineEventDto createEvent(UUID campaignId, InGameDate date, String title, String body, UUID noteId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        TimelineEvent te = new TimelineEvent();
        te.setCampaign(campaign);
        te.setInGameYear(date.year());
        te.setInGameMonth(date.month());
        te.setInGameDay(date.day());
        te.setTitle(title);
        te.setBody(body);
        if (noteId != null) {
            te.setNoteRef(noteRepository.findById(noteId)
                    .orElseThrow(() -> new NotFoundException("Note not found: " + noteId)));
        }
        te = timelineEventRepository.save(te);
        String label = computeRelativeLabel(campaignId, te);
        return TimelineEventDto.from(te, label);
    }

    public TimelineEventDto updateEvent(UUID id, InGameDate date, String title, String body, UUID noteId) {
        TimelineEvent te = timelineEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Timeline event not found: " + id));
        te.setInGameYear(date.year());
        te.setInGameMonth(date.month());
        te.setInGameDay(date.day());
        te.setTitle(title);
        te.setBody(body);
        if (noteId != null) {
            te.setNoteRef(noteRepository.findById(noteId).orElse(null));
        } else {
            te.setNoteRef(null);
        }
        te = timelineEventRepository.save(te);
        String label = computeRelativeLabel(te.getCampaign().getId(), te);
        return TimelineEventDto.from(te, label);
    }

    public void deleteEvent(UUID id) {
        if (!timelineEventRepository.existsById(id)) {
            throw new NotFoundException("Timeline event not found: " + id);
        }
        timelineEventRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<TimelineEventDto> findByCampaignId(UUID campaignId) {
        List<TimelineEvent> events = timelineEventRepository
                .findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(campaignId);
        InGameDate current = getCurrentDate(campaignId);
        CalendarConfig cfg = getCalendarConfig(campaignId);
        return events.stream()
                .map(e -> TimelineEventDto.from(e, computeRelativeLabel(current, e, cfg)))
                .toList();
    }

    // --- Helpers ---

    private String computeRelativeLabel(UUID campaignId, TimelineEvent te) {
        return computeRelativeLabel(getCurrentDate(campaignId), te, getCalendarConfig(campaignId));
    }

    private String computeRelativeLabel(InGameDate current, TimelineEvent te, CalendarConfig cfg) {
        int currentDays = dateToEpochDays(current, cfg);
        int eventDays = dateToEpochDays(te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(), cfg);
        int diff = eventDays - currentDays;
        if (diff < 0) return Math.abs(diff) + (Math.abs(diff) == 1 ? " day ago" : " days ago");
        if (diff == 0) return "today";
        if (diff == 1) return "tomorrow";
        return "in " + diff + " days";
    }

    private int dateToEpochDays(InGameDate date, CalendarConfig cfg) {
        return dateToEpochDays(date.year(), date.month(), date.day(), cfg);
    }

    private int dateToEpochDays(int year, int month, int day, CalendarConfig cfg) {
        int days = 0;
        for (int y = 0; y < year; y++) days += yearDays(cfg);
        for (int m = 0; m < month; m++) days += cfg.monthLengths()[m];
        return days + day;
    }

    private int yearDays(CalendarConfig cfg) {
        int total = 0;
        for (int len : cfg.monthLengths()) total += len;
        return total;
    }

    // --- Settings JSON helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSettings(UUID campaignId) {
        Campaign c = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        if (c.getSettings() == null || c.getSettings().isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(c.getSettings(), Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void writeSettings(UUID campaignId, Map<String, Object> settings) {
        Campaign c = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        try {
            c.setSettings(objectMapper.writeValueAsString(settings));
            campaignRepository.save(c);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save settings", e);
        }
    }
}
```

- [ ] **Step 2: Verify compiles**

```bash
mvn -q compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/
git commit -m "feat(m10): add CalendarService with calendar config, date management, and timeline CRUD"
```

---

### Task 7: Unit tests — TreasuryService

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/treasury/service/TreasuryServiceTest.java`

- [ ] **Step 1: Write TreasuryServiceTest**

```java
package dev.hendrikhoemberg.dmhelper.treasury.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(TreasuryService.class)
class TreasuryServiceTest {

    @Autowired private TreasuryService service;
    @Autowired private ItemAssignmentRepository repository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private PartyMember pc;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);

        pc = new PartyMember();
        pc.setCampaign(campaign);
        pc.setCharacterName("Thia");
        pc.setPlayerName("Anna");
        pc.setClassAndLevel("Rogue 5");
        pc.setAc(16);
        pc.setMaxHp(38);
        pc.setInitiativeBonus(4);
        pc.setSpeed(30);
        pc.setPassivePerception(17);
        pc.setPassiveInsight(12);
        pc.setPassiveInvestigation(14);
        em.persist(pc);

        EquipmentItem sword = new EquipmentItem();
        sword.setSourceKey("srd_longsword");
        sword.setName("Longsword");
        sword.setCategory(EquipmentItem.Category.WEAPON);
        sword.setCost("15 gp");
        em.persist(sword);

        em.flush();
    }

    @Test
    void shouldCreateAssignmentToPC() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "mysterious amulet", 1, false));
        assertThat(result.id()).isNotNull();
        assertThat(result.holderName()).isEqualTo("Thia");
        assertThat(result.itemName()).isEqualTo("mysterious amulet");
        assertThat(result.quantity()).isEqualTo(1);
    }

    @Test
    void shouldCreateAssignmentToPartyStash() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "bag of gems", 3, false));
        assertThat(result.holderName()).isEqualTo("Party Stash");
    }

    @Test
    void shouldToggleAttunement() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Ring of Protection", 1, false));
        assertThat(result.attuned()).isFalse();

        var toggled = service.toggleAttunement(result.id());
        assertThat(toggled.attuned()).isTrue();
    }

    @Test
    void shouldCountAttunements() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Item 1", 1, true));
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Item 2", 1, true));
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Item 3", 1, false));

        assertThat(service.countAttunements(pc.getId())).isEqualTo(2);
    }

    @Test
    void shouldRejectAssignmentWithoutItemRef() {
        assertThatThrownBy(() -> service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, null, 1, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFindByCampaignId() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Dagger", 2, false));
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "Party Gold", 100, false));

        var all = service.findByCampaignId(campaign.getId());
        assertThat(all).hasSize(2);
    }

    @Test
    void shouldFindByPartyMember() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Dagger", 1, false));
        var result = service.findByPartyMemberId(pc.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).holderName()).isEqualTo("Thia");
    }

    @Test
    void shouldFindPartyStash() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "Stash Item", 1, false));
        var stash = service.findPartyStash(campaign.getId());
        assertThat(stash).hasSize(1);
        assertThat(stash.get(0).holderName()).isEqualTo("Party Stash");
    }

    @Test
    void shouldDeleteAssignment() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Delete Me", 1, false));
        service.delete(result.id());
        assertThat(repository.findById(result.id())).isEmpty();
    }

    @Test
    void shouldUpdateAssignment() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Update Me", 1, false));
        var updated = service.update(result.id(), null, 5, true); // move to stash
        assertThat(updated.holderName()).isEqualTo("Party Stash");
        assertThat(updated.quantity()).isEqualTo(5);
        assertThat(updated.attuned()).isTrue();
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl . -Dtest="dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryServiceTest" -DfailIfNoTests=false
```
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/treasury/
git commit -m "test(m10): add TreasuryService unit tests"
```

---

### Task 8: Unit tests — LedgerService

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/ledger/service/LedgerServiceTest.java`

- [ ] **Step 1: Write LedgerServiceTest**

```java
package dev.hendrikhoemberg.dmhelper.ledger.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(LedgerService.class)
class LedgerServiceTest {

    @Autowired private LedgerService service;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreateLedgerEntry() {
        var result = service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("100"), "GP",
                "Thia", "Quest reward", 1492, 2, 15));
        assertThat(result.id()).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("100");
        assertThat(result.holder()).isEqualTo("Thia");
        assertThat(result.currency()).isEqualTo("GP");
    }

    @Test
    void shouldComputeGoldBalance() {
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("100"), "GP",
                "Thia", "Quest", null, null, null));
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "SPEND", new BigDecimal("32"), "GP",
                "Thia", "Inn stay", null, null, null));

        BigDecimal balance = service.computeGoldBalance(campaign.getId(), "Thia", "GP");
        assertThat(balance).isEqualByComparingTo("68.00");
    }

    @Test
    void shouldComputeBalancePerHolder() {
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("200"), "GP",
                "Party Stash", "Hoard", null, null, null));
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("50"), "GP",
                "Bruenor", "Share", null, null, null));

        var balances = service.computeAllGoldBalances(campaign.getId());
        assertThat(balances).hasSize(2);
    }

    @Test
    void shouldOrderByTimestampDesc() {
        var first = service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("10"), "GP",
                "Thia", "First", null, null, null));
        var second = service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("20"), "GP",
                "Thia", "Second", null, null, null));

        var list = service.findByCampaignId(campaign.getId());
        assertThat(list.get(0).id()).isEqualTo(second.id());
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl . -Dtest="dev.hendrikhoemberg.dmhelper.ledger.service.LedgerServiceTest" -DfailIfNoTests=false
```
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/ledger/
git commit -m "test(m10): add LedgerService unit tests"
```

---

### Task 9: Unit tests — CalendarService

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarServiceTest.java`

- [ ] **Step 1: Write CalendarServiceTest**

```java
package dev.hendrikhoemberg.dmhelper.calendar.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(CalendarService.class)
class CalendarServiceTest {

    @Autowired private CalendarService service;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        campaign.setSettings("""
                {"calendarConfig":{"monthLengths":[31,28,31,30,31,30,31,31,30,31,30,31],
                "monthNames":["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
                "weekdayNames":["Mo","Tu","We","Th","Fr","Sa","Su"]},
                "currentInGameDate":{"year":1492,"month":2,"day":15}}""");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldReadCalendarConfig() {
        CalendarConfig cfg = service.getCalendarConfig(campaign.getId());
        assertThat(cfg.monthNames()).containsExactly("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec");
        assertThat(cfg.monthLengths()[1]).isEqualTo(28);
    }

    @Test
    void shouldReturnDefaultsWhenNoConfig() {
        Campaign empty = new Campaign();
        empty.setName("Empty");
        em.persist(empty);
        em.flush();

        CalendarConfig cfg = service.getCalendarConfig(empty.getId());
        assertThat(cfg.monthNames()).contains("January");
    }

    @Test
    void shouldReadCurrentDate() {
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.year()).isEqualTo(1492);
        assertThat(date.month()).isEqualTo(2);
        assertThat(date.day()).isEqualTo(15);
    }

    @Test
    void shouldSetCurrentDate() {
        service.setCurrentDate(campaign.getId(), new InGameDate(1500, 5, 10));
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.year()).isEqualTo(1500);
        assertThat(date.month()).isEqualTo(5);
        assertThat(date.day()).isEqualTo(10);
    }

    @Test
    void shouldAdvanceDaysWithinMonth() {
        service.advanceDays(campaign.getId(), 1);
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.day()).isEqualTo(16);
        assertThat(date.month()).isEqualTo(2);
    }

    @Test
    void shouldAdvanceDaysAcrossMonth() {
        service.advanceDays(campaign.getId(), 20);
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.month()).isEqualTo(3);
        assertThat(date.day()).isEqualTo(4);
    }

    @Test
    void shouldCreateTimelineEvent() {
        var event = service.createEvent(campaign.getId(),
                new InGameDate(1492, 3, 30), "Solar Eclipse", "The sky darkens", null);
        assertThat(event.title()).isEqualTo("Solar Eclipse");
        assertThat(event.relativeLabel()).isNotNull();
    }

    @Test
    void shouldReturnTimelineSortedByDate() {
        service.createEvent(campaign.getId(), new InGameDate(1493, 0, 1), "Later", null, null);
        service.createEvent(campaign.getId(), new InGameDate(1492, 0, 1), "Earlier", null, null);

        var events = service.findByCampaignId(campaign.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).title()).isEqualTo("Earlier");
        assertThat(events.get(1).title()).isEqualTo("Later");
    }

    @Test
    void shouldComputeRelativeLabels() {
        // current date is 1492-2-15 (March 15 in 0-indexed months)
        service.createEvent(campaign.getId(),
                new InGameDate(1492, 2, 15), "Today Event", null, null);
        service.createEvent(campaign.getId(),
                new InGameDate(1492, 2, 16), "Tomorrow Event", null, null);
        service.createEvent(campaign.getId(),
                new InGameDate(1492, 2, 10), "Past Event", null, null);

        var events = service.findByCampaignId(campaign.getId());
        assertThat(events.get(0).relativeLabel()).isEqualTo("5 days ago");
        assertThat(events.get(1).relativeLabel()).isEqualTo("today");
        assertThat(events.get(2).relativeLabel()).isEqualTo("tomorrow");
    }

    @Test
    void shouldDeleteTimelineEvent() {
        var event = service.createEvent(campaign.getId(),
                new InGameDate(1492, 3, 1), "Delete Me", null, null);
        service.deleteEvent(event.id());
        var events = service.findByCampaignId(campaign.getId());
        assertThat(events).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl . -Dtest="dev.hendrikhoemberg.dmhelper.calendar.service.CalendarServiceTest" -DfailIfNoTests=false
```
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/calendar/
git commit -m "test(m10): add CalendarService unit tests"
```

---

### Task 10: TreasuryController — htmx CRUD for item assignments

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/web/TreasuryController.java`
- Create: `src/main/resources/templates/treasury/list.html`
- Create: `src/main/resources/templates/treasury/_card.html`
- Create: `src/main/resources/templates/treasury/_form.html`
- Create: `src/main/resources/templates/treasury/_attunement-warn.html`
- Create: `src/main/resources/templates/treasury/_holder-section.html`

- [ ] **Step 1: Create TreasuryController**

Create `treasury/web/TreasuryController.java` per the interface in §6.1. The controller uses `@ModelAttribute` to add `campaign`, `campaignId`, and `partyMembers` to every model. The `newForm` returns the `_form` fragment. The `create` action redirects to the list page. The `action` endpoint handles `"attune"` (toggle) and `"qty"` (update quantity). The `fragment/{memberId}` endpoint serves the holder section for htmx lazy-load from the sheet page.

```java
package dev.hendrikhoemberg.dmhelper.treasury.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/treasury")
public class TreasuryController {

    private final TreasuryService treasuryService;
    private final LedgerService ledgerService;
    private final CampaignRepository campaignRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final MagicItemRepository magicItemRepository;
    private final EquipmentItemRepository equipmentItemRepository;

    public TreasuryController(TreasuryService treasuryService, LedgerService ledgerService,
                              CampaignRepository campaignRepository, PartyMemberRepository partyMemberRepository,
                              MagicItemRepository magicItemRepository, EquipmentItemRepository equipmentItemRepository) {
        this.treasuryService = treasuryService;
        this.ledgerService = ledgerService;
        this.campaignRepository = campaignRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.magicItemRepository = magicItemRepository;
        this.equipmentItemRepository = equipmentItemRepository;
    }

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("partyMembers", partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId));
    }

    @GetMapping
    String overview(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("assignments", treasuryService.findByCampaignId(campaignId));
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        model.addAttribute("magicItems", magicItemRepository.findAllByOrderByNameAsc());
        model.addAttribute("equipmentItems", equipmentItemRepository.findAllByOrderByNameAsc());
        return "treasury/list";
    }

    @GetMapping("/fragment/{memberId}")
    String partyMemberItemsFragment(@PathVariable UUID campaignId, @PathVariable UUID memberId, Model model) {
        model.addAttribute("assignments", treasuryService.findByPartyMemberId(memberId));
        var pm = partyMemberRepository.findById(memberId).orElseThrow();
        model.addAttribute("holderName", pm.getCharacterName());
        model.addAttribute("attunementCount", treasuryService.countAttunements(memberId));
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        return "treasury/_holder-section :: holderSection";
    }

    @GetMapping("/new")
    String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("magicItems", magicItemRepository.findAllByOrderByNameAsc());
        model.addAttribute("equipmentItems", equipmentItemRepository.findAllByOrderByNameAsc());
        return "treasury/_form :: form";
    }

    @PostMapping
    String create(@PathVariable UUID campaignId,
                  @RequestParam(required = false) UUID partyMemberId,
                  @RequestParam(required = false) UUID magicItemId,
                  @RequestParam(required = false) UUID equipmentItemId,
                  @RequestParam(required = false) String customText,
                  @RequestParam(defaultValue = "1") int quantity,
                  @RequestParam(defaultValue = "false") boolean attuned,
                  Model model) {
        treasuryService.create(new TreasuryService.CreateAssignmentRequest(
                campaignId, partyMemberId, magicItemId, equipmentItemId, customText, quantity, attuned));
        return overview(campaignId, model);
    }

    @PutMapping("/{id}/attune")
    String toggleAttunement(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        var dto = treasuryService.toggleAttunement(id);
        model.addAttribute("assignment", dto);
        if (dto.partyMemberId() != null) {
            model.addAttribute("attunementCount", treasuryService.countAttunements(dto.partyMemberId()));
        }
        return "treasury/_card :: card";
    }

    @PutMapping("/{id}/quantity")
    String updateQuantity(@PathVariable UUID campaignId, @PathVariable UUID id,
                          @RequestParam int quantity, Model model) {
        var existing = treasuryService.findById(id);
        var dto = treasuryService.update(id, existing.partyMemberId(), quantity, existing.attuned());
        model.addAttribute("assignment", dto);
        return "treasury/_card :: card";
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        treasuryService.delete(id);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: Create treasury templates**

Create the template files with appropriate Thymeleaf + htmx markup:

**`treasury/list.html`** — Full page with tabs/sections per holder. Each section lists that holder's items (from `_card.html`), gold balance, and attunement warning (from `_attunement-warn.html`). An "Add Item" button at the top loads `_form.html`. Uses the standard page layout (`fragments/head`, `fragments/navbar`, `fragments/sidebar`).

**`treasury/_card.html`** — Single item card fragment (`th:fragment="card"`):
- Item name (compendium name or custom text)
- Holder name (character name or "Party Stash")
- Quantity badge
- Attuned badge (if attuned, with a subtle indicator)
- Action buttons: attune toggle (htmx PUT), quantity change (inline input + htmx PUT), delete (htmx DELETE)
- If attunement count > 3, include `_attunement-warn` fragment

**`treasury/_form.html`** — Add item form fragment (`th:fragment="form"`):
- Holder select dropdown (party members + "Party Stash" option)
- Item type radio: "Magic Item" / "Equipment" / "Custom"
- Conditional dropdowns for magic/equipment (loaded from model) or text input for custom
- Quantity input (default 1)
- Attuned checkbox
- Submit button (htmx POST, swaps to list view)

**`treasury/_attunement-warn.html`** — Warning fragment:
- Displayed when attunement count > 3 for a PC
- Shows current count, with text like "3 attuned items (limit 3) — table ruling advised"

**`treasury/_holder-section.html`** — Fragment for one holder's section (`th:fragment="holderSection"`):
- Holder name header
- Gold balance line (from ledgerService balances)
- Attunement count warning
- List of `_card` fragments for this holder's items

- [ ] **Step 3: Verify app compiles and templates render**

```bash
mvn -q compile && timeout 10 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK"

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/treasury/web/ src/main/resources/templates/treasury/
git commit -m "feat(m10): add TreasuryController with htmx templates for item assignments"
```

---

### Task 11: LedgerController — htmx for transaction history

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/web/LedgerController.java`
- Create: `src/main/resources/templates/ledger/list.html`
- Create: `src/main/resources/templates/ledger/_card.html`
- Create: `src/main/resources/templates/ledger/_form.html`
- Create: `src/main/resources/templates/ledger/_balance.html`

- [ ] **Step 1: Create LedgerController**

```java
package dev.hendrikhoemberg.dmhelper.ledger.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final CalendarService calendarService;
    private final CampaignRepository campaignRepository;

    public LedgerController(LedgerService ledgerService, CalendarService calendarService,
                            CampaignRepository campaignRepository) {
        this.ledgerService = ledgerService;
        this.calendarService = calendarService;
        this.campaignRepository = campaignRepository;
    }

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    @GetMapping
    String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("entries", ledgerService.findByCampaignId(campaignId));
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        return "ledger/list";
    }

    @GetMapping("/new")
    String newForm(@PathVariable UUID campaignId, Model model) {
        var date = calendarService.getCurrentDate(campaignId);
        model.addAttribute("currentYear", date.year());
        model.addAttribute("currentMonth", date.month());
        model.addAttribute("currentDay", date.day());
        return "ledger/_form :: form";
    }

    @PostMapping
    String create(@PathVariable UUID campaignId,
                  @RequestParam String kind,
                  @RequestParam String direction,
                  @RequestParam BigDecimal amount,
                  @RequestParam(defaultValue = "GP") String currency,
                  @RequestParam String holder,
                  @RequestParam(required = false) String note,
                  @RequestParam(required = false) Integer inGameYear,
                  @RequestParam(required = false) Integer inGameMonth,
                  @RequestParam(required = false) Integer inGameDay,
                  Model model) {
        ledgerService.create(new LedgerService.CreateLedgerEntryRequest(
                campaignId, kind, direction, amount, currency,
                holder, note, inGameYear, inGameMonth, inGameDay));
        return list(campaignId, model);
    }

    @GetMapping("/balances")
    String balances(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        return "ledger/_balance :: balanceSection";
    }
}
```

- [ ] **Step 2: Create ledger templates**

**`ledger/list.html`** — Full page with transaction history and balances:
- "Record Transaction" button loads `_form.html`
- Balances section (from `_balance.html`)
- Transaction list (each entry rendered by `_card.html`)

**`ledger/_card.html`** — Single entry fragment:
- Entry type badge (GOLD/ITEM, GAIN/SPEND with color)
- Amount (green for gain, red for spend)
- Holder, note
- In-game date if set
- Real-world timestamp

**`ledger/_form.html`** — Record transaction form:
- Kind select: GOLD / ITEM
- Direction select: GAIN / SPEND
- Amount input (number)
- Currency select: GP / SP / CP / PP / EP
- Holder text input
- Note textarea
- Optional in-game date fields (pre-filled from campaign current date, clearable)
- Submit (htmx POST, re-renders list)

**`ledger/_balance.html`** — Balance display fragment (`th:fragment="balanceSection"`):
- Table of holder → currency → balance
- Refreshable via htmx endpoint

- [ ] **Step 3: Verify app compiles**

```bash
mvn -q compile && timeout 10 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK"

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/ledger/web/ src/main/resources/templates/ledger/
git commit -m "feat(m10): add LedgerController with htmx templates for transaction history"
```

---

### Task 12: CalendarController — htmx for calendar, timeline, and config

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/web/CalendarController.java`
- Create: `src/main/resources/templates/calendar/overview.html`
- Create: `src/main/resources/templates/calendar/_current-date.html`
- Create: `src/main/resources/templates/calendar/_config-form.html`
- Create: `src/main/resources/templates/calendar/_timeline-list.html`
- Create: `src/main/resources/templates/calendar/_event-card.html`
- Create: `src/main/resources/templates/calendar/_event-form.html`

- [ ] **Step 1: Create CalendarController**

```java
package dev.hendrikhoemberg.dmhelper.calendar.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final CampaignRepository campaignRepository;

    public CalendarController(CalendarService calendarService, CampaignRepository campaignRepository) {
        this.calendarService = calendarService;
        this.campaignRepository = campaignRepository;
    }

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    @GetMapping
    String overview(@PathVariable UUID campaignId, Model model) {
        var date = calendarService.getCurrentDate(campaignId);
        var config = calendarService.getCalendarConfig(campaignId);
        model.addAttribute("currentDate", date);
        model.addAttribute("config", config);
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/overview";
    }

    @PostMapping("/advance")
    String advance(@PathVariable UUID campaignId, @RequestParam(defaultValue = "1") int days, Model model) {
        var date = calendarService.advanceDays(campaignId, days);
        model.addAttribute("currentDate", date);
        model.addAttribute("config", calendarService.getCalendarConfig(campaignId));
        return "calendar/_current-date :: currentDateFragment";
    }

    @PostMapping("/set-date")
    String setDate(@PathVariable UUID campaignId,
                   @RequestParam int year, @RequestParam int month, @RequestParam int day,
                   Model model) {
        calendarService.setCurrentDate(campaignId, new CalendarService.InGameDate(year, month, day));
        var date = calendarService.getCurrentDate(campaignId);
        model.addAttribute("currentDate", date);
        model.addAttribute("config", calendarService.getCalendarConfig(campaignId));
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/overview";
    }

    @GetMapping("/config")
    String configForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("config", calendarService.getCalendarConfig(campaignId));
        return "calendar/_config-form :: configForm";
    }

    @PostMapping("/config")
    String updateConfig(@PathVariable UUID campaignId,
                        @RequestParam String monthNames,
                        @RequestParam String monthLengths,
                        @RequestParam String weekdayNames,
                        Model model) {
        String[] mn = monthNames.split(",");
        String[] mlRaw = monthLengths.split(",");
        int[] ml = new int[mlRaw.length];
        for (int i = 0; i < mlRaw.length; i++) ml[i] = Integer.parseInt(mlRaw[i].trim());
        String[] wn = weekdayNames.split(",");
        calendarService.updateCalendarConfig(campaignId,
                new CalendarService.CalendarConfig(ml, mn, wn));
        return overview(campaignId, model);
    }

    @GetMapping("/events/new")
    String newEventForm(@PathVariable UUID campaignId, Model model) {
        return "calendar/_event-form :: eventForm";
    }

    @PostMapping("/events")
    String createEvent(@PathVariable UUID campaignId,
                       @RequestParam int year, @RequestParam int month, @RequestParam int day,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) UUID noteId, Model model) {
        calendarService.createEvent(campaignId,
                new CalendarService.InGameDate(year, month, day), title, body, noteId);
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/_timeline-list :: timelineList";
    }

    @GetMapping("/events/{eventId}/edit")
    String editEventForm(@PathVariable UUID campaignId, @PathVariable UUID eventId, Model model) {
        var events = calendarService.findByCampaignId(campaignId);
        var event = events.stream().filter(e -> e.id().equals(eventId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Event not found"));
        model.addAttribute("event", event);
        return "calendar/_event-form :: eventForm";
    }

    @PutMapping("/events/{eventId}")
    String updateEvent(@PathVariable UUID campaignId, @PathVariable UUID eventId,
                       @RequestParam int year, @RequestParam int month, @RequestParam int day,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) UUID noteId, Model model) {
        calendarService.updateEvent(eventId,
                new CalendarService.InGameDate(year, month, day), title, body, noteId);
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/_timeline-list :: timelineList";
    }

    @DeleteMapping("/events/{eventId}")
    ResponseEntity<Void> deleteEvent(@PathVariable UUID campaignId, @PathVariable UUID eventId) {
        calendarService.deleteEvent(eventId);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: Create calendar templates**

**`calendar/overview.html`** — Full page with:
- Current date display (from `_current-date.html`)
- Advance-days control (input + htmx POST to `/advance`, swaps current date fragment)
- Set-date form (inline or modal)
- Calendar config button (loads `_config-form.html`)
- Timeline section (from `_timeline-list.html`)
- Add event button (loads `_event-form.html`)

**`calendar/_current-date.html`** — Current date fragment (`th:fragment="currentDateFragment"`):
- Shows year, month name (from config), day
- Appears as a formatted string like "15 March 1492"

**`calendar/_config-form.html`** — Calendar config form fragment (`th:fragment="configForm"`):
- Month names (12 comma-separated text inputs)
- Month lengths (12 comma-separated number inputs)
- Weekday names (7 comma-separated text inputs)
- Save button (htmx POST, re-renders overview)

**`calendar/_timeline-list.html`** — Timeline list fragment (`th:fragment="timelineList"`):
- Chronological list of `_event-card` fragments
- Empty state if no events

**`calendar/_event-card.html`** — Single event fragment:
- Date display
- Relative label ("today", "tomorrow", "in 12 days", "3 days ago")
- Title (bold)
- Body (if present)
- Quick Note link (if noteRef is set)
- Edit/delete buttons

**`calendar/_event-form.html`** — Event form fragment (`th:fragment="eventForm"`):
- Date fields (year, month dropdown, day)
- Title input
- Body textarea
- Note link (optional UUID input)
- Submit button (htmx POST or PUT, swaps timeline list)

- [ ] **Step 3: Verify app compiles**

```bash
mvn -q compile && timeout 10 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK"

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/calendar/web/ src/main/resources/templates/calendar/
git commit -m "feat(m10): add CalendarController with htmx templates for calendar and timeline"
```

---

### Task 13: Controller integration tests

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/treasury/web/TreasuryControllerTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/ledger/web/LedgerControllerTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/web/CalendarControllerTest.java`

- [ ] **Step 1: Write web MVC tests**

Each test class uses `@WebMvcTest` with `@MockitoBean` for service dependencies. Key scenarios:

**TreasuryControllerTest:**
- `GET /campaigns/{id}/treasury` returns 200 with treasury/list template
- `POST /campaigns/{id}/treasury` calls service.create and redirects
- `PUT /campaigns/{id}/treasury/{id}/attune` calls toggleAttunement
- `DELETE /campaigns/{id}/treasury/{id}` returns 200

**LedgerControllerTest:**
- `GET /campaigns/{id}/ledger` returns 200 with ledger/list template
- `POST /campaigns/{id}/ledger` calls service.create and renders list

**CalendarControllerTest:**
- `GET /campaigns/{id}/calendar` returns 200 with calendar/overview template
- `POST /campaigns/{id}/calendar/advance?days=1` calls advanceDays
- `POST /campaigns/{id}/calendar/events` calls createEvent

```java
// Example pattern (TreasuryControllerTest):
@WebMvcTest(TreasuryController.class)
class TreasuryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TreasuryService treasuryService;
    @MockitoBean private LedgerService ledgerService;
    @MockitoBean private CampaignRepository campaignRepository;
    @MockitoBean private PartyMemberRepository partyMemberRepository;
    @MockitoBean private MagicItemRepository magicItemRepository;
    @MockitoBean private EquipmentItemRepository equipmentItemRepository;

    @Test
    void shouldShowTreasuryOverview() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(mockCampaign()));

        mockMvc.perform(get("/campaigns/{campaignId}/treasury", campaignId))
               .andExpect(status().isOk())
               .andExpect(view().name("treasury/list"));
    }
}
```

- [ ] **Step 2: Run controller tests**

```bash
mvn test -pl . -Dtest="dev.hendrikhoemberg.dmhelper.treasury.web.TreasuryControllerTest,dev.hendrikhoemberg.dmhelper.ledger.web.LedgerControllerTest,dev.hendrikhoemberg.dmhelper.calendar.web.CalendarControllerTest" -DfailIfNoTests=false
```
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/treasury/ src/test/java/dev/hendrikhoemberg/dmhelper/ledger/ src/test/java/dev/hendrikhoemberg/dmhelper/calendar/
git commit -m "test(m10): add web controller integration tests"
```

---

### Task 14: Campaign export/import integration

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

- [ ] **Step 1: Add DTO records to CampaignExportDto**

Add the three new nested records from §7.1 (`AssignmentExportDto`, `LedgerExportDto`, `TimelineExportDto`) and add new fields to the top-level record:
- `List<AssignmentExportDto> assignments`
- `List<LedgerExportDto> ledger`
- `List<TimelineExportDto> timeline`

Update all `CampaignExportDto.from()` factory methods to include `List.of()` for the new lists.

- [ ] **Step 2: Add export logic to CampaignService.exportToJson()**

Inject `ItemAssignmentRepository`, `LedgerEntryRepository`, `TimelineEventRepository`, `MagicItemRepository`, `EquipmentItemRepository` into `CampaignService` constructor.

Add export code from §7.2 to build the `assignments`, `ledger`, and `timeline` lists.

- [ ] **Step 3: Add import logic to CampaignService.importFromJson()**

Add import code from §7.3 to process each new list.

- [ ] **Step 4: Extend round-trip test**

Modify `CampaignImportExportRoundTripTest` to include assignments, ledger entries, and timeline events in the round-trip. Inject the new repositories and services.

```java
@Test
void roundTripPreservesTreasuryAndCalendar() {
    Campaign c = campaignService.create("Full Trip", "all the things");

    PartyMember pm = partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
            16, 38, 4, 30, 17, 12, 11, null);

    // Create item assignment
    ItemAssignment ia = new ItemAssignment();
    ia.setCampaign(c);
    ia.setPartyMember(pm);
    ia.setCustomText("Dagger +1");
    ia.setQuantity(1);
    ia.setAttuned(true);
    assignmentRepo.save(ia);

    // Create ledger entry
    LedgerEntry le = new LedgerEntry();
    le.setCampaign(c);
    le.setKind(LedgerEntry.Kind.GOLD);
    le.setDirection(LedgerEntry.Direction.GAIN);
    le.setAmount(new BigDecimal("500"));
    le.setCurrency("GP");
    le.setHolder("Party Stash");
    le.setNote("Dragon hoard");
    ledgerEntryRepo.save(le);

    // Create timeline event
    TimelineEvent te = new TimelineEvent();
    te.setCampaign(c);
    te.setInGameYear(1492);
    te.setInGameMonth(5);
    te.setInGameDay(1);
    te.setTitle("The Eclipse");
    te.setBody("A dark omen");
    timelineEventRepo.save(te);

    String json = campaignService.exportToJson(c.getId());
    Campaign imported = campaignService.importFromJson(json);

    List<ItemAssignment> importedAssignments = assignmentRepo
            .findByCampaignIdOrderByPartyMemberAsc(imported.getId());
    assertThat(importedAssignments).hasSize(1);
    assertThat(importedAssignments.get(0).getCustomText()).isEqualTo("Dagger +1");
    assertThat(importedAssignments.get(0).isAttuned()).isTrue();

    List<LedgerEntry> importedLedger = ledgerEntryRepo
            .findByCampaignIdOrderByTimestampDesc(imported.getId());
    assertThat(importedLedger).hasSize(1);
    assertThat(importedLedger.get(0).getAmount()).isEqualByComparingTo("500");
    assertThat(importedLedger.get(0).getHolder()).isEqualTo("Party Stash");

    List<TimelineEvent> importedTimeline = timelineEventRepo
            .findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(imported.getId());
    assertThat(importedTimeline).hasSize(1);
    assertThat(importedTimeline.get(0).getTitle()).isEqualTo("The Eclipse");
}
```

- [ ] **Step 5: Run round-trip test**

```bash
mvn test -pl . -Dtest="dev.hendrikhoemberg.dmhelper.campaign.service.CampaignImportExportRoundTripTest" -DfailIfNoTests=false
```
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/ src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/
git commit -m "feat(m10): add campaign export/import for treasury, ledger, and calendar"
```

---

### Task 15: Campaign page and navbar integration

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify (optional): `src/main/resources/templates/sheet/detail.html`

- [ ] **Step 1: Add navigation links**

Add Treasury, Ledger, and Calendar links to both the campaign detail page (§5.4) and the navbar (§5.4).

**campaigns/detail.html** — add three new sections after "Notes":

```html
<h2>Treasury & Loot</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/treasury(id=${campaign.id})}" class="btn btn-primary">
        Manage Loot
    </a>
</div>

<h2>Ledger</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/ledger(id=${campaign.id})}" class="btn btn-primary">
        View Ledger
    </a>
</div>

<h2>Calendar & Timeline</h2>
<div class="detail-actions" style="margin-bottom: var(--space-lg);">
    <a th:href="@{/campaigns/{id}/calendar(id=${campaign.id})}" class="btn btn-primary">
        Open Calendar
    </a>
</div>
```

**fragments/navbar.html** — add three links in the campaign sub-nav:

```html
<a th:href="@{/campaigns/{id}/treasury(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Treasury</a>
<a th:href="@{/campaigns/{id}/ledger(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Ledger</a>
<a th:href="@{/campaigns/{id}/calendar(id=${campaignId})}" style="color: var(--color-text-muted); text-decoration: none; font-size: var(--text-sm);">Calendar</a>
```

- [ ] **Step 2: Add loot section to sheet detail page (optional)**

Add an htmx lazy-load section to `sheet/detail.html` that fetches the party member's items from the treasury fragment endpoint:

```html
<section id="sheet-loot"
         hx-get="@{/campaigns/{campaignId}/treasury/fragment/{memberId}(campaignId=${campaignId}, memberId=${partyMember.id})}"
         hx-trigger="load"
         hx-swap="innerHTML">
    <div class="loading-muted">Loading equipment...</div>
</section>
```

This requires `campaignId` and `partyMember` to be in the model. If not already available, add them to the sheet controller.

- [ ] **Step 3: Verify app starts and pages render**

```bash
mvn -q compile && timeout 15 mvn -q spring-boot:run 2>&1 | grep -q "Started DmhelperApplication" && echo "OK"
```
Expected: "OK"

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/campaigns/detail.html src/main/resources/templates/fragments/navbar.html src/main/resources/templates/sheet/detail.html
git commit -m "feat(m10): add Treasury, Ledger, and Calendar navigation links"
```

---

### Task 16: Full test suite run and final verification

**Files:** none (verification only)

- [ ] **Step 1: Run all tests**

```bash
mvn test
```
Expected: All tests pass (BUILD SUCCESS)

- [ ] **Step 2: Run app and manually verify M10 definition of done**

```bash
mvn spring-boot:run
```

Manual verification checklist:
- [ ] Create a campaign, add party members
- [ ] Navigate to Treasury — add items to party members and party stash
- [ ] Toggle attunement — verify warning at >3 items
- [ ] Navigate to Ledger — record gold gain and spend, verify running balances
- [ ] Navigate to Calendar — set current date, advance by N days, verify display updates
- [ ] Create timeline events with different dates — verify relative labels ("today", "in 12 days")
- [ ] Edit and delete timeline events
- [ ] Export campaign — verify JSON includes assignments, ledger entries, and timeline events
- [ ] Import campaign — verify all data survives round-trip
- [ ] Verify navbar links work — Treasury, Ledger, Calendar navigate correctly
- [ ] Verify campaign detail page shows all three new sections with links

- [ ] **Step 3: Fix any issues found during manual testing**

- [ ] **Step 4: Commit any fixes**

```bash
git add -A && git commit -m "fix(m10): manual testing fixes and polish"
```

---

## 11. Completion Checklist

- [ ] `treasury/data/ItemAssignment.java` — entity with PC/stash holder and compendium refs
- [ ] `treasury/data/ItemAssignmentRepository.java` — queries by campaign, party member, stash, attunement count
- [ ] `ledger/data/LedgerEntry.java` — entity with Kind/Direction enums, in-game date, append-only
- [ ] `ledger/data/LedgerEntryRepository.java` — queries by campaign, holder, balance computation
- [ ] `calendar/data/TimelineEvent.java` — entity with year/month/day, noteRef
- [ ] `calendar/data/TimelineEventRepository.java` — chronological query by campaign
- [ ] `treasury/service/TreasuryService.java` — CRUD, attunement toggle, count, DTOs
- [ ] `ledger/service/LedgerService.java` — append-only create, balance derivation
- [ ] `calendar/service/CalendarService.java` — calendar config, date management, advance-days, timeline CRUD, relative labels
- [ ] `treasury/web/TreasuryController.java` — htmx controller for item assignments
- [ ] `ledger/web/LedgerController.java` — htmx controller for transaction history
- [ ] `calendar/web/CalendarController.java` — htmx controller for calendar and timeline
- [ ] Treasury templates — list, card, form, attunement-warn, holder-section
- [ ] Ledger templates — list, card, form, balance
- [ ] Calendar templates — overview, current-date, config-form, timeline-list, event-card, event-form
- [ ] CampaignExportDto — AssignmentExportDto, LedgerExportDto, TimelineExportDto
- [ ] CampaignService — export/import logic for all three new entity types
- [ ] CampaignImportExportRoundTripTest — extended to cover treasury, ledger, calendar
- [ ] Unit tests — TreasuryService, LedgerService, CalendarService
- [ ] Web MVC tests — TreasuryController, LedgerController, CalendarController
- [ ] Navbar — Treasury, Ledger, Calendar links
- [ ] Campaign detail page — Treasury, Ledger, Calendar & Timeline sections
- [ ] All tests pass (`mvn test`)
- [ ] App starts and renders all new pages
