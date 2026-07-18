package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "scene_section", uniqueConstraints = {
    @UniqueConstraint(name = "uq_scene_section_scene_sort", columnNames = {"scene_id", "sort_order"})
})
public class SceneSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SceneSectionKind kind;

    @Column(length = 500)
    private String label;

    @Column(columnDefinition = "CLOB")
    private String body;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_kind", length = 10)
    private ThreatKind threatKind;

    @Column(name = "threat_id")
    private UUID threatId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public SceneSectionKind getKind() { return kind; }
    public void setKind(SceneSectionKind kind) { this.kind = kind; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public ThreatKind getThreatKind() { return threatKind; }
    public void setThreatKind(ThreatKind threatKind) { this.threatKind = threatKind; }

    public UUID getThreatId() { return threatId; }
    public void setThreatId(UUID threatId) { this.threatId = threatId; }
}
