package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "threat_reference", indexes = {
    @Index(name = "idx_threat_reference_trap", columnList = "trap_id"),
    @Index(name = "idx_threat_reference_hazard", columnList = "hazard_id"),
    @Index(name = "idx_threat_reference_target", columnList = "target_type, target_id")
})
public class ThreatReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trap_id")
    private Trap trap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hazard_id")
    private Hazard hazard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ThreatReferenceRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private CampaignContentType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "display_text", length = 500)
    private String displayText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Trap getTrap() { return trap; }
    public void setTrap(Trap trap) { this.trap = trap; }

    public Hazard getHazard() { return hazard; }
    public void setHazard(Hazard hazard) { this.hazard = hazard; }

    public ThreatReferenceRole getRole() { return role; }
    public void setRole(ThreatReferenceRole role) { this.role = role; }

    public CampaignContentType getTargetType() { return targetType; }
    public void setTargetType(CampaignContentType targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
