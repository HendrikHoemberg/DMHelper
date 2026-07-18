package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "rollable_table_entry_reference")
public class RollableTableEntryReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private RollableTableEntry entry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TableReferenceScope targetScope;

    @Column(nullable = false, length = 50)
    private String targetType;

    private UUID targetId;

    @Column(length = 100)
    private String catalogRuleset;

    @Column(length = 255)
    private String catalogSourceKey;

    @Column(length = 500)
    private String displayText;

    @Column(nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public RollableTableEntry getEntry() { return entry; }
    public void setEntry(RollableTableEntry entry) { this.entry = entry; }
    public TableReferenceScope getTargetScope() { return targetScope; }
    public void setTargetScope(TableReferenceScope targetScope) { this.targetScope = targetScope; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    public String getCatalogRuleset() { return catalogRuleset; }
    public void setCatalogRuleset(String catalogRuleset) { this.catalogRuleset = catalogRuleset; }
    public String getCatalogSourceKey() { return catalogSourceKey; }
    public void setCatalogSourceKey(String catalogSourceKey) { this.catalogSourceKey = catalogSourceKey; }
    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
