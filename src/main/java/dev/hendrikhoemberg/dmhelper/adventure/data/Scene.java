package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "adventure_scene", indexes = {
    @Index(name = "idx_scene_chapter", columnList = "chapter_id"),
    @Index(name = "idx_scene_map", columnList = "map_id"),
    @Index(name = "idx_scene_encounter", columnList = "encounter_id"),
})
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 50)
    private String sceneKey;

    @Column(columnDefinition = "CLOB")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SceneStatus status = SceneStatus.UNVISITED;

    @Column(nullable = false)
    private int sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private GameMap map;

    private Integer pinX;

    private Integer pinY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToMany
    @JoinTable(name = "scene_statblock",
            joinColumns = @JoinColumn(name = "scene_id"),
            inverseJoinColumns = @JoinColumn(name = "statblock_id"))
    @OrderColumn(name = "position")
    private List<StatBlock> statBlocks = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "scene_handout",
            joinColumns = @JoinColumn(name = "scene_id"),
            inverseJoinColumns = @JoinColumn(name = "handout_id"))
    @OrderColumn(name = "position")
    private List<Handout> handouts = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Chapter getChapter() { return chapter; }
    public void setChapter(Chapter chapter) { this.chapter = chapter; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSceneKey() { return sceneKey; }
    public void setSceneKey(String sceneKey) { this.sceneKey = sceneKey; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public SceneStatus getStatus() { return status; }
    public void setStatus(SceneStatus status) { this.status = status; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public Integer getPinX() { return pinX; }
    public void setPinX(Integer pinX) { this.pinX = pinX; }

    public Integer getPinY() { return pinY; }
    public void setPinY(Integer pinY) { this.pinY = pinY; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public List<StatBlock> getStatBlocks() { return statBlocks; }
    public void setStatBlocks(List<StatBlock> statBlocks) { this.statBlocks = statBlocks; }

    public List<Handout> getHandouts() { return handouts; }
    public void setHandouts(List<Handout> handouts) { this.handouts = handouts; }
}
