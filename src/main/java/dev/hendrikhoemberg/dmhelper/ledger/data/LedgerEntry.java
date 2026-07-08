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
