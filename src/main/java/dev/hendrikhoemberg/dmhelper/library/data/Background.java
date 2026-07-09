package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "background", indexes = {
    @Index(name = "idx_bg_name", columnList = "name"),
})
public class Background {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String abilityScores;

    @Column(length = 100)
    private String featRef;

    @Column(columnDefinition = "CLOB")
    private String skills;

    @Column(columnDefinition = "CLOB")
    private String tools;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(columnDefinition = "CLOB")
    private String equipment;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbilityScores() { return abilityScores; }
    public void setAbilityScores(String abilityScores) { this.abilityScores = abilityScores; }
    public String getFeatRef() { return featRef; }
    public void setFeatRef(String featRef) { this.featRef = featRef; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }
}
