package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "magic_item", indexes = {
    @Index(name = "idx_magic_name", columnList = "name"),
    @Index(name = "idx_magic_rarity", columnList = "rarity"),
    @Index(name = "idx_magic_category", columnList = "category"),
    @Index(name = "idx_magic_item_source", columnList = "source"),
    @Index(name = "idx_magic_item_campaign", columnList = "campaign_id_fk"),
})
public class MagicItem {

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

    @Column(length = 50)
    private String rarity;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String type;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(length = 200)
    private String weight;

    @Column(length = 50)
    private String cost;

    private boolean requiresAttunement;

    @Column(columnDefinition = "CLOB")
    private String attunementDetail;

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
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }
    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }
    public boolean isRequiresAttunement() { return requiresAttunement; }
    public void setRequiresAttunement(boolean requiresAttunement) { this.requiresAttunement = requiresAttunement; }
    public String getAttunementDetail() { return attunementDetail; }
    public void setAttunementDetail(String attunementDetail) { this.attunementDetail = attunementDetail; }
}
