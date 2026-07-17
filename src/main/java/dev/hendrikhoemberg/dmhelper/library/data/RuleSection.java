package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "rule_section", indexes = {
    @Index(name = "idx_rule_name", columnList = "name"),
    @Index(name = "idx_rule_ruleset", columnList = "ruleset"),
    @Index(name = "idx_rule_section_source", columnList = "source"),
    @Index(name = "idx_rule_section_campaign", columnList = "campaign_id_fk"),
})
public class RuleSection {

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

    @Column(columnDefinition = "CLOB")
    private String body;

    @Column(length = 100)
    private String parentKey;

    private int sortOrder;

    @Column(length = 100)
    private String ruleset;

    private int initialHeaderLevel;

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
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getParentKey() { return parentKey; }
    public void setParentKey(String parentKey) { this.parentKey = parentKey; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getRuleset() { return ruleset; }
    public void setRuleset(String ruleset) { this.ruleset = ruleset; }
    public int getInitialHeaderLevel() { return initialHeaderLevel; }
    public void setInitialHeaderLevel(int initialHeaderLevel) { this.initialHeaderLevel = initialHeaderLevel; }
}
