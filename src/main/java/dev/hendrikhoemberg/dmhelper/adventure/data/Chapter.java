package dev.hendrikhoemberg.dmhelper.adventure.data;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "adventure_chapter", indexes = {
    @Index(name = "idx_chapter_adventure", columnList = "adventure_id"),
})
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adventure_id", nullable = false)
    private Adventure adventure;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String intro;

    @Column(nullable = false)
    private int sortOrder = 0;

    @OneToMany(mappedBy = "chapter", orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Scene> scenes = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Adventure getAdventure() { return adventure; }
    public void setAdventure(Adventure adventure) { this.adventure = adventure; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public List<Scene> getScenes() { return scenes; }
    public void setScenes(List<Scene> scenes) { this.scenes = scenes; }
}
