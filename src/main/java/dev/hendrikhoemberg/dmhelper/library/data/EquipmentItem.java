package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "equipment_item", indexes = {
    @Index(name = "idx_equip_name", columnList = "name"),
    @Index(name = "idx_equip_category", columnList = "category"),
})
public class EquipmentItem {

    public enum Category { WEAPON, ARMOR, GEAR, TOOL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

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
