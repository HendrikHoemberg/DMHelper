package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "species", indexes = {
    @Index(name = "idx_species_name", columnList = "name"),
})
public class Species {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String size;

    @Column(length = 50)
    private String speed;

    @Column(columnDefinition = "CLOB")
    private String traits;

    @Column(columnDefinition = "CLOB")
    private String description;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }
    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
