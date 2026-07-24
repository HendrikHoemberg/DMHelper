package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "readiness_acknowledgement",
        uniqueConstraints = @UniqueConstraint(name = "uq_readiness_ack_campaign_item",
                columnNames = {"campaign_id", "item_key"}),
        indexes = @Index(name = "idx_readiness_ack_campaign", columnList = "campaign_id"))
public class ReadinessAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "item_key", nullable = false, length = 200)
    private String itemKey;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
