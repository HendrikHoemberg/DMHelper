package dev.hendrikhoemberg.dmhelper.notes.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "note_link")
public class NoteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_note_id", nullable = false)
    private Note sourceNote;

    @Column(nullable = false, length = 50)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 500)
    private String displayText;

    @Column(nullable = false)
    private boolean resolved = false;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Note getSourceNote() { return sourceNote; }
    public void setSourceNote(Note sourceNote) { this.sourceNote = sourceNote; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}
