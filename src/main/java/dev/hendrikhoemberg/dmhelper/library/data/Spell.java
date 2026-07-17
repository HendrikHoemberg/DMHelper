package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@JsonIgnoreProperties({"provenance"})
@Table(name = "spell", indexes = {
    @Index(name = "idx_spell_name", columnList = "name"),
    @Index(name = "idx_spell_level", columnList = "level"),
    @Index(name = "idx_spell_school", columnList = "school"),
    @Index(name = "idx_spell_source", columnList = "source"),
    @Index(name = "idx_spell_campaign", columnList = "campaign_id_fk"),
})
public class Spell {

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
    @JsonIgnore
    private ContentProvenance provenance;

    @Column(nullable = false, length = 255)
    private String name;

    private int level;

    @Column(length = 50)
    private String school;

    @Column(length = 100)
    private String castingTime;

    @Column(length = 100)
    private String range;

    @Column(length = 200)
    private String components;

    @Column(length = 100)
    private String duration;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(columnDefinition = "CLOB")
    private String higherLevel;

    private boolean ritual;
    private boolean concentration;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public ContentSource getSource() { return source; }
    public void setSource(ContentSource source) { this.source = source; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    @JsonIgnore
    public ContentProvenance getProvenance() { return provenance; }
    public void setProvenance(ContentProvenance provenance) { this.provenance = provenance; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }
    public String getCastingTime() { return castingTime; }
    public void setCastingTime(String castingTime) { this.castingTime = castingTime; }
    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }
    public String getComponents() { return components; }
    public void setComponents(String components) { this.components = components; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHigherLevel() { return higherLevel; }
    public void setHigherLevel(String higherLevel) { this.higherLevel = higherLevel; }
    public boolean isRitual() { return ritual; }
    public void setRitual(boolean ritual) { this.ritual = ritual; }
    public boolean isConcentration() { return concentration; }
    public void setConcentration(boolean concentration) { this.concentration = concentration; }
}
