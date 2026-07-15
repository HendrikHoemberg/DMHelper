package dev.hendrikhoemberg.dmhelper.campaign.packagev2.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_package_key")
public class CampaignPackageKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID campaignId;

    @Column(nullable = false, length = 40)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 100)
    private String packageKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public CampaignPackageKey() {}

    public CampaignPackageKey(UUID campaignId, CampaignContentType type, UUID entityId, String packageKey) {
        this.campaignId = campaignId;
        this.entityType = type.name();
        this.entityId = entityId;
        this.packageKey = packageKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getPackageKey() { return packageKey; }
    public void setPackageKey(String packageKey) { this.packageKey = packageKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
