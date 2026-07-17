package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "character_class", indexes = {
    @Index(name = "idx_class_name", columnList = "name"),
    @Index(name = "idx_class_subclassof", columnList = "subclassOf"),
    @Index(name = "idx_class_source", columnList = "source"),
    @Index(name = "idx_class_campaign", columnList = "campaign_id_fk"),
})
public class CharacterClass {

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

    @Column(length = 10)
    private String hitDie;

    @Column(columnDefinition = "CLOB")
    private String savingThrows;

    @Column(columnDefinition = "CLOB")
    private String features;

    @Column(columnDefinition = "CLOB")
    private String spellcasting;

    @Column(length = 100)
    private String subclassOf;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(columnDefinition = "CLOB")
    private String proficiencies;

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
    public String getHitDie() { return hitDie; }
    public void setHitDie(String hitDie) { this.hitDie = hitDie; }
    public String getSavingThrows() { return savingThrows; }
    public void setSavingThrows(String savingThrows) { this.savingThrows = savingThrows; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public String getSpellcasting() { return spellcasting; }
    public void setSpellcasting(String spellcasting) { this.spellcasting = spellcasting; }
    public String getSubclassOf() { return subclassOf; }
    public void setSubclassOf(String subclassOf) { this.subclassOf = subclassOf; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getProficiencies() { return proficiencies; }
    public void setProficiencies(String proficiencies) { this.proficiencies = proficiencies; }
}
