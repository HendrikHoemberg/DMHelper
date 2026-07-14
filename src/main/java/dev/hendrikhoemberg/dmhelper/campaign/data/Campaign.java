package dev.hendrikhoemberg.dmhelper.campaign.data;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(columnDefinition = "CLOB")
    private String settings;

    // DB-level default so ddl-auto=update can add this column to non-empty tables
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean milestoneLeveling = false;

    @Column
    private UUID currentSceneId;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }

    public boolean isMilestoneLeveling() { return milestoneLeveling; }
    public void setMilestoneLeveling(boolean milestoneLeveling) { this.milestoneLeveling = milestoneLeveling; }

    public UUID getCurrentSceneId() { return currentSceneId; }
    public void setCurrentSceneId(UUID currentSceneId) { this.currentSceneId = currentSceneId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Book-cover byline, e.g. "4 heroes · opened 8 July". Derived, never persisted. */
    @Transient
    private transient String authorLine;

    public String getAuthorLine() { return authorLine; }
    public void setAuthorLine(String authorLine) { this.authorLine = authorLine; }
}
