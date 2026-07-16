package dev.hendrikhoemberg.dmhelper.campaign.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_annotation", indexes = {
    @Index(name = "idx_source_annotation_owner", columnList = "campaign_id, owner_type, owner_id")
})
public class SourceAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 30)
    private String ownerType;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(length = 200)
    private String fieldPath;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceAnnotationConfidence confidence = SourceAnnotationConfidence.UNKNOWN;

    @Column(length = 500)
    private String sourceLocator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceAnnotationStatus status = SourceAnnotationStatus.OPEN;

    @Column(columnDefinition = "CLOB")
    private String resolutionNote;

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

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public SourceAnnotationConfidence getConfidence() { return confidence; }
    public void setConfidence(SourceAnnotationConfidence confidence) { this.confidence = confidence; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public SourceAnnotationStatus getStatus() { return status; }
    public void setStatus(SourceAnnotationStatus status) { this.status = status; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
