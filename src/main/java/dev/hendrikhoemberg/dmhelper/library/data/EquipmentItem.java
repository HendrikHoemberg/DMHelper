package dev.hendrikhoemberg.dmhelper.library.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "equipment_item", indexes = {
    @Index(name = "idx_equip_name", columnList = "name"),
    @Index(name = "idx_equip_category", columnList = "category"),
    @Index(name = "idx_equipment_source", columnList = "source"),
    @Index(name = "idx_equipment_campaign", columnList = "campaign_id_fk"),
})
public class EquipmentItem {

    public enum Category { WEAPON, ARMOR, GEAR, TOOL }

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Category category;

    @Column(length = 50)
    private String cost;

    @Column(length = 50)
    private String weight;

    @Column(columnDefinition = "CLOB")
    private String properties;

    @Column(columnDefinition = "CLOB")
    private String description;

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
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }
    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }
    public String getProperties() { return properties; }
    public void setProperties(String properties) { this.properties = properties; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
