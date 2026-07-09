package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "stat_block", indexes = {
        @Index(name = "idx_statblock_source", columnList = "source"),
        @Index(name = "idx_statblock_campaign", columnList = "campaign_id"),
        @Index(name = "idx_statblock_name", columnList = "name"),
        @Index(name = "idx_statblock_cr", columnList = "cr"),
        @Index(name = "idx_statblock_type", columnList = "type")
})
public class StatBlock {

    public enum Source { SRD, CUSTOM }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id_fk")
    @JsonIgnore
    private Campaign campaign;

    @Deprecated(forRemoval = true)
    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 10)
    private String cr;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(length = 100)
    private String size;

    @Column(length = 100)
    private String alignment;

    private int ac;

    @Column(nullable = false, length = 50)
    private String hp;

    @Column(length = 200)
    private String speed;

    private int strScore;
    private int dexScore;
    private int conScore;
    private int intScore;
    private int wisScore;
    private int chaScore;

    private Integer strSave;
    private Integer dexSave;
    private Integer conSave;
    private Integer intSave;
    private Integer wisSave;
    private Integer chaSave;

    @Column(length = 500)
    private String skills;

    @Column(length = 500)
    private String damageVulnerabilities;

    @Column(length = 500)
    private String damageResistances;

    @Column(length = 500)
    private String damageImmunities;

    @Column(length = 500)
    private String conditionImmunities;

    @Column(length = 500)
    private String senses;

    @Column(length = 1000)
    private String languages;

    @Column(columnDefinition = "CLOB")
    private String traits;

    @Column(columnDefinition = "CLOB")
    private String actions;

    @Column(columnDefinition = "CLOB")
    private String bonusActions;

    @Column(columnDefinition = "CLOB")
    private String reactions;

    @Column(columnDefinition = "CLOB")
    private String legendaryActions;

    @Column(length = 500)
    private String legendaryDescription;

    @Column(columnDefinition = "CLOB")
    private String lairActions;

    @Column(length = 100)
    private String sourceKey;

    private int xp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    @Deprecated(forRemoval = true)
    public UUID getCampaignId() { return campaign != null ? campaign.getId() : campaignId; }

    @Deprecated(forRemoval = true)
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCr() { return cr; }
    public void setCr(String cr) { this.cr = cr; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public int getAc() { return ac; }
    public void setAc(int ac) { this.ac = ac; }

    public String getHp() { return hp; }
    public void setHp(String hp) { this.hp = hp; }

    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }

    public int getStrScore() { return strScore; }
    public void setStrScore(int strScore) { this.strScore = strScore; }

    public int getDexScore() { return dexScore; }
    public void setDexScore(int dexScore) { this.dexScore = dexScore; }

    public int getConScore() { return conScore; }
    public void setConScore(int conScore) { this.conScore = conScore; }

    public int getIntScore() { return intScore; }
    public void setIntScore(int intScore) { this.intScore = intScore; }

    public int getWisScore() { return wisScore; }
    public void setWisScore(int wisScore) { this.wisScore = wisScore; }

    public int getChaScore() { return chaScore; }
    public void setChaScore(int chaScore) { this.chaScore = chaScore; }

    public Integer getStrSave() { return strSave; }
    public void setStrSave(Integer strSave) { this.strSave = strSave; }

    public Integer getDexSave() { return dexSave; }
    public void setDexSave(Integer dexSave) { this.dexSave = dexSave; }

    public Integer getConSave() { return conSave; }
    public void setConSave(Integer conSave) { this.conSave = conSave; }

    public Integer getIntSave() { return intSave; }
    public void setIntSave(Integer intSave) { this.intSave = intSave; }

    public Integer getWisSave() { return wisSave; }
    public void setWisSave(Integer wisSave) { this.wisSave = wisSave; }

    public Integer getChaSave() { return chaSave; }
    public void setChaSave(Integer chaSave) { this.chaSave = chaSave; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getDamageVulnerabilities() { return damageVulnerabilities; }
    public void setDamageVulnerabilities(String damageVulnerabilities) { this.damageVulnerabilities = damageVulnerabilities; }

    public String getDamageResistances() { return damageResistances; }
    public void setDamageResistances(String damageResistances) { this.damageResistances = damageResistances; }

    public String getDamageImmunities() { return damageImmunities; }
    public void setDamageImmunities(String damageImmunities) { this.damageImmunities = damageImmunities; }

    public String getConditionImmunities() { return conditionImmunities; }
    public void setConditionImmunities(String conditionImmunities) { this.conditionImmunities = conditionImmunities; }

    public String getSenses() { return senses; }
    public void setSenses(String senses) { this.senses = senses; }

    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }

    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }

    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }

    public String getBonusActions() { return bonusActions; }
    public void setBonusActions(String bonusActions) { this.bonusActions = bonusActions; }

    public String getReactions() { return reactions; }
    public void setReactions(String reactions) { this.reactions = reactions; }

    public String getLegendaryActions() { return legendaryActions; }
    public void setLegendaryActions(String legendaryActions) { this.legendaryActions = legendaryActions; }

    public String getLegendaryDescription() { return legendaryDescription; }
    public void setLegendaryDescription(String legendaryDescription) { this.legendaryDescription = legendaryDescription; }

    public String getLairActions() { return lairActions; }
    public void setLairActions(String lairActions) { this.lairActions = lairActions; }

    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Transient
    private transient List<Map<String, String>> traitsParsed;

    @Transient
    private transient List<Map<String, String>> actionsParsed;

    @Transient
    private transient List<Map<String, String>> bonusActionsParsed;

    @Transient
    private transient List<Map<String, String>> reactionsParsed;

    @Transient
    private transient List<Map<String, String>> legendaryActionsParsed;

    @Transient
    private transient List<Map<String, String>> lairActionsParsed;

    public List<Map<String, String>> getTraitsParsed() { return traitsParsed; }
    public void setTraitsParsed(List<Map<String, String>> traitsParsed) { this.traitsParsed = traitsParsed; }

    public List<Map<String, String>> getActionsParsed() { return actionsParsed; }
    public void setActionsParsed(List<Map<String, String>> actionsParsed) { this.actionsParsed = actionsParsed; }

    public List<Map<String, String>> getBonusActionsParsed() { return bonusActionsParsed; }
    public void setBonusActionsParsed(List<Map<String, String>> bonusActionsParsed) { this.bonusActionsParsed = bonusActionsParsed; }

    public List<Map<String, String>> getReactionsParsed() { return reactionsParsed; }
    public void setReactionsParsed(List<Map<String, String>> reactionsParsed) { this.reactionsParsed = reactionsParsed; }

    public List<Map<String, String>> getLegendaryActionsParsed() { return legendaryActionsParsed; }
    public void setLegendaryActionsParsed(List<Map<String, String>> legendaryActionsParsed) { this.legendaryActionsParsed = legendaryActionsParsed; }

    public List<Map<String, String>> getLairActionsParsed() { return lairActionsParsed; }
    public void setLairActionsParsed(List<Map<String, String>> lairActionsParsed) { this.lairActionsParsed = lairActionsParsed; }
}
