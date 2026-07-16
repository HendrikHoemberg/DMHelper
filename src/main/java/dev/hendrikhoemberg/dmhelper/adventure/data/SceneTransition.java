package dev.hendrikhoemberg.dmhelper.adventure.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "scene_transition", uniqueConstraints = {
    @UniqueConstraint(name = "uq_scene_transition_scene_sort", columnNames = {"scene_id", "sort_order"})
})
public class SceneTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SceneTransitionKind kind;

    @Column(length = 500)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_scene_id")
    private Scene targetScene;

    @Column(length = 500)
    private String externalDestination;

    @Column(length = 2000)
    private String condition;

    @Column(columnDefinition = "CLOB")
    private String dmNote;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false)
    private int sortOrder = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public SceneTransitionKind getKind() { return kind; }
    public void setKind(SceneTransitionKind kind) { this.kind = kind; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Scene getTargetScene() { return targetScene; }
    public void setTargetScene(Scene targetScene) { this.targetScene = targetScene; }

    public String getExternalDestination() { return externalDestination; }
    public void setExternalDestination(String externalDestination) { this.externalDestination = externalDestination; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getDmNote() { return dmNote; }
    public void setDmNote(String dmNote) { this.dmNote = dmNote; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
