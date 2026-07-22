package dev.hendrikhoemberg.dmhelper.session.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_audit_entry", indexes = {
    @Index(name = "idx_session_audit_order", columnList = "session_id, created_at, id")
})
public class SessionAuditEntry {

    public enum EntryType {
        PRESENTATION_OVERRIDE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CampaignSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private EntryType entryType;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CampaignSession getSession() { return session; }
    public void setSession(CampaignSession session) { this.session = session; }

    public EntryType getEntryType() { return entryType; }
    public void setEntryType(EntryType entryType) { this.entryType = entryType; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public UUID getContentId() { return contentId; }
    public void setContentId(UUID contentId) { this.contentId = contentId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
