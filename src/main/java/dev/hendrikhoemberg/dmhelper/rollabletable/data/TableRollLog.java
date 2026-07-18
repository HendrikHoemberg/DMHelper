package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "table_roll_log", indexes = {
    @Index(name = "idx_table_roll_log_campaign", columnList = "campaign_id"),
    @Index(name = "idx_table_roll_log_table", columnList = "table_id")
})
public class TableRollLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RollableTable table;

    @Column(length = 255)
    private String tableKeySnapshot;

    @Column(length = 500)
    private String tableNameSnapshot;

    @Column(columnDefinition = "CLOB")
    private String resultJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TableDraftType draftType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TableDraftStatus draftStatus;

    @Column(columnDefinition = "CLOB")
    private String resolvedTargetIds;

    private Instant resolvedAt;

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
    public RollableTable getTable() { return table; }
    public void setTable(RollableTable table) { this.table = table; }
    public String getTableKeySnapshot() { return tableKeySnapshot; }
    public void setTableKeySnapshot(String tableKeySnapshot) { this.tableKeySnapshot = tableKeySnapshot; }
    public String getTableNameSnapshot() { return tableNameSnapshot; }
    public void setTableNameSnapshot(String tableNameSnapshot) { this.tableNameSnapshot = tableNameSnapshot; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public TableDraftType getDraftType() { return draftType; }
    public void setDraftType(TableDraftType draftType) { this.draftType = draftType; }
    public TableDraftStatus getDraftStatus() { return draftStatus; }
    public void setDraftStatus(TableDraftStatus draftStatus) { this.draftStatus = draftStatus; }
    public String getResolvedTargetIds() { return resolvedTargetIds; }
    public void setResolvedTargetIds(String resolvedTargetIds) { this.resolvedTargetIds = resolvedTargetIds; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
