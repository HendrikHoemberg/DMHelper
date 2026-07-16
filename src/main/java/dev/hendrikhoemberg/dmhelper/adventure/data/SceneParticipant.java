package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "scene_participant")
public class SceneParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(length = 500)
    private String displayName;

    @Column(nullable = false)
    private int quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SceneParticipantDisposition disposition;

    @Column(length = 1000)
    private String placementHint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statblock_id")
    private StatBlock statBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false)
    private int sortOrder = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public SceneParticipantDisposition getDisposition() { return disposition; }
    public void setDisposition(SceneParticipantDisposition disposition) { this.disposition = disposition; }

    public String getPlacementHint() { return placementHint; }
    public void setPlacementHint(String placementHint) { this.placementHint = placementHint; }

    public StatBlock getStatBlock() { return statBlock; }
    public void setStatBlock(StatBlock statBlock) { this.statBlock = statBlock; }

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
