package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "encounter_token_placement",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_encounter_placement_combatant",
               columnNames = "combatant_id"),
       indexes = {
           @Index(name = "idx_encounter_placement_encounter", columnList = "encounter_id"),
           @Index(name = "idx_encounter_placement_map", columnList = "map_id")
       })
public class EncounterTokenPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    @org.hibernate.annotations.OnDelete(
            action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Encounter encounter;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "combatant_id", nullable = false)
    @org.hibernate.annotations.OnDelete(
            action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Combatant combatant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private GameMap map;

    @Column(name = "position_x", nullable = false)
    private int positionX;

    @Column(name = "position_y", nullable = false)
    private int positionY;

    @Column(name = "size_cols", nullable = false)
    private int sizeCols = 1;

    @Column(name = "size_rows", nullable = false)
    private int sizeRows = 1;

    @Column(nullable = false, length = 7)
    private String color = "#7b68ee";

    @Column(length = 100)
    private String icon;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public Combatant getCombatant() { return combatant; }
    public void setCombatant(Combatant combatant) { this.combatant = combatant; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public int getPositionX() { return positionX; }
    public void setPositionX(int positionX) { this.positionX = positionX; }

    public int getPositionY() { return positionY; }
    public void setPositionY(int positionY) { this.positionY = positionY; }

    public int getSizeCols() { return sizeCols; }
    public void setSizeCols(int sizeCols) { this.sizeCols = sizeCols; }

    public int getSizeRows() { return sizeRows; }
    public void setSizeRows(int sizeRows) { this.sizeRows = sizeRows; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
