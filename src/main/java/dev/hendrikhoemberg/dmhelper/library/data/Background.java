package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "background", indexes = {
    @Index(name = "idx_bg_name", columnList = "name"),
    @Index(name = "idx_background_source", columnList = "source"),
    @Index(name = "idx_background_campaign", columnList = "campaign_id_fk"),
})
public class Background {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id_fk")
    @JsonIgnore
    private Campaign campaign;

    @Embedded
    private ContentProvenance provenance;

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
    public ContentSource getSource() { return source; }
    public void setSource(ContentSource source) { this.source = source; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public ContentProvenance getProvenance() { return provenance; }
    public void setProvenance(ContentProvenance provenance) { this.provenance = provenance; }
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
