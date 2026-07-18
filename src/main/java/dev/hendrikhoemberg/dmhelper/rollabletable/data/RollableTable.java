package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rollable_table", indexes = {
    @Index(name = "idx_rollable_table_campaign", columnList = "campaign_id_fk")
})
public class RollableTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id_fk")
    private Campaign campaign;

    @Embedded
    private ContentProvenance provenance;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TableAddressMode addressMode;

    @Column(length = 255)
    private String rollExpression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TableCategory category;

    @Column(length = 1000)
    private String tags;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RollableTableEntry> entries = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public ContentSource getSource() { return source; }
    public void setSource(ContentSource source) { this.source = source; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public ContentProvenance getProvenance() { return provenance; }
    public void setProvenance(ContentProvenance provenance) { this.provenance = provenance; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TableAddressMode getAddressMode() { return addressMode; }
    public void setAddressMode(TableAddressMode addressMode) { this.addressMode = addressMode; }
    public String getRollExpression() { return rollExpression; }
    public void setRollExpression(String rollExpression) { this.rollExpression = rollExpression; }
    public TableCategory getCategory() { return category; }
    public void setCategory(TableCategory category) { this.category = category; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<RollableTableEntry> getEntries() { return entries; }
    public void setEntries(List<RollableTableEntry> entries) { this.entries = entries; }
}
