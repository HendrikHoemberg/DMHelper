package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class AbstractThreat {

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
    private ThreatSeverity severity;

    private Integer minLevel;

    private Integer maxLevel;

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

    public ThreatSeverity getSeverity() { return severity; }
    public void setSeverity(ThreatSeverity severity) { this.severity = severity; }

    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }

    public Integer getMaxLevel() { return maxLevel; }
    public void setMaxLevel(Integer maxLevel) { this.maxLevel = maxLevel; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
